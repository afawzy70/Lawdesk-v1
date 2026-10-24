package com.lawdesk.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.lawdesk.platform.PostgresTestSupport;
import com.lawdesk.users.UserAccounts;
import com.lawdesk.users.UserAdministration;

/**
 * LD-008.7 (QAD-028): an administrative reset, a password change and a disablement revoke every session of the user
 * that signed in before the event; only the session that changed its own password stays signed in. Real HTTP sessions
 * (JSESSIONID from {@code POST /api/v1/auth/login}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestUsers.class)
@ExtendWith(OutputCaptureExtension.class)
class SessionRevocationTest extends PostgresTestSupport {

    private static final String TEMPORARY = "a temporary passphrase";
    private static final String SECOND = "the second new passphrase";
    private static final String THIRD = "yet another passphrase";

    @LocalServerPort
    private int port;

    @Autowired
    private TestUsers users;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserAdministration administration;

    /** Lets a test run something right after the account was read, to interleave deterministically. */
    @MockitoSpyBean
    private UserAccounts accounts;

    private final AtomicReference<Runnable> afterLoginRead = new AtomicReference<>();
    private final AtomicReference<Runnable> afterPasswordCheckRead = new AtomicReference<>();

    private Browser admin;
    private UUID caseId;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            Runnable hook = afterLoginRead.getAndSet(null);
            if (hook != null) {
                hook.run();
            }
            return result;
        }).when(accounts).findByLogin(anyString());
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            if (StackWalker.getInstance().walk(frames -> frames.anyMatch(
                    f -> f.getClassName().equals("com.lawdesk.security.MeController")))) {
                Runnable hook = afterPasswordCheckRead.getAndSet(null);
                if (hook != null) {
                    hook.run();
                }
            }
            return result;
        }).when(accounts).findById(any(UUID.class));
        var administrator = users.create("ADMIN");
        admin = signIn(administrator.username(), TestUsers.PASSWORD);
        caseId = jdbc.queryForObject("""
                insert into legal_case (reference_no, title, case_type, opened_on, status, created_by, updated_by)
                values (?, 'QAD-028 case', 'CIVIL', date '2026-01-05', 'OPEN', ?, ?) returning id""", UUID.class,
                "QAD028-" + UUID.randomUUID().toString().substring(0, 8), administrator.id(), administrator.id());
    }

    @AfterEach
    void tearDown() {
        jdbc.update("delete from case_event where case_id = ?", caseId);
        jdbc.update("delete from legal_case where id = ?", caseId);
    }

    private Browser signIn(String login, String password) {
        Browser browser = new Browser(port);
        assertThat(browser.login(login, password).statusCode()).as("login of %s", login).isEqualTo(200);
        return browser;
    }

    private HttpResponse<String> change(Browser browser, String current, String next) {
        return browser.post("/api/v1/me/password",
                "{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}".formatted(current, next));
    }

    private void reset(UUID user, String temporary) {
        assertThat(admin.post("/api/v1/users/" + user + "/password-reset",
                "{\"temporaryPassword\":\"" + temporary + "\"}").statusCode()).isEqualTo(204);
    }

    private int readCase(Browser browser) {
        return browser.get("/api/v1/cases/" + caseId).statusCode();
    }

    private void assertRevoked(Browser browser) {
        assertThat(readCase(browser)).as("GET case").isEqualTo(401);
        assertThat(browser.get("/api/v1/me").statusCode()).as("GET /me").isEqualTo(401);
        assertThat(browser.post("/api/v1/cases/" + caseId + "/status", "{\"status\":\"ACTIVE\"}").statusCode())
                .as("POST status").isEqualTo(401);
        assertThat(browser.put("/api/v1/users/" + UUID.randomUUID() + "/enabled", "{\"enabled\":true}").statusCode())
                .as("PUT enabled").isEqualTo(401);
        assertThat(change(browser, TestUsers.PASSWORD, "never accepted passphrase").statusCode())
                .as("POST /me/password").isEqualTo(401);
    }

    private long revocationEvents(UUID user) {
        return jdbc.queryForObject("""
                select count(*) from audit_event where action = 'SESSION_REVOKED' and outcome = 'DENIED'
                and actor_id = ?""", Long.class, user);
    }

    @Test
    void theQaReproductionTheOriginalSessionCannotReadTheCaseAfterAnotherSessionChangedTheResetPassword() {
        String username = "qad028" + UUID.randomUUID().toString().substring(0, 6);
        var created = admin.post("/api/v1/users", """
                {"username":"%s","email":"%s@example.invalid","displayName":"QAD 028","locale":"en",
                 "roles":["LEGAL_STAFF"],"temporaryPassword":"%s"}""".formatted(username, username, TEMPORARY));
        assertThat(created.statusCode()).isEqualTo(201);
        UUID id = jdbc.queryForObject("select id from app_user where username = ?", UUID.class, username);
        Browser sessionA = signIn(username, TEMPORARY);
        assertThat(change(sessionA, TEMPORARY, SECOND).statusCode()).isEqualTo(204);
        assertThat(readCase(sessionA)).isEqualTo(200);

        reset(id, THIRD);

        Browser sessionB = signIn(username, THIRD);
        assertThat(change(sessionB, THIRD, "the final passphrase").statusCode()).isEqualTo(204);

        assertThat(readCase(sessionA)).isEqualTo(401);
        assertRevoked(sessionA);
        assertThat(readCase(sessionB)).isEqualTo(200);
    }

    @Test
    void aResetRevokesEverySessionThatSignedInBefore() {
        var user = users.create("LEGAL_STAFF");
        Browser first = signIn(user.username(), TestUsers.PASSWORD);
        Browser second = signIn(user.email(), TestUsers.PASSWORD);

        reset(user.id(), TEMPORARY);

        assertRevoked(first);
        assertRevoked(second);
        Browser after = signIn(user.username(), TEMPORARY);
        assertThat(after.get("/api/v1/me").statusCode()).isEqualTo(200);
        assertThat(readCase(after)).isEqualTo(403);
    }

    @Test
    void anAdministratorWhoResetsTheirOwnPasswordIsSignedOutToo() {
        var other = users.create("ADMIN");
        Browser self = signIn(other.username(), TestUsers.PASSWORD);

        assertThat(self.post("/api/v1/users/" + other.id() + "/password-reset",
                "{\"temporaryPassword\":\"" + TEMPORARY + "\"}").statusCode()).isEqualTo(204);

        assertRevoked(self);
    }

    @Test
    void anOwnPasswordChangeKeepsTheChangingSessionAndRevokesTheOthers() {
        var user = users.create("LEGAL_STAFF");
        Browser changing = signIn(user.username(), TestUsers.PASSWORD);
        Browser other = signIn(user.username(), TestUsers.PASSWORD);

        assertThat(change(changing, TestUsers.PASSWORD, SECOND).statusCode()).isEqualTo(204);

        assertThat(readCase(changing)).isEqualTo(200);
        assertThat(changing.get("/api/v1/me").statusCode()).isEqualTo(200);
        assertRevoked(other);
        // The surviving session can change again, and is itself revoked by a later reset.
        assertThat(change(changing, SECOND, THIRD).statusCode()).isEqualTo(204);
        assertThat(readCase(changing)).isEqualTo(200);
        reset(user.id(), TEMPORARY);
        assertRevoked(changing);
    }

    @Test
    void aDisabledUsersSessionsStayRevokedWhenTheAccountIsEnabledAgain() {
        var user = users.create("LEGAL_STAFF");
        Browser session = signIn(user.username(), TestUsers.PASSWORD);

        assertThat(admin.put("/api/v1/users/" + user.id() + "/enabled", "{\"enabled\":false}").statusCode())
                .isEqualTo(200);
        assertThat(admin.put("/api/v1/users/" + user.id() + "/enabled", "{\"enabled\":true}").statusCode())
                .isEqualTo(200);

        assertRevoked(session);
        assertThat(readCase(signIn(user.username(), TestUsers.PASSWORD))).isEqualTo(200);
    }

    @Test
    void aRevokedSessionStaysDeadAfterLaterChangesOfAnyKind() {
        var user = users.create("LEGAL_STAFF");
        Browser revoked = signIn(user.username(), TestUsers.PASSWORD);
        reset(user.id(), TEMPORARY);
        assertThat(readCase(revoked)).isEqualTo(401);

        Browser fresh = signIn(user.username(), TEMPORARY);
        assertThat(change(fresh, TEMPORARY, SECOND).statusCode()).isEqualTo(204);
        assertThat(readCase(revoked)).isEqualTo(401);
        assertThat(admin.put("/api/v1/users/" + user.id() + "/roles", "{\"roles\":[\"LEGAL_MANAGER\"]}")
                .statusCode()).isEqualTo(200);
        assertThat(readCase(revoked)).isEqualTo(401);
        assertThat(change(fresh, SECOND, THIRD).statusCode()).isEqualTo(204);

        assertRevoked(revoked);
        assertThat(readCase(fresh)).isEqualTo(200);
    }

    @Test
    void aRoleChangeTakesEffectOnTheNextRequestWithoutRevokingTheSession() {
        var user = users.create("LEGAL_STAFF");
        Browser session = signIn(user.username(), TestUsers.PASSWORD);
        assertThat(session.get("/api/v1/users").statusCode()).isEqualTo(403);

        assertThat(admin.put("/api/v1/users/" + user.id() + "/roles", "{\"roles\":[\"LEGAL_MANAGER\"]}")
                .statusCode()).isEqualTo(200);

        assertThat(session.get("/api/v1/me").body()).contains("\"LEGAL_MANAGER\"");
        assertThat(session.get("/api/v1/users").statusCode()).isEqualTo(200);
        assertThat(readCase(session)).isEqualTo(200);
        assertThat(revocationEvents(user.id())).isZero();
    }

    @Test
    void logoutStillWorksBeforeAndAfterARevocation() {
        var user = users.create("LEGAL_STAFF");
        Browser plain = signIn(user.username(), TestUsers.PASSWORD);
        assertThat(plain.post("/api/v1/auth/logout", "").statusCode()).isEqualTo(204);
        assertThat(plain.get("/api/v1/me").statusCode()).isEqualTo(401);

        Browser revoked = signIn(user.username(), TestUsers.PASSWORD);
        reset(user.id(), TEMPORARY);
        assertThat(revoked.post("/api/v1/auth/logout", "").statusCode()).isEqualTo(204);
        assertThat(revoked.get("/api/v1/me").statusCode()).isEqualTo(401);
    }

    @Test
    void concurrentlyUsedSessionsAreAllRevoked() throws Exception {
        var user = users.create("LEGAL_STAFF");
        List<Browser> sessions = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            CyclicBarrier together = new CyclicBarrier(4);
            List<Future<Browser>> logins = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                logins.add(pool.submit(() -> {
                    together.await(10, TimeUnit.SECONDS);
                    return signIn(user.username(), TestUsers.PASSWORD);
                }));
            }
            for (Future<Browser> login : logins) {
                sessions.add(login.get(30, TimeUnit.SECONDS));
            }
            CyclicBarrier reading = new CyclicBarrier(5);
            List<Future<Integer>> reads = new ArrayList<>();
            for (Browser session : sessions) {
                reads.add(pool.submit((Callable<Integer>) () -> {
                    reading.await(10, TimeUnit.SECONDS);
                    return readCase(session);
                }));
            }
            reading.await(10, TimeUnit.SECONDS);
            reset(user.id(), TEMPORARY);
            for (Future<Integer> read : reads) {
                assertThat(read.get(30, TimeUnit.SECONDS)).isIn(200, 401);
            }
        } finally {
            pool.shutdownNow();
        }

        for (Browser session : sessions) {
            assertRevoked(session);
        }
    }

    @Test
    void aLoginThatReadTheAccountJustBeforeAResetDoesNotSurviveIt() {
        var user = users.create("LEGAL_STAFF");
        // The login reads the account (old password, still valid), then the reset commits, then the session is made.
        afterLoginRead.set(() -> administration.resetPassword(user.id(), TEMPORARY,
                new ActorRef(UserAccounts.SYSTEM, "system")));

        Browser racing = signIn(user.username(), TestUsers.PASSWORD);

        assertThat(afterLoginRead.get()).isNull();
        assertRevoked(racing);
        Browser fresh = signIn(user.username(), TEMPORARY);
        assertThat(change(fresh, TEMPORARY, SECOND).statusCode()).isEqualTo(204);
        assertThat(readCase(racing)).isEqualTo(401);
    }

    @Test
    void anOwnPasswordChangeRacingAResetDoesNotUndoTheReset() {
        var user = users.create("LEGAL_STAFF");
        Browser session = signIn(user.username(), TestUsers.PASSWORD);
        // The change checks the current password against the account as read, then the reset commits.
        afterPasswordCheckRead.set(() -> administration.resetPassword(user.id(), TEMPORARY,
                new ActorRef(UserAccounts.SYSTEM, "system")));

        HttpResponse<String> change = change(session, TestUsers.PASSWORD, SECOND);

        assertThat(afterPasswordCheckRead.get()).isNull();
        assertThat(change.statusCode()).isEqualTo(401);
        assertRevoked(session);
        assertThat(new Browser(port).login(user.username(), SECOND).statusCode()).isEqualTo(401);
        assertThat(signIn(user.username(), TEMPORARY).get("/api/v1/me").body())
                .contains("\"mustChangePassword\":true");
    }

    @Test
    void aRefusedRevokedSessionIsAuditedWithoutSessionIdsOrTokens(CapturedOutput output) {
        var user = users.create("LEGAL_STAFF");
        Browser session = signIn(user.username(), TestUsers.PASSWORD);
        String sessionId = session.cookie("JSESSIONID");
        String csrf = session.cookie("XSRF-TOKEN");
        assertThat(sessionId).isNotBlank();

        reset(user.id(), TEMPORARY);
        assertThat(readCase(session)).isEqualTo(401);
        assertThat(readCase(session)).isEqualTo(401);

        assertThat(revocationEvents(user.id())).isEqualTo(1);
        var event = jdbc.queryForMap("""
                select actor_username, target_type, target_id, reason, request_id from audit_event
                where action = 'SESSION_REVOKED' and actor_id = ?""", user.id());
        assertThat(event.get("actor_username")).isEqualTo(user.username());
        assertThat(event.get("target_type")).isEqualTo("USER");
        assertThat(event.get("target_id")).isEqualTo(user.id().toString());
        assertThat((String) event.get("request_id")).isNotBlank();
        List<String> everything = jdbc.queryForList("select concat_ws('|', actor_username, action, target_type, "
                + "target_id, outcome, reason, request_id) from audit_event", String.class);
        assertThat(everything).noneMatch(row -> row.contains(sessionId) || (csrf != null && row.contains(csrf)));
        assertThat(output.getAll()).doesNotContain(sessionId);
        if (csrf != null) {
            assertThat(output.getAll()).doesNotContain(csrf);
        }
    }
}
