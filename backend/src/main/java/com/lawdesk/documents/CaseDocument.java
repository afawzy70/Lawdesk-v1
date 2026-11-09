package com.lawdesk.documents;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The head of one archived document (V403, V701): the metadata of its current version plus its classification and, when it
 * was taken out of the case file, the withdrawal. Answers never carry the bytes.
 */
record CaseDocument(UUID id, UUID caseId, String title, String fileName, String mediaType, int sizeBytes,
        String sha256, long version, Instant createdAt, UUID createdBy, UUID categoryId, LocalDate documentDate,
        UUID proceedingId, String description, int currentVersionNo, Instant withdrawnAt, String withdrawnReason,
        Instant updatedAt) {
}
