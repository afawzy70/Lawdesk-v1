package com.lawdesk.security;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lawdesk.api.FieldErrorException;
import com.lawdesk.audit.AuditLog;
import com.lawdesk.users.PasswordPolicy;
import com.lawdesk.users.UserAccounts;

/** The signed-in user's own profile and password (LD-007.6, LD-008.5). */
@RestController
@RequestMapping("/api/v1/me")
class MeController {

    record PasswordChange(String currentPassword, String newPassword) {
    }

    private final UserAccounts accounts;
    private final PasswordEncoder encoder;
    private final AuditLog audit;

    MeController(UserAccounts accounts, PasswordEncoder encoder, AuditLog audit) {
        this.accounts = accounts;
        this.encoder = encoder;
        this.audit = audit;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    MeResponse me(@AuthenticationPrincipal LawdeskUser user) {
        return MeResponse.of(user);
    }

    @PostMapping("/password")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<Void> changePassword(@AuthenticationPrincipal LawdeskUser user,
            @RequestBody(required = false) PasswordChange change) {
        String current = change == null ? null : change.currentPassword();
        String next = change == null ? null : change.newPassword();
        var account = accounts.findById(user.id()).orElseThrow();
        if (current == null || !encoder.matches(current, account.passwordHash())) {
            audit.record("PASSWORD_CHANGE", AuditLog.Outcome.FAILURE, user.id(), user.username(), "USER",
                    user.id().toString(), "BAD_CURRENT_PASSWORD");
            throw new FieldErrorException("currentPassword", "CurrentPassword");
        }
        if (!PasswordPolicy.accepts(next, account.username(), account.email())) {
            throw new FieldErrorException("newPassword", PasswordPolicy.CODE);
        }
        accounts.changePassword(user.id(), encoder.encode(next), false, user.id());
        audit.record("PASSWORD_CHANGE", AuditLog.Outcome.SUCCESS, user.id(), user.username(), "USER",
                user.id().toString(), null);
        return ResponseEntity.noContent().build();
    }
}
