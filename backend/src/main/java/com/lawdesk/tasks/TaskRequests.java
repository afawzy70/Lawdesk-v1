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
            LocalDate dueDate,
            TaskPriority priority) {

        Create {
            title = Rules.trim(title);
            description = Rules.trimToNull(description);
        }
    }

    record Update(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 4000) String description,
            @NotNull UUID assigneeId,
            LocalDate dueDate,
            TaskPriority priority,
            @NotNull Long version) {

        Update {
            title = Rules.trim(title);
            description = Rules.trimToNull(description);
        }
    }

    record StatusChange(@NotNull TaskStatus status, @NotNull Long version) {
    }

    /** The signed-in user's in-app reminders (LD-028). */
    record Reminders(LocalDate today, int dueSoonDays, long overdue, long dueSoon) {
    }

    record Response(UUID id, UUID caseId, String caseReferenceNo, String caseTitle, String title, String description,
            UUID assigneeId, String assigneeName, LocalDate dueDate, TaskPriority priority, TaskStatus status,
            Instant completedAt, boolean overdue, boolean dueSoon, long version, Instant createdAt, UUID createdBy,
            Instant updatedAt) {
    }
}
