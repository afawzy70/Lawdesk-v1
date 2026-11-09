package com.lawdesk.reports;

import java.time.LocalDate;
import java.util.List;

/** The list an indicator opens: the same count as its dashboard figure, then the rows of the requested page. */
public record IndicatorPage(IndicatorCode code, boolean available, String unavailableReason, List<IndicatorItem> items,
        int page, int size, long totalItems, int totalPages, LocalDate from, LocalDate to) {
}
