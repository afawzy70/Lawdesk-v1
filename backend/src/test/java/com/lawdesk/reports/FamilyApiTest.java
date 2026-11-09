package com.lawdesk.reports;

import static com.lawdesk.security.TestActors.as;
import static com.lawdesk.security.TestActors.scoped;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
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
 * LD-058 (requirements section 27): the seventeen report families. They are filterable and exportable, restricted to the
 * cases the user may see, and their money is correct: the five amounts of decision D5 stay in separate columns, each
 * claim or case is counted once (the latest judgment decides a claim), and KWD has three decimals.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FamilyApiTest extends PostgresTestSupport {

    private static final LocalDate TODAY = LocalDate.now(ReportCalendar.ZONE);
    private static final List<String> CODES = List.of("CASES_BY_TYPE_COURT_STAGE", "CASES_BY_ORG_UNIT", "CASES_BY_MEMBER",
            "OPEN_CLOSED_CASES", "JUDGMENT_OUTCOMES", "CLAIM_RESULTS", "DEFENSE_POSITIONS", "LINKED_CASES", "ADJOURNMENTS",
            "EXPERT_SESSIONS", "DEADLINES_APPEALS", "ENFORCEMENT", "MISSING_EVIDENCE", "INFO_REQUESTS", "RISK_ASSESSMENT",
            "PERFORMANCE", "LIBRARY_PRECEDENTS");

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private ReportTestData data;
    private WpTestRows rows;
    private UUID staff;
    private UUID manager;
    private UUID visible1;
    private UUID visible2;
    private UUID hidden;

    @BeforeEach
    void setUp() {
        data = new ReportTestData(jdbc);
        rows = new WpTestRows(jdbc, data);
        staff = data.insertUser("Bbb Staff");
        manager = data.insertUser("Aaa Manager");
    }

    @AfterEach
    void removeRows() {
        data.deleteCreated();
    }

    private RequestPostProcessor reader(UUID id) {
        return scoped(id, "r" + id.toString().substring(0, 6), List.of(), Permission.REPORT_VIEW, Permission.REPORT_EXPORT,
                Permission.LIBRARY_VIEW);
    }

    private RequestPostProcessor outsider() {
        return reader(UUID.randomUUID());
    }

    private DocumentContext family(RequestPostProcessor user, String code, String query) throws Exception {
        return JsonPath.parse(mvc.perform(get("/api/v1/reports/families/" + code + query).with(user))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @SuppressWarnings("unchecked")
    private static List<List<Object>> tableRows(DocumentContext table) {
        return (List<List<Object>>) (List<?>) table.read("$.rows");
    }

    private static List<Object> totalRow(DocumentContext table) {
        List<List<Object>> r = tableRows(table);
        return r.get(r.size() - 1);
    }

    /** Two visible cases of the staff member with different amounts, one restricted case of the manager with large ones. */
    private void seedCases() {
        UUID court = data.insertCourt("FIRST_INSTANCE");
        visible1 = data.insertCase("one", "CIVIL", "ACTIVE", TODAY.minusDays(30));
        visible2 = data.insertCase("two", "COMMERCIAL", "CLOSED", TODAY.minusDays(60));
        hidden = data.insertCase("hidden", "CIVIL", "ACTIVE", TODAY.minusDays(20));
        rows.primaryMember(visible1, staff);
        rows.primaryMember(visible2, staff);
        rows.primaryMember(hidden, manager);
        rows.restricted(hidden);
        rows.amounts(visible1, "1500.500", "200.250");
        rows.amounts(visible2, "2000.000", null);
        rows.amounts(hidden, "999999.999", "888888.888");
        data.insertProceeding(visible1, "FIRST_INSTANCE", court, TODAY.minusDays(29), null);
        data.insertProceeding(hidden, "FIRST_INSTANCE", court, TODAY.minusDays(19), null);
    }

    @Test
    void theCatalogueListsSeventeenFamiliesWithTheirRequirementLines() throws Exception {
        DocumentContext catalogue = JsonPath.parse(mvc.perform(get("/api/v1/reports/families").with(reader(staff)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(catalogue.<List<String>>read("$[*].code")).containsExactlyElementsOf(CODES);
        assertThat(catalogue.<List<Integer>>read("$[*].number")).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13,
                14, 15, 16, 17);
        assertThat(catalogue.<List<List<String>>>read("$[*].requirements").stream().flatMap(List::stream))
                .contains("LD-DOC-27-001", "LD-DOC-27-017", "LD-DOC-24-001", "LD-DOC-24-003");
    }

    @Test
    void everyFamilyAnswersWithRowsAsWideAsItsColumnsAndAnOutsiderSeesNothing() throws Exception {
        seedCases();
        for (String code : CODES) {
            for (RequestPostProcessor user : List.of(reader(staff), outsider())) {
                DocumentContext table = family(user, code, "?lang=en");
                assertThat(table.<String>read("$.family")).isEqualTo(code);
                int width = table.<List<Object>>read("$.columns").size();
                if (Boolean.FALSE.equals(table.read("$.available"))) {
                    assertThat(width).isZero();
                    continue;
                }
                for (List<Object> row : tableRows(table)) {
                    assertThat(row).as(code).hasSize(width);
                }
            }
            if (!"LIBRARY_PRECEDENTS".equals(code)) {
                DocumentContext table = family(outsider(), code, "");
                List<String> types = table.read("$.columns[*].type");
                for (List<Object> row : tableRows(table)) {
                    for (int c = 0; c < types.size(); c++) {
                        Object cell = row.get(c);
                        if ("COUNT".equals(types.get(c)) && cell != null) {
                            assertThat(((Number) cell).longValue()).as(code + " " + c).isZero();
                        }
                        if ("MONEY".equals(types.get(c))) {
                            assertThat(cell).as(code).isEqualTo("0.000");
                        }
                    }
                }
            }
        }
    }

    @Test
    void casesByTypeCourtAndStageCountEachCaseOnceAndKeepTheTwoAmountsApart() throws Exception {
        seedCases();
        for (String groupBy : List.of("CASE_TYPE", "COURT", "STAGE")) {
            DocumentContext table = family(reader(staff), "CASES_BY_TYPE_COURT_STAGE", "?groupBy=" + groupBy);
            assertThat(table.<List<String>>read("$.columns[*].key")).containsExactly("group", "cases", "claimedAmount",
                    "financialImpactAmount");
            List<Object> total = totalRow(table);
            assertThat(((Number) total.get(1)).longValue()).as(groupBy).isEqualTo(2);
            assertThat(total.get(2)).as(groupBy + " claimed").isEqualTo("3500.500");
            assertThat(total.get(3)).as(groupBy + " impact").isEqualTo("200.250");
        }
        DocumentContext none = family(reader(staff), "CASES_BY_TYPE_COURT_STAGE", "?groupBy=STAGE");
        assertThat(tableRows(none).stream().map(r -> r.get(0))).contains("No proceeding yet");
    }

    @Test
    void theHiddenCaseNeverReachesATotalAndMemberAndUnitGroupsAddUpToTheSameTotal() throws Exception {
        seedCases();
        rows.involvement(visible1, "Sector One", "Dept A", "Section X");
        for (String code : List.of("CASES_BY_MEMBER", "OPEN_CLOSED_CASES")) {
            DocumentContext table = family(reader(staff), code, "");
            assertThat(totalRow(table).get(2)).as(code).isEqualTo("3500.500");
        }
        for (String groupBy : List.of("SECTOR", "DEPARTMENT", "SECTION")) {
            DocumentContext table = family(reader(staff), "CASES_BY_ORG_UNIT", "?groupBy=" + groupBy);
            assertThat(totalRow(table).get(2)).as(groupBy).isEqualTo("3500.500");
            assertThat(tableRows(table).stream().map(r -> r.get(0))).contains("Not specified");
        }
        DocumentContext members = family(reader(staff), "CASES_BY_MEMBER", "");
        assertThat(tableRows(members).stream().map(r -> r.get(0))).contains("Bbb Staff").doesNotContain("Aaa Manager");
        // the manager's own view shows the restricted case, with its own amounts, and nothing of the others
        DocumentContext mine = family(reader(manager), "OPEN_CLOSED_CASES", "?groupBy=STATUS");
        assertThat(totalRow(mine).get(2)).isEqualTo("999999.999");
    }

    @Test
    void filtersNarrowTheCasesCounted() throws Exception {
        seedCases();
        assertThat(totalRow(family(reader(staff), "OPEN_CLOSED_CASES", "?statusGroup=CLOSED")).get(2)).isEqualTo("2000.000");
        assertThat(totalRow(family(reader(staff), "OPEN_CLOSED_CASES", "?caseType=CIVIL")).get(2)).isEqualTo("1500.500");
        assertThat(totalRow(family(reader(staff), "OPEN_CLOSED_CASES", "?from=" + TODAY.minusDays(45))).get(2))
                .isEqualTo("1500.500");
        assertThat(totalRow(family(reader(staff), "OPEN_CLOSED_CASES", "?memberId=" + manager)).get(2)).isEqualTo("0.000");
    }

    @Test
    void aClaimDecidedAtTwoLevelsIsCountedOnceAtItsLatestResultSoAmountsAreNotDoubled() throws Exception {
        seedCases();
        UUID court = data.insertCourt("APPEAL");
        UUID first = data.insertProceeding(visible1, "FIRST_INSTANCE", court, TODAY.minusDays(100), null);
        UUID appeal = data.insertProceeding(visible1, "APPEAL", court, TODAY.minusDays(50), first);
        UUID j1 = data.insertJudgment(first, TODAY.minusDays(80), "PARTIAL", false);
        UUID j2 = data.insertJudgment(appeal, TODAY.minusDays(10), "PARTIAL", true);
        UUID claim = rows.judgmentWithClaim(visible1, j1, "ACCEPTED", "100.000", 1);
        UUID claimId = jdbc.queryForObject("select claim_id from claim_result where id = ?", UUID.class, claim);
        rows.claimResult(claimId, j2, "PARTIAL", "80.000");
        rows.judgmentWithClaim(visible1, j2, "REJECTED", "0.000", 2);
        UUID other = rows.judgmentWithClaim(visible1, j2, "ACCEPTED", "20.250", 3);
        assertThat(other).isNotNull();

        DocumentContext table = family(reader(staff), "CLAIM_RESULTS", "?lang=en");
        Map<String, List<Object>> byResult = new java.util.HashMap<>();
        for (List<Object> row : tableRows(table)) {
            byResult.put((String) row.get(0), row);
        }
        assertThat(byResult.get("Partly accepted").get(2)).isEqualTo("80.000");
        assertThat(((Number) byResult.get("Partly accepted").get(1)).longValue()).isEqualTo(1);
        assertThat(((Number) byResult.get("Accepted").get(1)).longValue()).isEqualTo(1);
        assertThat(totalRow(table).get(2)).as("100 at first instance is superseded by the appeal's 80").isEqualTo("100.250");
        assertThat(((Number) totalRow(table).get(1)).longValue()).as("three claims, three counts").isEqualTo(3);
    }

    @Test
    void riskUsesTheLatestAssessmentAndKeepsThePotentialLossAsItsOwnAmount() throws Exception {
        seedCases();
        rows.risk(visible1, 1, "HIGH", "1000.000");
        rows.risk(visible1, 2, "LOW", "10.500");
        rows.risk(visible2, 1, "VERY_HIGH", null);
        rows.risk(hidden, 1, "HIGH", "5000.000");
        DocumentContext table = family(reader(staff), "RISK_ASSESSMENT", "?lang=en");
        Map<String, List<Object>> byLevel = new java.util.HashMap<>();
        for (List<Object> row : tableRows(table)) {
            byLevel.put((String) row.get(0), row);
        }
        assertThat(byLevel.get("High").get(2)).isEqualTo("0.000");
        assertThat(byLevel.get("Low").get(2)).isEqualTo("10.500");
        assertThat(byLevel.get("Very high").get(2)).isEqualTo("0.000");
        assertThat(((Number) byLevel.get("Very high").get(3)).longValue()).as("loss not recorded").isEqualTo(1);
        assertThat(totalRow(table).get(2)).isEqualTo("10.500");
        assertThat(table.<String>read("$.note")).contains("decision aid");
    }

    @Test
    void linksCountOnceEvenWhenBothCasesAreVisibleAndOnlyWhenOneSideIs() throws Exception {
        seedCases();
        rows.link(visible1, visible2, "SUBJECT_LINK", "ACTIVE");
        rows.link(visible1, hidden, "REFERRAL", "ACTIVE");
        rows.link(hidden, visible2, "JOINDER_REQUEST", "REQUESTED");
        DocumentContext table = family(reader(staff), "LINKED_CASES", "?lang=en");
        assertThat(((Number) totalRow(table).get(2)).longValue()).isEqualTo(3);
        // a link whose both sides are hidden is not counted
        rows.link(hidden, data.insertCase("also hidden", "CIVIL", "OPEN", TODAY), "SUBJECT_LINK", "ACTIVE");
        assertThat(((Number) totalRow(family(reader(staff), "LINKED_CASES", "")).get(2)).longValue()).isEqualTo(3);
    }

    @Test
    void adjournmentReasonsAreGroupedIgnoringCaseAndMissingReasonsAreSaidSo() throws Exception {
        seedCases();
        UUID court = data.insertCourt("FIRST_INSTANCE");
        UUID proceeding = data.insertProceeding(visible2, "FIRST_INSTANCE", court, TODAY.minusDays(59), null);
        rows.hearing(visible2, proceeding, TODAY.minusDays(8), "ADJOURNED", "PLEADING", "COURT", "Await documents", staff);
        rows.hearing(visible2, proceeding, TODAY.minusDays(7), "ADJOURNED", "PLEADING", "COURT", " await DOCUMENTS ", staff);
        rows.hearing(visible2, proceeding, TODAY.minusDays(6), "ADJOURNED", "PLEADING", "COURT", null, staff);
        rows.hearing(visible2, proceeding, TODAY.minusDays(5), "HELD", "PLEADING", "COURT", null, staff);
        DocumentContext table = family(reader(staff), "ADJOURNMENTS", "?lang=en");
        Map<String, Long> byReason = new java.util.HashMap<>();
        for (List<Object> row : tableRows(table)) {
            byReason.put((String) row.get(0), ((Number) row.get(1)).longValue());
        }
        assertThat(byReason.get("Await documents")).isEqualTo(2);
        assertThat(byReason.get("Reason not recorded")).isEqualTo(1);
        assertThat(byReason.get("Total")).isEqualTo(3);
    }

    @Test
    void missingEvidenceCountsOnlyRequiredAndUnavailableItems() throws Exception {
        seedCases();
        rows.evidence(visible1, "REQUIRED", false);
        rows.evidence(visible1, "REQUIRED", false);
        rows.evidence(visible2, "UNAVAILABLE", false);
        rows.evidence(visible2, "SUPPORTING", false);
        rows.evidence(hidden, "REQUIRED", false);
        DocumentContext table = family(reader(staff), "MISSING_EVIDENCE", "?lang=en");
        assertThat(((Number) totalRow(table).get(1)).longValue()).isEqualTo(3);
    }

    @Test
    void informationRequestsAreReportedPerUnitOverTheVisibleCasesOnly() throws Exception {
        seedCases();
        UUID late = rows.infoRequest(visible1, "AWAITING", TODAY.minusDays(3));
        rows.infoRequest(hidden, "AWAITING", TODAY.minusDays(3));
        rows.reply(late, visible1, TODAY.minusDays(1), "INCOMPLETE");
        DocumentContext table = family(reader(staff), "INFO_REQUESTS", "?lang=en");
        long requests = tableRows(table).stream().mapToLong(r -> ((Number) r.get(1)).longValue()).sum();
        long overdue = tableRows(table).stream().mapToLong(r -> ((Number) r.get(4)).longValue()).sum();
        assertThat(requests).isEqualTo(1);
        assertThat(overdue).isEqualTo(1);
    }

    @Test
    void performanceIsObjectiveAlphabeticalAndNeverUsesTheOutcomeOfAJudgment() throws Exception {
        seedCases();
        UUID court = data.insertCourt("FIRST_INSTANCE");
        UUID proceeding = data.insertProceeding(visible2, "FIRST_INSTANCE", court, TODAY.minusDays(59), null);
        data.insertJudgment(proceeding, TODAY.minusDays(1), "AGAINST", true);
        // staff: one overdue task, one completed task, one hearing held; manager: no tasks
        data.insertTask(visible1, staff, "late", TODAY.minusDays(2), "OPEN");
        data.insertTask(visible1, staff, "done", TODAY.minusDays(1), "DONE");
        data.insertTask(hidden, manager, "hidden late", TODAY.minusDays(2), "OPEN");
        rows.hearing(visible1, data.insertProceeding(visible1, "FIRST_INSTANCE", court, TODAY.minusDays(3), null), TODAY.minusDays(1),
                "HELD", "PLEADING", "COURT", null, staff);
        rows.directive(visible1, staff, "DONE", TODAY.minusDays(3), TODAY.minusDays(1));
        rows.directive(visible1, staff, "NEW", TODAY.minusDays(3), TODAY.minusDays(1));
        String period = "?from=" + TODAY.minusDays(10) + "&to=" + TODAY.plusDays(10) + "&groupBy=MEMBERS&lang=en";
        DocumentContext table = family(reader(staff), "PERFORMANCE", period);
        List<String> keys = table.read("$.columns[*].key");
        assertThat(keys).doesNotContain("outcome", "judgments", "wins", "score", "rank");
        assertThat(keys).contains("tasksCompleted", "tasksOverdue", "hearingsHeld", "memos", "directivesOverdue");
        List<List<Object>> members = tableRows(table);
        assertThat(members.stream().map(r -> r.get(0)).toList()).isSorted();
        List<Object> mine = members.stream().filter(r -> "Bbb Staff".equals(r.get(0))).findFirst().orElseThrow();
        assertThat(mine.get(keys.indexOf("primaryCases"))).isEqualTo(2);
        assertThat(mine.get(keys.indexOf("alternateCases"))).isEqualTo(0);
        assertThat(mine.get(keys.indexOf("tasks"))).isEqualTo(2);
        assertThat(mine.get(keys.indexOf("tasksCompleted"))).isEqualTo(1);
        assertThat(mine.get(keys.indexOf("tasksOverdue"))).isEqualTo(1);
        assertThat(mine.get(keys.indexOf("hearingsHeld"))).isEqualTo(1);
        assertThat(mine.get(keys.indexOf("directives"))).isEqualTo(2);
        assertThat(mine.get(keys.indexOf("directivesDone"))).isEqualTo(1);
        assertThat(mine.get(keys.indexOf("directivesOverdue"))).isEqualTo(1);
        assertThat(table.<String>read("$.note")).contains("never ranked");
        assertThat(members.stream().map(r -> r.get(0))).doesNotContain("Aaa Manager");
        // the same figures whatever the judgment decided: change the outcome and nothing in the table moves
        jdbc.update("update judgment set outcome = 'IN_FAVOUR' where proceeding_id = ?", proceeding);
        assertThat(tableRows(family(reader(staff), "PERFORMANCE", period))).isEqualTo(members);
        DocumentContext units = family(reader(staff), "PERFORMANCE", "?groupBy=UNITS&lang=ar");
        assertThat(units.<List<String>>read("$.columns[*].key")).contains("requests", "overdue", "averageReplyDays");
    }

    @Test
    void deadlinesAndEnforcementAreMarkedUnavailableUntilTheirTablesExist() throws Exception {
        boolean deadline = jdbc.queryForObject("select to_regclass('public.deadline') is not null", Boolean.class);
        boolean enforcement = jdbc.queryForObject("select to_regclass('public.enforcement_file') is not null", Boolean.class);
        assertThat(family(reader(staff), "DEADLINES_APPEALS", "").<Boolean>read("$.available")).isEqualTo(deadline);
        assertThat(family(reader(staff), "ENFORCEMENT", "").<Boolean>read("$.available")).isEqualTo(enforcement);
    }

    @Test
    void theLibraryFamilyNeedsLibraryViewAndNothingElseDoes() throws Exception {
        mvc.perform(get("/api/v1/reports/families/LIBRARY_PRECEDENTS").with(as(Permission.REPORT_VIEW)))
                .andExpect(status().isForbidden());
        for (String groupBy : List.of("COURT", "YEAR", "DIRECTION", "FINALITY", "CLASSIFICATION", "TOPIC")) {
            mvc.perform(get("/api/v1/reports/families/LIBRARY_PRECEDENTS?groupBy=" + groupBy).with(reader(staff)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void badRequestsAreRefusedAndPermissionsEnforced() throws Exception {
        mvc.perform(get("/api/v1/reports/families/CASES_BY_TYPE_COURT_STAGE?groupBy=SECTOR").with(reader(staff)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/reports/families/NOPE").with(reader(staff))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/reports/families/OPEN_CLOSED_CASES?from=2031-02-01&to=2031-01-01").with(reader(staff)))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/v1/reports/families/OPEN_CLOSED_CASES?statusGroup=SIDEWAYS").with(reader(staff)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/reports/families").with(as(Permission.CASE_VIEW))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/reports/families/OPEN_CLOSED_CASES/export?format=pdf").with(as(Permission.REPORT_VIEW)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/reports/families")).andExpect(status().isUnauthorized());
    }

    @Test
    void everyFamilyExportsToCsvExcelAndPdfWithThreeDecimalMoney() throws Exception {
        seedCases();
        for (String code : CODES) {
            if (!family(reader(staff), code, "").<Boolean>read("$.available")) {
                mvc.perform(get("/api/v1/reports/families/" + code + "/export?format=csv").with(reader(staff)))
                        .andExpect(status().isUnprocessableEntity());
                continue;
            }
            for (String format : List.of("csv", "xlsx", "pdf")) {
                for (String lang : List.of("en", "ar")) {
                    byte[] body = mvc.perform(get("/api/v1/reports/families/" + code + "/export?format=" + format + "&lang=" + lang)
                            .with(reader(staff))).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
                    assertThat(body).as(code + format).isNotEmpty();
                    if ("pdf".equals(format)) {
                        assertThat(new String(body, 0, 4)).isEqualTo("%PDF");
                    }
                    if ("xlsx".equals(format)) {
                        assertThat(body).startsWith((byte) 'P', (byte) 'K');
                    }
                }
            }
        }
        String csv = mvc.perform(get("/api/v1/reports/families/OPEN_CLOSED_CASES/export?format=csv&lang=en").with(reader(staff)))
                .andReturn().getResponse().getContentAsString();
        assertThat(csv).contains("3500.500").contains("200.250").doesNotContain("999999");
        assertThat(new BigDecimal("3500.500").scale()).isEqualTo(3);
        long audited = jdbc.queryForObject("select count(*) from audit_event where action = 'REPORT_EXPORT'"
                + " and target_id = 'FAMILY_OPEN_CLOSED_CASES:CSV'", Long.class);
        assertThat(audited).isPositive();
    }
}
