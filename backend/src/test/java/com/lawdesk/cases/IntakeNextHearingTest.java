package com.lawdesk.cases;

import static com.lawdesk.security.TestActors.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.lawdesk.security.Permission;

/**
 * LD-045 / section 15: the next hearing may be given at registration. It is optional, is created with the case in one
 * transaction through the hearing service, needs HEARING_MANAGE only when it is given, and then shows in the case, in
 * the hearing list and in the calendar.
 */
class IntakeNextHearingTest extends GovernmentIntakeTestSupport {

    private static final UUID REGISTRAR_ID = UUID.fromString("00000000-0000-4000-8000-0000000000c2");

    private static final Permission[] WITH_HEARINGS = withHearings();

    private static Permission[] withHearings() {
        Permission[] all = java.util.Arrays.copyOf(REGISTRAR, REGISTRAR.length + 3);
        all[REGISTRAR.length] = Permission.HEARING_MANAGE;
        all[REGISTRAR.length + 1] = Permission.HEARING_VIEW;
        all[REGISTRAR.length + 2] = Permission.CALENDAR_VIEW;
        return all;
    }

    private static LocalDate soon() {
        return LocalDate.now(ZoneId.of("Asia/Kuwait")).plusDays(10);
    }

    private static String nextHearing(String body) {
        return "\"nextHearing\":{" + body + "}";
    }

    @Test
    void aGivenNextHearingIsCreatedWithTheCaseAndShowsInTheCaseTheHearingListAndTheCalendar() throws Exception {
        UUID specialist = member("Dr. Specialist");
        RequestPostProcessor user = as(REGISTRAR_ID, "registrar", WITH_HEARINGS);
        String date = soon().toString();
        UUID caseId = registered(user, intake(firstInstanceCourt(), specialist,
                nextHearing("\"hearingDate\":\"" + date + "\",\"hearingTime\":\"09:30\",\"notes\":\"  bring the file  \"")));

        assertThat(jdbc.queryForMap("select hearing_date::text d, hearing_time::text t, notes, status, case_id from hearing"))
                .containsEntry("d", date).containsEntry("t", "09:30:00").containsEntry("notes", "bring the file")
                .containsEntry("status", "SCHEDULED").containsEntry("case_id", caseId);
        assertThat(count("select count(*) from case_event where case_id = ? and type = 'HEARING_SCHEDULED'", caseId))
                .isEqualTo(1);

        mvc.perform(get("/api/v1/cases/" + caseId + "/hearings").with(user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].hearingDate").value(date));
        mvc.perform(get("/api/v1/calendar/events").param("from", date).param("to", date).param("caseId", caseId.toString())
                .with(user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[*].type").value(hasItem("HEARING")));
    }

    @Test
    void theHearingOfAnIntakeCaseIsInvisibleInTheHearingListAndTheCalendarToAUserOutsideTheCase() throws Exception {
        UUID specialist = member("Dr. Specialist");
        UUID outsider = member("Outsider");
        String date = soon().toString();
        UUID caseId = registered(as(REGISTRAR_ID, "registrar", WITH_HEARINGS), intake(firstInstanceCourt(), specialist,
                nextHearing("\"hearingDate\":\"" + date + "\"")));
        RequestPostProcessor other = com.lawdesk.security.TestActors.scoped(outsider, "outsider", java.util.List.of(), Permission.CASE_VIEW, Permission.HEARING_VIEW,
                Permission.CALENDAR_VIEW);

        mvc.perform(get("/api/v1/hearings").param("from", date).param("to", date).with(other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0));
        mvc.perform(get("/api/v1/calendar/events").param("from", date).param("to", date).with(other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events.length()").value(0));
        mvc.perform(get("/api/v1/cases/" + caseId + "/hearings").with(other)).andExpect(status().isNotFound());
    }

    @Test
    void withoutANextHearingNothingIsScheduledAndNoHearingPermissionIsNeeded() throws Exception {
        UUID specialist = member("Dr. Specialist");
        registered(as(REGISTRAR_ID, "registrar", REGISTRAR), intake(firstInstanceCourt(), specialist, null));

        assertThat(count("select count(*) from hearing")).isZero();
        assertThat(count("select count(*) from legal_case")).isEqualTo(1);
    }

    @Test
    void aNextHearingWithoutTheHearingPermissionIsRefusedAndTheCaseIsNotRegistered() throws Exception {
        UUID specialist = member("Dr. Specialist");
        String json = intake(firstInstanceCourt(), specialist, nextHearing("\"hearingDate\":\"" + soon() + "\""));

        mvc.perform(register(as(REGISTRAR_ID, "registrar", REGISTRAR), json)).andExpect(status().isForbidden());

        assertThat(count("select count(*) from legal_case")).isZero();
        assertThat(count("select count(*) from hearing")).isZero();
    }

    @Test
    void aBadHearingDateIsRefusedAndTheWholeRegistrationRollsBack() throws Exception {
        UUID specialist = member("Dr. Specialist");
        RequestPostProcessor user = as(REGISTRAR_ID, "registrar", WITH_HEARINGS);
        UUID court = firstInstanceCourt();

        // Not a date, and no date at all: bean validation.
        mvc.perform(register(user, intake(court, specialist, nextHearing("\"hearingDate\":\"31-02-2027\""))))
                .andExpect(status().isBadRequest());
        mvc.perform(register(user, intake(court, specialist, nextHearing("\"notes\":\"no date\""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("nextHearing.hearingDate")));
        // Further ahead than the hearing service allows: the field is named under the next hearing.
        mvc.perform(register(user, intake(court, specialist, nextHearing("\"hearingDate\":\"" + soon().plusYears(5) + "\""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("nextHearing.hearingDate")));
        // Before the filing date of the first stage (2026-01-12): a hearing rule.
        mvc.perform(register(user, intake(court, specialist, nextHearing("\"hearingDate\":\"2026-01-11\""))))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.rule").value("HEARING_BEFORE_FILING"));
        // A time that is not HH:mm.
        mvc.perform(register(user, intake(court, specialist,
                nextHearing("\"hearingDate\":\"" + soon() + "\",\"hearingTime\":\"25:99\""))))
                .andExpect(status().isBadRequest());

        assertThat(count("select count(*) from legal_case")).isZero();
        assertThat(count("select count(*) from proceeding")).isZero();
        assertThat(count("select count(*) from hearing")).isZero();
    }
}
