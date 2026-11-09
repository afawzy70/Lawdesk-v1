package com.lawdesk.deadlines;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * LD-054, requirements sections 10 and 32, decisions D6 and D11: the in-app reminders of the deadlines. The lead times are
 * not in the document, so they have no default: until an authorised user configures them no approaching reminder exists.
 */
class DeadlineReminderApiTest extends DeadlineTestSupport {

    @Autowired
    private DeadlineReminderRun run;

    private UUID member;
    private UUID owner;
    private UUID supervisor;
    private UUID caseId;
    private RequestPostProcessor specialist;
    private RequestPostProcessor competent;

    @BeforeEach
    void aCaseWithItsMembers() {
        member = user("Mona Specialist");
        owner = user("Omar Owner");
        supervisor = user("Hala Supervisor");
        caseId = legalCase(member, null);
        specialist = staff(member, SPECIALIST);
        competent = staff(supervisor, COMPETENT);
    }

    private UUID deadline(String action, int daysFromToday, boolean approved) throws Exception {
        UUID id = idOf(mvc.perform(postJson(specialist, "/api/v1/cases/" + caseId + "/deadlines",
                json("sourceType", "TEXT", "sourceText", "Notice", "startsOn", today().minusDays(90).toString(), "dueOn",
                        today().plusDays(daysFromToday).toString(), "action", action, "ownerId", owner)))
                .andExpect(status().isCreated()).andReturn());
        if (approved) {
            mvc.perform(postJson(competent, "/api/v1/deadlines/" + id + "/approve", json("version", 0)))
                    .andExpect(status().isOk());
        }
        return id;
    }

    private void leadTimes(Object... days) throws Exception {
        mvc.perform(putJson(competent, "/api/v1/deadline-settings/reminders", json("leadDays", List.of(days))))
                .andExpect(status().isOk());
    }

    private int notifications(String type) {
        return count("select count(*) from notification where type = ?", type);
    }

    @Test
    void withoutConfiguredLeadTimesNoApproachingReminderIsRaised() throws Exception {
        deadline("Due tomorrow", 1, true);
        deadline("Due today", 0, true);
        DeadlineRequests.ReminderRunResult result = run.run();
        assertThat(result.leadTimesConfigured()).isFalse();
        assertThat(result.approaching()).isZero();
        assertThat(notifications("DEADLINE_APPROACHING")).isZero();
        mvc.perform(postJson(competent, "/api/v1/deadlines/reminders/run", "{}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.leadTimesConfigured").value(false)).andExpect(jsonPath("$.approaching").value(0));
    }

    @Test
    void leadTimesAreSetByACompetentMemberWithoutAnyDefaultAndKeepTheirHistoryInTheAudit() throws Exception {
        mvc.perform(getJson(specialist, "/api/v1/deadline-settings/reminders")).andExpect(jsonPath("$.configured").value(false));
        mvc.perform(putJson(specialist, "/api/v1/deadline-settings/reminders", json("leadDays", List.of(3))))
                .andExpect(status().isForbidden());
        mvc.perform(putJson(competent, "/api/v1/deadline-settings/reminders", json("leadDays", List.of(-1))))
                .andExpect(status().isBadRequest());
        mvc.perform(putJson(competent, "/api/v1/deadline-settings/reminders", json("leadDays", List.of(366))))
                .andExpect(status().isBadRequest());
        mvc.perform(putJson(competent, "/api/v1/deadline-settings/reminders", "{}")).andExpect(status().isBadRequest());
        mvc.perform(putJson(competent, "/api/v1/deadline-settings/reminders", json("leadDays", List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11))))
                .andExpect(status().isBadRequest());
        assertThat(count("select count(*) from deadline_reminder_setting")).isZero();
        mvc.perform(putJson(competent, "/api/v1/deadline-settings/reminders", json("leadDays", List.of(7, 1, 7, 0))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.leadDays", contains(0, 1, 7)));
        mvc.perform(getJson(specialist, "/api/v1/deadline-settings/reminders")).andExpect(jsonPath("$.leadDays", contains(0, 1, 7)));
        // Configured to remind on no day at all is a valid, explicit choice.
        mvc.perform(putJson(competent, "/api/v1/deadline-settings/reminders", json("leadDays", List.of())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.leadDays", org.hamcrest.Matchers.hasSize(0)));
        assertThat(count("select count(*) from audit_event where action = 'DEADLINE_REMINDERS_SET'")).isEqualTo(2);
    }

    @Test
    void approachingDeadlinesRemindTheOwnerAndTheSpecialistOncePerReachedLeadTime() throws Exception {
        leadTimes(1, 7);
        UUID soon = deadline("Due in five days", 5, true);
        deadline("Due in forty days", 40, true);
        deadline("Not approved yet", 2, false);
        UUID closed = deadline("Already done", 3, true);
        mvc.perform(postJson(specialist, "/api/v1/deadlines/" + closed + "/status", json("status", "COMPLETED", "version", 1)))
                .andExpect(status().isOk());

        DeadlineRequests.ReminderRunResult first = run.run();
        assertThat(first.leadTimesConfigured()).isTrue();
        assertThat(first.approaching()).isEqualTo(2);
        assertThat(count("select count(*) from notification where type = 'DEADLINE_APPROACHING' and user_id = ? and case_id = ?",
                owner, caseId)).isEqualTo(1);
        assertThat(count("select count(*) from notification where type = 'DEADLINE_APPROACHING' and user_id = ?", member)).isEqualTo(1);
        assertThat(count("select count(*) from notification where record_ref = ?", "DEADLINE:" + soon + ":7")).isEqualTo(2);
        // Repeating the run never duplicates.
        assertThat(run.run().approaching()).isZero();
        assertThat(notifications("DEADLINE_APPROACHING")).isEqualTo(2);
        // Nothing was completed, missed or closed by the run.
        assertThat(count("select count(*) from deadline where status = 'OPEN'")).isEqualTo(3);
        // Four days later the closer lead time is reached and reminds again once.
        jdbc.update("update deadline set due_on = ? where id = ?", today().plusDays(1), soon);
        assertThat(run.run().approaching()).isEqualTo(2);
        assertThat(count("select count(*) from notification where record_ref = ?", "DEADLINE:" + soon + ":1")).isEqualTo(2);
    }

    @Test
    void overdueDeadlinesAreReportedOnceWithoutBeingClosed() throws Exception {
        UUID late = deadline("Late", -2, true);
        deadline("Late but not approved", -2, false);
        DeadlineRequests.ReminderRunResult result = run.run();
        assertThat(result.leadTimesConfigured()).isFalse();
        assertThat(result.overdue()).isEqualTo(2);
        assertThat(notifications("DEADLINE_OVERDUE")).isEqualTo(2);
        assertThat(run.run().overdue()).isZero();
        assertThat(count("select count(*) from deadline where id = ? and status = 'OPEN'", late)).isEqualTo(1);
        // The owner is no member of the case and holds no scope: the notification about a case they cannot see is not shown.
        mvc.perform(getJson(outsider(owner, com.lawdesk.security.Permission.CASE_VIEW), "/api/v1/notifications"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(0));
        mvc.perform(getJson(staff(member, com.lawdesk.security.Permission.CASE_VIEW), "/api/v1/notifications"))
                .andExpect(jsonPath("$.items[0].type").value("DEADLINE_OVERDUE"))
                .andExpect(jsonPath("$.items[0].params.action").value("Late"))
                .andExpect(jsonPath("$.items[0].caseId").value(caseId.toString()));
    }

    @Test
    void theRunIsForCompetentMembersOnly() throws Exception {
        mvc.perform(postJson(specialist, "/api/v1/deadlines/reminders/run", "{}")).andExpect(status().isForbidden());
    }
}
