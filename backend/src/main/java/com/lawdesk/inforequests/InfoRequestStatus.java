package com.lawdesk.inforequests;

import java.util.Set;

/** The states of a request to a unit of the authority (requirements section 18). */
public enum InfoRequestStatus {
    DRAFT,
    PENDING_APPROVAL,
    SENT,
    AWAITING,
    /** Derived from the replies: the latest reply is partial. */
    PARTIAL,
    INCOMPLETE,
    COMPLETE,
    /** Set by the follow-up run when the due date has passed without a complete reply; it never closes the request. */
    OVERDUE,
    CLOSED;

    /** The statuses that wait for a reply and become overdue once their due date has passed. */
    static final Set<InfoRequestStatus> WAITING = Set.of(SENT, AWAITING, PARTIAL, INCOMPLETE, OVERDUE);

    /** The statuses a user may set directly; the others follow from the replies and the follow-up run. */
    static final Set<InfoRequestStatus> SETTABLE = Set.of(DRAFT, PENDING_APPROVAL, SENT, AWAITING, CLOSED);

    boolean waiting() {
        return WAITING.contains(this);
    }
}
