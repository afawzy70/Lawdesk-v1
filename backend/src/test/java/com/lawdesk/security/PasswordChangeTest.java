package com.lawdesk.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import com.lawdesk.api.ContractTestController;
import com.lawdesk.platform.PostgresTestSupport;

/** LD-008.2 password policy and LD-008.5 forced password change. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestUsers.class, ContractTestController.class})
class PasswordChangeTest extends PostgresTestSupport {

    private static final String NEW_PASSWORD = "a brand new passphrase";

    @LocalServerPort
    private int port;

    @Autowired
    private TestUsers users;

    private Browser signedIn(TestUsers.Created user) {
        Browser browser = new Browser(port);
        assertThat(browser.login(user.username(), TestUsers.PASSWORD).statusCode()).isEqualTo(200);
        return browser;
    }

    private HttpResponse<String> change(Browser browser, String current, String next) {
        return browser.post("/api/v1/me/password",
                "{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}".formatted(current, next));
    }

    @Test
    void aUserWhoMustChangeThePasswordCanOnlyReachTheProfileAndTheChange() {
        var user = users.create("ADMIN");
        users.requirePasswordChange(user.id());
        Browser browser = signedIn(user);

        assertThat(browser.get("/api/v1/me").body()).contains("\"mustChangePassword\":true");
        HttpResponse<String> blocked = browser.get("/api/v1/contract-test/me");
        assertThat(blocked.statusCode()).isEqualTo(403);
        assertThat(blocked.body()).contains("\"rule\":\"PASSWORD_CHANGE_REQUIRED\"");

        assertThat(change(browser, TestUsers.PASSWORD, NEW_PASSWORD).statusCode()).isEqualTo(204);

        assertThat(browser.get("/api/v1/me").body()).contains("\"mustChangePassword\":false");
        assertThat(browser.get("/api/v1/contract-test/me").statusCode()).isEqualTo(200);
        assertThat(new Browser(port).login(user.username(), NEW_PASSWORD).statusCode()).isEqualTo(200);
        assertThat(new Browser(port).login(user.username(), TestUsers.PASSWORD).statusCode()).isEqualTo(401);
    }

    @Test
    void theCurrentPasswordMustBeRight() {
        var user = users.create("LEGAL_STAFF");
        Browser browser = signedIn(user);

        HttpResponse<String> response = change(browser, "not my current password", NEW_PASSWORD);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("\"field\":\"currentPassword\"").contains("\"code\":\"CurrentPassword\"");
        assertThat(new Browser(port).login(user.username(), TestUsers.PASSWORD).statusCode()).isEqualTo(200);
    }

    @Test
    void newPasswordsFollowThePolicy() {
        var user = users.create("LEGAL_STAFF");
        Browser browser = signedIn(user);

        for (String weak : new String[] {"short", "x".repeat(129), user.username() , user.email()}) {
            HttpResponse<String> response = change(browser, TestUsers.PASSWORD, weak);

            assertThat(response.statusCode()).as(weak).isEqualTo(400);
            assertThat(response.body()).contains("\"field\":\"newPassword\"").contains("\"code\":\"PasswordPolicy\"")
                    .doesNotContain(weak);
        }
    }
}
