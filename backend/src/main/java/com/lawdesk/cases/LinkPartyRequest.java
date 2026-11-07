package com.lawdesk.cases;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * {@code POST /api/v1/cases/{id}/parties} (LD-016). On a case registered through the intake (LD-045) the party's
 * {@code side} is required and the role follows from it; on an older case the role alone is used, as before.
 */
record LinkPartyRequest(@NotNull UUID partyId, @NotNull @Pattern(regexp = PartyRole.PATTERN) String role,
        @Pattern(regexp = Side.PATTERN) String side) {
}
