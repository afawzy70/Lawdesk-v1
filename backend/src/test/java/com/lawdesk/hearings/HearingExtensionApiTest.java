package com.lawdesk.hearings;

import static com.lawdesk.security.TestActors.as;
import static com.lawdesk.security.TestActors.scoped;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;
import com.lawdesk.security.Permission;

/**
 * LD-053, requirements section 7, scenario S9: the fields of the hearing record, an adjournment that keeps the previous
 * hearing and creates a separate next one, and the "no next date" path that puts the case on the follow-up list instead
 * of guessing a date.
 */
class HearingExtensionApiTest extends HearingTestSupport {

    private UUID responsible;

    @AfterEach
    void removeUsers() {
        // Runs before the base class clean-up: notifications and hearings refer to the users and must go first.
        dropPause();
        jdbc.update("delete from hearing where adjourned_from_id is not null");
        jdbc.update("delete from hearing");
        com.lawdesk.platform.OperationsTestData.deleteAll(jdbc);
        jdbc.update("delete from app_user where username like 'wp3h%'");
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private UUID member(String name) {
        String username = "wp3h" + UUID.randomUUID().toString().substring(0, 8);
        return jdbc.queryForObject("""
                insert into app_user (username, email, display_name, password_hash, enabled, created_by, updated_by)
                values (?, ?, ?, 'x', true, gen_random_uuid(), gen_random_uuid()) returning id""", UUID.class,
                username, username + "@example.invalid", name);
    }

    private String full(Object date, String kind) {
        return """
                {"hearingDate":"%s","hearingTime":"09:30","purpose":"EVIDENCE","notes":"n","chamber":"الدائرة الثالثة",
                 "kind":"%s","judgeOrExpert":"المستشار س","responsibleMemberId":"%s","attendees":"المحامي والمدعي",
                 "requiredAction":"إعداد مذكرة"}""".formatted(date, kind, responsible);
    }

    private MvcResult createFull(UUID proceeding, Object date, String kind) throws Exception {
        responsible = responsible == null ? member("Responsible Member") : responsible;
        return mvc.perform(postHearing(proceeding, full(date, kind))).andExpect(status().isCreated()).andReturn();
    }

    @Test
    void theRecordHoldsTheFieldsOfTheRequirementsDocument() throws Exception {
        UUID proceeding = createProceeding(createCase("Fields"), "100");
        responsible = member("Responsible Member");
        mvc.perform(postHearing(proceeding, full(today().plusDays(5), "EXPERT")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.chamber").value("الدائرة الثالثة"))
                .andExpect(jsonPath("$.kind").value("EXPERT"))
                .andExpect(jsonPath("$.judgeOrExpert").value("المستشار س"))
                .andExpect(jsonPath("$.responsibleMemberId").value(responsible.toString()))
                .andExpect(jsonPath("$.responsibleMemberName").value("Responsible Member"))
                .andExpect(jsonPath("$.attendees").value("المحامي والمدعي"))
                .andExpect(jsonPath("$.requiredAction").value("إعداد مذكرة"))
                .andExpect(jsonPath("$.noNextDate").value(false))
                .andExpect(jsonPath("$.followUp").value(false));
    }

    @Test
    void aHearingWithoutTheNewFieldsIsACourtHearingAndTheOldBodiesStillWork() throws Exception {
        UUID proceeding = createProceeding(createCase("Old body"), "101");
        mvc.perform(postHearing(proceeding, hearing(today().plusDays(3), null, "PLEADING", null)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.kind").value("COURT"))
                .andExpect(jsonPath("$.chamber").doesNotExist()).andExpect(jsonPath("$.responsibleMemberId").doesNotExist());
    }

    @Test
    void refusesAnUnknownKindAnUnknownMemberAndOversizedText() throws Exception {
        UUID proceeding = createProceeding(createCase("Bad"), "102");
        String date = today().plusDays(3).toString();
        mvc.perform(postHearing(proceeding, """
                {"hearingDate":"%s","purpose":"OTHER","kind":"PARTY"}""".formatted(date))).andExpect(status().isBadRequest());
        mvc.perform(postHearing(proceeding, """
                {"hearingDate":"%s","purpose":"OTHER","responsibleMemberId":"%s"}""".formatted(date, UUID.randomUUID())))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("responsibleMemberId"));
        mvc.perform(postHearing(proceeding, """
                {"hearingDate":"%s","purpose":"OTHER","proceedingsSummary":"%s"}""".formatted(date, "x".repeat(4001))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aScheduledHearingIsUpdatedWithTheNewFields() throws Exception {
        UUID proceeding = createProceeding(createCase("Update"), "103");
        UUID id = schedule(proceeding, today().plusDays(4));
        mvc.perform(put("/api/v1/hearings/" + id).with(as(Permission.HEARING_MANAGE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"hearingDate":"%s","purpose":"PLEADING","kind":"INVESTIGATION","chamber":"دائرة التحقيق",
                                 "judgeOrExpert":"عضو النيابة","version":0}""".formatted(today().plusDays(4))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.kind").value("INVESTIGATION"))
                .andExpect(jsonPath("$.chamber").value("دائرة التحقيق")).andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void theResultRecordsWhatHappenedAtTheHearing() throws Exception {
        UUID proceeding = createProceeding(createCase("Held"), "104");
        UUID id = schedule(proceeding, today().minusDays(1));
        mvc.perform(postResult(id, """
                        {"status":"HELD","decision":"reserved","attendees":"Both sides","proceedingsSummary":"Pleadings heard",
                         "opponentsRequests":"A time limit to reply","requiredAction":"Reply memorandum","version":0}"""))
                .andExpect(status().isOk()).andExpect(jsonPath("$.attendees").value("Both sides"))
                .andExpect(jsonPath("$.proceedingsSummary").value("Pleadings heard"))
                .andExpect(jsonPath("$.opponentsRequests").value("A time limit to reply"))
                .andExpect(jsonPath("$.requiredAction").value("Reply memorandum"))
                .andExpect(jsonPath("$.adjournmentReason").doesNotExist());
    }

    // --- S9: an adjournment keeps the previous hearing and makes a separate next one -------------------------------------

    @Test
    void anAdjournmentKeepsThePreviousHearingWithItsReasonAndCreatesASeparateNextOne() throws Exception {
        UUID caseId = createCase("S9");
        UUID proceeding = createProceeding(caseId, "105");
        String first = today().minusDays(2).toString();
        String second = today().plusDays(20).toString();
        MvcResult created = createFull(proceeding, first, "COURT");
        UUID id = UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.id"));
        MvcResult adjourned = mvc.perform(postResult(id, """
                        {"status":"ADJOURNED","decision":"adjourned","adjournmentReason":"لإعلان الخصم","nextHearingDate":"%s",
                         "nextHearingTime":"11:00","version":0}""".formatted(second)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ADJOURNED"))
                .andExpect(jsonPath("$.adjournmentReason").value("لإعلان الخصم"))
                .andExpect(jsonPath("$.noNextDate").value(false)).andExpect(jsonPath("$.followUp").value(false))
                .andReturn();
        UUID next = UUID.fromString(JsonPath.read(adjourned.getResponse().getContentAsString(), "$.nextHearingId"));

        assertThat(rows("hearing")).isEqualTo(2);
        // The previous hearing is untouched apart from its result.
        assertThat(jdbc.queryForObject("select hearing_date::text from hearing where id = ?", String.class, id)).isEqualTo(first);
        assertThat(jdbc.queryForObject("select chamber from hearing where id = ?", String.class, id)).isEqualTo("الدائرة الثالثة");
        mvc.perform(get("/api/v1/hearings/" + next).with(as(Permission.HEARING_VIEW)))
                .andExpect(jsonPath("$.status").value("SCHEDULED")).andExpect(jsonPath("$.adjournedFromId").value(id.toString()))
                .andExpect(jsonPath("$.hearingDate").value(second))
                .andExpect(jsonPath("$.chamber").value("الدائرة الثالثة"))
                .andExpect(jsonPath("$.kind").value("COURT"))
                .andExpect(jsonPath("$.responsibleMemberId").value(responsible.toString()))
                .andExpect(jsonPath("$.adjournmentReason").doesNotExist());
        mvc.perform(get("/api/v1/cases/" + caseId + "/hearings").with(as(Permission.HEARING_VIEW)))
                .andExpect(jsonPath("$.totalItems").value(2));
    }

    @Test
    void anAdjournmentStillNeedsADateUnlessItSaysThereIsNone() throws Exception {
        UUID proceeding = createProceeding(createCase("Needs date"), "106");
        UUID id = schedule(proceeding, today().minusDays(1));
        mvc.perform(postResult(id, """
                {"status":"ADJOURNED","version":0}""")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("nextHearingDate"));
        assertThat(statusOf(id)).isEqualTo("SCHEDULED");
    }

    // --- no next date -----------------------------------------------------------------------------------------------------

    @Test
    void noNextDateIsRecordedWithItsReasonPutsTheCaseOnTheFollowUpListAndNotifies() throws Exception {
        UUID caseId = createCase("No date");
        UUID proceeding = createProceeding(caseId, "107");
        responsible = member("Responsible Member");
        UUID id = UUID.fromString(JsonPath.read(createFull(proceeding, today().minusDays(1).toString(), "COURT")
                .getResponse().getContentAsString(), "$.id"));
        mvc.perform(postResult(id, """
                        {"status":"ADJOURNED","adjournmentReason":"الدعوى شطبت إداريا","noNextDate":true,
                         "noNextDateReason":"المحكمة لم تحدد موعدا","version":0}"""))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ADJOURNED"))
                .andExpect(jsonPath("$.noNextDate").value(true))
                .andExpect(jsonPath("$.noNextDateReason").value("المحكمة لم تحدد موعدا"))
                .andExpect(jsonPath("$.followUp").value(true)).andExpect(jsonPath("$.nextHearingId").doesNotExist());
        assertThat(rows("hearing")).isEqualTo(1);

        mvc.perform(get("/api/v1/hearings/follow-up").with(as(Permission.HEARING_VIEW)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(id.toString()));
        assertThat(count("select count(*) from notification where type = 'HEARING_NO_NEXT_DATE' and user_id = ?", responsible))
                .isEqualTo(1);
        // Raising it again (the follow-up run) never duplicates it.
        mvc.perform(post("/api/v1/follow-up/run").with(as(Permission.INFO_REQUEST_ESCALATE)).with(csrf()))
                .andExpect(status().isOk());
        assertThat(count("select count(*) from notification where type = 'HEARING_NO_NEXT_DATE'")).isEqualTo(1);
    }

    @Test
    void noNextDateNeedsAReasonAndExcludesADate() throws Exception {
        UUID proceeding = createProceeding(createCase("Rules"), "108");
        UUID id = schedule(proceeding, today().minusDays(1));
        mvc.perform(postResult(id, """
                {"status":"ADJOURNED","noNextDate":true,"version":0}""")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("noNextDateReason"));
        mvc.perform(postResult(id, """
                {"status":"ADJOURNED","noNextDate":true,"noNextDateReason":"x","nextHearingDate":"%s","version":0}"""
                .formatted(today().plusDays(9)))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("nextHearingDate"));
        mvc.perform(postResult(id, """
                {"status":"HELD","noNextDate":true,"noNextDateReason":"x","version":0}""")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("noNextDate"));
        mvc.perform(postResult(id, """
                {"status":"HELD","adjournmentReason":"x","version":0}""")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("adjournmentReason"));
        assertThat(statusOf(id)).isEqualTo("SCHEDULED");
    }

    @Test
    void aLaterNextHearingEndsTheFollowUpWithoutRewritingWhatWasRecorded() throws Exception {
        UUID caseId = createCase("Later");
        UUID proceeding = createProceeding(caseId, "109");
        UUID id = schedule(proceeding, today().minusDays(3));
        mvc.perform(postResult(id, """
                {"status":"ADJOURNED","noNextDate":true,"noNextDateReason":"no date given","version":0}"""))
                .andExpect(status().isOk());
        String path = "/api/v1/hearings/" + id + "/next-hearing";
        mvc.perform(post(path).with(as(Permission.HEARING_MANAGE)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"hearingDate":"%s","hearingTime":"10:00","version":1}""".formatted(today().plusDays(12))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.adjournedFromId").value(id.toString()))
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
        mvc.perform(get("/api/v1/hearings/" + id).with(as(Permission.HEARING_VIEW)))
                .andExpect(jsonPath("$.followUp").value(false)).andExpect(jsonPath("$.noNextDate").value(true))
                .andExpect(jsonPath("$.noNextDateReason").value("no date given"))
                .andExpect(jsonPath("$.nextHearingId").isString());
        mvc.perform(get("/api/v1/hearings/follow-up").with(as(Permission.HEARING_VIEW)))
                .andExpect(jsonPath("$.totalItems").value(0));
        // It cannot be done twice, nor for a hearing that has a date.
        mvc.perform(post(path).with(as(Permission.HEARING_MANAGE)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"hearingDate":"%s","version":2}""".formatted(today().plusDays(30))))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.rule").value("HEARING_NOT_AWAITING_DATE"));
    }

    @Test
    void theNextHearingMustFollowThePreviousOne() throws Exception {
        UUID proceeding = createProceeding(createCase("Order"), "110");
        UUID id = schedule(proceeding, today().minusDays(3));
        mvc.perform(postResult(id, """
                {"status":"ADJOURNED","noNextDate":true,"noNextDateReason":"r","version":0}""")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/hearings/" + id + "/next-hearing").with(as(Permission.HEARING_MANAGE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"hearingDate":"%s","version":1}""".formatted(today().minusDays(5))))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.rule").value("ADJOURNMENT_NOT_LATER"));
    }

    // --- visibility (D3) ----------------------------------------------------------------------------------------------------

    @Test
    void theFollowUpListAndTheNextHearingFollowCaseVisibility() throws Exception {
        UUID caseId = createCase("Hidden");
        UUID proceeding = createProceeding(caseId, "111");
        UUID id = schedule(proceeding, today().minusDays(3));
        mvc.perform(postResult(id, """
                {"status":"ADJOURNED","noNextDate":true,"noNextDateReason":"r","version":0}""")).andExpect(status().isOk());
        var nobody = scoped(UUID.randomUUID(), "nobody", List.of(), Permission.HEARING_VIEW, Permission.HEARING_MANAGE);
        mvc.perform(get("/api/v1/hearings/follow-up").with(nobody)).andExpect(jsonPath("$.totalItems").value(0));
        mvc.perform(post("/api/v1/hearings/" + id + "/next-hearing").with(nobody).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"hearingDate":"%s","version":1}""".formatted(today().plusDays(9))))
                .andExpect(status().isNotFound());
        var seesOnly = scoped(UUID.randomUUID(), "sees", List.of("ALL"), Permission.HEARING_VIEW, Permission.HEARING_MANAGE);
        mvc.perform(post("/api/v1/hearings/" + id + "/next-hearing").with(seesOnly).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"hearingDate":"%s","version":1}""".formatted(today().plusDays(9))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/hearings/follow-up").with(as(Permission.INFO_REQUEST_VIEW))).andExpect(status().isForbidden());
    }
}
