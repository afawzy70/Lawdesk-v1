package com.lawdesk.documents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

/**
 * The sample pages the OCR quality test reads are generated here, not stored. This test shows they really carry shaped Arabic
 * text and differ from each other, so a quality result (or a skipped test on a machine without an engine) is about real input.
 */
class GeneratedSamplesTest {

    private static long darkPixels(byte[] png) throws Exception {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        long dark = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) & 0xFF) < 100) {
                    dark++;
                }
            }
        }
        return dark;
    }

    @Test
    void generatedArabicAndEnglishPagesCarryText() throws Exception {
        assumeTrue(ArchiveFiles.canDraw(), "the bundled font cannot be drawn on this machine");
        long arabic = darkPixels(ArchiveFiles.png("صحيفة الدعوى المدنية\nمذكرة دفاع المدعى عليه"));
        long arabicOneLine = darkPixels(ArchiveFiles.png("صحيفة الدعوى المدنية"));
        long english = darkPixels(ArchiveFiles.png("Statement of claim\nPleading number 15"));
        long blank = darkPixels(ArchiveFiles.png(""));
        assertThat(blank).isZero();
        assertThat(arabicOneLine).isGreaterThan(2_000);
        assertThat(arabic).isGreaterThan(arabicOneLine);
        assertThat(english).isGreaterThan(2_000).isNotEqualTo(arabic);
    }

    @Test
    void theQualityMeasureComparesTextTheWayTheTestsNeedIt() {
        assertThat(com.lawdesk.library.ArabicQuality.similarity("صحيفة الدعوى", "صحيفة الدعوى")).isEqualTo(1.0);
        assertThat(com.lawdesk.library.ArabicQuality.similarity("صحيفة الدعوى", "صحيفه الدعوي")).isGreaterThan(0.8);
        assertThat(com.lawdesk.library.ArabicQuality.similarity("صحيفة الدعوى", "unrelated")).isLessThan(0.2);
        assertThat(com.lawdesk.library.ArabicQuality.similarity("", "")).isEqualTo(1.0);
        // Spaces, punctuation and diacritics do not count against a reading.
        assertThat(com.lawdesk.library.ArabicQuality.similarity("مُذَكِّرَة دفاع", "مذكرة، دفاع.")).isEqualTo(1.0);
    }
}
