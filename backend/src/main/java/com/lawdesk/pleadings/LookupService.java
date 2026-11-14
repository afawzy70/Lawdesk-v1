package com.lawdesk.pleadings;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.security.access.AccessDeniedException;

import com.lawdesk.api.BusinessRuleException;
import com.lawdesk.api.ConflictException;
import com.lawdesk.api.NotFoundException;
import com.lawdesk.audit.AuditLog;
import com.lawdesk.security.ActorRef;
import com.lawdesk.security.CurrentActor;
import com.lawdesk.security.Permission;

/**
 * Managed lists (owner decision D10): values are added, edited, approved and deactivated, never deleted, and each change
 * keeps the previous state in {@code lookup_value_history}. The lists are those of {@link ListCatalog}; a value the
 * system logic depends on (system = true) keeps its code and stays active, a label-only list takes no new value.
 */
@Service
class LookupService {

    private static final String SELECT = """
            select id, list_code, code, label_ar, label_en, origin, approved_at, active, version, sort_order, system from lookup_value""";

    private final JdbcClient jdbc;
    private final CurrentActor currentActor;
    private final AuditLog audit;

    LookupService(JdbcClient jdbc, CurrentActor currentActor, AuditLog audit) {
        this.jdbc = jdbc;
        this.currentActor = currentActor;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    List<LookupRequests.View> list(String listCode, boolean includeInactive) {
        requireList(listCode);
        return jdbc.sql(SELECT + " where list_code = :list" + (includeInactive ? "" : " and active")
                + " order by sort_order, created_at, code").param("list", listCode).query(LookupService::view).list();
    }

    @Transactional
    LookupRequests.View create(String listCode, LookupRequests.Create request) {
        ActorRef actor = currentActor.require();
        requireList(listCode);
        requireManager(listCode);
        if (!ListCatalog.find(listCode).orElseThrow().addable()) {
            throw new BusinessRuleException("LIST_LABEL_ONLY", "the list " + listCode + " takes no new values");
        }
        UUID id;
        try {
            id = jdbc.sql("""
                    insert into lookup_value (list_code, code, label_ar, label_en, origin, sort_order, created_by, updated_by)
                    values (:list, :code, :ar, :en, 'PROPOSED',
                            coalesce(:sort, (select coalesce(max(sort_order), 0) + 1 from lookup_value
                                             where list_code = :list)), :actor, :actor) returning id""")
                    .param("list", listCode).param("code", request.code()).param("ar", request.labelAr())
                    .param("en", request.labelEn()).param("sort", request.sortOrder()).param("actor", actor.id())
                    .query(UUID.class).single();
        } catch (DuplicateKeyException e) {
            throw new ConflictException("the list " + listCode + " has the value " + request.code() + " already");
        }
        history(id, "CREATED", actor);
        audit.recordWithChange("LOOKUP_CREATE", actor.id(), actor.username(), "LOOKUP", id.toString(),
                listCode + "/" + request.code());
        return find(id);
    }

    @Transactional
    LookupRequests.View update(UUID id, LookupRequests.Update request) {
        ActorRef actor = currentActor.require();
        LookupRequests.View current = find(id);
        requireManager(current.listCode());
        if (current.system() && !request.active()) {
            throw new BusinessRuleException("SYSTEM_VALUE_FIXED", "a system value is never deactivated");
        }
        int changed = jdbc.sql("""
                update lookup_value set label_ar = :ar, label_en = :en, active = :active,
                       sort_order = coalesce(:sort, sort_order), version = version + 1,
                       updated_at = now(), updated_by = :actor where id = :id and version = :version""")
                .param("sort", request.sortOrder())
                .param("ar", request.labelAr()).param("en", request.labelEn()).param("active", request.active())
                .param("actor", actor.id()).param("id", id).param("version", request.version()).update();
        if (changed == 0) {
            throw new ConflictException("the value " + id + " was changed by someone else");
        }
        String change = request.active() == current.active() ? "EDITED" : request.active() ? "ACTIVATED" : "DEACTIVATED";
        history(id, change, actor);
        audit.recordWithChange("LOOKUP_" + change, actor.id(), actor.username(), "LOOKUP", id.toString(),
                current.listCode() + "/" + current.code());
        return find(id);
    }

    @Transactional
    LookupRequests.View approve(UUID id) {
        ActorRef actor = currentActor.require();
        LookupRequests.View current = find(id);
        requireManager(current.listCode());
        if (current.approvedAt() == null) {
            jdbc.sql("""
                    update lookup_value set approved_by = :actor, approved_at = now(), version = version + 1,
                           updated_at = now(), updated_by = :actor where id = :id""")
                    .param("actor", actor.id()).param("id", id).update();
            history(id, "APPROVED", actor);
            audit.recordWithChange("LOOKUP_APPROVE", actor.id(), actor.username(), "LOOKUP", id.toString(),
                    current.listCode() + "/" + current.code());
        }
        return find(id);
    }

    @Transactional(readOnly = true)
    List<LookupRequests.History> history(UUID id) {
        find(id);
        return jdbc.sql("""
                select rev, label_ar, label_en, origin, active, change, changed_by, changed_at
                from lookup_value_history where value_id = :id order by rev desc""").param("id", id)
                .query((rs, n) -> new LookupRequests.History(rs.getInt("rev"), rs.getString("label_ar"),
                        rs.getString("label_en"), rs.getString("origin"), rs.getBoolean("active"), rs.getString("change"),
                        rs.getObject("changed_by", UUID.class), rs.getTimestamp("changed_at").toInstant()))
                .list();
    }

    private void history(UUID id, String change, ActorRef actor) {
        jdbc.sql("""
                insert into lookup_value_history (value_id, rev, label_ar, label_en, origin, active, approved_at, change,
                                                  changed_by)
                select id, (select coalesce(max(rev), 0) + 1 from lookup_value_history where value_id = :id), label_ar,
                       label_en, origin, active, approved_at, :change, :actor from lookup_value where id = :id""")
                .param("id", id).param("change", change).param("actor", actor.id()).update();
    }

    /** The three original lists stay with LOOKUP_MANAGE (checked by the controller); every other list is the administrator's. */
    private void requireManager(String listCode) {
        if (!ListCatalog.ORIGINAL.contains(listCode) && !currentActor.has(Permission.LIST_ADMIN)) {
            throw new AccessDeniedException("missing LIST_ADMIN");
        }
    }

    private static void requireList(String listCode) {
        if (!ListCatalog.isLookupList(listCode)) {
            throw new NotFoundException("list");
        }
    }

    private LookupRequests.View find(UUID id) {
        return jdbc.sql(SELECT + " where id = :id").param("id", id).query(LookupService::view).optional()
                .orElseThrow(() -> new NotFoundException("value"));
    }

    private static LookupRequests.View view(ResultSet rs, int n) throws SQLException {
        java.sql.Timestamp approved = rs.getTimestamp("approved_at");
        String origin = rs.getString("origin");
        return new LookupRequests.View(rs.getObject("id", UUID.class), rs.getString("list_code"), rs.getString("code"),
                rs.getString("label_ar"), rs.getString("label_en"), origin,
                "PROPOSED".equals(origin) && approved == null, approved == null ? null : approved.toInstant(),
                rs.getBoolean("active"), rs.getLong("version"), rs.getInt("sort_order"), rs.getBoolean("system"));
    }
}
