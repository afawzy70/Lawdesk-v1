package com.lawdesk.notifications;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.Pageable;
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

/** The signed-in user's in-app notifications (requirements section 32). */
@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController {

    /**
     * One notification; {@code params} holds the values for the text of its type, as a JSON object. {@code recordType} and
     * {@code recordId} are the two halves of {@code recordRef} (for example HEARING and the hearing's id), so a screen can
     * open the record; the id is a calendar day for a conflict.
     */
    record Item(UUID id, String type, UUID caseId, String caseReferenceNo, String recordRef, String recordType,
            String recordId, java.util.Map<String, String> params, Instant createdAt, Instant readAt) {
    }

    /** One kind of notification and whether the signed-in user receives it. */
    record Preference(String type, boolean core, int position, boolean enabled) {
    }

    record PreferenceChange(@NotNull Boolean enabled) {
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
            @RequestParam(required = false) Boolean read, @RequestParam(name = "type", required = false) List<String> types,
            @RequestParam(required = false) UUID caseId, Pageable pageable) {
        return service.list(unreadOnly, read, types, caseId, pageable.getPageNumber(), pageable.getPageSize());
    }

    /** The kinds that exist: the eleven of the requirements (core) and the system notices. */
    @GetMapping("/types")
    @PreAuthorize(Permission.Codes.CASE_VIEW)
    List<NotificationTypes.Type> types() {
        return NotificationTypes.ALL;
    }

    @GetMapping("/preferences")
    @PreAuthorize(Permission.Codes.CASE_VIEW)
    List<Preference> preferences() {
        return service.preferences();
    }

    /** The signed-in user switches a kind of in-app notification on or off for themselves. */
    @PutMapping("/preferences/{type}")
    @PreAuthorize(Permission.Codes.CASE_VIEW)
    Preference setPreference(@PathVariable String type, @Valid @RequestBody PreferenceChange change) {
        return service.setPreference(type, change.enabled());
    }

    @PostMapping("/{id}/unread")
    @PreAuthorize(Permission.Codes.CASE_VIEW)
    ResponseEntity<Void> unread(@PathVariable UUID id) {
        service.markUnread(id);
        return ResponseEntity.noContent().build();
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
