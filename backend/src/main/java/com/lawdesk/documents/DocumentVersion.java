package com.lawdesk.documents;

import java.time.Instant;
import java.util.UUID;

/** One stored version of a document (V701). Version 1 of a document that existed before WP5 has the document's own id. */
record DocumentVersion(UUID id, UUID documentId, int versionNo, String fileName, String mediaType, int sizeBytes,
        String sha256, String changeNote, UUID uploadedBy, Instant uploadedAt) {
}
