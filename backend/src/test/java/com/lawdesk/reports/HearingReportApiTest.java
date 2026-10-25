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
 * LD-032 hearings report (docs/delivery/PHASE-6-ACCEPTANCE.md), read through Workstream D's HearingReportQueries.
 * Hearings are dated in May 2013, a period no other test writes; the report calendar is pinned to 15 May 2013 so the
 * default period is that month. Past scheduled hearings await their result by the port's own (real) today.
 */
@SpringBootTest
@AutoConfigureMockMvc
class HearingReportApiTest extends PostgresTestSupport {

    private static final String REPORT = "/api/v1/reports/hearings";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoSpyBean
    ReportCalendar calendar;

    private ReportTestData data;
    private String m;
    private UUID appealCourt;
    private UUID xavier;
    private Map<String, String> h;

    /**
     * Case X: H1 first instance 2 May (held), H2 appeal 20 May (scheduled), H3 first instance 31 May (adjourned).
     * Case Y: H4 first instance 1 May 10:30 (scheduled), H5 first instance 1 May without a time (cancelled), H6 first
     * instance 1 June (scheduled, outside May).
     */
    @BeforeEach
    void hearings() {
        doReturn(LocalDate.of(2013, 5, 15)).when(calendar).today();
        data = new ReportTestData(jdbc);
        m = data.marker();
        UUID firstCourt = data.insertCourt("FIRST_INSTANCE");
        appealCourt = data.insertCourt("APPEAL");
        xavier = data.insertCaseWithReference(m + "-X", "Xavier v. Nile", "CIVIL", "ACTIVE", LocalDate.of(2012, 1, 3));
        UUID yusuf = data.insertCaseWithReference(m + "-Y", "Yusuf estate", "FAMILY", "OPEN", LocalDate.of(2012, 2, 3));
        UUID x1 = data.insertProceeding(xavier, "FIRST_INSTANCE", firstCourt, LocalDate.of(2012, 2, 1), null);
        data.insertJudgment(x1, LocalDate.of(2012, 12, 1), "AGAINST", false);
        UUID x2 = data.insertProceeding(xavier, "APPEAL", appealCourt, LocalDate.of(2013, 1, 5), x1);
        UUID y1 = data.insertProceeding(yusuf, "FIRST_INSTANCE", firstCourt, LocalDate.of(2012, 3, 1), null);
        UUID y2 = data.insertProceeding(yusuf, "FIRST_INSTANCE", firstCourt, LocalDate.of(2012, 4, 1), null);

        UUID h1 = data.insertHearing(xavier, x1, LocalDate.of(2013, 5, 2), "HELD");
        UUID h2 = data.insertHearing(xavier, x2, LocalDate.of(2013, 5, 20), "SCHEDULED");
        UUID h3 = data.insertHearing(xavier, x1, LocalDate.of(2013, 5, 31), "ADJOURNED");
        UUID h4 = data.insertHearing(yusuf, y1, LocalDate.of(2013, 5, 1), "SCHEDULED");
        jdbc.update("update hearing set hearing_time = '10:30' where id = ?", h4);
        UUID h5 = data.insertHearing(yusuf, y2, LocalDate.of(2013, 5, 1), "CANCELLED");
        UUID h6 = data.insertHearing(yusuf, y1, LocalDate.of(2013, 6, 1), "SCHEDULED");
        h = Map.of("H1", h1.toString(), "H2", h2.toString(), "H3", h3.toString(), "H4", h4.toString(), "H5",
                h5.toString(), "H6", h6.toString());
    }

    @AfterEach
    void removeRows() {
        data.deleteCreated();
    }

    private static MockHttpServletRequestBuilder report() {
        return get(REPORT).with(as(Permission.REPORT_VIEW));
    }

    private void expect(MockHttpServletRequestBuilder request, String... hearings) throws Exception {
        String[] ids = new String[hearings.length];
        for (int i = 0; i < hearings.length; i++) {
            ids[i] = h.get(hearings[i]);
        }
        mvc.perform(request).andExpect(status().isOk())
                .andExpect(ids.length == 0 ? jsonPath("$.items", empty()) : jsonPath("$.items[*].id", contains(ids)))
                .andExpect(jsonPath("$.totalItems").value(ids.length))
                .andExpect(jsonPath("$.summary.total").value(ids.length));
    }

    @Test
    void listsTheHearingsOfTheCurrentMonthInCalendarOrderWithCaseStageAndCourt() throws Exception {
        expect(report(), "H5", "H4", "H1", "H2", "H3");
        mvc.perform(report()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.from").value("2013-05-01")).andExpect(jsonPath("$.to").value("2013-05-31"))
                .andExpect(jsonPath("$.items[1].hearingDate").value("2013-05-01"))
                .andExpect(jsonPath("$.items[1].hearingTime").value("10:30:00"))
                .andExpect(jsonPath("$.items[0].hearingTime").doesNotExist())
                .andExpect(jsonPath("$.items[1].purpose").value("PLEADING"))
                .andExpect(jsonPath("$.items[1].status").value("SCHEDULED"))
                .andExpect(jsonPath("$.items[1].awaitingResult").value(true))
                .andExpect(jsonPath("$.items[2].awaitingResult").value(false))
                .andExpect(jsonPath("$.items[3].stage").value("APPEAL"))
                .andExpect(jsonPath("$.items[3].court.id").value(appealCourt.toString()))
                .andExpect(jsonPath("$.items[3].court.nameEn").isString())
                .andExpect(jsonPath("$.items[3].courtCaseNumber").isString())
                .andExpect(jsonPath("$.items[3].courtCaseYear").value(2013))
                .andExpect(jsonPath("$.items[3].caseId").value(xavier.toString()))
                .andExpect(jsonPath("$.items[3].caseReferenceNo").value(m + "-X"))
                .andExpect(jsonPath("$.items[3].caseTitle").value("Xavier v. Nile"));
    }

    @Test
    void summarisesEveryMatchingHearingByStatusAndThoseAwaitingAResult() throws Exception {
        mvc.perform(report().param("size", "1")).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.totalPages").value(5))
                .andExpect(jsonPath("$.summary.total").value(5))
                .andExpect(jsonPath("$.summary.byStatus.SCHEDULED").value(2))
                .andExpect(jsonPath("$.summary.byStatus.HELD").value(1))
                .andExpect(jsonPath("$.summary.byStatus.ADJOURNED").value(1))
                .andExpect(jsonPath("$.summary.byStatus.CANCELLED").value(1))
                .andExpect(jsonPath("$.summary.awaitingResult").value(2));
    }

    @Test
    void filtersByAnInclusivePeriodAndByStatus() throws Exception {
        expect(report().param("from", "2013-05-31").param("to", "2013-06-01"), "H3", "H6");
        expect(report().param("from", "2013-05-01").param("to", "2013-05-01"), "H5", "H4");
        expect(report().param("from", "2013-05-03").param("to", "2013-05-19"));
        expect(report().param("status", "SCHEDULED"), "H4", "H2");
        expect(report().param("status", "HELD", "ADJOURNED"), "H1", "H3");
        expect(report().param("status", "SCHEDULED").param("from", "2013-06-01").param("to", "2013-06-30"), "H6");
    }

    @Test
    void pagesThroughTheCalendar() throws Exception {
        mvc.perform(report().param("size", "2").param("page", "1"))
                .andExpect(jsonPath("$.items[*].id", contains(h.get("H1"), h.get("H2"))))
                .andExpect(jsonPath("$.totalItems").value(5)).andExpect(jsonPath("$.totalPages").value(3));
        mvc.perform(report().param("size", "100")).andExpect(jsonPath("$.size").value(100));
        mvc.perform(report().param("size", "250")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("size"));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "status | POSTPONED | typeMismatch",
            "from | 2013-02-30 | typeMismatch",
            "to | tomorrow | typeMismatch",
            "page | -1 | Min",
            "size | 0 | Min",
            "sort | hearingDate,desc | Pattern"})
    void malformedParametersAreFieldErrors(String field, String value, String code) throws Exception {
        mvc.perform(report().param(field, value)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value(field))
                .andExpect(jsonPath("$.errors[0].code").value(code));
    }

    @Test
    void anInvertedPeriodBreaksARule() throws Exception {
        mvc.perform(report().param("from", "2013-05-02").param("to", "2013-05-01"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.rule").value("DATE_RANGE_INVALID"));
    }

    @Test
    void needsASessionAndTheReportPermission() throws Exception {
        mvc.perform(get(REPORT)).andExpect(status().isUnauthorized());
        UUID actor = UUID.randomUUID();
        mvc.perform(get(REPORT).with(as(actor, "hearing-clerk", Permission.HEARING_VIEW)))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForList("""
                select target_id from audit_event where action = 'ACCESS_DENIED' and actor_id = ?""", String.class,
                actor)).containsExactly("GET /api/v1/reports/hearings");
    }

    @Test
    void readingChangesNothingAndRepeatedReadsAgree() throws Exception {
        Integer hearings = jdbc.queryForObject("select count(*) from hearing", Integer.class);
        String first = mvc.perform(report()).andReturn().getResponse().getContentAsString();
        assertThat(mvc.perform(report()).andReturn().getResponse().getContentAsString()).isEqualTo(first);
        assertThat(jdbc.queryForObject("select count(*) from hearing", Integer.class)).isEqualTo(hearings);
    }
}
