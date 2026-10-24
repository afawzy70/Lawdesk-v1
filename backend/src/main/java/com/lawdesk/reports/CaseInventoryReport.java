package com.lawdesk.reports;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.lawdesk.cases.CaseReportQueries.StatusGroup;
import com.lawdesk.cases.CaseStatus;
import com.lawdesk.cases.CaseType;
import com.lawdesk.proceedings.ProceedingReportQueries.CourtRef;
import com.lawdesk.proceedings.Stage;

/**
 * {@code GET /api/v1/reports/cases} (docs/delivery/PHASE-6-ACCEPTANCE.md, LD-031): the shared page shape
 * (architecture/CONTRACTS.md §4) with a summary over every matching case.
 */
record CaseInventoryReport(List<Row> items, int page, int size, long totalItems, int totalPages, Summary summary) {

    /** One case; {@code currentStage} and {@code currentCourt} are null while the case has no proceedings. */
    record Row(UUID id, String referenceNo, String title, CaseType caseType, CaseStatus status,
            StatusGroup statusGroup, LocalDate openedOn, Stage currentStage, CourtRef currentCourt,
            long proceedingCount) {
    }

    /** {@code byStage} counts current stages, with {@code NONE} for cases without proceedings. */
    record Summary(long total, long open, long closed, Map<CaseStatus, Long> byStatus, Map<CaseType, Long> byType,
            Map<String, Long> byStage) {
    }
}
