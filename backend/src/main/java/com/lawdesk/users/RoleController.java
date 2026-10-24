package com.lawdesk.users;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lawdesk.security.Permission;

/** {@code /api/v1/roles}: roles and their permissions; listing is open to role viewers and to user managers. */
@RestController
@RequestMapping("/api/v1/roles")
class RoleController {

    private final JdbcTemplate jdbc;

    RoleController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    @PreAuthorize(Permission.Codes.ROLE_VIEW + " or " + Permission.Codes.USER_MANAGE)
    List<RoleView> list() {
        return jdbc.query("""
                select r.id, r.code, r.name, r.system,
                       array(select rp.permission from role_permission rp where rp.role_id = r.id order by rp.permission)
                           as permissions
                from app_role r order by r.code""", (rs, row) -> new RoleView(rs.getObject("id", UUID.class),
                rs.getString("code"), rs.getString("name"), rs.getBoolean("system"),
                List.of((String[]) rs.getArray("permissions").getArray())));
    }
}
