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

/** Special decisions and enforcement suspension, LD-023 (docs/delivery/PHASE-4-ACCEPTANCE.md). */
@RestController
@RequestMapping("/api/v1")
class SpecialDecisionController {

    private final SpecialDecisionService service;

    SpecialDecisionController(SpecialDecisionService service) {
        this.service = service;
    }

    @GetMapping("/cases/{caseId}/special-decisions")
    @PreAuthorize(Permission.Codes.JUDGMENT_VIEW)
    PageResponse<SpecialDecisionRequests.Response> list(@PathVariable UUID caseId, Pageable pageable) {
        return PageResponse.from(service.list(caseId, pageable));
    }

    @PostMapping("/proceedings/{proceedingId}/special-decisions")
    @PreAuthorize(Permission.Codes.JUDGMENT_MANAGE)
    ResponseEntity<SpecialDecisionRequests.Response> create(@PathVariable UUID proceedingId,
            @Valid @RequestBody SpecialDecisionRequests.Create request) {
        SpecialDecisionRequests.Response created = service.create(proceedingId, request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/special-decisions/{id}").buildAndExpand(created.id()).toUri()).body(created);
    }

    @GetMapping("/special-decisions/{id}")
    @PreAuthorize(Permission.Codes.JUDGMENT_VIEW)
    SpecialDecisionRequests.Response get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PutMapping("/special-decisions/{id}")
    @PreAuthorize(Permission.Codes.JUDGMENT_MANAGE)
    SpecialDecisionRequests.Response update(@PathVariable UUID id,
            @Valid @RequestBody SpecialDecisionRequests.Update request) {
        return service.update(id, request);
    }
}
