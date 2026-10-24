package com.lawdesk.tasks;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * Stable codes of the task rules, answered with 422 and {@code rule} (docs/delivery/PHASE-5-ACCEPTANCE.md), and the
 * module's reading of dates and text. Clients may rely on the codes; never rename one.
 */
final class Rules {

    static final String CASE_ARCHIVED = "CASE_ARCHIVED";
    static final String ASSIGNEE_NOT_ASSIGNABLE = "ASSIGNEE_NOT_ASSIGNABLE";
    static final String TASK_STATUS_TRANSITION = "TASK_STATUS_TRANSITION";

    /** "Due soon" is due today or within the next three days (ASSUMPTION, LD-028). */
    static final int DUE_SOON_DAYS = 3;

    /** "Today" is the calendar date in Egypt (ASSUMPTION, common rule 9). */
    static final ZoneId ZONE = ZoneId.of("Africa/Cairo");

    private Rules() {
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

    /** Trimmed text that keeps a blank value blank, so {@code @NotBlank} still names it. */
    static String trim(String value) {
        return value == null ? null : value.strip();
    }
}
