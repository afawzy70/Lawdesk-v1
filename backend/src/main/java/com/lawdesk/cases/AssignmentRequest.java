package com.lawdesk.cases;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/**
 * {@code PUT /api/v1/cases/{id}/assignment} (LD-045): the specialist member (العضو المختص, required) and the optional
 * alternate member (العضو البديل) of a case, with the version the client read.
 */
record AssignmentRequest(@NotNull UUID primaryMemberId, UUID alternateMemberId, @NotNull Long version) {
}
