package com.lawdesk.experts;

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

/** The expert registry, LD-025 (docs/delivery/PHASE-5-ACCEPTANCE.md). */
@RestController
@RequestMapping("/api/v1/experts")
class ExpertController {

    private final ExpertRegistry registry;

    ExpertController(ExpertRegistry registry) {
        this.registry = registry;
    }

    /** By name; {@code q} searches name and specialty, {@code active} filters. */
    @GetMapping
    @PreAuthorize(Permission.Codes.EXPERT_VIEW)
    PageResponse<ExpertRequests.Expert> list(@RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String q, Pageable pageable) {
        return PageResponse.from(registry.list(active, q, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize(Permission.Codes.EXPERT_VIEW)
    ExpertRequests.Expert get(@PathVariable UUID id) {
        return registry.get(id);
    }

    @PostMapping
    @PreAuthorize(Permission.Codes.EXPERT_MANAGE)
    ResponseEntity<ExpertRequests.Expert> create(@Valid @RequestBody ExpertRequests.ExpertInput input) {
        ExpertRequests.Expert created = registry.create(input);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/experts/{id}").buildAndExpand(created.id()).toUri()).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Permission.Codes.EXPERT_MANAGE)
    ExpertRequests.Expert update(@PathVariable UUID id, @Valid @RequestBody ExpertRequests.ExpertUpdate input) {
        return registry.update(id, input);
    }
}
