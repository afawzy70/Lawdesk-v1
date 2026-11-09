package com.lawdesk.deadlines;

/** Stable codes of the deadline rules, answered with 422 and {@code rule}. Never rename one. */
final class Rules {

    static final String CASE_ARCHIVED = "CASE_ARCHIVED";
    /** The propose endpoint was asked to compute without an approved rule: the system states no period of its own. */
    static final String NO_APPROVED_RULE = "NO_APPROVED_RULE";
    static final String RULE_NOT_EFFECTIVE = "RULE_NOT_EFFECTIVE";
    static final String RULE_NOT_COUNTABLE = "RULE_NOT_COUNTABLE";
    static final String RULE_NOT_DRAFT = "RULE_NOT_DRAFT";
    static final String RULE_NOT_APPROVED = "RULE_NOT_APPROVED";
    static final String HOLIDAY_NOT_DRAFT = "HOLIDAY_NOT_DRAFT";
    static final String HOLIDAY_NOT_APPROVED = "HOLIDAY_NOT_APPROVED";
    static final String SOURCE_NOT_IN_CASE = "DEADLINE_SOURCE_NOT_IN_CASE";
    static final String PROOF_NOT_IN_CASE = "DEADLINE_PROOF_NOT_IN_CASE";
    static final String DEADLINE_ALREADY_APPROVED = "DEADLINE_ALREADY_APPROVED";
    static final String DEADLINE_NO_DUE_DATE = "DEADLINE_NO_DUE_DATE";
    static final String DEADLINE_PROPOSAL_PENDING = "DEADLINE_PROPOSAL_PENDING";
    static final String DEADLINE_CHANGE_REASON_REQUIRED = "DEADLINE_CHANGE_REASON_REQUIRED";
    static final String DEADLINE_NOT_OPEN = "DEADLINE_NOT_OPEN";
    static final String COMPLETION_IN_FUTURE = "DEADLINE_COMPLETION_IN_FUTURE";
    static final String DEADLINE_TRANSITION_NOT_ALLOWED = "DEADLINE_TRANSITION_NOT_ALLOWED";
    static final String HOLIDAY_DATE_TAKEN = "HOLIDAY_DATE_TAKEN";

    private Rules() {
    }
}
