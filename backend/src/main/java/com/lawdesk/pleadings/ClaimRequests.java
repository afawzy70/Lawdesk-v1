package com.lawdesk.pleadings;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Bodies and views of the claims API (requirements section 5). */
final class ClaimRequests {

    private ClaimRequests() {
    }

    record Create(@NotBlank @Size(max = 20000) String text, @NotNull ClaimKind kind, UUID submittedByPartyId,
            LocalDate submittedOn, UUID stageId, UUID sourceDocumentId, @Size(max = 2000) String notes) {

        Create {
            text = text == null ? null : text.strip();
            notes = CaseRows.blankToNull(notes);
        }
    }

    record Update(@NotBlank @Size(max = 20000) String text, @NotNull ClaimKind kind, @NotNull ClaimStatus status,
            UUID submittedByPartyId, LocalDate submittedOn, UUID stageId, UUID sourceDocumentId,
            @Size(max = 2000) String notes, @Size(max = 500) String reason, @NotNull Long version) {

        Update {
            text = text == null ? null : text.strip();
            notes = CaseRows.blankToNull(notes);
            reason = CaseRows.blankToNull(reason);
        }
    }

    record View(UUID id, UUID caseId, int seq, String text, ClaimKind kind, ClaimStatus status,
            UUID submittedByPartyId, String submittedByPartyName, LocalDate submittedOn, UUID stageId,
            UUID sourceDocumentId, String sourceDocumentTitle, String notes, int revision, long version,
            Instant createdAt, Instant updatedAt) {
    }

    record Revision(int rev, String text, ClaimKind kind, ClaimStatus status, UUID submittedByPartyId,
            LocalDate submittedOn, UUID stageId, UUID sourceDocumentId, String notes, String reason, UUID changedBy,
            Instant changedAt) {
    }
}
