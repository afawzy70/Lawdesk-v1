package com.lawdesk.caselinks;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lawdesk.security.Permission;

/** Linked cases (LD-053, requirements section 8). */
@RestController
@RequestMapping("/api/v1")
class CaseLinkController {

    private final CaseLinkService service;

    CaseLinkController(CaseLinkService service) {
        this.service = service;
    }

    @GetMapping("/cases/{caseId}/links")
    @PreAuthorize(Permission.Codes.CASE_VIEW)
    CaseLinkRequests.Links forCase(@PathVariable UUID caseId) {
        return service.forCase(caseId);
    }

    @PostMapping("/cases/{caseId}/links")
    @PreAuthorize(Permission.Codes.CASE_LINK_MANAGE)
    ResponseEntity<CaseLinkRequests.Link> create(@PathVariable UUID caseId,
            @Valid @RequestBody CaseLinkRequests.Create request) {
        return ResponseEntity.status(201).body(service.create(caseId, request));
    }

    @PutMapping("/case-links/{id}")
    @PreAuthorize(Permission.Codes.CASE_LINK_MANAGE)
    CaseLinkRequests.Link update(@PathVariable UUID id, @Valid @RequestBody CaseLinkRequests.Update request) {
        return service.update(id, request);
    }
}
