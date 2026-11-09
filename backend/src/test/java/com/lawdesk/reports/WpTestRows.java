package com.lawdesk.reports;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

/** Rows of the other modules that the dashboard indicators and report families read, inserted with SQL (WP8, LD-058). */
final class WpTestRows {

    private final JdbcTemplate jdbc;
    private final ReportTestData data;
    private int sequence;

    WpTestRows(JdbcTemplate jdbc, ReportTestData data) {
        this.jdbc = jdbc;
        this.data = data;
    }

    void primaryMember(UUID caseId, UUID member) {
        jdbc.update("update legal_case set primary_member_id = ? where id = ?", member, caseId);
    }

    void alternateMember(UUID caseId, UUID member) {
        jdbc.update("update legal_case set alternate_member_id = ? where id = ?", member, caseId);
    }

    void amounts(UUID caseId, String claimed, String impact) {
        jdbc.update("update legal_case set claimed_amount = cast(? as numeric), financial_impact = ?, "
                + "financial_amount = cast(? as numeric) where id = ?", claimed, impact == null ? null : "YES", impact, caseId);
    }

    void restricted(UUID caseId) {
        jdbc.update("update legal_case set confidentiality = 'RESTRICTED' where id = ?", caseId);
    }

    UUID involvement(UUID caseId, String sector, String department, String section) {
        return data.track("case_org_involvement", jdbc.queryForObject("""
                insert into case_org_involvement (case_id, is_primary, sector_name_snapshot, department_name_snapshot,
                    section_name_snapshot, created_by, updated_by) values (?, true, ?, ?, ?, ?, ?) returning id""",
                UUID.class, caseId, sector, department, section, ReportTestData.CREATOR, ReportTestData.CREATOR));
    }

    UUID risk(UUID caseId, int version, String level, String potentialLoss) {
        return data.track("risk_assessment", jdbc.queryForObject("""
                insert into risk_assessment (case_id, version_no, level, level_reasons, potential_loss, trigger_kind,
                    assessed_by) values (?, ?, ?, 'reasons', cast(? as numeric), 'MANUAL', ?) returning id""", UUID.class,
                caseId, version, level, potentialLoss, ReportTestData.CREATOR));
    }

    UUID link(UUID caseId, UUID other, String kind, String status) {
        return data.track("case_link", jdbc.queryForObject("""
                insert into case_link (case_id, other_case_id, kind, reason, status, created_by, updated_by)
                values (?, ?, ?, 'same subject', ?, ?, ?) returning id""", UUID.class, caseId, other, kind, status,
                ReportTestData.CREATOR, ReportTestData.CREATOR));
    }

    UUID infoRequest(UUID caseId, String status, LocalDate due) {
        String no = "IR-" + data.marker() + "-" + (++sequence);
        return data.track("info_request", jdbc.queryForObject("""
                insert into info_request (case_id, request_no, unit_name_snapshot, subject, status, due_on, sent_on,
                    created_by, updated_by) values (?, ?, 'Unit A', 'documents', ?, ?, ?, ?, ?) returning id""",
                UUID.class, caseId, no, status, due, due == null ? null : due.minusDays(30), ReportTestData.CREATOR,
                ReportTestData.CREATOR));
    }

    UUID reply(UUID requestId, UUID caseId, LocalDate received, String completeness) {
        return data.track("info_reply", jdbc.queryForObject("""
                insert into info_reply (request_id, case_id, received_on, completeness, created_by)
                values (?, ?, ?, ?, ?) returning id""", UUID.class, requestId, caseId, received, completeness,
                ReportTestData.CREATOR));
    }

    UUID hearing(UUID caseId, UUID proceedingId, LocalDate date, String status, String purpose, String kind,
            String adjournmentReason, UUID responsible) {
        boolean scheduled = "SCHEDULED".equals(status);
        return data.track("hearing", jdbc.queryForObject("""
                insert into hearing (case_id, proceeding_id, hearing_date, purpose, kind, status, adjournment_reason,
                    responsible_member_id, result_recorded_at, result_recorded_by, created_by, updated_by)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id""", UUID.class, caseId, proceedingId, date,
                purpose, kind, status, adjournmentReason, responsible,
                scheduled ? null : java.sql.Timestamp.from(java.time.Instant.now()),
                scheduled ? null : ReportTestData.CREATOR, ReportTestData.CREATOR, ReportTestData.CREATOR));
    }

    UUID judgmentWithClaim(UUID caseId, UUID judgmentId, String result, String awarded, int seq) {
        UUID claim = data.track("claim", jdbc.queryForObject("""
                insert into claim (case_id, seq, text, kind, created_by, updated_by) values (?, ?, 'text', 'PRIMARY', ?, ?)
                returning id""", UUID.class, caseId, seq, ReportTestData.CREATOR, ReportTestData.CREATOR));
        return claimResult(claim, judgmentId, result, awarded);
    }

    UUID claimResult(UUID claimId, UUID judgmentId, String result, String awarded) {
        return data.track("claim_result", jdbc.queryForObject("""
                insert into claim_result (judgment_id, claim_id, result, awarded_amount, created_by, updated_by)
                values (?, ?, ?, cast(? as numeric), ?, ?) returning id""", UUID.class, judgmentId, claimId, result,
                awarded, ReportTestData.CREATOR, ReportTestData.CREATOR));
    }

    UUID evidence(UUID caseId, String role, boolean withDocument) {
        return data.track("evidence_item", jdbc.queryForObject("""
                insert into evidence_item (case_id, name, role, created_by, updated_by) values (?, 'item', ?, ?, ?)
                returning id""", UUID.class, caseId, role, ReportTestData.CREATOR, ReportTestData.CREATOR));
    }

    UUID directive(UUID caseId, UUID addressee, String status, LocalDate date, LocalDate due) {
        String no = "DIR-" + data.marker() + "-" + (++sequence);
        return data.track("directive", jdbc.queryForObject("""
                insert into directive (directive_no, case_id, title, directive_date, source, addressee_user_id, due_date,
                    status, completed_at, created_by, updated_by) values (?, ?, 't', ?, 'src', ?, ?, ?, ?, ?, ?)
                returning id""", UUID.class, no, caseId, date, addressee, due, status,
                "DONE".equals(status) ? java.sql.Timestamp.from(java.time.Instant.now()) : null,
                ReportTestData.CREATOR, ReportTestData.CREATOR));
    }
}
