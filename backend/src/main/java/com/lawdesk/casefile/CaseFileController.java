package com.lawdesk.casefile;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lawdesk.api.FieldErrorException;
import com.lawdesk.security.Permission;

/** The executive summary and the unified timeline of a case (LD-053, requirements section 21). */
@RestController
@RequestMapping("/api/v1/cases/{caseId}")
class CaseFileController {

    private final CaseFileService service;

    CaseFileController(CaseFileService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    @PreAuthorize(Permission.Codes.CASE_VIEW)
    CaseFileViews.Summary summary(@PathVariable UUID caseId) {
        return service.summary(caseId);
    }

    /**
     * Past and future events of the case in one list. {@code types} (comma separated kinds) filters; {@code order} is
     * {@code desc} (default, newest or furthest in the future first) or {@code asc}.
     */
    @GetMapping("/unified-timeline")
    @PreAuthorize(Permission.Codes.CASE_VIEW)
    CaseFileViews.Timeline timeline(@PathVariable UUID caseId, @RequestParam(required = false) String types,
            @RequestParam(defaultValue = "desc") String order, Pageable pageable) {
        if (!order.equals("asc") && !order.equals("desc")) {
            throw new FieldErrorException("order", "Pattern");
        }
        List<String> kinds = types == null || types.isBlank() ? List.of()
                : Arrays.stream(types.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
        return service.timeline(caseId, kinds, order.equals("asc"), pageable);
    }
}
