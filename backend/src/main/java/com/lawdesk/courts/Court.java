package com.lawdesk.courts;

import java.time.Instant;
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

/** A court of the reference data (V300__LD-018_court.sql). Deactivated instead of deleted. */
@Entity
public class Court {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 32)
    private String code;

    @Column(nullable = false, length = 200)
    private String nameAr;

    @Column(nullable = false, length = 200)
    private String nameEn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CourtLevel level;

    @Column(length = 100)
    private String governorate;

    @Column(nullable = false)
    private boolean active = true;

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

    protected Court() {
    }

    Court(ActorRef actor, Instant now) {
        this.createdAt = now;
        this.createdBy = actor.id();
        touch(actor, now);
    }

    void describe(String code, String nameAr, String nameEn, CourtLevel level, String governorate) {
        this.code = code;
        this.nameAr = nameAr;
        this.nameEn = nameEn;
        this.level = level;
        this.governorate = governorate;
    }

    void setActive(boolean active) {
        this.active = active;
    }

    void touch(ActorRef actor, Instant now) {
        this.updatedAt = now;
        this.updatedBy = actor.id();
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getNameAr() {
        return nameAr;
    }

    public String getNameEn() {
        return nameEn;
    }

    public CourtLevel getLevel() {
        return level;
    }

    public String getGovernorate() {
        return governorate;
    }

    public boolean isActive() {
        return active;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
