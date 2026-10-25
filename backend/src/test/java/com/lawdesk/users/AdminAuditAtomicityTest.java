package com.lawdesk.users;

import static com.lawdesk.security.TestActors.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.lawdesk.platform.Faults;
import com.lawdesk.platform.PostgresTestSupport;
import com.lawdesk.security.Permission;
import com.lawdesk.security.TestUsers;

/**
 * QAD-042 (S2): the SUCCESS audit event of every user and role administration change is written in the transaction of
 * the change. A change that does not commit (a failure at commit time, an audit event that cannot be written, a lost
 * race) leaves neither the change nor an event; a retry records exactly one. Real PostgreSQL fault triggers.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestUsers.class)
class AdminAuditAtomicityTest extends PostgresTestSupport {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TestUsers users;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void removeFaults() {
        Faults.clear(jdbc);
    }

    /** One administrative change: its audit action, the request, and a snapshot of the rows it changes. */
    private record Change(String action, MockHttpServletRequestBuilder request, String snapshotSql, Object... args) {
    }

    private RequestPostProcessor admin() {
        var created = users.create("ADMIN");
        return as(created.id(), created.username(), Permission.values());
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, RequestPostProcessor actor,
            String body) {
        return request.with(actor).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private List<Map<String, Object>> snapshot(Change change) {
        return jdbc.queryForList(change.snapshotSql(), change.args());
    }

    private int events(String action) {
        return jdbc.queryForObject("select count(*) from audit_event where action = ? and outcome = 'SUCCESS'",
                Integer.class, action);
    }

    private String role() throws Exception {
        UUID id = jdbc.queryForObject("insert into app_role (code, name) values (?, 'Clerk') returning id", UUID.class,
                "R" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase());
        jdbc.update("insert into role_permission (role_id, permission) values (?, 'CASE_VIEW')", id);
        return id.toString();
    }

    /** Builders are created before any fault is installed, because setting them up writes to the same tables. */
    private List<Function<RequestPostProcessor, Change>> changes() throws Exception {
        String name = "n" + UUID.randomUUID().toString().substring(0, 8);
        var target = users.create("LEGAL_STAFF");
        String roleId = role();
        String permissionsRoleId = role();
        String uid = target.id().toString();
        Object[] userRow = {target.id()};
        String userSql = "select display_name, enabled, version, password_hash from app_user where id = ?";
        return List.of(
                actor -> new Change("USER_CREATE", json(post("/api/v1/users"), actor, """
                        {"username":"%s","email":"%s@example.invalid","displayName":"New","locale":"ar",
                         "roles":["LEGAL_STAFF"],"temporaryPassword":"temporary passphrase 1"}"""
                        .formatted(name, name)), "select username from app_user where username = ?", name),
                actor -> new Change("USER_UPDATE", json(put("/api/v1/users/" + uid), actor, """
                        {"email":"%s","displayName":"Renamed","locale":"en","version":0}""".formatted(target.email())),
                        userSql, userRow),
                actor -> new Change("USER_DISABLE", json(put("/api/v1/users/" + uid + "/enabled"), actor,
                        "{\"enabled\":false}"), userSql, userRow),
                actor -> new Change("USER_ROLES_CHANGE", json(put("/api/v1/users/" + uid + "/roles"), actor,
                        "{\"roles\":[\"LEGAL_MANAGER\"]}"),
                        "select role_id from user_role where user_id = ? order by 1", userRow),
                actor -> new Change("PASSWORD_RESET", json(post("/api/v1/users/" + uid + "/password-reset"), actor,
                        "{\"temporaryPassword\":\"another temporary passphrase\"}"), userSql, userRow),
                actor -> new Change("ROLE_CREATE", json(post("/api/v1/roles"), actor, """
                        {"code":"C%s","name":"Created","permissions":["CASE_VIEW"]}""".formatted(name.toUpperCase())),
                        "select code from app_role where code = ?", "C" + name.toUpperCase()),
                actor -> new Change("ROLE_UPDATE", json(put("/api/v1/roles/" + roleId), actor, """
                        {"name":"Renamed clerk","permissions":["CASE_VIEW"],"version":0}"""),
                        "select name, version from app_role where id = ?", UUID.fromString(roleId)),
                actor -> new Change("ROLE_PERMISSIONS_CHANGE", json(put("/api/v1/roles/" + permissionsRoleId), actor, """
                        {"name":"Clerk","permissions":["CASE_VIEW","CASE_EDIT"],"version":0}"""),
                        "select permission from role_permission where role_id = ? order by 1",
                        UUID.fromString(permissionsRoleId)));
    }

    private void assertNothingHappened(Change change, List<Map<String, Object>> before, int eventsBefore) {
        assertThat(snapshot(change)).as("%s left no change behind", change.action()).isEqualTo(before);
        assertThat(events(change.action())).as("%s left no SUCCESS event", change.action()).isEqualTo(eventsBefore);
    }

    @Test
    void aFailureAtCommitTimeLeavesNeitherTheChangeNorTheEvent() throws Exception {
        RequestPostProcessor actor = admin();
        List<Function<RequestPostProcessor, Change>> changes = changes();
        for (var make : changes) {
            Change change = make.apply(actor);
            List<Map<String, Object>> before = snapshot(change);
            int eventsBefore = events(change.action());
            Faults.failCommit(jdbc);
            int answer;
            try {
                answer = mvc.perform(change.request()).andReturn().getResponse().getStatus();
            } finally {
                Faults.clear(jdbc);
            }
            assertThat(answer).as(change.action()).isGreaterThanOrEqualTo(500);
            assertNothingHappened(change, before, eventsBefore);
        }
    }

    @Test
    void anEventThatCannotBeWrittenRefusesTheChange() throws Exception {
        RequestPostProcessor actor = admin();
        List<Function<RequestPostProcessor, Change>> changes = changes();
        for (var make : changes) {
            Change change = make.apply(actor);
            List<Map<String, Object>> before = snapshot(change);
            int eventsBefore = events(change.action());
            Faults.failAuditWrite(jdbc, change.action());
            int answer;
            try {
                answer = mvc.perform(change.request()).andReturn().getResponse().getStatus();
            } finally {
                Faults.clear(jdbc);
            }
            assertThat(answer).as(change.action()).isGreaterThanOrEqualTo(500);
            assertNothingHappened(change, before, eventsBefore);
        }
    }

    @Test
    void aRetryAfterAFailureRecordsExactlyOneEvent() throws Exception {
        RequestPostProcessor actor = admin();
        List<Function<RequestPostProcessor, Change>> changes = changes();
        for (var make : changes) {
            Change change = make.apply(actor);
            int eventsBefore = events(change.action());
            Faults.failCommit(jdbc);
            try {
                mvc.perform(change.request());
            } finally {
                Faults.clear(jdbc);
            }
            int answer = mvc.perform(make.apply(actor).request()).andReturn().getResponse().getStatus();
            assertThat(answer).as(change.action()).isBetween(200, 299);
            assertThat(events(change.action())).as(change.action()).isEqualTo(eventsBefore + 1);
        }
    }

    @Test
    void twoSimultaneousUpdatesRecordExactlyOneEvent() throws Exception {
        RequestPostProcessor actor = admin();
        var target = users.create("LEGAL_STAFF");
        String body = """
                {"email":"%s","displayName":"Raced","locale":"en","version":0}""".formatted(target.email());
        int before = events("USER_UPDATE");
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> attempt = () -> {
                go.await();
                return mvc.perform(json(put("/api/v1/users/" + target.id()), actor, body)).andReturn().getResponse()
                        .getStatus();
            };
            List<Future<Integer>> results = List.of(pool.submit(attempt), pool.submit(attempt));
            go.countDown();
            assertThat(List.of(results.get(0).get(30, TimeUnit.SECONDS), results.get(1).get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        } finally {
            pool.shutdownNow();
        }
        assertThat(events("USER_UPDATE")).isEqualTo(before + 1);
    }
}
