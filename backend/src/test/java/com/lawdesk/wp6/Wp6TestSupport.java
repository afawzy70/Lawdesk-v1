package com.lawdesk.wp6;

import static com.lawdesk.security.TestActors.as;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;
import com.lawdesk.platform.OperationsTestData;
import com.lawdesk.platform.PostgresTestSupport;
import com.lawdesk.security.Permission;
import com.lawdesk.security.TestActors;

/**
 * Shared setup of the WP6 tests (LD-056): users, cases inserted with their members, proceedings and hearings inserted
 * directly (their own APIs are tested elsewhere), everything WP6 adds is exercised through MockMvc.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class Wp6TestSupport extends PostgresTestSupport {

    /** What a legal member of staff holds. */
    protected static final Permission[] STAFF = {Permission.CASE_VIEW, Permission.TASK_VIEW, Permission.TASK_MANAGE,
        Permission.HEARING_VIEW, Permission.HEARING_MANAGE, Permission.CALENDAR_VIEW, Permission.CALENDAR_MANAGE,
        Permission.EXPERT_VIEW, Permission.INFO_REQUEST_VIEW, Permission.DEADLINE_VIEW};

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcTemplate jdbc;

    private int cases;

    @AfterEach
    void removeWp6Data() {
        OperationsTestData.deleteAll(jdbc);
        jdbc.update("delete from notification_preference");
        jdbc.update("delete from user_role where user_id in (select id from app_user where username like 'wp6%')");
        jdbc.update("delete from app_user where username like 'wp6%'");
    }

    protected static LocalDate today() {
        return LocalDate.now(ZoneId.of("Africa/Cairo"));
    }

    protected UUID user(String displayName) {
        String username = "wp6" + UUID.randomUUID().toString().substring(0, 8);
        return jdbc.queryForObject("""
                insert into app_user (username, email, display_name, password_hash, enabled, created_by, updated_by)
                values (?, ?, ?, 'x', true, gen_random_uuid(), gen_random_uuid()) returning id""", UUID.class,
                username, username + "@example.invalid", displayName);
    }

    /** A case whose specialist is {@code primary}; nobody else sees it without a scope or a task on it. */
    protected UUID legalCase(UUID primary, UUID alternate) {
        String reference = "W6-" + (++cases) + "-" + UUID.randomUUID().toString().substring(0, 6);
        return jdbc.queryForObject("""
                insert into legal_case (reference_no, title, case_type, opened_on, status, primary_member_id,
                                        alternate_member_id, created_by, updated_by)
                values (?, ?, 'ADMINISTRATIVE', '2026-01-10', 'OPEN', ?, ?, ?, ?) returning id""", UUID.class,
                reference, "Case " + reference, primary, alternate, primary, primary);
    }

    protected UUID proceeding(UUID caseId, String stage) {
        UUID parent = "FIRST_INSTANCE".equals(stage) ? null : proceeding(caseId, "FIRST_INSTANCE");
        UUID court = jdbc.queryForObject("""
                insert into court (code, name_ar, name_en, level, created_by, updated_by)
                values (?, 'محكمة', 'Court', ?, gen_random_uuid(), gen_random_uuid()) returning id""", UUID.class,
                "W6-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(), stage);
        return jdbc.queryForObject("""
                insert into proceeding (case_id, stage, court_id, court_case_number, court_case_year, filing_date,
                                        parent_proceeding_id, created_by, updated_by)
                values (?, ?, ?, ?, 2026, '2026-01-12', ?, gen_random_uuid(), gen_random_uuid()) returning id""",
                UUID.class, caseId, stage, court, UUID.randomUUID().toString().substring(0, 6), parent);
    }

    protected UUID hearing(UUID caseId, UUID proceeding, LocalDate date, LocalTime time, String kind, UUID responsible) {
        return jdbc.queryForObject("""
                insert into hearing (case_id, proceeding_id, hearing_date, hearing_time, purpose, kind,
                                     responsible_member_id, created_by, updated_by)
                values (?, ?, ?, ?, 'PLEADING', ?, ?, gen_random_uuid(), gen_random_uuid()) returning id""", UUID.class,
                caseId, proceeding, date, time, kind, responsible);
    }

    /** Signs in as the user with the permissions plus the department scope and the right to edit in it. */
    protected RequestPostProcessor staff(UUID id, Permission... permissions) {
        return as(id, "u" + id.toString().substring(0, 6), permissions);
    }

    /** Sees every case (scope ALL) but may change none: no CASE_EDIT_IN_SCOPE, not a member. */
    protected RequestPostProcessor viewer(UUID id, Permission... permissions) {
        return TestActors.scoped(id, "v" + id.toString().substring(0, 6), List.of("ALL"), permissions);
    }

    /** Sees nothing it is not a member of (or does not hold a task on). */
    protected RequestPostProcessor outsider(UUID id, Permission... permissions) {
        return TestActors.scoped(id, "o" + id.toString().substring(0, 6), List.of(), permissions);
    }

    protected MockHttpServletRequestBuilder postJson(RequestPostProcessor actor, String path, String body) {
        return post(path).with(actor).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    protected MockHttpServletRequestBuilder putJson(RequestPostProcessor actor, String path, String body) {
        return put(path).with(actor).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    protected MockHttpServletRequestBuilder getJson(RequestPostProcessor actor, String path) {
        return get(path).with(actor);
    }

    protected String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }

    protected <T> T read(MvcResult result, String path) throws Exception {
        return JsonPath.read(body(result), path);
    }

    protected UUID idOf(MvcResult result) throws Exception {
        return UUID.fromString(read(result, "$.id"));
    }

    protected int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    protected static String q(Object value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    /** A task body; null values are sent as JSON null. */
    protected static String taskBody(String title, UUID caseId, UUID assignee, Object startsOn, Object dueDate,
            String instructions) {
        return """
                {"title":%s,"caseId":%s,"assigneeId":%s,"startsOn":%s,"dueDate":%s,"instructions":%s}"""
                .formatted(q(title), q(caseId), q(assignee), q(startsOn), q(dueDate), q(instructions));
    }

    protected UUID createTask(RequestPostProcessor actor, String title, UUID caseId, UUID assignee, Object dueDate)
            throws Exception {
        MvcResult result = mvc.perform(postJson(actor, "/api/v1/tasks", taskBody(title, caseId, assignee, null, dueDate, null)))
                .andReturn();
        if (result.getResponse().getStatus() != 201) {
            throw new AssertionError("task not created: " + result.getResponse().getStatus() + " " + body(result));
        }
        return idOf(result);
    }

    protected long versionOf(UUID taskId) {
        Long version = jdbc.queryForObject("select version from task where id = ?", Long.class, taskId);
        return version == null ? 0 : version;
    }

    protected String statusOf(UUID taskId) {
        return jdbc.queryForObject("select status from task where id = ?", String.class, taskId);
    }

    protected RequestPostProcessor manager(UUID id) {
        return staff(id, STAFF);
    }
}
