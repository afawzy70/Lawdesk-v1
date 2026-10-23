package com.lawdesk.api;

/** The requested resource does not exist, or the caller may not know that it does: answered with 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String resource) {
        super(resource + " not found");
    }
}
