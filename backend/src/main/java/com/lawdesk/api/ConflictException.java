package com.lawdesk.api;

/** The request conflicts with the stored state (a duplicate, a stale version): answered with 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
