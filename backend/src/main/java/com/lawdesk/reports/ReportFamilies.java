package com.lawdesk.reports;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.api.BusinessRuleException;
import com.lawdesk.api.FieldErrorException;
import com.lawdesk.api.NotFoundException;
import com.lawdesk.caselinks.CaseLinkReportQueries;
import com.lawdesk.cases.CaseAccessPolicy;
import com.lawdesk.cases.CaseReportQueries;
import com.lawdesk.cases.CaseReportQueries.CaseFilter;
import com.lawdesk.cases.CaseReportQueries.Dimension;
import com.lawdesk.cases.CaseReportQueries.MemberCases;
import com.lawdesk.cases.CaseStatus;
import com.lawdesk.directives.DirectiveReportQueries;
import com.lawdesk.hearings.HearingKind;
import com.lawdesk.hearings.HearingReportQueries;
import com.lawdesk.hearings.HearingStatus;
import com.lawdesk.inforequests.InfoRequestReportQueries;
import com.lawdesk.library.LibraryReportQueries;
import com.lawdesk.pleadings.PleadingReportQueries;
import com.lawdesk.proceedings.JudgmentReportQueries;
import com.lawdesk.proceedings.ProceedingReportQueries;
import com.lawdesk.risk.RiskReportQueries;
import com.lawdesk.security.CurrentActor;
import com.lawdesk.security.Permission;
import com.lawdesk.tasks.TaskPerformanceQueries;
import com.lawdesk.users.UserDirectory;

/**
 * The seventeen report families of requirements section 27 (LD-058). Each is filterable ({@link FamilyQuery}), carries
 * its answer as a {@link ReportTable} and is exported to CSV, Excel and PDF from that same table. Rules for what is
 * counted stay with the owning modules' report ports; every port restricts to the cases the signed-in user may see
 * (the filter's case scope includes the visibility rule), so totals never include hidden cases. Money: the five
 * amounts of decision D5 (financial impact, claimed, judged, settlement, potential loss) are separate columns, each
 * counting a case, claim or assessment once, and are never added to each other.
 */
@Component
public class ReportFamilies {

    /** A family's identity: its code, the requirement lines it answers and the groupings it offers (first is default). */
    public record Info(String code, int number, List<String> requirements, List<String> groupings) {
    }

    static final Map<String, Info> CATALOG = new LinkedHashMap<>();

    private static void family(String code, int number, String requirements, String... groupings) {
        CATALOG.put(code, new Info(code, number, List.of(requirements.split(",")), List.of(groupings)));
    }

    static {
        family("CASES_BY_TYPE_COURT_STAGE", 1, "LD-DOC-27-001", "CASE_TYPE", "COURT", "STAGE");
        family("CASES_BY_ORG_UNIT", 2, "LD-DOC-27-002", "SECTOR", "DEPARTMENT", "SECTION");
        family("CASES_BY_MEMBER", 3, "LD-DOC-27-003");
        family("OPEN_CLOSED_CASES", 4, "LD-DOC-27-004", "STATUS_GROUP", "STATUS");
        family("JUDGMENT_OUTCOMES", 5, "LD-DOC-27-005");
        family("CLAIM_RESULTS", 6, "LD-DOC-27-006");
        family("DEFENSE_POSITIONS", 7, "LD-DOC-27-007");
        family("LINKED_CASES", 8, "LD-DOC-27-008");
        family("ADJOURNMENTS", 9, "LD-DOC-27-009");
        family("EXPERT_SESSIONS", 10, "LD-DOC-27-010");
        family("DEADLINES_APPEALS", 11, "LD-DOC-27-011");
        family("ENFORCEMENT", 12, "LD-DOC-27-012");
        family("MISSING_EVIDENCE", 13, "LD-DOC-27-013");
        family("INFO_REQUESTS", 14, "LD-DOC-27-014");
        family("RISK_ASSESSMENT", 15, "LD-DOC-27-015");
        family("PERFORMANCE", 16, "LD-DOC-27-016,LD-DOC-24-001,LD-DOC-24-002,LD-DOC-24-003", "MEMBERS", "UNITS");
        family("LIBRARY_PRECEDENTS", 17, "LD-DOC-27-017", "COURT", "YEAR", "DIRECTION", "FINALITY", "CLASSIFICATION",
                "TOPIC");
    }

    private final CaseReportQueries cases;
    private final ProceedingReportQueries proceedings;
    private final JudgmentReportQueries judgments;
    private final PleadingReportQueries pleadings;
    private final CaseLinkReportQueries links;
    private final HearingReportQueries hearings;
    private final InfoRequestReportQueries requests;
    private final RiskReportQueries risks;
    private final LibraryReportQueries library;
    private final TaskPerformanceQueries taskPerformance;
    private final DirectiveReportQueries directives;
    private final UserDirectory users;
    private final ReportCalendar calendar;
    private final CurrentActor actor;
    private final JdbcClient jdbc;

    ReportFamilies(CaseReportQueries cases, ProceedingReportQueries proceedings, JudgmentReportQueries judgments,
            PleadingReportQueries pleadings, CaseLinkReportQueries links, HearingReportQueries hearings,
            InfoRequestReportQueries requests, RiskReportQueries risks, LibraryReportQueries library,
            TaskPerformanceQueries taskPerformance, DirectiveReportQueries directives, UserDirectory users,
            ReportCalendar calendar, CurrentActor actor, JdbcClient jdbc) {
        this.cases = cases;
        this.proceedings = proceedings;
        this.judgments = judgments;
        this.pleadings = pleadings;
        this.links = links;
        this.hearings = hearings;
        this.requests = requests;
        this.risks = risks;
        this.library = library;
        this.taskPerformance = taskPerformance;
        this.directives = directives;
        this.users = users;
        this.calendar = calendar;
        this.actor = actor;
        this.jdbc = jdbc;
    }

    public List<Info> catalog() {
        return List.copyOf(CATALOG.values());
    }

    static Info info(String code) {
        Info info = CATALOG.get(code);
        if (info == null) {
            throw new NotFoundException("report family");
        }
        return info;
    }

    // ---- running a family ------------------------------------------------------------------------------------------

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ReportTable run(String code, FamilyQuery query) {
        Info info = info(code);
        WpLabels labels = WpLabels.of(query.lang());
        String groupBy = groupBy(info, query);
        CaseFilter filter = filter(query, "PERFORMANCE".equals(code));
        CaseAccessPolicy.Predicate scope = cases.scope(filter);
        return switch (code) {
            case "CASES_BY_TYPE_COURT_STAGE" -> casesByTypeCourtStage(info, groupBy, filter, scope, labels);
            case "CASES_BY_ORG_UNIT" -> caseGroups(info, groupBy, labels, cases.groups(filter, Dimension.valueOf(groupBy)),
                    labels.word("group.level." + groupBy), null);
            case "CASES_BY_MEMBER" -> caseGroups(info, groupBy, labels, cases.groups(filter, Dimension.MEMBER),
                    labels.word("member"), this::memberName);
            case "OPEN_CLOSED_CASES" -> openClosed(info, groupBy, filter, labels);
            case "JUDGMENT_OUTCOMES" -> judgmentOutcomes(info, scope, labels);
            case "CLAIM_RESULTS" -> claimResults(info, scope, labels);
            case "DEFENSE_POSITIONS" -> defensePositions(info, scope, labels);
            case "LINKED_CASES" -> linkedCases(info, scope, labels);
            case "ADJOURNMENTS" -> adjournments(info, scope, labels);
            case "EXPERT_SESSIONS" -> expertSessions(info, scope, labels);
            case "DEADLINES_APPEALS" -> deadlines(info, scope, labels);
            case "ENFORCEMENT" -> enforcement(info, scope, labels);
            case "MISSING_EVIDENCE" -> missingEvidence(info, scope, labels);
            case "INFO_REQUESTS" -> infoRequests(info, scope, labels);
            case "RISK_ASSESSMENT" -> riskAssessment(info, scope, labels);
            case "PERFORMANCE" -> performance(info, groupBy, query, filter, scope, labels);
            case "LIBRARY_PRECEDENTS" -> libraryPrecedents(info, groupBy, labels);
            default -> throw new NotFoundException("report family");
        };
    }

    private static String groupBy(Info info, FamilyQuery query) {
        if (info.groupings().isEmpty()) {
            return null;
        }
        String groupBy = query.groupBy() == null ? info.groupings().get(0) : query.groupBy();
        if (!info.groupings().contains(groupBy)) {
            throw new FieldErrorException("groupBy", "Known");
        }
        return groupBy;
    }

    /** The query as a case filter; the performance family reads from and to as an activity period, not as an opening period. */
    private CaseFilter filter(FamilyQuery query, boolean activityPeriod) {
        if (query.from() != null && query.to() != null && query.from().isAfter(query.to())) {
            throw new BusinessRuleException("DATE_RANGE_INVALID", "the period starts after it ends");
        }
        Set<CaseStatus> statuses = query.statusGroup() == null ? Set.of() : new HashSet<>(query.statusGroup().statuses());
        CaseFilter filter = new CaseFilter(null, statuses, new HashSet<>(query.caseType()),
                activityPeriod ? null : query.from(), activityPeriod ? null : query.to(), null);
        return query.memberId() == null ? filter : filter.and(CaseReportQueries.primaryMember(query.memberId()));
    }

    // ---- table building --------------------------------------------------------------------------------------------

    private static ReportTable.Column col(WpLabels labels, String key, String type) {
        return new ReportTable.Column(key, labels.word(key), type);
    }

    private static ReportTable.Column col(String key, String label, String type) {
        return new ReportTable.Column(key, label, type);
    }

    private ReportTable table(Info info, String groupBy, WpLabels labels, List<ReportTable.Column> columns,
            List<List<Object>> rows, boolean hasTotal, String note) {
        return new ReportTable(info.code(), labels.word("family." + info.code()), true, null, info.groupings(), groupBy,
                columns, rows, hasTotal, note);
    }

    private ReportTable unavailable(Info info, WpLabels labels) {
        return new ReportTable(info.code(), labels.word("family." + info.code()), false, IndicatorService.MODULE_NOT_AVAILABLE,
                info.groupings(), null, List.of(), List.of(), false, labels.word("notAvailableYet"));
    }

    private static List<Object> row(Object... cells) {
        return Arrays.asList(cells);
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(3);
    }

    private String memberName(String id) {
        if (id == null) {
            return null;
        }
        try {
            return users.find(UUID.fromString(id)).map(UserDirectory.UserSummary::displayName).orElse(null);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ---- 1-4: cases ------------------------------------------------------------------------------------------------

    private List<ReportTable.Column> distributionColumns(WpLabels labels, String groupLabel) {
        return List.of(col("group", groupLabel, "TEXT"), col(labels, "cases", "COUNT"),
                col(labels, "claimedAmount", "MONEY"), col(labels, "financialImpactAmount", "MONEY"));
    }

    private List<List<Object>> withTotal(List<List<Object>> rows, WpLabels labels) {
        long cases = 0;
        BigDecimal claimed = BigDecimal.ZERO;
        BigDecimal impact = BigDecimal.ZERO;
        for (List<Object> r : rows) {
            cases += (Long) r.get(1);
            claimed = claimed.add((BigDecimal) r.get(2));
            impact = impact.add((BigDecimal) r.get(3));
        }
        List<List<Object>> out = new ArrayList<>(rows);
        out.add(row(labels.word("total"), cases, money(claimed), money(impact)));
        return out;
    }

    private ReportTable casesByTypeCourtStage(Info info, String groupBy, CaseFilter filter,
            CaseAccessPolicy.Predicate scope, WpLabels labels) {
        List<List<Object>> rows = new ArrayList<>();
        String groupLabel = labels.word("group.level." + groupBy);
        if ("CASE_TYPE".equals(groupBy)) {
            for (CaseReportQueries.Group g : cases.groups(filter, Dimension.CASE_TYPE)) {
                rows.add(row(labels.word(g.key()), g.cases(), g.claimedAmount(), g.financialImpactAmount()));
            }
        } else {
            for (ProceedingReportQueries.Distribution d : proceedings.distribution(scope, "COURT".equals(groupBy))) {
                String label = d.key() == null ? labels.word("noProceeding")
                        : "COURT".equals(groupBy) ? ("ar".equals(labels.lang()) ? d.nameAr() : d.nameEn()) : labels.word(d.key());
                rows.add(row(label, d.cases(), d.claimedAmount(), d.financialImpactAmount()));
            }
        }
        return table(info, groupBy, labels, distributionColumns(labels, groupLabel), withTotal(rows, labels), true,
                labels.word("currentPositionNote"));
    }

    private ReportTable caseGroups(Info info, String groupBy, WpLabels labels, List<CaseReportQueries.Group> groups,
            String groupLabel, java.util.function.Function<String, String> nameOf) {
        List<List<Object>> rows = new ArrayList<>();
        for (CaseReportQueries.Group g : groups) {
            String label = g.key() == null ? labels.word("notSpecified") : nameOf == null ? g.label() : orUnknown(
                    nameOf.apply(g.key()), labels);
            rows.add(row(label, g.cases(), g.claimedAmount(), g.financialImpactAmount()));
        }
        return table(info, groupBy, labels, distributionColumns(labels, groupLabel), withTotal(rows, labels), true,
                labels.word("primaryNote"));
    }

    private static String orUnknown(String name, WpLabels labels) {
        return name == null ? labels.word("unknownUser") : name;
    }

    private ReportTable openClosed(Info info, String groupBy, CaseFilter filter, WpLabels labels) {
        List<List<Object>> rows = new ArrayList<>();
        List<CaseReportQueries.Group> groups = cases.groups(filter, Dimension.STATUS);
        if ("STATUS".equals(groupBy)) {
            for (CaseStatus status : CaseStatus.values()) {
                groups.stream().filter(g -> status.name().equals(g.key())).findFirst().ifPresentOrElse(
                        g -> rows.add(row(labels.word(status.name()), g.cases(), g.claimedAmount(), g.financialImpactAmount())),
                        () -> rows.add(row(labels.word(status.name()), 0L, money(null), money(null))));
            }
        } else {
            for (CaseReportQueries.StatusGroup group : CaseReportQueries.StatusGroup.values()) {
                long n = 0;
                BigDecimal claimed = BigDecimal.ZERO;
                BigDecimal impact = BigDecimal.ZERO;
                for (CaseReportQueries.Group g : groups) {
                    if (CaseReportQueries.StatusGroup.of(CaseStatus.valueOf(g.key())) == group) {
                        n += g.cases();
                        claimed = claimed.add(g.claimedAmount());
                        impact = impact.add(g.financialImpactAmount());
                    }
                }
                rows.add(row(labels.word("group." + group.name()), n, money(claimed), money(impact)));
            }
        }
        return table(info, groupBy, labels, distributionColumns(labels, labels.word("group.level." + groupBy)),
                withTotal(rows, labels), true, null);
    }

    // ---- 5-8: judgments, claims, defences, links --------------------------------------------------------------------

    private ReportTable judgmentOutcomes(Info info, CaseAccessPolicy.Predicate scope, WpLabels labels) {
        List<List<Object>> rows = new ArrayList<>();
        long total = 0;
        long finals = 0;
        for (JudgmentReportQueries.OutcomeRow o : judgments.outcomes(scope)) {
            rows.add(row(labels.word(o.outcome().name()), o.judgments(), o.finalJudgments()));
            total += o.judgments();
            finals += o.finalJudgments();
        }
        rows.add(row(labels.word("total"), total, finals));
        return table(info, null, labels, List.of(col("group", labels.word("outcome"), "TEXT"),
                col("judgments", labels.word("judgments"), "COUNT"), col("final", labels.word("final"), "COUNT")), rows, true,
                labels.word("outcomeNote"));
    }

    private ReportTable claimResults(Info info, CaseAccessPolicy.Predicate scope, WpLabels labels) {
        List<List<Object>> rows = new ArrayList<>();
        long total = 0;
        BigDecimal awarded = BigDecimal.ZERO;
        for (PleadingReportQueries.ResultRow r : pleadings.claimResults(scope)) {
            rows.add(row(r.result() == null ? labels.word("decided") : labels.word("claimResult." + r.result()), r.claims(),
                    r.awardedAmount()));
            total += r.claims();
            awarded = awarded.add(r.awardedAmount());
        }
        rows.add(row(labels.word("total"), total, money(awarded)));
        return table(info, null, labels, List.of(col("group", labels.word("result"), "TEXT"),
                col(labels, "claims", "COUNT"), col(labels, "awardedAmount", "MONEY")), rows, true,
                labels.word("currentResultNote"));
    }

    private ReportTable defensePositions(Info info, CaseAccessPolicy.Predicate scope, WpLabels labels) {
        List<List<Object>> rows = new ArrayList<>();
        long total = 0;
        for (PleadingReportQueries.PositionRow r : pleadings.defensePositions(scope)) {
            rows.add(row(labels.word("defenseKind." + r.kind()),
                    r.position() == null ? labels.word("noPosition") : labels.word("defensePosition." + r.position()),
                    r.defenses()));
            total += r.defenses();
        }
        rows.add(row(labels.word("total"), null, total));
        return table(info, null, labels, List.of(col("kind", labels.word("kind"), "TEXT"),
                col("position", labels.word("position"), "TEXT"), col(labels, "defenses", "COUNT")), rows, true,
                labels.word("currentResultNote"));
    }

    private ReportTable linkedCases(Info info, CaseAccessPolicy.Predicate scope, WpLabels labels) {
        List<List<Object>> rows = new ArrayList<>();
        long total = 0;
        long decisions = 0;
        for (CaseLinkReportQueries.KindRow r : links.byKind(scope)) {
            rows.add(row(labels.word("linkKind." + r.kind()), labels.word("linkStatus." + r.status()), r.links(),
                    r.courtDecisions()));
            total += r.links();
            decisions += r.courtDecisions();
        }
        rows.add(row(labels.word("total"), null, total, decisions));
        return table(info, null, labels, List.of(col("kind", labels.word("kind"), "TEXT"),
                col("status", labels.word("status"), "TEXT"), col(labels, "links", "COUNT"),
                col(labels, "courtDecisions", "COUNT")), rows, true, labels.word("linkNote"));
    }

    // ---- 9-10: hearings ---------------------------------------------------------------------------------------------

    private ReportTable adjournments(Info info, CaseAccessPolicy.Predicate scope, WpLabels labels) {
        List<List<Object>> rows = new ArrayList<>();
        long total = 0;
        for (HearingReportQueries.AdjournmentReason r : hearings.adjournmentReasons(scope)) {
            rows.add(row(r.reason() == null ? labels.word("reasonNotRecorded") : r.reason(), r.hearings(), r.cases()));
            total += r.hearings();
        }
        rows.add(row(labels.word("total"), total, null));
        return table(info, null, labels, List.of(col("reason", labels.word("reason"), "TEXT"),
                col(labels, "hearings", "COUNT"), col(labels, "cases", "COUNT")), rows, true, labels.word("distinctCasesNote"));
    }

    private ReportTable expertSessions(Info info, CaseAccessPolicy.Predicate scope, WpLabels labels) {
        List<List<Object>> rows = new ArrayList<>();
        long total = 0;
        for (Map.Entry<HearingStatus, Long> e : hearings.countByStatus(scope, HearingKind.EXPERT).entrySet()) {
            rows.add(row(labels.word("hearing." + e.getKey().name()), e.getValue()));
            total += e.getValue();
        }
        rows.add(row(labels.word("total"), total));
        return table(info, null, labels, List.of(col("status", labels.word("status"), "TEXT"),
                col(labels, "hearings", "COUNT")), rows, true, null);
    }

    // ---- 11-12: modules delivered by parallel work packages ----------------------------------------------------------

    private boolean exists(String table) {
        return IndicatorService.tableExists(jdbc, table);
    }

    private ReportTable deadlines(Info info, CaseAccessPolicy.Predicate scope, WpLabels labels) {
        if (!exists("deadline")) {
            return unavailable(info, labels);
        }
        List<List<Object>> rows = new ArrayList<>(jdbc.sql("select d.source_type, d.status, d.calc_status, count(*) as n,"
                + " count(*) filter (where d.status = 'OPEN' and d.due_on < :today) as late from deadline d"
                + " join legal_case on legal_case.id = d.case_id where " + scope.sql()
                + " group by d.source_type, d.status, d.calc_status order by 1, 2, 3").params(scope.params())
                .param("today", calendar.today()).query((rs, i) -> row(rs.getString("source_type"), rs.getString("status"),
                        rs.getString("calc_status"), rs.getLong("n"), rs.getLong("late"))).list());
        return table(info, null, labels, List.of(col("kind", labels.word("kind"), "TEXT"),
                col("status", labels.word("status"), "TEXT"), col("calc", labels.word("calcStatus"), "TEXT"),
                col(labels, "count", "COUNT"), col(labels, "overdue", "COUNT")), rows, false, null);
    }

    private ReportTable enforcement(Info info, CaseAccessPolicy.Predicate scope, WpLabels labels) {
        if (!exists("enforcement_file")) {
            return unavailable(info, labels);
        }
        List<List<Object>> rows = new ArrayList<>(jdbc.sql("select f.status, count(*) as n,"
                + " coalesce(sum(f.amount_required), 0) as required from enforcement_file f"
                + " join legal_case on legal_case.id = f.case_id where " + scope.sql() + " group by f.status order by 1")
                .params(scope.params()).query((rs, i) -> row(rs.getString("status"), rs.getLong("n"),
                        rs.getBigDecimal("required").setScale(3))).list());
        return table(info, null, labels, List.of(col("status", labels.word("status"), "TEXT"),
                col(labels, "count", "COUNT"), col(labels, "requiredAmount", "MONEY")), rows, false,
                labels.word("requiredAmountNote"));
    }

    // ---- 13-15: evidence, requests, risk ------------------------------------------------------------------------------

    private ReportTable missingEvidence(Info info, CaseAccessPolicy.Predicate scope, WpLabels labels) {
        List<List<Object>> rows = new ArrayList<>();
        long items = 0;
        long without = 0;
        for (PleadingReportQueries.MissingRow r : pleadings.missingEvidence(scope)) {
            rows.add(row(labels.word("evidence." + r.role()), r.items(), r.withoutDocument(), r.cases()));
            items += r.items();
            without += r.withoutDocument();
        }
        rows.add(row(labels.word("total"), items, without, null));
        return table(info, null, labels, List.of(col("role", labels.word("role"), "TEXT"), col(labels, "items", "COUNT"),
                col(labels, "withoutDocument", "COUNT"), col(labels, "cases", "COUNT")), rows, true,
                labels.word("distinctCasesNote"));
    }

    private ReportTable infoRequests(Info info, CaseAccessPolicy.Predicate scope, WpLabels labels) {
        List<List<Object>> rows = new ArrayList<>();
        for (InfoRequestReportQueries.UnitRow r : requests.byUnit(scope, calendar.today())) {
            rows.add(row(labels.orNotSpecified(r.unitName()), r.requests(), r.replies(), r.answered(), r.overdue(),
                    r.incomplete(), r.casesAffected(), r.averageReplyDays() == null ? null
                            : BigDecimal.valueOf(r.averageReplyDays()).setScale(1, java.math.RoundingMode.HALF_UP)));
        }
        return table(info, null, labels, unitColumns(labels), rows, false, labels.word("distinctCasesNote"));
    }

    private List<ReportTable.Column> unitColumns(WpLabels labels) {
        return List.of(col(labels, "unit", "TEXT"), col(labels, "requests", "COUNT"), col(labels, "replies", "COUNT"),
                col(labels, "answered", "COUNT"), col(labels, "overdue", "COUNT"), col(labels, "incomplete", "COUNT"),
                col(labels, "casesAffected", "COUNT"), col(labels, "averageReplyDays", "DECIMAL"));
    }

    private ReportTable riskAssessment(Info info, CaseAccessPolicy.Predicate scope, WpLabels labels) {
        List<List<Object>> rows = new ArrayList<>();
        long total = 0;
        BigDecimal loss = BigDecimal.ZERO;
        for (RiskReportQueries.LevelRow r : risks.byLevel(scope)) {
            rows.add(row(r.level() == null ? labels.word("notAssessed") : labels.word("risk." + r.level()), r.cases(),
                    r.potentialLoss(), r.withoutLoss()));
            total += r.cases();
            loss = loss.add(r.potentialLoss());
        }
        rows.add(row(labels.word("total"), total, money(loss), null));
        return table(info, null, labels, List.of(col("level", labels.word("level"), "TEXT"), col(labels, "cases", "COUNT"),
                col(labels, "potentialLoss", "MONEY"), col(labels, "withoutLoss", "COUNT")), rows, true,
                labels.word("riskNote"));
    }

    // ---- 16: performance of members and units --------------------------------------------------------------------------

    private ReportTable performance(Info info, String groupBy, FamilyQuery query, CaseFilter filter,
            CaseAccessPolicy.Predicate scope, WpLabels labels) {
        if ("UNITS".equals(groupBy)) {
            List<List<Object>> rows = new ArrayList<>();
            for (InfoRequestReportQueries.UnitRow r : requests.byUnit(scope, calendar.today())) {
                rows.add(row(labels.orNotSpecified(r.unitName()), r.requests(), r.replies(), r.answered(), r.overdue(),
                        r.incomplete(), r.casesAffected(), r.averageReplyDays() == null ? null
                                : BigDecimal.valueOf(r.averageReplyDays()).setScale(1, java.math.RoundingMode.HALF_UP)));
            }
            rows.sort(Comparator.comparing(r -> String.valueOf(r.get(0)), String.CASE_INSENSITIVE_ORDER));
            return table(info, groupBy, labels, unitColumns(labels), rows, false, labels.word("noRanking"));
        }
        LocalDate today = calendar.today();
        PeriodQuery.Period period = new PeriodQuery(query.from(), query.to()).resolve(today);
        Map<UUID, long[]> figures = new HashMap<>();
        // order of the 13 figures: primary, primaryOpen, alternate, tasks, done, open, overdue, sched, held, awaiting,
        // memos, directives, directivesDone, directivesOverdue -> 14 slots
        for (MemberCases m : cases.memberCases(filter)) {
            long[] f = figures.computeIfAbsent(m.memberId(), k -> new long[14]);
            f[0] = m.primary();
            f[1] = m.primaryOpen();
            f[2] = m.alternate();
        }
        for (TaskPerformanceQueries.MemberTasks t : taskPerformance.byAssignee(scope, period.from(), period.to(), today)) {
            long[] f = figures.computeIfAbsent(t.memberId(), k -> new long[14]);
            f[3] = t.tasks();
            f[4] = t.completed();
            f[5] = t.open();
            f[6] = t.overdue();
        }
        for (HearingReportQueries.MemberHearings h : hearings.byResponsibleMember(scope, period.from(), period.to())) {
            long[] f = figures.computeIfAbsent(h.memberId(), k -> new long[14]);
            f[7] = h.scheduled();
            f[8] = h.held();
            f[9] = h.awaitingResult();
        }
        for (PleadingReportQueries.MemberMemos m : pleadings.memosByPreparer(scope, period.from(), period.to())) {
            figures.computeIfAbsent(m.memberId(), k -> new long[14])[10] = m.memos();
        }
        for (DirectiveReportQueries.MemberDirectives d : directives.byAddressee(scope, period.from(), period.to(), today)) {
            long[] f = figures.computeIfAbsent(d.memberId(), k -> new long[14]);
            f[11] = d.directives();
            f[12] = d.done();
            f[13] = d.overdue();
        }
        List<List<Object>> rows = new ArrayList<>();
        figures.forEach((id, f) -> {
            List<Object> cells = new ArrayList<>();
            cells.add(orUnknown(memberName(id.toString()), labels));
            for (long value : f) {
                cells.add(value);
            }
            rows.add(cells);
        });
        // Alphabetical, never ordered by a figure: this view measures workload and timeliness, it does not rank people.
        rows.sort(Comparator.comparing(r -> String.valueOf(r.get(0)), String.CASE_INSENSITIVE_ORDER));
        List<ReportTable.Column> columns = new ArrayList<>(List.of(col(labels, "member", "TEXT")));
        for (String key : List.of("primaryCases", "primaryOpen", "alternateCases", "tasks", "tasksCompleted", "tasksOpen",
                "tasksOverdue", "hearingsScheduled", "hearingsHeld", "hearingsAwaiting", "memos", "directives",
                "directivesDone", "directivesOverdue")) {
            columns.add(col(labels, key, "COUNT"));
        }
        return table(info, groupBy, labels, columns, rows, false,
                labels.word("noRanking") + " " + labels.word("period") + ": " + period.from() + " – " + period.to());
    }

    // ---- 17: the library ------------------------------------------------------------------------------------------------

    private ReportTable libraryPrecedents(Info info, String groupBy, WpLabels labels) {
        if (!actor.has(Permission.LIBRARY_VIEW)) {
            throw new AccessDeniedException("missing LIBRARY_VIEW");
        }
        LibraryReportQueries.Groups groups = library.groups();
        List<LibraryReportQueries.Row> source = switch (groupBy) {
            case "COURT" -> groups.byCourt();
            case "YEAR" -> groups.byYear();
            case "DIRECTION" -> groups.byDirection();
            case "FINALITY" -> groups.byFinality();
            case "CLASSIFICATION" -> groups.byClassification();
            default -> groups.byTopic();
        };
        List<List<Object>> rows = new ArrayList<>();
        for (LibraryReportQueries.Row r : source) {
            rows.add(row(r.label() == null ? labels.word("notSpecified") : r.label(), r.count()));
        }
        return table(info, groupBy, labels, List.of(col("group", labels.word("group.level." + groupBy), "TEXT"),
                col(labels, "count", "COUNT")), rows, false, groups.note());
    }

    // ---- exports ----------------------------------------------------------------------------------------------------------

    /** The table as an export table: typed cells, the labels the table already carries. */
    ExportTable exportTable(ReportTable table, FamilyQuery query) {
        WpLabels labels = WpLabels.of(query.lang());
        List<List<Object>> rows = new ArrayList<>();
        for (List<Object> row : table.rows()) {
            rows.add(row);
        }
        return new ExportTable(table.title(), calendar.today().toString(),
                labels.base(), table.columns().stream().map(ReportTable.Column::label).toList(), rows);
    }

    static String slug(String code) {
        return code.toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
