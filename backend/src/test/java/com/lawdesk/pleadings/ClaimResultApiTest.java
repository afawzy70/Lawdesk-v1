package com.lawdesk.pleadings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import com.lawdesk.security.Permission;

/**
 * Requirements sections 5 and 9, scenarios S1 and S13: the court's result on each claim, recorded per judgment; a later
 * judgment (another stage) has results of its own and never replaces an earlier one.
 */
class ClaimResultApiTest extends PleadingsTestSupport {

    private String results(UUID judgment) {
        return "/api/v1/judgments/" + judgment + "/claim-results";
    }

    private String entry(UUID claim, String result, String amount, String reasons) {
        return json("claimId", claim, "result", result, "awardedAmount", amount, "operative", "operative of " + result,
                "reasons", reasons, "notes", "n");
    }

    private String entries(String... entries) {
        return "{\"results\":[" + String.join(",", entries) + "]}";
    }

    // S1
    @Test
    void aJudgmentGivesEachOfThreeClaimsItsOwnResult() throws Exception {
        UUID caseId = newCase();
        UUID stage = firstInstance(caseId);
        UUID c1 = claim(caseId, "Compensation");
        UUID c2 = claim(caseId, "Refund");
        UUID c3 = claim(caseId, "Costs");
        UUID judgment = judgment(stage, "2026-09-28");

        // Before anything is recorded every claim is listed without a result: "detailed results not recorded".
        get(everything(), results(judgment)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[*].claimSeq").value(Matchers.contains(1, 2, 3)))
                .andExpect(jsonPath("$[*].result").value(Matchers.everyItem(Matchers.nullValue())));

        put(everything(), results(judgment), entries(entry(c1, "ACCEPTED", "1500.250", "r1"), entry(c2, "REJECTED", null, "r2"),
                entry(c3, "PARTIAL", "200", "r3"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].result").value("ACCEPTED"))
                .andExpect(jsonPath("$[0].awardedAmount").value("1500.250"))
                .andExpect(jsonPath("$[0].operative").value("operative of ACCEPTED"))
                .andExpect(jsonPath("$[0].reasons").value("r1"))
                .andExpect(jsonPath("$[1].result").value("REJECTED"))
                .andExpect(jsonPath("$[1].awardedAmount").doesNotExist())
                .andExpect(jsonPath("$[2].result").value("PARTIAL"))
                .andExpect(jsonPath("$[2].awardedAmount").value("200.000"));
        assertThat(count("select count(*) from claim_result where judgment_id = ?", judgment)).isEqualTo(3);
        assertThat(count("select count(*) from case_event where case_id = ? and type = 'CLAIM_RESULTS_RECORDED'", caseId))
                .isEqualTo(1);
    }

    @Test
    void takesAllTwelveResultsAndRefusesOthers() throws Exception {
        UUID caseId = newCase();
        UUID judgment = judgment(firstInstance(caseId), "2026-09-28");
        UUID claim = claim(caseId, "x");
        for (String result : new String[] {"ACCEPTED", "PARTIAL", "REJECTED", "INADMISSIBLE", "NO_JURISDICTION", "REFERRED",
            "EXTINGUISHED", "WAIVER_RECORDED", "ABANDONMENT_RECORDED", "NOT_DECIDED", "DEFERRED", "OTHER"}) {
            put(everything(), results(judgment), entries(entry(claim, result, null, null))).andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].result").value(result));
        }
        put(everything(), results(judgment), entries(entry(claim, "WON", null, null))).andExpect(status().isBadRequest());
        put(everything(), results(judgment), entries(entry(claim, "ACCEPTED", "-5", null))).andExpect(status().isBadRequest());
        put(everything(), results(judgment), entries(entry(claim, "ACCEPTED", "1.2345", null))).andExpect(status().isBadRequest());
        assertThat(count("select count(*) from claim_result")).isEqualTo(1);
    }

    @Test
    void recordingAgainChangesOnlyTheNamedClaims() throws Exception {
        UUID caseId = newCase();
        UUID judgment = judgment(firstInstance(caseId), "2026-09-28");
        UUID c1 = claim(caseId, "a");
        UUID c2 = claim(caseId, "b");
        put(everything(), results(judgment), entries(entry(c1, "ACCEPTED", null, null), entry(c2, "REJECTED", null, null)))
                .andExpect(status().isOk());
        put(everything(), results(judgment), entries(entry(c2, "DEFERRED", null, "later"))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].result").value("ACCEPTED")).andExpect(jsonPath("$[1].result").value("DEFERRED"))
                .andExpect(jsonPath("$[1].reasons").value("later"));
        assertThat(count("select count(*) from claim_result")).isEqualTo(2);
    }

    @Test
    void refusesAClaimOfAnotherCaseAndTheSameClaimTwice() throws Exception {
        UUID caseId = newCase();
        UUID judgment = judgment(firstInstance(caseId), "2026-09-28");
        UUID mine = claim(caseId, "mine");
        UUID foreign = claim(newCase(), "foreign");
        put(everything(), results(judgment), entries(entry(foreign, "ACCEPTED", null, null))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("results[0].claimId"));
        put(everything(), results(judgment), entries(entry(mine, "ACCEPTED", null, null), entry(mine, "REJECTED", null, null)))
                .andExpect(status().isBadRequest());
        put(everything(), results(judgment), "{\"results\":[]}").andExpect(status().isBadRequest());
        assertThat(count("select count(*) from claim_result")).isZero();
        // The database refuses it as well.
        assertThatThrownBy(() -> jdbc.update("""
                insert into claim_result (judgment_id, claim_id, result, created_by, updated_by) values (?, ?, 'OTHER', ?, ?)""",
                judgment, foreign, USER, USER)).hasMessageContaining("different cases");
    }

    // S13
    @Test
    void aSecondStageNeverReplacesTheFirstStagesResults() throws Exception {
        UUID caseId = newCase();
        UUID first = firstInstance(caseId);
        UUID c1 = claim(caseId, "Compensation");
        UUID c2 = claim(caseId, "Refund");
        UUID firstJudgment = judgment(first, "2026-03-01");
        put(everything(), results(firstJudgment), entries(entry(c1, "REJECTED", null, "first-instance reasons"),
                entry(c2, "ACCEPTED", "75.125", null))).andExpect(status().isOk());

        UUID appeal = proceeding(caseId, "APPEAL", first, "2026-04-01");
        UUID appealJudgment = judgment(appeal, "2026-06-01");
        put(everything(), results(appealJudgment), entries(entry(c1, "ACCEPTED", "900.000", "appeal reasons"),
                entry(c2, "NOT_DECIDED", null, null))).andExpect(status().isOk());

        // Each judgment keeps its own results.
        get(everything(), results(firstJudgment)).andExpect(jsonPath("$[0].result").value("REJECTED"))
                .andExpect(jsonPath("$[0].reasons").value("first-instance reasons"))
                .andExpect(jsonPath("$[1].result").value("ACCEPTED")).andExpect(jsonPath("$[1].awardedAmount").value("75.125"));
        get(everything(), results(appealJudgment)).andExpect(jsonPath("$[0].result").value("ACCEPTED"))
                .andExpect(jsonPath("$[1].result").value("NOT_DECIDED"));
        // The case view shows both side by side, in the order of the stages.
        get(everything(), "/api/v1/cases/" + caseId + "/claim-results").andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].stage").value("FIRST_INSTANCE")).andExpect(jsonPath("$[0].claimSeq").value(1))
                .andExpect(jsonPath("$[0].result").value("REJECTED"))
                .andExpect(jsonPath("$[2].stage").value("APPEAL")).andExpect(jsonPath("$[2].result").value("ACCEPTED"))
                .andExpect(jsonPath("$[2].judgmentDate").value("2026-06-01"));

        // Amending a claim later, or adding another claim, does not touch recorded results.
        put(everything(), "/api/v1/claims/" + c1, json("text", "Compensation (amended)", "kind", "PRIMARY", "status", "STANDING",
                "version", 0)).andExpect(status().isOk());
        UUID c3 = claim(caseId, "Late claim");
        get(everything(), "/api/v1/cases/" + caseId + "/claim-results").andExpect(jsonPath("$.length()").value(4));
        get(everything(), results(firstJudgment)).andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[2].claimId").value(c3.toString())).andExpect(jsonPath("$[2].result").doesNotExist());
    }

    @Test
    void whoMayReadAndWriteTheResults() throws Exception {
        UUID specialist = user("Specialist");
        UUID caseId = newCase(specialist, "CIVIL");
        UUID judgment = judgment(firstInstance(caseId), "2026-09-28");
        UUID claim = claim(caseId, "x");
        String body = entries(entry(claim, "ACCEPTED", null, null));
        UUID stranger = UUID.randomUUID();

        get(outsider(stranger), results(judgment)).andExpect(status().isNotFound());
        put(outsider(stranger), results(judgment), body).andExpect(status().isNotFound());
        get(outsider(stranger), "/api/v1/cases/" + caseId + "/claim-results").andExpect(status().isNotFound());
        get(viewerOf(stranger), results(judgment)).andExpect(status().isOk());
        put(viewerOf(stranger), results(judgment), body).andExpect(status().isForbidden());
        put(with(Permission.JUDGMENT_VIEW, Permission.CLAIM_MANAGE), results(judgment), body).andExpect(status().isForbidden());
        get(with(Permission.CLAIM_VIEW), results(judgment)).andExpect(status().isForbidden());
        get(everything(), results(UUID.randomUUID())).andExpect(status().isNotFound());
        put(member(specialist), results(judgment), body).andExpect(status().isOk());
        assertThat(count("select count(*) from claim_result")).isEqualTo(1);
    }
}
