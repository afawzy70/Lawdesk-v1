package com.lawdesk.legaldocs;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.lawdesk.api.PageResponse;
import com.lawdesk.security.Permission;

/**
 * The register of memos and correspondence (requirements section 12). Reading needs DOCUMENT_VIEW, drafting and recording
 * the sending DOCUMENT_MANAGE, approving MEMO_APPROVE; the service adds the case rights to each of them.
 */
@RestController
class LegalDocumentController {

    private static final String REVIEW_OR_DRAFT = "hasAnyAuthority('DOCUMENT_MANAGE', 'MEMO_APPROVE')";

    private final LegalDocumentService service;

    LegalDocumentController(LegalDocumentService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/cases/{caseId}/legal-documents")
    @PreAuthorize(Permission.Codes.DOCUMENT_VIEW)
    List<LegalDocumentRequests.View> forCase(@PathVariable UUID caseId) {
        return service.forCase(caseId);
    }

    @GetMapping("/api/v1/legal-documents")
    @PreAuthorize(Permission.Codes.DOCUMENT_VIEW)
    PageResponse<LegalDocumentRequests.View> register(@RequestParam(required = false) String status,
            @RequestParam(required = false) UUID caseId, @RequestParam(required = false) UUID categoryId, Pageable pageable) {
        return PageResponse.from(service.register(status, caseId, categoryId, pageable));
    }

    @PostMapping("/api/v1/cases/{caseId}/legal-documents")
    @PreAuthorize(Permission.Codes.DOCUMENT_MANAGE)
    ResponseEntity<LegalDocumentRequests.View> create(@PathVariable UUID caseId,
            @RequestBody LegalDocumentRequests.Body body) {
        LegalDocumentRequests.View created = service.create(caseId, body);
        return ResponseEntity.created(URI.create("/api/v1/legal-documents/" + created.id())).body(created);
    }

    @GetMapping("/api/v1/legal-documents/{id}")
    @PreAuthorize(Permission.Codes.DOCUMENT_VIEW)
    LegalDocumentRequests.View get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping("/api/v1/legal-documents/{id}/events")
    @PreAuthorize(Permission.Codes.DOCUMENT_VIEW)
    List<LegalDocumentRequests.Event> events(@PathVariable UUID id) {
        return service.events(id);
    }

    @PutMapping("/api/v1/legal-documents/{id}")
    @PreAuthorize(Permission.Codes.DOCUMENT_MANAGE)
    LegalDocumentRequests.View update(@PathVariable UUID id, @RequestBody LegalDocumentRequests.Body body) {
        return service.update(id, body);
    }

    @PostMapping("/api/v1/legal-documents/{id}/file")
    @PreAuthorize(Permission.Codes.DOCUMENT_MANAGE)
    LegalDocumentRequests.View attachFile(@PathVariable UUID id,
            @RequestPart(name = "file", required = false) MultipartFile file,
            @RequestParam(name = "note", required = false) String note) {
        return service.attachFile(id, file, note);
    }

    @PostMapping("/api/v1/legal-documents/{id}/submit")
    @PreAuthorize(Permission.Codes.DOCUMENT_MANAGE)
    LegalDocumentRequests.View submit(@PathVariable UUID id, @RequestBody LegalDocumentRequests.Note body) {
        return service.submit(id, body);
    }

    @PostMapping("/api/v1/legal-documents/{id}/return")
    @PreAuthorize(REVIEW_OR_DRAFT)
    LegalDocumentRequests.View returnToDraft(@PathVariable UUID id, @RequestBody LegalDocumentRequests.Note body) {
        return service.returnToDraft(id, body);
    }

    @PostMapping("/api/v1/legal-documents/{id}/approve")
    @PreAuthorize(Permission.Codes.MEMO_APPROVE)
    LegalDocumentRequests.View approve(@PathVariable UUID id, @RequestBody LegalDocumentRequests.Note body) {
        return service.approve(id, body);
    }

    @PostMapping("/api/v1/legal-documents/{id}/reopen")
    @PreAuthorize(Permission.Codes.MEMO_APPROVE)
    LegalDocumentRequests.View reopen(@PathVariable UUID id, @RequestBody LegalDocumentRequests.Note body) {
        return service.reopen(id, body);
    }

    @PostMapping("/api/v1/legal-documents/{id}/record-sent")
    @PreAuthorize(Permission.Codes.DOCUMENT_MANAGE)
    LegalDocumentRequests.View recordSent(@PathVariable UUID id, @RequestBody LegalDocumentRequests.SentRecord body) {
        return service.recordSent(id, body);
    }

    @PostMapping("/api/v1/legal-documents/{id}/archive")
    @PreAuthorize(Permission.Codes.DOCUMENT_MANAGE)
    LegalDocumentRequests.View archive(@PathVariable UUID id, @RequestBody LegalDocumentRequests.Note body) {
        return service.archive(id, body);
    }
}
