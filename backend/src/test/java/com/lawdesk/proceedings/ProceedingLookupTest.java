package com.lawdesk.proceedings;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * architecture/CONTRACTS.md §8: Phase 5 attaches hearings and expert referrals to proceedings, and Phase 6 reports on
 * them, without reading Workstream C's tables. {@link ProceedingLookup} is their read-only port.
 */
class ProceedingLookupTest extends JudicialApiTestSupport {

    @Autowired
    ProceedingLookup lookup;

    @Test
    void findsAProceedingWithItsCaseCourtAndStage() throws Exception {
        UUID caseId = createCase("Lookup case");
        UUID court = createCourt("LK1", "FIRST_INSTANCE");
        UUID id = recordProceeding(caseId, proceeding("FIRST_INSTANCE", court, "12", 2024, "2024-02-01", null));

        ProceedingLookup.ProceedingSummary found = lookup.find(id).orElseThrow();

        assertThat(found).isEqualTo(new ProceedingLookup.ProceedingSummary(id, caseId, Stage.FIRST_INSTANCE, court,
                "12", 2024, LocalDate.parse("2024-02-01"), null));
        assertThat(lookup.find(UUID.randomUUID())).isEmpty();
    }

    @Test
    void listsTheProceedingsOfACaseFromFirstInstanceUp() throws Exception {
        UUID caseId = createCase("Ladder case");
        UUID first = recordProceeding(caseId,
                proceeding("FIRST_INSTANCE", createCourt("LK2", "FIRST_INSTANCE"), "1", 2024, "2024-01-15", null));
        recordJudgment(first, "2024-05-01");
        UUID appeal = recordProceeding(caseId,
                proceeding("APPEAL", createCourt("LK3", "APPEAL"), "2", 2024, "2024-06-01", first));

        assertThat(lookup.forCase(caseId)).extracting(ProceedingLookup.ProceedingSummary::id)
                .containsExactly(first, appeal);
        assertThat(lookup.forCase(UUID.randomUUID())).isEmpty();
    }
}
