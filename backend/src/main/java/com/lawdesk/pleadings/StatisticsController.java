package com.lawdesk.pleadings;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lawdesk.security.Permission;

/** Statistics of claim results and defence positions (REPORT_VIEW; only over the cases the user may see). */
@RestController
class StatisticsController {

    private final StatisticsService service;

    StatisticsController(StatisticsService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/reports/claim-results")
    @PreAuthorize(Permission.Codes.REPORT_VIEW)
    StatisticsRequests.ClaimResults claimResults(@RequestParam(required = false) String stage,
            @RequestParam(required = false) String caseType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.claimResults(stage, caseType, from, to);
    }

    @GetMapping("/api/v1/reports/defense-positions")
    @PreAuthorize(Permission.Codes.REPORT_VIEW)
    StatisticsRequests.DefensePositions defensePositions(@RequestParam(required = false) String stage,
            @RequestParam(required = false) String caseType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.defensePositions(stage, caseType, from, to);
    }
}
