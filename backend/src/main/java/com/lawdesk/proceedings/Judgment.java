package com.lawdesk.proceedings;

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

/** The judgment that ends a proceeding (V302__LD-022_judgment.sql); one per proceeding. */
@Entity
class Judgment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID proceedingId;

    @Column(nullable = false)
    private LocalDate judgmentDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Outcome outcome;

    @Column(length = 4000)
    private String summary;

    @Column(name = "final", nullable = false)
    private boolean isFinal;

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

    protected Judgment() {
    }

    Judgment(UUID proceedingId, ActorRef actor, Instant now) {
        this.proceedingId = proceedingId;
        this.createdAt = now;
        this.createdBy = actor.id();
        touch(actor, now);
    }

    void decide(LocalDate judgmentDate, Outcome outcome, String summary, boolean isFinal) {
        this.judgmentDate = judgmentDate;
        this.outcome = outcome;
        this.summary = summary;
        this.isFinal = isFinal;
    }

    void touch(ActorRef actor, Instant now) {
        this.updatedAt = now;
        this.updatedBy = actor.id();
    }

    UUID getId() {
        return id;
    }

    UUID getProceedingId() {
        return proceedingId;
    }

    LocalDate getJudgmentDate() {
        return judgmentDate;
    }

    Outcome getOutcome() {
        return outcome;
    }

    String getSummary() {
        return summary;
    }

    boolean isFinal() {
        return isFinal;
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
}
