package com.lawdesk.reports;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.api.BusinessRuleException;
import com.lawdesk.hearings.HearingReportQueries;
import com.lawdesk.hearings.HearingReportQueries.HearingFilter;
import com.lawdesk.cases.CaseReportQueries;
import com.lawdesk.cases.CaseReportQueries.CaseFilter;
import com.lawdesk.cases.CaseReportQueries.SortField;
import com.lawdesk.proceedings.JudgmentReportQueries;
import com.lawdesk.proceedings.JudgmentReportQueries.JudgmentFilter;

/**
 * Report exports (docs/delivery/PHASE-6-ACCEPTANCE.md, LD-034): every row matching the report's filters, in its sort
 * order, without paging. ASSUMPTION: at most {@value #MAX_ROWS} rows; more is refused with {@code EXPORT_TOO_LARGE}
 * before anything is produced.
 */
@Component
class ReportExports {

    static final int MAX_ROWS = 10_000;

    /** A produced export: the report it came from, its file name and content. */
    record ExportFile(String report, String filename, String contentType, byte[] content) {
    }

    private final CaseReportQueries cases;
    private final CaseInventory inventory;
    private final JudgmentReportQueries judgments;
    private final JudgmentList judgmentList;
    private final ReportCalendar calendar;
    private final HearingReportQueries hearings;
    private final HearingList hearingList;
    private final WorkloadReport workload;
    private final ExpertReport experts;

    ReportExports(CaseReportQueries cases, CaseInventory inventory, JudgmentReportQueries judgments,
            JudgmentList judgmentList, ReportCalendar calendar, HearingReportQueries hearings, HearingList hearingList,
            WorkloadReport workload, ExpertReport experts) {
        this.cases = cases;
        this.inventory = inventory;
        this.judgments = judgments;
        this.judgmentList = judgmentList;
        this.calendar = calendar;
        this.hearings = hearings;
        this.hearingList = hearingList;
        this.workload = workload;
        this.experts = experts;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ExportFile hearings(HearingReportQuery query, ExportQuery export) {
        HearingFilter filter = hearingList.filter(query);
        requireWithinLimit(hearings.count(filter));
        ReportLabels labels = ReportLabels.of(export.lang());
        List<List<Object>> rows = new ArrayList<>();
        for (HearingReport.Row row : hearingList.rows(hearings.find(filter, 0, MAX_ROWS))) {
            rows.add(Arrays.asList(row.hearingDate(), row.hearingTime(), row.caseReferenceNo(), row.caseTitle(),
                    labels.stage(row.stage()), labels.court(row.court()),
                    row.courtCaseNumber() == null ? null : row.courtCaseNumber() + "/" + row.courtCaseYear(),
                    labels.word("purpose." + row.purpose().name()), labels.word("hearing." + row.status().name()),
                    labels.yesNo(row.awaitingResult())));
        }
        List<String> header = headers(labels, "hearingDate", "hearingTime", "caseReferenceNo", "caseTitle", "stage",
                "court", "number", "purpose", "status", "awaitingResult");
        return file("HEARINGS", "hearings", export,
                new ExportTable(labels.word("report.HEARINGS"), filter.from() + " – " + filter.to(), labels, header,
                        rows));
    }

    public ExportFile workload(ExportQuery export) {
        WorkloadReport.Report report = workload.read();
        requireWithinLimit(report.items().size());
        ReportLabels labels = ReportLabels.of(export.lang());
        List<List<Object>> rows = new ArrayList<>();
        for (WorkloadReport.Row row : report.items()) {
            rows.add(Arrays.asList(row.displayName() == null ? labels.word("unknownUser") : row.displayName(),
                    row.open(), row.inProgress(), row.overdue()));
        }
        return file("WORKLOAD", "workload", export, new ExportTable(labels.word("report.WORKLOAD"),
                calendar.today().toString(), labels, headers(labels, "assignee", "open", "inProgress", "overdue"),
                rows));
    }

    public ExportFile experts(ExportQuery export) {
        ExpertReport.Report report = experts.read();
        requireWithinLimit(report.items().size());
        ReportLabels labels = ReportLabels.of(export.lang());
        List<List<Object>> rows = new ArrayList<>();
        for (ExpertReport.Row row : report.items()) {
            rows.add(Arrays.asList(row.name(), row.specialty(), labels.yesNo(row.active()), row.open(),
                    row.overdue()));
        }
        return file("EXPERTS", "experts", export, new ExportTable(labels.word("report.EXPERTS"),
                calendar.today().toString(), labels,
                headers(labels, "expert", "specialty", "active", "openReferrals", "overdueReports"), rows));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ExportFile caseInventory(CaseInventoryQuery query, ExportQuery export) {
        CaseFilter filter = inventory.filter(query);
        requireWithinLimit(cases.counts(filter).total());
        Paging.Order<SortField> order = Paging.Order.parse(query.sort(), CaseInventory.SORT_FIELDS,
                CaseInventory.DEFAULT_ORDER);
        ReportLabels labels = ReportLabels.of(export.lang());
        List<List<Object>> rows = new ArrayList<>();
        for (CaseInventoryReport.Row row : inventory.rows(
                cases.list(filter, order.field(), order.descending(), 0, MAX_ROWS))) {
            rows.add(Arrays.asList(row.referenceNo(), row.title(), labels.type(row.caseType()),
                    labels.status(row.status()), labels.group(row.statusGroup()), row.openedOn(),
                    labels.stage(row.currentStage()), labels.court(row.currentCourt()), row.proceedingCount()));
        }
        List<String> header = headers(labels, "referenceNo", "title", "caseType", "status", "statusGroup",
                "openedOn", "currentStage", "currentCourt", "proceedingCount");
        return file("CASE_INVENTORY", "case-inventory", export,
                new ExportTable(labels.word("report.CASE_INVENTORY"), calendar.today().toString(), labels, header,
                        rows));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ExportFile judgments(JudgmentReportQuery query, ExportQuery export) {
        JudgmentFilter filter = judgmentList.filter(query);
        requireWithinLimit(judgments.summarize(filter).total());
        Paging.Order<JudgmentReportQueries.SortField> order = Paging.Order.parse(query.sort(),
                JudgmentList.SORT_FIELDS, JudgmentList.DEFAULT_ORDER);
        ReportLabels labels = ReportLabels.of(export.lang());
        List<List<Object>> rows = new ArrayList<>();
        for (JudgmentReport.Row row : judgmentList.rows(
                judgments.list(filter, order.field(), order.descending(), 0, MAX_ROWS))) {
            rows.add(Arrays.asList(row.judgmentDate(), row.caseReferenceNo(), row.caseTitle(),
                    labels.stage(row.stage()), labels.court(row.court()),
                    row.courtCaseNumber() + "/" + row.courtCaseYear(), labels.outcome(row.outcome()),
                    labels.yesNo(row.finalJudgment())));
        }
        List<String> header = headers(labels, "judgmentDate", "caseReferenceNo", "caseTitle", "stage", "court",
                "number", "outcome", "final");
        return file("JUDGMENTS", "judgments", export,
                new ExportTable(labels.word("report.JUDGMENTS"), filter.from() + " – " + filter.to(), labels, header,
                        rows));
    }

    private static void requireWithinLimit(long rows) {
        if (rows > MAX_ROWS) {
            throw new BusinessRuleException("EXPORT_TOO_LARGE", rows + " rows match; the export limit is " + MAX_ROWS);
        }
    }

    private static List<String> headers(ReportLabels labels, String... keys) {
        return Arrays.stream(keys).map(labels::word).toList();
    }

    private ExportFile file(String report, String name, ExportQuery export, ExportTable table) {
        LocalDate today = calendar.today();
        String base = name + "-" + today;
        return switch (export.format()) {
            case "xlsx" -> new ExportFile(report, base + ".xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", Xlsx.write(table));
            case "pdf" -> new ExportFile(report, base + ".pdf", "application/pdf", Pdf.write(table));
            default -> new ExportFile(report, base + ".csv", "text/csv;charset=UTF-8",
                    Csv.write(table.header(), table.textRows()));
        };
    }
}
