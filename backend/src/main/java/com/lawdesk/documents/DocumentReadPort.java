package com.lawdesk.documents;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.api.NotFoundException;
import com.lawdesk.cases.CaseAccessPolicy;
import com.lawdesk.security.CurrentActor;
import com.lawdesk.security.Permission;

/**
 * The read-only door other modules use to reach the text of archived documents (LD-059: the legal assistant). It adds nothing
 * of its own to {@link DocumentTextService}: the text search is that service, and reading one document's text is allowed only
 * to a holder of DOCUMENT_VIEW who may see the document's case (a case that is not visible, or a document that is withdrawn
 * from the case file, is reported as missing). The text is returned as data, to be shown or quoted, never interpreted.
 */
@Component
public class DocumentReadPort {

    /** A piece of the current text of a document the user may see. */
    public record TextHit(UUID documentId, UUID caseId, String caseReferenceNo, String title, String fileName, int versionNo,
            String snippet) {
    }

    /** The current version's text, with the state of the text extraction ({@code status} says whether there is any). */
    public record DocumentText(UUID documentId, UUID caseId, String caseReferenceNo, String title, String fileName, int versionNo,
            String status, Integer pageCount, boolean truncated, String text) {
    }

    private final DocumentTextService texts;
    private final JdbcClient jdbc;
    private final CaseAccessPolicy access;
    private final CurrentActor currentActor;

    DocumentReadPort(DocumentTextService texts, JdbcClient jdbc, CaseAccessPolicy access, CurrentActor currentActor) {
        this.texts = texts;
        this.jdbc = jdbc;
        this.access = access;
        this.currentActor = currentActor;
    }

    /** Documents of visible cases whose text holds every word of {@code query}; empty without DOCUMENT_VIEW or for a word too short to search. */
    @Transactional(readOnly = true)
    public List<TextHit> findText(String query, UUID caseId, int limit) {
        // A word too short to search is turned away here: refusing it inside the search would mark the caller's transaction.
        if (!currentActor.has(Permission.DOCUMENT_VIEW) || query == null || query.strip().length() < 3) {
            return List.of();
        }
        return texts.search(query, caseId, null, PageRequest.of(0, limit)).getContent().stream()
                .map(h -> new TextHit(h.documentId(), h.caseId(), h.caseReferenceNo(), h.title(), h.fileName(), h.versionNo(),
                        h.snippet())).toList();
    }

    /**
     * @throws AccessDeniedException without DOCUMENT_VIEW (403)
     * @throws NotFoundException     when the document does not exist, is withdrawn, or its case is not visible (404)
     */
    @Transactional(readOnly = true)
    public DocumentText text(UUID documentId) {
        if (!currentActor.has(Permission.DOCUMENT_VIEW)) {
            throw new AccessDeniedException("missing DOCUMENT_VIEW");
        }
        record Head(UUID caseId, String referenceNo, String title, String fileName, int versionNo) {
        }
        Optional<Head> head = jdbc.sql("""
                select d.case_id, c.reference_no, d.title, d.file_name, d.current_version_no
                from case_document d join legal_case c on c.id = d.case_id
                where d.id = :id and d.withdrawn_at is null""").param("id", documentId)
                .query((rs, n) -> new Head(rs.getObject("case_id", UUID.class), rs.getString("reference_no"),
                        rs.getString("title"), rs.getString("file_name"), rs.getInt("current_version_no"))).optional();
        Head found = head.orElseThrow(() -> new NotFoundException("document"));
        access.requireView(found.caseId());
        Optional<DocumentTextService.TextView> view = jdbc.sql("""
                select v.id from document_version v where v.document_id = :id and v.version_no = :no""")
                .param("id", documentId).param("no", found.versionNo()).query(UUID.class).optional()
                .flatMap(versionId -> texts.view(versionId, true));
        return new DocumentText(documentId, found.caseId(), found.referenceNo(), found.title(), found.fileName(),
                found.versionNo(), view.map(DocumentTextService.TextView::status).orElse("NO_TEXT"),
                view.map(DocumentTextService.TextView::pageCount).orElse(null),
                view.map(DocumentTextService.TextView::truncated).orElse(false), view.map(DocumentTextService.TextView::text).orElse(null));
    }
}
