package com.lawdesk.followup;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.lawdesk.calendar.CalendarEngine;
import com.lawdesk.calendar.CalendarEntry;
import com.lawdesk.calendar.CalendarScope;
import com.lawdesk.notifications.NotificationPublisher;

/**
 * Scenario S14 and the plug-in point: a deadline that a later work package adds to the calendar is announced through the
 * same plumbing, for the people it names; a date that does not exist, or one that is done, announces nothing.
 */
class DateRemindersTest {

    private static final LocalDate DAY = LocalDate.of(2027, 3, 10);
    private static final UUID LAWYER = UUID.randomUUID();
    private static final UUID ALTERNATE = UUID.randomUUID();
    private static final UUID CASE = UUID.randomUUID();

    private final CalendarEngine calendar = mock(CalendarEngine.class);
    private final NotificationPublisher publisher = mock(NotificationPublisher.class);
    private final DateReminders reminders = new DateReminders(calendar, publisher, mock(JdbcClient.class), 3, 7);

    private CalendarEntry entry(String type, String status) {
        return CalendarEntry.of(type, "DEADLINE", UUID.randomUUID(), CASE, "LD-2027-00001", "Case", "OPEN", "Appeal", null,
                null, DAY, null, null, LAWYER, status, ALTERNATE, List.of(LAWYER, ALTERNATE));
    }

    private void calendarHolds(CalendarEntry... entries) {
        when(calendar.collect(any(), any(CalendarScope.class)))
                .thenReturn(new CalendarEngine.Result(List.of(entries), List.of(), false));
    }

    @Test
    void aDeadlineFromAnotherModuleIsAnnouncedToTheEntriesPeople() {
        CalendarEntry deadline = entry("APPEAL_DEADLINE", "OPEN");
        calendarHolds(deadline);
        when(publisher.publish(any(), eq("APPEAL_DEADLINE"), eq(CASE), anyString(), anyMap(), anyString())).thenReturn(true);

        org.assertj.core.api.Assertions.assertThat(reminders.upcoming(DAY.minusDays(1))).isEqualTo(2);
        verify(publisher).publish(eq(LAWYER), eq("APPEAL_DEADLINE"), eq(CASE), eq("DEADLINE:" + deadline.refId()), anyMap(),
                eq(DAY.toString()));
        verify(publisher).publish(eq(ALTERNATE), eq("APPEAL_DEADLINE"), eq(CASE), eq("DEADLINE:" + deadline.refId()), anyMap(),
                eq(DAY.toString()));
    }

    @Test
    void doneCancelledAndOtherKindsOfEntryAnnounceNothing() {
        calendarHolds(entry("APPEAL_DEADLINE", "CANCELLED"), entry("APPEAL_DEADLINE", "DONE"), entry("MEETING", "ACTIVE"),
                entry("TASK", "NEW"), entry("MEMO_DUE", "ACTIVE"), entry("INFO_REQUEST_DUE", "SENT"));
        org.assertj.core.api.Assertions.assertThat(reminders.upcoming(DAY.minusDays(1))).isZero();
        verify(publisher, never()).publish(any(), anyString(), any(), anyString(), anyMap(), any());
        verify(publisher, never()).publish(any(), anyString(), any(), anyString(), anyMap());
    }

    @Test
    void nothingIsInventedWhenTheCalendarHoldsNothing() {
        calendarHolds();
        org.assertj.core.api.Assertions.assertThat(reminders.upcoming(DAY)).isZero();
        verify(publisher, never()).publish(any(), anyString(), isNull(), anyString(), anyMap(), any());
    }
}
