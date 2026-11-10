package com.lawdesk.documents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Accuracy of the real engine on the synthetic corpus ({@link OcrCorpus}), against the text that was drawn. The thresholds
 * are not wishes: each is below the minimum measured for its group (docs/evidence/DOC-OCR/README.md) by a margin that
 * covers the difference between engine builds (measured on Tesseract 5.3.4; the image carries 5.5.x). Skipped, visibly,
 * where Tesseract with Arabic data is not installed (the hosted CI runner has none; the image job proves the image has it).
 * With {@code -Docr.report=<file>} the per-case table (raw page vs the page the engine is really given) is written.
 */
class OcrQualityTest {

    private static final String LOW_RESOLUTION = "clear-ar-50dpi,clear-ar-72dpi";

    @Test
    void measuredAccuracyOnTheCorpusStaysAboveTheRecordedThresholds() throws Exception {
        TesseractOcrEngine prepared = new TesseractOcrEngine("tesseract", "ara+eng", true, 3);
        TesseractOcrEngine raw = new TesseractOcrEngine("tesseract", "ara+eng", false, 3);
        OcrEngine.Status status = prepared.status();
        assumeTrue(status.available(), "BLOCKED: no Tesseract with Arabic data on this machine (" + status.detail() + ")");
        String report = System.getProperty("ocr.report");
        StringBuilder table = new StringBuilder(
                "case\tgroup\tdpi\tskew_deg\tdetected_skew\tnopre_char\tnopre_word\tnopre_search\tprep_char\tprep_word\tprep_search\tprep_ms\n");
        List<String> failures = new ArrayList<>();
        for (OcrCorpus.Case c : OcrCorpus.cases()) {
            var image = OcrCorpus.image(c);
            byte[] bytes = OcrCorpus.encode(image, "png");
            String rawText = report != null ? raw.recognise(bytes) : "";
            long t0 = System.nanoTime();
            String text = prepared.recognise(bytes);
            long ms = (System.nanoTime() - t0) / 1_000_000;
            double ch = OcrCorpus.characterAccuracy(c.truth(), text);
            double wd = OcrCorpus.wordAccuracy(c.truth(), text);
            double se = OcrCorpus.searchRecall(c.truth(), text);
            table.append(String.format("%s\t%s\t%d\t%.1f\t%.2f\t%.3f\t%.3f\t%.3f\t%.3f\t%.3f\t%.3f\t%d%n", c.name(), c.group(),
                    c.dpi(), c.angleDegrees(), OcrPreprocessor.skew(image),
                    OcrCorpus.characterAccuracy(c.truth(), rawText), OcrCorpus.wordAccuracy(c.truth(), rawText),
                    OcrCorpus.searchRecall(c.truth(), rawText), ch, wd, se, ms));
            String problem = violation(c, ch, se);
            if (problem != null) {
                failures.add(c.name() + ": " + problem + " (char " + ch + ", word " + wd + ", search " + se + ")");
            }
        }
        if (report != null) {
            Files.writeString(Path.of(report), table.toString());
        }
        assertThat(failures).as("cases below their recorded threshold").isEmpty();
    }

    /** The threshold of a case's group, or null when the case meets it (or belongs to a group that has none by design). */
    static String violation(OcrCorpus.Case c, double characters, double search) {
        if (LOW_RESOLUTION.contains(c.name()) || "diacritics".equals(c.group())) {
            return null; // recorded as limitations, not as a promise
        }
        boolean strict = "clear".equals(c.group()) || "skew".equals(c.group())
                || ("scan".equals(c.group()) && !c.name().contains("mixed") && !c.name().contains("speckle"));
        if (strict) {
            return characters >= 0.95 && search >= 0.90 ? null : "needs character accuracy >= 0.95 and search recall >= 0.90";
        }
        // Mixed Arabic/English lines and digits: the engine may reorder lines or runs, so only what a search needs is promised.
        return search >= 0.80 ? null : "needs search recall >= 0.80";
    }
}
