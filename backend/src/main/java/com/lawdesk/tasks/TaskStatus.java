package com.lawdesk.tasks;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * The lifecycle of a task (requirements section 14): new, in progress, waiting for documents, waiting for another
 * body, completed, late, cancelled. Until LD-056 the words were OPEN and DONE; both are still accepted as input
 * ({@link #parse}) and map to NEW and COMPLETED, see {@code task_status_map} (V720).
 */
public enum TaskStatus {
    NEW,
    IN_PROGRESS,
    WAITING_DOCUMENTS,
    WAITING_OTHER,
    COMPLETED,
    /** Set by the follow-up run on an open task whose due date has passed; never chosen by a user. */
    LATE,
    CANCELLED;

    /** Open tasks are the ones still to do: they become due soon and overdue, and they open their case to the assignee. */
    public static final Set<TaskStatus> OPEN_STATES = EnumSet.of(NEW, IN_PROGRESS, WAITING_DOCUMENTS, WAITING_OTHER, LATE);

    /** The open states the follow-up run may turn into LATE. */
    static final Set<TaskStatus> CAN_BE_LATE = EnumSet.of(NEW, IN_PROGRESS, WAITING_DOCUMENTS, WAITING_OTHER);

    /** The statuses this one may move to; completed and cancelled tasks can only be reopened. */
    Set<TaskStatus> next() {
        return switch (this) {
            case NEW -> EnumSet.of(IN_PROGRESS, WAITING_DOCUMENTS, WAITING_OTHER, COMPLETED, CANCELLED);
            case IN_PROGRESS -> EnumSet.of(NEW, WAITING_DOCUMENTS, WAITING_OTHER, COMPLETED, CANCELLED);
            case WAITING_DOCUMENTS -> EnumSet.of(NEW, IN_PROGRESS, WAITING_OTHER, COMPLETED, CANCELLED);
            case WAITING_OTHER -> EnumSet.of(NEW, IN_PROGRESS, WAITING_DOCUMENTS, COMPLETED, CANCELLED);
            case LATE -> EnumSet.of(IN_PROGRESS, WAITING_DOCUMENTS, WAITING_OTHER, COMPLETED, CANCELLED);
            case COMPLETED, CANCELLED -> EnumSet.of(NEW);
        };
    }

    public boolean isOpen() {
        return OPEN_STATES.contains(this);
    }

    /** The status named by {@code value}; the legacy words OPEN and DONE are mapped. */
    @JsonCreator
    public static TaskStatus parse(String value) {
        if (value == null) {
            throw new IllegalArgumentException("status");
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "OPEN" -> NEW;
            case "DONE" -> COMPLETED;
            default -> valueOf(value.trim().toUpperCase(Locale.ROOT));
        };
    }
}
