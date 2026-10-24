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
    @Column(nullable = false, length = 12)
    private TaskStatus status;

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
        this.status = TaskStatus.OPEN;
        this.createdAt = now;
        this.createdBy = actor.id();
        touch(actor, now);
    }

    void describe(String title, String description, UUID assigneeId, LocalDate dueDate, TaskPriority priority) {
        this.title = title;
        this.description = description;
        this.assigneeId = assigneeId;
        this.dueDate = dueDate;
        this.priority = priority;
    }

    void move(TaskStatus next, ActorRef actor, Instant now) {
        this.status = next;
        this.completedAt = next == TaskStatus.DONE ? now : null;
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
