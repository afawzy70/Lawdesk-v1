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

/** Judgments and outcomes, LD-022 (docs/delivery/PHASE-4-ACCEPTANCE.md). */
@RestController
@RequestMapping("/api/v1")
class JudgmentController {

    private final JudgmentService service;

    JudgmentController(JudgmentService service) {
        this.service = service;
    }

    @GetMapping("/cases/{caseId}/judgments")
    @PreAuthorize(Permission.Codes.JUDGMENT_VIEW)
    PageResponse<JudgmentRequests.Response> list(@PathVariable UUID caseId, Pageable pageable) {
        return PageResponse.from(service.list(caseId, pageable));
    }

    @PostMapping("/proceedings/{proceedingId}/judgment")
    @PreAuthorize(Permission.Codes.JUDGMENT_MANAGE)
    ResponseEntity<JudgmentRequests.Response> create(@PathVariable UUID proceedingId,
            @Valid @RequestBody JudgmentRequests.Create request) {
        JudgmentRequests.Response created = service.create(proceedingId, request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/judgments/{id}").buildAndExpand(created.id()).toUri()).body(created);
    }

    @GetMapping("/judgments/{id}")
    @PreAuthorize(Permission.Codes.JUDGMENT_VIEW)
    JudgmentRequests.Response get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PutMapping("/judgments/{id}")
    @PreAuthorize(Permission.Codes.JUDGMENT_MANAGE)
    JudgmentRequests.Response update(@PathVariable UUID id, @Valid @RequestBody JudgmentRequests.Update request) {
        return service.update(id, request);
    }
}
