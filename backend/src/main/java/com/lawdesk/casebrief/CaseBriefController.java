package com.lawdesk.casebrief;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lawdesk.security.Permission;

/**
 * The case brief: a read-only, provider-free view over the records of one case. The case access policy decides first (404 for
 * a case the user may not see); only then is the request itself checked.
 */
@RestController
@RequestMapping("/api/v1/cases/{caseId}/brief")
class CaseBriefController {

    private final CaseBriefService service;

    CaseBriefController(CaseBriefService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(Permission.Codes.CASE_VIEW)
    CaseBriefViews.Brief brief(@PathVariable UUID caseId, @RequestParam(defaultValue = "5") int similarLimit) {
        return service.brief(caseId, similarLimit);
    }
}
