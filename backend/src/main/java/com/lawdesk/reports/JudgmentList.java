package com.lawdesk.reports;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.cases.CaseReportQueries;
import com.lawdesk.cases.CaseSummary;
import com.lawdesk.proceedings.JudgmentReportQueries;
import com.lawdesk.proceedings.JudgmentReportQueries.JudgmentFilter;
import com.lawdesk.proceedings.JudgmentReportQueries.JudgmentRow;
import com.lawdesk.proceedings.JudgmentReportQueries.JudgmentSummary;
import com.lawdesk.proceedings.JudgmentReportQueries.SortField;

/**
 * The judgments report (docs/delivery/PHASE-6-ACCEPTANCE.md, LD-032): judgments from the proceedings module, the case
 * reference and title from the cases module, read in one repeatable-read snapshot.
 */
@Component
class JudgmentList {

    static final Map<String, SortField> SORT_FIELDS = Map.of("judgmentDate", SortField.JUDGMENT_DATE, "outcome",
            SortField.OUTCOME, "stage", SortField.STAGE);
    static final Paging.Order<SortField> DEFAULT_ORDER = new Paging.Order<>(SortField.JUDGMENT_DATE, true);

    private final JudgmentReportQueries judgments;
    private final CaseReportQueries cases;
    private final ReportCalendar calendar;

    JudgmentList(JudgmentReportQueries judgments, CaseReportQueries cases, ReportCalendar calendar) {
        this.judgments = judgments;
        this.cases = cases;
        this.calendar = calendar;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public JudgmentReport page(JudgmentReportQuery query) {
        JudgmentFilter filter = filter(query);
        Paging paging = Paging.of(query.page(), query.size());
        Paging.Order<SortField> order = Paging.Order.parse(query.sort(), SORT_FIELDS, DEFAULT_ORDER);
        JudgmentSummary summary = judgments.summarize(filter);
        List<JudgmentRow> rows = judgments.list(filter, order.field(), order.descending(), paging.offset(),
                paging.size());
        return new JudgmentReport(rows(rows), paging.page(), paging.size(), summary.total(),
                paging.totalPages(summary.total()), filter.from(), filter.to(),
                new JudgmentReport.Summary(summary.total(), summary.finalJudgments(), summary.byOutcome(),
                        summary.byStage()));
    }

    /** The query as a judgment filter; the period defaults to the current month. */
    JudgmentFilter filter(JudgmentReportQuery query) {
        PeriodQuery.Period period = new PeriodQuery(query.from(), query.to()).resolve(calendar.today());
        return new JudgmentFilter(period.from(), period.to(), Set.copyOf(query.outcome()), Set.copyOf(query.stage()),
                query.courtId(), query.finalJudgment());
    }

    /** The rows with the reference and title of their cases. */
    List<JudgmentReport.Row> rows(List<JudgmentRow> rows) {
        Map<UUID, CaseSummary> summaries = cases.summaries(rows.stream().map(JudgmentRow::caseId).distinct().toList());
        return rows.stream().map(row -> {
            CaseSummary summary = summaries.get(row.caseId());
            return new JudgmentReport.Row(row.id(), row.judgmentDate(), row.outcome(), row.finalJudgment(),
                    row.stage(), row.court(), row.courtCaseNumber(), row.courtCaseYear(), row.caseId(),
                    summary == null ? null : summary.referenceNo(), summary == null ? null : summary.title());
        }).toList();
    }
}
