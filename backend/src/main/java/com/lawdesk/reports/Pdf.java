package com.lawdesk.reports;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

/**
 * PDF for report exports (docs/delivery/PHASE-6-ACCEPTANCE.md, LD-034 criterion 6), written with Apache PDFBox: an
 * A4 landscape table with the header repeated on every page. The DejaVu Sans font (Bitstream Vera licence, public
 * domain changes; {@code fonts/DejaVuSans-LICENSE.txt}) is embedded as a subset, so Arabic shows on any reader. Each
 * line is shaped and reordered by {@link PdfText}; an Arabic export mirrors the page: the first column is on the
 * right and text is right-aligned.
 */
final class Pdf {

    private static final PDRectangle PAGE = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
    private static final float MARGIN = 36;
    private static final float TITLE_SIZE = 13;
    private static final float SIZE = 8;
    private static final float LEADING = 10.5f;
    private static final float PAD = 3;

    private final PDDocument document;
    private final PDType0Font font;
    private final boolean rightToLeft;
    private final Map<Integer, Boolean> glyphs = new HashMap<>();
    private PDPageContentStream content;
    private float y;

    private Pdf(PDDocument document, boolean rightToLeft) throws IOException {
        this.document = document;
        this.rightToLeft = rightToLeft;
        try (InputStream ttf = Pdf.class.getResourceAsStream("/fonts/DejaVuSans.ttf")) {
            if (ttf == null) {
                throw new IllegalStateException("the export font is missing");
            }
            this.font = PDType0Font.load(document, ttf);
        }
    }

    static byte[] write(ExportTable table) {
        try (PDDocument document = new PDDocument()) {
            Pdf pdf = new Pdf(document, table.labels().rightToLeft());
            pdf.table(table);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void table(ExportTable table) throws IOException {
        int columns = table.header().size();
        float columnWidth = (PAGE.getWidth() - 2 * MARGIN) / columns;
        newPage();
        line(table.title() + " — " + PdfText.isolated(table.subtitle()), MARGIN, PAGE.getWidth() - 2 * MARGIN,
                y - TITLE_SIZE, TITLE_SIZE);
        y -= TITLE_SIZE + 12;
        row(table.header(), columnWidth, true);
        for (List<String> cells : table.textRows()) {
            if (!row(cells, columnWidth, false)) {
                newPage();
                row(table.header(), columnWidth, true);
                if (!row(cells, columnWidth, false)) {
                    throw new IllegalStateException("a row is taller than a page");
                }
            }
        }
        content.close();
    }

    /** Draws one row; returns false, drawing nothing, when it does not fit on the page any more. */
    private boolean row(List<String> cells, float columnWidth, boolean header) throws IOException {
        List<List<String>> lines = new ArrayList<>();
        int height = 1;
        for (String cell : cells) {
            List<String> wrapped = PdfText.wrap(cell, columnWidth - 2 * PAD, this::width);
            lines.add(wrapped);
            height = Math.max(height, wrapped.size());
        }
        float rowHeight = height * LEADING + 2 * PAD;
        if (y - rowHeight < MARGIN) {
            return false;
        }
        if (header) {
            content.setNonStrokingColor(0.91f, 0.93f, 0.97f);
            content.addRect(MARGIN, y - rowHeight, PAGE.getWidth() - 2 * MARGIN, rowHeight);
            content.fill();
            content.setNonStrokingColor(0f, 0f, 0f);
        }
        for (int column = 0; column < cells.size(); column++) {
            float left = rightToLeft ? PAGE.getWidth() - MARGIN - (column + 1) * columnWidth
                    : MARGIN + column * columnWidth;
            float baseline = y - PAD - SIZE;
            for (String text : lines.get(column)) {
                line(text, left + PAD, columnWidth - 2 * PAD, baseline, SIZE);
                baseline -= LEADING;
            }
        }
        y -= rowHeight;
        content.setStrokingColor(0.8f, 0.8f, 0.8f);
        content.moveTo(MARGIN, y);
        content.lineTo(PAGE.getWidth() - MARGIN, y);
        content.stroke();
        return true;
    }

    /** One line of logical text inside a box of the given width: left-aligned, or right-aligned in Arabic. */
    private void line(String logical, float left, float boxWidth, float baseline, float size) throws IOException {
        String visual = drawable(PdfText.visual(logical, rightToLeft));
        if (visual.isEmpty()) {
            return;
        }
        float textWidth = font.getStringWidth(visual) / 1000 * size;
        content.beginText();
        content.setFont(font, size);
        content.newLineAtOffset(rightToLeft ? left + boxWidth - textWidth : left, baseline);
        content.showText(visual);
        content.endText();
    }

    private float width(String logical) {
        try {
            return font.getStringWidth(drawable(PdfText.visual(logical, rightToLeft))) / 1000 * SIZE;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Replaces characters the font cannot draw (control characters, rare scripts) with {@code ?}. */
    private String drawable(String text) {
        StringBuilder safe = new StringBuilder(text.length());
        text.codePoints().forEach(cp -> {
            boolean known = glyphs.computeIfAbsent(cp, this::hasGlyph);
            safe.appendCodePoint(known ? cp : '?');
        });
        return safe.toString();
    }

    private boolean hasGlyph(int codePoint) {
        if (Character.isISOControl(codePoint)) {
            return false;
        }
        try {
            font.encode(new String(Character.toChars(codePoint)));
            return true;
        } catch (IllegalArgumentException | IOException e) {
            return false;
        }
    }

    private void newPage() throws IOException {
        if (content != null) {
            content.close();
        }
        PDPage page = new PDPage(PAGE);
        document.addPage(page);
        content = new PDPageContentStream(document, page);
        content.setLineWidth(0.5f);
        y = PAGE.getHeight() - MARGIN;
    }
}
