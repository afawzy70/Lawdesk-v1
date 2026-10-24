package com.lawdesk.cases;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** {@code POST /api/v1/cases/{id}/parties} (LD-016). */
record LinkPartyRequest(@NotNull UUID partyId, @NotNull @Pattern(regexp = PartyRole.PATTERN) String role) {
}
