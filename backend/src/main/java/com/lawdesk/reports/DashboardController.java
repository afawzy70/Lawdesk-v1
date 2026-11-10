package com.lawdesk.reports;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lawdesk.cases.CaseReportQueries;
import com.lawdesk.experts.ExpertReportQueries;
import com.lawdesk.hearings.HearingReportQueries;
import com.lawdesk.tasks.TaskReportQueries;
import com.lawdesk.cases.CaseReportQueries.CaseCounts;
import com.lawdesk.cases.CaseReportQueries.StatusGroup;
import com.lawdesk.proceedings.JudgmentReportQueries;
import com.lawdesk.proceedings.JudgmentReportQueries.JudgmentCounts;
import com.lawdesk.proceedings.ProceedingReportQueries;
import com.lawdesk.proceedings.ProceedingReportQueries.StageCount;
import com.lawdesk.security.Permission;

/**
 * {@code GET /api/v1/reports/dashboard} (docs/delivery/PHASE-6-ACCEPTANCE.md, LD-030): figures computed from stored
 * cases, proceedings and judgments, read through the owning modules' report ports.
 */
@RestController
@RequestMapping("/api/v1/reports/dashboard")
class DashboardController {

    private final CaseReportQueries cases;
    private final ProceedingReportQueries proceedings;
    private final JudgmentReportQueries judgments;
    private final ReportCalendar calendar;
    private final HearingReportQueries hearings;
    private final ExpertReportQueries experts;
    private final TaskReportQueries tasks;
    private final IndicatorService indicators;

    /** "This week" on the dashboard: today and the six days after it (ASSUMPTION). */
    static final int WEEK_AHEAD_DAYS = 6;

    DashboardController(CaseReportQueries cases, ProceedingReportQueries proceedings, JudgmentReportQueries judgments,
            ReportCalendar calendar, HearingReportQueries hearings, ExpertReportQueries experts,
            TaskReportQueries tasks, IndicatorService indicators) {
        this.cases = cases;
        this.proceedings = proceedings;
        this.judgments = judgments;
        this.calendar = calendar;
        this.hearings = hearings;
        this.experts = experts;
        this.tasks = tasks;
        this.indicators = indicators;
    }

    @GetMapping
    @PreAuthorize(Permission.Codes.REPORT_VIEW)
    // One snapshot: every figure is read in the same repeatable-read transaction, so they agree with each other.
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    DashboardReport dashboard(@Valid @ModelAttribute PeriodQuery query) {
        LocalDate today = calendar.today();
        PeriodQuery.Period period = query.resolve(today);
        YearMonth month = YearMonth.from(today);

        // The indicator figures first: they read the case counts, the upcoming hearings and the overdue tasks, which the
        // dashboard shows too under the same definitions (the same ports and filters), so those are not read a second time.
        // An indicator that failed or is unavailable falls back to its own port, as before.
        IndicatorService.Figures computed = indicators.withCaseCounts(IndicatorQuery.period(query.from(), query.to()));
        CaseCounts caseCounts = computed.caseCounts();
        Long upcoming = computed.countOf(IndicatorCode.UPCOMING_HEARINGS);
        Long overdueTasks = computed.countOf(IndicatorCode.OVERDUE_TASKS);
        List<StageCount> stages = proceedings.byStage();
        JudgmentCounts judged = judgments.between(period.from(), period.to());
        return new DashboardReport(month.toString(),
                new DashboardReport.Cases(caseCounts.total(), caseCounts.count(StatusGroup.OPEN),
                        caseCounts.count(StatusGroup.CLOSED),
                        cases.openedBetween(month.atDay(1), month.atEndOfMonth()), caseCounts.byStatus(),
                        caseCounts.byType()),
                new DashboardReport.Proceedings(stages.stream().mapToLong(StageCount::awaitingJudgment).sum(), stages),
                new DashboardReport.Judgments(period.from(), period.to(), judged.recorded(), judged.finalJudgments(),
                        judged.byOutcome()),
                new DashboardReport.Operations(upcoming != null ? upcoming : hearings.upcomingCount(WEEK_AHEAD_DAYS), hearings.awaitingResultCount(),
                        experts.overdueReportCount(), overdueTasks != null ? overdueTasks : tasks.overdueCount(), tasks.dueSoonCount()),
                List.of(), computed.figures());
    }
}
