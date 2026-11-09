package com.lawdesk.wp6;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.lawdesk.security.Permission;

/**
 * LD-056, requirements section 15 and scenario S11: the judicial calendar is derived from hearings, tasks, expert
 * sessions, information-request dates and manual dates; every source is restricted by the case access policy; days are
 * Kuwait days (Asia/Kuwait) over UTC instants.
 */
class CalendarApiTest extends Wp6TestSupport {

    private static final ZoneId KUWAIT = ZoneId.of("Asia/Kuwait");

    private UUID lead;
    private UUID alternate;
    private UUID colleague;
    private UUID caseId;
    private UUID proceeding;
    private LocalDate day;
    private RequestPostProcessor leadActor;

    @BeforeEach
    void setUp() {
        lead = user("Lead Member");
        alternate = user("Alternate Member");
        colleague = user("Colleague");
        caseId = legalCase(lead, alternate);
        proceeding = proceeding(caseId, "FIRST_INSTANCE");
        // The 15th of next month: a week around it stays inside the month.
        day = today().plusMonths(1).withDayOfMonth(15);
        leadActor = manager(lead);
    }

    private String events(RequestPostProcessor actor, String query) throws Exception {
        return body(mvc.perform(getJson(actor, "/api/v1/calendar/events?" + query)).andExpect(status().isOk()).andReturn());
    }

    private List<String> types(String json) {
        return com.jayway.jsonpath.JsonPath.read(json, "$.events[*].type");
    }

    // ---- S11: a hearing and a task both appear --------------------------------------------------------------------

    @Test
    void aHearingAndATaskBothAppearInTheCalendarOfTheMemberAndInTheGeneralCalendar() throws Exception {
        mvc.perform(postJson(leadActor, "/api/v1/proceedings/" + proceeding + "/hearings", """
                        {"hearingDate":"%s","hearingTime":"09:30","purpose":"PLEADING","kind":"COURT","responsibleMemberId":"%s"}"""
                        .formatted(day, lead))).andExpect(status().isCreated());
        createTask(leadActor, "Prepare the memorandum", caseId, lead, day);

        String mine = events(leadActor, "from=" + day + "&to=" + day + "&userId=me");
        assertThat(types(mine)).containsExactlyInAnyOrder("HEARING", "TASK");
        // The same two are in the department's general calendar (no user filter) for a manager who sees every case.
        UUID boss = user("Department Manager");
        String general = events(viewer(boss, Permission.CASE_VIEW, Permission.CALENDAR_VIEW, Permission.HEARING_VIEW,
                Permission.TASK_VIEW), "from=" + day + "&to=" + day);
        assertThat(types(general)).containsExactlyInAnyOrder("HEARING", "TASK");
        // Another member's personal calendar has neither.
        assertThat(types(events(manager(colleague), "from=" + day + "&to=" + day + "&userId=me"))).isEmpty();
        mvc.perform(getJson(leadActor, "/api/v1/calendar/events?from=" + day + "&to=" + day + "&userId=me"))
                .andExpect(jsonPath("$.zone").value("Asia/Kuwait"))
                .andExpect(jsonPath("$.events[?(@.type=='HEARING')].startTime").value(Matchers.contains("09:30")))
                .andExpect(jsonPath("$.events[?(@.type=='HEARING')].caseId").value(Matchers.contains(caseId.toString())))
                .andExpect(jsonPath("$.events[?(@.type=='TASK')].allDay").value(Matchers.contains(true)))
                .andExpect(jsonPath("$.events[?(@.type=='TASK')].title").value(Matchers.contains("Prepare the memorandum")))
                .andExpect(jsonPath("$.events[?(@.type=='TASK')].refType").value(Matchers.contains("TASK")));
    }

    @Test
    void dayWeekAndMonthViewsReturnTheirRange() throws Exception {
        hearing(caseId, proceeding, day, LocalTime.of(10, 0), "COURT", lead);
        hearing(caseId, proceeding, day.minusDays(10), null, "COURT", lead);
        createTask(leadActor, "Far task", caseId, lead, day.plusDays(9));

        mvc.perform(getJson(leadActor, "/api/v1/calendar/day?date=" + day)).andExpect(status().isOk())
                .andExpect(jsonPath("$.view").value("DAY")).andExpect(jsonPath("$.from").value(day.toString()))
                .andExpect(jsonPath("$.to").value(day.toString())).andExpect(jsonPath("$.events.length()").value(1));
        // The week runs Sunday to Saturday.
        LocalDate sunday = day.minusDays(day.getDayOfWeek().getValue() % 7);
        mvc.perform(getJson(leadActor, "/api/v1/calendar/week?date=" + day)).andExpect(status().isOk())
                .andExpect(jsonPath("$.view").value("WEEK")).andExpect(jsonPath("$.from").value(sunday.toString()))
                .andExpect(jsonPath("$.to").value(sunday.plusDays(6).toString()))
                .andExpect(jsonPath("$.events.length()").value(1));
        YearMonth month = YearMonth.from(day);
        mvc.perform(getJson(leadActor, "/api/v1/calendar/month?month=" + month)).andExpect(status().isOk())
                .andExpect(jsonPath("$.view").value("MONTH")).andExpect(jsonPath("$.from").value(month.atDay(1).toString()))
                .andExpect(jsonPath("$.to").value(month.atEndOfMonth().toString()))
                .andExpect(jsonPath("$.events.length()").value(3))
                .andExpect(jsonPath("$.days[?(@.date=='" + day + "')].count").value(Matchers.contains(1)));
    }

    @Test
    void thePeriodAndTheViewAreValidated() throws Exception {
        mvc.perform(getJson(leadActor, "/api/v1/calendar/events?from=" + day + "&to=" + day.minusDays(1)))
                .andExpect(status().isBadRequest());
        mvc.perform(getJson(leadActor, "/api/v1/calendar/events?from=" + day + "&to=" + day.plusDays(101)))
                .andExpect(status().isBadRequest());
        mvc.perform(getJson(leadActor, "/api/v1/calendar/events?from=" + day)).andExpect(status().isBadRequest());
        mvc.perform(getJson(leadActor, "/api/v1/calendar/events?from=x&to=y")).andExpect(status().isBadRequest());
        mvc.perform(getJson(leadActor, "/api/v1/calendar/month?month=2026-13")).andExpect(status().isBadRequest());
        mvc.perform(getJson(leadActor, "/api/v1/calendar/day")).andExpect(status().isBadRequest());
        mvc.perform(getJson(leadActor, "/api/v1/calendar/events?from=" + day + "&to=" + day + "&type=NOPE"))
                .andExpect(status().isBadRequest());
        mvc.perform(getJson(leadActor, "/api/v1/calendar/events?from=" + day + "&to=" + day + "&userId=nobody"))
                .andExpect(status().isBadRequest());
        mvc.perform(getJson(leadActor, "/api/v1/calendar/events?from=" + day + "&to=" + day.plusDays(100)))
                .andExpect(status().isOk());
    }

    // ---- sources and filters ----------------------------------------------------------------------------------------

    @Test
    void expertSessionsInformationRequestDatesAndManualDatesAppear() throws Exception {
        UUID expert = jdbc.queryForObject("""
                insert into expert (name, specialty, created_by, updated_by) values ('Eng. Expert', 'Engineering',
                gen_random_uuid(), gen_random_uuid()) returning id""", UUID.class);
        UUID referral = jdbc.queryForObject("""
                insert into expert_referral (case_id, proceeding_id, expert_id, referral_date, created_by, updated_by)
                values (?, ?, ?, '2026-01-12', gen_random_uuid(), gen_random_uuid()) returning id""", UUID.class,
                caseId, proceeding, expert);
        jdbc.update("insert into expert_session (referral_id, session_date, session_time, created_by) values (?, ?, '11:00', gen_random_uuid())",
                referral, day);
        jdbc.update("""
                insert into info_request (case_id, request_no, subject, status, due_on, responsible_member_id, created_by, updated_by)
                values (?, 'IR-2026-W6001', 'Missing file', 'SENT', ?, ?, gen_random_uuid(), gen_random_uuid())""", caseId, day, lead);
        jdbc.update("""
                insert into info_request (case_id, request_no, subject, status, due_on, closed_on, responsible_member_id, created_by, updated_by)
                values (?, 'IR-2026-W6002', 'Closed one', 'CLOSED', ?, '2026-01-02', ?, gen_random_uuid(), gen_random_uuid())""", caseId, day, lead);
        mvc.perform(postJson(leadActor, "/api/v1/calendar/dates", """
                        {"kind":"APPEAL_DEADLINE","title":"Appeal date set by the lawyer","caseId":"%s","date":"%s"}"""
                        .formatted(caseId, day))).andExpect(status().isCreated());
        mvc.perform(postJson(leadActor, "/api/v1/calendar/dates", """
                        {"kind":"MEETING","title":"Department meeting","date":"%s","startTime":"13:00","endTime":"14:00"}"""
                        .formatted(day))).andExpect(status().isCreated());

        String all = events(leadActor, "from=" + day + "&to=" + day);
        assertThat(types(all)).containsExactlyInAnyOrder("EXPERT_SESSION", "INFO_REQUEST_DUE", "APPEAL_DEADLINE", "MEETING");
        assertThat(types(events(leadActor, "from=" + day + "&to=" + day + "&type=MEETING&type=EXPERT_SESSION")))
                .containsExactlyInAnyOrder("EXPERT_SESSION", "MEETING");
        // The case filter keeps what belongs to the case: the meeting has none.
        assertThat(types(events(leadActor, "from=" + day + "&to=" + day + "&caseId=" + caseId)))
                .containsExactlyInAnyOrder("EXPERT_SESSION", "INFO_REQUEST_DUE", "APPEAL_DEADLINE");
    }

    @Test
    void hearingKindsBecomeCourtExpertAndInvestigationEntries() throws Exception {
        UUID appeal = proceeding(caseId, "APPEAL");
        hearing(caseId, proceeding, day, LocalTime.of(9, 0), "COURT", lead);
        hearing(caseId, appeal, day, LocalTime.of(10, 0), "COURT", lead);
        hearing(caseId, proceeding, day.plusDays(1), LocalTime.of(9, 0), "EXPERT", lead);
        hearing(caseId, proceeding, day.plusDays(2), LocalTime.of(9, 0), "INVESTIGATION", lead);
        String json = events(leadActor, "from=" + day + "&to=" + day.plusDays(2));
        assertThat(types(json)).containsExactly("HEARING", "HEARING", "EXPERT_SESSION", "INVESTIGATION");
        List<String> stages = com.jayway.jsonpath.JsonPath.read(json, "$.events[?(@.type=='HEARING')].stage");
        assertThat(stages).containsExactlyInAnyOrder("FIRST_INSTANCE", "APPEAL");
    }

    @Test
    void eachSourceNeedsTheMatchingViewPermission() throws Exception {
        hearing(caseId, proceeding, day, LocalTime.of(9, 0), "COURT", lead);
        createTask(leadActor, "A task", caseId, lead, day);
        RequestPostProcessor noHearings = staff(lead, Permission.CASE_VIEW, Permission.CALENDAR_VIEW, Permission.TASK_VIEW);
        assertThat(types(events(noHearings, "from=" + day + "&to=" + day))).containsExactly("TASK");
        RequestPostProcessor noTasks = staff(lead, Permission.CASE_VIEW, Permission.CALENDAR_VIEW, Permission.HEARING_VIEW);
        assertThat(types(events(noTasks, "from=" + day + "&to=" + day))).containsExactly("HEARING");
    }

    @Test
    void entriesAreOrderedByTimeAndCarryTheirOwnerAndTheAlternate() throws Exception {
        hearing(caseId, proceeding, day, LocalTime.of(12, 0), "COURT", lead);
        UUID early = hearing(caseId, proceeding(caseId, "APPEAL"), day, LocalTime.of(8, 0), "COURT", alternate);
        mvc.perform(getJson(leadActor, "/api/v1/calendar/day?date=" + day)).andExpect(status().isOk())
                .andExpect(jsonPath("$.events[0].refId").value(early.toString()))
                .andExpect(jsonPath("$.events[0].ownerId").value(alternate.toString()))
                .andExpect(jsonPath("$.events[0].ownerName").value("Alternate Member"))
                .andExpect(jsonPath("$.events[1].ownerName").value("Lead Member"))
                .andExpect(jsonPath("$.events[1].alternateMemberId").value(alternate.toString()))
                .andExpect(jsonPath("$.events[1].alternateMemberName").value("Alternate Member"));
    }

    // ---- deadlines of WP4 plug in through the same interface -----------------------------------------------------------

    private UUID deadline(UUID forCase, String action, LocalDate due, String status, String calcStatus, UUID owner) {
        return jdbc.queryForObject("""
                insert into deadline (case_id, source_type, source_text, due_on, action, owner_id, status, completed_on,
                                      calc_status, approved_by, approved_at, created_by, updated_by)
                values (?, 'TEXT', 'Judgment of the court', ?, ?, ?, ?, case when ? = 'COMPLETED' then current_date end, ?,
                        case when ? = 'APPROVED' then gen_random_uuid() end, case when ? = 'APPROVED' then now() end,
                        gen_random_uuid(), gen_random_uuid()) returning id""", UUID.class, forCase, due, action, owner, status,
                status, calcStatus, calcStatus, calcStatus);
    }

    @Test
    void legalDeadlinesAppearOnTheirDueDateOnceApprovedOrEnteredByHand() throws Exception {
        UUID open = deadline(caseId, "File the appeal", day, "OPEN", "MANUAL", lead);
        deadline(caseId, "Approved one", day, "OPEN", "APPROVED", null);
        deadline(caseId, "Done one", day, "COMPLETED", "MANUAL", lead);
        deadline(caseId, "Missed one", day, "MISSED", "MANUAL", lead);
        // A proposal nobody approved is not a date: nothing is shown for it (decision D6).
        jdbc.update("""
                insert into deadline_rule (code, label, period_days, count_method, counts_start_day, roll_forward, closed_weekdays,
                                           legal_source, effective_from, status, approved_by, approved_at, created_by, updated_by)
                values ('W6', 'Rule', 30, 'CALENDAR', false, false, '', 'Source', '2026-01-01', 'APPROVED', gen_random_uuid(), now(),
                        gen_random_uuid(), gen_random_uuid())""");
        UUID rule = jdbc.queryForObject("select id from deadline_rule where code = 'W6'", UUID.class);
        jdbc.update("""
                insert into deadline (case_id, source_type, source_text, due_on, action, rule_id, calculation, status, calc_status,
                                      created_by, updated_by)
                values (?, 'TEXT', 'x', ?, 'Proposed only', ?, '{}'::jsonb, 'OPEN', 'PROPOSED', gen_random_uuid(), gen_random_uuid())""",
                caseId, day, rule);
        String json = events(leadActor, "from=" + day + "&to=" + day + "&type=DEADLINE");
        assertThat(types(json)).hasSize(4).containsOnly("DEADLINE");
        List<String> titles = com.jayway.jsonpath.JsonPath.read(json, "$.events[*].title");
        assertThat(titles).containsExactlyInAnyOrder("File the appeal", "Approved one", "Done one", "Missed one");
        mvc.perform(getJson(leadActor, "/api/v1/calendar/events?from=" + day + "&to=" + day + "&type=DEADLINE"))
                .andExpect(jsonPath("$.events[?(@.refId=='" + open + "')].refType").value(Matchers.contains("DEADLINE")))
                .andExpect(jsonPath("$.events[?(@.refId=='" + open + "')].ownerId").value(Matchers.contains(lead.toString())))
                .andExpect(jsonPath("$.events[?(@.title=='Missed one')].status").value(Matchers.contains("MISSED")))
                .andExpect(jsonPath("$.events[?(@.title=='Approved one')].ownerId").value(Matchers.contains(lead.toString())));
        // No notification is raised from it here: the deadlines have their own reminders (WP4).
        assertThat(types(events(leadActor, "from=" + day + "&to=" + day))).contains("DEADLINE");
    }

    @Test
    void deadlinesFollowTheCaseAccessRulesAndTheirPermission() throws Exception {
        deadline(caseId, "File the appeal", day, "OPEN", "MANUAL", lead);
        Permission[] all = {Permission.CASE_VIEW, Permission.CALENDAR_VIEW, Permission.DEADLINE_VIEW};
        String range = "from=" + day + "&to=" + day;
        assertThat(types(events(outsider(colleague, all), range))).isEmpty();
        assertThat(types(events(viewer(colleague, all), range))).containsExactly("DEADLINE");
        assertThat(types(events(staff(lead, Permission.CASE_VIEW, Permission.CALENDAR_VIEW), range))).isEmpty();
        jdbc.update("update legal_case set confidentiality = 'RESTRICTED' where id = ?", caseId);
        assertThat(types(events(viewer(colleague, all), range))).isEmpty();
    }

    // ---- access (decision D3) ---------------------------------------------------------------------------------------

    @Test
    void everySourceIsRestrictedToTheCasesTheUserMaySee() throws Exception {
        hearing(caseId, proceeding, day, LocalTime.of(9, 0), "COURT", lead);
        createTask(leadActor, "On the case", caseId, lead, day);
        createTask(leadActor, "Private to the lead", null, lead, day);
        jdbc.update("""
                insert into info_request (case_id, request_no, subject, status, due_on, created_by, updated_by)
                values (?, 'IR-2026-W6003', 'Hidden', 'SENT', ?, gen_random_uuid(), gen_random_uuid())""", caseId, day);
        mvc.perform(postJson(leadActor, "/api/v1/calendar/dates", """
                        {"kind":"OTHER","title":"On the case","caseId":"%s","date":"%s"}""".formatted(caseId, day)))
                .andExpect(status().isCreated());
        Permission[] all = {Permission.CASE_VIEW, Permission.CALENDAR_VIEW, Permission.HEARING_VIEW, Permission.TASK_VIEW,
            Permission.INFO_REQUEST_VIEW};
        String range = "from=" + day + "&to=" + day;

        // Neither a member nor scoped: nothing, not even the private task of someone else.
        assertThat(types(events(outsider(colleague, all), range))).isEmpty();
        assertThat(types(events(outsider(colleague, all), range + "&caseId=" + caseId))).isEmpty();
        // The department-wide scope sees the case's entries and, like the task list, the tasks that belong to no case.
        assertThat(types(events(viewer(colleague, all), range))).containsExactlyInAnyOrder("HEARING", "TASK", "TASK",
                "INFO_REQUEST_DUE", "OTHER");
        // A member sees them and their own private task.
        assertThat(types(events(staff(lead, all), range))).containsExactlyInAnyOrder("HEARING", "TASK", "TASK",
                "INFO_REQUEST_DUE", "OTHER");
        // A restricted case needs an explicit grant: scope ALL does not show its entries (the case-less task stays).
        jdbc.update("update legal_case set confidentiality = 'RESTRICTED' where id = ?", caseId);
        assertThat(types(events(viewer(colleague, all), range))).containsExactly("TASK");
    }

    @Test
    void aTaskOnlyAssigneeSeesTheEventsOfTheCaseTheyWereGivenATaskOn() throws Exception {
        hearing(caseId, proceeding, day, LocalTime.of(9, 0), "COURT", lead);
        createTask(leadActor, "Collect", caseId, colleague, day);
        RequestPostProcessor assignee = outsider(colleague, Permission.CASE_VIEW, Permission.CALENDAR_VIEW,
                Permission.HEARING_VIEW, Permission.TASK_VIEW);
        assertThat(types(events(assignee, "from=" + day + "&to=" + day))).containsExactlyInAnyOrder("HEARING", "TASK");
        // Done: the case closes to them again (the task itself stays on their own calendar).
        jdbc.update("update task set status = 'COMPLETED', completed_at = now() where case_id = ?", caseId);
        assertThat(types(events(assignee, "from=" + day + "&to=" + day))).containsExactly("TASK");
    }

    @Test
    void theCalendarNeedsASessionAndThePermission() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/v1/calendar/events?from=" + day + "&to=" + day))
                .andExpect(status().isUnauthorized());
        mvc.perform(getJson(staff(lead, Permission.CASE_VIEW), "/api/v1/calendar/events?from=" + day + "&to=" + day))
                .andExpect(status().isForbidden());
        mvc.perform(getJson(staff(lead, Permission.CASE_VIEW), "/api/v1/calendar/day?date=" + day))
                .andExpect(status().isForbidden());
        mvc.perform(getJson(staff(lead, Permission.CASE_VIEW), "/api/v1/calendar/conflicts?from=" + day + "&to=" + day))
                .andExpect(status().isForbidden());
    }

    // ---- time zone --------------------------------------------------------------------------------------------------

    @Test
    void daysAreKuwaitDaysOverUtcInstants() throws Exception {
        UUID late = hearing(caseId, proceeding, day, LocalTime.of(23, 30), "COURT", lead);
        // 00:30 in Kuwait on the next day is 21:30 UTC on `day`: stored in UTC, shown on the Kuwait day.
        mvc.perform(postJson(leadActor, "/api/v1/calendar/dates", """
                        {"kind":"MEETING","title":"After midnight","date":"%s","startTime":"00:30"}"""
                        .formatted(day.plusDays(1)))).andExpect(status().isCreated());
        Instant stored = jdbc.queryForObject("select starts_at from calendar_date", java.sql.Timestamp.class).toInstant();
        assertThat(stored).isEqualTo(day.plusDays(1).atTime(0, 30).atZone(KUWAIT).toInstant());
        assertThat(stored.atZone(ZoneId.of("UTC")).toLocalDate()).isEqualTo(day);

        mvc.perform(getJson(leadActor, "/api/v1/calendar/day?date=" + day)).andExpect(status().isOk())
                .andExpect(jsonPath("$.events.length()").value(1))
                .andExpect(jsonPath("$.events[0].refId").value(late.toString()))
                .andExpect(jsonPath("$.events[0].startsAt").value(day.atTime(23, 30).atZone(KUWAIT).toInstant().toString()));
        mvc.perform(getJson(leadActor, "/api/v1/calendar/day?date=" + day.plusDays(1))).andExpect(status().isOk())
                .andExpect(jsonPath("$.events.length()").value(1))
                .andExpect(jsonPath("$.events[0].title").value("After midnight"))
                .andExpect(jsonPath("$.events[0].date").value(day.plusDays(1).toString()))
                .andExpect(jsonPath("$.events[0].startTime").value("00:30"));
    }

    // ---- closed cases -----------------------------------------------------------------------------------------------

    @Test
    void pastHearingsStayAfterTheCaseClosesAndFutureOnesAreFlaggedForVerification() throws Exception {
        LocalDate past = today().minusDays(20);
        hearing(caseId, proceeding, past, LocalTime.of(9, 0), "COURT", lead);
        hearing(caseId, proceeding(caseId, "APPEAL"), day, LocalTime.of(9, 0), "COURT", lead);
        jdbc.update("update legal_case set status = 'CLOSED' where id = ?", caseId);
        mvc.perform(getJson(leadActor, "/api/v1/calendar/events?from=" + past + "&to=" + past))
                .andExpect(jsonPath("$.events.length()").value(1))
                .andExpect(jsonPath("$.events[0].caseStatus").value("CLOSED"))
                .andExpect(jsonPath("$.events[0].closedCaseFuture").value(false));
        mvc.perform(getJson(leadActor, "/api/v1/calendar/events?from=" + day + "&to=" + day))
                .andExpect(jsonPath("$.events.length()").value(1))
                .andExpect(jsonPath("$.events[0].closedCaseFuture").value(true));
    }

    // ---- conflicts --------------------------------------------------------------------------------------------------

    @Test
    void twoTimedEntriesOfTheSameMemberAtTheSameTimeConflictAndTheAlternateIsOffered() throws Exception {
        UUID otherCase = legalCase(lead, alternate);
        UUID first = hearing(caseId, proceeding, day, LocalTime.of(9, 0), "COURT", lead);
        UUID second = hearing(otherCase, proceeding(otherCase, "FIRST_INSTANCE"), day, LocalTime.of(9, 0), "COURT", lead);
        hearing(otherCase, proceeding(otherCase, "APPEAL"), day, LocalTime.of(11, 0), "COURT", lead);
        // Different members at the same time do not conflict; neither do untimed entries nor cancelled hearings.
        hearing(caseId, proceeding(caseId, "APPEAL"), day, LocalTime.of(9, 0), "COURT", alternate);
        hearing(caseId, proceeding(caseId, "CASSATION"), day, null, "COURT", lead);
        UUID cancelled = hearing(caseId, proceeding(caseId, "ENFORCEMENT"), day, LocalTime.of(9, 0), "COURT", lead);
        jdbc.update("update hearing set status = 'CANCELLED', result_recorded_at = now(), result_recorded_by = gen_random_uuid() where id = ?", cancelled);

        MvcResult result = mvc.perform(getJson(leadActor, "/api/v1/calendar/conflicts?from=" + day + "&to=" + day))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conflicts.length()").value(1))
                .andExpect(jsonPath("$.conflicts[0].ownerId").value(lead.toString()))
                .andExpect(jsonPath("$.conflicts[0].date").value(day.toString()))
                .andReturn();
        List<String> ids = read(result, "$.conflicts[0].entries[*].refId");
        assertThat(ids).containsExactlyInAnyOrder(first.toString(), second.toString());
        List<String> alternates = read(result, "$.conflicts[0].entries[*].alternateMemberId");
        assertThat(alternates).containsOnly(alternate.toString());
        // The same flag is on the entries of the calendar views.
        mvc.perform(getJson(leadActor, "/api/v1/calendar/day?date=" + day))
                .andExpect(jsonPath("$.conflicts.length()").value(1))
                .andExpect(jsonPath("$.events[?(@.conflict==true)]").value(Matchers.hasSize(2)));
    }

    @Test
    void anEntryWithAnEndOverlapsALaterStartInsideIt() throws Exception {
        mvc.perform(postJson(leadActor, "/api/v1/calendar/dates", """
                        {"kind":"MEETING","title":"Long meeting","date":"%s","startTime":"09:00","endTime":"11:00"}"""
                        .formatted(day))).andExpect(status().isCreated());
        hearing(caseId, proceeding, day, LocalTime.of(10, 0), "COURT", lead);
        hearing(caseId, proceeding(caseId, "APPEAL"), day, LocalTime.of(11, 0), "COURT", lead);
        mvc.perform(getJson(leadActor, "/api/v1/calendar/conflicts?from=" + day + "&to=" + day))
                .andExpect(jsonPath("$.conflicts.length()").value(1))
                .andExpect(jsonPath("$.conflicts[0].entries.length()").value(2));
    }

    // ---- manual dates -----------------------------------------------------------------------------------------------

    @Test
    void manualDatesAreCreatedEditedAndCancelled() throws Exception {
        MvcResult created = mvc.perform(postJson(leadActor, "/api/v1/calendar/dates", """
                        {"kind":"MEMO_DUE","title":"  File the memorandum ","notes":" by the lawyer's date ","caseId":"%s","date":"%s"}"""
                        .formatted(caseId, day)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("File the memorandum"))
                .andExpect(jsonPath("$.allDay").value(true))
                .andExpect(jsonPath("$.ownerId").value(lead.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.version").value(0)).andReturn();
        UUID id = idOf(created);
        mvc.perform(putJson(leadActor, "/api/v1/calendar/dates/" + id, """
                        {"kind":"MEMO_DUE","title":"File the memorandum","date":"%s","startTime":"10:00","endTime":"10:30",
                         "ownerId":"%s","version":0}""".formatted(day.plusDays(1), alternate)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.allDay").value(false))
                .andExpect(jsonPath("$.ownerId").value(alternate.toString()))
                .andExpect(jsonPath("$.date").value(day.plusDays(1).toString()))
                .andExpect(jsonPath("$.version").value(1));
        mvc.perform(putJson(leadActor, "/api/v1/calendar/dates/" + id, """
                        {"kind":"MEMO_DUE","title":"x","date":"%s","version":0}""".formatted(day)))
                .andExpect(status().isConflict());
        mvc.perform(postJson(leadActor, "/api/v1/calendar/dates/" + id + "/status", "{\"status\":\"CANCELLED\",\"version\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(getJson(leadActor, "/api/v1/calendar/events?from=" + day.plusDays(1) + "&to=" + day.plusDays(1)))
                .andExpect(jsonPath("$.events[0].status").value("CANCELLED"));
        mvc.perform(getJson(leadActor, "/api/v1/calendar/dates/" + id)).andExpect(status().isOk());
        assertThat(count("select count(*) from case_event where case_id = ? and type = 'CALENDAR_DATE_ADDED'", caseId)).isEqualTo(1);
    }

    @Test
    void manualDatesAreValidated() throws Exception {
        String base = "\"kind\":\"OTHER\",\"title\":\"t\",\"date\":\"" + day + "\"";
        mvc.perform(postJson(leadActor, "/api/v1/calendar/dates", "{}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(Matchers.hasItems("kind", "title", "date")));
        mvc.perform(postJson(leadActor, "/api/v1/calendar/dates", "{\"kind\":\"OTHER\",\"title\":\"  \",\"date\":\"" + day + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(postJson(leadActor, "/api/v1/calendar/dates", "{" + base + ",\"kind\":\"HOLIDAY\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(postJson(leadActor, "/api/v1/calendar/dates", "{" + base + ",\"endTime\":\"10:00\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("endTime"));
        mvc.perform(postJson(leadActor, "/api/v1/calendar/dates", "{" + base + ",\"startTime\":\"10:00\",\"endTime\":\"09:00\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("endTime"));
        mvc.perform(postJson(leadActor, "/api/v1/calendar/dates", "{" + base + ",\"ownerId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.rule").value("OWNER_NOT_ASSIGNABLE"));
        assertThat(count("select count(*) from calendar_date")).isZero();
    }

    @Test
    void manualDatesOnACaseFollowTheAccessRules() throws Exception {
        String json = "{\"kind\":\"OTHER\",\"title\":\"t\",\"caseId\":\"" + caseId + "\",\"date\":\"" + day + "\"}";
        Permission[] all = {Permission.CASE_VIEW, Permission.CALENDAR_VIEW, Permission.CALENDAR_MANAGE};
        mvc.perform(postJson(outsider(colleague, all), "/api/v1/calendar/dates", json)).andExpect(status().isNotFound());
        mvc.perform(postJson(viewer(colleague, all), "/api/v1/calendar/dates", json)).andExpect(status().isForbidden());
        mvc.perform(postJson(staff(lead, Permission.CASE_VIEW, Permission.CALENDAR_VIEW), "/api/v1/calendar/dates", json))
                .andExpect(status().isForbidden());
        mvc.perform(MockMvcRequestBuilders.post("/api/v1/calendar/dates").contentType("application/json").content(json))
                .andExpect(status().isUnauthorized());
        UUID id = idOf(mvc.perform(postJson(leadActor, "/api/v1/calendar/dates", json)).andExpect(status().isCreated()).andReturn());
        mvc.perform(getJson(outsider(colleague, all), "/api/v1/calendar/dates/" + id)).andExpect(status().isNotFound());
        mvc.perform(putJson(viewer(colleague, all), "/api/v1/calendar/dates/" + id,
                "{\"kind\":\"OTHER\",\"title\":\"changed\",\"date\":\"" + day + "\",\"version\":0}")).andExpect(status().isForbidden());
        mvc.perform(postJson(viewer(colleague, all), "/api/v1/calendar/dates/" + id + "/status", "{\"status\":\"DONE\",\"version\":0}"))
                .andExpect(status().isForbidden());
        mvc.perform(postJson(outsider(colleague, all), "/api/v1/calendar/dates/" + id + "/status", "{\"status\":\"DONE\",\"version\":0}"))
                .andExpect(status().isNotFound());
        jdbc.update("update legal_case set status = 'ARCHIVED' where id = ?", caseId);
        mvc.perform(postJson(leadActor, "/api/v1/calendar/dates", json)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("CASE_ARCHIVED"));
        assertThat(count("select count(*) from calendar_date")).isEqualTo(1);
    }

    @Test
    void aManualDateWithoutACaseBelongsToItsOwnerAndCreator() throws Exception {
        String json = "{\"kind\":\"MEETING\",\"title\":\"Private meeting\",\"date\":\"" + day + "\",\"ownerId\":\"" + alternate + "\"}";
        UUID id = idOf(mvc.perform(postJson(leadActor, "/api/v1/calendar/dates", json)).andExpect(status().isCreated()).andReturn());
        Permission[] all = {Permission.CASE_VIEW, Permission.CALENDAR_VIEW, Permission.CALENDAR_MANAGE};
        mvc.perform(getJson(staff(alternate, all), "/api/v1/calendar/dates/" + id)).andExpect(status().isOk());
        mvc.perform(getJson(staff(lead, all), "/api/v1/calendar/dates/" + id)).andExpect(status().isOk());
        mvc.perform(getJson(outsider(colleague, all), "/api/v1/calendar/dates/" + id)).andExpect(status().isNotFound());
        assertThat(types(events(outsider(colleague, all), "from=" + day + "&to=" + day))).isEmpty();
        assertThat(types(events(staff(alternate, all), "from=" + day + "&to=" + day + "&userId=me"))).containsExactly("MEETING");
    }
}
