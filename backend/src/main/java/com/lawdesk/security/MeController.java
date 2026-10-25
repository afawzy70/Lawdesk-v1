package com.lawdesk.security;

import java.util.OptionalLong;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lawdesk.api.FieldErrorException;
import com.lawdesk.audit.AuditLog;
import com.lawdesk.users.PasswordPolicy;
import com.lawdesk.users.UserAccounts;

/**
 * The signed-in user's own profile and password (LD-007.6, LD-008.5). A change raises the credential version, which
 * revokes every other session of the user; this session is kept with the new version (LD-008.7).
 */
@RestController
@RequestMapping("/api/v1/me")
class MeController {

    record PasswordChange(String currentPassword, String newPassword) {
    }

    private final UserAccounts accounts;
    private final PasswordEncoder encoder;
    private final AuditLog audit;
    private final SecurityContextRepository contexts;
    private final SessionRevocation revocation;
    private final TransactionTemplate transaction;

    MeController(UserAccounts accounts, PasswordEncoder encoder, AuditLog audit, SecurityContextRepository contexts,
            SessionRevocation revocation, TransactionTemplate transaction) {
        this.transaction = transaction;
        this.accounts = accounts;
        this.encoder = encoder;
        this.audit = audit;
        this.contexts = contexts;
        this.revocation = revocation;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    MeResponse me(@AuthenticationPrincipal LawdeskUser user) {
        return MeResponse.of(user);
    }

    @PostMapping("/password")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<Void> changePassword(@AuthenticationPrincipal LawdeskUser user,
            @RequestBody(required = false) PasswordChange change, HttpServletRequest request,
            HttpServletResponse response) {
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
        // Only if no reset, disablement or other change committed since this session's request was authenticated.
        // QAD-043: the change and its SUCCESS event commit together; the session is renewed only after that commit.
        String hash = encoder.encode(next);
        OptionalLong version = transaction.execute(status -> {
            OptionalLong changed = accounts.changePassword(user.id(), hash, false, user.id(), user.credentialVersion());
            if (changed.isPresent()) {
                audit.recordWithChange("PASSWORD_CHANGE", user.id(), user.username(), "USER", user.id().toString(),
                        null);
            }
            return changed;
        });
        if (version == null || version.isEmpty()) {
            revocation.refuse(request, user);
            throw new SessionRevocation.SessionRevokedException();
        }
        LawdeskUser kept = user.withCredentialVersion(version.getAsLong());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(kept, null, kept.getAuthorities()));
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        return ResponseEntity.noContent().build();
    }
}
