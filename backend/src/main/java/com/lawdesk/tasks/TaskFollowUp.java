package com.lawdesk.tasks;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.notifications.NotificationPublisher;
import com.lawdesk.notifications.NotificationTypes;

/**
 * The task part of the scheduled follow-up (requirements sections 14 and 32). An open task past its due date becomes LATE
 * (remembering what it was) and its assignee and the person who gave it are told; an open task due within three days is
 * announced to its assignee. Safe to repeat: a status changes once, and a notification exists once per task, due date and
 * person. Nothing is sent outside the system (decision D11).
 */
@Component
public class TaskFollowUp {

    /** What one run did. */
    public record Outcome(int lateTasks, int notifications) {
    }

    private record Row(UUID id, UUID caseId, String title, LocalDate dueDate, UUID assigneeId, UUID createdBy,
            String caseReferenceNo) {
    }

    private final JdbcClient jdbc;
    private final TaskHistory history;
    private final NotificationPublisher notifications;

    TaskFollowUp(JdbcClient jdbc, TaskHistory history, NotificationPublisher notifications) {
        this.jdbc = jdbc;
        this.history = history;
        this.notifications = notifications;
    }

    @Transactional
    public Outcome run() {
        LocalDate today = Rules.today();
        List<UUID> marked = jdbc.sql("""
                update task set status_before_late = status, status = 'LATE', version = version + 1, updated_at = now()
                where due_date < :today and status in ('NEW', 'IN_PROGRESS', 'WAITING_DOCUMENTS', 'WAITING_OTHER')
                returning id""").param("today", today).query(UUID.class).list();
        for (UUID id : marked) {
            String before = jdbc.sql("select status_before_late from task where id = :id").param("id", id)
                    .query(String.class).single();
            history.record(id, null, TaskHistory.SYSTEM_LATE, "status", before, TaskStatus.LATE, null);
        }
        int raised = 0;
        for (Row task : tasks("t.status = 'LATE'", today, null)) {
            for (UUID person : people(task.assigneeId(), task.createdBy())) {
                raised += publish(person, NotificationTypes.TASK_OVERDUE, task);
            }
        }
        for (Row task : tasks("t.status in ('NEW', 'IN_PROGRESS', 'WAITING_DOCUMENTS', 'WAITING_OTHER')"
                + " and t.due_date between :today and :soon", today, today.plusDays(Rules.DUE_SOON_DAYS))) {
            raised += publish(task.assigneeId(), NotificationTypes.TASK_DUE_SOON, task);
        }
        return new Outcome(marked.size(), raised);
    }

    private int publish(UUID person, String type, Row task) {
        Map<String, String> params = new java.util.TreeMap<>();
        params.put("taskTitle", task.title());
        params.put("dueDate", task.dueDate().toString());
        if (task.caseReferenceNo() != null) {
            params.put("caseReferenceNo", task.caseReferenceNo());
        }
        return notifications.publish(person, type, task.caseId(), "TASK:" + task.id(), params,
                task.dueDate().toString()) ? 1 : 0;
    }

    private List<Row> tasks(String condition, LocalDate today, LocalDate soon) {
        var statement = jdbc.sql("""
                select t.id, t.case_id, t.title, t.due_date, t.assignee_id, t.created_by, c.reference_no
                from task t left join legal_case c on c.id = t.case_id
                where t.due_date is not null and """ + " " + condition + " order by t.due_date, t.id");
        if (condition.contains(":today")) {
            statement = statement.param("today", today);
        }
        if (soon != null) {
            statement = statement.param("soon", soon);
        }
        return statement.query((rs, n) -> new Row(rs.getObject("id", UUID.class), rs.getObject("case_id", UUID.class),
                rs.getString("title"), rs.getObject("due_date", LocalDate.class), rs.getObject("assignee_id", UUID.class),
                rs.getObject("created_by", UUID.class), rs.getString("reference_no"))).list();
    }

    private static Set<UUID> people(UUID... ids) {
        Set<UUID> out = new LinkedHashSet<>();
        for (UUID id : ids) {
            if (id != null) {
                out.add(id);
            }
        }
        return out;
    }
}
