package com.lawdesk.templates;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request and response bodies of the legal templates API (LD-057, requirements section 29). */
final class TemplateRequests {

    private TemplateRequests() {
    }

    static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    static final int MAX_BODY = 100_000;

    record Create(
            @NotBlank @Size(max = 40) String kind,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 1000) String description,
            @Size(max = MAX_BODY) String body) {

        Create {
            kind = trim(kind);
            title = trim(title);
            description = trim(description);
            body = trim(body);
        }
    }

    record Update(
            @NotBlank @Size(max = 40) String kind,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 1000) String description,
            @NotNull Boolean active,
            @NotNull Long version) {

        Update {
            kind = trim(kind);
            title = trim(title);
            description = trim(description);
        }
    }

    record NewVersion(@NotBlank @Size(max = MAX_BODY) String body, @Size(max = 1000) String changeNote) {

        NewVersion {
            body = trim(body);
            changeNote = trim(changeNote);
        }
    }

    record EditVersion(@NotBlank @Size(max = MAX_BODY) String body, @Size(max = 1000) String changeNote,
            @NotNull Long version) {

        EditVersion {
            body = trim(body);
            changeNote = trim(changeNote);
        }
    }

    record Step(@NotNull Long version) {
    }

    record DraftRequest(@NotNull UUID templateId, UUID versionId) {
    }

    record Placeholder(String key, boolean known, String labelAr, String labelEn) {
    }

    /** {@code body} is null for a reader who does not manage templates. */
    record Version(UUID id, UUID templateId, int versionNo, String status, String body, String changeNote,
            List<Placeholder> placeholders, UUID reviewedBy, Instant reviewedAt, UUID approvedBy, Instant approvedAt,
            long version, Instant createdAt, UUID createdBy) {
    }

    record Summary(UUID id, String kind, String title, String description, String origin, boolean active,
            int versionCount, Integer latestApprovedVersionNo, long version, Instant updatedAt) {
    }

    record Template(UUID id, String kind, String title, String description, String origin, boolean active,
            Integer latestApprovedVersionNo, List<Version> versions, long version, Instant createdAt, Instant updatedAt) {
    }

    record FilledField(String key, String labelAr, String labelEn) {
    }

    /** A draft is never final; {@code missing} lists what the case file does not hold, {@code unknown} the unrecognised fields. */
    record Draft(UUID templateId, String templateTitle, String kind, UUID versionId, int versionNo, String versionStatus,
            UUID caseId, boolean draft, @JsonProperty("final") boolean finalText, String text, List<FilledField> filled,
            List<FilledField> missing, List<String> unknown, String notice) {
    }
}
