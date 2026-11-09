package com.lawdesk.library;

import java.util.List;

import org.springframework.stereotype.Component;

/**
 * The read-only library port for the report families (WP8, LD-058): library judgments and principles by court, year,
 * direction, finality, classification and legal topic. Counts of what is recorded, not findings (see
 * {@link LibraryReportService#NOTE}). The library is shared reference material, not case data, so it has no case scope.
 */
@Component
public class LibraryReportQueries {

    /** A key, its label and how many library records carry it. */
    public record Row(String key, String label, long count) {
    }

    /** The groupings, each a list of rows. */
    public record Groups(long total, long active, long withdrawn, List<Row> byCourt, List<Row> byYear,
            List<Row> byDirection, List<Row> byFinality, List<Row> byClassification, List<Row> byTopic, String note) {
    }

    private final LibraryReportService reports;

    LibraryReportQueries(LibraryReportService reports) {
        this.reports = reports;
    }

    public Groups groups() {
        LibraryViews.Summary summary = reports.summary();
        return new Groups(summary.total(), summary.active(), summary.withdrawn(), rows(summary.byCourt()),
                rows(summary.byYear()), rows(summary.byDirection()), rows(summary.byFinality()),
                summary.byClassification().stream().map(c -> new Row(c.code(), c.dimension() + " / " + c.labelAr()
                        + (c.labelEn() == null ? "" : " / " + c.labelEn()), c.count())).toList(),
                summary.frequentIssues().stream().map(i -> new Row(i.topic(), i.topic(), i.principleCount())).toList(),
                summary.note());
    }

    private static List<Row> rows(List<LibraryViews.Count> counts) {
        return counts.stream().map(c -> new Row(c.key(), c.label(), c.count())).toList();
    }
}
