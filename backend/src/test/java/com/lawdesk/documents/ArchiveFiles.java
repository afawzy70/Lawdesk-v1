package com.lawdesk.documents;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.TextLayout;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/** Builds real files for the archive tests: PDFs with and without a text layer, Word documents and page images. */
public final class ArchiveFiles {

    private ArchiveFiles() {
    }

    /** A PDF whose text layer holds the lines, in the bundled DejaVu Sans (Arabic letters included). */
    public static byte[] pdfWithText(String... lines) {
        try (PDDocument document = new PDDocument(); InputStream ttf = ArchiveFiles.class.getResourceAsStream("/fonts/DejaVuSans.ttf")) {
            PDType0Font font = PDType0Font.load(document, ttf);
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(font, 12);
                content.newLineAtOffset(50, 780);
                for (String line : lines) {
                    content.showText(line);
                    content.newLineAtOffset(0, -18);
                }
                content.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * A PDF whose lines were laid out the way word processors and PDF printers write Arabic: letters shaped into their
     * contextual presentation forms and stored in visual (left to right on the page) order. {@code logical} lines are in
     * ordinary reading order.
     */
    public static byte[] pdfWithArabic(String... logical) {
        String[] visual = new String[logical.length];
        for (int i = 0; i < logical.length; i++) {
            visual[i] = visual(logical[i]);
        }
        return pdfWithText(visual);
    }

    /** One line of logical text as it is stored in a PDF: shaped, then reordered for display. */
    public static String visual(String logical) {
        try {
            String shaped = new com.ibm.icu.text.ArabicShaping(com.ibm.icu.text.ArabicShaping.LETTERS_SHAPE).shape(logical);
            com.ibm.icu.text.Bidi bidi = new com.ibm.icu.text.Bidi(shaped, com.ibm.icu.text.Bidi.DIRECTION_DEFAULT_RIGHT_TO_LEFT);
            return bidi.writeReordered(com.ibm.icu.text.Bidi.DO_MIRRORING);
        } catch (com.ibm.icu.text.ArabicShapingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A PDF that is only a picture of the text (a scan): no text layer at all. */
    public static byte[] scannedPdf(String text) {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDImageXObject image = LosslessFactory.createFromImage(document, image(text, 40, 1200, 300));
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.drawImage(image, 30, 500, 520, 130);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A Word (.docx) document with one paragraph per line. */
    public static byte[] docx(String... paragraphs) {
        StringBuilder body = new StringBuilder();
        for (String paragraph : paragraphs) {
            body.append("<w:p><w:r><w:t xml:space=\"preserve\">").append(escape(paragraph)).append("</w:t></w:r></w:p>");
        }
        String document = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>" + body
                + "</w:body></w:document>";
        return zip("[Content_Types].xml", "<?xml version=\"1.0\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"/>",
                "word/document.xml", document);
    }

    /** A zip that claims to be a Word document and whose body declares an external entity. */
    public static byte[] docxWithEntity() {
        String document = "<?xml version=\"1.0\"?><!DOCTYPE d [<!ENTITY x SYSTEM \"file:///etc/hostname\">]>"
                + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body><w:p><w:r>"
                + "<w:t>&x;</w:t></w:r></w:p></w:body></w:document>";
        return zip("[Content_Types].xml", "<Types/>", "word/document.xml", document);
    }

    private static byte[] zip(String... namesAndContents) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (int i = 0; i < namesAndContents.length; i += 2) {
                zip.putNextEntry(new ZipEntry(namesAndContents[i]));
                zip.write(namesAndContents[i + 1].getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** A PNG of the text, drawn with the bundled DejaVu Sans and shaped by Java (so Arabic joins correctly). */
    public static byte[] png(String text) {
        return png(text, 48, 1400, 400);
    }

    public static byte[] png(String text, int size, int width, int height) {
        return encode(image(text, size, width, height), "png");
    }

    public static byte[] jpeg(String text) {
        return encode(image(text, 48, 1400, 400), "jpg");
    }

    public static byte[] tiff(String text) {
        return encode(image(text, 48, 1400, 400), "tiff");
    }

    /** True when the Java runtime can draw the bundled font (it always can; a headless server without fonts could not). */
    public static boolean canDraw() {
        try {
            font(20);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static Font font(float size) {
        try (InputStream ttf = ArchiveFiles.class.getResourceAsStream("/fonts/DejaVuSans.ttf")) {
            return Font.createFont(Font.TRUETYPE_FONT, ttf).deriveFont(size);
        } catch (IOException | FontFormatException e) {
            throw new IllegalStateException("the bundled font cannot be loaded", e);
        }
    }

    private static BufferedImage image(String text, int size, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.setColor(Color.BLACK);
            float y = size * 1.6f;
            for (String line : text.split("\n")) {
                if (line.isEmpty()) {
                    continue;
                }
                TextLayout layout = new TextLayout(line, font(size), g.getFontRenderContext());
                boolean rtl = layout.isLeftToRight() ? false : true;
                float x = rtl ? width - 40 - layout.getAdvance() : 40;
                layout.draw(g, x, y);
                y += size * 1.8f;
            }
        } finally {
            g.dispose();
        }
        return image;
    }

    private static byte[] encode(BufferedImage image, String format) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            if (!ImageIO.write(image, format, out)) {
                throw new IllegalStateException("no writer for " + format);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }
}
