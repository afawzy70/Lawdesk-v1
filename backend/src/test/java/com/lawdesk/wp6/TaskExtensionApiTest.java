package com.lawdesk.wp6;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.lawdesk.security.Permission;

/**
 * LD-056, requirements section 14: the extended task (start date, instructions, seven statuses, delay reason), its
 * history and reassignment, and the task-only assignee of scenario S15 (sees the case read-only, edits only the task).
 */
class TaskExtensionApiTest extends Wp6TestSupport {

    private UUID lead;
    private UUID worker;
    private UUID other;
    private UUID caseId;
    private RequestPostProcessor leadActor;
    private RequestPostProcessor workerActor;

    @BeforeEach
    void setUp() {
        lead = user("Lead Member");
        worker = user("Worker");
        other = user("Other Worker");
        caseId = legalCase(lead, null);
        leadActor = manager(lead);
        // The assignee holds the task permissions but no scope and no membership: the case is open to them through the task only.
        workerActor = outsider(worker, Permission.CASE_VIEW, Permission.TASK_VIEW, Permission.TASK_MANAGE);
    }

    private String move(String status, long version) {
        return "{\"status\":\"%s\",\"version\":%d}".formatted(status, version);
    }

    private UUID assigned() throws Exception {
        return createTask(leadActor, "Collect the documents", caseId, worker, today().plusDays(5));
    }

    // ---- the extended record -----------------------------------------------------------------------------------

    @Test
    void createsATaskWithStartDateAndInstructionsAndItStartsNew() throws Exception {
        MvcResult created = mvc.perform(postJson(leadActor, "/api/v1/tasks", taskBody("File the reply", caseId, worker,
                        today().plusDays(1), today().plusDays(8), "  Attach the judgment copy and the receipt  ")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.startsOn").value(today().plusDays(1).toString()))
                .andExpect(jsonPath("$.instructions").value("Attach the judgment copy and the receipt"))
                .andExpect(jsonPath("$.delayReason").value(Matchers.nullValue()))
                .andReturn();
        UUID id = idOf(created);
        assertThat(count("select count(*) from task where id = ? and instructions is not null", id)).isEqualTo(1);
    }

    @Test
    void aStartDateAfterTheDueDateIsRefused() throws Exception {
        mvc.perform(postJson(leadActor, "/api/v1/tasks", taskBody("Backwards", caseId, worker, today().plusDays(9),
                        today().plusDays(8), null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("startsOn"));
        UUID id = assigned();
        mvc.perform(putJson(leadActor, "/api/v1/tasks/" + id, """
                        {"title":"x","assigneeId":"%s","startsOn":"%s","dueDate":"%s","version":0}"""
                        .formatted(worker, today().plusDays(9), today().plusDays(5))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("startsOn"));
    }

    @Test
    void instructionsAndStartDateCanBeEdited() throws Exception {
        UUID id = assigned();
        mvc.perform(putJson(leadActor, "/api/v1/tasks/" + id, """
                        {"title":"Collect the documents","assigneeId":"%s","startsOn":"%s","dueDate":"%s",
                         "instructions":"Ask the registry first","priority":"HIGH","version":0}"""
                        .formatted(worker, today(), today().plusDays(5))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startsOn").value(today().toString()))
                .andExpect(jsonPath("$.instructions").value("Ask the registry first"))
                .andExpect(jsonPath("$.priority").value("HIGH"));
    }

    // ---- the seven statuses ------------------------------------------------------------------------------------

    @Test
    void movesThroughTheSevenStatuses() throws Exception {
        UUID id = assigned();
        String[] path = {"IN_PROGRESS", "WAITING_DOCUMENTS", "WAITING_OTHER", "IN_PROGRESS", "COMPLETED"};
        long version = 0;
        for (String next : path) {
            mvc.perform(postJson(workerActor, "/api/v1/tasks/" + id + "/status", move(next, version)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(next));
            version++;
        }
        assertThat(statusOf(id)).isEqualTo("COMPLETED");
        assertThat(count("select count(*) from task where id = ? and completed_at is not null", id)).isEqualTo(1);
        mvc.perform(postJson(leadActor, "/api/v1/tasks/" + id + "/status", move("NEW", version)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.completedAt").value(Matchers.nullValue()));
        mvc.perform(postJson(leadActor, "/api/v1/tasks/" + id + "/status", move("CANCELLED", version + 1)))
                .andExpect(status().isOk());
        mvc.perform(postJson(leadActor, "/api/v1/tasks/" + id + "/status", move("COMPLETED", version + 2)))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.rule").value("TASK_STATUS_TRANSITION"));
    }

    @Test
    void lateIsSetByTheFollowUpRunNotByHand() throws Exception {
        UUID id = assigned();
        mvc.perform(postJson(leadActor, "/api/v1/tasks/" + id + "/status", move("LATE", 0)))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.rule").value("TASK_STATUS_TRANSITION"));
        assertThat(statusOf(id)).isEqualTo("NEW");
    }

    @Test
    void theLegacyStatusWordsAreStillAcceptedAndMapped() throws Exception {
        UUID id = assigned();
        mvc.perform(postJson(workerActor, "/api/v1/tasks/" + id + "/status", move("DONE", 0)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
        mvc.perform(postJson(leadActor, "/api/v1/tasks/" + id + "/status", move("OPEN", 1)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NEW"));
        mvc.perform(getJson(leadActor, "/api/v1/tasks?status=OPEN")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1));
        mvc.perform(getJson(leadActor, "/api/v1/tasks?status=NEW&status=WAITING_OTHER")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1));
        mvc.perform(getJson(leadActor, "/api/v1/tasks?status=DONE")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0));
        mvc.perform(getJson(leadActor, "/api/v1/tasks?status=NOPE")).andExpect(status().isBadRequest());
        mvc.perform(postJson(leadActor, "/api/v1/tasks/" + id + "/status", move("NOPE", 2)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aTaskThatIsWaitingStillOpensTheCaseToItsAssigneeAndACompletedOneDoesNot() throws Exception {
        UUID id = assigned();
        mvc.perform(getJson(workerActor, "/api/v1/cases/" + caseId)).andExpect(status().isOk());
        for (String waiting : new String[] {"WAITING_DOCUMENTS", "WAITING_OTHER"}) {
            long v = versionOf(id);
            mvc.perform(postJson(workerActor, "/api/v1/tasks/" + id + "/status", move(waiting, v))).andExpect(status().isOk());
            mvc.perform(getJson(workerActor, "/api/v1/cases/" + caseId)).andExpect(status().isOk());
        }
        mvc.perform(postJson(workerActor, "/api/v1/tasks/" + id + "/status", move("COMPLETED", versionOf(id))))
                .andExpect(status().isOk());
        mvc.perform(getJson(workerActor, "/api/v1/cases/" + caseId)).andExpect(status().isNotFound());
    }

    // ---- S15: the task-only assignee ---------------------------------------------------------------------------

    @Test
    void aTaskOnlyAssigneeSeesTheCaseReadOnlyAndEditsOnlyTheirTask() throws Exception {
        UUID mine = assigned();
        UUID theirs = createTask(leadActor, "Someone else's task", caseId, other, today().plusDays(3));
        mvc.perform(getJson(workerActor, "/api/v1/cases/" + caseId)).andExpect(status().isOk());
        mvc.perform(putJson(workerActor, "/api/v1/cases/" + caseId,
                        "{\"title\":\"Changed\",\"caseType\":\"CIVIL\",\"openedOn\":\"2026-01-10\",\"version\":0}"))
                .andExpect(status().isForbidden());
        // Their own task: status and delay reason, yes.
        mvc.perform(postJson(workerActor, "/api/v1/tasks/" + mine + "/status", move("IN_PROGRESS", 0))).andExpect(status().isOk());
        mvc.perform(postJson(workerActor, "/api/v1/tasks/" + mine + "/delay-reason",
                        "{\"reason\":\"Waiting for the registry\",\"version\":1}")).andExpect(status().isOk());
        // Not its fields, not its assignee, not a task on the case, not another task of the case.
        mvc.perform(putJson(workerActor, "/api/v1/tasks/" + mine, """
                        {"title":"Renamed","assigneeId":"%s","version":2}""".formatted(worker))).andExpect(status().isForbidden());
        mvc.perform(postJson(workerActor, "/api/v1/tasks/" + mine + "/reassign",
                        "{\"assigneeId\":\"%s\",\"version\":2}".formatted(other))).andExpect(status().isForbidden());
        mvc.perform(postJson(workerActor, "/api/v1/tasks", taskBody("New one", caseId, other, null, null, null)))
                .andExpect(status().isForbidden());
        mvc.perform(postJson(workerActor, "/api/v1/tasks/" + theirs + "/status", move("IN_PROGRESS", 0)))
                .andExpect(status().isForbidden());
        mvc.perform(postJson(workerActor, "/api/v1/tasks/" + theirs + "/delay-reason",
                        "{\"reason\":\"x\",\"version\":0}")).andExpect(status().isForbidden());
        assertThat(statusOf(theirs)).isEqualTo("NEW");
    }

    // ---- delay reason ------------------------------------------------------------------------------------------

    @Test
    void theAssigneeRecordsTheReasonForDelayAndItIsKeptInTheHistory() throws Exception {
        UUID id = assigned();
        mvc.perform(postJson(workerActor, "/api/v1/tasks/" + id + "/delay-reason",
                        "{\"reason\":\"  The registry is closed until Sunday \",\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.delayReason").value("The registry is closed until Sunday"))
                .andExpect(jsonPath("$.delayReasonAt").isString())
                .andExpect(jsonPath("$.version").value(1));
        mvc.perform(postJson(workerActor, "/api/v1/tasks/" + id + "/delay-reason", "{\"reason\":\"   \",\"version\":1}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("reason"));
        mvc.perform(postJson(workerActor, "/api/v1/tasks/" + id + "/delay-reason", "{\"reason\":\"again\",\"version\":0}"))
                .andExpect(status().isConflict());
        mvc.perform(getJson(workerActor, "/api/v1/tasks/" + id + "/history")).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.action=='DELAY_REASON')].newValue")
                        .value(Matchers.contains("The registry is closed until Sunday")));
    }

    // ---- reassignment ------------------------------------------------------------------------------------------

    @Test
    void reassigningMovesTheTaskAndTheSightOfTheCaseAndKeepsTheHistory() throws Exception {
        UUID id = assigned();
        mvc.perform(postJson(leadActor, "/api/v1/tasks/" + id + "/reassign",
                        "{\"assigneeId\":\"%s\",\"reason\":\"Workload\",\"version\":0}".formatted(other)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.assigneeId").value(other.toString()))
                .andExpect(jsonPath("$.assigneeName").value("Other Worker"));
        mvc.perform(getJson(workerActor, "/api/v1/cases/" + caseId)).andExpect(status().isNotFound());
        mvc.perform(getJson(outsider(other, Permission.CASE_VIEW, Permission.TASK_VIEW), "/api/v1/cases/" + caseId))
                .andExpect(status().isOk());
        mvc.perform(getJson(leadActor, "/api/v1/tasks/" + id + "/history")).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.action=='REASSIGNED')].oldValue").value(Matchers.contains(worker.toString())))
                .andExpect(jsonPath("$[?(@.action=='REASSIGNED')].newValue").value(Matchers.contains(other.toString())))
                .andExpect(jsonPath("$[?(@.action=='REASSIGNED')].reason").value(Matchers.contains("Workload")))
                .andExpect(jsonPath("$[?(@.action=='REASSIGNED')].actorId").value(Matchers.contains(lead.toString())));
        // The previous assignee can no longer touch it.
        mvc.perform(postJson(workerActor, "/api/v1/tasks/" + id + "/status", move("IN_PROGRESS", 1)))
                .andExpect(status().isNotFound());
    }

    @Test
    void reassignmentIsRefusedForTheSamePersonADisabledUserAndAStaleVersion() throws Exception {
        UUID id = assigned();
        mvc.perform(postJson(leadActor, "/api/v1/tasks/" + id + "/reassign",
                        "{\"assigneeId\":\"%s\",\"version\":0}".formatted(worker)))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.rule").value("ASSIGNEE_UNCHANGED"));
        jdbc.update("update app_user set enabled = false where id = ?", other);
        mvc.perform(postJson(leadActor, "/api/v1/tasks/" + id + "/reassign",
                        "{\"assigneeId\":\"%s\",\"version\":0}".formatted(other)))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.rule").value("ASSIGNEE_NOT_ASSIGNABLE"));
        jdbc.update("update app_user set enabled = true where id = ?", other);
        mvc.perform(postJson(leadActor, "/api/v1/tasks/" + id + "/reassign",
                        "{\"assigneeId\":\"%s\",\"version\":7}".formatted(other))).andExpect(status().isConflict());
        mvc.perform(postJson(leadActor, "/api/v1/tasks/" + id + "/reassign", "{\"version\":0}"))
                .andExpect(status().isBadRequest());
        assertThat(count("select count(*) from task where id = ? and assignee_id = ?", id, worker)).isEqualTo(1);
    }

    @Test
    void reassigningNeedsThePermissionAndSightOfTheTask() throws Exception {
        UUID id = assigned();
        String json = "{\"assigneeId\":\"%s\",\"version\":0}".formatted(other);
        // Sees every case but is not a member of this one: may read the task, may not move it.
        mvc.perform(postJson(viewer(user("Stranger"), Permission.TASK_VIEW, Permission.TASK_MANAGE), "/api/v1/tasks/" + id + "/reassign", json))
                .andExpect(status().isForbidden());
        mvc.perform(postJson(outsider(user("Blind"), Permission.TASK_MANAGE), "/api/v1/tasks/" + id + "/reassign", json))
                .andExpect(status().isNotFound());
        mvc.perform(postJson(staff(lead, Permission.TASK_VIEW), "/api/v1/tasks/" + id + "/reassign", json))
                .andExpect(status().isForbidden());
        mvc.perform(postJson(leadActor, "/api/v1/tasks/" + id + "/reassign", json)).andExpect(status().isOk());
    }

    // ---- history -----------------------------------------------------------------------------------------------

    @Test
    void theHistoryRecordsWhoChangedWhatInOrder() throws Exception {
        UUID id = assigned();
        mvc.perform(putJson(leadActor, "/api/v1/tasks/" + id, """
                        {"title":"Collect all the documents","assigneeId":"%s","dueDate":"%s","priority":"URGENT","version":0}"""
                        .formatted(worker, today().plusDays(9)))).andExpect(status().isOk());
        mvc.perform(postJson(workerActor, "/api/v1/tasks/" + id + "/status", move("IN_PROGRESS", 1))).andExpect(status().isOk());

        MvcResult history = mvc.perform(getJson(leadActor, "/api/v1/tasks/" + id + "/history")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("CREATED"))
                .andExpect(jsonPath("$[0].actorId").value(lead.toString()))
                .andExpect(jsonPath("$[0].actorName").value("Lead Member"))
                .andReturn();
        List<String> fields = read(history, "$[?(@.action=='UPDATED')].field");
        assertThat(fields).containsExactlyInAnyOrder("title", "due_date", "priority");
        List<String> statuses = read(history, "$[?(@.action=='STATUS')].newValue");
        assertThat(statuses).containsExactly("IN_PROGRESS");
        assertThat((List<String>) read(history, "$[?(@.action=='STATUS')].oldValue")).containsExactly("NEW");
        assertThat((List<String>) read(history, "$[?(@.action=='STATUS')].actorId")).containsExactly(worker.toString());
        assertThat((List<String>) read(history, "$[?(@.field=='priority')].oldValue")).containsExactly("NORMAL");
    }

    @Test
    void theHistoryIsOnlyForThoseWhoSeeTheTask() throws Exception {
        UUID id = assigned();
        mvc.perform(getJson(workerActor, "/api/v1/tasks/" + id + "/history")).andExpect(status().isOk());
        mvc.perform(getJson(outsider(user("Blind"), Permission.TASK_VIEW), "/api/v1/tasks/" + id + "/history"))
                .andExpect(status().isNotFound());
        mvc.perform(getJson(staff(lead), "/api/v1/tasks/" + id + "/history")).andExpect(status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/tasks/" + id + "/history"))
                .andExpect(status().isUnauthorized());
        mvc.perform(getJson(leadActor, "/api/v1/tasks/" + UUID.randomUUID() + "/history")).andExpect(status().isNotFound());
    }
}
