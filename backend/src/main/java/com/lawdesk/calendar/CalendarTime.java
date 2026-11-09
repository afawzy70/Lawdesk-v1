package com.lawdesk.calendar;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * The calendar's time rule (requirements section 15, "what needs no decision" in LD-DOC-07): instants are UTC, people see
 * Kuwait time. A date or time typed without a zone (a hearing's day and hour) is a Kuwait wall-clock reading.
 */
public final class CalendarTime {

    /** The zone every calendar day is read in. */
    public static final ZoneId KUWAIT = ZoneId.of("Asia/Kuwait");

    private CalendarTime() {
    }

    /** The instant of {@code time} on {@code date} in Kuwait; midnight when there is no time. */
    public static Instant instant(LocalDate date, LocalTime time) {
        return (time == null ? date.atStartOfDay(KUWAIT) : date.atTime(time).atZone(KUWAIT)).toInstant();
    }

    /** The Kuwait day of an instant. */
    public static LocalDate day(Instant instant) {
        return instant.atZone(KUWAIT).toLocalDate();
    }

    /** The Kuwait wall-clock time of an instant, to the minute. */
    public static LocalTime time(Instant instant) {
        return instant.atZone(KUWAIT).toLocalTime().withSecond(0).withNano(0);
    }

    /** Today in Kuwait. */
    public static LocalDate today() {
        return LocalDate.now(KUWAIT);
    }
}
