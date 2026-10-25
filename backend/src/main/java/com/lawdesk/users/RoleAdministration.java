package com.lawdesk.users;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
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
 * Role administration (LD-009.1). Roles group permission codes; holders get a change on their next request, because
 * authorities are reloaded for every request (LD-007.7). Roles are never deleted, and the ADMIN role keeps every
 * permission.
 */
@Service
public class RoleAdministration {

    private static final String ADMIN = "ADMIN";
    private static final Set<String> KNOWN = Arrays.stream(Permission.values()).map(Enum::name)
            .collect(Collectors.toUnmodifiableSet());

    private static final String SELECT = """
            select r.id, r.code, r.name, r.system, r.version,
                   array(select rp.permission from role_permission rp where rp.role_id = r.id order by rp.permission)
                       as permissions
            from app_role r""";

    private final JdbcTemplate jdbc;
    private final AuditLog audit;

    RoleAdministration(JdbcTemplate jdbc, AuditLog audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    public List<RoleView> list() {
        return jdbc.query(SELECT + " order by r.code", (rs, row) -> view(rs));
    }

    public RoleView get(UUID id) {
        return find(id).orElseThrow(() -> new NotFoundException("role"));
    }

    @Transactional
    public RoleView create(RoleRequests.Create request, ActorRef actor) {
        Set<String> permissions = known(request.permissions());
        UUID id;
        try {
            id = jdbc.queryForObject("insert into app_role (code, name) values (?, ?) returning id", UUID.class,
                    request.code(), request.name().strip());
        } catch (DuplicateKeyException e) {
            throw new ConflictException("role code already used");
        }
        grant(id, permissions);
        record("ROLE_CREATE", actor, id);
        return get(id);
    }

    @Transactional
    public RoleView update(UUID id, RoleRequests.Update request, ActorRef actor) {
        Set<String> permissions = known(request.permissions());
        RoleView current = get(id);
        if (ADMIN.equals(current.code()) && !permissions.equals(KNOWN)) {
            throw new BusinessRuleException("ADMIN_ROLE_FIXED", "the administrator role keeps every permission");
        }
        int changed = jdbc.update("""
                update app_role set name = ?, updated_at = now(), version = version + 1
                where id = ? and version = ?""", request.name().strip(), id, request.version());
        if (changed == 0) {
            throw new ConflictException("stale version");
        }
        record("ROLE_UPDATE", actor, id);
        if (!permissions.equals(new TreeSet<>(current.permissions()))) {
            jdbc.update("delete from role_permission where role_id = ?", id);
            grant(id, permissions);
            record("ROLE_PERMISSIONS_CHANGE", actor, id);
        }
        return get(id);
    }

    private static Set<String> known(List<String> codes) {
        Set<String> unique = new TreeSet<>(codes);
        if (!KNOWN.containsAll(unique)) {
            throw new FieldErrorException("permissions", "UnknownPermission");
        }
        return unique;
    }

    private void grant(UUID role, Set<String> permissions) {
        for (String permission : permissions) {
            jdbc.update("insert into role_permission (role_id, permission) values (?, ?)", role, permission);
        }
    }

    private Optional<RoleView> find(UUID id) {
        return jdbc.query(SELECT + " where r.id = ?", (rs, row) -> view(rs), id).stream().findFirst();
    }

    private void record(String action, ActorRef actor, UUID target) {
        audit.recordWithChange(action, actor.id(), actor.username(), "ROLE", target.toString(), null);
    }

    private static RoleView view(ResultSet rs) throws SQLException {
        return new RoleView(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name"),
                rs.getBoolean("system"), List.of((String[]) rs.getArray("permissions").getArray()),
                rs.getLong("version"));
    }
}
