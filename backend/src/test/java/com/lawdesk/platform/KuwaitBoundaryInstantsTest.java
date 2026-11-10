package com.lawdesk.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.lawdesk.calendar.CalendarTime;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.PastOrPresent;

/**
 * PINNED-CLOCK tests (LD-DOC r3). Every instant is a fixed {@link Clock} or {@link Instant} that only the test supplies; nothing
 * here reads the real clock, so these prove the rule and say nothing about the live server's clock (see the evidence README for
 * the live check). The rule: instants are stored and sent in UTC, people see Kuwait time (UTC+3, no daylight saving), so the
 * Kuwait day starts at 21:00:00Z of the previous UTC day.
 */
class KuwaitBoundaryInstantsTest {

    private record Dated(@PastOrPresent LocalDate on) {
    }

    private record Upcoming(@FutureOrPresent LocalDate on) {
    }

    /** instant (UTC), Kuwait date, Kuwait wall clock to the minute, UTC date. */
    static Stream<Arguments> instants() {
        return Stream.of(
                Arguments.of("2026-11-09T20:59:59Z", "2026-11-09", "23:59", "2026-11-09"),
                Arguments.of("2026-11-09T21:00:00Z", "2026-11-10", "00:00", "2026-11-09"),
                Arguments.of("2026-11-09T23:59:59Z", "2026-11-10", "02:59", "2026-11-09"),
                Arguments.of("2026-11-10T00:00:00Z", "2026-11-10", "03:00", "2026-11-10"),
                Arguments.of("2026-11-10T02:59:59Z", "2026-11-10", "05:59", "2026-11-10"));
    }

    private static LocalValidatorFactoryBean validatorOn(Clock clock) {
        LocalValidatorFactoryBean validator = ProductClockConfig.defaultValidator(clock);
        validator.afterPropertiesSet();
        return validator;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("instants")
    void aStoredUtcInstantReadsAsTheKuwaitDayAndClock(String utc, String kuwaitDay, String kuwaitClock, String utcDay) {
        Instant instant = Instant.parse(utc);
        assertThat(CalendarTime.day(instant)).isEqualTo(LocalDate.parse(kuwaitDay));
        assertThat(CalendarTime.time(instant)).isEqualTo(LocalTime.parse(kuwaitClock));
        // Kuwait is a fixed UTC+3 with no daylight saving, so the offset is the same on every date.
        assertThat(ZoneId.of("Asia/Kuwait").getRules().getOffset(instant)).isEqualTo(ZoneOffset.ofHours(3));
        // A Kuwait wall-clock reading goes back to the same UTC instant (storage is UTC).
        assertThat(CalendarTime.instant(CalendarTime.day(instant), CalendarTime.time(instant)))
                .isEqualTo(instant.truncatedTo(java.time.temporal.ChronoUnit.MINUTES));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("instants")
    void pastOrPresentAcceptsTheKuwaitDayAndRefusesTheDayAfter(String utc, String kuwaitDay, String kuwaitClock,
            String utcDay) {
        Clock pinned = Clock.fixed(Instant.parse(utc), ZoneId.of("Asia/Kuwait"));
        LocalValidatorFactoryBean validator = validatorOn(pinned);
        LocalDate today = LocalDate.parse(kuwaitDay);
        assertThat(validator.validate(new Dated(today))).isEmpty();
        assertThat(validator.validate(new Dated(today.minusDays(1)))).isEmpty();
        assertThat(validator.validate(new Dated(today.plusDays(1)))).hasSize(1);
        // FutureOrPresent is the mirror: the Kuwait day itself is still allowed, yesterday is not.
        assertThat(validator.validate(new Upcoming(today))).isEmpty();
        assertThat(validator.validate(new Upcoming(today.minusDays(1)))).hasSize(1);
    }

    /**
     * The contrast that gives the test its value: a validator on the server's own zone (UTC, Boot's default before
     * ProductClockConfig) refuses the Kuwait day inside the window 21:00:00Z-23:59:59Z and agrees outside it.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("instants")
    void aUtcClockWouldRefuseTheKuwaitDayOnlyInsideTheWindow(String utc, String kuwaitDay, String kuwaitClock,
            String utcDay) {
        LocalValidatorFactoryBean onUtc = validatorOn(Clock.fixed(Instant.parse(utc), ZoneOffset.UTC));
        boolean insideWindow = !kuwaitDay.equals(utcDay);
        assertThat(onUtc.validate(new Dated(LocalDate.parse(kuwaitDay))).isEmpty()).isEqualTo(!insideWindow);
    }
}
