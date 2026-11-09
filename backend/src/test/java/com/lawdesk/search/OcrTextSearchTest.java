package com.lawdesk.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.lawdesk.security.Permission;
import com.lawdesk.wp9.Wp9TestSupport;

/**
 * Regression (UAT 3937a50): the stored search vector of a document held only its title and file name, so a document found only
 * through the text of its current version (read from the file or by OCR) was no longer found by the unified search. The text must
 * be searchable again, reported as matched in the file text, and only to a user who may see the case.
 */
class OcrTextSearchTest extends Wp9TestSupport {

    private static final Permission[] ALL = {Permission.CASE_VIEW, Permission.SEARCH_ALL, Permission.DOCUMENT_VIEW};

    private UUID documentWithText(UUID caseId, String title, String text, String status, String method) {
        UUID doc = document(caseId, title, "scan-" + UUID.randomUUID().toString().substring(0, 6) + ".png");
        jdbc.update("""
                insert into document_version (id, document_id, version_no, file_name, media_type, size_bytes, sha256, uploaded_by)
                values (?, ?, 1, 'scan.png', 'image/png', 10, repeat('a', 64), gen_random_uuid())""", doc, doc);
        jdbc.update("insert into document_text (version_id, document_id, status, method, text) values (?, ?, ?, ?, ?)", doc, doc,
                status, method, text);
        return doc;
    }

    private MvcResult search(RequestPostProcessor who, String q) throws Exception {
        return mvc.perform(getJson(who, "/api/v1/search").param("q", q).param("types", "DOCUMENT")).andExpect(status().isOk())
                .andReturn();
    }

    @Test
    void aDocumentIsFoundThroughItsOcrTextAndOnlyByAUserWhoMaySeeTheCase() throws Exception {
        UUID owner = user("Owner");
        UUID stranger = user("Stranger");
        UUID c = legalCase(owner, null);
        UUID doc = documentWithText(c, "مسح ضوئي", "OCRUNIQTOKENONE نص عربي مقروء بالتعرف الضوئي", "OCR_DONE", "OCR");
        RequestPostProcessor member = outsider(owner, ALL);

        MvcResult found = search(member, "OCRUNIQTOKENONE");
        assertThat(this.<List<String>>read(found, "$.groups[0].items[*].id")).containsExactly(doc.toString());
        assertThat(this.<List<String>>read(found, "$.groups[0].items[0].matchedIn")).containsExactly("DOCUMENT_TEXT");
        assertThat(this.<String>read(found, "$.groups[0].items[0].snippet")).contains("OCRUNIQTOKENONE");

        // A user who may not see the case finds nothing, and the total does not count it.
        MvcResult none = search(outsider(stranger, ALL), "OCRUNIQTOKENONE");
        assertThat(this.<Integer>read(none, "$.total")).isZero();
    }

    @Test
    void theTextIsFoundWhenItArrivesAfterTheDocumentAndStopsBeingFoundWhenTheVersionMovesOn() throws Exception {
        UUID owner = user("Owner");
        UUID c = legalCase(owner, null);
        UUID doc = document(c, "عقد", "contract.pdf");
        jdbc.update("""
                insert into document_version (id, document_id, version_no, file_name, media_type, size_bytes, sha256, uploaded_by)
                values (?, ?, 1, 'contract.pdf', 'application/pdf', 10, repeat('a', 64), gen_random_uuid())""", doc, doc);
        RequestPostProcessor member = outsider(owner, ALL);
        assertThat(this.<Integer>read(search(member, "LATETOKENTWO"), "$.total")).isZero();
        // The OCR worker writes the text afterwards (PENDING first, which is not searched, then OCR_DONE).
        jdbc.update("insert into document_text (version_id, document_id, status, method) values (?, ?, 'PENDING', 'OCR')", doc, doc);
        assertThat(this.<Integer>read(search(member, "LATETOKENTWO"), "$.total")).isZero();
        jdbc.update("update document_text set status = 'OCR_DONE', text = 'LATETOKENTWO مقروء' where version_id = ?", doc);
        assertThat(this.<Integer>read(search(member, "LATETOKENTWO"), "$.total")).isEqualTo(1);
        // Version 2 becomes the current one: the text of version 1 is no longer the document's text.
        jdbc.update("""
                insert into document_version (id, document_id, version_no, file_name, media_type, size_bytes, sha256, uploaded_by)
                values (gen_random_uuid(), ?, 2, 'contract2.pdf', 'application/pdf', 10, repeat('b', 64), gen_random_uuid())""", doc);
        jdbc.update("update case_document set current_version_no = 2 where id = ?", doc);
        assertThat(this.<Integer>read(search(member, "LATETOKENTWO"), "$.total")).isZero();
    }
}
