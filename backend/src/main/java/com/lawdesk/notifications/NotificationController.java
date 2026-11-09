package com.lawdesk.notifications;

import java.time.Instant;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lawdesk.api.PageResponse;
import com.lawdesk.security.Permission;

/** The signed-in user's in-app notifications (requirements section 32). */
@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController {

    /** One notification; {@code params} holds the values for the text of its type, as a JSON object. */
    record Item(UUID id, String type, UUID caseId, String caseReferenceNo, String recordRef, java.util.Map<String, String> params,
            Instant createdAt, Instant readAt) {
    }

    record UnreadCount(long unread) {
    }

    private final NotificationService service;

    NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(Permission.Codes.CASE_VIEW)
    PageResponse<Item> list(@RequestParam(defaultValue = "false") boolean unreadOnly,
            Pageable pageable) {
        return service.list(unreadOnly, pageable.getPageNumber(), pageable.getPageSize());
    }

    @GetMapping("/unread-count")
    @PreAuthorize(Permission.Codes.CASE_VIEW)
    UnreadCount unread() {
        return new UnreadCount(service.unread());
    }

    @PostMapping("/{id}/read")
    @PreAuthorize(Permission.Codes.CASE_VIEW)
    ResponseEntity<Void> read(@PathVariable UUID id) {
        service.markRead(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    @PreAuthorize(Permission.Codes.CASE_VIEW)
    ResponseEntity<Void> readAll() {
        service.markAllRead();
        return ResponseEntity.noContent().build();
    }
}
