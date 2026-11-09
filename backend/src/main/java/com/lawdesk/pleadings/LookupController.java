package com.lawdesk.pleadings;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lawdesk.security.Permission;

/** Managed lists (D10): anyone who can see cases reads them; LOOKUP_MANAGE adds, edits, approves and deactivates. */
@RestController
class LookupController {

    private final LookupService service;

    LookupController(LookupService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/lookups/{list}")
    @PreAuthorize(Permission.Codes.CASE_VIEW)
    List<LookupRequests.View> list(@PathVariable String list, @RequestParam(defaultValue = "false") boolean includeInactive) {
        return service.list(list, includeInactive);
    }

    @PostMapping("/api/v1/lookups/{list}")
    @PreAuthorize(Permission.Codes.LOOKUP_MANAGE)
    ResponseEntity<LookupRequests.View> create(@PathVariable String list, @Valid @RequestBody LookupRequests.Create request) {
        LookupRequests.View created = service.create(list, request);
        return ResponseEntity.created(java.net.URI.create("/api/v1/lookups/values/" + created.id())).body(created);
    }

    @PutMapping("/api/v1/lookups/values/{id}")
    @PreAuthorize(Permission.Codes.LOOKUP_MANAGE)
    LookupRequests.View update(@PathVariable UUID id, @Valid @RequestBody LookupRequests.Update request) {
        return service.update(id, request);
    }

    @PostMapping("/api/v1/lookups/values/{id}/approve")
    @PreAuthorize(Permission.Codes.LOOKUP_MANAGE)
    LookupRequests.View approve(@PathVariable UUID id) {
        return service.approve(id);
    }

    @GetMapping("/api/v1/lookups/values/{id}/history")
    @PreAuthorize(Permission.Codes.CASE_VIEW)
    List<LookupRequests.History> history(@PathVariable UUID id) {
        return service.history(id);
    }
}
