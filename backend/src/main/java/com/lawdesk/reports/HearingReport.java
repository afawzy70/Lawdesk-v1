package com.lawdesk.reports;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.lawdesk.hearings.HearingPurpose;
import com.lawdesk.hearings.HearingStatus;
import com.lawdesk.proceedings.ProceedingReportQueries.CourtRef;
import com.lawdesk.proceedings.Stage;

/**
 * {@code GET /api/v1/reports/hearings} (docs/delivery/PHASE-6-ACCEPTANCE.md, LD-032 hearings part): the shared page
 * shape, the period it covers, and a summary over every matching hearing.
 */
record HearingReport(List<Row> items, int page, int size, long totalItems, int totalPages, LocalDate from,
        LocalDate to, Summary summary) {

    /** One hearing with the case and proceeding (stage, court, number) it belongs to. */
    record Row(UUID id, LocalDate hearingDate, LocalTime hearingTime, HearingPurpose purpose, HearingStatus status,
            boolean awaitingResult, Stage stage, CourtRef court, String courtCaseNumber, Integer courtCaseYear,
            UUID caseId, String caseReferenceNo, String caseTitle) {
    }

    /** {@code awaitingResult}: matching hearings still scheduled although their day has passed. */
    record Summary(long total, Map<HearingStatus, Long> byStatus, long awaitingResult) {
    }
}
