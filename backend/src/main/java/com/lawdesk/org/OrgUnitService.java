package com.lawdesk.org;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.api.BusinessRuleException;
import com.lawdesk.api.ConflictException;
import com.lawdesk.api.FieldErrorException;
import com.lawdesk.api.NotFoundException;
import com.lawdesk.audit.AuditLog;
import com.lawdesk.security.ActorRef;
import com.lawdesk.security.CurrentActor;

/**
 * The organisation tree (LD-050, docs/design/LD-DOC-03): units are never deleted, only deactivated, so a case that names
 * a unit keeps a valid reference after the structure changes. Renaming a unit does not rewrite what a case recorded about
 * it: cases keep their own snapshot of the names (LD-DOC-03, WP1b).
 */
@Service
class OrgUnitService {

    static final String PARENT_TREE_MISMATCH = "ORG_PARENT_TREE_MISMATCH";
    static final String LEVEL_NOT_BELOW_PARENT = "ORG_LEVEL_NOT_BELOW_PARENT";
    static final String CYCLE = "ORG_CYCLE";
    static final String NOT_LEGAL_UNIT = "ORG_NOT_LEGAL_UNIT";
    static final String UNIT_INACTIVE = "ORG_UNIT_INACTIVE";

    private static final String COLUMNS = "id, tree, parent_id, level, name_ar, name_en, active, valid_from, valid_to, version";

    private final JdbcClient jdbc;
    private final CurrentActor currentActor;
    private final AuditLog audit;

    OrgUnitService(JdbcClient jdbc, CurrentActor currentActor, AuditLog audit) {
        this.jdbc = jdbc;
        this.currentActor = currentActor;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    List<OrgUnit> list(String tree, boolean includeInactive) {
        return jdbc.sql("select " + COLUMNS + " from org_unit where (cast(:tree as varchar) is null or tree = :tree)"
                        + " and (:all or active) order by tree, lower(name_ar), id")
                .param("tree", tree).param("all", includeInactive).query(OrgUnitService::row).list();
    }

    @Transactional(readOnly = true)
    OrgUnit get(UUID id) {
        return find(id).orElseThrow(() -> new NotFoundException("org unit"));
    }

    @Transactional
    OrgUnit create(OrgRequests.Create request) {
        ActorRef actor = currentActor.require();
        checkValidity(request.validFrom(), request.validTo());
        checkPlacement(request.tree(), request.level(), request.parentId(), null);
        OrgUnit created;
        try {
            created = jdbc.sql("""
                            insert into org_unit (tree, parent_id, level, name_ar, name_en, valid_from, valid_to,
                                                  created_by, updated_by)
                            values (:tree, :parent, :level, :nameAr, :nameEn, :from, :to, :actor, :actor)
                            returning\s""" + COLUMNS)
                    .param("tree", request.tree()).param("parent", request.parentId()).param("level", request.level())
                    .param("nameAr", request.nameAr().strip()).param("nameEn", blankToNull(request.nameEn()))
                    .param("from", request.validFrom()).param("to", request.validTo()).param("actor", actor.id())
                    .query(OrgUnitService::row).single();
        } catch (DuplicateKeyException e) {
            throw new ConflictException("a unit with this name already exists under the same parent");
        }
        audit.recordWithChange("ORG_UNIT_CREATE", actor.id(), actor.username(), "ORG_UNIT", created.id().toString(),
                created.tree() + "/" + created.level());
        return created;
    }

    @Transactional
    OrgUnit update(UUID id, OrgRequests.Update request) {
        ActorRef actor = currentActor.require();
        OrgUnit current = get(id);
        if (current.version() != request.version()) {
            throw new ConflictException("org unit " + id + " has changed since version " + request.version());
        }
        checkValidity(request.validFrom(), request.validTo());
        checkPlacement(current.tree(), current.level(), request.parentId(), id);
        OrgUnit updated;
        try {
            updated = jdbc.sql("""
                            update org_unit set parent_id = :parent, name_ar = :nameAr, name_en = :nameEn,
                                active = :active, valid_from = :from, valid_to = :to, version = version + 1,
                                updated_at = now(), updated_by = :actor
                            where id = :id and version = :version
                            returning\s""" + COLUMNS)
                    .param("id", id).param("version", request.version()).param("parent", request.parentId())
                    .param("nameAr", request.nameAr().strip()).param("nameEn", blankToNull(request.nameEn()))
                    .param("active", request.active()).param("from", request.validFrom())
                    .param("to", request.validTo()).param("actor", actor.id())
                    .query(OrgUnitService::row).optional()
                    .orElseThrow(() -> new ConflictException("org unit " + id + " has changed concurrently"));
        } catch (DuplicateKeyException e) {
            throw new ConflictException("a unit with this name already exists under the same parent");
        }
        audit.recordWithChange("ORG_UNIT_UPDATE", actor.id(), actor.username(), "ORG_UNIT", id.toString(),
                current.active() == updated.active() ? "edit" : updated.active() ? "reactivate" : "deactivate");
        return updated;
    }

    /** Places a user in a unit of the legal tree, or takes them out of any unit. */
    @Transactional
    void placeUser(UUID userId, UUID unitId) {
        ActorRef actor = currentActor.require();
        if (unitId != null) {
            OrgUnit unit = get(unitId);
            if (!"LEGAL".equals(unit.tree())) {
                throw new BusinessRuleException(NOT_LEGAL_UNIT, "users belong to units of the legal department");
            }
            if (!unit.active()) {
                throw new BusinessRuleException(UNIT_INACTIVE, "the unit is deactivated");
            }
        }
        int changed = jdbc.sql("update app_user set org_unit_id = :unit where id = :id")
                .param("unit", unitId).param("id", userId).update();
        if (changed == 0) {
            throw new NotFoundException("user");
        }
        audit.recordWithChange("USER_ORG_UNIT_CHANGE", actor.id(), actor.username(), "USER", userId.toString(),
                unitId == null ? "none" : "unit=" + unitId);
    }

    /** A parent must be in the same tree, one level above the unit, and not the unit itself or any of its descendants. */
    private void checkPlacement(String tree, String level, UUID parentId, UUID self) {
        if (parentId == null) {
            return;
        }
        OrgUnit parent = find(parentId).orElseThrow(() -> new FieldErrorException("parentId", "Exists"));
        if (!parent.tree().equals(tree)) {
            throw new BusinessRuleException(PARENT_TREE_MISMATCH, "the parent belongs to the other tree");
        }
        if (rank(level) <= rank(parent.level())) {
            throw new BusinessRuleException(LEVEL_NOT_BELOW_PARENT, level + " cannot sit under " + parent.level());
        }
        if (self != null && isSelfOrDescendant(self, parentId)) {
            throw new BusinessRuleException(CYCLE, "a unit cannot move under itself");
        }
    }

    private boolean isSelfOrDescendant(UUID root, UUID candidate) {
        return jdbc.sql("""
                        with recursive tree(id) as (select id from org_unit where id = :root
                            union select o.id from org_unit o join tree on o.parent_id = tree.id)
                        select exists (select 1 from tree where id = :candidate)""")
                .param("root", root).param("candidate", candidate).query(Boolean.class).single();
    }

    private static int rank(String level) {
        return switch (level) {
            case "SECTOR" -> 0;
            case "DEPARTMENT" -> 1;
            default -> 2;
        };
    }

    private static void checkValidity(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new FieldErrorException("validTo", "Range");
        }
    }

    private Optional<OrgUnit> find(UUID id) {
        return jdbc.sql("select " + COLUMNS + " from org_unit where id = :id").param("id", id)
                .query(OrgUnitService::row).optional();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static OrgUnit row(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        return new OrgUnit(rs.getObject("id", UUID.class), rs.getString("tree"), rs.getObject("parent_id", UUID.class),
                rs.getString("level"), rs.getString("name_ar"), rs.getString("name_en"), rs.getBoolean("active"),
                rs.getObject("valid_from", LocalDate.class), rs.getObject("valid_to", LocalDate.class),
                rs.getLong("version"));
    }
}
