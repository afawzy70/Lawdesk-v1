package com.lawdesk.documents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * The multi-page hold-out document (h-multipage.pdf, four pages of different quality) through the whole path with the real engine:
 * upload, rendering, recognition, archive search per term of every page, absent words, and the stored file. Every term must be found
 * unless it is on {@link #STILL_MISSED}; every query for a word that is on no page must find nothing. Skipped, visibly, without Tesseract.
 * With {@code -Docr.report=<file>} each term's source (PRIMARY / ALTERNATE / not found) is written.
 */
class OcrHoldoutPdfApiTest extends ArchiveTestSupport {

    private static final String SEARCH = "/api/v1/documents/search";

    /** Terms of the PDF not found by the search (from the first measurement; the PDF is rendered by the application, not by the corpus). */
    static final List<String> STILL_MISSED = HoldoutExpectations.PDF_STILL_MISSED;

    @Test
    void everyTermOfTheMultiPageDocumentIsFoundOrListedAndAbsentWordsFindNothing() throws Exception {
        TesseractOcrEngine real = new TesseractOcrEngine("tesseract", "ara+eng");
        assumeTrue(real.status().available(), "BLOCKED: no Tesseract with Arabic data on this machine (" + real.status().detail() + ")");
        ocr.reading = image -> {
            try {
                return real.read(image);
            } catch (OcrEngine.OcrException e) {
                throw new IllegalStateException(e);
            }
        };
        UUID owner = user("Owner");
        UUID caseId = newCase(owner);
        byte[] pdf = Files.readAllBytes(OcrExactTermsTest.root().resolve(OcrHoldoutCorpus.DIR).resolve("h-multipage.pdf"));
        UUID id = attach(member(owner), caseId, pdfFile("h-multipage.pdf", pdf));
        assertThat(runRecognition()).isOne();
        get(member(owner), "/api/v1/documents/" + id + "/text").andExpect(jsonPath("$.status").value("OCR_DONE"))
                .andExpect(jsonPath("$.pageCount").value(4));

        StringBuilder report = new StringBuilder("page\tterm\tresult\n");
        List<String> unexpected = new ArrayList<>();
        int page = 0;
        for (OcrHoldoutCorpus.Page p : OcrHoldoutCorpus.pdfPages()) {
            page++;
            for (String term : p.terms()) {
                String body = body(get(member(owner), SEARCH + "?q=" + term + "&caseId=" + caseId));
                String source = body.contains("\"totalItems\":0") ? "NOT_FOUND" : body.contains("\"source\":\"PRIMARY\"") ? "PRIMARY" : "ALTERNATE";
                report.append(page).append('\t').append(term).append('\t').append(source).append('\n');
                boolean listed = STILL_MISSED.contains(page + ":" + term);
                if (source.equals("NOT_FOUND") != listed) {
                    unexpected.add(page + ":" + term + " -> " + source + (listed ? " (listed as missed)" : ""));
                }
            }
        }
        // false results: words that are on no page must find nothing
        for (String absent : OcrMeasure.ABSENT) {
            get(member(owner), SEARCH + "?q=" + absent + "&caseId=" + caseId)
                    .andExpect(jsonPath("$.totalItems").value(0));
        }
        String out = System.getProperty("ocr.report");
        if (out != null) {
            Files.writeString(Path.of(out), report.toString());
        }
        assertThat(unexpected).as("terms of the multi-page document not found (or listed but found)").isEmpty();
        get(member(owner), "/api/v1/documents/" + id + "/content").andExpect(content().bytes(pdf));
    }
}
