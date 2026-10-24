package com.lawdesk.cases;

import java.time.Instant;
import java.util.UUID;

/** A stored legal party as the API returns it (docs/delivery/PHASE-3-ACCEPTANCE.md, LD-016). */
record Party(
        UUID id,
        PartyKind kind,
        String name,
        String nationalId,
        String registrationNo,
        String phone,
        String email,
        String address,
        long version,
        Instant createdAt,
        UUID createdBy,
        Instant updatedAt,
        UUID updatedBy) {
}
