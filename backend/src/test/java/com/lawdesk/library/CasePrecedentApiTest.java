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
 * LD-052, section 16.4 and owner decision D3: a library judgment is linked to cases by the member who works on them, and
 * what a user sees of those links never goes beyond the cases they may see.
 */
class CasePrecedentApiTest extends LibrarySupport {

    private static final UUID OWNER = UUID.fromString("00000000-0000-4000-8000-0000000000b1");
    private static final UUID VIEWER = UUID.fromString("00000000-0000-4000-8000-0000000000b2");
    private static final UUID OUTSIDER = UUID.fromString("00000000-0000-4000-8000-0000000000b3");

    /** Creator of the cases: sees and changes them. */
    private final RequestPostProcessor owner = scoped(OWNER, "owner", List.of(), Permission.LIBRARY_VIEW,
            Permission.CASE_VIEW);
    /** Department-wide viewer without the right to change: sees every non-restricted case. */
    private final RequestPostProcessor viewer = scoped(VIEWER, "viewer", List.of("ALL"), Permission.LIBRARY_VIEW,
            Permission.CASE_VIEW);
    /** Holds the library permission and nothing else: sees no case. */
    private final RequestPostProcessor outsider = scoped(OUTSIDER, "outsider", List.of(), Permission.LIBRARY_VIEW);

    private String link(UUID judgment, String reason) {
        return json("judgmentId", judgment.toString(), "reason", reason);
    }

    @Test
    void aMemberLinksAJudgmentToHerCaseWithTheReasonAndItShowsOnTheCase() throws Exception {
        UUID judgment = register("تعويض", "caseNumber", "9", "caseYear", 2020);
        UUID caseId = caseOf(OWNER, "قضية", "تعويض", null, "CIVIL");
        mvc.perform(postJson(owner, "/api/v1/cases/" + caseId + "/precedents",
                json("judgmentId", judgment.toString(), "reason", "نفس مسألة التقادم", "similarityNote", "الوقائع متقاربة",
                        "origin", "SUGGESTION"))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.reason").value("نفس مسألة التقادم"))
                .andExpect(jsonPath("$.origin").value("SUGGESTION"))
                .andExpect(jsonPath("$.judgment.caseNumber").value("9"))
                .andExpect(jsonPath("$.judgment.sourceReference").isNotEmpty())
                .andExpect(jsonPath("$.createdBy").value(OWNER.toString()));
        mvc.perform(getAs(owner, "/api/v1/cases/" + caseId + "/precedents")).andExpect(jsonPath("$.length()").value(1));
        assertThat(count("select count(*) from case_event where case_id = ? and type = 'PRECEDENT_LINKED'", caseId))
                .isEqualTo(1);
        assertThat(audited("CASE_PRECEDENT_LINK")).isEqualTo(1);
    }

    @Test
    void theRelationIsManyToManyAndTheSameLinkTwiceIsAConflict() throws Exception {
        UUID a = register("أ");
        UUID b = register("ب");
        UUID case1 = caseOf(OWNER, "قضية ١", "x", null, "CIVIL");
        UUID case2 = caseOf(OWNER, "قضية ٢", "x", null, "CIVIL");
        for (UUID c : List.of(case1, case2)) {
            for (UUID j : List.of(a, b)) {
                mvc.perform(postJson(owner, "/api/v1/cases/" + c + "/precedents", link(j, "سبب"))).andExpect(status().isCreated());
            }
        }
        mvc.perform(postJson(owner, "/api/v1/cases/" + case1 + "/precedents", link(a, "مرة أخرى")))
                .andExpect(status().isConflict());
        mvc.perform(getAs(owner, "/api/v1/cases/" + case1 + "/precedents")).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(getAs(owner, "/api/v1/library/judgments/" + a)).andExpect(jsonPath("$.linkedCases.length()").value(2));
    }

    @Test
    void unlinkingIsAMarkAndTheJudgmentCanBeLinkedAgain() throws Exception {
        UUID judgment = register("أ");
        UUID caseId = caseOf(OWNER, "قضية", "x", null, "CIVIL");
        String created = read(mvc.perform(postJson(owner, "/api/v1/cases/" + caseId + "/precedents", link(judgment, "سبب")))
                .andReturn());
        String linkId = JsonPath.read(created, "$.id");
        mvc.perform(deleteAs(owner, "/api/v1/cases/" + caseId + "/precedents/" + linkId)).andExpect(status().isNoContent());
        mvc.perform(getAs(owner, "/api/v1/cases/" + caseId + "/precedents")).andExpect(jsonPath("$.length()").value(0));
        assertThat(count("select count(*) from case_precedent_link where removed_at is not null")).isEqualTo(1);
        mvc.perform(deleteAs(owner, "/api/v1/cases/" + caseId + "/precedents/" + linkId)).andExpect(status().isNotFound());
        mvc.perform(postJson(owner, "/api/v1/cases/" + caseId + "/precedents", link(judgment, "من جديد")))
                .andExpect(status().isCreated());
        assertThat(count("select count(*) from case_event where case_id = ? and type = 'PRECEDENT_UNLINKED'", caseId))
                .isEqualTo(1);
    }

    @Test
    void aReasonIsMandatoryAndAWithdrawnOrUnknownJudgmentCannotBeLinked() throws Exception {
        UUID judgment = register("أ");
        UUID caseId = caseOf(OWNER, "قضية", "x", null, "CIVIL");
        mvc.perform(postJson(owner, "/api/v1/cases/" + caseId + "/precedents", link(judgment, "  ")))
                .andExpect(status().isBadRequest());
        mvc.perform(postJson(owner, "/api/v1/cases/" + caseId + "/precedents", link(UUID.randomUUID(), "سبب")))
                .andExpect(status().isNotFound());
        mvc.perform(postJson(MANAGER, "/api/v1/library/judgments/" + judgment + "/withdraw",
                json("reason", "خطأ", "version", 0)));
        mvc.perform(postJson(owner, "/api/v1/cases/" + caseId + "/precedents", link(judgment, "سبب")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("LIBRARY_JUDGMENT_WITHDRAWN"));
    }

    @Test
    void aUserWhoCannotSeeTheCaseGetsNotFoundOnEveryCaseRoute() throws Exception {
        UUID judgment = register("أ");
        UUID caseId = caseOf(OWNER, "قضية", "x", null, "CIVIL");
        String created = read(mvc.perform(postJson(owner, "/api/v1/cases/" + caseId + "/precedents", link(judgment, "سبب")))
                .andReturn());
        String linkId = JsonPath.read(created, "$.id");
        mvc.perform(getAs(outsider, "/api/v1/cases/" + caseId + "/precedents")).andExpect(status().isNotFound());
        mvc.perform(getAs(outsider, "/api/v1/cases/" + caseId + "/similar-judgments")).andExpect(status().isNotFound());
        mvc.perform(postJson(outsider, "/api/v1/cases/" + caseId + "/precedents", link(judgment, "سبب")))
                .andExpect(status().isNotFound());
        mvc.perform(deleteAs(outsider, "/api/v1/cases/" + caseId + "/precedents/" + linkId))
                .andExpect(status().isNotFound());
        assertThat(count("select count(*) from case_precedent_link where removed_at is null")).isEqualTo(1);
    }

    @Test
    void aUserWhoSeesButCannotChangeTheCaseReadsTheLinksAndIsRefusedTheChange() throws Exception {
        UUID judgment = register("أ");
        UUID other = register("ب");
        UUID caseId = caseOf(OWNER, "قضية", "x", null, "CIVIL");
        String created = read(mvc.perform(postJson(owner, "/api/v1/cases/" + caseId + "/precedents", link(judgment, "سبب")))
                .andReturn());
        String linkId = JsonPath.read(created, "$.id");
        mvc.perform(getAs(viewer, "/api/v1/cases/" + caseId + "/precedents")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(getAs(viewer, "/api/v1/cases/" + caseId + "/similar-judgments")).andExpect(status().isOk());
        mvc.perform(postJson(viewer, "/api/v1/cases/" + caseId + "/precedents", link(other, "سبب")))
                .andExpect(status().isForbidden());
        mvc.perform(deleteAs(viewer, "/api/v1/cases/" + caseId + "/precedents/" + linkId))
                .andExpect(status().isForbidden());
        assertThat(count("select count(*) from case_precedent_link")).isEqualTo(1);
    }

    @Test
    void theLibraryShowsAJudgmentsLinkedCasesOnlyForCasesTheUserMaySee() throws Exception {
        UUID judgment = register("أ");
        UUID mine = caseOf(OWNER, "قضيتي", "x", null, "CIVIL");
        UUID restricted = caseOf(UUID.randomUUID(), "قضية سرية", "x", null, "CIVIL");
        jdbc.update("update legal_case set confidentiality = 'RESTRICTED' where id = ?", restricted);
        jdbc.update("insert into case_precedent_link (case_id, library_judgment_id, reason, created_by) values (?, ?, 'a', ?),"
                + " (?, ?, 'b', ?)", mine, judgment, OWNER, restricted, judgment, OWNER);
        // The creator of one case sees only that one; the department-wide viewer cannot see the RESTRICTED case either.
        mvc.perform(getAs(owner, "/api/v1/library/judgments/" + judgment)).andExpect(jsonPath("$.linkedCases.length()").value(1))
                .andExpect(jsonPath("$.linkedCases[0].caseId").value(mine.toString()));
        mvc.perform(getAs(viewer, "/api/v1/library/judgments/" + judgment))
                .andExpect(jsonPath("$.linkedCases.length()").value(1))
                .andExpect(jsonPath("$.linkedCases[0].caseId").value(mine.toString()));
        mvc.perform(getAs(outsider, "/api/v1/library/judgments/" + judgment)).andExpect(status().isOk())
                .andExpect(jsonPath("$.linkedCases.length()").value(0));
        // The library reader without a case scope is told nothing about links or titles of cases.
        assertThat(read(mvc.perform(getAs(outsider, "/api/v1/library/judgments/" + judgment)).andReturn()))
                .doesNotContain("قضيتي").doesNotContain("قضية سرية");
    }
}
