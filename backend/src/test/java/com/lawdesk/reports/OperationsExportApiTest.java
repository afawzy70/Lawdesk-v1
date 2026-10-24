package com.lawdesk.reports;

import static com.lawdesk.security.TestActors.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
 * LD-034 exports of the Phase 5 reports (hearings, workload, experts): the same rules as the case and judgment exports
 * (format, language, row limit, attachment, audit without data). The report calendar is pinned to 15 May 2013 (hearing
 * period and file names); task and referral rules use their ports' own today.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OperationsExportApiTest extends PostgresTestSupport {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoSpyBean
    ReportCalendar calendar;

    private ReportTestData data;
    private String m;
    private String courtCode;
    private String number;
    private UUID amal;
    private UUID alia;
    private final UUID actor = UUID.randomUUID();

    /** One case with a scheduled hearing on 2 May 2013 at 09:15, a task of Amal, a referral to expert Alia. */
    @BeforeEach
    void rows() {
        doReturn(LocalDate.of(2013, 5, 15)).when(calendar).today();
        LocalDate today = LocalDate.now(ZoneId.of("Africa/Cairo"));
        data = new ReportTestData(jdbc);
        m = data.marker();
        UUID court = data.insertCourt("FIRST_INSTANCE");
        courtCode = jdbc.queryForObject("select code from court where id = ?", String.class, court);
        UUID caseId = data.insertCaseWithReference(m + "-H", "=Hassan, \"Sons\"", "CIVIL", "ACTIVE",
                LocalDate.of(2012, 1, 3));
        UUID proceeding = data.insertProceeding(caseId, "FIRST_INSTANCE", court, LocalDate.of(2012, 2, 1), null);
        number = jdbc.queryForObject("select court_case_number from proceeding where id = ?", String.class,
                proceeding);
        UUID hearing = data.insertHearing(caseId, proceeding, LocalDate.of(2013, 5, 2), "SCHEDULED");
        jdbc.update("update hearing set hearing_time = '09:15' where id = ?", hearing);
        amal = data.insertUser("Amal " + m);
        data.insertTask(caseId, amal, "overdue", today.minusDays(1), "OPEN");
        alia = data.insertExpert("Alia " + m, "Accounting");
        data.insertReferral(caseId, proceeding, alia, today.minusDays(30), today.minusDays(1), "OPEN");
    }

    @AfterEach
    void removeRows() {
        data.deleteCreated();
    }

    private MockHttpServletRequestBuilder export(String report, String format, String lang) {
        return get("/api/v1/reports/" + report + "/export").param("format", format).param("lang", lang)
                .with(as(actor, "exporter", Permission.REPORT_EXPORT));
    }

    private static List<String> lines(MvcResult result) {
        byte[] bytes = result.getResponse().getContentAsByteArray();
        assertThat(Arrays.copyOf(bytes, 3)).containsExactly(0xEF, 0xBB, 0xBF);
        return List.of(new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8).split("\r\n"));
    }

    private List<String> audited() {
        return jdbc.queryForList("""
                select target_id from audit_event where actor_id = ? and action = 'REPORT_EXPORT' order by id""",
                String.class, actor);
    }

    @Test
    void exportsTheHearingsOfThePeriodAsCsv() throws Exception {
        MvcResult result = mvc.perform(export("hearings", "csv", "en").param("from", "2013-05-02")
                        .param("to", "2013-05-02"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"hearings-2013-05-15.csv\""))
                .andReturn();
        assertThat(lines(result)).containsExactly(
                "Hearing date,Time,Case reference,Case title,Stage,Court,Number / year,Purpose,Status,"
                        + "Awaiting result",
                "2013-05-02,09:15," + m + "-H,\"'=Hassan, \"\"Sons\"\"\",First instance,Court " + courtCode + ","
                        + number + "/2012,Pleading,Scheduled,Yes");

        List<String> arabic = lines(mvc.perform(export("hearings", "csv", "ar").param("from", "2013-05-02")
                .param("to", "2013-05-02")).andReturn());
        assertThat(arabic.get(0)).isEqualTo("تاريخ الجلسة,الوقت,مرجع القضية,عنوان القضية,درجة التقاضي,المحكمة,"
                + "الرقم / السنة,الغرض,الحالة,في انتظار النتيجة");
        assertThat(arabic.get(1)).endsWith(",مرافعة,محددة,نعم");
        assertThat(audited()).containsExactly("HEARINGS:CSV", "HEARINGS:CSV");
    }

    @Test
    void exportsTheWorkloadAndTheExpertsWithNames() throws Exception {
        List<String> workload = lines(mvc.perform(export("workload", "csv", "en")).andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"workload-2013-05-15.csv\""))
                .andReturn());
        assertThat(workload.get(0)).isEqualTo("Assignee,Open,In progress,Overdue");
        assertThat(workload).contains("Amal " + m + ",1,0,1");

        List<String> experts = lines(mvc.perform(export("experts", "csv", "ar")).andExpect(status().isOk()).andReturn());
        assertThat(experts.get(0)).isEqualTo("الخبير,التخصص,نشط,الإحالات المفتوحة,التقارير المتأخرة");
        assertThat(experts).contains("Alia " + m + ",Accounting,نعم,1,1");
        assertThat(audited()).containsExactly("WORKLOAD:CSV", "EXPERTS:CSV");
    }

    @Test
    void exportsAnArabicHearingsPdfAndAnExpertsWorkbook() throws Exception {
        byte[] pdf = mvc.perform(export("hearings", "pdf", "ar").param("from", "2013-05-02").param("to", "2013-05-02"))
                .andExpect(status().isOk()).andExpect(header().string("Content-Type", "application/pdf"))
                .andReturn().getResponse().getContentAsByteArray();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            StringBuilder drawn = new StringBuilder();
            new PDFTextStripper() {
                @Override
                protected void processTextPosition(TextPosition position) {
                    drawn.append(position.getUnicode());
                }
            }.getText(document);
            assertThat(drawn.toString()).contains(PdfText.visual("مرافعة", true)).contains(m + "-H");
        }
        mvc.perform(export("experts", "xlsx", "en")).andExpect(status().isOk()).andExpect(header().string(
                "Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        assertThat(audited()).containsExactly("HEARINGS:PDF", "EXPERTS:XLSX");
    }

    @Test
    void theRulesOfEveryExportApply() throws Exception {
        for (String report : List.of("hearings", "workload", "experts")) {
            mvc.perform(get("/api/v1/reports/" + report + "/export").param("format", "csv")
                    .with(as(actor, "viewer", Permission.REPORT_VIEW))).andExpect(status().isForbidden());
            mvc.perform(export(report, "doc", "en")).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("format"));
        }
        mvc.perform(export("hearings", "csv", "en").param("status", "LOST")).andExpect(status().isBadRequest());
        mvc.perform(export("hearings", "csv", "en").param("from", "2013-05-03").param("to", "2013-05-02"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.rule").value("DATE_RANGE_INVALID"));
        assertThat(audited()).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from audit_event where actor_id = ? and action = 'ACCESS_DENIED'",
                Integer.class, actor)).isEqualTo(3);
    }
}
