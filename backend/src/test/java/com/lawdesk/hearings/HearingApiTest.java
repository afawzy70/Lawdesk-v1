package com.lawdesk.hearings;

import static com.lawdesk.security.TestActors.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.lawdesk.security.Permission;

/** LD-024 schedule and record hearings (docs/delivery/PHASE-5-ACCEPTANCE.md): QA-0185…QA-0191. */
class HearingApiTest extends HearingTestSupport {

    // QA-0185 authorized happy path

    @Test
    void schedulesAHearingAndATimelineEvent() throws Exception {
        UUID caseId = createCase("Rent dispute");
        UUID proceeding = createProceeding(caseId, "1234");
        LocalDate date = today().plusDays(10);

        MvcResult result = mvc.perform(postHearing(proceeding,
                        hearing(date, "09:30", "FIRST_SESSION", "  bring the lease  ")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.caseId").value(caseId.toString()))
                .andExpect(jsonPath("$.caseReferenceNo").value(referenceOf(caseId)))
                .andExpect(jsonPath("$.caseTitle").value("Rent dispute"))
                .andExpect(jsonPath("$.proceedingId").value(proceeding.toString()))
                .andExpect(jsonPath("$.stage").value("FIRST_INSTANCE"))
                .andExpect(jsonPath("$.courtCaseNumber").value("1234"))
                .andExpect(jsonPath("$.courtCaseYear").value(2025))
                .andExpect(jsonPath("$.courtCode").value(Matchers.startsWith("HC-")))
                .andExpect(jsonPath("$.courtNameAr").value(Matchers.startsWith("محكمة")))
                .andExpect(jsonPath("$.courtNameEn").value(Matchers.startsWith("Court")))
                .andExpect(jsonPath("$.hearingDate").value(date.toString()))
                .andExpect(jsonPath("$.hearingTime").value("09:30"))
                .andExpect(jsonPath("$.purpose").value("FIRST_SESSION"))
                .andExpect(jsonPath("$.notes").value("bring the lease"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.decision").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.adjournedFromId").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.nextHearingId").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.awaitingResult").value(false))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn();
        String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        assertThat(result.getResponse().getHeader("Location")).endsWith("/api/v1/hearings/" + id);
        assertThat(jdbc.queryForObject("select created_by from hearing where id = ?::uuid", UUID.class, id))
                .isEqualTo(CLERK);
        verify(caseLookup).recordEvent(eq(caseId), eq("HEARING_SCHEDULED"), contains(date.toString()));
        verify(caseLookup).recordEvent(eq(caseId), eq("HEARING_SCHEDULED"), contains("1234/2025"));

        mvc.perform(get("/api/v1/hearings/" + id).with(as(Permission.HEARING_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hearingTime").value("09:30"))
                .andExpect(jsonPath("$.caseTitle").value("Rent dispute"));
    }

    @Test
    void listsTheHearingsOfOneCaseOldestFirst() throws Exception {
        UUID caseId = createCase("Ordered");
        UUID other = createCase("Other");
        UUID first = createProceeding(caseId, "10");
        UUID second = createProceeding(caseId, "11");
        UUID late = schedule(first, "2025-03-01", "11:00");
        UUID untimed = schedule(second, "2025-03-01");
        UUID early = schedule(first, "2025-02-01");
        schedule(createProceeding(other, "12"), "2025-01-20");

        mvc.perform(get("/api/v1/cases/" + caseId + "/hearings").with(as(Permission.HEARING_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id").value(Matchers.contains(early.toString(), untimed.toString(),
                        late.toString())))
                .andExpect(jsonPath("$.items[0].awaitingResult").value(true))
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));
        mvc.perform(get("/api/v1/cases/" + caseId + "/hearings").param("size", "2").param("page", "1")
                        .with(as(Permission.HEARING_VIEW)))
                .andExpect(jsonPath("$.items[*].id").value(Matchers.contains(late.toString())))
                .andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get("/api/v1/cases/" + UUID.randomUUID() + "/hearings").with(as(Permission.HEARING_VIEW)))
                .andExpect(status().isNotFound());
    }

    @Test
    void theCalendarListsHearingsOfAllCasesInADateRange() throws Exception {
        UUID one = createProceeding(createCase("One"), "20");
        UUID two = createProceeding(createCase("Two"), "21");
        LocalDate today = today();
        UUID todays = schedule(one, today, "10:00");
        UUID inTwoDays = schedule(two, today.plusDays(2));
        UUID lastDay = schedule(one, today.plusDays(30));
        schedule(two, today.plusDays(31));
        UUID past = schedule(two, today.minusDays(3));
        UUID cancelled = schedule(one, today.plusDays(5));
        mvc.perform(postResult(cancelled, result("CANCELLED", "struck off", null, 0)))
                .andExpect(status().isOk());

        // Default window: today to today + 30 days, every status.
        mvc.perform(get("/api/v1/hearings").with(as(Permission.HEARING_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id").value(Matchers.contains(todays.toString(),
                        inTwoDays.toString(), cancelled.toString(), lastDay.toString())))
                .andExpect(jsonPath("$.items[1].caseTitle").value("Two"));
        mvc.perform(get("/api/v1/hearings").param("status", "SCHEDULED").with(as(Permission.HEARING_VIEW)))
                .andExpect(jsonPath("$.items[*].id").value(Matchers.contains(todays.toString(),
                        inTwoDays.toString(), lastDay.toString())));
        mvc.perform(get("/api/v1/hearings").param("from", today.minusDays(3).toString())
                        .param("to", today.minusDays(1).toString()).with(as(Permission.HEARING_VIEW)))
                .andExpect(jsonPath("$.items[*].id").value(Matchers.contains(past.toString())))
                .andExpect(jsonPath("$.items[0].awaitingResult").value(true));
        mvc.perform(get("/api/v1/hearings").param("from", today.toString()).param("to", today.toString())
                        .param("size", "1").with(as(Permission.HEARING_VIEW)))
                .andExpect(jsonPath("$.totalItems").value(1));
    }

    // QA-0186 required-field validation

    @Test
    void requiredFieldsAreValidated() throws Exception {
        UUID proceeding = createProceeding(createCase("Required"), "30");
        mvc.perform(postHearing(proceeding, "{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.errors[*].field").value(Matchers.containsInAnyOrder("hearingDate", "purpose")))
                .andExpect(jsonPath("$.errors[*].code").value(Matchers.everyItem(Matchers.is("NotNull"))));
        mvc.perform(postHearing(proceeding, hearing("2025-02-01", null, "SOMETHING", null)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors").doesNotExist());
        mvc.perform(postHearing(proceeding, hearing("2025-02-01", "9.30", "PLEADING", null)))
                .andExpect(status().isBadRequest());
        mvc.perform(postHearing(proceeding, hearing("01/02/2025", null, "PLEADING", null)))
                .andExpect(status().isBadRequest());
        UUID id = schedule(proceeding, "2025-02-01");
        mvc.perform(put("/api/v1/hearings/" + id).with(as(Permission.HEARING_MANAGE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(Matchers.containsInAnyOrder("hearingDate", "purpose",
                        "version")));
        mvc.perform(postResult(id, "{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(Matchers.containsInAnyOrder("status", "version")));
        assertThat(rows("hearing")).isEqualTo(1);
    }

    // QA-0187 invalid input boundary

    @Test
    void boundariesOfNotesDateAndFiling() throws Exception {
        UUID proceeding = createProceeding(createCase("Bounds"), "40");
        mvc.perform(postHearing(proceeding, hearing("2025-02-01", null, "OTHER", "n".repeat(2001))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("notes"))
                .andExpect(jsonPath("$.errors[0].code").value("Size"));
        mvc.perform(postHearing(proceeding, hearing("2025-02-01", null, "OTHER", " " + "n".repeat(2000) + " ")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.notes").value("n".repeat(2000)));
        mvc.perform(postHearing(proceeding, hearing("2025-02-02", null, "OTHER", "   ")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.notes").value(Matchers.nullValue()));

        mvc.perform(postHearing(proceeding, hearing(today().plusYears(2).plusDays(1), null, "OTHER", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("hearingDate"))
                .andExpect(jsonPath("$.errors[0].code").value("Range"));
        mvc.perform(postHearing(proceeding, hearing(today().plusYears(2), null, "OTHER", null)))
                .andExpect(status().isCreated());

        mvc.perform(postHearing(proceeding, hearing("2025-01-09", null, "OTHER", null)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("HEARING_BEFORE_FILING"));
        mvc.perform(postHearing(proceeding, hearing(FILED, null, "OTHER", null)))
                .andExpect(status().isCreated());
        assertThat(rows("hearing")).isEqualTo(4);
    }

    @Test
    void theCalendarRangeIsBounded() throws Exception {
        LocalDate today = today();
        mvc.perform(get("/api/v1/hearings").param("from", today.toString()).param("to", today.minusDays(1).toString())
                        .with(as(Permission.HEARING_VIEW)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("to"))
                .andExpect(jsonPath("$.errors[0].code").value("Range"));
        mvc.perform(get("/api/v1/hearings").param("from", today.toString())
                        .param("to", today.plusDays(367).toString()).with(as(Permission.HEARING_VIEW)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("to"));
        mvc.perform(get("/api/v1/hearings").param("from", today.toString())
                        .param("to", today.plusDays(366).toString()).with(as(Permission.HEARING_VIEW)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/hearings").param("from", "tomorrow").with(as(Permission.HEARING_VIEW)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/hearings").param("status", "PENDING").with(as(Permission.HEARING_VIEW)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reschedulesAScheduledHearing() throws Exception {
        UUID caseId = createCase("Reschedule");
        UUID proceeding = createProceeding(caseId, "50");
        UUID id = schedule(proceeding, "2025-02-01");
        UUID editor = UUID.randomUUID();
        clearInvocations(caseLookup);

        mvc.perform(put("/api/v1/hearings/" + id).with(as(editor, "editor", Permission.HEARING_MANAGE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update("2025-02-08", "10:15", "EVIDENCE", "witnesses", 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hearingDate").value("2025-02-08"))
                .andExpect(jsonPath("$.hearingTime").value("10:15"))
                .andExpect(jsonPath("$.purpose").value("EVIDENCE"))
                .andExpect(jsonPath("$.notes").value("witnesses"))
                .andExpect(jsonPath("$.version").value(1));
        assertThat(jdbc.queryForObject("select updated_by from hearing where id = ?", UUID.class, id))
                .isEqualTo(editor);
        verify(caseLookup).recordEvent(eq(caseId), eq("HEARING_RESCHEDULED"), contains("2025-02-08"));
        clearInvocations(caseLookup);

        // Only the notes change: no timeline entry.
        mvc.perform(put("/api/v1/hearings/" + id).with(as(Permission.HEARING_MANAGE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update("2025-02-08", "10:15", "EVIDENCE", "two witnesses", 1)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
        verify(caseLookup, never()).recordEvent(eq(caseId), anyString(), anyString());

        mvc.perform(put("/api/v1/hearings/" + id).with(as(Permission.HEARING_MANAGE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update("2025-01-01", null, "EVIDENCE", null, 2)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("HEARING_BEFORE_FILING"));
        mvc.perform(put("/api/v1/hearings/" + UUID.randomUUID()).with(as(Permission.HEARING_MANAGE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update("2025-02-08", null, "EVIDENCE", null, 0)))
                .andExpect(status().isNotFound());
    }

    @Test
    void recordsAHeldHearingOnce() throws Exception {
        UUID caseId = createCase("Held");
        UUID id = schedule(createProceeding(caseId, "60"), "2025-02-01");
        clearInvocations(caseLookup);

        mvc.perform(postResult(id, result("HELD", "  reserved for judgment ", null, 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("HELD"))
                .andExpect(jsonPath("$.decision").value("reserved for judgment"))
                .andExpect(jsonPath("$.awaitingResult").value(false))
                .andExpect(jsonPath("$.nextHearingId").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.version").value(1));
        verify(caseLookup).recordEvent(eq(caseId), eq("HEARING_HELD"), contains("2025-02-01"));
        assertThat(jdbc.queryForObject("select result_recorded_by from hearing where id = ?", UUID.class, id))
                .isEqualTo(CLERK);

        mvc.perform(postResult(id, result("CANCELLED", null, null, 1)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("HEARING_NOT_SCHEDULED"));
        mvc.perform(put("/api/v1/hearings/" + id).with(as(Permission.HEARING_MANAGE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update("2025-02-02", null, "PLEADING", null, 1)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("HEARING_NOT_SCHEDULED"));
        assertThat(statusOf(id)).isEqualTo("HELD");
    }

    @Test
    void anAdjournmentSchedulesTheNextHearing() throws Exception {
        UUID caseId = createCase("Adjourned");
        UUID proceeding = createProceeding(caseId, "70");
        UUID id = schedule(proceeding, "2025-02-01", "09:00");
        clearInvocations(caseLookup);

        MvcResult adjourned = mvc.perform(postResult(id, """
                        {"status":"ADJOURNED","decision":"for the defendant's memorandum",
                         "nextHearingDate":"2025-03-15","nextHearingTime":"10:00","version":0}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ADJOURNED"))
                .andExpect(jsonPath("$.nextHearingId").isString())
                .andReturn();
        String next = JsonPath.read(adjourned.getResponse().getContentAsString(), "$.nextHearingId");
        verify(caseLookup).recordEvent(eq(caseId), eq("HEARING_ADJOURNED"), contains("2025-03-15"));

        mvc.perform(get("/api/v1/hearings/" + next).with(as(Permission.HEARING_VIEW)))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.hearingDate").value("2025-03-15"))
                .andExpect(jsonPath("$.hearingTime").value("10:00"))
                .andExpect(jsonPath("$.purpose").value("PLEADING"))
                .andExpect(jsonPath("$.proceedingId").value(proceeding.toString()))
                .andExpect(jsonPath("$.adjournedFromId").value(id.toString()));
        assertThat(jdbc.queryForObject("select created_by from hearing where id = ?::uuid", UUID.class, next))
                .isEqualTo(CLERK);

        // The continued hearing cannot move to or before the hearing it continues.
        mvc.perform(put("/api/v1/hearings/" + next).with(as(Permission.HEARING_MANAGE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update("2025-02-01", null, "PLEADING", null, 0)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("ADJOURNMENT_NOT_LATER"));
        mvc.perform(put("/api/v1/hearings/" + next).with(as(Permission.HEARING_MANAGE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update("2025-02-02", null, "JUDGMENT", null, 0)))
                .andExpect(status().isOk());
    }

    @Test
    void anAdjournmentNeedsALaterFreeNextDate() throws Exception {
        UUID proceeding = createProceeding(createCase("Adjournment rules"), "80");
        UUID id = schedule(proceeding, "2025-02-01");
        schedule(proceeding, "2025-03-01");

        mvc.perform(postResult(id, result("ADJOURNED", null, null, 0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("nextHearingDate"))
                .andExpect(jsonPath("$.errors[0].code").value("NotNull"));
        mvc.perform(postResult(id, result("ADJOURNED", null, "2025-02-01", 0)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("ADJOURNMENT_NOT_LATER"));
        mvc.perform(postResult(id, result("ADJOURNED", null, today().plusYears(2).plusDays(1).toString(), 0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("nextHearingDate"))
                .andExpect(jsonPath("$.errors[0].code").value("Range"));
        // That day already has a hearing of this proceeding: nothing changes.
        mvc.perform(postResult(id, result("ADJOURNED", null, "2025-03-01", 0)))
                .andExpect(status().isConflict());
        assertThat(statusOf(id)).isEqualTo("SCHEDULED");
        assertThat(rows("hearing")).isEqualTo(2);

        mvc.perform(postResult(id, result("HELD", null, "2025-03-02", 0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("nextHearingDate"))
                .andExpect(jsonPath("$.errors[0].code").value("Null"));
        mvc.perform(postResult(id, result("ADJOURNED", "d".repeat(2001), "2025-03-02", 0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("decision"));
        mvc.perform(postResult(id, result("SCHEDULED", null, null, 0))).andExpect(status().isBadRequest());
    }

    @Test
    void aFutureHearingCanOnlyBeCancelled() throws Exception {
        UUID caseId = createCase("Future");
        UUID proceeding = createProceeding(caseId, "90");
        UUID id = schedule(proceeding, today().plusDays(1));
        mvc.perform(postResult(id, result("HELD", null, null, 0)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("HEARING_NOT_YET_HELD"));
        mvc.perform(postResult(id, result("ADJOURNED", null, today().plusDays(9).toString(), 0)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("HEARING_NOT_YET_HELD"));
        UUID todays = schedule(proceeding, today());
        mvc.perform(postResult(todays, result("HELD", null, null, 0))).andExpect(status().isOk());

        mvc.perform(postResult(id, result("CANCELLED", "struck off the roll", null, 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        verify(caseLookup).recordEvent(eq(caseId), eq("HEARING_CANCELLED"), anyString());
        // A cancelled hearing frees its day.
        schedule(proceeding, today().plusDays(1));
    }

    @Test
    void anArchivedCaseTakesNoHearingChanges() throws Exception {
        UUID caseId = createCase("Archived");
        UUID proceeding = createProceeding(caseId, "95");
        UUID id = schedule(proceeding, "2025-02-01");
        archive(caseId);

        mvc.perform(postHearing(proceeding, hearing("2025-02-05", null, "OTHER", null)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("CASE_ARCHIVED"));
        mvc.perform(put("/api/v1/hearings/" + id).with(as(Permission.HEARING_MANAGE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update("2025-02-03", null, "OTHER", null, 0)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("CASE_ARCHIVED"));
        mvc.perform(postResult(id, result("HELD", null, null, 0)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("CASE_ARCHIVED"));
        mvc.perform(get("/api/v1/cases/" + caseId + "/hearings").with(as(Permission.HEARING_VIEW)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(1));
    }

    // QA-0188 unauthenticated access

    @Test
    void everyEndpointNeedsASession() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(get("/api/v1/hearings")).andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
        mvc.perform(get("/api/v1/hearings/" + id)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/cases/" + id + "/hearings")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/proceedings/" + id + "/hearings").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/hearings/" + id).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/hearings/" + id + "/result").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
    }

    // QA-0189 insufficient permission

    @Test
    void viewersCannotWriteAndOthersCannotRead() throws Exception {
        UUID caseId = createCase("Permissions");
        UUID proceeding = createProceeding(caseId, "100");
        String body = hearing("2025-02-01", null, "PLEADING", null);
        mvc.perform(post("/api/v1/proceedings/" + proceeding + "/hearings")
                        .with(as(Permission.HEARING_VIEW, Permission.CASE_EDIT, Permission.PROCEEDING_MANAGE))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden()).andExpect(content().contentTypeCompatibleWith(PROBLEM));
        // Refused before the body is read: an invalid body still answers 403.
        mvc.perform(post("/api/v1/proceedings/" + proceeding + "/hearings").with(as(Permission.HEARING_VIEW))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        UUID id = schedule(proceeding, "2025-02-01");
        mvc.perform(put("/api/v1/hearings/" + id).with(as(Permission.HEARING_VIEW)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update("2025-02-02", null, "PLEADING", null, 0)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/hearings/" + id + "/result").with(as(Permission.HEARING_VIEW)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(result("HELD", null, null, 0)))
                .andExpect(status().isForbidden());
        for (String path : List.of("/api/v1/hearings", "/api/v1/hearings/" + id,
                "/api/v1/cases/" + caseId + "/hearings")) {
            mvc.perform(get(path).with(as(Permission.CASE_VIEW, Permission.PROCEEDING_VIEW)))
                    .andExpect(status().isForbidden());
        }
        assertThat(rows("hearing")).isEqualTo(1);
        assertThat(statusOf(id)).isEqualTo("SCHEDULED");
    }

    @Test
    void aRefusalIsAuditedAsAccessDenied() throws Exception {
        UUID actor = UUID.randomUUID();
        UUID hearingId = UUID.randomUUID();
        mvc.perform(post("/api/v1/hearings/" + hearingId + "/result").with(csrf())
                        .with(as(actor, "refused-" + actor, Permission.HEARING_VIEW))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("""
                select count(*) from audit_event where action = 'ACCESS_DENIED' and outcome = 'DENIED'
                and actor_id = ? and target_id = ?""", Integer.class, actor,
                "POST /api/v1/hearings/" + hearingId + "/result")).isEqualTo(1);
    }

    // QA-0190 record integrity and rollback

    @Test
    void unknownOrMalformedReferencesAreRefused() throws Exception {
        mvc.perform(postHearing(UUID.randomUUID(), hearing("2025-02-01", null, "PLEADING", null)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/hearings/" + UUID.randomUUID()).with(as(Permission.HEARING_VIEW)))
                .andExpect(status().isNotFound());
        mvc.perform(postResult(UUID.randomUUID(), result("HELD", null, null, 0))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/hearings/not-a-uuid").with(as(Permission.HEARING_VIEW)))
                .andExpect(status().isBadRequest());
        assertThat(rows("hearing")).isZero();
    }

    @Test
    void aFailingTimelineEventRollsTheWriteBack() throws Exception {
        UUID caseId = createCase("Rollback");
        UUID proceeding = createProceeding(caseId, "110");
        UUID id = schedule(proceeding, "2025-02-01");
        doThrow(new IllegalStateException("timeline unavailable")).when(caseLookup)
                .recordEvent(eq(caseId), anyString(), anyString());

        mvc.perform(postHearing(proceeding, hearing("2025-02-02", null, "OTHER", null)))
                .andExpect(status().isInternalServerError());
        mvc.perform(postResult(id, result("ADJOURNED", null, "2025-03-01", 0)))
                .andExpect(status().isInternalServerError());
        assertThat(rows("hearing")).isEqualTo(1);
        assertThat(statusOf(id)).isEqualTo("SCHEDULED");
        assertThat(jdbc.queryForObject("select version from hearing where id = ?", Long.class, id)).isZero();
    }

    @Test
    void theDatabaseKeepsResultsAndDaysConsistent() throws Exception {
        UUID caseId = createCase("Schema");
        UUID proceeding = createProceeding(caseId, "120");
        schedule(proceeding, "2025-02-01");
        String insert = """
                insert into hearing (case_id, proceeding_id, hearing_date, purpose, status, created_by, updated_by)
                values (?, ?, ?, 'OTHER', ?, ?, ?)""";
        UUID actor = UUID.randomUUID();
        assertThatThrownBy(() -> jdbc.update(insert, caseId, proceeding, LocalDate.parse("2025-02-01"), "SCHEDULED",
                actor, actor)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, caseId, proceeding, LocalDate.parse("2025-02-03"), "HELD",
                actor, actor)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, caseId, proceeding, LocalDate.parse("2025-02-04"), "POSTPONED",
                actor, actor)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), proceeding, LocalDate.parse("2025-02-05"),
                "SCHEDULED", actor, actor)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(rows("hearing")).isEqualTo(1);
    }

    // QA-0191 concurrent or repeat request

    @Test
    void theSameProceedingAndDayIsAConflict() throws Exception {
        UUID proceeding = createProceeding(createCase("Duplicate"), "130");
        UUID other = createProceeding(createCase("Other duplicate"), "131");
        UUID first = schedule(proceeding, "2025-02-01");
        mvc.perform(postHearing(proceeding, hearing("2025-02-01", "12:00", "OTHER", null)))
                .andExpect(status().isConflict());
        schedule(other, "2025-02-01");
        UUID second = schedule(proceeding, "2025-02-02");
        mvc.perform(put("/api/v1/hearings/" + second).with(as(Permission.HEARING_MANAGE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update("2025-02-01", null, "PLEADING", null, 0)))
                .andExpect(status().isConflict());
        assertThat(rows("hearing")).isEqualTo(3);
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void aStaleVersionIsAConflictAndChangesNothing() throws Exception {
        UUID id = schedule(createProceeding(createCase("Stale"), "140"), "2025-02-01");
        mvc.perform(put("/api/v1/hearings/" + id).with(as(Permission.HEARING_MANAGE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update("2025-02-02", null, "PLEADING", null, 0)))
                .andExpect(status().isOk());
        mvc.perform(put("/api/v1/hearings/" + id).with(as(Permission.HEARING_MANAGE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update("2025-02-03", null, "PLEADING", null, 0)))
                .andExpect(status().isConflict());
        mvc.perform(postResult(id, result("HELD", null, null, 0))).andExpect(status().isConflict());
        assertThat(statusOf(id)).isEqualTo("SCHEDULED");
        assertThat(jdbc.queryForObject("select hearing_date from hearing where id = ?", LocalDate.class, id))
                .isEqualTo(LocalDate.parse("2025-02-02"));
    }

    @Test
    void twoRacingAdjournmentsRecordOneResult() throws Exception {
        UUID id = schedule(createProceeding(createCase("Race"), "150"), "2025-02-01");
        MockHttpServletRequestBuilder first = postResult(id, result("ADJOURNED", null, "2025-03-01", 0));
        MockHttpServletRequestBuilder second = postResult(id, result("ADJOURNED", null, "2025-03-08", 0));
        pauseHearingUpdates();
        List<Integer> statuses;
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<MvcResult> paused = pool.submit(() -> mvc.perform(first).andReturn());
            awaitPause();
            Future<MvcResult> other = pool.submit(() -> mvc.perform(second).andReturn());
            statuses = List.of(paused.get(30, TimeUnit.SECONDS).getResponse().getStatus(),
                    other.get(30, TimeUnit.SECONDS).getResponse().getStatus());
        } finally {
            dropPause();
        }
        assertThat(statuses).containsExactly(200, 409);
        assertThat(statusOf(id)).isEqualTo("ADJOURNED");
        assertThat(jdbc.queryForObject("select count(*) from hearing where adjourned_from_id = ?", Integer.class, id))
                .isEqualTo(1);
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

    static String update(String date, String time, String purpose, String notes, long version) {
        return """
                {"hearingDate":%s,"hearingTime":%s,"purpose":%s,"notes":%s,"version":%d}""".formatted(quote(date),
                quote(time), quote(purpose), quote(notes), version);
    }

    static String result(String status, String decision, String nextDate, long version) {
        return """
                {"status":%s,"decision":%s,"nextHearingDate":%s,"version":%d}""".formatted(quote(status),
                quote(decision), quote(nextDate), version);
    }
}
