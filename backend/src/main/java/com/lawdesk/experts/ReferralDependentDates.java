package com.lawdesk.experts;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.lawdesk.proceedings.ProceedingDependentDates;

/**
 * QAD-032 for LD-025: a proceeding's filing date cannot be corrected to after one of its expert referrals. Every
 * referral counts, cancelled ones too (ASSUMPTION). Referrals lock the proceeding row before they compare their date
 * with the filing date (ReferralService), as the correction does before it asks here. Sessions and reports are bound
 * by the referral date, not the filing date, so the earliest referral bounds them all.
 */
@Component
class ReferralDependentDates implements ProceedingDependentDates {

    static final String FILING_AFTER_DEPENDENT_REFERRAL = "FILING_AFTER_DEPENDENT_REFERRAL";

    private final JdbcTemplate jdbc;

    ReferralDependentDates(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<LocalDate> earliestDependentDate(UUID proceedingId) {
        return Optional.ofNullable(jdbc.queryForObject(
                "select min(referral_date) from expert_referral where proceeding_id = ?", LocalDate.class,
                proceedingId));
    }

    @Override
    public String rule() {
        return FILING_AFTER_DEPENDENT_REFERRAL;
    }
}
