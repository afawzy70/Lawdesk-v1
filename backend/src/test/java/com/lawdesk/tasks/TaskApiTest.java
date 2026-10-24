package com.lawdesk.tasks;

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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;
import com.lawdesk.security.Permission;

/** LD-027 tasks and assignments (docs/delivery/PHASE-5-ACCEPTANCE.md): QA-0209…QA-0215. */
class TaskApiTest extends TaskTestSupport {

    // QA-0209 authorized happy path

    @Test
    void createsACaseTaskAndATimelineEvent() throws Exception {
        UUID caseId = createCase("Appeal deadline");
        UUID assignee = user("Mona Saleh", true);
        String due = today().plusDays(5).toString();

        MvcResult result = mvc.perform(postTask("""
                        {"title":"  File the appeal memorandum ","description":"  Attach the judgment copy ",
                         "caseId":"%s","assigneeId":"%s","dueDate":"%s","priority":"URGENT"}"""
                        .formatted(caseId, assignee, due)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.title").value("File the appeal memorandum"))
                .andExpect(jsonPath("$.description").value("Attach the judgment copy"))
                .andExpect(jsonPath("$.caseId").value(caseId.toString()))
                .andExpect(jsonPath("$.caseReferenceNo").isString())
                .andExpect(jsonPath("$.assigneeId").value(assignee.toString()))
                .andExpect(jsonPath("$.assigneeName").value("Mona Saleh"))
                .andExpect(jsonPath("$.dueDate").value(due))
                .andExpect(jsonPath("$.priority").value("URGENT"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.completedAt").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.overdue").value(false))
                .andExpect(jsonPath("$.dueSoon").value(false))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.createdBy").value(CLERK.toString()))
                .andReturn();
        String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        assertThat(result.getResponse().getHeader("Location")).endsWith("/api/v1/tasks/" + id);
        verify(caseLookup).recordEvent(eq(caseId), eq("TASK_CREATED"), contains("File the appeal memorandum"));

        mvc.perform(get("/api/v1/tasks/" + id).with(as(Permission.TASK_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeName").value("Mona Saleh"));
    }

    @Test
    void aTaskNeedsNoCaseAndDefaultsToNormalPriority() throws Exception {
        UUID assignee = user("Karim Adel", true);
        clearInvocations(caseLookup);
        mvc.perform(postTask(task("Renew the bar subscription", null, assignee, null, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.caseId").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.caseReferenceNo").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.dueDate").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.priority").value("NORMAL"));
        verify(caseLookup, never()).recordEvent(org.mockito.ArgumentMatchers.any(), anyString(), anyString());
    }

    @Test
    void editsATaskAndRecordsANewAssignee() throws Exception {
        UUID caseId = createCase("Edit");
        UUID first = user("First", true);
        UUID second = user("Second", true);
        UUID id = create("Draft the memo", caseId, first, today().plusDays(3));
        clearInvocations(caseLookup);
        UUID editor = UUID.randomUUID();

        mvc.perform(put("/api/v1/tasks/" + id).with(as(editor, "editor", Permission.TASK_MANAGE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(edit("Draft and file the memo", second, today().plusDays(4), "HIGH", 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Draft and file the memo"))
                .andExpect(jsonPath("$.assigneeName").value("Second"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.version").value(1));
        assertThat(jdbc.queryForObject("select updated_by from task where id = ?", UUID.class, id)).isEqualTo(editor);
        verify(caseLookup).recordEvent(eq(caseId), eq("TASK_ASSIGNED"), contains("Second"));
        clearInvocations(caseLookup);

        mvc.perform(putTask(id, edit("Draft and file the memo", second, today().plusDays(6), "LOW", 1)))
                .andExpect(status().isOk());
        verify(caseLookup, never()).recordEvent(eq(caseId), anyString(), anyString());
    }

    @Test
    void movesThroughTheStatusLifecycle() throws Exception {
        UUID caseId = createCase("Lifecycle");
        UUID id = create("Collect the documents", caseId, user("Owner", true), null);
        clearInvocations(caseLookup);

        mvc.perform(moveTask(id, "IN_PROGRESS", 0)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        verify(caseLookup, never()).recordEvent(eq(caseId), anyString(), anyString());
        mvc.perform(moveTask(id, "DONE", 1)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.completedAt").isString());
        verify(caseLookup).recordEvent(eq(caseId), eq("TASK_COMPLETED"), contains("Collect the documents"));
        mvc.perform(moveTask(id, "IN_PROGRESS", 2)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("TASK_STATUS_TRANSITION"));
        mvc.perform(moveTask(id, "OPEN", 2)).andExpect(status().isOk())
                .andExpect(jsonPath("$.completedAt").value(Matchers.nullValue()));
        verify(caseLookup).recordEvent(eq(caseId), eq("TASK_REOPENED"), anyString());
        mvc.perform(moveTask(id, "CANCELLED", 3)).andExpect(status().isOk());
        verify(caseLookup).recordEvent(eq(caseId), eq("TASK_CANCELLED"), anyString());
        mvc.perform(moveTask(id, "DONE", 4)).andExpect(status().isUnprocessableContent());
        mvc.perform(moveTask(id, "CANCELLED", 4)).andExpect(status().isUnprocessableContent());
        mvc.perform(moveTask(id, "OPEN", 4)).andExpect(status().isOk());
        mvc.perform(moveTask(id, "OPEN", 5)).andExpect(status().isUnprocessableContent());
    }

    @Test
    void listsTasksByDueDateThenPriorityWithFilters() throws Exception {
        UUID caseId = createCase("List");
        UUID mona = user("Mona", true);
        UUID omar = user("Omar", true);
        UUID undated = create("Undated", null, mona, null);
        UUID later = create("Later", caseId, mona, today().plusDays(10));
        UUID soonNormal = create("Soon normal", null, omar, today().plusDays(2));
        MvcResult urgent = mvc.perform(postTask(task("Soon urgent", caseId, mona, today().plusDays(2), "URGENT")))
                .andExpect(status().isCreated()).andReturn();
        UUID soonUrgent = UUID.fromString(JsonPath.read(urgent.getResponse().getContentAsString(), "$.id"));
        UUID done = create("Done", caseId, mona, today());
        move(done, "DONE", 0);

        mvc.perform(get("/api/v1/tasks").with(as(Permission.TASK_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id").value(Matchers.contains(done.toString(), soonUrgent.toString(),
                        soonNormal.toString(), later.toString(), undated.toString())))
                .andExpect(jsonPath("$.totalItems").value(5));
        mvc.perform(get("/api/v1/tasks").param("assignee", omar.toString()).with(as(Permission.TASK_VIEW)))
                .andExpect(jsonPath("$.items[*].id").value(Matchers.contains(soonNormal.toString())));
        mvc.perform(get("/api/v1/tasks").param("assignee", "me").with(as(mona, "mona", Permission.TASK_VIEW)))
                .andExpect(jsonPath("$.totalItems").value(4));
        mvc.perform(get("/api/v1/tasks").param("caseId", caseId.toString()).param("status", "OPEN")
                        .with(as(Permission.TASK_VIEW)))
                .andExpect(jsonPath("$.items[*].id").value(Matchers.contains(soonUrgent.toString(), later.toString())));
        mvc.perform(get("/api/v1/tasks").param("status", "OPEN").param("status", "DONE").param("size", "2")
                        .with(as(Permission.TASK_VIEW)))
                .andExpect(jsonPath("$.totalItems").value(5))
                .andExpect(jsonPath("$.totalPages").value(3));
        mvc.perform(get("/api/v1/tasks").param("assignee", "someone").with(as(Permission.TASK_VIEW)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("assignee"));
        mvc.perform(get("/api/v1/tasks").param("status", "WAITING").with(as(Permission.TASK_VIEW)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/tasks").param("due", "tomorrow").with(as(Permission.TASK_VIEW)))
                .andExpect(status().isBadRequest());
    }

    // QA-0210 required-field validation

    @Test
    void requiredFieldsAreValidated() throws Exception {
        mvc.perform(postTask("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[*].field").value(Matchers.containsInAnyOrder("title", "assigneeId")));
        mvc.perform(postTask(task("   ", null, user("Blank", true), null, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("title"))
                .andExpect(jsonPath("$.errors[0].code").value("NotBlank"));
        mvc.perform(postTask(task("Bad priority", null, user("Prio", true), null, "CRITICAL")))
                .andExpect(status().isBadRequest());
        UUID id = create("Edit me", null, user("Editor", true), null);
        mvc.perform(putTask(id, "{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(Matchers.containsInAnyOrder("title", "assigneeId",
                        "version")));
        mvc.perform(post("/api/v1/tasks/" + id + "/status").with(as(Permission.TASK_MANAGE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(Matchers.containsInAnyOrder("status", "version")));
        assertThat(tasks()).isEqualTo(1);
    }

    // QA-0211 invalid input boundary

    @Test
    void boundariesOfTextDueDateAndAssignee() throws Exception {
        UUID assignee = user("Bounds", true);
        mvc.perform(postTask(task("t".repeat(201), null, assignee, null, null)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].code").value("Size"));
        mvc.perform(postTask(task("t".repeat(200), null, assignee, today(), null))).andExpect(status().isCreated());
        mvc.perform(postTask("""
                        {"title":"Long","description":"%s","assigneeId":"%s"}""".formatted("d".repeat(4001), assignee)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("description"));
        mvc.perform(postTask(task("Past", null, assignee, today().minusDays(1), null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("dueDate"))
                .andExpect(jsonPath("$.errors[0].code").value("FutureOrPresent"));
        mvc.perform(postTask(task("Disabled", null, user("Gone", false), null, null)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("ASSIGNEE_NOT_ASSIGNABLE"));
        mvc.perform(postTask(task("Unknown", null, UUID.randomUUID(), null, null)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("ASSIGNEE_NOT_ASSIGNABLE"));
        mvc.perform(postTask(task("No case", UUID.randomUUID(), assignee, null, null)))
                .andExpect(status().isNotFound());
        assertThat(tasks()).isEqualTo(1);
    }

    @Test
    void anUnchangedPastDueDateAndADisabledAssigneeMayStay() throws Exception {
        UUID assignee = user("Leaving", true);
        UUID id = create("Old task", null, assignee, today());
        jdbc.update("update task set due_date = due_date - 3 where id = ?", id);
        jdbc.update("update app_user set enabled = false where id = ?", assignee);

        mvc.perform(putTask(id, edit("Old task, renamed", assignee, today().minusDays(3), "NORMAL", 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overdue").value(true));
        mvc.perform(putTask(id, edit("Old task", assignee, today().minusDays(2), "NORMAL", 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("FutureOrPresent"));
        mvc.perform(putTask(id, edit("Old task", user("Disabled other", false), today().minusDays(3), "NORMAL", 1)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("ASSIGNEE_NOT_ASSIGNABLE"));
    }

    @Test
    void anArchivedCaseTakesNoTaskChanges() throws Exception {
        UUID caseId = createCase("Archived");
        UUID assignee = user("Archivist", true);
        UUID id = create("Before archiving", caseId, assignee, null);
        archive(caseId);
        mvc.perform(postTask(task("After", caseId, assignee, null, null)))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.rule").value("CASE_ARCHIVED"));
        mvc.perform(putTask(id, edit("Renamed", assignee, null, "LOW", 0)))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.rule").value("CASE_ARCHIVED"));
        mvc.perform(moveTask(id, "DONE", 0))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.rule").value("CASE_ARCHIVED"));
    }

    // QA-0212 unauthenticated access

    @Test
    void everyEndpointNeedsASession() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(get("/api/v1/tasks")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/tasks/" + id)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/tasks/reminders")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/tasks").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/tasks/" + id).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/tasks/" + id + "/status").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isUnauthorized());
    }

    // QA-0213 insufficient permission

    @Test
    void viewersCannotWriteAndOthersCannotRead() throws Exception {
        UUID assignee = user("Perm", true);
        mvc.perform(post("/api/v1/tasks").with(as(Permission.TASK_VIEW, Permission.CASE_EDIT)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(task("x", null, assignee, null, null)))
                .andExpect(status().isForbidden());
        UUID id = create("Guarded", null, assignee, null);
        mvc.perform(put("/api/v1/tasks/" + id).with(as(Permission.TASK_VIEW)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/tasks/" + id + "/status").with(as(Permission.TASK_VIEW)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DONE\",\"version\":0}"))
                .andExpect(status().isForbidden());
        for (String path : List.of("/api/v1/tasks", "/api/v1/tasks/" + id, "/api/v1/tasks/reminders")) {
            mvc.perform(get(path).with(as(Permission.CASE_VIEW, Permission.HEARING_VIEW)))
                    .andExpect(status().isForbidden());
        }
        assertThat(statusOf(id)).isEqualTo("OPEN");
        assertThat(tasks()).isEqualTo(1);
    }

    @Test
    void aRefusalIsAuditedAsAccessDenied() throws Exception {
        UUID actor = UUID.randomUUID();
        mvc.perform(post("/api/v1/tasks").with(csrf()).with(as(actor, "refused-" + actor, Permission.TASK_VIEW))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("""
                select count(*) from audit_event where action = 'ACCESS_DENIED' and actor_id = ?
                and target_id = 'POST /api/v1/tasks'""", Integer.class, actor)).isEqualTo(1);
    }

    // QA-0214 record integrity and rollback

    @Test
    void aFailingTimelineEventRollsTheWriteBack() throws Exception {
        UUID caseId = createCase("Rollback");
        UUID assignee = user("Rollback", true);
        UUID id = create("Kept", caseId, assignee, null);
        doThrow(new IllegalStateException("timeline unavailable")).when(caseLookup)
                .recordEvent(eq(caseId), anyString(), anyString());
        mvc.perform(postTask(task("Lost", caseId, assignee, null, null))).andExpect(status().isInternalServerError());
        mvc.perform(moveTask(id, "DONE", 0)).andExpect(status().isInternalServerError());
        assertThat(tasks()).isEqualTo(1);
        assertThat(statusOf(id)).isEqualTo("OPEN");
    }

    @Test
    void unknownTasksAreNotFoundAndTheDatabaseKeepsStatusesConsistent() throws Exception {
        mvc.perform(get("/api/v1/tasks/" + UUID.randomUUID()).with(as(Permission.TASK_VIEW)))
                .andExpect(status().isNotFound());
        mvc.perform(moveTask(UUID.randomUUID(), "DONE", 0)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/tasks/not-a-uuid").with(as(Permission.TASK_VIEW))).andExpect(status().isBadRequest());
        UUID actor = UUID.randomUUID();
        String insert = """
                insert into task (title, assignee_id, status, completed_at, created_by, updated_by)
                values ('x', ?, ?, ?, ?, ?)""";
        assertThatThrownBy(() -> jdbc.update(insert, actor, "DONE", null, actor, actor))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, actor, "OPEN", java.sql.Timestamp.from(Instant.now()), actor,
                actor)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, actor, "WAITING", null, actor, actor))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(tasks()).isZero();
    }

    // QA-0215 concurrent or repeat request

    @Test
    void aStaleVersionIsAConflictAndChangesNothing() throws Exception {
        UUID assignee = user("Stale", true);
        UUID id = create("Versioned", null, assignee, null);
        move(id, "IN_PROGRESS", 0);
        mvc.perform(moveTask(id, "DONE", 0)).andExpect(status().isConflict());
        mvc.perform(putTask(id, edit("Changed", assignee, null, "LOW", 0))).andExpect(status().isConflict());
        assertThat(statusOf(id)).isEqualTo("IN_PROGRESS");
        assertThat(jdbc.queryForObject("select title from task where id = ?", String.class, id)).isEqualTo("Versioned");
    }
}
