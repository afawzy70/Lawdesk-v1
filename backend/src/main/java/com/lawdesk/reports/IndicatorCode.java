package com.lawdesk.reports;

/**
 * The indicators of the main dashboard (requirements section 26, LD-DOC-26-001…014). Fourteen indicators; the second
 * (new, circulating, concluded cases) has three figures, so there are sixteen codes. Each code is defined once, in
 * {@link IndicatorService}, and the dashboard figure, the list reached by clicking it and its export all read that one
 * definition, so they cannot disagree.
 */
public enum IndicatorCode {
    TOTAL_CASES(1, Entity.CASE),
    CASES_NEW(2, Entity.CASE),
    CASES_ONGOING(2, Entity.CASE),
    CASES_CONCLUDED(2, Entity.CASE),
    RESERVED_FOR_JUDGMENT(3, Entity.CASE),
    JUDGMENTS_ISSUED(4, Entity.JUDGMENT),
    JUDGMENTS_IN_ENFORCEMENT(5, Entity.JUDGMENT),
    UPCOMING_HEARINGS(6, Entity.HEARING),
    EXPERT_HEARINGS(7, Entity.HEARING),
    APPEAL_DEADLINES(8, Entity.DEADLINE),
    OVERDUE_TASKS(9, Entity.TASK),
    CASES_BY_UNIT(10, Entity.CASE),
    CASES_BY_MEMBER(11, Entity.CASE),
    HIGH_RISK_CASES(12, Entity.CASE),
    OVERDUE_INFO_REQUESTS(13, Entity.INFO_REQUEST),
    LINKED_CASES(14, Entity.CASE);

    /** What one counted row is. */
    public enum Entity {
        CASE,
        JUDGMENT,
        HEARING,
        TASK,
        INFO_REQUEST,
        DEADLINE
    }

    private final int number;
    private final Entity entity;

    IndicatorCode(int number, Entity entity) {
        this.number = number;
        this.entity = entity;
    }

    /** The position (1…14) in the requirements list. */
    public int number() {
        return number;
    }

    public Entity entity() {
        return entity;
    }

    /** True for the indicators that are a distribution over groups (by unit, by member) and can be narrowed to one. */
    public boolean grouped() {
        return this == CASES_BY_UNIT || this == CASES_BY_MEMBER;
    }
}
