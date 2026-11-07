package com.lawdesk.cases;

import java.time.LocalDate;
import java.util.UUID;

/**
 * The port through which a case registered by the government intake gets its first-instance proceeding in the same
 * transaction. Implemented by the proceedings module, so the case module does not depend on it.
 */
public interface FirstInstanceRecorder {

    /**
     * Records the first-instance proceeding of {@code caseId} as the signed-in user.
     *
     * @throws com.lawdesk.api.BusinessRuleException when a ladder rule refuses it (inactive court, wrong level, …)
     */
    void record(UUID caseId, UUID courtId, String courtCaseNumber, int courtCaseYear, LocalDate filingDate);
}
