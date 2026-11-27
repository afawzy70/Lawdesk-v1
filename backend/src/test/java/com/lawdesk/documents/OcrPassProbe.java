package com.lawdesk.documents;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** Developer measurement: which pass (picture variant x layout mode) finds which terms. Only with -Docr.probe=<tsv file>. */
class OcrPassProbe {

    @Test
    void probe() throws Exception {
        String out = System.getProperty("ocr.probe");
        assumeTrue(out != null, "only with -Docr.probe=<file>");
        TesseractOcrEngine engine = new TesseractOcrEngine("tesseract", "ara+eng");
        StringBuilder table = new StringBuilder("file\tpass\thits\tof\tmissed\n");
        for (OcrExactCorpus.Page page : OcrExactCorpus.pages()) {
            byte[] original = OcrExactTermsTest.picture(page);
            Map<String, byte[]> variants = new LinkedHashMap<>();
            variants.put("prepared", OcrPreprocessor.prepare(original));
            variants.put("original", original);
            byte[] grey = OcrPreprocessor.prepareGrey(original, false);
            if (grey != null) {
                variants.put("grey", grey);
            }
            byte[] big = OcrPreprocessor.prepareGrey(original, true);
            if (big != null) {
                variants.put("enlarged", big);
                var image = OcrPreprocessor.decode(big);
                variants.put("enlarged-otsu", OcrPreprocessor.png(OcrPreprocessor.threshold(image)));
            }
            for (var v : variants.entrySet()) {
                for (int psm : new int[] {3, 6, 11}) {
                    String text = engine.pass(v.getValue(), psm).text();
                    var missed = page.terms().stream().filter(t -> !OcrExactCorpus.contains(text, t)).toList();
                    table.append(page.file()).append('\t').append(v.getKey()).append('-').append(psm).append('\t')
                            .append(page.terms().size() - missed.size()).append('\t').append(page.terms().size()).append('\t')
                            .append(String.join(" | ", missed)).append('\n');
                }
            }
        }
        Files.writeString(Path.of(out), table.toString());
    }
}
