package com.lawdesk.reports;

import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Rows for report tests, inserted with SQL so every figure is computed from real stored data. Test classes with the
 * same configuration share one database (PostgresTestSupport), so this removes only the rows it inserted, newest
 * first (judgments, then proceedings children before parents, then courts and cases).
 */
final class ReportTestData {

    static final UUID CREATOR = UUID.fromString("00000000-0000-4000-8000-0000000000e5");

    private final JdbcTemplate jdbc;
    private final String marker = "R" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    private final Deque<String[]> created = new ArrayDeque<>();
    private int sequence;

    ReportTestData(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** A prefix unique to this instance, for references and court codes it creates. */
    String marker() {
        return marker;
    }

    UUID insertCase(String title, String type, String status, LocalDate openedOn) {
        return insertCaseWithReference(marker + "-" + (++sequence), title, type, status, openedOn);
    }

    UUID insertCaseWithReference(String referenceNo, String title, String type, String status, LocalDate openedOn) {
        UUID id = jdbc.queryForObject("""
                insert into legal_case (reference_no, title, case_type, opened_on, status, created_by, updated_by)
                values (?, ?, ?, ?, ?, ?, ?) returning id""", UUID.class, referenceNo, title, type, openedOn, status,
                CREATOR, CREATOR);
        created.push(new String[] {"legal_case", id.toString()});
        return id;
    }

    UUID insertCourt(String level) {
        String code = marker + "-" + (++sequence);
        UUID id = jdbc.queryForObject("""
                insert into court (code, name_ar, name_en, level, created_by, updated_by)
                values (?, ?, ?, ?, ?, ?) returning id""", UUID.class, code, "محكمة " + code, "Court " + code, level,
                CREATOR, CREATOR);
        created.push(new String[] {"court", id.toString()});
        return id;
    }

    UUID insertProceeding(UUID caseId, String stage, UUID courtId, LocalDate filingDate, UUID parentId) {
        UUID id = jdbc.queryForObject("""
                insert into proceeding (case_id, stage, court_id, court_case_number, court_case_year, filing_date,
                                        parent_proceeding_id, created_by, updated_by)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?) returning id""", UUID.class, caseId, stage, courtId,
                marker + (++sequence), filingDate.getYear(), filingDate, parentId, CREATOR, CREATOR);
        created.push(new String[] {"proceeding", id.toString()});
        return id;
    }

    UUID insertJudgment(UUID proceedingId, LocalDate date, String outcome, boolean isFinal) {
        UUID id = jdbc.queryForObject("""
                insert into judgment (proceeding_id, judgment_date, outcome, final, created_by, updated_by)
                values (?, ?, ?, ?, ?, ?) returning id""", UUID.class, proceedingId, date, outcome, isFinal, CREATOR,
                CREATOR);
        created.push(new String[] {"judgment", id.toString()});
        return id;
    }

    /** Deletes what this instance inserted, in reverse order of creation, so references are removed first. */
    void deleteCreated() {
        while (!created.isEmpty()) {
            String[] row = created.pop();
            jdbc.update("delete from " + row[0] + " where id = ?::uuid", row[1]);
        }
    }
}
