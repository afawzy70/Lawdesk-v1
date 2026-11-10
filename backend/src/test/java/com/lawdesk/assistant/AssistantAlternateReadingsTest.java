package com.lawdesk.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * QA-RT-001 follow-up: words another recognition pass read (document_text_alternate) may make a document findable in the archive
 * search, with a warning, but they are never facts: the assistant retrieves, quotes and cites the main reading only.
 */
class AssistantAlternateReadingsTest extends AssistantTestSupport {

    @Test
    void aWordOnlyAnAlternateReadingFoundNeverBecomesAnAssistantSourceOrQuote() throws Exception {
        UUID owner = user("Owner");
        UUID caseId = legalCase(owner, null);
        UUID doc = documentWithText(caseId, "مذكرة ممسوحة", "نص القراءة الأساسية عن عقد التوريد فقط", "OCR_DONE");
        jdbc.update("""
                insert into document_text_alternate (version_id, document_id, page, line_no, word, pass, confidence)
                values (?, ?, 1, 2, 'زئبقيون', 'original', 91.0)""", doc, doc);

        MvcResult result = ask(member(owner), question(caseId, "ما قصة زئبقيون في المذكرة؟"), 200);
        assertThat(body(result)).doesNotContain("زئبقيون");
        assertThat(this.<List<String>>read(result, "$.sources[*].type")).doesNotContain("DOCUMENT");

        // the same document is used when the question holds a word of the main reading, and then only that text is quoted
        MvcResult primary = ask(member(owner), question(caseId, "ما شروط عقد التوريد؟"), 200);
        assertThat(this.<List<String>>read(primary, "$.sources[*].type")).contains("DOCUMENT");
        assertThat(body(primary)).doesNotContain("زئبقيون");

        // analysis of the document reads the main reading only
        MvcResult analysis = ask(member(owner), json("task", "DOCUMENT_ANALYSIS", "documentId", doc.toString()), 200);
        assertThat(body(analysis)).doesNotContain("زئبقيون");
    }
}
