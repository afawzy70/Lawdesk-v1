package com.lawdesk.reports;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * The export parameters next to a report's filters (docs/delivery/PHASE-6-ACCEPTANCE.md, LD-034 criterion 1): the
 * required {@code format} and the language of the labels, English by default.
 */
record ExportQuery(@NotNull @Pattern(regexp = "csv") String format, @Pattern(regexp = "en|ar") String lang) {
}
