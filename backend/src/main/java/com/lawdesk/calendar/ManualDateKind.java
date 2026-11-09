package com.lawdesk.calendar;

/**
 * The kinds of date a person enters by hand (requirements section 15): meetings, investigation dates, dates for filing
 * memoranda, appeal and grievance deadlines, enforcement dates, anything else. A manually entered date is the lawyer's
 * date: nothing is calculated from a legal period (decision D6).
 */
public enum ManualDateKind {
    MEETING,
    INVESTIGATION,
    MEMO_DUE,
    APPEAL_DEADLINE,
    ENFORCEMENT,
    OTHER
}
