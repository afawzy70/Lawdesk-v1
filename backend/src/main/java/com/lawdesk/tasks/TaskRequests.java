package com.lawdesk.tasks;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request and response bodies of the task API (LD-027, LD-028). Text is trimmed before it is validated. */
final class TaskRequests {

    private TaskRequests() {
    }

    /** {@code caseId} is optional and fixed once created; {@code priority} defaults to NORMAL. */
    record Create(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 4000) String description,
            UUID caseId,
            @NotNull UUID assigneeId,
            LocalDate startsOn,
            LocalDate dueDate,
            TaskPriority priority,
            @Size(max = 4000) String instructions) {

        Create {
            title = Rules.trim(title);
            description = Rules.trimToNull(description);
            instructions = Rules.trimToNull(instructions);
        }
    }

    record Update(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 4000) String description,
            @NotNull UUID assigneeId,
            LocalDate startsOn,
            LocalDate dueDate,
            TaskPriority priority,
            @Size(max = 4000) String instructions,
            @NotNull Long version) {

        Update {
            title = Rules.trim(title);
            description = Rules.trimToNull(description);
            instructions = Rules.trimToNull(instructions);
        }
    }

    /** Only the assignee's own record of why the task is late; the reason is kept in the history too. */
    record DelayReason(@NotBlank @Size(max = 2000) String reason, @NotNull Long version) {

        DelayReason {
            reason = Rules.trim(reason);
        }
    }

    /** Moves the task to another member of staff; the reason is optional and goes to the history. */
    record Reassign(@NotNull UUID assigneeId, @Size(max = 500) String reason, @NotNull Long version) {

        Reassign {
            reason = Rules.trimToNull(reason);
        }
    }

    /** One line of the task history; the names are those of the directory and are null for an unknown or system actor. */
    record HistoryEntry(UUID id, Instant changedAt, UUID actorId, String actorName, String action, String field,
            String oldValue, String newValue, String reason) {
    }

    record StatusChange(@NotNull TaskStatus status, @NotNull Long version) {
    }

    /** The signed-in user's in-app reminders (LD-028). */
    record Reminders(LocalDate today, int dueSoonDays, long overdue, long dueSoon) {
    }

    record Response(UUID id, UUID caseId, String caseReferenceNo, String caseTitle, String title, String description,
            UUID assigneeId, String assigneeName, LocalDate startsOn, LocalDate dueDate, TaskPriority priority,
            TaskStatus status, Instant completedAt, boolean overdue, boolean dueSoon, String instructions,
            String delayReason, Instant delayReasonAt, long version, Instant createdAt, UUID createdBy,
            Instant updatedAt) {
    }
}
