package com.lawdesk.deadlines;

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

import com.lawdesk.api.PageResponse;
import com.lawdesk.security.Permission;

/**
 * The settings of the legal deadlines (LD-054, requirements section 10): periods, official holidays and the reminder lead
 * times. Entering is for the deadline managers, approving and retiring for the competent members.
 */
@RestController
@RequestMapping("/api/v1")
class DeadlineSettingsController {

    private final DeadlineSettingsService service;

    DeadlineSettingsController(DeadlineSettingsService service) {
        this.service = service;
    }

    @GetMapping("/deadline-rules")
    @PreAuthorize(Permission.Codes.DEADLINE_VIEW)
    PageResponse<DeadlineRequests.RuleResponse> rules(@RequestParam(required = false) String status, Pageable pageable) {
        return service.rules(requireStatus(status), pageable.getPageNumber(), pageable.getPageSize());
    }

    @PostMapping("/deadline-rules")
    @PreAuthorize(Permission.Codes.DEADLINE_MANAGE)
    ResponseEntity<DeadlineRequests.RuleResponse> createRule(@Valid @RequestBody DeadlineRequests.RuleInput request) {
        return ResponseEntity.status(201).body(service.createRule(request));
    }

    @GetMapping("/deadline-rules/{id}")
    @PreAuthorize(Permission.Codes.DEADLINE_VIEW)
    DeadlineRequests.RuleResponse rule(@PathVariable UUID id) {
        return service.rule(id);
    }

    @PutMapping("/deadline-rules/{id}")
    @PreAuthorize(Permission.Codes.DEADLINE_MANAGE)
    DeadlineRequests.RuleResponse updateRule(@PathVariable UUID id, @Valid @RequestBody DeadlineRequests.RuleInput request) {
        return service.updateRule(id, request);
    }

    @PostMapping("/deadline-rules/{id}/approve")
    @PreAuthorize(Permission.Codes.DEADLINE_APPROVE)
    DeadlineRequests.RuleResponse approveRule(@PathVariable UUID id, @Valid @RequestBody DeadlineRequests.VersionOnly request) {
        return service.approveRule(id, request.version());
    }

    @PostMapping("/deadline-rules/{id}/retire")
    @PreAuthorize(Permission.Codes.DEADLINE_APPROVE)
    DeadlineRequests.RuleResponse retireRule(@PathVariable UUID id, @Valid @RequestBody DeadlineRequests.VersionOnly request) {
        return service.retireRule(id, request.version());
    }

    @GetMapping("/official-holidays")
    @PreAuthorize(Permission.Codes.DEADLINE_VIEW)
    PageResponse<DeadlineRequests.HolidayResponse> holidays(@RequestParam(required = false) String status,
            @RequestParam(required = false) Integer year, Pageable pageable) {
        return service.holidays(requireStatus(status), year, pageable.getPageNumber(), pageable.getPageSize());
    }

    @PostMapping("/official-holidays")
    @PreAuthorize(Permission.Codes.DEADLINE_MANAGE)
    ResponseEntity<DeadlineRequests.HolidayResponse> createHoliday(@Valid @RequestBody DeadlineRequests.HolidayInput request) {
        return ResponseEntity.status(201).body(service.createHoliday(request));
    }

    @PutMapping("/official-holidays/{id}")
    @PreAuthorize(Permission.Codes.DEADLINE_MANAGE)
    DeadlineRequests.HolidayResponse updateHoliday(@PathVariable UUID id,
            @Valid @RequestBody DeadlineRequests.HolidayInput request) {
        return service.updateHoliday(id, request);
    }

    @PostMapping("/official-holidays/{id}/approve")
    @PreAuthorize(Permission.Codes.DEADLINE_APPROVE)
    DeadlineRequests.HolidayResponse approveHoliday(@PathVariable UUID id,
            @Valid @RequestBody DeadlineRequests.VersionOnly request) {
        return service.approveHoliday(id, request.version());
    }

    @PostMapping("/official-holidays/{id}/retire")
    @PreAuthorize(Permission.Codes.DEADLINE_APPROVE)
    DeadlineRequests.HolidayResponse retireHoliday(@PathVariable UUID id,
            @Valid @RequestBody DeadlineRequests.VersionOnly request) {
        return service.retireHoliday(id, request.version());
    }

    /** The lead times of the in-app reminders; {@code configured: false} until somebody sets them (no default). */
    @GetMapping("/deadline-settings/reminders")
    @PreAuthorize(Permission.Codes.DEADLINE_VIEW)
    DeadlineRequests.ReminderSetting reminders() {
        return service.reminders();
    }

    @PutMapping("/deadline-settings/reminders")
    @PreAuthorize(Permission.Codes.DEADLINE_APPROVE)
    DeadlineRequests.ReminderSetting saveReminders(@Valid @RequestBody DeadlineRequests.ReminderInput request) {
        return service.saveReminders(request);
    }

    private static String requireStatus(String status) {
        if (status != null && !java.util.Set.of("DRAFT", "APPROVED", "RETIRED").contains(status)) {
            throw new com.lawdesk.api.FieldErrorException("status", "Pattern");
        }
        return status;
    }
}
