package com.lawdesk.reports;

import static com.lawdesk.security.TestActors.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.lawdesk.platform.PostgresTestSupport;
import com.lawdesk.security.Permission;

/**
 * LD-034 Excel and PDF exports (docs/delivery/PHASE-6-ACCEPTANCE.md, criterion 6): the same rows and labels as the
 * CSV, an Excel sheet laid out right to left in Arabic, and a PDF whose Arabic is shaped and drawn right to left with
 * an embedded font. "Today" is pinned to 15 March 2031.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ExportFormatsApiTest extends PostgresTestSupport {

    private static final String CASES = "/api/v1/reports/cases/export";
    private static final String JUDGMENTS = "/api/v1/reports/judgments/export";
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoSpyBean
    ReportCalendar calendar;

    private ReportTestData data;
    private String m;
    private final UUID actor = UUID.randomUUID();

    /** A: civil open case at first instance (judgment in favour, 10 March 2031); B: archived family case. */
    @BeforeEach
    void twoCases() {
        doReturn(LocalDate.of(2031, 3, 15)).when(calendar).today();
        data = new ReportTestData(jdbc);
        m = data.marker();
        UUID court = data.insertCourt("FIRST_INSTANCE");
        UUID a = data.insertCaseWithReference(m + "-A", "=HYPERLINK(\"http://x\")", "CIVIL", "OPEN",
                LocalDate.of(2025, 1, 10));
        data.insertCaseWithReference(m + "-B", "تركة حسن", "FAMILY", "ARCHIVED", LocalDate.of(2025, 3, 5));
        UUID proceeding = data.insertProceeding(a, "FIRST_INSTANCE", court, LocalDate.of(2025, 2, 1), null);
        data.insertJudgment(proceeding, LocalDate.of(2031, 3, 10), "IN_FAVOUR", true);
    }

    @AfterEach
    void removeRows() {
        data.deleteCreated();
    }

    private MockHttpServletRequestBuilder export(String path, String format, String lang) {
        return get(path).param("format", format).param("lang", lang)
                .with(as(actor, "exporter", Permission.REPORT_EXPORT));
    }

    private List<String> audited() {
        return jdbc.queryForList("""
                select target_id from audit_event where actor_id = ? and action = 'REPORT_EXPORT' order by id""",
                String.class, actor);
    }

    /** The parts of a workbook. Read through its central directory, as spreadsheet applications do. */
    private static Map<String, String> unzip(byte[] xlsx) throws IOException {
        Path file = Files.createTempFile("export", ".xlsx");
        try {
            Files.write(file, xlsx);
            Map<String, String> entries = new HashMap<>();
            try (ZipFile zip = new ZipFile(file.toFile())) {
                for (ZipEntry entry : Collections.list(zip.entries())) {
                    try (InputStream in = zip.getInputStream(entry)) {
                        entries.put(entry.getName(), unescape(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
                    }
                }
            }
            return entries;
        } finally {
            Files.delete(file);
        }
    }

    /** XML text with its character references resolved, so assertions read the stored text. */
    private static String unescape(String xml) {
        String resolved = Pattern.compile("&#x([0-9a-fA-F]+);").matcher(xml)
                .replaceAll(match -> Matcher.quoteReplacement(Character.toString(Integer.parseInt(match.group(1), 16))));
        return resolved.replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
    }

    @Test
    void exportsTheCaseInventoryAsAnArabicRightToLeftWorkbook() throws Exception {
        byte[] xlsx = mvc.perform(export(CASES, "xlsx", "ar").param("q", m)).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", XLSX))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"case-inventory-2031-03-15.xlsx\""))
                .andReturn().getResponse().getContentAsByteArray();

        Map<String, String> entries = unzip(xlsx);
        String sheet = entries.get("xl/worksheets/sheet1.xml");
        String strings = entries.getOrDefault("xl/sharedStrings.xml", "") + sheet;
        assertThat(sheet).contains("rightToLeft=\"true\"").doesNotContain("<f>");
        assertThat(strings).contains("المرجع", "درجة التقاضي الحالية", m + "-A", "مؤرشفة", "تركة حسن",
                "=HYPERLINK(\"http://x\")");
        assertThat(audited()).containsExactly("CASE_INVENTORY:XLSX");
    }

    @Test
    void anEnglishWorkbookReadsLeftToRightWithTheSameRows() throws Exception {
        byte[] xlsx = mvc.perform(export(JUDGMENTS, "xlsx", "en").param("from", "2031-03-10").param("to", "2031-03-10")
                .param("final", "true")).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        Map<String, String> entries = unzip(xlsx);
        String sheet = entries.get("xl/worksheets/sheet1.xml");
        assertThat(sheet).doesNotContain("rightToLeft=\"true\"");
        assertThat(entries.getOrDefault("xl/sharedStrings.xml", "") + sheet)
                .contains("Judgment date", "In favour of the client", "Yes", m + "-A");
        assertThat(audited()).containsExactly("JUDGMENTS:XLSX");
    }

    /** The characters of every glyph in drawing order, line by line (top to bottom). */
    private static String drawn(PDDocument pdf) throws IOException {
        StringBuilder text = new StringBuilder();
        PDFTextStripper stripper = new PDFTextStripper() {
            @Override
            protected void processTextPosition(TextPosition position) {
                text.append(position.getUnicode());
            }
        };
        stripper.getText(pdf);
        return text.toString();
    }

    @Test
    void exportsAnArabicPdfWithShapedRightToLeftTextAndAnEmbeddedFont() throws Exception {
        byte[] bytes = mvc.perform(export(CASES, "pdf", "ar").param("q", m)).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"case-inventory-2031-03-15.pdf\""))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(bytes, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            String drawn = drawn(pdf);
            // المرجع shaped and drawn right to left (see PdfTextTest), never its unshaped letters in logical order.
            assertThat(drawn).contains("ﻊﺟﺮﻤﻟﺍ").doesNotContain("المرجع");
            // تركة حسن: two words, each shaped and reversed, the first word on the right.
            assertThat(drawn).contains(PdfText.visual("تركة حسن", true)).contains(m + "-A");
            PDFont font = pdf.getPage(0).getResources().getFont(
                    pdf.getPage(0).getResources().getFontNames().iterator().next());
            assertThat(font.isEmbedded()).isTrue();
            assertThat(font.getName()).contains("DejaVuSans");
        }
        assertThat(audited()).containsExactly("CASE_INVENTORY:PDF");
    }

    @Test
    void exportsAnEnglishPdfOfTheJudgments() throws Exception {
        byte[] bytes = mvc.perform(export(JUDGMENTS, "pdf", "en").param("from", "2031-03-10").param("to", "2031-03-10")
                .param("final", "true")).andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"judgments-2031-03-15.pdf\""))
                .andReturn().getResponse().getContentAsByteArray();
        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            String text = new PDFTextStripper().getText(pdf);
            assertThat(text).contains("Judgment date", "In favour of the client", m + "-A", "2031-03-10");
        }
        assertThat(audited()).containsExactly("JUDGMENTS:PDF");
    }

    @Test
    void aLongReportContinuesOnFurtherPages() throws Exception {
        data.insertBulkCases(120);
        byte[] bytes = mvc.perform(export(CASES, "pdf", "en").param("q", m)).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsByteArray();
        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            assertThat(pdf.getNumberOfPages()).isGreaterThan(1);
            String text = new PDFTextStripper().getText(pdf);
            assertThat(text).contains(m + "-BULK-120", m + "-BULK-99", m + "-A");
        }
    }

    @Test
    void theRowLimitAppliesToEveryFormat() throws Exception {
        data.insertBulkCases(9_999);
        for (String format : List.of("xlsx", "pdf")) {
            mvc.perform(export(CASES, format, "en").param("q", m)).andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.rule").value("EXPORT_TOO_LARGE"));
        }
        assertThat(audited()).isEmpty();
    }

    /** QAD-035: a streaming reader sizes the sheet from its declared dimension, so it must match the cells. */
    @Test
    void declaresTheRealDimensionInEnglishAndArabicWorkbooks() throws Exception {
        for (String lang : List.of("en", "ar")) {
            byte[] cases = mvc.perform(export(CASES, "xlsx", lang).param("q", m)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsByteArray();
            XlsxTest.Sheet sheet = XlsxTest.read(cases);
            assertThat(sheet.rows()).as(lang).isEqualTo(3);
            assertThat(sheet.dimension()).as(lang).isEqualTo("A1:I3");

            byte[] judgments = mvc.perform(export(JUDGMENTS, "xlsx", lang).param("from", "2031-03-10")
                    .param("to", "2031-03-10")).andExpect(status().isOk()).andReturn().getResponse()
                    .getContentAsByteArray();
            assertThat(XlsxTest.read(judgments).dimension()).as(lang).isEqualTo("A1:H2");
        }
    }

    @Test
    void declaresTheRealDimensionAtTheTenThousandRowLimit() throws Exception {
        data.insertBulkCases(9_998);
        for (String lang : List.of("en", "ar")) {
            byte[] xlsx = mvc.perform(export(CASES, "xlsx", lang).param("q", m)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsByteArray();
            XlsxTest.Sheet sheet = XlsxTest.read(xlsx);
            assertThat(sheet.rows()).as(lang).isEqualTo(10_001);
            assertThat(sheet.dimension()).as(lang).isEqualTo("A1:I10001");
        }
    }

    @Test
    void anUnknownFormatIsAFieldError() throws Exception {
        mvc.perform(export(CASES, "docx", "en")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("format"))
                .andExpect(jsonPath("$.errors[0].code").value("Pattern"));
    }
}
