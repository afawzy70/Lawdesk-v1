package com.lawdesk.reports;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongConsumer;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.experts.ExpertReportQueries;
import com.lawdesk.experts.ExpertReportQueries.ExpertRef;
import com.lawdesk.experts.ExpertReportQueries.ExpertWorkload;
import com.lawdesk.experts.ExpertReportQueries.ReferralFilter;
import com.lawdesk.experts.ReferralStatus;

/**
 * The experts report (docs/delivery/PHASE-6-ACCEPTANCE.md, LD-033): every expert of the registry, deactivated ones
 * included, with open referrals and overdue reports, from Workstream D's {@link ExpertReportQueries}, which also
 * defines "overdue". Sorted by name.
 *
 * <p>QAD-039: the registry is read first, as ids with their counts, and the caller may refuse it by size
 * ({@code sizeGuard}) before any name is looked up; the expert port answers at most {@code MAX_ROWS} ids per call, so
 * names are fetched in chunks of {@value #NAME_CHUNK}, never with more ids than the port allows.
 */
@Component
class ExpertReport {

    record Row(UUID expertId, String name, String specialty, boolean active, long open, long overdue) {
    }

    record Summary(Map<ReferralStatus, Long> byStatus, long overdueReports) {
    }

    record Report(List<Row> items, Summary summary) {
    }

    /** Ids per name lookup, well below the port's own limit of {@link ExpertReportQueries#MAX_ROWS}. */
    static final int NAME_CHUNK = 1_000;

    private final ExpertReportQueries experts;

    ExpertReport(ExpertReportQueries experts) {
        this.experts = experts;
    }

    /** The whole report, as shown on screen: it has no row limit. */
    public Report read() {
        return read(rows -> {
        });
    }

    /**
     * The report; {@code sizeGuard} receives the number of experts before any name is looked up and may refuse the
     * request by throwing (an export over its row limit).
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Report read(LongConsumer sizeGuard) {
        List<ExpertWorkload> workload = experts.workload();
        sizeGuard.accept(workload.size());
        Map<UUID, ExpertRef> names = names(workload.stream().map(ExpertWorkload::expertId).toList());
        List<Row> rows = workload.stream().map(entry -> {
            ExpertRef expert = names.get(entry.expertId());
            return new Row(entry.expertId(), expert == null ? null : expert.name(),
                    expert == null ? null : expert.specialty(), expert != null && expert.active(), entry.open(),
                    entry.overdue());
        }).sorted(Comparator.comparing(Row::name, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(row -> row.expertId().toString())).toList();
        return new Report(rows,
                new Summary(experts.countByStatus(ReferralFilter.all()), experts.overdueReportCount()));
    }

    /** The registry entries of these experts, looked up {@value #NAME_CHUNK} ids at a time. */
    private Map<UUID, ExpertRef> names(List<UUID> ids) {
        Map<UUID, ExpertRef> names = new HashMap<>();
        for (int from = 0; from < ids.size(); from += NAME_CHUNK) {
            names.putAll(experts.experts(ids.subList(from, Math.min(ids.size(), from + NAME_CHUNK))));
        }
        return names;
    }
}
