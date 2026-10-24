package com.lawdesk.cases;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One row of the case list (docs/delivery/PHASE-3-ACCEPTANCE.md, LD-015 criterion 1). */
record CaseListItem(UUID id, String referenceNo, String title, CaseType caseType, CaseStatus status,
        LocalDate openedOn, Instant updatedAt) {
}
