package com.lawdesk.documents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.lawdesk.security.Permission;

/** LD-055 (requirement 13, last item): a document is linked to the claim, defence, hearing, judgment or task it concerns. */
class DocumentLinkApiTest extends ArchiveTestSupport {

    private String link(String type, UUID target) {
        return "{\"targetType\":\"" + type + "\",\"targetId\":\"" + target + "\",\"note\":\"filed with it\"}";
    }

    private UUID document(UUID caseId) throws Exception {
        return attach(everything(), caseId, pdfFile("d.pdf", ArchiveFiles.pdfWithText("linked document")));
    }

    @Test
    void linksADocumentToEachKindOfRecordOfItsCase() throws Exception {
        UUID caseId = newCase();
        UUID proceeding = firstInstance(caseId);
        UUID doc = document(caseId);
        UUID claim = claim(caseId, 1);
        UUID defense = defense(caseId, 1);
        UUID hearing = hearing(caseId, proceeding);
        UUID judgment = judgment(proceeding, "2026-04-01");
        UUID task = task(caseId, "Collect the exhibits");

        for (Object[] target : new Object[][] {{"CLAIM", claim, "#1 claim text"}, {"DEFENSE", defense, "defence title"},
            {"HEARING", hearing, "hearing 2026-03-01"}, {"JUDGMENT", judgment, "judgment 2026-04-01"}, {"TASK", task, "Collect the exhibits"}}) {
            post(everything(), "/api/v1/documents/" + doc + "/links", link((String) target[0], (UUID) target[1])).andExpect(status().isCreated())
                    .andExpect(jsonPath("$.targetType").value(target[0])).andExpect(jsonPath("$.targetId").value(target[1].toString()))
                    .andExpect(jsonPath("$.targetLabel").value(target[2])).andExpect(jsonPath("$.note").value("filed with it"));
        }
        get(everything(), "/api/v1/documents/" + doc + "/links").andExpect(jsonPath("$.length()").value(5));
        get(everything(), "/api/v1/cases/" + caseId + "/document-links").andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].documentTitle").value("d.pdf"));
        assertThat(count("select count(*) from case_event where case_id = ? and type = 'DOCUMENT_LINKED'", caseId)).isEqualTo(5);
    }

    @Test
    void aRecordOfAnotherCaseCannotBeLinked() throws Exception {
        UUID caseId = newCase();
        UUID other = newCase();
        UUID doc = document(caseId);
        post(everything(), "/api/v1/documents/" + doc + "/links", link("CLAIM", claim(other, 1))).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("LINK_NOT_IN_CASE"));
        post(everything(), "/api/v1/documents/" + doc + "/links", link("TASK", task(other, "elsewhere"))).andExpect(status().isUnprocessableContent());
        post(everything(), "/api/v1/documents/" + doc + "/links", link("CLAIM", UUID.randomUUID())).andExpect(status().isNotFound());
        assertThat(count("select count(*) from document_link")).isZero();
    }

    @Test
    void theDatabaseItselfRefusesALinkAcrossCases() throws Exception {
        UUID caseId = newCase();
        UUID doc = document(caseId);
        UUID foreignClaim = claim(newCase(), 1);
        assertThatThrownBy(() -> jdbc.update("insert into document_link (document_id, claim_id, created_by) values (?, ?, ?)", doc, foreignClaim, USER))
                .hasMessageContaining("different cases");
        UUID claim = claim(caseId, 2);
        UUID defense = defense(caseId, 2);
        assertThatThrownBy(() -> jdbc.update("insert into document_link (document_id, claim_id, defense_id, created_by) values (?, ?, ?, ?)", doc, claim, defense, USER))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void theTypeTheTargetAndTheNoteAreChecked() throws Exception {
        UUID doc = document(newCase());
        post(everything(), "/api/v1/documents/" + doc + "/links", "{\"targetType\":\"PARTY\",\"targetId\":\"" + UUID.randomUUID() + "\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("targetType"));
        post(everything(), "/api/v1/documents/" + doc + "/links", "{\"targetType\":\"CLAIM\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("targetId"));
        post(everything(), "/api/v1/documents/" + doc + "/links", "{\"targetType\":\"claim\",\"targetId\":\"" + UUID.randomUUID() + "\",\"note\":\"" + "n".repeat(501) + "\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("note"));
    }

    @Test
    void theSameLinkIsStoredOnce() throws Exception {
        UUID caseId = newCase();
        UUID doc = document(caseId);
        UUID claim = claim(caseId, 1);
        post(everything(), "/api/v1/documents/" + doc + "/links", link("CLAIM", claim)).andExpect(status().isCreated());
        post(everything(), "/api/v1/documents/" + doc + "/links", link("CLAIM", claim)).andExpect(status().isConflict());
    }

    @Test
    void aLinkCanBeRemovedAndTheRemovalIsRecorded() throws Exception {
        UUID caseId = newCase();
        UUID doc = document(caseId);
        int audited = count("select count(*) from audit_event where action = 'DOCUMENT_UNLINK'");
        String linkId = id(post(everything(), "/api/v1/documents/" + doc + "/links", link("CLAIM", claim(caseId, 1))).andExpect(status().isCreated()));
        delete(everything(), "/api/v1/document-links/" + linkId).andExpect(status().isNoContent());
        delete(everything(), "/api/v1/document-links/" + linkId).andExpect(status().isNotFound());
        assertThat(count("select count(*) from case_event where case_id = ? and type = 'DOCUMENT_UNLINKED'", caseId)).isOne();
        assertThat(count("select count(*) from audit_event where action = 'DOCUMENT_UNLINK'")).isEqualTo(audited + 1);
    }

    @Test
    void linksFollowTheCaseAccessRule() throws Exception {
        UUID owner = user("Owner");
        UUID stranger = UUID.randomUUID();
        UUID caseId = newCase(owner);
        UUID doc = attach(member(owner), caseId, pdfFile("d.pdf", ArchiveFiles.pdfWithText("linked")));
        UUID claim = claim(caseId, 1);
        String linkId = id(post(member(owner), "/api/v1/documents/" + doc + "/links", link("CLAIM", claim)).andExpect(status().isCreated()));

        get(outsider(stranger), "/api/v1/documents/" + doc + "/links").andExpect(status().isNotFound());
        get(outsider(stranger), "/api/v1/cases/" + caseId + "/document-links").andExpect(status().isNotFound());
        post(outsider(stranger), "/api/v1/documents/" + doc + "/links", link("CLAIM", claim)).andExpect(status().isNotFound());
        delete(outsider(stranger), "/api/v1/document-links/" + linkId).andExpect(status().isNotFound());
        get(viewer(stranger), "/api/v1/documents/" + doc + "/links").andExpect(jsonPath("$.length()").value(1));
        post(viewer(stranger), "/api/v1/documents/" + doc + "/links", link("CLAIM", claim(caseId, 2))).andExpect(status().isForbidden());
        delete(viewer(stranger), "/api/v1/document-links/" + linkId).andExpect(status().isForbidden());
        post(with(Permission.DOCUMENT_VIEW, Permission.CASE_VIEW), "/api/v1/documents/" + doc + "/links", link("CLAIM", claim)).andExpect(status().isForbidden());
    }
}
