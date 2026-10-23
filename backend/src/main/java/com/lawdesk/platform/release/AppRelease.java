package com.lawdesk.platform.release;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;

/** An application start recorded against this database (see {@code V1__app_release.sql}). */
@Entity
public class AppRelease {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 64)
    @Column(nullable = false, length = 64, updatable = false)
    private String version;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private OffsetDateTime startedAt;

    protected AppRelease() {
    }

    public AppRelease(String version) {
        this.version = version;
    }

    public Long getId() {
        return id;
    }

    public String getVersion() {
        return version;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }
}
