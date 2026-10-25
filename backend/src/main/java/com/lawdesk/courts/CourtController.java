package com.lawdesk.courts;

import java.net.URI;
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

/** Court reference data, LD-018 (docs/delivery/PHASE-4-ACCEPTANCE.md). Courts are deactivated, never deleted. */
@RestController
@RequestMapping("/api/v1/courts")
class CourtController {

    private final CourtService service;

    CourtController(CourtService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(Permission.Codes.COURT_VIEW)
    PageResponse<CourtRequests.Response> list(@RequestParam(required = false) CourtLevel level,
            @RequestParam(required = false) Boolean active, @RequestParam(required = false) String q,
            Pageable pageable) {
        return PageResponse.from(service.list(level, active, q, pageable).map(CourtRequests.Response::of));
    }

    @GetMapping("/{id}")
    @PreAuthorize(Permission.Codes.COURT_VIEW)
    CourtRequests.Response get(@PathVariable UUID id) {
        return CourtRequests.Response.of(service.get(id));
    }

    @PostMapping
    @PreAuthorize(Permission.Codes.COURT_MANAGE)
    ResponseEntity<CourtRequests.Response> create(@Valid @RequestBody CourtRequests.Create request) {
        Court court = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(court.getId())
                .toUri();
        return ResponseEntity.created(location).body(CourtRequests.Response.of(court));
    }

    @PutMapping("/{id}")
    @PreAuthorize(Permission.Codes.COURT_MANAGE)
    CourtRequests.Response update(@PathVariable UUID id, @Valid @RequestBody CourtRequests.Update request) {
        return CourtRequests.Response.of(service.update(id, request));
    }
}
