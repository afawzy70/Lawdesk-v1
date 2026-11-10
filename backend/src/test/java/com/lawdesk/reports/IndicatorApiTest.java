package com.lawdesk.reports;

import static com.lawdesk.security.TestActors.as;
import static com.lawdesk.security.TestActors.scoped;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.ArrayList;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.lawdesk.platform.PostgresTestSupport;
import com.lawdesk.security.Permission;

/**
 * LD-058 (requirements section 26): the 14 dashboard indicators. The central promise is tested for every indicator and
 * every group, for three different users: the figure on the dashboard equals the total of the list its click opens, and no
 * figure or list includes a case the user may not see. Other test classes may leave rows, so exact values are checked as
 * the change from a baseline read before this test's own rows exist.
 */
@SpringBootTest
@AutoConfigureMockMvc
class IndicatorApiTest extends PostgresTestSupport {

    private static final LocalDate TODAY = ReportCalendar.ZONE == null ? null : LocalDate.now(ReportCalendar.ZONE);

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private ReportTestData data;
    private WpTestRows rows;
    private UUID staff;
    private UUID manager;

    @BeforeEach
    void setUp() {
        data = new ReportTestData(jdbc);
        rows = new WpTestRows(jdbc, data);
        staff = data.insertUser("Staff Member");
        manager = data.insertUser("Manager Member");
    }

    @AfterEach
    void removeRows() {
        data.deleteCreated();
    }

    private RequestPostProcessor department() {
        return as(Permission.REPORT_VIEW, Permission.REPORT_EXPORT);
    }

    private RequestPostProcessor member(UUID id) {
        return scoped(id, "m" + id.toString().substring(0, 6), List.of(), Permission.REPORT_VIEW, Permission.REPORT_EXPORT);
    }

    private RequestPostProcessor outsider() {
        return scoped(UUID.randomUUID(), "outsider", List.of(), Permission.REPORT_VIEW, Permission.REPORT_EXPORT);
    }

    private DocumentContext json(RequestPostProcessor user, String url) throws Exception {
        return JsonPath.parse(mvc.perform(get(url).with(user)).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString());
    }

    private Map<String, Long> figures(RequestPostProcessor user) throws Exception {
        DocumentContext figures = json(user, "/api/v1/reports/indicators");
        Map<String, Long> out = new java.util.LinkedHashMap<>();
        List<Map<String, Object>> list = figures.read("$");
        for (Map<String, Object> figure : list) {
            Object count = figure.get("count");
            out.put((String) figure.get("code"), count == null ? null : ((Number) count).longValue());
        }
        return out;
    }

    private Map<String, Long> delta(Map<String, Long> before, Map<String, Long> after) {
        Map<String, Long> changed = new java.util.LinkedHashMap<>();
        after.forEach((code, value) -> {
            if (value != null && !value.equals(before.get(code))) {
                changed.put(code, value - before.get(code));
            }
        });
        return changed;
    }

    /** A varied set of rows: every indicator has something in it, plus a restricted case nobody but its members sees. */
    private Map<String, UUID> seed() {
        UUID court = data.insertCourt("FIRST_INSTANCE");
        Map<String, UUID> ids = new java.util.LinkedHashMap<>();
        UUID fresh = data.insertCase("new", "CIVIL", "OPEN", TODAY.minusDays(3));
        UUID ongoing = data.insertCase("ongoing", "COMMERCIAL", "ACTIVE", TODAY.minusDays(40));
        UUID suspended = data.insertCase("suspended", "CIVIL", "SUSPENDED", TODAY.minusDays(80));
        UUID closed = data.insertCase("closed", "LABOR", "CLOSED", TODAY.minusDays(400));
        UUID archived = data.insertCase("archived", "FAMILY", "ARCHIVED", TODAY.minusDays(800));
        UUID hidden = data.insertCase("hidden", "CIVIL", "ACTIVE", TODAY.minusDays(10));
        ids.put("fresh", fresh);
        ids.put("ongoing", ongoing);
        ids.put("suspended", suspended);
        ids.put("closed", closed);
        ids.put("archived", archived);
        ids.put("hidden", hidden);
        for (UUID id : List.of(fresh, ongoing, suspended, closed, archived)) {
            rows.primaryMember(id, staff);
        }
        rows.primaryMember(hidden, manager);
        rows.restricted(hidden);
        rows.involvement(fresh, "Sector One", "Dept A", "Section X");
        rows.involvement(ongoing, "Sector One", "Dept B", null);
        rows.involvement(suspended, "Sector Two", "Dept A", "Section X");
        // reserved for judgment: a proceeding without a judgment and a pronouncement hearing that was not cancelled
        UUID reserved = data.insertProceeding(ongoing, "FIRST_INSTANCE", court, TODAY.minusDays(39), null);
        rows.hearing(ongoing, reserved, TODAY.minusDays(1), "HELD", "JUDGMENT", "COURT", null, staff);
        // a judged proceeding with a pronouncement hearing is not reserved any more
        UUID judged = data.insertProceeding(suspended, "FIRST_INSTANCE", court, TODAY.minusDays(70), null);
        rows.hearing(suspended, judged, TODAY.minusDays(5), "HELD", "JUDGMENT", "COURT", null, staff);
        data.insertJudgment(judged, TODAY, "IN_FAVOUR", false);
        UUID upcomingProc = data.insertProceeding(fresh, "FIRST_INSTANCE", court, TODAY.minusDays(2), null);
        rows.hearing(fresh, upcomingProc, TODAY.plusDays(2), "SCHEDULED", "PLEADING", "COURT", null, staff);
        rows.hearing(fresh, upcomingProc, TODAY.plusDays(3), "SCHEDULED", "EVIDENCE", "EXPERT", null, staff);
        rows.hearing(fresh, upcomingProc, TODAY.plusDays(30), "SCHEDULED", "PLEADING", "COURT", null, staff);
        rows.hearing(hidden, data.insertProceeding(hidden, "FIRST_INSTANCE", court, TODAY.minusDays(9), null),
                TODAY.plusDays(1), "SCHEDULED", "PLEADING", "EXPERT", null, manager);
        data.insertTask(ongoing, staff, "overdue task", TODAY.minusDays(2), "OPEN");
        data.insertTask(ongoing, staff, "done task", TODAY.minusDays(2), "DONE");
        data.insertTask(ongoing, staff, "future task", TODAY.plusDays(9), "OPEN");
        data.insertTask(hidden, manager, "hidden overdue", TODAY.minusDays(2), "OPEN");
        rows.risk(ongoing, 1, "HIGH", "1000.000");
        rows.risk(ongoing, 2, "LOW", "10.000");
        rows.risk(suspended, 1, "VERY_HIGH", "2500.500");
        rows.risk(hidden, 1, "HIGH", "9.000");
        rows.link(ongoing, suspended, "SUBJECT_LINK", "ACTIVE");
        rows.link(fresh, closed, "JOINDER_REQUEST", "REQUESTED");
        rows.infoRequest(ongoing, "AWAITING", TODAY.minusDays(5));
        rows.infoRequest(ongoing, "COMPLETE", TODAY.minusDays(5));
        rows.infoRequest(ongoing, "SENT", TODAY.plusDays(5));
        rows.infoRequest(hidden, "AWAITING", TODAY.minusDays(5));
        return ids;
    }

    @Test
    void sixteenCodesCoverTheFourteenIndicators() throws Exception {
        DocumentContext figures = json(department(), "/api/v1/reports/indicators");
        List<Integer> numbers = figures.read("$[*].number");
        assertThat(numbers).containsExactly(1, 2, 2, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14);
        assertThat(numbers.stream().distinct().count()).isEqualTo(14);
    }

    @Test
    void theDashboardCarriesTheSameFiguresAsTheIndicatorEndpoint() throws Exception {
        seed();
        DocumentContext dashboard = json(department(), "/api/v1/reports/dashboard");
        assertThat(dashboard.<List<Object>>read("$.indicators")).isEqualTo(
                json(department(), "/api/v1/reports/indicators").<List<Object>>read("$"));
    }

    @Test
    void everyIndicatorFigureEqualsTheTotalOfItsListForManagerStaffAndAnOutsider() throws Exception {
        seed();
        assertFiguresEqualLists(List.of(department(), member(staff), outsider()));
    }

    /**
     * LD-DOC-R3: the same promise for the other ways a case becomes visible (case-type scope, organisational-unit scope,
     * an explicit grant on a RESTRICTED case, a task on a case, the creator, the alternate member) and for the cases a
     * scope must never add (RESTRICTED ones). Figures, dashboard and lists are read through the same query code, so a
     * scope on which they disagree shows here.
     */
    @Test
    void figuresAndDashboardEqualTheirListsForTypeUnitGrantTaskCreatorAndAlternateScopes() throws Exception {
        UUID grantee = data.insertUser("Grantee");
        UUID alternate = data.insertUser("Alternate");
        UUID creator = data.insertUser("Creator");
        UUID unit = data.track("org_unit", jdbc.queryForObject("""
                insert into org_unit (tree, parent_id, level, name_ar, created_by, updated_by)
                values ('LEGAL', null, 'DEPARTMENT', 'LD-DOC-R3 unit', gen_random_uuid(), gen_random_uuid()) returning id""",
                UUID.class));
        Map<String, UUID> ids = seed();
        jdbc.update("update legal_case set owning_unit_id = ? where id in (?, ?)", unit, ids.get("fresh"), ids.get("closed"));
        data.track("case_grant", jdbc.queryForObject("insert into case_grant (case_id, user_id, level, reason, granted_by)"
                + " values (?, ?, 'VIEW', 'second opinion', gen_random_uuid()) returning id", UUID.class, ids.get("hidden"), grantee));
        rows.alternateMember(ids.get("closed"), alternate);
        jdbc.update("update legal_case set created_by = ? where id = ?", creator, ids.get("archived"));
        Permission[] p = {Permission.REPORT_VIEW, Permission.REPORT_EXPORT, Permission.CASE_VIEW};
        List<RequestPostProcessor> users = List.of(
                scoped(UUID.randomUUID(), "typeScope", List.of("TYPE:CIVIL"), p),
                scoped(UUID.randomUUID(), "unitScope", List.of("UNIT:" + unit), p),
                scoped(grantee, "grantee", List.of(), p),
                scoped(staff, "taskAssignee", List.of(), p),
                scoped(creator, "creator", List.of(), p),
                scoped(alternate, "alternate", List.of(), p),
                scoped(manager, "restrictedMember", List.of(), p),
                scoped(UUID.randomUUID(), "all", List.of("ALL"), p));
        assertFiguresEqualLists(users);
        for (RequestPostProcessor user : users) {
            DocumentContext dashboard = json(user, "/api/v1/reports/dashboard");
            Map<String, Long> figures = figures(user);
            assertThat(dashboard.<Number>read("$.cases.total").longValue()).isEqualTo(figures.get("TOTAL_CASES"));
            assertThat(dashboard.<Number>read("$.operations.hearingsThisWeek").longValue())
                    .isEqualTo(figures.get("UPCOMING_HEARINGS"));
            assertThat(dashboard.<Number>read("$.operations.overdueTasks").longValue()).isEqualTo(figures.get("OVERDUE_TASKS"));
            assertThat(json(user, "/api/v1/reports/indicators/UPCOMING_HEARINGS").<Number>read("$.totalItems").longValue())
                    .isEqualTo(figures.get("UPCOMING_HEARINGS"));
            assertThat(json(user, "/api/v1/reports/indicators/OVERDUE_TASKS").<Number>read("$.totalItems").longValue())
                    .isEqualTo(figures.get("OVERDUE_TASKS"));
            assertThat(json(user, "/api/v1/cases?size=1").<Number>read("$.totalItems").longValue())
                    .isEqualTo(figures.get("TOTAL_CASES"));
        }
        // the RESTRICTED case reaches its member and its grantee only, never a scope
        for (int i : List.of(2, 6)) {
            assertThat(json(users.get(i), "/api/v1/cases?size=50").<List<String>>read("$.items[*].title")).contains("hidden");
        }
        for (int i : List.of(0, 1, 7)) {
            assertThat(json(users.get(i), "/api/v1/cases?size=50").<List<String>>read("$.items[*].title"))
                    .doesNotContain("hidden");
        }
    }

    private void assertFiguresEqualLists(List<RequestPostProcessor> users) throws Exception {
        for (RequestPostProcessor user : users) {
            DocumentContext figures = json(user, "/api/v1/reports/indicators");
            List<Map<String, Object>> list = figures.read("$");
            assertThat(list).hasSize(16);
            for (Map<String, Object> figure : list) {
                String code = (String) figure.get("code");
                if (Boolean.FALSE.equals(figure.get("available"))) {
                    DocumentContext page = json(user, "/api/v1/reports/indicators/" + code);
                    assertThat(page.<Integer>read("$.totalItems")).isZero();
                    assertThat(page.<Boolean>read("$.available")).isFalse();
                    continue;
                }
                long count = ((Number) figure.get("count")).longValue();
                DocumentContext page = json(user, "/api/v1/reports/indicators/" + code + "?size=100");
                assertThat(page.<Number>read("$.totalItems").longValue()).as(code).isEqualTo(count);
                assertThat(page.<List<Object>>read("$.items").size()).as(code + " rows").isEqualTo((int) Math.min(count, 100));
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> groups = (List<Map<String, Object>>) figure.get("groups");
                if (groups != null) {
                    long perLevel = 0;
                    String currentLevel = null;
                    for (Map<String, Object> group : groups) {
                        String level = (String) group.get("level");
                        if (!java.util.Objects.equals(level, currentLevel)) {
                            if (currentLevel != null || perLevel != 0) {
                                assertThat(perLevel).as(code + " groups of " + currentLevel + " add up").isEqualTo(count);
                            }
                            currentLevel = level;
                            perLevel = 0;
                        }
                        long groupCount = ((Number) group.get("count")).longValue();
                        perLevel += groupCount;
                        var request = get("/api/v1/reports/indicators/" + code).with(user)
                                .param("group", (String) group.get("key"));
                        if (level != null) {
                            request = request.param("level", level);
                        }
                        assertThat(JsonPath.parse(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse()
                                .getContentAsString()).<Number>read("$.totalItems").longValue())
                                .as(code + " group " + group.get("key")).isEqualTo(groupCount);
                    }
                    assertThat(perLevel).as(code + " last level adds up").isEqualTo(count);
                }
            }
        }
    }

    @Test
    void definitionsCountExactlyWhatTheyShouldAndNeverTheHiddenCase() throws Exception {
        Map<String, Long> department = figures(department());
        Map<String, Long> staffBefore = figures(member(staff));
        Map<String, UUID> ids = seed();

        Map<String, Long> change = delta(department, figures(department()));
        // 5 visible cases + the restricted one is invisible to the department scope too
        assertThat(change.get("TOTAL_CASES")).isEqualTo(5);
        assertThat(change.get("CASES_NEW")).isEqualTo(1);
        assertThat(change.get("CASES_ONGOING")).isEqualTo(2);
        assertThat(change.get("CASES_CONCLUDED")).isEqualTo(2);
        assertThat(change.get("RESERVED_FOR_JUDGMENT")).isEqualTo(1);
        assertThat(change.get("JUDGMENTS_ISSUED")).isEqualTo(1);
        assertThat(change.get("UPCOMING_HEARINGS")).isEqualTo(2);
        assertThat(change.get("EXPERT_HEARINGS")).isEqualTo(1);
        assertThat(change.get("OVERDUE_TASKS")).isEqualTo(1);
        assertThat(change.get("HIGH_RISK_CASES")).as("latest version only: the LOW v2 supersedes HIGH v1").isEqualTo(1);
        assertThat(change.get("LINKED_CASES")).as("only the ACTIVE link; both of its cases").isEqualTo(2);
        assertThat(change.get("OVERDUE_INFO_REQUESTS")).isEqualTo(1);
        assertThat(change.get("CASES_BY_UNIT")).isEqualTo(5);
        assertThat(change.get("CASES_BY_MEMBER")).isEqualTo(5);

        Map<String, Long> staffChange = delta(staffBefore, figures(member(staff)));
        assertThat(staffChange).isEqualTo(change);

        // the manager member sees exactly the restricted case they are primary member of
        Map<String, Long> managerFigures = figures(member(manager));
        assertThat(managerFigures.get("TOTAL_CASES")).isGreaterThanOrEqualTo(1);
        DocumentContext list = json(member(manager), "/api/v1/reports/indicators/TOTAL_CASES?size=100");
        assertThat(list.<List<String>>read("$.items[*].caseId")).contains(ids.get("hidden").toString());
        assertThat(json(department(), "/api/v1/reports/indicators/TOTAL_CASES?size=100")
                .<List<String>>read("$.items[*].caseId")).doesNotContain(ids.get("hidden").toString());
    }

    @Test
    void anOutsiderSeesZeroOnEveryAvailableIndicatorAndNoGroups() throws Exception {
        seed();
        DocumentContext figures = json(outsider(), "/api/v1/reports/indicators");
        List<Map<String, Object>> list = figures.read("$");
        for (Map<String, Object> figure : list) {
            if (Boolean.TRUE.equals(figure.get("available"))) {
                assertThat(((Number) figure.get("count")).longValue()).as((String) figure.get("code")).isZero();
            }
            if (figure.get("groups") != null) {
                assertThat((List<?>) figure.get("groups")).isEmpty();
            }
        }
    }

    @Test
    void groupsByUnitAndMemberFollowThePrimaryInvolvementAndMember() throws Exception {
        seed();
        DocumentContext figures = json(member(staff), "/api/v1/reports/indicators");
        List<Map<String, Object>> byUnit = figures.read("$[?(@.code == 'CASES_BY_UNIT')].groups[?(@.level == 'SECTOR')]");
        Map<String, Integer> sectors = new java.util.HashMap<>();
        for (Map<String, Object> g : byUnit) {
            sectors.put((String) g.get("key"), ((Number) g.get("count")).intValue());
        }
        assertThat(sectors).containsEntry("Sector One", 2).containsEntry("Sector Two", 1).containsEntry("_none", 2);
        List<Map<String, Object>> byMember = figures.read("$[?(@.code == 'CASES_BY_MEMBER')].groups[*]");
        assertThat(byMember).hasSize(1);
        assertThat(byMember.get(0).get("key")).isEqualTo(staff.toString());
        assertThat(byMember.get(0).get("label")).isEqualTo("Staff Member");
        // the department that has no section recorded is grouped as not specified, not guessed
        DocumentContext sections = json(member(staff), "/api/v1/reports/indicators/CASES_BY_UNIT?level=SECTION&group=_none");
        assertThat(sections.<Integer>read("$.totalItems")).isEqualTo(3);
    }

    @Test
    void aMalformedMemberGroupIsRefusedWith400AndAnUnknownCodeToo() throws Exception {
        mvc.perform(get("/api/v1/reports/indicators/CASES_BY_MEMBER?group=not-a-uuid").with(department()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/reports/indicators/NOPE").with(department())).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/reports/indicators/TOTAL_CASES?page=-1").with(department()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void needsReportViewAndExportNeedsReportExport() throws Exception {
        mvc.perform(get("/api/v1/reports/indicators").with(as(Permission.CASE_VIEW))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/reports/indicators/TOTAL_CASES/export?format=csv").with(as(Permission.REPORT_VIEW)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/reports/indicators")).andExpect(status().isUnauthorized());
    }

    @Test
    void indicatorsOfModulesNotInstalledAreMarkedUnavailableNotZero() throws Exception {
        boolean deadline = jdbc.queryForObject("select to_regclass('deadline') is not null", Boolean.class);
        boolean enforcement = jdbc.queryForObject("select to_regclass('enforcement_file') is not null", Boolean.class);
        DocumentContext figures = json(department(), "/api/v1/reports/indicators");
        List<Boolean> appeal = figures.read("$[?(@.code == 'APPEAL_DEADLINES')].available");
        List<Boolean> enforced = figures.read("$[?(@.code == 'JUDGMENTS_IN_ENFORCEMENT')].available");
        assertThat(appeal).containsExactly(deadline);
        assertThat(enforced).containsExactly(enforcement);
        if (!deadline) {
            assertThat(figures.<List<Object>>read("$[?(@.code == 'APPEAL_DEADLINES')].count").get(0)).isNull();
            assertThat(figures.<List<String>>read("$[?(@.code == 'APPEAL_DEADLINES')].unavailableReason"))
                    .containsExactly("MODULE_NOT_AVAILABLE");
            mvc.perform(get("/api/v1/reports/indicators/APPEAL_DEADLINES/export?format=csv").with(department()))
                    .andExpect(status().isUnprocessableEntity());
        }
    }

    @Test
    void exportsHaveTheSameRowsAsTheListInCsvXlsxAndPdf() throws Exception {
        seed();
        int total = json(department(), "/api/v1/reports/indicators/CASES_ONGOING").read("$.totalItems");
        String csv = mvc.perform(get("/api/v1/reports/indicators/CASES_ONGOING/export?format=csv&lang=en").with(department()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(csv.lines().count()).isEqualTo(total + 1L);
        byte[] xlsx = mvc.perform(get("/api/v1/reports/indicators/CASES_ONGOING/export?format=xlsx&lang=ar").with(department()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(xlsx).startsWith((byte) 'P', (byte) 'K');
        byte[] pdf = mvc.perform(get("/api/v1/reports/indicators/OVERDUE_TASKS/export?format=pdf&lang=en").with(department()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        // the outsider's export is empty: header only
        String empty = mvc.perform(get("/api/v1/reports/indicators/TOTAL_CASES/export?format=csv").with(outsider()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(empty.lines().count()).isEqualTo(1);
    }

    @Test
    void theJudgmentPeriodChangesBothTheFigureAndItsList() throws Exception {
        seed();
        String year = "?from=" + TODAY.minusDays(1) + "&to=" + TODAY.plusDays(1);
        long figure = ((Number) json(department(), "/api/v1/reports/indicators" + year)
                .<List<Object>>read("$[?(@.code == 'JUDGMENTS_ISSUED')].count").get(0)).longValue();
        long list = json(department(), "/api/v1/reports/indicators/JUDGMENTS_ISSUED" + year).<Number>read("$.totalItems").longValue();
        assertThat(figure).isEqualTo(list);
        mvc.perform(get("/api/v1/reports/indicators/JUDGMENTS_ISSUED?from=2031-02-01&to=2031-01-01").with(department()))
                .andExpect(status().isUnprocessableEntity());
        List<String> codes = new ArrayList<>();
        assertThat(codes).isEmpty();
    }

    @Test
    void appealDeadlinesAndEnforcementCountTheirRowsOnceTheirModulesAreInstalled() throws Exception {
        boolean deadline = jdbc.queryForObject("select to_regclass('deadline') is not null", Boolean.class);
        boolean enforcement = jdbc.queryForObject("select to_regclass('enforcement_file') is not null", Boolean.class);
        org.junit.jupiter.api.Assumptions.assumeTrue(deadline && enforcement);
        Map<String, Long> before = figures(department());
        UUID court = data.insertCourt("FIRST_INSTANCE");
        UUID visible = data.insertCase("judged", "CIVIL", "ACTIVE", TODAY.minusDays(30));
        UUID hidden = data.insertCase("judged hidden", "CIVIL", "ACTIVE", TODAY.minusDays(30));
        rows.restricted(hidden);
        UUID judgment = data.insertJudgment(data.insertProceeding(visible, "FIRST_INSTANCE", court, TODAY.minusDays(29), null),
                TODAY.minusDays(1), "AGAINST", false);
        UUID hiddenJudgment = data.insertJudgment(data.insertProceeding(hidden, "FIRST_INSTANCE", court, TODAY.minusDays(29), null),
                TODAY.minusDays(1), "AGAINST", false);
        for (UUID[] pair : new UUID[][] {{visible, judgment}, {hidden, hiddenJudgment}}) {
            data.track("deadline", jdbc.queryForObject("""
                    insert into deadline (case_id, source_type, source_id, due_on, action, status, calc_status, created_by, updated_by)
                    values (?, 'JUDGMENT', ?, ?, 'appeal', 'OPEN', 'MANUAL', ?, ?) returning id""", UUID.class, pair[0], pair[1],
                    TODAY.plusDays(30), ReportTestData.CREATOR, ReportTestData.CREATOR));
            data.track("enforcement_file", jdbc.queryForObject("""
                    insert into enforcement_file (judgment_id, case_id, status, created_by, updated_by)
                    values (?, ?, 'IN_PROGRESS', ?, ?) returning id""", UUID.class, pair[1], pair[0], ReportTestData.CREATOR,
                    ReportTestData.CREATOR));
        }
        // a completed deadline and a not-started file are not counted
        data.track("deadline", jdbc.queryForObject("""
                insert into deadline (case_id, source_type, source_id, due_on, action, status, completed_on, calc_status, created_by,
                    updated_by) values (?, 'JUDGMENT', ?, ?, 'done', 'COMPLETED', ?, 'MANUAL', ?, ?) returning id""", UUID.class,
                visible, judgment, TODAY.plusDays(30), TODAY, ReportTestData.CREATOR, ReportTestData.CREATOR));
        Map<String, Long> change = delta(before, figures(department()));
        assertThat(change.get("APPEAL_DEADLINES")).isEqualTo(1);
        assertThat(change.get("JUDGMENTS_IN_ENFORCEMENT")).isEqualTo(1);
        DocumentContext list = json(department(), "/api/v1/reports/indicators/APPEAL_DEADLINES?size=100");
        assertThat(list.<List<String>>read("$.items[*].caseId")).contains(visible.toString()).doesNotContain(hidden.toString());
        assertThat(json(outsider(), "/api/v1/reports/indicators/JUDGMENTS_IN_ENFORCEMENT").<Integer>read("$.totalItems")).isZero();
    }
}
