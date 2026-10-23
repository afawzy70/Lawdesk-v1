package com.lawdesk.security;

import java.util.Arrays;
import java.util.UUID;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

/**
 * Signs a MockMvc request in as a user holding exactly the given permissions (architecture/CONTRACTS.md §3). The
 * principal is the same {@link ActorPrincipal} type real logins produce, so tests written before login exists keep
 * working after it.
 */
public final class TestActors {

    public static final UUID DEFAULT_ID = UUID.fromString("00000000-0000-4000-8000-000000000001");

    private TestActors() {
    }

    public static RequestPostProcessor as(Permission... permissions) {
        return as(DEFAULT_ID, "test.user", permissions);
    }

    public static RequestPostProcessor as(UUID id, String username, Permission... permissions) {
        ActorPrincipal principal = new TestPrincipal(id, username);
        var authorities = Arrays.stream(permissions).map(p -> new SimpleGrantedAuthority(p.name())).toList();
        return authentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities));
    }

    private record TestPrincipal(UUID id, String username) implements ActorPrincipal {
    }
}
