package com.lawdesk.org;

import java.net.URI;
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

/** {@code /api/v1/org-units} and a user's unit (LD-050). Reading is open to those who work with cases. */
@RestController
@RequestMapping("/api/v1")
class OrgUnitController {

    private static final String READ = "hasAnyAuthority('ORG_MANAGE', 'CASE_VIEW', 'CASE_CREATE')";

    private final OrgUnitService service;

    OrgUnitController(OrgUnitService service) {
        this.service = service;
    }

    @GetMapping("/org-units")
    @PreAuthorize(READ)
    List<OrgUnit> list(@RequestParam(required = false) String tree,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        if (tree != null && !tree.equals("LEGAL") && !tree.equals("AUTHORITY")) {
            throw new com.lawdesk.api.FieldErrorException("tree", "Pattern");
        }
        return service.list(tree, includeInactive);
    }

    @PostMapping("/org-units")
    @PreAuthorize(Permission.Codes.ORG_MANAGE)
    ResponseEntity<OrgUnit> create(@Valid @RequestBody OrgRequests.Create request) {
        OrgUnit created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/org-units/" + created.id())).body(created);
    }

    @GetMapping("/org-units/{id}")
    @PreAuthorize(READ)
    OrgUnit get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PutMapping("/org-units/{id}")
    @PreAuthorize(Permission.Codes.ORG_MANAGE)
    OrgUnit update(@PathVariable UUID id, @Valid @RequestBody OrgRequests.Update request) {
        return service.update(id, request);
    }

    @PutMapping("/users/{userId}/org-unit")
    @PreAuthorize(Permission.Codes.ORG_MANAGE)
    ResponseEntity<Void> placeUser(@PathVariable UUID userId, @Valid @RequestBody OrgRequests.UserUnit request) {
        service.placeUser(userId, request.orgUnitId());
        return ResponseEntity.noContent().build();
    }
}
