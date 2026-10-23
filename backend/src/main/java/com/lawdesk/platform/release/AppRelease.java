package com.lawdesk.platform.release;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;

/** An application start recorded against this database (see {@code V1__app_release.sql} and {@code V2}). */
@Entity
public class AppRelease {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Same rule as the database check in {@code V2__app_release_version_visible.sql}: not only whitespace. */
    @NotNull
    @Pattern(regexp = "(?s).*[^\\p{IsWhite_Space}\\u200B\\uFEFF].*", message = "must contain a visible character")
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
