package com.lawdesk.experts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.lawdesk.experts.ExpertReportQueries.ExpertWorkload;
import com.lawdesk.experts.ExpertReportQueries.ReferralFilter;
import com.lawdesk.experts.ExpertReportQueries.ReferralRow;

/** The read-only expert port for Phase 6 reports (architecture/CONTRACTS.md §8.3, PHASE-5-ACCEPTANCE LD-025.5). */
class ExpertReportQueriesTest extends ExpertTestSupport {

    @Autowired
    ExpertReportQueries reports;

    @Test
    void findsReferralsByExpertCaseAndStatusWithTheOverdueRule() throws Exception {
        UUID caseId = createCase("Report");
        UUID proceeding = createProceeding(caseId, "600");
        UUID otherProceeding = createProceeding(createCase("Other report"), "601");
        UUID samir = createExpert("Samir");
        UUID hoda = createExpert("Hoda");
        UUID late = refer(proceeding, samir, "2025-02-01", today().minusDays(1));
        UUID onTime = refer(otherProceeding, samir, "2025-02-02", today());
        UUID submitted = refer(proceeding, hoda, "2025-02-03", "2025-02-10");
        mvc.perform(action(submitted, "report", "{\"reportSubmittedDate\":\"2025-02-09\",\"version\":0}"))
                .andExpect(status().isOk());

        assertThat(reports.find(ReferralFilter.all(), 0, 100)).extracting(ReferralRow::id)
                .containsExactly(late, onTime, submitted);
        ReferralRow first = reports.find(ReferralFilter.all(), 0, 1).getFirst();
        assertThat(first.caseId()).isEqualTo(caseId);
        assertThat(first.proceedingId()).isEqualTo(proceeding);
        assertThat(first.expertId()).isEqualTo(samir);
        assertThat(first.status()).isEqualTo(ReferralStatus.OPEN);
        assertThat(first.reportOverdue()).isTrue();
        assertThat(reports.find(ReferralFilter.all(), 1, 1).getFirst().reportOverdue()).isFalse();

        assertThat(reports.count(ReferralFilter.all().withExpert(samir))).isEqualTo(2);
        assertThat(reports.find(ReferralFilter.all().withCase(caseId), 0, 100)).extracting(ReferralRow::id)
                .containsExactly(late, submitted);
        assertThat(reports.find(ReferralFilter.all().withStatuses(Set.of(ReferralStatus.REPORT_SUBMITTED)), 0, 100))
                .extracting(ReferralRow::id).containsExactly(submitted);
        assertThat(reports.find(ReferralFilter.all().overdueOnly(), 0, 100)).extracting(ReferralRow::id)
                .containsExactly(late);
        assertThat(reports.overdueReportCount()).isEqualTo(1);
        assertThat(reports.countByStatus(ReferralFilter.all())).containsExactlyInAnyOrderEntriesOf(Map.of(
                ReferralStatus.OPEN, 2L, ReferralStatus.REPORT_SUBMITTED, 1L, ReferralStatus.CANCELLED, 0L));
        assertThat(reports.workload()).containsExactlyInAnyOrder(new ExpertWorkload(samir, 2, 1),
                new ExpertWorkload(hoda, 0, 0));
        assertThat(reports.today()).isEqualTo(today());
    }

    @Test
    void refusesUnboundedQueries() {
        assertThatThrownBy(() -> reports.find(ReferralFilter.all(), 0, 10_001))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reports.find(ReferralFilter.all(), -1, 1)).isInstanceOf(IllegalArgumentException.class);
    }
}
