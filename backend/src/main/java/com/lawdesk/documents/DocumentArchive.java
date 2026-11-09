package com.lawdesk.documents;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * The port other modules use to archive a document in a case file (LD-053: the reply to an information request is filed
 * with the case through the document module, so it has the same checks, limits and timeline entry as any upload).
 */
@Component
public class DocumentArchive {

    private final DocumentService service;
    private final DocumentStore store;

    DocumentArchive(DocumentService service, DocumentStore store) {
        this.service = service;
        this.store = store;
    }

    /**
     * Archives {@code file} in the case file, in the caller's transaction and as the signed-in user.
     *
     * @return the id of the stored document
     */
    @Transactional
    public UUID archive(UUID caseId, MultipartFile file, String title) {
        return service.attach(caseId, file, title).id();
    }

    /** True when the document exists and belongs to this case. */
    @Transactional(readOnly = true)
    public boolean belongsToCase(UUID documentId, UUID caseId) {
        return store.find(documentId).map(d -> d.caseId().equals(caseId)).orElse(false);
    }
}
