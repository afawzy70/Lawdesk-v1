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
        UUID updatedBy,
        /** True for the authority (الهيئة), the party every intake case carries. */
        boolean authority,
        /** A confidential party's contact details are shown only to those who may see identifiers (LD-050, D4). */
        boolean confidential,
        String notes) {
}
