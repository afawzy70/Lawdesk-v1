package com.lawdesk.hearings;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.lawdesk.proceedings.ProceedingDependentDates;

/**
 * QAD-032: a proceeding's filing date cannot be corrected to after one of its hearings. Every hearing counts, cancelled
 * ones too (ASSUMPTION), so the record never shows a hearing before the filing. Hearing writes lock the proceeding row
 * before they compare their date with the filing date (HearingService), as the correction does before it asks here.
 */
@Component
class HearingDependentDates implements ProceedingDependentDates {

    private final JdbcTemplate jdbc;

    HearingDependentDates(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<LocalDate> earliestDependentDate(UUID proceedingId) {
        return Optional.ofNullable(jdbc.queryForObject("select min(hearing_date) from hearing where proceeding_id = ?",
                LocalDate.class, proceedingId));
    }

    @Override
    public String rule() {
        return Rules.FILING_AFTER_DEPENDENT_HEARING;
    }
}
