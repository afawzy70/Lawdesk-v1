package com.lawdesk.cases;

import java.util.UUID;
import java.util.regex.Pattern;

import com.lawdesk.api.NotFoundException;

/** Input normalisation shared by the case API (docs/delivery/PHASE-3-ACCEPTANCE.md, common rule 4). */
final class Text {

    /** Same rule as the {@code legal_case.reference_no} check in {@code V200__LD-012_legal_case.sql}. */
    static final String REFERENCE_NO = "[A-Za-z0-9][A-Za-z0-9/._-]*";

    private static final Pattern CANONICAL_UUID =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private Text() {
    }

    static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * A path id; anything that is not a UUID cannot name a stored resource and is answered like an unknown id (404),
     * never with a 500 from a failed conversion.
     */
    static UUID id(String value, String resource) {
        if (value == null || !CANONICAL_UUID.matcher(value).matches()) {
            throw new NotFoundException(resource);
        }
        return UUID.fromString(value);
    }
}
