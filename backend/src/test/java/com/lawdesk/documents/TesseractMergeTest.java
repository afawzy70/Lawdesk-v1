package com.lawdesk.documents;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TesseractMergeTest {

    @Test
    void addsOnlyLinesWhoseWordsTheFirstReadingLacked() {
        String first = "مذكرة دفاع المدعى عليه\n";
        String second = "صحيفة الدعوى المدنية\n\nمُذَكِّرَة دفاع المدعي عليه\n";
        // the first line is new and was above the other in the page; the second differs only in diacritics and alef maqsura/yeh
        assertThat(TesseractOcrEngine.merge(first, second)).isEqualTo("صحيفة الدعوى المدنية\nمذكرة دفاع المدعى عليه\n");
        // a slightly different second reading of a line already read adds nothing
        assertThat(TesseractOcrEngine.merge("77/2026 القضية رقم\nCase number 77/2026\n", "الفضية رقم 77/2026\nCase number 77/2026\n"))
                .isEqualTo("77/2026 القضية رقم\nCase number 77/2026\n");
    }

    @Test
    void neverRemovesAnythingAndIgnoresNoiseWithoutLetters() {
        assertThat(TesseractOcrEngine.merge("Case 77/2026\n", "|||\n— —\nCase 77/2026\n")).isEqualTo("Case 77/2026\n");
        assertThat(TesseractOcrEngine.merge("", "one line\n")).isEqualTo("one line\n");
    }
}
