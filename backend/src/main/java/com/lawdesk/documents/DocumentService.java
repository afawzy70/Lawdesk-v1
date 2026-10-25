package com.lawdesk.documents;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.lawdesk.api.BusinessRuleException;
import com.lawdesk.api.ConflictException;
import com.lawdesk.api.FieldErrorException;
import com.lawdesk.api.NotFoundException;
import com.lawdesk.audit.AuditLog;
import com.lawdesk.cases.CaseLookup;
import com.lawdesk.cases.CaseSummary;
import com.lawdesk.security.ActorRef;
import com.lawdesk.security.CurrentActor;

/**
 * Legal PDF attachments of a case (LD-026, docs/delivery/PHASE-5-ACCEPTANCE.md, architecture/CONTRACTS.md §8.3): at
 * most ten per case, each at most 10 MiB, declared as {@code application/pdf} and starting with {@code %PDF-}.
 */
@Service
class DocumentService {

    /** At most ten documents per case (ASSUMPTION, §8.3). */
    static final int MAX_PER_CASE = 10;
    /** At most 10 MiB per file (ASSUMPTION, §8.3); the multipart limit in application.yaml is the same. */
    static final int MAX_BYTES = 10 * 1024 * 1024;
    static final String PDF = "application/pdf";

    private static final byte[] MAGIC = "%PDF-".getBytes(StandardCharsets.ISO_8859_1);
    private static final int MAX_FILE_NAME = 255;
    private static final int MAX_TITLE = 200;

    private final DocumentStore store;
    private final CaseLookup cases;
    private final CurrentActor currentActor;
    private final AuditLog audit;

    DocumentService(DocumentStore store, CaseLookup cases, CurrentActor currentActor, AuditLog audit) {
        this.store = store;
        this.cases = cases;
        this.currentActor = currentActor;
        this.audit = audit;
    }

    @Transactional
    CaseDocument attach(UUID caseId, MultipartFile file, String title) {
        ActorRef actor = currentActor.require();
        byte[] content = checkedPdf(file);
        String fileName = safeFileName(file.getOriginalFilename());
        String chosenTitle = title == null || title.isBlank() ? null : title.strip();
        if (chosenTitle != null && chosenTitle.length() > MAX_TITLE) {
            throw new FieldErrorException("title", "Size");
        }
        CaseSummary legalCase = writableCase(caseId);

        store.lockCase(caseId);
        if (store.countForCase(caseId) >= MAX_PER_CASE) {
            throw new BusinessRuleException(Rules.DOCUMENT_LIMIT_REACHED,
                    "a case holds at most " + MAX_PER_CASE + " documents");
        }
        CaseDocument stored = store.insert(caseId, chosenTitle == null ? truncate(fileName, MAX_TITLE) : chosenTitle,
                fileName, content, sha256(content), actor.id());
        cases.recordEvent(legalCase.id(), "DOCUMENT_ATTACHED", "Document \"%s\" attached (%s, %d bytes)"
                .formatted(stored.title(), stored.fileName(), stored.sizeBytes()));
        return stored;
    }

    @Transactional(readOnly = true)
    List<CaseDocument> forCase(UUID caseId) {
        if (cases.find(caseId).isEmpty()) {
            throw new NotFoundException("case");
        }
        return store.forCase(caseId);
    }

    /** The latest documents of every case, newest first, for the documents screen. */
    @Transactional(readOnly = true)
    Page<CaseDocument> latest(Pageable pageable) {
        return new PageImpl<>(store.latest(pageable.getOffset(), pageable.getPageSize()),
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()), store.count());
    }

    /** What the answers say about each document's case; a page holds a few cases. */
    @Transactional(readOnly = true)
    Map<UUID, CaseSummary> casesOf(List<CaseDocument> documents) {
        Map<UUID, CaseSummary> found = new HashMap<>();
        for (CaseDocument document : documents) {
            if (!found.containsKey(document.caseId())) {
                cases.find(document.caseId()).ifPresent(c -> found.put(c.id(), c));
            }
        }
        return found;
    }

    @Transactional(readOnly = true)
    CaseDocument get(UUID id) {
        return store.find(id).orElseThrow(() -> new NotFoundException("document"));
    }

    /** The file and its metadata; every download is recorded in the security audit log (ASSUMPTION). */
    @Transactional(readOnly = true)
    Download download(UUID id) {
        ActorRef actor = currentActor.require();
        CaseDocument document = get(id);
        byte[] content = store.content(id).orElseThrow(() -> new NotFoundException("document"));
        audit.record("DOCUMENT_DOWNLOAD", AuditLog.Outcome.SUCCESS, actor.id(), actor.username(), "DOCUMENT",
                id.toString(), null);
        return new Download(document, content);
    }

    @Transactional
    void remove(UUID id, Long version) {
        currentActor.require();
        if (version == null) {
            throw new FieldErrorException("version", "NotNull");
        }
        CaseDocument document = get(id);
        writableCase(document.caseId());
        store.lockCase(document.caseId());
        if (!store.delete(id, version)) {
            if (store.find(id).isPresent()) {
                throw new ConflictException("document was changed by someone else");
            }
            throw new NotFoundException("document");
        }
        cases.recordEvent(document.caseId(), "DOCUMENT_REMOVED", "Document \"%s\" removed (%s)"
                .formatted(document.title(), document.fileName()));
    }

    record Download(CaseDocument document, byte[] content) {
    }

    private CaseSummary writableCase(UUID caseId) {
        CaseSummary legalCase = cases.find(caseId).orElseThrow(() -> new NotFoundException("case"));
        if ("ARCHIVED".equals(legalCase.status())) {
            throw new BusinessRuleException(Rules.CASE_ARCHIVED, "an archived case is read-only");
        }
        return legalCase;
    }

    /** The file's bytes, once it passed the presence, size and PDF checks. */
    private static byte[] checkedPdf(MultipartFile file) {
        if (file == null) {
            throw new FieldErrorException("file", "NotNull");
        }
        if (file.isEmpty()) {
            throw new FieldErrorException("file", "NotEmpty");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new FieldErrorException("file", "MaxSize");
        }
        String declared = file.getContentType() == null ? "" : file.getContentType();
        if (!PDF.equals(declared.split(";", 2)[0].strip().toLowerCase(Locale.ROOT))) {
            throw new FieldErrorException("file", "PdfOnly");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (content.length < MAGIC.length || !Arrays.equals(content, 0, MAGIC.length, MAGIC, 0, MAGIC.length)) {
            throw new FieldErrorException("file", "PdfOnly");
        }
        return content;
    }

    /** The last path segment without control characters, at most 255 characters; "document.pdf" when nothing is left. */
    static String safeFileName(String original) {
        String name = original == null ? "" : original;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        name = name.replaceAll("\\p{Cntrl}", "").strip();
        return name.isEmpty() ? "document.pdf" : truncate(name, MAX_FILE_NAME);
    }

    private static String truncate(String value, int length) {
        return value.length() <= length ? value : value.substring(0, length);
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
