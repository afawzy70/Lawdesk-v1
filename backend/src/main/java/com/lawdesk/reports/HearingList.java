package com.lawdesk.reports;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.cases.CaseReportQueries;
import com.lawdesk.cases.CaseSummary;
import com.lawdesk.hearings.HearingReportQueries;
import com.lawdesk.hearings.HearingReportQueries.HearingFilter;
import com.lawdesk.hearings.HearingReportQueries.HearingRow;
import com.lawdesk.proceedings.ProceedingReportQueries;
import com.lawdesk.proceedings.ProceedingReportQueries.ProceedingRef;

/**
 * The hearings report (docs/delivery/PHASE-6-ACCEPTANCE.md, LD-032 hearings part): hearings from Workstream D's
 * {@link HearingReportQueries}, which also decides which hearing awaits its result; the case from the cases port,
 * stage and court from the proceedings port. Read in one repeatable-read snapshot.
 */
@Component
class HearingList {

    private final HearingReportQueries hearings;
    private final CaseReportQueries cases;
    private final ProceedingReportQueries proceedings;
    private final ReportCalendar calendar;

    HearingList(HearingReportQueries hearings, CaseReportQueries cases, ProceedingReportQueries proceedings,
            ReportCalendar calendar) {
        this.hearings = hearings;
        this.cases = cases;
        this.proceedings = proceedings;
        this.calendar = calendar;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public HearingReport page(HearingReportQuery query) {
        HearingFilter filter = filter(query);
        Paging paging = Paging.of(query.page(), query.size());
        long total = hearings.count(filter);
        List<HearingRow> rows = paging.offset() >= total ? List.of()
                : hearings.find(filter, (int) paging.offset(), paging.size());
        return new HearingReport(rows(rows), paging.page(), paging.size(), total, paging.totalPages(total),
                filter.from(), filter.to(),
                new HearingReport.Summary(total, hearings.countByStatus(filter), awaitingResult(filter, total)));
    }

    /** The query as a hearing filter; the period defaults to the current month. */
    HearingFilter filter(HearingReportQuery query) {
        PeriodQuery.Period period = new PeriodQuery(query.from(), query.to()).resolve(calendar.today());
        return new HearingFilter(period.from(), period.to(),
                query.status().isEmpty() ? null : EnumSet.copyOf(query.status()), null, null);
    }

    /** How many matching hearings the hearing port marks as awaiting their result. */
    private long awaitingResult(HearingFilter filter, long total) {
        long awaiting = 0;
        for (int offset = 0; offset < total; offset += HearingReportQueries.MAX_ROWS) {
            awaiting += hearings.find(filter, offset, HearingReportQueries.MAX_ROWS).stream()
                    .filter(HearingRow::awaitingResult).count();
        }
        return awaiting;
    }

    /** The rows with their case's reference and title and their proceeding's stage, court and number. */
    List<HearingReport.Row> rows(List<HearingRow> rows) {
        Map<UUID, CaseSummary> caseSummaries = cases.summaries(
                rows.stream().map(HearingRow::caseId).distinct().toList());
        Map<UUID, ProceedingRef> refs = proceedings.proceedings(
                rows.stream().map(HearingRow::proceedingId).distinct().toList());
        return rows.stream().map(row -> {
            CaseSummary summary = caseSummaries.get(row.caseId());
            ProceedingRef proceeding = refs.get(row.proceedingId());
            return new HearingReport.Row(row.id(), row.hearingDate(), row.hearingTime(), row.purpose(), row.status(),
                    row.awaitingResult(), proceeding == null ? null : proceeding.stage(),
                    proceeding == null ? null : proceeding.court(),
                    proceeding == null ? null : proceeding.courtCaseNumber(),
                    proceeding == null ? null : proceeding.courtCaseYear(), row.caseId(),
                    summary == null ? null : summary.referenceNo(), summary == null ? null : summary.title());
        }).toList();
    }
}
