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
 * QA-RT-001, the storage and search half (the engine half is {@link OcrExactTermsTest}). The page text is the main reading;
 * the words a second reading was sure of are kept one by one, searched, and reported as such per hit (source ALTERNATE, the
 * word, pass, line and confidence); a word no reading found is not found; nothing is matched across two alternate words or
 * across an alternate and the main reading; the engine's doubt is stored and returned; access rules are the same for both;
 * recognition never touches the stored original; a reprocess replaces the alternates and keeps the earlier text until the new one is read.
 */
class OcrAlternateReadingsApiTest extends ArchiveTestSupport {

    private static final String SEARCH = "/api/v1/documents/search";

    static OcrEngine.Alternate alt(String word, String pass, int line, double confidence) {
        return new OcrEngine.Alternate(word, pass, line, confidence);
    }

    static OcrEngine.Reading reading(String text, List<OcrEngine.Alternate> alternates, int words, int uncertain) {
        return new OcrEngine.Reading(text, alternates, words, uncertain, List.of(), 3);
    }

    @Test
    void anAlternateWordIsSearchableAndEveryHitSaysWhichReadingItRestsOn() throws Exception {
        ocr.reading = image -> reading("المادة 12 من القانون المدني الكوبتي\n02-5 رقم عقد",
                List.of(alt("الكويتي", "original", 3, 92.5), alt("LD-2026/045", "grey-sparse", 1, 88.0)), 9, 2);
        UUID owner = user("Owner");
        UUID caseId = newCase(owner);
        byte[] png = ArchiveFiles.png("scan");
        UUID id = attach(member(owner), caseId, file("scan.png", "image/png", png));
        assertThat(runRecognition()).isOne();

        get(member(owner), "/api/v1/documents/" + id + "/text?withText=true").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OCR_DONE"))
                .andExpect(jsonPath("$.text").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("الكويتي"))))
                .andExpect(jsonPath("$.alternates[1].word").value("الكويتي")).andExpect(jsonPath("$.alternates[1].pass").value("original"))
                .andExpect(jsonPath("$.alternates[1].line").value(3)).andExpect(jsonPath("$.alternates[1].confidence").value(92.5))
                .andExpect(jsonPath("$.wordsRead").value(9)).andExpect(jsonPath("$.wordsUncertain").value(2));
        // without withText: the counts, no text, no alternates
        get(member(owner), "/api/v1/documents/" + id + "/text").andExpect(jsonPath("$.wordsUncertain").value(2))
                .andExpect(jsonPath("$.text").doesNotExist()).andExpect(jsonPath("$.alternates").isEmpty());

        // A word that is in the main reading: PRIMARY, no alternate match.
        get(member(owner), SEARCH + "?q=القانون").andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].source").value("PRIMARY")).andExpect(jsonPath("$.items[0].alternateMatches").isEmpty());
        // Words only an alternate reading found: ALTERNATE, with the word, the pass, the line and the confidence.
        get(member(owner), SEARCH + "?q=الكويتي").andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].documentId").value(id.toString()))
                .andExpect(jsonPath("$.items[0].source").value("ALTERNATE"))
                .andExpect(jsonPath("$.items[0].alternateMatches[0].word").value("الكويتي"))
                .andExpect(jsonPath("$.items[0].alternateMatches[0].pass").value("original"))
                .andExpect(jsonPath("$.items[0].alternateMatches[0].line").value(3))
                .andExpect(jsonPath("$.items[0].alternateMatches[0].confidence").value(92.5));
        for (String query : new String[] {"LD-2026/045", "ld-2026/045", "LD 2026", "045"}) {
            get(member(owner), SEARCH + "?q=" + query).andExpect(jsonPath("$.totalItems").value(1))
                    .andExpect(jsonPath("$.items[0].source").value("ALTERNATE"))
                    .andExpect(jsonPath("$.items[0].alternateMatches[0].pass").value("grey-sparse"));
        }
        // Mixed: one word from the main reading, one from an alternate: still ALTERNATE (it relies on an alternate), both listed honestly.
        get(member(owner), SEARCH + "?q=القانون الكويتي").andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].source").value("ALTERNATE"))
                .andExpect(jsonPath("$.items[0].alternateMatches.length()").value(1));
    }

    @Test
    void aWordNoReadingFoundIsNotFoundAndNothingIsComposedAcrossReadings() throws Exception {
        // Two different alternate words and a main reading that holds "ld 2026": the reference LD-2026/045 is NOT on the page
        // as any one reading saw it, even though its parts occur across the readings.
        ocr.reading = image -> reading("عقد رقم LD-2026 الإيجار",
                List.of(alt("ABC-2026/045", "original", 1, 90), alt("XY-2025/044", "grey-sparse", 2, 85)), 5, 1);
        UUID owner = user("Owner");
        UUID id = attach(member(owner), newCase(owner), file("scan.png", "image/png", ArchiveFiles.png("scan")));
        runRecognition();
        // false results: words that are in no reading
        for (String missing : new String[] {"الوزارة", "LD-2026/999", "الخبير", "REF-1999/001"}) {
            get(member(owner), SEARCH + "?q=" + missing).andExpect(jsonPath("$.totalItems").value(0));
        }
        // phantom reference: ld+2026 from the main reading and 045 from an alternate, or ld from one alternate and 045 from another
        get(member(owner), SEARCH + "?q=LD-2026/045").andExpect(jsonPath("$.totalItems").value(0));
        get(member(owner), SEARCH + "?q=ABC-2025/045").andExpect(jsonPath("$.totalItems").value(0));
        get(member(owner), SEARCH + "?q=XY-2026/044").andExpect(jsonPath("$.totalItems").value(0));
        // what one reading did see is found
        get(member(owner), SEARCH + "?q=ABC-2026/045").andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].documentId").value(id.toString()));
        get(member(owner), SEARCH + "?q=LD-2026").andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].source").value("PRIMARY"));
    }

    @Test
    void aReadingWithoutAlternatesStaysAsBeforeAndCarriesNoDoubt() throws Exception {
        ocr.reader = image -> "مذكرة دفاع الشركة";
        UUID id = attach(everything(), newCase(), file("scan.png", "image/png", ArchiveFiles.png("scan")));
        runRecognition();
        get(everything(), "/api/v1/documents/" + id + "/text?withText=true").andExpect(jsonPath("$.status").value("OCR_DONE"))
                .andExpect(jsonPath("$.alternates").isEmpty());
        get(everything(), SEARCH + "?q=مذكرة").andExpect(jsonPath("$.totalItems").value(1)).andExpect(jsonPath("$.items[0].source").value("PRIMARY"));
    }

    @Test
    void alternatesFollowTheSameAccessRulesAsTheText() throws Exception {
        ocr.reading = image -> reading("نص ظاهر", List.of(alt("RESTRICTEDREF-2026/045", "original", 1, 91), alt("الكويتي", "grey-sparse", 1, 80)), 4, 2);
        UUID owner = user("Owner");
        UUID stranger = UUID.randomUUID();
        UUID restrictedOwner = user("Restricted owner");
        UUID id = attach(member(owner), newCase(owner), file("scan.png", "image/png", ArchiveFiles.png("scan")));
        UUID restricted = restrictedCase(restrictedOwner);
        UUID secret = attach(member(restrictedOwner), restricted, file("secret.png", "image/png", ArchiveFiles.png("secret")));
        runRecognition();
        get(member(owner), SEARCH + "?q=RESTRICTEDREF-2026/045").andExpect(jsonPath("$.totalItems").value(1));

        // out of scope
        String hidden = body(get(outsider(stranger), SEARCH + "?q=RESTRICTEDREF-2026/045").andExpect(jsonPath("$.totalItems").value(0)));
        assertThat(hidden).doesNotContain("RESTRICTEDREF").doesNotContain(id.toString());
        get(outsider(stranger), SEARCH + "?q=الكويتي").andExpect(jsonPath("$.totalItems").value(0));
        String unified = body(get(outsider(stranger, Permission.CASE_VIEW, Permission.SEARCH_ALL, Permission.DOCUMENT_VIEW),
                "/api/v1/search?q=RESTRICTEDREF&types=DOCUMENT").andExpect(status().isOk()));
        assertThat(unified).doesNotContain(id.toString()).contains("\"total\":0");
        get(outsider(stranger), "/api/v1/documents/" + id + "/text?withText=true").andExpect(status().isNotFound());

        // RESTRICTED case: a department-wide viewer (scope ALL) does not see it; its member does; a VIEW grant opens it; revoking closes it
        UUID viewerId = user("Viewer");
        get(viewer(viewerId), SEARCH + "?q=الكويتي&caseId=" + restricted).andExpect(jsonPath("$.totalItems").value(0));
        get(member(restrictedOwner), SEARCH + "?q=الكويتي&caseId=" + restricted).andExpect(jsonPath("$.totalItems").value(1));
        UUID grant = jdbc.queryForObject("""
                insert into case_grant (case_id, user_id, level, reason, granted_by) values (?, ?, 'VIEW', 'review', ?) returning id""",
                UUID.class, restricted, viewerId, restrictedOwner);
        get(viewer(viewerId), SEARCH + "?q=الكويتي&caseId=" + restricted).andExpect(jsonPath("$.totalItems").value(1));
        get(viewer(viewerId), "/api/v1/documents/" + secret + "/text?withText=true").andExpect(jsonPath("$.alternates[0].word").exists());
        jdbc.update("update case_grant set revoked_at = now(), revoked_by = ? where id = ?", restrictedOwner, grant);
        get(viewer(viewerId), SEARCH + "?q=الكويتي&caseId=" + restricted).andExpect(jsonPath("$.totalItems").value(0));
        get(viewer(viewerId), "/api/v1/documents/" + secret + "/text?withText=true").andExpect(status().isNotFound());
        // the member is changed: the former member loses the case, alternates included
        UUID newOwner = user("New owner");
        jdbc.update("update legal_case set primary_member_id = ? where id = ?", newOwner, restricted);
        get(member(restrictedOwner), SEARCH + "?q=الكويتي&caseId=" + restricted).andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void anAlternateOnlyHitIsNeverGivenToTheAssistantAsAFact() throws Exception {
        ocr.reading = image -> reading("نص أصلي فقط", List.of(alt("بديل", "original", 1, 90)), 3, 1);
        UUID owner = user("Owner");
        UUID caseId = newCase(owner);
        attach(everything(), caseId, file("scan.png", "image/png", ArchiveFiles.png("scan")));
        runRecognition();
        // the archive search finds the alternate word (the assistant side is AssistantAlternateReadingsTest)
        get(everything(), SEARCH + "?q=بديل").andExpect(jsonPath("$.totalItems").value(1)).andExpect(jsonPath("$.items[0].source").value("ALTERNATE"));
        // and the only text the port can return for the document is the main reading
        var view = texts.view(jdbc.queryForObject("select v.id from document_version v join case_document d on d.id = v.document_id"
                + " where d.case_id = ?", UUID.class, caseId), true).orElseThrow();
        assertThat(view.text()).doesNotContain("بديل");
    }

    @Test
    void recognitionLeavesTheStoredOriginalByteForByte() throws Exception {
        ocr.reading = image -> reading("نص", List.of(alt("كلمة", "original", 1, 90)), 2, 0);
        byte[] png = ArchiveFiles.png("original bytes");
        UUID id = attach(everything(), newCase(), file("scan.png", "image/png", png));
        String before = jdbc.queryForObject("select sha256 from document_version where document_id = ?", String.class, id);
        runRecognition();
        assertThat(jdbc.queryForObject("select sha256 from document_version where document_id = ?", String.class, id))
                .isEqualTo(before).isEqualTo(sha256(png));
        get(everything(), "/api/v1/documents/" + id + "/content").andExpect(status().isOk()).andExpect(content().bytes(png));
        assertThat(count("select count(*) from document_version where document_id = ?", id)).isOne();
    }

    @Test
    void aReprocessReplacesTheAlternatesWithoutDuplicatesAndKeepsTheEarlierTextUntilTheNewOneIsRead() throws Exception {
        ocr.reading = image -> reading("النص الأول", List.of(alt("اولى", "original", 1, 90), alt("ثانية", "grey-sparse", 2, 80)), 3, 2);
        UUID id = attach(everything(), newCase(), file("scan.png", "image/png", ArchiveFiles.png("scan")));
        runRecognition();
        assertThat(count("select count(*) from document_text_alternate where document_id = ?", id)).isEqualTo(2);
        for (int round = 0; round < 3; round++) {
            post(everything(), "/api/v1/documents/" + id + "/text/reprocess", "{}").andExpect(jsonPath("$.status").value("PENDING"));
            // the earlier text and alternates are still there while the new reading is waiting
            assertThat(jdbc.queryForObject("select text from document_text where document_id = ?", String.class, id)).isEqualTo("النص الأول");
            assertThat(count("select count(*) from document_text_alternate where document_id = ?", id)).isEqualTo(2);
            if (round == 1) {
                ocr.failing = true; // an engine failure during a reprocess loses nothing
                for (int i = 0; i < 3; i++) {
                    runRecognition();
                }
                assertThat(jdbc.queryForObject("select text from document_text where document_id = ?", String.class, id)).isEqualTo("النص الأول");
                ocr.failing = false;
                post(everything(), "/api/v1/documents/" + id + "/text/reprocess", "{}");
            }
            runRecognition();
            assertThat(count("select count(*) from document_text_alternate where document_id = ?", id)).isEqualTo(2); // idempotent
            get(everything(), "/api/v1/documents/" + id + "/text?withText=true").andExpect(jsonPath("$.status").value("OCR_DONE"))
                    .andExpect(jsonPath("$.alternates.length()").value(2));
        }
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
                .andExpect(jsonPath("$.items[0].documentId").value(clearId.toString()))
                .andExpect(jsonPath("$.items[0].source").value("ALTERNATE")); // the main reading still says الكوبتي
        get(member(owner), SEARCH + "?q=LD-2026/045&caseId=" + caseId).andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].documentId").value(mixedId.toString()))
                .andExpect(jsonPath("$.items[0].source").value("ALTERNATE"));
        get(member(owner), "/api/v1/documents/" + clearId + "/content").andExpect(content().bytes(clear));
        get(member(owner), "/api/v1/documents/" + mixedId + "/content").andExpect(content().bytes(mixed));
        assertThat(jdbc.queryForObject("select sha256 from document_version where document_id = ?", String.class, mixedId))
                .isEqualTo(sha256(mixed));
    }

    private static String sha256(byte[] bytes) throws Exception {
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
