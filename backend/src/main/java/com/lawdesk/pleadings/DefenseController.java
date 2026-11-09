package com.lawdesk.pleadings;

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

/** الدفوع وأوجه الدفاع (requirements section 17): DEFENSE_VIEW reads, DEFENSE_MANAGE writes, plus the case rights. */
@RestController
class DefenseController {

    private final DefenseService service;
    private final DefenseTemplateService templates;

    DefenseController(DefenseService service, DefenseTemplateService templates) {
        this.service = service;
        this.templates = templates;
    }

    @GetMapping("/api/v1/cases/{caseId}/defenses")
    @PreAuthorize(Permission.Codes.DEFENSE_VIEW)
    List<DefenseRequests.View> list(@PathVariable UUID caseId) {
        return service.list(caseId);
    }

    @PostMapping("/api/v1/cases/{caseId}/defenses")
    @PreAuthorize(Permission.Codes.DEFENSE_MANAGE)
    ResponseEntity<DefenseRequests.View> create(@PathVariable UUID caseId, @Valid @RequestBody DefenseRequests.Create request) {
        DefenseRequests.View created = service.create(caseId, request);
        return ResponseEntity.created(URI.create("/api/v1/defenses/" + created.id())).body(created);
    }

    @GetMapping("/api/v1/defenses/{id}")
    @PreAuthorize(Permission.Codes.DEFENSE_VIEW)
    DefenseRequests.Detail get(@PathVariable UUID id) {
        return service.detail(id);
    }

    @PutMapping("/api/v1/defenses/{id}")
    @PreAuthorize(Permission.Codes.DEFENSE_MANAGE)
    DefenseRequests.View update(@PathVariable UUID id, @Valid @RequestBody DefenseRequests.Update request) {
        return service.update(id, request);
    }

    @PutMapping("/api/v1/defenses/{id}/assertions/{proceedingId}")
    @PreAuthorize(Permission.Codes.DEFENSE_MANAGE)
    DefenseRequests.AssertionView assertIn(@PathVariable UUID id, @PathVariable UUID proceedingId,
            @Valid @RequestBody DefenseRequests.AssertionBody body) {
        return service.assertIn(id, proceedingId, body);
    }

    @GetMapping("/api/v1/defenses/{id}/precedents")
    @PreAuthorize(Permission.Codes.DEFENSE_VIEW)
    List<DefenseRequests.PrecedentView> precedents(@PathVariable UUID id) {
        return service.precedentsOf(id);
    }

    @PostMapping("/api/v1/defenses/{id}/precedents")
    @PreAuthorize(Permission.Codes.DEFENSE_MANAGE)
    ResponseEntity<DefenseRequests.PrecedentView> linkPrecedent(@PathVariable UUID id,
            @Valid @RequestBody DefenseRequests.PrecedentBody body) {
        DefenseRequests.PrecedentView created = service.linkPrecedent(id, body);
        return ResponseEntity.created(URI.create("/api/v1/defenses/" + id + "/precedents/" + created.id())).body(created);
    }

    @DeleteMapping("/api/v1/defenses/{id}/precedents/{linkId}")
    @PreAuthorize(Permission.Codes.DEFENSE_MANAGE)
    ResponseEntity<Void> unlinkPrecedent(@PathVariable UUID id, @PathVariable UUID linkId) {
        service.unlinkPrecedent(id, linkId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/cases/{caseId}/defense-assertions")
    @PreAuthorize(Permission.Codes.DEFENSE_VIEW)
    List<DefenseRequests.AssertionView> assertions(@PathVariable UUID caseId) {
        return service.assertionsOfCase(caseId);
    }

    @GetMapping("/api/v1/defense-templates")
    @PreAuthorize(Permission.Codes.DEFENSE_VIEW)
    List<DefenseRequests.TemplateView> templates(@RequestParam(required = false) String q) {
        return templates.search(q);
    }

    @PostMapping("/api/v1/defense-templates")
    @PreAuthorize(Permission.Codes.DEFENSE_MANAGE)
    ResponseEntity<DefenseRequests.TemplateView> createTemplate(@Valid @RequestBody DefenseRequests.TemplateBody body) {
        DefenseRequests.TemplateView created = templates.create(body);
        return ResponseEntity.created(URI.create("/api/v1/defense-templates/" + created.id())).body(created);
    }

    @PutMapping("/api/v1/defense-templates/{id}")
    @PreAuthorize(Permission.Codes.DEFENSE_MANAGE)
    DefenseRequests.TemplateView updateTemplate(@PathVariable UUID id, @Valid @RequestBody DefenseRequests.TemplateBody body) {
        return templates.update(id, body);
    }
}
