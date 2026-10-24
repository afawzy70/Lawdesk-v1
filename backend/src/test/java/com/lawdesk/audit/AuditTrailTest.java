package com.lawdesk.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.lawdesk.api.ContractTestController;
import com.lawdesk.platform.PostgresTestSupport;
import com.lawdesk.security.Browser;
import com.lawdesk.security.TestUsers;

/** LD-011: security events are recorded with actor, outcome, reason and request id, and never with secrets. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestUsers.class, ContractTestController.class})
class AuditTrailTest extends PostgresTestSupport {

    @LocalServerPort
    private int port;

    @Autowired
    private TestUsers users;

    @Autowired
    private JdbcTemplate jdbc;

    private List<Map<String, Object>> eventsFor(String username) {
        return jdbc.queryForList("""
                select action, outcome, reason, actor_id, actor_username, target_type, target_id, request_id
                from audit_event where actor_username = ? order by occurred_at, id""", username);
    }

    @Test
    void loginLogoutAndPasswordChangeAreRecorded() {
        var user = users.create("LEGAL_STAFF");
        Browser browser = new Browser(port);
        browser.login(user.username(), "wrong password here");
        browser.login(user.username(), TestUsers.PASSWORD);
        browser.post("/api/v1/me/password", "{\"currentPassword\":\"%s\",\"newPassword\":\"another long passphrase\"}"
                .formatted(TestUsers.PASSWORD));
        browser.post("/api/v1/auth/logout", "");

        var events = eventsFor(user.username());

        assertThat(events).extracting(e -> e.get("action") + ":" + e.get("outcome") + ":" + e.get("reason"))
                .containsExactly("LOGIN:FAILURE:BAD_CREDENTIALS", "LOGIN:SUCCESS:null", "PASSWORD_CHANGE:SUCCESS:null",
                        "LOGOUT:SUCCESS:null");
        assertThat(events).allSatisfy(e -> assertThat((String) e.get("request_id")).isNotBlank());
        assertThat(events.get(1).get("actor_id")).isEqualTo(user.id());
    }

    @Test
    void refusalReasonsAreRecordedButNeverTheirSecrets() {
        var disabled = users.create("LEGAL_STAFF");
        users.disable(disabled.id());
        new Browser(port).login(disabled.username(), TestUsers.PASSWORD);
        String unknown = "ghost-" + disabled.username();
        new Browser(port).login(unknown, "a secret that must not be logged");

        assertThat(eventsFor(disabled.username())).extracting(e -> e.get("reason")).containsExactly("DISABLED");
        var ghost = eventsFor(unknown);
        assertThat(ghost).extracting(e -> e.get("reason")).containsExactly("UNKNOWN_USER");
        assertThat(ghost.get(0).get("actor_id")).isNull();
        String everything = jdbc.queryForList("select * from audit_event").toString();
        assertThat(everything).doesNotContain("a secret that must not be logged").doesNotContain(TestUsers.PASSWORD)
                .doesNotContain("$2a$").doesNotContain("$2b$");
    }

    @Test
    void anOverlongUnknownLoginIsTruncated() {
        String login = "x".repeat(300);
        new Browser(port).login(login, "whatever password");

        Integer longest = jdbc.queryForObject("select max(length(actor_username)) from audit_event", Integer.class);
        assertThat(longest).isLessThanOrEqualTo(64);
    }

    @Test
    void forbiddenAccessIsRecordedAsDenied() {
        var user = users.create("LEGAL_STAFF");
        Browser browser = new Browser(port);
        browser.login(user.username(), TestUsers.PASSWORD);

        assertThat(browser.get("/api/v1/contract-test/me").statusCode()).isEqualTo(200);
        jdbc.update("delete from role_permission where role_id = (select id from app_role where code = 'LEGAL_STAFF') and permission = 'CASE_VIEW'");
        try {
            assertThat(browser.get("/api/v1/contract-test/me").statusCode()).isEqualTo(403);
        } finally {
            jdbc.update("insert into role_permission (role_id, permission) select id, 'CASE_VIEW' from app_role where code = 'LEGAL_STAFF'");
        }

        assertThat(eventsFor(user.username())).extracting(e -> e.get("action") + ":" + e.get("outcome") + ":" + e.get("target_id"))
                .contains("ACCESS_DENIED:DENIED:GET /api/v1/contract-test/me");
    }

    @Test
    void theDatabaseRefusesToChangeOrDeleteAuditEvents() {
        new Browser(port).login("nobody-at-all", "some password");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update("update audit_event set reason = 'X'"))
                .hasMessageContaining("append-only");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update("delete from audit_event"))
                .hasMessageContaining("append-only");
    }

    @Test
    void theApplicationCannotChangeOrDeleteAuditEvents() {
        assertThat(AuditLog.class.getMethods()).extracting(java.lang.reflect.Method::getName)
                .doesNotContain("update", "delete", "remove");
    }
}
