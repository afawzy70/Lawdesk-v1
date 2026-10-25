package com.lawdesk.reports;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.api.BusinessRuleException;
import com.lawdesk.cases.CaseReportQueries;
import com.lawdesk.cases.CaseReportQueries.CaseCounts;
import com.lawdesk.cases.CaseReportQueries.CaseFilter;
import com.lawdesk.cases.CaseReportQueries.CaseRow;
import com.lawdesk.cases.CaseReportQueries.SortField;
import com.lawdesk.cases.CaseReportQueries.StatusGroup;
import com.lawdesk.cases.CaseStatus;
import com.lawdesk.proceedings.ProceedingReportQueries;
import com.lawdesk.proceedings.ProceedingReportQueries.CasePosition;
import com.lawdesk.proceedings.Stage;

/**
 * The case inventory (docs/delivery/PHASE-6-ACCEPTANCE.md, LD-031): cases from the cases module, their place on the
 * judicial ladder from the proceedings module. Each request reads one repeatable-read snapshot, so the page and the
 * summary agree.
 */
@Component
class CaseInventory {

    static final Map<String, SortField> SORT_FIELDS = Map.of("referenceNo", SortField.REFERENCE_NO, "title",
            SortField.TITLE, "caseType", SortField.CASE_TYPE, "status", SortField.STATUS, "openedOn",
            SortField.OPENED_ON);
    static final Paging.Order<SortField> DEFAULT_ORDER = new Paging.Order<>(SortField.OPENED_ON, true);

    private final CaseReportQueries cases;
    private final ProceedingReportQueries proceedings;

    CaseInventory(CaseReportQueries cases, ProceedingReportQueries proceedings) {
        this.cases = cases;
        this.proceedings = proceedings;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CaseInventoryReport page(CaseInventoryQuery query) {
        CaseFilter filter = filter(query);
        Paging paging = Paging.of(query.page(), query.size());
        Paging.Order<SortField> order = Paging.Order.parse(query.sort(), SORT_FIELDS, DEFAULT_ORDER);

        CaseCounts counts = cases.counts(filter);
        List<CaseRow> rows = cases.list(filter, order.field(), order.descending(), paging.offset(), paging.size());
        Map<Stage, Long> stages = currentStageCounts(cases.ids(filter));
        Map<String, Long> byStage = new LinkedHashMap<>();
        stages.forEach((stage, n) -> byStage.put(stage.name(), n));
        byStage.put("NONE", counts.total() - stages.values().stream().mapToLong(Long::longValue).sum());

        return new CaseInventoryReport(rows(rows), paging.page(), paging.size(), counts.total(),
                paging.totalPages(counts.total()),
                new CaseInventoryReport.Summary(counts.total(), counts.count(StatusGroup.OPEN),
                        counts.count(StatusGroup.CLOSED), counts.byStatus(), counts.byType(), byStage));
    }

    /**
     * The current stage of every matching case, counted. The registry may hold more cases than one port call takes
     * (QAD-039 audit), so the ids go in chunks of {@link ProceedingReportQueries#MAX_IDS} and the counts add up; the
     * ids are distinct, so no case is counted twice.
     */
    private Map<Stage, Long> currentStageCounts(List<UUID> caseIds) {
        Map<Stage, Long> total = new EnumMap<>(Stage.class);
        for (Stage stage : Stage.values()) {
            total.put(stage, 0L);
        }
        for (int from = 0; from < caseIds.size(); from += ProceedingReportQueries.MAX_IDS) {
            proceedings.currentStageCounts(
                    caseIds.subList(from, Math.min(caseIds.size(), from + ProceedingReportQueries.MAX_IDS)))
                    .forEach((stage, n) -> total.merge(stage, n, Long::sum));
        }
        return total;
    }

    /** The rows of these cases, with their current stage and court. */
    List<CaseInventoryReport.Row> rows(List<CaseRow> rows) {
        Map<UUID, CasePosition> positions = proceedings.positions(rows.stream().map(CaseRow::id).toList());
        return rows.stream().map(row -> {
            CasePosition position = positions.get(row.id());
            return new CaseInventoryReport.Row(row.id(), row.referenceNo(), row.title(), row.caseType(),
                    row.status(), StatusGroup.of(row.status()), row.openedOn(),
                    position == null ? null : position.currentStage(),
                    position == null ? null : position.currentCourt(),
                    position == null ? 0 : position.proceedingCount());
        }).toList();
    }

    /**
     * The query as a case filter. Stage and court conditions become the set of matching case ids; a status filter that
     * contradicts the status group matches nothing.
     *
     * @throws BusinessRuleException {@code DATE_RANGE_INVALID} when {@code openedFrom} is after {@code openedTo}
     */
    CaseFilter filter(CaseInventoryQuery query) {
        if (query.openedFrom() != null && query.openedTo() != null && query.openedFrom().isAfter(query.openedTo())) {
            throw new BusinessRuleException("DATE_RANGE_INVALID", "the opening period starts after it ends");
        }
        Set<CaseStatus> statuses = query.status().isEmpty() ? EnumSet.noneOf(CaseStatus.class)
                : EnumSet.copyOf(query.status());
        Set<UUID> onlyCases = null;
        if (query.statusGroup() != null) {
            List<CaseStatus> group = query.statusGroup().statuses();
            if (statuses.isEmpty()) {
                statuses.addAll(group);
            } else {
                statuses.retainAll(group);
                if (statuses.isEmpty()) {
                    onlyCases = Set.of();
                }
            }
        }
        if (onlyCases == null && (!query.stage().isEmpty() || query.courtId() != null)) {
            onlyCases = proceedings.casesMatching(
                    query.stage().isEmpty() ? Set.of() : EnumSet.copyOf(query.stage()), query.courtId());
        }
        return new CaseFilter(query.q(), statuses, Set.copyOf(query.caseType()), query.openedFrom(), query.openedTo(),
                onlyCases);
    }
}
