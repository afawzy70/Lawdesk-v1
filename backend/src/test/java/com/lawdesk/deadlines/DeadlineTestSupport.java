package com.lawdesk.deadlines;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lawdesk.followup.Wp3TestSupport;
import com.lawdesk.security.Permission;

/**
 * Shared setup of the WP4 tests (LD-054). Every rule and holiday is TEST-ONLY data created by the test that needs it; the
 * application has none of its own.
 */
abstract class DeadlineTestSupport extends Wp3TestSupport {

    static final Permission[] SPECIALIST = {Permission.CASE_VIEW, Permission.DEADLINE_VIEW, Permission.DEADLINE_MANAGE,
        Permission.DOCUMENT_VIEW, Permission.DOCUMENT_MANAGE};
    static final Permission[] COMPETENT = {Permission.CASE_VIEW, Permission.DEADLINE_VIEW, Permission.DEADLINE_MANAGE,
        Permission.DEADLINE_APPROVE, Permission.DOCUMENT_VIEW, Permission.DOCUMENT_MANAGE};
    static final Permission[] VIEW_ONLY = {Permission.CASE_VIEW, Permission.DEADLINE_VIEW};

    protected UUID court() {
        return jdbc.queryForObject("""
                insert into court (code, name_ar, name_en, level, created_by, updated_by)
                values ('T' || upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 8)), 'محكمة', 'Court', 'FIRST_INSTANCE',
                        gen_random_uuid(), gen_random_uuid()) returning id""", UUID.class);
    }

    protected UUID proceeding(UUID caseId) {
        return jdbc.queryForObject("""
                insert into proceeding (case_id, stage, court_id, court_case_number, court_case_year, filing_date,
                                        created_by, updated_by)
                values (?, 'FIRST_INSTANCE', ?, '100', 2026, '2026-01-12', gen_random_uuid(), gen_random_uuid()) returning id""",
                UUID.class, caseId, court());
    }

    protected UUID judgment(UUID proceeding, LocalDate on) {
        return jdbc.queryForObject("""
                insert into judgment (proceeding_id, judgment_date, outcome, created_by, updated_by)
                values (?, ?, 'AGAINST', gen_random_uuid(), gen_random_uuid()) returning id""", UUID.class, proceeding, on);
    }

    protected UUID hearing(UUID caseId, UUID proceeding, LocalDate on) {
        return jdbc.queryForObject("""
                insert into hearing (case_id, proceeding_id, hearing_date, purpose, created_by, updated_by)
                values (?, ?, ?, 'PLEADING', gen_random_uuid(), gen_random_uuid()) returning id""", UUID.class, caseId,
                proceeding, on);
    }

    protected UUID task(UUID caseId, UUID assignee) {
        return jdbc.queryForObject("""
                insert into task (case_id, title, assignee_id, created_by, updated_by)
                values (?, 'Collect the file', ?, ?, ?) returning id""", UUID.class, caseId, assignee, assignee, assignee);
    }

    protected UUID document(UUID caseId) {
        UUID id = jdbc.queryForObject("""
                insert into case_document (case_id, title, file_name, size_bytes, sha256, created_by)
                values (?, 'Proof of filing', 'proof.pdf', 4, repeat('a', 64), gen_random_uuid()) returning id""",
                UUID.class, caseId);
        jdbc.update("insert into case_document_content (document_id, content) values (?, ?)", id, new byte[] {1, 2, 3, 4});
        return id;
    }

    /** A TEST-ONLY approved rule: created, then approved by {@code approver}. */
    protected UUID approvedRule(RequestPostProcessor drafter, RequestPostProcessor approver, String code, int days,
            String method, boolean countsStartDay, boolean rollForward, Object closedWeekdays, String effectiveFrom)
            throws Exception {
        UUID id = draftRule(drafter, code, days, method, countsStartDay, rollForward, closedWeekdays, effectiveFrom);
        long version = jdbc.queryForObject("select version from deadline_rule where id = ?", Long.class, id);
        mvc.perform(postJson(approver, "/api/v1/deadline-rules/" + id + "/approve", json("version", version)))
                .andExpect(status().isOk());
        return id;
    }

    protected UUID draftRule(RequestPostProcessor drafter, String code, int days, String method, boolean countsStartDay,
            boolean rollForward, Object closedWeekdays, String effectiveFrom) throws Exception {
        MvcResult result = mvc.perform(postJson(drafter, "/api/v1/deadline-rules",
                json("code", code, "label", "Test-only rule " + code, "periodDays", days, "countMethod", method,
                        "countsStartDay", countsStartDay, "rollForward", rollForward, "closedWeekdays", closedWeekdays,
                        "legalSource", "Test-only source for " + code, "effectiveFrom", effectiveFrom)))
                .andExpect(status().isCreated()).andReturn();
        return idOf(result);
    }

    protected UUID approvedHoliday(RequestPostProcessor drafter, RequestPostProcessor approver, String on, String label)
            throws Exception {
        MvcResult result = mvc.perform(postJson(drafter, "/api/v1/official-holidays",
                json("holidayOn", on, "label", label, "legalSource", "Test-only source")))
                .andExpect(status().isCreated()).andReturn();
        UUID id = idOf(result);
        mvc.perform(postJson(approver, "/api/v1/official-holidays/" + id + "/approve", json("version", 0)))
                .andExpect(status().isOk());
        return id;
    }
}
