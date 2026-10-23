package com.lawdesk.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lawdesk.audit.AuditLog;
import com.lawdesk.users.UserAccounts;

/**
 * Sign-in for the SPA (LD-007): the session cookie carries the sign-in, the CSRF cookie protects every unsafe request.
 * Every refusal looks the same to the caller; only the audit trail records why (LD-007.2, LD-011.2).
 */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    record LoginRequest(String login, String password) {
    }

    private final AuthenticationManager authentication;
    private final SecurityContextRepository contexts;
    private final UserAccounts accounts;
    private final AuditLog audit;

    AuthController(AuthenticationManager authentication, SecurityContextRepository contexts, UserAccounts accounts,
            AuditLog audit) {
        this.authentication = authentication;
        this.contexts = contexts;
        this.accounts = accounts;
        this.audit = audit;
    }

    /** Public: issues the XSRF-TOKEN cookie the SPA echoes in X-XSRF-TOKEN. */
    @GetMapping("/csrf")
    ResponseEntity<Void> csrf(CsrfToken token) {
        token.getToken();
        return ResponseEntity.noContent().build();
    }

    /** Public (it is how one signs in). */
    @PostMapping("/login")
    MeResponse login(@RequestBody(required = false) LoginRequest body, HttpServletRequest request,
            HttpServletResponse response) {
        String login = body == null || body.login() == null ? "" : body.login().strip();
        String password = body == null || body.password() == null ? "" : body.password();
        Authentication signedIn;
        try {
            signedIn = authentication.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(login, password));
        } catch (AuthenticationException e) {
            refused(login, e);
            throw new BadCredentialsException("refused");
        }
        LawdeskUser user = ((LawdeskUser) signedIn.getPrincipal()).withoutPassword();
        accounts.recordSuccess(user.id());
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        audit.record("LOGIN", AuditLog.Outcome.SUCCESS, user.id(), user.username(), "USER", user.id().toString(), null);
        return MeResponse.of(user);
    }

    private void refused(String login, AuthenticationException e) {
        var account = accounts.findByLogin(login);
        String reason;
        if (e instanceof LockedException) {
            reason = "LOCKED";
        } else if (e instanceof DisabledException) {
            reason = "DISABLED";
        } else if (account.isEmpty()) {
            reason = "UNKNOWN_USER";
        } else {
            reason = "BAD_CREDENTIALS";
            accounts.recordFailure(account.get().id());
        }
        audit.record("LOGIN", AuditLog.Outcome.FAILURE, account.map(a -> a.id()).orElse(null),
                account.map(a -> a.username()).orElse(login), "USER", account.map(a -> a.id().toString()).orElse(null),
                reason);
    }
}
