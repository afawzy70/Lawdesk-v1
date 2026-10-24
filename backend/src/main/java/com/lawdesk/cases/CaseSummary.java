package com.lawdesk.cases;

import java.util.UUID;

/** What other modules know about a case (architecture/CONTRACTS.md §5); {@code status} is a {@link CaseStatus} name. */
public record CaseSummary(UUID id, String referenceNo, String title, String status) {
}
