package com.lawdesk.cases;

/** Whether a party is a natural person or an organisation (LD-016). */
public enum PartyKind {
    PERSON,
    ORGANIZATION;

    /** The names as a validation pattern; {@code CaseTypeTest} keeps it equal to the values above. */
    static final String PATTERN = "PERSON|ORGANIZATION";
}
