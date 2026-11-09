package com.lawdesk.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/** LD-052, section 16.2 and acceptance scenario S4: a judgment in the library and a principle extracted from it, searchable. */
class LibraryPrincipleApiTest extends LibrarySupport {

    private static final String BASE = "/api/v1/library/judgments";

    @Test
    void s4_aJudgmentWithAnExtractedPrincipleIsFoundBySearchingThePrinciple() throws Exception {
        UUID judgment = register("دعوى تعويض عن إنهاء خدمة", "courtName", "محكمة الاستئناف", "caseNumber", "88",
                "caseYear", 2021, "judgmentDate", "2022-02-01", "reasons", "أسباب لا تذكر المبدأ المطلوب");
        UUID principle = addPrinciple(judgment, "الإنهاء بغير سبب مشروع يوجب التعويض عن الضرر المادي والأدبي",
                "legalTopic", "التعويض عن الفصل التعسفي", "legalBasis", "المادة ٤١ من قانون العمل",
                "factsApplied", "فصل موظف دون تحقيق", "scopeNote", "ينطبق على العقود غير محددة المدة",
                "memberNotes", "يراجع مع الحكم الأصلي");

        mvc.perform(getAs(READER, BASE + "/" + judgment + "/principles")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(principle.toString()))
                .andExpect(jsonPath("$[0].reviewStatus").value("DRAFT"))
                // The principle carries where it comes from, taken from the judgment and not copied.
                .andExpect(jsonPath("$[0].courtName").value("محكمة الاستئناف"))
                .andExpect(jsonPath("$[0].caseNumber").value("88"))
                .andExpect(jsonPath("$[0].judgmentDate").value("2022-02-01"));

        // Searching words that occur only in the principle finds the judgment, and says where the match was.
        mvc.perform(getAs(READER, BASE + "?q=الضرر الأدبي")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(judgment.toString()))
                .andExpect(jsonPath("$.items[0].matchedIn[0]").value("PRINCIPLE"))
                .andExpect(jsonPath("$.items[0].principleCount").value(1));
        // The legal basis and the topic are searchable too.
        assertThat(searchIds(READER, "q=الفصل التعسفي")).containsExactly(judgment.toString());
        assertThat(searchIds(READER, "q=المادة 41")).containsExactly(judgment.toString());
        // The detail shows the principle apart from the judgment text, with the judgment text still intact.
        mvc.perform(getAs(READER, BASE + "/" + judgment)).andExpect(jsonPath("$.principles.length()").value(1))
                .andExpect(jsonPath("$.reasons").value("أسباب لا تذكر المبدأ المطلوب"));
    }

    @Test
    void aJudgmentCarriesSeveralPrinciples() throws Exception {
        UUID judgment = register("عقد توريد");
        addPrinciple(judgment, "المبدأ الأول عن التسليم");
        addPrinciple(judgment, "المبدأ الثاني عن الغرامة");
        mvc.perform(getAs(READER, BASE + "/" + judgment + "/principles")).andExpect(jsonPath("$.length()").value(2));
        assertThat(searchIds(READER, "q=الغرامة")).containsExactly(judgment.toString());
        assertThat(searchIds(READER, "q=التسليم")).containsExactly(judgment.toString());
    }

    @Test
    void thePrincipleTextIsMandatoryAndTheJudgmentMustExist() throws Exception {
        UUID judgment = register("عقد");
        mvc.perform(postJson(MANAGER, BASE + "/" + judgment + "/principles", json("principleText", "  ")))
                .andExpect(status().isBadRequest());
        mvc.perform(postJson(MANAGER, BASE + "/" + UUID.randomUUID() + "/principles", json("principleText", "نص")))
                .andExpect(status().isNotFound());
        assertThat(count("select count(*) from library_principle")).isZero();
    }

    @Test
    void reviewingRecordsWhoAndWhenAndEditingThePrincipleNeedsANewReview() throws Exception {
        UUID judgment = register("عقد");
        UUID principle = addPrinciple(judgment, "المبدأ");
        mvc.perform(postJson(MANAGER, "/api/v1/library/principles/" + principle + "/review", json("version", 0)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reviewStatus").value("REVIEWED"))
                .andExpect(jsonPath("$.reviewedBy").value(MANAGER_ID.toString()))
                .andExpect(jsonPath("$.reviewedAt").exists()).andExpect(jsonPath("$.version").value(1));
        mvc.perform(putJson(MANAGER, "/api/v1/library/principles/" + principle,
                json("principleText", "المبدأ بعد التعديل", "version", 1))).andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("DRAFT")).andExpect(jsonPath("$.reviewedBy").doesNotExist())
                .andExpect(jsonPath("$.principleText").value("المبدأ بعد التعديل"));
        // A stale version is a conflict.
        mvc.perform(putJson(MANAGER, "/api/v1/library/principles/" + principle,
                json("principleText", "x", "version", 0))).andExpect(status().isConflict());
        assertThat(audited("LIBRARY_PRINCIPLE_CREATE", "LIBRARY_PRINCIPLE_UPDATE", "LIBRARY_PRINCIPLE_REVIEW")).isEqualTo(3);
    }

    @Test
    void readersSeePrinciplesButCannotChangeThem() throws Exception {
        UUID judgment = register("عقد");
        UUID principle = addPrinciple(judgment, "المبدأ");
        mvc.perform(postJson(READER, BASE + "/" + judgment + "/principles", json("principleText", "x")))
                .andExpect(status().isForbidden());
        mvc.perform(putJson(READER, "/api/v1/library/principles/" + principle, json("principleText", "x", "version", 0)))
                .andExpect(status().isForbidden());
        mvc.perform(postJson(READER, "/api/v1/library/principles/" + principle + "/review", json("version", 0)))
                .andExpect(status().isForbidden());
    }
}
