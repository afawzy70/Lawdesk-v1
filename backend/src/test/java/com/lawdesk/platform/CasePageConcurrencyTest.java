package com.lawdesk.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.lawdesk.security.Browser;
import com.lawdesk.security.TestUsers;

/**
 * QA-RT-011: opening a case page makes the SPA fire dozens of GETs at once. Every GET handler of the case page (all
 * {@code /api/v1/cases/{id}/...} reads without further variables, plus the lookups the page loads) is sent truly
 * simultaneously, several rounds, over real HTTP with the production Hikari settings (application.yaml: default pool of
 * 10, 3 s connection timeout; the pool is NOT raised here). No response may be a 5xx.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestUsers.class)
class CasePageConcurrencyTest extends PostgresTestSupport {

    private static final int ROUNDS = 5;
    /** The page asks for some things more than once (lookups per widget); this brings the burst to the observed ~80. */
    private static final int COPIES = 2;

    @LocalServerPort
    private int port;

    @Autowired
    private TestUsers users;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mappings;

    @AfterEach
    void clean() {
        OperationsTestData.deleteAll(jdbc);
        jdbc.update("delete from party");
    }

    @Test
    void openingACasePageNeverAnswersWithAServerError() throws Exception {
        TestUsers.Created manager = users.create("LEGAL_MANAGER");
        UUID caseId = seedCase(manager.id());
        Browser browser = new Browser(port);
        assertThat(browser.login(manager.username(), TestUsers.PASSWORD).statusCode()).isEqualTo(200);
        String cookie = "JSESSIONID=" + browser.cookie("JSESSIONID") + "; XSRF-TOKEN=" + browser.cookie("XSRF-TOKEN");

        List<String> paths = casePagePaths(caseId);
        assertThat(paths.size()).as("case page requests").isGreaterThan(35);

        // Sequential control: every request is fine on its own (this is what the defect report observed too).
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        Map<String, Integer> sequential = new TreeMap<>();
        for (String path : paths) {
            int status = get(client, path, cookie).statusCode();
            if (status >= 500) {
                sequential.put(path, status);
            }
        }
        assertThat(sequential).as("sequential 5xx").isEmpty();

        List<String> burst = new ArrayList<>();
        for (int i = 0; i < COPIES; i++) {
            burst.addAll(paths);
        }
        Map<String, String> failures = new TreeMap<>();
        int total = 0;
        for (int round = 0; round < ROUNDS; round++) {
            // A fresh client per round: new connections, nothing warmed, like a browser opening the page.
            HttpClient fresh = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            CountDownLatch go = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(burst.size());
            List<CompletableFuture<HttpResponse<String>>> calls = new ArrayList<>();
            for (String path : burst) {
                calls.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        go.await();
                        return get(fresh, path, cookie);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    }
                }, executor));
            }
            go.countDown();
            for (int i = 0; i < burst.size(); i++) {
                HttpResponse<String> response = calls.get(i).get();
                total++;
                if (response.statusCode() >= 500) {
                    failures.merge("round " + round + " " + response.statusCode() + " " + burst.get(i), response.body(),
                            (a, b) -> a);
                }
            }
            executor.shutdown();
        }
        System.out.println("CASE-PAGE-CONCURRENCY requests=" + total + " failures=" + failures.size());
        assertThat(failures).as("5xx among %d simultaneous case page requests", total).isEmpty();
    }

    private static HttpResponse<String> get(HttpClient client, String path, String cookie) {
        try {
            return client.send(HttpRequest.newBuilder(URI.create(path)).header("Accept", "application/json")
                    .header("Cookie", cookie).timeout(Duration.ofSeconds(30)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /** Every parameterless GET read of the case, plus the lookups the case page loads. */
    private List<String> casePagePaths(UUID caseId) {
        List<String> paths = new ArrayList<>();
        String base = "http://localhost:" + port;
        for (RequestMappingInfo info : mappings.getHandlerMethods().keySet()) {
            if (info.getPathPatternsCondition() == null || !info.getMethodsCondition().getMethods().contains(RequestMethod.GET)) {
                continue;
            }
            for (String pattern : info.getPathPatternsCondition().getPatternValues()) {
                if (pattern.matches("/api/v1/cases/\\{(id|caseId)}/[a-z-]+(/[a-z-]+)?")) {
                    paths.add(base + pattern.replaceFirst("\\{(id|caseId)}", caseId.toString()));
                }
            }
        }
        for (String lookup : List.of("/api/v1/users/directory", "/api/v1/org-units", "/api/v1/deadline-rules",
                "/api/v1/lists/CASE_TYPE", "/api/v1/lists/COURT_LEVEL", "/api/v1/lists/PARTY_ROLE",
                "/api/v1/assistant/outputs", "/api/v1/me", "/api/v1/courts", "/api/v1/tasks?caseId=" + caseId)) {
            paths.add(base + lookup);
        }
        return paths;
    }

    private UUID seedCase(UUID member) {
        UUID user = UUID.fromString("00000000-0000-4000-8000-0000000000b1");
        UUID legalCase = jdbc.queryForObject("""
                insert into legal_case (reference_no, title, case_type, opened_on, status, primary_member_id, created_by, updated_by)
                values ('CP-' || gen_random_uuid(), 'SYNTH-DOC page case', 'COMMERCIAL', '2026-01-10', 'OPEN', ?, ?, ?)
                returning id""", UUID.class, member, user, user);
        UUID court = jdbc.queryForObject("""
                insert into court (code, name_ar, name_en, level, active, created_by, updated_by)
                values ('CP-' || upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 8)), 'محكمة', 'Court',
                        'FIRST_INSTANCE', true, gen_random_uuid(), gen_random_uuid()) returning id""", UUID.class);
        UUID proceeding = jdbc.queryForObject("""
                insert into proceeding (case_id, stage, court_id, court_case_number, court_case_year, filing_date, created_by, updated_by)
                values (?, 'FIRST_INSTANCE', ?, '1', 2026, '2026-01-12', ?, ?) returning id""", UUID.class,
                legalCase, court, user, user);
        jdbc.update("""
                insert into judgment (proceeding_id, judgment_date, outcome, final, created_by, updated_by)
                values (?, '2026-02-01', 'PARTIAL', false, ?, ?)""", proceeding, user, user);
        jdbc.update("""
                insert into hearing (case_id, proceeding_id, hearing_date, purpose, created_by, updated_by)
                values (?, ?, '2026-12-01', 'FIRST_SESSION', ?, ?)""", legalCase, proceeding, user, user);
        jdbc.update("insert into claim (case_id, seq, text, kind, created_by, updated_by) values (?, 1, 'SYNTH-DOC', 'PRIMARY', ?, ?)",
                legalCase, user, user);
        jdbc.update("insert into task (case_id, title, assignee_id, created_by, updated_by) values (?, 'SYNTH-DOC', ?, ?, ?)",
                legalCase, member, user, user);
        jdbc.update("insert into case_note (case_id, body, created_by, updated_by) values (?, 'SYNTH-DOC', ?, ?)",
                legalCase, user, user);
        jdbc.update("""
                insert into case_document (case_id, title, file_name, size_bytes, sha256, created_by)
                values (?, 'SYNTH-DOC', 'f.pdf', 10, repeat('a', 64), ?)""", legalCase, user);
        jdbc.update("""
                insert into deadline (case_id, source_type, source_text, action, due_on, calc_status, created_by, updated_by)
                values (?, 'TEXT', 'SYNTH-DOC', 'SYNTH-DOC', '2026-12-01', 'MANUAL', ?, ?)""", legalCase, user, user);
        UUID party = jdbc.queryForObject("insert into party (kind, name, created_by, updated_by) values ('PERSON', 'SYNTH-DOC', ?, ?) returning id",
                UUID.class, user, user);
        jdbc.update("insert into case_party (case_id, party_id, role, created_by) values (?, ?, 'CLIENT', ?)", legalCase, party, user);
        return legalCase;
    }
}
