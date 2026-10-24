package com.lawdesk.reports;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lawdesk.security.Permission;

/**
 * The reports on Phase 5 legal operations (docs/delivery/PHASE-6-ACCEPTANCE.md, LD-032 hearings part, LD-033):
 * {@code GET /api/v1/reports/hearings}, {@code /workload} and {@code /experts}.
 */
@RestController
@RequestMapping("/api/v1/reports")
class OperationsReportController {

    private final HearingList hearings;
    private final WorkloadReport workload;
    private final ExpertReport experts;

    OperationsReportController(HearingList hearings, WorkloadReport workload, ExpertReport experts) {
        this.hearings = hearings;
        this.workload = workload;
        this.experts = experts;
    }

    @GetMapping("/hearings")
    @PreAuthorize(Permission.Codes.REPORT_VIEW)
    HearingReport hearings(@Valid @ModelAttribute HearingReportQuery query) {
        return hearings.page(query);
    }

    @GetMapping("/workload")
    @PreAuthorize(Permission.Codes.REPORT_VIEW)
    WorkloadReport.Report workload() {
        return workload.read();
    }

    @GetMapping("/experts")
    @PreAuthorize(Permission.Codes.REPORT_VIEW)
    ExpertReport.Report experts() {
        return experts.read();
    }
}
