package com.lawdesk.notes;

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
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.lawdesk.api.PageResponse;
import com.lawdesk.security.Permission;

/** Legal notes of a case, LD-029 (docs/delivery/PHASE-5-ACCEPTANCE.md); reading them needs CASE_VIEW (§8.2). */
@RestController
@RequestMapping("/api/v1")
class NoteController {

    private final NoteService service;

    NoteController(NoteService service) {
        this.service = service;
    }

    /** Newest first. */
    @GetMapping("/cases/{caseId}/notes")
    @PreAuthorize(Permission.Codes.CASE_VIEW)
    PageResponse<NoteService.Note> forCase(@PathVariable UUID caseId, Pageable pageable) {
        return PageResponse.from(service.forCase(caseId, pageable));
    }

    @PostMapping("/cases/{caseId}/notes")
    @PreAuthorize(Permission.Codes.NOTE_MANAGE)
    ResponseEntity<NoteService.Note> add(@PathVariable UUID caseId, @Valid @RequestBody NoteService.Write request) {
        NoteService.Note created = service.add(caseId, request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/notes/{id}").buildAndExpand(created.id()).toUri()).body(created);
    }

    @PutMapping("/notes/{id}")
    @PreAuthorize(Permission.Codes.NOTE_MANAGE)
    NoteService.Note edit(@PathVariable UUID id, @Valid @RequestBody NoteService.Edit request) {
        return service.edit(id, request);
    }

    @DeleteMapping("/notes/{id}")
    @PreAuthorize(Permission.Codes.NOTE_MANAGE)
    ResponseEntity<Void> remove(@PathVariable UUID id, @RequestParam(required = false) Long version) {
        service.remove(id, version);
        return ResponseEntity.noContent().build();
    }
}
