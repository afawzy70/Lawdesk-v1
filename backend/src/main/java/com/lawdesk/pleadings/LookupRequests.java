package com.lawdesk.pleadings;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Bodies and views of the managed lists (owner decision D10). */
final class LookupRequests {

    private LookupRequests() {
    }

    record Create(@NotBlank @Size(max = 60) @Pattern(regexp = "[A-Z][A-Z0-9_]*") String code,
            @NotBlank @Size(max = 200) String labelAr, @Size(max = 200) String labelEn) {

        Create {
            labelAr = labelAr == null ? null : labelAr.strip();
            labelEn = CaseRows.blankToNull(labelEn);
        }
    }

    record Update(@NotBlank @Size(max = 200) String labelAr, @Size(max = 200) String labelEn, @NotNull Boolean active,
            @NotNull Long version) {

        Update {
            labelAr = labelAr == null ? null : labelAr.strip();
            labelEn = CaseRows.blankToNull(labelEn);
        }
    }

    /** {@code needsApproval}: proposed by the implementers (or a user) and not yet approved. */
    record View(UUID id, String listCode, String code, String labelAr, String labelEn, String origin,
            boolean needsApproval, Instant approvedAt, boolean active, long version) {
    }

    record History(int rev, String labelAr, String labelEn, String origin, boolean active, String change, UUID changedBy,
            Instant changedAt) {
    }
}
