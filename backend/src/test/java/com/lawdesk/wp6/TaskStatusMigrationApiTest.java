package com.lawdesk.wp6;

import static com.lawdesk.security.TestActors.as;
import static com.lawdesk.security.TestActors.scoped;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;
import com.lawdesk.followup.FollowUpRun;
import com.lawdesk.platform.PostgresTestSupport;
import com.lawdesk.security.Permission;

/**
 * LD-056, V720, end to end. {@link TaskStatusMigrationTest} proves the migration at the table. This starts the whole application on a
 * database that holds tasks exactly as the previous release wrote them (statuses OPEN, IN_PROGRESS, DONE, CANCELLED, schema of V665),
 * lets Flyway apply the later migrations at start-up, and then reads and writes through the API: lists, details, filters (new words and
 * the old words as input), the case-visibility rule that depends on open tasks, the workload report and its export, the indicators, the
 * calendar, history, writers that still send the old words, the follow-up run that marks overdue tasks late and the notifications it
 * raises, and the reversal map. Nothing is excluded to make a check pass: every expectation is the old word's meaning.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TaskStatusMigrationApiTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Africa/Cairo"));

    /** What the previous release stored for each task, by title: the word, the assignee, the case, the due date. */
    private record Legacy(String title, String word, String assignee, String caseKey, LocalDate due, boolean completed) {
    }

    private static final String MEMBER = "member";
    private static final String ASSIGNEE_A = "assignee-a";
    private static final String ASSIGNEE_B = "assignee-b";
    private static final String OPEN_ON_C2 = "open-on-c2";
    private static final String DONE_ON_C2 = "done-on-c2";

    private static final List<Legacy> LEGACY = List.of(
            new Legacy("t-open", "OPEN", ASSIGNEE_A, "C1", TODAY.plusDays(10), false),
            new Legacy("t-overdue", "OPEN", ASSIGNEE_A, "C1", TODAY.minusDays(5), false),
            new Legacy("t-progress", "IN_PROGRESS", ASSIGNEE_B, "C1", TODAY.plusDays(3), false),
            new Legacy("t-done", "DONE", ASSIGNEE_B, "C1", TODAY.minusDays(20), true),
            new Legacy("t-cancelled", "CANCELLED", ASSIGNEE_B, "C1", TODAY.plusDays(4), false),
            new Legacy("t-standalone", "OPEN", ASSIGNEE_A, null, TODAY.plusDays(20), false),
            new Legacy("t-c2-open", "OPEN", OPEN_ON_C2, "C2", TODAY.plusDays(6), false),
            new Legacy("t-c2-done", "DONE", DONE_ON_C2, "C2", TODAY.minusDays(8), true),
            new Legacy("w-start", "OPEN", ASSIGNEE_A, "C1", TODAY.plusDays(12), false),
            new Legacy("w-reopen", "DONE", ASSIGNEE_B, "C1", TODAY.minusDays(2), true),
            new Legacy("w-finish", "OPEN", ASSIGNEE_A, "C1", TODAY.plusDays(13), false),
            new Legacy("w-late", "OPEN", ASSIGNEE_A, "C1", TODAY.minusDays(9), false));

    private static final Map<String, UUID> USERS = new LinkedHashMap<>();
    private static final Map<String, UUID> CASES = new LinkedHashMap<>();
    private static final Map<String, UUID> TASKS = new LinkedHashMap<>();
    private static final PGSimpleDataSource DATABASE = legacyDatabase();

    /** A database at the schema of the previous release (V665) holding tasks the previous release wrote. */
    private static PGSimpleDataSource legacyDatabase() {
        PGSimpleDataSource database = (PGSimpleDataSource) PostgresTestSupport.freshDatabase();
        Flyway.configure().dataSource(database).locations("classpath:db/migration").target(MigrationVersion.fromVersion("665"))
                .outOfOrder(true).load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(database);
        for (String key : List.of(MEMBER, ASSIGNEE_A, ASSIGNEE_B, OPEN_ON_C2, DONE_ON_C2)) {
            USERS.put(key, jdbc.queryForObject("""
                    insert into app_user (username, email, display_name, password_hash, created_by, updated_by)
                    values (?, ?, ?, 'x', gen_random_uuid(), gen_random_uuid()) returning id""", UUID.class,
                    "legacy-" + key, "legacy-" + key + "@example.invalid", "Legacy " + key));
        }
        CASES.put("C1", jdbc.queryForObject("""
                insert into legal_case (reference_no, title, case_type, opened_on, primary_member_id, created_by, updated_by)
                values ('LEG-1', 'Legacy case one', 'CIVIL', date '2025-01-01', ?, ?, ?) returning id""", UUID.class,
                USERS.get(MEMBER), USERS.get(MEMBER), USERS.get(MEMBER)));
        CASES.put("C2", jdbc.queryForObject("""
                insert into legal_case (reference_no, title, case_type, opened_on, created_by, updated_by)
                values ('LEG-2', 'Legacy case two', 'CIVIL', date '2025-01-01', gen_random_uuid(), gen_random_uuid()) returning id""",
                UUID.class));
        for (Legacy task : LEGACY) {
            UUID id = jdbc.queryForObject("""
                    insert into task (case_id, title, description, assignee_id, due_date, priority, status, completed_at,
                                      created_by, updated_by)
                    values (?, ?, 'Legacy description', ?, ?, 'HIGH', ?, case when ? then timestamptz '2026-10-01 10:00+00' end, ?, ?)
                    returning id""", UUID.class, task.caseKey() == null ? null : CASES.get(task.caseKey()), task.title(),
                    USERS.get(task.assignee()), task.due(), task.word(), task.completed(), USERS.get(MEMBER), USERS.get(MEMBER));
            TASKS.put(task.title(), id);
        }
        return database;
    }

    @DynamicPropertySource
    static void useLegacyDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::getUrl);
        registry.add("spring.datasource.username", DATABASE::getUser);
        registry.add("spring.datasource.password", DATABASE::getPassword);
        registry.add("spring.datasource.hikari.minimum-idle", () -> 0);
        registry.add("lawdesk.followup.schedule-enabled", () -> false);
        registry.add("lawdesk.ocr.schedule-enabled", () -> false);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    FollowUpRun followUp;

    private static String expectedNew(String word) {
        return switch (word) {
            case "OPEN" -> "NEW";
            case "DONE" -> "COMPLETED";
            default -> word;
        };
    }

    private RequestPostProcessor manager() {
        return as(Permission.TASK_VIEW, Permission.TASK_MANAGE, Permission.CASE_VIEW, Permission.REPORT_VIEW,
                Permission.REPORT_EXPORT, Permission.CALENDAR_VIEW);
    }

    private RequestPostProcessor user(String key, Permission... permissions) {
        return scoped(USERS.get(key), "legacy-" + key, List.of(), permissions);
    }

    private String json(RequestPostProcessor actor, String url) throws Exception {
        return mvc.perform(get(url).with(actor)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    /** The ids of the seeded tasks in the answer, by title. */
    private Map<String, String> seededStatuses(String body) {
        List<Map<String, Object>> items = JsonPath.read(body, "$.items");
        Map<String, String> byTitle = new TreeMap<>();
        for (Map<String, Object> item : items) {
            if (TASKS.containsKey(item.get("title"))) {
                byTitle.put((String) item.get("title"), (String) item.get("status"));
            }
        }
        return byTitle;
    }

    private Set<String> titles(String body) {
        List<Map<String, Object>> items = JsonPath.read(body, "$.items");
        return items.stream().map(i -> (String) i.get("title")).filter(TASKS::containsKey).collect(Collectors.toCollection(TreeSet::new));
    }

    private Set<String> legacyTitles(String... words) {
        Set<String> wanted = Set.of(words);
        return LEGACY.stream().filter(t -> wanted.contains(t.word())).map(Legacy::title).collect(Collectors.toCollection(TreeSet::new));
    }

    // ---- reads ---------------------------------------------------------------------------------------------------------------------

    @Test
    @Order(1)
    void everyLegacyRowSurvivedWithOnlyTheTwoRenamedStatusesChanged() {
        assertThat(jdbc.queryForObject("select count(*) from task", Integer.class)).isEqualTo(LEGACY.size());
        for (Legacy legacy : LEGACY) {
            Map<String, Object> row = jdbc.queryForMap("select * from task where id = ?", TASKS.get(legacy.title()));
            assertThat(row.get("status")).as(legacy.title()).isEqualTo(expectedNew(legacy.word()));
            assertThat(row.get("title")).isEqualTo(legacy.title());
            assertThat(row.get("description")).isEqualTo("Legacy description");
            assertThat(row.get("priority")).isEqualTo("HIGH");
            assertThat(row.get("assignee_id")).isEqualTo(USERS.get(legacy.assignee()));
            assertThat(row.get("due_date")).isEqualTo(java.sql.Date.valueOf(legacy.due()));
            assertThat(row.get("completed_at") != null).as(legacy.title() + " completed_at").isEqualTo(legacy.completed());
            assertThat(row.get("starts_on")).isNull();
            assertThat(row.get("status_before_late")).isNull();
        }
        assertThat(jdbc.queryForObject("select count(*) from task where status in ('OPEN', 'DONE')", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from task_history", Integer.class)).isZero();
    }

    @Test
    @Order(2)
    void listAndDetailShowTheNewNamesForOldRowsAndNeverTheOldWords() throws Exception {
        String list = json(manager(), "/api/v1/tasks?size=100");
        Map<String, String> statuses = seededStatuses(list);
        assertThat(statuses).hasSize(LEGACY.size());
        for (Legacy legacy : LEGACY) {
            assertThat(statuses.get(legacy.title())).as(legacy.title()).isEqualTo(expectedNew(legacy.word()));
        }
        assertThat(list).doesNotContain("\"OPEN\"").doesNotContain("\"DONE\"");
        for (Legacy legacy : LEGACY) {
            String detail = json(manager(), "/api/v1/tasks/" + TASKS.get(legacy.title()));
            assertThat((String) JsonPath.read(detail, "$.status")).isEqualTo(expectedNew(legacy.word()));
            assertThat(JsonPath.<Object>read(detail, "$.completedAt") != null).as(legacy.title()).isEqualTo(legacy.completed());
            assertThat((String) JsonPath.read(detail, "$.title")).isEqualTo(legacy.title());
            assertThat(JsonPath.<Object>read(detail, "$.startsOn")).isNull();
            assertThat((Boolean) JsonPath.read(detail, "$.overdue")).as(legacy.title() + " overdue")
                    .isEqualTo(!legacy.completed() && !"CANCELLED".equals(legacy.word()) && legacy.due().isBefore(TODAY));
        }
    }

    @Test
    @Order(3)
    void filtersTakeTheNewNamesAndTheOldWordsAlike() throws Exception {
        Set<String> open = legacyTitles("OPEN");
        Set<String> done = legacyTitles("DONE");
        assertThat(titles(json(manager(), "/api/v1/tasks?size=100&status=OPEN"))).isEqualTo(open);
        assertThat(titles(json(manager(), "/api/v1/tasks?size=100&status=NEW"))).isEqualTo(open);
        assertThat(titles(json(manager(), "/api/v1/tasks?size=100&status=open"))).as("case-insensitive old word").isEqualTo(open);
        assertThat(titles(json(manager(), "/api/v1/tasks?size=100&status=DONE"))).isEqualTo(done);
        assertThat(titles(json(manager(), "/api/v1/tasks?size=100&status=COMPLETED"))).isEqualTo(done);
        assertThat(titles(json(manager(), "/api/v1/tasks?size=100&status=IN_PROGRESS"))).isEqualTo(legacyTitles("IN_PROGRESS"));
        assertThat(titles(json(manager(), "/api/v1/tasks?size=100&status=CANCELLED"))).isEqualTo(legacyTitles("CANCELLED"));
        Set<String> openAndDone = new TreeSet<>(open);
        openAndDone.addAll(done);
        assertThat(titles(json(manager(), "/api/v1/tasks?size=100&status=OPEN&status=DONE"))).isEqualTo(openAndDone);
        assertThat(titles(json(manager(), "/api/v1/tasks?size=100&status=OPEN&status=NEW"))).as("both spellings of one status").isEqualTo(open);
        assertThat(titles(json(manager(), "/api/v1/tasks?size=100&status=WAITING_DOCUMENTS&status=WAITING_OTHER&status=LATE"))).isEmpty();
        mvc.perform(get("/api/v1/tasks?status=FINISHED").with(manager())).andExpect(status().isBadRequest());
        // The other filters keep their meaning on migrated rows.
        assertThat(titles(json(manager(), "/api/v1/tasks?size=100&due=overdue"))).isEqualTo(new TreeSet<>(Set.of("t-overdue", "w-late")));
        assertThat(titles(json(manager(), "/api/v1/tasks?size=100&assignee=" + USERS.get(ASSIGNEE_B))))
                .isEqualTo(new TreeSet<>(Set.of("t-progress", "t-done", "t-cancelled", "w-reopen")));
        assertThat(titles(json(manager(), "/api/v1/tasks?size=100&caseId=" + CASES.get("C2"))))
                .isEqualTo(new TreeSet<>(Set.of("t-c2-open", "t-c2-done")));
        assertThat(titles(json(manager(), "/api/v1/tasks?size=100&status=OPEN&caseId=" + CASES.get("C2"))))
                .isEqualTo(new TreeSet<>(Set.of("t-c2-open")));
    }

    /** A task on a case opens the case to its assignee while the task is open (OPEN before, any status but COMPLETED/CANCELLED now). */
    @Test
    @Order(4)
    void aCaseIsOpenToTheAssigneeOfAnOldOpenTaskAndClosedToTheAssigneeOfAnOldDoneTask() throws Exception {
        mvc.perform(get("/api/v1/cases/" + CASES.get("C2")).with(user(OPEN_ON_C2, Permission.CASE_VIEW))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/cases/" + CASES.get("C2")).with(user(DONE_ON_C2, Permission.CASE_VIEW))).andExpect(status().isNotFound());
        // The same answers from the database function every list, count and export uses.
        assertThat(jdbc.queryForObject("select case_visible(?, ?, false, '', '')", Boolean.class, CASES.get("C2"), USERS.get(OPEN_ON_C2))).isTrue();
        assertThat(jdbc.queryForObject("select case_visible(?, ?, false, '', '')", Boolean.class, CASES.get("C2"), USERS.get(DONE_ON_C2))).isFalse();
        // Assignee B holds an old IN_PROGRESS task on case one: visible; the member always sees it.
        mvc.perform(get("/api/v1/cases/" + CASES.get("C1")).with(user(ASSIGNEE_B, Permission.CASE_VIEW))).andExpect(status().isOk());
        // The task list of the assignee of the old done task shows no task of case two that is not hers.
        assertThat(titles(json(user(DONE_ON_C2, Permission.TASK_VIEW, Permission.CASE_VIEW), "/api/v1/tasks?size=100&assignee=me")))
                .isEqualTo(new TreeSet<>(Set.of("t-c2-done")));
    }

    @Test
    @Order(5)
    void theWorkloadReportCountsOldRowsUnderTheNewStatusesAndItsExportAgrees() throws Exception {
        String body = json(manager(), "/api/v1/reports/workload");
        Map<String, Integer> byStatus = JsonPath.read(body, "$.summary.byStatus");
        Map<String, Long> expected = LEGACY.stream().collect(Collectors.groupingBy(t -> expectedNew(t.word()), TreeMap::new, Collectors.counting()));
        for (String status : List.of("NEW", "IN_PROGRESS", "COMPLETED", "CANCELLED")) {
            assertThat(byStatus.get(status).longValue()).as("summary " + status).isEqualTo(expected.getOrDefault(status, 0L));
        }
        for (String status : List.of("WAITING_DOCUMENTS", "WAITING_OTHER", "LATE")) {
            assertThat(byStatus.get(status).longValue()).as("summary " + status).isZero();
        }
        assertThat(byStatus).doesNotContainKeys("OPEN", "DONE");
        // Open tasks per assignee: NEW is "open", every other open state is "in progress"; completed and cancelled are not counted.
        long aOpen = LEGACY.stream().filter(t -> t.assignee().equals(ASSIGNEE_A) && t.word().equals("OPEN")).count();
        long aOverdue = LEGACY.stream().filter(t -> t.assignee().equals(ASSIGNEE_A) && t.word().equals("OPEN") && t.due().isBefore(TODAY)).count();
        long bInProgress = LEGACY.stream().filter(t -> t.assignee().equals(ASSIGNEE_B) && t.word().equals("IN_PROGRESS")).count();
        List<Map<String, Object>> rows = JsonPath.read(body, "$.items");
        Map<String, Object> a = rows.stream().filter(r -> USERS.get(ASSIGNEE_A).toString().equals(r.get("assigneeId"))).findFirst().orElseThrow();
        Map<String, Object> b = rows.stream().filter(r -> USERS.get(ASSIGNEE_B).toString().equals(r.get("assigneeId"))).findFirst().orElseThrow();
        assertThat(((Number) a.get("open")).longValue()).isEqualTo(aOpen);
        assertThat(((Number) a.get("overdue")).longValue()).isEqualTo(aOverdue);
        assertThat(((Number) b.get("inProgress")).longValue()).isEqualTo(bInProgress);
        assertThat(((Number) b.get("open")).longValue()).isZero();
        // The export (CSV) carries the same row as the screen.
        String csv = mvc.perform(get("/api/v1/reports/workload/export?format=csv").with(manager())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        String line = csv.lines().filter(l -> l.contains("Legacy " + ASSIGNEE_A)).findFirst().orElseThrow();
        String[] fields = line.replace("\"", "").split(",");
        assertThat(fields[fields.length - 3]).isEqualTo(String.valueOf(aOpen));
        assertThat(fields[fields.length - 1]).isEqualTo(String.valueOf(aOverdue));
    }

    @Test
    @Order(6)
    void theIndicatorsCountOldOpenTasksAsOverdue() throws Exception {
        String figures = json(as(Permission.REPORT_VIEW, Permission.REPORT_EXPORT, Permission.TASK_VIEW, Permission.CASE_VIEW),
                "/api/v1/reports/indicators");
        List<Number> overdue = JsonPath.read(figures, "$[?(@.code == 'OVERDUE_TASKS')].count");
        assertThat(overdue).hasSize(1);
        assertThat(overdue.get(0).longValue()).isEqualTo(LEGACY.stream()
                .filter(t -> (t.word().equals("OPEN") || t.word().equals("IN_PROGRESS")) && t.due().isBefore(TODAY)).count());
        String list = json(as(Permission.REPORT_VIEW, Permission.REPORT_EXPORT, Permission.TASK_VIEW, Permission.CASE_VIEW),
                "/api/v1/reports/indicators/OVERDUE_TASKS?size=100");
        List<String> listed = JsonPath.read(list, "$.items[*].title");
        assertThat(new TreeSet<>(listed)).containsAll(Set.of("t-overdue", "w-late")).doesNotContain("t-done", "t-cancelled");
        mvc.perform(get("/api/v1/reports/indicators/OVERDUE_TASKS/export?format=csv")
                .with(as(Permission.REPORT_VIEW, Permission.REPORT_EXPORT, Permission.TASK_VIEW, Permission.CASE_VIEW))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/reports/dashboard").with(as(Permission.REPORT_VIEW, Permission.TASK_VIEW, Permission.CASE_VIEW)))
                .andExpect(status().isOk());
    }

    @Test
    @Order(7)
    void theCalendarListsOldTasksOnTheirDueDatesWithTheNewStatuses() throws Exception {
        RequestPostProcessor actor = as(Permission.CALENDAR_VIEW, Permission.TASK_VIEW, Permission.CASE_VIEW);
        String events = json(actor, "/api/v1/calendar/events?from=" + TODAY.minusDays(30) + "&to=" + TODAY.plusDays(30) + "&type=TASK");
        List<Map<String, Object>> list = JsonPath.read(events, "$.events");
        Map<String, String> statusByTask = new TreeMap<>();
        for (Map<String, Object> event : list) {
            for (Map.Entry<String, UUID> task : TASKS.entrySet()) {
                if (task.getValue().toString().equals(event.get("refId"))) {
                    statusByTask.put(task.getKey(), (String) event.get("status"));
                }
            }
        }
        assertThat(statusByTask.keySet()).as("every dated legacy task is on the calendar").isEqualTo(new TreeSet<>(TASKS.keySet()));
        for (Legacy legacy : LEGACY) {
            assertThat(statusByTask.get(legacy.title())).as(legacy.title()).isEqualTo(expectedNew(legacy.word()));
        }
        // The month view of the due date of an old task answers as well.
        mvc.perform(get("/api/v1/calendar/month?month=" + TODAY.plusDays(10).toString().substring(0, 7)).with(actor)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/calendar/day?date=" + TODAY.plusDays(10)).with(actor)).andExpect(status().isOk());
    }

    @Test
    @Order(8)
    void theMapTakesEveryMigratedRowBackToTheWordThePreviousReleaseStoredAndRoundTrips() {
        Map<String, String> fromMap = new TreeMap<>();
        for (Map.Entry<String, UUID> task : TASKS.entrySet()) {
            fromMap.put(task.getKey(), jdbc.queryForObject(
                    "select m.legacy_status from task t join task_status_map m on m.status = t.status where t.id = ?", String.class, task.getValue()));
        }
        for (Legacy legacy : LEGACY) {
            assertThat(fromMap.get(legacy.title())).as("reverse of " + legacy.title()).isEqualTo(legacy.word());
        }
        // Forward then back: each exact new status maps to a legacy word that maps forward to the same status.
        for (Map<String, Object> row : jdbc.queryForList("select status, legacy_status from task_status_map where exact")) {
            assertThat(expectedNew((String) row.get("legacy_status"))).isEqualTo(row.get("status"));
        }
        // The statuses that did not exist before degrade to a legacy word that keeps the work open or in progress (never to a closed word).
        for (Map<String, Object> row : jdbc.queryForList("select status, legacy_status from task_status_map where not exact")) {
            assertThat((String) row.get("legacy_status")).isIn("OPEN", "IN_PROGRESS");
        }
    }

    // ---- writers and history -------------------------------------------------------------------------------------------------------

    private MvcResult change(UUID id, long version, String word) throws Exception {
        return mvc.perform(post("/api/v1/tasks/" + id + "/status").with(manager()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + word + "\",\"version\":" + version + "}")).andReturn();
    }

    private long version(UUID id) {
        return jdbc.queryForObject("select version from task where id = ?", Long.class, id);
    }

    @Test
    @Order(10)
    void aClientThatStillSendsTheOldWordsIsUnderstoodAndHistoryUsesTheNewNames() throws Exception {
        UUID start = TASKS.get("w-start");
        UUID finish = TASKS.get("w-finish");
        UUID reopen = TASKS.get("w-reopen");
        // OPEN -> IN_PROGRESS, then IN_PROGRESS -> "OPEN" (back to NEW).
        assertThat(change(start, version(start), "IN_PROGRESS").getResponse().getStatus()).isEqualTo(200);
        assertThat(jdbc.queryForObject("select status from task where id = ?", String.class, start)).isEqualTo("IN_PROGRESS");
        assertThat(change(start, version(start), "OPEN").getResponse().getStatus()).isEqualTo(200);
        assertThat(jdbc.queryForObject("select status from task where id = ?", String.class, start)).isEqualTo("NEW");
        // OPEN -> "DONE".
        MvcResult done = change(finish, version(finish), "DONE");
        assertThat(done.getResponse().getStatus()).isEqualTo(200);
        assertThat((String) JsonPath.read(done.getResponse().getContentAsString(), "$.status")).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("select completed_at is not null from task where id = ?", Boolean.class, finish)).isTrue();
        // A task that was DONE is reopened with the old word.
        MvcResult reopened = change(reopen, version(reopen), "OPEN");
        assertThat(reopened.getResponse().getStatus()).isEqualTo(200);
        assertThat(jdbc.queryForObject("select status || '/' || (completed_at is null) from task where id = ?", String.class, reopen)).isEqualTo("NEW/true");
        // History records the new names, even for a row that held the old word before the migration.
        List<Map<String, Object>> history = JsonPath.read(json(manager(), "/api/v1/tasks/" + start + "/history"), "$");
        assertThat(history).extracting(h -> h.get("action") + ":" + h.get("oldValue") + ">" + h.get("newValue"))
                .containsExactly("STATUS:NEW>IN_PROGRESS", "STATUS:IN_PROGRESS>NEW");
        List<Map<String, Object>> finished = JsonPath.read(json(manager(), "/api/v1/tasks/" + finish + "/history"), "$");
        assertThat(finished).extracting(h -> h.get("action") + ":" + h.get("oldValue") + ">" + h.get("newValue")).containsExactly("STATUS:NEW>COMPLETED");
        List<Map<String, Object>> reopenedHistory = JsonPath.read(json(manager(), "/api/v1/tasks/" + reopen + "/history"), "$");
        assertThat(reopenedHistory).extracting(h -> h.get("action") + ":" + h.get("oldValue") + ">" + h.get("newValue")).containsExactly("STATUS:COMPLETED>NEW");
        assertThat(jdbc.queryForObject("select count(*) from task_history where old_value in ('OPEN', 'DONE') or new_value in ('OPEN', 'DONE')", Integer.class)).isZero();
        // A statement written the way the previous release wrote it is stored under the new name.
        UUID direct = jdbc.queryForObject("""
                insert into task (case_id, title, assignee_id, status, created_by, updated_by)
                values (?, 'legacy-writer', ?, 'OPEN', ?, ?) returning id""", UUID.class, CASES.get("C1"), USERS.get(ASSIGNEE_A), USERS.get(MEMBER), USERS.get(MEMBER));
        assertThat(jdbc.queryForObject("select status from task where id = ?", String.class, direct)).isEqualTo("NEW");
        jdbc.update("update task set status = 'DONE', completed_at = now() where id = ?", direct);
        assertThat(jdbc.queryForObject("select status from task where id = ?", String.class, direct)).isEqualTo("COMPLETED");
        jdbc.update("delete from task where id = ?", direct);
    }

    @Test
    @Order(99)
    void theFollowUpRunMarksOldOpenTasksLateKeepingTheOldMeaningAndNotifiesTheAssignee() throws Exception {
        FollowUpRun.Result result = followUp.run();
        // Overdue work of the old OPEN word, plus w-reopen: a DONE task reopened by the writer test above, whose date has passed.
        assertThat(result.lateTasks()).isEqualTo(
                (int) LEGACY.stream().filter(t -> t.word().equals("OPEN") && t.due().isBefore(TODAY)).count() + 1);
        UUID late = TASKS.get("w-late");
        assertThat(jdbc.queryForMap("select status, status_before_late from task where id = ?", late))
                .containsEntry("status", "LATE").containsEntry("status_before_late", "NEW");
        // Completed and cancelled tasks are never marked late, even long past their date.
        assertThat(jdbc.queryForObject("select status from task where id = ?", String.class, TASKS.get("t-done"))).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("select status from task where id = ?", String.class, TASKS.get("t-c2-done"))).isEqualTo("COMPLETED");
        // The system's history entry names the new status the row had, not the old word.
        List<Map<String, Object>> history = JsonPath.read(json(manager(), "/api/v1/tasks/" + late + "/history"), "$");
        assertThat(history).extracting(h -> h.get("action") + ":" + h.get("oldValue") + ">" + h.get("newValue")).containsExactly("SYSTEM_LATE:NEW>LATE");
        // The legacy meaning is kept by the map: a late task was open work.
        assertThat(jdbc.queryForObject("select m.legacy_status from task t join task_status_map m on m.status = t.status where t.id = ?", String.class, late))
                .isEqualTo("OPEN");
        // The filter with the old word finds late tasks under neither OPEN nor DONE; the new name finds them.
        assertThat(titles(json(manager(), "/api/v1/tasks?size=100&status=LATE"))).isEqualTo(new TreeSet<>(Set.of("t-overdue", "w-late", "w-reopen")));
        assertThat(titles(json(manager(), "/api/v1/tasks?size=100&status=OPEN"))).doesNotContain("t-overdue", "w-late", "w-reopen");
        // The assignee is notified, and the case stays open to her.
        String notifications = json(user(ASSIGNEE_A, Permission.CASE_VIEW, Permission.TASK_VIEW), "/api/v1/notifications?size=100&type=TASK_OVERDUE");
        List<String> refs = JsonPath.read(notifications, "$.items[*].recordRef");
        assertThat(refs).contains("TASK:" + late, "TASK:" + TASKS.get("t-overdue"));
        assertThat(jdbc.queryForObject("select case_visible(?, ?, false, '', '')", Boolean.class, CASES.get("C1"), USERS.get(ASSIGNEE_A))).isTrue();
        // Back to work: a late task moves on to a real status, and its remembered status is cleared.
        MvcResult resumed = change(late, version(late), "IN_PROGRESS");
        assertThat(resumed.getResponse().getStatus()).isEqualTo(200);
        assertThat(jdbc.queryForMap("select status, status_before_late from task where id = ?", late)).containsEntry("status", "IN_PROGRESS")
                .containsEntry("status_before_late", null);
    }
}
