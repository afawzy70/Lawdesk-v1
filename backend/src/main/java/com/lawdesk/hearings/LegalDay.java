package com.lawdesk.hearings;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/** Dates, times and text as the hearing module reads them (docs/delivery/PHASE-5-ACCEPTANCE.md, common rules). */
final class LegalDay {

    /** "Today" is the calendar date in Egypt (ASSUMPTION, common rule 9). */
    static final ZoneId ZONE = ZoneId.of("Africa/Cairo");

    private LegalDay() {
    }

    static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    /** PostgreSQL keeps microseconds; answers show what was stored. */
    static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    /** Trimmed text, or {@code null} when nothing but whitespace is left (common rule 5). */
    static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
