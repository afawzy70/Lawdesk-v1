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

    /** Enabled users, the ones work can be assigned to, by display name. */
    public List<UserSummary> active() {
        return jdbc.query(SELECT + " where enabled order by lower(display_name), username", (rs, n) -> summary(rs));
    }

    private static UserSummary summary(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new UserSummary(rs.getObject("id", UUID.class), rs.getString("username"), rs.getString("display_name"),
                rs.getBoolean("enabled"));
    }
}
