package com.lawdesk.reports;

import static com.lawdesk.security.TestActors.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.lawdesk.platform.PostgresTestSupport;
import com.lawdesk.security.Permission;

/**
 * LD-033 workload report (docs/delivery/PHASE-6-ACCEPTANCE.md), read through Workstream D's TaskReportQueries and the
 * user directory. The task port's "today" is the real day in Egypt, so due dates are relative to it; totals are checked
 * as the change from a baseline because other test classes share the database.
 */
@SpringBootTest
@AutoConfigureMockMvc
class WorkloadReportApiTest extends PostgresTestSupport {

    private static final String REPORT = "/api/v1/reports/workload";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private ReportTestData data;
    private UUID amal;
    private UUID badr;
    private final UUID unknown = UUID.randomUUID();

    @BeforeEach
    void users() {
        data = new ReportTestData(jdbc);
        amal = data.insertUser("Amal " + data.marker());
        badr = data.insertUser("Badr " + data.marker());
    }

    @AfterEach
    void removeRows() {
        data.deleteCreated();
    }

    private DocumentContext read() throws Exception {
        return JsonPath.parse(mvc.perform(get(REPORT).with(as(Permission.REPORT_VIEW))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private static Map<String, Object> row(DocumentContext json, UUID assignee) {
        List<Map<String, Object>> rows = json.read("$.items[?(@.assigneeId == '" + assignee + "')]");
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }

    private static long figure(DocumentContext json, String path) {
        return json.read(path, Long.class);
    }

    @Test
    void reportsTheOpenWorkOfEveryAssigneeWithTheirNamesAndTheTaskTotals() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("Africa/Cairo"));
        UUID caseId = data.insertCase("workload", "CIVIL", "ACTIVE", today.minusYears(1));
        DocumentContext before = read();

        data.insertTask(caseId, amal, "overdue", today.minusDays(1), "OPEN");
        data.insertTask(caseId, amal, "later", today.plusDays(10), "IN_PROGRESS");
        data.insertTask(caseId, amal, "finished", today.minusDays(1), "DONE");
        data.insertTask(null, badr, "undated", null, "OPEN");
        data.insertTask(caseId, badr, "due today", today, "OPEN");
        data.insertTask(caseId, unknown, "left behind", today.minusDays(3), "OPEN");
        DocumentContext after = read();

        assertThat(row(after, amal)).containsEntry("displayName", "Amal " + data.marker())
                .containsEntry("open", 1).containsEntry("inProgress", 1).containsEntry("overdue", 1)
                .containsEntry("enabled", true);
        assertThat(row(after, badr)).containsEntry("open", 2).containsEntry("inProgress", 0)
                .containsEntry("overdue", 0);
        assertThat(row(after, unknown)).containsEntry("displayName", null).containsEntry("username", null)
                .containsEntry("open", 1).containsEntry("overdue", 1);

        List<String> order = after.read("$.items[*].assigneeId");
        assertThat(order.indexOf(amal.toString())).isLessThan(order.indexOf(badr.toString()));
        assertThat(order.indexOf(badr.toString())).isLessThan(order.indexOf(unknown.toString()));

        for (Map.Entry<String, Long> change : Map.of("$.summary.byStatus.OPEN", 4L,
                "$.summary.byStatus.IN_PROGRESS", 1L, "$.summary.byStatus.DONE", 1L, "$.summary.byStatus.CANCELLED", 0L,
                "$.summary.overdue", 2L, "$.summary.dueSoon", 1L).entrySet()) {
            assertThat(figure(after, change.getKey()) - figure(before, change.getKey())).as(change.getKey())
                    .isEqualTo(change.getValue());
        }
        assertThat(after.read("$.dueSoonDays", Integer.class)).isEqualTo(3);
    }

    @Test
    void anAssigneeWithoutOpenTasksIsNotListed() throws Exception {
        data.insertTask(null, amal, "done", null, "DONE");
        data.insertTask(null, amal, "cancelled", null, "CANCELLED");
        List<Map<String, Object>> rows = read().read("$.items[?(@.assigneeId == '" + amal + "')]");
        assertThat(rows).isEmpty();
    }

    @Test
    void needsASessionAndTheReportPermission() throws Exception {
        mvc.perform(get(REPORT)).andExpect(status().isUnauthorized());
        UUID actor = UUID.randomUUID();
        mvc.perform(get(REPORT).with(as(actor, "task-manager", Permission.TASK_VIEW))).andExpect(status().isForbidden());
        assertThat(jdbc.queryForList("""
                select target_id from audit_event where action = 'ACCESS_DENIED' and actor_id = ?""", String.class,
                actor)).containsExactly("GET /api/v1/reports/workload");
    }
}
