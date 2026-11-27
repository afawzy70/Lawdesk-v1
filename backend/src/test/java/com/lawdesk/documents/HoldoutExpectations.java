package com.lawdesk.documents;

import java.util.List;
import java.util.Map;

/** The documented misses and false hits on the hold-out pages (from the measurement; see docs/evidence/DOC-R3-OCR/README.md). */
final class HoldoutExpectations {

    static final Map<String, List<String>> STILL_MISSED = Map.of(
            "h-ar-150dpi-8pt-noise.jpg", List.of("الدائرة", "الاختصاص"),
            // dates: the run is stored in reversed order inside an Arabic line, or one digit group is misread
            "h-dates-300dpi-10pt.png", List.of("2026-11-09"),
            "h-dates-150dpi-8pt.png", List.of("2026-11-09"),
            "h-dates-200dpi-10pt-skew2-noise.jpg", List.of("2026-11-09"),
            "h-refs-300dpi-10pt.png", List.of("م/2025/3041"),
            "h-table-300dpi-10pt.png", List.of("2026-02-10"),
            "h-table-150dpi-8pt.png", List.of("2026-02-10"));

    static final Map<String, List<String>> FALSE_POSITIVES = Map.of(
            // the noisy page reads ٤٥٦٧/٢٠٢٥ as 4567/2028: a search for the wrong number finds the page (a misreading, not an invented word)
            "h-refs-200dpi-8pt-noise.jpg", List.of("٤٥٦٧/٢٠٢8"));

    /** Terms of the multi-page PDF not found by the archive search, as "page:term". */
    static final List<String> PDF_STILL_MISSED = List.of("2:٤٥٦٧/٢٠٢٥", "3:الخبير", "3:التقاضي", "3:مصاريف", "3:الإعلان", "3:350.500", "3:75.250", "3:2026-02-10", "4:1447/09/25", "4:2026-11-09", "4:٢٠٢٦-١٢-٠١", "4:الإعلان", "4:الاستحقاق");

    private HoldoutExpectations() {
    }
}
