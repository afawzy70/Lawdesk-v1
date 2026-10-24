package com.lawdesk.cases;

/** The area of law a case belongs to (docs/delivery/PHASE-3-ACCEPTANCE.md, LD-012). */
public enum CaseType {
    CIVIL,
    CRIMINAL,
    COMMERCIAL,
    LABOR,
    FAMILY,
    ADMINISTRATIVE;

    /**
     * The names as a validation pattern, so that an unknown type is a field error rather than unreadable JSON.
     * Annotations need a constant; {@code CaseTypeTest} keeps it equal to the values above.
     */
    static final String PATTERN = "CIVIL|CRIMINAL|COMMERCIAL|LABOR|FAMILY|ADMINISTRATIVE";
}
