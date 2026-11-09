package com.lawdesk.tasks;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Who changed what on a task (V720 {@code task_history}); written in the transaction of the change itself. */
@Component
class TaskHistory {

    static final String CREATED = "CREATED";
    static final String UPDATED = "UPDATED";
    static final String STATUS = "STATUS";
    static final String REASSIGNED = "REASSIGNED";
    static final String DELAY_REASON = "DELAY_REASON";
    static final String SYSTEM_LATE = "SYSTEM_LATE";
    static final String SYSTEM_RESTORED = "SYSTEM_RESTORED";

    private static final int MAX = 500;

    /** One stored line before the names are looked up. */
    record Line(UUID id, java.time.Instant changedAt, UUID actorId, String action, String field, String oldValue,
            String newValue, String reason) {
    }

    private final JdbcClient jdbc;

    TaskHistory(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Adds one line; {@code actorId} is null for the follow-up run. Values are cut to 500 characters. */
    void record(UUID taskId, UUID actorId, String action, String field, Object oldValue, Object newValue,
            String reason) {
        jdbc.sql("""
                insert into task_history (task_id, changed_at, actor_id, action, field, old_value, new_value, reason)
                values (:task, clock_timestamp(), :actor, :action, :field, :old, :new, :reason)""")
                .param("task", taskId).param("actor", actorId).param("action", action).param("field", field)
                .param("old", cut(oldValue)).param("new", cut(newValue)).param("reason", cut(reason)).update();
    }

    /** Oldest first. */
    List<Line> list(UUID taskId) {
        return jdbc.sql("""
                select id, changed_at, actor_id, action, field, old_value, new_value, reason
                from task_history where task_id = :task order by changed_at, id""").param("task", taskId)
                .query((rs, n) -> new Line(rs.getObject("id", UUID.class),
                        rs.getObject("changed_at", OffsetDateTime.class).toInstant(), rs.getObject("actor_id", UUID.class),
                        rs.getString("action"), rs.getString("field"), rs.getString("old_value"),
                        rs.getString("new_value"), rs.getString("reason")))
                .list();
    }

    private static String cut(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return text.length() <= MAX ? text : text.substring(0, MAX);
    }
}
