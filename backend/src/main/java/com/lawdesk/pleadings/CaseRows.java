package com.lawdesk.pleadings;

import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.api.FieldErrorException;
import com.lawdesk.api.NotFoundException;
import com.lawdesk.audit.AuditLog;
import com.lawdesk.cases.CaseAccessPolicy;
import com.lawdesk.cases.CaseLookup;
import com.lawdesk.security.ActorRef;
import com.lawdesk.security.CurrentActor;

/**
 * What every LD-051 service needs around a record that belongs to a case: finding its case, the one access rule of
 * decision D3 (a case the user may not see is "not found", one seen but not changeable is refused), the sequence numbers,
 * the checks that a referenced record belongs to the same case, the timeline entry and the audit event.
 */
@Component
class CaseRows {

    /** Stages in the order of the judicial ladder, then the tracks beside it (the order of {@code Stage}); {@code p} is the proceeding. */
    static final String STAGE_ORDER = "case p.stage when 'FIRST_INSTANCE' then 0 when 'APPEAL' then 1 when 'CASSATION' then 2"
            + " when 'CONSTITUTIONAL' then 3 when 'ENFORCEMENT' then 4 else 5 end";

    private static final int EVENT_MAX = 500;

    private final JdbcClient jdbc;
    private final CaseAccessPolicy access;
    private final CaseLookup cases;
    private final AuditLog audit;
    private final CurrentActor currentActor;

    CaseRows(JdbcClient jdbc, CaseAccessPolicy access, CaseLookup cases, AuditLog audit, CurrentActor currentActor) {
        this.jdbc = jdbc;
        this.access = access;
        this.cases = cases;
        this.audit = audit;
        this.currentActor = currentActor;
    }

    ActorRef actor() {
        return currentActor.require();
    }

    /** The case the user may see, else 404. */
    void view(UUID caseId) {
        access.requireView(caseId);
    }

    /** The case the user may change: 404 when they cannot see it, 403 when they can see but not change it. */
    ActorRef change(UUID caseId) {
        ActorRef actor = currentActor.require();
        access.requireChange(caseId);
        return actor;
    }

    /** The case of a record of {@code table} ({@code table} is always a constant of the calling service). */
    UUID caseOf(String table, UUID id, String resource) {
        return jdbc.sql("select case_id from " + table + " where id = :id").param("id", id).query(UUID.class)
                .optional().orElseThrow(() -> new NotFoundException(resource));
    }

    /** The case of a judgment (through its proceeding). */
    UUID caseOfJudgment(UUID judgmentId) {
        return jdbc.sql("""
                select p.case_id from judgment j join proceeding p on p.id = j.proceeding_id where j.id = :id""")
                .param("id", judgmentId).query(UUID.class).optional().orElseThrow(() -> new NotFoundException("judgment"));
    }

    UUID caseOfProceeding(UUID proceedingId) {
        return jdbc.sql("select case_id from proceeding where id = :id").param("id", proceedingId).query(UUID.class)
                .optional().orElseThrow(() -> new NotFoundException("proceeding"));
    }

    /** Next sequence number of {@code table} in the case; the case row is locked so two writers never share one. */
    int nextSeq(String table, UUID caseId) {
        jdbc.sql("select id from legal_case where id = :id for update").param("id", caseId).query(UUID.class).list();
        return jdbc.sql("select coalesce(max(seq), 0) + 1 from " + table + " where case_id = :id").param("id", caseId)
                .query(Integer.class).single();
    }

    /** A reference to a record of {@code table} that must belong to the case (a stage, a document, a fact...). */
    void sameCase(String table, UUID id, UUID caseId, String field) {
        if (id != null && !jdbc.sql("select exists (select 1 from " + table + " where id = :id and case_id = :c)")
                .param("id", id).param("c", caseId).query(Boolean.class).single()) {
            throw new FieldErrorException(field, "NotInCase");
        }
    }

    void partyOfCase(UUID partyId, UUID caseId, String field) {
        if (partyId != null && !jdbc.sql("select exists (select 1 from case_party where party_id = :p and case_id = :c)")
                .param("p", partyId).param("c", caseId).query(Boolean.class).single()) {
            throw new FieldErrorException(field, "NotInCase");
        }
    }

    /** A user who exists (a member who prepared a defence). */
    void user(UUID id, String field) {
        if (id != null && !jdbc.sql("select exists (select 1 from app_user where id = :id)").param("id", id)
                .query(Boolean.class).single()) {
            throw new FieldErrorException(field, "Exists");
        }
    }

    void event(UUID caseId, String type, String summary) {
        cases.recordEvent(caseId, type, summary.length() <= EVENT_MAX ? summary : summary.substring(0, EVENT_MAX - 1) + "…");
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    void audit(String action, ActorRef actor, UUID caseId, String reason) {
        audit.recordWithChange(action, actor.id(), actor.username(), "CASE", caseId.toString(), reason);
    }

    static String clip(String text, int max) {
        String flat = text.strip().replaceAll("\\s+", " ");
        return flat.length() <= max ? flat : flat.substring(0, max - 1) + "…";
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
