package com.lawdesk.deadlines;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request and response bodies of the deadline API (LD-054). Text is trimmed before it is validated. */
final class DeadlineRequests {

    private DeadlineRequests() {
    }

    static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** What the period runs from. */
    enum SourceType { JUDGMENT, HEARING, PROCEEDING, TASK, TEXT }

    enum Status { OPEN, COMPLETED, MISSED }

    /** A deadline typed by a user: always MANUAL until a competent member approves it. */
    record Create(
            @NotNull SourceType sourceType,
            UUID sourceId,
            @Size(max = 500) String sourceText,
            LocalDate startsOn,
            @Min(1) @Max(3650) Integer periodDays,
            LocalDate dueOn,
            @NotBlank @Size(max = 500) String action,
            UUID ownerId,
            @Size(max = 2000) String notes) {

        Create {
            sourceText = trim(sourceText);
            action = trim(action);
            notes = trim(notes);
        }
    }

    /** A request to compute the due date from an approved rule; the result is PROPOSED, never official. */
    record Propose(
            @NotNull SourceType sourceType,
            UUID sourceId,
            @Size(max = 500) String sourceText,
            UUID ruleId,
            @NotNull LocalDate startsOn,
            @NotBlank @Size(max = 500) String action,
            UUID ownerId,
            @Size(max = 2000) String notes) {

        Propose {
            sourceText = trim(sourceText);
            action = trim(action);
            notes = trim(notes);
        }
    }

    /** The editable fields as a whole, under the version the caller read. The source never changes. */
    record Update(
            LocalDate startsOn,
            @Min(1) @Max(3650) Integer periodDays,
            LocalDate dueOn,
            @NotBlank @Size(max = 500) String action,
            UUID ownerId,
            @Size(max = 2000) String notes,
            @Size(max = 1000) String changeReason,
            @NotNull Long version) {

        Update {
            action = trim(action);
            notes = trim(notes);
            changeReason = trim(changeReason);
        }
    }

    record Approve(@NotNull Long version) {
    }

    /** Completion, a missed deadline, or reopening: only ever set by a user. */
    record StatusChange(
            @NotNull Status status,
            LocalDate completedOn,
            UUID proofDocumentId,
            @NotNull Long version) {
    }

    /** The row of the register and of the case: the whole deadline with the names the screens show. */
    record Response(UUID id, UUID caseId, String caseReferenceNo, String caseTitle, String sourceType, UUID sourceId,
            String sourceText, String sourceLabel, LocalDate startsOn, Integer periodDays, LocalDate dueOn, UUID ruleId,
            String ruleCode, DeadlineCalculation calculation, String action, UUID ownerId, String ownerName,
            String status, LocalDate completedOn, UUID proofDocumentId, String proofDocumentTitle, String notes,
            String calcStatus, boolean official, UUID approvedById, String approvedByName, Instant approvedAt,
            String changeReason, boolean overdue, Integer daysRemaining, long version, Instant createdAt,
            Instant updatedAt) {
    }

    // ---- settings: rules, holidays, reminder lead times --------------------------------------------------------

    enum CountMethod { CALENDAR, WORKING }

    record RuleInput(
            @NotBlank @Size(max = 60) @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_.-]*$") String code,
            @NotBlank @Size(max = 300) String label,
            @NotNull @Min(1) @Max(3650) Integer periodDays,
            @NotNull CountMethod countMethod,
            @NotNull Boolean countsStartDay,
            @NotNull Boolean rollForward,
            @NotNull List<Integer> closedWeekdays,
            @NotBlank @Size(max = 1000) String legalSource,
            @NotNull LocalDate effectiveFrom,
            Long version) {

        RuleInput {
            code = trim(code);
            label = trim(label);
            legalSource = trim(legalSource);
        }
    }

    record RuleResponse(UUID id, String code, String label, int periodDays, String countMethod, boolean countsStartDay,
            boolean rollForward, List<Integer> closedWeekdays, String legalSource, LocalDate effectiveFrom, String status,
            UUID approvedById, String approvedByName, Instant approvedAt, UUID retiredById, Instant retiredAt,
            long version, Instant createdAt) {
    }

    record HolidayInput(
            @NotNull LocalDate holidayOn,
            @NotBlank @Size(max = 200) String label,
            @NotBlank @Size(max = 1000) String legalSource,
            Long version) {

        HolidayInput {
            label = trim(label);
            legalSource = trim(legalSource);
        }
    }

    record HolidayResponse(UUID id, LocalDate holidayOn, String label, String legalSource, String status, UUID approvedById,
            String approvedByName, Instant approvedAt, UUID retiredById, Instant retiredAt, long version, Instant createdAt) {
    }

    /** Approving or retiring a rule or a holiday, under the version the caller read. */
    record VersionOnly(@NotNull Long version) {
    }

    /** Lead times in whole days before the due date; {@code null} until somebody configures them (there is no default). */
    record ReminderSetting(boolean configured, List<Integer> leadDays, Instant updatedAt) {
    }

    record ReminderInput(@NotNull List<Integer> leadDays) {
    }

    record ReminderRunResult(boolean leadTimesConfigured, int approaching, int overdue) {
    }
}
