package com.lawdesk.documents;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.lawdesk.api.BusinessRuleException;
import com.lawdesk.api.ConflictException;
import com.lawdesk.api.FieldErrorException;
import com.lawdesk.api.NotFoundException;
import com.lawdesk.audit.AuditLog;
import com.lawdesk.cases.CaseAccessPolicy;
import com.lawdesk.cases.CaseLookup;
import com.lawdesk.cases.CaseSummary;
import com.lawdesk.security.ActorRef;
import com.lawdesk.security.CurrentActor;

/**
 * The electronic archive of a case (LD-026, LD-055; requirements section 13): documents in PDF, Word and image formats within
 * the limits of {@link ArchiveSettings}, each with its versions (earlier ones are kept), a classification, and a deletion
 * procedure. Every operation goes through the case access rule: a case the user cannot see answers 404, one seen but not
 * changeable 403.
 */
@Service
class DocumentService {

    private static final int MAX_FILE_NAME = 255;
    private static final int MAX_TITLE = 200;
    private static final int MAX_NOTE = 500;
    private static final int MAX_DESCRIPTION = 2000;
    /** Recorded when the old DELETE call removes a document without saying why. */
    static final String UNSPECIFIED_REASON = "removed without a stated reason";

    private final DocumentStore store;
    private final CaseLookup cases;
    private final CaseAccessPolicy access;
    private final CurrentActor currentActor;
    private final AuditLog audit;
    private final ArchiveSettings settings;
    private final DocumentTextService texts;
    private final JdbcClient jdbc;

    DocumentService(DocumentStore store, CaseLookup cases, CaseAccessPolicy access, CurrentActor currentActor,
            AuditLog audit, ArchiveSettings settings, DocumentTextService texts, JdbcClient jdbc) {
        this.store = store;
        this.cases = cases;
        this.access = access;
        this.currentActor = currentActor;
        this.audit = audit;
        this.settings = settings;
        this.texts = texts;
        this.jdbc = jdbc;
    }

    // ---- attaching and versions --------------------------------------------------------------------------------

    @Transactional
    CaseDocument attach(UUID caseId, MultipartFile file, String title) {
        ActorRef actor = currentActor.require();
        Checked checked = checked(file);
        String fileName = safeFileName(file.getOriginalFilename(), checked.extension());
        String chosenTitle = title == null || title.isBlank() ? null : title.strip();
        if (chosenTitle != null && chosenTitle.length() > MAX_TITLE) {
            throw new FieldErrorException("title", "Size");
        }
        CaseSummary legalCase = writableCase(caseId);

        store.lockCase(caseId);
        int limit = settings.limits().maxDocumentsPerCase();
        if (store.countForCase(caseId) >= limit) {
            throw new BusinessRuleException(Rules.DOCUMENT_LIMIT_REACHED, "a case holds at most " + limit + " documents");
        }
        CaseDocument stored = store.insert(caseId, chosenTitle == null ? truncate(fileName, MAX_TITLE) : chosenTitle,
                fileName, checked.mediaType(), checked.content(), sha256(checked.content()), actor.id());
        texts.index(stored.id(), stored.id(), checked.mediaType(), checked.content());
        cases.recordEvent(legalCase.id(), "DOCUMENT_ATTACHED", "Document \"%s\" attached (%s, %d bytes)"
                .formatted(stored.title(), stored.fileName(), stored.sizeBytes()));
        return stored;
    }

    /** Stores a new version of the document; the earlier versions stay and the new one becomes the current file. */
    @Transactional
    DocumentVersion addVersion(UUID documentId, MultipartFile file, String note) {
        ActorRef actor = currentActor.require();
        Checked checked = checked(file);
        String changeNote = note == null || note.isBlank() ? null : note.strip();
        if (changeNote != null && changeNote.length() > MAX_NOTE) {
            throw new FieldErrorException("note", "Size");
        }
        CaseDocument document = get(documentId);
        CaseSummary legalCase = writableCase(document.caseId());
        // Lock the head so two uploads cannot take the same number.
        document = store.findForUpdate(documentId).orElseThrow(() -> new NotFoundException("document"));
        requireInCaseFile(document);
        requireNotLocked(document.id());
        int limit = settings.limits().maxVersionsPerDocument();
        if (store.versionCount(documentId) >= limit) {
            throw new BusinessRuleException(Rules.DOCUMENT_VERSION_LIMIT_REACHED,
                    "a document keeps at most " + limit + " versions");
        }
        int number = store.nextVersionNo(documentId);
        DocumentVersion version = store.insertVersion(UUID.randomUUID(), documentId, number,
                safeFileName(file.getOriginalFilename(), checked.extension()), checked.mediaType(), checked.content(),
                sha256(checked.content()), changeNote, actor.id());
        store.makeCurrent(documentId, version, actor.id());
        texts.index(version.id(), documentId, checked.mediaType(), checked.content());
        cases.recordEvent(legalCase.id(), "DOCUMENT_VERSION_ADDED", "Version %d of document \"%s\" added (%s)"
                .formatted(number, document.title(), version.fileName()));
        return version;
    }

    @Transactional(readOnly = true)
    List<DocumentVersion> versions(UUID documentId) {
        get(documentId);
        return store.versions(documentId);
    }

    // ---- reading -----------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    List<CaseDocument> forCase(UUID caseId) {
        if (cases.find(caseId).isEmpty()) {
            throw new NotFoundException("case");
        }
        return store.forCase(caseId);
    }

    /** Documents taken out of the case file, still recoverable, for those who may restore or delete them. */
    @Transactional(readOnly = true)
    List<CaseDocument> withdrawnForCase(UUID caseId) {
        if (cases.find(caseId).isEmpty()) {
            throw new NotFoundException("case");
        }
        return store.withdrawnForCase(caseId);
    }

    /** The latest documents of every case the user may see, newest first, for the documents screen. */
    @Transactional(readOnly = true)
    Page<CaseDocument> latest(Pageable pageable) {
        CaseAccessPolicy.Predicate visible = access.visibleCaseIds("case_id");
        return new PageImpl<>(store.latest(visible, pageable.getOffset(), pageable.getPageSize()),
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()), store.count(visible));
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
        CaseDocument document = store.find(id).orElseThrow(() -> new NotFoundException("document"));
        // A document is as visible as its case: one the user may not see is reported missing.
        if (!access.canView(document.caseId())) {
            throw new NotFoundException("document");
        }
        return document;
    }

    /** The current file and its metadata; every download is recorded in the security audit log (ASSUMPTION). */
    @Transactional(readOnly = true)
    Download download(UUID id) {
        CaseDocument document = get(id);
        return download(document, store.version(id, document.currentVersionNo()).orElseThrow(() -> new NotFoundException("document")));
    }

    @Transactional(readOnly = true)
    Download downloadVersion(UUID id, int versionNo) {
        CaseDocument document = get(id);
        return download(document, store.version(id, versionNo).orElseThrow(() -> new NotFoundException("version")));
    }

    private Download download(CaseDocument document, DocumentVersion version) {
        ActorRef actor = currentActor.require();
        // A withdrawn document is out of the case file: only those who manage or administer the archive still reach its file.
        if (document.withdrawnAt() != null && !currentActor.has(com.lawdesk.security.Permission.DOCUMENT_MANAGE)
                && !currentActor.has(com.lawdesk.security.Permission.DOCUMENT_ARCHIVE_ADMIN)) {
            throw new NotFoundException("document");
        }
        byte[] content = store.content(version.id()).orElseThrow(() -> new NotFoundException("document"));
        audit.record("DOCUMENT_DOWNLOAD", AuditLog.Outcome.SUCCESS, actor.id(), actor.username(), "DOCUMENT",
                document.id().toString(), "version " + version.versionNo());
        return new Download(document, version, content);
    }

    record Download(CaseDocument document, DocumentVersion version, byte[] content) {
    }

    // ---- classification ----------------------------------------------------------------------------------------

    @Transactional
    CaseDocument classify(UUID id, Long version, String title, UUID categoryId, LocalDate date, UUID proceedingId,
            String description) {
        ActorRef actor = currentActor.require();
        if (version == null) {
            throw new FieldErrorException("version", "NotNull");
        }
        CaseDocument document = get(id);
        writableCase(document.caseId());
        requireInCaseFile(document);
        String newTitle = title == null || title.isBlank() ? document.title() : title.strip();
        if (newTitle.length() > MAX_TITLE) {
            throw new FieldErrorException("title", "Size");
        }
        String note = description == null || description.isBlank() ? null : description.strip();
        if (note != null && note.length() > MAX_DESCRIPTION) {
            throw new FieldErrorException("description", "Size");
        }
        if (categoryId != null && !jdbc.sql("""
                select exists (select 1 from lookup_value where id = :id and list_code = 'DOCUMENT_CATEGORY'
                               and (active or id = :kept))""").param("id", categoryId)
                .param("kept", document.categoryId() == null ? new UUID(0, 0) : document.categoryId())
                .query(Boolean.class).single()) {
            throw new FieldErrorException("categoryId", "Exists");
        }
        if (proceedingId != null && !jdbc.sql("select exists (select 1 from proceeding where id = :id and case_id = :c)")
                .param("id", proceedingId).param("c", document.caseId()).query(Boolean.class).single()) {
            throw new FieldErrorException("proceedingId", "NotInCase");
        }
        if (!store.classify(id, version, newTitle, categoryId, date, proceedingId, note, actor.id())) {
            throw new ConflictException("document was changed by someone else");
        }
        cases.recordEvent(document.caseId(), "DOCUMENT_CLASSIFIED", "Document \"%s\" classified".formatted(newTitle));
        audit.recordWithChange("DOCUMENT_CLASSIFY", actor.id(), actor.username(), "DOCUMENT", id.toString(), null);
        return get(id);
    }

    // ---- taking a document out of the case file ----------------------------------------------------------------

    /** The old DELETE call: the document leaves the case file but is kept, as {@link #withdraw} does. */
    @Transactional
    void remove(UUID id, Long version) {
        withdraw(id, version, UNSPECIFIED_REASON);
    }

    /** Takes the document out of the case file without deleting anything; it can be restored or deleted by the archive procedure. */
    @Transactional
    void withdraw(UUID id, Long version, String reason) {
        ActorRef actor = currentActor.require();
        if (version == null) {
            throw new FieldErrorException("version", "NotNull");
        }
        String why = requiredReason(reason);
        CaseDocument document = get(id);
        access.requireChange(document.caseId());
        writableCase(document.caseId());
        store.lockCase(document.caseId());
        if (document.withdrawnAt() != null) {
            throw new BusinessRuleException(Rules.DOCUMENT_WITHDRAWN, "the document is already withdrawn");
        }
        requireNotLocked(id);
        if (!store.withdraw(id, version, why, actor.id())) {
            throw new ConflictException("document was changed by someone else");
        }
        cases.recordEvent(document.caseId(), "DOCUMENT_REMOVED", "Document \"%s\" removed (%s)"
                .formatted(document.title(), document.fileName()));
        audit.recordWithChange("DOCUMENT_WITHDRAW", actor.id(), actor.username(), "DOCUMENT", id.toString(), why);
    }

    @Transactional
    void restore(UUID id, Long version) {
        ActorRef actor = currentActor.require();
        if (version == null) {
            throw new FieldErrorException("version", "NotNull");
        }
        CaseDocument document = get(id);
        writableCase(document.caseId());
        store.lockCase(document.caseId());
        if (document.withdrawnAt() == null) {
            throw new BusinessRuleException(Rules.DOCUMENT_NOT_WITHDRAWN, "the document is in the case file");
        }
        int limit = settings.limits().maxDocumentsPerCase();
        if (store.countForCase(document.caseId()) >= limit) {
            throw new BusinessRuleException(Rules.DOCUMENT_LIMIT_REACHED, "a case holds at most " + limit + " documents");
        }
        if (!store.restore(id, version, actor.id())) {
            throw new ConflictException("document was changed by someone else");
        }
        cases.recordEvent(document.caseId(), "DOCUMENT_RESTORED", "Document \"%s\" restored".formatted(document.title()));
        audit.recordWithChange("DOCUMENT_RESTORE", actor.id(), actor.username(), "DOCUMENT", id.toString(), null);
    }

    /**
     * The approved deletion procedure: a withdrawn document is deleted for good, with every version, its text and its links,
     * by a holder of DOCUMENT_ARCHIVE_ADMIN (checked by the controller) who states why. Nothing referencing the document may
     * remain, the deletion is logged without file content and audited, and the database refuses any other delete.
     */
    @Transactional
    void purge(UUID id, String reason) {
        ActorRef actor = currentActor.require();
        String why = requiredReason(reason);
        CaseDocument document = get(id);
        if (document.withdrawnAt() == null) {
            throw new BusinessRuleException(Rules.DOCUMENT_NOT_WITHDRAWN,
                    "a document is withdrawn from the case file before it can be deleted");
        }
        if (referenced(id)) {
            throw new BusinessRuleException(Rules.DOCUMENT_REFERENCED, "records of the case still refer to this document");
        }
        Map<String, Object> withdrawal = store.withdrawal(id);
        jdbc.sql("""
                insert into document_deletion_log (document_id, case_id, title, file_name, media_type, version_count,
                       sha256_list, withdrawn_by, withdrawn_at, withdrawn_reason, deleted_by, deleted_reason)
                select d.id, d.case_id, d.title, d.file_name, d.media_type, count(v.id),
                       string_agg(v.sha256, ',' order by v.version_no), cast(:wby as uuid), cast(:wat as timestamptz), cast(:wreason as varchar), :by, cast(:reason as varchar)
                from case_document d join document_version v on v.document_id = d.id where d.id = :id
                group by d.id""").param("wby", withdrawal.get("withdrawn_by")).param("wat", withdrawal.get("withdrawn_at"))
                .param("wreason", withdrawal.get("withdrawn_reason")).param("by", actor.id()).param("reason", why)
                .param("id", id).update();
        jdbc.sql("select set_config('lawdesk.archive_purge', 'on', true)").query(String.class).single();
        jdbc.sql("delete from case_document where id = :id").param("id", id).update();
        audit.recordWithChange("DOCUMENT_PURGE", actor.id(), actor.username(), "DOCUMENT", id.toString(), why);
    }

    /** True when any record still points at the document (evidence, a claim's source, a memo, a copy, a letter...). */
    private boolean referenced(UUID id) {
        return jdbc.sql("""
                select exists (select 1 from evidence_item where document_id = :id)
                    or exists (select 1 from claim where source_document_id = :id)
                    or exists (select 1 from defense where memo_document_id = :id)
                    or exists (select 1 from defense_assertion where memo_document_id = :id)
                    or exists (select 1 from judgment where copy_document_id = :id)
                    or exists (select 1 from info_reply where document_id = :id)
                    or exists (select 1 from case_link where decision_document_id = :id)
                    or exists (select 1 from legal_document where document_id = :id)
                    or exists (select 1 from enforcement_action where document_id = :id)""").param("id", id)
                .query(Boolean.class).single();
    }

    /** The log of permanent deletions in the cases the user may see. */
    @Transactional(readOnly = true)
    List<DeletionEntry> deletionLog(UUID caseId) {
        CaseAccessPolicy.Predicate visible = access.visibleCaseIds("case_id");
        Map<String, Object> params = new HashMap<>(visible.params());
        String filter = "";
        if (caseId != null) {
            params.put("caseId", caseId);
            filter = " and case_id = :caseId";
        }
        return jdbc.sql("""
                select id, document_id, case_id, title, file_name, media_type, version_count, withdrawn_by, withdrawn_at,
                       withdrawn_reason, deleted_by, deleted_at, deleted_reason
                from document_deletion_log where""" + " " + visible.sql() + filter + " order by deleted_at desc, id limit 200")
                .params(params).query((rs, n) -> new DeletionEntry(rs.getObject("id", UUID.class),
                        rs.getObject("document_id", UUID.class), rs.getObject("case_id", UUID.class), rs.getString("title"),
                        rs.getString("file_name"), rs.getString("media_type"), rs.getInt("version_count"),
                        rs.getObject("withdrawn_by", UUID.class),
                        rs.getTimestamp("withdrawn_at") == null ? null : rs.getTimestamp("withdrawn_at").toInstant(),
                        rs.getString("withdrawn_reason"), rs.getObject("deleted_by", UUID.class),
                        rs.getTimestamp("deleted_at").toInstant(), rs.getString("deleted_reason"))).list();
    }

    record DeletionEntry(UUID id, UUID documentId, UUID caseId, String title, String fileName, String mediaType,
            int versionCount, UUID withdrawnBy, java.time.Instant withdrawnAt, String withdrawnReason, UUID deletedBy,
            java.time.Instant deletedAt, String deletedReason) {
    }

    /** Refuses the call (404/403) unless the user may change the document's case; used by operations the service does not wrap. */
    void requireChangeable(CaseDocument document) {
        writableCase(document.caseId());
    }

    // ---- rules -------------------------------------------------------------------------------------------------

    private CaseSummary writableCase(UUID caseId) {
        CaseSummary legalCase = cases.find(caseId).orElseThrow(() -> new NotFoundException("case"));
        access.requireChange(caseId);
        if ("ARCHIVED".equals(legalCase.status())) {
            throw new BusinessRuleException(Rules.CASE_ARCHIVED, "an archived case is read-only");
        }
        return legalCase;
    }

    private static void requireInCaseFile(CaseDocument document) {
        if (document.withdrawnAt() != null) {
            throw new BusinessRuleException(Rules.DOCUMENT_WITHDRAWN, "the document is withdrawn from the case file");
        }
    }

    /** The electronic copy of an approved, sent or archived memo or letter is not changed or withdrawn. */
    private void requireNotLocked(UUID documentId) {
        if (jdbc.sql("""
                select exists (select 1 from legal_document where document_id = :id
                               and status in ('APPROVED', 'SENT', 'ARCHIVED'))""").param("id", documentId)
                .query(Boolean.class).single()) {
            throw new BusinessRuleException(Rules.LEGAL_DOCUMENT_LOCKED,
                    "the document is the copy of an approved memo or letter; return it to draft first");
        }
    }

    private static String requiredReason(String reason) {
        String why = reason == null ? "" : reason.strip();
        if (why.isEmpty()) {
            throw new FieldErrorException("reason", "NotBlank");
        }
        if (why.length() > MAX_NOTE) {
            throw new FieldErrorException("reason", "Size");
        }
        return why;
    }

    /** The file once it passed presence, size, format (by signature) and declared-type checks. */
    private record Checked(byte[] content, String mediaType, String extension) {
    }

    private Checked checked(MultipartFile file) {
        if (file == null) {
            throw new FieldErrorException("file", "NotNull");
        }
        if (file.isEmpty()) {
            throw new FieldErrorException("file", "NotEmpty");
        }
        if (file.getSize() > settings.limits().maxFileBytes()) {
            throw new FieldErrorException("file", "MaxSize");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        FileFormats.Detected detected = FileFormats.detect(content)
                .orElseThrow(() -> new FieldErrorException("file", "UnsupportedFormat"));
        List<ArchiveSettings.Format> formats = settings.formats();
        ArchiveSettings.Format format = formats.stream().filter(f -> f.code().equals(detected.code())).findFirst().orElseThrow();
        if (!format.enabled() || !FileFormats.declared(file.getContentType()).equals(detected.mediaType())) {
            throw new FieldErrorException("file", "UnsupportedFormat");
        }
        return new Checked(content, detected.mediaType(), format.extension());
    }

    /** The last path segment without control characters, at most 255 characters; "document.&lt;ext&gt;" when nothing is left. */
    static String safeFileName(String original, String extension) {
        String name = original == null ? "" : original;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        name = name.replaceAll("\\p{Cntrl}", "").strip();
        return name.isEmpty() ? "document." + extension : truncate(name, MAX_FILE_NAME);
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
