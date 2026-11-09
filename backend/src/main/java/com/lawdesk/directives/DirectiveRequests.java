package com.lawdesk.directives;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request and response bodies of the directives register (LD-057, requirements section 25). */
final class DirectiveRequests {

    private DirectiveRequests() {
    }

    static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** New, in progress, done, cancelled: the execution status (حالة التنفيذ). */
    enum Status { NEW, IN_PROGRESS, DONE, CANCELLED }

    record Create(
            @NotNull UUID caseId,
            UUID taskId,
            @Size(max = 40) String kind,
            @NotBlank @Size(max = 300) String title,
            @Size(max = 20000) String details,
            @NotNull LocalDate directiveDate,
            @NotBlank @Size(max = 300) String source,
            @Size(max = 100) String sourceReferenceNo,
            UUID addresseeUserId,
            @Size(max = 300) String addresseeText,
            LocalDate dueDate) {

        Create {
            kind = trim(kind);
            title = trim(title);
            details = trim(details);
            source = trim(source);
            sourceReferenceNo = trim(sourceReferenceNo);
            addresseeText = trim(addresseeText);
        }
    }

    record Update(
            UUID taskId,
            @Size(max = 40) String kind,
            @NotBlank @Size(max = 300) String title,
            @Size(max = 20000) String details,
            @NotNull LocalDate directiveDate,
            @NotBlank @Size(max = 300) String source,
            @Size(max = 100) String sourceReferenceNo,
            UUID addresseeUserId,
            @Size(max = 300) String addresseeText,
            LocalDate dueDate,
            @NotNull Long version) {

        Update {
            kind = trim(kind);
            title = trim(title);
            details = trim(details);
            source = trim(source);
            sourceReferenceNo = trim(sourceReferenceNo);
            addresseeText = trim(addresseeText);
        }
    }

    record StatusChange(@NotNull Status status, @Size(max = 1000) String note, @NotNull Long version) {

        StatusChange {
            note = trim(note);
        }
    }

    /** Query parameters of {@code GET /api/v1/directives}, bound as one object so a bad value is a field error. */
    record Search(
            @Size(max = 200) String q,
            Status status,
            @Size(max = 40) String kind,
            UUID caseId,
            @Size(max = 40) String addressee,
            Boolean overdue,
            @Min(0) Integer page,
            @Min(1) Integer size) {

        static final int DEFAULT_SIZE = 20;

        Search {
            q = trim(q);
            kind = trim(kind);
            addressee = trim(addressee);
        }

        int pageOrDefault() {
            return page == null ? 0 : page;
        }

        int sizeOrDefault() {
            return size == null ? DEFAULT_SIZE : Math.min(size, 100);
        }
    }

    record Directive(UUID id, String directiveNo, UUID caseId, String caseReferenceNo, String caseTitle, UUID taskId,
            String kind, String title, String details, LocalDate directiveDate, String source, String sourceReferenceNo,
            UUID addresseeUserId, String addresseeText, String addresseeName, LocalDate dueDate, String status,
            String statusNote, Instant completedAt, boolean overdue, long version, Instant createdAt, UUID createdBy,
            Instant updatedAt, UUID updatedBy) {
    }
}
