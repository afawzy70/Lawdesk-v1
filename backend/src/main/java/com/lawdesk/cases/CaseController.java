package com.lawdesk.cases;

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
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.lawdesk.api.PageResponse;
import com.lawdesk.security.Permission;

/** {@code /api/v1/cases} (docs/delivery/PHASE-3-ACCEPTANCE.md, LD-012, LD-013, LD-015). */
@RestController
@RequestMapping("/api/v1/cases")
class CaseController {

    private final CaseService service;

    CaseController(CaseService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize(Permission.Codes.CASE_CREATE)
    ResponseEntity<LegalCase> create(@Valid @RequestBody CreateCaseRequest request) {
        LegalCase created = service.create(request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequestUri().path("/{id}")
                .buildAndExpand(created.id()).toUri()).body(created);
    }

    @GetMapping
    @PreAuthorize(Permission.Codes.CASE_VIEW)
    PageResponse<CaseListItem> search(@Valid @ModelAttribute CaseSearch search) {
        return service.search(search);
    }

    @GetMapping("/{id}")
    @PreAuthorize(Permission.Codes.CASE_VIEW)
    LegalCase get(@PathVariable String id) {
        return service.get(Text.id(id, "case"));
    }

    @PutMapping("/{id}")
    @PreAuthorize(Permission.Codes.CASE_EDIT)
    LegalCase update(@PathVariable String id, @Valid @RequestBody UpdateCaseRequest request) {
        return service.update(Text.id(id, "case"), request);
    }
}
