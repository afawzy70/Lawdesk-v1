package com.lawdesk.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.lawdesk.users.UserDirectory;
import com.lawdesk.users.UserDirectory.UserSummary;

/**
 * LD-056: the calendar is open to new kinds of date. A source added as a bean (the legal deadlines of WP4 will be one) is
 * asked like the built-in ones, its entries are merged, ordered, named and checked for conflicts, and a request for other
 * types never calls it.
 */
class CalendarEngineTest {

    private static final LocalDate DAY = LocalDate.of(2027, 3, 10);
    private static final UUID OWNER = UUID.randomUUID();

    private final UserDirectory users = mock(UserDirectory.class);

    private CalendarEntry entry(String type, String ref, LocalTime start, LocalTime end, UUID owner, String status) {
        return CalendarEntry.of(type, ref, UUID.nameUUIDFromBytes(ref.getBytes()), null, null, null, null, ref, null, null,
                DAY, start, end, owner, status, null, List.of(owner));
    }

    private CalendarSource source(Set<String> types, CalendarEntry... entries) {
        CalendarSource source = mock(CalendarSource.class);
        when(source.types()).thenReturn(types);
        when(source.find(any(), any())).thenReturn(List.of(entries));
        return source;
    }

    private CalendarQuery query(Set<String> types, UUID owner) {
        return new CalendarQuery(DAY, DAY, owner, null, types, CalendarEngine.MAX_ENTRIES);
    }

    @Test
    void aNewKindOfSourceJoinsTheCalendarWithoutChangingTheEngine() {
        when(users.find(OWNER)).thenReturn(Optional.of(new UserSummary(OWNER, "u", "The Owner", true)));
        CalendarSource deadlines = source(Set.of("APPEAL_DEADLINE"), entry("APPEAL_DEADLINE", "DEADLINE", null, null, OWNER, "ACTIVE"));
        CalendarSource hearings = source(Set.of("HEARING"), entry("HEARING", "HEARING", LocalTime.of(9, 0), null, OWNER, "SCHEDULED"));
        CalendarEngine engine = new CalendarEngine(List.of(deadlines, hearings), users);

        CalendarEngine.Result result = engine.collect(query(Set.of(), null), CalendarScope.system());

        // A day-long entry sorts at the start of the day, before the hearing at nine.
        assertThat(result.entries()).extracting(CalendarEntry::type).containsExactly("APPEAL_DEADLINE", "HEARING");
        assertThat(result.entries()).extracting(CalendarEntry::ownerName).containsOnly("The Owner");
        assertThat(result.truncated()).isFalse();
    }

    @Test
    void aRequestForOtherTypesNeverCallsTheSource() {
        CalendarSource deadlines = source(Set.of("APPEAL_DEADLINE"), entry("APPEAL_DEADLINE", "DEADLINE", null, null, OWNER, "ACTIVE"));
        CalendarEngine engine = new CalendarEngine(List.of(deadlines), users);
        assertThat(engine.collect(query(Set.of("TASK"), null), CalendarScope.system()).entries()).isEmpty();
        verify(deadlines, never()).find(any(), any());
    }

    @Test
    void theOwnerFilterAppliesEvenToASourceThatIgnoresIt() {
        UUID other = UUID.randomUUID();
        CalendarSource lazy = source(Set.of("TASK"), entry("TASK", "MINE", null, null, OWNER, "NEW"),
                entry("TASK", "THEIRS", null, null, other, "NEW"));
        CalendarEngine engine = new CalendarEngine(List.of(lazy), users);
        assertThat(engine.collect(query(Set.of(), OWNER), CalendarScope.system()).entries()).extracting(CalendarEntry::title)
                .containsExactly("MINE");
    }

    @Test
    void anAnswerLargerThanTheLimitIsCutAndSaysSo() {
        CalendarEntry[] many = new CalendarEntry[CalendarEngine.MAX_ENTRIES + 1];
        for (int i = 0; i < many.length; i++) {
            many[i] = entry("TASK", "T" + i, null, null, OWNER, "NEW");
        }
        CalendarEngine engine = new CalendarEngine(List.of(source(Set.of("TASK"), many)), users);
        CalendarEngine.Result result = engine.collect(query(Set.of(), null), CalendarScope.system());
        assertThat(result.entries()).hasSize(CalendarEngine.MAX_ENTRIES);
        assertThat(result.truncated()).isTrue();
    }

    @Test
    void conflictsNeedTimedActiveEntriesOfOneOwnerThatOverlap() {
        UUID other = UUID.randomUUID();
        List<CalendarEntry> entries = List.of(
                entry("HEARING", "A", LocalTime.of(9, 0), null, OWNER, "SCHEDULED"),
                entry("HEARING", "B", LocalTime.of(9, 0), null, OWNER, "SCHEDULED"),
                entry("HEARING", "C", LocalTime.of(9, 0), null, other, "SCHEDULED"),
                entry("HEARING", "D", LocalTime.of(9, 0), null, OWNER, "CANCELLED"),
                entry("MEETING", "E", LocalTime.of(10, 0), LocalTime.of(12, 0), OWNER, "ACTIVE"),
                entry("HEARING", "F", LocalTime.of(11, 59), null, OWNER, "SCHEDULED"),
                entry("HEARING", "G", LocalTime.of(12, 0), null, OWNER, "SCHEDULED"),
                entry("TASK", "H", null, null, OWNER, "NEW"),
                entry("TASK", "I", null, null, OWNER, "NEW"));
        List<CalendarEngine.Conflict> conflicts = CalendarEngine.conflicts(entries);
        assertThat(conflicts).hasSize(2);
        assertThat(conflicts.get(0).entries()).extracting(CalendarEntry::title).containsExactly("A", "B");
        assertThat(conflicts.get(1).entries()).extracting(CalendarEntry::title).containsExactly("E", "F");
    }
}
