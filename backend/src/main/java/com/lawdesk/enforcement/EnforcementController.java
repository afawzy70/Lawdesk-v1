package com.lawdesk.enforcement;

import java.net.URI;
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
import org.springframework.web.bind.annotation.RestController;

import com.lawdesk.api.PageResponse;
import com.lawdesk.security.Permission;

/** Judgment enforcement (requirements section 11): reading needs ENFORCEMENT_VIEW, writing ENFORCEMENT_MANAGE, plus the case rights. */
@RestController
class EnforcementController {

    private final EnforcementService service;

    EnforcementController(EnforcementService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/judgments/{judgmentId}/enforcement")
    @PreAuthorize(Permission.Codes.ENFORCEMENT_VIEW)
    EnforcementRequests.Overview overview(@PathVariable UUID judgmentId) {
        return service.overview(judgmentId);
    }

    @PostMapping("/api/v1/judgments/{judgmentId}/enforcement")
    @PreAuthorize(Permission.Codes.ENFORCEMENT_MANAGE)
    ResponseEntity<EnforcementRequests.Overview> create(@PathVariable UUID judgmentId,
            @RequestBody EnforcementRequests.FileBody body) {
        return ResponseEntity.created(URI.create("/api/v1/judgments/" + judgmentId + "/enforcement"))
                .body(service.create(judgmentId, body));
    }

    @PutMapping("/api/v1/enforcement/{fileId}")
    @PreAuthorize(Permission.Codes.ENFORCEMENT_MANAGE)
    EnforcementRequests.Overview update(@PathVariable UUID fileId, @RequestBody EnforcementRequests.FileBody body) {
        return service.update(fileId, body);
    }

    @PostMapping("/api/v1/enforcement/{fileId}/actions")
    @PreAuthorize(Permission.Codes.ENFORCEMENT_MANAGE)
    ResponseEntity<EnforcementRequests.ActionView> addAction(@PathVariable UUID fileId,
            @RequestBody EnforcementRequests.ActionBody body) {
        EnforcementRequests.ActionView created = service.addAction(fileId, body);
        return ResponseEntity.created(URI.create("/api/v1/enforcement-actions/" + created.id())).body(created);
    }

    @PutMapping("/api/v1/enforcement-actions/{id}")
    @PreAuthorize(Permission.Codes.ENFORCEMENT_MANAGE)
    EnforcementRequests.ActionView updateAction(@PathVariable UUID id, @RequestBody EnforcementRequests.ActionBody body) {
        return service.updateAction(id, body);
    }

    /** Judgments not executed ({@code scope=UNEXECUTED}, the default), late ({@code LATE}) or all ({@code ALL}). */
    @GetMapping("/api/v1/enforcement/judgments")
    @PreAuthorize(Permission.Codes.ENFORCEMENT_VIEW)
    PageResponse<EnforcementRequests.ListItem> list(@RequestParam(required = false) String scope,
            @RequestParam(required = false) UUID caseId, Pageable pageable) {
        return PageResponse.from(service.list(scope, caseId, pageable));
    }
}
