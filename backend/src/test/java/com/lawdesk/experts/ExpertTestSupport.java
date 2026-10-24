package com.lawdesk.experts;

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
 * Shared setup for the LD-025 expert tests: cases, courts and proceedings through the Phase 3 and 4 APIs, experts and
 * referrals through the LD-025 API. The configuration equals the other API tests', so they share a context and
 * database: expert rows go first after each test, then the proceedings and courts they reference.
 */
@SpringBootTest
@AutoConfigureMockMvc
abstract class ExpertTestSupport extends PostgresTestSupport {

    static final UUID CLERK = UUID.fromString("00000000-0000-4000-8000-0000000000a5");
    static final String FILED = "2025-01-10";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoSpyBean
    CaseLookup caseLookup;

    private int courts;

    @AfterEach
    void removeExpertRecords() {
        jdbc.update("delete from expert_session");
        jdbc.update("delete from expert_referral");
        jdbc.update("delete from expert");
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

    static String quote(Object value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    UUID createCase(String title) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/cases").with(as(Permission.CASE_CREATE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","caseType":"CIVIL","openedOn":"2024-01-10"}""".formatted(title)))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    UUID createProceeding(UUID caseId, String number) throws Exception {
        String code = "EX-" + (++courts) + "-" + number;
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

    static String expert(String name, String specialty, String office, String phone) {
        return """
                {"name":%s,"specialty":%s,"office":%s,"phone":%s}""".formatted(quote(name), quote(specialty),
                quote(office), quote(phone));
    }

    MockHttpServletRequestBuilder postExpert(String json) {
        return post("/api/v1/experts").with(as(CLERK, "clerk", Permission.EXPERT_MANAGE)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json);
    }

    UUID createExpert(String name) throws Exception {
        MvcResult result = mvc.perform(postExpert(expert(name, "Engineering", "Giza experts office", null)))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    static String referral(UUID expertId, Object referralDate, String mission, Object reportDueDate) {
        return """
                {"expertId":%s,"referralDate":%s,"mission":%s,"reportDueDate":%s}""".formatted(quote(expertId),
                quote(referralDate), quote(mission), quote(reportDueDate));
    }

    MockHttpServletRequestBuilder postReferral(UUID proceedingId, String json) {
        return post("/api/v1/proceedings/" + proceedingId + "/expert-referrals")
                .with(as(CLERK, "clerk", Permission.EXPERT_MANAGE)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json);
    }

    UUID refer(UUID proceedingId, UUID expertId, Object referralDate, Object reportDueDate) throws Exception {
        MvcResult result = mvc.perform(postReferral(proceedingId, referral(expertId, referralDate, null,
                        reportDueDate)))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    MockHttpServletRequestBuilder action(UUID referralId, String what, String json) {
        return post("/api/v1/expert-referrals/" + referralId + "/" + what)
                .with(as(CLERK, "clerk", Permission.EXPERT_MANAGE)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json);
    }

    String statusOf(UUID referralId) {
        return jdbc.queryForObject("select status from expert_referral where id = ?", String.class, referralId);
    }

    int rows(String table) {
        Integer count = jdbc.queryForObject("select count(*) from " + table, Integer.class);
        return count == null ? 0 : count;
    }
}
