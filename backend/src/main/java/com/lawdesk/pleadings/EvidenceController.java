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
import org.springframework.web.bind.annotation.RestController;

import com.lawdesk.security.Permission;

/** الوقائع والأدلة (requirement section 20): EVIDENCE_VIEW reads, EVIDENCE_MANAGE writes, plus the case rights. */
@RestController
class EvidenceController {

    private final EvidenceService service;

    EvidenceController(EvidenceService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/cases/{caseId}/facts")
    @PreAuthorize(Permission.Codes.EVIDENCE_VIEW)
    List<EvidenceRequests.FactView> facts(@PathVariable UUID caseId) {
        return service.facts(caseId);
    }

    @PostMapping("/api/v1/cases/{caseId}/facts")
    @PreAuthorize(Permission.Codes.EVIDENCE_MANAGE)
    ResponseEntity<EvidenceRequests.FactView> addFact(@PathVariable UUID caseId, @Valid @RequestBody EvidenceRequests.FactBody body) {
        EvidenceRequests.FactView created = service.addFact(caseId, body);
        return ResponseEntity.created(URI.create("/api/v1/facts/" + created.id())).body(created);
    }

    @PutMapping("/api/v1/facts/{id}")
    @PreAuthorize(Permission.Codes.EVIDENCE_MANAGE)
    EvidenceRequests.FactView updateFact(@PathVariable UUID id, @Valid @RequestBody EvidenceRequests.FactBody body) {
        return service.updateFact(id, body);
    }

    @GetMapping("/api/v1/cases/{caseId}/evidence")
    @PreAuthorize(Permission.Codes.EVIDENCE_VIEW)
    List<EvidenceRequests.ItemView> items(@PathVariable UUID caseId) {
        return service.items(caseId);
    }

    @GetMapping("/api/v1/cases/{caseId}/evidence/gaps")
    @PreAuthorize(Permission.Codes.EVIDENCE_VIEW)
    List<EvidenceRequests.ItemView> gaps(@PathVariable UUID caseId) {
        return service.gaps(caseId);
    }

    @PostMapping("/api/v1/cases/{caseId}/evidence")
    @PreAuthorize(Permission.Codes.EVIDENCE_MANAGE)
    ResponseEntity<EvidenceRequests.ItemView> add(@PathVariable UUID caseId, @Valid @RequestBody EvidenceRequests.ItemBody body) {
        EvidenceRequests.ItemView created = service.add(caseId, body);
        return ResponseEntity.created(URI.create("/api/v1/evidence/" + created.id())).body(created);
    }

    @PutMapping("/api/v1/evidence/{id}")
    @PreAuthorize(Permission.Codes.EVIDENCE_MANAGE)
    EvidenceRequests.ItemView update(@PathVariable UUID id, @Valid @RequestBody EvidenceRequests.ItemBody body) {
        return service.update(id, body);
    }

    @PostMapping("/api/v1/evidence/{id}/links")
    @PreAuthorize(Permission.Codes.EVIDENCE_MANAGE)
    ResponseEntity<EvidenceRequests.LinkView> link(@PathVariable UUID id, @Valid @RequestBody EvidenceRequests.LinkBody body) {
        EvidenceRequests.LinkView created = service.link(id, body);
        return ResponseEntity.created(URI.create("/api/v1/evidence/" + id + "/links/" + created.id())).body(created);
    }

    @DeleteMapping("/api/v1/evidence/{id}/links/{linkId}")
    @PreAuthorize(Permission.Codes.EVIDENCE_MANAGE)
    ResponseEntity<Void> unlink(@PathVariable UUID id, @PathVariable UUID linkId) {
        service.unlink(id, linkId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/evidence/{id}/followups")
    @PreAuthorize(Permission.Codes.EVIDENCE_MANAGE)
    ResponseEntity<EvidenceRequests.FollowupView> follow(@PathVariable UUID id, @Valid @RequestBody EvidenceRequests.FollowupBody body) {
        EvidenceRequests.FollowupView created = service.follow(id, body);
        return ResponseEntity.created(URI.create("/api/v1/evidence/" + id + "/followups/" + created.id())).body(created);
    }

    @DeleteMapping("/api/v1/evidence/{id}/followups/{followupId}")
    @PreAuthorize(Permission.Codes.EVIDENCE_MANAGE)
    ResponseEntity<Void> unfollow(@PathVariable UUID id, @PathVariable UUID followupId) {
        service.unfollow(id, followupId);
        return ResponseEntity.noContent().build();
    }

    /** Where each stored document of the case is used (for the documents list of the case file). */
    @GetMapping("/api/v1/cases/{caseId}/document-usage")
    @PreAuthorize(Permission.Codes.DOCUMENT_VIEW)
    List<EvidenceRequests.DocumentUse> documentUsage(@PathVariable UUID caseId) {
        return service.documentUsage(caseId);
    }
}
