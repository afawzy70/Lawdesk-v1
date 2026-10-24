package com.lawdesk.proceedings;

import static com.lawdesk.security.TestActors.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.lawdesk.security.Permission;

/** LD-023 special decisions and enforcement suspension (docs/delivery/PHASE-4-ACCEPTANCE.md): QA-0177…QA-0183. */
class SpecialDecisionApiTest extends JudicialApiTestSupport {

    private record Appealed(UUID caseId, UUID firstInstance, UUID firstJudgment, UUID appeal) {
    }

    /** A first-instance judgment under appeal: the usual setting of a request to suspend its enforcement. */
    private Appealed appealed(String suffix) throws Exception {
        UUID caseId = createCase("Suspension " + suffix);
        UUID first = recordProceeding(caseId, proceeding("FIRST_INSTANCE", createCourt("FI-S" + suffix,
                "FIRST_INSTANCE"), "1", 2025, "2025-01-10", null));
        UUID judgment = recordJudgment(first, "2025-03-01");
        UUID appeal = recordProceeding(caseId, proceeding("APPEAL", createCourt("AP-S" + suffix, "APPEAL"), "2", 2025,
                "2025-03-20", first));
        return new Appealed(caseId, first, judgment, appeal);
    }

    static String decision(String type, String date, String effect, String summary, UUID judgmentId) {
        return """
                {"decisionType":%s,"decisionDate":%s,"effect":%s,"summary":%s,"judgmentId":%s}"""
                .formatted(quote(type), quote(date), quote(effect), quote(summary), quote(judgmentId));
    }

    private MockHttpServletRequestBuilder postDecision(UUID proceedingId, String json) {
        return post("/api/v1/proceedings/" + proceedingId + "/special-decisions")
                .with(as(CLERK, "clerk", Permission.JUDGMENT_MANAGE)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private UUID recordDecision(UUID proceedingId, String json) throws Exception {
        MvcResult result = mvc.perform(postDecision(proceedingId, json)).andExpect(status().isCreated()).andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    // QA-0177 authorized happy path

    @Test
    void recordsAnEnforcementSuspensionOfTheAppealedJudgment() throws Exception {
        Appealed a = appealed("1");
        MvcResult result = mvc.perform(postDecision(a.appeal(), decision("ENFORCEMENT_SUSPENSION", "2025-04-02",
                        "GRANTED", "  Enforcement suspended pending the appeal. ", a.firstJudgment())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.proceedingId").value(a.appeal().toString()))
                .andExpect(jsonPath("$.caseId").value(a.caseId().toString()))
                .andExpect(jsonPath("$.decisionType").value("ENFORCEMENT_SUSPENSION"))
                .andExpect(jsonPath("$.decisionDate").value("2025-04-02"))
                .andExpect(jsonPath("$.effect").value("GRANTED"))
                .andExpect(jsonPath("$.summary").value("Enforcement suspended pending the appeal."))
                .andExpect(jsonPath("$.judgmentId").value(a.firstJudgment().toString()))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn();
        String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        assertThat(result.getResponse().getHeader("Location")).endsWith("/api/v1/special-decisions/" + id);
        assertThat(jdbc.queryForObject("select created_by from special_decision where id = ?::uuid", UUID.class, id))
                .isEqualTo(CLERK);
        verify(caseLookup).recordEvent(eq(a.caseId()), eq("SPECIAL_DECISION_RECORDED"),
                contains("ENFORCEMENT_SUSPENSION"));

        mvc.perform(get("/api/v1/special-decisions/" + id).with(as(Permission.JUDGMENT_VIEW)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.effect").value("GRANTED"));
    }

    @Test
    void otherDecisionsNeedNoJudgment() throws Exception {
        Appealed a = appealed("2");
        mvc.perform(postDecision(a.firstInstance(), decision("URGENT_MEASURE", "2025-02-01", "REFUSED", null, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.judgmentId").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.summary").value(Matchers.nullValue()));
        mvc.perform(postDecision(a.appeal(), decision("OTHER", "2025-04-01", "PARTIALLY_GRANTED", "x", null)))
                .andExpect(status().isCreated());
    }

    @Test
    void listsTheDecisionsOfOneCaseByDate() throws Exception {
        Appealed a = appealed("3");
        UUID later = recordDecision(a.appeal(), decision("OTHER", "2025-05-01", "GRANTED", null, null));
        UUID earlier = recordDecision(a.firstInstance(), decision("URGENT_MEASURE", "2025-02-01", "GRANTED", null,
                null));
        Appealed elsewhere = appealed("4");
        recordDecision(elsewhere.appeal(), decision("OTHER", "2025-05-01", "GRANTED", null, null));

        mvc.perform(get("/api/v1/cases/" + a.caseId() + "/special-decisions").with(as(Permission.JUDGMENT_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id").value(Matchers.contains(earlier.toString(), later.toString())))
                .andExpect(jsonPath("$.totalItems").value(2));
        mvc.perform(get("/api/v1/cases/" + UUID.randomUUID() + "/special-decisions")
                .with(as(Permission.JUDGMENT_VIEW))).andExpect(status().isNotFound());
    }

    @Test
    void updatesADecision() throws Exception {
        Appealed a = appealed("5");
        UUID id = recordDecision(a.appeal(), decision("ENFORCEMENT_SUSPENSION", "2025-04-02", "REFUSED", null,
                a.firstJudgment()));
        mvc.perform(put("/api/v1/special-decisions/" + id).with(as(Permission.JUDGMENT_MANAGE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decisionType":"ENFORCEMENT_SUSPENSION","decisionDate":"2025-04-03","effect":"GRANTED",
                                 "judgmentId":"%s","version":0}""".formatted(a.firstJudgment())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effect").value("GRANTED"))
                .andExpect(jsonPath("$.decisionDate").value("2025-04-03"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void unknownRecordsAreNotFound() throws Exception {
        mvc.perform(postDecision(UUID.randomUUID(), decision("OTHER", "2025-01-01", "GRANTED", null, null)))
                .andExpect(status().isNotFound()).andExpect(content().contentTypeCompatibleWith(PROBLEM));
        Appealed a = appealed("6");
        mvc.perform(postDecision(a.appeal(), decision("ENFORCEMENT_SUSPENSION", "2025-04-01", "GRANTED", null,
                        UUID.randomUUID())))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/special-decisions/" + UUID.randomUUID()).with(as(Permission.JUDGMENT_VIEW)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/special-decisions/nope").with(as(Permission.JUDGMENT_VIEW)))
                .andExpect(status().isBadRequest());
        assertThat(rows("special_decision")).isZero();
    }

    // QA-0178 required-field validation; QA-0179 invalid input boundary

    @Test
    void requiredFieldsAndBoundariesAreValidated() throws Exception {
        Appealed a = appealed("7");
        mvc.perform(postDecision(a.appeal(), "{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'decisionType')].code").value(Matchers.hasItem("NotNull")))
                .andExpect(jsonPath("$.errors[?(@.field == 'decisionDate')].code").value(Matchers.hasItem("NotNull")))
                .andExpect(jsonPath("$.errors[?(@.field == 'effect')].code").value(Matchers.hasItem("NotNull")));
        mvc.perform(postDecision(a.appeal(), decision("OTHER", LocalDate.now().plusDays(2).toString(), "GRANTED",
                        "s".repeat(4001), null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'decisionDate')].code")
                        .value(Matchers.hasItem("PastOrPresent")))
                .andExpect(jsonPath("$.errors[?(@.field == 'summary')].code").value(Matchers.hasItem("Size")));
        mvc.perform(postDecision(a.appeal(), decision("STAY", "2025-04-01", "GRANTED", null, null)))
                .andExpect(status().isBadRequest());
        mvc.perform(postDecision(a.appeal(), decision("OTHER", "2025-04-01", "MAYBE", null, null)))
                .andExpect(status().isBadRequest());
        assertThat(rows("special_decision")).isZero();
        mvc.perform(postDecision(a.appeal(), decision("OTHER", "2025-04-01", "GRANTED", "s".repeat(4000), null)))
                .andExpect(status().isCreated());
    }

    @Test
    void aSuspensionNamesAJudgmentOfTheSameCase() throws Exception {
        Appealed a = appealed("8");
        Appealed other = appealed("9");
        mvc.perform(postDecision(a.appeal(), decision("ENFORCEMENT_SUSPENSION", "2025-04-01", "GRANTED", null, null)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("SUSPENSION_REQUIRES_JUDGMENT"));
        mvc.perform(postDecision(a.appeal(), decision("ENFORCEMENT_SUSPENSION", "2025-04-01", "GRANTED", null,
                        other.firstJudgment())))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("DECISION_JUDGMENT_OTHER_CASE"));
        mvc.perform(postDecision(a.appeal(), decision("OTHER", "2025-04-01", "GRANTED", null,
                        other.firstJudgment())))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("DECISION_JUDGMENT_OTHER_CASE"));
        assertThat(rows("special_decision")).isZero();
    }

    @Test
    void aDecisionCannotPrecedeTheFiling() throws Exception {
        Appealed a = appealed("10");
        mvc.perform(postDecision(a.appeal(), decision("OTHER", "2025-03-19", "GRANTED", null, null)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("DECISION_BEFORE_FILING"));
    }

    // QA-0180 unauthenticated access; QA-0181 insufficient permission

    @Test
    void everyEndpointNeedsASessionAndThePermission() throws Exception {
        Appealed a = appealed("11");
        UUID id = recordDecision(a.appeal(), decision("OTHER", "2025-04-01", "GRANTED", null, null));
        String body = decision("OTHER", "2025-04-01", "GRANTED", null, null);

        mvc.perform(get("/api/v1/special-decisions/" + id)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/cases/" + a.caseId() + "/special-decisions")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/proceedings/" + a.appeal() + "/special-decisions").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());

        mvc.perform(get("/api/v1/special-decisions/" + id).with(as(Permission.PROCEEDING_VIEW)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/cases/" + a.caseId() + "/special-decisions").with(as(Permission.PROCEEDING_MANAGE)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/proceedings/" + a.appeal() + "/special-decisions")
                        .with(as(Permission.PROCEEDING_MANAGE, Permission.JUDGMENT_VIEW)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden()).andExpect(content().contentTypeCompatibleWith(PROBLEM));
        mvc.perform(put("/api/v1/special-decisions/" + id).with(as(Permission.JUDGMENT_VIEW)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decisionType":"OTHER","decisionDate":"2025-04-01","effect":"REFUSED","version":0}"""))
                .andExpect(status().isForbidden());
        assertThat(rows("special_decision")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select effect from special_decision where id = ?::uuid", String.class, id))
                .isEqualTo("GRANTED");
    }

    // QA-0182 record integrity and rollback; QA-0183 concurrent or repeat request

    @Test
    void aFailingTimelineEventRollsTheDecisionBack() throws Exception {
        Appealed a = appealed("12");
        doThrow(new IllegalStateException("timeline unavailable")).when(caseLookup)
                .recordEvent(eq(a.caseId()), eq("SPECIAL_DECISION_RECORDED"), anyString());
        mvc.perform(postDecision(a.appeal(), decision("OTHER", "2025-04-01", "GRANTED", null, null)))
                .andExpect(status().isInternalServerError());
        assertThat(rows("special_decision")).isZero();
    }

    @Test
    void aStaleVersionIsAConflict() throws Exception {
        Appealed a = appealed("13");
        UUID id = recordDecision(a.appeal(), decision("OTHER", "2025-04-01", "GRANTED", null, null));
        String update = """
                {"decisionType":"OTHER","decisionDate":"2025-04-01","effect":"%s","version":0}""";
        mvc.perform(put("/api/v1/special-decisions/" + id).with(as(Permission.JUDGMENT_MANAGE)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(update.formatted("REFUSED")))
                .andExpect(status().isOk());
        mvc.perform(put("/api/v1/special-decisions/" + id).with(as(Permission.JUDGMENT_MANAGE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(update.formatted("GRANTED")))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select effect from special_decision where id = ?::uuid", String.class, id))
                .isEqualTo("REFUSED");
    }

    @Test
    void repeatedDecisionsAreKeptAsSeparateRecords() throws Exception {
        Appealed a = appealed("14");
        String refused = decision("ENFORCEMENT_SUSPENSION", "2025-04-01", "REFUSED", null, a.firstJudgment());
        recordDecision(a.appeal(), refused);
        recordDecision(a.appeal(), decision("ENFORCEMENT_SUSPENSION", "2025-05-01", "GRANTED", null,
                a.firstJudgment()));
        assertThat(rows("special_decision")).isEqualTo(2);
    }
}
