package com.lawdesk.cases;

/** The side of the case a party stands on: plaintiff (مدعي) or defendant (مدعى عليه). The authority takes one of them. */
public enum Side {
    PLAINTIFF,
    DEFENDANT;

    /** The names as a validation pattern; {@code CaseTypeTest} keeps patterns equal to their enums. */
    static final String PATTERN = "PLAINTIFF|DEFENDANT";

    public Side opposite() {
        return this == PLAINTIFF ? DEFENDANT : PLAINTIFF;
    }
}
