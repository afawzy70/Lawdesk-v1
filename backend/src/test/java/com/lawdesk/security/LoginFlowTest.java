package com.lawdesk.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import com.lawdesk.platform.PostgresTestSupport;

/** LD-007 login/logout and LD-008 account policies, over real HTTP through the real security chain. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestUsers.class)
class LoginFlowTest extends PostgresTestSupport {

    private static final String UNAUTHORIZED = "{\"type\":\"about:blank\",\"title\":\"Unauthorized\",\"status\":401}";

    @LocalServerPort
    private int port;

    @Autowired
    private TestUsers users;

    private Browser browser() {
        return new Browser(port);
    }

    @Test
    void theCsrfCookieIsIssuedByAPublicEndpoint() {
        Browser browser = browser();

        assertThat(browser.get("/api/v1/auth/csrf").statusCode()).isEqualTo(204);
        assertThat(browser.cookie("XSRF-TOKEN")).isNotBlank();
        assertThat(browser.setCookies()).anySatisfy(c -> assertThat(c).startsWith("XSRF-TOKEN=").doesNotContain("HttpOnly"));
    }

    @Test
    void usersSignInWithUsernameOrEmailInAnyCaseAndGetTheirProfile() {
        var user = users.create("LEGAL_STAFF");

        for (String login : List.of(user.username(), " " + user.username().toUpperCase() + " ", user.email().toUpperCase())) {
            Browser browser = browser();
            HttpResponse<String> response = browser.login(login, TestUsers.PASSWORD);

            assertThat(response.statusCode()).as(login).isEqualTo(200);
            assertThat(response.body()).contains("\"username\":\"" + user.username() + "\"")
                    .contains("\"roles\":[\"LEGAL_STAFF\"]").contains("\"CASE_CREATE\"")
                    .contains("\"mustChangePassword\":false").doesNotContain("password\":").doesNotContain("hash");
            assertThat(browser.get("/api/v1/me").statusCode()).isEqualTo(200);
        }
    }

    @Test
    void theSessionCookieIsHttpOnlySecureAndSameSiteLax() {
        var user = users.create("LEGAL_STAFF");
        Browser browser = browser();

        browser.login(user.username(), TestUsers.PASSWORD);

        assertThat(browser.setCookies()).anySatisfy(cookie -> assertThat(cookie).startsWith("JSESSIONID=")
                .contains("HttpOnly").contains("Secure").containsIgnoringCase("SameSite=Lax"));
    }

    @Test
    void signingInIssuesANewSessionId() {
        var first = users.create("LEGAL_STAFF");
        var second = users.create("LEGAL_STAFF");
        Browser browser = browser();
        browser.login(first.username(), TestUsers.PASSWORD);
        String before = browser.cookie("JSESSIONID");

        browser.login(second.username(), TestUsers.PASSWORD);

        assertThat(browser.cookie("JSESSIONID")).isNotBlank().isNotEqualTo(before);
        assertThat(browser.get("/api/v1/me").body()).contains(second.username());
    }

    @Test
    void everyRefusalLooksTheSame() {
        var wrongPassword = users.create("LEGAL_STAFF");
        var disabled = users.create("LEGAL_STAFF");
        users.disable(disabled.id());
        var locked = users.create("LEGAL_STAFF");
        users.unlockAt(locked.id(), Instant.now().plusSeconds(600));

        List<HttpResponse<String>> refusals = List.of(
                browser().login(wrongPassword.username(), "not the password at all"),
                browser().login("nobody-" + wrongPassword.username(), TestUsers.PASSWORD),
                browser().login(disabled.username(), TestUsers.PASSWORD),
                browser().login(locked.username(), TestUsers.PASSWORD));

        for (HttpResponse<String> refusal : refusals) {
            assertThat(refusal.statusCode()).isEqualTo(401);
            assertThat(refusal.body()).isEqualTo(UNAUTHORIZED);
            assertThat(refusal.headers().allValues("Set-Cookie")).noneMatch(c -> c.startsWith("JSESSIONID="));
        }
    }

    @Test
    void signingInNeedsTheCsrfToken() {
        var user = users.create("LEGAL_STAFF");
        Browser browser = browser();
        browser.get("/api/v1/auth/csrf");

        HttpResponse<String> response = browser.postWithoutCsrf("/api/v1/auth/login",
                "{\"login\":\"%s\",\"password\":\"%s\"}".formatted(user.username(), TestUsers.PASSWORD));

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(browser.get("/api/v1/me").statusCode()).isEqualTo(401);
    }

    @Test
    void signingOutEndsTheSession() {
        var user = users.create("LEGAL_STAFF");
        Browser browser = browser();
        browser.login(user.username(), TestUsers.PASSWORD);
        String session = browser.cookie("JSESSIONID");

        assertThat(browser.post("/api/v1/auth/logout", "").statusCode()).isEqualTo(204);

        Browser replay = browser();
        replay.cookie("JSESSIONID", session);
        assertThat(replay.get("/api/v1/me").statusCode()).isEqualTo(401);
        assertThat(browser.get("/api/v1/me").statusCode()).isEqualTo(401);
    }

    @Test
    void theProfileNeedsASession() {
        HttpResponse<String> response = browser().get("/api/v1/me");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).isEqualTo(UNAUTHORIZED);
    }

    @Test
    void disablingAUserEndsItsAccessOnTheNextRequest() {
        var user = users.create("LEGAL_STAFF");
        Browser browser = browser();
        browser.login(user.username(), TestUsers.PASSWORD);

        users.disable(user.id());

        assertThat(browser.get("/api/v1/me").statusCode()).isEqualTo(401);
    }

    @Test
    void roleChangesApplyOnTheNextRequest() {
        var user = users.create("LEGAL_STAFF");
        Browser browser = browser();
        browser.login(user.username(), TestUsers.PASSWORD);
        assertThat(browser.get("/api/v1/me").body()).doesNotContain("USER_MANAGE");

        users.grant(user.id(), "ADMIN");

        assertThat(browser.get("/api/v1/me").body()).contains("USER_MANAGE").contains("\"ADMIN\"");
    }

    @Test
    void fiveFailuresLockTheAccountEvenAgainstTheRightPassword() {
        var user = users.create("LEGAL_STAFF");
        for (int i = 0; i < 5; i++) {
            browser().login(user.username(), "wrong password number " + i);
        }

        assertThat(browser().login(user.username(), TestUsers.PASSWORD).statusCode()).isEqualTo(401);

        users.unlockAt(user.id(), Instant.now().minusSeconds(1));
        assertThat(browser().login(user.username(), TestUsers.PASSWORD).statusCode()).isEqualTo(200);
        assertThat(users.failedAttempts(user.id())).isZero();
    }

    @Test
    void concurrentFailuresStillLockTheAccount() throws Exception {
        var user = users.create("LEGAL_STAFF");
        List<Callable<Integer>> attempts = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            attempts.add(() -> browser().login(user.username(), "wrong password in parallel").statusCode());
        }
        try (var pool = Executors.newFixedThreadPool(6)) {
            for (var result : pool.invokeAll(attempts)) {
                assertThat(result.get()).isEqualTo(401);
            }
        }

        assertThat(users.failedAttempts(user.id())).isGreaterThanOrEqualTo(5);
        assertThat(browser().login(user.username(), TestUsers.PASSWORD).statusCode()).isEqualTo(401);
    }

    @Test
    void passwordsAreStoredAsAdaptiveHashes() {
        var user = users.create("LEGAL_STAFF");

        assertThat(users.passwordHash(user.id())).startsWith("{bcrypt}$2").doesNotContain(TestUsers.PASSWORD);
    }
}
