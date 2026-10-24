package com.lawdesk.cases;

import java.util.EnumSet;
import java.util.Set;

/** The lifecycle status of a case (docs/delivery/PHASE-3-ACCEPTANCE.md, LD-017). */
public enum CaseStatus {
    OPEN,
    ACTIVE,
    SUSPENDED,
    CLOSED,
    ARCHIVED;

    /** The names as a validation pattern; {@code CaseTypeTest} keeps it equal to the values above. */
    static final String PATTERN = "OPEN|ACTIVE|SUSPENDED|CLOSED|ARCHIVED";

    /** The statuses a case may move to from this one; an archived case is final. */
    Set<CaseStatus> next() {
        return switch (this) {
            case OPEN -> EnumSet.of(ACTIVE, CLOSED);
            case ACTIVE -> EnumSet.of(SUSPENDED, CLOSED);
            case SUSPENDED -> EnumSet.of(ACTIVE, CLOSED);
            case CLOSED -> EnumSet.of(ACTIVE, ARCHIVED);
            case ARCHIVED -> EnumSet.noneOf(CaseStatus.class);
        };
    }

    boolean canChangeTo(CaseStatus target) {
        return next().contains(target);
    }

    /** Closed and archived cases keep their details (LD-013); only an archived case also keeps its parties. */
    boolean detailsReadOnly() {
        return this == CLOSED || this == ARCHIVED;
    }
}
