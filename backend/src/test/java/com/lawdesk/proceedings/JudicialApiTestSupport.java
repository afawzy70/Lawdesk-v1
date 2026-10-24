package com.lawdesk.proceedings;

import static com.lawdesk.security.TestActors.as;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
}
