package com.lawdesk.reports;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.sql.DataSource;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.api.FieldErrorException;
import com.lawdesk.caselinks.CaseLinkReportQueries;
import com.lawdesk.cases.CaseAccessPolicy;
import com.lawdesk.cases.CaseReportQueries;
import com.lawdesk.cases.CaseReportQueries.CaseFilter;
import com.lawdesk.cases.CaseReportQueries.CaseRow;
import com.lawdesk.cases.CaseReportQueries.Dimension;
import com.lawdesk.cases.CaseReportQueries.SortField;
import com.lawdesk.cases.CaseReportQueries.UnitLevel;
import com.lawdesk.cases.CaseStatus;
import com.lawdesk.cases.CaseSummary;
import com.lawdesk.hearings.HearingKind;
import com.lawdesk.hearings.HearingReportQueries;
import com.lawdesk.hearings.HearingReportQueries.HearingFilter;
import com.lawdesk.hearings.HearingReportQueries.HearingRow;
import com.lawdesk.hearings.HearingStatus;
import com.lawdesk.inforequests.InfoRequestReportQueries;
import com.lawdesk.proceedings.JudgmentReportQueries;
import com.lawdesk.proceedings.JudgmentReportQueries.JudgmentFilter;
import com.lawdesk.proceedings.JudgmentReportQueries.JudgmentRow;
import com.lawdesk.risk.RiskReportQueries;
import com.lawdesk.tasks.TaskReportQueries;
import com.lawdesk.tasks.TaskReportQueries.TaskFilter;
import com.lawdesk.tasks.TaskReportQueries.TaskRow;

/**
 * The one definition of every dashboard indicator (LD-058, requirements section 26). {@link #figures} (the dashboard),
 * {@link #page} (the list a click opens) and {@link #export} all build the indicator through {@link #source}, so the
 * count of a list is, by construction, the figure on the dashboard for the same user and parameters. The rules
 * themselves stay with the owning modules' report ports (hearings, tasks, judgments, risk, links, information
 * requests); this service only chooses which port call an indicator is. Every port restricts to the cases the signed-in
 * user may see, so no figure includes a hidden case.
 *
 * <p>Indicators 5 (judgments in enforcement) and 8 (appeal deadlines) read tables of modules that may not be on this
 * system yet; while a table is missing they are reported as unavailable, never as zero or a sample.
 */
@Component
public class IndicatorService {

    /** "Upcoming" is today and the six days after it, as on the dashboard since LD-030. */
    static final int WEEK_AHEAD_DAYS = 6;
    static final String MODULE_NOT_AVAILABLE = "MODULE_NOT_AVAILABLE";
    /** An indicator whose own query failed: reported as unavailable, never allowed to fail the dashboard. */
    static final String INDICATOR_FAILED = "INDICATOR_FAILED";

    private static final Logger log = LoggerFactory.getLogger(IndicatorService.class);
    static final int MAX_GROUPS = 100;

    /** The statuses of the three case-state indicators (new, circulating, concluded); one definition for figure and list. */
    static final Set<CaseStatus> NEW = EnumSet.of(CaseStatus.OPEN);
    static final Set<CaseStatus> ONGOING = EnumSet.of(CaseStatus.ACTIVE, CaseStatus.SUSPENDED);
    static final Set<CaseStatus> CONCLUDED = EnumSet.of(CaseStatus.CLOSED, CaseStatus.ARCHIVED);

    /** What an indicator is: how many rows, and a page of them. */
    interface Source {
        long count();

        List<IndicatorItem> page(long offset, int limit);
    }

    private final CaseReportQueries cases;
    private final HearingReportQueries hearings;
    private final JudgmentReportQueries judgments;
    private final TaskReportQueries tasks;
    private final InfoRequestReportQueries requests;
    private final ReportCalendar calendar;
    private final JdbcClient jdbc;
    private final CaseAccessPolicy access;
    private final DataSource dataSource;

    IndicatorService(CaseReportQueries cases, HearingReportQueries hearings, JudgmentReportQueries judgments,
            TaskReportQueries tasks, InfoRequestReportQueries requests, ReportCalendar calendar, JdbcClient jdbc,
            CaseAccessPolicy access, DataSource dataSource) {
        this.cases = cases;
        this.hearings = hearings;
        this.judgments = judgments;
        this.tasks = tasks;
        this.requests = requests;
        this.calendar = calendar;
        this.jdbc = jdbc;
        this.access = access;
        this.dataSource = dataSource;
    }

    // ---- the dashboard ---------------------------------------------------------------------------------------------

    /** Every indicator's figure, in the order of the requirements. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<IndicatorFigure> figures(IndicatorQuery query) {
        List<IndicatorFigure> figures = new ArrayList<>();
        IndicatorQuery plain = IndicatorQuery.period(query.from(), query.to());
        // One pass over the register answers the six figures that are only a sum of case statuses; each is still the
        // count of the list it opens (IndicatorApiTest checks it), only read once instead of six times.
        CaseReportQueries.CaseCounts caseCounts = cases.counts(CaseFilter.ALL);
        for (IndicatorCode code : IndicatorCode.values()) {
            figures.add(figure(code, plain, caseCounts));
        }
        return figures;
    }

    /** One indicator's figure; if its own queries fail it is unavailable ({@link #INDICATOR_FAILED}) and the failure is logged. */
    private IndicatorFigure figure(IndicatorCode code, IndicatorQuery plain, CaseReportQueries.CaseCounts caseCounts) {
        try {
            return isolated(() -> {
                Source source = source(code, plain);
                if (source == null) {
                    return new IndicatorFigure(code, code.number(), code.entity(), false, MODULE_NOT_AVAILABLE, null, null);
                }
                Long fromStatuses = statusFigure(code, caseCounts);
                return new IndicatorFigure(code, code.number(), code.entity(), true, null,
                        fromStatuses != null ? fromStatuses : source.count(), code.grouped() ? groups(code) : null);
            });
        } catch (RuntimeException e) {
            log.error("indicator {} could not be calculated; reported as unavailable", code, e);
            return new IndicatorFigure(code, code.number(), code.entity(), false, INDICATOR_FAILED, null, null);
        }
    }

    /**
     * Runs the work in its own savepoint: a statement that fails in PostgreSQL aborts the whole transaction, so without one the
     * failure of a single indicator would make every later query of the request fail as well. Without a transaction (no
     * savepoint possible) the work simply runs.
     */
    private <T> T isolated(java.util.function.Supplier<T> work) {
        if (dataSource == null) {
            return work.get();
        }
        Connection connection = DataSourceUtils.getConnection(dataSource);
        Savepoint savepoint;
        try {
            savepoint = connection.setSavepoint();
        } catch (SQLException e) {
            DataSourceUtils.releaseConnection(connection, dataSource);
            return work.get();
        }
        try {
            T result = work.get();
            connection.releaseSavepoint(savepoint);
            return result;
        } catch (SQLException e) {
            throw new IllegalStateException("could not release the savepoint of an indicator", e);
        } catch (RuntimeException e) {
            try {
                connection.rollback(savepoint);
            } catch (SQLException rollbackFailure) {
                e.addSuppressed(rollbackFailure);
            }
            throw e;
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    /** The figures that are sums of case statuses, from counts already read; null for every other indicator. */
    private static Long statusFigure(IndicatorCode code, CaseReportQueries.CaseCounts counts) {
        return switch (code) {
            case TOTAL_CASES, CASES_BY_UNIT, CASES_BY_MEMBER -> counts.total();
            case CASES_NEW -> sum(counts, NEW);
            case CASES_ONGOING -> sum(counts, ONGOING);
            case CASES_CONCLUDED -> sum(counts, CONCLUDED);
            default -> null;
        };
    }

    private static long sum(CaseReportQueries.CaseCounts counts, Set<CaseStatus> statuses) {
        return statuses.stream().mapToLong(counts.byStatus()::get).sum();
    }

    private List<IndicatorFigure.Group> groups(IndicatorCode code) {
        List<IndicatorFigure.Group> out = new ArrayList<>();
        if (code == IndicatorCode.CASES_BY_MEMBER) {
            addGroups(out, null, cases.groups(CaseFilter.ALL, Dimension.MEMBER));
        } else {
            for (UnitLevel level : UnitLevel.values()) {
                addGroups(out, level.name(), cases.groups(CaseFilter.ALL, Dimension.valueOf(level.name())));
            }
        }
        return out;
    }

    private static void addGroups(List<IndicatorFigure.Group> out, String level, List<CaseReportQueries.Group> groups) {
        int added = 0;
        for (CaseReportQueries.Group group : groups) {
            if (added++ >= MAX_GROUPS) {
                break;
            }
            out.add(new IndicatorFigure.Group(level, group.key() == null ? IndicatorQuery.NONE : group.key(),
                    group.label(), group.cases()));
        }
    }

    // ---- the list a click opens ------------------------------------------------------------------------------------

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public IndicatorPage page(IndicatorCode code, IndicatorQuery query) {
        PeriodQuery.Period period = new PeriodQuery(query.from(), query.to()).resolve(calendar.today());
        Paging paging = Paging.of(query.page(), query.size());
        try {
            return isolated(() -> pageOf(code, query, period, paging));
        } catch (FieldErrorException | com.lawdesk.api.BusinessRuleException e) {
            // The request itself is wrong (a malformed group, a rule): the caller is told, the indicator is not "failed".
            throw e;
        } catch (RuntimeException e) {
            log.error("the list of indicator {} could not be read; reported as unavailable", code, e);
            return new IndicatorPage(code, false, INDICATOR_FAILED, List.of(), paging.page(), paging.size(), 0, 0,
                    period.from(), period.to());
        }
    }

    private IndicatorPage pageOf(IndicatorCode code, IndicatorQuery query, PeriodQuery.Period period, Paging paging) {
        Source source = source(code, query);
        if (source == null) {
            return new IndicatorPage(code, false, MODULE_NOT_AVAILABLE, List.of(), paging.page(), paging.size(), 0, 0,
                    period.from(), period.to());
        }
        long total = source.count();
        WpLabels labels = WpLabels.of(query.lang());
        List<IndicatorItem> items = paging.offset() >= total ? List.of()
                : source.page(paging.offset(), paging.size()).stream().map(i -> i.labelled(labels)).toList();
        return new IndicatorPage(code, true, null, items, paging.page(), paging.size(), total, paging.totalPages(total),
                period.from(), period.to());
    }

    /** Every row of the indicator (at most {@link ReportExports#MAX_ROWS}), refused before any lookup when larger. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<IndicatorItem> export(IndicatorCode code, IndicatorQuery query) {
        Source source = source(code, query);
        if (source == null) {
            throw new com.lawdesk.api.BusinessRuleException(MODULE_NOT_AVAILABLE,
                    "the module that owns this indicator is not available yet");
        }
        try {
            return isolated(() -> {
                ReportExports.requireWithinLimit(source.count());
                return source.page(0, ReportExports.MAX_ROWS);
            });
        } catch (com.lawdesk.api.BusinessRuleException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("the export of indicator {} failed", code, e);
            throw new com.lawdesk.api.BusinessRuleException(INDICATOR_FAILED, "the indicator could not be read");
        }
    }

    // ---- the definitions -------------------------------------------------------------------------------------------

    /** The indicator, or null while the module that owns its data is not installed. */
    Source source(IndicatorCode code, IndicatorQuery query) {
        LocalDate today = calendar.today();
        return switch (code) {
            case TOTAL_CASES -> caseSource(CaseFilter.ALL);
            case CASES_NEW -> caseSource(withStatuses(NEW));
            case CASES_ONGOING -> caseSource(withStatuses(ONGOING));
            case CASES_CONCLUDED -> caseSource(withStatuses(CONCLUDED));
            case RESERVED_FOR_JUDGMENT -> caseSource(CaseFilter.ALL.and(HearingReportQueries.reservedForJudgment()));
            case HIGH_RISK_CASES -> caseSource(CaseFilter.ALL.and(RiskReportQueries.highRisk()));
            case LINKED_CASES -> caseSource(CaseFilter.ALL.and(CaseLinkReportQueries.linked()));
            case CASES_BY_UNIT -> caseSource(unitFilter(query));
            case CASES_BY_MEMBER -> caseSource(memberFilter(query));
            case JUDGMENTS_ISSUED -> judgmentSource(
                    new PeriodQuery(query.from(), query.to()).resolve(today));
            case UPCOMING_HEARINGS -> hearingSource(new HearingFilter(today, today.plusDays(WEEK_AHEAD_DAYS),
                    EnumSet.of(HearingStatus.SCHEDULED), null, null));
            case EXPERT_HEARINGS -> hearingSource(new HearingFilter(today, today.plusDays(WEEK_AHEAD_DAYS),
                    EnumSet.of(HearingStatus.SCHEDULED), null, null).withKinds(EnumSet.of(HearingKind.EXPERT)));
            case OVERDUE_TASKS -> taskSource();
            case OVERDUE_INFO_REQUESTS -> requestSource(today);
            case APPEAL_DEADLINES -> pending("deadline", "from deadline d where d.source_type = 'JUDGMENT'"
                    + " and d.status = 'OPEN' and %s", "d.case_id",
                    "d.id, d.case_id, d.action as title, d.due_on as on_date, d.status, 'JUDGMENT' as detail",
                    "d.due_on, d.id");
            case JUDGMENTS_IN_ENFORCEMENT -> pending("enforcement_file", "from enforcement_file d where d.status in"
                    + " ('IN_PROGRESS', 'PARTIALLY_EXECUTED') and %s", "d.case_id",
                    "d.judgment_id as id, d.case_id, null as title, d.due_on as on_date, d.status, null as detail",
                    "d.due_on nulls last, d.id");
        };
    }

    private static CaseFilter withStatuses(Set<CaseStatus> statuses) {
        return new CaseFilter(null, statuses, Set.of(), null, null, null);
    }

    private CaseFilter unitFilter(IndicatorQuery query) {
        if (query.group() == null) {
            return CaseFilter.ALL;
        }
        UnitLevel level = query.level() == null ? UnitLevel.SECTOR : query.level();
        return CaseFilter.ALL.and(CaseReportQueries.authorityUnit(level,
                IndicatorQuery.NONE.equals(query.group()) ? null : query.group().strip()));
    }

    private CaseFilter memberFilter(IndicatorQuery query) {
        if (query.group() == null) {
            return CaseFilter.ALL;
        }
        if (IndicatorQuery.NONE.equals(query.group())) {
            return CaseFilter.ALL.and(CaseReportQueries.noPrimaryMember());
        }
        try {
            return CaseFilter.ALL.and(CaseReportQueries.primaryMember(UUID.fromString(query.group())));
        } catch (IllegalArgumentException e) {
            throw new FieldErrorException("group", "Uuid");
        }
    }

    private Source caseSource(CaseFilter filter) {
        return new Source() {
            @Override
            public long count() {
                return cases.counts(filter).total();
            }

            @Override
            public List<IndicatorItem> page(long offset, int limit) {
                return cases.list(filter, SortField.OPENED_ON, true, offset, limit).stream()
                        .map(IndicatorService::caseItem).toList();
            }
        };
    }

    private static IndicatorItem caseItem(CaseRow row) {
        return new IndicatorItem(IndicatorCode.Entity.CASE, row.id(), row.id(), row.referenceNo(), row.title(), null,
                row.openedOn(), row.status().name(), row.caseType().name());
    }

    private Source judgmentSource(PeriodQuery.Period period) {
        JudgmentFilter filter = new JudgmentFilter(period.from(), period.to(), null, null, null, null);
        return new Source() {
            @Override
            public long count() {
                return judgments.summarize(filter).total();
            }

            @Override
            public List<IndicatorItem> page(long offset, int limit) {
                List<JudgmentRow> rows = judgments.list(filter, JudgmentReportQueries.SortField.JUDGMENT_DATE, true,
                        offset, limit);
                Map<UUID, CaseSummary> refs = refs(rows.stream().map(JudgmentRow::caseId).toList());
                return rows.stream().map(r -> item(IndicatorCode.Entity.JUDGMENT, r.id(), r.caseId(), refs, null,
                        r.judgmentDate(), r.outcome().name(), r.stage().name())).toList();
            }
        };
    }

    private Source hearingSource(HearingFilter filter) {
        return new Source() {
            @Override
            public long count() {
                return hearings.count(filter);
            }

            @Override
            public List<IndicatorItem> page(long offset, int limit) {
                List<HearingRow> rows = hearings.find(filter, (int) offset, limit);
                Map<UUID, CaseSummary> refs = refs(rows.stream().map(HearingRow::caseId).toList());
                return rows.stream().map(r -> item(IndicatorCode.Entity.HEARING, r.id(), r.caseId(), refs, null,
                        r.hearingDate(), r.status().name(), r.purpose().name())).toList();
            }
        };
    }

    private Source taskSource() {
        TaskFilter filter = TaskFilter.all().overdueOnly();
        return new Source() {
            @Override
            public long count() {
                return tasks.count(filter);
            }

            @Override
            public List<IndicatorItem> page(long offset, int limit) {
                List<TaskRow> rows = tasks.find(filter, (int) offset, limit);
                Map<UUID, CaseSummary> refs = refs(rows.stream().map(TaskRow::caseId).filter(java.util.Objects::nonNull)
                        .toList());
                return rows.stream().map(r -> item(IndicatorCode.Entity.TASK, r.id(), r.caseId(), refs, r.title(),
                        r.dueDate(), r.status().name(), r.priority().name())).toList();
            }
        };
    }

    private Source requestSource(LocalDate today) {
        return new Source() {
            @Override
            public long count() {
                return requests.overdueCount(today);
            }

            @Override
            public List<IndicatorItem> page(long offset, int limit) {
                List<InfoRequestReportQueries.Item> rows = requests.overdue(today, offset, limit);
                Map<UUID, CaseSummary> refs = refs(rows.stream().map(InfoRequestReportQueries.Item::caseId).toList());
                return rows.stream().map(r -> item(IndicatorCode.Entity.INFO_REQUEST, r.id(), r.caseId(), refs,
                        r.requestNo() + " — " + r.subject(), r.dueOn(), r.status(), r.unitName())).toList();
            }
        };
    }

    private static IndicatorItem item(IndicatorCode.Entity entity, UUID id, UUID caseId, Map<UUID, CaseSummary> refs,
            String title, LocalDate date, String status, String detail) {
        CaseSummary summary = caseId == null ? null : refs.get(caseId);
        return new IndicatorItem(entity, id, caseId, summary == null ? null : summary.referenceNo(),
                summary == null ? null : summary.title(), title, date, status, detail);
    }

    private Map<UUID, CaseSummary> refs(List<UUID> caseIds) {
        return cases.summaries(caseIds.stream().distinct().toList());
    }

    // ---- indicators of modules that may not be installed -----------------------------------------------------------

    /** True when the table exists, that is when the module that owns it has been deployed. */
    boolean tableExists(String table) {
        return tableExists(jdbc, table);
    }

    /**
     * The table is looked up by its plain name, through the connection's search path. It must not be written {@code public.<name>}:
     * a deployment's role has its own schema as search path and no rights on {@code public} (deploy/postgres/app-role.sql), and
     * resolving a name in a schema the role may not use fails with "permission denied" instead of answering "no".
     */
    static boolean tableExists(JdbcClient jdbc, String table) {
        return Boolean.TRUE.equals(jdbc.sql("select to_regclass(:t) is not null").param("t", table).query(Boolean.class).single());
    }

    /**
     * An indicator over a table of a module delivered by a parallel work package (deadlines, enforcement); null while the
     * table does not exist. The rows are restricted to the cases the signed-in user may see.
     */
    private Source pending(String table, String fromWhere, String caseColumn, String columns, String order) {
        if (!tableExists(table)) {
            return null;
        }
        CaseAccessPolicy.Predicate visible = access.visibleCaseIds(caseColumn);
        String base = fromWhere.formatted(visible.sql());
        return new Source() {
            @Override
            public long count() {
                return jdbc.sql("select count(*) " + base).params(visible.params()).query(Long.class).single();
            }

            @Override
            public List<IndicatorItem> page(long offset, int limit) {
                List<Object[]> rows = jdbc.sql("select " + columns + " " + base + " order by " + order
                        + " limit :limit offset :offset").params(visible.params()).param("limit", limit)
                        .param("offset", offset).query((rs, i) -> new Object[] {rs.getObject("id", UUID.class),
                                rs.getObject("case_id", UUID.class), rs.getString("title"),
                                rs.getObject("on_date", LocalDate.class), rs.getString("status"),
                                rs.getString("detail")}).list();
                Map<UUID, CaseSummary> refs = refs(rows.stream().map(r -> (UUID) r[1]).toList());
                IndicatorCode.Entity entity = "deadline".equals(table) ? IndicatorCode.Entity.DEADLINE
                        : IndicatorCode.Entity.JUDGMENT;
                Function<Object[], IndicatorItem> map = r -> item(entity, (UUID) r[0], (UUID) r[1], refs,
                        (String) r[2], (LocalDate) r[3], (String) r[4], (String) r[5]);
                return rows.stream().map(map).toList();
            }
        };
    }
}
