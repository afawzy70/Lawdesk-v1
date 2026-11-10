package com.lawdesk.documents;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Developer measurement (only with {@code -Docr.cost=<file>}): what the extra readings cost on a 20-page document of mixed quality
 * (10 tuned-corpus pages and 10 hold-out pages, one A4 page each), before (main reading plus the old sparse merge) and after,
 * through the real path (upload, render, recognition, store), plus two documents recognised by two workers at once. The engine's own
 * runs are timed by the wrapper when OCR_TIME_LOG is set: elapsed seconds, user and system CPU seconds, peak resident KB per run.
 */
class OcrCostTest extends ArchiveTestSupport {

    private static byte[] twentyPagePdf(int pages) throws Exception {
        List<byte[]> images = new ArrayList<>();
        for (OcrExactCorpus.Page p : OcrExactCorpus.pages()) {
            images.add(OcrExactTermsTest.picture(p));
        }
        for (OcrHoldoutCorpus.Page p : OcrHoldoutCorpus.pages()) {
            images.add(Files.readAllBytes(OcrExactTermsTest.root().resolve(OcrHoldoutCorpus.DIR).resolve(p.file())));
        }
        try (var document = new org.apache.pdfbox.pdmodel.PDDocument()) {
            for (int i = 0; i < pages; i++) {
                // alternate between the tuned and the hold-out pages so that the document is mixed
                byte[] bytes = images.get(i % 2 == 0 ? (i / 2) % 16 : 16 + (i / 2) % 12);
                var image = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes));
                var pdPage = new org.apache.pdfbox.pdmodel.PDPage(org.apache.pdfbox.pdmodel.common.PDRectangle.A4);
                document.addPage(pdPage);
                var pdImage = org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory.createFromImage(document, image);
                float width = org.apache.pdfbox.pdmodel.common.PDRectangle.A4.getWidth() - 40;
                float height = Math.min(width * image.getHeight() / image.getWidth(), org.apache.pdfbox.pdmodel.common.PDRectangle.A4.getHeight() - 60);
                try (var content = new org.apache.pdfbox.pdmodel.PDPageContentStream(document, pdPage)) {
                    content.drawImage(pdImage, 20, org.apache.pdfbox.pdmodel.common.PDRectangle.A4.getHeight() - 40 - height, width, height);
                }
            }
            var out = new java.io.ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    @Test
    void costOfTheExtraReadings() throws Exception {
        String report = System.getProperty("ocr.cost");
        assumeTrue(report != null, "only with -Docr.cost=<file>");
        TesseractOcrEngine after = new TesseractOcrEngine("tesseract", "ara+eng");
        TesseractOcrEngine before = new TesseractOcrEngine("tesseract", "ara+eng", true, 3, false);
        assumeTrue(after.status().available(), "BLOCKED: no Tesseract with Arabic data");
        StringBuilder out = new StringBuilder();
        byte[] pdf20 = twentyPagePdf(20);
        byte[] pdf5 = twentyPagePdf(5);
        out.append("pdf20_bytes=").append(pdf20.length).append('\n');
        UUID owner = user("Owner");
        for (var variant : List.of("before", "after")) {
            TesseractOcrEngine engine = variant.equals("before") ? before : after;
            List<Long> perPage = java.util.Collections.synchronizedList(new ArrayList<>());
            ocr.reading = image -> {
                try {
                    long t0 = System.nanoTime();
                    OcrEngine.Reading r = engine.read(image);
                    perPage.add((System.nanoTime() - t0) / 1_000_000);
                    return r;
                } catch (OcrEngine.OcrException e) {
                    throw new IllegalStateException(e);
                }
            };
            mark(out, variant + "_20_start");
            attach(member(owner), newCase(owner), pdfFile("twenty-" + variant + ".pdf", pdf20));
            long cpu0 = childCpuTicks();
            long t0 = System.nanoTime();
            runRecognition();
            long ms = (System.nanoTime() - t0) / 1_000_000;
            out.append(variant).append("_20pages_file_ms=").append(ms).append('\n');
            out.append(variant).append("_20pages_per_page_ms=").append(perPage).append('\n');
            out.append(variant).append("_20pages_children_cpu_s=").append((childCpuTicks() - cpu0) / 100.0).append('\n');
            perPage.clear();
            // two workers at once, two 5-page documents
            attach(member(owner), newCase(owner), pdfFile("five-a-" + variant + ".pdf", pdf5));
            attach(member(owner), newCase(owner), pdfFile("five-b-" + variant + ".pdf", pdf5));
            long t1 = System.nanoTime();
            List<Integer> handled = java.util.Collections.synchronizedList(new ArrayList<>());
            Thread a = new Thread(() -> handled.add(texts.processPending(1)));
            Thread b = new Thread(() -> handled.add(texts.processPending(1)));
            a.start();
            b.start();
            a.join();
            b.join();
            out.append(variant).append("_two_workers_two_5page_files_wall_ms=").append((System.nanoTime() - t1) / 1_000_000)
                    .append(" handled=").append(handled).append('\n');
            out.append(variant).append("_documents_ocr_done=").append(count("select count(*) from document_text where status = 'OCR_DONE'")).append('\n');
        }
        String log = System.getenv("OCR_TIME_LOG");
        if (log != null && Files.exists(Path.of(log))) {
            out.append("engine_run_log (elapsed_s user_s sys_s peak_rss_kb) lines=").append(Files.readAllLines(Path.of(log)).size()).append('\n');
        }
        Files.writeString(Path.of(report), out.toString());
    }

    private static void mark(StringBuilder out, String what) {
        String log = System.getenv("OCR_TIME_LOG");
        if (log != null) {
            try {
                Files.writeString(Path.of(log), "# " + what + "\n", java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            } catch (IOException ignored) {
                // measurement only
            }
        }
    }

    /** User + system CPU ticks (1/100 s) of the waited-for child processes of this JVM (the engine runs). */
    private static long childCpuTicks() throws IOException {
        String stat = Files.readString(Path.of("/proc/self/stat"));
        String[] f = stat.substring(stat.lastIndexOf(')') + 2).split(" ");
        return Long.parseLong(f[13]) + Long.parseLong(f[14]); // cutime, cstime
    }
}
