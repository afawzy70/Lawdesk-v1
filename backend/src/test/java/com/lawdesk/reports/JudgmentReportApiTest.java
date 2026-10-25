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
import java.util.Map;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.lawdesk.platform.PostgresTestSupport;
import com.lawdesk.security.Permission;

/**
 * LD-032 judgments report (docs/delivery/PHASE-6-ACCEPTANCE.md; QA-0249…QA-0255). Judgments are inserted in 2012,
 * a period no other test class writes, and "today" is pinned to 15 May 2012, so the default period is May 2012.
 */
@SpringBootTest
@AutoConfigureMockMvc
class JudgmentReportApiTest extends PostgresTestSupport {

    private static final String REPORT = "/api/v1/reports/judgments";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoSpyBean
    ReportCalendar calendar;

    private ReportTestData data;
    private String m;
    private UUID firstCourt;
    private UUID appealCourt;
    private UUID xavier;
    private Map<String, String> j;

    /**
     * Case X: J1 first instance (F1) 1 May, in favour; J2 its appeal (A1) 31 May, against, final; J5 cassation (K1)
     * 1 June, other. Case Y: J3 first instance (F1) 15 May, partial; J4 first instance (F1) 30 April, dismissed, final.
     */
    @BeforeEach
    void judgments() {
        doReturn(LocalDate.of(2012, 5, 15)).when(calendar).today();
        data = new ReportTestData(jdbc);
        m = data.marker();
        firstCourt = data.insertCourt("FIRST_INSTANCE");
        appealCourt = data.insertCourt("APPEAL");
        UUID cassationCourt = data.insertCourt("CASSATION");
        xavier = data.insertCaseWithReference(m + "-X", "Xavier v. Nile", "CIVIL", "ACTIVE", LocalDate.of(2011, 1, 3));
        UUID yusuf = data.insertCaseWithReference(m + "-Y", "Yusuf estate", "FAMILY", "OPEN", LocalDate.of(2011, 2, 3));

        UUID p1 = data.insertProceeding(xavier, "FIRST_INSTANCE", firstCourt, LocalDate.of(2011, 3, 1), null);
        UUID j1 = data.insertJudgment(p1, LocalDate.of(2012, 5, 1), "IN_FAVOUR", false);
        UUID p2 = data.insertProceeding(xavier, "APPEAL", appealCourt, LocalDate.of(2012, 5, 2), p1);
        UUID j2 = data.insertJudgment(p2, LocalDate.of(2012, 5, 31), "AGAINST", true);
        UUID p5 = data.insertProceeding(xavier, "CASSATION", cassationCourt, LocalDate.of(2012, 5, 31), p2);
        UUID j5 = data.insertJudgment(p5, LocalDate.of(2012, 6, 1), "OTHER", false);
        UUID p3 = data.insertProceeding(yusuf, "FIRST_INSTANCE", firstCourt, LocalDate.of(2011, 4, 1), null);
        UUID j3 = data.insertJudgment(p3, LocalDate.of(2012, 5, 15), "PARTIAL", false);
        UUID p4 = data.insertProceeding(yusuf, "FIRST_INSTANCE", firstCourt, LocalDate.of(2011, 5, 1), null);
        UUID j4 = data.insertJudgment(p4, LocalDate.of(2012, 4, 30), "DISMISSED", true);
        j = Map.of("J1", j1.toString(), "J2", j2.toString(), "J3", j3.toString(), "J4", j4.toString(), "J5",
                j5.toString());
    }

    @AfterEach
    void removeRows() {
        data.deleteCreated();
    }

    private static MockHttpServletRequestBuilder report() {
        return get(REPORT).with(as(Permission.REPORT_VIEW));
    }

    private void expect(MockHttpServletRequestBuilder request, String... judgments) throws Exception {
        String[] ids = new String[judgments.length];
        for (int i = 0; i < judgments.length; i++) {
            ids[i] = j.get(judgments[i]);
        }
        mvc.perform(request).andExpect(status().isOk())
                .andExpect(ids.length == 0 ? jsonPath("$.items", empty()) : jsonPath("$.items[*].id", contains(ids)))
                .andExpect(jsonPath("$.totalItems").value(ids.length))
                .andExpect(jsonPath("$.summary.total").value(ids.length));
    }

    @Test
    void listsTheJudgmentsOfTheCurrentMonthNewestFirstWithCaseAndCourt() throws Exception {
        expect(report(), "J2", "J3", "J1");
        mvc.perform(report()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.from").value("2012-05-01")).andExpect(jsonPath("$.to").value("2012-05-31"))
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.items[0].judgmentDate").value("2012-05-31"))
                .andExpect(jsonPath("$.items[0].outcome").value("AGAINST"))
                .andExpect(jsonPath("$.items[0].final").value(true))
                .andExpect(jsonPath("$.items[0].stage").value("APPEAL"))
                .andExpect(jsonPath("$.items[0].court.id").value(appealCourt.toString()))
                .andExpect(jsonPath("$.items[0].court.nameEn").isString())
                .andExpect(jsonPath("$.items[0].court.nameAr").isString())
                .andExpect(jsonPath("$.items[0].courtCaseNumber").isString())
                .andExpect(jsonPath("$.items[0].courtCaseYear").value(2012))
                .andExpect(jsonPath("$.items[0].caseId").value(xavier.toString()))
                .andExpect(jsonPath("$.items[0].caseReferenceNo").value(m + "-X"))
                .andExpect(jsonPath("$.items[0].caseTitle").value("Xavier v. Nile"))
                .andExpect(jsonPath("$.items[1].final").value(false))
                .andExpect(jsonPath("$.items[1].caseReferenceNo").value(m + "-Y"));
    }

    @Test
    void summarisesEveryMatchingJudgmentByOutcomeStageAndFinality() throws Exception {
        mvc.perform(report().param("size", "1")).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.summary.total").value(3)).andExpect(jsonPath("$.summary.final").value(1))
                .andExpect(jsonPath("$.summary.byOutcome.IN_FAVOUR").value(1))
                .andExpect(jsonPath("$.summary.byOutcome.AGAINST").value(1))
                .andExpect(jsonPath("$.summary.byOutcome.PARTIAL").value(1))
                .andExpect(jsonPath("$.summary.byOutcome.DISMISSED").value(0))
                .andExpect(jsonPath("$.summary.byOutcome.OTHER").value(0))
                .andExpect(jsonPath("$.summary.byStage.FIRST_INSTANCE").value(2))
                .andExpect(jsonPath("$.summary.byStage.APPEAL").value(1))
                .andExpect(jsonPath("$.summary.byStage.CASSATION").value(0));
    }

    @Test
    void filtersByAnInclusivePeriod() throws Exception {
        expect(report().param("from", "2012-04-30").param("to", "2012-06-01"), "J5", "J2", "J3", "J1", "J4");
        expect(report().param("from", "2012-05-15").param("to", "2012-05-15"), "J3");
        expect(report().param("from", "2012-05-02").param("to", "2012-05-14"));
        expect(report().param("from", "2012-05-20"), "J2");
        expect(report().param("to", "2012-05-10"), "J1");
    }

    @Test
    void filtersByOutcomeStageCourtAndFinality() throws Exception {
        expect(report().param("outcome", "AGAINST", "PARTIAL"), "J2", "J3");
        expect(report().param("stage", "APPEAL"), "J2");
        expect(report().param("stage", "CASSATION"));
        expect(report().param("courtId", firstCourt.toString()), "J3", "J1");
        expect(report().param("courtId", UUID.randomUUID().toString()));
        expect(report().param("final", "true"), "J2");
        expect(report().param("final", "false"), "J3", "J1");
        expect(report().param("final", "false").param("outcome", "IN_FAVOUR").param("stage", "FIRST_INSTANCE"), "J1");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "judgmentDate,asc | J1,J3,J2",
            "judgmentDate | J1,J3,J2",
            "judgmentDate,desc | J2,J3,J1",
            "outcome,asc | J1,J2,J3",
            "outcome,desc | J3,J2,J1",
            "stage,desc | J2,J3,J1",
            "stage,asc | J3,J1,J2"})
    void sortsByTheWhitelistedFieldsWithTheNewestFirstOnTies(String sort, String order) throws Exception {
        expect(report().param("sort", sort), order.split(","));
    }

    @Test
    void pagesThroughTheResult() throws Exception {
        mvc.perform(report().param("size", "2").param("page", "1"))
                .andExpect(jsonPath("$.items[*].id", contains(j.get("J1")))).andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.summary.total").value(3));
        mvc.perform(report().param("size", "1000")).andExpect(jsonPath("$.size").value(100));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "sort | caseTitle,asc | Pattern",
            "sort | judgmentDate,sideways | Pattern",
            "page | -1 | Min",
            "size | 0 | Min",
            "outcome | WIN | typeMismatch",
            "stage | SUPREME | typeMismatch",
            "final | maybe | typeMismatch",
            "courtId | 42 | typeMismatch",
            "from | 2012-13-01 | typeMismatch",
            "to | 31/05/2012 | typeMismatch"})
    void malformedParametersAreFieldErrors(String field, String value, String code) throws Exception {
        mvc.perform(report().param(field, value)).andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value(field))
                .andExpect(jsonPath("$.errors[0].code").value(code));
    }

    @Test
    void anInvertedPeriodBreaksARule() throws Exception {
        mvc.perform(report().param("from", "2012-05-02").param("to", "2012-05-01"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.rule").value("DATE_RANGE_INVALID"));
    }

    @Test
    void needsASession() throws Exception {
        mvc.perform(get(REPORT)).andExpect(status().isUnauthorized());
    }

    @Test
    void needsTheReportPermissionAndAuditsTheRefusal() throws Exception {
        UUID actor = UUID.randomUUID();
        mvc.perform(get(REPORT).with(as(actor, "judgment-viewer", Permission.JUDGMENT_VIEW)))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForList("""
                select target_id from audit_event where action = 'ACCESS_DENIED' and actor_id = ?""", String.class,
                actor)).containsExactly("GET /api/v1/reports/judgments");
    }

    @Test
    void readingChangesNothingAndRepeatedReadsAgree() throws Exception {
        Integer judgments = jdbc.queryForObject("select count(*) from judgment", Integer.class);
        String first = mvc.perform(report()).andReturn().getResponse().getContentAsString();
        assertThat(mvc.perform(report()).andReturn().getResponse().getContentAsString()).isEqualTo(first);
        assertThat(jdbc.queryForObject("select count(*) from judgment", Integer.class)).isEqualTo(judgments);
    }
}
