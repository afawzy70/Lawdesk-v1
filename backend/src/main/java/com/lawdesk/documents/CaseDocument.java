package com.lawdesk.documents;

import java.time.Instant;
import java.util.UUID;

/** The metadata of one stored PDF (V403__LD-026_case_document.sql); answers never carry the bytes. */
record CaseDocument(UUID id, UUID caseId, String title, String fileName, String mediaType, int sizeBytes,
        String sha256, long version, Instant createdAt, UUID createdBy) {
}
