package com.lawdesk.documents;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.lawdesk.security.Permission;

/** Links from documents to the claim, defence, hearing, judgment or task they concern (LD-055, requirement 13). */
@RestController
class DocumentLinkController {

    record Create(String targetType, UUID targetId, String note) {
    }

    private final DocumentLinkService service;

    DocumentLinkController(DocumentLinkService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/documents/{id}/links")
    @PreAuthorize(Permission.Codes.DOCUMENT_VIEW)
    List<DocumentLinkService.Link> forDocument(@PathVariable UUID id) {
        return service.forDocument(id);
    }

    @GetMapping("/api/v1/cases/{caseId}/document-links")
    @PreAuthorize(Permission.Codes.DOCUMENT_VIEW)
    List<DocumentLinkService.Link> forCase(@PathVariable UUID caseId) {
        return service.forCase(caseId);
    }

    @PostMapping("/api/v1/documents/{id}/links")
    @PreAuthorize(Permission.Codes.DOCUMENT_MANAGE)
    ResponseEntity<DocumentLinkService.Link> add(@PathVariable UUID id, @RequestBody Create body) {
        DocumentLinkService.Link created = service.add(id, body.targetType(), body.targetId(), body.note());
        return ResponseEntity.created(java.net.URI.create("/api/v1/documents/" + id + "/links/" + created.id())).body(created);
    }

    @DeleteMapping("/api/v1/document-links/{id}")
    @PreAuthorize(Permission.Codes.DOCUMENT_MANAGE)
    ResponseEntity<Void> remove(@PathVariable UUID id) {
        service.remove(id);
        return ResponseEntity.noContent().build();
    }
}
