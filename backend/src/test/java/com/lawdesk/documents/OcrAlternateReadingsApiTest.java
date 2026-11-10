package com.lawdesk.documents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.lawdesk.security.Permission;

/**
 * QA-RT-001, the storage and search half (the engine half is {@link OcrExactTermsTest}): the words a second reading of a page
 * was sure of are searchable but are not passed off as the page text; a word that no reading found is not found (a search
 * never succeeds on a word that is missing from the extracted text); the engine's doubt is stored and returned; a user outside
 * the case sees nothing of any of it; and recognition never touches the stored original.
 */
class OcrAlternateReadingsApiTest extends ArchiveTestSupport {

    private static final String SEARCH = "/api/v1/documents/search";

    private static OcrEngine.Reading reading(String text, String alternates, int words, int uncertain) {
        return new OcrEngine.Reading(text, alternates, words, uncertain, List.of(), 3);
    }

    @Test
    void wordsOfASecondReadingAreSearchableButNotPartOfThePageText() throws Exception {
        ocr.reading = image -> reading("المادة 12 من القانون المدني الكوبتي\n02-5 رقم عقد", "الكويتي LD-2026/045", 9, 2);
        UUID owner = user("Owner");
        UUID caseId = newCase(owner);
        byte[] png = ArchiveFiles.png("scan");
        UUID id = attach(member(owner), caseId, file("scan.png", "image/png", png));
        assertThat(runRecognition()).isOne();

        get(member(owner), "/api/v1/documents/" + id + "/text?withText=true").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OCR_DONE"))
                .andExpect(jsonPath("$.text").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("الكويتي"))))
                .andExpect(jsonPath("$.alternateReadings").value("الكويتي LD-2026/045"))
                .andExpect(jsonPath("$.wordsRead").value(9)).andExpect(jsonPath("$.wordsUncertain").value(2));
        // the text without withText carries the counts (so a list can say "recognition uncertain") but no text
        get(member(owner), "/api/v1/documents/" + id + "/text").andExpect(jsonPath("$.wordsUncertain").value(2))
                .andExpect(jsonPath("$.text").doesNotExist()).andExpect(jsonPath("$.alternateReadings").doesNotExist());

        // The word and the reference are found whole and by parts (each part is searched, 'LD-2026/045' is 'ld 2026 045').
        for (String query : new String[] {"الكويتي", "الكويتي القانون", "LD-2026/045", "LD 2026", "045", "ld-2026/045 القانون"}) {
            get(member(owner), SEARCH + "?q=" + query).andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(1))
                    .andExpect(jsonPath("$.items[0].documentId").value(id.toString()));
        }
        // A word neither reading found is not found: the misreading «الكوبتي» is in the text, «الوزارة» is in neither.
        get(member(owner), SEARCH + "?q=الوزارة").andExpect(jsonPath("$.totalItems").value(0));
        get(member(owner), SEARCH + "?q=LD-2026/999").andExpect(jsonPath("$.totalItems").value(0));
        // the unified search reads the alternates too
        String unified = body(get(outsider(owner, Permission.CASE_VIEW, Permission.SEARCH_ALL, Permission.DOCUMENT_VIEW),
                "/api/v1/search?q=الكويتي&types=DOCUMENT").andExpect(status().isOk()));
        assertThat(unified).contains(id.toString());
    }

    @Test
    void aReadingWithoutAlternatesStaysAsBeforeAndCarriesNoDoubt() throws Exception {
        ocr.reader = image -> "مذكرة دفاع الشركة";
        UUID id = attach(everything(), newCase(), file("scan.png", "image/png", ArchiveFiles.png("scan")));
        runRecognition();
        get(everything(), "/api/v1/documents/" + id + "/text?withText=true").andExpect(jsonPath("$.status").value("OCR_DONE"))
                .andExpect(jsonPath("$.alternateReadings").doesNotExist()).andExpect(jsonPath("$.wordsUncertain").value(0));
        get(everything(), SEARCH + "?q=مذكرة").andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void nothingOfTheAlternateReadingsReachesAUserWhoCannotSeeTheCase() throws Exception {
        ocr.reading = image -> reading("نص ظاهر", "RESTRICTEDREF-2026/045 الكويتي", 4, 1);
        UUID owner = user("Owner");
        UUID stranger = UUID.randomUUID();
        UUID id = attach(member(owner), newCase(owner), file("scan.png", "image/png", ArchiveFiles.png("scan")));
        runRecognition();
        get(member(owner), SEARCH + "?q=RESTRICTEDREF-2026/045").andExpect(jsonPath("$.totalItems").value(1));

        String hidden = body(get(outsider(stranger), SEARCH + "?q=RESTRICTEDREF-2026/045").andExpect(jsonPath("$.totalItems").value(0)));
        assertThat(hidden).doesNotContain("RESTRICTEDREF").doesNotContain(id.toString());
        String hiddenWords = body(get(outsider(stranger), SEARCH + "?q=الكويتي").andExpect(jsonPath("$.totalItems").value(0)));
        assertThat(hiddenWords).doesNotContain(id.toString());
        String unified = body(get(outsider(stranger, Permission.CASE_VIEW, Permission.SEARCH_ALL, Permission.DOCUMENT_VIEW),
                "/api/v1/search?q=RESTRICTEDREF&types=DOCUMENT").andExpect(status().isOk()));
        assertThat(unified).doesNotContain(id.toString()).contains("\"total\":0");
        get(outsider(stranger), "/api/v1/documents/" + id + "/text?withText=true").andExpect(status().isNotFound());
    }

    @Test
    void recognitionLeavesTheStoredOriginalByteForByte() throws Exception {
        ocr.reading = image -> reading("نص", "كلمة", 2, 0);
        byte[] png = ArchiveFiles.png("original bytes");
        UUID id = attach(everything(), newCase(), file("scan.png", "image/png", png));
        String before = jdbc.queryForObject("select sha256 from document_version where document_id = ?", String.class, id);
        runRecognition();
        assertThat(jdbc.queryForObject("select sha256 from document_version where document_id = ?", String.class, id))
                .isEqualTo(before).isEqualTo(sha256(png));
        get(everything(), "/api/v1/documents/" + id + "/content").andExpect(status().isOk()).andExpect(content().bytes(png));
        assertThat(count("select count(*) from document_version where document_id = ?", id)).isOne();
    }

    /**
     * With the real engine (skipped where Tesseract with Arabic data is absent): the two pages QA-RT-001 was found on are
     * uploaded as they are, read, and searched for the word and the reference that were missing; the stored file is unchanged.
     */
    @Test
    void theQaFixturesAreFoundThroughTheArchiveSearchWithTheRealEngine() throws Exception {
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
        byte[] clear = Files.readAllBytes(OcrExactTermsTest.root().resolve(OcrExactCorpus.JOURNEY_DIR).resolve("ocr-clear-ar.png"));
        byte[] mixed = Files.readAllBytes(OcrExactTermsTest.root().resolve(OcrExactCorpus.JOURNEY_DIR).resolve("ocr-mixed.png"));
        UUID clearId = attach(member(owner), caseId, file("ocr-clear-ar.png", "image/png", clear));
        UUID mixedId = attach(member(owner), caseId, file("ocr-mixed.png", "image/png", mixed));
        assertThat(runRecognition()).isEqualTo(2);
        get(member(owner), SEARCH + "?q=الكويتي&caseId=" + caseId).andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].documentId").value(clearId.toString()));
        get(member(owner), SEARCH + "?q=LD-2026/045&caseId=" + caseId).andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].documentId").value(mixedId.toString()));
        get(member(owner), "/api/v1/documents/" + clearId + "/content").andExpect(content().bytes(clear));
        get(member(owner), "/api/v1/documents/" + mixedId + "/content").andExpect(content().bytes(mixed));
        assertThat(jdbc.queryForObject("select sha256 from document_version where document_id = ?", String.class, mixedId))
                .isEqualTo(sha256(mixed));
    }

    private static String sha256(byte[] bytes) throws Exception {
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
