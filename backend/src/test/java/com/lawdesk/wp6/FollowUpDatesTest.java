package com.lawdesk.wp6;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.lawdesk.calendar.CalendarTime;
import com.lawdesk.security.Permission;

/**
 * LD-056, scenario S14 and requirements sections 14, 15 and 32: the scheduled follow-up raises, idempotently and in the
 * system only, the notifications that depend on dates and on what happened: late tasks, tasks due soon, hearings and
 * expert sessions ahead, hearings without a result, appeal dates that exist (manual dates now, WP4 deadlines later), new
 * cases and judgments, requests with missing documents, and conflicts of dates.
 */
class FollowUpDatesTest extends Wp6TestSupport {

    private UUID lead;
    private UUID alternate;
    private UUID worker;
    private UUID caseId;
    private UUID proceeding;
    private RequestPostProcessor leadActor;
    private RequestPostProcessor runner;

    @BeforeEach
    void setUp() {
        lead = user("Lead Member");
        alternate = user("Alternate Member");
        worker = user("Worker");
        caseId = legalCase(lead, alternate);
        proceeding = proceeding(caseId, "FIRST_INSTANCE");
        leadActor = manager(lead);
        runner = staff(user("Runner"), Permission.INFO_REQUEST_ESCALATE, Permission.CASE_VIEW);
    }

    private ResultActions run() throws Exception {
        return mvc.perform(postJson(runner, "/api/v1/follow-up/run", "{}")).andExpect(status().isOk());
    }

    private int notifications(UUID user, String type) {
        return count("select count(*) from notification where user_id = ? and type = ?", user, type);
    }

    private static LocalDate kuwaitToday() {
        return CalendarTime.today();
    }

    // ---- tasks (section 14) -----------------------------------------------------------------------------------

    @Test
    void anOpenTaskPastItsDueDateBecomesLateOnceAndItsPeopleAreTold() throws Exception {
        UUID waiting = createTask(leadActor, "Waiting task", caseId, worker, today().plusDays(5));
        UUID fresh = createTask(leadActor, "New task", caseId, worker, today().plusDays(5));
        UUID finished = createTask(leadActor, "Finished task", caseId, worker, today().plusDays(5));
        UUID cancelled = createTask(leadActor, "Cancelled task", caseId, worker, today().plusDays(5));
        mvc.perform(postJson(manager(worker), "/api/v1/tasks/" + waiting + "/status", "{\"status\":\"WAITING_OTHER\",\"version\":0}"))
                .andExpect(status().isOk());
        mvc.perform(postJson(manager(worker), "/api/v1/tasks/" + finished + "/status", "{\"status\":\"COMPLETED\",\"version\":0}"))
                .andExpect(status().isOk());
        mvc.perform(postJson(leadActor, "/api/v1/tasks/" + cancelled + "/status", "{\"status\":\"CANCELLED\",\"version\":0}"))
                .andExpect(status().isOk());
        jdbc.update("update task set due_date = current_date - 3 where case_id = ?", caseId);

        run().andExpect(jsonPath("$.lateTasks").value(2));
        assertThat(statusOf(waiting)).isEqualTo("LATE");
        assertThat(statusOf(fresh)).isEqualTo("LATE");
        assertThat(statusOf(finished)).isEqualTo("COMPLETED");
        assertThat(statusOf(cancelled)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("select status_before_late from task where id = ?", String.class, waiting))
                .isEqualTo("WAITING_OTHER");
        assertThat(count("select count(*) from task_history where task_id = ? and action = 'SYSTEM_LATE' and actor_id is null"
                + " and old_value = 'WAITING_OTHER' and new_value = 'LATE'", waiting)).isEqualTo(1);
        // The assignee and the person who gave the task are told, once per task; nobody else.
        assertThat(notifications(worker, "TASK_OVERDUE")).isEqualTo(2);
        assertThat(notifications(lead, "TASK_OVERDUE")).isEqualTo(2);
        assertThat(notifications(alternate, "TASK_OVERDUE")).isZero();

        run().andExpect(jsonPath("$.lateTasks").value(0));
        assertThat(count("select count(*) from notification where type = 'TASK_OVERDUE'")).isEqualTo(4);
        assertThat(count("select count(*) from task_history where action = 'SYSTEM_LATE'")).isEqualTo(2);
        mvc.perform(getJson(leadActor, "/api/v1/tasks/" + fresh)).andExpect(jsonPath("$.status").value("LATE"))
                .andExpect(jsonPath("$.overdue").value(true));
    }

    @Test
    void aLateTaskIsMovedOnByItsAssigneeOrRestoredWhenItsDueDateMoves() throws Exception {
        UUID restored = createTask(leadActor, "Gets more time", caseId, worker, today().plusDays(5));
        UUID worked = createTask(leadActor, "Gets done", caseId, worker, today().plusDays(5));
        mvc.perform(postJson(manager(worker), "/api/v1/tasks/" + restored + "/status", "{\"status\":\"WAITING_DOCUMENTS\",\"version\":0}"))
                .andExpect(status().isOk());
        jdbc.update("update task set due_date = current_date - 3 where case_id = ?", caseId);
        run();
        assertThat(statusOf(restored)).isEqualTo("LATE");

        mvc.perform(postJson(manager(worker), "/api/v1/tasks/" + worked + "/status", "{\"status\":\"COMPLETED\",\"version\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
        // A new due date puts the task back to what it was; being late again later is news again.
        mvc.perform(putJson(leadActor, "/api/v1/tasks/" + restored, """
                        {"title":"Gets more time","assigneeId":"%s","dueDate":"%s","version":%d}"""
                        .formatted(worker, today().plusDays(4), versionOf(restored))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("WAITING_DOCUMENTS"));
        assertThat(jdbc.queryForObject("select status_before_late from task where id = ?", String.class, restored)).isNull();
        assertThat(count("select count(*) from task_history where task_id = ? and action = 'SYSTEM_RESTORED'", restored)).isEqualTo(1);
        run().andExpect(jsonPath("$.lateTasks").value(0));
        jdbc.update("update task set due_date = current_date - 1 where id = ?", restored);
        run().andExpect(jsonPath("$.lateTasks").value(1));
        assertThat(notifications(worker, "TASK_OVERDUE")).isEqualTo(3);
    }

    @Test
    void aTaskDueWithinThreeDaysIsAnnouncedToItsAssigneeOnce() throws Exception {
        createTask(leadActor, "Soon", caseId, worker, today().plusDays(2));
        createTask(leadActor, "Later", caseId, worker, today().plusDays(10));
        run();
        run();
        assertThat(notifications(worker, "TASK_DUE_SOON")).isEqualTo(1);
        assertThat(notifications(lead, "TASK_DUE_SOON")).isZero();
        assertThat(count("select count(*) from task where status = 'LATE'")).isZero();
    }

    // ---- hearings and expert sessions (section 15) --------------------------------------------------------------

    @Test
    void anUpcomingHearingTellsItsResponsibleMemberTheSpecialistAndTheAlternate() throws Exception {
        UUID responsible = user("Responsible");
        UUID soon = hearing(caseId, proceeding, kuwaitToday().plusDays(2), LocalTime.of(9, 30), "COURT", responsible);
        hearing(caseId, proceeding(caseId, "APPEAL"), kuwaitToday().plusDays(12), LocalTime.of(9, 30), "COURT", responsible);
        hearing(caseId, proceeding(caseId, "CASSATION"), kuwaitToday().minusDays(1), LocalTime.of(9, 30), "COURT", responsible);
        UUID cancelled = hearing(caseId, proceeding(caseId, "ENFORCEMENT"), kuwaitToday().plusDays(1), null, "COURT", responsible);
        jdbc.update("update hearing set status = 'CANCELLED', result_recorded_at = now(), result_recorded_by = gen_random_uuid() where id = ?", cancelled);

        run();
        run();
        for (UUID person : new UUID[] {responsible, lead, alternate}) {
            assertThat(notifications(person, "HEARING_UPCOMING")).as("person %s", person).isEqualTo(1);
        }
        assertThat(count("select count(*) from notification where type = 'HEARING_UPCOMING'")).isEqualTo(3);
        assertThat(jdbc.queryForObject("select record_ref from notification where type = 'HEARING_UPCOMING' limit 1", String.class))
                .isEqualTo("HEARING:" + soon);
        // The same hearing on another day is news again.
        jdbc.update("update hearing set hearing_date = ? where id = ?", kuwaitToday().plusDays(3), soon);
        run();
        assertThat(notifications(lead, "HEARING_UPCOMING")).isEqualTo(2);
    }

    @Test
    void anExpertSessionAheadIsAnnounced() throws Exception {
        UUID expert = jdbc.queryForObject("""
                insert into expert (name, specialty, created_by, updated_by) values ('Eng. Expert', 'Engineering',
                gen_random_uuid(), gen_random_uuid()) returning id""", UUID.class);
        UUID referral = jdbc.queryForObject("""
                insert into expert_referral (case_id, proceeding_id, expert_id, referral_date, created_by, updated_by)
                values (?, ?, ?, '2026-01-12', gen_random_uuid(), gen_random_uuid()) returning id""", UUID.class,
                caseId, proceeding, expert);
        UUID session = jdbc.queryForObject("insert into expert_session (referral_id, session_date, session_time, created_by) values (?, ?, '11:00', gen_random_uuid()) returning id",
                UUID.class, referral, kuwaitToday().plusDays(1));
        run();
        run();
        assertThat(notifications(lead, "EXPERT_SESSION_UPCOMING")).isEqualTo(1);
        assertThat(notifications(alternate, "EXPERT_SESSION_UPCOMING")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select record_ref from notification where type = 'EXPERT_SESSION_UPCOMING' limit 1", String.class))
                .isEqualTo("EXPERT_SESSION:" + session);
    }

    @Test
    void aHearingWhoseDayPassedWithoutAResultIsRaisedOnce() throws Exception {
        UUID responsible = user("Responsible");
        UUID unrecorded = hearing(caseId, proceeding, kuwaitToday().minusDays(2), LocalTime.of(9, 0), "COURT", responsible);
        UUID recorded = hearing(caseId, proceeding(caseId, "APPEAL"), kuwaitToday().minusDays(2), LocalTime.of(9, 0), "COURT", responsible);
        jdbc.update("update hearing set status = 'HELD', result_recorded_at = now(), result_recorded_by = gen_random_uuid() where id = ?", recorded);
        hearing(caseId, proceeding(caseId, "CASSATION"), kuwaitToday(), LocalTime.of(23, 0), "COURT", responsible);

        run();
        run();
        assertThat(notifications(responsible, "HEARING_NO_RESULT")).isEqualTo(1);
        assertThat(notifications(lead, "HEARING_NO_RESULT")).isEqualTo(1);
        assertThat(count("select count(*) from notification where type = 'HEARING_NO_RESULT'")).isEqualTo(3);
        assertThat(jdbc.queryForObject("select record_ref from notification where type = 'HEARING_NO_RESULT' limit 1", String.class))
                .isEqualTo("HEARING:" + unrecorded);
    }

    // ---- S14: dates that exist ---------------------------------------------------------------------------------

    @Test
    void appealDateNotificationsComeOnlyFromDatesThatExist() throws Exception {
        // Nothing is entered: nothing is invented, whatever the case, its hearings or its judgments are.
        hearing(caseId, proceeding, kuwaitToday().minusDays(30), null, "COURT", lead);
        run();
        assertThat(count("select count(*) from notification where type = 'APPEAL_DEADLINE'")).isZero();

        // A manual date the lawyer entered for the appeal is a deadline that exists.
        mvc.perform(postJson(leadActor, "/api/v1/calendar/dates", """
                        {"kind":"APPEAL_DEADLINE","title":"Appeal date","caseId":"%s","date":"%s"}"""
                        .formatted(caseId, kuwaitToday().plusDays(2)))).andExpect(status().isCreated());
        mvc.perform(postJson(leadActor, "/api/v1/calendar/dates", """
                        {"kind":"APPEAL_DEADLINE","title":"Too far","caseId":"%s","date":"%s"}"""
                        .formatted(caseId, kuwaitToday().plusDays(40)))).andExpect(status().isCreated());
        mvc.perform(postJson(leadActor, "/api/v1/calendar/dates", """
                        {"kind":"MEMO_DUE","title":"Memo","caseId":"%s","date":"%s"}"""
                        .formatted(caseId, kuwaitToday().plusDays(2)))).andExpect(status().isCreated());
        UUID cancelled = idOf(mvc.perform(postJson(leadActor, "/api/v1/calendar/dates", """
                        {"kind":"APPEAL_DEADLINE","title":"Cancelled","caseId":"%s","date":"%s"}"""
                        .formatted(caseId, kuwaitToday().plusDays(1)))).andExpect(status().isCreated()).andReturn());
        mvc.perform(postJson(leadActor, "/api/v1/calendar/dates/" + cancelled + "/status", "{\"status\":\"CANCELLED\",\"version\":0}"))
                .andExpect(status().isOk());
        run();
        run();
        assertThat(count("select count(*) from notification where type = 'APPEAL_DEADLINE'")).isEqualTo(2);
        assertThat(notifications(lead, "APPEAL_DEADLINE")).isEqualTo(1);
        assertThat(notifications(alternate, "APPEAL_DEADLINE")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select record_ref from notification where type = 'APPEAL_DEADLINE' limit 1", String.class))
                .startsWith("CALENDAR_DATE:");
        assertThat(count("select count(*) from notification where type not in ('APPEAL_DEADLINE', 'HEARING_NO_RESULT')"
                + " and params::text like '%Memo%'")).isZero();
    }

    // ---- what happened ------------------------------------------------------------------------------------------

    @Test
    void aNewCaseTellsItsSpecialistAndAlternateExceptTheOneWhoRegisteredIt() throws Exception {
        UUID registrar = user("Registrar");
        UUID assigned = legalCase(lead, alternate);
        jdbc.update("update legal_case set created_by = ? where id = ?", registrar, assigned);
        UUID old = legalCase(lead, alternate);
        jdbc.update("update legal_case set created_by = ?, created_at = now() - interval '30 days' where id = ?", registrar, old);
        run();
        run();
        // The case of the set-up was registered by its specialist, so only the alternate hears of it.
        assertThat(notifications(lead, "CASE_NEW")).isEqualTo(1);
        assertThat(notifications(alternate, "CASE_NEW")).isEqualTo(2);
        assertThat(notifications(registrar, "CASE_NEW")).isZero();
        assertThat(count("select count(*) from notification where type = 'CASE_NEW' and record_ref = ?", "CASE:" + old)).isZero();
    }

    @Test
    void aNewJudgmentTellsTheMembersOfTheCaseOnce() throws Exception {
        jdbc.update("""
                insert into judgment (proceeding_id, judgment_date, outcome, final, created_by, updated_by)
                values (?, ?, 'IN_FAVOUR', false, gen_random_uuid(), gen_random_uuid())""", proceeding, today());
        run();
        run();
        assertThat(notifications(lead, "JUDGMENT_NEW")).isEqualTo(1);
        assertThat(notifications(alternate, "JUDGMENT_NEW")).isEqualTo(1);
    }

    @Test
    void aRequestWhoseReplyIsIncompleteRaisesMissingDocumentsAndANewIncompleteReplyRaisesItAgain() throws Exception {
        UUID request = jdbc.queryForObject("""
                insert into info_request (case_id, request_no, subject, status, due_on, responsible_member_id, created_by, updated_by)
                values (?, 'IR-2026-W6101', 'Missing file', 'INCOMPLETE', ?, ?, gen_random_uuid(), gen_random_uuid()) returning id""",
                UUID.class, caseId, today().plusDays(9), worker);
        run();
        run();
        assertThat(notifications(worker, "DOCUMENT_MISSING")).isEqualTo(1);
        assertThat(notifications(lead, "DOCUMENT_MISSING")).isEqualTo(1);
        jdbc.update("""
                insert into info_reply (request_id, case_id, received_on, completeness, created_by)
                values (?, ?, ?, 'INCOMPLETE', gen_random_uuid())""", request, caseId, today());
        run();
        assertThat(notifications(worker, "DOCUMENT_MISSING")).isEqualTo(2);
    }

    @Test
    void conflictingDatesOfOneMemberAreRaisedToThatMemberOnceWithoutNamingTheCases() throws Exception {
        UUID otherCase = legalCase(lead, alternate);
        LocalDate day = kuwaitToday().plusDays(7);
        hearing(caseId, proceeding, day, LocalTime.of(9, 0), "COURT", lead);
        hearing(otherCase, proceeding(otherCase, "FIRST_INSTANCE"), day, LocalTime.of(9, 0), "COURT", lead);
        run();
        run();
        assertThat(notifications(lead, "DATE_CONFLICT")).isEqualTo(1);
        assertThat(notifications(alternate, "DATE_CONFLICT")).isZero();
        assertThat(jdbc.queryForObject("select record_ref from notification where type = 'DATE_CONFLICT'", String.class))
                .isEqualTo("CALENDAR:" + day);
        assertThat(jdbc.queryForObject("select case_id from notification where type = 'DATE_CONFLICT'", UUID.class)).isNull();
        assertThat(jdbc.queryForObject("select params->>'date' from notification where type = 'DATE_CONFLICT'", String.class))
                .isEqualTo(day.toString());
        // A third entry in the same group changes the conflict: the member hears of it again.
        hearing(otherCase, proceeding(otherCase, "APPEAL"), day, LocalTime.of(9, 0), "COURT", lead);
        run();
        assertThat(notifications(lead, "DATE_CONFLICT")).isEqualTo(2);
    }

    // ---- preferences, access and the run itself -------------------------------------------------------------------

    @Test
    void aMemberWhoSwitchedAKindOffGetsNoneOfThemFromTheRun() throws Exception {
        hearing(caseId, proceeding, kuwaitToday().plusDays(1), LocalTime.of(9, 0), "COURT", lead);
        mvc.perform(putJson(staff(alternate, Permission.CASE_VIEW), "/api/v1/notifications/preferences/HEARING_UPCOMING",
                "{\"enabled\":false}")).andExpect(status().isOk());
        run();
        assertThat(notifications(lead, "HEARING_UPCOMING")).isEqualTo(1);
        assertThat(notifications(alternate, "HEARING_UPCOMING")).isZero();
    }

    @Test
    void whatTheRunRaisesIsShownOnlyToTheUsersWhoMaySeeTheCase() throws Exception {
        hearing(caseId, proceeding, kuwaitToday().plusDays(1), LocalTime.of(9, 0), "COURT", lead);
        run();
        mvc.perform(getJson(staff(lead, Permission.CASE_VIEW), "/api/v1/notifications?type=HEARING_UPCOMING"))
                .andExpect(jsonPath("$.totalItems").value(1));
        // The specialist is taken off the case: the notification stays theirs but the case is closed to them.
        jdbc.update("update legal_case set primary_member_id = ?, created_by = ? where id = ?", worker, worker, caseId);
        mvc.perform(getJson(outsider(lead, Permission.CASE_VIEW), "/api/v1/notifications?type=HEARING_UPCOMING"))
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void theRunNeedsThePermissionAndSendsNothingOutside() throws Exception {
        mvc.perform(postJson(staff(user("Nobody"), Permission.CASE_VIEW), "/api/v1/follow-up/run", "{}"))
                .andExpect(status().isForbidden());
        String result = body(run().andReturn());
        assertThat((Integer) com.jayway.jsonpath.JsonPath.read(result, "$.lateTasks")).isZero();
        assertThat((Integer) com.jayway.jsonpath.JsonPath.read(result, "$.overdueRequests")).isZero();
        // What it reports is what is stored, in the system, for the people of the case (here the new case's alternate).
        assertThat((Integer) com.jayway.jsonpath.JsonPath.read(result, "$.notifications"))
                .isEqualTo(count("select count(*) from notification"));
    }
}
