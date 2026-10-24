package com.lawdesk.cases;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.groups.Default;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.lawdesk.api.PageResponse;
import com.lawdesk.security.Permission;

/** {@code /api/v1/parties} and {@code /api/v1/cases/{id}/parties} (docs/delivery/PHASE-3-ACCEPTANCE.md, LD-016). */
@RestController
class PartyController {

    private final PartyService service;

    PartyController(PartyService service) {
        this.service = service;
    }

    @PostMapping("/api/v1/parties")
    @PreAuthorize(Permission.Codes.PARTY_MANAGE)
    ResponseEntity<Party> create(@Valid @RequestBody PartyRequest request) {
        Party created = service.create(request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequestUri().path("/{id}")
                .buildAndExpand(created.id()).toUri()).body(created);
    }

    @GetMapping("/api/v1/parties")
    @PreAuthorize(Permission.Codes.CASE_VIEW)
    PageResponse<Party> search(@Valid @ModelAttribute Paging paging) {
        return service.search(paging);
    }

    @GetMapping("/api/v1/parties/{id}")
    @PreAuthorize(Permission.Codes.CASE_VIEW)
    Party get(@PathVariable String id) {
        return service.get(Text.id(id, "party"));
    }

    @PutMapping("/api/v1/parties/{id}")
    @PreAuthorize(Permission.Codes.PARTY_MANAGE)
    Party update(@PathVariable String id,
            @Validated({Default.class, PartyRequest.Update.class}) @RequestBody PartyRequest request) {
        return service.update(Text.id(id, "party"), request);
    }

    @GetMapping("/api/v1/cases/{id}/parties")
    @PreAuthorize(Permission.Codes.CASE_VIEW)
    List<CaseParty> ofCase(@PathVariable String id) {
        return service.ofCase(Text.id(id, "case"));
    }

    @PostMapping("/api/v1/cases/{id}/parties")
    @PreAuthorize(Permission.Codes.PARTY_MANAGE)
    @ResponseStatus(HttpStatus.CREATED)
    CaseParty link(@PathVariable String id, @Valid @RequestBody LinkPartyRequest request) {
        return service.link(Text.id(id, "case"), request);
    }

    @DeleteMapping("/api/v1/cases/{id}/parties/{partyId}")
    @PreAuthorize(Permission.Codes.PARTY_MANAGE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unlink(@PathVariable String id, @PathVariable String partyId) {
        service.unlink(Text.id(id, "case"), Text.id(partyId, "case party"));
    }
}
