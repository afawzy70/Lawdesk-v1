package com.lawdesk.directives;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lawdesk.api.PageResponse;
import com.lawdesk.directives.DirectiveRequests.Directive;
import com.lawdesk.security.Permission;

/** The register of administrative decisions and assignments, LD-057 (requirements section 25). */
@RestController
@RequestMapping("/api/v1/directives")
class DirectiveController {

    private final DirectiveService service;

    DirectiveController(DirectiveService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(Permission.Codes.CASE_VIEW)
    PageResponse<Directive> search(@Valid @ModelAttribute DirectiveRequests.Search search) {
        return service.search(search);
    }

    @PostMapping
    @PreAuthorize(Permission.Codes.DIRECTIVE_MANAGE)
    ResponseEntity<Directive> create(@Valid @RequestBody DirectiveRequests.Create request) {
        return ResponseEntity.status(201).body(service.create(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize(Permission.Codes.CASE_VIEW)
    Directive get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Permission.Codes.DIRECTIVE_MANAGE)
    Directive update(@PathVariable UUID id, @Valid @RequestBody DirectiveRequests.Update request) {
        return service.update(id, request);
    }

    /** The addressee moves the status of their own directive; an editor of the case moves any (checked by the service). */
    @PostMapping("/{id}/status")
    @PreAuthorize(Permission.Codes.CASE_VIEW)
    Directive changeStatus(@PathVariable UUID id, @Valid @RequestBody DirectiveRequests.StatusChange request) {
        return service.changeStatus(id, request);
    }
}
