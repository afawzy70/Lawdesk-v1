package com.lawdesk.wp6;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

import com.lawdesk.platform.PostgresTestSupport;

/**
 * LD-056 (docs/design/LD-DOC-03 section 3): the task migration (V720) is applied to a database that already holds tasks
 * written the way the previous release wrote them. No row is lost or rewritten except the two renamed statuses; the
 * mapping table is documented, complete and reversible; writers that still use the old words keep working.
 */
@SpringBootTest
class TaskStatusMigrationTest extends PostgresTestSupport {

    @Autowired
    private Flyway flyway;

    private Flyway configured(DataSource database, MigrationVersion target) {
        return Flyway.configure().configuration(flyway.getConfiguration()).dataSource(database)
                .locations("classpath:db/migration").target(target).load();
    }

    private UUID task(JdbcTemplate jdbc, UUID caseId, String title, String status, boolean completed) {
        return jdbc.queryForObject("""
                insert into task (case_id, title, description, assignee_id, due_date, priority, status, completed_at,
                                  created_by, updated_by)
                values (?, ?, 'Legacy description', gen_random_uuid(), date '2026-12-01', 'HIGH', ?,
                        case when ? then timestamptz '2026-11-01 10:00+00' end, gen_random_uuid(), gen_random_uuid())
                returning id""", UUID.class, caseId, title, status, completed);
    }

    @Test
    void existingTasksSurviveWithTheirStatusesMappedAndNothingElseChanged() {
        DataSource database = freshDatabase();
        configured(database, MigrationVersion.fromVersion("665")).migrate();
        JdbcTemplate jdbc = new JdbcTemplate(database);
        UUID caseId = jdbc.queryForObject("""
                insert into legal_case (reference_no, title, case_type, opened_on, created_by, updated_by)
                values ('LD-2025-00001', 'Existing case', 'CIVIL', date '2025-01-01', gen_random_uuid(), gen_random_uuid())
                returning id""", UUID.class);
        UUID open = task(jdbc, caseId, "open", "OPEN", false);
        UUID started = task(jdbc, caseId, "started", "IN_PROGRESS", false);
        UUID done = task(jdbc, caseId, "done", "DONE", true);
        UUID cancelled = task(jdbc, caseId, "cancelled", "CANCELLED", false);
        List<Map<String, Object>> before = jdbc.queryForList("""
                select id, case_id, title, description, assignee_id, due_date, priority, completed_at, version,
                       created_at, created_by, updated_at, updated_by from task order by title""");

        var result = Flyway.configure().configuration(flyway.getConfiguration()).dataSource(database)
                .locations("classpath:db/migration").load().migrate();

        assertThat(result.success).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from task", Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForList("""
                select id, case_id, title, description, assignee_id, due_date, priority, completed_at, version,
                       created_at, created_by, updated_at, updated_by from task order by title""")).isEqualTo(before);
        assertThat(jdbc.queryForObject("select status from task where id = ?", String.class, open)).isEqualTo("NEW");
        assertThat(jdbc.queryForObject("select status from task where id = ?", String.class, started)).isEqualTo("IN_PROGRESS");
        assertThat(jdbc.queryForObject("select status from task where id = ?", String.class, done)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("select status from task where id = ?", String.class, cancelled)).isEqualTo("CANCELLED");
        // The new columns start empty: nothing is invented for old tasks.
        assertThat(jdbc.queryForObject("""
                select count(*) from task where starts_on is not null or instructions is not null
                    or delay_reason is not null or status_before_late is not null""", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from task_history", Integer.class)).isZero();
    }

    @Test
    void theMappingTableIsCompleteAndTakesEveryNewStatusBackToAWordThePreviousReleaseReads() {
        DataSource database = freshDatabase();
        configured(database, MigrationVersion.LATEST).migrate();
        JdbcTemplate jdbc = new JdbcTemplate(database);

        Map<String, String> map = new java.util.TreeMap<>();
        jdbc.queryForList("select status, legacy_status from task_status_map")
                .forEach(row -> map.put((String) row.get("status"), (String) row.get("legacy_status")));
        assertThat(map).containsExactlyInAnyOrderEntriesOf(Map.of("NEW", "OPEN", "IN_PROGRESS", "IN_PROGRESS",
                "WAITING_DOCUMENTS", "IN_PROGRESS", "WAITING_OTHER", "IN_PROGRESS", "COMPLETED", "DONE", "LATE", "OPEN",
                "CANCELLED", "CANCELLED"));
        // Every status the table of tasks accepts is in the map, and the legacy words round-trip for the exact ones.
        assertThat(jdbc.queryForList("select status from task_status_map where exact order by status", String.class))
                .containsExactly("CANCELLED", "COMPLETED", "IN_PROGRESS", "NEW");
        assertThat(jdbc.queryForObject("select count(distinct legacy_status) from task_status_map", Integer.class)).isEqualTo(4);
        for (String status : map.keySet()) {
            UUID id = jdbc.queryForObject("""
                    insert into task (title, assignee_id, status, completed_at, status_before_late, created_by, updated_by)
                    values ('t', gen_random_uuid(), ?, case when ? = 'COMPLETED' then now() end,
                            case when ? = 'LATE' then 'NEW' end, gen_random_uuid(), gen_random_uuid()) returning id""",
                    UUID.class, status, status, status);
            assertThat(jdbc.queryForObject("""
                    select m.legacy_status from task t join task_status_map m on m.status = t.status where t.id = ?""",
                    String.class, id)).isEqualTo(map.get(status));
        }
        assertThat(jdbc.queryForObject("select count(*) from task", Integer.class)).isEqualTo(map.size());
    }

    @Test
    void writersThatStillUseTheOldWordsAreStoredUnderTheNewNames() {
        DataSource database = freshDatabase();
        configured(database, MigrationVersion.LATEST).migrate();
        JdbcTemplate jdbc = new JdbcTemplate(database);
        jdbc.update("""
                insert into task (title, assignee_id, status, created_by, updated_by)
                values ('old client', gen_random_uuid(), 'OPEN', gen_random_uuid(), gen_random_uuid())""");
        jdbc.update("""
                insert into task (title, assignee_id, status, completed_at, created_by, updated_by)
                values ('old client done', gen_random_uuid(), 'DONE', now(), gen_random_uuid(), gen_random_uuid())""");
        assertThat(jdbc.queryForList("select status from task order by title", String.class))
                .containsExactly("NEW", "COMPLETED");
        jdbc.update("update task set status = 'OPEN', completed_at = null where title = 'old client done'");
        assertThat(jdbc.queryForList("select status from task where title = 'old client done'", String.class))
                .containsExactly("NEW");
        // The rules of the table still hold: an unknown word, a late task that forgot what it was, a completed one
        // without its completion time, a start after the due date.
        assertThatThrownBy(() -> jdbc.update("""
                insert into task (title, assignee_id, status, created_by, updated_by)
                values ('x', gen_random_uuid(), 'WAITING', gen_random_uuid(), gen_random_uuid())"""))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                insert into task (title, assignee_id, status, created_by, updated_by)
                values ('x', gen_random_uuid(), 'LATE', gen_random_uuid(), gen_random_uuid())"""))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                insert into task (title, assignee_id, status, created_by, updated_by)
                values ('x', gen_random_uuid(), 'COMPLETED', gen_random_uuid(), gen_random_uuid())"""))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                insert into task (title, assignee_id, starts_on, due_date, created_by, updated_by)
                values ('x', gen_random_uuid(), date '2026-12-02', date '2026-12-01', gen_random_uuid(), gen_random_uuid())"""))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
