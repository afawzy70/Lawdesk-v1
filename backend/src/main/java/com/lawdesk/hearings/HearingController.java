package com.lawdesk.hearings;

import java.time.LocalDate;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.lawdesk.api.PageResponse;
import com.lawdesk.security.Permission;

/** Hearings of proceedings and the hearing calendar, LD-024 (docs/delivery/PHASE-5-ACCEPTANCE.md). */
@RestController
@RequestMapping("/api/v1")
class HearingController {

    private final HearingService service;

    HearingController(HearingService service) {
        this.service = service;
    }

    @PostMapping("/proceedings/{proceedingId}/hearings")
    @PreAuthorize(Permission.Codes.HEARING_MANAGE)
    ResponseEntity<HearingRequests.Response> create(@PathVariable UUID proceedingId,
            @Valid @RequestBody HearingRequests.Create request) {
        HearingRequests.Response created = service.create(proceedingId, request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/hearings/{id}").buildAndExpand(created.id()).toUri()).body(created);
    }

    @GetMapping("/hearings")
    @PreAuthorize(Permission.Codes.HEARING_VIEW)
    PageResponse<HearingRequests.Response> calendar(@RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to, @RequestParam(required = false) HearingStatus status,
            Pageable pageable) {
        return PageResponse.from(service.calendar(from, to, status, pageable));
    }

    @GetMapping("/cases/{caseId}/hearings")
    @PreAuthorize(Permission.Codes.HEARING_VIEW)
    PageResponse<HearingRequests.Response> forCase(@PathVariable UUID caseId, Pageable pageable) {
        return PageResponse.from(service.forCase(caseId, pageable));
    }

    @GetMapping("/hearings/{id}")
    @PreAuthorize(Permission.Codes.HEARING_VIEW)
    HearingRequests.Response get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PutMapping("/hearings/{id}")
    @PreAuthorize(Permission.Codes.HEARING_MANAGE)
    HearingRequests.Response update(@PathVariable UUID id, @Valid @RequestBody HearingRequests.Update request) {
        return service.update(id, request);
    }

    @PostMapping("/hearings/{id}/result")
    @PreAuthorize(Permission.Codes.HEARING_MANAGE)
    HearingRequests.Response result(@PathVariable UUID id, @Valid @RequestBody HearingRequests.Result request) {
        return service.recordResult(id, request);
    }
}
