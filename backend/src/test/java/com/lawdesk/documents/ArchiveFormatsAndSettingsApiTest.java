package com.lawdesk.documents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import com.lawdesk.security.Permission;

/**
 * LD-055 (requirement 13): the archive accepts PDF, Word and images, recognised by their signature, within limits that are
 * settings. The default values are ASSUMPTIONS recorded in docs/evidence/DOC-WP5/README.md.
 */
class ArchiveFormatsAndSettingsApiTest extends ArchiveTestSupport {

    private static final String DOCX = FileFormats.DOCX_TYPE;
    private static final String SETTINGS = "/api/v1/archive/settings";

    private String update(int maxFile, int perCase, int versions, String formats) {
        return """
                {"limits":{"maxFileBytes":%d,"maxDocumentsPerCase":%d,"maxVersionsPerDocument":%d,"maxOcrPages":50,
                 "maxTextChars":2000000},"enabledFormats":%s}""".formatted(maxFile, perCase, versions, formats);
    }

    @Test
    void acceptsPdfWordAndImagesAndKeepsTheirMediaType() throws Exception {
        UUID caseId = newCase();
        Object[][] samples = {
            {"a.pdf", "application/pdf", ArchiveFiles.pdfWithText("pdf")},
            {"b.docx", DOCX, ArchiveFiles.docx("word")},
            {"c.doc", "application/msword", new byte[] {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1, 0, 1}},
            {"d.png", "image/png", ArchiveFiles.png("png")},
            {"e.jpg", "image/jpeg", ArchiveFiles.jpeg("jpeg")},
            {"f.tif", "image/tiff", ArchiveFiles.tiff("tiff")}};
        for (Object[] sample : samples) {
            upload(everything(), "/api/v1/cases/" + caseId + "/documents", file((String) sample[0], (String) sample[1], (byte[]) sample[2]))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.mediaType").value(sample[1]))
                    .andExpect(jsonPath("$.currentVersionNo").value(1));
        }
        assertThat(count("select count(*) from document_version where document_id in (select id from case_document where case_id = ?)", caseId))
                .isEqualTo(6);
    }

    @Test
    void refusesFilesThatAreNotWhatTheyClaimToBe() throws Exception {
        String path = "/api/v1/cases/" + newCase() + "/documents";
        byte[] png = ArchiveFiles.png("x");
        // Picture content declared as a PDF, a PDF declared as a picture, and a Word type on a bare zip.
        upload(everything(), path, file("a.pdf", "application/pdf", png)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("UnsupportedFormat"));
        upload(everything(), path, file("a.png", "image/png", ArchiveFiles.pdfWithText("x"))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("UnsupportedFormat"));
        upload(everything(), path, file("a.docx", DOCX, new byte[] {'P', 'K', 3, 4, 0, 0, 0, 0})).andExpect(status().isBadRequest());
        // Formats the archive does not verify: script, markup, vector images, executables and archives.
        for (String[] bad : new String[][] {{"x.html", "text/html", "<html><script>alert(1)</script></html>"},
            {"x.svg", "image/svg+xml", "<svg onload='alert(1)'/>"}, {"x.exe", "application/octet-stream", "MZ\u0090\u0000"},
            {"x.txt", "text/plain", "hello"}}) {
            upload(everything(), path, file(bad[0], bad[1], bad[2].getBytes(StandardCharsets.ISO_8859_1))).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("file"));
        }
        assertThat(count("select count(*) from case_document")).isZero();
    }

    @Test
    void theFileNameNeverDecidesTheFormat() throws Exception {
        String path = "/api/v1/cases/" + newCase() + "/documents";
        upload(everything(), path, file("report.pdf", "image/png", ArchiveFiles.png("really a picture"))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.mediaType").value("image/png")).andExpect(jsonPath("$.fileName").value("report.pdf"));
    }

    @Test
    void anArchiveAdministratorCanSwitchAFormatOff() throws Exception {
        UUID caseId = newCase();
        int audited = count("select count(*) from audit_event where action = 'ARCHIVE_SETTINGS_UPDATE'");
        put(everything(), SETTINGS, update(10485760, 100, 20, "[\"PDF\",\"DOCX\"]")).andExpect(status().isForbidden());
        put(with(Permission.DOCUMENT_ARCHIVE_ADMIN), SETTINGS, update(10485760, 100, 20, "[\"PDF\",\"DOCX\"]")).andExpect(status().isOk())
                .andExpect(jsonPath("$.formats[?(@.code=='PNG')].enabled").value(false))
                .andExpect(jsonPath("$.formats[?(@.code=='PDF')].enabled").value(true));
        upload(everything(), "/api/v1/cases/" + caseId + "/documents", file("x.png", "image/png", ArchiveFiles.png("x")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].code").value("UnsupportedFormat"));
        upload(everything(), "/api/v1/cases/" + caseId + "/documents", file("x.docx", DOCX, ArchiveFiles.docx("x"))).andExpect(status().isCreated());
        assertThat(count("select count(*) from audit_event where action = 'ARCHIVE_SETTINGS_UPDATE'")).isEqualTo(audited + 1);
    }

    @Test
    void theSettingsCanBeReadByAnyoneWhoReadsDocumentsAndShowTheEngineHonestly() throws Exception {
        get(with(Permission.DOCUMENT_VIEW), SETTINGS).andExpect(status().isOk())
                .andExpect(jsonPath("$.limits.maxFileBytes").value(10485760))
                .andExpect(jsonPath("$.limits.maxDocumentsPerCase").value(100))
                .andExpect(jsonPath("$.limits.maxVersionsPerDocument").value(20))
                .andExpect(jsonPath("$.limits.maxOcrPages").value(50))
                .andExpect(jsonPath("$.formats.length()").value(6))
                .andExpect(jsonPath("$.ocr.available").value(true));
        ocr.available = false;
        get(with(Permission.DOCUMENT_VIEW), SETTINGS).andExpect(jsonPath("$.ocr.available").value(false))
                .andExpect(jsonPath("$.ocr.detail").value(Matchers.containsString("not installed")));
        get(with(Permission.CASE_VIEW), SETTINGS).andExpect(status().isForbidden());
    }

    @Test
    void settingsOutsideTheirBoundsOrWithoutAFormatAreRefused() throws Exception {
        var admin = with(Permission.DOCUMENT_ARCHIVE_ADMIN);
        // The web layer's own limit is 10 MiB, so the file limit cannot be raised past it by a setting.
        put(admin, SETTINGS, update(10485761, 100, 20, "[\"PDF\"]")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("maxFileBytes")).andExpect(jsonPath("$.errors[0].code").value("Range"));
        put(admin, SETTINGS, update(10485760, 0, 20, "[\"PDF\"]")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("maxDocumentsPerCase"));
        put(admin, SETTINGS, update(10485760, 100, 20, "[]")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("enabledFormats"));
        put(admin, SETTINGS, update(10485760, 100, 20, "[\"EXE\"]")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("Unknown"));
        put(admin, SETTINGS, "{}").andExpect(status().isBadRequest());
        assertThat(count("select int_value from archive_setting where code = 'MAX_DOCUMENTS_PER_CASE'")).isEqualTo(100);
        assertThat(count("select count(*) from archive_format where enabled")).isEqualTo(6);
    }

    @Test
    void theFileSizeLimitIsASettingThatTheUploadRespects() throws Exception {
        put(with(Permission.DOCUMENT_ARCHIVE_ADMIN), SETTINGS, update(1048576, 100, 20, "[\"PDF\"]")).andExpect(status().isOk());
        byte[] twoMiB = new byte[2 * 1024 * 1024];
        java.util.Arrays.fill(twoMiB, (byte) ' ');
        System.arraycopy("%PDF-".getBytes(StandardCharsets.ISO_8859_1), 0, twoMiB, 0, 5);
        upload(everything(), "/api/v1/cases/" + newCase() + "/documents", pdfFile("big.pdf", twoMiB)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("MaxSize"));
    }

    @Test
    void theNumberOfDocumentsPerCaseIsASettingThatTheUploadRespects() throws Exception {
        UUID caseId = newCase();
        put(with(Permission.DOCUMENT_ARCHIVE_ADMIN), SETTINGS, update(10485760, 2, 20, "[\"PDF\"]")).andExpect(status().isOk());
        attach(everything(), caseId, pdfFile("1.pdf", ArchiveFiles.pdfWithText("1")));
        attach(everything(), caseId, pdfFile("2.pdf", ArchiveFiles.pdfWithText("2")));
        upload(everything(), "/api/v1/cases/" + caseId + "/documents", pdfFile("3.pdf", ArchiveFiles.pdfWithText("3")))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.rule").value("DOCUMENT_LIMIT_REACHED"));
        // Another case has its own count.
        attach(everything(), newCase(), pdfFile("1.pdf", ArchiveFiles.pdfWithText("1")));
    }

    @Test
    void thePermissionsOfTheArchiveAreSeededForTheRolesTheDesignNames() {
        assertThat(rolesWith("DOCUMENT_ARCHIVE_ADMIN")).containsExactlyInAnyOrder("ADMIN", "ARCHIVIST");
        assertThat(rolesWith("MEMO_APPROVE")).containsExactlyInAnyOrder("ADMIN", "LEGAL_MANAGER", "SECTION_HEAD");
        assertThat(rolesWith("ENFORCEMENT_MANAGE")).containsExactlyInAnyOrder("ADMIN", "LEGAL_MANAGER", "SECTION_HEAD", "LEGAL_STAFF");
        assertThat(rolesWith("ENFORCEMENT_VIEW")).containsExactlyInAnyOrder("ADMIN", "LEGAL_MANAGER", "SECTION_HEAD", "LEGAL_STAFF",
                "DATA_ENTRY", "READ_ONLY");
    }

    private java.util.List<String> rolesWith(String permission) {
        return jdbc.queryForList("""
                select r.code from role_permission rp join app_role r on r.id = rp.role_id where rp.permission = ?""", String.class, permission);
    }
}
