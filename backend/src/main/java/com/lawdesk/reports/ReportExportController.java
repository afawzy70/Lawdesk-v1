package com.lawdesk.reports;

import java.util.Locale;

import jakarta.validation.Valid;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lawdesk.audit.AuditLog;
import com.lawdesk.security.ActorRef;
import com.lawdesk.security.CurrentActor;
import com.lawdesk.security.Permission;

/**
 * Report exports (docs/delivery/PHASE-6-ACCEPTANCE.md, LD-034): {@code GET /api/v1/reports/<report>/export} with the
 * report's filters, {@code format} and {@code lang}. Each produced export is audited as {@code REPORT_EXPORT} with the
 * report and format only, never the data or the filter values.
 */
@RestController
@RequestMapping("/api/v1/reports")
class ReportExportController {

    private final ReportExports exports;
    private final AuditLog audit;
    private final CurrentActor actors;

    ReportExportController(ReportExports exports, AuditLog audit, CurrentActor actors) {
        this.exports = exports;
        this.audit = audit;
        this.actors = actors;
    }

    @GetMapping("/cases/export")
    @PreAuthorize(Permission.Codes.REPORT_EXPORT)
    ResponseEntity<byte[]> cases(@Valid @ModelAttribute CaseInventoryQuery query,
            @Valid @ModelAttribute ExportQuery export) {
        return attachment(exports.caseInventory(query, export), export);
    }

    @GetMapping("/judgments/export")
    @PreAuthorize(Permission.Codes.REPORT_EXPORT)
    ResponseEntity<byte[]> judgments(@Valid @ModelAttribute JudgmentReportQuery query,
            @Valid @ModelAttribute ExportQuery export) {
        return attachment(exports.judgments(query, export), export);
    }

    @GetMapping("/hearings/export")
    @PreAuthorize(Permission.Codes.REPORT_EXPORT)
    ResponseEntity<byte[]> hearings(@Valid @ModelAttribute HearingReportQuery query,
            @Valid @ModelAttribute ExportQuery export) {
        return attachment(exports.hearings(query, export), export);
    }

    @GetMapping("/workload/export")
    @PreAuthorize(Permission.Codes.REPORT_EXPORT)
    ResponseEntity<byte[]> workload(@Valid @ModelAttribute ExportQuery export) {
        return attachment(exports.workload(export), export);
    }

    @GetMapping("/experts/export")
    @PreAuthorize(Permission.Codes.REPORT_EXPORT)
    ResponseEntity<byte[]> experts(@Valid @ModelAttribute ExportQuery export) {
        return attachment(exports.experts(export), export);
    }

    private ResponseEntity<byte[]> attachment(ReportExports.ExportFile file, ExportQuery export) {
        ActorRef actor = actors.require();
        audit.record("REPORT_EXPORT", AuditLog.Outcome.SUCCESS, actor.id(), actor.username(), "REPORT",
                file.report() + ":" + export.format().toUpperCase(Locale.ROOT), null);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.filename()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(file.content());
    }
}
