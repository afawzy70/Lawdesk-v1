package com.lawdesk.proceedings;

/** Kinds of special decision (LD-023). */
public enum DecisionType {
    /** وقف التنفيذ: suspends the enforcement of a judgment, which it must name. */
    ENFORCEMENT_SUSPENSION,
    /** إجراء وقتي / مستعجل. */
    URGENT_MEASURE,
    OTHER
}
