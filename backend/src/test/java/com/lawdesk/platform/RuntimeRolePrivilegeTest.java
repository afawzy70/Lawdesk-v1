package com.lawdesk.platform;

import static com.lawdesk.platform.DeploymentDatabase.RUNTIME;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The credential the deployed backend holds must not own, change or escalate over the database it uses (T-DB-1 to T-DB-4).
 */
class RuntimeRolePrivilegeTest {

    private static DeploymentDatabase database;
    private static JdbcTemplate admin;

    @BeforeAll
    static void deploy() {
        database = DeploymentDatabase.deploy();
        admin = new JdbcTemplate(database.admin);
    }

    @Test
    void theRuntimeRoleOwnsNothingInTheSchema() {
        assertThat(admin.queryForObject("select pg_get_userbyid(nspowner) from pg_namespace where nspname = 'lawdesk'", String.class))
                .isEqualTo("lawdesk_owner");
        assertThat(admin.queryForList("""
                select c.relname || ' ' || pg_get_userbyid(c.relowner) from pg_class c
                where c.relnamespace = 'lawdesk'::regnamespace and pg_get_userbyid(c.relowner) <> 'lawdesk_owner'
                union all
                select p.proname || ' ' || pg_get_userbyid(p.proowner) from pg_proc p
                where p.pronamespace = 'lawdesk'::regnamespace and pg_get_userbyid(p.proowner) <> 'lawdesk_owner'""", String.class))
                .as("objects not owned by the migration owner").isEmpty();
    }

    @Test
    void theRuntimeRoleCannotAlterDisableReplaceOrCreateAnything() {
        refused("alter table judgment disable trigger judgment_appeal_deadline_runs_from_judgment",
                "alter table judgment disable trigger all",
                "drop trigger judgment_appeal_deadline_runs_from_judgment on judgment",
                "create or replace function archive_purge_guard() returns trigger language plpgsql as 'begin return old; end'",
                "create function probe() returns int language sql as 'select 1'",
                "create table probe (k int)",
                "create temporary table probe (k int)",
                "create schema probe",
                "alter table legal_case add column probe int",
                "alter table legal_case owner to " + RUNTIME,
                "drop table audit_event",
                "truncate audit_event",
                "create index probe on legal_case (title)",
                "create trigger probe before insert on legal_case for each row execute function archive_purge_guard()");
    }

    @Test
    void theRuntimeRoleCannotEscalate() {
        refused("set session_replication_role = replica",
                "set role lawdesk_owner",
                "set session authorization lawdesk_owner",
                "grant lawdesk_owner to " + RUNTIME,
                "alter role " + RUNTIME + " superuser",
                "alter role lawdesk_owner password 'x'",
                "create role probe_role",
                // PostgreSQL always lets a role set defaults for objects it creates itself (it can create none); what matters is
                // that it cannot touch the owner's defaults.
                "alter default privileges for role lawdesk_owner grant select on tables to public",
                "alter default privileges for role lawdesk_owner in schema lawdesk grant all on tables to " + RUNTIME);
        assertThat(admin.queryForObject("""
                select rolsuper or rolcreaterole or rolcreatedb or rolreplication or rolbypassrls from pg_roles where rolname = ?""",
                Boolean.class, RUNTIME)).isFalse();
    }

    @Test
    void theRuntimeRoleCannotChangeTheMigrationHistory() {
        refused("update flyway_schema_history set success = false",
                "delete from flyway_schema_history",
                "insert into flyway_schema_history (installed_rank, description, type, script, installed_by, execution_time, success)"
                        + " values (999999, 'x', 'SQL', 'x', 'x', 0, true)");
        assertThat(new JdbcTemplate(database.as(RUNTIME)).queryForObject("select count(*) from flyway_schema_history", Long.class))
                .as("an older release's Flyway still reads the history").isPositive();
    }

    @Test
    void theRuntimeRoleStillReadsAndWritesApplicationData() {
        JdbcTemplate runtime = new JdbcTemplate(database.as(RUNTIME));
        runtime.update("insert into official_holiday (holiday_on, label, legal_source, created_by, updated_by)"
                + " values (date '2031-01-01', 'probe', 'probe', gen_random_uuid(), gen_random_uuid())");
        assertThat(runtime.update("update official_holiday set label = 'probe 2' where holiday_on = date '2031-01-01'")).isEqualTo(1);
        assertThat(runtime.update("delete from official_holiday where holiday_on = date '2031-01-01'")).isEqualTo(1);
        assertThat(runtime.queryForObject("select nextval('directive_no_seq')", Long.class)).isPositive();
    }

    /** Each statement must be refused for want of privilege (SQLSTATE 42501), nothing else. */
    private void refused(String... statements) {
        List<String> wrong = new java.util.ArrayList<>();
        for (String sql : statements) {
            try (Connection connection = database.as(RUNTIME).getConnection(); Statement statement = connection.createStatement()) {
                connection.setAutoCommit(false);
                statement.execute(sql);
                connection.rollback();
                wrong.add("ALLOWED: " + sql);
            } catch (SQLException e) {
                if (!"42501".equals(e.getSQLState())) {
                    wrong.add(e.getSQLState() + " (not a privilege refusal): " + sql + " -> " + e.getMessage());
                }
            }
        }
        assertThat(wrong).isEmpty();
    }
}
