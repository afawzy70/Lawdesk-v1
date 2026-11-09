package com.lawdesk.calendar;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.api.FieldErrorException;
import com.lawdesk.cases.CaseAccessPolicy;
import com.lawdesk.security.CurrentActor;

/** The calendar of the signed-in user: day, week, month and period views over the engine, with the user's access. */
@Service
class CalendarService {

    /** The longest period one request may ask for, in days after the first. */
    static final int MAX_SPAN_DAYS = 100;

    /** Number of entries on a day (days without entries are left out). */
    record DayCount(LocalDate date, int count) {
    }

    record View(String view, LocalDate from, LocalDate to, String zone, List<CalendarEntry> events,
            List<CalendarEngine.Conflict> conflicts, List<DayCount> days, boolean truncated) {
    }

    private final CalendarEngine engine;
    private final CaseAccessPolicy access;
    private final CurrentActor currentActor;

    CalendarService(CalendarEngine engine, CaseAccessPolicy access, CurrentActor currentActor) {
        this.engine = engine;
        this.access = access;
        this.currentActor = currentActor;
    }

    @Transactional(readOnly = true)
    View period(String view, LocalDate from, LocalDate to, String user, UUID caseId, List<String> types) {
        if (to.isBefore(from)) {
            throw new FieldErrorException("to", "Range");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_SPAN_DAYS) {
            throw new FieldErrorException("to", "Size");
        }
        CalendarQuery query = new CalendarQuery(from, to, owner(user), caseId, typesOf(types), CalendarEngine.MAX_ENTRIES);
        CalendarEngine.Result result = engine.collect(query, CalendarScope.of(access, currentActor));
        Map<LocalDate, Integer> counts = new LinkedHashMap<>();
        result.entries().forEach(e -> counts.merge(e.date(), 1, Integer::sum));
        List<DayCount> days = counts.entrySet().stream().map(e -> new DayCount(e.getKey(), e.getValue())).toList();
        return new View(view, from, to, CalendarTime.KUWAIT.getId(), result.entries(), result.conflicts(), days,
                result.truncated());
    }

    View day(LocalDate date, String user, UUID caseId, List<String> types) {
        return period("DAY", date, date, user, caseId, types);
    }

    /** Sunday to Saturday around the date. */
    View week(LocalDate date, String user, UUID caseId, List<String> types) {
        LocalDate start = date.minusDays(date.getDayOfWeek().getValue() % 7);
        return period("WEEK", start, start.plusDays(6), user, caseId, types);
    }

    View month(String month, String user, UUID caseId, List<String> types) {
        YearMonth parsed;
        try {
            parsed = YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw new FieldErrorException("month", "Pattern");
        }
        return period("MONTH", parsed.atDay(1), parsed.atEndOfMonth(), user, caseId, types);
    }

    private UUID owner(String user) {
        if (user == null || user.isBlank()) {
            return null;
        }
        if ("me".equals(user)) {
            return currentActor.require().id();
        }
        try {
            return UUID.fromString(user);
        } catch (IllegalArgumentException e) {
            throw new FieldErrorException("userId", "Pattern");
        }
    }

    private static Set<String> typesOf(List<String> types) {
        if (types == null || types.isEmpty()) {
            return Set.of();
        }
        for (String type : types) {
            if (!CalendarEntry.TYPES.contains(type)) {
                throw new FieldErrorException("type", "Pattern");
            }
        }
        return Set.copyOf(types);
    }
}
