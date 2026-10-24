package com.lawdesk.experts;

import static com.lawdesk.security.TestActors.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;
import com.lawdesk.security.Permission;

/** LD-025 expert referrals and sessions (docs/delivery/PHASE-5-ACCEPTANCE.md): QA-0193…QA-0199. */
class ExpertReferralApiTest extends ExpertTestSupport {

    static String session(Object date, String time, String location, String notes) {
        return """
                {"sessionDate":%s,"sessionTime":%s,"location":%s,"notes":%s}""".formatted(quote(date), quote(time),
                quote(location), quote(notes));
    }

    // QA-0193 authorized happy path

    @Test
    void refersAProceedingToAnExpertWithSessionsAndAReport() throws Exception {
        UUID caseId = createCase("Boundary dispute");
        UUID proceeding = createProceeding(caseId, "500");
        UUID expert = createExpert("Eng. Samir Fathy");

        MvcResult created = mvc.perform(postReferral(proceeding, referral(expert, "2025-02-01",
                        "  Inspect the boundary wall ", "2025-04-01")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.caseId").value(caseId.toString()))
                .andExpect(jsonPath("$.caseReferenceNo").isString())
                .andExpect(jsonPath("$.proceedingId").value(proceeding.toString()))
                .andExpect(jsonPath("$.courtCaseNumber").value("500"))
                .andExpect(jsonPath("$.expertId").value(expert.toString()))
                .andExpect(jsonPath("$.expertName").value("Eng. Samir Fathy"))
                .andExpect(jsonPath("$.expertSpecialty").value("Engineering"))
                .andExpect(jsonPath("$.referralDate").value("2025-02-01"))
                .andExpect(jsonPath("$.mission").value("Inspect the boundary wall"))
                .andExpect(jsonPath("$.reportDueDate").value("2025-04-01"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.reportOverdue").value(true))
                .andExpect(jsonPath("$.sessions").isEmpty())
                .andReturn();
        String id = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        UUID referralId = UUID.fromString(id);
        assertThat(created.getResponse().getHeader("Location")).endsWith("/api/v1/expert-referrals/" + id);
        verify(caseLookup).recordEvent(eq(caseId), eq("EXPERT_REFERRED"), contains("Eng. Samir Fathy"));

        mvc.perform(action(referralId, "sessions", session("2025-02-15", "10:00", " Site of the wall ", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessions[0].sessionDate").value("2025-02-15"))
                .andExpect(jsonPath("$.sessions[0].sessionTime").value("10:00"))
                .andExpect(jsonPath("$.sessions[0].location").value("Site of the wall"))
                .andExpect(jsonPath("$.version").value(1));
        mvc.perform(action(referralId, "sessions", session("2025-02-10", null, null, "Parties attended")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessions[*].sessionDate").value(Matchers.contains("2025-02-10", "2025-02-15")));
        verify(caseLookup).recordEvent(eq(caseId), eq("EXPERT_SESSION_RECORDED"), contains("2025-02-15"));

        mvc.perform(put("/api/v1/expert-referrals/" + id).with(as(Permission.EXPERT_MANAGE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mission\":\"Inspect the wall and the gate\",\"reportDueDate\":\"2025-05-01\",\"version\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mission").value("Inspect the wall and the gate"))
                .andExpect(jsonPath("$.version").value(3));

        mvc.perform(action(referralId, "report", "{\"reportSubmittedDate\":\"2025-03-20\",\"version\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REPORT_SUBMITTED"))
                .andExpect(jsonPath("$.reportSubmittedDate").value("2025-03-20"))
                .andExpect(jsonPath("$.reportOverdue").value(false));
        verify(caseLookup).recordEvent(eq(caseId), eq("EXPERT_REPORT_SUBMITTED"), contains("2025-03-20"));

        mvc.perform(get("/api/v1/cases/" + caseId + "/expert-referrals").with(as(Permission.EXPERT_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(id))
                .andExpect(jsonPath("$.items[0].sessions.length()").value(2));
        mvc.perform(get("/api/v1/expert-referrals/" + id).with(as(Permission.EXPERT_VIEW)))
                .andExpect(jsonPath("$.status").value("REPORT_SUBMITTED"));
    }

    @Test
    void aReferralCanBeCancelledAndThenTakesNoMoreWork() throws Exception {
        UUID caseId = createCase("Cancelled");
        UUID referral = refer(createProceeding(caseId, "510"), createExpert("Hoda"), "2025-02-01", null);
        mvc.perform(action(referral, "cancel", "{\"version\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        verify(caseLookup).recordEvent(eq(caseId), eq("EXPERT_REFERRAL_CANCELLED"), anyString());
        for (String[] attempt : new String[][] {{"sessions", session("2025-02-02", null, null, null)},
                {"report", "{\"reportSubmittedDate\":\"2025-02-03\",\"version\":1}"}, {"cancel", "{\"version\":1}"}}) {
            mvc.perform(action(referral, attempt[0], attempt[1]))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.rule").value("REFERRAL_CLOSED"));
        }
        mvc.perform(put("/api/v1/expert-referrals/" + referral).with(as(Permission.EXPERT_MANAGE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"mission\":\"x\",\"version\":1}"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.rule").value("REFERRAL_CLOSED"));
    }

    // QA-0194 required-field validation, QA-0195 invalid input boundary

    @Test
    void validatesReferralsSessionsAndReports() throws Exception {
        UUID proceeding = createProceeding(createCase("Validation"), "520");
        UUID expert = createExpert("Validator");
        mvc.perform(postReferral(proceeding, "{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(Matchers.containsInAnyOrder("expertId", "referralDate")));
        mvc.perform(postReferral(proceeding, referral(expert, today().plusDays(1), null, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("PastOrPresent"));
        mvc.perform(postReferral(proceeding, referral(expert, "2025-02-01", "m".repeat(4001), null)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("mission"));
        mvc.perform(postReferral(proceeding, referral(expert, "2025-01-09", null, null)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("REFERRAL_BEFORE_FILING"));
        mvc.perform(postReferral(proceeding, referral(expert, "2025-02-01", null, "2025-01-31")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("REPORT_DUE_BEFORE_REFERRAL"));
        UUID referral = refer(proceeding, expert, FILED, FILED);

        mvc.perform(action(referral, "sessions", "{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("sessionDate"));
        mvc.perform(action(referral, "sessions", session("2025-01-09", null, null, null)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("SESSION_BEFORE_REFERRAL"));
        mvc.perform(action(referral, "sessions", session(FILED, null, "l".repeat(201), null)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("location"));
        mvc.perform(action(referral, "report", "{\"version\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("reportSubmittedDate"));
        mvc.perform(action(referral, "report", "{\"reportSubmittedDate\":\"2025-01-09\",\"version\":0}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("REPORT_BEFORE_REFERRAL"));
        mvc.perform(action(referral, "report", "{\"reportSubmittedDate\":\"%s\",\"version\":0}"
                        .formatted(today().plusDays(1))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].code").value("PastOrPresent"));
        assertThat(rows("expert_referral")).isEqualTo(1);
        assertThat(rows("expert_session")).isZero();
        assertThat(statusOf(referral)).isEqualTo("OPEN");
    }

    @Test
    void anInactiveExpertUnknownRecordsAndArchivedCasesAreRefused() throws Exception {
        UUID caseId = createCase("Refusals");
        UUID proceeding = createProceeding(caseId, "530");
        UUID inactive = createExpert("Retired");
        mvc.perform(put("/api/v1/experts/" + inactive).with(as(Permission.EXPERT_MANAGE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ExpertApiTest.edit("Retired", "Engineering", false, 0)))
                .andExpect(status().isOk());
        mvc.perform(postReferral(proceeding, referral(inactive, "2025-02-01", null, null)))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.rule").value("EXPERT_INACTIVE"));
        mvc.perform(postReferral(proceeding, referral(UUID.randomUUID(), "2025-02-01", null, null)))
                .andExpect(status().isNotFound());
        mvc.perform(postReferral(UUID.randomUUID(), referral(createExpert("Active"), "2025-02-01", null, null)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/expert-referrals/" + UUID.randomUUID()).with(as(Permission.EXPERT_VIEW)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/cases/" + UUID.randomUUID() + "/expert-referrals").with(as(Permission.EXPERT_VIEW)))
                .andExpect(status().isNotFound());

        UUID referral = refer(proceeding, createExpert("Before archiving"), "2025-02-01", null);
        for (String[] step : new String[][] {{"CLOSED", "0"}, {"ARCHIVED", "1"}}) {
            mvc.perform(post("/api/v1/cases/" + caseId + "/status").with(as(Permission.CASE_STATUS_CHANGE))
                    .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"status\":\"%s\",\"version\":%s}".formatted(step[0], step[1])))
                    .andExpect(status().isOk());
        }
        mvc.perform(action(referral, "sessions", session("2025-02-02", null, null, null)))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.rule").value("CASE_ARCHIVED"));
        mvc.perform(postReferral(proceeding, referral(createExpert("Late"), "2025-02-01", null, null)))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.rule").value("CASE_ARCHIVED"));
    }

    // QA-0196 unauthenticated access, QA-0197 insufficient permission

    @Test
    void referralEndpointsNeedASessionAndThePermission() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(get("/api/v1/cases/" + id + "/expert-referrals")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/expert-referrals/" + id)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/proceedings/" + id + "/expert-referrals").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        for (String what : List.of("sessions", "report", "cancel")) {
            mvc.perform(post("/api/v1/expert-referrals/" + id + "/" + what).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        }

        UUID caseId = createCase("Permissions");
        UUID proceeding = createProceeding(caseId, "540");
        UUID expert = createExpert("Guarded");
        mvc.perform(post("/api/v1/proceedings/" + proceeding + "/expert-referrals")
                        .with(as(Permission.EXPERT_VIEW, Permission.PROCEEDING_MANAGE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(referral(expert, "2025-02-01", null, null)))
                .andExpect(status().isForbidden());
        UUID referral = refer(proceeding, expert, "2025-02-01", null);
        for (String what : List.of("sessions", "report", "cancel")) {
            mvc.perform(post("/api/v1/expert-referrals/" + referral + "/" + what).with(as(Permission.EXPERT_VIEW))
                    .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/v1/cases/" + caseId + "/expert-referrals").with(as(Permission.CASE_VIEW)))
                .andExpect(status().isForbidden());
        assertThat(statusOf(referral)).isEqualTo("OPEN");
        assertThat(rows("expert_session")).isZero();
    }

    // QA-0198 record integrity and rollback

    @Test
    void aFailingTimelineEventRollsTheWriteBackAndTheDatabaseKeepsReferralsConsistent() throws Exception {
        UUID caseId = createCase("Rollback");
        UUID other = createCase("Other");
        UUID proceeding = createProceeding(caseId, "550");
        UUID expert = createExpert("Rollback expert");
        UUID referral = refer(proceeding, expert, "2025-02-01", null);
        doThrow(new IllegalStateException("timeline unavailable")).when(caseLookup)
                .recordEvent(eq(caseId), anyString(), anyString());
        mvc.perform(postReferral(proceeding, referral(expert, "2025-02-02", null, null)))
                .andExpect(status().isInternalServerError());
        mvc.perform(action(referral, "sessions", session("2025-02-03", null, null, null)))
                .andExpect(status().isInternalServerError());
        mvc.perform(action(referral, "report", "{\"reportSubmittedDate\":\"2025-02-04\",\"version\":0}"))
                .andExpect(status().isInternalServerError());
        assertThat(rows("expert_referral")).isEqualTo(1);
        assertThat(rows("expert_session")).isZero();
        assertThat(statusOf(referral)).isEqualTo("OPEN");

        UUID actor = UUID.randomUUID();
        String insert = """
                insert into expert_referral (case_id, proceeding_id, expert_id, referral_date, status,
                                             report_submitted_date, created_by, updated_by)
                values (?, ?, ?, ?, ?, ?, ?, ?)""";
        LocalDate day = LocalDate.parse("2025-02-01");
        assertThatThrownBy(() -> jdbc.update(insert, other, proceeding, expert, day, "OPEN", null, actor, actor))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, caseId, proceeding, expert, day, "REPORT_SUBMITTED", null,
                actor, actor)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, caseId, proceeding, expert, day, "OPEN", day, actor, actor))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(rows("expert_referral")).isEqualTo(1);
    }

    // QA-0199 concurrent or repeat request

    @Test
    void aStaleVersionIsAConflictAndChangesNothing() throws Exception {
        UUID referral = refer(createProceeding(createCase("Stale"), "560"), createExpert("Stale expert"),
                "2025-02-01", null);
        mvc.perform(action(referral, "sessions", session("2025-02-02", null, null, null)))
                .andExpect(status().isCreated());
        mvc.perform(action(referral, "report", "{\"reportSubmittedDate\":\"2025-02-03\",\"version\":0}"))
                .andExpect(status().isConflict());
        mvc.perform(action(referral, "cancel", "{\"version\":0}")).andExpect(status().isConflict());
        mvc.perform(put("/api/v1/expert-referrals/" + referral).with(as(Permission.EXPERT_MANAGE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"mission\":\"x\",\"version\":0}"))
                .andExpect(status().isConflict());
        assertThat(statusOf(referral)).isEqualTo("OPEN");
    }
}
