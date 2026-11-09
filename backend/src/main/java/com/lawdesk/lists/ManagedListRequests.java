package com.lawdesk.lists;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request and response bodies of the managed lists API (LD-057, owner decision D10). */
final class ManagedListRequests {

    private ManagedListRequests() {
    }

    static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** A value of a list; {@code origin} says whether the requirements text names it (DOCUMENT) or a member proposed it. */
    record Value(UUID id, String listCode, String code, String labelAr, String labelEn, String origin, boolean active,
            int sortOrder, long version, Instant updatedAt) {
    }

    record Create(
            @NotBlank @Pattern(regexp = "[A-Z0-9_]{1,40}") String code,
            @NotBlank @Size(max = 200) String labelAr,
            @Size(max = 200) String labelEn,
            Integer sortOrder) {

        Create {
            labelAr = trim(labelAr);
            labelEn = trim(labelEn);
        }
    }

    record Update(
            @NotBlank @Size(max = 200) String labelAr,
            @Size(max = 200) String labelEn,
            @NotNull Boolean active,
            Integer sortOrder,
            @NotNull Long version) {

        Update {
            labelAr = trim(labelAr);
            labelEn = trim(labelEn);
        }
    }
}
