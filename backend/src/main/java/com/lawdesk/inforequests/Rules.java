package com.lawdesk.inforequests;

/** Stable codes of the information-request rules, answered with 422 and {@code rule}. Never rename one. */
final class Rules {

    static final String CASE_ARCHIVED = "CASE_ARCHIVED";
    static final String INFO_REQUEST_TRANSITION_NOT_ALLOWED = "INFO_REQUEST_TRANSITION_NOT_ALLOWED";
    static final String INFO_REQUEST_NOT_SENT = "INFO_REQUEST_NOT_SENT";
    static final String INFO_REQUEST_CLOSED = "INFO_REQUEST_CLOSED";
    static final String REPLY_DOCUMENT_NOT_IN_CASE = "REPLY_DOCUMENT_NOT_IN_CASE";

    private Rules() {
    }
}
