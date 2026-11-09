package com.lawdesk.library;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lawdesk.api.PageResponse;
import com.lawdesk.library.LibraryViews.Classification;
import com.lawdesk.library.LibraryViews.Judgment;
import com.lawdesk.library.LibraryViews.ListItem;
import com.lawdesk.library.LibraryViews.Principle;
import com.lawdesk.library.LibraryViews.Relations;
import com.lawdesk.library.LibraryViews.Summary;
import com.lawdesk.library.LibraryViews.Validity;
import com.lawdesk.security.Permission;

/**
 * The judgments and precedents library, LD-052 (requirements section 16). Reading needs LIBRARY_VIEW, adding and
 * changing needs LIBRARY_MANAGE. The library is department-wide reference data; it holds no case data.
 */
@RestController
@RequestMapping("/api/v1/library")
class LibraryController {

    private final LibraryJudgmentService judgments;
    private final LibraryPrincipleService principles;
    private final LibraryRelationService relations;
    private final ClassificationService classifications;
    private final LibraryReportService reports;

    LibraryController(LibraryJudgmentService judgments, LibraryPrincipleService principles,
            LibraryRelationService relations, ClassificationService classifications, LibraryReportService reports) {
        this.judgments = judgments;
        this.principles = principles;
        this.relations = relations;
        this.classifications = classifications;
        this.reports = reports;
    }

    // ---- judgments -------------------------------------------------------------------------------------------------

    @GetMapping("/judgments")
    @PreAuthorize(Permission.Codes.LIBRARY_VIEW)
    PageResponse<ListItem> search(@RequestParam(required = false) String q,
            @RequestParam(required = false) String courtName, @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String caseType,
            @RequestParam(required = false) String courtLevel,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String finality,
            @RequestParam(required = false) List<UUID> classificationId,
            @RequestParam(defaultValue = "false") boolean includeWithdrawn, Pageable pageable) {
        LibraryText.requireCode("caseType", caseType, LibraryRequests.CASE_TYPE);
        LibraryText.requireCode("courtLevel", courtLevel, LibraryRequests.COURT_LEVEL);
        LibraryText.requireCode("direction", direction, LibraryRequests.DIRECTION);
        LibraryText.requireCode("finality", finality, LibraryRequests.FINALITY);
        var filters = new LibraryJudgmentService.Filters(LibraryText.trimToNull(q), LibraryText.trimToNull(courtName),
                year, caseType, courtLevel, direction, finality, classificationId == null ? List.of() : classificationId,
                includeWithdrawn);
        return PageResponse.from(judgments.search(filters, pageable));
    }

    @PostMapping("/judgments")
    @PreAuthorize(Permission.Codes.LIBRARY_MANAGE)
    ResponseEntity<Judgment> register(@Valid @RequestBody LibraryRequests.JudgmentWrite request) {
        Judgment created = judgments.create(request);
        return ResponseEntity.created(URI.create("/api/v1/library/judgments/" + created.id())).body(created);
    }

    @GetMapping("/judgments/{id}")
    @PreAuthorize(Permission.Codes.LIBRARY_VIEW)
    Judgment get(@PathVariable UUID id) {
        return judgments.get(id);
    }

    @PutMapping("/judgments/{id}")
    @PreAuthorize(Permission.Codes.LIBRARY_MANAGE)
    Judgment update(@PathVariable UUID id, @Valid @RequestBody LibraryRequests.JudgmentWrite request) {
        return judgments.update(id, request);
    }

    @PostMapping("/judgments/{id}/withdraw")
    @PreAuthorize(Permission.Codes.LIBRARY_MANAGE)
    Judgment withdraw(@PathVariable UUID id, @Valid @RequestBody LibraryRequests.Withdraw request) {
        return judgments.withdraw(id, request.reason(), request.version());
    }

    @PostMapping("/judgments/{id}/reinstate")
    @PreAuthorize(Permission.Codes.LIBRARY_MANAGE)
    Judgment reinstate(@PathVariable UUID id, @Valid @RequestBody LibraryRequests.Versioned request) {
        return judgments.reinstate(id, request.version());
    }

    // ---- principles ------------------------------------------------------------------------------------------------

    @GetMapping("/judgments/{id}/principles")
    @PreAuthorize(Permission.Codes.LIBRARY_VIEW)
    List<Principle> principles(@PathVariable UUID id) {
        return principles.forJudgment(id);
    }

    @PostMapping("/judgments/{id}/principles")
    @PreAuthorize(Permission.Codes.LIBRARY_MANAGE)
    ResponseEntity<Principle> addPrinciple(@PathVariable UUID id, @Valid @RequestBody LibraryRequests.PrincipleWrite request) {
        Principle created = principles.create(id, request);
        return ResponseEntity.created(URI.create("/api/v1/library/principles/" + created.id())).body(created);
    }

    @PutMapping("/principles/{id}")
    @PreAuthorize(Permission.Codes.LIBRARY_MANAGE)
    Principle updatePrinciple(@PathVariable UUID id, @Valid @RequestBody LibraryRequests.PrincipleWrite request) {
        return principles.update(id, request);
    }

    @PostMapping("/principles/{id}/review")
    @PreAuthorize(Permission.Codes.LIBRARY_MANAGE)
    Principle reviewPrinciple(@PathVariable UUID id, @Valid @RequestBody LibraryRequests.Versioned request) {
        return principles.review(id, request.version());
    }

    // ---- relations and validity ------------------------------------------------------------------------------------

    @GetMapping("/judgments/{id}/validity")
    @PreAuthorize(Permission.Codes.LIBRARY_VIEW)
    Validity validity(@PathVariable UUID id) {
        return relations.validity(id);
    }

    @PostMapping("/judgments/{id}/relations")
    @PreAuthorize(Permission.Codes.LIBRARY_MANAGE)
    ResponseEntity<Relations> addRelation(@PathVariable UUID id, @Valid @RequestBody LibraryRequests.RelationWrite request) {
        return ResponseEntity.created(URI.create("/api/v1/library/judgments/" + id + "/validity"))
                .body(relations.add(id, request));
    }

    @DeleteMapping("/relations/{id}")
    @PreAuthorize(Permission.Codes.LIBRARY_MANAGE)
    ResponseEntity<Void> removeRelation(@PathVariable UUID id) {
        relations.remove(id);
        return ResponseEntity.noContent().build();
    }

    // ---- classification lists --------------------------------------------------------------------------------------

    @GetMapping("/classifications")
    @PreAuthorize(Permission.Codes.LIBRARY_VIEW)
    List<Classification> classifications(
            @RequestParam(required = false) String dimension,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        LibraryText.requireCode("dimension", dimension, LibraryRequests.DIMENSION);
        return classifications.list(dimension, includeInactive);
    }

    @PostMapping("/classifications")
    @PreAuthorize(Permission.Codes.LIBRARY_MANAGE)
    ResponseEntity<Classification> addClassification(@Valid @RequestBody LibraryRequests.ClassificationCreate request) {
        Classification created = classifications.create(request);
        return ResponseEntity.created(URI.create("/api/v1/library/classifications/" + created.id())).body(created);
    }

    @PutMapping("/classifications/{id}")
    @PreAuthorize(Permission.Codes.LIBRARY_MANAGE)
    Classification updateClassification(@PathVariable UUID id,
            @Valid @RequestBody LibraryRequests.ClassificationUpdate request) {
        return classifications.update(id, request);
    }

    // ---- reports ---------------------------------------------------------------------------------------------------

    @GetMapping("/reports/summary")
    @PreAuthorize(Permission.Codes.LIBRARY_VIEW)
    Summary summary() {
        return reports.summary();
    }
}
