package com.lawdesk.proceedings;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * QAD-032: records of other modules dated against a proceeding's filing date, such as the hearings of Phase 5. Those
 * modules implement this port, so a proceeding's filing date can never be corrected to after them, while this package
 * stays independent of theirs. The filing-date correction calls every implementation while it holds the proceeding's
 * row lock; the implementing modules take the same lock ({@link ProceedingLookup#lockForUpdate}) before they compare
 * their own dates with the filing date, so the two kinds of write cannot interleave.
 */
public interface ProceedingDependentDates {

    /** The earliest date of this module's records of the proceeding, or empty when it has none. */
    Optional<LocalDate> earliestDependentDate(UUID proceedingId);

    /** The stable rule code answered (422) when a filing date would move after that date. */
    String rule();
}
