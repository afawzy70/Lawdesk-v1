package com.lawdesk.users;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
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
            id, username, email, display_name, locale, password_hash, enabled, must_change_password, locked_until,
            credential_version, org_unit_id""";

    private final JdbcTemplate jdbc;

    UserAccounts(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** By username or email, ignoring case. Usernames cannot contain '@' and emails must, so at most one matches. */
    @Transactional(readOnly = true)
    public Optional<Account> findByLogin(String login) {
        return one("select " + COLUMNS + " from app_user where lower(username) = lower(?) or lower(email) = lower(?)",
                login, login);
    }

    @Transactional(readOnly = true)
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

    /**
     * Changes the password only while the credential version is still the one the caller's session signed in with,
     * and raises it (LD-008.7): a reset or another change committed in between wins and nothing is changed. Returns the
     * new version, or empty when the session was revoked meanwhile.
     */
    public OptionalLong changePassword(UUID id, String passwordHash, boolean mustChange, UUID actor,
            long expectedCredentialVersion) {
        List<Long> changed = jdbc.queryForList("""
                update app_user set password_hash = ?, must_change_password = ?, password_changed_at = now(),
                    updated_at = now(), updated_by = ?, version = version + 1,
                    credential_version = credential_version + 1
                where id = ? and credential_version = ? returning credential_version""", Long.class,
                passwordHash, mustChange, actor, id, expectedCredentialVersion);
        return changed.isEmpty() ? OptionalLong.empty() : OptionalLong.of(changed.get(0));
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
        // QA-RT-011: read the rows first and only then load roles, permissions and scopes. Running those queries inside
        // the row mapper held the result set's connection while asking the pool for another one, so N concurrent
        // requests used up a pool of N and each waited for the timeout (a 500). The reads are also one read-only
        // transaction (see the callers), so they share a single connection.
        List<AccountRow> rows = jdbc.query(sql, (rs, row) -> new AccountRow(rs.getObject("id", UUID.class),
                rs.getString("username"), rs.getString("email"), rs.getString("display_name"), rs.getString("locale"),
                rs.getString("password_hash"), rs.getBoolean("enabled"), rs.getBoolean("must_change_password"),
                rs.getTimestamp("locked_until"), rs.getLong("credential_version"),
                rs.getObject("org_unit_id", UUID.class)), args);
        return rows.stream().findFirst().map(this::account);
    }

    private record AccountRow(UUID id, String username, String email, String displayName, String locale,
            String passwordHash, boolean enabled, boolean mustChangePassword, Timestamp lockedUntil,
            long credentialVersion, UUID orgUnit) {
    }

    private Account account(AccountRow row) {
        UUID id = row.id();
        Timestamp locked = row.lockedUntil();
        Set<String> roles = new TreeSet<>(jdbc.queryForList(
                "select r.code from user_role ur join app_role r on r.id = ur.role_id where ur.user_id = ?",
                String.class, id));
        Set<String> permissions = new TreeSet<>(jdbc.queryForList("""
                select distinct rp.permission from user_role ur join role_permission rp on rp.role_id = ur.role_id
                where ur.user_id = ?""", String.class, id));
        return new Account(id, row.username(), row.email(), row.displayName(), row.locale(), row.passwordHash(),
                row.enabled(), row.mustChangePassword(), locked == null ? null : locked.toInstant(), roles, permissions,
                row.credentialVersion(), scopes(id, roles, row.orgUnit()));
    }

    /**
     * The scope tokens of a user: rows of their roles and of themselves. {@code OWN_UNIT} becomes the user's own unit
     * (nothing when the user has none), so the policy never has to look it up.
     */
    private Set<String> scopes(UUID id, Set<String> roles, UUID orgUnit) {
        Set<String> tokens = new TreeSet<>();
        jdbc.query("""
                select scope_type, scope_ref from access_scope
                where (subject_type = 'USER' and subject_ref = ?)
                   or (subject_type = 'ROLE' and subject_ref = any (cast(? as text[])))""", rs -> {
            String type = rs.getString("scope_type");
            switch (type) {
                case "ALL" -> tokens.add("ALL");
                case "ORG_UNIT" -> tokens.add("UNIT:" + rs.getString("scope_ref"));
                case "CASE_TYPE" -> tokens.add("TYPE:" + rs.getString("scope_ref"));
                case "OWN_UNIT" -> {
                    if (orgUnit != null) {
                        tokens.add("UNIT:" + orgUnit);
                    }
                }
                default -> { }
            }
        }, id.toString(), roles.toArray(String[]::new));
        return tokens;
    }
}
