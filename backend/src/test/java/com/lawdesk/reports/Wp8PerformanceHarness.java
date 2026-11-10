package com.lawdesk.reports;

import static com.lawdesk.security.TestActors.scoped;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.lawdesk.platform.PostgresTestSupport;
import com.lawdesk.platform.SqlStatementCounter;
import com.lawdesk.security.Permission;

/**
 * LD-058 performance harness (docs/design/LD-DOC-03 section 5: list and search under 500 ms at 50 000 cases and 500 000
 * child records). NOT part of the normal test run: it needs {@code -Dlawdesk.perf=true}. It creates its own throwaway
 * embedded PostgreSQL (never UAT), loads {@code tools/performance/wp8/seed-wp8.sql} (synthetic, marked SYNTH), then calls the
 * real endpoints through MockMvc so the {@code case_visible} access function is in every query path. Users: a
 * department-wide manager, a member who sees only their own cases, a user with no scope, and (for the search only) a
 * department-wide manager who also holds SEARCH_ALL and every view permission, so all twelve record types are searched.
 *
 * <p>Each request is measured sequentially ({@code runs} timed requests after one warm-up: p50, p95, min, max, SQL
 * statements per request) and then with {@code clients} parallel clients each sending {@code crun} (default 5) requests (p50 and p95 over
 * all of them). A warm-up slower than {@value #SLOW_MS} ms is reported from that single run. A raw CSV of every latency is
 * written next to the table.
 *
 * <pre>
 * ./mvnw -o test -Dtest=Wp8PerformanceHarness -Dlawdesk.perf=true [-Dlawdesk.perf.cases=50000] [-Dlawdesk.perf.runs=15]
 *     [-Dlawdesk.perf.clients=8] [-Dlawdesk.perf.sha=&lt;git sha&gt;] [-Dlawdesk.perf.only=&lt;regex of request names&gt;]
 *     [-Dlawdesk.perf.out=../docs/evidence/DOC-PERF/run.md]
 * </pre>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
@Import(SqlStatementCounter.Config.class)
@EnabledIfSystemProperty(named = "lawdesk.perf", matches = "true")
class Wp8PerformanceHarness extends PostgresTestSupport {

    private static final long TARGET_MS = 500;
    private static final long SLOW_MS = 6_000;

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private record Measurement(String user, String name, String url, long min, long p50, long p95, long max, int statements,
            long cp50, long cp95, int status, List<Long> raw) {
    }

    private record Actor(String name, RequestPostProcessor post, boolean searchOnly) {
    }

    /**
     * LD-DOC-R3: {@code -Dlawdesk.perf.db=<name>} measures an already seeded database of the server named by
     * {@code LAWDESK_TEST_DB_URL} (a throw-away copy made with CREATE DATABASE ... TEMPLATE) instead of seeding a new one.
     * Flyway still runs, so migrations added after the copy was made are applied on top of the data, as in an upgrade.
     */
    @org.springframework.test.context.DynamicPropertySource
    static void seededDatabase(org.springframework.test.context.DynamicPropertyRegistry registry) {
        String name = System.getProperty("lawdesk.perf.db");
        String url = System.getenv("LAWDESK_TEST_DB_URL");
        if (name != null && url != null) {
            String jdbc = url.substring(0, url.lastIndexOf('/') + 1) + name;
            registry.add("spring.datasource.url", () -> jdbc);
        }
    }

    @Test
    void measure() throws Exception {
        int cases = Integer.getInteger("lawdesk.perf.cases", 50_000);
        int runs = Integer.getInteger("lawdesk.perf.runs", 15);
        long seedStart = System.nanoTime();
        String seed = Files.readString(Path.of("../tools/performance/wp8/seed-wp8.sql"), StandardCharsets.UTF_8)
                .replace("${cases}", Integer.toString(cases));
        if (System.getProperty("lawdesk.perf.db") == null) {
            jdbc.execute(seed);
        }
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
        Permission[] everything = {Permission.CASE_VIEW, Permission.REPORT_VIEW, Permission.REPORT_EXPORT,
                Permission.LIBRARY_VIEW, Permission.SEARCH_ALL, Permission.CLAIM_VIEW, Permission.DEFENSE_VIEW,
                Permission.HEARING_VIEW, Permission.JUDGMENT_VIEW, Permission.DOCUMENT_VIEW, Permission.TASK_VIEW,
                Permission.PARTY_ID_VIEW};
        List<Actor> users = List.of(
                new Actor("manager (department-wide)", scoped(UUID.randomUUID(), "manager", List.of("ALL"), permissions), false),
                new Actor("member (own cases only)", scoped(member, "member", List.of(), permissions), false),
                new Actor("no scope", scoped(UUID.randomUUID(), "nobody", List.of(), permissions), false),
                new Actor("manager with SEARCH_ALL", scoped(UUID.randomUUID(), "searcher", List.of("ALL"), everything), true));

        List<String[]> endpoints = new ArrayList<>(List.of(
                new String[] {"case list, first page", "/api/v1/cases?size=20"},
                new String[] {"case list, deep page", "/api/v1/cases?size=20&page=100"},
                new String[] {"case list, text filter", "/api/v1/cases?q=SYNTH-0042&size=20"},
                new String[] {"case list, status + type filter", "/api/v1/cases?status=ACTIVE&caseType=CIVIL&size=20"},
                new String[] {"unified search, reference", "/api/v1/search?q=SYNTH-004213"},
                new String[] {"unified search, words", "/api/v1/search?q=Contract%20dispute"},
                new String[] {"case inventory report", "/api/v1/reports/cases?size=20"},
                new String[] {"case inventory report, text filter", "/api/v1/reports/cases?q=SYNTH-0042&size=20"},
                new String[] {"dashboard (with the 16 indicator figures)", "/api/v1/reports/dashboard"},
                new String[] {"indicator figures", "/api/v1/reports/indicators"}));
        for (IndicatorCode code : IndicatorCode.values()) {
            endpoints.add(new String[] {"indicator list " + code, "/api/v1/reports/indicators/" + code + "?size=20"});
        }
        for (String family : ReportFamilies.CATALOG.keySet()) {
            endpoints.add(new String[] {"report family " + family, "/api/v1/reports/families/" + family});
        }

        String loadBefore = load();
        List<Measurement> results = new ArrayList<>();
        String only = System.getProperty("lawdesk.perf.only", ".*");
        for (Actor user : users) {
            for (String[] endpoint : endpoints) {
                boolean search = endpoint[0].startsWith("unified search");
                if (!endpoint[0].matches(only) || user.searchOnly() && !search) {
                    continue;
                }
                results.add(time(user, endpoint[0], endpoint[1], runs));
            }
        }
        write(cases, total, children, counts, seedMs, results, loadBefore, load());
    }

    private Measurement time(Actor user, String name, String url, int runs) throws Exception {
        long warmStart = System.nanoTime();
        int httpStatus = mvc.perform(get(url).with(user.post())).andReturn().getResponse().getStatus();
        long warm = (System.nanoTime() - warmStart) / 1_000_000;
        if (warm > SLOW_MS) {
            // Far over the target: one measurement is evidence enough; do not spend minutes repeating it.
            return new Measurement(user.name(), name, url, warm, warm, warm, warm, -1, -1, -1, httpStatus, List.of(warm));
        }
        List<Long> times = new ArrayList<>();
        int statements = 0;
        for (int i = 0; i < runs; i++) {
            SqlStatementCounter.reset();
            long start = System.nanoTime();
            httpStatus = mvc.perform(get(url).with(user.post())).andReturn().getResponse().getStatus();
            times.add((System.nanoTime() - start) / 1_000_000);
            statements = SqlStatementCounter.count();
        }
        List<Long> sorted = new ArrayList<>(times);
        Collections.sort(sorted);
        int clients = Integer.getInteger("lawdesk.perf.clients", 8);
        long p50 = percentile(sorted, 50);
        // Requests this slow are already far over the target; eight at once would only take minutes to say so again.
        List<Long> parallel = p50 > 3_000 ? List.of(-1L) : concurrent(user, url, clients, Integer.getInteger("lawdesk.perf.crun", 5));
        return new Measurement(user.name(), name, url, sorted.get(0), percentile(sorted, 50), percentile(sorted, 95),
                sorted.get(sorted.size() - 1), statements, percentile(parallel, 50), percentile(parallel, 95), httpStatus,
                times);
    }

    /** {@code clients} parallel clients, each sending {@code crun} (default 5) requests; the sorted latency of every request. */
    private List<Long> concurrent(Actor user, String url, int clients, int runs) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(clients);
        try {
            List<Future<List<Long>>> futures = new ArrayList<>();
            for (int c = 0; c < clients; c++) {
                Callable<List<Long>> client = () -> {
                    List<Long> own = new ArrayList<>();
                    for (int i = 0; i < runs; i++) {
                        long start = System.nanoTime();
                        mvc.perform(get(url).with(user.post())).andReturn();
                        own.add((System.nanoTime() - start) / 1_000_000);
                    }
                    return own;
                };
                futures.add(pool.submit(client));
            }
            List<Long> all = new ArrayList<>();
            for (Future<List<Long>> f : futures) {
                all.addAll(f.get());
            }
            Collections.sort(all);
            return all;
        } finally {
            pool.shutdown();
        }
    }

    /** Nearest-rank percentile of an ascending list. */
    private static long percentile(List<Long> sorted, int p) {
        int index = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    private static String load() {
        try {
            return Files.readString(Path.of("/proc/loadavg")).split(" ")[0] + " (1 min, " + Runtime.getRuntime().availableProcessors()
                    + " CPUs)";
        } catch (IOException e) {
            return "unknown";
        }
    }

    private void write(int cases, long total, long children, List<String> counts, long seedMs, List<Measurement> results,
            String loadBefore, String loadAfter) throws IOException {
        int runs = Integer.getInteger("lawdesk.perf.runs", 15);
        int clients = Integer.getInteger("lawdesk.perf.clients", 8);
        StringBuilder out = new StringBuilder();
        out.append("# Performance run (LD-058)\n\n");
        out.append("Synthetic data only (SYNTH), throwaway embedded PostgreSQL, real endpoints through MockMvc with the access functions in the query path.\n\n");
        out.append("- code version (git SHA): ").append(System.getProperty("lawdesk.perf.sha", "not given")).append("\n");
        out.append("- cases: ").append(total).append(" (requested ").append(cases).append(")\n");
        out.append("- child rows (").append(String.join(", ", counts)).append("): ").append(children).append("\n");
        out.append("- seeding took ").append(seedMs).append(" ms\n");
        out.append("- host load average at start / end of the measurement: ").append(loadBefore).append(" / ").append(loadAfter).append("\n");
        out.append("- sequential: one warm-up (a request slower than ").append(SLOW_MS).append(" ms is reported from that single run), then ")
                .append(runs).append(" timed runs; p50 and p95 are nearest-rank percentiles; SQL = statements sent per request\n");
        out.append("- concurrent: ").append(clients).append(" parallel clients x ").append(Integer.getInteger("lawdesk.perf.crun", 5)).append(" requests each; p50 and p95 over all ")
                .append(clients * Integer.getInteger("lawdesk.perf.crun", 5)).append(" requests\n");
        out.append("- target: ").append(TARGET_MS).append(" ms (list and search), judged on the sequential p95\n\n");
        out.append("| user | request | HTTP | SQL | min | p50 | p95 | max | conc p50 | conc p95 | target |\n|---|---|---|---|---|---|---|---|---|---|---|\n");
        long missed = 0;
        StringBuilder csv = new StringBuilder("user,request,run,ms\n");
        for (Measurement m : results) {
            boolean ok = m.p95 <= TARGET_MS && m.status == 200;
            if (!ok) {
                missed++;
            }
            out.append("| ").append(m.user).append(" | ").append(m.name).append(" | ").append(m.status).append(" | ")
                    .append(m.statements < 0 ? "-" : String.valueOf(m.statements)).append(" | ").append(m.min).append(" | ")
                    .append(m.p50).append(" | ").append(m.p95).append(" | ").append(m.max).append(" | ")
                    .append(m.cp50 < 0 ? "-" : String.valueOf(m.cp50)).append(" | ").append(m.cp95 < 0 ? "-" : String.valueOf(m.cp95))
                    .append(" | ").append(ok ? "met" : "MISSED").append(" |\n");
            for (int i = 0; i < m.raw.size(); i++) {
                csv.append('"').append(m.user).append("\",\"").append(m.name).append("\",").append(i).append(',').append(m.raw.get(i)).append('\n');
            }
        }
        out.append("\nRequests over the target (sequential p95): ").append(missed).append(" of ").append(results.size()).append("\n");
        Path file = Path.of(System.getProperty("lawdesk.perf.out", "target/wp8-performance.md"));
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        Files.writeString(file, out.toString(), StandardCharsets.UTF_8);
        Files.writeString(Path.of(file.toString().replaceAll("\\.md$", "") + ".raw.csv"), csv.toString(), StandardCharsets.UTF_8);
        System.out.println(out);
        if (Boolean.getBoolean("lawdesk.perf.enforce") && missed > 0) {
            throw new AssertionError(missed + " requests missed the target");
        }
    }
}
