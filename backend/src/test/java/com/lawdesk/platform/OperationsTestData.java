package com.lawdesk.platform;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Removes the cases a Phase 5 test created, and everything that hangs off them, in foreign-key order. Spring caches one
 * database for every test class with the same configuration, and {@code CaseSearchApiTest} inserts fixed reference
 * numbers (the first generated one is {@code LD-2026-00001}); a case left behind by another class would make it fail
 * depending on the order the classes run in. The reference counter is left alone on purpose: resetting it would hand
 * the next test the number {@code 00001} again.
 */
public final class OperationsTestData {

    private OperationsTestData() {
    }

    public static void deleteAll(JdbcTemplate jdbc) {
        // Phase 5, children before parents.
        jdbc.update("delete from case_note");
        jdbc.update("delete from case_document");
        jdbc.update("delete from case_document_quota");
        jdbc.update("delete from task");
        jdbc.update("delete from expert_session");
        jdbc.update("delete from expert_referral");
        jdbc.update("delete from expert");
        jdbc.update("delete from hearing where adjourned_from_id is not null");
        jdbc.update("delete from hearing");
        // Phase 4: a cassation attacks an appeal, an appeal a first-instance proceeding.
        jdbc.update("delete from special_decision");
        jdbc.update("delete from judgment");
        jdbc.update("delete from proceeding where stage = 'CASSATION'");
        jdbc.update("delete from proceeding where stage = 'APPEAL'");
        jdbc.update("delete from proceeding");
        jdbc.update("delete from court");
        // Phase 3.
        jdbc.update("delete from case_party");
        jdbc.update("delete from case_event");
        jdbc.update("delete from legal_case");
    }
}
