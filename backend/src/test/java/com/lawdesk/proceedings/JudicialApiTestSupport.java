package com.lawdesk.proceedings;

import static com.lawdesk.security.TestActors.as;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.lawdesk.cases.CaseLookup;
import com.lawdesk.platform.PostgresTestSupport;
import com.lawdesk.security.Permission;

/**
 * Shared setup for the Phase 4 proceeding tests. Cases are created through Workstream B's API and read through the real
 * {@link CaseLookup}, which is spied on so tests can check (or break) the timeline events. Courts come from the LD-018
 * API. Only Phase 4 tables are cleaned up; B's rows stay, each test uses its own case.
 */
@SpringBootTest
@AutoConfigureMockMvc
abstract class JudicialApiTestSupport extends PostgresTestSupport {

    static final MediaType PROBLEM = MediaType.APPLICATION_PROBLEM_JSON;
    static final UUID CLERK = UUID.fromString("00000000-0000-4000-8000-0000000000c1");

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoSpyBean
    CaseLookup caseLookup;

    @AfterEach
    void removeJudicialRecords() {
        dropPause();
        jdbc.update("delete from special_decision");
        jdbc.update("delete from judgment");
        // Children before parents: cassations attack appeals, appeals attack first-instance proceedings.
        jdbc.update("delete from proceeding where stage = 'CASSATION'");
        jdbc.update("delete from proceeding where stage = 'APPEAL'");
        jdbc.update("delete from proceeding");
        jdbc.update("delete from court");
    }

    UUID createCase(String title) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/cases").with(as(Permission.CASE_CREATE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","caseType":"CIVIL","openedOn":"2024-01-10"}""".formatted(title)))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    UUID createCourt(String code, String level) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/courts").with(as(Permission.COURT_MANAGE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","nameAr":"محكمة %s","nameEn":"Court %s","level":"%s"}"""
                                .formatted(code, code, code, level)))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    void deactivateCourt(UUID court, String code, String level) throws Exception {
        mvc.perform(put("/api/v1/courts/" + court).with(as(Permission.COURT_MANAGE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","nameAr":"محكمة","nameEn":"Court","level":"%s","active":false,"version":0}"""
                                .formatted(code, level)))
                .andExpect(status().isOk());
    }

    static String proceeding(String stage, UUID court, String number, Integer year, String filingDate, UUID parent) {
        return """
                {"stage":%s,"courtId":%s,"courtCaseNumber":%s,"courtCaseYear":%s,"filingDate":%s,
                 "parentProceedingId":%s}""".formatted(quote(stage), quote(court), quote(number),
                year == null ? "null" : year.toString(), quote(filingDate), quote(parent));
    }

    static String judgment(String date, String outcome, String summary, Boolean isFinal) {
        return """
                {"judgmentDate":%s,"outcome":%s,"summary":%s,"final":%s}""".formatted(quote(date), quote(outcome),
                quote(summary), isFinal == null ? "null" : isFinal.toString());
    }

    static String quote(Object value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    MockHttpServletRequestBuilder postProceeding(UUID caseId, String json) {
        return post("/api/v1/cases/" + caseId + "/proceedings")
                .with(as(CLERK, "clerk", Permission.PROCEEDING_MANAGE)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json);
    }

    MockHttpServletRequestBuilder postJudgment(UUID proceedingId, String json) {
        return post("/api/v1/proceedings/" + proceedingId + "/judgment")
                .with(as(CLERK, "clerk", Permission.JUDGMENT_MANAGE)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json);
    }

    UUID recordProceeding(UUID caseId, String json) throws Exception {
        MvcResult result = mvc.perform(postProceeding(caseId, json)).andExpect(status().isCreated()).andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    UUID recordJudgment(UUID proceedingId, String date) throws Exception {
        MvcResult result = mvc.perform(postJudgment(proceedingId, judgment(date, "AGAINST", null, false)))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    int rows(String table) {
        Integer count = jdbc.queryForObject("select count(*) from " + table, Integer.class);
        return count == null ? 0 : count;
    }

    /**
     * Runs {@code paused} until its {@code event} on {@code table} stops in a test-only trigger (QAD-024), then runs
     * {@code other} to completion while {@code paused} still holds its transaction open. Returns both statuses, so a
     * race between two writes is deterministic: without a row lock both succeed, with one the second waits and then
     * sees the first one's committed data.
     */
    List<Integer> race(String event, String table, Callable<MvcResult> paused, Callable<MvcResult> other)
            throws Exception {
        jdbc.execute("""
                create function qad024_pause() returns trigger language plpgsql as $$
                begin perform pg_sleep(1.5); return new; end $$""");
        jdbc.execute("create trigger qad024_pause before " + event + " on " + table
                + " for each row execute function qad024_pause()");
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<MvcResult> first = pool.submit(paused);
            awaitPause();
            Future<MvcResult> second = pool.submit(other);
            return List.of(first.get(30, TimeUnit.SECONDS).getResponse().getStatus(),
                    second.get(30, TimeUnit.SECONDS).getResponse().getStatus());
        } finally {
            dropPause();
        }
    }

    private void awaitPause() throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (Instant.now().isBefore(deadline)) {
            Integer sleeping = jdbc.queryForObject(
                    "select count(*) from pg_stat_activity where wait_event = 'PgSleep' and pid <> pg_backend_pid()",
                    Integer.class);
            if (sleeping != null && sleeping > 0) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("the paused request never reached its trigger");
    }

    private void dropPause() {
        for (String table : List.of("judgment", "proceeding", "special_decision")) {
            jdbc.execute("drop trigger if exists qad024_pause on " + table);
        }
        jdbc.execute("drop function if exists qad024_pause()");
    }
}
