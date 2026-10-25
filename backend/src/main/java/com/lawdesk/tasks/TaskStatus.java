package com.lawdesk.tasks;

import java.util.EnumSet;
import java.util.Set;

/** The lifecycle of a task (LD-027, ASSUMPTION in docs/delivery/PHASE-5-ACCEPTANCE.md). */
public enum TaskStatus {
    OPEN,
    IN_PROGRESS,
    DONE,
    CANCELLED;

    /** Open tasks are the ones still to do: they become due soon and overdue. */
    public static final Set<TaskStatus> OPEN_STATES = EnumSet.of(OPEN, IN_PROGRESS);

    /** The statuses this one may move to; done and cancelled tasks can only be reopened. */
    Set<TaskStatus> next() {
        return switch (this) {
            case OPEN -> EnumSet.of(IN_PROGRESS, DONE, CANCELLED);
            case IN_PROGRESS -> EnumSet.of(OPEN, DONE, CANCELLED);
            case DONE, CANCELLED -> EnumSet.of(OPEN);
        };
    }

    public boolean isOpen() {
        return OPEN_STATES.contains(this);
    }
}
