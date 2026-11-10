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
 * Exact terms on the committed synthetic pages ({@link OcrExactCorpus}) with the real engine: every term a user would search
 * for must be in the text the engine read (the main reading plus the confident words of the other passes). A term that is not
 * found fails the test unless it is in {@link #STILL_MISSED}, the documented list of misses; a listed term that is found again
 * also fails, so the list cannot go stale. The two terms QA found missing (QA-RT-001) have no exemption.
 * Skipped, visibly, where Tesseract with Arabic data is not installed. With {@code -Docr.report=<file>} the before/after table is written.
 */
class OcrExactTermsTest {

    /** Terms still missed after the change, by file; every entry is a measured failure (docs/evidence/DOC-R3-OCR/README.md). */
    static final Map<String, List<String>> STILL_MISSED = Map.of(
            // Read as "15-03-2026": the engine's right-to-left ordering reverses the run of a date inside an Arabic line. Every
            // component is in the text and the archive search matches by parts, but the run "2026 03 15" is not there.
            "ocr-mixed.png", List.of("2026-03-15"),
            // Table cells: two Arabic words in ruled cells are misread by every pass tried (no reading of any variant has them).
            "x-table-200dpi.png", List.of("المدعية", "الخبير"),
            // Small type (9 pt at 150 dpi) under noise: the main reading finds no word at all; the other passes find five of seven terms.
            "x-ar-150dpi-9pt-noise.jpg", List.of("الكويتية", "الكويتي"));

    static Path root() {
        Path here = Path.of("").toAbsolutePath();
        return Files.isDirectory(here.resolve("tools")) ? here : here.getParent();
    }

    static byte[] picture(OcrExactCorpus.Page page) throws Exception {
        Path dir = root().resolve(page.journey() ? OcrExactCorpus.JOURNEY_DIR : OcrExactCorpus.EXTRA_DIR);
        return Files.readAllBytes(dir.resolve(page.file()));
    }

    @Test
    void theTermsOfEveryPageAreFoundInTheExtractedText() throws Exception {
        TesseractOcrEngine after = new TesseractOcrEngine("tesseract", "ara+eng");
        TesseractOcrEngine before = new TesseractOcrEngine("tesseract", "ara+eng", true, 3, false);
        OcrEngine.Status status = after.status();
        assumeTrue(status.available(), "BLOCKED: no Tesseract with Arabic data on this machine (" + status.detail() + ")");
        String report = System.getProperty("ocr.report");
        StringBuilder table = new StringBuilder(OcrMeasure.Row.header());
        StringBuilder tableBefore = new StringBuilder(OcrMeasure.Row.header());
        List<String> unexpected = new ArrayList<>();
        for (OcrExactCorpus.Page page : OcrExactCorpus.pages()) {
            byte[] bytes = picture(page);
            OcrEngine.Reading reading = after.read(bytes);
            // found = in the main reading, or inside ONE alternate word (never across two alternates)
            List<String> missed = page.terms().stream()
                    .filter(t -> !OcrExactCorpus.contains(reading.text(), t) && !OcrMeasure.containsInAlternate(reading, t)).toList();
            List<String> listed = STILL_MISSED.getOrDefault(page.file(), List.of());
            for (String t : missed) {
                if (!listed.contains(t)) {
                    unexpected.add(page.file() + ": «" + t + "» is not in the extracted text");
                }
            }
            for (String t : listed) {
                if (!missed.contains(t)) {
                    unexpected.add(page.file() + ": «" + t + "» is listed as missed but was found; remove it from STILL_MISSED");
                }
            }
            if (reading.words() == 0 || !reading.alternates().isEmpty() || !missed.isEmpty()) {
                // doubt is never silent: a page whose main reading was empty, disagreed with another pass, or lost a term says so
                if (reading.uncertainWords() == 0) {
                    unexpected.add(page.file() + ": the reading was empty, disagreed or missed a term but reports no uncertain words");
                }
            }
            if (report != null) {
                table.append(OcrMeasure.measure(page.file(), page.drawing(), page.terms(), bytes, after).line());
                tableBefore.append(OcrMeasure.measure(page.file(), page.drawing(), page.terms(), bytes, before).line());
            }
        }
        if (report != null) {
            Files.writeString(Path.of(report), table.toString());
            Files.writeString(Path.of(report + ".before"), tableBefore.toString());
        }
        assertThat(unexpected).as("terms missing from the extracted text (or a stale STILL_MISSED entry)").isEmpty();
    }

    /** QA-RT-001: the legal word and the case reference the independent run did not find; no exemption, ever. */
    @Test
    void theTwoTermsQaFoundMissingAreInTheText() throws Exception {
        TesseractOcrEngine engine = new TesseractOcrEngine("tesseract", "ara+eng");
        OcrEngine.Status status = engine.status();
        assumeTrue(status.available(), "BLOCKED: no Tesseract with Arabic data on this machine (" + status.detail() + ")");
        OcrEngine.Reading clear = engine.read(Files.readAllBytes(root().resolve(OcrExactCorpus.JOURNEY_DIR).resolve("ocr-clear-ar.png")));
        OcrEngine.Reading mixed = engine.read(Files.readAllBytes(root().resolve(OcrExactCorpus.JOURNEY_DIR).resolve("ocr-mixed.png")));
        assertThat(OcrExactCorpus.contains(clear.text() + "\n" + clear.alternateText(), "الكويتي")).as("الكويتي").isTrue();
        assertThat(OcrExactCorpus.contains(mixed.text() + "\n" + mixed.alternateText(), "LD-2026/045")).as("LD-2026/045").isTrue();
    }
}
