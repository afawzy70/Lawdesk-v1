package com.lawdesk.proceedings;

import java.math.BigDecimal;
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

    // LD-051 (section 9): the extended judgment. All optional; null means "not specified".
    @Column(length = 100)
    private String chamber;

    private UUID judgmentTypeId;

    private UUID natureId;

    @Column(columnDefinition = "text")
    private String operativeText;

    @Column(columnDefinition = "text")
    private String reasons;

    @Column(precision = 15, scale = 3)
    private BigDecimal costsAmount;

    @Column(precision = 15, scale = 3)
    private BigDecimal lawyerFeesAmount;

    private Boolean appealable;

    private LocalDate notifiedOn;

    @Column(name = "judgment_status", length = 10)
    private String judgmentStatus;

    private UUID copyDocumentId;

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

    void describe(JudgmentRequests.Details d) {
        this.chamber = d.chamber();
        this.judgmentTypeId = d.judgmentTypeId();
        this.natureId = d.natureId();
        this.operativeText = d.operativeText();
        this.reasons = d.reasons();
        this.costsAmount = money(d.costsAmount());
        this.lawyerFeesAmount = money(d.lawyerFeesAmount());
        this.appealable = d.appealable();
        this.notifiedOn = d.notifiedOn();
        this.judgmentStatus = d.status() == null ? null : d.status().name();
        this.copyDocumentId = d.copyDocumentId();
    }

    /** Amounts are KWD with three decimals; the request has been validated to carry at most three. */
    private static BigDecimal money(BigDecimal amount) {
        return amount == null ? null : amount.setScale(3, java.math.RoundingMode.UNNECESSARY);
    }

    String getChamber() {
        return chamber;
    }

    UUID getJudgmentTypeId() {
        return judgmentTypeId;
    }

    UUID getNatureId() {
        return natureId;
    }

    String getOperativeText() {
        return operativeText;
    }

    String getReasons() {
        return reasons;
    }

    BigDecimal getCostsAmount() {
        return costsAmount;
    }

    BigDecimal getLawyerFeesAmount() {
        return lawyerFeesAmount;
    }

    Boolean getAppealable() {
        return appealable;
    }

    LocalDate getNotifiedOn() {
        return notifiedOn;
    }

    String getJudgmentStatus() {
        return judgmentStatus;
    }

    UUID getCopyDocumentId() {
        return copyDocumentId;
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
