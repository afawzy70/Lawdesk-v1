package com.lawdesk.experts;

import static com.lawdesk.security.TestActors.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.lawdesk.experts.ExpertReportQueries.ExpertRef;
import com.lawdesk.experts.ExpertReportQueries.ExpertWorkload;
import com.lawdesk.experts.ExpertReportQueries.ReferralFilter;
import com.lawdesk.experts.ExpertReportQueries.ReferralRow;
import com.lawdesk.security.Permission;

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
    void namesTheExpertsReportsShowInOneQuery() throws Exception {
        UUID samir = createExpert("Samir");
        UUID hoda = createExpert("Hoda");
        mvc.perform(put("/api/v1/experts/" + hoda).with(as(Permission.EXPERT_MANAGE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ExpertApiTest.edit("Hoda", "Accounting", false, 0)))
                .andExpect(status().isOk());

        Map<UUID, ExpertRef> named = reports.experts(Set.of(samir, hoda, UUID.randomUUID()));
        assertThat(named).containsOnlyKeys(samir, hoda);
        assertThat(named.get(samir)).isEqualTo(new ExpertRef(samir, "Samir", "Engineering", true));
        assertThat(named.get(hoda)).isEqualTo(new ExpertRef(hoda, "Hoda", "Accounting", false));
        assertThat(reports.experts(Set.of())).isEmpty();
        assertThatThrownBy(() -> reports.experts(Stream.generate(UUID::randomUUID).limit(10_001)
                .toList())).isInstanceOf(IllegalArgumentException.class);
        assertThat(reports.experts(Stream.generate(UUID::randomUUID).limit(10_000).toList()))
                .isEmpty();
    }

    @Test
    void refusesUnboundedQueries() {
        assertThatThrownBy(() -> reports.find(ReferralFilter.all(), 0, 10_001))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reports.find(ReferralFilter.all(), -1, 1)).isInstanceOf(IllegalArgumentException.class);
    }
}
