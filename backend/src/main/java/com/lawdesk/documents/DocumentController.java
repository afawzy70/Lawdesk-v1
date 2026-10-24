package com.lawdesk.documents;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.lawdesk.api.PageResponse;
import com.lawdesk.cases.CaseSummary;
import com.lawdesk.security.Permission;

/** Case PDF attachments, LD-026 (docs/delivery/PHASE-5-ACCEPTANCE.md, architecture/CONTRACTS.md §8.3). */
@RestController
@RequestMapping("/api/v1")
class DocumentController {

    /** Answers never carry the bytes; the file comes from {@code /content}. */
    record Response(UUID id, UUID caseId, String caseReferenceNo, String caseTitle, String title, String fileName,
            String mediaType, int sizeBytes, String sha256, long version, Instant createdAt, UUID createdBy) {

        static Response of(CaseDocument d, CaseSummary legalCase) {
            return new Response(d.id(), d.caseId(), legalCase == null ? null : legalCase.referenceNo(),
                    legalCase == null ? null : legalCase.title(), d.title(), d.fileName(), d.mediaType(),
                    d.sizeBytes(), d.sha256(), d.version(), d.createdAt(), d.createdBy());
        }
    }

    private final DocumentService service;

    DocumentController(DocumentService service) {
        this.service = service;
    }

    /**
     * The multipart body is read lazily (spring.servlet.multipart.resolve-lazily), so the permission is checked before
     * the upload is parsed (QAD-026) and an oversized upload is answered by {@link DocumentRequestErrors}.
     */
    @PostMapping("/cases/{caseId}/documents")
    @PreAuthorize(Permission.Codes.DOCUMENT_MANAGE)
    ResponseEntity<Response> attach(@PathVariable UUID caseId, HttpServletRequest request,
            @RequestPart(name = "file", required = false) MultipartFile file,
            @RequestParam(name = "title", required = false) String title) throws HttpMediaTypeNotSupportedException {
        String type = request.getContentType();
        if (type == null || !type.toLowerCase(Locale.ROOT).startsWith(MediaType.MULTIPART_FORM_DATA_VALUE)) {
            throw new HttpMediaTypeNotSupportedException("documents are uploaded as multipart/form-data");
        }
        Response created = respond(service.attach(caseId, file, title));
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/documents/{id}").buildAndExpand(created.id()).toUri()).body(created);
    }

    /** A case holds at most ten documents, so one page holds them all, newest first. */
    @GetMapping("/cases/{caseId}/documents")
    @PreAuthorize(Permission.Codes.DOCUMENT_VIEW)
    PageResponse<Response> forCase(@PathVariable UUID caseId) {
        List<CaseDocument> documents = service.forCase(caseId);
        Map<UUID, CaseSummary> cases = service.casesOf(documents);
        var items = documents.stream().map(d -> Response.of(d, cases.get(d.caseId()))).toList();
        return PageResponse.from(new PageImpl<>(items, PageRequest.of(0, DocumentService.MAX_PER_CASE), items.size()));
    }

    /** The latest documents of every case, newest first (the documents screen). */
    @GetMapping("/documents")
    @PreAuthorize(Permission.Codes.DOCUMENT_VIEW)
    PageResponse<Response> latest(Pageable pageable) {
        Page<CaseDocument> page = service.latest(pageable);
        Map<UUID, CaseSummary> cases = service.casesOf(page.getContent());
        return PageResponse.from(page.map(d -> Response.of(d, cases.get(d.caseId()))));
    }

    @GetMapping("/documents/{id}")
    @PreAuthorize(Permission.Codes.DOCUMENT_VIEW)
    Response get(@PathVariable UUID id) {
        return respond(service.get(id));
    }

    /** Always a download, never rendered by the browser: attachment, nosniff and no caching (§8.3). */
    @GetMapping("/documents/{id}/content")
    @PreAuthorize(Permission.Codes.DOCUMENT_VIEW)
    ResponseEntity<byte[]> content(@PathVariable UUID id) {
        DocumentService.Download download = service.download(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(download.content().length)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.document().fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(download.content());
    }

    private Response respond(CaseDocument document) {
        return Response.of(document, service.casesOf(List.of(document)).get(document.caseId()));
    }

    @DeleteMapping("/documents/{id}")
    @PreAuthorize(Permission.Codes.DOCUMENT_MANAGE)
    ResponseEntity<Void> remove(@PathVariable UUID id, @RequestParam(required = false) Long version) {
        service.remove(id, version);
        return ResponseEntity.noContent().build();
    }
}
