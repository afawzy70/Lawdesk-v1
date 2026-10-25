package com.lawdesk.reports;

import static com.lawdesk.security.TestActors.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.hamcrest.Matchers;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.lawdesk.experts.ExpertReportQueries;
import com.lawdesk.platform.PostgresTestSupport;
import com.lawdesk.proceedings.ProceedingReportQueries;
import com.lawdesk.security.Permission;
import com.lawdesk.users.UserDirectory;

/**
 * QAD-039 and the audit that followed (docs/delivery/PHASE-6-ACCEPTANCE.md, LD-034 criterion 4): every export is
 * refused with 422 {@code EXPORT_TOO_LARGE} at 10 001 rows, before anything is serialised or looked up, in every format
 * and without an attachment or a success audit; exactly 10 000 rows are exported. Every on-screen report also answers
 * 200 above 10 000 rows. Rows are inserted by SQL; counts that other test classes might leave rows for (experts,
 * assignees) are topped up from a baseline so the totals are exact.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ExportLimitApiTest extends PostgresTestSupport {

    private static final int LIMIT = ReportExports.MAX_ROWS;
    private static final List<String> FORMATS = List.of("csv", "xlsx", "pdf");
    private static final LocalDate TODAY = LocalDate.of(2014, 5, 15);

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoSpyBean
    ReportCalendar calendar;

    @MockitoSpyBean
    ExpertReportQueries expertPort;

    @MockitoSpyBean
    UserDirectory userDirectory;

    @MockitoSpyBean
    ProceedingReportQueries proceedingPort;

    private ReportTestData data;
    private final UUID actor = UUID.randomUUID();

    @BeforeEach
    void pinToday() {
        doReturn(TODAY).when(calendar).today();
        data = new ReportTestData(jdbc);
    }

    @AfterEach
    void removeRows() {
        data.deleteCreated();
    }

    private MockHttpServletRequestBuilder export(String report, String format) {
        return get("/api/v1/reports/" + report + "/export").param("format", format)
                .with(as(actor, "exporter", Permission.REPORT_EXPORT));
    }

    private MockHttpServletRequestBuilder read(String report) {
        return get("/api/v1/reports/" + report).with(as(Permission.REPORT_VIEW));
    }

    private int exports() {
        return jdbc.queryForObject("select count(*) from audit_event where actor_id = ? and action = 'REPORT_EXPORT'",
                Integer.class, actor);
    }

    private int csvDataRows(MockHttpServletRequestBuilder request) throws Exception {
        byte[] bytes = mvc.perform(request).andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", Matchers.startsWith("attachment")))
                .andReturn().getResponse().getContentAsByteArray();
        String text = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        return text.split("\r\n").length - 1;
    }

    private DocumentContext body(ResultActions result) throws Exception {
        return JsonPath.parse(result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void theExpertsExportRefusesMoreThanTheLimitBeforeAnyLookupAndExportsExactlyTheLimit() throws Exception {
        int registered = jdbc.queryForObject("select count(*) from expert", Integer.class);
        data.insertBulkExperts(LIMIT - registered);

        // Exactly 10 000: every format works (CSV also counted row by row).
        assertThat(csvDataRows(export("experts", "csv"))).isEqualTo(LIMIT);
        mvc.perform(export("experts", "xlsx")).andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", Matchers.endsWith(".xlsx\"")));
        mvc.perform(export("experts", "pdf")).andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", Matchers.endsWith(".pdf\"")));
        assertThat(exports()).isEqualTo(3);

        // 10 001: refused before the name lookup, in every format.
        data.insertBulkExperts(1);
        clearInvocations(expertPort);
        for (String format : FORMATS) {
            mvc.perform(export("experts", format)).andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.rule").value("EXPORT_TOO_LARGE"))
                    .andExpect(header().doesNotExist("Content-Disposition"));
        }
        verify(expertPort, never()).experts(anyCollection());
        assertThat(exports()).as("no further success audit").isEqualTo(3);
    }

    @Test
    void theExpertsReportOnScreenWorksAboveTheLimitAndLooksNamesUpInBoundedChunks() throws Exception {
        int registered = jdbc.queryForObject("select count(*) from expert", Integer.class);
        data.insertBulkExperts(LIMIT + 1 - registered);
        clearInvocations(expertPort);

        DocumentContext report = body(mvc.perform(read("experts")));
        assertThat(report.read("$.items.length()", Integer.class)).isEqualTo(LIMIT + 1);
        List<String> names = report.read("$.items[*].name");
        assertThat(names).hasSize(LIMIT + 1).doesNotContainNull().anyMatch(name -> name.startsWith(data.marker()));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UUID>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(expertPort, atLeastOnce()).experts(ids.capture());
        assertThat(ids.getAllValues()).allSatisfy(call -> assertThat(call.size()).isLessThanOrEqualTo(1_000));
        assertThat(ids.getAllValues().stream().mapToInt(Collection::size).sum()).isEqualTo(LIMIT + 1);
    }

    @Test
    void theWorkloadExportRefusesMoreThanTheLimitBeforeAnyUserLookup() throws Exception {
        int assignees = jdbc.queryForObject(
                "select count(distinct assignee_id) from task where status in ('OPEN', 'IN_PROGRESS')", Integer.class);
        data.insertBulkAssignees(LIMIT - assignees);

        assertThat(csvDataRows(export("workload", "csv"))).isEqualTo(LIMIT);
        assertThat(exports()).isEqualTo(1);

        data.insertBulkAssignees(1);
        clearInvocations(userDirectory);
        for (String format : FORMATS) {
            mvc.perform(export("workload", format)).andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.rule").value("EXPORT_TOO_LARGE"))
                    .andExpect(header().doesNotExist("Content-Disposition"));
        }
        verify(userDirectory, never()).find(any());
        verify(userDirectory, never()).active();
        assertThat(exports()).isEqualTo(1);

        // The screen has no row limit: it answers with every assignee.
        assertThat(body(mvc.perform(read("workload"))).read("$.items.length()", Integer.class)).isEqualTo(LIMIT + 1);
    }

    @Test
    void theHearingsExportRefusesMoreThanTheLimitAndExportsExactlyTheLimit() throws Exception {
        UUID court = data.insertCourt("FIRST_INSTANCE");
        UUID caseId = data.insertCase("hearings", "CIVIL", "ACTIVE", LocalDate.of(2013, 1, 5));
        UUID proceeding = data.insertProceeding(caseId, "FIRST_INSTANCE", court, LocalDate.of(2013, 2, 1), null);
        data.insertBulkHearings(caseId, proceeding, LocalDate.of(2014, 5, 12), LIMIT);

        assertThat(csvDataRows(export("hearings", "csv"))).isEqualTo(LIMIT);
        assertThat(exports()).isEqualTo(1);

        data.insertBulkHearings(caseId, proceeding, LocalDate.of(2014, 5, 12), 1);
        for (String format : FORMATS) {
            mvc.perform(export("hearings", format)).andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.rule").value("EXPORT_TOO_LARGE"))
                    .andExpect(header().doesNotExist("Content-Disposition"));
        }
        assertThat(exports()).isEqualTo(1);

        DocumentContext report = body(mvc.perform(read("hearings").param("size", "100")));
        assertThat(report.read("$.totalItems", Integer.class)).isEqualTo(LIMIT + 1);
        assertThat(report.read("$.items.length()", Integer.class)).isEqualTo(100);
    }

    @Test
    void theJudgmentsExportRefusesMoreThanTheLimitAndExportsExactlyTheLimit() throws Exception {
        UUID court = data.insertCourt("FIRST_INSTANCE");
        UUID caseId = data.insertCase("judgments", "CIVIL", "ACTIVE", LocalDate.of(2013, 1, 5));
        data.insertBulkJudgments(caseId, court, LocalDate.of(2013, 2, 1), LocalDate.of(2014, 5, 10), LIMIT);

        assertThat(csvDataRows(export("judgments", "csv"))).isEqualTo(LIMIT);
        assertThat(exports()).isEqualTo(1);

        data.insertBulkJudgments(caseId, court, LocalDate.of(2013, 2, 1), LocalDate.of(2014, 5, 10), 1);
        for (String format : FORMATS) {
            mvc.perform(export("judgments", format)).andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.rule").value("EXPORT_TOO_LARGE"))
                    .andExpect(header().doesNotExist("Content-Disposition"));
        }
        assertThat(exports()).isEqualTo(1);
        assertThat(body(mvc.perform(read("judgments"))).read("$.totalItems", Integer.class)).isEqualTo(LIMIT + 1);
    }

    /**
     * The inventory counts the current stage of every matching case: the ids go to the proceedings port in bounded
     * chunks, and the counts of the chunks add up (no case lost or counted twice at the chunk boundary).
     */
    @Test
    void theCaseInventoryCountsStagesOfMoreThanTenThousandCasesInBoundedChunks() throws Exception {
        UUID court = data.insertCourt("FIRST_INSTANCE");
        data.insertBulkCases(LIMIT + 1);
        data.insertBulkProceedings(court, LocalDate.of(2013, 2, 1));
        clearInvocations(proceedingPort);

        DocumentContext report = body(mvc.perform(read("cases").param("q", data.marker()).param("size", "100")));
        assertThat(report.read("$.totalItems", Integer.class)).isEqualTo(LIMIT + 1);
        assertThat(report.read("$.summary.byStage.FIRST_INSTANCE", Integer.class)).isEqualTo(LIMIT + 1);
        assertThat(report.read("$.summary.byStage.NONE", Integer.class)).isZero();
        assertThat(report.read("$.items[0].currentStage", String.class)).isEqualTo("FIRST_INSTANCE");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UUID>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(proceedingPort, atLeastOnce()).currentStageCounts(ids.capture());
        assertThat(ids.getAllValues()).allSatisfy(call -> assertThat(call.size()).isLessThanOrEqualTo(LIMIT));
        assertThat(ids.getAllValues().stream().mapToInt(Collection::size).sum()).isEqualTo(LIMIT + 1);

        // A stage filter hands the cases port every matching case id as its filter; it still answers above 10 000.
        DocumentContext filtered = body(mvc.perform(read("cases").param("q", data.marker()).param("stage",
                "FIRST_INSTANCE").param("size", "100")));
        assertThat(filtered.read("$.totalItems", Integer.class)).isEqualTo(LIMIT + 1);
        assertThat(filtered.read("$.summary.total", Integer.class)).isEqualTo(LIMIT + 1);
    }

    /** Slowest answer allowed for a request whose rows come from 10 000 different cases (the slow path took minutes). */
    private static final java.time.Duration QUICK = java.time.Duration.ofSeconds(30);

    private long elapsedMillis(Runnable request) {
        long start = System.nanoTime();
        request.run();
        return (System.nanoTime() - start) / 1_000_000;
    }

    /**
     * Case references, proceedings and the cases matching a stage filter reach their ports as long lists of ids. The
     * lists are given to the database as one array parameter, and an {@code id = any(cast(? as uuid[]))} filter
     * re-evaluates that cast for every row of a sequential scan: 10 000 ids took about 105 s per query. They are now
     * read once, as a subquery. Each export and screen below spreads its rows over 10 000 different cases.
     */
    @Test
    void reportsOverTenThousandDifferentCasesAnswerQuicklyInEveryPath() throws Exception {
        UUID court = data.insertCourt("FIRST_INSTANCE");
        data.insertBulkCases(LIMIT);
        data.insertBulkProceedings(court, LocalDate.of(2013, 2, 1));
        data.insertJudgmentsOfBulkProceedings(LocalDate.of(2014, 5, 10));
        data.insertHearingsOfBulkProceedings(LocalDate.of(2014, 5, 12));

        // The case inventory with a stage filter: the matching case ids are the filter of three queries.
        long inventory = elapsedMillis(() -> {
            try {
                DocumentContext report = body(mvc.perform(read("cases").param("q", data.marker())
                        .param("stage", "FIRST_INSTANCE").param("size", "100")));
                assertThat(report.read("$.totalItems", Integer.class)).isEqualTo(LIMIT);
                assertThat(report.read("$.items[0].proceedingCount", Integer.class)).isEqualTo(1);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        assertThat(java.time.Duration.ofMillis(inventory)).as("case inventory with a stage filter").isLessThan(QUICK);

        // The judgments and hearings exports: references of 10 000 cases and 10 000 proceedings are looked up.
        for (String report : List.of("judgments", "hearings")) {
            long[] rows = new long[1];
            long took = elapsedMillis(() -> {
                try {
                    rows[0] = csvDataRows(export(report, "csv"));
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
            assertThat(rows[0]).as(report + " rows").isEqualTo(LIMIT);
            assertThat(java.time.Duration.ofMillis(took)).as(report + " export").isLessThan(QUICK);
        }
        assertThat(exports()).isEqualTo(2);
    }

    @Test
    void theCaseInventoryExportRefusesMoreThanTheLimitInEveryFormatAndStillAnswersOnScreen() throws Exception {
        data.insertBulkCases(LIMIT + 1);
        for (String format : FORMATS) {
            mvc.perform(export("cases", format).param("q", data.marker())).andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.rule").value("EXPORT_TOO_LARGE"))
                    .andExpect(header().doesNotExist("Content-Disposition"));
        }
        assertThat(exports()).isZero();

        DocumentContext report = body(mvc.perform(read("cases").param("q", data.marker()).param("size", "100")));
        assertThat(report.read("$.totalItems", Integer.class)).isEqualTo(LIMIT + 1);
        assertThat(report.read("$.summary.byStage.NONE", Integer.class)).isEqualTo(LIMIT + 1);
    }
}
