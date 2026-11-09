package com.lawdesk.pleadings;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.lawdesk.security.Permission;

/** طلبات الدعوى (requirements section 5): reading needs CLAIM_VIEW, writing CLAIM_MANAGE, plus the case rights. */
@RestController
class ClaimController {

    private final ClaimService service;

    ClaimController(ClaimService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/cases/{caseId}/claims")
    @PreAuthorize(Permission.Codes.CLAIM_VIEW)
    List<ClaimRequests.View> list(@PathVariable UUID caseId) {
        return service.list(caseId);
    }

    @PostMapping("/api/v1/cases/{caseId}/claims")
    @PreAuthorize(Permission.Codes.CLAIM_MANAGE)
    ResponseEntity<ClaimRequests.View> create(@PathVariable UUID caseId,
            @Valid @RequestBody ClaimRequests.Create request) {
        ClaimRequests.View created = service.create(caseId, request);
        return ResponseEntity.created(URI.create("/api/v1/claims/" + created.id())).body(created);
    }

    @GetMapping("/api/v1/claims/{id}")
    @PreAuthorize(Permission.Codes.CLAIM_VIEW)
    ClaimRequests.View get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PutMapping("/api/v1/claims/{id}")
    @PreAuthorize(Permission.Codes.CLAIM_MANAGE)
    ClaimRequests.View update(@PathVariable UUID id, @Valid @RequestBody ClaimRequests.Update request) {
        return service.update(id, request);
    }

    @GetMapping("/api/v1/claims/{id}/revisions")
    @PreAuthorize(Permission.Codes.CLAIM_VIEW)
    List<ClaimRequests.Revision> revisions(@PathVariable UUID id) {
        return service.revisions(id);
    }
}
