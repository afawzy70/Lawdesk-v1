package com.lawdesk.security;

import java.util.List;
import java.util.UUID;

/** {@code GET /api/v1/me} (LD-007.6): who is signed in and what they may do; never credentials. */
public record MeResponse(UUID id, String username, String displayName, String email, String locale, List<String> roles,
        List<String> permissions, boolean mustChangePassword) {

    static MeResponse of(LawdeskUser user) {
        return new MeResponse(user.id(), user.username(), user.displayName(), user.email(), user.locale(),
                user.sortedRoles(), user.sortedPermissions(), user.mustChangePassword());
    }
}
