package com.lawdesk.reports;

import java.util.ArrayList;
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

/**
 * The dashboard indicators (LD-058): all figures, the list one figure opens, and its export. Figure, list and export are
 * built by {@link IndicatorService} from one definition per indicator, for the signed-in user's visible cases only.
 */
@RestController
@RequestMapping("/api/v1/reports/indicators")
class IndicatorController {

    private final IndicatorService indicators;
    private final ReportExports exports;
    private final ReportCalendar calendar;
    private final Attachments attachments;

    IndicatorController(IndicatorService indicators, ReportExports exports, ReportCalendar calendar,
            Attachments attachments) {
        this.indicators = indicators;
        this.exports = exports;
        this.calendar = calendar;
        this.attachments = attachments;
    }

    @GetMapping
    @PreAuthorize(Permission.Codes.REPORT_VIEW)
    List<IndicatorFigure> figures(@Valid @ModelAttribute IndicatorQuery query) {
        return indicators.figures(query);
    }

    @GetMapping("/{code}")
    @PreAuthorize(Permission.Codes.REPORT_VIEW)
    IndicatorPage items(@PathVariable IndicatorCode code, @Valid @ModelAttribute IndicatorQuery query) {
        return indicators.page(code, query);
    }

    @GetMapping("/{code}/export")
    @PreAuthorize(Permission.Codes.REPORT_EXPORT)
    ResponseEntity<byte[]> export(@PathVariable IndicatorCode code, @Valid @ModelAttribute IndicatorQuery query,
            @Valid @ModelAttribute ExportQuery export) {
        List<IndicatorItem> items = indicators.export(code, query);
        WpLabels labels = WpLabels.of(export.lang());
        List<List<Object>> rows = new ArrayList<>();
        for (IndicatorItem item : items) {
            rows.add(java.util.Arrays.asList(item.caseReferenceNo(), item.caseTitle(), item.title(), item.date(),
                    labels.value(item.entity(), false, item.status()), labels.value(item.entity(), true, item.detail())));
        }
        List<String> header = List.of(labels.word("caseReferenceNo"), labels.word("caseTitle"), labels.word("title"),
                labels.word("date"), labels.word("status"), labels.word("detail"));
        ExportTable table = new ExportTable(labels.word("indicator." + code.name()), calendar.today().toString(),
                labels.base(), header, rows);
        return attachments.of(exports.file("INDICATOR_" + code.name(), "indicator-" + code.name().toLowerCase(
                java.util.Locale.ROOT).replace('_', '-'), export, table), export);
    }
}
