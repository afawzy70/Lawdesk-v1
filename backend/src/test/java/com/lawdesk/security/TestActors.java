package com.lawdesk.security;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

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

    /**
     * Holds the given permissions plus, so that tests written before case-visibility scopes (LD-050) keep testing what
     * they were written for, the department-wide scope {@code ALL} and the right to edit in that scope. Tests of
     * visibility and of membership use {@link #scoped}, which grants exactly what it is given.
     */
    public static RequestPostProcessor as(UUID id, String username, Permission... permissions) {
        Permission[] withEdit = Stream.concat(Arrays.stream(permissions), Stream.of(Permission.CASE_EDIT_IN_SCOPE))
                .distinct().toArray(Permission[]::new);
        return scoped(id, username, List.of("ALL"), withEdit);
    }

    /** Holds the given permissions and exactly the given scope tokens ({@code ALL}, {@code UNIT:<id>}, {@code TYPE:<t>}). */
    public static RequestPostProcessor scoped(UUID id, String username, Collection<String> scopes,
            Permission... permissions) {
        ActorPrincipal principal = new TestPrincipal(id, username);
        var authorities = Stream.concat(Arrays.stream(permissions).map(Permission::name),
                scopes.stream().map(scope -> LawdeskUser.SCOPE_PREFIX + scope))
                .map(SimpleGrantedAuthority::new).toList();
        return authentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities));
    }

    /**
     * Signs the current test thread in as a department-wide user, for tests that call a read port (a report query)
     * directly. Ports refuse to answer without a signed-in user (LD-050). Call it again after a MockMvc request, which
     * clears the context when it ends.
     */
    public static void signIn() {
        signIn(DEFAULT_ID);
    }

    /** As {@link #signIn()} for a given user, which tests of data created by that user need. */
    public static void signIn(UUID id) {
        ActorPrincipal principal = new TestPrincipal(id, "test.user");
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null,
                        List.of(new SimpleGrantedAuthority(LawdeskUser.SCOPE_PREFIX + "ALL"),
                                new SimpleGrantedAuthority(Permission.CASE_EDIT_IN_SCOPE.name()))));
    }

    public static void signOut() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    private record TestPrincipal(UUID id, String username) implements ActorPrincipal {
    }
}
