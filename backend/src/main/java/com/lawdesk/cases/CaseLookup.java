package com.lawdesk.cases;

import java.util.Optional;
import java.util.UUID;

/**
 * The port other modules use to reach cases (architecture/CONTRACTS.md §5). Workstream C never writes case tables: it
 * looks cases up here and adds its own entries to the case timeline through {@link #recordEvent}.
 */
public interface CaseLookup {

    /** The case with this id, or empty when there is none. */
    Optional<CaseSummary> find(UUID id);

    /**
     * Appends an event to the case timeline, in the caller's transaction and as the signed-in user, so it must be
     * called while handling an authenticated request.
     *
     * @param type    an upper-case code such as {@code PROCEEDING_RECORDED}: {@code [A-Z][A-Z0-9_]{0,63}}
     * @param summary what happened, 1–500 characters after trimming
     * @throws com.lawdesk.api.NotFoundException when the case does not exist
     * @throws IllegalArgumentException          when the type or the summary is malformed
     */
    void recordEvent(UUID caseId, String type, String summary);
}
