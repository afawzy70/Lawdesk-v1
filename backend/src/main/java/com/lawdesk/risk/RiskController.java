package com.lawdesk.risk;

import java.util.List;
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

import com.lawdesk.api.PageResponse;
import com.lawdesk.security.Permission;

/** Legal risk assessment of cases (LD-053, requirements section 19). */
@RestController
@RequestMapping("/api/v1")
class RiskController {

    private final RiskService service;

    RiskController(RiskService service) {
        this.service = service;
    }

    /** Every version of the case's assessment, newest first (a case has few; they are all returned). */
    @GetMapping("/cases/{caseId}/risk-assessments")
    @PreAuthorize(Permission.Codes.RISK_VIEW)
    PageResponse<RiskRequests.Assessment> forCase(@PathVariable UUID caseId) {
        List<RiskRequests.Assessment> items = service.forCase(caseId);
        return new PageResponse<>(items, 0, Math.max(items.size(), 1), items.size(), items.isEmpty() ? 0 : 1);
    }

    @PostMapping("/cases/{caseId}/risk-assessments")
    @PreAuthorize(Permission.Codes.RISK_MANAGE)
    ResponseEntity<RiskRequests.Assessment> assess(@PathVariable UUID caseId, @Valid @RequestBody RiskRequests.Assess request) {
        return ResponseEntity.status(201).body(service.assess(caseId, request));
    }

    /** The report of high-risk cases: the latest version of each visible case whose level is high or very high. */
    @GetMapping("/risk-assessments/high")
    @PreAuthorize(Permission.Codes.RISK_VIEW)
    RiskRequests.HighRiskReport high(Pageable pageable) {
        return service.high(pageable);
    }

    @GetMapping("/risk-assessments/{id}")
    @PreAuthorize(Permission.Codes.RISK_VIEW)
    RiskRequests.Assessment get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping("/risk-assessments/{id}/remediations")
    @PreAuthorize(Permission.Codes.RISK_MANAGE)
    ResponseEntity<RiskRequests.Remediation> addRemediation(@PathVariable UUID id,
            @Valid @RequestBody RiskRequests.RemediationInput request) {
        return ResponseEntity.status(201).body(service.addRemediation(id, request));
    }

    @PutMapping("/risk-remediations/{id}")
    @PreAuthorize(Permission.Codes.RISK_MANAGE)
    RiskRequests.Remediation updateRemediation(@PathVariable UUID id, @Valid @RequestBody RiskRequests.RemediationUpdate request) {
        return service.updateRemediation(id, request);
    }
}
