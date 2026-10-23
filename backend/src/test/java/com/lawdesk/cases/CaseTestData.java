package com.lawdesk.cases;

import org.springframework.jdbc.core.JdbcTemplate;

/** Shared clean-up for Phase 3 tests: test classes with the same configuration share one database. */
final class CaseTestData {

    private CaseTestData() {
    }

    static void deleteAll(JdbcTemplate jdbc) {
        jdbc.update("delete from case_event");
        jdbc.update("delete from legal_case");
        jdbc.update("delete from case_reference_counter");
    }
}
