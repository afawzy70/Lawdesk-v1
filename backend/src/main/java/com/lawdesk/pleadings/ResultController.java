package com.lawdesk.pleadings;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.lawdesk.security.Permission;

/** Results of judgments on claims and defences: reading needs JUDGMENT_VIEW, recording JUDGMENT_MANAGE. */
@RestController
class ResultController {

    private final ResultService service;

    ResultController(ResultService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/judgments/{id}/claim-results")
    @PreAuthorize(Permission.Codes.JUDGMENT_VIEW)
    List<ResultRequests.ClaimResultView> claimResults(@PathVariable UUID id) {
        return service.claimResults(id);
    }

    @PutMapping("/api/v1/judgments/{id}/claim-results")
    @PreAuthorize(Permission.Codes.JUDGMENT_MANAGE)
    List<ResultRequests.ClaimResultView> recordClaimResults(@PathVariable UUID id,
            @Valid @RequestBody ResultRequests.ClaimResults request) {
        return service.recordClaimResults(id, request);
    }

    @GetMapping("/api/v1/cases/{caseId}/claim-results")
    @PreAuthorize(Permission.Codes.JUDGMENT_VIEW)
    List<ResultRequests.CaseClaimResult> claimResultsOfCase(@PathVariable UUID caseId) {
        return service.claimResultsOfCase(caseId);
    }

    @GetMapping("/api/v1/judgments/{id}/defense-positions")
    @PreAuthorize(Permission.Codes.JUDGMENT_VIEW)
    List<ResultRequests.DefensePositionView> defensePositions(@PathVariable UUID id) {
        return service.defensePositions(id);
    }

    @PutMapping("/api/v1/judgments/{id}/defense-positions")
    @PreAuthorize(Permission.Codes.JUDGMENT_MANAGE)
    List<ResultRequests.DefensePositionView> recordDefensePositions(@PathVariable UUID id,
            @Valid @RequestBody ResultRequests.DefensePositions request) {
        return service.recordDefensePositions(id, request);
    }

    @GetMapping("/api/v1/cases/{caseId}/defense-positions")
    @PreAuthorize(Permission.Codes.JUDGMENT_VIEW)
    List<ResultRequests.CaseDefensePosition> defensePositionsOfCase(@PathVariable UUID caseId) {
        return service.defensePositionsOfCase(caseId);
    }
}
