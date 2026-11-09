package com.lawdesk.pleadings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;
import com.lawdesk.security.Permission;

/**
 * Requirement section 17 and scenarios S3, S13: the defences of a case, whether each was raised in each stage, and the
 * court's position on each in each judgment; the defence library never copies anything by itself.
 */
class DefenseApiTest extends PleadingsTestSupport {

    private String defenses(UUID caseId) {
        return "/api/v1/cases/" + caseId + "/defenses";
    }

    private String positions(UUID judgment) {
        return "/api/v1/judgments/" + judgment + "/defense-positions";
    }

    private String positionBody(UUID defense, String position, String reasons) {
        return "{\"positions\":[" + json("defenseId", defense, "position", position, "reasons", reasons, "operative", "op") + "]}";
    }

    @Test
    void recordsNumberedDefencesWithTheirMemoAndTheStageTheyWereRaisedIn() throws Exception {
        UUID member = user("Member who prepared it");
        UUID caseId = newCase();
        UUID stage = firstInstance(caseId);
        UUID memo = document(caseId, "Defence memo");

        post(everything(), defenses(caseId), json("title", "  Inadmissible: filed late  ", "kind", "FORMAL", "argument",
                "The claim was filed after the time limit", "legalBasis", "a cited article", "facts", "notified on X",
                "legalTexts", "the text relied on", "memoDocumentId", memo, "raisedOn", "2026-03-30", "raisedStageId", stage,
                "preparedBy", member, "notes", "n"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.seq").value(1))
                .andExpect(jsonPath("$.title").value("Inadmissible: filed late"))
                .andExpect(jsonPath("$.kind").value("FORMAL"))
                .andExpect(jsonPath("$.memoDocumentTitle").value("Defence memo"))
                .andExpect(jsonPath("$.preparedByName").value("Member who prepared it"))
                .andExpect(jsonPath("$.raisedStageId").value(stage.toString()));
        post(everything(), defenses(caseId), json("title", "Merits", "kind", "SUBSTANTIVE")).andExpect(jsonPath("$.seq").value(2));

        get(everything(), defenses(caseId)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].seq").value(Matchers.contains(1, 2)));
        // The stage it was raised in is recorded as its first assertion.
        get(everything(), "/api/v1/cases/" + caseId + "/defense-assertions").andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("RAISED")).andExpect(jsonPath("$[0].stage").value("FIRST_INSTANCE"))
                .andExpect(jsonPath("$[0].onDate").value("2026-03-30"));
        assertThat(count("select count(*) from case_event where case_id = ? and type = 'DEFENSE_ADDED'", caseId)).isEqualTo(2);
    }

    @Test
    void refusesBadInputAndRecordsOutsideTheCase() throws Exception {
        UUID caseId = newCase();
        UUID elsewhere = newCase();
        post(everything(), defenses(caseId), json("title", " ", "kind", "FORMAL")).andExpect(status().isBadRequest());
        post(everything(), defenses(caseId), json("title", "x", "kind", "BOGUS")).andExpect(status().isBadRequest());
        post(everything(), defenses(caseId), json("kind", "FORMAL")).andExpect(status().isBadRequest());
        post(everything(), defenses(caseId), json("title", "x", "kind", "OTHER", "memoDocumentId", document(elsewhere, "d")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("memoDocumentId"));
        post(everything(), defenses(caseId), json("title", "x", "kind", "OTHER", "raisedStageId", firstInstance(elsewhere)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("raisedStageId"));
        post(everything(), defenses(caseId), json("title", "x", "kind", "OTHER", "preparedBy", UUID.randomUUID()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("preparedBy"));
        assertThat(count("select count(*) from defense")).isZero();
    }

    @Test
    void anUpdateNeedsTheCurrentVersion() throws Exception {
        UUID caseId = newCase();
        UUID id = created(everything(), defenses(caseId), json("title", "First", "kind", "OTHER"));
        put(everything(), "/api/v1/defenses/" + id, json("title", "Second", "kind", "PROCEDURAL", "argument", "a", "version", 0))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Second"))
                .andExpect(jsonPath("$.kind").value("PROCEDURAL")).andExpect(jsonPath("$.version").value(1));
        put(everything(), "/api/v1/defenses/" + id, json("title", "Third", "kind", "OTHER", "version", 0))
                .andExpect(status().isConflict());
        get(everything(), "/api/v1/defenses/" + id).andExpect(jsonPath("$.defense.title").value("Second"));
    }

    // S13: the defence is followed through the stages, each with its own history
    @Test
    void theDefenceIsFollowedThroughTheStages() throws Exception {
        UUID caseId = newCase();
        UUID first = firstInstance(caseId);
        UUID appeal = proceeding(caseId, "APPEAL", first, "2026-04-01");
        UUID cassation = proceeding(caseId, "CASSATION", appeal, "2026-07-01");
        UUID defense = created(everything(), defenses(caseId), json("title", "d", "kind", "FORMAL", "raisedStageId", first,
                "raisedOn", "2026-02-01"));
        UUID memo = document(caseId, "Appeal memo");

        put(everything(), "/api/v1/defenses/" + defense + "/assertions/" + appeal, json("status", "RE_ASSERTED", "onDate",
                "2026-04-10", "memoDocumentId", memo)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RE_ASSERTED")).andExpect(jsonPath("$.stage").value("APPEAL"));
        put(everything(), "/api/v1/defenses/" + defense + "/assertions/" + cassation, json("status", "NOT_ASSERTED"))
                .andExpect(status().isOk());
        // Recording the same stage again replaces that stage's row only.
        put(everything(), "/api/v1/defenses/" + defense + "/assertions/" + appeal, json("status", "RAISED", "onDate", "2026-04-11"))
                .andExpect(status().isOk());

        get(everything(), "/api/v1/cases/" + caseId + "/defense-assertions").andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].stage").value("FIRST_INSTANCE")).andExpect(jsonPath("$[0].status").value("RAISED"))
                .andExpect(jsonPath("$[1].stage").value("APPEAL")).andExpect(jsonPath("$[1].onDate").value("2026-04-11"))
                .andExpect(jsonPath("$[2].stage").value("CASSATION")).andExpect(jsonPath("$[2].status").value("NOT_ASSERTED"));
        put(everything(), "/api/v1/defenses/" + defense + "/assertions/" + firstInstance(newCase()), json("status", "RAISED"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("proceedingId"));
        put(everything(), "/api/v1/defenses/" + defense + "/assertions/" + appeal, json("status", "MAYBE"))
                .andExpect(status().isBadRequest());
    }

    // S3 and S13
    @Test
    void theCourtsPositionIsRecordedPerJudgmentAndNeverReplaced() throws Exception {
        UUID caseId = newCase();
        UUID first = firstInstance(caseId);
        UUID appeal = proceeding(caseId, "APPEAL", first, "2026-04-01");
        UUID memo = document(caseId, "Memo");
        UUID d1 = created(everything(), defenses(caseId), json("title", "Inadmissible", "kind", "FORMAL", "memoDocumentId", memo));
        UUID d2 = created(everything(), defenses(caseId), json("title", "Merits", "kind", "SUBSTANTIVE"));
        UUID firstJudgment = judgment(first, "2026-03-01");
        UUID appealJudgment = judgment(appeal, "2026-06-01");

        get(everything(), positions(firstJudgment)).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].position").value(Matchers.everyItem(Matchers.nullValue())));
        put(everything(), positions(firstJudgment), positionBody(d1, "REJECTED", "reasons one")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].position").value("REJECTED")).andExpect(jsonPath("$[0].reasons").value("reasons one"))
                .andExpect(jsonPath("$[1].position").doesNotExist());
        put(everything(), positions(appealJudgment), positionBody(d1, "ACCEPTED", "reasons two")).andExpect(status().isOk());

        get(everything(), positions(firstJudgment)).andExpect(jsonPath("$[0].position").value("REJECTED"));
        get(everything(), "/api/v1/cases/" + caseId + "/defense-positions").andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].stage").value("FIRST_INSTANCE")).andExpect(jsonPath("$[0].position").value("REJECTED"))
                .andExpect(jsonPath("$[1].stage").value("APPEAL")).andExpect(jsonPath("$[1].position").value("ACCEPTED"));
        // The defence shows its memo, its positions and the stage of each.
        get(everything(), "/api/v1/defenses/" + d1).andExpect(status().isOk())
                .andExpect(jsonPath("$.defense.memoDocumentId").value(memo.toString()))
                .andExpect(jsonPath("$.positions.length()").value(2))
                .andExpect(jsonPath("$.positions[1].stage").value("APPEAL"))
                .andExpect(jsonPath("$.positions[1].judgmentDate").value("2026-06-01"));
        for (String position : new String[] {"ACCEPTED", "REJECTED", "PARTIAL", "NOT_ADDRESSED", "MOOT", "DEFERRED", "OTHER"}) {
            put(everything(), positions(firstJudgment), positionBody(d2, position, null)).andExpect(status().isOk());
        }
        put(everything(), positions(firstJudgment), positionBody(d2, "WON", null)).andExpect(status().isBadRequest());
        put(everything(), positions(firstJudgment), positionBody(created(everything(), defenses(newCase()), json("title", "x", "kind", "OTHER")),
                "ACCEPTED", null)).andExpect(status().isBadRequest());
        assertThat(count("select count(*) from defense_position")).isEqualTo(3);
    }

    @Test
    void whoMayReadAndWriteDefences() throws Exception {
        UUID specialist = user("Specialist");
        UUID caseId = newCase(specialist, "CIVIL");
        UUID judgment = judgment(firstInstance(caseId), "2026-03-01");
        UUID defense = created(member(specialist), defenses(caseId), json("title", "Mine", "kind", "OTHER"));
        UUID stranger = UUID.randomUUID();

        for (String path : new String[] {defenses(caseId), "/api/v1/defenses/" + defense, positions(judgment),
            "/api/v1/cases/" + caseId + "/defense-assertions", "/api/v1/cases/" + caseId + "/defense-positions"}) {
            get(outsider(stranger), path).andExpect(status().isNotFound());
            get(viewerOf(stranger), path).andExpect(status().isOk());
        }
        post(outsider(stranger), defenses(caseId), json("title", "x", "kind", "OTHER")).andExpect(status().isNotFound());
        post(viewerOf(stranger), defenses(caseId), json("title", "x", "kind", "OTHER")).andExpect(status().isForbidden());
        put(viewerOf(stranger), "/api/v1/defenses/" + defense, json("title", "x", "kind", "OTHER", "version", 0))
                .andExpect(status().isForbidden());
        put(outsider(stranger), "/api/v1/defenses/" + defense, json("title", "x", "kind", "OTHER", "version", 0))
                .andExpect(status().isNotFound());
        put(viewerOf(stranger), positions(judgment), positionBody(defense, "ACCEPTED", null)).andExpect(status().isForbidden());
        put(outsider(stranger), positions(judgment), positionBody(defense, "ACCEPTED", null)).andExpect(status().isNotFound());
        get(with(Permission.CASE_VIEW), defenses(caseId)).andExpect(status().isForbidden());
        post(with(Permission.DEFENSE_VIEW), defenses(caseId), json("title", "x", "kind", "OTHER")).andExpect(status().isForbidden());
        get(everything(), defenses(UUID.randomUUID())).andExpect(status().isNotFound());
        get(everything(), "/api/v1/defenses/" + UUID.randomUUID()).andExpect(status().isNotFound());
        assertThat(count("select count(*) from defense")).isEqualTo(1);
    }

    @Test
    void theLibraryHoldsWordingButNeverCopiesItIntoACase() throws Exception {
        UUID template = created(everything(), "/api/v1/defense-templates", json("title", "Late filing", "kind", "FORMAL", "text",
                "standard wording", "legalBasis", "a basis"));
        get(everything(), "/api/v1/defense-templates").andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("DRAFT")).andExpect(jsonPath("$[0].reviewedBy").doesNotExist());
        put(everything(), "/api/v1/defense-templates/" + template, json("title", "Late filing", "kind", "FORMAL", "text",
                "standard wording", "status", "REVIEWED", "version", 0)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVIEWED")).andExpect(jsonPath("$.reviewedBy").value(USER.toString()));
        get(everything(), "/api/v1/defense-templates?q=late").andExpect(jsonPath("$.length()").value(1));
        get(everything(), "/api/v1/defense-templates?q=nothing-like-it").andExpect(jsonPath("$.length()").value(0));
        put(everything(), "/api/v1/defense-templates/" + template, json("title", "x", "kind", "FORMAL", "status", "DRAFT",
                "version", 0)).andExpect(status().isConflict());

        // A defence built from the template records where the wording came from, and copies none of it.
        UUID caseId = newCase();
        post(everything(), defenses(caseId), json("title", "Late filing", "kind", "FORMAL", "templateId", template))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.templateId").value(template.toString()))
                .andExpect(jsonPath("$.argument").doesNotExist()).andExpect(jsonPath("$.legalBasis").doesNotExist());
        post(everything(), defenses(caseId), json("title", "x", "kind", "FORMAL", "templateId", UUID.randomUUID()))
                .andExpect(status().isBadRequest());
        post(with(Permission.DEFENSE_VIEW), "/api/v1/defense-templates", json("title", "x", "kind", "FORMAL"))
                .andExpect(status().isForbidden());
        get(with(Permission.CASE_VIEW), "/api/v1/defense-templates").andExpect(status().isForbidden());
        String id = JsonPath.read(body(get(everything(), "/api/v1/defense-templates")), "$[0].id");
        assertThat(id).isEqualTo(template.toString());
    }
}
