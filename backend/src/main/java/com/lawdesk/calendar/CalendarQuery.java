package com.lawdesk.calendar;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * What a source is asked for: the Kuwait days {@code from} to {@code to} (both included), optionally one owner or one case
 * or some types. {@code limit} is the most entries a source should return.
 */
public record CalendarQuery(LocalDate from, LocalDate to, UUID ownerId, UUID caseId, Set<String> types, int limit) {

    /** The first instant of {@code from} in Kuwait. */
    public Instant fromInstant() {
        return CalendarTime.instant(from, null);
    }

    /** The first instant after {@code to} in Kuwait. */
    public Instant toExclusiveInstant() {
        return CalendarTime.instant(to.plusDays(1), null);
    }

    public boolean wants(String type) {
        return types.isEmpty() || types.contains(type);
    }
}
