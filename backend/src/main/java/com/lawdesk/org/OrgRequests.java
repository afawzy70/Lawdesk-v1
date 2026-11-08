package com.lawdesk.org;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request bodies of {@code /api/v1/org-units}. */
final class OrgRequests {

    private OrgRequests() {
    }

    record Create(@NotNull @Pattern(regexp = "LEGAL|AUTHORITY") String tree, UUID parentId,
            @NotNull @Pattern(regexp = "SECTOR|DEPARTMENT|SECTION") String level,
            @NotBlank @Size(max = 200) String nameAr, @Size(max = 200) String nameEn, LocalDate validFrom,
            LocalDate validTo) {
    }

    record Update(UUID parentId, @NotBlank @Size(max = 200) String nameAr, @Size(max = 200) String nameEn,
            boolean active, LocalDate validFrom, LocalDate validTo, long version) {
    }

    /** {@code orgUnitId} null removes the user from their unit. */
    record UserUnit(UUID orgUnitId) {
    }
}
