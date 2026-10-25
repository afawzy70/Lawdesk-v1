package com.lawdesk.tasks;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The read-only task port for Phase 6 reports (architecture/CONTRACTS.md §8.3): tasks by status, assignee and overdue
 * state, and the open workload per assignee. It has no security of its own: the reports module checks
 * {@code REPORT_VIEW} first. "Open" ({@link TaskStatus#OPEN_STATES}), "overdue" (open and due before today) and "due
 * soon" (open and due within {@link #DUE_SOON_DAYS} days) are defined here, so reports never redefine them.
 */
@Component
@Transactional(readOnly = true)
public class TaskReportQueries {

    /** "Due soon" is due today or within the next three days (ASSUMPTION, LD-028). */
    public static final int DUE_SOON_DAYS = Rules.DUE_SOON_DAYS;

    /** The most rows one call returns (the export limit of LD-034, ASSUMPTION). */
    public static final int MAX_ROWS = 10_000;

    /**
     * Empty {@code statuses} means every status; a {@code null} assignee or case means every one; {@code overdue} keeps
     * the overdue tasks only.
     */
    public record TaskFilter(Set<TaskStatus> statuses, UUID assigneeId, UUID caseId, boolean overdue) {

        public TaskFilter {
            statuses = statuses == null ? Set.of() : Set.copyOf(statuses);
        }

        public static TaskFilter all() {
            return new TaskFilter(Set.of(), null, null, false);
        }

        public TaskFilter withStatuses(Set<TaskStatus> only) {
            return new TaskFilter(only, assigneeId, caseId, overdue);
        }

        public TaskFilter withAssignee(UUID assignee) {
            return new TaskFilter(statuses, assignee, caseId, overdue);
        }

        public TaskFilter withCase(UUID onlyCase) {
            return new TaskFilter(statuses, assigneeId, onlyCase, overdue);
        }

        public TaskFilter overdueOnly() {
            return new TaskFilter(statuses, assigneeId, caseId, true);
        }
    }

    /** One task as reports see it; case and assignee names come from their own ports. */
    public record TaskRow(UUID id, UUID caseId, String title, UUID assigneeId, LocalDate dueDate, TaskPriority priority,
            TaskStatus status, boolean overdue, java.time.Instant completedAt) {
    }

    /** The open tasks of one assignee: still open, in progress, and how many of those are overdue. */
    public record AssigneeWorkload(UUID assigneeId, long open, long inProgress, long overdue) {
    }

    private static final String OPEN = "status in ('OPEN', 'IN_PROGRESS')";

    private final NamedParameterJdbcTemplate jdbc;

    TaskReportQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** "Today" for every task rule: the calendar date in Egypt. */
    public LocalDate today() {
        return Rules.today();
    }

    /** The matching tasks by due date (undated last), then creation. */
    public List<TaskRow> find(TaskFilter filter, int offset, int limit) {
        if (offset < 0 || limit < 1 || limit > MAX_ROWS) {
            throw new IllegalArgumentException("offset must be >= 0 and limit 1.." + MAX_ROWS);
        }
        return jdbc.query("""
                select id, case_id, title, assignee_id, due_date, priority, status, completed_at,
                       (""" + OPEN + " and due_date < :today) as overdue from task" + where(filter)
                + " order by due_date nulls last, created_at, id offset :offset limit :limit",
                params(filter).addValue("offset", offset).addValue("limit", limit), (rs, n) -> row(rs));
    }

    public long count(TaskFilter filter) {
        return number("select count(*) from task" + where(filter), params(filter));
    }

    /** Every status, with zero for the ones no task has. */
    public Map<TaskStatus, Long> countByStatus(TaskFilter filter) {
        Map<TaskStatus, Long> counts = new EnumMap<>(TaskStatus.class);
        for (TaskStatus status : TaskStatus.values()) {
            counts.put(status, 0L);
        }
        jdbc.query("select status, count(*) as n from task" + where(filter) + " group by status", params(filter),
                rs -> {
                    counts.put(TaskStatus.valueOf(rs.getString("status")), rs.getLong("n"));
                });
        return counts;
    }

    public long overdueCount() {
        return count(TaskFilter.all().overdueOnly());
    }

    public long dueSoonCount() {
        return number("select count(*) from task where " + OPEN + " and due_date between :today and :soon",
                new MapSqlParameterSource("today", today()).addValue("soon", today().plusDays(DUE_SOON_DAYS)));
    }

    /** Assignees with open tasks, by assignee id. */
    public List<AssigneeWorkload> workload() {
        return jdbc.query("""
                select assignee_id, count(*) filter (where status = 'OPEN') as open,
                       count(*) filter (where status = 'IN_PROGRESS') as in_progress,
                       count(*) filter (where due_date < :today) as overdue
                from task where""" + " " + OPEN + " group by assignee_id order by assignee_id",
                new MapSqlParameterSource("today", today()),
                (rs, n) -> new AssigneeWorkload(rs.getObject("assignee_id", UUID.class), rs.getLong("open"),
                        rs.getLong("in_progress"), rs.getLong("overdue")));
    }

    private long number(String sql, MapSqlParameterSource params) {
        Long count = jdbc.queryForObject(sql, params, Long.class);
        return count == null ? 0 : count;
    }

    private static String where(TaskFilter filter) {
        StringBuilder sql = new StringBuilder(" where true");
        if (!filter.statuses().isEmpty()) {
            sql.append(" and status in (:statuses)");
        }
        if (filter.assigneeId() != null) {
            sql.append(" and assignee_id = :assigneeId");
        }
        if (filter.caseId() != null) {
            sql.append(" and case_id = :caseId");
        }
        if (filter.overdue()) {
            sql.append(" and ").append(OPEN).append(" and due_date < :today");
        }
        return sql.toString();
    }

    private MapSqlParameterSource params(TaskFilter filter) {
        return new MapSqlParameterSource().addValue("statuses", filter.statuses().stream().map(Enum::name).toList())
                .addValue("assigneeId", filter.assigneeId()).addValue("caseId", filter.caseId())
                .addValue("today", today());
    }

    private static TaskRow row(ResultSet rs) throws SQLException {
        java.sql.Timestamp completed = rs.getTimestamp("completed_at");
        return new TaskRow(rs.getObject("id", UUID.class), rs.getObject("case_id", UUID.class), rs.getString("title"),
                rs.getObject("assignee_id", UUID.class), rs.getObject("due_date", LocalDate.class),
                TaskPriority.valueOf(rs.getString("priority")), TaskStatus.valueOf(rs.getString("status")),
                rs.getBoolean("overdue"), completed == null ? null : completed.toInstant());
    }
}
