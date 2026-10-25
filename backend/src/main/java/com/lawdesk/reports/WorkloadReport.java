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

import com.lawdesk.tasks.TaskReportQueries;
import com.lawdesk.tasks.TaskReportQueries.AssigneeWorkload;
import com.lawdesk.tasks.TaskReportQueries.TaskFilter;
import com.lawdesk.tasks.TaskStatus;
import com.lawdesk.users.UserDirectory;
import com.lawdesk.users.UserDirectory.UserSummary;

/**
 * The workload report (docs/delivery/PHASE-6-ACCEPTANCE.md, LD-033): the open tasks of every assignee from Workstream
 * D's {@link TaskReportQueries}, which also defines open, overdue and due soon; names from the {@link UserDirectory}.
 * One row per assignee with open work, by display name (assignees the directory does not know last).
 *
 * <p>QAD-039 audit: the assignees are read first, as ids with their counts, and the caller may refuse them by size
 * ({@code sizeGuard}) before any user is looked up. Names come from one {@code active()} lookup; only assignees it
 * does not hold (disabled or unknown accounts) are looked up one by one.
 */
@Component
class WorkloadReport {

    /** One assignee; {@code username} and {@code displayName} are null when the directory has no such user. */
    record Row(UUID assigneeId, String username, String displayName, Boolean enabled, long open, long inProgress,
            long overdue) {
    }

    record Summary(Map<TaskStatus, Long> byStatus, long overdue, long dueSoon) {
    }

    /** {@code dueSoonDays}: "due soon" means due today or within that many days. */
    record Report(List<Row> items, int dueSoonDays, Summary summary) {
    }

    private final TaskReportQueries tasks;
    private final UserDirectory users;

    WorkloadReport(TaskReportQueries tasks, UserDirectory users) {
        this.tasks = tasks;
        this.users = users;
    }

    /** The whole report, as shown on screen: it has no row limit. */
    public Report read() {
        return read(rows -> {
        });
    }

    /**
     * The report; {@code sizeGuard} receives the number of assignees before any user is looked up and may refuse the
     * request by throwing (an export over its row limit).
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Report read(LongConsumer sizeGuard) {
        List<AssigneeWorkload> workload = tasks.workload();
        sizeGuard.accept(workload.size());
        Map<UUID, UserSummary> active = new HashMap<>();
        if (!workload.isEmpty()) {
            users.active().forEach(user -> active.put(user.id(), user));
        }
        List<Row> rows = workload.stream().map(entry -> row(entry, active))
                .sorted(Comparator.comparing(Row::displayName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(row -> row.assigneeId().toString()))
                .toList();
        return new Report(rows, TaskReportQueries.DUE_SOON_DAYS,
                new Summary(tasks.countByStatus(TaskFilter.all()), tasks.overdueCount(), tasks.dueSoonCount()));
    }

    private Row row(AssigneeWorkload workload, Map<UUID, UserSummary> active) {
        UserSummary user = active.get(workload.assigneeId());
        if (user == null) {
            user = users.find(workload.assigneeId()).orElse(null);
        }
        return new Row(workload.assigneeId(), user == null ? null : user.username(),
                user == null ? null : user.displayName(), user == null ? null : user.enabled(), workload.open(),
                workload.inProgress(), workload.overdue());
    }
}
