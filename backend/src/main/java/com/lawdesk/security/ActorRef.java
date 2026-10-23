package com.lawdesk.security;

import java.util.UUID;

/** The signed-in user as domain code sees it: stored in created_by/updated_by columns and history entries. */
public record ActorRef(UUID id, String username) {
}
