package com.lawdesk.users;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.api.BusinessRuleException;
import com.lawdesk.api.ConflictException;
import com.lawdesk.api.FieldErrorException;
import com.lawdesk.api.NotFoundException;
import com.lawdesk.audit.AuditLog;
import com.lawdesk.security.ActorRef;
import com.lawdesk.security.Permission;

/**
 * User administration (LD-009) and administrative password reset (LD-008.6). Guards against escalation: nobody changes
 * their own roles or status, only role managers grant roles that manage users or roles, and the last enabled
 * administrator stays one. Changes that could remove an administrator lock the ADMIN role row first, so concurrent
 * requests cannot both pass the last-administrator check.
 */
@Service
public class UserAdministration {

    private static final String ADMIN = "ADMIN";
    private static final Set<String> MANAGING = Set.of(Permission.USER_MANAGE.name(), Permission.ROLE_MANAGE.name());

    private static final String SELECT = """
            select u.id, u.username, u.email, u.display_name, u.locale, u.enabled, u.must_change_password,
                   (u.locked_until is not null and u.locked_until > now()) as locked, u.created_at, u.updated_at, u.version,
                   array(select r.code from user_role ur join app_role r on r.id = ur.role_id
                         where ur.user_id = u.id order by r.code) as roles
            from app_user u""";

    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final AuditLog audit;

    UserAdministration(JdbcTemplate jdbc, PasswordEncoder encoder, AuditLog audit) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.audit = audit;
    }

    public Page<UserView> list(String query, Pageable page) {
        String filter = query == null || query.isBlank() ? null : "%" + escapeLike(query.strip().toLowerCase(Locale.ROOT)) + "%";
        String where = filter == null ? "" : " where lower(u.username) like ? escape '\\' or lower(u.email) like ? "
                + "escape '\\' or lower(u.display_name) like ? escape '\\'";
        Object[] args = filter == null ? new Object[0] : new Object[] {filter, filter, filter};
        long total = jdbc.queryForObject("select count(*) from app_user u" + where, Long.class, args);
        Object[] paged = Arrays.copyOf(args, args.length + 2);
        paged[args.length] = page.getPageSize();
        paged[args.length + 1] = page.getOffset();
        List<UserView> items = jdbc.query(SELECT + where + " order by lower(u.username) limit ? offset ?",
                (rs, row) -> view(rs), paged);
        return new PageImpl<>(items, page, total);
    }

    public UserView get(UUID id) {
        return find(id).orElseThrow(() -> new NotFoundException("user"));
    }

    @Transactional
    public UserView create(UserRequests.Create request, ActorRef actor, Set<String> actorAuthorities) {
        String username = request.username().toLowerCase(Locale.ROOT);
        if (!PasswordPolicy.accepts(request.temporaryPassword(), username, request.email())) {
            throw new FieldErrorException("temporaryPassword", PasswordPolicy.CODE);
        }
        List<UUID> roles = roleIds(request.roles());
        requireMayGrant(request.roles(), actorAuthorities);
        UUID id;
        try {
            id = jdbc.queryForObject("""
                    insert into app_user (username, email, display_name, locale, password_hash, must_change_password,
                                          created_by, updated_by)
                    values (?, ?, ?, ?, ?, true, ?, ?) returning id""", UUID.class, username, request.email().strip(),
                    request.displayName().strip(), request.locale(), encoder.encode(request.temporaryPassword()),
                    actor.id(), actor.id());
        } catch (DuplicateKeyException e) {
            throw new ConflictException("username or email already used");
        }
        for (UUID role : roles) {
            jdbc.update("insert into user_role (user_id, role_id) values (?, ?)", id, role);
        }
        record("USER_CREATE", actor, id, null);
        return get(id);
    }

    @Transactional
    public UserView update(UUID id, UserRequests.Update request, ActorRef actor) {
        int changed;
        try {
            changed = jdbc.update("""
                    update app_user set email = ?, display_name = ?, locale = ?, updated_at = now(), updated_by = ?,
                        version = version + 1
                    where id = ? and version = ?""", request.email().strip(), request.displayName().strip(),
                    request.locale(), actor.id(), id, request.version());
        } catch (DuplicateKeyException e) {
            throw new ConflictException("email already used");
        }
        if (changed == 0) {
            get(id);
            throw new ConflictException("stale version");
        }
        record("USER_UPDATE", actor, id, null);
        return get(id);
    }

    @Transactional
    public UserView setEnabled(UUID id, boolean enabled, ActorRef actor) {
        requireNotSelf(id, actor);
        UserView user = get(id);
        if (!enabled && user.enabled() && user.roles().contains(ADMIN)) {
            requireAnotherEnabledAdministrator(id);
        }
        // LD-008.7: disabling revokes every session of the user; enabling again does not revive them.
        jdbc.update("""
                update app_user set enabled = ?, updated_at = now(), updated_by = ?, version = version + 1,
                    credential_version = credential_version + case when ? then 0 else 1 end
                where id = ?""", enabled, actor.id(), enabled, id);
        record(enabled ? "USER_ENABLE" : "USER_DISABLE", actor, id, null);
        return get(id);
    }

    @Transactional
    public UserView setRoles(UUID id, List<String> requested, ActorRef actor, Set<String> actorAuthorities) {
        requireNotSelf(id, actor);
        List<UUID> roles = roleIds(requested);
        UserView user = get(id);
        Set<String> added = new TreeSet<>(requested);
        added.removeAll(user.roles());
        requireMayGrant(added, actorAuthorities);
        if (user.enabled() && user.roles().contains(ADMIN) && !requested.contains(ADMIN)) {
            requireAnotherEnabledAdministrator(id);
        }
        jdbc.update("delete from user_role where user_id = ?", id);
        for (UUID role : roles) {
            jdbc.update("insert into user_role (user_id, role_id) values (?, ?)", id, role);
        }
        jdbc.update("update app_user set updated_at = now(), updated_by = ?, version = version + 1 where id = ?",
                actor.id(), id);
        record("USER_ROLES_CHANGE", actor, id, null);
        return get(id);
    }

    @Transactional
    public void resetPassword(UUID id, String temporaryPassword, ActorRef actor) {
        UserView user = get(id);
        if (!PasswordPolicy.accepts(temporaryPassword, user.username(), user.email())) {
            throw new FieldErrorException("temporaryPassword", PasswordPolicy.CODE);
        }
        jdbc.update("""
                update app_user set password_hash = ?, must_change_password = true, failed_attempts = 0,
                    locked_until = null, password_changed_at = now(), updated_at = now(), updated_by = ?,
                    version = version + 1, credential_version = credential_version + 1
                where id = ?""", encoder.encode(temporaryPassword), actor.id(), id);
        record("PASSWORD_RESET", actor, id, null);
    }

    private void requireNotSelf(UUID id, ActorRef actor) {
        if (id.equals(actor.id())) {
            throw new BusinessRuleException("SELF_CHANGE", "users cannot change their own roles or status");
        }
    }

    /** Roles that manage users or roles (and ADMIN, which holds every permission) need ROLE_MANAGE to grant. */
    private void requireMayGrant(Iterable<String> roles, Set<String> actorAuthorities) {
        if (actorAuthorities.contains(Permission.ROLE_MANAGE.name())) {
            return;
        }
        for (String role : roles) {
            boolean managing = ADMIN.equals(role) || !jdbc.queryForList("""
                    select rp.permission from role_permission rp join app_role r on r.id = rp.role_id
                    where r.code = ? and rp.permission in (?, ?)""", String.class, role,
                    Permission.USER_MANAGE.name(), Permission.ROLE_MANAGE.name()).isEmpty();
            if (managing) {
                throw new AccessDeniedException("granting " + role + " needs " + MANAGING);
            }
        }
    }

    private void requireAnotherEnabledAdministrator(UUID excluded) {
        jdbc.queryForObject("select id from app_role where code = ? for update", UUID.class, ADMIN);
        Integer others = jdbc.queryForObject("""
                select count(*) from app_user u join user_role ur on ur.user_id = u.id join app_role r on r.id = ur.role_id
                where r.code = ? and u.enabled and u.id <> ?""", Integer.class, ADMIN, excluded);
        if (others == null || others == 0) {
            throw new BusinessRuleException("LAST_ADMIN", "the last enabled administrator must stay one");
        }
    }

    private List<UUID> roleIds(List<String> codes) {
        Set<String> unique = new TreeSet<>(codes);
        List<UUID> ids = unique.isEmpty() ? List.of() : jdbc.queryForList(
                "select id from app_role where code in (" + String.join(",", unique.stream().map(c -> "?").toList()) + ")",
                UUID.class, unique.toArray());
        if (ids.size() != unique.size()) {
            throw new FieldErrorException("roles", "UnknownRole");
        }
        return ids;
    }

    private Optional<UserView> find(UUID id) {
        return jdbc.query(SELECT + " where u.id = ?", (rs, row) -> view(rs), id).stream().findFirst();
    }

    private void record(String action, ActorRef actor, UUID target, String reason) {
        audit.record(action, AuditLog.Outcome.SUCCESS, actor.id(), actor.username(), "USER", target.toString(), reason);
    }

    private static UserView view(ResultSet rs) throws SQLException {
        Array roles = rs.getArray("roles");
        return new UserView(rs.getObject("id", UUID.class), rs.getString("username"), rs.getString("email"),
                rs.getString("display_name"), rs.getString("locale"), rs.getBoolean("enabled"),
                rs.getBoolean("must_change_password"), rs.getBoolean("locked"),
                List.of((String[]) roles.getArray()), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(), rs.getLong("version"));
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
