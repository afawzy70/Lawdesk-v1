package com.lawdesk.library;

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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lawdesk.library.LibraryViews.Precedent;
import com.lawdesk.library.LibraryViews.Suggestions;
import com.lawdesk.security.Permission;

/**
 * The library as seen from a case (LD-052, requirements section 16.4 and 16.5): the judgments linked to it and the ones the
 * system suggests. Every route checks the case through the case access policy: 404 when the user may not see the case,
 * 403 when they see it but may not change it.
 */
@RestController
@RequestMapping("/api/v1/cases/{caseId}")
class CasePrecedentController {

    private final CasePrecedentService precedents;
    private final SimilarJudgmentService similar;

    CasePrecedentController(CasePrecedentService precedents, SimilarJudgmentService similar) {
        this.precedents = precedents;
        this.similar = similar;
    }

    @GetMapping("/precedents")
    @PreAuthorize(Permission.Codes.LIBRARY_VIEW)
    List<Precedent> list(@PathVariable UUID caseId) {
        return precedents.forCase(caseId);
    }

    @PostMapping("/precedents")
    @PreAuthorize(Permission.Codes.LIBRARY_VIEW)
    ResponseEntity<Precedent> link(@PathVariable UUID caseId, @Valid @RequestBody LibraryRequests.PrecedentLink request) {
        Precedent created = precedents.link(caseId, request);
        return ResponseEntity.created(URI.create("/api/v1/cases/" + caseId + "/precedents/" + created.id())).body(created);
    }

    @DeleteMapping("/precedents/{linkId}")
    @PreAuthorize(Permission.Codes.LIBRARY_VIEW)
    ResponseEntity<Void> unlink(@PathVariable UUID caseId, @PathVariable UUID linkId) {
        precedents.unlink(caseId, linkId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/similar-judgments")
    @PreAuthorize(Permission.Codes.LIBRARY_VIEW)
    Suggestions similar(@PathVariable UUID caseId, @RequestParam(defaultValue = "10") int limit) {
        if (limit < 1) {
            throw new com.lawdesk.api.FieldErrorException("limit", "Min");
        }
        if (limit > 25) {
            throw new com.lawdesk.api.FieldErrorException("limit", "Max");
        }
        return similar.forCase(caseId, limit);
    }
}
