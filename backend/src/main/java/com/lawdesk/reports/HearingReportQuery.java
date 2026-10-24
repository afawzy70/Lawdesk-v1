package com.lawdesk.reports;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

import org.springframework.format.annotation.DateTimeFormat;

import com.lawdesk.hearings.HearingStatus;

/**
 * Parameters of the hearings report and its export (docs/delivery/PHASE-6-ACCEPTANCE.md, LD-032 hearings part). The
 * hearing port lists hearings in calendar order only, so {@code sort} accepts that order alone (ASSUMPTION).
 */
record HearingReportQuery(
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        List<HearingStatus> status,
        @Min(0) Integer page,
        @Min(1) Integer size,
        @Pattern(regexp = "hearingDate(,asc)?") String sort) {

    HearingReportQuery {
        status = status == null ? List.of() : List.copyOf(status);
    }
}
