package com.lawdesk.reports;

import static com.lawdesk.security.TestActors.scoped;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.lawdesk.platform.PostgresTestSupport;
import com.lawdesk.security.Permission;

/**
 * LD-058 performance harness (docs/design/LD-DOC-03 section 5: list and search under 500 ms at 50 000 cases and 500 000
 * child records). NOT part of the normal test run: it needs {@code -Dlawdesk.perf=true}. It creates its own throwaway
 * embedded PostgreSQL (never UAT), loads {@code tools/performance/wp8/seed-wp8.sql} (synthetic, marked SYNTH), then calls the
 * real endpoints through MockMvc as three users, so the {@code case_visible} access function is in every query path:
 * a department-wide manager, a user scoped to one case type, and a member who sees only their own cases.
 *
 * <pre>
 * ./mvnw -o test -Dtest=Wp8PerformanceHarness -Dlawdesk.perf=true [-Dlawdesk.perf.cases=50000]
 *     [-Dlawdesk.perf.out=../docs/evidence/DOC-WP8/performance-run.md] [-Dlawdesk.perf.runs=7]
 * </pre>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
@EnabledIfSystemProperty(named = "lawdesk.perf", matches = "true")
class Wp8PerformanceHarness extends PostgresTestSupport {

    private static final long TARGET_MS = 500;
    private static final long SLOW_MS = 6_000;

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private record Measurement(String user, String name, String url, long min, long median, long max, int status) {
    }

    @Test
    void measure() throws Exception {
        int cases = Integer.getInteger("lawdesk.perf.cases", 50_000);
        int runs = Integer.getInteger("lawdesk.perf.runs", 7);
        long seedStart = System.nanoTime();
        String seed = Files.readString(Path.of("../tools/performance/wp8/seed-wp8.sql"), StandardCharsets.UTF_8)
                .replace("${cases}", Integer.toString(cases));
        jdbc.execute(seed);
        long seedMs = (System.nanoTime() - seedStart) / 1_000_000;
        UUID member = jdbc.queryForObject("select primary_member_id from legal_case where reference_no = 'SYNTH-000001'",
                UUID.class);
        long total = jdbc.queryForObject("select count(*) from legal_case", Long.class);
        List<String> counts = new ArrayList<>();
        long children = 0;
        for (String table : List.of("proceeding", "judgment", "hearing", "task", "claim", "defense", "evidence_item",
                "info_request", "risk_assessment", "case_link", "case_org_involvement")) {
            long n = jdbc.queryForObject("select count(*) from " + table, Long.class);
            children += n;
            counts.add(table + "=" + n);
        }

        Permission[] permissions = {Permission.CASE_VIEW, Permission.REPORT_VIEW, Permission.REPORT_EXPORT,
                Permission.LIBRARY_VIEW};
        List<RequestPostProcessor> users = List.of(
                scoped(UUID.randomUUID(), "manager", List.of("ALL"), permissions),
                scoped(UUID.randomUUID(), "typed", List.of("TYPE:CIVIL"), permissions),
                scoped(member, "member", List.of(), permissions));
        List<String> names = List.of("manager (department-wide)", "scoped to one case type", "member (own cases only)");

        List<String[]> endpoints = new ArrayList<>(List.of(
                new String[] {"case list, first page", "/api/v1/cases?size=20"},
                new String[] {"case list, deep page", "/api/v1/cases?size=20&page=100"},
                new String[] {"case list, text filter", "/api/v1/cases?q=SYNTH-0042&size=20"},
                new String[] {"case list, status + type filter", "/api/v1/cases?status=ACTIVE&caseType=CIVIL&size=20"},
                new String[] {"unified search, reference", "/api/v1/search?q=SYNTH-004213"},
                new String[] {"unified search, words", "/api/v1/search?q=Contract%20dispute"},
                new String[] {"case inventory report", "/api/v1/reports/cases?size=20"},
                new String[] {"dashboard (with the 16 indicator figures)", "/api/v1/reports/dashboard"},
                new String[] {"indicator figures", "/api/v1/reports/indicators"}));
        for (IndicatorCode code : IndicatorCode.values()) {
            endpoints.add(new String[] {"indicator list " + code, "/api/v1/reports/indicators/" + code + "?size=20"});
        }
        for (String family : ReportFamilies.CATALOG.keySet()) {
            endpoints.add(new String[] {"report family " + family, "/api/v1/reports/families/" + family});
        }

        List<Measurement> results = new ArrayList<>();
        String only = System.getProperty("lawdesk.perf.only", ".*");
        for (int u = 0; u < users.size(); u++) {
            for (String[] endpoint : endpoints) {
                if (!endpoint[0].matches(only)) {
                    continue;
                }
                results.add(time(names.get(u), endpoint[0], endpoint[1], users.get(u), runs));
            }
        }
        write(cases, total, children, counts, seedMs, results);
    }

    private Measurement time(String user, String name, String url, RequestPostProcessor actor, int runs) throws Exception {
        long warmStart = System.nanoTime();
        int httpStatus = mvc.perform(get(url).with(actor)).andReturn().getResponse().getStatus();
        long warm = (System.nanoTime() - warmStart) / 1_000_000;
        List<Long> times = new ArrayList<>();
        if (warm > SLOW_MS) {
            // Far over the target: one measurement is evidence enough; do not spend minutes repeating it.
            return new Measurement(user, name, url, warm, warm, warm, httpStatus);
        }
        for (int i = 0; i < runs; i++) {
            long start = System.nanoTime();
            httpStatus = mvc.perform(get(url).with(actor)).andReturn().getResponse().getStatus();
            times.add((System.nanoTime() - start) / 1_000_000);
        }
        times.sort(Long::compare);
        return new Measurement(user, name, url, times.get(0), times.get(times.size() / 2), times.get(times.size() - 1),
                httpStatus);
    }

    private void write(int cases, long total, long children, List<String> counts, long seedMs, List<Measurement> results)
            throws IOException {
        StringBuilder out = new StringBuilder();
        out.append("# WP8 performance run\n\n");
        out.append("Synthetic data only (SYNTH), throwaway embedded PostgreSQL, real endpoints through MockMvc with the access functions in the query path.\n\n");
        out.append("- cases: ").append(total).append(" (requested ").append(cases).append(")\n");
        out.append("- child rows (").append(String.join(", ", counts)).append("): ").append(children).append("\n");
        out.append("- seeding took ").append(seedMs).append(" ms\n");
        out.append("- each request: one warm-up (a request slower than ").append(SLOW_MS).append(" ms is reported from that single run), then ").append(Integer.getInteger("lawdesk.perf.runs", 7))
                .append(" timed runs; the table shows min / median / max in ms\n");
        out.append("- target: ").append(TARGET_MS).append(" ms (list and search)\n\n");
        out.append("| user | request | HTTP | min | median | max | target |\n|---|---|---|---|---|---|---|\n");
        long missed = 0;
        for (Measurement m : results) {
            boolean ok = m.median <= TARGET_MS && m.status == 200;
            if (!ok) {
                missed++;
            }
            out.append("| ").append(m.user).append(" | ").append(m.name).append(" | ").append(m.status).append(" | ")
                    .append(m.min).append(" | ").append(m.median).append(" | ").append(m.max).append(" | ")
                    .append(ok ? "met" : "MISSED").append(" |\n");
        }
        out.append("\nRequests over the target (median): ").append(missed).append(" of ").append(results.size()).append("\n");
        Path file = Path.of(System.getProperty("lawdesk.perf.out", "target/wp8-performance.md"));
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        Files.writeString(file, out.toString(), StandardCharsets.UTF_8);
        System.out.println(out);
        if (Boolean.getBoolean("lawdesk.perf.enforce") && missed > 0) {
            throw new AssertionError(missed + " requests missed the target");
        }
    }
}
