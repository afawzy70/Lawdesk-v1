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
        List<PendingFigure> pending) {

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
     * Dashboard figures whose data belongs to a Phase 5 module that has no report port on {@code develop} yet. They are
     * listed instead of being computed, and never replaced by sample figures (architecture/CONTRACTS.md §8.3).
     */
    enum PendingFigure {
        HEARINGS_THIS_WEEK,
        EXPERT_REPORTS_DUE,
        OVERDUE_TASKS
    }
}
