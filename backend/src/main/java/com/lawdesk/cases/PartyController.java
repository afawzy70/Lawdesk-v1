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
    private final CounselService counsel;

    PartyController(PartyService service, CounselService counsel) {
        this.service = service;
        this.counsel = counsel;
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

    @GetMapping("/api/v1/parties/{id}/cases")
    @PreAuthorize(Permission.Codes.CASE_VIEW)
    List<PartyRepository.PartyCase> cases(@PathVariable String id) {
        return service.casesOf(Text.id(id, "party"));
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

    @GetMapping("/api/v1/cases/{id}/parties/counsel")
    @PreAuthorize(Permission.Codes.CASE_VIEW)
    List<CounselService.View> counsel(@PathVariable String id) {
        return counsel.list(Text.id(id, "case"));
    }

    @PostMapping("/api/v1/cases/{id}/parties/{partyId}/counsel")
    @PreAuthorize(Permission.Codes.PARTY_MANAGE)
    @ResponseStatus(HttpStatus.CREATED)
    CounselService.View addCounsel(@PathVariable String id, @PathVariable String partyId,
            @Valid @RequestBody CounselService.Create request) {
        return counsel.add(Text.id(id, "case"), Text.id(partyId, "party"), request);
    }

    @DeleteMapping("/api/v1/cases/{id}/parties/counsel/{counselId}")
    @PreAuthorize(Permission.Codes.PARTY_MANAGE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeCounsel(@PathVariable String id, @PathVariable String counselId) {
        counsel.remove(Text.id(id, "case"), Text.id(counselId, "counsel"));
    }
}
