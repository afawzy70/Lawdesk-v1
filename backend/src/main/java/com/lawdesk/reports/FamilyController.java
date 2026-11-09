package com.lawdesk.reports;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lawdesk.security.Permission;

/** The report families of requirements section 27 (LD-058): the catalogue, one family as a table, and its export. */
@RestController
@RequestMapping("/api/v1/reports/families")
class FamilyController {

    private final ReportFamilies families;
    private final ReportExports exports;
    private final Attachments attachments;

    FamilyController(ReportFamilies families, ReportExports exports, Attachments attachments) {
        this.families = families;
        this.exports = exports;
        this.attachments = attachments;
    }

    @GetMapping
    @PreAuthorize(Permission.Codes.REPORT_VIEW)
    List<ReportFamilies.Info> catalog() {
        return families.catalog();
    }

    @GetMapping("/{code}")
    @PreAuthorize(Permission.Codes.REPORT_VIEW)
    ReportTable.Json family(@PathVariable String code, @Valid @ModelAttribute FamilyQuery query) {
        return families.run(code, query).json();
    }

    @GetMapping("/{code}/export")
    @PreAuthorize(Permission.Codes.REPORT_EXPORT)
    ResponseEntity<byte[]> export(@PathVariable String code, @Valid @ModelAttribute FamilyQuery query,
            @Valid @ModelAttribute ExportQuery export) {
        ReportTable table = families.run(code, query);
        if (!table.available()) {
            throw new com.lawdesk.api.BusinessRuleException(IndicatorService.MODULE_NOT_AVAILABLE,
                    "the module that owns this report is not available yet");
        }
        ReportExports.requireWithinLimit(table.rows().size());
        return attachments.of(exports.file("FAMILY_" + code, "report-" + ReportFamilies.slug(code), export,
                families.exportTable(table, query)), export);
    }
}
