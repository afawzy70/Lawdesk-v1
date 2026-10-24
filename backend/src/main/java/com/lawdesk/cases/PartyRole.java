package com.lawdesk.cases;

/** The role a party plays on a case (LD-016): the firm's client, the opposing side, or anyone else involved. */
public enum PartyRole {
    CLIENT,
    OPPONENT,
    OTHER;

    /** The names as a validation pattern; {@code CaseTypeTest} keeps it equal to the values above. */
    static final String PATTERN = "CLIENT|OPPONENT|OTHER";
}
