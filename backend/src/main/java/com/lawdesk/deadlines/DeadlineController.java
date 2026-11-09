package com.lawdesk.deadlines;

import java.time.LocalDate;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.lawdesk.api.PageResponse;
import com.lawdesk.security.Permission;

/** The legal deadlines of the cases and their register (LD-054, requirements section 10). */
@RestController
@RequestMapping("/api/v1")
class DeadlineController {

    private final DeadlineService service;
    private final DeadlineReminderRun reminders;

    DeadlineController(DeadlineService service, DeadlineReminderRun reminders) {
        this.service = service;
        this.reminders = reminders;
    }

    @GetMapping("/cases/{caseId}/deadlines")
    @PreAuthorize(Permission.Codes.DEADLINE_VIEW)
    PageResponse<DeadlineRequests.Response> forCase(@PathVariable UUID caseId,
            @RequestParam(required = false) DeadlineRequests.Status status,
            @RequestParam(required = false) DeadlineRequests.SourceType sourceType,
            @RequestParam(required = false) UUID sourceId, Pageable pageable) {
        return service.forCase(caseId, new DeadlineStore.Filter(name(status), null, null, null, null, null, null,
                name(sourceType), sourceId), pageable.getPageNumber(), pageable.getPageSize());
    }

    @PostMapping("/cases/{caseId}/deadlines")
    @PreAuthorize(Permission.Codes.DEADLINE_MANAGE)
    ResponseEntity<DeadlineRequests.Response> create(@PathVariable UUID caseId,
            @Valid @RequestBody DeadlineRequests.Create request) {
        return created(service.create(caseId, request));
    }

    /** Computes a due date from an approved rule. Answers 422 {@code NO_APPROVED_RULE} while no approved rule exists. */
    @PostMapping("/cases/{caseId}/deadlines/propose")
    @PreAuthorize(Permission.Codes.DEADLINE_MANAGE)
    ResponseEntity<DeadlineRequests.Response> propose(@PathVariable UUID caseId,
            @Valid @RequestBody DeadlineRequests.Propose request) {
        return created(service.propose(caseId, request));
    }

    /** The register: every deadline of the cases the user may see, filtered; overdue = open and past its due date. */
    @GetMapping("/deadlines")
    @PreAuthorize(Permission.Codes.DEADLINE_VIEW)
    PageResponse<DeadlineRequests.Response> list(@RequestParam(required = false) DeadlineRequests.Status status,
            @RequestParam(required = false) CalcStatus calcStatus, @RequestParam(required = false) UUID ownerId,
            @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) Boolean overdue, @RequestParam(required = false) UUID caseId,
            @RequestParam(required = false) DeadlineRequests.SourceType sourceType, Pageable pageable) {
        return service.list(new DeadlineStore.Filter(name(status), name(calcStatus), ownerId, from, to, overdue, caseId,
                name(sourceType), null), pageable.getPageNumber(), pageable.getPageSize());
    }

    @GetMapping("/deadlines/{id}")
    @PreAuthorize(Permission.Codes.DEADLINE_VIEW)
    DeadlineRequests.Response get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PutMapping("/deadlines/{id}")
    @PreAuthorize(Permission.Codes.DEADLINE_MANAGE)
    DeadlineRequests.Response update(@PathVariable UUID id, @Valid @RequestBody DeadlineRequests.Update request) {
        return service.update(id, request);
    }

    /** Approval by a competent member: the name and the time are kept; a computed date is official only after this. */
    @PostMapping("/deadlines/{id}/approve")
    @PreAuthorize(Permission.Codes.DEADLINE_APPROVE)
    DeadlineRequests.Response approve(@PathVariable UUID id, @Valid @RequestBody DeadlineRequests.Approve request) {
        return service.approve(id, request.version());
    }

    @PostMapping("/deadlines/{id}/status")
    @PreAuthorize(Permission.Codes.DEADLINE_MANAGE)
    DeadlineRequests.Response status(@PathVariable UUID id, @Valid @RequestBody DeadlineRequests.StatusChange request) {
        return service.changeStatus(id, request);
    }

    @GetMapping("/judgments/{judgmentId}/deadlines")
    @PreAuthorize(Permission.Codes.DEADLINE_VIEW)
    PageResponse<DeadlineRequests.Response> forJudgment(@PathVariable UUID judgmentId, Pageable pageable) {
        return service.forJudgment(judgmentId, pageable.getPageNumber(), pageable.getPageSize());
    }

    /** Runs the reminders now (the scheduled run does the same every hour). */
    @PostMapping("/deadlines/reminders/run")
    @PreAuthorize(Permission.Codes.DEADLINE_APPROVE)
    DeadlineRequests.ReminderRunResult runReminders() {
        return reminders.run();
    }

    enum CalcStatus { MANUAL, PROPOSED, APPROVED }

    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static ResponseEntity<DeadlineRequests.Response> created(DeadlineRequests.Response created) {
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/deadlines/{id}").buildAndExpand(created.id()).toUri()).body(created);
    }
}
