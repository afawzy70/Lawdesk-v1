package com.lawdesk.tasks;

import static com.lawdesk.security.TestActors.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;
import com.lawdesk.security.Permission;

/**
 * OWNER DECISION 2026-10-25 (QAD-031): a task may be completed directly from OPEN, and every status change of a task is
 * recorded in the security audit log as {@code TASK_STATUS_CHANGED} (outcome SUCCESS, the acting user, the task id as
 * target and a detail such as {@code OPEN->DONE}); refused or failed changes record nothing, and no free text (title,
 * description, names) ever enters the audit event.
 */
class TaskStatusAuditTest extends TaskTestSupport {

    private static final String SECRET = "Settlement strategy for the Nile client";

    private List<Map<String, Object>> auditOf(UUID task) {
        return jdbc.queryForList("""
                select action, outcome, actor_id, actor_username, target_type, target_id, reason
                from audit_event where target_id = ? and action = 'TASK_STATUS_CHANGED' order by id""",
                task.toString());
    }

    private UUID secretTask(UUID caseId) throws Exception {
        UUID assignee = user("Mona Saleh", true);
        MvcResult created = mvc.perform(postTask("""
                        {"title":"%s","description":"%s","caseId":%s,"assigneeId":"%s"}"""
                        .formatted(SECRET, SECRET + " in detail", quote(caseId), assignee)))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.id"));
    }

    @Test
    void everyStatusChangeIsAuditedWithItsTransition() throws Exception {
        UUID id = secretTask(createCase("Audited"));
        assertThat(auditOf(id)).as("creating a task is not a status change").isEmpty();

        mvc.perform(moveTask(id, "DONE", 0)).andExpect(status().isOk());
        mvc.perform(moveTask(id, "OPEN", 1)).andExpect(status().isOk());
        mvc.perform(moveTask(id, "IN_PROGRESS", 2)).andExpect(status().isOk());
        mvc.perform(moveTask(id, "CANCELLED", 3)).andExpect(status().isOk());

        List<Map<String, Object>> events = auditOf(id);
        assertThat(events).extracting(e -> e.get("reason")).containsExactly("OPEN->DONE", "DONE->OPEN",
                "OPEN->IN_PROGRESS", "IN_PROGRESS->CANCELLED");
        assertThat(events).allSatisfy(event -> {
            assertThat(event.get("outcome")).isEqualTo("SUCCESS");
            assertThat(event.get("actor_id")).isEqualTo(CLERK);
            assertThat(event.get("actor_username")).isEqualTo("clerk");
            assertThat(event.get("target_type")).isEqualTo("TASK");
            assertThat(event.get("target_id")).isEqualTo(id.toString());
        });
    }

    @Test
    void anOfficeTaskWithoutACaseIsAuditedToo() throws Exception {
        UUID id = create("Renew the subscription", null, user("Karim", true), null);
        mvc.perform(moveTask(id, "DONE", 0)).andExpect(status().isOk());
        assertThat(auditOf(id)).extracting(e -> e.get("reason")).containsExactly("OPEN->DONE");
    }

    @Test
    void refusedOrFailedChangesRecordNothing() throws Exception {
        UUID caseId = createCase("Not audited");
        UUID id = secretTask(caseId);
        mvc.perform(moveTask(id, "DONE", 0)).andExpect(status().isOk());

        // 422: a done task cannot start; 409: stale version; 404: unknown task; 403: no permission; 400: no status.
        mvc.perform(moveTask(id, "IN_PROGRESS", 1)).andExpect(status().isUnprocessableContent());
        mvc.perform(moveTask(id, "OPEN", 0)).andExpect(status().isConflict());
        mvc.perform(moveTask(UUID.randomUUID(), "DONE", 0)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/tasks/" + id + "/status").with(as(Permission.TASK_VIEW)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"OPEN\",\"version\":1}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/tasks/" + id + "/status").with(as(Permission.TASK_MANAGE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        // An edit is not a status change.
        mvc.perform(putTask(id, edit(SECRET, user("Other", true), null, "LOW", 1))).andExpect(status().isOk());

        assertThat(auditOf(id)).extracting(e -> e.get("reason")).containsExactly("OPEN->DONE");
    }

    @Test
    void aChangeThatRollsBackRecordsNothing() throws Exception {
        UUID caseId = createCase("Rolled back");
        UUID id = secretTask(caseId);
        doThrow(new IllegalStateException("timeline unavailable")).when(caseLookup)
                .recordEvent(eq(caseId), anyString(), anyString());
        mvc.perform(moveTask(id, "DONE", 0)).andExpect(status().isInternalServerError());
        assertThat(statusOf(id)).isEqualTo("OPEN");
        assertThat(auditOf(id)).isEmpty();
    }

    @Test
    void anArchivedCaseRecordsNothing() throws Exception {
        UUID caseId = createCase("Archived audit");
        UUID id = secretTask(caseId);
        archive(caseId);
        mvc.perform(moveTask(id, "DONE", 0)).andExpect(status().isUnprocessableContent());
        assertThat(auditOf(id)).isEmpty();
    }

    @Test
    void noFreeTextOrNameEverReachesTheAuditEvent() throws Exception {
        UUID id = secretTask(createCase("No text"));
        mvc.perform(moveTask(id, "DONE", 0)).andExpect(status().isOk());

        Map<String, Object> event = auditOf(id).getFirst();
        String everything = String.join(" ", event.values().stream().map(String::valueOf).toList());
        assertThat(everything).doesNotContainIgnoringCase("settlement").doesNotContainIgnoringCase("nile")
                .doesNotContainIgnoringCase("Mona").doesNotContainIgnoringCase("detail");
        assertThat((String) event.get("reason")).matches("[A-Z_]+->[A-Z_]+");
    }
}
