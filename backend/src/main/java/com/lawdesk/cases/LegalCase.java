package com.lawdesk.cases;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A stored legal case as the API returns it (docs/delivery/PHASE-3-ACCEPTANCE.md, LD-012 criterion 1). */
public record LegalCase(
        UUID id,
        String referenceNo,
        String title,
        CaseType caseType,
        String subject,
        LocalDate openedOn,
        CaseStatus status,
        long version,
        Instant createdAt,
        UUID createdBy,
        Instant updatedAt,
        UUID updatedBy) {

    CaseSummary summary() {
        return new CaseSummary(id, referenceNo, title, status.name());
    }
}
