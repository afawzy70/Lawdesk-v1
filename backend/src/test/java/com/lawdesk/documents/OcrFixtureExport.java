package com.lawdesk.documents;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Writes the synthetic page images the UAT journey uploads (tools/journeys/ocr_fixtures) and their ground truth. Developer
 * tool, run with {@code -Docr.fixtures=<directory>}; the images are deterministic (seeded noise), SYNTH-DOC, invented text.
 */
class OcrFixtureExport {

    @Test
    void export() throws Exception {
        String dir = System.getProperty("ocr.fixtures");
        assumeTrue(dir != null, "only with -Docr.fixtures=<directory>");
        List<OcrCorpus.Case> cases = List.of(
                new OcrCorpus.Case("ocr-clear-ar", "fixture", 200, List.of("SYNTH-DOC مذكرة دفاع في الدعوى التجارية",
                        "المادة 12 من القانون المدني الكويتي", "تأجيل الجلسة إلى يوم الأحد القادم", "OCRJOURNEYONE"), 0, 0, 1, 0, false),
                new OcrCorpus.Case("ocr-scan-skew", "fixture", 300, List.of("SYNTH-DOC صحيفة استئناف الحكم الصادر",
                        "عن محكمة أول درجة الدائرة المدنية", "طلب إلغاء الحكم المستأنف وإعادة القضية", "OCRJOURNEYTWO"), 3, 12, 0.6, 0.8, false),
                new OcrCorpus.Case("ocr-mixed", "fixture", 300, List.of("SYNTH-DOC عقد الإيجار رقم LD-2026/045",
                        "المبلغ المطلوب 1,250.500 KWD يستحق في 2026-03-15", "الشركة الوطنية للتوريد ABC Trading", "OCRJOURNEYTHREE"), 0, 0, 1, 0, false));
        Path out = Path.of(dir);
        Files.createDirectories(out);
        StringBuilder manifest = new StringBuilder("[\n");
        for (OcrCorpus.Case c : cases) {
            // the noisy scan is a JPEG (what a scanner writes, and 1.7 MB smaller than the same page as PNG)
            String extension = c.noiseSigma() > 0 ? "jpg" : "png";
            Files.write(out.resolve(c.name() + "." + extension), OcrCorpus.encode(OcrCorpus.image(c), extension));
            manifest.append(manifest.length() > 3 ? ",\n" : "").append("  {\"file\": \"").append(c.name()).append('.').append(extension)
                    .append("\", \"dpi\": ")
                    .append(c.dpi()).append(", \"skewDegrees\": ").append(c.angleDegrees()).append(", \"lines\": [");
            for (int i = 0; i < c.lines().size(); i++) {
                manifest.append(i > 0 ? ", " : "").append('"').append(c.lines().get(i).replace("\"", "\\\"")).append('"');
            }
            manifest.append("]}");
        }
        Files.writeString(out.resolve("manifest.json"), manifest.append("\n]\n").toString());
    }

    /** The extra pages of the exact-term corpus, with their ground truth and search terms (backend/src/test/resources/ocr-corpus). */
    @Test
    void exportExtraCorpus() throws Exception {
        String dir = System.getProperty("ocr.extra");
        assumeTrue(dir != null, "only with -Docr.extra=<directory>");
        Path out = Path.of(dir);
        Files.createDirectories(out);
        StringBuilder manifest = new StringBuilder("[\n");
        boolean first = true;
        for (OcrExactCorpus.Page p : OcrExactCorpus.pages()) {
            if (p.journey()) {
                continue;
            }
            OcrCorpus.Case c = p.drawing();
            String extension = p.file().substring(p.file().lastIndexOf('.') + 1);
            Files.write(out.resolve(p.file()), OcrCorpus.encode(OcrCorpus.image(c), extension));
            manifest.append(first ? "" : ",\n").append("  {\"file\": \"").append(p.file()).append("\", \"dpi\": ").append(c.dpi())
                    .append(", \"points\": ").append(c.points()).append(", \"skewDegrees\": ").append(c.angleDegrees())
                    .append(", \"noiseSigma\": ").append(c.noiseSigma()).append(", \"table\": ").append(c.table())
                    .append(", \"lines\": ").append(json(c.lines())).append(", \"terms\": ").append(json(p.terms())).append('}');
            first = false;
        }
        Files.writeString(out.resolve("manifest.json"), manifest.append("\n]\n").toString());
    }

    private static String json(List<String> values) {
        return values.stream().map(v -> '"' + v.replace("\\", "\\\\").replace("\"", "\\\"").replace("\t", "\\t") + '"')
                .collect(java.util.stream.Collectors.joining(", ", "[", "]"));
    }
}
