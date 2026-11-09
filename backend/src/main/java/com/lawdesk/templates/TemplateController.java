package com.lawdesk.templates;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

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

import com.lawdesk.security.Permission;
import com.lawdesk.templates.TemplateRequests.Draft;
import com.lawdesk.templates.TemplateRequests.Summary;
import com.lawdesk.templates.TemplateRequests.Template;
import com.lawdesk.templates.TemplateRequests.Version;

/**
 * The library of ready legal templates and the drafts made from them, LD-057 (requirements section 29). Reading needs
 * CASE_VIEW (the service shows only approved versions, without their text, to those who do not manage templates); writing
 * needs TEMPLATE_MANAGE; a draft needs the right to see the case.
 */
@RestController
@RequestMapping("/api/v1")
class TemplateController {

    private final TemplateService templates;
    private final DraftService drafts;

    TemplateController(TemplateService templates, DraftService drafts) {
        this.templates = templates;
        this.drafts = drafts;
    }

    @GetMapping("/templates")
    @PreAuthorize(Permission.Codes.CASE_VIEW)
    List<Summary> list(@RequestParam(required = false) String kind,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return templates.list(kind, includeInactive);
    }

    /** The case-file fields a template may use as {{placeholders}}. */
    @GetMapping("/templates/fields")
    @PreAuthorize(Permission.Codes.CASE_VIEW)
    List<TemplateFields.Field> fields() {
        return TemplateFields.all();
    }

    @PostMapping("/templates")
    @PreAuthorize(Permission.Codes.TEMPLATE_MANAGE)
    ResponseEntity<Template> create(@Valid @RequestBody TemplateRequests.Create request) {
        return ResponseEntity.status(201).body(templates.create(request));
    }

    @GetMapping("/templates/{id}")
    @PreAuthorize(Permission.Codes.CASE_VIEW)
    Template get(@PathVariable UUID id) {
        return templates.get(id);
    }

    @PutMapping("/templates/{id}")
    @PreAuthorize(Permission.Codes.TEMPLATE_MANAGE)
    Template update(@PathVariable UUID id, @Valid @RequestBody TemplateRequests.Update request) {
        return templates.update(id, request);
    }

    @PostMapping("/templates/{id}/versions")
    @PreAuthorize(Permission.Codes.TEMPLATE_MANAGE)
    ResponseEntity<Version> addVersion(@PathVariable UUID id, @Valid @RequestBody TemplateRequests.NewVersion request) {
        return ResponseEntity.status(201).body(templates.addVersion(id, request));
    }

    @PutMapping("/template-versions/{id}")
    @PreAuthorize(Permission.Codes.TEMPLATE_MANAGE)
    Version editVersion(@PathVariable UUID id, @Valid @RequestBody TemplateRequests.EditVersion request) {
        return templates.editVersion(id, request);
    }

    @PostMapping("/template-versions/{id}/review")
    @PreAuthorize(Permission.Codes.TEMPLATE_MANAGE)
    Version review(@PathVariable UUID id, @Valid @RequestBody TemplateRequests.Step request) {
        return templates.review(id, request.version());
    }

    @PostMapping("/template-versions/{id}/approve")
    @PreAuthorize(Permission.Codes.TEMPLATE_MANAGE)
    Version approve(@PathVariable UUID id, @Valid @RequestBody TemplateRequests.Step request) {
        return templates.approve(id, request.version());
    }

    @PostMapping("/cases/{caseId}/template-drafts")
    @PreAuthorize(Permission.Codes.CASE_VIEW)
    Draft draft(@PathVariable UUID caseId, @Valid @RequestBody TemplateRequests.DraftRequest request) {
        return drafts.generate(caseId, request);
    }
}
