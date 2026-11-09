package com.lawdesk.deadlines;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.lawdesk.api.BusinessRuleException;

/**
 * Computes a proposed due date from an approved rule (LD-054, owner decision D6). Pure: the rule and the approved holidays
 * are passed in, nothing is read or written here, and nothing is assumed beyond what the rule states.
 *
 * <ul>
 *   <li>The first counted day is the start date when the rule counts it, the day after otherwise.</li>
 *   <li>CALENDAR: the period is that many consecutive days from the first counted day; when the rule rolls forward, a due
 *       date on a closed day (a closed weekday or an approved holiday) moves to the next open day.</li>
 *   <li>WORKING: the period is that many open days; closed weekdays and approved holidays are not counted.</li>
 * </ul>
 */
final class DeadlineCalculator {

    /** More steps than any valid rule needs (3650 days, at most six closed weekdays out of seven): a safety net only. */
    private static final int MAX_STEPS = 100_000;

    /** The parts of an approved rule the arithmetic uses. */
    record Rule(UUID id, String code, String label, int periodDays, String countMethod, boolean countsStartDay,
            boolean rollForward, List<Integer> closedWeekdays, String legalSource, LocalDate effectiveFrom) {
    }

    private DeadlineCalculator() {
    }

    static DeadlineCalculation calculate(Rule rule, LocalDate startsOn, Map<LocalDate, String> holidays) {
        LocalDate first = rule.countsStartDay() ? startsOn : startsOn.plusDays(1);
        List<DeadlineCalculation.Skipped> skipped = new ArrayList<>();
        LocalDate due;
        LocalDate rolledFrom = null;
        if (rule.countMethod().equals("WORKING")) {
            if (rule.closedWeekdays().size() >= 7) {
                throw new BusinessRuleException(Rules.RULE_NOT_COUNTABLE, "every weekday is closed: no working day exists");
            }
            due = first;
            int counted = 0;
            for (int steps = 0; ; steps++) {
                guard(steps);
                String closed = closedKind(rule, due, holidays);
                if (closed == null) {
                    counted++;
                    if (counted == rule.periodDays()) {
                        break;
                    }
                } else {
                    skipped.add(skip(due, closed, holidays));
                }
                due = due.plusDays(1);
            }
        } else {
            due = first.plusDays(rule.periodDays() - 1L);
            if (rule.rollForward() && closedKind(rule, due, holidays) != null) {
                rolledFrom = due;
                for (int steps = 0; closedKind(rule, due, holidays) != null; steps++) {
                    guard(steps);
                    skipped.add(skip(due, closedKind(rule, due, holidays), holidays));
                    due = due.plusDays(1);
                }
            }
        }
        return new DeadlineCalculation(rule.id(), rule.code(), rule.label(), rule.periodDays(), rule.countMethod(),
                rule.countsStartDay(), rule.rollForward(), rule.closedWeekdays(), rule.legalSource(),
                rule.effectiveFrom(), startsOn, first, rolledFrom, due, List.copyOf(skipped));
    }

    private static void guard(int steps) {
        if (steps > MAX_STEPS) {
            throw new BusinessRuleException(Rules.RULE_NOT_COUNTABLE, "the period does not end");
        }
    }

    /** WEEKDAY, HOLIDAY or null for an open day; a closed weekday wins over a holiday that falls on it. */
    private static String closedKind(Rule rule, LocalDate day, Map<LocalDate, String> holidays) {
        if (rule.closedWeekdays().contains(day.getDayOfWeek().getValue())) {
            return "WEEKDAY";
        }
        return holidays.containsKey(day) ? "HOLIDAY" : null;
    }

    private static DeadlineCalculation.Skipped skip(LocalDate day, String kind, Map<LocalDate, String> holidays) {
        return new DeadlineCalculation.Skipped(day, kind, kind.equals("HOLIDAY") ? holidays.get(day) : null);
    }
}
