package com.lawdesk.access;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Bodies and views of the access administration endpoints (LD-050). */
final class AccessRequests {

    private AccessRequests() {
    }

    record GrantCreate(@NotNull UUID userId, @NotNull @Pattern(regexp = "VIEW|CONTRIBUTE") String level,
            @NotBlank @Size(max = 500) String reason, Instant expiresAt) {
    }

    record Grant(UUID id, UUID caseId, UUID userId, String userName, String level, String reason, UUID grantedBy,
            Instant grantedAt, Instant expiresAt, Instant revokedAt, UUID revokedBy) {
    }

    record ScopeCreate(@NotNull @Pattern(regexp = "ROLE|USER") String subjectType,
            @NotBlank @Size(max = 64) String subjectRef,
            @NotNull @Pattern(regexp = "ALL|ORG_UNIT|OWN_UNIT|CASE_TYPE") String scopeType,
            @Size(max = 64) String scopeRef) {
    }

    record Scope(UUID id, String subjectType, String subjectRef, String scopeType, String scopeRef, Instant createdAt) {
    }

    record Confidentiality(@NotNull @Pattern(regexp = "NORMAL|RESTRICTED") String level,
            @NotBlank @Size(max = 500) String reason, long version) {
    }
}
