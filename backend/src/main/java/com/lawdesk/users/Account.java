package com.lawdesk.users;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * A user as stored, with the codes of its roles and the permissions those roles grant. {@code credentialVersion} rises
 * with every reset, password change and disablement (LD-008.7); sessions signed in with an older one are revoked.
 * {@code scopes} are the tokens of the cases the user may see beyond their own (LD-050): {@code ALL}, {@code UNIT:<id>}
 * and {@code TYPE:<case type>}; the administrator role has none unless a row grants it.
 */
public record Account(UUID id, String username, String email, String displayName, String locale, String passwordHash,
        boolean enabled, boolean mustChangePassword, Instant lockedUntil, Set<String> roles, Set<String> permissions,
        long credentialVersion, Set<String> scopes) {

    public boolean lockedAt(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }
}
