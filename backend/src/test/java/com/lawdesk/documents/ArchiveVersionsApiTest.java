package com.lawdesk.documents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.lawdesk.security.Permission;

/** LD-055 (requirement 13): previous versions are kept; who uploaded each and when; access follows the case. */
class ArchiveVersionsApiTest extends ArchiveTestSupport {

    private static final byte[] FIRST = ArchiveFiles.pdfWithText("first version of the memo");
    private static final byte[] SECOND = ArchiveFiles.pdfWithText("second version of the memo, longer than the first");

    @Test
    void everyUploadStartsAtVersionOneWithTheDocumentsOwnId() throws Exception {
        UUID caseId = newCase();
        UUID id = attach(everything(), caseId, pdfFile("memo.pdf", FIRST));

        assertThat(count("select count(*) from document_version where id = ? and document_id = ? and version_no = 1", id, id)).isOne();
        get(everything(), "/api/v1/documents/" + id + "/versions").andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].versionNo").value(1))
                .andExpect(jsonPath("$[0].current").value(true))
                .andExpect(jsonPath("$[0].uploadedBy").value(USER.toString()))
                .andExpect(jsonPath("$[0].uploadedAt").isString())
                .andExpect(jsonPath("$[0].fileName").value("memo.pdf"));
        get(everything(), "/api/v1/documents/" + id).andExpect(jsonPath("$.currentVersionNo").value(1));
    }

    @Test
    void aNewVersionBecomesCurrentAndEveryEarlierOneIsKept() throws Exception {
        UUID caseId = newCase();
        UUID id = attach(everything(), caseId, pdfFile("memo.pdf", FIRST));
        UUID second = UUID.randomUUID();

        upload(withId(second, Permission.DOCUMENT_MANAGE, Permission.DOCUMENT_VIEW, Permission.CASE_VIEW), "/api/v1/documents/" + id + "/versions",
                pdfFile("memo-v2.pdf", SECOND), "note", "after the review").andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionNo").value(2)).andExpect(jsonPath("$.changeNote").value("after the review"))
                .andExpect(jsonPath("$.uploadedBy").value(second.toString())).andExpect(jsonPath("$.current").value(true));

        get(everything(), "/api/v1/documents/" + id).andExpect(jsonPath("$.currentVersionNo").value(2))
                .andExpect(jsonPath("$.fileName").value("memo-v2.pdf")).andExpect(jsonPath("$.sizeBytes").value(SECOND.length));
        get(everything(), "/api/v1/documents/" + id + "/versions").andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].versionNo").value(2)).andExpect(jsonPath("$[0].current").value(true))
                .andExpect(jsonPath("$[1].versionNo").value(1)).andExpect(jsonPath("$[1].current").value(false))
                .andExpect(jsonPath("$[1].uploadedBy").value(USER.toString()));
        // The earlier file is still downloadable, byte for byte; the plain content is the current version.
        get(everything(), "/api/v1/documents/" + id + "/versions/1/content").andExpect(status().isOk()).andExpect(content().bytes(FIRST));
        get(everything(), "/api/v1/documents/" + id + "/content").andExpect(status().isOk()).andExpect(content().bytes(SECOND));
        get(everything(), "/api/v1/documents/" + id + "/versions/3/content").andExpect(status().isNotFound());
        assertThat(count("select count(*) from case_event where case_id = ? and type = 'DOCUMENT_VERSION_ADDED'", caseId)).isOne();
    }

    @Test
    void aDocumentKeepsAtMostTheConfiguredNumberOfVersions() throws Exception {
        jdbc.update("update archive_setting set int_value = 2 where code = 'MAX_VERSIONS_PER_DOCUMENT'");
        UUID id = attach(everything(), newCase(), pdfFile("memo.pdf", FIRST));
        upload(everything(), "/api/v1/documents/" + id + "/versions", pdfFile("v2.pdf", SECOND)).andExpect(status().isCreated());
        upload(everything(), "/api/v1/documents/" + id + "/versions", pdfFile("v3.pdf", ArchiveFiles.pdfWithText("third")))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.rule").value("DOCUMENT_VERSION_LIMIT_REACHED"));
        assertThat(count("select count(*) from document_version where document_id = ?", id)).isEqualTo(2);
    }

    @Test
    void aVersionMayChangeTheFormatOfTheFile() throws Exception {
        UUID id = attach(everything(), newCase(), pdfFile("scan.pdf", FIRST));
        upload(everything(), "/api/v1/documents/" + id + "/versions", file("scan.png", "image/png", ArchiveFiles.png("scan")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.mediaType").value("image/png"));
        get(everything(), "/api/v1/documents/" + id).andExpect(jsonPath("$.mediaType").value("image/png"));
        get(everything(), "/api/v1/documents/" + id + "/versions/1/content").andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Type", "application/pdf"));
    }

    @Test
    void aWithdrawnDocumentTakesNoNewVersion() throws Exception {
        UUID id = attach(everything(), newCase(), pdfFile("memo.pdf", FIRST));
        post(everything(), "/api/v1/documents/" + id + "/withdraw", "{\"reason\":\"filed twice\",\"version\":0}")
                .andExpect(status().isNoContent());
        upload(everything(), "/api/v1/documents/" + id + "/versions", pdfFile("v2.pdf", SECOND))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.rule").value("DOCUMENT_WITHDRAWN"));
    }

    @Test
    void anArchivedCaseTakesNoNewVersion() throws Exception {
        UUID caseId = newCase();
        UUID id = attach(everything(), caseId, pdfFile("memo.pdf", FIRST));
        jdbc.update("update legal_case set status = 'ARCHIVED' where id = ?", caseId);
        upload(everything(), "/api/v1/documents/" + id + "/versions", pdfFile("v2.pdf", SECOND))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.rule").value("CASE_ARCHIVED"));
    }

    @Test
    void theNoteAndTheFileAreChecked() throws Exception {
        UUID id = attach(everything(), newCase(), pdfFile("memo.pdf", FIRST));
        upload(everything(), "/api/v1/documents/" + id + "/versions", pdfFile("v2.pdf", SECOND), "note", "n".repeat(501))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("note"));
        upload(everything(), "/api/v1/documents/" + id + "/versions", file("v2.pdf", "application/pdf", "plain text".getBytes()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].code").value("UnsupportedFormat"));
        assertThat(count("select count(*) from document_version where document_id = ?", id)).isOne();
    }

    // Access follows the case: 404 for those who may not see it, 403 for those who see but may not change it.

    @Test
    void versionsAreAsVisibleAsTheirCase() throws Exception {
        UUID owner = user("Owner");
        UUID caseId = newCase(owner);
        UUID id = attach(member(owner), caseId, pdfFile("memo.pdf", FIRST));
        UUID stranger = UUID.randomUUID();

        get(outsider(stranger), "/api/v1/documents/" + id + "/versions").andExpect(status().isNotFound());
        get(outsider(stranger), "/api/v1/documents/" + id + "/versions/1/content").andExpect(status().isNotFound());
        upload(outsider(stranger), "/api/v1/documents/" + id + "/versions", pdfFile("v2.pdf", SECOND)).andExpect(status().isNotFound());
        get(viewer(stranger), "/api/v1/documents/" + id + "/versions").andExpect(status().isOk());
        upload(viewer(stranger), "/api/v1/documents/" + id + "/versions", pdfFile("v2.pdf", SECOND)).andExpect(status().isForbidden());
        upload(member(owner), "/api/v1/documents/" + id + "/versions", pdfFile("v2.pdf", SECOND)).andExpect(status().isCreated());
    }

    @Test
    void permissionsAreCheckedBeforeTheUploadIsRead() throws Exception {
        UUID id = attach(everything(), newCase(), pdfFile("memo.pdf", FIRST));
        upload(with(Permission.DOCUMENT_VIEW, Permission.CASE_VIEW), "/api/v1/documents/" + id + "/versions", pdfFile("v2.pdf", SECOND))
                .andExpect(status().isForbidden());
        get(with(Permission.CASE_VIEW), "/api/v1/documents/" + id + "/versions").andExpect(status().isForbidden());
    }

    @Test
    void aStoredVersionCanNeverBeRewritten() {
        UUID caseId = newCase();
        UUID id = jdbc.queryForObject("""
                insert into case_document (case_id, title, file_name, media_type, size_bytes, sha256, created_by)
                values (?, 't', 'f.pdf', 'application/pdf', 5, repeat('a', 64), ?) returning id""", UUID.class, caseId, USER);
        jdbc.update("""
                insert into document_version (id, document_id, version_no, file_name, media_type, size_bytes, sha256, uploaded_by)
                values (?, ?, 1, 'f.pdf', 'application/pdf', 5, repeat('a', 64), ?)""", id, id, USER);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update("update document_version set sha256 = repeat('b', 64) where id = ?", id))
                .hasMessageContaining("never changed");
    }
}
