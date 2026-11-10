package com.lawdesk.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.SQLTransientConnectionException;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import com.lawdesk.security.Browser;
import com.lawdesk.security.TestUsers;

/** QA-RT-011: a genuinely exhausted pool is answered 503 with Retry-After (not a blind 500), also outside MVC. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestUsers.class)
class PoolExhaustionTest extends PostgresTestSupport {

    @LocalServerPort
    private int port;

    @Autowired
    private TestUsers users;

    @Autowired
    private DataSource dataSource;

    @Test
    void anExhaustedPoolIsAnswered503WithRetryAfterEvenInTheSecurityFilters() throws Exception {
        TestUsers.Created user = users.create("LEGAL_MANAGER");
        Browser browser = new Browser(port);
        assertThat(browser.login(user.username(), TestUsers.PASSWORD).statusCode()).isEqualTo(200);

        List<Connection> held = new ArrayList<>();
        try {
            // Take every connection of the (production-sized) pool, then ask for something that needs one.
            while (true) {
                try {
                    held.add(dataSource.getConnection());
                } catch (SQLTransientConnectionException exhausted) {
                    break;
                }
            }
            HttpResponse<String> response = browser.get("/api/v1/me");

            assertThat(response.statusCode()).isEqualTo(503);
            assertThat(response.headers().firstValue("Retry-After")).contains("1");
            assertThat(response.headers().firstValue("X-Request-Id")).isPresent();
            assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("application/problem+json");
            assertThat(response.body()).contains("\"status\":503").doesNotContain("Hikari");
        } finally {
            for (Connection connection : held) {
                connection.close();
            }
        }
        assertThat(browser.get("/api/v1/me").statusCode()).as("recovers once connections are back").isEqualTo(200);
    }

}
