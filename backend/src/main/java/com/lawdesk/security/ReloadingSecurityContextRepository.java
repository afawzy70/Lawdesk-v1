package com.lawdesk.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.DeferredSecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

import com.lawdesk.users.UserAccounts;

/**
 * Keeps the session's sign-in, but rebuilds the user from the database on every request (LD-007.7): a disabled or
 * deleted user is signed out at once, and changed roles apply to the next request. A lock only stops new sign-ins.
 */
public class ReloadingSecurityContextRepository implements SecurityContextRepository {

    private final HttpSessionSecurityContextRepository sessions = new HttpSessionSecurityContextRepository();
    private final UserAccounts accounts;

    public ReloadingSecurityContextRepository(UserAccounts accounts) {
        this.accounts = accounts;
    }

    @Override
    public DeferredSecurityContext loadDeferredContext(HttpServletRequest request) {
        DeferredSecurityContext stored = sessions.loadDeferredContext(request);
        return new DeferredSecurityContext() {
            private SecurityContext context;

            @Override
            public SecurityContext get() {
                if (context == null) {
                    context = reload(stored.get());
                }
                return context;
            }

            @Override
            public boolean isGenerated() {
                return stored.isGenerated();
            }
        };
    }

    private SecurityContext reload(SecurityContext stored) {
        Authentication authentication = stored.getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LawdeskUser user)) {
            return stored;
        }
        SecurityContext fresh = SecurityContextHolder.createEmptyContext();
        accounts.findById(user.id()).map(LawdeskUser::of).filter(LawdeskUser::isEnabled)
                .map(LawdeskUser::withoutPassword)
                .ifPresent(current -> fresh.setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(current, null, current.getAuthorities())));
        return fresh;
    }

    @Override
    @SuppressWarnings("deprecation")
    public SecurityContext loadContext(org.springframework.security.web.context.HttpRequestResponseHolder holder) {
        return reload(sessions.loadContext(holder));
    }

    @Override
    public void saveContext(SecurityContext context, HttpServletRequest request, HttpServletResponse response) {
        sessions.saveContext(context, request, response);
    }

    @Override
    public boolean containsContext(HttpServletRequest request) {
        return sessions.containsContext(request);
    }
}
