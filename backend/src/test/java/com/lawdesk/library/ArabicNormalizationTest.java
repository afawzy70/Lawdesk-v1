package com.lawdesk.library;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.lawdesk.platform.PostgresTestSupport;

/**
 * LD-052 / owner decision D7: the library does not assume the search engine is enough for Arabic. The database functions
 * that normalise text and measure word-trigram similarity are tested on their own, with Arabic examples.
 */
@SpringBootTest
class ArabicNormalizationTest extends PostgresTestSupport {

    @Autowired
    JdbcTemplate jdbc;

    private String normalized(String text) {
        return jdbc.queryForObject("select library_normalize(?)", String.class, text);
    }

    private double similarity(String a, String b) {
        return jdbc.queryForObject("select library_similarity(library_trigrams(?), library_trigrams(?))", Double.class, a, b);
    }

    @Test
    void alefAndHamzaFormsBecomeOneLetter() {
        assertThat(normalized("إدارة أحمد آمنة")).isEqualTo(normalized("ادارة احمد امنه"));
        assertThat(normalized("أحمد")).isEqualTo("احمد");
        assertThat(normalized("إدارة")).isEqualTo("اداره");
    }

    @Test
    void taaMarbutaAndHehAreTheSame() {
        assertThat(normalized("المحكمة")).isEqualTo(normalized("المحكمه"));
        assertThat(normalized("مسؤولية")).isEqualTo("مسوليه");
    }

    @Test
    void yehAlefMaqsuraAndEitherHamzaCarrierAreTheSame() {
        assertThat(normalized("مستشفى")).isEqualTo(normalized("مستشفي"));
        assertThat(normalized("مسئولية")).isEqualTo(normalized("مسؤولية")).isEqualTo(normalized("مسولية"));
    }

    @Test
    void diacriticsAndTatweelAreDropped() {
        assertThat(normalized("الحُكْمُ")).isEqualTo(normalized("الحكم"));
        assertThat(normalized("عـــقد")).isEqualTo("عقد");
        assertThat(normalized("إِنْشَاء")).isEqualTo(normalized("انشاء"));
    }

    @Test
    void theDefiniteArticleIsStrippedFromLongWordsOnly() {
        assertThat(normalized("القرار الإداري")).isEqualTo("قرار اداري");
        // "الله" and "التي" are not ال + a word of three letters, so they stay whole.
        assertThat(normalized("الله")).isEqualTo("الله");
        assertThat(normalized("التي")).isEqualTo("التي");
    }

    @Test
    void conjunctionAndPrepositionPrefixesBeforeTheArticleAreStrippedToo() {
        assertThat(normalized("والأدبي")).isEqualTo("ادبي");
        assertThat(normalized("بالتعويض")).isEqualTo("تعويض");
        assertThat(normalized("للموظف")).isEqualTo("موظف");
        assertThat(normalized("كالمعتاد")).isEqualTo("معتاد");
        assertThat(normalized("فالقرار")).isEqualTo("قرار");
        // Words that merely begin with these letters stay whole.
        assertThat(normalized("بلال وليد")).isEqualTo("بلال وليد");
    }

    @Test
    void digitsAndPunctuationAreNormalised() {
        assertThat(normalized("الطعن رقم ١٢٣٤ لسنة ٢٠٢٠، تجاري.")).isEqualTo("طعن رقم 1234 لسنه 2020 تجاري");
        assertThat(normalized("Case No. 55/2019")).isEqualTo("case no 55 2019");
        assertThat(normalized(null)).isEmpty();
        assertThat(normalized("   ")).isEmpty();
    }

    @Test
    void similarSpellingsAreCloseAndUnrelatedWordsAreFar() {
        assertThat(similarity("التعويض عن الضرر", "التعويض عن الضرر")).isEqualTo(1.0);
        // Spelling variants meet after normalisation.
        assertThat(similarity("إدارة المرافق", "اداره المرافق")).isEqualTo(1.0);
        // One letter wrong keeps most trigrams; a different subject shares almost none.
        assertThat(similarity("التعويض", "التعويظ")).isGreaterThan(0.2);
        assertThat(similarity("التعويض", "الإيجار")).isLessThan(0.15);
        assertThat(similarity("", "التعويض")).isZero();
    }

    @Test
    void searchVectorsAreBuiltOnNormalisedText() {
        Boolean matches = jdbc.queryForObject(
                "select to_tsvector('simple', library_normalize('القرارات الإدارية')) @@ to_tsquery('simple', ?)",
                Boolean.class, jdbc.queryForObject("select library_normalize('الاداريه')", String.class));
        assertThat(matches).isTrue();
    }
}
