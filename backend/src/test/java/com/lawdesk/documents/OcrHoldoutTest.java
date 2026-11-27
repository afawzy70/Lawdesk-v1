package com.lawdesk.documents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * The hold-out corpus ({@link OcrHoldoutCorpus}) with the real engine, reported separately from the tuned corpus and as three separate
 * things (see {@link OcrMeasure}). A term not found fails the test unless it is in {@link #STILL_MISSED}; a listed term found again, and
 * any false positive not in {@link #FALSE_POSITIVES}, fail it as well. The lists are what the first measurement showed; nothing was tuned
 * to shorten them (the pass set was frozen before these pages existed). Skipped, visibly, without Tesseract and Arabic data.
 * With {@code -Docr.report=<file>} the table is written.
 */
class OcrHoldoutTest {

    /** Terms not found (primary text or one alternate word) on the hold-out pages, by file. Filled from the first measurement. */
    static final Map<String, List<String>> STILL_MISSED = HoldoutExpectations.STILL_MISSED;

    /** False positives (a query for a word that is not on the page found a hit), by file. */
    static final Map<String, List<String>> FALSE_POSITIVES = HoldoutExpectations.FALSE_POSITIVES;

    @Test
    void theHoldoutPagesAreMeasuredAndEveryMissAndFalseHitIsListed() throws Exception {
        TesseractOcrEngine engine = new TesseractOcrEngine("tesseract", "ara+eng");
        assumeTrue(engine.status().available(), "BLOCKED: no Tesseract with Arabic data on this machine (" + engine.status().detail() + ")");
        StringBuilder table = new StringBuilder(OcrMeasure.Row.header());
        List<String> unexpected = new ArrayList<>();
        for (OcrHoldoutCorpus.Page page : OcrHoldoutCorpus.pages()) {
            byte[] bytes = Files.readAllBytes(OcrExactTermsTest.root().resolve(OcrHoldoutCorpus.DIR).resolve(page.file()));
            OcrMeasure.Row row = OcrMeasure.measure(page.file(), page.drawing(), page.terms(), bytes, engine);
            table.append(row.line());
            List<String> listed = STILL_MISSED.getOrDefault(page.file(), List.of());
            row.missed().stream().filter(t -> !listed.contains(t)).forEach(t -> unexpected.add(page.file() + ": «" + t + "» missed"));
            listed.stream().filter(t -> !row.missed().contains(t)).forEach(t -> unexpected.add(page.file() + ": «" + t + "» listed but found"));
            List<String> falseListed = FALSE_POSITIVES.getOrDefault(page.file(), List.of());
            row.falseHits().stream().filter(t -> !falseListed.contains(t)).forEach(t -> unexpected.add(page.file() + ": false hit «" + t + "»"));
            falseListed.stream().filter(t -> !row.falseHits().contains(t)).forEach(t -> unexpected.add(page.file() + ": «" + t + "» listed as false hit but not hit"));
        }
        String report = System.getProperty("ocr.report");
        if (report != null) {
            Files.writeString(Path.of(report), table.toString());
        }
        assertThat(unexpected).as("hold-out misses / false hits not on the documented lists").isEmpty();
    }
}
