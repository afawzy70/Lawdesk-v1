package com.lawdesk.wp9;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;

import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.lawdesk.followup.Wp3TestSupport;

/**
 * Shared fixtures of the WP9 tests (LD-057): parties, documents, hearings, judgments, tasks, notes and library judgments
 * inserted directly, so each test states in SQL exactly what exists and the API is what is under test.
 */
public abstract class Wp9TestSupport extends Wp3TestSupport {

    @AfterEach
    void removeWp9Data() {
        jdbc.update("delete from library_principle");
        jdbc.update("delete from library_judgment_relation");
        jdbc.update("delete from library_judgment_file");
        jdbc.update("delete from library_judgment_class");
        jdbc.update("delete from library_judgment");
    }

    protected MockHttpServletRequestBuilder delete(RequestPostProcessor actor, String path) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(path).with(actor).with(csrf());
    }

    protected UUID party(String name, String kind, String nationalId, boolean confidential, String phone) {
        return jdbc.queryForObject("""
                insert into party (kind, name, national_id, confidential, phone, created_by, updated_by)
                values (?, ?, ?, ?, ?, gen_random_uuid(), gen_random_uuid()) returning id""", UUID.class, kind, name,
                nationalId, confidential, phone);
    }

    protected void onCase(UUID caseId, UUID partyId, String role) {
        jdbc.update("""
                insert into case_party (case_id, party_id, role, created_by) values (?, ?, ?, gen_random_uuid())""",
                caseId, partyId, role);
    }

    protected UUID document(UUID caseId, String title, String fileName) {
        return jdbc.queryForObject("""
                insert into case_document (case_id, title, file_name, size_bytes, sha256, created_by)
                values (?, ?, ?, 10, repeat('a', 64), gen_random_uuid()) returning id""", UUID.class, caseId, title,
                fileName);
    }

    protected UUID proceeding(UUID caseId, String number) {
        UUID court = jdbc.query("select id from court where code = 'WP9-COURT'", rs -> rs.next()
                ? rs.getObject(1, UUID.class) : null);
        if (court == null) {
            court = jdbc.queryForObject("""
                    insert into court (code, name_ar, name_en, level, created_by, updated_by)
                    values ('WP9-COURT', 'محكمة الاختبار', 'Test court', 'FIRST_INSTANCE', gen_random_uuid(), gen_random_uuid())
                    returning id""", UUID.class);
        }
        return jdbc.queryForObject("""
                insert into proceeding (case_id, stage, court_id, court_case_number, court_case_year, filing_date,
                                        created_by, updated_by)
                values (?, 'FIRST_INSTANCE', ?, ?, 2026, '2026-02-01', gen_random_uuid(), gen_random_uuid()) returning id""",
                UUID.class, caseId, court, number);
    }

    protected UUID hearing(UUID caseId, UUID proceedingId, String date, String summary, String decision) {
        return jdbc.queryForObject("""
                insert into hearing (case_id, proceeding_id, hearing_date, purpose, proceedings_summary, notes,
                                     created_by, updated_by)
                values (?, ?, ?::date, 'PLEADING', ?, ?, gen_random_uuid(), gen_random_uuid()) returning id""", UUID.class,
                caseId, proceedingId, date, summary, decision);
    }

    protected UUID judgment(UUID proceedingId, String summary) {
        return jdbc.queryForObject("""
                insert into judgment (proceeding_id, judgment_date, outcome, summary, created_by, updated_by)
                values (?, '2026-05-01', 'PARTIAL', ?, gen_random_uuid(), gen_random_uuid()) returning id""", UUID.class,
                proceedingId, summary);
    }

    protected UUID note(UUID caseId, String body) {
        return jdbc.queryForObject("""
                insert into case_note (case_id, body, created_by, updated_by)
                values (?, ?, gen_random_uuid(), gen_random_uuid()) returning id""", UUID.class, caseId, body);
    }

    protected UUID task(UUID caseId, String title, String description, UUID assignee) {
        return jdbc.queryForObject("""
                insert into task (case_id, title, description, assignee_id, created_by, updated_by)
                values (?, ?, ?, ?, ?, ?) returning id""", UUID.class, caseId, title, description, assignee, assignee,
                assignee);
    }

    protected UUID libraryJudgment(String subject, String summary) {
        return jdbc.queryForObject("""
                insert into library_judgment (court_name, subject, summary, source_reference, verification_note,
                                              created_by, updated_by)
                values ('محكمة التمييز', ?, ?, 'مجموعة اختبارية', 'نسخة اصطناعية', gen_random_uuid(), gen_random_uuid())
                returning id""", UUID.class, subject, summary);
    }
}
