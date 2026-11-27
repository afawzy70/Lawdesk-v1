package com.lawdesk.platform;

import static com.lawdesk.platform.DeploymentDatabase.RUNTIME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * An existing installation (objects owned by the runtime role) is moved to the split model without losing or changing a row,
 * the move can be repeated, and deploy/postgres/audit.sql refuses any drift (T-DB-8, T-DB-12; handoff criteria 1, 2, 6).
 */
class DatabaseOwnershipTransferTest {

    @Test
    void transferringAPopulatedInstallationChangesNoDataAndCanBeRepeated() throws Exception {
        DeploymentDatabase database = DeploymentDatabase.legacy();
        JdbcTemplate admin = new JdbcTemplate(database.admin);
        UUID actor = UUID.randomUUID();
        JdbcTemplate runtime = new JdbcTemplate(database.as(RUNTIME));
        for (int i = 1; i <= 25; i++) {
            runtime.update("insert into legal_case (reference_no, title, case_type, opened_on, created_by, updated_by)"
                    + " values (?, ?, 'CIVIL', date '2026-01-01', ?, ?)", "SYN-" + i, "synthetic قضية " + i, actor, actor);
        }
        runtime.update("insert into case_reference_counter (year, last_number) values (2031, 1)");
        Map<String, String> before = digest(admin);

        try (Connection open = database.as(RUNTIME).getConnection(); Statement held = open.createStatement()) {
            held.execute("select count(*) from legal_case");
            database.split();
            // a session opened before the transfer (the running previous release) keeps working
            try (ResultSet rows = held.executeQuery("select count(*) from legal_case")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong(1)).isEqualTo(25);
            }
            held.execute("update legal_case set title = title where reference_no = 'SYN-1'");
        }
        database.migrateAsOwner();
        database.applyOwnership();

        assertThat(digest(admin)).as("every table: row count and content digest").isEqualTo(before);
        database.audit();
        String catalog = catalog(admin);
        database.split();
        database.applyOwnership();
        assertThat(catalog(admin)).as("repeating the provisioning changes nothing").isEqualTo(catalog);
        database.audit();
    }

    @Test
    void theAuditRefusesEveryKindOfDrift() {
        DeploymentDatabase database = DeploymentDatabase.deploy();
        JdbcTemplate admin = new JdbcTemplate(database.admin);
        database.audit();
        String name = database.catalog();
        String[][] drifts = {
                {"alter table legal_case owner to " + RUNTIME, "alter table legal_case owner to lawdesk_owner"},
                {"grant create on schema lawdesk to " + RUNTIME, "revoke create on schema lawdesk from " + RUNTIME},
                {"grant temporary on database " + name + " to " + RUNTIME, "revoke temporary on database " + name + " from " + RUNTIME},
                {"grant truncate on audit_event to lawdesk_dml", "revoke truncate on audit_event from lawdesk_dml"},
                {"grant trigger on legal_case to " + RUNTIME, "revoke trigger on legal_case from " + RUNTIME},
                {"grant insert on flyway_schema_history to lawdesk_dml", "revoke insert on flyway_schema_history from lawdesk_dml"},
                {"grant lawdesk_owner to " + RUNTIME, "revoke lawdesk_owner from " + RUNTIME},
                {"grant pg_write_all_data to " + RUNTIME, "revoke pg_write_all_data from " + RUNTIME},
                {"alter role " + RUNTIME + " createdb", "alter role " + RUNTIME + " nocreatedb"},
                {"alter role lawdesk_legal login", "alter role lawdesk_legal nologin"},
                {"alter role lawdesk_operator login", "alter role lawdesk_operator nologin"},
                {"create function drift() returns int language sql security definer as 'select 1'", "drop function drift()"},
                {"grant select on legal_case to " + RUNTIME + " with grant option", "revoke grant option for select on legal_case from " + RUNTIME},
        };
        for (String[] drift : drifts) {
            admin.execute(drift[0]);
            try {
                assertThatThrownBy(database::audit).as(drift[0]).hasMessageContaining("privilege audit failed");
            } finally {
                admin.execute(drift[1]);
            }
        }
        database.audit();
    }

    @Test
    void anOperatorLoginGetsNoDataAccessAndTheInspectionScriptOnlyReads() {
        DeploymentDatabase database = DeploymentDatabase.deploy();
        JdbcTemplate admin = new JdbcTemplate(database.admin);
        // run by hand only (D-09 pending): the login is synthetic and removed again
        DeploymentDatabase.execute(database.admin, "set lawdesk.operator_login = 'op_synthetic'",
                DeploymentDatabase.read("deploy/postgres/operator-login.sql"));
        try {
            database.audit();
            assertThat(admin.queryForObject("select has_table_privilege('op_synthetic', 'lawdesk.legal_case', 'select')"
                    + " or has_schema_privilege('op_synthetic', 'lawdesk', 'usage')", Boolean.class)).isFalse();
            String catalog = catalog(admin);
            database.runScript("inspect-ownership.sql");
            assertThat(catalog(admin)).isEqualTo(catalog);
        } finally {
            admin.execute("revoke connect on database " + database.catalog() + " from op_synthetic");
            admin.execute("drop role op_synthetic");
        }
    }

    @Test
    void theReferenceCounterOnlyMovesForwardOneStepForTheRuntimeRole() {
        DeploymentDatabase database = DeploymentDatabase.deploy();
        JdbcTemplate runtime = new JdbcTemplate(database.as(RUNTIME));
        // the statement CaseRepository uses
        String next = "insert into case_reference_counter (year, last_number) values (2031, 1)"
                + " on conflict (year) do update set last_number = case_reference_counter.last_number + 1 returning last_number";
        assertThat(runtime.queryForObject(next, Integer.class)).isEqualTo(1);
        assertThat(runtime.queryForObject(next, Integer.class)).isEqualTo(2);
        for (String sql : new String[] {"update case_reference_counter set last_number = 1 where year = 2031",
                "update case_reference_counter set last_number = 90 where year = 2031",
                "update case_reference_counter set year = 2032 where year = 2031",
                "delete from case_reference_counter where year = 2031",
                "insert into case_reference_counter (year, last_number) values (2033, 50)"}) {
            assertThatThrownBy(() -> runtime.update(sql)).as(sql).rootCause().hasMessageContaining("case reference counter");
        }
        assertThat(runtime.queryForObject("select last_number from case_reference_counter where year = 2031", Integer.class)).isEqualTo(2);
    }

    /** Row count and content digest of every table except the migration history and the release log. */
    private static Map<String, String> digest(JdbcTemplate admin) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String table : admin.queryForList("select tablename from pg_tables where schemaname = 'lawdesk'"
                + " and tablename not in ('flyway_schema_history', 'app_release') order by 1", String.class)) {
            result.put(table, admin.queryForObject("select count(*) || ':' || md5(coalesce(string_agg(t::text, ',' order by t::text), ''))"
                    + " from lawdesk." + table + " t", String.class));
        }
        return result;
    }

    private static String catalog(JdbcTemplate admin) {
        return admin.queryForObject("""
                select string_agg(line, E'\\n' order by line) from (
                  select c.relname || ' ' || pg_get_userbyid(c.relowner) || ' ' || coalesce(c.relacl::text, '') as line
                    from pg_class c where c.relnamespace = 'lawdesk'::regnamespace
                  union all select 'schema ' || pg_get_userbyid(nspowner) || ' ' || coalesce(nspacl::text, '') from pg_namespace where nspname = 'lawdesk'
                  union all select 'default ' || pg_get_userbyid(defaclrole) || ' ' || defaclobjtype::text || ' ' || defaclacl::text from pg_default_acl
                ) x""", String.class);
    }
}
