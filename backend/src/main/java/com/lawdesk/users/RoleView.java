package com.lawdesk.users;

import java.util.List;
import java.util.UUID;

/** A role with the permission codes it grants (LD-009.1). */
public record RoleView(UUID id, String code, String name, boolean system, List<String> permissions, long version) {
}
