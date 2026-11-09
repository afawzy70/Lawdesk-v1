package com.lawdesk.proceedings;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

/**
 * Owner decision D2 (LD-050): constitutional, enforcement and grievance are stages of a proceeding, heard by courts or
 * bodies of the matching level; they need a parent proceeding of the same case and no more, and they leave the ladder
 * rules of appeal and cassation as they were.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdditionalStagesApiTest extends JudicialApiTestSupport {

    @Test
    void eachNewTrackIsHeardByItsOwnLevelAndNeedsAParentOfTheSameCase() throws Exception {
        UUID caseId = createCase("Tracks");
        UUID first = createCourt("FI-D2", "FIRST_INSTANCE");
        UUID parent = recordProceeding(caseId, proceeding("FIRST_INSTANCE", first, "1", 2026, "2026-01-10", null));
        for (String track : new String[] {"CONSTITUTIONAL", "ENFORCEMENT", "GRIEVANCE"}) {
            UUID court = createCourt(track.substring(0, 3) + "-D2", track);
            // No judgment is needed on the parent: the documents give no further rule.
            mvc.perform(postProceeding(caseId, proceeding(track, court, "9", 2026, "2026-02-01", parent)))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.stage").value(track));
            mvc.perform(postProceeding(caseId, proceeding(track, court, "10", 2026, "2026-02-01", null)))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.rule").value("PARENT_PROCEEDING_REQUIRED"));
            mvc.perform(postProceeding(caseId, proceeding(track, first, "11", 2026, "2026-02-01", parent)))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.rule").value("COURT_LEVEL_MISMATCH"));
        }
    }

    @Test
    void theParentMustBelongToTheSameCaseAndTheLadderRulesAreUnchanged() throws Exception {
        UUID caseId = createCase("Ladder");
        UUID other = createCase("Other");
        UUID first = createCourt("FI-D2B", "FIRST_INSTANCE");
        UUID enforcement = createCourt("ENF-D2B", "ENFORCEMENT");
        UUID appealCourt = createCourt("AP-D2B", "APPEAL");
        UUID parent = recordProceeding(caseId, proceeding("FIRST_INSTANCE", first, "1", 2026, "2026-01-10", null));
        UUID foreign = recordProceeding(other, proceeding("FIRST_INSTANCE", first, "2", 2026, "2026-01-10", null));
        mvc.perform(postProceeding(caseId, proceeding("ENFORCEMENT", enforcement, "5", 2026, "2026-02-01", foreign)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("PARENT_PROCEEDING_OTHER_CASE"));
        // An appeal still needs a judged first instance.
        mvc.perform(postProceeding(caseId, proceeding("APPEAL", appealCourt, "6", 2026, "2026-02-01", parent)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("APPEAL_REQUIRES_FIRST_INSTANCE_JUDGMENT"));
    }
}
