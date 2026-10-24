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

import com.jayway.jsonpath.JsonPath;
import com.lawdesk.security.Permission;

/** LD-022 judgments and outcomes (docs/delivery/PHASE-4-ACCEPTANCE.md): QA-0169…QA-0175. */
class JudgmentApiTest extends JudicialApiTestSupport {

    private UUID firstInstance(String suffix, UUID caseId) throws Exception {
        UUID court = createCourt("FI-J" + suffix, "FIRST_INSTANCE");
        return recordProceeding(caseId, proceeding("FIRST_INSTANCE", court, "1", 2025, "2025-01-10", null));
    }

    // QA-0169 authorized happy path

    @Test
    void recordsAJudgmentAndATimelineEvent() throws Exception {
        UUID caseId = createCase("Judged");
        UUID proceeding = firstInstance("1", caseId);

        MvcResult result = mvc.perform(postJudgment(proceeding,
                        judgment("2025-04-20", "PARTIAL", "  Partly in favour: rent reduced.  ", true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.proceedingId").value(proceeding.toString()))
                .andExpect(jsonPath("$.caseId").value(caseId.toString()))
                .andExpect(jsonPath("$.judgmentDate").value("2025-04-20"))
                .andExpect(jsonPath("$.outcome").value("PARTIAL"))
                .andExpect(jsonPath("$.summary").value("Partly in favour: rent reduced."))
                .andExpect(jsonPath("$.final").value(true))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn();
        String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        assertThat(result.getResponse().getHeader("Location")).endsWith("/api/v1/judgments/" + id);
        assertThat(jdbc.queryForObject("select created_by from judgment where id = ?::uuid", UUID.class, id))
                .isEqualTo(CLERK);
        verify(caseLookup).recordEvent(eq(caseId), eq("JUDGMENT_RECORDED"), contains("2025-04-20"));

        mvc.perform(get("/api/v1/judgments/" + id).with(as(Permission.JUDGMENT_VIEW)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("PARTIAL"));
        mvc.perform(get("/api/v1/proceedings/" + proceeding).with(as(Permission.PROCEEDING_VIEW)))
                .andExpect(jsonPath("$.hasJudgment").value(true));
    }

    @Test
    void finalityDefaultsToFalseAndTheSummaryIsOptional() throws Exception {
        UUID proceeding = firstInstance("2", createCase("Defaults"));
        mvc.perform(postJudgment(proceeding, """
                        {"judgmentDate":"2025-04-20","outcome":"DISMISSED"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.final").value(false))
                .andExpect(jsonPath("$.summary").value(Matchers.nullValue()));
    }

    @Test
    void listsTheJudgmentsOfOneCase() throws Exception {
        UUID caseId = createCase("Listed");
        UUID p1 = firstInstance("3", caseId);
        UUID p2 = firstInstance("4", caseId);
        UUID j2 = recordJudgment(p2, "2025-03-01");
        UUID j1 = recordJudgment(p1, "2025-02-01");
        recordJudgment(firstInstance("5", createCase("Elsewhere")), "2025-02-01");

        mvc.perform(get("/api/v1/cases/" + caseId + "/judgments").with(as(Permission.JUDGMENT_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id").value(Matchers.contains(j1.toString(), j2.toString())))
                .andExpect(jsonPath("$.totalItems").value(2));
        mvc.perform(get("/api/v1/cases/" + UUID.randomUUID() + "/judgments").with(as(Permission.JUDGMENT_VIEW)))
                .andExpect(status().isNotFound());
    }

    @Test
    void updatesAJudgment() throws Exception {
        UUID proceeding = firstInstance("6", createCase("Corrected"));
        UUID id = recordJudgment(proceeding, "2025-03-01");
        UUID editor = UUID.randomUUID();
        mvc.perform(put("/api/v1/judgments/" + id).with(as(editor, "editor", Permission.JUDGMENT_MANAGE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"judgmentDate":"2025-03-02","outcome":"IN_FAVOUR","summary":"Corrected",
                                 "final":true,"version":0}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.judgmentDate").value("2025-03-02"))
                .andExpect(jsonPath("$.outcome").value("IN_FAVOUR"))
                .andExpect(jsonPath("$.final").value(true))
                .andExpect(jsonPath("$.version").value(1));
        assertThat(jdbc.queryForObject("select updated_by from judgment where id = ?::uuid", UUID.class, id))
                .isEqualTo(editor);
    }

    @Test
    void unknownProceedingsAndJudgmentsAreNotFound() throws Exception {
        mvc.perform(postJudgment(UUID.randomUUID(), judgment("2025-01-01", "OTHER", null, false)))
                .andExpect(status().isNotFound()).andExpect(content().contentTypeCompatibleWith(PROBLEM));
        mvc.perform(get("/api/v1/judgments/" + UUID.randomUUID()).with(as(Permission.JUDGMENT_VIEW)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/judgments/x").with(as(Permission.JUDGMENT_VIEW)))
                .andExpect(status().isBadRequest());
    }

    // QA-0170 required-field validation; QA-0171 invalid input boundary

    @Test
    void requiredFieldsAndBoundariesAreValidated() throws Exception {
        UUID proceeding = firstInstance("7", createCase("Validated"));
        mvc.perform(postJudgment(proceeding, "{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'judgmentDate')].code").value(Matchers.hasItem("NotNull")))
                .andExpect(jsonPath("$.errors[?(@.field == 'outcome')].code").value(Matchers.hasItem("NotNull")));
        mvc.perform(postJudgment(proceeding, judgment(LocalDate.now().plusDays(2).toString(), "AGAINST",
                        "s".repeat(4001), false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'judgmentDate')].code")
                        .value(Matchers.hasItem("PastOrPresent")))
                .andExpect(jsonPath("$.errors[?(@.field == 'summary')].code").value(Matchers.hasItem("Size")));
        mvc.perform(postJudgment(proceeding, judgment("2025-04-01", "WON", null, false)))
                .andExpect(status().isBadRequest());
        assertThat(rows("judgment")).isZero();
        mvc.perform(postJudgment(proceeding, judgment("2025-04-01", "OTHER", "s".repeat(4000), false)))
                .andExpect(status().isCreated());
    }

    @Test
    void aJudgmentCannotPrecedeTheFiling() throws Exception {
        UUID proceeding = firstInstance("8", createCase("Early"));
        mvc.perform(postJudgment(proceeding, judgment("2025-01-09", "AGAINST", null, false)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("JUDGMENT_BEFORE_FILING"));
        UUID id = recordJudgment(proceeding, "2025-01-10");
        mvc.perform(put("/api/v1/judgments/" + id).with(as(Permission.JUDGMENT_MANAGE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"judgmentDate":"2025-01-01","outcome":"AGAINST","final":false,"version":0}"""))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("JUDGMENT_BEFORE_FILING"));
    }

    @Test
    void theFilingDateCannotMoveAfterTheJudgment() throws Exception {
        UUID caseId = createCase("Moved");
        UUID court = createCourt("FI-J9", "FIRST_INSTANCE");
        UUID proceeding = recordProceeding(caseId, proceeding("FIRST_INSTANCE", court, "1", 2025, "2025-01-10", null));
        recordJudgment(proceeding, "2025-02-01");
        mvc.perform(put("/api/v1/proceedings/" + proceeding).with(as(Permission.PROCEEDING_MANAGE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courtId":"%s","courtCaseNumber":"1","courtCaseYear":2025,"filingDate":"2025-03-01",
                                 "version":0}""".formatted(court)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("JUDGMENT_BEFORE_FILING"));
    }

    // QA-0172 unauthenticated access; QA-0173 insufficient permission

    @Test
    void everyEndpointNeedsASessionAndThePermission() throws Exception {
        UUID caseId = createCase("Guarded");
        UUID proceeding = firstInstance("10", caseId);
        UUID id = recordJudgment(proceeding, "2025-02-01");

        mvc.perform(get("/api/v1/judgments/" + id)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/cases/" + caseId + "/judgments")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/proceedings/" + proceeding + "/judgment").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(judgment("2025-02-01", "OTHER", null, false)))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/v1/judgments/" + id).with(as(Permission.PROCEEDING_VIEW)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/cases/" + caseId + "/judgments").with(as(Permission.PROCEEDING_MANAGE)))
                .andExpect(status().isForbidden());
        // Legal staff may record proceedings but not judgments (CONTRACTS.md §3 default roles).
        mvc.perform(post("/api/v1/proceedings/" + proceeding + "/judgment")
                        .with(as(Permission.PROCEEDING_MANAGE, Permission.JUDGMENT_VIEW)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(judgment("2025-02-01", "OTHER", null, false)))
                .andExpect(status().isForbidden()).andExpect(content().contentTypeCompatibleWith(PROBLEM));
        mvc.perform(put("/api/v1/judgments/" + id).with(as(Permission.JUDGMENT_VIEW)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"judgmentDate":"2025-02-01","outcome":"IN_FAVOUR","final":true,"version":0}"""))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("select outcome from judgment where id = ?::uuid", String.class, id))
                .isEqualTo("AGAINST");
    }

    // QA-0174 record integrity and rollback; QA-0175 concurrent or repeat request

    @Test
    void aSecondJudgmentForTheSameProceedingIsAConflict() throws Exception {
        UUID proceeding = firstInstance("11", createCase("Twice"));
        recordJudgment(proceeding, "2025-02-01");
        mvc.perform(postJudgment(proceeding, judgment("2025-02-02", "IN_FAVOUR", null, true)))
                .andExpect(status().isConflict());
        assertThat(rows("judgment")).isEqualTo(1);
    }

    @Test
    void aFailingTimelineEventRollsTheJudgmentBack() throws Exception {
        UUID caseId = createCase("Rolled back");
        UUID proceeding = firstInstance("12", caseId);
        doThrow(new IllegalStateException("timeline unavailable")).when(caseLookup)
                .recordEvent(eq(caseId), eq("JUDGMENT_RECORDED"), anyString());
        mvc.perform(postJudgment(proceeding, judgment("2025-02-01", "OTHER", null, false)))
                .andExpect(status().isInternalServerError());
        assertThat(rows("judgment")).isZero();
    }

    @Test
    void aStaleVersionIsAConflict() throws Exception {
        UUID id = recordJudgment(firstInstance("13", createCase("Stale judgment")), "2025-02-01");
        String update = """
                {"judgmentDate":"2025-02-01","outcome":"%s","final":false,"version":0}""";
        mvc.perform(put("/api/v1/judgments/" + id).with(as(Permission.JUDGMENT_MANAGE)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(update.formatted("IN_FAVOUR")))
                .andExpect(status().isOk());
        mvc.perform(put("/api/v1/judgments/" + id).with(as(Permission.JUDGMENT_MANAGE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(update.formatted("DISMISSED")))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select outcome from judgment where id = ?::uuid", String.class, id))
                .isEqualTo("IN_FAVOUR");
    }
}
