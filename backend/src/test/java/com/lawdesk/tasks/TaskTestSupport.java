package com.lawdesk.tasks;

import static com.lawdesk.security.TestActors.as;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.lawdesk.platform.OperationsTestData;
import com.lawdesk.cases.CaseLookup;
import com.lawdesk.platform.PostgresTestSupport;
import com.lawdesk.security.Permission;

/**
 * Shared setup for the LD-027 / LD-028 task tests. Cases come from the Phase 3 API, assignees are inserted as users the
 * way {@code UserDirectoryTest} does. The configuration equals the other Phase 3–5 API tests, so they share a context
 * and database: tasks are removed after each test, cases and users stay (each test makes its own).
 */
@SpringBootTest
@AutoConfigureMockMvc
abstract class TaskTestSupport extends PostgresTestSupport {

    static final UUID CLERK = UUID.fromString("00000000-0000-4000-8000-0000000000e1");

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoSpyBean
    CaseLookup caseLookup;

    @AfterEach
    void removeTasks() {
        OperationsTestData.deleteAll(jdbc);
    }

    static LocalDate today() {
        return LocalDate.now(ZoneId.of("Africa/Cairo"));
    }

    UUID user(String displayName, boolean enabled) {
        String username = "task" + UUID.randomUUID().toString().substring(0, 8);
        return jdbc.queryForObject("""
                insert into app_user (username, email, display_name, password_hash, enabled, created_by, updated_by)
                values (?, ?, ?, 'x', ?, gen_random_uuid(), gen_random_uuid()) returning id""", UUID.class,
                username, username + "@example.invalid", displayName, enabled);
    }

    UUID createCase(String title) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/cases").with(as(Permission.CASE_CREATE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","caseType":"CIVIL","openedOn":"2024-01-10"}""".formatted(title)))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    void archive(UUID caseId) throws Exception {
        for (String[] step : new String[][] {{"CLOSED", "0"}, {"ARCHIVED", "1"}}) {
            mvc.perform(post("/api/v1/cases/" + caseId + "/status").with(as(Permission.CASE_STATUS_CHANGE))
                            .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"status":"%s","version":%s}""".formatted(step[0], step[1])))
                    .andExpect(status().isOk());
        }
    }

    static String quote(Object value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    static String task(String title, UUID caseId, UUID assignee, Object dueDate, String priority) {
        return """
                {"title":%s,"description":null,"caseId":%s,"assigneeId":%s,"dueDate":%s,"priority":%s}"""
                .formatted(quote(title), quote(caseId), quote(assignee), quote(dueDate), quote(priority));
    }

    static String edit(String title, UUID assignee, Object dueDate, String priority, long version) {
        return """
                {"title":%s,"description":null,"assigneeId":%s,"dueDate":%s,"priority":%s,"version":%d}"""
                .formatted(quote(title), quote(assignee), quote(dueDate), quote(priority), version);
    }

    MockHttpServletRequestBuilder postTask(String json) {
        return post("/api/v1/tasks").with(as(CLERK, "clerk", Permission.TASK_MANAGE)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json);
    }

    MockHttpServletRequestBuilder putTask(UUID id, String json) {
        return put("/api/v1/tasks/" + id).with(as(CLERK, "clerk", Permission.TASK_MANAGE)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json);
    }

    MockHttpServletRequestBuilder moveTask(UUID id, String status, long version) {
        return post("/api/v1/tasks/" + id + "/status").with(as(CLERK, "clerk", Permission.TASK_MANAGE)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"status":"%s","version":%d}""".formatted(status, version));
    }

    UUID create(String title, UUID caseId, UUID assignee, Object dueDate) throws Exception {
        MvcResult result = mvc.perform(postTask(task(title, caseId, assignee, dueDate, null)))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    void move(UUID id, String status, long version) throws Exception {
        mvc.perform(moveTask(id, status, version)).andExpect(status().isOk());
    }

    String statusOf(UUID id) {
        return jdbc.queryForObject("select status from task where id = ?", String.class, id);
    }

    int tasks() {
        Integer count = jdbc.queryForObject("select count(*) from task", Integer.class);
        return count == null ? 0 : count;
    }
}
