package com.lawdesk.assistant;

import java.util.List;
import java.util.UUID;

import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.lawdesk.security.Permission;
import com.lawdesk.wp9.Wp9TestSupport;

/**
 * Shared fixtures of the WP10 tests (LD-059). Everything the assistant reads is inserted straight into the database, so each
 * test states exactly which words exist in which case; the API (and nothing else) is what is under test.
 */
abstract class AssistantTestSupport extends Wp9TestSupport {

    /** What a legal member holds: the assistant and the read permissions of what it quotes. */
    static final Permission[] MEMBER = {Permission.ASSISTANT_USE, Permission.CASE_VIEW, Permission.SEARCH_ALL, Permission.CLAIM_VIEW,
        Permission.DEFENSE_VIEW, Permission.HEARING_VIEW, Permission.JUDGMENT_VIEW, Permission.DOCUMENT_VIEW, Permission.TASK_VIEW,
        Permission.LIBRARY_VIEW, Permission.DEADLINE_VIEW};

    /** Every table the assistant must leave alone, with a count of its rows. */
    static final List<String> RECORD_TABLES = List.of("legal_case", "case_note", "task", "hearing", "judgment", "claim", "defense",
            "case_document", "case_party", "case_event", "notification", "case_grant", "library_judgment", "settlement", "directive");

    protected RequestPostProcessor member(UUID user) {
        return outsider(user, MEMBER);
    }

    protected MvcResult ask(RequestPostProcessor who, String body, int status) throws Exception {
        return mvc.perform(postJson(who, "/api/v1/assistant/ask", body))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().is(status)).andReturn();
    }

    protected String question(UUID caseId, String text) {
        return json("task", "QUESTION", "caseId", caseId == null ? null : caseId.toString(), "question", text);
    }

    protected UUID claim(UUID caseId, int seq, String text) {
        return jdbc.queryForObject("""
                insert into claim (case_id, seq, text, kind, submitted_on, created_by, updated_by)
                values (?, ?, ?, 'PRIMARY', '2026-02-10', gen_random_uuid(), gen_random_uuid()) returning id""", UUID.class, caseId,
                seq, text);
    }

    protected UUID defense(UUID caseId, int seq, String title, String argument) {
        return jdbc.queryForObject("""
                insert into defense (case_id, seq, title, kind, argument, legal_basis, raised_on, created_by, updated_by)
                values (?, ?, ?, 'SUBSTANTIVE', ?, 'المادة 12 من القانون المدني', '2026-02-20', gen_random_uuid(), gen_random_uuid())
                returning id""", UUID.class, caseId, seq, title, argument);
    }

    /** An archived document with a stored current version and the text read from it ({@code status} as the archive records it). */
    protected UUID documentWithText(UUID caseId, String title, String text, String status) {
        UUID doc = document(caseId, title, title.replace(' ', '-') + ".pdf");
        jdbc.update("""
                insert into document_version (id, document_id, version_no, file_name, media_type, size_bytes, sha256, uploaded_by)
                values (?, ?, 1, ?, 'application/pdf', 10, repeat('a', 64), gen_random_uuid())""", doc, doc, title + ".pdf");
        jdbc.update("""
                insert into document_text (version_id, document_id, status, method, text)
                values (?, ?, ?, ?, ?)""", doc, doc, status, "EXTRACTED".equals(status) ? "PDF_TEXT" : "OCR", text);
        return doc;
    }

    protected long queryLogRows(UUID user) {
        Long n = jdbc.queryForObject("select count(*) from assistant_query_log where user_id = ?", Long.class, user);
        return n == null ? 0 : n;
    }

    protected List<Long> recordCounts() {
        return RECORD_TABLES.stream().map(t -> jdbc.queryForObject("select count(*) from " + t, Long.class)).toList();
    }
}
