package com.lawdesk.pleadings;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Bodies and views of the facts and evidence API (requirement section 20). */
final class EvidenceRequests {

    private EvidenceRequests() {
    }

    record FactBody(@NotBlank @Size(max = 20000) String text, LocalDate factDate, Long version) {

        FactBody {
            text = text == null ? null : text.strip();
        }
    }

    record FactView(UUID id, UUID caseId, int seq, String text, LocalDate factDate, int evidenceCount, long version,
            Instant updatedAt) {
    }

    /** One body for creating and changing an evidence item ({@code version} only when changing). */
    record ItemBody(@NotBlank @Size(max = 300) String name, @Size(max = 100) String docType, LocalDate issuedOn,
            @Size(max = 300) String issuer, LocalDate receivedOn, @Size(max = 300) String source, @NotNull EvidenceRole role,
            EvidenceImportance importance, UUID documentId, Boolean submittedToCourt, LocalDate submittedOn,
            @Size(max = 300) String submittedIn, Boolean opponentResponded, @Size(max = 2000) String opponentResponse,
            @Size(max = 2000) String courtPosition, @Size(max = 2000) String notes, Long version) {

        ItemBody {
            name = name == null ? null : name.strip();
            docType = CaseRows.blankToNull(docType);
            issuer = CaseRows.blankToNull(issuer);
            source = CaseRows.blankToNull(source);
            submittedIn = CaseRows.blankToNull(submittedIn);
            opponentResponse = CaseRows.blankToNull(opponentResponse);
            courtPosition = CaseRows.blankToNull(courtPosition);
            notes = CaseRows.blankToNull(notes);
        }

        boolean submitted() {
            return Boolean.TRUE.equals(submittedToCourt);
        }
    }

    record LinkBody(UUID factId, UUID defenseId, UUID claimId) {
    }

    record LinkView(UUID id, String kind, UUID targetId, String label) {
    }

    record FollowupBody(@NotNull UUID taskId) {
    }

    record FollowupView(UUID id, UUID taskId, String taskTitle, String taskStatus, Instant createdAt) {
    }

    record ItemView(UUID id, UUID caseId, UUID documentId, String documentTitle, String name, String docType,
            LocalDate issuedOn, String issuer, LocalDate receivedOn, String source, EvidenceRole role,
            EvidenceImportance importance, boolean submittedToCourt, LocalDate submittedOn, String submittedIn,
            Boolean opponentResponded, String opponentResponse, String courtPosition, String notes, long version,
            List<LinkView> links, List<FollowupView> followups, String gap) {
    }

    /** Where a stored document is used in the case file: as evidence (with its links), a claim's source, a memo or a judgment copy. */
    record DocumentUse(UUID documentId, String type, UUID refId, String label, List<LinkView> links) {
    }
}
