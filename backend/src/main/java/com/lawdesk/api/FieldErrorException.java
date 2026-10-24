package com.lawdesk.api;

/** A single field fails a rule that bean validation cannot express: answered like a validation failure (400). */
public class FieldErrorException extends RuntimeException {

    private final String field;
    private final String code;

    public FieldErrorException(String field, String code) {
        super(field + " " + code);
        this.field = field;
        this.code = code;
    }

    public String field() {
        return field;
    }

    public String code() {
        return code;
    }
}
