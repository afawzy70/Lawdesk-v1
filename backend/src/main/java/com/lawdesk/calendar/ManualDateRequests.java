package com.lawdesk.calendar;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Bodies of the manual-date API. Times are Kuwait wall-clock {@code HH:mm}; without a start time the date is day-long. */
final class ManualDateRequests {

    private static final String TIME = "HH:mm";

    private ManualDateRequests() {
    }

    record Create(
            @NotNull ManualDateKind kind,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 2000) String notes,
            UUID caseId,
            @NotNull LocalDate date,
            @JsonFormat(pattern = TIME) LocalTime startTime,
            @JsonFormat(pattern = TIME) LocalTime endTime,
            UUID ownerId) {

        Create {
            title = title == null ? null : title.strip();
            notes = blankToNull(notes);
        }
    }

    /** The case of a date is fixed; everything else may change. */
    record Update(
            @NotNull ManualDateKind kind,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 2000) String notes,
            @NotNull LocalDate date,
            @JsonFormat(pattern = TIME) LocalTime startTime,
            @JsonFormat(pattern = TIME) LocalTime endTime,
            UUID ownerId,
            @NotNull Long version) {

        Update {
            title = title == null ? null : title.strip();
            notes = blankToNull(notes);
        }
    }

    record StatusChange(@NotNull ManualDateStatus status, @NotNull Long version) {
    }

    record Response(UUID id, ManualDateKind kind, String title, String notes, UUID caseId, String caseReferenceNo,
            LocalDate date, @JsonFormat(pattern = TIME) LocalTime startTime, @JsonFormat(pattern = TIME) LocalTime endTime,
            boolean allDay, Instant startsAt, Instant endsAt, UUID ownerId, String ownerName, ManualDateStatus status,
            long version, Instant createdAt, UUID createdBy) {
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
