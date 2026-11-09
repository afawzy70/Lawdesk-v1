package com.lawdesk.calendar;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

/** The manual dates (meetings, memo and appeal dates the lawyer entered) as a calendar source. */
@Component
class ManualDateSource implements CalendarSource {

    private final ManualDateService dates;

    ManualDateSource(ManualDateService dates) {
        this.dates = dates;
    }

    @Override
    public Set<String> types() {
        return Set.of("MEETING", "INVESTIGATION", "MEMO_DUE", "APPEAL_DEADLINE", "ENFORCEMENT", "OTHER");
    }

    @Override
    public List<CalendarEntry> find(CalendarQuery query, CalendarScope scope) {
        return dates.entries(query, scope);
    }
}
