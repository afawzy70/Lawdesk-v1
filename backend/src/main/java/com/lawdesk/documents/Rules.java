package com.lawdesk.documents;

/**
 * Stable codes of the document rules, answered with 422 and {@code rule} (docs/delivery/PHASE-5-ACCEPTANCE.md).
 * Clients may rely on them; never rename one.
 */
final class Rules {

    static final String CASE_ARCHIVED = "CASE_ARCHIVED";
    static final String DOCUMENT_LIMIT_REACHED = "DOCUMENT_LIMIT_REACHED";
    static final String DOCUMENT_VERSION_LIMIT_REACHED = "DOCUMENT_VERSION_LIMIT_REACHED";
    static final String DOCUMENT_WITHDRAWN = "DOCUMENT_WITHDRAWN";
    static final String DOCUMENT_NOT_WITHDRAWN = "DOCUMENT_NOT_WITHDRAWN";
    static final String DOCUMENT_REFERENCED = "DOCUMENT_REFERENCED";
    static final String LEGAL_DOCUMENT_LOCKED = "LEGAL_DOCUMENT_LOCKED";
    static final String LINK_NOT_IN_CASE = "LINK_NOT_IN_CASE";

    private Rules() {
    }
}
