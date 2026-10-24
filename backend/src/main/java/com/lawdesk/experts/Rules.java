package com.lawdesk.experts;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Stable codes of the expert rules, answered with 422 and {@code rule} (docs/delivery/PHASE-5-ACCEPTANCE.md), and the
 * module's reading of dates and text. Clients may rely on the codes; never rename one.
 */
final class Rules {

    static final String CASE_ARCHIVED = "CASE_ARCHIVED";
    static final String EXPERT_INACTIVE = "EXPERT_INACTIVE";
    static final String REFERRAL_BEFORE_FILING = "REFERRAL_BEFORE_FILING";
    static final String REPORT_DUE_BEFORE_REFERRAL = "REPORT_DUE_BEFORE_REFERRAL";
    static final String REPORT_BEFORE_REFERRAL = "REPORT_BEFORE_REFERRAL";
    static final String SESSION_BEFORE_REFERRAL = "SESSION_BEFORE_REFERRAL";
    static final String REFERRAL_CLOSED = "REFERRAL_CLOSED";

    /** "Today" is the calendar date in Egypt (ASSUMPTION, common rule 9). */
    static final ZoneId ZONE = ZoneId.of("Africa/Cairo");

    private Rules() {
    }

    static LocalDate today() {
        return LocalDate.now(ZONE);
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
