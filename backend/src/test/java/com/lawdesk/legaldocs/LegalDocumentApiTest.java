package com.lawdesk.legaldocs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.lawdesk.documents.ArchiveFiles;
import com.lawdesk.documents.ArchiveTestSupport;
import com.lawdesk.security.Permission;

/**
 * LD-055 (requirement 12): memos and correspondence with the cycle DRAFT, IN_REVIEW, APPROVED, SENT, ARCHIVED. Approval needs
 * MEMO_APPROVE and the right to see the case, not to change it; "sent" is a member's record, never a delivery.
 */
class LegalDocumentApiTest extends ArchiveTestSupport {

    private static final String BASE = "/api/v1/legal-documents/";

    private UUID owner;
    private final UUID approver = UUID.randomUUID();

    @org.junit.jupiter.api.BeforeEach
    void members() {
        owner = user("Owner");
    }

    private RequestPostProcessor author() {
        return member(owner);
    }

    private RequestPostProcessor approver() {
        return viewer(approver, Permission.MEMO_APPROVE, Permission.DOCUMENT_VIEW, Permission.CASE_VIEW);
    }

    private String body(String title, long version) {
        return """
                {"title":"%s","categoryId":"%s","documentDate":"2026-05-01","issuer":"Legal Department","addressee":"Court of First Instance",
                 "responsibleMemberId":"%s","description":"reply to the claim","version":%d}""".formatted(title, categoryId("DEFENSE_MEMO"), owner, version);
    }

    private String draft(UUID caseId, String title) throws Exception {
        return id(post(author(), "/api/v1/cases/" + caseId + "/legal-documents", body(title, 0)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT")));
    }

    private void file(String id, byte[] pdf) throws Exception {
        upload(author(), BASE + id + "/file", pdfFile("memo.pdf", pdf)).andExpect(status().isOk());
    }

    private long version(String id) {
        return jdbc.queryForObject("select version from legal_document where id = ?::uuid", Long.class, id);
    }

    private String note(String text, String id) {
        return "{\"note\":\"" + text + "\",\"version\":" + version(id) + "}";
    }

    private String statusOf(String id) {
        return jdbc.queryForObject("select status from legal_document where id = ?::uuid", String.class, id);
    }

    @Test
    void aMemoGoesThroughTheWholeCycleAndEveryStepIsRecorded() throws Exception {
        UUID caseId = newCase(owner);
        String id = draft(caseId, "Defence memo no. 1");

        post(author(), BASE + id + "/submit", note("please review", id)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("LEGAL_DOCUMENT_NEEDS_FILE"));
        file(id, ArchiveFiles.pdfWithText("the memo"));
        get(author(), BASE + id).andExpect(jsonPath("$.documentFileName").value("memo.pdf")).andExpect(jsonPath("$.documentVersionNo").value(1))
                .andExpect(jsonPath("$.categoryLabelEn").value("Defence memo")).andExpect(jsonPath("$.responsibleMemberName").isString());

        post(author(), BASE + id + "/submit", note("please review", id)).andExpect(jsonPath("$.status").value("IN_REVIEW"))
                .andExpect(jsonPath("$.submittedBy").value(owner.toString()));
        post(approver(), BASE + id + "/approve", note("approved as drafted", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED")).andExpect(jsonPath("$.approvedBy").value(approver.toString()))
                .andExpect(jsonPath("$.approvedAt").isString()).andExpect(jsonPath("$.approvalNote").value("approved as drafted"))
                .andExpect(jsonPath("$.sentRecorded").value(false)).andExpect(jsonPath("$.sentViaIntegration").value(false));

        post(author(), BASE + id + "/record-sent", "{\"sentOn\":\"" + LocalDate.now(ZoneId.of("Asia/Kuwait")) + "\",\"method\":\"hand delivery\",\"reference\":\"OUT-77\",\"version\":" + version(id) + "}")
                .andExpect(jsonPath("$.status").value("SENT")).andExpect(jsonPath("$.sentRecorded").value(true))
                .andExpect(jsonPath("$.sentMethod").value("hand delivery")).andExpect(jsonPath("$.sentReference").value("OUT-77"))
                .andExpect(jsonPath("$.sentRecordedBy").value(owner.toString())).andExpect(jsonPath("$.sentViaIntegration").value(false));
        post(author(), BASE + id + "/archive", note("filed", id)).andExpect(jsonPath("$.status").value("ARCHIVED")).andExpect(jsonPath("$.archivedAt").isString());

        get(author(), BASE + id + "/events").andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[*].toStatus").value(Matchers.contains("DRAFT", "IN_REVIEW", "APPROVED", "SENT", "ARCHIVED")))
                .andExpect(jsonPath("$[2].createdBy").value(approver.toString()));
        assertThat(jdbc.queryForList("select type from case_event where case_id = ? and type like 'LEGAL_DOCUMENT_%' order by seq", String.class, caseId))
                .containsExactly("LEGAL_DOCUMENT_ADDED", "LEGAL_DOCUMENT_SUBMITTED", "LEGAL_DOCUMENT_APPROVED", "LEGAL_DOCUMENT_SENT_RECORDED", "LEGAL_DOCUMENT_ARCHIVED");
        assertThat(count("select count(*) from audit_event where action like 'LEGAL_DOCUMENT_%'")).isGreaterThanOrEqualTo(5);
    }

    @Test
    void sendingIsOnlyARecordAndTheDatabaseKeepsItThatWay() throws Exception {
        String id = draft(newCase(owner), "Letter to the ministry");
        file(id, ArchiveFiles.pdfWithText("letter"));
        post(author(), BASE + id + "/submit", note("review", id)).andExpect(status().isOk());
        post(approver(), BASE + id + "/approve", note("ok", id)).andExpect(status().isOk());
        // No integration exists: nothing in the request can set the integration flag, and the table refuses it.
        post(author(), BASE + id + "/record-sent", "{\"sentOn\":\"2026-05-02\",\"method\":\"post\",\"sentViaIntegration\":true,\"version\":" + version(id) + "}")
                .andExpect(jsonPath("$.sentViaIntegration").value(false));
        assertThatThrownBy(() -> jdbc.update("update legal_document set sent_via_integration = true where id = ?::uuid", id))
                .hasMessageContaining("legal_document_no_integration");
    }

    @Test
    void recordingTheSendingNeedsADateInThePastAndAMethod() throws Exception {
        String id = draft(newCase(owner), "Letter");
        file(id, ArchiveFiles.pdfWithText("letter"));
        post(author(), BASE + id + "/submit", note("review", id)).andExpect(status().isOk());
        post(approver(), BASE + id + "/approve", note("ok", id)).andExpect(status().isOk());
        String tomorrow = LocalDate.now(ZoneId.of("Asia/Kuwait")).plusDays(1).toString();
        post(author(), BASE + id + "/record-sent", "{\"sentOn\":\"" + tomorrow + "\",\"method\":\"post\",\"version\":" + version(id) + "}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("sentOn"));
        post(author(), BASE + id + "/record-sent", "{\"method\":\"post\",\"version\":" + version(id) + "}").andExpect(status().isBadRequest());
        post(author(), BASE + id + "/record-sent", "{\"sentOn\":\"2026-05-02\",\"method\":\" \",\"version\":" + version(id) + "}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("method"));
        assertThat(statusOf(id)).isEqualTo("APPROVED");
    }

    @Test
    void approvingNeedsMemoApproveAndOnlyTheRightToSeeTheCase() throws Exception {
        UUID caseId = newCase(owner);
        String id = draft(caseId, "Memo");
        file(id, ArchiveFiles.pdfWithText("memo"));
        post(author(), BASE + id + "/submit", note("review", id)).andExpect(status().isOk());

        // The author holds every document right but not MEMO_APPROVE; the approver holds nothing but the right to approve.
        post(author(), BASE + id + "/approve", note("ok", id)).andExpect(status().isForbidden());
        post(outsider(approver, Permission.MEMO_APPROVE), BASE + id + "/approve", note("ok", id)).andExpect(status().isNotFound());
        post(viewer(approver, Permission.DOCUMENT_VIEW, Permission.CASE_VIEW), BASE + id + "/approve", note("ok", id)).andExpect(status().isForbidden());
        // Approving is not changing: the approver cannot draft, edit or send.
        post(approver(), "/api/v1/cases/" + caseId + "/legal-documents", body("Other", 0)).andExpect(status().isForbidden());
        post(approver(), BASE + id + "/archive", note("x", id)).andExpect(status().isForbidden());
        post(approver(), BASE + id + "/approve", note("ok", id)).andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void onlyADraftIsEditedAndItsFileReplaced() throws Exception {
        String id = draft(newCase(owner), "Memo");
        file(id, ArchiveFiles.pdfWithText("memo"));
        put(author(), BASE + id, body("Memo (revised)", version(id))).andExpect(jsonPath("$.title").value("Memo (revised)"));
        post(author(), BASE + id + "/submit", note("review", id)).andExpect(status().isOk());
        put(author(), BASE + id, body("Changed in review", version(id))).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("LEGAL_DOCUMENT_NOT_DRAFT"));
        upload(author(), BASE + id + "/file", pdfFile("v2.pdf", ArchiveFiles.pdfWithText("new text"))).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("LEGAL_DOCUMENT_NOT_DRAFT"));
    }

    @Test
    void theApproverReturnsAMemoWithAReasonAndTheAuthorRevisesIt() throws Exception {
        String id = draft(newCase(owner), "Memo");
        file(id, ArchiveFiles.pdfWithText("first text"));
        post(author(), BASE + id + "/submit", note("review", id)).andExpect(status().isOk());
        post(approver(), BASE + id + "/return", note("", id)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("note"));
        post(approver(), BASE + id + "/return", note("cite article 12", id)).andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.submittedBy").doesNotExist());
        upload(author(), BASE + id + "/file", pdfFile("v2.pdf", ArchiveFiles.pdfWithText("second text, citing article 12")), "note", "cites article 12")
                .andExpect(jsonPath("$.documentVersionNo").value(2));
        post(author(), BASE + id + "/submit", note("again", id)).andExpect(jsonPath("$.status").value("IN_REVIEW"));
        post(approver(), BASE + id + "/approve", note("ok", id)).andExpect(jsonPath("$.status").value("APPROVED"));
        get(author(), BASE + id + "/events").andExpect(jsonPath("$[2].note").value("cite article 12"));
    }

    @Test
    void anApprovedMemosFileIsLockedUntilTheApproverReopensIt() throws Exception {
        UUID caseId = newCase(owner);
        String id = draft(caseId, "Memo");
        file(id, ArchiveFiles.pdfWithText("approved text"));
        UUID document = jdbc.queryForObject("select document_id from legal_document where id = ?::uuid", UUID.class, id);
        post(author(), BASE + id + "/submit", note("review", id)).andExpect(status().isOk());
        post(approver(), BASE + id + "/approve", note("ok", id)).andExpect(status().isOk());

        upload(author(), "/api/v1/documents/" + document + "/versions", pdfFile("v2.pdf", ArchiveFiles.pdfWithText("changed after approval")))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.rule").value("LEGAL_DOCUMENT_LOCKED"));
        post(author(), "/api/v1/documents/" + document + "/withdraw", "{\"reason\":\"x\",\"version\":" + jdbc.queryForObject("select version from case_document where id = ?", Long.class, document) + "}")
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.rule").value("LEGAL_DOCUMENT_LOCKED"));
        post(author(), BASE + id + "/reopen", note("x", id)).andExpect(status().isForbidden());
        post(approver(), BASE + id + "/reopen", note("", id)).andExpect(status().isBadRequest());
        post(approver(), BASE + id + "/reopen", note("a figure was wrong", id)).andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.approvedBy").doesNotExist());
        upload(author(), "/api/v1/documents/" + document + "/versions", pdfFile("v2.pdf", ArchiveFiles.pdfWithText("corrected"))).andExpect(status().isCreated());
        assertThat(count("select count(*) from legal_document_event where legal_document_id = ?::uuid and to_status = 'APPROVED'", id)).isOne();
    }

    @Test
    void stepsOutOfOrderAreRefused() throws Exception {
        String id = draft(newCase(owner), "Memo");
        post(approver(), BASE + id + "/approve", note("ok", id)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("LEGAL_DOCUMENT_TRANSITION_NOT_ALLOWED"));
        post(author(), BASE + id + "/record-sent", "{\"sentOn\":\"2026-05-02\",\"method\":\"post\",\"version\":" + version(id) + "}")
                .andExpect(status().isUnprocessableContent());
        post(author(), BASE + id + "/archive", note("x", id)).andExpect(status().isUnprocessableContent());
        post(approver(), BASE + id + "/return", note("x", id)).andExpect(status().isUnprocessableContent());
        assertThat(statusOf(id)).isEqualTo("DRAFT");
    }

    @Test
    void aStaleVersionIsAConflict() throws Exception {
        String id = draft(newCase(owner), "Memo");
        file(id, ArchiveFiles.pdfWithText("memo"));
        post(author(), BASE + id + "/submit", "{\"note\":\"x\",\"version\":0}").andExpect(status().isConflict());
        put(author(), BASE + id, body("Memo", 0)).andExpect(status().isConflict());
        put(author(), BASE + id, body("Memo", version(id))).andExpect(status().isOk());
    }

    @Test
    void referencesAreChecked() throws Exception {
        UUID caseId = newCase(owner);
        UUID otherCase = newCase();
        UUID foreignDoc = attach(everything(), otherCase, pdfFile("other.pdf", ArchiveFiles.pdfWithText("other")));
        String path = "/api/v1/cases/" + caseId + "/legal-documents";
        post(author(), path, "{\"title\":\"x\",\"categoryId\":\"" + UUID.randomUUID() + "\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("categoryId"));
        post(author(), path, "{\"title\":\"x\",\"documentId\":\"" + foreignDoc + "\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("documentId"));
        post(author(), path, "{\"title\":\"x\",\"responsibleMemberId\":\"" + UUID.randomUUID() + "\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("responsibleMemberId"));
        post(author(), path, "{\"title\":\" \"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("title"));
        post(author(), path, "{\"title\":\"" + "t".repeat(301) + "\"}").andExpect(status().isBadRequest());
        // A category from another managed list is not a document category.
        UUID judgmentType = jdbc.queryForObject("select id from lookup_value where list_code = 'JUDGMENT_TYPE' limit 1", UUID.class);
        post(author(), path, "{\"title\":\"x\",\"categoryId\":\"" + judgmentType + "\"}").andExpect(status().isBadRequest());
        // An existing archived document of the same case may be registered as the electronic copy.
        UUID own = attach(author(), caseId, pdfFile("own.pdf", ArchiveFiles.pdfWithText("own")));
        post(author(), path, "{\"title\":\"with copy\",\"documentId\":\"" + own + "\"}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentId").value(own.toString()));
    }

    @Test
    void theRegisterListsOnlyMemosOfCasesTheUserMaySee() throws Exception {
        UUID stranger = user("Stranger");
        UUID visible = newCase(stranger);
        UUID hidden = newCase(owner);
        post(member(stranger), "/api/v1/cases/" + visible + "/legal-documents", body("Visible memo", 0)).andExpect(status().isCreated());
        String hiddenId = draft(hidden, "Hidden memo");

        get(outsider(stranger), "/api/v1/legal-documents").andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].title").value("Visible memo"));
        get(outsider(stranger), "/api/v1/legal-documents?caseId=" + hidden).andExpect(jsonPath("$.totalItems").value(0));
        get(outsider(stranger), BASE + hiddenId).andExpect(status().isNotFound());
        get(outsider(stranger), BASE + hiddenId + "/events").andExpect(status().isNotFound());
        get(outsider(stranger), "/api/v1/cases/" + hidden + "/legal-documents").andExpect(status().isNotFound());
        put(outsider(stranger), BASE + hiddenId, body("x", 0)).andExpect(status().isNotFound());
        get(viewer(stranger), "/api/v1/legal-documents").andExpect(jsonPath("$.totalItems").value(2));
        post(viewer(stranger), "/api/v1/cases/" + hidden + "/legal-documents", body("x", 0)).andExpect(status().isForbidden());
        put(viewer(stranger), BASE + hiddenId, body("x", 0)).andExpect(status().isForbidden());
    }

    @Test
    void theRegisterFiltersByStatusAndCategory() throws Exception {
        UUID caseId = newCase(owner);
        String one = draft(caseId, "One");
        draft(caseId, "Two");
        file(one, ArchiveFiles.pdfWithText("one"));
        post(author(), BASE + one + "/submit", note("review", one)).andExpect(status().isOk());
        get(author(), "/api/v1/legal-documents?status=IN_REVIEW").andExpect(jsonPath("$.totalItems").value(1)).andExpect(jsonPath("$.items[0].title").value("One"));
        get(author(), "/api/v1/legal-documents?status=DRAFT").andExpect(jsonPath("$.totalItems").value(1));
        get(author(), "/api/v1/legal-documents?categoryId=" + categoryId("DEFENSE_MEMO")).andExpect(jsonPath("$.totalItems").value(2));
        get(author(), "/api/v1/legal-documents?categoryId=" + categoryId("EXPERT_REPORT")).andExpect(jsonPath("$.totalItems").value(0));
        get(author(), "/api/v1/legal-documents?status=NONSENSE").andExpect(status().isBadRequest());
    }

    @Test
    void anArchivedCaseIsReadOnlyAndPermissionsAreRequired() throws Exception {
        UUID caseId = newCase(owner);
        String id = draft(caseId, "Memo");
        get(with(Permission.CASE_VIEW), BASE + id).andExpect(status().isForbidden());
        post(with(Permission.DOCUMENT_VIEW, Permission.CASE_VIEW), "/api/v1/cases/" + caseId + "/legal-documents", body("x", 0)).andExpect(status().isForbidden());
        jdbc.update("update legal_case set status = 'ARCHIVED' where id = ?", caseId);
        put(author(), BASE + id, body("late", version(id))).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.rule").value("CASE_ARCHIVED"));
        post(author(), "/api/v1/cases/" + caseId + "/legal-documents", body("x", 0)).andExpect(status().isUnprocessableContent());
    }
}
