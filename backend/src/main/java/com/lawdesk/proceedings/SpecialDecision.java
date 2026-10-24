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

/** A special decision taken within a proceeding (V303__LD-023_special_decision.sql). */
@Entity
class SpecialDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID proceedingId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DecisionType decisionType;

    @Column(nullable = false)
    private LocalDate decisionDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DecisionEffect effect;

    @Column(length = 4000)
    private String summary;

    private UUID judgmentId;

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

    protected SpecialDecision() {
    }

    SpecialDecision(UUID proceedingId, ActorRef actor, Instant now) {
        this.proceedingId = proceedingId;
        this.createdAt = now;
        this.createdBy = actor.id();
        touch(actor, now);
    }

    void decide(DecisionType decisionType, LocalDate decisionDate, DecisionEffect effect, String summary,
            UUID judgmentId) {
        this.decisionType = decisionType;
        this.decisionDate = decisionDate;
        this.effect = effect;
        this.summary = summary;
        this.judgmentId = judgmentId;
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

    DecisionType getDecisionType() {
        return decisionType;
    }

    LocalDate getDecisionDate() {
        return decisionDate;
    }

    DecisionEffect getEffect() {
        return effect;
    }

    String getSummary() {
        return summary;
    }

    UUID getJudgmentId() {
        return judgmentId;
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
