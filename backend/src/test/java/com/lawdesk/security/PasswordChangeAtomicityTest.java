package com.lawdesk.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.lawdesk.platform.Faults;
import com.lawdesk.platform.PostgresTestSupport;

/**
 * QAD-043 (S2): a password change and its SUCCESS audit event commit together. If the event cannot be written, or the
 * transaction fails at commit, the old password still works, the session and the credential version are untouched and
 * no success is reported; a wrong current password is still recorded as a FAILURE (an independent event).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestUsers.class)
class PasswordChangeAtomicityTest extends PostgresTestSupport {

    private static final String NEW_PASSWORD = "a brand new passphrase";

    @LocalServerPort
    private int port;

    @Autowired
    private TestUsers users;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void removeFaults() {
        Faults.clear(jdbc);
    }

    private HttpResponse<String> change(Browser browser, String current, String next) {
        return browser.post("/api/v1/me/password",
                "{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}".formatted(current, next));
    }

    private Browser signedIn(TestUsers.Created user) {
        Browser browser = new Browser(port);
        assertThat(browser.login(user.username(), TestUsers.PASSWORD).statusCode()).isEqualTo(200);
        return browser;
    }

    private int successEvents(UUID user) {
        return jdbc.queryForObject("""
                select count(*) from audit_event where action = 'PASSWORD_CHANGE' and outcome = 'SUCCESS'
                and target_id = ?""", Integer.class, user.toString());
    }

    private Map<String, Object> credentials(UUID user) {
        return jdbc.queryForMap("select password_hash, credential_version, version from app_user where id = ?", user);
    }

    private void assertNothingChanged(TestUsers.Created user, Browser browser, Map<String, Object> before) {
        assertThat(credentials(user.id())).isEqualTo(before);
        assertThat(successEvents(user.id())).isZero();
        assertThat(browser.get("/api/v1/me").statusCode()).as("the session is still valid").isEqualTo(200);
        assertThat(new Browser(port).login(user.username(), TestUsers.PASSWORD).statusCode()).isEqualTo(200);
        assertThat(new Browser(port).login(user.username(), NEW_PASSWORD).statusCode()).isEqualTo(401);
    }

    @Test
    void anEventThatCannotBeWrittenRollsTheChangeBack() {
        var user = users.create("LEGAL_STAFF");
        Browser browser = signedIn(user);
        Map<String, Object> before = credentials(user.id());
        Faults.failAuditWrite(jdbc, "PASSWORD_CHANGE");

        HttpResponse<String> response = change(browser, TestUsers.PASSWORD, NEW_PASSWORD);
        Faults.clear(jdbc);

        assertThat(response.statusCode()).isEqualTo(500);
        assertNothingChanged(user, browser, before);
    }

    @Test
    void aFailureAtCommitTimeRollsTheChangeBack() {
        var user = users.create("LEGAL_STAFF");
        Browser browser = signedIn(user);
        Map<String, Object> before = credentials(user.id());
        Faults.failCommit(jdbc);

        HttpResponse<String> response = change(browser, TestUsers.PASSWORD, NEW_PASSWORD);
        Faults.clear(jdbc);

        assertThat(response.statusCode()).isEqualTo(500);
        assertNothingChanged(user, browser, before);
    }

    @Test
    void theRetryChangesThePasswordWithExactlyOneEvent() {
        var user = users.create("LEGAL_STAFF");
        Browser browser = signedIn(user);
        Faults.failAuditWrite(jdbc, "PASSWORD_CHANGE");
        assertThat(change(browser, TestUsers.PASSWORD, NEW_PASSWORD).statusCode()).isEqualTo(500);
        Faults.clear(jdbc);

        assertThat(change(browser, TestUsers.PASSWORD, NEW_PASSWORD).statusCode()).isEqualTo(204);

        assertThat(successEvents(user.id())).isEqualTo(1);
        assertThat(new Browser(port).login(user.username(), NEW_PASSWORD).statusCode()).isEqualTo(200);
        assertThat(new Browser(port).login(user.username(), TestUsers.PASSWORD).statusCode()).isEqualTo(401);
        assertThat(browser.get("/api/v1/me").statusCode()).as("this session continues").isEqualTo(200);
    }

    @Test
    void otherSessionsOfTheUserAreRevokedByASuccessfulChange() {
        var user = users.create("LEGAL_STAFF");
        Browser changing = signedIn(user);
        Browser other = signedIn(user);

        assertThat(change(changing, TestUsers.PASSWORD, NEW_PASSWORD).statusCode()).isEqualTo(204);

        assertThat(changing.get("/api/v1/me").statusCode()).isEqualTo(200);
        assertThat(other.get("/api/v1/me").statusCode()).isEqualTo(401);
    }

    @Test
    void aWrongCurrentPasswordIsStillRecordedAsAFailureEvenWhenSuccessEventsCannotBeWritten() {
        var user = users.create("LEGAL_STAFF");
        Browser browser = signedIn(user);
        Faults.failAuditWrite(jdbc, "PASSWORD_CHANGE");

        HttpResponse<String> response = change(browser, "not my current password", NEW_PASSWORD);
        Faults.clear(jdbc);

        assertThat(response.statusCode()).isEqualTo(400);
        List<String> outcomes = jdbc.queryForList("""
                select outcome || ':' || reason from audit_event where action = 'PASSWORD_CHANGE' and target_id = ?""",
                String.class, user.id().toString());
        assertThat(outcomes).containsExactly("FAILURE:BAD_CURRENT_PASSWORD");
    }

    @Test
    void noSecretEntersTheSuccessEvent() {
        var user = users.create("LEGAL_STAFF");
        Browser browser = signedIn(user);
        assertThat(change(browser, TestUsers.PASSWORD, NEW_PASSWORD).statusCode()).isEqualTo(204);

        Map<String, Object> event = jdbc.queryForMap("""
                select * from audit_event where action = 'PASSWORD_CHANGE' and target_id = ?""", user.id().toString());
        String everything = event.values().toString();
        assertThat(everything).doesNotContain(NEW_PASSWORD).doesNotContain(TestUsers.PASSWORD)
                .doesNotContain(users.passwordHash(user.id())).doesNotContain("$2");
        assertThat(event.get("outcome")).isEqualTo("SUCCESS");
    }
}
