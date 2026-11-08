package com.lawdesk.security;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** The only way domain code reads who is acting; it never touches the SecurityContext itself. */
@Component
public class CurrentActor {

    /** The signed-in user; without one the request fails as unauthenticated (401). */
    public ActorRef require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof ActorPrincipal principal)) {
            throw new AuthenticationCredentialsNotFoundException("no signed-in user");
        }
        return new ActorRef(principal.id(), principal.username());
    }

    /** Whether the signed-in user holds {@code permission}; false without a signed-in user. */
    public boolean has(Permission permission) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(permission.name()));
    }

    /**
     * The case-visibility scope tokens of the signed-in user ({@code ALL}, {@code UNIT:<id>}, {@code TYPE:<type>};
     * LD-050), read from the same authorities as the permissions.
     */
    public java.util.Set<String> scopes() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return java.util.Set.of();
        }
        return authentication.getAuthorities().stream().map(a -> a.getAuthority())
                .filter(a -> a.startsWith(LawdeskUser.SCOPE_PREFIX)).map(a -> a.substring(LawdeskUser.SCOPE_PREFIX.length()))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
