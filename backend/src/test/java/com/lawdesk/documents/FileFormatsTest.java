package com.lawdesk.documents;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Files are recognised by their signature, never by their name or the type the browser declared. */
class FileFormatsTest {

    @Test
    void recognisesEveryAcceptedFormat() {
        assertThat(FileFormats.detect(ArchiveFiles.pdfWithText("x")).orElseThrow().code()).isEqualTo("PDF");
        assertThat(FileFormats.detect(ArchiveFiles.docx("x")).orElseThrow().code()).isEqualTo("DOCX");
        assertThat(FileFormats.detect(ArchiveFiles.png("x")).orElseThrow().code()).isEqualTo("PNG");
        assertThat(FileFormats.detect(ArchiveFiles.jpeg("x")).orElseThrow().code()).isEqualTo("JPEG");
        assertThat(FileFormats.detect(ArchiveFiles.tiff("x")).orElseThrow().code()).isEqualTo("TIFF");
        assertThat(FileFormats.detect(new byte[] {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A,
            (byte) 0xE1, 0}).orElseThrow().code()).isEqualTo("DOC");
    }

    @Test
    void refusesWhatLooksLikeAFormatButIsNot() {
        assertThat(FileFormats.detect("<html>%PDF-".getBytes(StandardCharsets.ISO_8859_1))).isEmpty();
        assertThat(FileFormats.detect("<svg xmlns='http://www.w3.org/2000/svg'/>".getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(FileFormats.detect("MZ\u0090\u0000".getBytes(StandardCharsets.ISO_8859_1))).isEmpty();
        assertThat(FileFormats.detect(new byte[0])).isEmpty();
        // A zip that is not a Word document (a jar, an Excel workbook, a bare archive) is refused.
        assertThat(FileFormats.detect(new byte[] {'P', 'K', 3, 4, 0, 0})).isEmpty();
    }

    @Test
    void theDeclaredTypeIsComparedWithoutParametersOrCase() {
        assertThat(FileFormats.declared("Application/PDF; charset=binary")).isEqualTo("application/pdf");
        assertThat(FileFormats.declared(null)).isEmpty();
    }

    @Test
    void listsOnlyTheEnabledFormats() {
        var formats = List.of(new ArchiveSettings.Format("PDF", "PDF", "application/pdf", "pdf", true),
                new ArchiveSettings.Format("PNG", "IMAGE", "image/png", "png", false));
        assertThat(FileFormats.codes(formats)).containsExactly("PDF");
    }
}
