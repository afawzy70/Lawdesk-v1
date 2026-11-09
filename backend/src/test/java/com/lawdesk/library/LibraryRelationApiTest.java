package com.lawdesk.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;

/**
 * LD-052, section 16.6: what happened to a judgment afterwards is recorded with its source; the library shows it and
 * warns, but never decides the legal validity and never calls a trend settled.
 */
class LibraryRelationApiTest extends LibrarySupport {

    private static final String BASE = "/api/v1/library/judgments/";

    private String relation(String type, Object... more) {
        Object[] base = {"relationType", type, "sourceReference", "حكم الاستئناف رقم ٩٠٠ لسنة ٢٠٢١"};
        Object[] all = new Object[base.length + more.length];
        System.arraycopy(base, 0, all, 0, base.length);
        System.arraycopy(more, 0, all, base.length, more.length);
        return json(all);
    }

    @Test
    void aJudgmentWithNoRelationsHasNoLaterWarningButSaysFinalityIsNotSpecified() throws Exception {
        UUID id = register("إيجار");
        mvc.perform(getAs(READER, BASE + id + "/validity")).andExpect(status().isOk())
                .andExpect(jsonPath("$.relationCount").value(0))
                .andExpect(jsonPath("$.warnings[0]").value("FINALITY_NOT_SPECIFIED"))
                .andExpect(jsonPath("$.legalValidityDecided").value(false))
                .andExpect(jsonPath("$.note").isNotEmpty());
    }

    @Test
    void aRecordedRelationWithItsSourceMakesTheLibraryWarnWithoutDecidingAnything() throws Exception {
        UUID original = register("إيجار", "finality", "FINAL");
        UUID later = register("إيجار لاحق", "judgmentDate", "2022-02-02");
        mvc.perform(postJson(MANAGER, BASE + original + "/relations", relation("OVERTURNED", "relatedJudgmentId",
                later.toString(), "relationDate", "2022-02-02", "notes", "نقض في الطعن"))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.outgoing.length()").value(1))
                .andExpect(jsonPath("$.outgoing[0].relationType").value("OVERTURNED"))
                .andExpect(jsonPath("$.outgoing[0].otherJudgmentId").value(later.toString()))
                .andExpect(jsonPath("$.outgoing[0].sourceReference").isNotEmpty());
        mvc.perform(getAs(READER, BASE + original + "/validity")).andExpect(jsonPath("$.relationCount").value(1))
                .andExpect(jsonPath("$.relationTypes[0]").value("OVERTURNED"))
                .andExpect(jsonPath("$.warnings[0]").value("LATER_RELATION_EXISTS"))
                .andExpect(jsonPath("$.finality").value("FINAL"))
                .andExpect(jsonPath("$.legalValidityDecided").value(false));
        // The later judgment sees the other side of the same fact, and has no warning of its own.
        mvc.perform(getAs(READER, BASE + later)).andExpect(jsonPath("$.relations.incoming.length()").value(1))
                .andExpect(jsonPath("$.relations.incoming[0].otherJudgmentId").value(original.toString()))
                .andExpect(jsonPath("$.validity.relationCount").value(0));
        // The search results flag a judgment that has relations.
        mvc.perform(getAs(READER, "/api/v1/library/judgments?q=إيجار"))
                .andExpect(jsonPath("$.items[?(@.id=='" + original + "')].relationTypes[0]").value("OVERTURNED"));
    }

    @Test
    void aDecisionOutsideTheLibraryCanBeRecordedByCitationAndEveryTypeIsAccepted() throws Exception {
        UUID id = register("إيجار", "finality", "NOT_FINAL");
        for (String type : new String[] {"APPEALED", "UPHELD", "MODIFIED", "OVERTURNED", "LATER", "DEPARTED_FROM"}) {
            mvc.perform(postJson(MANAGER, BASE + id + "/relations", relation(type, "relatedCitation", "الطعن ٧٧ لسنة ٢٠٢٢ " + type)))
                    .andExpect(status().isCreated());
        }
        mvc.perform(getAs(READER, BASE + id + "/validity")).andExpect(jsonPath("$.relationCount").value(6))
                .andExpect(jsonPath("$.warnings[1]").value("NOT_FINAL"));
        assertThat(audited("LIBRARY_RELATION_ADD")).isEqualTo(6);
    }

    @Test
    void aRelationNeedsItsSourceTheOtherSideAndAKnownType() throws Exception {
        UUID id = register("إيجار");
        mvc.perform(postJson(MANAGER, BASE + id + "/relations", json("relationType", "OVERTURNED", "relatedCitation", "x")))
                .andExpect(status().isBadRequest());
        mvc.perform(postJson(MANAGER, BASE + id + "/relations", relation("OVERTURNED")))
                .andExpect(status().isBadRequest());
        mvc.perform(postJson(MANAGER, BASE + id + "/relations", relation("CANCELLED", "relatedCitation", "x")))
                .andExpect(status().isBadRequest());
        mvc.perform(postJson(MANAGER, BASE + id + "/relations", relation("LATER", "relatedJudgmentId", id.toString())))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(postJson(MANAGER, BASE + id + "/relations", relation("LATER", "relatedJudgmentId",
                UUID.randomUUID().toString()))).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("LIBRARY_RELATED_JUDGMENT_UNKNOWN"));
        mvc.perform(postJson(MANAGER, BASE + UUID.randomUUID() + "/relations", relation("LATER", "relatedCitation", "x")))
                .andExpect(status().isNotFound());
        assertThat(count("select count(*) from library_judgment_relation")).isZero();
    }

    @Test
    void theSameRelationTwiceIsAConflictAndRemovalIsAMarkThatClearsTheWarning() throws Exception {
        UUID id = register("إيجار", "finality", "FINAL");
        String body = relation("UPHELD", "relatedCitation", "الطعن ١٢");
        String added = read(mvc.perform(postJson(MANAGER, BASE + id + "/relations", body)).andExpect(status().isCreated())
                .andReturn());
        mvc.perform(postJson(MANAGER, BASE + id + "/relations", body)).andExpect(status().isConflict());
        String relationId = JsonPath.read(added, "$.outgoing[0].id");
        mvc.perform(deleteAs(MANAGER, "/api/v1/library/relations/" + relationId)).andExpect(status().isNoContent());
        mvc.perform(getAs(READER, BASE + id + "/validity")).andExpect(jsonPath("$.relationCount").value(0))
                .andExpect(jsonPath("$.warnings.length()").value(0));
        assertThat(count("select count(*) from library_judgment_relation where removed_at is not null")).isEqualTo(1);
        mvc.perform(deleteAs(MANAGER, "/api/v1/library/relations/" + relationId)).andExpect(status().isNotFound());
        assertThat(audited("LIBRARY_RELATION_REMOVE")).isEqualTo(1);
    }

    @Test
    void aWithdrawnJudgmentIsWarnedAboutAndReadersCannotRecordRelations() throws Exception {
        UUID id = register("إيجار", "finality", "FINAL");
        mvc.perform(postJson(MANAGER, "/api/v1/library/judgments/" + id + "/withdraw", json("reason", "خطأ", "version", 0)));
        mvc.perform(getAs(READER, BASE + id + "/validity")).andExpect(jsonPath("$.warnings[0]").value("WITHDRAWN"));
        mvc.perform(postJson(READER, BASE + id + "/relations", relation("LATER", "relatedCitation", "x")))
                .andExpect(status().isForbidden());
    }

    @Test
    void theLibraryNeverCallsAFewJudgmentsASettledTrend() throws Exception {
        UUID first = register("التقادم", "judgmentDate", "2020-01-01");
        addPrinciple(first, "التقادم يبدأ من تاريخ العلم", "legalTopic", "بدء التقادم");
        UUID second = register("تقادم آخر", "judgmentDate", "2021-01-01");
        addPrinciple(second, "المبدأ نفسه", "legalTopic", "بدء التقادم");
        mvc.perform(getAs(READER, "/api/v1/library/reports/summary"))
                .andExpect(jsonPath("$.frequentIssues[0].principleCount").value(2))
                .andExpect(jsonPath("$.frequentIssues[0].judgmentCount").value(2))
                .andExpect(jsonPath("$.frequentIssues[0].firstJudgmentDate").value("2020-01-01"))
                .andExpect(jsonPath("$.frequentIssues[0].lastJudgmentDate").value("2021-01-01"))
                .andExpect(jsonPath("$.frequentIssues[0].settled").doesNotExist())
                .andExpect(jsonPath("$.note").isNotEmpty());
    }
}
