package com.lawdesk.pleadings;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import com.lawdesk.security.Permission;

/**
 * Statistics of claim results and defence positions (requirements 5 and 17): counted from the result rows themselves, by
 * result and by stage, only over the cases the user may see, and with the awarded amounts kept apart from every other amount.
 */
class StatisticsApiTest extends PleadingsTestSupport {

    private UUID specialist;
    private UUID visibleCase;
    private UUID hiddenCase;

    private String results(UUID claim, String result, String amount) {
        return "{\"results\":[" + json("claimId", claim, "result", result, "awardedAmount", amount) + "]}";
    }

    private void setUp() throws Exception {
        specialist = user("Specialist");
        visibleCase = newCase(specialist, "CIVIL");
        hiddenCase = newCase(user("Someone else"), "COMMERCIAL");
        for (UUID caseId : new UUID[] {visibleCase, hiddenCase}) {
            UUID first = firstInstance(caseId);
            UUID appeal = proceeding(caseId, "APPEAL", first, "2026-04-01");
            UUID c1 = claim(caseId, "one");
            UUID firstJudgment = judgment(first, "2026-03-01");
            UUID appealJudgment = judgment(appeal, "2026-06-01");
            put(everything(), "/api/v1/judgments/" + firstJudgment + "/claim-results", results(c1, "REJECTED", null)).andExpect(status().isOk());
            put(everything(), "/api/v1/judgments/" + appealJudgment + "/claim-results", results(c1, "ACCEPTED", "100.500")).andExpect(status().isOk());
            UUID d1 = created(everything(), "/api/v1/cases/" + caseId + "/defenses", json("title", "Late filing", "kind", "FORMAL"));
            put(everything(), "/api/v1/judgments/" + firstJudgment + "/defense-positions",
                    "{\"positions\":[" + json("defenseId", d1, "position", "ACCEPTED") + "]}").andExpect(status().isOk());
        }
    }

    @Test
    void countsResultsByResultAndByStageOverEveryCaseAUserSees() throws Exception {
        setUp();
        get(everything(), "/api/v1/reports/claim-results").andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.byResult.length()").value(12))
                .andExpect(jsonPath("$.byResult[?(@.result=='ACCEPTED')].count").value(Matchers.contains(2)))
                .andExpect(jsonPath("$.byResult[?(@.result=='ACCEPTED')].awardedTotal").value(Matchers.contains("201.000")))
                .andExpect(jsonPath("$.byResult[?(@.result=='PARTIAL')].count").value(Matchers.contains(0)))
                .andExpect(jsonPath("$.byResult[?(@.result=='PARTIAL')].awardedTotal").value(Matchers.contains("0.000")))
                .andExpect(jsonPath("$.byStage.length()").value(6))
                .andExpect(jsonPath("$.byStage[?(@.stage=='FIRST_INSTANCE')].count").value(Matchers.contains(2)))
                .andExpect(jsonPath("$.byStage[?(@.stage=='CASSATION')].count").value(Matchers.contains(0)))
                .andExpect(jsonPath("$.byStageAndResult.length()").value(2))
                .andExpect(jsonPath("$.byStageAndResult[?(@.stage=='APPEAL')].result").value(Matchers.contains("ACCEPTED")));
    }

    @Test
    void filtersByStageCaseTypeAndJudgmentDates() throws Exception {
        setUp();
        get(everything(), "/api/v1/reports/claim-results?stage=APPEAL").andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.byResult[?(@.result=='REJECTED')].count").value(Matchers.contains(0)));
        get(everything(), "/api/v1/reports/claim-results?caseType=CIVIL").andExpect(jsonPath("$.total").value(2));
        get(everything(), "/api/v1/reports/claim-results?from=2026-05-01&to=2026-12-31").andExpect(jsonPath("$.total").value(2));
        get(everything(), "/api/v1/reports/claim-results?from=2026-03-01&to=2026-03-01").andExpect(jsonPath("$.total").value(2));
        get(everything(), "/api/v1/reports/claim-results?stage=NONSENSE").andExpect(status().isBadRequest());
        get(everything(), "/api/v1/reports/claim-results?from=2026-06-01&to=2026-01-01").andExpect(status().isBadRequest());
        get(everything(), "/api/v1/reports/claim-results?caseType=NONSENSE").andExpect(status().isBadRequest());
    }

    @Test
    void countsOnlyTheCasesTheUserMaySee() throws Exception {
        setUp();
        get(member(specialist), "/api/v1/reports/claim-results").andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.byResult[?(@.result=='ACCEPTED')].awardedTotal").value(Matchers.contains("100.500")));
        get(outsider(UUID.randomUUID()), "/api/v1/reports/claim-results").andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.byResult[?(@.result=='ACCEPTED')].awardedTotal").value(Matchers.contains("0.000")));
        get(member(specialist), "/api/v1/reports/defense-positions").andExpect(jsonPath("$.total").value(1));
        get(outsider(UUID.randomUUID()), "/api/v1/reports/defense-positions").andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.mostUsed.length()").value(0));
    }

    @Test
    void countsDefencePositionsAndTheMostUsedDefences() throws Exception {
        setUp();
        get(everything(), "/api/v1/reports/defense-positions").andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.byPosition.length()").value(7))
                .andExpect(jsonPath("$.byPosition[?(@.position=='ACCEPTED')].count").value(Matchers.contains(2)))
                .andExpect(jsonPath("$.byStage[?(@.stage=='FIRST_INSTANCE')].count").value(Matchers.contains(2)))
                .andExpect(jsonPath("$.byKindAndPosition[0].kind").value("FORMAL"))
                .andExpect(jsonPath("$.mostUsed[0].title").value("Late filing"))
                .andExpect(jsonPath("$.mostUsed[0].count").value(2));
        get(everything(), "/api/v1/reports/defense-positions?caseType=CIVIL").andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void needsTheReportPermission() throws Exception {
        get(with(Permission.JUDGMENT_VIEW), "/api/v1/reports/claim-results").andExpect(status().isForbidden());
        get(with(Permission.JUDGMENT_VIEW), "/api/v1/reports/defense-positions").andExpect(status().isForbidden());
        get(with(Permission.REPORT_VIEW), "/api/v1/reports/claim-results").andExpect(status().isOk());
    }
}
