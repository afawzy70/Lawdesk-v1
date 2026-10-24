package com.lawdesk.hearings;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.lawdesk.cases.CaseSummary;
import com.lawdesk.courts.Court;
import com.lawdesk.proceedings.ProceedingLookup.ProceedingSummary;
import com.lawdesk.proceedings.Stage;

/** Request and response bodies of the hearing API (LD-024). Text is trimmed before it is validated. */
final class HearingRequests {

    /** Times are written and read as {@code HH:mm}. */
    static final String TIME = "HH:mm";

    private HearingRequests() {
    }

    record Create(
            @NotNull LocalDate hearingDate,
            @JsonFormat(pattern = TIME) LocalTime hearingTime,
            @NotNull HearingPurpose purpose,
            @Size(max = 2000) String notes) {

        Create {
            notes = LegalDay.trimToNull(notes);
        }
    }

    /** Only a scheduled hearing changes; case, proceeding and the hearing it continues stay. */
    record Update(
            @NotNull LocalDate hearingDate,
            @JsonFormat(pattern = TIME) LocalTime hearingTime,
            @NotNull HearingPurpose purpose,
            @Size(max = 2000) String notes,
            @NotNull Long version) {

        Update {
            notes = LegalDay.trimToNull(notes);
        }
    }

    /** The outcome of a scheduled hearing; the {@code next*} fields belong to an adjournment only. */
    record Result(
            @NotNull ResultStatus status,
            @Size(max = 2000) String decision,
            LocalDate nextHearingDate,
            @JsonFormat(pattern = TIME) LocalTime nextHearingTime,
            HearingPurpose nextHearingPurpose,
            @NotNull Long version) {

        Result {
            decision = LegalDay.trimToNull(decision);
        }
    }

    /** The statuses a result may set; {@code SCHEDULED} is not one of them. */
    enum ResultStatus {
        HELD, ADJOURNED, CANCELLED;

        HearingStatus hearingStatus() {
            return HearingStatus.valueOf(name());
        }
    }

    record Response(UUID id, UUID caseId, String caseReferenceNo, String caseTitle, UUID proceedingId, Stage stage,
            UUID courtId, String courtCode, String courtNameAr, String courtNameEn, String courtCaseNumber,
            int courtCaseYear, LocalDate hearingDate, @JsonFormat(pattern = TIME) LocalTime hearingTime,
            HearingPurpose purpose, String notes, HearingStatus status, String decision, UUID adjournedFromId,
            UUID nextHearingId, boolean awaitingResult, long version, Instant createdAt, Instant updatedAt) {

        static Response of(Hearing h, CaseSummary legalCase, ProceedingSummary proceeding, Court court,
                UUID nextHearingId, LocalDate today) {
            return new Response(h.getId(), h.getCaseId(), legalCase.referenceNo(), legalCase.title(),
                    h.getProceedingId(), proceeding.stage(), court.getId(), court.getCode(), court.getNameAr(),
                    court.getNameEn(), proceeding.courtCaseNumber(), proceeding.courtCaseYear(), h.getHearingDate(),
                    h.getHearingTime(), h.getPurpose(), h.getNotes(), h.getStatus(), h.getDecision(),
                    h.getAdjournedFromId(), nextHearingId, h.awaitingResult(today), h.getVersion(), h.getCreatedAt(),
                    h.getUpdatedAt());
        }
    }
}
