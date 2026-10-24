package com.lawdesk.proceedings;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import com.lawdesk.courts.Court;

/** Request and response bodies of the proceeding API (LD-019…LD-021). */
final class ProceedingRequests {

    private ProceedingRequests() {
    }

    /** {@code parentProceedingId} is required for appeals and cassations and refused for first instance (422). */
    record Create(
            @NotNull Stage stage,
            @NotNull UUID courtId,
            @NotBlank @Size(max = 32) String courtCaseNumber,
            @NotNull @Min(1900) @Max(2100) Integer courtCaseYear,
            @NotNull @PastOrPresent LocalDate filingDate,
            UUID parentProceedingId) {
    }

    /** Stage and parent are fixed once recorded; court, number, year and filing date can be corrected. */
    record Update(
            @NotNull UUID courtId,
            @NotBlank @Size(max = 32) String courtCaseNumber,
            @NotNull @Min(1900) @Max(2100) Integer courtCaseYear,
            @NotNull @PastOrPresent LocalDate filingDate,
            @NotNull Long version) {
    }

    record Response(UUID id, UUID caseId, Stage stage, UUID courtId, String courtCode, String courtNameAr,
            String courtNameEn, String courtCaseNumber, int courtCaseYear, LocalDate filingDate,
            UUID parentProceedingId, boolean hasJudgment, long version, Instant createdAt, Instant updatedAt) {

        static Response of(Proceeding p, Court court, boolean hasJudgment) {
            return new Response(p.getId(), p.getCaseId(), p.getStage(), p.getCourtId(), court.getCode(),
                    court.getNameAr(), court.getNameEn(), p.getCourtCaseNumber(), p.getCourtCaseYear(),
                    p.getFilingDate(), p.getParentProceedingId(), hasJudgment, p.getVersion(), p.getCreatedAt(),
                    p.getUpdatedAt());
        }
    }
}
