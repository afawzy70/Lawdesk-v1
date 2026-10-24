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

/**
 * One stage of a case before a court (V301__LD-019_proceeding.sql). Case, court and parent are kept as ids: cases
 * belong to Workstream B and are reached through {@code CaseLookup}; stage and parent never change once recorded.
 */
@Entity
class Proceeding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID caseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private Stage stage;

    @Column(nullable = false)
    private UUID courtId;

    @Column(nullable = false, length = 32)
    private String courtCaseNumber;

    @Column(nullable = false)
    private int courtCaseYear;

    @Column(nullable = false)
    private LocalDate filingDate;

    @Column(updatable = false)
    private UUID parentProceedingId;

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

    protected Proceeding() {
    }

    Proceeding(UUID caseId, Stage stage, UUID parentProceedingId, ActorRef actor, Instant now) {
        this.caseId = caseId;
        this.stage = stage;
        this.parentProceedingId = parentProceedingId;
        this.createdAt = now;
        this.createdBy = actor.id();
        touch(actor, now);
    }

    void place(UUID courtId, String courtCaseNumber, int courtCaseYear, LocalDate filingDate) {
        this.courtId = courtId;
        this.courtCaseNumber = courtCaseNumber;
        this.courtCaseYear = courtCaseYear;
        this.filingDate = filingDate;
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

    Stage getStage() {
        return stage;
    }

    UUID getCourtId() {
        return courtId;
    }

    String getCourtCaseNumber() {
        return courtCaseNumber;
    }

    int getCourtCaseYear() {
        return courtCaseYear;
    }

    LocalDate getFilingDate() {
        return filingDate;
    }

    UUID getParentProceedingId() {
        return parentProceedingId;
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

    /** "1234/2025", as the number is written in Egyptian court records. */
    String numberAndYear() {
        return courtCaseNumber + "/" + courtCaseYear;
    }
}
