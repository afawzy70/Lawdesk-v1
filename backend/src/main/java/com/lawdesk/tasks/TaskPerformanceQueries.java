package com.lawdesk.tasks;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.cases.CaseAccessPolicy;

/**
 * The read-only task port for the performance view (WP8, LD-058). Objective counts only. Written against the columns
 * that keep their meaning when the task lifecycle grows: a task is completed when it has a completion time, abandoned
 * when cancelled, and overdue when it is neither and its due date has passed.
 */
@Component
@Transactional(readOnly = true)
public class TaskPerformanceQueries {

    private final JdbcClient jdbc;

    TaskPerformanceQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Tasks of cases, due in the period, per assignee: all (cancelled excluded), completed, still open and overdue. */
    public record MemberTasks(UUID memberId, long tasks, long completed, long open, long overdue) {
    }

    public List<MemberTasks> byAssignee(CaseAccessPolicy.Predicate caseScope, LocalDate from, LocalDate to,
            LocalDate today) {
        return jdbc.sql("select t.assignee_id as member, count(*) as n, count(*) filter (where t.completed_at is not null) as done,"
                + " count(*) filter (where t.completed_at is null) as open,"
                + " count(*) filter (where t.completed_at is null and t.due_date < :today) as overdue"
                + " from task t join legal_case on legal_case.id = t.case_id where t.status <> 'CANCELLED'"
                + " and t.due_date between :from and :to and " + caseScope.sql() + " group by t.assignee_id")
                .params(caseScope.params()).param("from", from).param("to", to).param("today", today)
                .query((rs, i) -> new MemberTasks(rs.getObject("member", UUID.class), rs.getLong("n"), rs.getLong("done"),
                        rs.getLong("open"), rs.getLong("overdue"))).list();
    }
}
