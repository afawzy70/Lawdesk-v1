package com.lawdesk.reports;

import java.time.LocalDate;
import java.time.YearMonth;

import org.springframework.format.annotation.DateTimeFormat;

import com.lawdesk.api.BusinessRuleException;

/**
 * The {@code from} / {@code to} parameters of a report: inclusive calendar dates (docs/delivery/PHASE-6-ACCEPTANCE.md,
 * common rules 5 and 7). Bound as one object, so a malformed date is a field error ({@code typeMismatch}).
 */
record PeriodQuery(@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

    /** A resolved period, both days included. */
    record Period(LocalDate from, LocalDate to) {
    }

    /**
     * The period asked for; a missing bound comes from the current month (ASSUMPTION).
     *
     * @throws BusinessRuleException {@code DATE_RANGE_INVALID} when {@code from} is after {@code to}
     */
    Period resolve(LocalDate today) {
        YearMonth month = YearMonth.from(today);
        Period period = new Period(from == null ? month.atDay(1) : from, to == null ? month.atEndOfMonth() : to);
        if (period.from().isAfter(period.to())) {
            throw new BusinessRuleException("DATE_RANGE_INVALID", "the period starts after it ends");
        }
        return period;
    }
}
