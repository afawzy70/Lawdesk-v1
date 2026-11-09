package com.lawdesk.library;

import static com.lawdesk.security.TestActors.scoped;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;
import com.lawdesk.security.Permission;

/**
 * LD-052, section 16.5 and acceptance scenario S5: a new case is offered similar judgments, by a fixed non-generative
 * comparison that explains each suggestion, and it is the member who decides to link one.
 */
class SimilarJudgmentApiTest extends LibrarySupport {

    private static final UUID OWNER = UUID.fromString("00000000-0000-4000-8000-0000000000b1");
    private final RequestPostProcessor owner = scoped(OWNER, "owner", List.of(), Permission.LIBRARY_VIEW,
            Permission.CASE_VIEW);

    private String similar(UUID caseId) throws Exception {
        return read(mvc.perform(getAs(owner, "/api/v1/cases/" + caseId + "/similar-judgments")).andExpect(status().isOk())
                .andReturn());
    }

    @Test
    void s5_aNewCaseIsOfferedTheMatchingJudgmentWithTheReasonsWhy() throws Exception {
        UUID topic = classification("LEGAL_TOPIC", "TERMINATION", "إنهاء الخدمة");
        UUID match = register("التعويض عن إنهاء خدمة موظف بغير سبب مشروع", "caseType", "LABOR", "courtLevel", "CASSATION",
                "keywords", List.of("فصل تعسفي"), "classificationIds", List.of(topic.toString()),
                "judgmentDate", "2021-06-01", "caseNumber", "10", "caseYear", 2020, "copyReference", "الأرشيف ١٢");
        UUID unrelated = register("إيجار محل تجاري وإخلاء العين المؤجرة", "caseType", "COMMERCIAL");
        addPrinciple(match, "الإنهاء دون سبب مشروع يوجب التعويض", "legalTopic", "الفصل التعسفي");
        UUID caseId = caseOf(OWNER, "مطالبة بتعويض", "تعويض عن إنهاء خدمة", "تم فصل الموظف بغير سبب مشروع وهو يطلب التعويض عن فصل تعسفي إنهاء الخدمة", "LABOR");

        String body = similar(caseId);
        List<String> ids = JsonPath.read(body, "$.items[*].judgment.id");
        assertThat(ids).containsExactly(match.toString());
        assertThat(ids).doesNotContain(unrelated.toString());
        List<String> codes = JsonPath.read(body, "$.items[0].reasons[*].code");
        assertThat(codes).contains("TEXT_MATCH", "SAME_CASE_TYPE", "KEYWORD_MATCH", "SHARED_CLASSIFICATION",
                "PRINCIPLE_MATCH", "SUBJECT_SIMILARITY");
        // Each reason says which words or values it rests on and how many points it gave; the total is their sum.
        List<Integer> points = JsonPath.read(body, "$.items[0].reasons[*].points");
        assertThat(points.stream().mapToInt(Integer::intValue).sum()).isEqualTo((int) JsonPath.read(body, "$.items[0].score"));
        List<String> termsOfText = JsonPath.read(body, "$.items[0].reasons[?(@.code=='TEXT_MATCH')].terms[*]");
        assertThat(termsOfText).contains("تعويض", "فصل");
        // The citation: source, date, number and whether a copy is saved.
        assertThat((String) JsonPath.read(body, "$.items[0].judgment.sourceReference")).isNotBlank();
        assertThat((String) JsonPath.read(body, "$.items[0].judgment.judgmentDate")).isEqualTo("2021-06-01");
        assertThat((String) JsonPath.read(body, "$.items[0].judgment.caseNumber")).isEqualTo("10");
        assertThat((Boolean) JsonPath.read(body, "$.items[0].judgment.hasCopy")).isTrue();
        assertThat((Boolean) JsonPath.read(body, "$.basis.defensesIncluded")).isFalse();
        assertThat((String) JsonPath.read(body, "$.note")).isNotBlank();
    }

    @Test
    void theSameCaseAndLibraryAlwaysGiveTheSameAnswerInTheSameOrder() throws Exception {
        for (int i = 0; i < 4; i++) {
            register("التعويض عن الضرر المادي رقم " + i, "caseType", "CIVIL", "judgmentDate", "2020-0" + (i + 1) + "-01");
        }
        UUID caseId = caseOf(OWNER, "ضرر", "التعويض عن الضرر المادي", null, "CIVIL");
        String first = similar(caseId);
        assertThat(similar(caseId)).isEqualTo(first);
        // Equal scores are ordered by the newest judgment first.
        List<String> dates = JsonPath.read(first, "$.items[*].judgment.judgmentDate");
        assertThat(dates).isSortedAccordingTo(java.util.Comparator.reverseOrder());
    }

    @Test
    void aTypeAloneIsNeverEnoughAndADifferentTypeOrLevelIsShownAsADifference() throws Exception {
        register("موضوع بعيد تماما عن كل ما سبق", "caseType", "LABOR");
        UUID shared = register("التعويض عن الضرر", "caseType", "CIVIL", "courtLevel", "APPEAL");
        UUID caseId = caseOf(OWNER, "قضية", "التعويض عن الضرر", null, "LABOR");
        String body = similar(caseId);
        assertThat((List<?>) JsonPath.read(body, "$.items")).hasSize(1);
        assertThat((String) JsonPath.read(body, "$.items[0].judgment.id")).isEqualTo(shared.toString());
        List<String> differences = JsonPath.read(body, "$.items[0].differences[*].code");
        assertThat(differences).contains("DIFFERENT_CASE_TYPE");
        List<String> codes = JsonPath.read(body, "$.items[0].reasons[*].code");
        assertThat(codes).doesNotContain("SAME_CASE_TYPE");
    }

    @Test
    void courtLevelsOfTheCasesProceedingsAreComparedWithTheJudgment() throws Exception {
        register("التعويض عن الضرر", "courtLevel", "APPEAL");
        register("التعويض عن الضرر الأدبي", "courtLevel", "CASSATION");
        UUID caseId = caseOf(OWNER, "قضية", "التعويض عن الضرر", null, "CIVIL");
        UUID court = jdbc.queryForObject("""
                insert into court (code, name_ar, name_en, level, created_by, updated_by)
                values ('SIM-AP', 'محكمة استئناف', 'Appeal', 'APPEAL', gen_random_uuid(), gen_random_uuid()) returning id""",
                UUID.class);
        UUID first = jdbc.queryForObject("""
                insert into court (code, name_ar, name_en, level, created_by, updated_by)
                values ('SIM-FI', 'محكمة ابتدائية', 'First', 'FIRST_INSTANCE', gen_random_uuid(), gen_random_uuid()) returning id""",
                UUID.class);
        UUID root = jdbc.queryForObject("""
                insert into proceeding (case_id, stage, court_id, court_case_number, court_case_year, filing_date, created_by, updated_by)
                values (?, 'FIRST_INSTANCE', ?, '1', 2026, '2026-01-12', gen_random_uuid(), gen_random_uuid()) returning id""",
                UUID.class, caseId, first);
        jdbc.update("""
                insert into proceeding (case_id, stage, court_id, court_case_number, court_case_year, filing_date,
                    parent_proceeding_id, created_by, updated_by)
                values (?, 'APPEAL', ?, '2', 2026, '2026-03-12', ?, gen_random_uuid(), gen_random_uuid())""", caseId, court, root);
        try {
            String body = similar(caseId);
            List<String> levelReasons = JsonPath.read(body, "$.items[?(@.judgment.courtLevel=='APPEAL')].reasons[*].code");
            assertThat(levelReasons).contains("SAME_COURT_LEVEL");
            List<String> differences = JsonPath.read(body, "$.items[?(@.judgment.courtLevel=='CASSATION')].differences[*].code");
            assertThat(differences).contains("DIFFERENT_COURT_LEVEL");
        } finally {
            jdbc.update("delete from proceeding where case_id = ? and stage = 'APPEAL'", caseId);
            jdbc.update("delete from proceeding where case_id = ?", caseId);
            jdbc.update("delete from court where code in ('SIM-AP', 'SIM-FI')");
        }
    }

    @Test
    void withdrawnJudgmentsAreNotSuggestedAndRelationsAndFinalityAreWarnedAbout() throws Exception {
        UUID withdrawn = register("التعويض عن الضرر المادي");
        mvc.perform(postJson(MANAGER, "/api/v1/library/judgments/" + withdrawn + "/withdraw",
                json("reason", "خطأ", "version", 0)));
        UUID overturned = register("التعويض عن الضرر الأدبي", "finality", "FINAL");
        mvc.perform(postJson(MANAGER, "/api/v1/library/judgments/" + overturned + "/relations",
                json("relationType", "OVERTURNED", "relatedCitation", "الطعن ٥", "sourceReference", "المصدر")));
        UUID caseId = caseOf(OWNER, "قضية", "التعويض عن الضرر", null, "CIVIL");
        String body = similar(caseId);
        List<String> ids = JsonPath.read(body, "$.items[*].judgment.id");
        assertThat(ids).containsExactly(overturned.toString());
        List<String> warnings = JsonPath.read(body, "$.items[0].warnings");
        assertThat(warnings).contains("LATER_RELATION_EXISTS");
        List<String> relationTypes = JsonPath.read(body, "$.items[0].relationTypes");
        assertThat(relationTypes).containsExactly("OVERTURNED");
    }

    @Test
    void aSuggestionIsNotALinkUntilTheMemberLinksItAndThenItIsMarkedLinked() throws Exception {
        UUID match = register("التعويض عن الضرر");
        UUID caseId = caseOf(OWNER, "قضية", "التعويض عن الضرر", null, "CIVIL");
        assertThat(count("select count(*) from case_precedent_link")).isZero();
        assertThat((Boolean) JsonPath.read(similar(caseId), "$.items[0].alreadyLinked")).isFalse();
        mvc.perform(postJson(owner, "/api/v1/cases/" + caseId + "/precedents",
                json("judgmentId", match.toString(), "reason", "سبب", "origin", "SUGGESTION"))).andExpect(status().isCreated());
        assertThat((Boolean) JsonPath.read(similar(caseId), "$.items[0].alreadyLinked")).isTrue();
    }

    @Test
    void aCaseWithNothingToCompareOrAnEmptyLibraryGetsAnEmptyListAndLimitsAreChecked() throws Exception {
        UUID caseId = caseOf(OWNER, "x", "x", null, "CIVIL");
        mvc.perform(getAs(owner, "/api/v1/cases/" + caseId + "/similar-judgments")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(getAs(owner, "/api/v1/cases/" + caseId + "/similar-judgments?limit=0")).andExpect(status().isBadRequest());
        mvc.perform(getAs(owner, "/api/v1/cases/" + caseId + "/similar-judgments?limit=26")).andExpect(status().isBadRequest());
        mvc.perform(getAs(owner, "/api/v1/cases/" + UUID.randomUUID() + "/similar-judgments")).andExpect(status().isNotFound());
    }
}
