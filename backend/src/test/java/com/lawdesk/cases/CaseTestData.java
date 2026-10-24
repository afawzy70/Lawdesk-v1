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
        jdbc.update("delete from case_event");
        jdbc.update("delete from legal_case");
        jdbc.update("delete from case_reference_counter");
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
