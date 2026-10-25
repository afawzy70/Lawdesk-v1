package com.lawdesk.tasks;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.api.BusinessRuleException;
import com.lawdesk.api.ConflictException;
import com.lawdesk.api.FieldErrorException;
import com.lawdesk.api.NotFoundException;
import com.lawdesk.cases.CaseLookup;
import com.lawdesk.cases.CaseSummary;
import com.lawdesk.security.ActorRef;
import com.lawdesk.security.CurrentActor;
import com.lawdesk.users.UserDirectory;
import com.lawdesk.users.UserDirectory.UserSummary;

/**
 * Tasks and assignments (LD-027) and deadline reminders (LD-028), docs/delivery/PHASE-5-ACCEPTANCE.md. Cases are read
 * through {@link CaseLookup}, people through {@link UserDirectory}; case tasks add timeline entries in the same
 * transaction.
 */
@Service
class TaskService {

    /** The due-state filters of the task list. */
    enum Due {
        OVERDUE, SOON
    }

    private static final LocalDate EARLIEST = LocalDate.of(1, 1, 1);

    private final TaskRepository tasks;
    private final CaseLookup cases;
    private final UserDirectory users;
    private final CurrentActor currentActor;

    TaskService(TaskRepository tasks, CaseLookup cases, UserDirectory users, CurrentActor currentActor) {
        this.tasks = tasks;
        this.cases = cases;
        this.users = users;
        this.currentActor = currentActor;
    }

    @Transactional
    TaskRequests.Response create(TaskRequests.Create request) {
        ActorRef actor = currentActor.require();
        CaseSummary legalCase = request.caseId() == null ? null : writableCase(request.caseId());
        LocalDate today = Rules.today();
        requireNotPast(request.dueDate(), today);
        UserSummary assignee = assignable(request.assigneeId());

        Task task = new Task(request.caseId(), actor, Rules.now());
        task.describe(request.title(), request.description(), assignee.id(), request.dueDate(),
                request.priority() == null ? TaskPriority.NORMAL : request.priority());
        Task stored = tasks.saveAndFlush(task);
        if (legalCase != null) {
            cases.recordEvent(legalCase.id(), "TASK_CREATED", "Task \"%s\" assigned to %s%s".formatted(
                    stored.getTitle(), assignee.displayName(), dueSuffix(stored)));
        }
        return new Lookups().respond(stored, today);
    }

    @Transactional(readOnly = true)
    TaskRequests.Response get(UUID id) {
        return new Lookups().respond(tasks.findById(id).orElseThrow(() -> new NotFoundException("task")), Rules.today());
    }

    @Transactional(readOnly = true)
    Page<TaskRequests.Response> search(String assignee, UUID caseId, Set<TaskStatus> statuses, Due due,
            Pageable pageable) {
        UUID assigneeId = assigneeOf(assignee);
        LocalDate today = Rules.today();
        Set<TaskStatus> wanted = statuses == null || statuses.isEmpty() ? EnumSet.allOf(TaskStatus.class)
                : EnumSet.copyOf(statuses);
        // Only open tasks can be overdue or due soon; overdue reaches back to the earliest date PostgreSQL stores.
        LocalDate from = EARLIEST;
        LocalDate to = today;
        if (due != null) {
            wanted.retainAll(TaskStatus.OPEN_STATES);
            if (due == Due.OVERDUE) {
                to = today.minusDays(1);
            } else {
                from = today;
                to = today.plusDays(Rules.DUE_SOON_DAYS);
            }
        }
        Pageable page = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        if (wanted.isEmpty()) {
            return Page.empty(page);
        }
        Lookups lookups = new Lookups();
        return tasks.search(assigneeId, caseId, wanted, due == null, from, to, page)
                .map(t -> lookups.respond(t, today));
    }

    @Transactional(readOnly = true)
    TaskRequests.Reminders reminders() {
        UUID me = currentActor.require().id();
        LocalDate today = Rules.today();
        long overdue = tasks.countByAssigneeIdAndStatusInAndDueDateBefore(me, TaskStatus.OPEN_STATES, today);
        long dueSoon = tasks.countByAssigneeIdAndStatusInAndDueDateBetween(me, TaskStatus.OPEN_STATES, today,
                today.plusDays(Rules.DUE_SOON_DAYS));
        return new TaskRequests.Reminders(today, Rules.DUE_SOON_DAYS, overdue, dueSoon);
    }

    @Transactional
    TaskRequests.Response update(UUID id, TaskRequests.Update request) {
        ActorRef actor = currentActor.require();
        Task task = lockedCurrent(id, request.version());
        CaseSummary legalCase = task.getCaseId() == null ? null : writableCase(task.getCaseId());
        LocalDate today = Rules.today();
        if (!Objects.equals(request.dueDate(), task.getDueDate())) {
            requireNotPast(request.dueDate(), today);
        }
        boolean reassigned = !request.assigneeId().equals(task.getAssigneeId());
        UserSummary assignee = reassigned ? assignable(request.assigneeId()) : null;

        task.describe(request.title(), request.description(), request.assigneeId(), request.dueDate(),
                request.priority() == null ? TaskPriority.NORMAL : request.priority());
        task.touch(actor, Rules.now());
        Task stored = tasks.saveAndFlush(task);
        if (legalCase != null && assignee != null) {
            cases.recordEvent(legalCase.id(), "TASK_ASSIGNED", "Task \"%s\" assigned to %s".formatted(
                    stored.getTitle(), assignee.displayName()));
        }
        return new Lookups().respond(stored, today);
    }

    @Transactional
    TaskRequests.Response changeStatus(UUID id, TaskRequests.StatusChange request) {
        ActorRef actor = currentActor.require();
        Task task = lockedCurrent(id, request.version());
        CaseSummary legalCase = task.getCaseId() == null ? null : writableCase(task.getCaseId());
        TaskStatus from = task.getStatus();
        if (!from.next().contains(request.status())) {
            throw new BusinessRuleException(Rules.TASK_STATUS_TRANSITION,
                    "a task cannot move from " + from + " to " + request.status());
        }
        task.move(request.status(), actor, Rules.now());
        Task stored = tasks.saveAndFlush(task);
        String event = switch (request.status()) {
            case DONE -> "TASK_COMPLETED";
            case CANCELLED -> "TASK_CANCELLED";
            case OPEN -> from.isOpen() ? null : "TASK_REOPENED";
            case IN_PROGRESS -> null;
        };
        if (legalCase != null && event != null) {
            cases.recordEvent(legalCase.id(), event, "Task \"%s\" %s".formatted(stored.getTitle(),
                    request.status().name().toLowerCase(Locale.ROOT).replace('_', ' ')));
        }
        return new Lookups().respond(stored, Rules.today());
    }

    private Task lockedCurrent(UUID id, long version) {
        Task task = tasks.lockById(id).orElseThrow(() -> new NotFoundException("task"));
        if (task.getVersion() != version) {
            throw new ConflictException("task was changed by someone else");
        }
        return task;
    }

    private CaseSummary writableCase(UUID caseId) {
        CaseSummary legalCase = cases.find(caseId).orElseThrow(() -> new NotFoundException("case"));
        if ("ARCHIVED".equals(legalCase.status())) {
            throw new BusinessRuleException(Rules.CASE_ARCHIVED, "an archived case is read-only");
        }
        return legalCase;
    }

    private UserSummary assignable(UUID userId) {
        return users.find(userId).filter(UserSummary::enabled).orElseThrow(() -> new BusinessRuleException(
                Rules.ASSIGNEE_NOT_ASSIGNABLE, "tasks are assigned to enabled users only"));
    }

    private static void requireNotPast(LocalDate dueDate, LocalDate today) {
        if (dueDate != null && dueDate.isBefore(today)) {
            throw new FieldErrorException("dueDate", "FutureOrPresent");
        }
    }

    /** {@code me}, a user id, or nothing. */
    private UUID assigneeOf(String assignee) {
        if (assignee == null || assignee.isBlank()) {
            return null;
        }
        if ("me".equals(assignee)) {
            return currentActor.require().id();
        }
        try {
            return UUID.fromString(assignee);
        } catch (IllegalArgumentException e) {
            throw new FieldErrorException("assignee", "Pattern");
        }
    }

    private static String dueSuffix(Task task) {
        return task.getDueDate() == null ? "" : ", due " + task.getDueDate();
    }

    /** Reads cases and users once per answer; a page of tasks shares a few of each. */
    private final class Lookups {

        private final Map<UUID, Optional<CaseSummary>> legalCases = new HashMap<>();
        private final Map<UUID, Optional<UserSummary>> people = new HashMap<>();

        TaskRequests.Response respond(Task t, LocalDate today) {
            CaseSummary legalCase = t.getCaseId() == null ? null
                    : legalCases.computeIfAbsent(t.getCaseId(), cases::find).orElse(null);
            UserSummary assignee = people.computeIfAbsent(t.getAssigneeId(), users::find).orElse(null);
            return new TaskRequests.Response(t.getId(), t.getCaseId(),
                    legalCase == null ? null : legalCase.referenceNo(), legalCase == null ? null : legalCase.title(),
                    t.getTitle(), t.getDescription(), t.getAssigneeId(),
                    assignee == null ? null : assignee.displayName(), t.getDueDate(), t.getPriority(), t.getStatus(),
                    t.getCompletedAt(), t.overdue(today), t.dueSoon(today), t.getVersion(), t.getCreatedAt(),
                    t.getCreatedBy(), t.getUpdatedAt());
        }
    }
}
