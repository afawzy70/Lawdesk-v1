package com.lawdesk.users;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Reads and changes the account data that authentication needs (LD-007, LD-008). */
@Repository
public class UserAccounts {

    /** Actor recorded for changes the system makes itself, such as the first administrator. */
    public static final UUID SYSTEM = new UUID(0, 0);

    /** LD-008.3: failures before a lock, and how long it lasts. */
    static final int MAX_FAILURES = 5;
    static final String LOCK_DURATION = "15 minutes";

    private static final String COLUMNS = """
            id, username, email, display_name, locale, password_hash, enabled, must_change_password, locked_until""";

    private final JdbcTemplate jdbc;

    UserAccounts(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** By username or email, ignoring case. Usernames cannot contain '@' and emails must, so at most one matches. */
    public Optional<Account> findByLogin(String login) {
        return one("select " + COLUMNS + " from app_user where lower(username) = lower(?) or lower(email) = lower(?)",
                login, login);
    }

    public Optional<Account> findById(UUID id) {
        return one("select " + COLUMNS + " from app_user where id = ?", id);
    }

    public long count() {
        return jdbc.queryForObject("select count(*) from app_user", Long.class);
    }

    /**
     * Counts a failed login in one statement, so concurrent attempts all count; the fifth consecutive failure locks the
     * account. A failure after an expired lock starts a new count.
     */
    public void recordFailure(UUID id) {
        jdbc.update("""
                update app_user set
                    failed_attempts = case when locked_until is not null and locked_until <= now() then 1
                                           else failed_attempts + 1 end,
                    locked_until = case when locked_until is not null and locked_until <= now() then null
                                        when failed_attempts + 1 >= ? then now() + cast(? as interval)
                                        else locked_until end
                where id = ?""", MAX_FAILURES, LOCK_DURATION, id);
    }

    public void recordSuccess(UUID id) {
        jdbc.update("update app_user set failed_attempts = 0, locked_until = null where id = ?", id);
    }

    public void changePassword(UUID id, String passwordHash, boolean mustChange, UUID actor) {
        jdbc.update("""
                update app_user set password_hash = ?, must_change_password = ?, password_changed_at = now(),
                    updated_at = now(), updated_by = ?, version = version + 1 where id = ?""",
                passwordHash, mustChange, actor, id);
    }

    @Transactional
    public UUID createAdministrator(String username, String email, String passwordHash) {
        UUID id = jdbc.queryForObject("""
                insert into app_user (username, email, display_name, password_hash, must_change_password, created_by, updated_by)
                values (?, ?, 'Administrator', ?, true, ?, ?) returning id""", UUID.class, username, email,
                passwordHash, SYSTEM, SYSTEM);
        jdbc.update("insert into user_role (user_id, role_id) select ?, id from app_role where code = 'ADMIN'", id);
        return id;
    }

    private Optional<Account> one(String sql, Object... args) {
        List<Account> found = jdbc.query(sql, (rs, row) -> account(rs), args);
        return found.stream().findFirst();
    }

    private Account account(ResultSet rs) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        Timestamp locked = rs.getTimestamp("locked_until");
        Set<String> roles = new TreeSet<>(jdbc.queryForList(
                "select r.code from user_role ur join app_role r on r.id = ur.role_id where ur.user_id = ?",
                String.class, id));
        Set<String> permissions = new TreeSet<>(jdbc.queryForList("""
                select distinct rp.permission from user_role ur join role_permission rp on rp.role_id = ur.role_id
                where ur.user_id = ?""", String.class, id));
        return new Account(id, rs.getString("username"), rs.getString("email"), rs.getString("display_name"),
                rs.getString("locale"), rs.getString("password_hash"), rs.getBoolean("enabled"),
                rs.getBoolean("must_change_password"), locked == null ? null : locked.toInstant(), roles, permissions);
    }
}
