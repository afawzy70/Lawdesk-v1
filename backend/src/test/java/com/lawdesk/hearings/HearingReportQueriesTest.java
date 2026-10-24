package com.lawdesk.hearings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.lawdesk.hearings.HearingReportQueries.HearingFilter;
import com.lawdesk.hearings.HearingReportQueries.HearingRow;

/** The read-only hearing port for Phase 6 reports (architecture/CONTRACTS.md §8.3, PHASE-5-ACCEPTANCE LD-024.8). */
class HearingReportQueriesTest extends HearingTestSupport {

    @Autowired
    HearingReportQueries reports;

    @Test
    void findsHearingsInADateRangeWithFiltersInCalendarOrder() throws Exception {
        UUID caseId = createCase("Report one");
        UUID proceeding = createProceeding(caseId, "300");
        UUID otherProceeding = createProceeding(createCase("Report two"), "301");
        UUID timed = schedule(proceeding, "2025-03-01", "10:00");
        UUID untimed = schedule(otherProceeding, "2025-03-01");
        UUID held = schedule(proceeding, "2025-02-01");
        mvc.perform(postResult(held, HearingApiTest.result("HELD", null, null, 0))).andExpect(status().isOk());
        schedule(proceeding, "2025-05-01");

        HearingFilter march = HearingFilter.between(LocalDate.parse("2025-02-01"), LocalDate.parse("2025-03-31"));
        assertThat(reports.find(march, 0, 100)).extracting(HearingRow::id).containsExactly(held, untimed, timed);
        assertThat(reports.count(march)).isEqualTo(3);
        assertThat(reports.find(march, 1, 1)).extracting(HearingRow::id).containsExactly(untimed);

        HearingRow row = reports.find(march, 2, 1).getFirst();
        assertThat(row.caseId()).isEqualTo(caseId);
        assertThat(row.proceedingId()).isEqualTo(proceeding);
        assertThat(row.hearingDate()).isEqualTo(LocalDate.parse("2025-03-01"));
        assertThat(row.hearingTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(row.purpose()).isEqualTo(HearingPurpose.PLEADING);
        assertThat(row.status()).isEqualTo(HearingStatus.SCHEDULED);
        assertThat(row.awaitingResult()).isTrue();

        assertThat(reports.find(march.withStatuses(Set.of(HearingStatus.HELD)), 0, 100)).extracting(HearingRow::id)
                .containsExactly(held);
        assertThat(reports.find(march.withCase(caseId), 0, 100)).extracting(HearingRow::id)
                .containsExactly(held, timed);
        assertThat(reports.count(march.withProceeding(otherProceeding))).isEqualTo(1);
    }

    @Test
    void countsByStatusWithEveryStatusPresent() throws Exception {
        UUID proceeding = createProceeding(createCase("Counts"), "310");
        UUID adjourned = schedule(proceeding, "2025-02-01");
        mvc.perform(postResult(adjourned, HearingApiTest.result("ADJOURNED", null, "2025-02-15", 0)))
                .andExpect(status().isOk());
        UUID cancelled = schedule(proceeding, "2025-02-10");
        mvc.perform(postResult(cancelled, HearingApiTest.result("CANCELLED", null, null, 0)))
                .andExpect(status().isOk());

        Map<HearingStatus, Long> counts = reports.countByStatus(
                HearingFilter.between(LocalDate.parse("2025-01-01"), LocalDate.parse("2025-12-31")));
        assertThat(counts).containsExactlyInAnyOrderEntriesOf(Map.of(HearingStatus.SCHEDULED, 1L,
                HearingStatus.HELD, 0L, HearingStatus.ADJOURNED, 1L, HearingStatus.CANCELLED, 1L));
    }

    @Test
    void upcomingAndAwaitingResultFollowTheModulesRules() throws Exception {
        UUID proceeding = createProceeding(createCase("Rules"), "320");
        LocalDate today = reports.today();
        assertThat(today).isEqualTo(today());
        schedule(proceeding, today);
        schedule(proceeding, today.plusDays(7));
        schedule(proceeding, today.plusDays(8));
        schedule(proceeding, today.minusDays(1));
        UUID held = schedule(proceeding, today.minusDays(2));
        mvc.perform(postResult(held, HearingApiTest.result("HELD", null, null, 0))).andExpect(status().isOk());

        assertThat(reports.upcomingCount(7)).isEqualTo(2);
        assertThat(reports.awaitingResultCount()).isEqualTo(1);
    }

    @Test
    void refusesUnboundedQueries() {
        HearingFilter filter = HearingFilter.between(LocalDate.parse("2025-01-01"), LocalDate.parse("2025-01-31"));
        assertThatThrownBy(() -> HearingFilter.between(null, LocalDate.now()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> reports.find(filter, 0, 10_001)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reports.find(filter, -1, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reports.upcomingCount(-1)).isInstanceOf(IllegalArgumentException.class);
    }
}
