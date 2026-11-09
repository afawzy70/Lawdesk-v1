package com.lawdesk.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;

/** Owner decision D10: managed lists that tell the document's values from proposed ones and keep their history. */
class LibraryClassificationApiTest extends LibrarySupport {

    private static final String BASE = "/api/v1/library/classifications";

    @Test
    void theOnlySeededValuesAreTheOnesTheRequirementsTextLists() throws Exception {
        mvc.perform(getAs(READER, BASE + "?dimension=REQUEST_TYPE")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6)).andExpect(jsonPath("$[0].labelAr").value("أصلي"))
                .andExpect(jsonPath("$[0].origin").value("DOCUMENT"));
        mvc.perform(getAs(READER, BASE + "?dimension=DEFENSE_TYPE")).andExpect(jsonPath("$.length()").value(4));
        mvc.perform(getAs(READER, BASE + "?dimension=OUTCOME")).andExpect(jsonPath("$.length()").value(12));
        for (String empty : new String[] {"CASE_KIND", "LEGAL_TOPIC", "SECTOR"}) {
            mvc.perform(getAs(READER, BASE + "?dimension=" + empty)).andExpect(jsonPath("$.length()").value(0));
        }
        assertThat(count("select count(*) from library_judgment")).isZero();
        assertThat(count("select count(*) from library_classification where origin <> 'DOCUMENT'")).isZero();
    }

    @Test
    void membersAddProposedValuesAndCannotClaimThemForTheDocument() throws Exception {
        mvc.perform(postJson(MANAGER, BASE, json("dimension", "LEGAL_TOPIC", "code", "TENDERS", "labelAr", "المناقصات",
                "labelEn", "Tenders", "origin", "DOCUMENT"))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.origin").value("PROPOSED")).andExpect(jsonPath("$.active").value(true));
        mvc.perform(postJson(MANAGER, BASE, json("dimension", "LEGAL_TOPIC", "code", "TENDERS", "labelAr", "أخرى")))
                .andExpect(status().isConflict());
        mvc.perform(postJson(MANAGER, BASE, json("dimension", "LEGAL_TOPIC", "code", "OTHER_CODE", "labelAr", "المناقصات")))
                .andExpect(status().isConflict());
        mvc.perform(postJson(MANAGER, BASE, json("dimension", "NOPE", "code", "X", "labelAr", "x")))
                .andExpect(status().isBadRequest());
        mvc.perform(postJson(MANAGER, BASE, json("dimension", "LEGAL_TOPIC", "code", "lower", "labelAr", "x")))
                .andExpect(status().isBadRequest());
        assertThat(audited("LIBRARY_CLASSIFICATION_CREATE")).isEqualTo(1);
    }

    @Test
    void editingKeepsTheOriginAndTheCodeAndIsAuditedWithThePreviousLabel() throws Exception {
        UUID id = seededClassification("DEFENSE_TYPE", "FORMAL");
        mvc.perform(putJson(MANAGER, BASE + "/" + id, json("labelAr", "شكلي (معدّل)", "active", true, "version", 0)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.labelAr").value("شكلي (معدّل)"))
                .andExpect(jsonPath("$.origin").value("DOCUMENT")).andExpect(jsonPath("$.code").value("FORMAL"))
                .andExpect(jsonPath("$.version").value(1));
        assertThat(auditedReason("LIBRARY_CLASSIFICATION_UPDATE")).contains("شكلي");
        mvc.perform(putJson(MANAGER, BASE + "/" + id, json("labelAr", "x", "active", true, "version", 0)))
                .andExpect(status().isConflict());
        mvc.perform(putJson(MANAGER, BASE + "/" + UUID.randomUUID(), json("labelAr", "x", "active", true, "version", 0)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deactivatingHidesTheValueFromTheListButKeepsItOnJudgmentsAlreadyClassified() throws Exception {
        UUID topic = classification("LEGAL_TOPIC", "OLD", "موضوع");
        UUID judgment = registerJson(judgmentJson("x", "classificationIds", java.util.List.of(topic.toString())));
        String listed = read(mvc.perform(getAs(MANAGER, BASE + "?dimension=LEGAL_TOPIC")).andReturn());
        int version = JsonPath.read(listed, "$[0].version");
        mvc.perform(putJson(MANAGER, BASE + "/" + topic, json("labelAr", "موضوع", "active", false, "version", version)))
                .andExpect(status().isOk());
        mvc.perform(getAs(READER, BASE + "?dimension=LEGAL_TOPIC")).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(getAs(READER, BASE + "?dimension=LEGAL_TOPIC&includeInactive=true"))
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(getAs(READER, "/api/v1/library/judgments/" + judgment))
                .andExpect(jsonPath("$.classifications[0].code").value("OLD"));
        assertThat(count("select count(*) from library_classification where id = ?", topic)).isEqualTo(1);
    }

    @Test
    void onlyManagersChangeTheLists() throws Exception {
        UUID id = seededClassification("OUTCOME", "ACCEPTED");
        mvc.perform(postJson(READER, BASE, json("dimension", "LEGAL_TOPIC", "code", "X", "labelAr", "x")))
                .andExpect(status().isForbidden());
        mvc.perform(putJson(READER, BASE + "/" + id, json("labelAr", "x", "active", true, "version", 0)))
                .andExpect(status().isForbidden());
    }
}
