package com.lawdesk.security;

import java.time.Instant;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Creates users directly in the database for tests; usernames are unique per call, so tests share a database. */
@Component
public class TestUsers {

    public static final String PASSWORD = "correct horse battery";

    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;

    TestUsers(JdbcTemplate jdbc, PasswordEncoder encoder) {
        this.jdbc = jdbc;
        this.encoder = encoder;
    }

    public record Created(UUID id, String username, String email) {
    }

    public Created create(String role) {
        String username = "u" + UUID.randomUUID().toString().substring(0, 8);
        UUID id = jdbc.queryForObject("""
                insert into app_user (username, email, display_name, password_hash, enabled, locale, created_by, updated_by)
                values (?, ?, ?, ?, true, 'en', ?, ?) returning id""", UUID.class, username,
                username + "@example.invalid", "Test " + username, encoder.encode(PASSWORD), UUID.randomUUID(),
                UUID.randomUUID());
        if (role != null) {
            grant(id, role);
        }
        return new Created(id, username, username + "@example.invalid");
    }

    public void grant(UUID user, String role) {
        jdbc.update("insert into user_role (user_id, role_id) select ?, id from app_role where code = ?", user, role);
    }

    public void disable(UUID user) {
        jdbc.update("update app_user set enabled = false where id = ?", user);
    }

    public void requirePasswordChange(UUID user) {
        jdbc.update("update app_user set must_change_password = true where id = ?", user);
    }

    public void unlockAt(UUID user, Instant when) {
        jdbc.update("update app_user set locked_until = ? where id = ?", java.sql.Timestamp.from(when), user);
    }

    public int failedAttempts(UUID user) {
        return jdbc.queryForObject("select failed_attempts from app_user where id = ?", Integer.class, user);
    }

    public String passwordHash(UUID user) {
        return jdbc.queryForObject("select password_hash from app_user where id = ?", String.class, user);
    }
}
