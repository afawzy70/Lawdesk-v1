package com.lawdesk.cases;

import java.time.Instant;
import java.util.UUID;

/** A party linked to a case, with its role (LD-016). */
record CaseParty(UUID partyId, PartyKind kind, String name, PartyRole role, Instant addedAt,
        /** Plaintiff or defendant side; null for parties linked before the side existed. */
        Side side,
        /** True for the authority (الهيئة) itself. */
        boolean authority) {
}
