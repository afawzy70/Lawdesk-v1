package com.lawdesk.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.lawdesk.platform.PostgresTestSupport;
import com.lawdesk.users.AdminBootstrap;

/** LD-008.4: the first administrator comes from a password file outside source control, and only into an empty system. */
@SpringBootTest
class AdminBootstrapTest extends PostgresTestSupport {

    private static final String PASSWORD = "first admin passphrase";

    @DynamicPropertySource
    static void bootstrap(DynamicPropertyRegistry registry) {
        registry.add("lawdesk.bootstrap.admin.username", () -> "Owner");
        registry.add("lawdesk.bootstrap.admin.email", () -> "owner@example.invalid");
        registry.add("lawdesk.bootstrap.admin.password-file", () -> passwordFile(PASSWORD).toString());
    }

    private static Path passwordFile(String content) {
        try {
            Path file = Files.createTempFile("lawdesk-admin", ".txt");
            Files.writeString(file, content + "\n");
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private AdminBootstrap bootstrap;

    @Test
    void anEmptySystemGetsOneAdministratorWhoMustChangeThePassword() {
        assertThat(jdbc.queryForObject("select count(*) from app_user", Integer.class)).isEqualTo(1);
        var admin = jdbc.queryForMap("select id, username, email, enabled, must_change_password, password_hash from app_user");
        assertThat(admin.get("username")).isEqualTo("owner");
        assertThat(admin.get("email")).isEqualTo("owner@example.invalid");
        assertThat(admin.get("enabled")).isEqualTo(true);
        assertThat(admin.get("must_change_password")).isEqualTo(true);
        assertThat(encoder.matches(PASSWORD, (String) admin.get("password_hash"))).isTrue();
        assertThat(jdbc.queryForList("select r.code from user_role ur join app_role r on r.id = ur.role_id where ur.user_id = ?",
                String.class, admin.get("id"))).containsExactly("ADMIN");
    }

    @Test
    void runningAgainChangesNothingOnceAUserExists() {
        bootstrap.run(null);

        assertThat(jdbc.queryForObject("select count(*) from app_user", Integer.class)).isEqualTo(1);
    }

    @Test
    void aBootstrapPasswordMustFollowThePolicy() {
        AdminBootstrap weak = new AdminBootstrap(null, null, null, null, "owner2", "owner2@example.invalid",
                passwordFile("too short").toString());

        assertThatThrownBy(() -> weak.validatedPassword()).isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("too short");
    }
}
