package com.lawdesk.users;

import java.net.URI;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lawdesk.api.PageResponse;
import com.lawdesk.security.CurrentActor;
import com.lawdesk.security.Permission;

/** {@code /api/v1/users}: user administration (LD-009) and password reset (LD-008.6). */
@RestController
@RequestMapping("/api/v1/users")
class UserController {

    private final UserAdministration users;
    private final CurrentActor actor;

    UserController(UserAdministration users, CurrentActor actor) {
        this.users = users;
        this.actor = actor;
    }

    @GetMapping
    @PreAuthorize(Permission.Codes.USER_VIEW)
    PageResponse<UserView> list(@RequestParam(required = false) String q, Pageable page) {
        return PageResponse.from(users.list(q, page));
    }

    @GetMapping("/{id}")
    @PreAuthorize(Permission.Codes.USER_VIEW)
    UserView get(@PathVariable UUID id) {
        return users.get(id);
    }

    @PostMapping
    @PreAuthorize(Permission.Codes.USER_MANAGE)
    ResponseEntity<UserView> create(@Valid @RequestBody UserRequests.Create request, Authentication authentication) {
        UserView created = users.create(request, actor.require(), authorities(authentication));
        return ResponseEntity.created(URI.create("/api/v1/users/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Permission.Codes.USER_MANAGE)
    UserView update(@PathVariable UUID id, @Valid @RequestBody UserRequests.Update request) {
        return users.update(id, request, actor.require());
    }

    @PutMapping("/{id}/enabled")
    @PreAuthorize(Permission.Codes.USER_MANAGE)
    UserView enabled(@PathVariable UUID id, @Valid @RequestBody UserRequests.Enabled request) {
        return users.setEnabled(id, request.enabled(), actor.require());
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize(Permission.Codes.USER_MANAGE)
    UserView roles(@PathVariable UUID id, @Valid @RequestBody UserRequests.Roles request,
            Authentication authentication) {
        return users.setRoles(id, request.roles(), actor.require(), authorities(authentication));
    }

    @PostMapping("/{id}/password-reset")
    @PreAuthorize(Permission.Codes.USER_MANAGE)
    ResponseEntity<Void> resetPassword(@PathVariable UUID id, @RequestBody UserRequests.PasswordReset request) {
        users.resetPassword(id, request.temporaryPassword(), actor.require());
        return ResponseEntity.noContent().build();
    }

    private static Set<String> authorities(Authentication authentication) {
        return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }
}
