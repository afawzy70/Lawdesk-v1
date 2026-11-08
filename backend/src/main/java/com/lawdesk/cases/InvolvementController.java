package com.lawdesk.cases;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lawdesk.security.Permission;

/** The administrative units a case concerns: reading needs CASE_VIEW, changing needs CASE_EDIT (and the case rights). */
@RestController
class InvolvementController {

    private final InvolvementService service;

    InvolvementController(InvolvementService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/cases/{id}/involvements")
    @PreAuthorize(Permission.Codes.CASE_VIEW)
    List<InvolvementRequests.View> list(@PathVariable String id,
            @RequestParam(defaultValue = "false") boolean includeRemoved) {
        return service.list(Text.id(id, "case"), includeRemoved);
    }

    @PostMapping("/api/v1/cases/{id}/involvements")
    @PreAuthorize(Permission.Codes.CASE_EDIT)
    ResponseEntity<InvolvementRequests.View> add(@PathVariable String id,
            @Valid @RequestBody InvolvementRequests.Create request) {
        UUID caseId = Text.id(id, "case");
        InvolvementRequests.View created = service.add(caseId, request);
        return ResponseEntity.created(URI.create("/api/v1/cases/" + caseId + "/involvements/" + created.id()))
                .body(created);
    }

    @PutMapping("/api/v1/cases/{id}/involvements/{involvementId}")
    @PreAuthorize(Permission.Codes.CASE_EDIT)
    InvolvementRequests.View update(@PathVariable String id, @PathVariable UUID involvementId,
            @Valid @RequestBody InvolvementRequests.Update request) {
        return service.update(Text.id(id, "case"), involvementId, request);
    }

    @DeleteMapping("/api/v1/cases/{id}/involvements/{involvementId}")
    @PreAuthorize(Permission.Codes.CASE_EDIT)
    ResponseEntity<Void> remove(@PathVariable String id, @PathVariable UUID involvementId) {
        service.remove(Text.id(id, "case"), involvementId);
        return ResponseEntity.noContent().build();
    }
}
