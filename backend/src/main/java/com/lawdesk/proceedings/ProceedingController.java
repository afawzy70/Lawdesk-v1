package com.lawdesk.proceedings;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.lawdesk.api.PageResponse;
import com.lawdesk.security.Permission;

/** Proceedings of a case, LD-019…LD-021 (docs/delivery/PHASE-4-ACCEPTANCE.md). */
@RestController
@RequestMapping("/api/v1")
class ProceedingController {

    private final ProceedingService service;

    ProceedingController(ProceedingService service) {
        this.service = service;
    }

    @GetMapping("/cases/{caseId}/proceedings")
    @PreAuthorize(Permission.Codes.PROCEEDING_VIEW)
    PageResponse<ProceedingRequests.Response> list(@PathVariable UUID caseId, Pageable pageable) {
        return PageResponse.from(service.list(caseId, pageable));
    }

    @PostMapping("/cases/{caseId}/proceedings")
    @PreAuthorize(Permission.Codes.PROCEEDING_MANAGE)
    ResponseEntity<ProceedingRequests.Response> create(@PathVariable UUID caseId,
            @Valid @RequestBody ProceedingRequests.Create request) {
        ProceedingRequests.Response created = service.create(caseId, request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/proceedings/{id}").buildAndExpand(created.id()).toUri()).body(created);
    }

    @GetMapping("/proceedings/{id}")
    @PreAuthorize(Permission.Codes.PROCEEDING_VIEW)
    ProceedingRequests.Response get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PutMapping("/proceedings/{id}")
    @PreAuthorize(Permission.Codes.PROCEEDING_MANAGE)
    ProceedingRequests.Response update(@PathVariable UUID id, @Valid @RequestBody ProceedingRequests.Update request) {
        return service.update(id, request);
    }
}
