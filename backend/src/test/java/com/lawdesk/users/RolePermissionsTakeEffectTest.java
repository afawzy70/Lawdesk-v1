package com.lawdesk.users;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.lawdesk.platform.PostgresTestSupport;
import com.lawdesk.security.Browser;
import com.lawdesk.security.TestUsers;

/** LD-007.7 with LD-009.1: a change to a role's permissions reaches signed-in holders on their next request. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestUsers.class)
class RolePermissionsTakeEffectTest extends PostgresTestSupport {

    @LocalServerPort
    private int port;

    @Autowired
    private TestUsers users;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void holdersOfAChangedRoleGetTheNewPermissionsWithoutSigningInAgain() {
        String code = "R" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(Locale.ROOT);
        Browser admin = new Browser(port);
        var administrator = users.create("ADMIN");
        assertThat(admin.login(administrator.username(), TestUsers.PASSWORD).statusCode()).isEqualTo(200);
        var created = admin.post("/api/v1/roles", """
                {"code":"%s","name":"Readers","permissions":["CASE_VIEW"]}""".formatted(code));
        assertThat(created.statusCode()).isEqualTo(201);
        String id = jdbc.queryForObject("select id::text from app_role where code = ?", String.class, code);

        var holder = users.create(code);
        Browser browser = new Browser(port);
        browser.login(holder.username(), TestUsers.PASSWORD);
        assertThat(browser.get("/api/v1/courts").statusCode()).isEqualTo(403);

        var changed = admin.put("/api/v1/roles/" + id, """
                {"name":"Readers","permissions":["CASE_VIEW","COURT_VIEW"],"version":0}""");
        assertThat(changed.statusCode()).isEqualTo(200);

        assertThat(browser.get("/api/v1/me").body()).contains("\"COURT_VIEW\"");
        assertThat(browser.get("/api/v1/courts").statusCode()).isEqualTo(200);
    }
}
