package com.lawdesk.pleadings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;
import com.lawdesk.security.Permission;

/**
 * Requirements section 9 (LD-DOC-09): the extended judgment. The legacy fields (date, outcome, summary, final) stay valid
 * and a request that does not carry the new {@code details} leaves them as they are.
 */
class JudgmentDetailsApiTest extends PleadingsTestSupport {

    private UUID typeId(String code) {
        return jdbc.queryForObject("select id from lookup_value where list_code = 'JUDGMENT_TYPE' and code = ?", UUID.class, code);
    }

    private UUID natureId(String code) {
        return jdbc.queryForObject("select id from lookup_value where list_code = 'JUDGMENT_NATURE' and code = ?", UUID.class, code);
    }

    private String judgmentBody(String details, Integer version) {
        return "{\"judgmentDate\":\"2026-09-28\",\"outcome\":\"PARTIAL\",\"summary\":\"legacy summary\",\"final\":false"
                + (details == null ? "" : ",\"details\":" + details) + (version == null ? "" : ",\"version\":" + version) + "}";
    }

    @Test
    void theLegacyRequestStillWorksAndTheExtendedFieldsAreUnspecified() throws Exception {
        UUID proceeding = firstInstance(newCase());
        post(everything(), "/api/v1/proceedings/" + proceeding + "/judgment", judgmentBody(null, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.outcome").value("PARTIAL"))
                .andExpect(jsonPath("$.summary").value("legacy summary"))
                .andExpect(jsonPath("$.details.operativeText").doesNotExist())
                .andExpect(jsonPath("$.details.costsAmount").doesNotExist())
                .andExpect(jsonPath("$.details.appealable").doesNotExist())
                .andExpect(jsonPath("$.details.judgmentTypeId").doesNotExist());
    }

    @Test
    void recordsTheExtendedJudgmentWithAmountsAsStrings() throws Exception {
        UUID caseId = newCase();
        UUID proceeding = firstInstance(caseId);
        UUID copy = document(caseId, "Judgment copy");
        String details = json("chamber", "الدائرة الثالثة", "judgmentTypeId", typeId("SUBSTANTIVE"), "natureId",
                natureId("FIRST_INSTANCE_RULING"), "operativeText", "  The full operative text  ", "reasons", "The reasons",
                "costsAmount", "120.000", "lawyerFeesAmount", 300, "appealable", true, "notifiedOn", "2026-09-30",
                "status", "NOTIFIED", "copyDocumentId", copy);

        String created = body(post(everything(), "/api/v1/proceedings/" + proceeding + "/judgment", judgmentBody(details, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.details.chamber").value("الدائرة الثالثة"))
                .andExpect(jsonPath("$.details.judgmentTypeLabelEn").value("Substantive judgment"))
                .andExpect(jsonPath("$.details.natureLabelAr").value("ابتدائي"))
                .andExpect(jsonPath("$.details.operativeText").value("The full operative text"))
                .andExpect(jsonPath("$.details.costsAmount").value("120.000"))
                .andExpect(jsonPath("$.details.lawyerFeesAmount").value("300.000"))
                .andExpect(jsonPath("$.details.appealable").value(true))
                .andExpect(jsonPath("$.details.notifiedOn").value("2026-09-30"))
                .andExpect(jsonPath("$.details.status").value("NOTIFIED"))
                .andExpect(jsonPath("$.details.copyDocumentId").value(copy.toString())));
        String id = JsonPath.read(created, "$.id");

        get(everything(), "/api/v1/judgments/" + id).andExpect(jsonPath("$.details.costsAmount").value("120.000"));
        get(everything(), "/api/v1/cases/" + caseId + "/judgments").andExpect(jsonPath("$.items[0].details.reasons").value("The reasons"));
    }

    @Test
    void anUpdateWithoutDetailsKeepsThemAndOneWithDetailsReplacesThem() throws Exception {
        UUID proceeding = firstInstance(newCase());
        String id = JsonPath.read(body(post(everything(), "/api/v1/proceedings/" + proceeding + "/judgment",
                judgmentBody(json("operativeText", "kept", "costsAmount", "10.500"), null))), "$.id");

        put(everything(), "/api/v1/judgments/" + id, judgmentBody(null, 0)).andExpect(status().isOk())
                .andExpect(jsonPath("$.details.operativeText").value("kept"))
                .andExpect(jsonPath("$.details.costsAmount").value("10.500"));
        put(everything(), "/api/v1/judgments/" + id, judgmentBody(json("reasons", "new reasons"), 1)).andExpect(status().isOk())
                .andExpect(jsonPath("$.details.operativeText").doesNotExist())
                .andExpect(jsonPath("$.details.costsAmount").doesNotExist())
                .andExpect(jsonPath("$.details.reasons").value("new reasons"));
    }

    @Test
    void refusesValuesThatDoNotFit() throws Exception {
        UUID caseId = newCase();
        UUID proceeding = firstInstance(caseId);
        String path = "/api/v1/proceedings/" + proceeding + "/judgment";
        // A nature is not a type; an unknown value; a deactivated value; a document of another case.
        post(everything(), path, judgmentBody(json("judgmentTypeId", natureId("FINAL_RULING")), null)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("details.judgmentTypeId"));
        post(everything(), path, judgmentBody(json("natureId", UUID.randomUUID()), null)).andExpect(status().isBadRequest());
        jdbc.update("update lookup_value set active = false where id = ?", typeId("PREPARATORY"));
        try {
            post(everything(), path, judgmentBody(json("judgmentTypeId", typeId("PREPARATORY")), null)).andExpect(status().isBadRequest());
        } finally {
            jdbc.update("update lookup_value set active = true where id = ?", typeId("PREPARATORY"));
        }
        post(everything(), path, judgmentBody(json("copyDocumentId", document(newCase(), "elsewhere")), null))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("details.copyDocumentId"));
        post(everything(), path, judgmentBody(json("costsAmount", "-1.000"), null)).andExpect(status().isBadRequest());
        post(everything(), path, judgmentBody(json("costsAmount", "1.2345"), null)).andExpect(status().isBadRequest());
        post(everything(), path, judgmentBody(json("status", "SOMETHING"), null)).andExpect(status().isBadRequest());
        assertThat(count("select count(*) from judgment")).isZero();
    }

    @Test
    void aValueDeactivatedLaterStaysOnTheJudgmentThatUsesIt() throws Exception {
        UUID proceeding = firstInstance(newCase());
        UUID type = typeId("JURISDICTION");
        String id = JsonPath.read(body(post(everything(), "/api/v1/proceedings/" + proceeding + "/judgment",
                judgmentBody(json("judgmentTypeId", type), null))), "$.id");
        jdbc.update("update lookup_value set active = false where id = ?", type);
        try {
            put(everything(), "/api/v1/judgments/" + id, judgmentBody(json("judgmentTypeId", type, "reasons", "r"), 0))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.details.judgmentTypeLabelEn").value("Judgment on jurisdiction"));
        } finally {
            jdbc.update("update lookup_value set active = true where id = ?", type);
        }
    }

    @Test
    void anOutsiderCannotReadOrWriteTheDetails() throws Exception {
        UUID specialist = user("Specialist");
        UUID caseId = newCase(specialist, "CIVIL");
        UUID proceeding = firstInstance(caseId);
        UUID stranger = UUID.randomUUID();
        post(outsider(stranger), "/api/v1/proceedings/" + proceeding + "/judgment", judgmentBody(json("reasons", "x"), null))
                .andExpect(status().isNotFound());
        post(viewerOf(stranger), "/api/v1/proceedings/" + proceeding + "/judgment", judgmentBody(json("reasons", "x"), null))
                .andExpect(status().isForbidden());
        post(member(specialist), "/api/v1/proceedings/" + proceeding + "/judgment", judgmentBody(json("reasons", "x"), null))
                .andExpect(status().isCreated());
        post(with(Permission.JUDGMENT_VIEW), "/api/v1/proceedings/" + proceeding + "/judgment", judgmentBody(null, null))
                .andExpect(status().isForbidden());
    }
}
