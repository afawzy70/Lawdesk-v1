package com.lawdesk.cases;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

/** Shared fixtures and clean-up for Phase 3 tests: test classes with the same configuration share one database. */
final class CaseTestData {

    static final UUID CREATOR = UUID.fromString("00000000-0000-4000-8000-0000000000c0");

    private CaseTestData() {
    }

    static void deleteAll(JdbcTemplate jdbc) {
        // LD-057 (WP9), children before parents: settlements, directives, templates and the lists members added to.
        jdbc.update("delete from settlement_document");
        jdbc.update("delete from settlement_history");
        jdbc.update("delete from settlement");
        jdbc.update("delete from directive");
        jdbc.update("delete from form_template_version");
        jdbc.update("delete from form_template");
        jdbc.update("delete from managed_list_value where origin = 'PROPOSED'");
        jdbc.update("update managed_list_value set active = true, label_en = null where origin = 'DOCUMENT'");
        jdbc.update("delete from case_precedent_link");
        jdbc.update("delete from case_duplicate_hint");
        jdbc.update("delete from case_party_counsel");
        // LD-053 (WP3): records that hang off cases, children before parents.
        // LD-054 (WP4): deadlines hang off cases and documents; the test-only rules, holidays and lead times go too.
        jdbc.update("delete from deadline");
        jdbc.update("delete from deadline_reminder_setting");
        jdbc.update("delete from official_holiday");
        jdbc.update("delete from deadline_rule");
        jdbc.update("delete from notification");
        // LD-056 (WP6): calendar dates and notification preferences, task history (it goes with its task).
        jdbc.update("delete from calendar_date");
        jdbc.update("delete from notification_preference");
        jdbc.update("delete from info_follow_up");
        jdbc.update("delete from info_reply");
        jdbc.update("delete from info_request");
        jdbc.update("delete from risk_remediation");
        jdbc.update("delete from risk_assessment_element");
        jdbc.update("delete from risk_assessment");
        jdbc.update("delete from case_link");
        com.lawdesk.platform.PleadingsTestData.deleteAll(jdbc);
        jdbc.update("delete from case_org_involvement");
        jdbc.update("delete from case_grant");
        jdbc.update("delete from case_party");
        jdbc.update("delete from party");
        jdbc.update("delete from case_event");
        jdbc.update("delete from legal_case");
        jdbc.update("delete from case_reference_counter");
    }

    /** Inserts a party directly, bypassing the API, and returns its id. */
    static UUID insertParty(JdbcTemplate jdbc, String kind, String name, String nationalId) {
        return jdbc.queryForObject("""
                insert into party (kind, name, national_id, created_by, updated_by)
                values (?, ?, ?, ?, ?) returning id""", UUID.class, kind, name, nationalId, CREATOR, CREATOR);
    }

    /** Inserts a case directly, bypassing the API, and returns its id. */
    static UUID insertCase(JdbcTemplate jdbc, String referenceNo, String title, CaseType type, CaseStatus status,
            LocalDate openedOn) {
        return jdbc.queryForObject("""
                insert into legal_case (reference_no, title, case_type, opened_on, status, created_by, updated_by)
                values (?, ?, ?, ?, ?, ?, ?) returning id""", UUID.class, referenceNo, title, type.name(),
                openedOn, status.name(), CREATOR, CREATOR);
    }
}
