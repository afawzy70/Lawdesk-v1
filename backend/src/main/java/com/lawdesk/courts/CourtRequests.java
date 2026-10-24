package com.lawdesk.courts;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** The court API's request and response bodies. Surrounding whitespace is removed before storing. */
final class CourtRequests {

    /** Letters, digits and hyphens; surrounding whitespace is tolerated and trimmed, the code stored upper-case. */
    static final String CODE = "\\s*[A-Za-z0-9-]{2,32}\\s*";

    private CourtRequests() {
    }

    record Create(
            @NotBlank @Pattern(regexp = CODE) String code,
            @NotBlank @Size(max = 200) String nameAr,
            @NotBlank @Size(max = 200) String nameEn,
            @NotNull CourtLevel level,
            @Size(max = 100) String governorate) {
    }

    /** A full replacement, with the {@code version} the client last read (optimistic locking). */
    record Update(
            @NotBlank @Pattern(regexp = CODE) String code,
            @NotBlank @Size(max = 200) String nameAr,
            @NotBlank @Size(max = 200) String nameEn,
            @NotNull CourtLevel level,
            @Size(max = 100) String governorate,
            @NotNull Boolean active,
            @NotNull Long version) {

        Create description() {
            return new Create(code, nameAr, nameEn, level, governorate);
        }
    }

    record Response(UUID id, String code, String nameAr, String nameEn, CourtLevel level, String governorate,
            boolean active, long version, Instant createdAt, Instant updatedAt) {

        static Response of(Court court) {
            return new Response(court.getId(), court.getCode(), court.getNameAr(), court.getNameEn(),
                    court.getLevel(), court.getGovernorate(), court.isActive(), court.getVersion(),
                    court.getCreatedAt(), court.getUpdatedAt());
        }
    }
}
