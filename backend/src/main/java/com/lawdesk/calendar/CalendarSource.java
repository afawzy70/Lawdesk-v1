package com.lawdesk.calendar;

import java.util.List;
import java.util.Set;

/**
 * Where calendar entries come from. The calendar owns no dates of its own beyond the manual ones: each module that has
 * dates offers a source, and the calendar asks every source the same question. A new kind of date (the legal deadlines of
 * a later work package) is added by implementing this interface as a Spring bean; nothing else changes, and the entries
 * then also feed the date notifications and the conflict check.
 *
 * <p>A source must restrict what it returns with {@link CalendarScope}: the cases through {@link CalendarScope#cases} (or
 * {@link CalendarScope#records}) and the permission of its module through {@link CalendarScope#may}.
 */
public interface CalendarSource {

    /** The entry types this source can produce; a request for other types does not call it. */
    Set<String> types();

    /** The entries of the Kuwait days of the query that the scope allows, at most {@code query.limit()}. */
    List<CalendarEntry> find(CalendarQuery query, CalendarScope scope);
}
