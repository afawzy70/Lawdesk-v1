package com.lawdesk.legaldocs;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Requests and answers of the memo and correspondence register (LD-055, requirements section 12). */
final class LegalDocumentRequests {

    private LegalDocumentRequests() {
    }

    /** The data of a memo or letter; {@code version} is required when changing one. */
    record Body(String title, UUID categoryId, LocalDate documentDate, String issuer, String addressee,
            UUID responsibleMemberId, String description, UUID documentId, Long version) {
    }

    /** A note for a transition (the reason for returning or reopening, the approval note). */
    record Note(String note, Long version) {
    }

    /** The record that a document was sent: a user's statement, not a delivery by a system. */
    record SentRecord(LocalDate sentOn, String method, String reference, Long version) {
    }

    record View(UUID id, UUID caseId, String caseReferenceNo, String caseTitle, UUID categoryId, String categoryLabelAr,
            String categoryLabelEn, String title, LocalDate documentDate, String issuer, String addressee,
            UUID responsibleMemberId, String responsibleMemberName, String description, UUID documentId,
            String documentFileName, Integer documentVersionNo, String status, UUID submittedBy, Instant submittedAt,
            UUID approvedBy, String approvedByName, Instant approvedAt, String approvalNote, boolean sentRecorded,
            LocalDate sentOn, String sentMethod, String sentReference, UUID sentRecordedBy, Instant sentRecordedAt,
            boolean sentViaIntegration, Instant archivedAt, long version, Instant createdAt, UUID createdBy,
            Instant updatedAt) {
    }

    record Event(UUID id, String fromStatus, String toStatus, String note, Instant createdAt, UUID createdBy,
            String createdByName) {
    }
}
