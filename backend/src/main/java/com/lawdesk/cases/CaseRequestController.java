package com.lawdesk.cases;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.lawdesk.security.Permission;

/** {@code /api/v1/cases/{id}/requests}: «الطلبات» of a case, per side. */
@RestController
class CaseRequestController {

    private final CaseRequestService service;

    CaseRequestController(CaseRequestService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/cases/{id}/requests")
    @PreAuthorize(Permission.Codes.CASE_VIEW)
    CaseRequestService.Items list(@PathVariable String id) {
        return service.list(Text.id(id, "case"));
    }

    @PutMapping("/api/v1/cases/{id}/requests")
    @PreAuthorize(Permission.Codes.CASE_EDIT)
    CaseRequestService.Items replace(@PathVariable String id, @Valid @RequestBody CaseRequestService.Items request) {
        return service.replace(Text.id(id, "case"), request);
    }
}
