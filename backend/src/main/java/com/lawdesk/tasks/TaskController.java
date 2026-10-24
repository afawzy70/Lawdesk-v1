package com.lawdesk.tasks;

import java.util.List;
import java.util.Locale;
import java.util.Set;
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

import com.lawdesk.api.FieldErrorException;
import com.lawdesk.api.PageResponse;
import com.lawdesk.security.Permission;

/** Tasks, assignments and deadline reminders, LD-027 and LD-028 (docs/delivery/PHASE-5-ACCEPTANCE.md). */
@RestController
@RequestMapping("/api/v1/tasks")
class TaskController {

    private final TaskService service;

    TaskController(TaskService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize(Permission.Codes.TASK_MANAGE)
    ResponseEntity<TaskRequests.Response> create(@Valid @RequestBody TaskRequests.Create request) {
        TaskRequests.Response created = service.create(request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/tasks/{id}").buildAndExpand(created.id()).toUri()).body(created);
    }

    /** {@code assignee} is {@code me} or a user id; {@code due} is {@code overdue} or {@code soon}. */
    @GetMapping
    @PreAuthorize(Permission.Codes.TASK_VIEW)
    PageResponse<TaskRequests.Response> search(@RequestParam(required = false) String assignee,
            @RequestParam(required = false) UUID caseId, @RequestParam(required = false) List<TaskStatus> status,
            @RequestParam(required = false) String due, Pageable pageable) {
        return PageResponse.from(service.search(assignee, caseId, status == null ? Set.of() : Set.copyOf(status),
                dueOf(due), pageable));
    }

    @GetMapping("/reminders")
    @PreAuthorize(Permission.Codes.TASK_VIEW)
    TaskRequests.Reminders reminders() {
        return service.reminders();
    }

    @GetMapping("/{id}")
    @PreAuthorize(Permission.Codes.TASK_VIEW)
    TaskRequests.Response get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Permission.Codes.TASK_MANAGE)
    TaskRequests.Response update(@PathVariable UUID id, @Valid @RequestBody TaskRequests.Update request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/status")
    @PreAuthorize(Permission.Codes.TASK_MANAGE)
    TaskRequests.Response changeStatus(@PathVariable UUID id,
            @Valid @RequestBody TaskRequests.StatusChange request) {
        return service.changeStatus(id, request);
    }

    private static TaskService.Due dueOf(String due) {
        if (due == null || due.isBlank()) {
            return null;
        }
        try {
            return TaskService.Due.valueOf(due.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new FieldErrorException("due", "Pattern");
        }
    }
}
