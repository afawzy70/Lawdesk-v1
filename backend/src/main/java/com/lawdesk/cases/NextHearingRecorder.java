package com.lawdesk.cases;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * The port through which a case registered by the government intake gets its optional next hearing in the same
 * transaction. Implemented by the hearings module, so the case module does not depend on it.
 */
public interface NextHearingRecorder {

    /**
     * Schedules a hearing in the first-instance proceeding of {@code caseId} as the signed-in user.
     *
     * @throws com.lawdesk.api.FieldErrorException with field {@code nextHearing.hearingDate} when the date is refused
     * @throws com.lawdesk.api.BusinessRuleException when a hearing rule refuses it (before the filing date, …)
     */
    void record(UUID caseId, LocalDate hearingDate, LocalTime hearingTime, String notes);
}
