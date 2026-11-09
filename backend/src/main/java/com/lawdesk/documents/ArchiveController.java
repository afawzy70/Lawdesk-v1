package com.lawdesk.documents;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.data.domain.Pageable;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.lawdesk.api.PageResponse;
import com.lawdesk.audit.AuditLog;
import com.lawdesk.cases.CaseSummary;
import com.lawdesk.security.CurrentActor;
import com.lawdesk.security.Permission;

/**
 * The electronic archive beyond the first upload (LD-055, requirements section 13): versions, classification, the extracted
 * text and its search, the withdraw/restore/delete procedure, and the archive settings. The case rights are checked by
 * {@link DocumentService}; the permission here only says which kind of operation the user may attempt.
 */
@RestController
@RequestMapping("/api/v1")
class ArchiveController {

    record Version(UUID id, int versionNo, String fileName, String mediaType, int sizeBytes, String sha256,
            String changeNote, UUID uploadedBy, Instant uploadedAt, boolean current, DocumentTextService.TextView text) {
    }

    record Classification(String title, UUID categoryId, LocalDate documentDate, UUID proceedingId, String description,
            Long version) {
    }

    record Reason(String reason, Long version) {
    }

    record SettingsView(ArchiveSettings.Limits limits, List<ArchiveSettings.Format> formats, OcrStatus ocr) {
    }

    record OcrStatus(boolean available, String engine, String languages, String detail) {
    }

    record SettingsUpdate(ArchiveSettings.Limits limits, List<String> enabledFormats) {
    }

    private final DocumentService documents;
    private final DocumentTextService texts;
    private final ArchiveSettings settings;
    private final CurrentActor currentActor;
    private final AuditLog audit;

    ArchiveController(DocumentService documents, DocumentTextService texts, ArchiveSettings settings,
            CurrentActor currentActor, AuditLog audit) {
        this.documents = documents;
        this.texts = texts;
        this.settings = settings;
        this.currentActor = currentActor;
        this.audit = audit;
    }

    // ---- versions ----------------------------------------------------------------------------------------------

    @GetMapping("/documents/{id}/versions")
    @PreAuthorize(Permission.Codes.DOCUMENT_VIEW)
    List<Version> versions(@PathVariable UUID id) {
        CaseDocument document = documents.get(id);
        var statuses = texts.statuses(id);
        return documents.versions(id).stream().map(v -> version(v, document, statuses.get(v.id()))).toList();
    }

    /** Multipart, read lazily: the permission is checked before the upload is parsed (QAD-026). */
    @PostMapping("/documents/{id}/versions")
    @PreAuthorize(Permission.Codes.DOCUMENT_MANAGE)
    ResponseEntity<Version> addVersion(@PathVariable UUID id, HttpServletRequest request,
            @RequestPart(name = "file", required = false) MultipartFile file,
            @RequestParam(name = "note", required = false) String note) throws HttpMediaTypeNotSupportedException {
        String type = request.getContentType();
        if (type == null || !type.toLowerCase(Locale.ROOT).startsWith(MediaType.MULTIPART_FORM_DATA_VALUE)) {
            throw new HttpMediaTypeNotSupportedException("versions are uploaded as multipart/form-data");
        }
        DocumentVersion created = documents.addVersion(id, file, note);
        CaseDocument document = documents.get(id);
        return ResponseEntity.created(java.net.URI.create("/api/v1/documents/" + id + "/versions/" + created.versionNo()))
                .body(version(created, document, texts.view(created.id(), false).orElse(null)));
    }

    @GetMapping("/documents/{id}/versions/{versionNo}/content")
    @PreAuthorize(Permission.Codes.DOCUMENT_VIEW)
    ResponseEntity<byte[]> versionContent(@PathVariable UUID id, @PathVariable int versionNo) {
        DocumentService.Download download = documents.downloadVersion(id, versionNo);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(download.version().mediaType()))
                .contentLength(download.content().length).cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.version().fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff").body(download.content());
    }

    private static Version version(DocumentVersion v, CaseDocument document, DocumentTextService.TextView text) {
        return new Version(v.id(), v.versionNo(), v.fileName(), v.mediaType(), v.sizeBytes(), v.sha256(), v.changeNote(),
                v.uploadedBy(), v.uploadedAt(), v.versionNo() == document.currentVersionNo(), text);
    }

    // ---- classification and withdrawal -------------------------------------------------------------------------

    @PutMapping("/documents/{id}/classification")
    @PreAuthorize(Permission.Codes.DOCUMENT_MANAGE)
    DocumentController.Response classify(@PathVariable UUID id, @RequestBody Classification body) {
        CaseDocument document = documents.classify(id, body.version(), body.title(), body.categoryId(),
                body.documentDate(), body.proceedingId(), body.description());
        return respond(document);
    }

    @PostMapping("/documents/{id}/withdraw")
    @PreAuthorize(Permission.Codes.DOCUMENT_MANAGE)
    ResponseEntity<Void> withdraw(@PathVariable UUID id, @RequestBody Reason body) {
        documents.withdraw(id, body.version(), body.reason());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/documents/{id}/restore")
    @PreAuthorize(Permission.Codes.DOCUMENT_MANAGE)
    ResponseEntity<Void> restore(@PathVariable UUID id, @RequestBody Reason body) {
        documents.restore(id, body.version());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/cases/{caseId}/documents/withdrawn")
    @PreAuthorize(Permission.Codes.DOCUMENT_MANAGE)
    List<DocumentController.Response> withdrawn(@PathVariable UUID caseId) {
        List<CaseDocument> list = documents.withdrawnForCase(caseId);
        var cases = documents.casesOf(list);
        return list.stream().map(d -> DocumentController.Response.of(d, cases.get(d.caseId()))).toList();
    }

    /** The deletion procedure's last step: only DOCUMENT_ARCHIVE_ADMIN, only a withdrawn document, always with a reason. */
    @PostMapping("/documents/{id}/permanent-deletion")
    @PreAuthorize(Permission.Codes.DOCUMENT_ARCHIVE_ADMIN)
    ResponseEntity<Void> purge(@PathVariable UUID id, @RequestBody Reason body) {
        documents.purge(id, body.reason());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/documents/deletion-log")
    @PreAuthorize(Permission.Codes.DOCUMENT_ARCHIVE_ADMIN)
    List<DocumentService.DeletionEntry> deletionLog(@RequestParam(required = false) UUID caseId) {
        return documents.deletionLog(caseId);
    }

    private DocumentController.Response respond(CaseDocument document) {
        return DocumentController.Response.of(document, documents.casesOf(List.of(document)).get(document.caseId()));
    }

    // ---- text and search ---------------------------------------------------------------------------------------

    /** The text state of the current version, with the text when {@code withText} is true. */
    @GetMapping("/documents/{id}/text")
    @PreAuthorize(Permission.Codes.DOCUMENT_VIEW)
    ResponseEntity<DocumentTextService.TextView> text(@PathVariable UUID id,
            @RequestParam(defaultValue = "false") boolean withText) {
        CaseDocument document = documents.get(id);
        UUID versionId = documents.versions(id).stream().filter(v -> v.versionNo() == document.currentVersionNo())
                .findFirst().orElseThrow().id();
        return texts.view(versionId, withText).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Reads the current version again, for example after a recognition engine was installed. */
    @PostMapping("/documents/{id}/text/reprocess")
    @PreAuthorize(Permission.Codes.DOCUMENT_MANAGE)
    ResponseEntity<DocumentTextService.TextView> reprocess(@PathVariable UUID id) {
        CaseDocument document = documents.get(id);
        documents.requireChangeable(document);
        UUID versionId = documents.versions(id).stream().filter(v -> v.versionNo() == document.currentVersionNo())
                .findFirst().orElseThrow().id();
        texts.reindex(versionId);
        return ResponseEntity.ok(texts.view(versionId, false).orElseThrow());
    }

    @GetMapping("/documents/search")
    @PreAuthorize(Permission.Codes.DOCUMENT_VIEW)
    PageResponse<DocumentTextService.Hit> search(@RequestParam(name = "q", required = false) String query,
            @RequestParam(required = false) UUID caseId, @RequestParam(required = false) UUID categoryId,
            Pageable pageable) {
        return PageResponse.from(texts.search(query, caseId, categoryId, pageable));
    }

    // ---- settings ----------------------------------------------------------------------------------------------

    @GetMapping("/archive/settings")
    @PreAuthorize(Permission.Codes.DOCUMENT_VIEW)
    SettingsView settings() {
        return view();
    }

    @PutMapping("/archive/settings")
    @PreAuthorize(Permission.Codes.DOCUMENT_ARCHIVE_ADMIN)
    SettingsView update(@RequestBody SettingsUpdate body) {
        var actor = currentActor.require();
        if (body.limits() == null || body.enabledFormats() == null) {
            throw new com.lawdesk.api.FieldErrorException("limits", "NotNull");
        }
        settings.update(body.limits(), body.enabledFormats(), actor.id());
        audit.record("ARCHIVE_SETTINGS_UPDATE", AuditLog.Outcome.SUCCESS, actor.id(), actor.username(), "ARCHIVE", "settings",
                null);
        return view();
    }

    private SettingsView view() {
        OcrEngine.Status status = texts.engineStatus();
        return new SettingsView(settings.limits(), settings.formats(),
                new OcrStatus(status.available(), status.engine(), status.languages(), status.detail()));
    }
}
