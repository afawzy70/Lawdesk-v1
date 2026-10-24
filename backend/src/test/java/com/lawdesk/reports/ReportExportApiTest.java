package com.lawdesk.reports;

import static com.lawdesk.security.TestActors.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.lawdesk.platform.PostgresTestSupport;
import com.lawdesk.security.Permission;

/**
 * LD-034 CSV exports of the case inventory and the judgments report (docs/delivery/PHASE-6-ACCEPTANCE.md;
 * QA-0265…QA-0271). Every request filters on this test's own rows; "today" is pinned to 15 March 2031.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReportExportApiTest extends PostgresTestSupport {

    private static final String CASES = "/api/v1/reports/cases/export";
    private static final String JUDGMENTS = "/api/v1/reports/judgments/export";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoSpyBean
    ReportCalendar calendar;

    private ReportTestData data;
    private String m;
    private String courtCode;
    private String courtCaseNumber;
    private final UUID actor = UUID.randomUUID();

    /**
     * A: civil open case whose title is a spreadsheet formula, at first instance with a final judgment in favour on
     * 10 March 2031; B: archived family case whose title holds a comma and quotes, without proceedings.
     */
    @BeforeEach
    void twoCases() {
        doReturn(LocalDate.of(2031, 3, 15)).when(calendar).today();
        data = new ReportTestData(jdbc);
        m = data.marker();
        UUID court = data.insertCourt("FIRST_INSTANCE");
        courtCode = jdbc.queryForObject("select code from court where id = ?", String.class, court);
        UUID a = data.insertCaseWithReference(m + "-A", "=HYPERLINK(\"http://x\")", "CIVIL", "OPEN",
                LocalDate.of(2025, 1, 10));
        data.insertCaseWithReference(m + "-B", "Hassan, Sons \"Trading\"", "FAMILY", "ARCHIVED",
                LocalDate.of(2025, 3, 5));
        UUID proceeding = data.insertProceeding(a, "FIRST_INSTANCE", court, LocalDate.of(2025, 2, 1), null);
        courtCaseNumber = jdbc.queryForObject("select court_case_number from proceeding where id = ?", String.class,
                proceeding);
        data.insertJudgment(proceeding, LocalDate.of(2031, 3, 10), "IN_FAVOUR", true);
    }

    @AfterEach
    void removeRows() {
        data.deleteCreated();
    }

    private MockHttpServletRequestBuilder export(String path) {
        return get(path).param("format", "csv").with(as(actor, "exporter", Permission.REPORT_EXPORT));
    }

    private static String body(MvcResult result) {
        byte[] bytes = result.getResponse().getContentAsByteArray();
        assertThat(Arrays.copyOf(bytes, 3)).containsExactly(0xEF, 0xBB, 0xBF);
        return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
    }

    private List<String> exports() {
        return jdbc.queryForList("""
                select action || ' ' || outcome || ' ' || target_type || ' ' || target_id from audit_event
                where actor_id = ? and action <> 'ACCESS_DENIED' order by id""", String.class, actor);
    }

    @Test
    void exportsTheCaseInventoryAsAnAttachedUtf8CsvAndAuditsIt() throws Exception {
        MvcResult result = mvc.perform(export(CASES).param("q", m)).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"case-inventory-2031-03-15.csv\""))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andReturn();

        assertThat(body(result)).isEqualTo(
                "Reference,Title,Type,Status,Open or closed,Opened on,Current stage,Current court,Proceedings\r\n"
                        + m + "-B,\"Hassan, Sons \"\"Trading\"\"\",Family (personal status),Archived,Closed,"
                        + "2025-03-05,,,0\r\n"
                        + m + "-A,\"'=HYPERLINK(\"\"http://x\"\")\",Civil,Open,Open,2025-01-10,First instance,"
                        + "Court " + courtCode + ",1\r\n");
        assertThat(exports()).containsExactly("REPORT_EXPORT SUCCESS REPORT CASE_INVENTORY:CSV");
        assertThat(jdbc.queryForObject("select count(*) from audit_event where target_id like ? or reason like ?",
                Integer.class, "%" + m + "%", "%" + m + "%")).isZero();
    }

    @Test
    void labelsTheExportInArabicAndAppliesTheReportFiltersAndSort() throws Exception {
        MvcResult result = mvc.perform(export(CASES).param("q", m).param("lang", "ar").param("sort", "referenceNo,asc")
                .param("caseType", "CIVIL", "FAMILY"))
                .andExpect(status().isOk()).andReturn();
        assertThat(body(result)).isEqualTo(
                "المرجع,العنوان,النوع,الحالة,مفتوحة أو مغلقة,تاريخ الفتح,درجة التقاضي الحالية,المحكمة الحالية,الدعاوى\r\n"
                        + m + "-A,\"'=HYPERLINK(\"\"http://x\"\")\",مدني,مفتوحة,مفتوحة,2025-01-10,أول درجة,"
                        + "محكمة " + courtCode + ",1\r\n"
                        + m + "-B,\"Hassan, Sons \"\"Trading\"\"\",أحوال شخصية,مؤرشفة,مغلقة,2025-03-05,,,0\r\n");

        String onlyFamily = body(mvc.perform(export(CASES).param("q", m).param("caseType", "FAMILY")).andReturn());
        assertThat(onlyFamily.split("\r\n")).hasSize(2);
        assertThat(onlyFamily).contains(m + "-B").doesNotContain(m + "-A");
    }

    @Test
    void anEmptyExportIsTheHeaderOnly() throws Exception {
        assertThat(body(mvc.perform(export(CASES).param("q", m + "-none")).andExpect(status().isOk()).andReturn()))
                .isEqualTo("Reference,Title,Type,Status,Open or closed,Opened on,Current stage,Current court,"
                        + "Proceedings\r\n");
    }

    @Test
    void exportsTheJudgmentsOfThePeriodAndAuditsIt() throws Exception {
        MvcResult result = mvc.perform(export(JUDGMENTS).param("courtId", jdbc.queryForObject(
                        "select id::text from court where code = ?", String.class, courtCode)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"judgments-2031-03-15.csv\""))
                .andReturn();
        assertThat(body(result)).isEqualTo(
                "Judgment date,Case reference,Case title,Stage,Court,Number / year,Outcome,Final\r\n"
                        + "2031-03-10," + m + "-A,\"'=HYPERLINK(\"\"http://x\"\")\",First instance,Court " + courtCode
                        + "," + courtCaseNumber + "/2025,In favour of the client,Yes\r\n");

        String arabic = body(mvc.perform(export(JUDGMENTS).param("lang", "ar").param("final", "true")
                .param("from", "2031-03-10").param("to", "2031-03-10").param("outcome", "IN_FAVOUR")).andReturn());
        assertThat(arabic).startsWith("تاريخ الحكم,مرجع القضية,عنوان القضية,درجة التقاضي,المحكمة,الرقم / السنة,"
                + "النتيجة,نهائي\r\n").contains(",لصالح الموكل,نعم\r\n");
        assertThat(exports()).containsExactly("REPORT_EXPORT SUCCESS REPORT JUDGMENTS:CSV",
                "REPORT_EXPORT SUCCESS REPORT JUDGMENTS:CSV");
    }

    @Test
    void exportsUpToTenThousandRowsAndRefusesMoreWithoutProducingAnything() throws Exception {
        data.insertBulkCases(9_998);
        String all = body(mvc.perform(export(CASES).param("q", m)).andExpect(status().isOk()).andReturn());
        assertThat(all.split("\r\n")).hasSize(10_001);

        jdbc.update("""
                insert into legal_case (reference_no, title, case_type, opened_on, status, created_by, updated_by)
                values (? || '-BULK-extra', 'one too many', 'CIVIL', date '2020-01-02', 'OPEN', ?, ?)""", m,
                ReportTestData.CREATOR, ReportTestData.CREATOR);
        mvc.perform(export(CASES).param("q", m)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("EXPORT_TOO_LARGE"))
                .andExpect(header().doesNotExist("Content-Disposition"));
        assertThat(exports()).containsExactly("REPORT_EXPORT SUCCESS REPORT CASE_INVENTORY:CSV");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "format | xls | Pattern",
            "format | CSVX | Pattern",
            "lang | fr | Pattern",
            "caseType | MARITIME | typeMismatch",
            "sort | title;drop | Pattern"})
    void malformedParametersAreFieldErrors(String field, String value, String code) throws Exception {
        mvc.perform(get(CASES).param("format", "csv").param(field, value)
                        .with(as(actor, "exporter", Permission.REPORT_EXPORT)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value(field))
                .andExpect(jsonPath("$.errors[0].code").value(code));
        assertThat(exports()).isEmpty();
    }

    @Test
    void theFormatIsRequired() throws Exception {
        mvc.perform(get(JUDGMENTS).with(as(actor, "exporter", Permission.REPORT_EXPORT)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("format"))
                .andExpect(jsonPath("$.errors[0].code").value("NotNull"));
        mvc.perform(get(JUDGMENTS).param("format", "csv").param("from", "2031-03-02").param("to", "2031-03-01")
                        .with(as(actor, "exporter", Permission.REPORT_EXPORT)))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.rule").value("DATE_RANGE_INVALID"));
        assertThat(exports()).isEmpty();
    }

    @Test
    void needsASession() throws Exception {
        mvc.perform(get(CASES).param("format", "csv")).andExpect(status().isUnauthorized());
        mvc.perform(get(JUDGMENTS).param("format", "csv")).andExpect(status().isUnauthorized());
    }

    @Test
    void needsTheExportPermissionEvenWithReportViewAndAuditsTheRefusal() throws Exception {
        mvc.perform(get(CASES).param("format", "csv").with(as(actor, "viewer", Permission.REPORT_VIEW)))
                .andExpect(status().isForbidden());
        mvc.perform(get(JUDGMENTS).param("format", "csv").with(as(actor, "viewer", Permission.REPORT_VIEW)))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForList("""
                select target_id from audit_event where action = 'ACCESS_DENIED' and actor_id = ? order by id""",
                String.class, actor)).containsExactly("GET /api/v1/reports/cases/export",
                "GET /api/v1/reports/judgments/export");
        assertThat(exports()).isEmpty();
    }
}
