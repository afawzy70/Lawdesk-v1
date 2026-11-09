package com.lawdesk.templates;

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
 * LD-057, requirements section 29 (LD-DOC-29-001..010): the library of ready legal templates, and the draft a member
 * generates from one for a case. A draft fills the placeholders from the case file, is never final, says which fields are
 * missing and invents nothing; template versions are all kept and go DRAFT, REVIEWED, APPROVED (D10).
 */
class TemplateApiTest extends Wp9TestSupport {

    private static final String BODY = "مذكرة بدفاع في القضية {{case.referenceNo}} المقامة من {{parties.opponents}} ضد {{parties.clients}}"
            + " أمام {{court.name}} بجلسة {{hearing.next.date}}، دائرة {{case.chamber}}.";

    private UUID head;
    private UUID readerUser;
    private UUID caseId;
    private RequestPostProcessor manager;

    @BeforeEach
    void aCaseAndATemplateManager() {
        head = user("Head");
        readerUser = user("Reader");
        caseId = legalCase(head, null);
        manager = staff(head, Permission.CASE_VIEW, Permission.TEMPLATE_MANAGE);
        jdbc.update("update legal_case set title = 'نزاع على عقد التوريد', chamber = null where id = ?", caseId);
    }

    private UUID template(String kind) throws Exception {
        return idOf(mvc.perform(postJson(manager, "/api/v1/templates",
                json("kind", kind, "title", "مذكرة دفاع نموذجية", "description", "للقضايا الإدارية", "body", BODY)))
                .andExpect(status().isCreated()).andReturn());
    }

    private UUID versionOf(UUID template, int no) {
        return jdbc.queryForObject("select id from form_template_version where template_id = ? and version_no = ?", UUID.class,
                template, no);
    }

    private void approve(UUID versionId) throws Exception {
        long v = jdbc.queryForObject("select version from form_template_version where id = ?", Long.class, versionId);
        mvc.perform(postJson(manager, "/api/v1/template-versions/" + versionId + "/review", json("version", v)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REVIEWED"));
        mvc.perform(postJson(manager, "/api/v1/template-versions/" + versionId + "/approve", json("version", v + 1)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.approvedBy").value(head.toString())).andExpect(jsonPath("$.reviewedBy").value(head.toString()));
    }

    // ---- the library ------------------------------------------------------------------------------------------

    @Test
    void aTemplateIsCreatedWithItsFirstVersionAsADraftOfAKindFromTheManagedList() throws Exception {
        mvc.perform(postJson(manager, "/api/v1/templates",
                        json("kind", "DEFENSE_MEMO", "title", "مذكرة دفاع نموذجية", "body", BODY)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.kind").value("DEFENSE_MEMO"))
                .andExpect(jsonPath("$.origin").value("PROPOSED")).andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.versions.length()").value(1)).andExpect(jsonPath("$.versions[0].versionNo").value(1))
                .andExpect(jsonPath("$.versions[0].status").value("DRAFT"))
                .andExpect(jsonPath("$.versions[0].body").value(BODY))
                .andExpect(jsonPath("$.versions[0].placeholders.length()").value(6))
                .andExpect(jsonPath("$.versions[0].placeholders[0].key").value("case.referenceNo"))
                .andExpect(jsonPath("$.versions[0].placeholders[0].known").value(true));
        mvc.perform(postJson(manager, "/api/v1/templates", json("kind", "NOPE", "title", "t", "body", "x")))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.rule").value("TEMPLATE_KIND_UNKNOWN"));
        mvc.perform(postJson(manager, "/api/v1/templates", json("kind", "DEFENSE_MEMO", "title", " ")))
                .andExpect(status().isBadRequest());
        mvc.perform(postJson(manager, "/api/v1/templates", json("kind", "DEFENSE_MEMO", "title", "بلا نص")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.versions.length()").value(0));
    }

    @Test
    void noTemplateIsSeeded() throws Exception {
        assertThat(count("select count(*) from form_template")).isZero();
        mvc.perform(getJson(manager, "/api/v1/templates")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void versionsAreAllKeptAndNumberedAndAReviewedVersionIsNeverEdited() throws Exception {
        UUID id = template("DEFENSE_MEMO");
        UUID v1 = versionOf(id, 1);
        mvc.perform(putJson(manager, "/api/v1/template-versions/" + v1, json("body", BODY + " نص إضافي", "version", 0)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.body").value(BODY + " نص إضافي"));
        approve(v1);
        mvc.perform(putJson(manager, "/api/v1/template-versions/" + v1, json("body", "تعديل", "version", 3)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.rule").value("TEMPLATE_VERSION_LOCKED"));
        mvc.perform(postJson(manager, "/api/v1/templates/" + id + "/versions",
                        json("body", BODY + " الصيغة الثانية", "changeNote", "إضافة فقرة")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.versionNo").value(2))
                .andExpect(jsonPath("$.status").value("DRAFT")).andExpect(jsonPath("$.changeNote").value("إضافة فقرة"));
        mvc.perform(getJson(manager, "/api/v1/templates/" + id)).andExpect(jsonPath("$.versions.length()").value(2))
                .andExpect(jsonPath("$.versions[0].versionNo").value(2)).andExpect(jsonPath("$.versions[1].status").value("APPROVED"))
                .andExpect(jsonPath("$.versions[1].body").value(BODY + " نص إضافي"));
        mvc.perform(postJson(manager, "/api/v1/templates/" + id + "/versions", json("body", " ")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void approvalFollowsReviewAndNeverSkipsItAndAnUnknownPlaceholderBlocksIt() throws Exception {
        UUID id = template("DEFENSE_MEMO");
        UUID v1 = versionOf(id, 1);
        mvc.perform(postJson(manager, "/api/v1/template-versions/" + v1 + "/approve", json("version", 0)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.rule").value("TEMPLATE_VERSION_TRANSITION"));
        UUID v2 = idOf(mvc.perform(postJson(manager, "/api/v1/templates/" + id + "/versions",
                json("body", "نص {{case.noSuchField}} و{{case.title}}"))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.placeholders[0].known").value(false)).andReturn());
        mvc.perform(postJson(manager, "/api/v1/template-versions/" + v2 + "/review", json("version", 0)))
                .andExpect(status().isOk());
        mvc.perform(postJson(manager, "/api/v1/template-versions/" + v2 + "/approve", json("version", 1)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.rule").value("TEMPLATE_UNKNOWN_PLACEHOLDER"));
    }

    @Test
    void theTemplateIsEditedAndDeactivatedButNeverDeleted() throws Exception {
        UUID id = template("DEFENSE_MEMO");
        mvc.perform(putJson(manager, "/api/v1/templates/" + id,
                        json("kind", "REPLY_MEMO", "title", "مذكرة تعقيب", "description", "", "active", false, "version", 0)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.kind").value("REPLY_MEMO"))
                .andExpect(jsonPath("$.active").value(false)).andExpect(jsonPath("$.version").value(1));
        mvc.perform(getJson(manager, "/api/v1/templates")).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(getJson(manager, "/api/v1/templates?includeInactive=true")).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(putJson(manager, "/api/v1/templates/" + id, json("kind", "REPLY_MEMO", "title", "x", "active", true, "version", 0)))
                .andExpect(status().isConflict());
    }

    @Test
    void onlyManagersWriteAndOthersSeeTheApprovedVersionsWithoutTheirText() throws Exception {
        UUID id = template("DEFENSE_MEMO");
        approve(versionOf(id, 1));
        mvc.perform(postJson(manager, "/api/v1/templates/" + id + "/versions", json("body", "مسودة لم تعتمد")))
                .andExpect(status().isCreated());
        RequestPostProcessor reader = staff(readerUser, Permission.CASE_VIEW);
        mvc.perform(postJson(reader, "/api/v1/templates", json("kind", "DEFENSE_MEMO", "title", "t", "body", "x")))
                .andExpect(status().isForbidden());
        mvc.perform(postJson(reader, "/api/v1/templates/" + id + "/versions", json("body", "x"))).andExpect(status().isForbidden());
        mvc.perform(postJson(reader, "/api/v1/template-versions/" + versionOf(id, 2) + "/review", json("version", 0)))
                .andExpect(status().isForbidden());
        mvc.perform(getJson(reader, "/api/v1/templates/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.versions.length()").value(1)).andExpect(jsonPath("$.versions[0].status").value("APPROVED"))
                .andExpect(jsonPath("$.versions[0].body").doesNotExist());
        mvc.perform(getJson(staff(readerUser), "/api/v1/templates")).andExpect(status().isForbidden());
    }

    @Test
    void theFieldsATemplateMayUseAreListedFromTheCaseFileOnly() throws Exception {
        mvc.perform(getJson(manager, "/api/v1/templates/fields")).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.key == 'case.referenceNo')].labelAr").value("الرقم الداخلي للقضية"))
                .andExpect(jsonPath("$[?(@.key == 'case.claimedAmount')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.key == 'case.financialAmount')]").isNotEmpty());
    }

    // ---- LD-DOC-29-010: the draft ---------------------------------------------------------------------------

    @Test
    void aDraftFillsThePlaceholdersFromTheCaseFileIsNeverFinalAndNamesWhatIsMissing() throws Exception {
        UUID id = template("DEFENSE_MEMO");
        approve(versionOf(id, 1));
        String reference = jdbc.queryForObject("select reference_no from legal_case where id = ?", String.class, caseId);
        onCase(caseId, party("هيئة الاختبار", "ORGANIZATION", null, false, null), "CLIENT");
        onCase(caseId, party("شركة المورد", "ORGANIZATION", null, false, null), "OPPONENT");
        UUID proceeding = proceeding(caseId, "455");
        hearing(caseId, proceeding, "2999-01-05", null, null);
        RequestPostProcessor reader = viewer(readerUser, Permission.CASE_VIEW);
        mvc.perform(postJson(reader, "/api/v1/cases/" + caseId + "/template-drafts", json("templateId", id)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.draft").value(true)).andExpect(jsonPath("$.final").value(false))
                .andExpect(jsonPath("$.text").value("مذكرة بدفاع في القضية " + reference + " المقامة من شركة المورد ضد هيئة الاختبار"
                        + " أمام محكمة الاختبار بجلسة 2999-01-05، دائرة {{case.chamber}}."))
                .andExpect(jsonPath("$.missing.length()").value(1)).andExpect(jsonPath("$.missing[0].key").value("case.chamber"))
                .andExpect(jsonPath("$.missing[0].labelAr").value("الدائرة")).andExpect(jsonPath("$.filled.length()").value(5))
                .andExpect(jsonPath("$.unknown.length()").value(0)).andExpect(jsonPath("$.versionNo").value(1))
                .andExpect(jsonPath("$.versionStatus").value("APPROVED")).andExpect(jsonPath("$.caseId").value(caseId.toString()))
                .andExpect(jsonPath("$.notice").isString());
        assertThat(count("select count(*) from audit_event where action = 'TEMPLATE_DRAFT' and outcome = 'SUCCESS'"
                + " and target_id = ?", caseId.toString())).isEqualTo(1);
        // Generating a draft changes nothing in the case file.
        assertThat(count("select count(*) from case_document where case_id = ?", caseId)).isZero();
    }

    @Test
    void aFieldTheCaseFileDoesNotHoldStaysAPlaceholderAndNothingIsInvented() throws Exception {
        UUID id = idOf(mvc.perform(postJson(manager, "/api/v1/templates",
                json("kind", "CASE_SUMMARY", "title", "ملخص", "body",
                        "القضية {{case.title}} — المبلغ المطالب به {{case.claimedAmount}} — الأثر المالي {{case.financialAmount}}"
                                + " — الأطراف {{parties.clients}} — المحكمة {{court.name}} — {{case.noSuchField}}")))
                .andExpect(status().isCreated()).andReturn());
        // A draft of an unapproved version is for managers only, and shows the unknown field.
        mvc.perform(postJson(manager, "/api/v1/cases/" + caseId + "/template-drafts", json("templateId", id)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.versionStatus").value("DRAFT"))
                .andExpect(jsonPath("$.text").value("القضية نزاع على عقد التوريد — المبلغ المطالب به {{case.claimedAmount}}"
                        + " — الأثر المالي {{case.financialAmount}} — الأطراف {{parties.clients}} — المحكمة {{court.name}}"
                        + " — {{case.noSuchField}}"))
                .andExpect(jsonPath("$.missing.length()").value(4)).andExpect(jsonPath("$.unknown[0]").value("case.noSuchField"));
        jdbc.update("update legal_case set claimed_amount = 1500.250, financial_impact = 'YES', financial_amount = 99.000 where id = ?",
                caseId);
        MvcResult filled = mvc.perform(postJson(manager, "/api/v1/cases/" + caseId + "/template-drafts", json("templateId", id)))
                .andExpect(status().isOk()).andReturn();
        // Each amount is its own field and is shown with its own label, never mixed.
        assertThat(read(filled, "$.text").toString()).contains("المبلغ المطالب به 1500.250 دينار كويتي")
                .contains("الأثر المالي 99.000 دينار كويتي");
    }

    @Test
    void onlyAnApprovedVersionIsUsedByEveryoneAndTheLatestApprovedByDefault() throws Exception {
        UUID id = template("DEFENSE_MEMO");
        RequestPostProcessor reader = viewer(readerUser, Permission.CASE_VIEW);
        mvc.perform(postJson(reader, "/api/v1/cases/" + caseId + "/template-drafts", json("templateId", id)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.rule").value("TEMPLATE_NO_APPROVED_VERSION"));
        mvc.perform(postJson(reader, "/api/v1/cases/" + caseId + "/template-drafts",
                json("templateId", id, "versionId", versionOf(id, 1)))).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.rule").value("TEMPLATE_NOT_APPROVED"));
        approve(versionOf(id, 1));
        UUID v2 = idOf(mvc.perform(postJson(manager, "/api/v1/templates/" + id + "/versions", json("body", "الصيغة الثانية {{case.title}}")))
                .andReturn());
        approve(v2);
        mvc.perform(postJson(reader, "/api/v1/cases/" + caseId + "/template-drafts", json("templateId", id)))
                .andExpect(jsonPath("$.versionNo").value(2)).andExpect(jsonPath("$.text").value("الصيغة الثانية نزاع على عقد التوريد"));
        mvc.perform(postJson(reader, "/api/v1/cases/" + caseId + "/template-drafts",
                json("templateId", id, "versionId", versionOf(id, 1)))).andExpect(jsonPath("$.versionNo").value(1));
        mvc.perform(putJson(manager, "/api/v1/templates/" + id, json("kind", "DEFENSE_MEMO", "title", "t", "active", false, "version", 0)))
                .andExpect(status().isOk());
        mvc.perform(postJson(reader, "/api/v1/cases/" + caseId + "/template-drafts", json("templateId", id)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.rule").value("TEMPLATE_INACTIVE"));
    }

    @Test
    void aDraftIsGeneratedOnlyForACaseTheUserMaySee() throws Exception {
        UUID id = template("DEFENSE_MEMO");
        approve(versionOf(id, 1));
        RequestPostProcessor nobody = outsider(readerUser, Permission.CASE_VIEW, Permission.TEMPLATE_MANAGE);
        mvc.perform(postJson(nobody, "/api/v1/cases/" + caseId + "/template-drafts", json("templateId", id)))
                .andExpect(status().isNotFound());
        mvc.perform(postJson(staff(readerUser), "/api/v1/cases/" + caseId + "/template-drafts", json("templateId", id)))
                .andExpect(status().isForbidden());
        mvc.perform(postJson(manager, "/api/v1/cases/" + caseId + "/template-drafts", json("templateId", UUID.randomUUID())))
                .andExpect(status().isNotFound());
        assertThat(count("select count(*) from audit_event where action = 'TEMPLATE_DRAFT' and target_id = ?", caseId.toString()))
                .isZero();
    }
}
