package com.lawdesk.access;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lawdesk.security.Permission;

/** Case grants, scopes and confidentiality (LD-050, owner decision D3). */
@RestController
@RequestMapping("/api/v1")
class AccessController {

    private final AccessAdministration administration;

    AccessController(AccessAdministration administration) {
        this.administration = administration;
    }

    @GetMapping("/cases/{caseId}/grants")
    @PreAuthorize(Permission.Codes.CASE_GRANT_MANAGE)
    List<AccessRequests.Grant> grants(@PathVariable UUID caseId) {
        return administration.grants(caseId);
    }

    @PostMapping("/cases/{caseId}/grants")
    @PreAuthorize(Permission.Codes.CASE_GRANT_MANAGE)
    ResponseEntity<AccessRequests.Grant> grant(@PathVariable UUID caseId,
            @Valid @RequestBody AccessRequests.GrantCreate request) {
        AccessRequests.Grant created = administration.grant(caseId, request);
        return ResponseEntity.created(URI.create("/api/v1/cases/" + caseId + "/grants/" + created.id())).body(created);
    }

    @DeleteMapping("/cases/{caseId}/grants/{grantId}")
    @PreAuthorize(Permission.Codes.CASE_GRANT_MANAGE)
    ResponseEntity<Void> revoke(@PathVariable UUID caseId, @PathVariable UUID grantId) {
        administration.revoke(caseId, grantId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/cases/{caseId}/confidentiality")
    @PreAuthorize(Permission.Codes.CASE_GRANT_MANAGE)
    ResponseEntity<Void> confidentiality(@PathVariable UUID caseId,
            @Valid @RequestBody AccessRequests.Confidentiality request) {
        administration.setConfidentiality(caseId, request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/access-scopes")
    @PreAuthorize(Permission.Codes.CASE_GRANT_MANAGE)
    List<AccessRequests.Scope> scopes(@RequestParam(required = false) String subjectType,
            @RequestParam(required = false) String subjectRef) {
        return administration.scopes(subjectType, subjectRef);
    }

    @PostMapping("/access-scopes")
    @PreAuthorize(Permission.Codes.CASE_GRANT_MANAGE)
    ResponseEntity<AccessRequests.Scope> addScope(@Valid @RequestBody AccessRequests.ScopeCreate request) {
        AccessRequests.Scope created = administration.addScope(request);
        return ResponseEntity.created(URI.create("/api/v1/access-scopes/" + created.id())).body(created);
    }

    @DeleteMapping("/access-scopes/{id}")
    @PreAuthorize(Permission.Codes.CASE_GRANT_MANAGE)
    ResponseEntity<Void> removeScope(@PathVariable UUID id) {
        administration.removeScope(id);
        return ResponseEntity.noContent().build();
    }
}
