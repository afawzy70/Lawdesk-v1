package com.lawdesk.reports;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * LD-034 criterion 6: Arabic in PDF exports is shaped (contextual letter forms, lam-alef ligatures) and laid out right
 * to left. PDF has no text layout engine, so lines are drawn in visual order; expected values are written out by hand
 * from the Unicode Arabic presentation forms.
 */
class PdfTextTest {

    @Test
    void shapesArabicIntoContextualFormsInVisualOrder() {
        // المرجع: alef isolated, lam initial, meem medial, reh final, jeem initial, ain final; drawn right to left.
        assertThat(PdfText.visual("المرجع", true)).isEqualTo("ﻊﺟﺮﻤﻟﺍ");
    }

    @Test
    void joinsLamAlefIntoItsLigature() {
        assertThat(PdfText.visual("لا", true)).isEqualTo("ﻻ");
        // ضلال: dad initial, lam-alef final ligature, lam isolated; drawn right to left.
        assertThat(PdfText.visual("ضلال", true)).isEqualTo("ﻝﻼﺿ");
    }

    @Test
    void keepsLatinTextAndNumbersLeftToRightInsideArabic() {
        // محكمة R5-1: the Arabic word on the right, the code on its left, each in its own reading direction.
        assertThat(PdfText.visual("محكمة R5-1", true)).isEqualTo("R5-1 ﺔﻤﻜﺤﻣ");
        assertThat(PdfText.visual("2025-01-10", true)).isEqualTo("2025-01-10");
        assertThat(PdfText.visual("1234/2025", true)).isEqualTo("1234/2025");
    }

    @Test
    void leavesEnglishUntouchedAndPlacesArabicNamesInsideItCorrectly() {
        assertThat(PdfText.visual("Reference", false)).isEqualTo("Reference");
        assertThat(PdfText.visual("Court لا", false)).isEqualTo("Court ﻻ");
        assertThat(PdfText.visual("", false)).isEmpty();
    }

    @Test
    void eachCellTakesTheDirectionOfItsFirstStrongLetterWhateverTheExportLanguage() {
        String arabicTitle = "أحمد ضد شركة النيل (فاتورة 123)";
        assertThat(PdfText.visual(arabicTitle, false)).isEqualTo(PdfText.visual(arabicTitle, true));
        assertThat(PdfText.visual("Workers v. Delta (2)", true)).isEqualTo("Workers v. Delta (2)");
        // The closing bracket of the Arabic title is drawn mirrored on the left, after the number.
        assertThat(PdfText.visual(arabicTitle, false)).startsWith("(123 ");
    }

    @Test
    void keepsAnIsolatedDateInReadingOrderAfterArabicAndDropsTheIsolateControls() {
        assertThat(PdfText.visual("جرد — " + PdfText.isolated("2026-10-24"), true))
                .isEqualTo("2026-10-24 — \uFEA9\uFEAE\uFE9F");
    }

    @Test
    void wrapsTextIntoLinesThatFitTheWidthBreakingAtSpacesOrInsideLongWords() {
        PdfText.Measure measure = text -> text.length() * 10f;
        assertThat(PdfText.wrap("one two three", 80, measure)).containsExactly("one two", "three");
        assertThat(PdfText.wrap("ABCDEFGHIJKL", 50, measure)).containsExactly("ABCDE", "FGHIJ", "KL");
        assertThat(PdfText.wrap("", 50, measure)).isEqualTo(List.of(""));
        assertThat(PdfText.wrap("two\nlines", 500, measure)).containsExactly("two", "lines");
    }
}
