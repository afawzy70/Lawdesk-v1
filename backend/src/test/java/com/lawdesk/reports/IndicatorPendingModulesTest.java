package com.lawdesk.reports;

import static com.lawdesk.security.TestActors.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.lawdesk.platform.PostgresTestSupport;
import com.lawdesk.security.Permission;

/**
 * Production defect after LD-058 (#79): the indicators that read the deadline and enforcement tables answered 500 on UAT, and
 * took the whole dashboard down with them. The tables hold rows of every shape the modules allow (no due date, a text source,
 * completed and executed records), and a failing indicator must be reported as unavailable, never as a failed dashboard.
 */
@SpringBootTest
@AutoConfigureMockMvc
class IndicatorPendingModulesTest extends PostgresTestSupport {

    private static final LocalDate TODAY = LocalDate.now(ReportCalendar.ZONE);

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoSpyBean
    IndicatorService service;

    private ReportTestData data;
    private UUID visible;
    private UUID judgment;
    private final UUID creator = ReportTestData.CREATOR;

    @BeforeEach
    void setUp() {
        data = new ReportTestData(jdbc);
        UUID court = data.insertCourt("FIRST_INSTANCE");
        visible = data.insertCase("with deadlines", "CIVIL", "ACTIVE", TODAY.minusDays(30));
        judgment = data.insertJudgment(data.insertProceeding(visible, "FIRST_INSTANCE", court, TODAY.minusDays(29), null),
                TODAY.minusDays(1), "AGAINST", false);
    }

    @AfterEach
    void clean() {
        org.mockito.Mockito.reset(service);
        data.deleteCreated();
    }

    private UUID deadline(String sourceType, UUID sourceId, String sourceText, LocalDate due, Integer period, String status) {
        return data.track("deadline", jdbc.queryForObject("""
                insert into deadline (case_id, source_type, source_id, source_text, due_on, period_days, action, status, completed_on,
                    calc_status, created_by, updated_by) values (?, ?, ?, ?, ?, ?, 'appeal', ?, ?, 'MANUAL', ?, ?) returning id""",
                UUID.class, visible, sourceType, sourceId, sourceText, due, period, status,
                "COMPLETED".equals(status) ? TODAY : null, creator, creator));
    }

    private void file(UUID forJudgment, String status, LocalDate due, String amount) {
        data.track("enforcement_file", jdbc.queryForObject("""
                insert into enforcement_file (judgment_id, case_id, status, due_on, amount_required, completed_on, created_by, updated_by)
                values (?, ?, ?, ?, cast(? as numeric), ?, ?, ?) returning id""", UUID.class, forJudgment, visible, status, due, amount,
                "EXECUTED".equals(status) ? TODAY : null, creator, creator));
    }

    private DocumentContext json(String url) throws Exception {
        return JsonPath.parse(mvc.perform(get(url).with(as(Permission.REPORT_VIEW, Permission.REPORT_EXPORT)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private long count(DocumentContext figures, String code) {
        return ((Number) figures.<List<Object>>read("$[?(@.code == '" + code + "')].count").get(0)).longValue();
    }

    @Test
    void deadlinesAndEnforcementOfEveryShapeAreCountedAndListed() throws Exception {
        long appealBefore = count(json("/api/v1/reports/indicators"), "APPEAL_DEADLINES");
        long enforcedBefore = count(json("/api/v1/reports/indicators"), "JUDGMENTS_IN_ENFORCEMENT");
        UUID court = data.insertCourt("FIRST_INSTANCE");
        deadline("JUDGMENT", judgment, null, TODAY.plusDays(20), null, "OPEN");
        deadline("JUDGMENT", judgment, null, null, 30, "OPEN");
        deadline("TEXT", null, "a court order", TODAY.plusDays(5), null, "OPEN");
        deadline("JUDGMENT", judgment, null, TODAY.plusDays(2), null, "COMPLETED");
        file(judgment, "IN_PROGRESS", null, null);
        UUID second = data.insertJudgment(data.insertProceeding(visible, "FIRST_INSTANCE", court, TODAY.minusDays(28), null),
                TODAY.minusDays(1), "PARTIAL", false);
        file(second, "PARTIALLY_EXECUTED", TODAY.plusDays(9), "1500.500");
        UUID third = data.insertJudgment(data.insertProceeding(visible, "FIRST_INSTANCE", court, TODAY.minusDays(27), null),
                TODAY.minusDays(1), "PARTIAL", false);
        file(third, "EXECUTED", null, "10.000");

        DocumentContext figures = json("/api/v1/reports/indicators");
        assertThat(count(figures, "APPEAL_DEADLINES") - appealBefore).isEqualTo(2);
        assertThat(count(figures, "JUDGMENTS_IN_ENFORCEMENT") - enforcedBefore).isEqualTo(2);
        for (String code : List.of("APPEAL_DEADLINES", "JUDGMENTS_IN_ENFORCEMENT")) {
            DocumentContext list = json("/api/v1/reports/indicators/" + code + "?size=100&lang=ar");
            assertThat(list.<Number>read("$.totalItems").longValue()).isEqualTo(count(figures, code));
            assertThat(list.<List<Object>>read("$.items")).hasSize((int) count(figures, code));
            json("/api/v1/reports/indicators/" + code + "/export?format=csv");
        }
        mvc.perform(get("/api/v1/reports/dashboard").with(as(Permission.REPORT_VIEW))).andExpect(status().isOk());
    }

    /**
     * The cause of the UAT defect: the deployed role has its own schema as search path and no rights on {@code public}
     * (deploy/postgres/app-role.sql); resolving {@code public.deadline} then fails with "permission denied" where it should answer.
     */
    @Test
    void theTableLookupWorksForTheRestrictedRoleDeploymentsUse() throws Exception {
        String url;
        try (java.sql.Connection connection = jdbc.getDataSource().getConnection()) {
            url = connection.getMetaData().getURL();
        }
        jdbc.execute("create role wp8_probe login password 'probe'");
        try {
            jdbc.execute("create schema wp8probe authorization wp8_probe");
            jdbc.execute("create table wp8probe.deadline (id int)");
            jdbc.execute("alter table wp8probe.deadline owner to wp8_probe");
            jdbc.execute("alter role wp8_probe set search_path = wp8probe");
            jdbc.execute("revoke all on schema public from public");
            org.springframework.jdbc.core.simple.JdbcClient restricted = org.springframework.jdbc.core.simple.JdbcClient.create(
                    new org.springframework.jdbc.datasource.DriverManagerDataSource(url, "wp8_probe", "probe"));
            assertThatThrownBy(() -> restricted.sql("select to_regclass('public.deadline') is not null").query(Boolean.class).single())
                    .rootCause().hasMessageContaining("permission denied");
            IndicatorService probe = new IndicatorService(null, null, null, null, null, null, restricted, null, null);
            assertThat(probe.tableExists("deadline")).isTrue();
            assertThat(probe.tableExists("enforcement_file")).isFalse();
        } finally {
            jdbc.execute("grant usage on schema public to public");
            jdbc.execute("drop owned by wp8_probe");
            jdbc.execute("drop role wp8_probe");
        }
    }

    /** A statement that fails in PostgreSQL aborts the transaction; the indicators after it must still be answered. */
    @Test
    void aFailedStatementDoesNotTakeTheIndicatorsAfterItDown() throws Exception {
        org.mockito.Mockito.doAnswer(invocation -> jdbc.queryForObject("select 1 / 0", Integer.class)).when(service)
                .source(org.mockito.ArgumentMatchers.eq(IndicatorCode.EXPERT_HEARINGS), org.mockito.ArgumentMatchers.any());
        DocumentContext figures = json("/api/v1/reports/indicators");
        assertThat(figures.<List<String>>read("$[?(@.code == 'EXPERT_HEARINGS')].unavailableReason")).containsExactly("INDICATOR_FAILED");
        for (String code : List.of("HIGH_RISK_CASES", "OVERDUE_TASKS", "OVERDUE_INFO_REQUESTS", "LINKED_CASES", "CASES_BY_MEMBER")) {
            assertThat(figures.<List<Boolean>>read("$[?(@.code == '" + code + "')].available")).as(code).containsExactly(true);
        }
        mvc.perform(get("/api/v1/reports/dashboard").with(as(Permission.REPORT_VIEW))).andExpect(status().isOk());
    }

    @Test
    void aFailingIndicatorIsReportedUnavailableAndTheDashboardStillAnswers() throws Exception {
        org.mockito.Mockito.doThrow(new IllegalStateException("boom")).when(service)
                .source(org.mockito.ArgumentMatchers.eq(IndicatorCode.APPEAL_DEADLINES), org.mockito.ArgumentMatchers.any());
        DocumentContext figures = json("/api/v1/reports/indicators");
        List<Map<String, Object>> failed = figures.read("$[?(@.code == 'APPEAL_DEADLINES')]");
        assertThat(failed).hasSize(1);
        assertThat(failed.get(0)).containsEntry("available", false).containsEntry("unavailableReason", "INDICATOR_FAILED")
                .containsEntry("count", null);
        assertThat(figures.<List<Object>>read("$")).hasSize(16);
        assertThat(count(figures, "TOTAL_CASES")).isPositive();
        mvc.perform(get("/api/v1/reports/dashboard").with(as(Permission.REPORT_VIEW))).andExpect(status().isOk());
        DocumentContext list = json("/api/v1/reports/indicators/APPEAL_DEADLINES");
        assertThat(list.<Boolean>read("$.available")).isFalse();
        assertThat(list.<String>read("$.unavailableReason")).isEqualTo("INDICATOR_FAILED");
    }
}
