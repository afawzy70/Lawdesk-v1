package com.lawdesk.hearings;

/**
 * Stable codes of the hearing rules, answered with 422 and {@code rule} (docs/delivery/PHASE-5-ACCEPTANCE.md). Clients
 * may rely on them; never rename one.
 */
final class Rules {

    static final String CASE_ARCHIVED = "CASE_ARCHIVED";
    static final String HEARING_BEFORE_FILING = "HEARING_BEFORE_FILING";
    static final String HEARING_NOT_SCHEDULED = "HEARING_NOT_SCHEDULED";
    static final String HEARING_NOT_YET_HELD = "HEARING_NOT_YET_HELD";
    static final String ADJOURNMENT_NOT_LATER = "ADJOURNMENT_NOT_LATER";
    /** QAD-032: answered by the proceeding update when its filing date would move after one of its hearings. */
    static final String FILING_AFTER_DEPENDENT_HEARING = "FILING_AFTER_DEPENDENT_HEARING";

    private Rules() {
    }
}
