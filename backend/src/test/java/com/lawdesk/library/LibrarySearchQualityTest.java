package com.lawdesk.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Owner decision D7 and LD-052, section 16.3: the search is tested on Arabic examples instead of being assumed good.
 * Spelling variants meet, results are ranked by where the words occur, a misspelling still finds the judgment, and
 * the classification and filter axes narrow the answer.
 */
class LibrarySearchQualityTest extends LibrarySupport {

    private static final String BASE = "/api/v1/library/judgments";

    @Test
    void hamzaTaaMarbutaAndDiacriticsDoNotHideAJudgment() throws Exception {
        UUID id = register("مسؤولية الإدارة عن القرارات الإدارية", "summary", "الحُكْمُ بالتعويض");
        assertThat(searchIds(READER, "q=مسولية الاداره")).containsExactly(id.toString());
        assertThat(searchIds(READER, "q=مسئوليه")).containsExactly(id.toString());
        assertThat(searchIds(READER, "q=الحكم")).containsExactly(id.toString());
        assertThat(searchIds(READER, "q=حكم")).containsExactly(id.toString());
        assertThat(searchIds(READER, "q=إدارة")).containsExactly(id.toString());
    }

    @Test
    void theDefiniteArticleAndDigitFormsMeet() throws Exception {
        UUID id = register("القرار الإداري", "caseNumber", "١٢٣٤", "summary", "الطعن رقم ١٢٣٤ لسنة ٢٠٢٠");
        assertThat(searchIds(READER, "q=قرار اداري")).containsExactly(id.toString());
        assertThat(searchIds(READER, "q=1234")).containsExactly(id.toString());
        assertThat(searchIds(READER, "q=الطعن رقم 1234")).containsExactly(id.toString());
    }

    @Test
    void aWordPrefixFindsTheLongerWord() throws Exception {
        UUID id = register("التعويضات المستحقة للموظف");
        assertThat(searchIds(READER, "q=تعويض")).containsExactly(id.toString());
    }

    @Test
    void everyWordMustMatchSoAMoreSpecificQueryNarrowsTheAnswer() throws Exception {
        UUID rent = register("إيجار محل تجاري");
        register("إيجار مسكن");
        assertThat(searchIds(READER, "q=إيجار")).hasSize(2);
        assertThat(searchIds(READER, "q=إيجار تجاري")).containsExactly(rent.toString());
    }

    @Test
    void rankingPutsASubjectMatchBeforeAMatchOnlyInTheReasons() throws Exception {
        UUID inReasons = register("نزاع عمالي", "reasons", "تناولت المحكمة مسألة التقادم في أسبابها");
        UUID inSubject = register("التقادم المسقط للحق");
        UUID inSummary = register("نزاع آخر", "summary", "ملخص عن التقادم");
        List<String> ranked = searchIds(READER, "q=التقادم");
        assertThat(ranked).containsExactly(inSubject.toString(), inSummary.toString(), inReasons.toString());
        mvc.perform(getAs(READER, BASE + "?q=التقادم")).andExpect(jsonPath("$.items[0].score").isNumber())
                .andExpect(jsonPath("$.items[0].matchedIn[0]").value("SUBJECT"))
                .andExpect(jsonPath("$.items[2].matchedIn[0]").value("REASONS"));
    }

    @Test
    void aMisspellingStillFindsTheJudgmentThroughTrigramSimilarity() throws Exception {
        UUID id = register("التعويض عن الضرر");
        register("عقد إيجار");
        // "التعويظ" shares no whole word with the subject, but most of its trigrams.
        assertThat(searchIds(READER, "q=التعويظ")).containsExactly(id.toString());
        mvc.perform(getAs(READER, BASE + "?q=التعويظ")).andExpect(jsonPath("$.items[0].matchedIn[0]").value("SIMILAR_TEXT"));
        // Unrelated words find nothing.
        assertThat(searchIds(READER, "q=برمجيات")).isEmpty();
    }

    @Test
    void similarSpellingsAreOnlyOfferedWhenNothingMatchesExactlyAndNeverWidenAnExactAnswer() throws Exception {
        UUID exact = register("التعويض عن الضرر");
        UUID near = register("التعويظ عن الضرار");
        // "التعويض" matches the first judgment word for word: the similar spelling of the second is not added.
        assertThat(searchIds(READER, "q=التعويض")).containsExactly(exact.toString());
        // A word that matches nothing exactly falls back to similar spellings, best first.
        assertThat(searchIds(READER, "q=التعويظا")).containsExactly(near.toString(), exact.toString());
    }

    @Test
    void keywordsAndLegalTextsAreSearchable() throws Exception {
        UUID id = register("حكم في نزاع", "keywords", List.of("الفصل التعسفي", "مكافأة نهاية الخدمة"),
                "legalTexts", "المادة ٤٥ من قانون العمل");
        assertThat(searchIds(READER, "q=مكافاه نهايه")).containsExactly(id.toString());
        assertThat(searchIds(READER, "q=المادة 45")).containsExactly(id.toString());
    }

    @Test
    void filtersNarrowByCourtYearTypeLevelDirectionFinalityAndClassification() throws Exception {
        UUID topic = classification("LEGAL_TOPIC", "LABOUR", "منازعات العمل");
        UUID a = register("أ", "courtName", "محكمة التمييز", "courtLevel", "CASSATION", "judgmentDate", "2020-05-01",
                "caseType", "LABOR", "direction", "FOR_AUTHORITY", "finality", "FINAL",
                "classificationIds", List.of(topic.toString()));
        UUID b = register("ب", "courtName", "محكمة الاستئناف", "courtLevel", "APPEAL", "judgmentDate", "2021-05-01",
                "caseType", "CIVIL", "direction", "AGAINST_AUTHORITY", "finality", "NOT_FINAL");
        assertThat(searchIds(READER, "courtName=التمييز")).containsExactly(a.toString());
        assertThat(searchIds(READER, "year=2021")).containsExactly(b.toString());
        assertThat(searchIds(READER, "caseType=LABOR")).containsExactly(a.toString());
        assertThat(searchIds(READER, "courtLevel=APPEAL")).containsExactly(b.toString());
        assertThat(searchIds(READER, "direction=FOR_AUTHORITY")).containsExactly(a.toString());
        assertThat(searchIds(READER, "finality=NOT_FINAL")).containsExactly(b.toString());
        assertThat(searchIds(READER, "classificationId=" + topic)).containsExactly(a.toString());
        assertThat(searchIds(READER, "year=2020&caseType=CIVIL")).isEmpty();
        mvc.perform(getAs(READER, BASE + "?courtLevel=NOPE")).andExpect(status().isBadRequest());
    }

    @Test
    void listsAreNewestJudgmentFirstWhenNothingIsSearchedAndArePaged() throws Exception {
        UUID old = register("قديم", "judgmentDate", "2010-01-01");
        UUID recent = register("حديث", "judgmentDate", "2024-01-01");
        UUID undated = register("بلا تاريخ");
        assertThat(searchIds(READER, "")).containsExactly(recent.toString(), old.toString(), undated.toString());
        mvc.perform(getAs(READER, BASE + "?size=2&page=1")).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.totalItems").value(3)).andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(getAs(READER, BASE + "?size=1000")).andExpect(status().isBadRequest());
    }

    @Test
    void aQueryThatIsOnlyPunctuationAnswersLikeNoQueryAndNeverFails() throws Exception {
        register("حكم");
        assertThat(searchIds(READER, "q=':|!()")).hasSize(1);
        assertThat(searchIds(READER, "q=' or 1=1 --")).isEmpty();
    }
}
