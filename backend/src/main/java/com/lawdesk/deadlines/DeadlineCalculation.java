package com.lawdesk.deadlines;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The arithmetic of a proposed due date, exactly as it was shown to the reviewer: the rule (with its source), the start
 * date, the first counted day, every closed day that was skipped and the date it ended on. It is stored with the proposal,
 * so a holiday entered later never rewrites a proposal somebody is about to approve.
 */
public record DeadlineCalculation(UUID ruleId, String ruleCode, String ruleLabel, int periodDays, String countMethod,
        boolean countsStartDay, boolean rollForward, List<Integer> closedWeekdays, String legalSource,
        LocalDate effectiveFrom, LocalDate startsOn, LocalDate firstCountedDay, LocalDate rolledForwardFrom,
        LocalDate dueOn, List<Skipped> skipped) {

    /** A day that was not counted (or that the due date rolled past): a closed weekday or an approved official holiday. */
    public record Skipped(LocalDate date, String kind, String label) {
    }
}
