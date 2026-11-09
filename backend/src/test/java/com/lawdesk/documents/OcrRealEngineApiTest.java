package com.lawdesk.documents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.lawdesk.security.Permission;

/**
 * LD-DOC-13-004 end to end with the REAL engine (skipped, visibly, where Tesseract with Arabic data is not installed): a
 * synthetic scan is stored, read by Tesseract, searchable in the archive search and in the unified search with Arabic
 * spelling variants, invisible to a user without access to the case, and a failure of the engine never loses the file.
 */
class OcrRealEngineApiTest extends ArchiveTestSupport {

    private static final String SEARCH = "/api/v1/documents/search";
    private static final List<String> LINES = List.of("مذكرة دفاع الشركة المدعى عليها", "المادة 12 من القانون المدني",
            "تاريخ الجلسة القادمة الأحد", "SYNTH-DOC scan Quetzal 4455");
    private static final OcrCorpus.Case SCAN = new OcrCorpus.Case("e2e", "scan", 300, LINES, 2.0, 12, 0.6, 0.8, false);

    private TesseractOcrEngine real;

    @BeforeEach
    void useTheRealEngine() {
        real = new TesseractOcrEngine("tesseract", "ara+eng", true, 3);
        assumeTrue(real.status().available(), "BLOCKED: no Tesseract with Arabic data on this machine (" + real.status().detail() + ")");
        ocr.reader = image -> {
            try {
                return real.recognise(image);
            } catch (OcrEngine.OcrException e) {
                throw new IllegalStateException(e);
            }
        };
    }

    @Test
    void aScannedImageIsReadFoundByArabicVariantsAndHiddenFromWhoCannotSeeTheCase() throws Exception {
        UUID owner = user("Owner");
        UUID stranger = UUID.randomUUID();
        UUID caseId = newCase(owner);
        byte[] png = OcrCorpus.png(SCAN);
        UUID id = attach(member(owner), caseId, file("scan.png", "image/png", png));
        get(member(owner), "/api/v1/documents/" + id + "/text").andExpect(jsonPath("$.status").value("PENDING"));
        assertThat(runRecognition()).isOne();
        get(member(owner), "/api/v1/documents/" + id + "/text?withText=true").andExpect(jsonPath("$.status").value("OCR_DONE"))
                .andExpect(jsonPath("$.engine").value("stub")); // the stub carries the real engine's reading

        // Archive search with spelling variants (no diacritics, taa marbuta as heh, definite article, alef forms).
        for (String query : new String[] {"مذكرة دفاع", "مذكره", "القانون المدني", "قانون مدني", "الجلسه القادمه", "Quetzal 4455"}) {
            get(member(owner), SEARCH + "?q=" + query).andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(1))
                    .andExpect(jsonPath("$.items[0].documentId").value(id.toString()));
        }
        // Unified search finds it through the text too (the file name and title say nothing of these words).
        String unified = body(get(outsider(owner, Permission.CASE_VIEW, Permission.SEARCH_ALL, Permission.DOCUMENT_VIEW),
                "/api/v1/search?q=Quetzal&types=DOCUMENT").andExpect(status().isOk()));
        assertThat(unified).contains(id.toString()).contains("DOCUMENT_TEXT");

        // Access: nothing of the text, the snippet or the hit reaches a user without the case.
        String hidden = body(get(outsider(stranger), SEARCH + "?q=Quetzal").andExpect(jsonPath("$.totalItems").value(0)));
        assertThat(hidden).doesNotContain("Quetzal").doesNotContain(id.toString());
        String hiddenUnified = body(get(outsider(stranger, Permission.CASE_VIEW, Permission.SEARCH_ALL, Permission.DOCUMENT_VIEW),
                "/api/v1/search?q=Quetzal&types=DOCUMENT").andExpect(status().isOk()));
        assertThat(hiddenUnified).doesNotContain(id.toString()).contains("\"total\":0").doesNotContain("snippet"); // the echoed query is not a result
        get(outsider(stranger), "/api/v1/documents/" + id + "/text?withText=true").andExpect(status().isNotFound());
    }

    @Test
    void aScannedPdfIsReadThroughTheRenderedPage() throws Exception {
        UUID id = attach(everything(), newCase(), pdfFile("scan.pdf", ArchiveFiles.scannedPdf("القانون المدني الكويتي\nمذكرة دفاع")));
        runRecognition();
        get(everything(), "/api/v1/documents/" + id + "/text").andExpect(jsonPath("$.status").value("OCR_DONE")).andExpect(jsonPath("$.method").value("OCR"));
        get(everything(), SEARCH + "?q=القانون المدني").andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void anEngineFailureNeverLosesTheFileAndARetryReadsIt() throws Exception {
        byte[] png = OcrCorpus.png(SCAN);
        ocr.failing = true;
        UUID id = attach(everything(), newCase(), file("scan.png", "image/png", png));
        for (int i = 0; i < 3; i++) {
            runRecognition();
        }
        get(everything(), "/api/v1/documents/" + id + "/text").andExpect(jsonPath("$.status").value("FAILED"));
        // The stored file is still there, byte for byte, and can be downloaded.
        get(everything(), "/api/v1/documents/" + id + "/content").andExpect(status().isOk()).andExpect(content().bytes(png));
        assertThat(count("select count(*) from document_version where document_id = ?", id)).isOne();
        // The engine recovers; a user asks for the file to be read again and it becomes searchable.
        ocr.failing = false;
        post(everything(), "/api/v1/documents/" + id + "/text/reprocess", "{}").andExpect(jsonPath("$.status").value("PENDING"));
        runRecognition();
        get(everything(), "/api/v1/documents/" + id + "/text").andExpect(jsonPath("$.status").value("OCR_DONE"));
        get(everything(), SEARCH + "?q=Quetzal").andExpect(jsonPath("$.totalItems").value(1));
    }
}
