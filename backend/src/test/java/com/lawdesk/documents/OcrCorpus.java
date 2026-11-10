package com.lawdesk.documents;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.imageio.ImageIO;

/**
 * Synthetic page images with known ground truth for the OCR quality measurement (SYNTH-DOC: invented sentences, no real
 * data). Everything is drawn with the bundled DejaVu Sans (the only typeface the repository carries), at a stated "dpi":
 * a 12 pt body line is 12/72 inch tall, so its pixel size is {@code 12 * dpi / 72}. The noise generator is seeded, so the
 * corpus is identical on every run.
 */
final class OcrCorpus {

    /** One case of the corpus: what was drawn, and how the page was degraded. */
    record Case(String name, String group, int dpi, List<String> lines, double angleDegrees, double noiseSigma,
            double contrast, double blur, boolean binaryNoise, double points, boolean table) {
        Case(String name, String group, int dpi, List<String> lines, double angleDegrees, double noiseSigma,
                double contrast, double blur, boolean binaryNoise) {
            this(name, group, dpi, lines, angleDegrees, noiseSigma, contrast, blur, binaryNoise, 12, false);
        }

        /** The ground truth: for a table page, the cells (separated by a tab in {@code lines}) joined by a space. */
        String truth() {
            return String.join("\n", lines).replace('\t', ' ');
        }
    }

    static final List<String> LEGAL_AR = List.of(
            "صحيفة الدعوى المدنية رقم 1234 لسنة 2026",
            "مذكرة دفاع المدعى عليه في القضية المنظورة أمام المحكمة الكلية",
            "وقد قررت المحكمة تأجيل نظر الدعوى إلى جلسة الأحد القادم",
            "للاطلاع على المستندات المرفقة والرد عليها خلال المدة المحددة");
    static final List<String> MIXED = List.of(
            "عقد الإيجار رقم LD-2026/045 بين الشركة الوطنية ABC Trading",
            "المبلغ المطلوب 1,250.500 KWD يستحق بتاريخ 2026-03-15",
            "Reference: Case 77/2026 — محكمة التمييز الدائرة التجارية",
            "رقم الهاتف 22345678 والبريد office@example.com");
    static final List<String> DIGITS = List.of(
            "رقم القضية 20260045 وتاريخ الجلسة 15/03/2026",
            "المبلغ ١٢٥٠٫٥٠٠ دينار كويتي عن الفاتورة رقم ٩٨٧٦٥",
            "Invoice 100234 total 3,450.750 date 2026-11-09");
    static final List<String> ENGLISH = List.of(
            "Statement of claim in the civil case number 1234",
            "The defendant submits that the contract was terminated",
            "Hearing adjourned to the 15th of March 2026");
    static final List<String> DIACRITICS = List.of(
            "الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ",
            "قَرَّرَتِ الْمَحْكَمَةُ تَأْجِيلَ الدَّعْوَى");

    static List<Case> cases() {
        List<Case> list = new ArrayList<>();
        // resolution ladder on clear text
        for (int dpi : new int[] {50, 72, 100, 150, 200, 300, 400}) {
            list.add(new Case("clear-ar-" + dpi + "dpi", "clear", dpi, LEGAL_AR, 0, 0, 1, 0, false));
        }
        // a whole page: 42 lines (A4 at 300 dpi), where the engine's layout analysis has real work to do
        List<String> page = new ArrayList<>();
        for (int i = 0; i < 42; i++) {
            page.add(LEGAL_AR.get(i % 4));
        }
        list.add(new Case("clear-ar-fullpage-42-lines-300dpi", "clear", 300, page, 0, 0, 1, 0, false));
        list.add(new Case("clear-en-300dpi", "clear", 300, ENGLISH, 0, 0, 1, 0, false));
        list.add(new Case("mixed-ar-en-300dpi", "mixed", 300, MIXED, 0, 0, 1, 0, false));
        list.add(new Case("mixed-ar-en-200dpi", "mixed", 200, MIXED, 0, 0, 1, 0, false));
        list.add(new Case("digits-300dpi", "digits", 300, DIGITS, 0, 0, 1, 0, false));
        list.add(new Case("digits-200dpi", "digits", 200, DIGITS, 0, 0, 1, 0, false));
        // scanned-looking: noise, low contrast, blur
        list.add(new Case("scan-noise-300dpi", "scan", 300, LEGAL_AR, 0, 25, 1, 0, false));
        list.add(new Case("scan-lowcontrast-300dpi", "scan", 300, LEGAL_AR, 0, 12, 0.35, 0, false));
        list.add(new Case("scan-blur-noise-300dpi", "scan", 300, LEGAL_AR, 0, 18, 0.7, 1.2, false));
        list.add(new Case("scan-speckle-200dpi", "scan", 200, LEGAL_AR, 0, 10, 0.8, 0.8, true));
        list.add(new Case("scan-mixed-noise-300dpi", "scan", 300, MIXED, 0, 20, 0.6, 0.8, false));
        // skew
        for (double angle : new double[] {0.5, 1, 2, 3, 5, -2, -5}) {
            list.add(new Case("skew-" + angle + "deg-300dpi", "skew", 300, LEGAL_AR, angle, 8, 0.9, 0, false));
        }
        list.add(new Case("skew-10deg-300dpi", "skew", 300, LEGAL_AR, 10, 8, 0.9, 0, false));
        // short pages: a stamp, a title, a two-line note (the engine's layout analysis needs enough text to find a block)
        List<String> two = List.of("صحيفة الدعوى المدنية", "مذكرة دفاع المدعى عليه");
        list.add(new Case("short-2-lines-ar-150dpi", "short", 150, two, 0, 0, 1, 0, false));
        list.add(new Case("short-2-lines-ar-300dpi", "short", 300, two, 0, 0, 1, 0, false));
        list.add(new Case("short-1-line-ar-300dpi", "short", 300, List.of("حكم نهائي في الطعن"), 0, 0, 1, 0, false));
        list.add(new Case("short-2-lines-en-300dpi", "short", 300, List.of("Statement of claim", "Pleading number 15"), 0, 0, 1, 0, false));
        list.add(new Case("short-2-lines-mixed-300dpi", "short", 300, List.of("القضية رقم 77/2026", "Case number 77/2026"), 0, 0, 1, 0, false));
        // diacritics (a documented limitation)
        list.add(new Case("diacritics-300dpi", "diacritics", 300, DIACRITICS, 0, 0, 1, 0, false));
        return list;
    }

    static byte[] png(Case c) {
        return encode(image(c), "png");
    }

    static BufferedImage image(Case c) {
        int size = Math.round((float) c.points() * c.dpi() / 72f); // a body line of c.points() points
        int width = Math.round(8.27f * c.dpi() * 0.9f); // A4 width, less the margins
        int lineHeight = Math.round(size * 2.0f);
        int height = lineHeight * (c.lines().size() + 2);
        BufferedImage page = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = page.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.setColor(Color.BLACK);
            float y = lineHeight * 1.2f;
            Font font = font(size);
            for (String line : c.lines()) {
                if (c.table()) {
                    // A right-to-left table: the first cell is the right-most one; every cell is a text run in a ruled box.
                    String[] cells = line.split("\t");
                    int columnWidth = (width - 80) / cells.length;
                    int top = Math.round(y - lineHeight * 0.75f);
                    int bottom = Math.round(y + lineHeight * 0.25f);
                    g.drawLine(40, top, width - 40, top);
                    for (int i = 0; i < cells.length; i++) {
                        TextLayout cell = new TextLayout(cells[i], font, g.getFontRenderContext());
                        int left = 40 + (cells.length - 1 - i) * columnWidth;
                        cell.draw(g, cell.isLeftToRight() ? left + 12 : left + columnWidth - 12 - cell.getAdvance(), y);
                        g.drawLine(left, top, left, bottom);
                    }
                    g.drawLine(width - 40, top, width - 40, bottom);
                    y += lineHeight;
                    continue;
                }
                TextLayout layout = new TextLayout(line, font, g.getFontRenderContext());
                float x = layout.isLeftToRight() ? 40 : width - 40 - layout.getAdvance();
                layout.draw(g, x, y);
                y += lineHeight;
            }
            if (c.table()) {
                int bottomRule = Math.round(y - lineHeight * 0.75f);
                g.drawLine(40, bottomRule, width - 40, bottomRule);
            }
        } finally {
            g.dispose();
        }
        BufferedImage out = page;
        if (c.angleDegrees() != 0) {
            out = rotate(out, c.angleDegrees());
        }
        if (c.blur() > 0) {
            out = blur(out, c.blur());
        }
        if (c.contrast() < 1 || c.noiseSigma() > 0) {
            degrade(out, c);
        }
        return out;
    }

    private static BufferedImage rotate(BufferedImage in, double degrees) {
        BufferedImage out = new BufferedImage(in.getWidth(), in.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, out.getWidth(), out.getHeight());
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setTransform(AffineTransform.getRotateInstance(Math.toRadians(degrees), in.getWidth() / 2.0, in.getHeight() / 2.0));
            g.drawImage(in, 0, 0, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private static BufferedImage blur(BufferedImage in, double radius) {
        int r = Math.max(1, (int) Math.round(radius));
        float[] kernel = new float[(2 * r + 1) * (2 * r + 1)];
        java.util.Arrays.fill(kernel, 1f / kernel.length);
        java.awt.image.ConvolveOp op = new java.awt.image.ConvolveOp(new java.awt.image.Kernel(2 * r + 1, 2 * r + 1, kernel),
                java.awt.image.ConvolveOp.EDGE_NO_OP, null);
        return op.filter(in, null);
    }

    /** Compresses the tonal range toward mid-grey and adds seeded noise (and, optionally, salt-and-pepper speckle). */
    private static void degrade(BufferedImage image, Case c) {
        Random random = new Random(c.name().hashCode());
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int grey = image.getRGB(x, y) & 0xFF;
                double v = 255 - (255 - grey) * c.contrast() - (1 - c.contrast()) * 40; // ink gets lighter, paper greyer
                v += random.nextGaussian() * c.noiseSigma();
                if (c.binaryNoise() && random.nextInt(400) == 0) {
                    v = random.nextBoolean() ? 0 : 255;
                }
                int clamped = (int) Math.max(0, Math.min(255, Math.round(v)));
                image.setRGB(x, y, (clamped << 16) | (clamped << 8) | clamped);
            }
        }
    }

    static Font font(float size) {
        try (InputStream ttf = OcrCorpus.class.getResourceAsStream("/fonts/DejaVuSans.ttf")) {
            return Font.createFont(Font.TRUETYPE_FONT, ttf).deriveFont(size);
        } catch (IOException | java.awt.FontFormatException e) {
            throw new IllegalStateException(e);
        }
    }

    static byte[] encode(BufferedImage image, String format) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, format, out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    // ---- metrics ------------------------------------------------------------------------------------------------

    /** The archive's search normalisation (V640 library_normalize), minus the definite-article rule, as a token list. */
    static List<String> tokens(String text) {
        String t = Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase();
        t = t.replaceAll("[\\u064B-\\u0670\\u0640\\u06D6-\\u06ED]", "");
        Map<Character, Character> fold = new HashMap<>();
        String from = "أإآٱىةؤئ٠١٢٣٤٥٦٧٨٩۰۱۲۳۴۵۶۷۸۹";
        String to = "ااااايهوي01234567890123456789";
        for (int i = 0; i < from.length(); i++) {
            fold.put(from.charAt(i), to.charAt(i));
        }
        StringBuilder sb = new StringBuilder();
        for (char ch : t.toCharArray()) {
            sb.append(fold.getOrDefault(ch, ch));
        }
        String cleaned = sb.toString().replaceAll("[^a-z0-9\\u0621-\\u064A]+", " ").strip();
        return cleaned.isEmpty() ? List.of() : List.of(cleaned.split(" +"));
    }

    static double characterAccuracy(String truth, String read) {
        String a = String.join("", tokens(truth));
        String b = String.join("", tokens(read));
        return a.isEmpty() ? 1 : Math.max(0, 1 - (double) distance(chars(a), chars(b)) / a.length());
    }

    /** 1 - WER on folded tokens (in reading order). */
    static double wordAccuracy(String truth, String read) {
        List<String> a = tokens(truth);
        List<String> b = tokens(read);
        return a.isEmpty() ? 1 : Math.max(0, 1 - (double) distance(a, b) / a.size());
    }

    /** Share of the truth's distinct words found anywhere in the read text: what a search for one word would find. */
    static double searchRecall(String truth, String read) {
        java.util.Set<String> wanted = new java.util.LinkedHashSet<>(tokens(truth));
        java.util.Set<String> got = new java.util.HashSet<>(tokens(read));
        if (wanted.isEmpty()) {
            return 1;
        }
        return wanted.stream().filter(got::contains).count() / (double) wanted.size();
    }

    private static List<String> chars(String s) {
        return s.codePoints().mapToObj(Character::toString).toList();
    }

    private static int distance(List<String> a, List<String> b) {
        int[] prev = new int[b.size() + 1];
        int[] cur = new int[b.size() + 1];
        for (int j = 0; j <= b.size(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.size(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.size(); j++) {
                int cost = a.get(i - 1).equals(b.get(j - 1)) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] swap = prev;
            prev = cur;
            cur = swap;
        }
        return prev[b.size()];
    }
}
