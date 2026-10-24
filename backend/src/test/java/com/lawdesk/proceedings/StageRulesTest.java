package com.lawdesk.proceedings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * LD-020 appeals and LD-021 cassation (docs/delivery/PHASE-4-ACCEPTANCE.md): an appeal follows a first-instance
 * judgment of the same case, a cassation follows an appeal judgment. Deadlines are recorded, not enforced.
 */
class StageRulesTest extends JudicialApiTestSupport {

    private record Ladder(UUID caseId, UUID firstCourt, UUID appealCourt, UUID cassationCourt) {
    }

    private Ladder ladder(String suffix) throws Exception {
        return new Ladder(createCase("Ladder " + suffix), createCourt("FI-" + suffix, "FIRST_INSTANCE"),
                createCourt("AP-" + suffix, "APPEAL"), createCourt("CS-" + suffix, "CASSATION"));
    }

    private UUID judgedFirstInstance(Ladder l) throws Exception {
        UUID first = recordProceeding(l.caseId(), proceeding("FIRST_INSTANCE", l.firstCourt(), "100", 2023,
                "2023-01-10", null));
        recordJudgment(first, "2023-06-01");
        return first;
    }

    // LD-020 appeals

    @Test
    void anAppealFollowsAFirstInstanceJudgmentOfTheSameCase() throws Exception {
        Ladder l = ladder("A1");
        UUID first = judgedFirstInstance(l);
        // Filed long after the 40-day appeal period: deadlines are recorded only (ASSUMPTION).
        mvc.perform(postProceeding(l.caseId(), proceeding("APPEAL", l.appealCourt(), "7", 2024, "2024-05-01", first)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stage").value("APPEAL"))
                .andExpect(jsonPath("$.parentProceedingId").value(first.toString()));
        verify(caseLookup).recordEvent(eq(l.caseId()), eq("PROCEEDING_RECORDED"), contains("7/2024"));
    }

    @Test
    void aFirstInstanceJudgmentMayBeAppealedMoreThanOnce() throws Exception {
        Ladder l = ladder("A2");
        UUID first = judgedFirstInstance(l);
        recordProceeding(l.caseId(), proceeding("APPEAL", l.appealCourt(), "1", 2023, "2023-07-01", first));
        recordProceeding(l.caseId(), proceeding("APPEAL", l.appealCourt(), "2", 2023, "2023-07-02", first));
        assertThat(rows("proceeding")).isEqualTo(3);
    }

    @Test
    void anAppealNeedsAParent() throws Exception {
        Ladder l = ladder("A3");
        mvc.perform(postProceeding(l.caseId(), proceeding("APPEAL", l.appealCourt(), "1", 2023, "2023-07-01", null)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("PARENT_PROCEEDING_REQUIRED"));
        mvc.perform(postProceeding(l.caseId(), proceeding("APPEAL", l.appealCourt(), "1", 2023, "2023-07-01",
                        UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    @Test
    void anAppealNeedsTheFirstInstanceJudgment() throws Exception {
        Ladder l = ladder("A4");
        UUID first = recordProceeding(l.caseId(), proceeding("FIRST_INSTANCE", l.firstCourt(), "1", 2023,
                "2023-01-10", null));
        mvc.perform(postProceeding(l.caseId(), proceeding("APPEAL", l.appealCourt(), "1", 2023, "2023-07-01", first)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("APPEAL_REQUIRES_FIRST_INSTANCE_JUDGMENT"));
        assertThat(rows("proceeding")).isEqualTo(1);
    }

    @Test
    void anAppealCannotFollowAnotherCasesProceeding() throws Exception {
        Ladder l = ladder("A5");
        UUID foreignCase = createCase("Foreign");
        UUID foreign = recordProceeding(foreignCase, proceeding("FIRST_INSTANCE", l.firstCourt(), "9", 2023,
                "2023-01-10", null));
        recordJudgment(foreign, "2023-06-01");
        mvc.perform(postProceeding(l.caseId(), proceeding("APPEAL", l.appealCourt(), "1", 2023, "2023-07-01",
                        foreign)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("PARENT_PROCEEDING_OTHER_CASE"));
    }

    @Test
    void anAppealCannotFollowAnAppeal() throws Exception {
        Ladder l = ladder("A6");
        UUID first = judgedFirstInstance(l);
        UUID appeal = recordProceeding(l.caseId(), proceeding("APPEAL", l.appealCourt(), "1", 2023, "2023-07-01",
                first));
        recordJudgment(appeal, "2023-12-01");
        mvc.perform(postProceeding(l.caseId(), proceeding("APPEAL", l.appealCourt(), "2", 2024, "2024-01-01", appeal)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("APPEAL_PARENT_NOT_FIRST_INSTANCE"));
    }

    @Test
    void anAppealIsHeardByAnAppealCourtAndFiledAfterTheJudgment() throws Exception {
        Ladder l = ladder("A7");
        UUID first = judgedFirstInstance(l);
        mvc.perform(postProceeding(l.caseId(), proceeding("APPEAL", l.firstCourt(), "1", 2023, "2023-07-01", first)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("COURT_LEVEL_MISMATCH"));
        mvc.perform(postProceeding(l.caseId(), proceeding("APPEAL", l.appealCourt(), "1", 2023, "2023-05-31", first)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("FILED_BEFORE_PARENT_JUDGMENT"));
        mvc.perform(postProceeding(l.caseId(), proceeding("APPEAL", l.appealCourt(), "1", 2023, "2023-06-01", first)))
                .andExpect(status().isCreated());
    }

    // LD-021 cassation

    @Test
    void aCassationFollowsAnAppealJudgment() throws Exception {
        Ladder l = ladder("C1");
        UUID first = judgedFirstInstance(l);
        UUID appeal = recordProceeding(l.caseId(), proceeding("APPEAL", l.appealCourt(), "1", 2023, "2023-07-01",
                first));
        mvc.perform(postProceeding(l.caseId(), proceeding("CASSATION", l.cassationCourt(), "3", 2024, "2024-01-05",
                        appeal)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("CASSATION_REQUIRES_APPEAL_JUDGMENT"));

        recordJudgment(appeal, "2023-12-01");
        mvc.perform(postProceeding(l.caseId(), proceeding("CASSATION", l.cassationCourt(), "3", 2024, "2024-01-05",
                        appeal)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stage").value("CASSATION"))
                .andExpect(jsonPath("$.parentProceedingId").value(appeal.toString()));
    }

    @Test
    void aCassationCannotSkipTheAppeal() throws Exception {
        Ladder l = ladder("C2");
        UUID first = judgedFirstInstance(l);
        mvc.perform(postProceeding(l.caseId(), proceeding("CASSATION", l.cassationCourt(), "3", 2024, "2024-01-05",
                        first)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("CASSATION_PARENT_NOT_APPEAL"));
        mvc.perform(postProceeding(l.caseId(), proceeding("CASSATION", l.cassationCourt(), "3", 2024, "2024-01-05",
                        null)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("PARENT_PROCEEDING_REQUIRED"));
    }

    @Test
    void aCassationIsHeardByTheCourtOfCassation() throws Exception {
        Ladder l = ladder("C3");
        UUID first = judgedFirstInstance(l);
        UUID appeal = recordProceeding(l.caseId(), proceeding("APPEAL", l.appealCourt(), "1", 2023, "2023-07-01",
                first));
        recordJudgment(appeal, "2023-12-01");
        mvc.perform(postProceeding(l.caseId(), proceeding("CASSATION", l.appealCourt(), "3", 2024, "2024-01-05",
                        appeal)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("COURT_LEVEL_MISMATCH"));
    }
}
