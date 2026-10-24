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

    /** A hearing; a status other than SCHEDULED carries the result columns its check constraint requires. */
    UUID insertHearing(UUID caseId, UUID proceedingId, LocalDate date, String status) {
        boolean scheduled = "SCHEDULED".equals(status);
        UUID id = jdbc.queryForObject("""
                insert into hearing (case_id, proceeding_id, hearing_date, purpose, status, result_recorded_at,
                                     result_recorded_by, created_by, updated_by)
                values (?, ?, ?, 'PLEADING', ?, ?, ?, ?, ?) returning id""", UUID.class, caseId, proceedingId, date,
                status, scheduled ? null : java.sql.Timestamp.from(java.time.Instant.now()),
                scheduled ? null : CREATOR, CREATOR, CREATOR);
        created.push(new String[] {"hearing", id.toString()});
        return id;
    }

    /** A task assigned to {@code assignee}; a done task has its completion time. */
    UUID insertTask(UUID caseId, UUID assignee, String title, LocalDate due, String status) {
        UUID id = jdbc.queryForObject("""
                insert into task (case_id, title, assignee_id, due_date, status, completed_at, created_by, updated_by)
                values (?, ?, ?, ?, ?, ?, ?, ?) returning id""", UUID.class, caseId, title, assignee, due, status,
                "DONE".equals(status) ? java.sql.Timestamp.from(java.time.Instant.now()) : null, CREATOR, CREATOR);
        created.push(new String[] {"task", id.toString()});
        return id;
    }

    /** A user account named {@code displayName}, enabled; it holds no role. */
    UUID insertUser(String displayName) {
        String username = (marker + "-" + (++sequence)).toLowerCase();
        UUID id = jdbc.queryForObject("""
                insert into app_user (username, email, display_name, password_hash, enabled, created_by, updated_by)
                values (?, ?, ?, 'x', true, ?, ?) returning id""", UUID.class, username, username + "@example.invalid",
                displayName, CREATOR, CREATOR);
        created.push(new String[] {"app_user", id.toString()});
        return id;
    }

    UUID insertExpert(String name, String specialty) {
        UUID id = jdbc.queryForObject("""
                insert into expert (name, specialty, created_by, updated_by) values (?, ?, ?, ?) returning id""",
                UUID.class, name, specialty, CREATOR, CREATOR);
        created.push(new String[] {"expert", id.toString()});
        return id;
    }

    /** A referral of the case's proceeding to an expert; a submitted report is dated on its due day. */
    UUID insertReferral(UUID caseId, UUID proceedingId, UUID expertId, LocalDate referred, LocalDate due,
            String status) {
        UUID id = jdbc.queryForObject("""
                insert into expert_referral (case_id, proceeding_id, expert_id, referral_date, report_due_date, status,
                                             report_submitted_date, created_by, updated_by)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?) returning id""", UUID.class, caseId, proceedingId, expertId,
                referred, due, status, "REPORT_SUBMITTED".equals(status) ? due : null, CREATOR, CREATOR);
        created.push(new String[] {"expert_referral", id.toString()});
        return id;
    }

    /** Inserts {@code count} civil open cases at once, referenced {@code <marker>-BULK-<n>}; they have no proceedings. */
    void insertBulkCases(int count) {
        jdbc.update("""
                insert into legal_case (reference_no, title, case_type, opened_on, status, created_by, updated_by)
                select ? || '-BULK-' || n, 'Bulk case ' || n, 'CIVIL', date '2020-01-01', 'OPEN', ?, ?
                from generate_series(1, ?) as n""", marker, CREATOR, CREATOR, count);
    }

    /** Deletes what this instance inserted, in reverse order of creation, so references are removed first. */
    void deleteCreated() {
        while (!created.isEmpty()) {
            String[] row = created.pop();
            jdbc.update("delete from " + row[0] + " where id = ?::uuid", row[1]);
        }
        jdbc.update("delete from legal_case where reference_no like ?", marker + "-BULK-%");
    }
}
