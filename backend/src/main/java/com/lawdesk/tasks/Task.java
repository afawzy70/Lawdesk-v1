package com.lawdesk.tasks;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Version;

import com.lawdesk.security.ActorRef;

/** One task of the office, optionally of a case (V401__LD-027_task.sql). The case is fixed once created. */
@Entity
class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(updatable = false)
    private UUID caseId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 4000)
    private String description;

    @Column(nullable = false)
    private UUID assigneeId;

    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TaskPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskStatus status;

    private LocalDate startsOn;

    @Column(length = 4000)
    private String instructions;

    @Column(length = 2000)
    private String delayReason;

    private UUID delayReasonBy;

    private Instant delayReasonAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private TaskStatus statusBeforeLate;

    private Instant completedAt;

    @Version
    private long version;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false, updatable = false)
    private UUID createdBy;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column(nullable = false)
    private UUID updatedBy;

    protected Task() {
    }

    Task(UUID caseId, ActorRef actor, Instant now) {
        this.caseId = caseId;
        this.status = TaskStatus.NEW;
        this.createdAt = now;
        this.createdBy = actor.id();
        touch(actor, now);
    }

    void describe(String title, String description, UUID assigneeId, LocalDate dueDate, TaskPriority priority,
            LocalDate startsOn, String instructions) {
        this.title = title;
        this.description = description;
        this.startsOn = startsOn;
        this.instructions = instructions;
        this.assigneeId = assigneeId;
        this.dueDate = dueDate;
        this.priority = priority;
    }

    void move(TaskStatus next, ActorRef actor, Instant now) {
        this.status = next;
        this.statusBeforeLate = null;
        this.completedAt = next == TaskStatus.COMPLETED ? now : null;
        touch(actor, now);
    }

    /** The follow-up run: an open task past its due date becomes late and remembers what it was. */
    void markLate(Instant now) {
        this.statusBeforeLate = this.status;
        this.status = TaskStatus.LATE;
        this.updatedAt = now;
    }

    /** A late task whose due date moved to today or later is what it was before. */
    void restoreFromLate() {
        this.status = this.statusBeforeLate;
        this.statusBeforeLate = null;
    }

    void recordDelay(String reason, ActorRef actor, Instant now) {
        this.delayReason = reason;
        this.delayReasonBy = actor.id();
        this.delayReasonAt = now;
        touch(actor, now);
    }

    void reassign(UUID assigneeId, ActorRef actor, Instant now) {
        this.assigneeId = assigneeId;
        touch(actor, now);
    }

    void touch(ActorRef actor, Instant now) {
        this.updatedAt = now;
        this.updatedBy = actor.id();
    }

    UUID getId() {
        return id;
    }

    UUID getCaseId() {
        return caseId;
    }

    String getTitle() {
        return title;
    }

    String getDescription() {
        return description;
    }

    UUID getAssigneeId() {
        return assigneeId;
    }

    LocalDate getStartsOn() {
        return startsOn;
    }

    String getInstructions() {
        return instructions;
    }

    String getDelayReason() {
        return delayReason;
    }

    Instant getDelayReasonAt() {
        return delayReasonAt;
    }

    TaskStatus getStatusBeforeLate() {
        return statusBeforeLate;
    }

    LocalDate getDueDate() {
        return dueDate;
    }

    TaskPriority getPriority() {
        return priority;
    }

    TaskStatus getStatus() {
        return status;
    }

    Instant getCompletedAt() {
        return completedAt;
    }

    long getVersion() {
        return version;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    UUID getCreatedBy() {
        return createdBy;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }

    /** Still to do and due before today. */
    boolean overdue(LocalDate today) {
        return status.isOpen() && dueDate != null && dueDate.isBefore(today);
    }

    /** Still to do and due today or within {@link Rules#DUE_SOON_DAYS} days. */
    boolean dueSoon(LocalDate today) {
        return status.isOpen() && dueDate != null && !dueDate.isBefore(today)
                && !dueDate.isAfter(today.plusDays(Rules.DUE_SOON_DAYS));
    }
}
