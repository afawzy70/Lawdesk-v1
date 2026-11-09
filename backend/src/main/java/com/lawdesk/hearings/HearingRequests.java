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
            @Size(max = 2000) String notes,
            @Size(max = 200) String chamber,
            HearingKind kind,
            @Size(max = 200) String judgeOrExpert,
            UUID responsibleMemberId,
            @Size(max = 2000) String attendees,
            @Size(max = 4000) String proceedingsSummary,
            @Size(max = 4000) String opponentsRequests,
            @Size(max = 2000) String requiredAction) {

        Create {
            notes = LegalDay.trimToNull(notes);
            chamber = LegalDay.trimToNull(chamber);
            judgeOrExpert = LegalDay.trimToNull(judgeOrExpert);
            attendees = LegalDay.trimToNull(attendees);
            proceedingsSummary = LegalDay.trimToNull(proceedingsSummary);
            opponentsRequests = LegalDay.trimToNull(opponentsRequests);
            requiredAction = LegalDay.trimToNull(requiredAction);
        }
    }

    /** Scheduling the next hearing of an adjourned hearing that had no date (the follow-up list). */
    record NextHearing(
            @NotNull LocalDate hearingDate,
            @JsonFormat(pattern = TIME) LocalTime hearingTime,
            HearingPurpose purpose,
            @NotNull Long version) {
    }

    /** One-click reassignment of a scheduled hearing's responsible member (for example to the case's alternate on a conflict). Without a version the current one is taken: the member decides on what the calendar shows. */
    record Reassign(@NotNull UUID memberId, Long version) {
    }

    /** Only a scheduled hearing changes; case, proceeding and the hearing it continues stay. */
    record Update(
            @NotNull LocalDate hearingDate,
            @JsonFormat(pattern = TIME) LocalTime hearingTime,
            @NotNull HearingPurpose purpose,
            @Size(max = 2000) String notes,
            @Size(max = 200) String chamber,
            HearingKind kind,
            @Size(max = 200) String judgeOrExpert,
            UUID responsibleMemberId,
            @Size(max = 2000) String attendees,
            @Size(max = 4000) String proceedingsSummary,
            @Size(max = 4000) String opponentsRequests,
            @Size(max = 2000) String requiredAction,
            @NotNull Long version) {

        Update {
            notes = LegalDay.trimToNull(notes);
            chamber = LegalDay.trimToNull(chamber);
            judgeOrExpert = LegalDay.trimToNull(judgeOrExpert);
            attendees = LegalDay.trimToNull(attendees);
            proceedingsSummary = LegalDay.trimToNull(proceedingsSummary);
            opponentsRequests = LegalDay.trimToNull(opponentsRequests);
            requiredAction = LegalDay.trimToNull(requiredAction);
        }
    }

    /** The outcome of a scheduled hearing; the {@code next*} fields belong to an adjournment only. */
    record Result(
            @NotNull ResultStatus status,
            @Size(max = 2000) String decision,
            LocalDate nextHearingDate,
            @JsonFormat(pattern = TIME) LocalTime nextHearingTime,
            HearingPurpose nextHearingPurpose,
            @Size(max = 2000) String adjournmentReason,
            Boolean noNextDate,
            @Size(max = 2000) String noNextDateReason,
            @Size(max = 2000) String attendees,
            @Size(max = 4000) String proceedingsSummary,
            @Size(max = 4000) String opponentsRequests,
            @Size(max = 2000) String requiredAction,
            @NotNull Long version) {

        Result {
            decision = LegalDay.trimToNull(decision);
            adjournmentReason = LegalDay.trimToNull(adjournmentReason);
            noNextDateReason = LegalDay.trimToNull(noNextDateReason);
            attendees = LegalDay.trimToNull(attendees);
            proceedingsSummary = LegalDay.trimToNull(proceedingsSummary);
            opponentsRequests = LegalDay.trimToNull(opponentsRequests);
            requiredAction = LegalDay.trimToNull(requiredAction);
        }

        boolean noDate() {
            return Boolean.TRUE.equals(noNextDate);
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
            UUID nextHearingId, boolean awaitingResult, long version, Instant createdAt, Instant updatedAt,
            String chamber, HearingKind kind, String judgeOrExpert, UUID responsibleMemberId,
            String responsibleMemberName, String attendees, String proceedingsSummary, String opponentsRequests,
            String requiredAction, String adjournmentReason, boolean noNextDate, String noNextDateReason,
            boolean followUp) {

        static Response of(Hearing h, CaseSummary legalCase, ProceedingSummary proceeding, Court court,
                UUID nextHearingId, LocalDate today, String responsibleMemberName) {
            return new Response(h.getId(), h.getCaseId(), legalCase.referenceNo(), legalCase.title(),
                    h.getProceedingId(), proceeding.stage(), court.getId(), court.getCode(), court.getNameAr(),
                    court.getNameEn(), proceeding.courtCaseNumber(), proceeding.courtCaseYear(), h.getHearingDate(),
                    h.getHearingTime(), h.getPurpose(), h.getNotes(), h.getStatus(), h.getDecision(),
                    h.getAdjournedFromId(), nextHearingId, h.awaitingResult(today), h.getVersion(), h.getCreatedAt(),
                    h.getUpdatedAt(), h.getChamber(), h.getKind(), h.getJudgeOrExpert(), h.getResponsibleMemberId(),
                    responsibleMemberName, h.getAttendees(), h.getProceedingsSummary(), h.getOpponentsRequests(),
                    h.getRequiredAction(), h.getAdjournmentReason(), h.isNoNextDate(), h.getNoNextDateReason(),
                    h.isFollowUpFlag());
        }
    }
}
