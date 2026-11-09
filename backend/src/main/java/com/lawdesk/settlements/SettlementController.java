package com.lawdesk.settlements;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lawdesk.security.Permission;
import com.lawdesk.settlements.SettlementRequests.HistoryEntry;
import com.lawdesk.settlements.SettlementRequests.Settlement;

/** Settlements, waivers and conciliations of a case, LD-057 (requirements section 23). */
@RestController
@RequestMapping("/api/v1")
class SettlementController {

    private final SettlementService service;

    SettlementController(SettlementService service) {
        this.service = service;
    }

    @GetMapping("/cases/{caseId}/settlements")
    @PreAuthorize(Permission.Codes.CASE_VIEW)
    List<Settlement> forCase(@PathVariable UUID caseId) {
        return service.forCase(caseId);
    }

    @PostMapping("/cases/{caseId}/settlements")
    @PreAuthorize(Permission.Codes.SETTLEMENT_MANAGE)
    ResponseEntity<Settlement> create(@PathVariable UUID caseId, @Valid @RequestBody SettlementRequests.Create request) {
        return ResponseEntity.status(201).body(service.create(caseId, request));
    }

    @GetMapping("/settlements/{id}")
    @PreAuthorize(Permission.Codes.CASE_VIEW)
    Settlement get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PutMapping("/settlements/{id}")
    @PreAuthorize(Permission.Codes.SETTLEMENT_MANAGE)
    Settlement update(@PathVariable UUID id, @Valid @RequestBody SettlementRequests.Update request) {
        return service.update(id, request);
    }

    /** Approving or rejecting needs SETTLEMENT_APPROVE, every other move SETTLEMENT_MANAGE (checked by the service). */
    @PostMapping("/settlements/{id}/status")
    @PreAuthorize("hasAnyAuthority('SETTLEMENT_MANAGE', 'SETTLEMENT_APPROVE')")
    Settlement changeStatus(@PathVariable UUID id, @Valid @RequestBody SettlementRequests.StatusChange request) {
        return service.changeStatus(id, request);
    }

    @GetMapping("/settlements/{id}/history")
    @PreAuthorize(Permission.Codes.CASE_VIEW)
    List<HistoryEntry> history(@PathVariable UUID id) {
        return service.history(id);
    }

    @PutMapping("/settlements/{id}/documents/{documentId}")
    @PreAuthorize(Permission.Codes.SETTLEMENT_MANAGE)
    Settlement linkDocument(@PathVariable UUID id, @PathVariable UUID documentId) {
        return service.linkDocument(id, documentId);
    }

    @DeleteMapping("/settlements/{id}/documents/{documentId}")
    @PreAuthorize(Permission.Codes.SETTLEMENT_MANAGE)
    Settlement unlinkDocument(@PathVariable UUID id, @PathVariable UUID documentId) {
        return service.unlinkDocument(id, documentId);
    }
}
