package com.lawdesk.cases;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
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
 * The administrative units a case concerns (requirements section 6): sector, department and section of the authority,
 * one primary and any number of supporting. The names of the structure are copied when an involvement is recorded and
 * never rewritten, so the case keeps the structure it was registered under whatever the settings become.
 */
@Service
class InvolvementService {

    static final String UNIT_NOT_AUTHORITY = "INVOLVEMENT_UNIT_NOT_AUTHORITY";
    static final String UNIT_INACTIVE = "INVOLVEMENT_UNIT_INACTIVE";
    static final String ALREADY_INVOLVED = "INVOLVEMENT_ALREADY_RECORDED";
    static final String PRIMARY_EXISTS = "INVOLVEMENT_PRIMARY_EXISTS";

    private static final String COLUMNS = """
            id, case_id, org_unit_id, is_primary, sector_name_snapshot, department_name_snapshot,
            section_name_snapshot, contact_name, contact_info, referral_date, requested_from_unit, notes, version,
            created_at, removed_at""";

    private final JdbcClient jdbc;
    private final CaseAccessPolicy access;
    private final CaseTimeline timeline;
    private final CurrentActor currentActor;
    private final AuditLog audit;

    InvolvementService(JdbcClient jdbc, CaseAccessPolicy access, CaseTimeline timeline, CurrentActor currentActor,
            AuditLog audit) {
        this.jdbc = jdbc;
        this.access = access;
        this.timeline = timeline;
        this.currentActor = currentActor;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    List<InvolvementRequests.View> list(UUID caseId, boolean includeRemoved) {
        access.requireView(caseId);
        return jdbc.sql("select " + COLUMNS + " from case_org_involvement where case_id = :id"
                        + (includeRemoved ? "" : " and removed_at is null")
                        + " order by is_primary desc, created_at, id")
                .param("id", caseId).query(InvolvementService::row).list();
    }

    @Transactional
    InvolvementRequests.View add(UUID caseId, InvolvementRequests.Create request) {
        ActorRef actor = currentActor.require();
        access.requireChange(caseId);
        Names names = names(request);
        if (request.primary() && primaryOf(caseId).isPresent()) {
            throw new BusinessRuleException(PRIMARY_EXISTS, "the case has a primary unit already");
        }
        if (request.orgUnitId() != null && jdbc.sql("""
                        select exists (select 1 from case_org_involvement
                                       where case_id = :id and org_unit_id = :unit and removed_at is null)""")
                .param("id", caseId).param("unit", request.orgUnitId()).query(Boolean.class).single()) {
            throw new BusinessRuleException(ALREADY_INVOLVED, "the unit is on the case already");
        }
        UUID id;
        try {
            id = jdbc.sql("""
                            insert into case_org_involvement (case_id, org_unit_id, is_primary, sector_name_snapshot,
                                department_name_snapshot, section_name_snapshot, contact_name, contact_info,
                                referral_date, requested_from_unit, notes, created_by, updated_by)
                            values (:case, :unit, :primary, :sector, :department, :section, :contact, :info, :referral,
                                :requested, :notes, :actor, :actor) returning id""")
                    .param("case", caseId).param("unit", request.orgUnitId()).param("primary", request.primary())
                    .param("sector", names.sector()).param("department", names.department())
                    .param("section", names.section()).param("contact", request.contactName())
                    .param("info", request.contactInfo()).param("referral", request.referralDate())
                    .param("requested", request.requestedFromUnit()).param("notes", request.notes())
                    .param("actor", actor.id()).query(UUID.class).single();
        } catch (DuplicateKeyException e) {
            throw new ConflictException("the case has a primary unit already");
        }
        timeline.append(caseId, "INVOLVED_UNIT_ADDED", CaseTimeline.fit("Unit involved: " + names.label()
                + (request.primary() ? " (primary)" : "")), actor.id());
        audit.recordWithChange("CASE_INVOLVEMENT_ADD", actor.id(), actor.username(), "CASE", caseId.toString(),
                request.primary() ? "primary" : "supporting");
        return get(id);
    }

    @Transactional
    InvolvementRequests.View update(UUID caseId, UUID id, InvolvementRequests.Update request) {
        ActorRef actor = currentActor.require();
        access.requireChange(caseId);
        InvolvementRequests.View current = find(caseId, id).filter(v -> v.removedAt() == null)
                .orElseThrow(() -> new NotFoundException("involvement"));
        if (current.version() != request.version()) {
            throw new ConflictException("involvement " + id + " has changed since version " + request.version());
        }
        if (request.primary() && !current.primary() && primaryOf(caseId).isPresent()) {
            throw new BusinessRuleException(PRIMARY_EXISTS, "the case has a primary unit already");
        }
        jdbc.sql("""
                        update case_org_involvement set is_primary = :primary, contact_name = :contact,
                            contact_info = :info, referral_date = :referral, requested_from_unit = :requested,
                            notes = :notes, version = version + 1, updated_at = now(), updated_by = :actor
                        where id = :id and version = :version""")
                .param("primary", request.primary()).param("contact", request.contactName())
                .param("info", request.contactInfo()).param("referral", request.referralDate())
                .param("requested", request.requestedFromUnit()).param("notes", request.notes())
                .param("actor", actor.id()).param("id", id).param("version", request.version()).update();
        timeline.append(caseId, "INVOLVED_UNIT_CHANGED", CaseTimeline.fit("Involved unit changed: "
                + label(current)), actor.id());
        return get(id);
    }

    @Transactional
    void remove(UUID caseId, UUID id) {
        ActorRef actor = currentActor.require();
        access.requireChange(caseId);
        InvolvementRequests.View current = find(caseId, id).filter(v -> v.removedAt() == null)
                .orElseThrow(() -> new NotFoundException("involvement"));
        jdbc.sql("""
                        update case_org_involvement set removed_at = now(), removed_by = :actor, is_primary = false,
                            version = version + 1
                        where id = :id""").param("actor", actor.id()).param("id", id).update();
        timeline.append(caseId, "INVOLVED_UNIT_REMOVED", CaseTimeline.fit("Involved unit removed: " + label(current)),
                actor.id());
        audit.recordWithChange("CASE_INVOLVEMENT_REMOVE", actor.id(), actor.username(), "CASE", caseId.toString(),
                "id=" + id);
    }

    private record Names(String sector, String department, String section) {

        String label() {
            return java.util.stream.Stream.of(sector, department, section).filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.joining(" / "));
        }
    }

    /** From the structure when a unit is named (the names of the unit and its ancestors by level), else as typed. */
    private Names names(InvolvementRequests.Create request) {
        if (request.orgUnitId() == null) {
            if (request.sectorName() == null && request.departmentName() == null && request.sectionName() == null) {
                throw new FieldErrorException("orgUnitId", "NotNull");
            }
            return new Names(request.sectorName(), request.departmentName(), request.sectionName());
        }
        var unit = jdbc.sql("select tree, active from org_unit where id = :id").param("id", request.orgUnitId())
                .query((rs, n) -> new Object[] {rs.getString(1), rs.getBoolean(2)}).optional()
                .orElseThrow(() -> new FieldErrorException("orgUnitId", "Exists"));
        if (!"AUTHORITY".equals(unit[0])) {
            throw new BusinessRuleException(UNIT_NOT_AUTHORITY, "the unit is not part of the authority's structure");
        }
        if (!(Boolean) unit[1]) {
            throw new BusinessRuleException(UNIT_INACTIVE, "the unit is deactivated");
        }
        String[] byLevel = new String[3];
        jdbc.sql("""
                        with recursive up(id, parent_id, level, name_ar) as (
                            select id, parent_id, level, name_ar from org_unit where id = :id
                            union all
                            select o.id, o.parent_id, o.level, o.name_ar from org_unit o join up on o.id = up.parent_id)
                        select level, name_ar from up""").param("id", request.orgUnitId()).query(rs -> {
            switch (rs.getString("level")) {
                case "SECTOR" -> byLevel[0] = rs.getString("name_ar");
                case "DEPARTMENT" -> byLevel[1] = rs.getString("name_ar");
                default -> byLevel[2] = rs.getString("name_ar");
            }
        });
        return new Names(byLevel[0], byLevel[1], byLevel[2]);
    }

    private Optional<UUID> primaryOf(UUID caseId) {
        return jdbc.sql("select id from case_org_involvement where case_id = :id and is_primary").param("id", caseId)
                .query(UUID.class).optional();
    }

    private InvolvementRequests.View get(UUID id) {
        return jdbc.sql("select " + COLUMNS + " from case_org_involvement where id = :id").param("id", id)
                .query(InvolvementService::row).single();
    }

    private Optional<InvolvementRequests.View> find(UUID caseId, UUID id) {
        return jdbc.sql("select " + COLUMNS + " from case_org_involvement where id = :id and case_id = :case")
                .param("id", id).param("case", caseId).query(InvolvementService::row).optional();
    }

    private static String label(InvolvementRequests.View view) {
        return new Names(view.sectorName(), view.departmentName(), view.sectionName()).label();
    }

    private static InvolvementRequests.View row(ResultSet rs, int n) throws SQLException {
        Date referral = rs.getDate("referral_date");
        java.sql.Timestamp removed = rs.getTimestamp("removed_at");
        return new InvolvementRequests.View(rs.getObject("id", UUID.class), rs.getObject("case_id", UUID.class),
                rs.getObject("org_unit_id", UUID.class), rs.getBoolean("is_primary"),
                rs.getString("sector_name_snapshot"), rs.getString("department_name_snapshot"),
                rs.getString("section_name_snapshot"), rs.getString("contact_name"), rs.getString("contact_info"),
                referral == null ? null : referral.toLocalDate(), rs.getString("requested_from_unit"),
                rs.getString("notes"), rs.getLong("version"), rs.getTimestamp("created_at").toInstant(),
                removed == null ? null : removed.toInstant());
    }
}
