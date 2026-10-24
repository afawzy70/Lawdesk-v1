package com.lawdesk.reports;

import static com.lawdesk.security.TestActors.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.lawdesk.platform.PostgresTestSupport;
import com.lawdesk.security.Permission;

/**
 * LD-030 dashboard figures (docs/delivery/PHASE-6-ACCEPTANCE.md; QA-0233…QA-0239). Other test classes may leave rows
 * in the shared database, so every figure is checked as the exact change from a baseline read before this test's own
 * rows were inserted. "Today" is pinned to 15 March 2031 through the report calendar.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DashboardReportApiTest extends PostgresTestSupport {

    private static final String DASHBOARD = "/api/v1/reports/dashboard";
    private static final LocalDate TODAY = LocalDate.of(2031, 3, 15);

    private static final List<String> FIGURES = List.of("$.cases.total", "$.cases.open", "$.cases.closed",
            "$.cases.newThisMonth", "$.cases.byStatus.OPEN", "$.cases.byStatus.ACTIVE", "$.cases.byStatus.SUSPENDED",
            "$.cases.byStatus.CLOSED", "$.cases.byStatus.ARCHIVED", "$.cases.byType.CIVIL", "$.cases.byType.CRIMINAL",
            "$.cases.byType.COMMERCIAL", "$.cases.byType.LABOR", "$.cases.byType.FAMILY",
            "$.cases.byType.ADMINISTRATIVE", "$.proceedings.awaitingJudgment", "$.proceedings.byStage[0].total",
            "$.proceedings.byStage[0].awaitingJudgment", "$.proceedings.byStage[1].total",
            "$.proceedings.byStage[1].awaitingJudgment", "$.proceedings.byStage[2].total",
            "$.proceedings.byStage[2].awaitingJudgment", "$.judgments.recorded", "$.judgments.final",
            "$.judgments.byOutcome.IN_FAVOUR", "$.judgments.byOutcome.AGAINST", "$.judgments.byOutcome.PARTIAL",
            "$.judgments.byOutcome.DISMISSED", "$.judgments.byOutcome.OTHER", "$.operations.hearingsThisWeek",
            "$.operations.hearingsAwaitingResult", "$.operations.expertReportsOverdue", "$.operations.overdueTasks",
            "$.operations.tasksDueSoon");

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoSpyBean
    ReportCalendar calendar;

    private ReportTestData data;

    @BeforeEach
    void pinToday() {
        doReturn(TODAY).when(calendar).today();
        data = new ReportTestData(jdbc);
    }

    @AfterEach
    void removeRows() {
        data.deleteCreated();
    }

    private static MockHttpServletRequestBuilder dashboard() {
        return get(DASHBOARD).with(as(Permission.REPORT_VIEW));
    }

    private Map<String, Integer> figures(MockHttpServletRequestBuilder request) throws Exception {
        String body = mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        DocumentContext json = JsonPath.parse(body);
        Map<String, Integer> values = new LinkedHashMap<>();
        for (String path : FIGURES) {
            values.put(path, json.read(path, Integer.class));
        }
        return values;
    }

    /** The change of every figure between two reads; figures that did not change are left out. */
    private static Map<String, Integer> delta(Map<String, Integer> before, Map<String, Integer> after) {
        Map<String, Integer> changed = new LinkedHashMap<>();
        after.forEach((path, value) -> {
            int difference = value - before.get(path);
            if (difference != 0) {
                changed.put(path, difference);
            }
        });
        return changed;
    }

    @Test
    void reportsEveryFigureInAStableShapeWithNothingPending() throws Exception {
        mvc.perform(dashboard()).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.proceedings.byStage[*].stage",
                        contains("FIRST_INSTANCE", "APPEAL", "CASSATION")))
                .andExpect(jsonPath("$.judgments.from").value("2031-03-01"))
                .andExpect(jsonPath("$.judgments.to").value("2031-03-31"))
                .andExpect(jsonPath("$.month").value("2031-03"))
                .andExpect(jsonPath("$.pending", empty()))
                .andExpect(jsonPath("$.operations.hearingsThisWeek").isNumber())
                .andExpect(jsonPath("$.operations.expertReportsOverdue").isNumber())
                .andExpect(jsonPath("$.operations.overdueTasks").isNumber());
    }

    @Test
    void countsCasesByStatusGroupStatusTypeAndOpeningMonth() throws Exception {
        Map<String, Integer> before = figures(dashboard());

        data.insertCase("first day of the month", "CIVIL", "OPEN", LocalDate.of(2031, 3, 1));
        data.insertCase("last day of the month", "CIVIL", "ACTIVE", LocalDate.of(2031, 3, 31));
        data.insertCase("day before the month", "LABOR", "SUSPENDED", LocalDate.of(2031, 2, 28));
        data.insertCase("day after the month", "FAMILY", "CLOSED", LocalDate.of(2031, 4, 1));
        data.insertCase("a year earlier", "FAMILY", "ARCHIVED", LocalDate.of(2030, 3, 15));

        assertThat(delta(before, figures(dashboard()))).containsExactlyInAnyOrderEntriesOf(Map.ofEntries(
                Map.entry("$.cases.total", 5), Map.entry("$.cases.open", 3), Map.entry("$.cases.closed", 2),
                Map.entry("$.cases.newThisMonth", 2), Map.entry("$.cases.byStatus.OPEN", 1),
                Map.entry("$.cases.byStatus.ACTIVE", 1), Map.entry("$.cases.byStatus.SUSPENDED", 1),
                Map.entry("$.cases.byStatus.CLOSED", 1), Map.entry("$.cases.byStatus.ARCHIVED", 1),
                Map.entry("$.cases.byType.CIVIL", 2), Map.entry("$.cases.byType.LABOR", 1),
                Map.entry("$.cases.byType.FAMILY", 2)));
    }

    @Test
    void countsProceedingsByStageAndThoseStillAwaitingAJudgment() throws Exception {
        Map<String, Integer> before = figures(dashboard());

        UUID caseId = data.insertCase("ladder", "COMMERCIAL", "ACTIVE", LocalDate.of(2029, 1, 10));
        UUID first = data.insertCourt("FIRST_INSTANCE");
        UUID appeal = data.insertCourt("APPEAL");
        UUID cassation = data.insertCourt("CASSATION");
        UUID decided = data.insertProceeding(caseId, "FIRST_INSTANCE", first, LocalDate.of(2029, 2, 1), null);
        data.insertJudgment(decided, LocalDate.of(2030, 1, 5), "AGAINST", false);
        data.insertProceeding(caseId, "FIRST_INSTANCE", first, LocalDate.of(2029, 3, 1), null);
        UUID appealed = data.insertProceeding(caseId, "APPEAL", appeal, LocalDate.of(2030, 2, 1), decided);
        data.insertJudgment(appealed, LocalDate.of(2030, 9, 1), "IN_FAVOUR", false);
        data.insertProceeding(caseId, "CASSATION", cassation, LocalDate.of(2030, 10, 1), appealed);

        assertThat(delta(before, figures(dashboard()))).containsExactlyInAnyOrderEntriesOf(Map.ofEntries(
                Map.entry("$.cases.total", 1), Map.entry("$.cases.open", 1), Map.entry("$.cases.byStatus.ACTIVE", 1),
                Map.entry("$.cases.byType.COMMERCIAL", 1), Map.entry("$.proceedings.awaitingJudgment", 2),
                Map.entry("$.proceedings.byStage[0].total", 2), Map.entry("$.proceedings.byStage[0].awaitingJudgment", 1),
                Map.entry("$.proceedings.byStage[1].total", 1), Map.entry("$.proceedings.byStage[2].total", 1),
                Map.entry("$.proceedings.byStage[2].awaitingJudgment", 1)));
    }

    @Test
    void countsJudgmentsOfTheCurrentMonthByDefaultWithItsBoundaries() throws Exception {
        UUID caseId = data.insertCase("judgments", "CIVIL", "ACTIVE", LocalDate.of(2030, 1, 10));
        UUID court = data.insertCourt("FIRST_INSTANCE");
        Map<String, Integer> before = figures(dashboard());

        judgment(caseId, court, LocalDate.of(2031, 3, 1), "IN_FAVOUR", true);
        judgment(caseId, court, LocalDate.of(2031, 3, 31), "PARTIAL", false);
        judgment(caseId, court, LocalDate.of(2031, 3, 15), "PARTIAL", true);
        judgment(caseId, court, LocalDate.of(2031, 2, 28), "AGAINST", true);
        judgment(caseId, court, LocalDate.of(2031, 4, 1), "DISMISSED", false);

        assertThat(delta(before, figures(dashboard()))).containsExactlyInAnyOrderEntriesOf(Map.ofEntries(
                Map.entry("$.proceedings.byStage[0].total", 5), Map.entry("$.judgments.recorded", 3),
                Map.entry("$.judgments.final", 2), Map.entry("$.judgments.byOutcome.IN_FAVOUR", 1),
                Map.entry("$.judgments.byOutcome.PARTIAL", 2)));
    }

    @Test
    void countsJudgmentsOfAChosenPeriodIncludingBothEnds() throws Exception {
        UUID caseId = data.insertCase("period", "CIVIL", "ACTIVE", LocalDate.of(2020, 1, 10));
        UUID court = data.insertCourt("FIRST_INSTANCE");
        MockHttpServletRequestBuilder period = dashboard().param("from", "2021-06-10").param("to", "2021-06-12");
        MockHttpServletRequestBuilder oneDay = dashboard().param("from", "2021-06-12").param("to", "2021-06-12");
        Map<String, Integer> before = figures(period);
        Map<String, Integer> beforeOneDay = figures(oneDay);

        judgment(caseId, court, LocalDate.of(2021, 6, 9), "OTHER", true);
        judgment(caseId, court, LocalDate.of(2021, 6, 10), "OTHER", false);
        judgment(caseId, court, LocalDate.of(2021, 6, 12), "DISMISSED", true);
        judgment(caseId, court, LocalDate.of(2021, 6, 13), "DISMISSED", true);

        mvc.perform(period).andExpect(jsonPath("$.judgments.from").value("2021-06-10"))
                .andExpect(jsonPath("$.judgments.to").value("2021-06-12"));
        assertThat(delta(before, figures(period))).containsExactlyInAnyOrderEntriesOf(Map.ofEntries(
                Map.entry("$.proceedings.byStage[0].total", 4), Map.entry("$.judgments.recorded", 2),
                Map.entry("$.judgments.final", 1), Map.entry("$.judgments.byOutcome.OTHER", 1),
                Map.entry("$.judgments.byOutcome.DISMISSED", 1)));
        assertThat(delta(beforeOneDay, figures(oneDay))).containsExactlyInAnyOrderEntriesOf(Map.ofEntries(
                Map.entry("$.proceedings.byStage[0].total", 4), Map.entry("$.judgments.recorded", 1),
                Map.entry("$.judgments.final", 1), Map.entry("$.judgments.byOutcome.DISMISSED", 1)));
    }

    /**
     * The legal-operations figures come from the Phase 5 ports, whose "today" is the real calendar day in Egypt (each
     * port's own clock), so these rows are dated relative to it.
     */
    @Test
    void countsUpcomingHearingsOverdueExpertReportsAndOverdueTasksFromThePhase5Ports() throws Exception {
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Africa/Cairo"));
        UUID caseId = data.insertCase("operations", "CIVIL", "ACTIVE", today.minusYears(1));
        UUID court = data.insertCourt("FIRST_INSTANCE");
        UUID proceeding = data.insertProceeding(caseId, "FIRST_INSTANCE", court, today.minusYears(1), null);
        UUID other = data.insertProceeding(caseId, "FIRST_INSTANCE", court, today.minusYears(1), null);
        UUID expert = data.insertExpert("Expert " + data.marker(), "Accounting");
        UUID assignee = UUID.randomUUID();
        Map<String, Integer> before = figures(dashboard());

        data.insertHearing(caseId, proceeding, today, "SCHEDULED");
        data.insertHearing(caseId, proceeding, today.plusDays(6), "SCHEDULED");
        data.insertHearing(caseId, proceeding, today.plusDays(7), "SCHEDULED");
        data.insertHearing(caseId, proceeding, today.minusDays(1), "SCHEDULED");
        data.insertHearing(caseId, other, today.plusDays(1), "CANCELLED");
        data.insertHearing(caseId, proceeding, today.minusDays(2), "HELD");
        data.insertReferral(caseId, proceeding, expert, today.minusDays(30), today.minusDays(1), "OPEN");
        data.insertReferral(caseId, proceeding, expert, today.minusDays(30), today, "OPEN");
        data.insertReferral(caseId, proceeding, expert, today.minusDays(30), today.minusDays(5), "REPORT_SUBMITTED");
        data.insertTask(caseId, assignee, "overdue", today.minusDays(1), "OPEN");
        data.insertTask(caseId, assignee, "overdue in progress", today.minusDays(5), "IN_PROGRESS");
        data.insertTask(caseId, assignee, "due today", today, "OPEN");
        data.insertTask(caseId, assignee, "due in three days", today.plusDays(3), "OPEN");
        data.insertTask(caseId, assignee, "due in four days", today.plusDays(4), "OPEN");
        data.insertTask(caseId, assignee, "done late", today.minusDays(1), "DONE");

        Map<String, Integer> changed = delta(before, figures(dashboard()));
        assertThat(changed).containsAllEntriesOf(Map.of("$.operations.hearingsThisWeek", 2,
                "$.operations.hearingsAwaitingResult", 1, "$.operations.expertReportsOverdue", 1,
                "$.operations.overdueTasks", 2, "$.operations.tasksDueSoon", 2));
    }

    @Test
    void anEmptyPeriodCountsNothing() throws Exception {
        mvc.perform(dashboard().param("from", "1901-01-01").param("to", "1901-12-31")).andExpect(status().isOk())
                .andExpect(jsonPath("$.judgments.recorded").value(0)).andExpect(jsonPath("$.judgments.final").value(0))
                .andExpect(jsonPath("$.judgments.byOutcome.IN_FAVOUR").value(0));
    }

    @Test
    void oneBoundAloneKeepsTheOtherFromTheCurrentMonth() throws Exception {
        mvc.perform(dashboard().param("from", "2031-02-01")).andExpect(jsonPath("$.judgments.from").value("2031-02-01"))
                .andExpect(jsonPath("$.judgments.to").value("2031-03-31"));
        mvc.perform(dashboard().param("to", "2031-03-10")).andExpect(jsonPath("$.judgments.from").value("2031-03-01"))
                .andExpect(jsonPath("$.judgments.to").value("2031-03-10"));
    }

    @Test
    void malformedDatesAreFieldErrors() throws Exception {
        mvc.perform(dashboard().param("from", "15/03/2031")).andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value("from"))
                .andExpect(jsonPath("$.errors[0].code").value("typeMismatch"));
        mvc.perform(dashboard().param("to", "2031-02-30")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("to"));
    }

    @Test
    void anInvertedPeriodBreaksARule() throws Exception {
        mvc.perform(dashboard().param("from", "2031-03-02").param("to", "2031-03-01"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.rule").value("DATE_RANGE_INVALID"));
    }

    @Test
    void needsASession() throws Exception {
        mvc.perform(get(DASHBOARD)).andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void needsTheReportPermissionAndAuditsTheRefusal() throws Exception {
        UUID actor = UUID.randomUUID();
        mvc.perform(get(DASHBOARD).param("from", "2031-01-01")
                        .with(as(actor, "no-reports", Permission.CASE_VIEW, Permission.JUDGMENT_VIEW,
                                Permission.REPORT_EXPORT)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        assertThat(jdbc.queryForList("""
                select target_type || ' ' || target_id || ' ' || outcome from audit_event
                where action = 'ACCESS_DENIED' and actor_id = ?""", String.class, actor))
                .containsExactly("HTTP GET /api/v1/reports/dashboard DENIED");
    }

    @Test
    void readingChangesNothingAndRepeatedReadsAgree() throws Exception {
        UUID caseId = data.insertCase("repeat", "CRIMINAL", "OPEN", LocalDate.of(2031, 3, 2));
        UUID court = data.insertCourt("FIRST_INSTANCE");
        judgment(caseId, court, LocalDate.of(2031, 3, 3), "OTHER", false);
        List<Integer> rows = rowCounts();

        Map<String, Integer> first = figures(dashboard());
        Map<String, Integer> second = figures(dashboard());

        assertThat(second).isEqualTo(first);
        assertThat(rowCounts()).isEqualTo(rows);
    }

    private void judgment(UUID caseId, UUID court, LocalDate date, String outcome, boolean isFinal) {
        UUID proceeding = data.insertProceeding(caseId, "FIRST_INSTANCE", court, date.minusDays(30), null);
        data.insertJudgment(proceeding, date, outcome, isFinal);
    }

    private List<Integer> rowCounts() {
        return List.of("legal_case", "proceeding", "judgment", "court", "case_event").stream()
                .map(table -> jdbc.queryForObject("select count(*) from " + table, Integer.class)).toList();
    }
}
