package com.lawdesk.tasks;

import static com.lawdesk.security.TestActors.as;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import com.lawdesk.security.Permission;

/** LD-028 deadline reminders, in the application only (docs/delivery/PHASE-5-ACCEPTANCE.md): QA-0217…QA-0223. */
class TaskReminderApiTest extends TaskTestSupport {

    private UUID overdue(UUID assignee, String title, int daysLate) throws Exception {
        UUID id = create(title, null, assignee, today());
        jdbc.update("update task set due_date = due_date - ? where id = ?", daysLate, id);
        return id;
    }

    @Test
    void countsTheSignedInUsersOverdueAndDueSoonTasks() throws Exception {
        UUID me = user("Reminded", true);
        UUID other = user("Someone else", true);
        UUID late = overdue(me, "Late", 2);
        UUID lateButDone = overdue(me, "Late but done", 1);
        move(lateButDone, "DONE", 0);
        overdue(other, "Not mine", 1);
        UUID dueToday = create("Today", null, me, today());
        UUID inThreeDays = create("In three days", null, me, today().plusDays(3));
        UUID started = create("Started", null, me, today().plusDays(1));
        move(started, "IN_PROGRESS", 0);
        create("In four days", null, me, today().plusDays(4));
        create("Undated", null, me, null);

        mvc.perform(get("/api/v1/tasks/reminders").with(as(me, "reminded", Permission.TASK_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.today").value(today().toString()))
                .andExpect(jsonPath("$.dueSoonDays").value(3))
                .andExpect(jsonPath("$.overdue").value(1))
                .andExpect(jsonPath("$.dueSoon").value(3));

        mvc.perform(get("/api/v1/tasks").param("assignee", "me").param("due", "overdue")
                        .with(as(me, "reminded", Permission.TASK_VIEW)))
                .andExpect(jsonPath("$.items[*].id").value(Matchers.contains(late.toString())))
                .andExpect(jsonPath("$.items[0].overdue").value(true))
                .andExpect(jsonPath("$.items[0].dueSoon").value(false));
        mvc.perform(get("/api/v1/tasks").param("assignee", "me").param("due", "soon")
                        .with(as(me, "reminded", Permission.TASK_VIEW)))
                .andExpect(jsonPath("$.items[*].id").value(Matchers.contains(dueToday.toString(), started.toString(),
                        inThreeDays.toString())))
                .andExpect(jsonPath("$.items[*].dueSoon").value(Matchers.everyItem(Matchers.is(true))));
        mvc.perform(get("/api/v1/tasks").param("due", "overdue").with(as(Permission.TASK_VIEW)))
                .andExpect(jsonPath("$.totalItems").value(2));
    }

    @Test
    void aUserWithoutTasksHasNoReminders() throws Exception {
        mvc.perform(get("/api/v1/tasks/reminders").with(as(UUID.randomUUID(), "nobody", Permission.TASK_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overdue").value(0))
                .andExpect(jsonPath("$.dueSoon").value(0));
    }

    @Test
    void remindersNeedTaskView() throws Exception {
        mvc.perform(get("/api/v1/tasks/reminders").with(as(Permission.HEARING_VIEW))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/tasks/reminders")).andExpect(status().isUnauthorized());
    }
}
