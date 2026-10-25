package com.lawdesk.reports;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
 */
@Component
class ExpertReport {

    record Row(UUID expertId, String name, String specialty, boolean active, long open, long overdue) {
    }

    record Summary(Map<ReferralStatus, Long> byStatus, long overdueReports) {
    }

    record Report(List<Row> items, Summary summary) {
    }

    private final ExpertReportQueries experts;

    ExpertReport(ExpertReportQueries experts) {
        this.experts = experts;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Report read() {
        List<ExpertWorkload> workload = experts.workload();
        Map<UUID, ExpertRef> names = experts.experts(workload.stream().map(ExpertWorkload::expertId).toList());
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
}
