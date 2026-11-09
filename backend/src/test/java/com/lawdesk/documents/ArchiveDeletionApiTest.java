package com.lawdesk.documents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.lawdesk.security.Permission;

/**
 * LD-055 (requirement 13): nothing is deleted for good except through the approved procedure: the document is first
 * withdrawn from the case file (reversible), then deleted by a holder of DOCUMENT_ARCHIVE_ADMIN who states why; the deletion
 * is logged without content and audited, and the database refuses any other delete.
 */
class ArchiveDeletionApiTest extends ArchiveTestSupport {

    private static final byte[] FIRST = ArchiveFiles.pdfWithText("the first text of the document");

    private UUID document(UUID caseId) throws Exception {
        UUID id = attach(everything(), caseId, pdfFile("memo.pdf", FIRST));
        upload(everything(), "/api/v1/documents/" + id + "/versions", pdfFile("memo2.pdf", ArchiveFiles.pdfWithText("the second text"))).andExpect(status().isCreated());
        return id;
    }

    private long version(UUID id) {
        return jdbc.queryForObject("select version from case_document where id = ?", Long.class, id);
    }

    private String withdraw(UUID id) {
        return "{\"reason\":\"filed in the wrong case\",\"version\":" + version(id) + "}";
    }

    @Test
    void withdrawingHidesTheDocumentButDeletesNothing() throws Exception {
        UUID caseId = newCase();
        UUID id = document(caseId);
        post(everything(), "/api/v1/documents/" + id + "/withdraw", withdraw(id)).andExpect(status().isNoContent());

        assertThat(count("select count(*) from case_document where id = ? and withdrawn_at is not null", id)).isOne();
        assertThat(count("select count(*) from document_version where document_id = ?", id)).isEqualTo(2);
        assertThat(count("select count(*) from document_version_content where version_id in (select id from document_version where document_id = ?)", id)).isEqualTo(2);
        get(everything(), "/api/v1/cases/" + caseId + "/documents").andExpect(jsonPath("$.totalItems").value(0));
        get(everything(), "/api/v1/documents").andExpect(jsonPath("$.totalItems").value(0));
        get(everything(), "/api/v1/cases/" + caseId + "/documents/withdrawn").andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].withdrawnReason").value("filed in the wrong case"));
        post(everything(), "/api/v1/documents/" + id + "/withdraw", withdraw(id)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("DOCUMENT_WITHDRAWN"));
        assertThat(count("select count(*) from audit_event where action = 'DOCUMENT_WITHDRAW' and target_id = ?", id.toString())).isOne();
    }

    @Test
    void aReasonIsRequiredToWithdraw() throws Exception {
        UUID id = document(newCase());
        post(everything(), "/api/v1/documents/" + id + "/withdraw", "{\"reason\":\"  \",\"version\":" + version(id) + "}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("reason"));
        post(everything(), "/api/v1/documents/" + id + "/withdraw", "{\"reason\":\"x\"}").andExpect(status().isBadRequest());
        post(everything(), "/api/v1/documents/" + id + "/withdraw", "{\"reason\":\"x\",\"version\":99}").andExpect(status().isConflict());
    }

    @Test
    void aWithdrawnDocumentCanBeRestored() throws Exception {
        UUID caseId = newCase();
        UUID id = document(caseId);
        post(everything(), "/api/v1/documents/" + id + "/restore", "{\"version\":" + version(id) + "}")
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.rule").value("DOCUMENT_NOT_WITHDRAWN"));
        post(everything(), "/api/v1/documents/" + id + "/withdraw", withdraw(id)).andExpect(status().isNoContent());
        post(everything(), "/api/v1/documents/" + id + "/restore", "{\"version\":" + version(id) + "}").andExpect(status().isNoContent());
        get(everything(), "/api/v1/cases/" + caseId + "/documents").andExpect(jsonPath("$.totalItems").value(1));
        assertThat(count("select count(*) from case_document where id = ? and withdrawn_at is null and withdrawn_reason is null", id)).isOne();
    }

    @Test
    void theDatabaseRefusesAnyDeleteOutsideTheProcedure() throws Exception {
        UUID id = document(newCase());
        assertThatThrownBy(() -> jdbc.update("delete from case_document where id = ?", id)).hasMessageContaining("deletion procedure");
        assertThatThrownBy(() -> jdbc.update("delete from document_version where document_id = ?", id)).hasMessageContaining("deletion procedure");
        assertThatThrownBy(() -> jdbc.update("delete from document_version_content")).hasMessageContaining("deletion procedure");
        assertThat(count("select count(*) from document_version where document_id = ?", id)).isEqualTo(2);
    }

    @Test
    void onlyAnArchiveAdministratorDeletesAndOnlyAWithdrawnDocumentWithAReason() throws Exception {
        UUID id = document(newCase());
        String body = "{\"reason\":\"wrongly filed; the original is kept in the court file\"}";
        post(everything(), "/api/v1/documents/" + id + "/permanent-deletion", body).andExpect(status().isForbidden());
        var admin = with(Permission.DOCUMENT_ARCHIVE_ADMIN, Permission.DOCUMENT_VIEW, Permission.CASE_VIEW);
        // Still in the case file: the procedure starts with withdrawing.
        post(admin, "/api/v1/documents/" + id + "/permanent-deletion", body).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("DOCUMENT_NOT_WITHDRAWN"));
        post(everything(), "/api/v1/documents/" + id + "/withdraw", withdraw(id)).andExpect(status().isNoContent());
        post(admin, "/api/v1/documents/" + id + "/permanent-deletion", "{\"reason\":\"\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("reason"));
        assertThat(count("select count(*) from case_document where id = ?", id)).isOne();
    }

    @Test
    void deletingForGoodRemovesEveryVersionAndLeavesALogWithoutContent() throws Exception {
        UUID caseId = newCase();
        UUID id = document(caseId);
        String shas = String.join(",", jdbc.queryForList("select sha256 from document_version where document_id = ? order by version_no", String.class, id));
        UUID admin = UUID.randomUUID();
        post(everything(), "/api/v1/documents/" + id + "/withdraw", withdraw(id)).andExpect(status().isNoContent());

        post(withId(admin, Permission.DOCUMENT_ARCHIVE_ADMIN, Permission.DOCUMENT_VIEW, Permission.CASE_VIEW), "/api/v1/documents/" + id + "/permanent-deletion",
                "{\"reason\":\"duplicate of another file\"}").andExpect(status().isNoContent());

        assertThat(count("select count(*) from case_document where id = ?", id)).isZero();
        assertThat(count("select count(*) from document_version where document_id = ?", id)).isZero();
        assertThat(count("select count(*) from document_text where document_id = ?", id)).isZero();
        assertThat(jdbc.queryForMap("select title, file_name, version_count, sha256_list, withdrawn_by, deleted_by, deleted_reason from document_deletion_log where document_id = ?", id))
                .containsEntry("title", "memo.pdf").containsEntry("version_count", 2).containsEntry("sha256_list", jdbc.queryForObject(
                        "select sha256_list from document_deletion_log where document_id = ?", String.class, id))
                .containsEntry("withdrawn_by", USER).containsEntry("deleted_by", admin).containsEntry("deleted_reason", "duplicate of another file");
        assertThat(jdbc.queryForObject("select sha256_list from document_deletion_log where document_id = ?", String.class, id))
                .isEqualTo(shas.split(",")[0] + "," + shas.split(",")[1]);
        assertThat(count("select count(*) from audit_event where action = 'DOCUMENT_PURGE' and actor_id = ? and target_id = ?", admin, id.toString())).isOne();
        get(everything(), "/api/v1/documents/" + id).andExpect(status().isNotFound());
        get(with(Permission.DOCUMENT_ARCHIVE_ADMIN), "/api/v1/documents/deletion-log?caseId=" + caseId).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].deletedReason").value("duplicate of another file"))
                .andExpect(jsonPath("$[0].versionCount").value(2)).andExpect(jsonPath("$[0].content").doesNotExist());
    }

    @Test
    void theDeletionLogCannotBeChangedOrDeleted() throws Exception {
        UUID id = document(newCase());
        post(everything(), "/api/v1/documents/" + id + "/withdraw", withdraw(id)).andExpect(status().isNoContent());
        post(with(Permission.DOCUMENT_ARCHIVE_ADMIN, Permission.DOCUMENT_VIEW, Permission.CASE_VIEW), "/api/v1/documents/" + id + "/permanent-deletion",
                "{\"reason\":\"duplicate\"}").andExpect(status().isNoContent());
        assertThatThrownBy(() -> jdbc.update("update document_deletion_log set deleted_reason = 'other'")).hasMessageContaining("never changed");
        assertThatThrownBy(() -> jdbc.update("delete from document_deletion_log")).hasMessageContaining("deletion procedure");
    }

    @Test
    void aDocumentThatRecordsOfTheCaseStillUseIsNotDeleted() throws Exception {
        UUID caseId = newCase();
        UUID id = document(caseId);
        UUID claim = claim(caseId, 1);
        jdbc.update("update claim set source_document_id = ? where id = ?", id, claim);
        post(everything(), "/api/v1/documents/" + id + "/withdraw", withdraw(id)).andExpect(status().isNoContent());
        post(with(Permission.DOCUMENT_ARCHIVE_ADMIN, Permission.DOCUMENT_VIEW, Permission.CASE_VIEW), "/api/v1/documents/" + id + "/permanent-deletion",
                "{\"reason\":\"duplicate\"}").andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.rule").value("DOCUMENT_REFERENCED"));
        assertThat(count("select count(*) from case_document where id = ?", id)).isOne();
    }

    @Test
    void theProcedureFollowsTheCaseAccessRule() throws Exception {
        UUID owner = user("Owner");
        UUID stranger = UUID.randomUUID();
        UUID caseId = newCase(owner);
        UUID id = attach(member(owner), caseId, pdfFile("memo.pdf", FIRST));
        long v = version(id);
        // Not visible: 404 for every step. Visible but not changeable: 403 for withdrawing.
        post(outsider(stranger), "/api/v1/documents/" + id + "/withdraw", "{\"reason\":\"x\",\"version\":" + v + "}").andExpect(status().isNotFound());
        post(viewer(stranger), "/api/v1/documents/" + id + "/withdraw", "{\"reason\":\"x\",\"version\":" + v + "}").andExpect(status().isForbidden());
        post(member(owner), "/api/v1/documents/" + id + "/withdraw", "{\"reason\":\"x\",\"version\":" + v + "}").andExpect(status().isNoContent());
        post(outsider(stranger, Permission.DOCUMENT_ARCHIVE_ADMIN), "/api/v1/documents/" + id + "/permanent-deletion", "{\"reason\":\"x\"}")
                .andExpect(status().isNotFound());
        // The log lists only the deletions of cases the user may see.
        post(viewer(stranger, Permission.DOCUMENT_ARCHIVE_ADMIN, Permission.DOCUMENT_VIEW, Permission.CASE_VIEW), "/api/v1/documents/" + id + "/permanent-deletion",
                "{\"reason\":\"duplicate\"}").andExpect(status().isNoContent());
        get(outsider(stranger, Permission.DOCUMENT_ARCHIVE_ADMIN), "/api/v1/documents/deletion-log").andExpect(jsonPath("$.length()").value(0));
        get(viewer(stranger, Permission.DOCUMENT_ARCHIVE_ADMIN), "/api/v1/documents/deletion-log").andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void theOldDeleteCallStillAnswersButOnlyWithdraws() throws Exception {
        UUID caseId = newCase();
        UUID id = attach(everything(), caseId, pdfFile("memo.pdf", FIRST));
        delete(everything(), "/api/v1/documents/" + id + "?version=0").andExpect(status().isNoContent());
        assertThat(count("select count(*) from case_document where id = ? and withdrawn_at is not null", id)).isOne();
    }

    @Test
    void withdrawnDocumentsDoNotCountAgainstTheCaseLimit() throws Exception {
        UUID caseId = newCase();
        jdbc.update("update archive_setting set int_value = 1 where code = 'MAX_DOCUMENTS_PER_CASE'");
        UUID first = attach(everything(), caseId, pdfFile("1.pdf", FIRST));
        upload(everything(), "/api/v1/cases/" + caseId + "/documents", pdfFile("2.pdf", ArchiveFiles.pdfWithText("two"))).andExpect(status().isUnprocessableContent());
        post(everything(), "/api/v1/documents/" + first + "/withdraw", withdraw(first)).andExpect(status().isNoContent());
        attach(everything(), caseId, pdfFile("2.pdf", ArchiveFiles.pdfWithText("two")));
        // Restoring the first would exceed the limit now.
        post(everything(), "/api/v1/documents/" + first + "/restore", "{\"version\":" + version(first) + "}").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("DOCUMENT_LIMIT_REACHED"));
    }
}
