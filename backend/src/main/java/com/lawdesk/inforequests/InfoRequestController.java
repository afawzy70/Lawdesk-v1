package com.lawdesk.inforequests;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.lawdesk.api.FieldErrorException;
import com.lawdesk.api.PageResponse;
import com.lawdesk.security.Permission;

/** Information requests to the authority's units, their replies and follow-up (LD-053, requirements section 18). */
@RestController
@RequestMapping("/api/v1")
class InfoRequestController {

    private final InfoRequestService service;

    InfoRequestController(InfoRequestService service) {
        this.service = service;
    }

    @GetMapping("/cases/{caseId}/info-requests")
    @PreAuthorize(Permission.Codes.INFO_REQUEST_VIEW)
    PageResponse<InfoRequestRequests.Response> forCase(@PathVariable UUID caseId,
            Pageable pageable) {
        return service.forCase(caseId, pageable.getPageNumber(), pageable.getPageSize());
    }

    @PostMapping("/cases/{caseId}/info-requests")
    @PreAuthorize(Permission.Codes.INFO_REQUEST_MANAGE)
    ResponseEntity<InfoRequestRequests.Response> create(@PathVariable UUID caseId,
            @Valid @RequestBody InfoRequestRequests.Create request) {
        InfoRequestRequests.Response created = service.create(caseId, request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/info-requests/{id}").buildAndExpand(created.id()).toUri()).body(created);
    }

    @GetMapping("/info-requests")
    @PreAuthorize(Permission.Codes.INFO_REQUEST_VIEW)
    PageResponse<InfoRequestRequests.Response> list(@RequestParam(required = false) InfoRequestStatus status,
            @RequestParam(required = false) Boolean overdue, @RequestParam(required = false) UUID unitId,
            Pageable pageable) {
        return service.list(status == null ? null : status.name(), overdue, unitId, pageable.getPageNumber(),
                pageable.getPageSize());
    }

    @GetMapping("/info-requests/report")
    @PreAuthorize(Permission.Codes.INFO_REQUEST_VIEW)
    InfoRequestRequests.Report report() {
        return service.report();
    }

    @GetMapping("/info-requests/{id}")
    @PreAuthorize(Permission.Codes.INFO_REQUEST_VIEW)
    InfoRequestRequests.Response get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PutMapping("/info-requests/{id}")
    @PreAuthorize(Permission.Codes.INFO_REQUEST_MANAGE)
    InfoRequestRequests.Response update(@PathVariable UUID id, @Valid @RequestBody InfoRequestRequests.Update request) {
        return service.update(id, request);
    }

    @PostMapping("/info-requests/{id}/status")
    @PreAuthorize(Permission.Codes.INFO_REQUEST_MANAGE)
    InfoRequestRequests.Response status(@PathVariable UUID id, @Valid @RequestBody InfoRequestRequests.StatusChange request) {
        return service.changeStatus(id, request);
    }

    @PostMapping(path = "/info-requests/{id}/replies", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(Permission.Codes.INFO_REQUEST_MANAGE)
    ResponseEntity<InfoRequestRequests.Reply> reply(@PathVariable UUID id,
            @Valid @RequestBody InfoRequestRequests.ReplyInput request) {
        return ResponseEntity.status(201).body(service.reply(id, request, null, null));
    }

    /**
     * A reply with its document: the file is archived in the case file through the document module (the same checks as
     * any upload). The permission is checked before the multipart body is read (QAD-026).
     */
    @PostMapping(path = "/info-requests/{id}/replies", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(Permission.Codes.INFO_REQUEST_MANAGE)
    ResponseEntity<InfoRequestRequests.Reply> replyWithDocument(@PathVariable UUID id, HttpServletRequest http,
            @RequestPart(name = "file", required = false) MultipartFile file,
            @RequestParam(name = "receivedOn", required = false) LocalDate receivedOn,
            @RequestParam(name = "completeness", required = false) InfoRequestRequests.Completeness completeness,
            @RequestParam(name = "summary", required = false) @Size(max = 4000) String summary,
            @RequestParam(name = "title", required = false) @Size(max = 200) String title) {
        if (receivedOn == null) {
            throw new FieldErrorException("receivedOn", "NotNull");
        }
        if (completeness == null) {
            throw new FieldErrorException("completeness", "NotNull");
        }
        InfoRequestRequests.ReplyInput input = new InfoRequestRequests.ReplyInput(receivedOn, completeness, summary, null,
                null, null);
        return ResponseEntity.status(201).body(service.reply(id, input, file, InfoRequestRequests.trim(title)));
    }

    @PostMapping("/info-requests/{id}/follow-ups")
    @PreAuthorize(Permission.Codes.INFO_REQUEST_MANAGE)
    ResponseEntity<InfoRequestRequests.FollowUp> followUp(@PathVariable UUID id,
            @Valid @RequestBody InfoRequestRequests.FollowUpInput request) {
        return ResponseEntity.status(201).body(service.followUp(id, request));
    }
}
