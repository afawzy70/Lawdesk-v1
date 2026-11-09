package com.lawdesk.calendar;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.lawdesk.users.UserDirectory;
import com.lawdesk.users.UserDirectory.UserSummary;

/**
 * Asks every {@link CalendarSource} the same question and puts the answers in one order: by time, then type. It names the
 * owners, marks the entries of a closed case that still lie ahead (so they are checked before being cancelled or ignored)
 * and finds the entries of one member that cannot both be attended.
 */
@Component
public class CalendarEngine {

    /** The most entries one answer holds; more sets {@code truncated}. */
    public static final int MAX_ENTRIES = 2000;

    /**
     * Entries of one member that overlap in time. Only entries with a start time can conflict: a day-long entry (a task, a
     * date without hours) does not occupy an hour, and nothing is assumed about the length of an entry that has no end.
     */
    public record Conflict(UUID ownerId, String ownerName, LocalDate date, Instant startsAt, List<CalendarEntry> entries) {
    }

    /** One answer. */
    public record Result(List<CalendarEntry> entries, List<Conflict> conflicts, boolean truncated) {
    }

    private final List<CalendarSource> sources;
    private final UserDirectory users;

    CalendarEngine(List<CalendarSource> sources, UserDirectory users) {
        this.sources = sources;
        this.users = users;
    }

    /** Collects the entries of the query that {@code scope} allows. */
    public Result collect(CalendarQuery query, CalendarScope scope) {
        List<CalendarEntry> all = new ArrayList<>();
        boolean truncated = false;
        CalendarQuery asked = new CalendarQuery(query.from(), query.to(), query.ownerId(), query.caseId(), query.types(),
                MAX_ENTRIES + 1);
        for (CalendarSource source : sources) {
            if (!query.types().isEmpty() && source.types().stream().noneMatch(query::wants)) {
                continue;
            }
            List<CalendarEntry> found = source.find(asked, scope);
            if (found.size() > MAX_ENTRIES) {
                truncated = true;
            }
            for (CalendarEntry entry : found) {
                boolean owned = query.ownerId() == null || query.ownerId().equals(entry.ownerId());
                if (owned && query.wants(entry.type())) {
                    all.add(entry);
                }
            }
        }
        all.sort(Comparator.comparing(CalendarEntry::startsAt).thenComparing(CalendarEntry::type)
                .thenComparing(CalendarEntry::id));
        if (all.size() > MAX_ENTRIES) {
            truncated = true;
            all = new ArrayList<>(all.subList(0, MAX_ENTRIES));
        }
        List<Conflict> conflicts = conflicts(all);
        java.util.Set<String> inConflict = new java.util.HashSet<>();
        conflicts.forEach(c -> c.entries().forEach(e -> inConflict.add(e.id())));
        LocalDate today = CalendarTime.today();
        Map<UUID, Optional<UserSummary>> people = new HashMap<>();
        List<CalendarEntry> out = new ArrayList<>();
        for (CalendarEntry e : all) {
            boolean closedFuture = ("CLOSED".equals(e.caseStatus()) || "ARCHIVED".equals(e.caseStatus()))
                    && !e.date().isBefore(today);
            out.add(e.flagged(inConflict.contains(e.id()), closedFuture).named(name(people, e.ownerId()),
                    name(people, e.alternateMemberId())));
        }
        List<Conflict> named = new ArrayList<>();
        for (Conflict c : conflicts) {
            List<CalendarEntry> entries = out.stream().filter(e -> c.entries().stream().anyMatch(x -> x.id().equals(e.id())))
                    .toList();
            named.add(new Conflict(c.ownerId(), name(people, c.ownerId()), c.date(), c.startsAt(), entries));
        }
        return new Result(out, named, truncated);
    }

    private String name(Map<UUID, Optional<UserSummary>> people, UUID id) {
        if (id == null) {
            return null;
        }
        return people.computeIfAbsent(id, users::find).map(UserSummary::displayName).orElse(null);
    }

    /** Groups of two or more timed, active entries of one owner whose times overlap. */
    static List<Conflict> conflicts(List<CalendarEntry> entries) {
        Map<UUID, List<CalendarEntry>> byOwner = new HashMap<>();
        for (CalendarEntry e : entries) {
            if (e.ownerId() != null && e.startTime() != null && !CalendarEntry.inactive(e.status())) {
                byOwner.computeIfAbsent(e.ownerId(), k -> new ArrayList<>()).add(e);
            }
        }
        List<Conflict> out = new ArrayList<>();
        for (Map.Entry<UUID, List<CalendarEntry>> owner : byOwner.entrySet()) {
            List<CalendarEntry> timed = new ArrayList<>(owner.getValue());
            timed.sort(Comparator.comparing(CalendarEntry::startsAt).thenComparing(CalendarEntry::id));
            List<CalendarEntry> group = new ArrayList<>();
            Instant groupEnd = null;
            Instant previousStart = null;
            for (CalendarEntry e : timed) {
                boolean overlaps = !group.isEmpty()
                        && (e.startsAt().isBefore(groupEnd) || e.startsAt().equals(previousStart));
                if (!overlaps) {
                    flush(out, owner.getKey(), group);
                    group = new ArrayList<>();
                    groupEnd = e.startsAt();
                }
                group.add(e);
                Instant end = e.endsAt() == null ? e.startsAt() : e.endsAt();
                if (end.isAfter(groupEnd)) {
                    groupEnd = end;
                }
                previousStart = e.startsAt();
            }
            flush(out, owner.getKey(), group);
        }
        out.sort(Comparator.comparing(Conflict::startsAt).thenComparing(c -> c.ownerId().toString()));
        return out;
    }

    private static void flush(List<Conflict> out, UUID owner, List<CalendarEntry> group) {
        if (group.size() > 1) {
            out.add(new Conflict(owner, null, group.get(0).date(), group.get(0).startsAt(), List.copyOf(group)));
        }
    }
}
