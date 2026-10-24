package com.lawdesk.users;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lawdesk.security.CurrentActor;
import com.lawdesk.security.Permission;

/**
 * {@code /api/v1/roles}: roles and their permissions (LD-009.1). Reading is open to role viewers and to user managers,
 * who assign roles; changing roles needs ROLE_MANAGE.
 */
@RestController
@RequestMapping("/api/v1/roles")
class RoleController {

    private static final String READ = Permission.Codes.ROLE_VIEW + " or " + Permission.Codes.USER_MANAGE;

    private final RoleAdministration roles;
    private final CurrentActor actor;

    RoleController(RoleAdministration roles, CurrentActor actor) {
        this.roles = roles;
        this.actor = actor;
    }

    @GetMapping
    @PreAuthorize(READ)
    List<RoleView> list() {
        return roles.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ)
    RoleView get(@PathVariable UUID id) {
        return roles.get(id);
    }

    @PostMapping
    @PreAuthorize(Permission.Codes.ROLE_MANAGE)
    ResponseEntity<RoleView> create(@Valid @RequestBody RoleRequests.Create request) {
        RoleView created = roles.create(request, actor.require());
        return ResponseEntity.created(URI.create("/api/v1/roles/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Permission.Codes.ROLE_MANAGE)
    RoleView update(@PathVariable UUID id, @Valid @RequestBody RoleRequests.Update request) {
        return roles.update(id, request, actor.require());
    }
}
