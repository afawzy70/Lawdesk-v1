package com.lawdesk.cases;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** {@code POST /api/v1/cases/{id}/status} (LD-017): the target status, an optional reason and the version read. */
record StatusChangeRequest(
        @NotNull @Pattern(regexp = CaseStatus.PATTERN) String status,
        @Size(max = 500) String reason,
        @NotNull Long version) {

    StatusChangeRequest {
        reason = Text.trimToNull(reason);
    }
}
