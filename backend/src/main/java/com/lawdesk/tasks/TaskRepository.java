package com.lawdesk.tasks;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface TaskRepository extends JpaRepository<Task, UUID> {

    /** Reads the task with {@code select ... for update}, so racing writes to it are serialised. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Task t where t.id = :id")
    Optional<Task> lockById(UUID id);

    /**
     * The task list filter. Dates are never bound as null (PostgreSQL cannot type a null date parameter): without a due
     * filter {@code anyDue} is true and the dates are ignored.
     */
    String FILTER = """
            from Task t
            where (:assignee is null or t.assigneeId = :assignee) and (:caseId is null or t.caseId = :caseId)
              and t.status in :statuses
              and (:anyDue = true or t.dueDate between :dueFrom and :dueTo)""";

    /** Due date (undated last), then priority (urgent first), then the order of creation. */
    @Query(value = "select t " + FILTER + """
             order by t.dueDate asc nulls last,
                      case t.priority when com.lawdesk.tasks.TaskPriority.URGENT then 0
                                      when com.lawdesk.tasks.TaskPriority.HIGH then 1
                                      when com.lawdesk.tasks.TaskPriority.NORMAL then 2 else 3 end,
                      t.createdAt, t.id""",
            countQuery = "select count(t) " + FILTER)
    Page<Task> search(UUID assignee, UUID caseId, Collection<TaskStatus> statuses, boolean anyDue, LocalDate dueFrom,
            LocalDate dueTo, Pageable pageable);

    long countByAssigneeIdAndStatusInAndDueDateBefore(UUID assigneeId, Collection<TaskStatus> statuses,
            LocalDate before);

    long countByAssigneeIdAndStatusInAndDueDateBetween(UUID assigneeId, Collection<TaskStatus> statuses,
            LocalDate from, LocalDate to);
}
