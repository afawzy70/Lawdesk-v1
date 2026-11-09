package com.lawdesk.reports;

import java.time.LocalDate;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import org.springframework.format.annotation.DateTimeFormat;

import com.lawdesk.cases.CaseReportQueries.UnitLevel;

/**
 * Parameters shared by the dashboard figures, the list a figure opens and its export: the judgment period ({@code from},
 * {@code to}; the current month when absent), the group of a distribution ({@code level} for units, {@code group} the
 * unit name or member id, or {@code _none} for "not specified"), and paging. Because the list takes the very same
 * parameters as the figure, a clicked figure and its list cannot differ.
 */
public record IndicatorQuery(
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        UnitLevel level,
        @Size(max = 300) String group,
        @Min(0) Integer page,
        @Min(1) Integer size,
        @jakarta.validation.constraints.Pattern(regexp = "en|ar") String lang) {

    /** The group key for "no value recorded". */
    public static final String NONE = "_none";

    public static IndicatorQuery period(LocalDate from, LocalDate to) {
        return new IndicatorQuery(from, to, null, null, null, null, null);
    }
}
