package com.lawdesk.reports;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lawdesk.security.Permission;

/** {@code GET /api/v1/reports/judgments} (docs/delivery/PHASE-6-ACCEPTANCE.md, LD-032). */
@RestController
@RequestMapping("/api/v1/reports/judgments")
class JudgmentReportController {

    private final JudgmentList judgments;

    JudgmentReportController(JudgmentList judgments) {
        this.judgments = judgments;
    }

    @GetMapping
    @PreAuthorize(Permission.Codes.REPORT_VIEW)
    JudgmentReport list(@Valid @ModelAttribute JudgmentReportQuery query) {
        return judgments.page(query);
    }
}
