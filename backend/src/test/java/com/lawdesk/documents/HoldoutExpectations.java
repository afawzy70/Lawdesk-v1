package com.lawdesk.documents;

import java.util.List;
import java.util.Map;

/** The documented misses and false hits on the hold-out pages (from the measurement; see docs/evidence/DOC-R3-OCR/README.md). */
final class HoldoutExpectations {

    static final Map<String, List<String>> STILL_MISSED = Map.of();

    static final Map<String, List<String>> FALSE_POSITIVES = Map.of();

    private HoldoutExpectations() {
    }
}
