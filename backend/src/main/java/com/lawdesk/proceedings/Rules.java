package com.lawdesk.proceedings;

/**
 * Stable codes of the judicial rules, answered with 422 and {@code rule} (docs/delivery/PHASE-4-ACCEPTANCE.md).
 * Clients may rely on them; never rename one.
 */
final class Rules {

    static final String COURT_LEVEL_MISMATCH = "COURT_LEVEL_MISMATCH";
    static final String COURT_INACTIVE = "COURT_INACTIVE";
    static final String FIRST_INSTANCE_HAS_NO_PARENT = "FIRST_INSTANCE_HAS_NO_PARENT";
    static final String PARENT_PROCEEDING_REQUIRED = "PARENT_PROCEEDING_REQUIRED";
    static final String PARENT_PROCEEDING_OTHER_CASE = "PARENT_PROCEEDING_OTHER_CASE";
    static final String APPEAL_PARENT_NOT_FIRST_INSTANCE = "APPEAL_PARENT_NOT_FIRST_INSTANCE";
    static final String APPEAL_REQUIRES_FIRST_INSTANCE_JUDGMENT = "APPEAL_REQUIRES_FIRST_INSTANCE_JUDGMENT";
    static final String CASSATION_PARENT_NOT_APPEAL = "CASSATION_PARENT_NOT_APPEAL";
    static final String CASSATION_REQUIRES_APPEAL_JUDGMENT = "CASSATION_REQUIRES_APPEAL_JUDGMENT";
    static final String FILED_BEFORE_PARENT_JUDGMENT = "FILED_BEFORE_PARENT_JUDGMENT";
    static final String JUDGMENT_BEFORE_FILING = "JUDGMENT_BEFORE_FILING";

    private Rules() {
    }
}
