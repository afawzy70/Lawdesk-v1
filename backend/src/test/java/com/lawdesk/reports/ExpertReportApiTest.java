package com.lawdesk.reports;

import static com.lawdesk.security.TestActors.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.lawdesk.platform.PostgresTestSupport;
import com.lawdesk.security.Permission;

/**
 * LD-033 experts report (docs/delivery/PHASE-6-ACCEPTANCE.md), read through Workstream D's ExpertReportQueries: every
 * expert of the registry with open and overdue referrals, by name. "Overdue" uses the port's own today (the real day in
 * Egypt); totals are checked as the change from a baseline.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ExpertReportApiTest extends PostgresTestSupport {

    private static final String REPORT = "/api/v1/reports/experts";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private ReportTestData data;
    private UUID caseId;
    private UUID proceeding;

    @BeforeEach
    void caseWithProceeding() {
        data = new ReportTestData(jdbc);
        LocalDate today = LocalDate.now(ZoneId.of("Africa/Cairo"));
        caseId = data.insertCase("experts", "CIVIL", "ACTIVE", today.minusYears(1));
        UUID court = data.insertCourt("FIRST_INSTANCE");
        proceeding = data.insertProceeding(caseId, "FIRST_INSTANCE", court, today.minusYears(1), null);
    }

    @AfterEach
    void removeRows() {
        data.deleteCreated();
    }

    private DocumentContext read() throws Exception {
        return JsonPath.parse(mvc.perform(get(REPORT).with(as(Permission.REPORT_VIEW))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private static Map<String, Object> row(DocumentContext json, UUID expert) {
        List<Map<String, Object>> rows = json.read("$.items[?(@.expertId == '" + expert + "')]");
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }

    @Test
    void reportsEveryExpertWithOpenAndOverdueReferralsAndTheReferralTotals() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("Africa/Cairo"));
        DocumentContext before = read();
        UUID alia = data.insertExpert("Alia " + data.marker(), "Accounting");
        UUID basma = data.insertExpert("Basma " + data.marker(), "Engineering");
        UUID retired = data.insertExpert("Zaki " + data.marker(), "Medicine");
        jdbc.update("update expert set active = false where id = ?", retired);
        data.insertReferral(caseId, proceeding, alia, today.minusDays(40), today.minusDays(1), "OPEN");
        data.insertReferral(caseId, proceeding, alia, today.minusDays(40), today, "OPEN");
        data.insertReferral(caseId, proceeding, alia, today.minusDays(40), today.minusDays(10), "REPORT_SUBMITTED");
        data.insertReferral(caseId, proceeding, retired, today.minusDays(40), today.minusDays(20), "CANCELLED");
        DocumentContext after = read();

        assertThat(row(after, alia)).containsEntry("name", "Alia " + data.marker())
                .containsEntry("specialty", "Accounting").containsEntry("active", true).containsEntry("open", 2)
                .containsEntry("overdue", 1);
        assertThat(row(after, basma)).containsEntry("open", 0).containsEntry("overdue", 0);
        assertThat(row(after, retired)).containsEntry("active", false).containsEntry("open", 0);
        List<String> order = after.read("$.items[*].expertId");
        assertThat(order.indexOf(alia.toString())).isLessThan(order.indexOf(basma.toString()));
        assertThat(order.indexOf(basma.toString())).isLessThan(order.indexOf(retired.toString()));

        for (Map.Entry<String, Long> change : Map.of("$.summary.byStatus.OPEN", 2L,
                "$.summary.byStatus.REPORT_SUBMITTED", 1L, "$.summary.byStatus.CANCELLED", 1L,
                "$.summary.overdueReports", 1L).entrySet()) {
            assertThat(after.read(change.getKey(), Long.class) - before.read(change.getKey(), Long.class))
                    .as(change.getKey()).isEqualTo(change.getValue());
        }
    }

    @Test
    void needsASessionAndTheReportPermission() throws Exception {
        mvc.perform(get(REPORT)).andExpect(status().isUnauthorized());
        UUID actor = UUID.randomUUID();
        mvc.perform(get(REPORT).with(as(actor, "expert-desk", Permission.EXPERT_VIEW))).andExpect(status().isForbidden());
        assertThat(jdbc.queryForList("""
                select target_id from audit_event where action = 'ACCESS_DENIED' and actor_id = ?""", String.class,
                actor)).containsExactly("GET /api/v1/reports/experts");
    }
}
