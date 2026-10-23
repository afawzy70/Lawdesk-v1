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
}
