package com.lawdesk.documents;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.imageio.ImageIO;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/**
 * Reads the text a file already carries, locally and without running anything inside the file: the text layer of a PDF and
 * the paragraphs of a Word document. A file without text (a scan, an image) is reported as needing recognition. The text is
 * normalised to Unicode NFKC so Arabic presentation forms become ordinary letters; nothing is interpreted.
 */
@Component
class TextExtractor {

    enum Outcome { TEXT, NEEDS_OCR, NO_TEXT, UNSUPPORTED, FAILED }

    record Result(Outcome outcome, String text, String method, int pages, boolean truncated) {

        static Result of(Outcome outcome) {
            return new Result(outcome, null, null, 0, false);
        }
    }

    /** A PDF whose text layer holds fewer letters than this is treated as a scan. */
    static final int MIN_TEXT_LAYER = 20;
    private static final int MAX_DOCX_XML_BYTES = 30 * 1024 * 1024;
    private static final float OCR_DPI = 300f;

    Result extract(String mediaType, byte[] content, int maxChars) {
        return switch (mediaType) {
            case "application/pdf" -> pdf(content, maxChars);
            case FileFormats.DOCX_TYPE -> docx(content, maxChars);
            case "image/png", "image/jpeg", "image/tiff" -> Result.of(Outcome.NEEDS_OCR);
            default -> Result.of(Outcome.UNSUPPORTED);
        };
    }

    private Result pdf(byte[] content, int maxChars) {
        try (PDDocument document = Loader.loadPDF(content)) {
            if (document.isEncrypted()) {
                return Result.of(Outcome.FAILED);
            }
            String raw = new PDFTextStripper().getText(document);
            String text = clean(raw);
            int pages = document.getNumberOfPages();
            if (letters(text) < MIN_TEXT_LAYER) {
                return new Result(Outcome.NEEDS_OCR, null, null, pages, false);
            }
            return new Result(Outcome.TEXT, cap(text, maxChars), "PDF_TEXT", pages, text.length() > maxChars);
        } catch (IOException | RuntimeException e) {
            return Result.of(Outcome.FAILED);
        }
    }

    private Result docx(byte[] content, int maxChars) {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    String text = clean(paragraphs(boundedCopy(zip)));
                    if (letters(text) == 0) {
                        return Result.of(Outcome.NO_TEXT);
                    }
                    return new Result(Outcome.TEXT, cap(text, maxChars), "DOCX", 0, text.length() > maxChars);
                }
            }
            return Result.of(Outcome.NO_TEXT);
        } catch (IOException | XMLStreamException | RuntimeException e) {
            return Result.of(Outcome.FAILED);
        }
    }

    /** The XML of the body, refusing a part that expands past a safe size (a zip bomb). */
    private static byte[] boundedCopy(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) > 0) {
            if (out.size() + read > MAX_DOCX_XML_BYTES) {
                throw new IOException("document.xml is too large");
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    /** The w:t runs of the body, a line per paragraph; entities and DTDs are disabled. */
    private static String paragraphs(byte[] xml) throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        XMLStreamReader reader = factory.createXMLStreamReader(new ByteArrayInputStream(xml));
        StringBuilder text = new StringBuilder();
        boolean inText = false;
        try {
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String name = reader.getLocalName();
                    inText = "t".equals(name);
                    if ("tab".equals(name)) {
                        text.append('\t');
                    } else if ("br".equals(name)) {
                        text.append('\n');
                    }
                } else if (event == XMLStreamConstants.CHARACTERS && inText) {
                    text.append(reader.getText());
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    inText = false;
                    if ("p".equals(reader.getLocalName())) {
                        text.append('\n');
                    }
                }
            }
        } finally {
            reader.close();
        }
        return text.toString();
    }

    /** NFKC, control characters (except line breaks and tabs) removed, no NUL bytes (PostgreSQL text refuses them). */
    static String clean(String raw) {
        String normal = Normalizer.normalize(raw, Normalizer.Form.NFKC);
        return normal.replaceAll("[\\p{Cc}&&[^\\n\\t]]", " ").replaceAll("[ \\t]+\\n", "\n").strip();
    }

    private static int letters(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (Character.isLetterOrDigit(text.charAt(i))) {
                count++;
            }
        }
        return count;
    }

    private static String cap(String text, int maxChars) {
        return text.length() <= maxChars ? text : text.substring(0, maxChars);
    }

    /** The first {@code maxPages} pages of a PDF as PNG images for recognition. */
    PageImages render(byte[] pdf, int maxPages) throws IOException {
        List<byte[]> images = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDFRenderer renderer = new PDFRenderer(document);
            int total = document.getNumberOfPages();
            for (int page = 0; page < Math.min(total, maxPages); page++) {
                BufferedImage image = renderer.renderImageWithDPI(page, OCR_DPI);
                ByteArrayOutputStream png = new ByteArrayOutputStream();
                ImageIO.write(image, "png", png);
                images.add(png.toByteArray());
            }
            return new PageImages(images, total);
        }
    }

    record PageImages(List<byte[]> images, int totalPages) {
    }
}
