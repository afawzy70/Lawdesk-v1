package com.lawdesk.documents;

/**
 * Stable codes of the document rules, answered with 422 and {@code rule} (docs/delivery/PHASE-5-ACCEPTANCE.md).
 * Clients may rely on them; never rename one.
 */
final class Rules {

    static final String CASE_ARCHIVED = "CASE_ARCHIVED";
    static final String DOCUMENT_LIMIT_REACHED = "DOCUMENT_LIMIT_REACHED";

    private Rules() {
    }
}
