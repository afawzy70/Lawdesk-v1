package com.lawdesk.users;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * architecture/CONTRACTS.md §8: the names of users, for the modules that assign work to them (Phase 5 tasks) or report
 * on it (Phase 6 workload). Those modules never read the users table; this gives them no email, password or role.
 */
@Component
public class UserDirectory {

    public record UserSummary(UUID id, String username, String displayName, boolean enabled) {
    }

    private static final String SELECT = "select id, username, display_name, enabled from app_user";

    private final JdbcTemplate jdbc;

    UserDirectory(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<UserSummary> find(UUID id) {
        return jdbc.query(SELECT + " where id = ?", (rs, n) -> summary(rs), id).stream().findFirst();
    }

    /** The display names of these users in one statement (users that do not exist are left out). */
    public java.util.Map<UUID, String> displayNames(java.util.Collection<UUID> ids) {
        java.util.Map<UUID, String> names = new java.util.HashMap<>();
        if (ids.isEmpty()) {
            return names;
        }
        jdbc.query("select id, display_name from app_user where id in (select unnest(cast(? as uuid[])))", rs -> {
            names.put(rs.getObject("id", UUID.class), rs.getString("display_name"));
        }, ids.stream().map(UUID::toString).collect(java.util.stream.Collectors.joining(",", "{", "}")));
        return names;
    }

    /** Enabled users, the ones work can be assigned to, by display name. */
    public List<UserSummary> active() {
        return jdbc.query(SELECT + " where enabled order by lower(display_name), username", (rs, n) -> summary(rs));
    }

    /** The legal department (الإدارة القانونية): enabled holders of the legal manager or legal staff role. */
    private static final String LEGAL_MEMBER = """
            enabled and exists (select 1 from user_role ur join app_role r on r.id = ur.role_id
                                where ur.user_id = app_user.id and r.code in ('LEGAL_MANAGER', 'LEGAL_STAFF'))""";

    /** LD-045: the active members of the legal department, who can be the specialist or the alternate of a case. */
    public List<UserSummary> legalDepartmentMembers() {
        return jdbc.query(SELECT + " where " + LEGAL_MEMBER + " order by lower(display_name), username",
                (rs, n) -> summary(rs));
    }

    /** True when {@code id} is an enabled member of the legal department right now. */
    public boolean isLegalDepartmentMember(UUID id) {
        return id != null && Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists (select 1 from app_user where id = ? and " + LEGAL_MEMBER + ")", Boolean.class, id));
    }

    private static UserSummary summary(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new UserSummary(rs.getObject("id", UUID.class), rs.getString("username"), rs.getString("display_name"),
                rs.getBoolean("enabled"));
    }
}
