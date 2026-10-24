package com.lawdesk.hearings;

import static com.lawdesk.security.TestActors.as;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

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
 * Shared setup for the LD-024 hearing tests. Cases, courts and proceedings are created through the APIs of Phases 3
 * and 4; {@link CaseLookup} is spied on so tests can check (or break) the timeline events. The configuration equals
 * the Phase 4 tests' one, so both share a Spring context and database: everything below the case is removed after
 * each test, hearings first because they reference proceedings.
 */
@SpringBootTest
@AutoConfigureMockMvc
abstract class HearingTestSupport extends PostgresTestSupport {

    static final MediaType PROBLEM = MediaType.APPLICATION_PROBLEM_JSON;
    static final UUID CLERK = UUID.fromString("00000000-0000-4000-8000-0000000000d1");
    static final String FILED = "2025-01-10";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoSpyBean
    CaseLookup caseLookup;

    private int courts;

    @AfterEach
    void removeOperationsRecords() {
        dropPause();
        jdbc.update("delete from hearing where adjourned_from_id is not null");
        jdbc.update("delete from hearing");
        jdbc.update("delete from special_decision");
        jdbc.update("delete from judgment");
        jdbc.update("delete from proceeding where stage = 'CASSATION'");
        jdbc.update("delete from proceeding where stage = 'APPEAL'");
        jdbc.update("delete from proceeding");
        jdbc.update("delete from court");
    }

    static LocalDate today() {
        return LocalDate.now(ZoneId.of("Africa/Cairo"));
    }

    UUID createCase(String title) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/cases").with(as(Permission.CASE_CREATE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","caseType":"CIVIL","openedOn":"2024-01-10"}""".formatted(title)))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    String referenceOf(UUID caseId) {
        return jdbc.queryForObject("select reference_no from legal_case where id = ?", String.class, caseId);
    }

    /** A first-instance proceeding "number/2025" of the case, filed on {@link #FILED}, at a new court. */
    UUID createProceeding(UUID caseId, String number) throws Exception {
        String code = "HC-" + (++courts) + "-" + number;
        MvcResult court = mvc.perform(post("/api/v1/courts").with(as(Permission.COURT_MANAGE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","nameAr":"محكمة %s","nameEn":"Court %s","level":"FIRST_INSTANCE"}"""
                                .formatted(code, code, code)))
                .andExpect(status().isCreated()).andReturn();
        String courtId = JsonPath.read(court.getResponse().getContentAsString(), "$.id");
        MvcResult result = mvc.perform(post("/api/v1/cases/" + caseId + "/proceedings")
                        .with(as(Permission.PROCEEDING_MANAGE)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stage":"FIRST_INSTANCE","courtId":"%s","courtCaseNumber":"%s","courtCaseYear":2025,
                                 "filingDate":"%s"}""".formatted(courtId, number, FILED)))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    void archive(UUID caseId) throws Exception {
        for (String[] step : new String[][] {{"CLOSED", "0"}, {"ARCHIVED", "1"}}) {
            mvc.perform(post("/api/v1/cases/" + caseId + "/status").with(as(Permission.CASE_STATUS_CHANGE))
                            .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"status":"%s","version":%s}""".formatted(step[0], step[1])))
                    .andExpect(status().isOk());
        }
    }

    static String hearing(Object date, String time, String purpose, String notes) {
        return """
                {"hearingDate":%s,"hearingTime":%s,"purpose":%s,"notes":%s}""".formatted(quote(date), quote(time),
                quote(purpose), quote(notes));
    }

    static String quote(Object value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    MockHttpServletRequestBuilder postHearing(UUID proceedingId, String json) {
        return post("/api/v1/proceedings/" + proceedingId + "/hearings")
                .with(as(CLERK, "clerk", Permission.HEARING_MANAGE)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json);
    }

    MockHttpServletRequestBuilder postResult(UUID hearingId, String json) {
        return post("/api/v1/hearings/" + hearingId + "/result")
                .with(as(CLERK, "clerk", Permission.HEARING_MANAGE)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json);
    }

    UUID schedule(UUID proceedingId, Object date) throws Exception {
        return schedule(proceedingId, date, null);
    }

    UUID schedule(UUID proceedingId, Object date, String time) throws Exception {
        MvcResult result = mvc.perform(postHearing(proceedingId, hearing(date, time, "PLEADING", null)))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    String statusOf(UUID hearingId) {
        return jdbc.queryForObject("select status from hearing where id = ?", String.class, hearingId);
    }

    int rows(String table) {
        Integer count = jdbc.queryForObject("select count(*) from " + table, Integer.class);
        return count == null ? 0 : count;
    }

    /** A test-only trigger that holds the first update of {@code hearing} inside its transaction for 1.5 s. */
    void pauseHearingUpdates() {
        jdbc.execute("""
                create function ld024_pause() returns trigger language plpgsql as $$
                begin perform pg_sleep(1.5); return new; end $$""");
        jdbc.execute("create trigger ld024_pause before update on hearing for each row execute function ld024_pause()");
    }

    void dropPause() {
        jdbc.execute("drop trigger if exists ld024_pause on hearing");
        jdbc.execute("drop function if exists ld024_pause()");
    }
}
