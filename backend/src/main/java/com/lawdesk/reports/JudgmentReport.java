package com.lawdesk.reports;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lawdesk.proceedings.Outcome;
import com.lawdesk.proceedings.ProceedingReportQueries.CourtRef;
import com.lawdesk.proceedings.Stage;

/**
 * {@code GET /api/v1/reports/judgments} (docs/delivery/PHASE-6-ACCEPTANCE.md, LD-032): the shared page shape
 * (architecture/CONTRACTS.md §4), the period it covers, and a summary over every matching judgment.
 */
record JudgmentReport(List<Row> items, int page, int size, long totalItems, int totalPages, LocalDate from,
        LocalDate to, Summary summary) {

    /** One judgment with the proceeding, court and case it belongs to; {@code final} as recorded. */
    record Row(UUID id, LocalDate judgmentDate, Outcome outcome, @JsonProperty("final") boolean finalJudgment,
            Stage stage, CourtRef court, String courtCaseNumber, int courtCaseYear, UUID caseId,
            String caseReferenceNo, String caseTitle) {
    }

    record Summary(long total, @JsonProperty("final") long finalJudgments, Map<Outcome, Long> byOutcome,
            Map<Stage, Long> byStage) {
    }
}
