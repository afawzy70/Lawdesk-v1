package com.lawdesk.settlements;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.lawdesk.security.Permission;
import com.lawdesk.wp9.Wp9TestSupport;

/**
 * LD-057, requirements section 23 (LD-DOC-23-001..010): settlements, waivers and conciliations of a case. The amount is the
 * settlement's own (D5); approval needs its own permission; recording or executing a settlement never closes the case or a
 * linked case (workflow 9); every change is in the history; the case access rules apply to every route (D3).
 */
class SettlementApiTest extends Wp9TestSupport {

    private UUID specialist;
    private UUID approver;
    private UUID outsiderUser;
    private UUID caseId;
    private UUID partyId;
    private RequestPostProcessor proposer;
    private RequestPostProcessor manager;

    @BeforeEach
    void aCaseWithItsSpecialistAndAnApprover() {
        specialist = user("Specialist");
        approver = user("Approver");
        outsiderUser = user("Outsider");
        caseId = legalCase(specialist, null);
        partyId = party("شركة العرض", "ORGANIZATION", null, false, null);
        onCase(caseId, partyId, "OPPONENT");
        proposer = staff(specialist, Permission.CASE_VIEW, Permission.SETTLEMENT_MANAGE);
        manager = staff(approver, Permission.CASE_VIEW, Permission.SETTLEMENT_MANAGE, Permission.SETTLEMENT_APPROVE);
    }

    private String body() {
        return json("kind", "SETTLEMENT", "offeringPartyId", partyId, "settlementAmount", "125000.500",
                "legalGrounds", "تفادي تكاليف التقاضي", "financialImpactNote", "يخفض الالتزام المتوقع",
                "legalOpinion", "ينصح بالقبول", "approvingAuthority", "مدير الإدارة القانونية");
    }

    private UUID propose() throws Exception {
        return idOf(mvc.perform(postJson(proposer, "/api/v1/cases/" + caseId + "/settlements", body()))
                .andExpect(status().isCreated()).andReturn());
    }

    private void move(RequestPostProcessor who, UUID id, String to, String... more) throws Exception {
        String[] fields = new String[more.length + 4];
        fields[0] = "status";
        fields[1] = to;
        fields[2] = "version";
        fields[3] = String.valueOf(count("select version from settlement where id = ?::uuid", id.toString()));
        System.arraycopy(more, 0, fields, 4, more.length);
        Object[] args = fields;
        mvc.perform(postJson(who, "/api/v1/settlements/" + id + "/status", json(args))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(to));
    }

    // ---- LD-DOC-23-001..009: what a settlement records ---------------------------------------------------------

    @Test
    void aSettlementRecordsTheOfferingPartyTheValueTheGroundsTheImpactTheOpinionAndTheApprovingAuthority() throws Exception {
        MvcResult created = mvc.perform(postJson(proposer, "/api/v1/cases/" + caseId + "/settlements", body()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PROPOSED")).andExpect(jsonPath("$.kind").value("SETTLEMENT"))
                .andExpect(jsonPath("$.offeringPartyId").value(partyId.toString()))
                .andExpect(jsonPath("$.offeringPartyName").value("شركة العرض"))
                .andExpect(jsonPath("$.settlementAmount").value("125000.500"))
                .andExpect(jsonPath("$.legalGrounds").value("تفادي تكاليف التقاضي"))
                .andExpect(jsonPath("$.financialImpactNote").value("يخفض الالتزام المتوقع"))
                .andExpect(jsonPath("$.legalOpinion").value("ينصح بالقبول"))
                .andExpect(jsonPath("$.approvingAuthority").value("مدير الإدارة القانونية"))
                .andExpect(jsonPath("$.completedOn").doesNotExist()).andExpect(jsonPath("$.documents.length()").value(0))
                .andExpect(jsonPath("$.createdBy").value(specialist.toString())).andReturn();
        mvc.perform(getJson(proposer, "/api/v1/cases/" + caseId + "/settlements")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        assertThat(count("select count(*) from case_event where case_id = ? and type = 'SETTLEMENT_RECORDED'", caseId))
                .isEqualTo(1);
        assertThat(count("select count(*) from audit_event where action = 'SETTLEMENT_CREATE' and outcome = 'SUCCESS'"
                + " and target_id = ?", idOf(created).toString())).isEqualTo(1);
    }

    @Test
    void theOfferingPartyMayBeFreeTextAndTheKindComesFromTheManagedList() throws Exception {
        mvc.perform(postJson(proposer, "/api/v1/cases/" + caseId + "/settlements",
                        json("kind", "WAIVER", "offeringPartyText", "الجهة المدعية")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.offeringPartyText").value("الجهة المدعية"))
                .andExpect(jsonPath("$.offeringPartyId").doesNotExist()).andExpect(jsonPath("$.settlementAmount").doesNotExist());
        mvc.perform(postJson(proposer, "/api/v1/cases/" + caseId + "/settlements", json("kind", "NO_SUCH_KIND")))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.rule").value("SETTLEMENT_KIND_UNKNOWN"));
        mvc.perform(postJson(proposer, "/api/v1/cases/" + caseId + "/settlements", json("kind", " ")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void theOfferingPartyMustBeAPartyOfThisCase() throws Exception {
        UUID stranger = party("طرف غريب", "PERSON", null, false, null);
        mvc.perform(postJson(proposer, "/api/v1/cases/" + caseId + "/settlements",
                        json("kind", "SETTLEMENT", "offeringPartyId", stranger)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.rule").value("SETTLEMENT_PARTY_NOT_IN_CASE"));
    }

    // ---- D5: the settlement amount is its own ---------------------------------------------------------------------

    @Test
    void theSettlementAmountIsKeptApartFromEveryOtherAmountOfTheCase() throws Exception {
        jdbc.update("update legal_case set claimed_amount = 900000.000, financial_impact = 'YES', financial_amount = 700000.000"
                + " where id = ?", caseId);
        UUID id = propose();
        move(manager, id, "UNDER_REVIEW");
        move(manager, id, "APPROVED", "reason", "موافق");
        assertThat(jdbc.queryForObject("select claimed_amount::text from legal_case where id = ?", String.class, caseId))
                .isEqualTo("900000.000");
        assertThat(jdbc.queryForObject("select financial_amount::text from legal_case where id = ?", String.class, caseId))
                .isEqualTo("700000.000");
        assertThat(jdbc.queryForObject("select settlement_amount::text from settlement where id = ?", String.class, id))
                .isEqualTo("125000.500");
        // The response carries one amount, the settlement's own.
        MvcResult shown = mvc.perform(getJson(proposer, "/api/v1/settlements/" + id)).andExpect(status().isOk()).andReturn();
        assertThat(body(shown)).contains("settlementAmount").doesNotContain("claimedAmount").doesNotContain("financialAmount")
                .doesNotContain("judgedAmount").doesNotContain("potentialLoss");
    }

    @Test
    void theAmountIsDinarsWithAtMostThreeDecimalsAndNeverNegative() throws Exception {
        String path = "/api/v1/cases/" + caseId + "/settlements";
        mvc.perform(postJson(proposer, path, json("kind", "SETTLEMENT", "settlementAmount", "-1.000")))
                .andExpect(status().isBadRequest());
        mvc.perform(postJson(proposer, path, json("kind", "SETTLEMENT", "settlementAmount", "1.2345")))
                .andExpect(status().isBadRequest());
        mvc.perform(postJson(proposer, path, json("kind", "SETTLEMENT", "settlementAmount", "abc")))
                .andExpect(status().isBadRequest());
        mvc.perform(postJson(proposer, path, json("kind", "SETTLEMENT", "settlementAmount", "0.001")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.settlementAmount").value("0.001"));
    }

    // ---- LD-DOC-23-006..008: approval, decision, completion --------------------------------------------------------

    @Test
    void approvalNeedsItsOwnPermissionAndIsRecordedWithWhoDecidedAndWhy() throws Exception {
        UUID id = propose();
        move(proposer, id, "UNDER_REVIEW");
        mvc.perform(postJson(proposer, "/api/v1/settlements/" + id + "/status",
                json("status", "APPROVED", "reason", "موافق", "version", 1))).andExpect(status().isForbidden());
        mvc.perform(postJson(manager, "/api/v1/settlements/" + id + "/status",
                        json("status", "APPROVED", "reason", "موافق على الشروط", "version", 1)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.decidedBy").value(approver.toString())).andExpect(jsonPath("$.decidedAt").isString())
                .andExpect(jsonPath("$.decisionReason").value("موافق على الشروط"));
    }

    @Test
    void aRejectionNeedsAReasonAndMayBeRevisedAndProposedAgain() throws Exception {
        UUID id = propose();
        mvc.perform(postJson(manager, "/api/v1/settlements/" + id + "/status", json("status", "REJECTED", "version", 0)))
                .andExpect(status().isBadRequest());
        move(manager, id, "REJECTED", "reason", "القيمة مرتفعة");
        mvc.perform(getJson(proposer, "/api/v1/settlements/" + id)).andExpect(jsonPath("$.decisionReason").value("القيمة مرتفعة"));
        move(proposer, id, "PROPOSED");
    }

    @Test
    void executionNeedsApprovalFirstAndTheCompletionDate() throws Exception {
        UUID id = propose();
        mvc.perform(postJson(proposer, "/api/v1/settlements/" + id + "/status",
                        json("status", "EXECUTED", "completedOn", today().toString(), "version", 0)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.rule").value("SETTLEMENT_TRANSITION"));
        move(manager, id, "APPROVED", "reason", "موافق");
        mvc.perform(postJson(proposer, "/api/v1/settlements/" + id + "/status",
                json("status", "EXECUTED", "version", 1))).andExpect(status().isBadRequest());
        move(proposer, id, "EXECUTED", "completedOn", today().toString());
        mvc.perform(getJson(proposer, "/api/v1/settlements/" + id)).andExpect(jsonPath("$.completedOn").value(today().toString()));
    }

    @Test
    void aFinishedSettlementCannotMoveAgainAndAWithdrawnOneIsOver() throws Exception {
        UUID id = propose();
        move(proposer, id, "WITHDRAWN");
        mvc.perform(postJson(manager, "/api/v1/settlements/" + id + "/status",
                        json("status", "APPROVED", "reason", "x", "version", 1)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.rule").value("SETTLEMENT_TRANSITION"));
        mvc.perform(putJson(proposer, "/api/v1/settlements/" + id, json("kind", "SETTLEMENT", "version", 1)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.rule").value("SETTLEMENT_NOT_EDITABLE"));
    }

    @Test
    void theTermsCannotBeEditedOnceApprovedBecauseTheApprovalWasForThoseTerms() throws Exception {
        UUID id = propose();
        mvc.perform(putJson(proposer, "/api/v1/settlements/" + id,
                        json("kind", "SETTLEMENT", "settlementAmount", "100000.000", "legalGrounds", "أسباب معدلة", "version", 0)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.settlementAmount").value("100000.000"))
                .andExpect(jsonPath("$.version").value(1));
        move(manager, id, "APPROVED", "reason", "موافق");
        mvc.perform(putJson(proposer, "/api/v1/settlements/" + id,
                        json("kind", "SETTLEMENT", "settlementAmount", "1.000", "version", 2)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.rule").value("SETTLEMENT_NOT_EDITABLE"));
        // A stale version conflicts.
        UUID other = propose();
        mvc.perform(putJson(proposer, "/api/v1/settlements/" + other, json("kind", "SETTLEMENT", "version", 5)))
                .andExpect(status().isConflict());
    }

    // ---- LD-DOC-23-010: the case is not finished by a settlement --------------------------------------------------

    @Test
    void recordingApprovingOrExecutingNeverClosesTheCaseOrALinkedCase() throws Exception {
        UUID linked = legalCase(specialist, null);
        jdbc.update("""
                insert into case_link (case_id, other_case_id, kind, reason, created_by, updated_by)
                values (?, ?, 'SUBJECT_LINK', 'مرتبطة', gen_random_uuid(), gen_random_uuid())""", caseId, linked);
        UUID id = propose();
        move(manager, id, "UNDER_REVIEW");
        move(manager, id, "APPROVED", "reason", "موافق");
        move(manager, id, "EXECUTED", "completedOn", today().toString());
        assertThat(jdbc.queryForObject("select status from legal_case where id = ?", String.class, caseId)).isEqualTo("OPEN");
        assertThat(jdbc.queryForObject("select status from legal_case where id = ?", String.class, linked)).isEqualTo("OPEN");
        assertThat(count("select count(*) from case_link where status = 'ENDED'")).isZero();
        // A waiver or a conciliation behaves the same.
        mvc.perform(postJson(proposer, "/api/v1/cases/" + caseId + "/settlements", json("kind", "WAIVER")))
                .andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("select status from legal_case where id = ?", String.class, caseId)).isEqualTo("OPEN");
    }

    // ---- full history -------------------------------------------------------------------------------------------

    @Test
    void everyChangeIsInTheHistoryWithWhoWhenAndWhatChanged() throws Exception {
        UUID id = propose();
        mvc.perform(putJson(proposer, "/api/v1/settlements/" + id,
                        json("kind", "SETTLEMENT", "settlementAmount", "99000.000", "legalGrounds", "تفادي تكاليف التقاضي",
                                "version", 0))).andExpect(status().isOk());
        move(proposer, id, "UNDER_REVIEW");
        move(manager, id, "APPROVED", "reason", "موافق");
        mvc.perform(getJson(proposer, "/api/v1/settlements/" + id + "/history")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4)).andExpect(jsonPath("$[0].action").value("CREATED"))
                .andExpect(jsonPath("$[0].actorId").value(specialist.toString()))
                .andExpect(jsonPath("$[1].action").value("UPDATED"))
                .andExpect(jsonPath("$[1].changes.settlementAmount.from").value("125000.500"))
                .andExpect(jsonPath("$[1].changes.settlementAmount.to").value("99000.000"))
                .andExpect(jsonPath("$[2].action").value("STATUS_CHANGED")).andExpect(jsonPath("$[2].fromStatus").value("PROPOSED"))
                .andExpect(jsonPath("$[2].toStatus").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$[3].toStatus").value("APPROVED")).andExpect(jsonPath("$[3].actorId").value(approver.toString()))
                .andExpect(jsonPath("$[3].note").value("موافق")).andExpect(jsonPath("$[3].occurredAt").isString());
        assertThat(count("select count(*) from audit_event where action = 'SETTLEMENT_STATUS' and outcome = 'SUCCESS'"
                + " and target_id = ?", id.toString())).isEqualTo(2);
    }

    // ---- LD-DOC-23-009: supporting documents through the document module -----------------------------------------

    @Test
    void supportingDocumentsAreDocumentsOfTheSameCaseFile() throws Exception {
        UUID id = propose();
        UUID mine = document(caseId, "عرض التسوية", "offer.pdf");
        UUID foreign = document(legalCase(specialist, null), "مستند قضية أخرى", "other.pdf");
        mvc.perform(putJson(proposer, "/api/v1/settlements/" + id + "/documents/" + mine, "{}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.documents.length()").value(1)).andExpect(jsonPath("$.documents[0].id").value(mine.toString()))
                .andExpect(jsonPath("$.documents[0].title").value("عرض التسوية"));
        mvc.perform(putJson(proposer, "/api/v1/settlements/" + id + "/documents/" + foreign, "{}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.rule").value("SETTLEMENT_DOCUMENT_NOT_IN_CASE"));
        mvc.perform(getJson(proposer, "/api/v1/settlements/" + id)).andExpect(jsonPath("$.documents.length()").value(1));
        mvc.perform(getJson(proposer, "/api/v1/settlements/" + id + "/history"))
                .andExpect(jsonPath("$[?(@.action == 'DOCUMENT_LINKED')]", org.hamcrest.Matchers.hasSize(1)));
        mvc.perform(delete(proposer, "/api/v1/settlements/" + id + "/documents/" + mine)).andExpect(status().isOk())
                .andExpect(jsonPath("$.documents.length()").value(0));
        // The document itself stays in the case file.
        assertThat(count("select count(*) from case_document where id = ?::uuid", mine.toString())).isEqualTo(1);
    }

    // ---- D3: the access rules apply to every route ---------------------------------------------------------------

    @Test
    void aUserWhoCannotSeeTheCaseGets404OnEveryRoute() throws Exception {
        UUID id = propose();
        UUID doc = document(caseId, "مستند", "d.pdf");
        RequestPostProcessor nobody = outsider(outsiderUser, Permission.CASE_VIEW, Permission.SETTLEMENT_MANAGE,
                Permission.SETTLEMENT_APPROVE);
        mvc.perform(getJson(nobody, "/api/v1/cases/" + caseId + "/settlements")).andExpect(status().isNotFound());
        mvc.perform(postJson(nobody, "/api/v1/cases/" + caseId + "/settlements", body())).andExpect(status().isNotFound());
        mvc.perform(getJson(nobody, "/api/v1/settlements/" + id)).andExpect(status().isNotFound());
        mvc.perform(putJson(nobody, "/api/v1/settlements/" + id, json("kind", "SETTLEMENT", "version", 0)))
                .andExpect(status().isNotFound());
        mvc.perform(postJson(nobody, "/api/v1/settlements/" + id + "/status", json("status", "APPROVED", "reason", "x", "version", 0)))
                .andExpect(status().isNotFound());
        mvc.perform(getJson(nobody, "/api/v1/settlements/" + id + "/history")).andExpect(status().isNotFound());
        mvc.perform(putJson(nobody, "/api/v1/settlements/" + id + "/documents/" + doc, "{}")).andExpect(status().isNotFound());
        mvc.perform(delete(nobody, "/api/v1/settlements/" + id + "/documents/" + doc)).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select status from settlement where id = ?", String.class, id)).isEqualTo("PROPOSED");
    }

    @Test
    void aUserWhoSeesTheCaseButMayNotChangeItCanReadAndIsRefusedEveryWrite() throws Exception {
        UUID id = propose();
        UUID doc = document(caseId, "مستند", "d.pdf");
        RequestPostProcessor reader = viewer(outsiderUser, Permission.CASE_VIEW, Permission.SETTLEMENT_MANAGE,
                Permission.SETTLEMENT_APPROVE);
        mvc.perform(getJson(reader, "/api/v1/cases/" + caseId + "/settlements")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(getJson(reader, "/api/v1/settlements/" + id + "/history")).andExpect(status().isOk());
        mvc.perform(postJson(reader, "/api/v1/cases/" + caseId + "/settlements", body())).andExpect(status().isForbidden());
        mvc.perform(putJson(reader, "/api/v1/settlements/" + id, json("kind", "SETTLEMENT", "version", 0)))
                .andExpect(status().isForbidden());
        mvc.perform(postJson(reader, "/api/v1/settlements/" + id + "/status", json("status", "APPROVED", "reason", "x", "version", 0)))
                .andExpect(status().isForbidden());
        mvc.perform(putJson(reader, "/api/v1/settlements/" + id + "/documents/" + doc, "{}")).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("select status from settlement where id = ?", String.class, id)).isEqualTo("PROPOSED");
    }

    @Test
    void thePermissionsGateReadingAndWriting() throws Exception {
        UUID id = propose();
        mvc.perform(getJson(staff(specialist), "/api/v1/cases/" + caseId + "/settlements")).andExpect(status().isForbidden());
        RequestPostProcessor readOnly = staff(specialist, Permission.CASE_VIEW);
        mvc.perform(getJson(readOnly, "/api/v1/settlements/" + id)).andExpect(status().isOk());
        mvc.perform(postJson(readOnly, "/api/v1/cases/" + caseId + "/settlements", body())).andExpect(status().isForbidden());
        mvc.perform(postJson(readOnly, "/api/v1/settlements/" + id + "/status", json("status", "WITHDRAWN", "version", 0)))
                .andExpect(status().isForbidden());
    }

    @Test
    void anArchivedCaseIsReadOnly() throws Exception {
        UUID id = propose();
        jdbc.update("update legal_case set status = 'ARCHIVED' where id = ?", caseId);
        mvc.perform(postJson(proposer, "/api/v1/cases/" + caseId + "/settlements", body()))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.rule").value("CASE_ARCHIVED"));
        mvc.perform(postJson(proposer, "/api/v1/settlements/" + id + "/status", json("status", "WITHDRAWN", "version", 0)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.rule").value("CASE_ARCHIVED"));
        mvc.perform(getJson(proposer, "/api/v1/settlements/" + id)).andExpect(status().isOk());
    }
}
