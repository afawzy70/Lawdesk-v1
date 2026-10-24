package com.lawdesk.reports;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.api.BusinessRuleException;
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

    ReportExports(CaseReportQueries cases, CaseInventory inventory, JudgmentReportQueries judgments,
            JudgmentList judgmentList, ReportCalendar calendar) {
        this.cases = cases;
        this.inventory = inventory;
        this.judgments = judgments;
        this.judgmentList = judgmentList;
        this.calendar = calendar;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ExportFile caseInventory(CaseInventoryQuery query, ExportQuery export) {
        CaseFilter filter = inventory.filter(query);
        requireWithinLimit(cases.counts(filter).total());
        Paging.Order<SortField> order = Paging.Order.parse(query.sort(), CaseInventory.SORT_FIELDS,
                CaseInventory.DEFAULT_ORDER);
        ReportLabels labels = ReportLabels.of(export.lang());
        List<List<String>> rows = new ArrayList<>();
        for (CaseInventoryReport.Row row : inventory.rows(
                cases.list(filter, order.field(), order.descending(), 0, MAX_ROWS))) {
            rows.add(Arrays.asList(row.referenceNo(), row.title(), labels.type(row.caseType()),
                    labels.status(row.status()), labels.group(row.statusGroup()), row.openedOn().toString(),
                    labels.stage(row.currentStage()), labels.court(row.currentCourt()),
                    Long.toString(row.proceedingCount())));
        }
        List<String> header = headers(labels, "referenceNo", "title", "caseType", "status", "statusGroup",
                "openedOn", "currentStage", "currentCourt", "proceedingCount");
        return csv("CASE_INVENTORY", "case-inventory", header, rows);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ExportFile judgments(JudgmentReportQuery query, ExportQuery export) {
        JudgmentFilter filter = judgmentList.filter(query);
        requireWithinLimit(judgments.summarize(filter).total());
        Paging.Order<JudgmentReportQueries.SortField> order = Paging.Order.parse(query.sort(),
                JudgmentList.SORT_FIELDS, JudgmentList.DEFAULT_ORDER);
        ReportLabels labels = ReportLabels.of(export.lang());
        List<List<String>> rows = new ArrayList<>();
        for (JudgmentReport.Row row : judgmentList.rows(
                judgments.list(filter, order.field(), order.descending(), 0, MAX_ROWS))) {
            rows.add(Arrays.asList(row.judgmentDate().toString(), row.caseReferenceNo(), row.caseTitle(),
                    labels.stage(row.stage()), labels.court(row.court()),
                    row.courtCaseNumber() + "/" + row.courtCaseYear(), labels.outcome(row.outcome()),
                    labels.yesNo(row.finalJudgment())));
        }
        List<String> header = headers(labels, "judgmentDate", "caseReferenceNo", "caseTitle", "stage", "court",
                "number", "outcome", "final");
        return csv("JUDGMENTS", "judgments", header, rows);
    }

    private static void requireWithinLimit(long rows) {
        if (rows > MAX_ROWS) {
            throw new BusinessRuleException("EXPORT_TOO_LARGE", rows + " rows match; the export limit is " + MAX_ROWS);
        }
    }

    private static List<String> headers(ReportLabels labels, String... keys) {
        return Arrays.stream(keys).map(labels::word).toList();
    }

    private ExportFile csv(String report, String name, List<String> header, List<List<String>> rows) {
        LocalDate today = calendar.today();
        return new ExportFile(report, name + "-" + today + ".csv", "text/csv;charset=UTF-8", Csv.write(header, rows));
    }
}
