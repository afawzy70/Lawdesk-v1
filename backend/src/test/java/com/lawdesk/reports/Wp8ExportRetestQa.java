package com.lawdesk.reports;

import static com.lawdesk.security.TestActors.scoped;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.lawdesk.platform.PostgresTestSupport;
import com.lawdesk.security.Permission;

/** QA-only 50k export replay. Never runs in the normal suite or against UAT. */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
@EnabledIfSystemProperty(named = "lawdesk.qa.export", matches = "true")
class Wp8ExportRetestQa extends PostgresTestSupport {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void theLargeInventoryExportReturnsItsDocumentedLimitWithoutAFile() throws Exception {
        String seed = Files.readString(Path.of("../tools/performance/wp8/seed-wp8.sql"), StandardCharsets.UTF_8)
                .replace("${cases}", "50000");
        jdbc.execute(seed);
        long count = jdbc.queryForObject("select count(*) from legal_case", Long.class);
        assertThat(count).isEqualTo(50_000);
        var actor = scoped(UUID.randomUUID(), "qa-50k-export", List.of("ALL"),
                Permission.CASE_VIEW, Permission.REPORT_VIEW, Permission.REPORT_EXPORT);
        long start = System.nanoTime();
        MvcResult response = mvc.perform(get("/api/v1/reports/cases/export").param("format", "csv").with(actor)).andReturn();
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        int status = response.getResponse().getStatus();
        String body = response.getResponse().getContentAsString();
        boolean limitRule = body.contains("EXPORT_TOO_LARGE");
        boolean attachment = response.getResponse().containsHeader("Content-Disposition");
        Path out = Path.of(System.getProperty("lawdesk.qa.export.out", "../qa/comprehensive_retest/export-50k-result.json"));
        Files.createDirectories(out.getParent());
        Files.writeString(out, "{\n"
                + "  \"applicationSourceSha\": \"" + System.getProperty("lawdesk.qa.export.sha", "not given") + "\",\n"
                + "  \"cases\": " + count + ",\n"
                + "  \"actorScope\": \"ALL\",\n"
                + "  \"endpoint\": \"GET /api/v1/reports/cases/export?format=csv\",\n"
                + "  \"httpStatus\": " + status + ",\n"
                + "  \"elapsedMs\": " + elapsedMs + ",\n"
                + "  \"ruleIsExportTooLarge\": " + limitRule + ",\n"
                + "  \"attachmentHeaderPresent\": " + attachment + ",\n"
                + "  \"threshold\": \"No owner-approved export latency limit; application 10000-row export limit retained\"\n"
                + "}\n", StandardCharsets.UTF_8);
        assertThat(status).isEqualTo(422);
        assertThat(limitRule).isTrue();
        assertThat(attachment).isFalse();
    }
}
