package com.lawdesk.lists;

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

import com.lawdesk.lists.ManagedListRequests.Value;
import com.lawdesk.security.Permission;

/**
 * The managed lists of LD-057 (D10): settlement kinds, directive kinds and template kinds. Anyone who reads cases reads the
 * lists; a list is added to and edited by the holders of the permission of the area it belongs to.
 */
@RestController
@RequestMapping("/api/v1/lists/{list}")
class ManagedListController {

    private final ManagedListService service;

    ManagedListController(ManagedListService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(Permission.Codes.CASE_VIEW)
    List<Value> list(@PathVariable String list, @RequestParam(defaultValue = "false") boolean includeInactive) {
        return service.list(list, includeInactive);
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('SETTLEMENT_MANAGE', 'DIRECTIVE_MANAGE', 'TEMPLATE_MANAGE')")
    ResponseEntity<Value> create(@PathVariable String list, @Valid @RequestBody ManagedListRequests.Create request) {
        return ResponseEntity.status(201).body(service.create(list, request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('SETTLEMENT_MANAGE', 'DIRECTIVE_MANAGE', 'TEMPLATE_MANAGE')")
    Value update(@PathVariable String list, @PathVariable UUID id, @Valid @RequestBody ManagedListRequests.Update request) {
        return service.update(list, id, request);
    }
}
