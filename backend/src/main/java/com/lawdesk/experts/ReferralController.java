package com.lawdesk.experts;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
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

/** Expert referrals of proceedings and their sessions, LD-025 (docs/delivery/PHASE-5-ACCEPTANCE.md). */
@RestController
@RequestMapping("/api/v1")
class ReferralController {

    private final ReferralService service;

    ReferralController(ReferralService service) {
        this.service = service;
    }

    @PostMapping("/proceedings/{proceedingId}/expert-referrals")
    @PreAuthorize(Permission.Codes.EXPERT_MANAGE)
    ResponseEntity<ExpertRequests.Referral> refer(@PathVariable UUID proceedingId,
            @Valid @RequestBody ExpertRequests.ReferralCreate request) {
        ExpertRequests.Referral created = service.refer(proceedingId, request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/expert-referrals/{id}").buildAndExpand(created.id()).toUri()).body(created);
    }

    /** A case holds a handful of referrals; one page holds them all, in referral order, with their sessions. */
    @GetMapping("/cases/{caseId}/expert-referrals")
    @PreAuthorize(Permission.Codes.EXPERT_VIEW)
    PageResponse<ExpertRequests.Referral> forCase(@PathVariable UUID caseId) {
        var items = service.forCase(caseId);
        return PageResponse.from(new PageImpl<>(items, PageRequest.of(0, Math.max(1, items.size())), items.size()));
    }

    @GetMapping("/expert-referrals/{id}")
    @PreAuthorize(Permission.Codes.EXPERT_VIEW)
    ExpertRequests.Referral get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PutMapping("/expert-referrals/{id}")
    @PreAuthorize(Permission.Codes.EXPERT_MANAGE)
    ExpertRequests.Referral update(@PathVariable UUID id, @Valid @RequestBody ExpertRequests.ReferralUpdate request) {
        return service.update(id, request);
    }

    @PostMapping("/expert-referrals/{id}/sessions")
    @PreAuthorize(Permission.Codes.EXPERT_MANAGE)
    ResponseEntity<ExpertRequests.Referral> addSession(@PathVariable UUID id,
            @Valid @RequestBody ExpertRequests.SessionCreate request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addSession(id, request));
    }

    @PostMapping("/expert-referrals/{id}/report")
    @PreAuthorize(Permission.Codes.EXPERT_MANAGE)
    ExpertRequests.Referral submitReport(@PathVariable UUID id,
            @Valid @RequestBody ExpertRequests.ReportSubmission request) {
        return service.submitReport(id, request);
    }

    @PostMapping("/expert-referrals/{id}/cancel")
    @PreAuthorize(Permission.Codes.EXPERT_MANAGE)
    ExpertRequests.Referral cancel(@PathVariable UUID id, @Valid @RequestBody ExpertRequests.Cancellation request) {
        return service.cancel(id, request);
    }
}
