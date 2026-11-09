package com.lawdesk.calendar;

import java.time.LocalDate;
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
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.lawdesk.security.Permission;

/**
 * The judicial calendar (requirements section 15): the personal calendar ({@code userId=me}), the department's general
 * calendar (no user) and its day, week, month and period views, the conflicts, and the manual dates. Every entry comes from
 * a source that applies the case access rule; days are Kuwait days.
 */
@RestController
@RequestMapping("/api/v1/calendar")
class CalendarController {

    private final CalendarService calendar;
    private final ManualDateService dates;

    CalendarController(CalendarService calendar, ManualDateService dates) {
        this.calendar = calendar;
        this.dates = dates;
    }

    /** {@code userId} is {@code me} or a user id; {@code type} may repeat. At most 101 days. */
    @GetMapping("/events")
    @PreAuthorize(Permission.Codes.CALENDAR_VIEW)
    CalendarService.View events(@RequestParam LocalDate from, @RequestParam LocalDate to,
            @RequestParam(required = false) String userId, @RequestParam(required = false) UUID caseId,
            @RequestParam(name = "type", required = false) List<String> types) {
        return calendar.period("LIST", from, to, userId, caseId, types);
    }

    @GetMapping("/day")
    @PreAuthorize(Permission.Codes.CALENDAR_VIEW)
    CalendarService.View day(@RequestParam LocalDate date, @RequestParam(required = false) String userId,
            @RequestParam(required = false) UUID caseId, @RequestParam(name = "type", required = false) List<String> types) {
        return calendar.day(date, userId, caseId, types);
    }

    @GetMapping("/week")
    @PreAuthorize(Permission.Codes.CALENDAR_VIEW)
    CalendarService.View week(@RequestParam LocalDate date, @RequestParam(required = false) String userId,
            @RequestParam(required = false) UUID caseId, @RequestParam(name = "type", required = false) List<String> types) {
        return calendar.week(date, userId, caseId, types);
    }

    @GetMapping("/month")
    @PreAuthorize(Permission.Codes.CALENDAR_VIEW)
    CalendarService.View month(@RequestParam String month, @RequestParam(required = false) String userId,
            @RequestParam(required = false) UUID caseId, @RequestParam(name = "type", required = false) List<String> types) {
        return calendar.month(month, userId, caseId, types);
    }

    /** The entries that cannot both be attended, with the alternate member of each case offered. */
    @GetMapping("/conflicts")
    @PreAuthorize(Permission.Codes.CALENDAR_VIEW)
    CalendarService.View conflicts(@RequestParam LocalDate from, @RequestParam LocalDate to,
            @RequestParam(required = false) String userId) {
        return calendar.period("CONFLICTS", from, to, userId, null, null);
    }

    @PostMapping("/dates")
    @PreAuthorize(Permission.Codes.CALENDAR_MANAGE)
    ResponseEntity<ManualDateRequests.Response> createDate(@Valid @RequestBody ManualDateRequests.Create request) {
        ManualDateRequests.Response created = dates.create(request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/calendar/dates/{id}").buildAndExpand(created.id()).toUri()).body(created);
    }

    @GetMapping("/dates/{id}")
    @PreAuthorize(Permission.Codes.CALENDAR_VIEW)
    ManualDateRequests.Response getDate(@PathVariable UUID id) {
        return dates.get(id);
    }

    @PutMapping("/dates/{id}")
    @PreAuthorize(Permission.Codes.CALENDAR_MANAGE)
    ManualDateRequests.Response updateDate(@PathVariable UUID id, @Valid @RequestBody ManualDateRequests.Update request) {
        return dates.update(id, request);
    }

    @PostMapping("/dates/{id}/status")
    @PreAuthorize(Permission.Codes.CALENDAR_MANAGE)
    ManualDateRequests.Response changeDateStatus(@PathVariable UUID id,
            @Valid @RequestBody ManualDateRequests.StatusChange request) {
        return dates.changeStatus(id, request);
    }
}
