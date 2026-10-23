package com.lawdesk.api;

/**
 * A domain rule refuses the request (for example an appeal without a first-instance judgment): answered with 422 and
 * the stable {@code rule} code; the message stays in the logs.
 */
public class BusinessRuleException extends RuntimeException {

    private final String rule;

    public BusinessRuleException(String rule, String message) {
        super(message);
        this.rule = rule;
    }

    public String rule() {
        return rule;
    }
}
