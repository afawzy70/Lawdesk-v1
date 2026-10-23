package com.lawdesk.security;

import java.util.UUID;

/** Implemented by every authenticated principal, so domain code can tell who acted (architecture/CONTRACTS.md §3). */
public interface ActorPrincipal {

    UUID id();

    String username();
}
