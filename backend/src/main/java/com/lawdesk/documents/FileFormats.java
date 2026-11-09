package com.lawdesk.documents;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Recognises an uploaded file by its signature, never by its name: the declared content type must also agree with what the
 * bytes are. Only the formats the archive knows how to verify exist here; {@link ArchiveSettings} says which are accepted.
 */
final class FileFormats {

    static final String DOCX_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    /** What the bytes are: the format code of {@code archive_format} and its media type. */
    record Detected(String code, String mediaType) {
    }

    private static final int MAX_ZIP_ENTRIES = 2000;

    private FileFormats() {
    }

    /** The format of {@code content}, or empty when it is none the archive verifies. */
    static Optional<Detected> detect(byte[] content) {
        if (startsWith(content, "%PDF-".getBytes(StandardCharsets.ISO_8859_1))) {
            return Optional.of(new Detected("PDF", "application/pdf"));
        }
        if (startsWith(content, new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A})) {
            return Optional.of(new Detected("PNG", "image/png"));
        }
        if (startsWith(content, new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})) {
            return Optional.of(new Detected("JPEG", "image/jpeg"));
        }
        if (startsWith(content, new byte[] {'I', 'I', '*', 0}) || startsWith(content, new byte[] {'M', 'M', 0, '*'})) {
            return Optional.of(new Detected("TIFF", "image/tiff"));
        }
        if (startsWith(content, new byte[] {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A,
            (byte) 0xE1})) {
            return Optional.of(new Detected("DOC", "application/msword"));
        }
        if (startsWith(content, new byte[] {'P', 'K', 3, 4}) && isDocx(content)) {
            return Optional.of(new Detected("DOCX", DOCX_TYPE));
        }
        return Optional.empty();
    }

    /** The declared content type without parameters, lower-cased ("" when absent). */
    static String declared(String contentType) {
        return contentType == null ? "" : contentType.split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
    }

    private static boolean startsWith(byte[] content, byte[] prefix) {
        return content.length >= prefix.length && Arrays.equals(content, 0, prefix.length, prefix, 0, prefix.length);
    }

    /** A Word document is a zip holding [Content_Types].xml and word/document.xml. */
    private static boolean isDocx(byte[] content) {
        boolean types = false;
        boolean body = false;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            int seen = 0;
            while ((entry = zip.getNextEntry()) != null && seen++ < MAX_ZIP_ENTRIES) {
                types |= "[Content_Types].xml".equals(entry.getName());
                body |= "word/document.xml".equals(entry.getName());
            }
        } catch (IOException | IllegalArgumentException e) {
            return false;
        }
        return types && body;
    }

    static List<String> codes(List<ArchiveSettings.Format> formats) {
        return formats.stream().filter(ArchiveSettings.Format::enabled).map(ArchiveSettings.Format::code).toList();
    }
}
