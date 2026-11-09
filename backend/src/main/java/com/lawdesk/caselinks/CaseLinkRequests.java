package com.lawdesk.caselinks;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request and response bodies of the linked-cases API (LD-053, requirements section 8). */
final class CaseLinkRequests {

    private CaseLinkRequests() {
    }

    static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** A subject link, a request to join, the court's decision to join, or a referral. */
    enum Kind { SUBJECT_LINK, JOINDER_REQUEST, JOINDER_DECISION, REFERRAL }

    /** ASSUMPTION (needs the owner's approval): the document names no states for a link. */
    enum Status { REQUESTED, ACTIVE, REJECTED, ENDED }

    record Create(
            @NotNull UUID otherCaseId,
            @NotNull Kind kind,
            @NotBlank @Size(max = 2000) String reason,
            @Size(max = 200) String requestedBy,
            LocalDate requestedOn,
            Boolean courtDecision,
            LocalDate decisionDate,
            @Size(max = 2000) String operative,
            UUID decisionDocumentId,
            Status status) {

        Create {
            reason = trim(reason);
            requestedBy = trim(requestedBy);
            operative = trim(operative);
        }
    }

    record Update(
            @NotBlank @Size(max = 2000) String reason,
            @Size(max = 200) String requestedBy,
            LocalDate requestedOn,
            @NotNull Status status,
            Boolean courtDecision,
            LocalDate decisionDate,
            @Size(max = 2000) String operative,
            UUID decisionDocumentId,
            @NotNull Long version) {

        Update {
            reason = trim(reason);
            requestedBy = trim(requestedBy);
            operative = trim(operative);
        }
    }

    /** A link as seen from one of its two cases: {@code otherCaseId} is the other one. */
    record Link(UUID id, UUID caseId, String direction, UUID otherCaseId, String otherCaseReferenceNo,
            String otherCaseTitle, String otherCaseStatus, String kind, String reason, String requestedBy,
            LocalDate requestedOn, boolean courtDecision, LocalDate decisionDate, String operative,
            UUID decisionDocumentId, String status, long version, Instant createdAt) {
    }

    /** The links the user may see, and how many more there are to cases they may not see (their details stay hidden). */
    record Links(List<Link> items, int hiddenCount) {
    }
}
