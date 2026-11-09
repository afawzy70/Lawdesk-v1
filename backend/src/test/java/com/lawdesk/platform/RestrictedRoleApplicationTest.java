package com.lawdesk.platform;

import static com.lawdesk.security.TestActors.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.lawdesk.reports.IndicatorCode;
import com.lawdesk.security.Permission;

/**
 * The deployed database role is not the test superuser: it owns the schema {@code lawdesk}, has that schema as its search path
 * and has no rights on {@code public} (deploy/postgres/app-role.sql). A query that names {@code public.<table>} or asks
 * {@code to_regclass('public.<table>')} answers "permission denied" there while it passes on every superuser test database
 * (LD-058, production defect #79: the indicators over the deadline and enforcement tables answered 500 on UAT only).
 *
 * <p>This test starts the whole application, Flyway included, as exactly such a role, and calls every parameterless GET endpoint
 * (and the report, indicator and search endpoints with parameters) as a user holding every permission. Nothing may answer 5xx, and
 * the application's log, where a swallowed indicator failure is recorded, must not mention a permission error. The first test
 * shows the role really is restricted, so a green run means something.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class RestrictedRoleApplicationTest {

    private static final String ROLE = "lawdesk_probe_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    private static final String PASSWORD = UUID.randomUUID().toString();
    private static final PGSimpleDataSource ADMIN = createRestrictedDatabase();

    @DynamicPropertySource
    static void restrictedDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", ADMIN::getUrl);
        registry.add("spring.datasource.username", () -> ROLE);
        registry.add("spring.datasource.password", () -> PASSWORD);
        registry.add("spring.datasource.hikari.minimum-idle", () -> 0);
        registry.add("lawdesk.followup.schedule-enabled", () -> false);
        registry.add("lawdesk.ocr.schedule-enabled", () -> false);
    }

    /**
     * What deploy/postgres/app-role.sql does, statement for statement (the file itself needs psql: it reads a secret and switches
     * database); {@link #theRoleScriptStillDoesWhatThisTestMirrors} fails when the script gains or loses a statement.
     */
    private static PGSimpleDataSource createRestrictedDatabase() {
        DataSource fresh = PostgresTestSupport.freshDatabase();
        PGSimpleDataSource admin = (PGSimpleDataSource) fresh;
        try (Connection connection = admin.getConnection(); Statement statement = connection.createStatement()) {
            String database = connection.getCatalog();
            statement.execute("CREATE ROLE " + ROLE + " LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD '"
                    + PASSWORD + "'");
            statement.execute("REVOKE ALL ON DATABASE " + database + " FROM PUBLIC");
            statement.execute("GRANT CONNECT, TEMPORARY ON DATABASE " + database + " TO " + ROLE);
            statement.execute("REVOKE ALL ON SCHEMA public FROM PUBLIC");
            statement.execute("CREATE SCHEMA IF NOT EXISTS lawdesk AUTHORIZATION " + ROLE);
            statement.execute("ALTER ROLE " + ROLE + " SET search_path = lawdesk");
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException("could not prepare the restricted database: " + e.getMessage(), e);
        }
        return admin;
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mappings;

    @Test
    void theApplicationRoleReallyHasNoRightsOnPublic() throws Exception {
        assertThat(jdbc.queryForObject("select current_user", String.class)).isEqualTo(ROLE);
        assertThat(jdbc.queryForObject("select current_schema()", String.class)).isEqualTo("lawdesk");
        assertThat(jdbc.queryForObject("select has_schema_privilege(current_user, 'public', 'USAGE')", Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("select rolsuper from pg_roles where rolname = current_user", Boolean.class)).isFalse();
        // The defect, reproduced: a name qualified with public fails here (and answers on a superuser database).
        assertThatThrownBy(() -> jdbc.queryForObject("select to_regclass('public.deadline') is not null", Boolean.class))
                .rootCause().hasMessageContaining("permission denied");
        assertThatThrownBy(() -> jdbc.queryForObject("select count(*) from public.deadline", Long.class))
                .rootCause().hasMessageContaining("permission denied");
        // The plain name resolves through the search path.
        assertThat(jdbc.queryForObject("select to_regclass('deadline') is not null", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("select to_regclass('no_such_module_table') is not null", Boolean.class)).isFalse();
    }

    @Test
    void everyGetEndpointAnswersWithoutAPermissionErrorForTheDeploymentRole(CapturedOutput output) throws Exception {
        // Some rows for the queries to find: a case through the API, and a task, a document and a deadline-like record by SQL.
        MvcResult created = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/cases")
                        .with(as(Permission.values())).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType("application/json").content("""
                                {"title":"restricted role probe","caseType":"CIVIL","openedOn":"2024-01-10"}"""))
                .andReturn();
        assertThat(created.getResponse().getStatus()).as(created.getResponse().getContentAsString()).isEqualTo(201);
        UUID caseId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id"));
        UUID actor = UUID.fromString("00000000-0000-4000-8000-0000000000e1");
        jdbc.update("""
                insert into task (case_id, title, assignee_id, due_date, created_by, updated_by)
                values (?, 'restricted role probe', ?, current_date - 3, ?, ?)""", caseId, actor, actor, actor);

        List<String> failures = new ArrayList<>();
        int calls = 0;
        for (var entry : mappings.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = entry.getKey();
            HandlerMethod handler = entry.getValue();
            var methods = info.getMethodsCondition().getMethods();
            if (!(methods.isEmpty() || methods.stream().anyMatch(m -> m.asHttpMethod() == HttpMethod.GET))
                    || info.getPathPatternsCondition() == null) {
                continue;
            }
            String pattern = info.getPathPatternsCondition().getPatterns().iterator().next().getPatternString();
            // /me needs a real login principal (the mock actor is not a LawdeskUser); contract-test/boom is a test controller that
            // answers 500 on purpose. Neither touches the database role.
            if (!pattern.startsWith("/api/v1/") || pattern.equals("/api/v1/me") || pattern.startsWith("/api/v1/contract-test/")) {
                continue;
            }
            for (String path : concrete(pattern, caseId)) {
                calls++;
                MvcResult result = mvc.perform(get(path).with(as(Permission.values()))).andReturn();
                int status = result.getResponse().getStatus();
                if (status >= 500) {
                    failures.add(status + " " + path + " (" + handler.getShortLogMessage() + ")");
                }
            }
        }
        // The endpoints that need parameters to reach their queries.
        for (String path : List.of("/api/v1/search?q=probe", "/api/v1/search?q=probe&size=5", "/api/v1/documents/search?q=probe",
                "/api/v1/reports/cases/export?format=csv", "/api/v1/reports/cases/export?format=xlsx",
                "/api/v1/reports/judgments/export?format=csv", "/api/v1/reports/hearings/export?format=csv",
                "/api/v1/reports/workload/export?format=csv", "/api/v1/reports/experts/export?format=csv",
                "/api/v1/reports/dashboard", "/api/v1/reports/indicators", "/api/v1/reports/cases?size=5",
                "/api/v1/tasks?status=OPEN", "/api/v1/tasks?status=NEW", "/api/v1/tasks?overdue=true")) {
            calls++;
            int status = mvc.perform(get(path).with(as(Permission.values()))).andReturn().getResponse().getStatus();
            if (status >= 500) {
                failures.add(status + " " + path);
            }
        }
        for (IndicatorCode code : IndicatorCode.values()) {
            for (String path : List.of("/api/v1/reports/indicators/" + code.name() + "?size=5", "/api/v1/reports/indicators/" + code.name() + "/export?format=csv")) {
                calls++;
                int status = mvc.perform(get(path).with(as(Permission.values()))).andReturn().getResponse().getStatus();
                if (status >= 500) {
                    failures.add(status + " " + path);
                }
            }
        }

        assertThat(calls).as("GET endpoints exercised").isGreaterThan(80);
        assertThat(failures).as("endpoints that answered 5xx as the deployment role").isEmpty();
        assertThat(output.getAll().toLowerCase())
                .as("a permission error recorded in the log (a swallowed failure, such as an indicator reported unavailable)")
                .doesNotContain("permission denied").doesNotContain("insufficient_privilege").doesNotContain("sqlstate 42501");
    }

    /** The path with every variable filled: the case id where the variable is a case, an indicator code, or a fresh id. */
    private static List<String> concrete(String pattern, UUID caseId) {
        if (pattern.contains("{code}")) {
            return List.of(pattern.replace("{code}", IndicatorCode.TOTAL_CASES.name()));
        }
        String path = pattern.replace("{caseId}", caseId.toString()).replace("{id}", caseId.toString());
        return List.of(path.replaceAll("\\{[^}]+}", UUID.randomUUID().toString()));
    }

    @Test
    void theRoleScriptStillDoesWhatThisTestMirrors() throws Exception {
        java.nio.file.Path script = java.nio.file.Path.of("..", "deploy", "postgres", "app-role.sql");
        if (!java.nio.file.Files.exists(script)) {
            script = java.nio.file.Path.of("deploy", "postgres", "app-role.sql");
        }
        String text = java.nio.file.Files.readString(script);
        for (String statement : List.of("LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS",
                "REVOKE ALL ON DATABASE lawdesk FROM PUBLIC", "GRANT CONNECT, TEMPORARY ON DATABASE lawdesk TO lawdesk_app",
                "REVOKE ALL ON SCHEMA public FROM PUBLIC", "CREATE SCHEMA IF NOT EXISTS lawdesk AUTHORIZATION lawdesk_app",
                "ALTER ROLE lawdesk_app SET search_path = lawdesk")) {
            assertThat(text).as("deploy/postgres/app-role.sql must still contain: " + statement).contains(statement);
        }
        long statements = text.lines().filter(l -> !l.startsWith("--") && !l.isBlank() && !l.startsWith("\\")).count();
        assertThat(statements).as("statements in app-role.sql; update this test's mirror when it changes").isEqualTo(8);
    }
}
