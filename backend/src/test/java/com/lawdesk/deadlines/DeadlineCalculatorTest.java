package com.lawdesk.deadlines;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.lawdesk.api.BusinessRuleException;

/**
 * LD-054, owner decision D6: the arithmetic of a proposed due date. Every rule and every holiday here is TEST-ONLY and is
 * created inside the test: the application ships with no statutory period, no counting method and no holiday.
 */
class DeadlineCalculatorTest {

    private static final LocalDate START = LocalDate.of(2031, 3, 3); // a Monday

    private static DeadlineCalculator.Rule rule(int days, String method, boolean countsStart, boolean roll, List<Integer> closed) {
        return new DeadlineCalculator.Rule(UUID.randomUUID(), "TEST-RULE", "Test-only rule", days, method, countsStart, roll,
                closed, "Test-only source", LocalDate.of(2000, 1, 1));
    }

    private static Map<LocalDate, String> holidays(Object... dateAndLabel) {
        Map<LocalDate, String> map = new java.util.LinkedHashMap<>();
        for (int i = 0; i < dateAndLabel.length; i += 2) {
            map.put((LocalDate) dateAndLabel[i], (String) dateAndLabel[i + 1]);
        }
        return map;
    }

    @Test
    void calendarDaysCountedFromTheDayAfterTheStart() {
        DeadlineCalculation c = DeadlineCalculator.calculate(rule(10, "CALENDAR", false, false, List.of()), START, Map.of());
        assertThat(c.dueOn()).isEqualTo(LocalDate.of(2031, 3, 13));
        assertThat(c.firstCountedDay()).isEqualTo(LocalDate.of(2031, 3, 4));
        assertThat(c.skipped()).isEmpty();
        assertThat(c.rolledForwardFrom()).isNull();
    }

    @Test
    void calendarDaysCountedIncludingTheStartDay() {
        DeadlineCalculation c = DeadlineCalculator.calculate(rule(10, "CALENDAR", true, false, List.of()), START, Map.of());
        assertThat(c.dueOn()).isEqualTo(LocalDate.of(2031, 3, 12));
        assertThat(c.firstCountedDay()).isEqualTo(START);
    }

    @Test
    void aCalendarPeriodEndingOnAClosedDayStaysThereUnlessTheRuleRollsForward() {
        // 2031-03-08 is a Saturday (6) and 2031-03-09 a Sunday (7).
        DeadlineCalculation stays = DeadlineCalculator.calculate(rule(5, "CALENDAR", false, false, List.of(6, 7)), START, Map.of());
        assertThat(stays.dueOn()).isEqualTo(LocalDate.of(2031, 3, 8));
        DeadlineCalculation rolled = DeadlineCalculator.calculate(rule(5, "CALENDAR", false, true, List.of(6, 7)), START, Map.of());
        assertThat(rolled.dueOn()).isEqualTo(LocalDate.of(2031, 3, 10));
        assertThat(rolled.rolledForwardFrom()).isEqualTo(LocalDate.of(2031, 3, 8));
        assertThat(rolled.skipped()).extracting(DeadlineCalculation.Skipped::date)
                .containsExactly(LocalDate.of(2031, 3, 8), LocalDate.of(2031, 3, 9));
        assertThat(rolled.skipped()).extracting(DeadlineCalculation.Skipped::kind).containsOnly("WEEKDAY");
    }

    @Test
    void rollingForwardPastAnApprovedHoliday() {
        Map<LocalDate, String> days = holidays(LocalDate.of(2031, 3, 10), "Test holiday");
        DeadlineCalculation c = DeadlineCalculator.calculate(rule(5, "CALENDAR", false, true, List.of(6, 7)), START, days);
        assertThat(c.dueOn()).isEqualTo(LocalDate.of(2031, 3, 11));
        assertThat(c.skipped()).extracting(DeadlineCalculation.Skipped::kind).containsExactly("WEEKDAY", "WEEKDAY", "HOLIDAY");
        assertThat(c.skipped().get(2).label()).isEqualTo("Test holiday");
    }

    @Test
    void workingDaysSkipClosedWeekdaysAndHolidays() {
        // From Monday the 3rd, 5 working days with Saturday and Sunday closed end on Monday the 10th (4,5,6,7 Tue-Fri, then Mon).
        DeadlineCalculation plain = DeadlineCalculator.calculate(rule(5, "WORKING", false, false, List.of(6, 7)), START, Map.of());
        assertThat(plain.dueOn()).isEqualTo(LocalDate.of(2031, 3, 10));
        assertThat(plain.skipped()).hasSize(2);

        Map<LocalDate, String> days = holidays(LocalDate.of(2031, 3, 5), "Test holiday");
        DeadlineCalculation withHoliday = DeadlineCalculator.calculate(rule(5, "WORKING", false, false, List.of(6, 7)), START, days);
        assertThat(withHoliday.dueOn()).isEqualTo(LocalDate.of(2031, 3, 11));
        assertThat(withHoliday.skipped()).extracting(DeadlineCalculation.Skipped::kind).containsExactly("HOLIDAY", "WEEKDAY", "WEEKDAY");
    }

    @Test
    void workingDaysIncludingAWorkingStartDay() {
        DeadlineCalculation c = DeadlineCalculator.calculate(rule(5, "WORKING", true, false, List.of(6, 7)), START, Map.of());
        assertThat(c.dueOn()).isEqualTo(LocalDate.of(2031, 3, 7));
        // A closed start day is not counted even when the start day counts.
        DeadlineCalculation saturday = DeadlineCalculator.calculate(rule(1, "WORKING", true, false, List.of(6, 7)),
                LocalDate.of(2031, 3, 8), Map.of());
        assertThat(saturday.dueOn()).isEqualTo(LocalDate.of(2031, 3, 10));
    }

    @Test
    void aHolidayOutsideTheCountedSpanIsNotReportedAsApplied() {
        Map<LocalDate, String> days = holidays(LocalDate.of(2031, 4, 1), "Far away", LocalDate.of(2031, 2, 1), "Before");
        DeadlineCalculation c = DeadlineCalculator.calculate(rule(3, "WORKING", false, false, List.of()), START, days);
        assertThat(c.dueOn()).isEqualTo(LocalDate.of(2031, 3, 6));
        assertThat(c.skipped()).isEmpty();
    }

    @Test
    void theCalculationRepeatsTheRuleSoTheReviewerSeesTheArithmetic() {
        DeadlineCalculator.Rule rule = rule(5, "WORKING", false, false, List.of(6, 7));
        DeadlineCalculation c = DeadlineCalculator.calculate(rule, START, Map.of());
        assertThat(c.ruleId()).isEqualTo(rule.id());
        assertThat(c.ruleCode()).isEqualTo("TEST-RULE");
        assertThat(c.legalSource()).isEqualTo("Test-only source");
        assertThat(c.periodDays()).isEqualTo(5);
        assertThat(c.countMethod()).isEqualTo("WORKING");
        assertThat(c.startsOn()).isEqualTo(START);
        assertThat(c.closedWeekdays()).containsExactly(6, 7);
    }

    @Test
    void aRuleThatClosesEveryDayCannotBeCountedInWorkingDays() {
        assertThatThrownBy(() -> DeadlineCalculator.calculate(
                rule(2, "WORKING", false, false, List.of(1, 2, 3, 4, 5, 6, 7)), START, Map.of()))
                .isInstanceOf(BusinessRuleException.class).extracting("rule").isEqualTo(Rules.RULE_NOT_COUNTABLE);
    }

    @Test
    void aLongPeriodAcrossManyHolidaysStillEnds() {
        DeadlineCalculation c = DeadlineCalculator.calculate(rule(3650, "WORKING", false, false, List.of(5, 6)), START, Map.of());
        assertThat(c.dueOn()).isAfter(START.plusDays(3650));
    }
}
