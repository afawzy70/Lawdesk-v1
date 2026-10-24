package com.lawdesk.hearings;

/** The state of a hearing (LD-024): scheduled, then exactly once held, adjourned or cancelled. */
public enum HearingStatus {
    SCHEDULED,
    /** انعقدت: the hearing took place. */
    HELD,
    /** تأجلت: the court adjourned the case to a next hearing. */
    ADJOURNED,
    /** The hearing will not take place, for example because the case was struck off the roll. */
    CANCELLED
}
