package com.lawdesk.followup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.lawdesk.security.Permission;

/**
 * LD-053, requirements section 19, scenario S7: a risk assessment of ten elements and a level with its reasons, a new
 * version after a new document (the earlier one stays, unedited), the plan for a high risk and the report of high-risk
 * cases. The potential loss is its own KWD amount (D5).
 */
class RiskAssessmentApiTest extends Wp3TestSupport {

    private static final Permission[] ASSESSOR = {Permission.CASE_VIEW, Permission.RISK_VIEW, Permission.RISK_MANAGE};
    private static final List<String> CODES = List.of("LEGAL_POSITION_STRENGTH", "EVIDENCE_STRENGTH",
            "ADMINISTRATIVE_PROCEDURE_SOUNDNESS", "POTENTIAL_PROCEDURAL_DEFECTS", "SIMILAR_PRECEDENTS",
            "JUDICIAL_TREND_STABILITY", "POTENTIAL_FINANCIAL_LOSS", "ADMINISTRATIVE_IMPACT", "SPILLOVER_TO_OTHER_CASES",
            "INFORMATION_AVAILABILITY");

    private UUID specialist;
    private UUID caseId;
    private RequestPostProcessor assessor;

    @BeforeEach
    void aCase() {
        specialist = user("Specialist");
        caseId = legalCase(specialist, null);
        assessor = staff(specialist, ASSESSOR);
    }

    private static Map<String, Object> element(String code, String rating, String reason) {
        return Map.of("code", code, "rating", rating, "reason", reason);
    }

    private static List<Map<String, Object>> elements(String rating) {
        return CODES.stream().map(code -> element(code, rating, "reason for " + code)).toList();
    }

    private static Map<String, Object> plan() {
        return Map.of("cause", "Weak evidence of notification", "action", "Obtain the delivery record",
                "documents", "Delivery receipt", "defenses", "Time-bar", "unitToAddress", "Records department");
    }

    private MvcResult assess(String level, Object potentialLoss, String trigger, List<?> elements, List<?> plans)
            throws Exception {
        return mvc.perform(postJson(assessor, "/api/v1/cases/" + caseId + "/risk-assessments",
                json("level", level, "levelReasons", "Based on the evidence so far", "potentialLoss", potentialLoss,
                        "trigger", trigger, "triggerNote", "note", "elements", elements, "remediation", plans)))
                .andExpect(status().isCreated()).andReturn();
    }

    @Test
    void anAssessmentHasTenElementsALevelWithReasonsAndItsOwnLossAmount() throws Exception {
        mvc.perform(postJson(assessor, "/api/v1/cases/" + caseId + "/risk-assessments",
                        json("level", "MEDIUM", "levelReasons", "Mixed evidence", "potentialLoss", "15000.500",
                                "trigger", "MANUAL", "elements", elements("MEDIUM"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionNo").value(1)).andExpect(jsonPath("$.level").value("MEDIUM"))
                .andExpect(jsonPath("$.levelReasons").value("Mixed evidence"))
                .andExpect(jsonPath("$.potentialLoss").value("15000.500"))
                .andExpect(jsonPath("$.trigger").value("MANUAL"))
                .andExpect(jsonPath("$.elements.length()").value(10))
                .andExpect(jsonPath("$.elements[6].code").value("POTENTIAL_FINANCIAL_LOSS"))
                .andExpect(jsonPath("$.assessedByName").value("Specialist"))
                .andExpect(jsonPath("$.remediation.length()").value(0));
    }

    @Test
    void anElementThatIsNotRatedIsNotAssessedNeverGuessed() throws Exception {
        MvcResult result = mvc.perform(postJson(assessor, "/api/v1/cases/" + caseId + "/risk-assessments",
                        json("level", "LOW", "levelReasons", "Strong file", "trigger", "MANUAL",
                                "elements", List.of(element("EVIDENCE_STRENGTH", "LOW", "complete")))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.elements.length()").value(10))
                .andExpect(jsonPath("$.potentialLoss").doesNotExist()).andReturn();
        assertThat(count("select count(*) from risk_assessment_element where rating = 'NOT_ASSESSED'")).isEqualTo(9);
        assertThat(read(result, "$.elements[1].rating").toString()).isEqualTo("LOW");
    }

    @Test
    void refusesWhatIsNotAValidAssessment() throws Exception {
        String path = "/api/v1/cases/" + caseId + "/risk-assessments";
        mvc.perform(postJson(assessor, path, json("level", "LOW", "levelReasons", " ", "trigger", "MANUAL")))
                .andExpect(status().isBadRequest());
        mvc.perform(postJson(assessor, path, json("level", "SEVERE", "levelReasons", "x", "trigger", "MANUAL")))
                .andExpect(status().isBadRequest());
        mvc.perform(postJson(assessor, path, json("level", "LOW", "levelReasons", "x", "trigger", "WHIM")))
                .andExpect(status().isBadRequest());
        mvc.perform(postJson(assessor, path, json("level", "LOW", "levelReasons", "x", "trigger", "MANUAL",
                        "elements", List.of(element("NOT_AN_ELEMENT", "LOW", "x"))))).andExpect(status().isBadRequest());
        mvc.perform(postJson(assessor, path, json("level", "LOW", "levelReasons", "x", "trigger", "MANUAL",
                        "elements", List.of(element("EVIDENCE_STRENGTH", "LOW", "x"), element("EVIDENCE_STRENGTH", "HIGH", "y")))))
                .andExpect(status().isBadRequest());
        mvc.perform(postJson(assessor, path, json("level", "LOW", "levelReasons", "x", "trigger", "MANUAL",
                        "elements", List.of(element("EVIDENCE_STRENGTH", "TERRIBLE", "x"))))).andExpect(status().isBadRequest());
        for (Object loss : List.of("-1.000", "1.2345", "abc", "10000000000000.000")) {
            mvc.perform(postJson(assessor, path, json("level", "LOW", "levelReasons", "x", "trigger", "MANUAL",
                            "potentialLoss", loss))).andExpect(status().isBadRequest());
        }
        assertThat(count("select count(*) from risk_assessment")).isZero();
    }

    @Test
    void aNewAssessmentAfterANewDocumentIsANewVersionAndTheEarlierOneIsKeptUnchanged() throws Exception {
        assess("HIGH", "90000.000", "MANUAL", elements("HIGH"), List.of(plan()));
        String before = jdbc.queryForObject("select level || '|' || level_reasons || '|' || potential_loss from risk_assessment where version_no = 1",
                String.class);
        UUID document = jdbc.queryForObject("""
                insert into case_document (case_id, title, file_name, size_bytes, sha256, created_by)
                values (?, 'Delivery record', 'a.pdf', 5, repeat('c', 64), ?) returning id""", UUID.class, caseId, specialist);
        mvc.perform(postJson(assessor, "/api/v1/cases/" + caseId + "/risk-assessments",
                        json("level", "LOW", "levelReasons", "The delivery record proves notification", "potentialLoss", "0.000",
                                "trigger", "NEW_DOCUMENT", "triggerRefId", document, "triggerNote", "Delivery record received",
                                "elements", elements("LOW"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.versionNo").value(2))
                .andExpect(jsonPath("$.trigger").value("NEW_DOCUMENT")).andExpect(jsonPath("$.triggerRefId").value(document.toString()));
        mvc.perform(getJson(assessor, "/api/v1/cases/" + caseId + "/risk-assessments"))
                .andExpect(jsonPath("$.items.length()").value(2)).andExpect(jsonPath("$.items[0].versionNo").value(2))
                .andExpect(jsonPath("$.items[1].versionNo").value(1)).andExpect(jsonPath("$.items[1].level").value("HIGH"))
                .andExpect(jsonPath("$.items[1].remediation.length()").value(1));
        assertThat(jdbc.queryForObject("select level || '|' || level_reasons || '|' || potential_loss from risk_assessment where version_no = 1",
                String.class)).isEqualTo(before);
    }

    @Test
    void theDatabaseRefusesToEditAVersion() throws Exception {
        assess("MEDIUM", null, "MANUAL", elements("MEDIUM"), List.of());
        assertThatThrownBy(() -> jdbc.update("update risk_assessment set level = 'LOW'"))
                .hasMessageContaining("never edited");
        assertThatThrownBy(() -> jdbc.update("update risk_assessment_element set rating = 'LOW'"))
                .hasMessageContaining("never edited");
    }

    @Test
    void versionNumbersAreGaplessPerCaseAndIndependentBetweenCases() throws Exception {
        assess("LOW", null, "MANUAL", List.of(), List.of());
        assess("LOW", null, "PERIODIC", List.of(), List.of());
        UUID second = legalCase(specialist, null);
        mvc.perform(postJson(assessor, "/api/v1/cases/" + second + "/risk-assessments",
                        json("level", "LOW", "levelReasons", "x", "trigger", "JUDGMENT")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.versionNo").value(1));
        assertThat(jdbc.queryForList("select version_no from risk_assessment where case_id = ? order by 1", Integer.class, caseId))
                .containsExactly(1, 2);
    }

    // --- the plan for a high risk (19.3) -----------------------------------------------------------------------------------

    @Test
    void aHighOrVeryHighLevelNeedsAPlanAndRaisesAnAlert() throws Exception {
        String path = "/api/v1/cases/" + caseId + "/risk-assessments";
        for (String level : List.of("HIGH", "VERY_HIGH")) {
            mvc.perform(postJson(assessor, path, json("level", level, "levelReasons", "x", "trigger", "MANUAL")))
                    .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.rule").value("REMEDIATION_REQUIRED"));
        }
        assertThat(count("select count(*) from risk_assessment")).isZero();
        MvcResult result = assess("VERY_HIGH", "5000.000", "JUDGMENT", elements("HIGH"), List.of(plan()));
        mvc.perform(getJson(assessor, "/api/v1/cases/" + caseId + "/risk-assessments"))
                .andExpect(jsonPath("$.items[0].remediation[0].cause").value("Weak evidence of notification"))
                .andExpect(jsonPath("$.items[0].remediation[0].status").value("OPEN"))
                .andExpect(jsonPath("$.items[0].remediation[0].unitToAddress").value("Records department"));
        assertThat(count("select count(*) from notification where type = 'RISK_HIGH' and user_id = ?", specialist)).isEqualTo(1);
        assertThat(read(result, "$.id").toString()).isNotBlank();
    }

    @Test
    void thePlanIsAddedToAndTrackedAfterwards() throws Exception {
        MvcResult result = assess("HIGH", "100.000", "MANUAL", elements("HIGH"), List.of(plan()));
        UUID assessment = idOf(result);
        MvcResult added = mvc.perform(postJson(assessor, "/api/v1/risk-assessments/" + assessment + "/remediations",
                        json("cause", "Missing expert report", "action", "Request an expert", "ownerId", specialist,
                                "targetDate", today().plusDays(14).toString())))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.ownerName").value("Specialist")).andReturn();
        UUID remediation = idOf(added);
        mvc.perform(putJson(assessor, "/api/v1/risk-remediations/" + remediation,
                        json("cause", "Missing expert report", "action", "Request an expert", "status", "IN_PROGRESS", "version", 0)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.version").value(1));
        mvc.perform(putJson(assessor, "/api/v1/risk-remediations/" + remediation,
                        json("cause", "c", "action", "a", "status", "DONE", "version", 0))).andExpect(status().isConflict());
        mvc.perform(putJson(assessor, "/api/v1/risk-remediations/" + remediation,
                        json("cause", "c", "action", "a", "status", "ARCHIVED", "version", 1))).andExpect(status().isBadRequest());
    }

    // --- the report of high-risk cases (19.5) ---------------------------------------------------------------------------------

    @Test
    void theReportListsTheLatestVersionOfEachVisibleCaseWhoseRiskIsHigh() throws Exception {
        assess("VERY_HIGH", "75000.250", "MANUAL", elements("HIGH"), List.of(plan()));
        UUID lowered = legalCase(specialist, null);
        mvc.perform(postJson(assessor, "/api/v1/cases/" + lowered + "/risk-assessments",
                json("level", "HIGH", "levelReasons", "x", "trigger", "MANUAL", "remediation", List.of(plan()))));
        mvc.perform(postJson(assessor, "/api/v1/cases/" + lowered + "/risk-assessments",
                json("level", "LOW", "levelReasons", "resolved", "trigger", "DECISION")));
        UUID others = legalCase(user("Someone else"), null);
        jdbc.update("""
                insert into risk_assessment (case_id, version_no, level, level_reasons, trigger_kind, assessed_by)
                values (?, 1, 'HIGH', 'hidden', 'MANUAL', ?)""", others, specialist);

        mvc.perform(getJson(outsider(specialist, ASSESSOR), "/api/v1/risk-assessments/high"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.notice").isString())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].caseId").value(caseId.toString()))
                .andExpect(jsonPath("$.items[0].level").value("VERY_HIGH"))
                .andExpect(jsonPath("$.items[0].potentialLoss").value("75000.250"))
                .andExpect(jsonPath("$.items[0].levelReasons").isString())
                .andExpect(jsonPath("$.items[0].openRemediation").value(1))
                .andExpect(jsonPath("$.items[0].totalRemediation").value(1));
        mvc.perform(getJson(staff(specialist, ASSESSOR), "/api/v1/risk-assessments/high"))
                .andExpect(jsonPath("$.items.length()").value(2));
    }

    // --- who sees and who changes (D3) ------------------------------------------------------------------------------------------

    @Test
    void anOutsiderFindsNeitherTheCaseNorItsAssessments() throws Exception {
        MvcResult result = assess("HIGH", null, "MANUAL", elements("HIGH"), List.of(plan()));
        UUID assessment = idOf(result);
        UUID remediation = jdbc.queryForObject("select id from risk_remediation limit 1", UUID.class);
        RequestPostProcessor nobody = outsider(user("Nobody"), Permission.CASE_VIEW, Permission.RISK_VIEW, Permission.RISK_MANAGE);
        mvc.perform(getJson(nobody, "/api/v1/cases/" + caseId + "/risk-assessments")).andExpect(status().isNotFound());
        mvc.perform(getJson(nobody, "/api/v1/risk-assessments/" + assessment)).andExpect(status().isNotFound());
        mvc.perform(postJson(nobody, "/api/v1/cases/" + caseId + "/risk-assessments",
                json("level", "LOW", "levelReasons", "x", "trigger", "MANUAL"))).andExpect(status().isNotFound());
        mvc.perform(postJson(nobody, "/api/v1/risk-assessments/" + assessment + "/remediations",
                json("cause", "c", "action", "a"))).andExpect(status().isNotFound());
        mvc.perform(putJson(nobody, "/api/v1/risk-remediations/" + remediation,
                json("cause", "c", "action", "a", "status", "DONE", "version", 0))).andExpect(status().isNotFound());
        mvc.perform(getJson(nobody, "/api/v1/risk-assessments/high")).andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void someoneWhoOnlySeesTheCaseReadsButDoesNotAssess() throws Exception {
        MvcResult result = assess("HIGH", null, "MANUAL", elements("HIGH"), List.of(plan()));
        UUID assessment = idOf(result);
        RequestPostProcessor reader = viewer(user("Reader"), Permission.CASE_VIEW, Permission.RISK_VIEW, Permission.RISK_MANAGE);
        mvc.perform(getJson(reader, "/api/v1/cases/" + caseId + "/risk-assessments")).andExpect(status().isOk());
        mvc.perform(postJson(reader, "/api/v1/cases/" + caseId + "/risk-assessments",
                json("level", "LOW", "levelReasons", "x", "trigger", "MANUAL"))).andExpect(status().isForbidden());
        mvc.perform(postJson(reader, "/api/v1/risk-assessments/" + assessment + "/remediations",
                json("cause", "c", "action", "a"))).andExpect(status().isForbidden());
    }

    @Test
    void thePermissionsGateTheModule() throws Exception {
        mvc.perform(getJson(staff(specialist, Permission.CASE_VIEW), "/api/v1/cases/" + caseId + "/risk-assessments"))
                .andExpect(status().isForbidden());
        mvc.perform(postJson(staff(specialist, Permission.CASE_VIEW, Permission.RISK_VIEW),
                "/api/v1/cases/" + caseId + "/risk-assessments", json("level", "LOW", "levelReasons", "x", "trigger", "MANUAL")))
                .andExpect(status().isForbidden());
    }
}
