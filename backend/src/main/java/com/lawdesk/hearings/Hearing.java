package com.lawdesk.hearings;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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

/**
 * One hearing of a proceeding (V400__LD-024_hearing.sql). Case and proceeding are ids: they belong to Workstreams B
 * and C and are read through their ports. Both are fixed once scheduled, and so is the hearing it continues.
 */
@Entity
class Hearing {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID caseId;

    @Column(nullable = false, updatable = false)
    private UUID proceedingId;

    @Column(nullable = false)
    private LocalDate hearingDate;

    private LocalTime hearingTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HearingPurpose purpose;

    @Column(length = 2000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HearingStatus status;

    @Column(length = 2000)
    private String decision;

    @Column(updatable = false)
    private UUID adjournedFromId;

    private Instant resultRecordedAt;

    private UUID resultRecordedBy;

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

    protected Hearing() {
    }

    Hearing(UUID caseId, UUID proceedingId, UUID adjournedFromId, ActorRef actor, Instant now) {
        this.caseId = caseId;
        this.proceedingId = proceedingId;
        this.adjournedFromId = adjournedFromId;
        this.status = HearingStatus.SCHEDULED;
        this.createdAt = now;
        this.createdBy = actor.id();
        touch(actor, now);
    }

    void plan(LocalDate hearingDate, LocalTime hearingTime, HearingPurpose purpose, String notes) {
        this.hearingDate = hearingDate;
        this.hearingTime = hearingTime;
        this.purpose = purpose;
        this.notes = notes;
    }

    void recordResult(HearingStatus status, String decision, ActorRef actor, Instant now) {
        this.status = status;
        this.decision = decision;
        this.resultRecordedAt = now;
        this.resultRecordedBy = actor.id();
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

    UUID getProceedingId() {
        return proceedingId;
    }

    LocalDate getHearingDate() {
        return hearingDate;
    }

    LocalTime getHearingTime() {
        return hearingTime;
    }

    HearingPurpose getPurpose() {
        return purpose;
    }

    String getNotes() {
        return notes;
    }

    HearingStatus getStatus() {
        return status;
    }

    String getDecision() {
        return decision;
    }

    UUID getAdjournedFromId() {
        return adjournedFromId;
    }

    long getVersion() {
        return version;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }

    /** Its day has passed and no result is recorded yet. */
    boolean awaitingResult(LocalDate today) {
        return status == HearingStatus.SCHEDULED && hearingDate.isBefore(today);
    }
}
