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
        // LD-053 (WP3), children before parents: notifications, information requests, risk assessments and case links.
        jdbc.update("delete from notification");
        jdbc.update("delete from info_follow_up");
        jdbc.update("delete from info_reply");
        jdbc.update("delete from info_request");
        jdbc.update("delete from risk_remediation");
        jdbc.update("delete from risk_assessment_element");
        jdbc.update("delete from risk_assessment");
        jdbc.update("delete from case_link");
        // LD-050: access records hang off cases, users and the organisation tree.
        // LD-052: a library judgment used in a case hangs off the case.
        jdbc.update("delete from case_precedent_link");
        jdbc.update("delete from case_duplicate_hint");
        jdbc.update("delete from case_party_counsel");
        jdbc.update("delete from case_org_involvement");
        jdbc.update("delete from case_grant");
        jdbc.update("delete from access_scope where subject_type = 'USER'");
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
        jdbc.update("update app_user set org_unit_id = null where org_unit_id is not null");
        jdbc.update("delete from org_unit");
    }
}
