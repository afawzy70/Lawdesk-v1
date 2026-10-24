package com.lawdesk.security;

import java.io.Serial;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.lawdesk.users.Account;

/**
 * The principal of a signed-in user. Authorities are permission codes (architecture/CONTRACTS.md §3); the ADMIN role
 * carries every code, including codes added after the role was seeded (LD-009.3).
 */
public record LawdeskUser(UUID id, String username, String email, String displayName, String locale,
        String passwordHash, boolean enabled, boolean mustChangePassword, Instant lockedUntil, Set<String> roles,
        Set<String> permissions) implements UserDetails, ActorPrincipal {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String ADMIN = "ADMIN";

    public static LawdeskUser of(Account account) {
        Set<String> granted = new TreeSet<>();
        if (account.roles().contains(ADMIN)) {
            Arrays.stream(Permission.values()).map(Enum::name).forEach(granted::add);
        } else {
            // Codes the application no longer knows are not granted.
            account.permissions().stream().filter(LawdeskUser::known).forEach(granted::add);
        }
        return new LawdeskUser(account.id(), account.username(), account.email(), account.displayName(),
                account.locale(), account.passwordHash(), account.enabled(), account.mustChangePassword(),
                account.lockedUntil(), Set.copyOf(account.roles()), Set.copyOf(granted));
    }

    private static boolean known(String code) {
        return Arrays.stream(Permission.values()).anyMatch(p -> p.name().equals(code));
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return permissions.stream().sorted().map(SimpleGrantedAuthority::new).toList();
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean isAccountNonLocked() {
        return lockedUntil == null || !lockedUntil.isAfter(Instant.now());
    }

    /** The principal kept in the session and in the context: without the password hash. */
    public LawdeskUser withoutPassword() {
        return new LawdeskUser(id, username, email, displayName, locale, null, enabled, mustChangePassword,
                lockedUntil, roles, permissions);
    }

    public List<String> sortedRoles() {
        return roles.stream().sorted().toList();
    }

    public List<String> sortedPermissions() {
        return permissions.stream().sorted().toList();
    }

    @Override
    public String toString() {
        return "LawdeskUser[" + username + "]";
    }
}
