package com.lawdesk.reports;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.format.annotation.DateTimeFormat;

import com.lawdesk.cases.CaseReportQueries.StatusGroup;
import com.lawdesk.cases.CaseStatus;
import com.lawdesk.cases.CaseType;
import com.lawdesk.proceedings.Stage;

/**
 * Parameters of the case inventory report and its export (docs/delivery/PHASE-6-ACCEPTANCE.md, LD-031, LD-034). Bound
 * as one object, so a malformed value is a field error ({@code typeMismatch}) instead of a failed conversion; only the
 * whitelisted sort fields pass the pattern.
 */
record CaseInventoryQuery(
        @Size(max = 100) String q,
        List<CaseStatus> status,
        StatusGroup statusGroup,
        List<CaseType> caseType,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate openedFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate openedTo,
        List<Stage> stage,
        UUID courtId,
        @Min(0) Integer page,
        @Min(1) Integer size,
        @Pattern(regexp = SORT) String sort) {

    static final String SORT = "(referenceNo|title|caseType|status|openedOn)(,(asc|desc))?";

    CaseInventoryQuery {
        status = status == null ? List.of() : List.copyOf(status);
        caseType = caseType == null ? List.of() : List.copyOf(caseType);
        stage = stage == null ? List.of() : List.copyOf(stage);
    }
}
