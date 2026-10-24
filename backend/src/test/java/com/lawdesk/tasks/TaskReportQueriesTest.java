package com.lawdesk.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.lawdesk.tasks.TaskReportQueries.AssigneeWorkload;
import com.lawdesk.tasks.TaskReportQueries.TaskFilter;
import com.lawdesk.tasks.TaskReportQueries.TaskRow;

/** The read-only task port for Phase 6 reports (architecture/CONTRACTS.md §8.3, PHASE-5-ACCEPTANCE LD-027.5). */
class TaskReportQueriesTest extends TaskTestSupport {

    @Autowired
    TaskReportQueries reports;

    @Test
    void findsTasksByStatusAssigneeCaseAndOverdueState() throws Exception {
        UUID caseId = createCase("Report");
        UUID mona = user("Mona", true);
        UUID omar = user("Omar", true);
        UUID late = create("Late", caseId, mona, today());
        jdbc.update("update task set due_date = due_date - 1 where id = ?", late);
        UUID open = create("Open", null, omar, today().plusDays(1));
        UUID done = create("Done", caseId, omar, null);
        move(done, "DONE", 0);

        assertThat(reports.find(TaskFilter.all(), 0, 100)).extracting(TaskRow::id).containsExactly(late, open, done);
        TaskRow row = reports.find(TaskFilter.all(), 0, 1).getFirst();
        assertThat(row.caseId()).isEqualTo(caseId);
        assertThat(row.assigneeId()).isEqualTo(mona);
        assertThat(row.title()).isEqualTo("Late");
        assertThat(row.priority()).isEqualTo(TaskPriority.NORMAL);
        assertThat(row.status()).isEqualTo(TaskStatus.OPEN);
        assertThat(row.overdue()).isTrue();

        assertThat(reports.count(TaskFilter.all().withStatuses(Set.of(TaskStatus.DONE)))).isEqualTo(1);
        assertThat(reports.find(TaskFilter.all().withAssignee(omar), 0, 100)).extracting(TaskRow::id)
                .containsExactly(open, done);
        assertThat(reports.find(TaskFilter.all().withCase(caseId), 0, 100)).extracting(TaskRow::id)
                .containsExactly(late, done);
        assertThat(reports.find(TaskFilter.all().overdueOnly(), 0, 100)).extracting(TaskRow::id).containsExactly(late);
        assertThat(reports.overdueCount()).isEqualTo(1);
        assertThat(reports.dueSoonCount()).isEqualTo(1);
        assertThat(reports.countByStatus(TaskFilter.all())).containsExactlyInAnyOrderEntriesOf(Map.of(
                TaskStatus.OPEN, 2L, TaskStatus.IN_PROGRESS, 0L, TaskStatus.DONE, 1L, TaskStatus.CANCELLED, 0L));
    }

    @Test
    void reportsTheOpenWorkloadPerAssignee() throws Exception {
        UUID mona = user("Mona", true);
        UUID omar = user("Omar", true);
        UUID late = create("Late", null, mona, today());
        jdbc.update("update task set due_date = due_date - 1 where id = ?", late);
        UUID started = create("Started", null, mona, null);
        move(started, "IN_PROGRESS", 0);
        UUID done = create("Done", null, omar, null);
        move(done, "DONE", 0);

        assertThat(reports.workload()).containsExactly(new AssigneeWorkload(mona, 1, 1, 1));
        assertThat(reports.today()).isEqualTo(today());
        assertThat(TaskReportQueries.DUE_SOON_DAYS).isEqualTo(3);
    }

    @Test
    void refusesUnboundedQueries() {
        assertThatThrownBy(() -> reports.find(TaskFilter.all(), 0, 10_001)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reports.find(TaskFilter.all(), -1, 1)).isInstanceOf(IllegalArgumentException.class);
    }
}
