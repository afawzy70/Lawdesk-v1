package com.lawdesk.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * QAD-033: the database-integrity migrations (V406, V407) are applied to a database that already holds Phase 5 rows
 * written the way the API writes them (through V405, the state of an already deployed UAT). They must not fail, must
 * not rewrite or reject a single existing row (the system column xmin of every row is unchanged), and must start
 * protecting the data from the first statement on.
 */
@SpringBootTest
class MigrationPhase5IntegrityTest extends PostgresTestSupport {

    private static final String[] TABLES = {"legal_case", "court", "proceeding", "hearing", "expert", "expert_referral",
            "expert_session"};

    @Autowired
    private Flyway flyway;

    private Flyway configured(DataSource database, MigrationVersion target) {
        return Flyway.configure().configuration(flyway.getConfiguration()).dataSource(database)
                .locations("classpath:db/migration").target(target).load();
    }

    private Map<String, List<String>> snapshot(JdbcTemplate jdbc) {
        Map<String, List<String>> rows = new java.util.TreeMap<>();
        for (String table : TABLES) {
            rows.put(table, jdbc.queryForList("select xmin::text || '|' || t::text from " + table + " t order by 1",
                    String.class));
        }
        return rows;
    }

    @Test
    void theIntegrityMigrationsLeaveExistingPhase5RowsUntouchedAndStartProtectingThem() {
        DataSource database = freshDatabase();
        configured(database, MigrationVersion.fromVersion("405")).migrate();
        JdbcTemplate jdbc = new JdbcTemplate(database);
        UUID actor = UUID.randomUUID();

        UUID caseId = jdbc.queryForObject("""
                insert into legal_case (reference_no, title, case_type, opened_on, created_by, updated_by)
                values ('LD-2025-00001', 'Existing case', 'CIVIL', date '2025-01-01', ?, ?) returning id""",
                UUID.class, actor, actor);
        UUID court = jdbc.queryForObject("""
                insert into court (code, name_ar, name_en, level, created_by, updated_by)
                values ('MIG-1', 'محكمة', 'Court', 'FIRST_INSTANCE', ?, ?) returning id""", UUID.class, actor, actor);
        UUID proceeding = jdbc.queryForObject("""
                insert into proceeding (case_id, stage, court_id, court_case_number, court_case_year, filing_date,
                                        created_by, updated_by)
                values (?, 'FIRST_INSTANCE', ?, '1', 2025, date '2025-01-10', ?, ?) returning id""", UUID.class,
                caseId, court, actor, actor);
        UUID hearing = jdbc.queryForObject("""
                insert into hearing (case_id, proceeding_id, hearing_date, purpose, created_by, updated_by)
                values (?, ?, date '2025-01-10', 'PLEADING', ?, ?) returning id""", UUID.class, caseId, proceeding,
                actor, actor);
        jdbc.update("""
                insert into hearing (case_id, proceeding_id, hearing_date, purpose, adjourned_from_id, created_by,
                                     updated_by)
                values (?, ?, date '2025-02-01', 'PLEADING', ?, ?, ?)""", caseId, proceeding, hearing, actor, actor);
        UUID expert = jdbc.queryForObject("""
                insert into expert (name, specialty, created_by, updated_by) values ('Expert', 'Engineering', ?, ?)
                returning id""", UUID.class, actor, actor);
        UUID referral = jdbc.queryForObject("""
                insert into expert_referral (case_id, proceeding_id, expert_id, referral_date, created_by, updated_by)
                values (?, ?, ?, date '2025-01-12', ?, ?) returning id""", UUID.class, caseId, proceeding, expert,
                actor, actor);
        jdbc.update("""
                insert into expert_session (referral_id, session_date, created_by) values (?, date '2025-01-12', ?)""",
                referral, actor);
        Map<String, List<String>> before = snapshot(jdbc);

        var result = configured(database, MigrationVersion.LATEST).migrate();

        assertThat(result.success).isTrue();
        assertThat(result.migrations).extracting(m -> m.version).contains("406", "407");
        assertThat(snapshot(jdbc)).isEqualTo(before);

        // Protection starts at once: the invalid statements are refused, the valid ones are not.
        assertThatThrownBy(() -> jdbc.update("update proceeding set filing_date = date '2025-01-11' where id = ?",
                proceeding)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update proceeding set filing_date = date '2025-01-13' where id = ?",
                proceeding)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update hearing set hearing_date = date '2025-01-09' where id = ?",
                hearing)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update proceeding set case_id = ? where id = ?", UUID.randomUUID(),
                proceeding)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update expert_referral set referral_date = date '2025-01-13' where id = ?",
                referral)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(snapshot(jdbc)).isEqualTo(before);
        assertThat(jdbc.update("update proceeding set filing_date = date '2025-01-10' where id = ?", proceeding))
                .isOne();
        assertThat(jdbc.queryForObject("select filing_date from proceeding where id = ?", LocalDate.class, proceeding))
                .isEqualTo(LocalDate.parse("2025-01-10"));
    }
}
