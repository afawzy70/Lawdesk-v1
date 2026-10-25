package com.lawdesk.reports;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lawdesk.cases.CaseStatus;
import com.lawdesk.cases.CaseType;
import com.lawdesk.proceedings.Outcome;
import com.lawdesk.proceedings.ProceedingReportQueries.StageCount;

/** {@code GET /api/v1/reports/dashboard} (docs/delivery/PHASE-6-ACCEPTANCE.md, LD-030 criterion 1). */
record DashboardReport(String month, Cases cases, Proceedings proceedings, Judgments judgments,
        Operations operations, List<PendingFigure> pending) {

    record Cases(long total, long open, long closed, long newThisMonth, Map<CaseStatus, Long> byStatus,
            Map<CaseType, Long> byType) {
    }

    record Proceedings(long awaitingJudgment, List<StageCount> byStage) {
    }

    /** {@code final} in JSON counts the judgments recorded as final; Java reserves the word. */
    record Judgments(LocalDate from, LocalDate to, long recorded, @JsonProperty("final") long finalJudgments,
            Map<Outcome, Long> byOutcome) {
    }

    /**
     * Legal operations (Phase 5), each rule evaluated by its owning module's report port: scheduled hearings from today
     * to today + 6 days, hearings still scheduled after their day, open expert referrals whose report is overdue, open
     * tasks due before today, and open tasks due today or within {@code TaskReportQueries.DUE_SOON_DAYS} days.
     */
    record Operations(long hearingsThisWeek, long hearingsAwaitingResult, long expertReportsOverdue, long overdueTasks,
            long tasksDueSoon) {
    }

    /**
     * Dashboard figures whose data belongs to a Phase 5 module that has no report port on {@code develop} yet. They are
     * listed instead of being computed, and never replaced by sample figures (architecture/CONTRACTS.md §8.3).
     */
    enum PendingFigure {
        HEARINGS_THIS_WEEK,
        EXPERT_REPORTS_DUE,
        OVERDUE_TASKS
    }
}
