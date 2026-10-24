package com.lawdesk.users;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** A user as stored, with the codes of its roles and the permissions those roles grant. */
public record Account(UUID id, String username, String email, String displayName, String locale, String passwordHash,
        boolean enabled, boolean mustChangePassword, Instant lockedUntil, Set<String> roles, Set<String> permissions) {

    public boolean lockedAt(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }
}
