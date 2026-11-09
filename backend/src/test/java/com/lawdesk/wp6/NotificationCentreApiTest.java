package com.lawdesk.wp6;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.lawdesk.security.Permission;

/**
 * LD-056, requirements section 32 and decision D11: the notification centre. The eleven kinds of the requirements, read
 * and unread per user, filters, a link to the record, preferences per user for in-app kinds only, and a notification that
 * is never stored twice for the same record.
 */
class NotificationCentreApiTest extends Wp6TestSupport {

    private static final List<String> ELEVEN = List.of("CASE_NEW", "HEARING_UPCOMING", "EXPERT_SESSION_UPCOMING",
            "APPEAL_DEADLINE", "TASK_OVERDUE", "HEARING_NO_RESULT", "INFO_REQUEST_OVERDUE", "JUDGMENT_NEW",
            "DOCUMENT_MISSING", "DATE_CONFLICT", "RISK_HIGH");

    private UUID me;
    private UUID someoneElse;
    private UUID caseId;
    private UUID otherCase;
    private RequestPostProcessor mine;

    @BeforeEach
    void setUp() {
        me = user("Me");
        someoneElse = user("Someone else");
        caseId = legalCase(me, null);
        otherCase = legalCase(me, null);
        mine = staff(me, Permission.CASE_VIEW);
    }

    private UUID notify(UUID user, String type, UUID forCase, String ref) {
        return jdbc.queryForObject("""
                insert into notification (user_id, type, case_id, record_ref, params, dedupe_key)
                values (?, ?, ?, ?, '{"caseReferenceNo":"X-1"}'::jsonb, ?) returning id""", UUID.class, user, type, forCase,
                ref, type + ":" + ref + ":" + user);
    }

    // ---- the eleven kinds -------------------------------------------------------------------------------------------

    @Test
    void theCatalogueHoldsTheElevenKindsOfTheRequirementsInTheirOrderAndTheSystemNotices() throws Exception {
        mvc.perform(getJson(mine, "/api/v1/notifications/types")).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.core==true)].code").value(Matchers.contains(ELEVEN.toArray())))
                .andExpect(jsonPath("$[?(@.core==false)].code").value(Matchers.containsInAnyOrder("TASK_ASSIGNED",
                        "TASK_DUE_SOON", "INFO_REQUEST_ESCALATED", "HEARING_NO_NEXT_DATE")))
                .andExpect(jsonPath("$.length()").value(15));
    }

    // ---- reading ----------------------------------------------------------------------------------------------------

    @Test
    void theListFiltersByKindCaseAndReadState() throws Exception {
        UUID a = notify(me, "TASK_OVERDUE", caseId, "TASK:a");
        notify(me, "HEARING_UPCOMING", caseId, "HEARING:b");
        notify(me, "HEARING_UPCOMING", otherCase, "HEARING:c");
        notify(me, "DATE_CONFLICT", null, "CALENDAR:2026-12-01");
        notify(someoneElse, "TASK_OVERDUE", caseId, "TASK:z");
        mvc.perform(postJson(mine, "/api/v1/notifications/" + a + "/read", "{}")).andExpect(status().isNoContent());

        mvc.perform(getJson(mine, "/api/v1/notifications?type=HEARING_UPCOMING")).andExpect(jsonPath("$.totalItems").value(2));
        mvc.perform(getJson(mine, "/api/v1/notifications?type=HEARING_UPCOMING&type=TASK_OVERDUE"))
                .andExpect(jsonPath("$.totalItems").value(3));
        mvc.perform(getJson(mine, "/api/v1/notifications?caseId=" + caseId)).andExpect(jsonPath("$.totalItems").value(2));
        mvc.perform(getJson(mine, "/api/v1/notifications?read=true")).andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(a.toString()));
        mvc.perform(getJson(mine, "/api/v1/notifications?read=false")).andExpect(jsonPath("$.totalItems").value(3));
        mvc.perform(getJson(mine, "/api/v1/notifications?unreadOnly=true&type=TASK_OVERDUE"))
                .andExpect(jsonPath("$.totalItems").value(0));
        mvc.perform(getJson(mine, "/api/v1/notifications?type=NOPE")).andExpect(status().isBadRequest());
        mvc.perform(getJson(mine, "/api/v1/notifications?caseId=x")).andExpect(status().isBadRequest());
    }

    @Test
    void eachItemLinksToItsRecord() throws Exception {
        UUID hearingId = UUID.randomUUID();
        notify(me, "HEARING_UPCOMING", caseId, "HEARING:" + hearingId);
        notify(me, "DATE_CONFLICT", null, "CALENDAR:2026-12-01");
        mvc.perform(getJson(mine, "/api/v1/notifications?type=HEARING_UPCOMING")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].recordType").value("HEARING"))
                .andExpect(jsonPath("$.items[0].recordId").value(hearingId.toString()))
                .andExpect(jsonPath("$.items[0].caseId").value(caseId.toString()));
        mvc.perform(getJson(mine, "/api/v1/notifications?type=DATE_CONFLICT")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].recordType").value("CALENDAR"))
                .andExpect(jsonPath("$.items[0].recordId").value("2026-12-01"));
    }

    @Test
    void readAndUnreadAreKeptPerUser() throws Exception {
        UUID mineId = notify(me, "TASK_OVERDUE", caseId, "TASK:shared");
        UUID theirs = notify(someoneElse, "TASK_OVERDUE", caseId, "TASK:shared");
        mvc.perform(postJson(mine, "/api/v1/notifications/" + mineId + "/read", "{}")).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select read_at is null from notification where id = ?", Boolean.class, theirs)).isTrue();
        mvc.perform(postJson(mine, "/api/v1/notifications/" + mineId + "/unread", "{}")).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select read_at is null from notification where id = ?", Boolean.class, mineId)).isTrue();
        mvc.perform(getJson(mine, "/api/v1/notifications/unread-count")).andExpect(jsonPath("$.unread").value(1));
        mvc.perform(postJson(mine, "/api/v1/notifications/" + theirs + "/unread", "{}")).andExpect(status().isNotFound());
        mvc.perform(MockMvcRequestBuilders.post("/api/v1/notifications/" + mineId + "/unread").contentType("application/json")
                .content("{}")).andExpect(status().isUnauthorized());
    }

    // ---- preferences ------------------------------------------------------------------------------------------------

    @Test
    void everyKindIsOnUntilTheUserSwitchesItOff() throws Exception {
        mvc.perform(getJson(mine, "/api/v1/notifications/preferences")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(15))
                .andExpect(jsonPath("$[?(@.enabled==false)]").value(Matchers.hasSize(0)));
        mvc.perform(putJson(mine, "/api/v1/notifications/preferences/TASK_OVERDUE", "{\"enabled\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.type").value("TASK_OVERDUE"))
                .andExpect(jsonPath("$.enabled").value(false));
        mvc.perform(getJson(mine, "/api/v1/notifications/preferences"))
                .andExpect(jsonPath("$[?(@.enabled==false)].type").value(Matchers.contains("TASK_OVERDUE")));
        mvc.perform(putJson(mine, "/api/v1/notifications/preferences/TASK_OVERDUE", "{\"enabled\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(true));
        mvc.perform(getJson(mine, "/api/v1/notifications/preferences"))
                .andExpect(jsonPath("$[?(@.enabled==false)]").value(Matchers.hasSize(0)));
        assertThat(count("select count(*) from notification_preference where user_id = ?", me)).isEqualTo(1);
    }

    @Test
    void preferencesArePersonalAndOnlyForKnownKinds() throws Exception {
        mvc.perform(putJson(mine, "/api/v1/notifications/preferences/TASK_OVERDUE", "{\"enabled\":false}")).andExpect(status().isOk());
        RequestPostProcessor other = staff(someoneElse, Permission.CASE_VIEW);
        mvc.perform(getJson(other, "/api/v1/notifications/preferences"))
                .andExpect(jsonPath("$[?(@.enabled==false)]").value(Matchers.hasSize(0)));
        mvc.perform(putJson(mine, "/api/v1/notifications/preferences/EMAIL_DIGEST", "{\"enabled\":false}")).andExpect(status().isNotFound());
        mvc.perform(putJson(mine, "/api/v1/notifications/preferences/task_overdue", "{\"enabled\":false}")).andExpect(status().isNotFound());
        mvc.perform(putJson(mine, "/api/v1/notifications/preferences/TASK_OVERDUE", "{}")).andExpect(status().isBadRequest());
        mvc.perform(MockMvcRequestBuilders.put("/api/v1/notifications/preferences/TASK_OVERDUE").contentType("application/json")
                .content("{\"enabled\":false}")).andExpect(status().isUnauthorized());
        mvc.perform(putJson(staff(me), "/api/v1/notifications/preferences/TASK_OVERDUE", "{\"enabled\":false}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void aSwitchedOffKindIsNotStoredForThatUserOnlyAndNothingLeavesTheSystem() throws Exception {
        RequestPostProcessor lead = manager(me);
        mvc.perform(putJson(staff(someoneElse, Permission.CASE_VIEW), "/api/v1/notifications/preferences/TASK_ASSIGNED",
                "{\"enabled\":false}")).andExpect(status().isOk());
        UUID third = user("Third");
        createTask(lead, "For someone else", caseId, someoneElse, today().plusDays(9));
        createTask(lead, "For the third", caseId, third, today().plusDays(9));
        assertThat(count("select count(*) from notification where type = 'TASK_ASSIGNED' and user_id = ?", someoneElse)).isZero();
        assertThat(count("select count(*) from notification where type = 'TASK_ASSIGNED' and user_id = ?", third)).isEqualTo(1);
        // Switching it back on does not invent the missed one; the next assignment is delivered.
        mvc.perform(putJson(staff(someoneElse, Permission.CASE_VIEW), "/api/v1/notifications/preferences/TASK_ASSIGNED",
                "{\"enabled\":true}")).andExpect(status().isOk());
        assertThat(count("select count(*) from notification where user_id = ?", someoneElse)).isZero();
        createTask(lead, "Again", caseId, someoneElse, today().plusDays(9));
        assertThat(count("select count(*) from notification where type = 'TASK_ASSIGNED' and user_id = ?", someoneElse)).isEqualTo(1);
        // In-app only: the table has no channel and nothing but the user's own list reads it.
        assertThat(count("select count(*) from information_schema.columns where table_name = 'notification' and column_name in ('channel', 'email', 'sms')")).isZero();
    }

    // ---- assignment notice ------------------------------------------------------------------------------------------

    @Test
    void assigningAndReassigningATaskTellsTheNewAssigneeOnceAndNotTheOneWhoDidIt() throws Exception {
        RequestPostProcessor lead = manager(me);
        UUID worker = user("Worker");
        UUID id = createTask(lead, "Collect", caseId, worker, today().plusDays(9));
        createTask(lead, "Mine", caseId, me, today().plusDays(9));
        mvc.perform(getJson(outsider(worker, Permission.CASE_VIEW), "/api/v1/notifications?type=TASK_ASSIGNED"))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].recordType").value("TASK"))
                .andExpect(jsonPath("$.items[0].recordId").value(id.toString()))
                .andExpect(jsonPath("$.items[0].params.taskTitle").value("Collect"));
        assertThat(count("select count(*) from notification where user_id = ?", me)).as("self-assignment tells nobody").isZero();

        UUID next = user("Next");
        mvc.perform(postJson(lead, "/api/v1/tasks/" + id + "/reassign", "{\"assigneeId\":\"" + next + "\",\"version\":0}"))
                .andExpect(status().isOk());
        assertThat(count("select count(*) from notification where type = 'TASK_ASSIGNED' and user_id = ?", next)).isEqualTo(1);
        // The previous assignee lost sight of the case: nothing of it is listed for them any more.
        mvc.perform(getJson(outsider(worker, Permission.CASE_VIEW), "/api/v1/notifications"))
                .andExpect(jsonPath("$.totalItems").value(0));
    }
}
