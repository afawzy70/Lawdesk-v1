package com.lawdesk.tasks;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.api.BusinessRuleException;
import com.lawdesk.api.ConflictException;
import com.lawdesk.api.FieldErrorException;
import com.lawdesk.api.NotFoundException;
import com.lawdesk.audit.AuditLog;
import com.lawdesk.cases.CaseAccessPolicy;
import com.lawdesk.cases.CaseLookup;
import com.lawdesk.cases.CaseSummary;
import com.lawdesk.notifications.NotificationPublisher;
import com.lawdesk.notifications.NotificationTypes;
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

    /** The audit action of every task status change. */
    static final String STATUS_CHANGED = "TASK_STATUS_CHANGED";

    private final TaskRepository tasks;
    private final CaseLookup cases;
    private final CaseAccessPolicy access;
    private final UserDirectory users;
    private final CurrentActor currentActor;
    private final AuditLog audit;
    private final TaskHistory history;
    private final NotificationPublisher notifications;

    TaskService(TaskRepository tasks, CaseLookup cases, CaseAccessPolicy access, UserDirectory users,
            CurrentActor currentActor, AuditLog audit, TaskHistory history, NotificationPublisher notifications) {
        this.history = history;
        this.notifications = notifications;
        this.tasks = tasks;
        this.cases = cases;
        this.access = access;
        this.users = users;
        this.currentActor = currentActor;
        this.audit = audit;
    }

    @Transactional
    TaskRequests.Response create(TaskRequests.Create request) {
        ActorRef actor = currentActor.require();
        CaseSummary legalCase = request.caseId() == null ? null : writableCase(request.caseId());
        if (legalCase != null) {
            // Distributing work on a case is changing the case: a task alone never gives that right.
            access.requireChange(legalCase.id());
        }
        LocalDate today = Rules.today();
        requireNotPast(request.dueDate(), today);
        requireStartBeforeDue(request.startsOn(), request.dueDate());
        UserSummary assignee = assignable(request.assigneeId());

        Task task = new Task(request.caseId(), actor, Rules.now());
        task.describe(request.title(), request.description(), assignee.id(), request.dueDate(),
                request.priority() == null ? TaskPriority.NORMAL : request.priority(), request.startsOn(),
                request.instructions());
        Task stored = tasks.saveAndFlush(task);
        history.record(stored.getId(), actor.id(), TaskHistory.CREATED, null, null, stored.getStatus(), null);
        announceAssignment(stored, actor.id());
        if (legalCase != null) {
            cases.recordEvent(legalCase.id(), "TASK_CREATED", "Task \"%s\" assigned to %s%s".formatted(
                    stored.getTitle(), assignee.displayName(), dueSuffix(stored)));
        }
        return new Lookups().respond(stored, today);
    }

    @Transactional(readOnly = true)
    TaskRequests.Response get(UUID id) {
        Task task = tasks.findById(id).orElseThrow(() -> new NotFoundException("task"));
        if (!mayRead(task)) {
            throw new NotFoundException("task");
        }
        return new Lookups().respond(task, Rules.today());
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
        return tasks.search(assigneeId, caseId, wanted, due == null, from, to, access.scope(), page)
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
        if (!mayEdit(task)) {
            throw new AccessDeniedException("only the creator or a member of the case edits a task");
        }
        CaseSummary legalCase = task.getCaseId() == null ? null : writableCase(task.getCaseId());
        LocalDate today = Rules.today();
        if (!Objects.equals(request.dueDate(), task.getDueDate())) {
            requireNotPast(request.dueDate(), today);
        }
        requireStartBeforeDue(request.startsOn(), request.dueDate());
        boolean reassigned = !request.assigneeId().equals(task.getAssigneeId());
        UserSummary assignee = reassigned ? assignable(request.assigneeId()) : null;
        UUID previousAssignee = task.getAssigneeId();
        TaskPriority priority = request.priority() == null ? TaskPriority.NORMAL : request.priority();
        List<String[]> changes = changes(task, request, priority);

        task.describe(request.title(), request.description(), request.assigneeId(), request.dueDate(), priority,
                request.startsOn(), request.instructions());
        task.touch(actor, Rules.now());
        boolean restored = task.getStatus() == TaskStatus.LATE
                && (request.dueDate() == null || !request.dueDate().isBefore(today));
        TaskStatus beforeRestore = task.getStatus();
        if (restored) {
            task.restoreFromLate();
        }
        Task stored = tasks.saveAndFlush(task);
        for (String[] change : changes) {
            history.record(stored.getId(), actor.id(), TaskHistory.UPDATED, change[0], change[1], change[2], null);
        }
        if (restored) {
            history.record(stored.getId(), actor.id(), TaskHistory.SYSTEM_RESTORED, "status", beforeRestore,
                    stored.getStatus(), null);
        }
        if (reassigned) {
            history.record(stored.getId(), actor.id(), TaskHistory.REASSIGNED, "assignee", previousAssignee,
                    stored.getAssigneeId(), null);
            announceAssignment(stored, actor.id());
        }
        if (legalCase != null && assignee != null) {
            cases.recordEvent(legalCase.id(), "TASK_ASSIGNED", "Task \"%s\" assigned to %s".formatted(
                    stored.getTitle(), assignee.displayName()));
        }
        return new Lookups().respond(stored, today);
    }

    /** The fields of {@code task} that {@code request} changes, as {field, old, new}; the assignee has its own line. */
    private static List<String[]> changes(Task task, TaskRequests.Update request, TaskPriority priority) {
        List<String[]> out = new ArrayList<>();
        diff(out, "title", task.getTitle(), request.title());
        diff(out, "description", task.getDescription(), request.description());
        diff(out, "instructions", task.getInstructions(), request.instructions());
        diff(out, "starts_on", task.getStartsOn(), request.startsOn());
        diff(out, "due_date", task.getDueDate(), request.dueDate());
        diff(out, "priority", task.getPriority(), priority);
        return out;
    }

    private static void diff(List<String[]> out, String field, Object before, Object after) {
        if (!Objects.equals(before, after)) {
            out.add(new String[] {field, before == null ? null : before.toString(), after == null ? null : after.toString()});
        }
    }

    /** Tells the new assignee in the system, unless they gave the task to themselves. */
    private void announceAssignment(Task task, UUID actorId) {
        if (task.getAssigneeId().equals(actorId)) {
            return;
        }
        notifications.publish(task.getAssigneeId(), NotificationTypes.TASK_ASSIGNED, task.getCaseId(),
                "TASK:" + task.getId(), java.util.Map.of("taskTitle", task.getTitle()), "v" + task.getVersion());
    }

    @Transactional
    TaskRequests.Response reassign(UUID id, TaskRequests.Reassign request) {
        ActorRef actor = currentActor.require();
        Task task = lockedCurrent(id, request.version());
        if (!mayEdit(task)) {
            throw new AccessDeniedException("only the creator or a member of the case reassigns a task");
        }
        CaseSummary legalCase = task.getCaseId() == null ? null : writableCase(task.getCaseId());
        if (request.assigneeId().equals(task.getAssigneeId())) {
            throw new BusinessRuleException(Rules.ASSIGNEE_UNCHANGED, "the task is already assigned to that user");
        }
        UserSummary assignee = assignable(request.assigneeId());
        UUID previous = task.getAssigneeId();
        task.reassign(assignee.id(), actor, Rules.now());
        Task stored = tasks.saveAndFlush(task);
        history.record(stored.getId(), actor.id(), TaskHistory.REASSIGNED, "assignee", previous, assignee.id(),
                request.reason());
        if (legalCase != null) {
            cases.recordEvent(legalCase.id(), "TASK_ASSIGNED", "Task \"%s\" assigned to %s".formatted(
                    stored.getTitle(), assignee.displayName()));
        }
        announceAssignment(stored, actor.id());
        return new Lookups().respond(stored, Rules.today());
    }

    /** The assignee's (or an editor's) account of why the task is late. */
    @Transactional
    TaskRequests.Response recordDelay(UUID id, TaskRequests.DelayReason request) {
        ActorRef actor = currentActor.require();
        Task task = lockedCurrent(id, request.version());
        if (!actor.id().equals(task.getAssigneeId()) && !mayEdit(task)) {
            throw new AccessDeniedException("only the assignee or an editor of the task records a delay reason");
        }
        String before = task.getDelayReason();
        task.recordDelay(request.reason(), actor, Rules.now());
        Task stored = tasks.saveAndFlush(task);
        history.record(stored.getId(), actor.id(), TaskHistory.DELAY_REASON, "delay_reason", before,
                stored.getDelayReason(), null);
        return new Lookups().respond(stored, Rules.today());
    }

    @Transactional(readOnly = true)
    List<TaskRequests.HistoryEntry> history(UUID id) {
        Task task = tasks.findById(id).orElseThrow(() -> new NotFoundException("task"));
        if (!mayRead(task)) {
            throw new NotFoundException("task");
        }
        java.util.Map<UUID, Optional<UserSummary>> people = new HashMap<>();
        return history.list(id).stream().map(line -> new TaskRequests.HistoryEntry(line.id(), line.changedAt(),
                line.actorId(), line.actorId() == null ? null
                        : people.computeIfAbsent(line.actorId(), users::find).map(UserSummary::displayName).orElse(null),
                line.action(), line.field(), line.oldValue(), line.newValue(), line.reason())).toList();
    }

    @Transactional
    TaskRequests.Response changeStatus(UUID id, TaskRequests.StatusChange request) {
        ActorRef actor = currentActor.require();
        Task task = lockedCurrent(id, request.version());
        // The assignee moves their own task; so does anyone who may edit it. Nobody else, whatever they see.
        if (!currentActor.require().id().equals(task.getAssigneeId()) && !mayEdit(task)) {
            throw new AccessDeniedException("only the assignee or an editor of the task changes its status");
        }
        CaseSummary legalCase = task.getCaseId() == null ? null : writableCase(task.getCaseId());
        TaskStatus from = task.getStatus();
        if (!from.next().contains(request.status())) {
            throw new BusinessRuleException(Rules.TASK_STATUS_TRANSITION,
                    "a task cannot move from " + from + " to " + request.status());
        }
        String event = switch (request.status()) {
            case COMPLETED -> "TASK_COMPLETED";
            case CANCELLED -> "TASK_CANCELLED";
            case NEW -> from.isOpen() ? null : "TASK_REOPENED";
            case IN_PROGRESS, WAITING_DOCUMENTS, WAITING_OTHER, LATE -> null;
        };
        // Recorded first: an assignee sees the case through the open task, so once it is done they no longer do.
        if (legalCase != null && event != null) {
            cases.recordTaskEvent(legalCase.id(), event, "Task \"%s\" %s".formatted(task.getTitle(),
                    request.status().name().toLowerCase(Locale.ROOT).replace('_', ' ')));
        }
        task.move(request.status(), actor, Rules.now());
        Task stored = tasks.saveAndFlush(task);
        history.record(stored.getId(), actor.id(), TaskHistory.STATUS, "status", from, stored.getStatus(), null);
        // OWNER DECISION 2026-10-25 (QAD-031): a task may be completed straight from NEW, and every status change is
        // audited; the detail is the transition only (for example NEW->COMPLETED), never the title, the description or a
        // name. QAD-040: the event is written in this transaction, so it exists exactly when the change is committed
        // (a failure while answering, or of the commit itself, removes both).
        audit.recordWithChange(STATUS_CHANGED, actor.id(), actor.username(), "TASK", stored.getId().toString(),
                from.name() + "->" + stored.getStatus().name());
        return new Lookups().respond(stored, Rules.today());
    }

    /** Who sees a task: its assignee, its creator, and whoever sees its case. */
    private boolean mayRead(Task task) {
        UUID me = currentActor.require().id();
        if (task.getCaseId() == null) {
            return me.equals(task.getAssigneeId()) || me.equals(task.getCreatedBy()) || access.scope().all();
        }
        return me.equals(task.getAssigneeId()) || me.equals(task.getCreatedBy()) || access.canView(task.getCaseId());
    }

    /**
     * Who edits a task's fields and reassigns it: its creator, whoever may change its case, and — for a task that
     * belongs to no case — its assignee. The assignee of a case task changes its status only.
     */
    private boolean mayEdit(Task task) {
        UUID me = currentActor.require().id();
        if (me.equals(task.getCreatedBy())) {
            return true;
        }
        return task.getCaseId() == null ? me.equals(task.getAssigneeId()) : access.mayChange(task.getCaseId());
    }

    private Task lockedCurrent(UUID id, long version) {
        Task task = tasks.lockById(id).orElseThrow(() -> new NotFoundException("task"));
        if (!mayRead(task)) {
            throw new NotFoundException("task");
        }
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

    private static void requireStartBeforeDue(LocalDate startsOn, LocalDate dueDate) {
        if (startsOn != null && dueDate != null && startsOn.isAfter(dueDate)) {
            throw new FieldErrorException("startsOn", "StartsBeforeDue");
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
                    assignee == null ? null : assignee.displayName(), t.getStartsOn(), t.getDueDate(), t.getPriority(),
                    t.getStatus(), t.getCompletedAt(), t.overdue(today), t.dueSoon(today), t.getInstructions(),
                    t.getDelayReason(), t.getDelayReasonAt(), t.getVersion(), t.getCreatedAt(), t.getCreatedBy(),
                    t.getUpdatedAt());
        }
    }
}
