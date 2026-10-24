package com.lawdesk.cases;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.format.annotation.DateTimeFormat;

/**
 * Query parameters of {@code GET /api/v1/cases} (docs/delivery/PHASE-3-ACCEPTANCE.md, LD-015). Bound as one object, so
 * a malformed value is a field error ({@code typeMismatch}) instead of a failed conversion.
 */
record CaseSearch(
        @Size(max = 100) String q,
        List<CaseStatus> status,
        List<CaseType> caseType,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate openedFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate openedTo,
        @Min(0) Integer page,
        @Min(1) Integer size,
        @Pattern(regexp = "(referenceNo|title|openedOn|status|createdAt|updatedAt)(,(asc|desc))?") String sort) {

    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 100;

    CaseSearch {
        q = Text.trimToNull(q);
        status = status == null ? List.of() : List.copyOf(status);
        caseType = caseType == null ? List.of() : List.copyOf(caseType);
    }

    int pageOrDefault() {
        return page == null ? 0 : page;
    }

    /** Sizes above the maximum are capped rather than refused (architecture/CONTRACTS.md §4). */
    int sizeOrDefault() {
        return size == null ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
    }
}
