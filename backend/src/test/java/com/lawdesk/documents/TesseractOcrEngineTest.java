package com.lawdesk.documents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.lawdesk.library.ArabicQuality;

/**
 * LD-055 (owner decision D7): the engine is local (Tesseract with Arabic data). Where it is not installed the engine says so
 * and these tests that need it are skipped, visibly: the quality test is never counted as passed without running it.
 */
class TesseractOcrEngineTest {

    @Test
    void anEngineThatIsNotInstalledReportsItselfUnavailableAndReadsNothing() {
        TesseractOcrEngine engine = new TesseractOcrEngine("/nonexistent/tesseract", "ara+eng");
        OcrEngine.Status status = engine.status();
        assertThat(status.available()).isFalse();
        assertThat(status.engine()).isEqualTo("tesseract");
        assertThat(status.detail()).contains("not installed");
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> engine.recognise(ArchiveFiles.png("x"))))
                .isInstanceOf(OcrEngine.OcrException.class);
    }

    @Test
    void aMissingLanguageMakesTheEngineUnavailableForArabic() {
        // "true --list-langs" exists everywhere, exits 0 and lists no language: the engine must not claim Arabic.
        OcrEngine.Status status = new TesseractOcrEngine("true", "ara+eng").status();
        assertThat(status.available()).isFalse();
        assertThat(status.detail()).contains("ara");
    }

    /**
     * Arabic and English page images generated here are read by the real engine and compared with what was drawn. The text is
     * 28 px on a 1000 px canvas (about 12 pt at 170 dpi). Measured limitation (docs/evidence/DOC-OCR): glyphs far larger than that
     * (48 px on a 1400x400 canvas) can make Tesseract drop the first line of a two-line page.
     */
    @Test
    void readsGeneratedArabicAndEnglishPages() throws Exception {
        TesseractOcrEngine engine = new TesseractOcrEngine("tesseract", "ara+eng");
        OcrEngine.Status status = engine.status();
        assumeTrue(status.available(), "BLOCKED: no Tesseract with Arabic data on this machine (" + status.detail() + ")");
        assumeTrue(ArchiveFiles.canDraw(), "BLOCKED: the bundled font cannot be drawn on this machine");

        List<String> samples = List.of("صحيفة الدعوى المدنية\nمذكرة دفاع المدعى عليه", "محكمة التمييز\nحكم نهائي في الطعن", "Statement of claim\nPleading number 15");
        double worst = 1.0;
        for (String sample : samples) {
            String read = engine.recognise(ArchiveFiles.png(sample, 28, 1000, 260));
            double similarity = ArabicQuality.similarity(sample, read);
            worst = Math.min(worst, similarity);
            System.out.println("OCR quality " + String.format("%.2f", similarity) + " for: " + sample.replace('\n', '/') + " -> "
                    + read.strip().replace('\n', '/'));
        }
        assertThat(worst).as("worst similarity of the generated samples").isGreaterThanOrEqualTo(0.8);
    }
}
