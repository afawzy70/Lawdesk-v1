package com.lawdesk.reports;

import static com.lawdesk.security.TestActors.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.lawdesk.platform.PostgresTestSupport;
import com.lawdesk.security.Permission;

/**
 * LD-031 case inventory report (docs/delivery/PHASE-6-ACCEPTANCE.md; QA-0241…QA-0247). Five cases with their
 * proceedings are inserted per test under a unique reference prefix; every request filters on that prefix with
 * {@code q}, so rows other test classes leave in the shared database never change the expected figures.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CaseInventoryReportApiTest extends PostgresTestSupport {

    private static final String REPORT = "/api/v1/reports/cases";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private ReportTestData data;
    private String m;
    private UUID alpha;
    private UUID epsilon;
    private UUID firstCourt;
    private UUID secondCourt;
    private UUID appealCourt;
    private UUID cassationCourt;

    /**
     * A: open civil case at appeal (court A1, 2 proceedings); B: closed family case at first instance (F1); C:
     * suspended labour case with two first-instance proceedings, F2 then F1 (current court F1); D: archived
     * administrative case without proceedings; E: active commercial case at cassation (K1, 3 proceedings).
     */
    @BeforeEach
    void fiveCases() {
        data = new ReportTestData(jdbc);
        m = data.marker();
        firstCourt = data.insertCourt("FIRST_INSTANCE");
        secondCourt = data.insertCourt("FIRST_INSTANCE");
        appealCourt = data.insertCourt("APPEAL");
        cassationCourt = data.insertCourt("CASSATION");

        alpha = data.insertCaseWithReference(m + "-A", "Alpha v. Beta", "CIVIL", "OPEN", LocalDate.of(2025, 1, 10));
        UUID a1 = data.insertProceeding(alpha, "FIRST_INSTANCE", firstCourt, LocalDate.of(2025, 2, 1), null);
        data.insertProceeding(alpha, "APPEAL", appealCourt, LocalDate.of(2025, 8, 1), a1);

        UUID gamma = data.insertCaseWithReference(m + "-B", "Gamma estate", "FAMILY", "CLOSED",
                LocalDate.of(2025, 3, 5));
        data.insertProceeding(gamma, "FIRST_INSTANCE", firstCourt, LocalDate.of(2025, 3, 20), null);

        UUID delta = data.insertCaseWithReference(m + "-C", "Delta labour", "LABOR", "SUSPENDED",
                LocalDate.of(2025, 3, 5));
        data.insertProceeding(delta, "FIRST_INSTANCE", secondCourt, LocalDate.of(2025, 4, 1), null);
        data.insertProceeding(delta, "FIRST_INSTANCE", firstCourt, LocalDate.of(2025, 5, 1), null);

        data.insertCaseWithReference(m + "-D", "Epsilon admin", "ADMINISTRATIVE", "ARCHIVED",
                LocalDate.of(2024, 12, 31));

        epsilon = data.insertCaseWithReference(m + "-E", "Zeta 100% trade", "COMMERCIAL", "ACTIVE",
                LocalDate.of(2025, 6, 30));
        UUID e1 = data.insertProceeding(epsilon, "FIRST_INSTANCE", secondCourt, LocalDate.of(2025, 7, 1), null);
        UUID e2 = data.insertProceeding(epsilon, "APPEAL", appealCourt, LocalDate.of(2025, 9, 1), e1);
        data.insertProceeding(epsilon, "CASSATION", cassationCourt, LocalDate.of(2025, 11, 1), e2);
    }

    @AfterEach
    void removeRows() {
        data.deleteCreated();
    }

    private MockHttpServletRequestBuilder report() {
        return get(REPORT).param("q", m).with(as(Permission.REPORT_VIEW));
    }

    private ResultActions references(MockHttpServletRequestBuilder request, String... suffixes) throws Exception {
        String[] expected = new String[suffixes.length];
        for (int i = 0; i < suffixes.length; i++) {
            expected[i] = m + "-" + suffixes[i];
        }
        return mvc.perform(request).andExpect(status().isOk())
                .andExpect(suffixes.length == 0 ? jsonPath("$.items", empty())
                        : jsonPath("$.items[*].referenceNo", contains(expected)))
                .andExpect(jsonPath("$.totalItems").value(suffixes.length))
                .andExpect(jsonPath("$.summary.total").value(suffixes.length));
    }

    @Test
    void listsTheMatchingCasesNewestOpenedFirstWithTheirCurrentStageAndCourt() throws Exception {
        references(report(), "E", "B", "C", "A", "D")
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.items[3].id").value(alpha.toString()))
                .andExpect(jsonPath("$.items[3].title").value("Alpha v. Beta"))
                .andExpect(jsonPath("$.items[3].caseType").value("CIVIL"))
                .andExpect(jsonPath("$.items[3].status").value("OPEN"))
                .andExpect(jsonPath("$.items[3].statusGroup").value("OPEN"))
                .andExpect(jsonPath("$.items[3].openedOn").value("2025-01-10"))
                .andExpect(jsonPath("$.items[3].currentStage").value("APPEAL"))
                .andExpect(jsonPath("$.items[3].currentCourt.id").value(appealCourt.toString()))
                .andExpect(jsonPath("$.items[3].currentCourt.code").isString())
                .andExpect(jsonPath("$.items[3].currentCourt.nameAr").isString())
                .andExpect(jsonPath("$.items[3].currentCourt.nameEn").isString())
                .andExpect(jsonPath("$.items[3].proceedingCount").value(2))
                .andExpect(jsonPath("$.items[0].currentStage").value("CASSATION"))
                .andExpect(jsonPath("$.items[0].currentCourt.id").value(cassationCourt.toString()))
                .andExpect(jsonPath("$.items[0].proceedingCount").value(3))
                .andExpect(jsonPath("$.items[2].currentStage").value("FIRST_INSTANCE"))
                .andExpect(jsonPath("$.items[2].currentCourt.id").value(firstCourt.toString()))
                .andExpect(jsonPath("$.items[2].statusGroup").value("OPEN"))
                .andExpect(jsonPath("$.items[1].statusGroup").value("CLOSED"))
                .andExpect(jsonPath("$.items[4].currentStage", nullValue()))
                .andExpect(jsonPath("$.items[4].currentCourt", nullValue()))
                .andExpect(jsonPath("$.items[4].proceedingCount").value(0))
                .andExpect(jsonPath("$.items[4].statusGroup").value("CLOSED"));
    }

    @Test
    void summarisesAllMatchingCasesByGroupStatusTypeAndCurrentStage() throws Exception {
        mvc.perform(report().param("size", "1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.summary.total").value(5)).andExpect(jsonPath("$.summary.open").value(3))
                .andExpect(jsonPath("$.summary.closed").value(2))
                .andExpect(jsonPath("$.summary.byStatus.OPEN").value(1))
                .andExpect(jsonPath("$.summary.byStatus.ACTIVE").value(1))
                .andExpect(jsonPath("$.summary.byStatus.SUSPENDED").value(1))
                .andExpect(jsonPath("$.summary.byStatus.CLOSED").value(1))
                .andExpect(jsonPath("$.summary.byStatus.ARCHIVED").value(1))
                .andExpect(jsonPath("$.summary.byType.CIVIL").value(1))
                .andExpect(jsonPath("$.summary.byType.CRIMINAL").value(0))
                .andExpect(jsonPath("$.summary.byType.ADMINISTRATIVE").value(1))
                .andExpect(jsonPath("$.summary.byStage.FIRST_INSTANCE").value(2))
                .andExpect(jsonPath("$.summary.byStage.APPEAL").value(1))
                .andExpect(jsonPath("$.summary.byStage.CASSATION").value(1))
                .andExpect(jsonPath("$.summary.byStage.NONE").value(1));
    }

    @Test
    void anEmptyResultHasZeroCounts() throws Exception {
        mvc.perform(get(REPORT).param("q", m + "-none").with(as(Permission.REPORT_VIEW))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items", empty())).andExpect(jsonPath("$.totalItems").value(0))
                .andExpect(jsonPath("$.totalPages").value(0)).andExpect(jsonPath("$.summary.total").value(0))
                .andExpect(jsonPath("$.summary.open").value(0))
                .andExpect(jsonPath("$.summary.byStage.NONE").value(0))
                .andExpect(jsonPath("$.summary.byStatus.OPEN").value(0));
    }

    @Test
    void filtersByStatusesStatusGroupAndTypes() throws Exception {
        references(report().param("status", "OPEN", "CLOSED"), "B", "A");
        references(report().param("statusGroup", "CLOSED"), "B", "D");
        references(report().param("statusGroup", "OPEN"), "E", "C", "A");
        references(report().param("statusGroup", "OPEN").param("status", "CLOSED"));
        references(report().param("statusGroup", "OPEN").param("status", "SUSPENDED", "CLOSED"), "C");
        references(report().param("caseType", "FAMILY", "LABOR"), "B", "C");
        references(report().param("caseType", "CRIMINAL"));
    }

    @Test
    void filtersByAnInclusiveOpeningPeriod() throws Exception {
        references(report().param("openedFrom", "2025-03-05").param("openedTo", "2025-03-05"), "B", "C");
        references(report().param("openedTo", "2024-12-31"), "D");
        references(report().param("openedFrom", "2025-03-06"), "E");
        references(report().param("openedFrom", "2025-01-10").param("openedTo", "2025-06-30"), "E", "B", "C", "A");
    }

    @Test
    void filtersByCurrentStageAndByAnyProceedingBeforeACourt() throws Exception {
        references(report().param("stage", "FIRST_INSTANCE"), "B", "C");
        references(report().param("stage", "APPEAL", "CASSATION"), "E", "A");
        references(report().param("courtId", secondCourt.toString()), "E", "C");
        references(report().param("courtId", firstCourt.toString()).param("stage", "FIRST_INSTANCE"), "B", "C");
        references(report().param("courtId", appealCourt.toString()).param("stage", "APPEAL"), "A");
        references(report().param("courtId", UUID.randomUUID().toString()));
        references(report().param("stage", "CASSATION").param("caseType", "CIVIL"));
    }

    @Test
    void searchesReferenceAndTitleIgnoringCaseWithLiteralWildcards() throws Exception {
        references(get(REPORT).param("q", "zeta 100%").param("openedFrom", "2025-06-30")
                .param("openedTo", "2025-06-30").with(as(Permission.REPORT_VIEW)), "E");
        references(get(REPORT).param("q", m.toLowerCase() + "-b").with(as(Permission.REPORT_VIEW)), "B");
        references(get(REPORT).param("q", m + "_A").with(as(Permission.REPORT_VIEW)));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "referenceNo,asc | A,B,C,D,E",
            "referenceNo | A,B,C,D,E",
            "referenceNo,desc | E,D,C,B,A",
            "title,desc | E,B,D,C,A",
            "status,asc | A,E,C,B,D",
            "caseType,asc | A,E,C,B,D",
            "openedOn,asc | D,A,B,C,E",
            "openedOn,desc | E,B,C,A,D"})
    void sortsByTheWhitelistedFieldsWithAStableTieBreak(String sort, String order) throws Exception {
        references(report().param("sort", sort), order.split(","));
    }

    @Test
    void pagesThroughTheResultWithoutRepeatingRows() throws Exception {
        mvc.perform(report().param("size", "2").param("page", "1"))
                .andExpect(jsonPath("$.items[*].referenceNo", contains(m + "-C", m + "-A")))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2)).andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.totalItems").value(5)).andExpect(jsonPath("$.summary.total").value(5));
        mvc.perform(report().param("size", "2").param("page", "2"))
                .andExpect(jsonPath("$.items[*].referenceNo", contains(m + "-D")));
        mvc.perform(report().param("page", "9")).andExpect(jsonPath("$.items", empty()))
                .andExpect(jsonPath("$.totalItems").value(5));
        mvc.perform(report().param("size", "100")).andExpect(jsonPath("$.size").value(100));
        mvc.perform(report().param("size", "500")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("size"));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "sort | courtName,asc | Pattern",
            "sort | title,up | Pattern",
            "sort | title;drop | Pattern",
            "page | -1 | Min",
            "size | 0 | Min",
            "page | one | Digits",
            "size | 101 | Max",
            "status | LOST | typeMismatch",
            "statusGroup | MAYBE | typeMismatch",
            "caseType | MARITIME | typeMismatch",
            "stage | FINAL | typeMismatch",
            "courtId | not-a-uuid | typeMismatch",
            "openedFrom | 2025-02-30 | typeMismatch",
            "openedTo | 10/01/2025 | typeMismatch"})
    void malformedParametersAreFieldErrors(String field, String value, String code) throws Exception {
        mvc.perform(report().param(field, value)).andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value(field))
                .andExpect(jsonPath("$.errors[0].code").value(code))
                .andExpect(jsonPath("$.errors[0].rejectedValue").doesNotExist());
    }

    @Test
    void aSearchTextOverOneHundredCharactersIsAFieldError() throws Exception {
        mvc.perform(get(REPORT).param("q", "x".repeat(101)).with(as(Permission.REPORT_VIEW)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("q"))
                .andExpect(jsonPath("$.errors[0].code").value("Size"));
        references(get(REPORT).param("q", m + "-A").with(as(Permission.REPORT_VIEW)), "A");
    }

    @Test
    void anInvertedOpeningPeriodBreaksARule() throws Exception {
        mvc.perform(report().param("openedFrom", "2025-03-06").param("openedTo", "2025-03-05"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.rule").value("DATE_RANGE_INVALID"));
    }

    @Test
    void needsASession() throws Exception {
        mvc.perform(get(REPORT)).andExpect(status().isUnauthorized());
    }

    @Test
    void needsTheReportPermissionAndAuditsTheRefusal() throws Exception {
        UUID actor = UUID.randomUUID();
        mvc.perform(get(REPORT).param("sort", "bogus").with(as(actor, "case-viewer", Permission.CASE_VIEW)))
                .andExpect(status().isForbidden());

        assertThat(jdbc.queryForList("""
                select target_id from audit_event where action = 'ACCESS_DENIED' and actor_id = ?""", String.class,
                actor)).containsExactly("GET /api/v1/reports/cases");
    }

    @Test
    void readingChangesNothingAndRepeatedReadsAgree() throws Exception {
        Integer cases = jdbc.queryForObject("select count(*) from legal_case", Integer.class);
        String first = mvc.perform(report()).andReturn().getResponse().getContentAsString();
        String second = mvc.perform(report()).andReturn().getResponse().getContentAsString();
        assertThat(second).isEqualTo(first);
        assertThat(jdbc.queryForObject("select count(*) from legal_case", Integer.class)).isEqualTo(cases);
    }
}
