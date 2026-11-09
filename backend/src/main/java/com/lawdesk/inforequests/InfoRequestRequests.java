package com.lawdesk.inforequests;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request and response bodies of the information-request API (LD-053). Text is trimmed before it is validated. */
final class InfoRequestRequests {

    private InfoRequestRequests() {
    }

    static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** A new request: always starts as a draft; the number is the system's. */
    record Create(
            @NotBlank @Size(max = 300) String subject,
            UUID unitId,
            @Size(max = 4000) String details,
            @Size(max = 2000) String requiredDocuments,
            LocalDate draftedOn,
            LocalDate dueOn,
            UUID responsibleMemberId,
            @Size(max = 200) String addressee,
            @Size(max = 2000) String notes,
            UUID linkedFactId,
            UUID linkedDefenseId) {

        Create {
            subject = trim(subject);
            details = trim(details);
            requiredDocuments = trim(requiredDocuments);
            addressee = trim(addressee);
            notes = trim(notes);
        }
    }

    /** The editable fields as a whole, under the version the caller read. */
    record Update(
            @NotBlank @Size(max = 300) String subject,
            UUID unitId,
            @Size(max = 4000) String details,
            @Size(max = 2000) String requiredDocuments,
            LocalDate draftedOn,
            LocalDate sentOn,
            @Size(max = 100) String letterNo,
            LocalDate letterDate,
            LocalDate dueOn,
            UUID responsibleMemberId,
            @Size(max = 200) String addressee,
            @Size(max = 2000) String notes,
            @Size(max = 2000) String delayReason,
            UUID linkedFactId,
            UUID linkedDefenseId,
            @NotNull Long version) {

        Update {
            subject = trim(subject);
            details = trim(details);
            requiredDocuments = trim(requiredDocuments);
            letterNo = trim(letterNo);
            addressee = trim(addressee);
            notes = trim(notes);
            delayReason = trim(delayReason);
        }
    }

    record StatusChange(
            @NotNull InfoRequestStatus status,
            LocalDate sentOn,
            @Size(max = 100) String letterNo,
            LocalDate letterDate,
            @Size(max = 2000) String closeReason,
            @NotNull Long version) {

        StatusChange {
            letterNo = trim(letterNo);
            closeReason = trim(closeReason);
        }
    }

    record ReplyInput(
            @NotNull LocalDate receivedOn,
            @NotNull Completeness completeness,
            @Size(max = 4000) String summary,
            UUID documentId,
            UUID linkedFactId,
            UUID linkedDefenseId) {

        ReplyInput {
            summary = trim(summary);
        }
    }

    enum Completeness { PARTIAL, INCOMPLETE, COMPLETE }

    enum FollowUpKind { REMINDER, RE_ADDRESS, ESCALATION }

    record FollowUpInput(
            @NotNull FollowUpKind kind,
            LocalDate onDate,
            @Size(max = 2000) String note,
            UUID escalatedTo) {

        FollowUpInput {
            note = trim(note);
        }
    }

    record Reply(UUID id, UUID requestId, LocalDate receivedOn, String completeness, String summary, UUID documentId,
            String documentTitle, UUID linkedFactId, UUID linkedDefenseId, Instant createdAt, UUID createdBy,
            String createdByName) {
    }

    record FollowUp(UUID id, UUID requestId, String kind, LocalDate onDate, String note, UUID escalatedTo,
            String escalatedToName, Instant createdAt, UUID by, String byName) {
    }

    record Response(UUID id, UUID caseId, String caseReferenceNo, String caseTitle, String requestNo, UUID unitId,
            String unitName, String subject, String details, String requiredDocuments, LocalDate draftedOn,
            LocalDate sentOn, String letterNo, LocalDate letterDate, LocalDate dueOn, UUID responsibleMemberId,
            String responsibleMemberName, String addressee, String notes, String status, String delayReason,
            String closeReason, LocalDate closedOn, boolean overdue, UUID linkedFactId, UUID linkedDefenseId,
            int replyCount, List<Reply> replies, List<FollowUp> followUps, long version, Instant createdAt,
            Instant updatedAt) {

        Response withChildren(List<Reply> newReplies, List<FollowUp> newFollowUps) {
            return new Response(id, caseId, caseReferenceNo, caseTitle, requestNo, unitId, unitName, subject, details,
                    requiredDocuments, draftedOn, sentOn, letterNo, letterDate, dueOn, responsibleMemberId,
                    responsibleMemberName, addressee, notes, status, delayReason, closeReason, closedOn, overdue,
                    linkedFactId, linkedDefenseId, replyCount, newReplies, newFollowUps, version, createdAt, updatedAt);
        }
    }

    /** One unit's row of the responsiveness report. */
    record UnitReport(UUID unitId, String unitName, long requests, long replies, long answeredRequests, long overdue,
            long incompleteReplies, long affectedCases, Double averageReplyDays) {
    }

    record Report(List<UnitReport> units) {
    }
}
