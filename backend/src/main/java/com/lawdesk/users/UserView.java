package com.lawdesk.users;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A user as administrators see it (LD-009); never the password hash. */
public record UserView(UUID id, String username, String email, String displayName, String locale, boolean enabled,
        boolean mustChangePassword, boolean locked, List<String> roles, Instant createdAt, Instant updatedAt,
        long version) {
}
