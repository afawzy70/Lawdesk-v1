package com.lawdesk.pleadings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import com.lawdesk.security.Permission;

/**
 * Requirements section 5 (LD-DOC-05-001..): the claims of a case, each with its own sequence number, and S2 — a claim
 * amended during the proceedings keeps its previous text, the date and the user who changed it.
 */
class ClaimApiTest extends PleadingsTestSupport {

    private String claims(UUID caseId) {
        return "/api/v1/cases/" + caseId + "/claims";
    }

    @Test
    void numbersTheClaimsOfACaseOneByOneAndKeepsTheFirstRevision() throws Exception {
        UUID caseId = newCase();
        UUID stage = firstInstance(caseId);
        UUID plaintiff = party(caseId, "Synthetic plaintiff");
        UUID statement = document(caseId, "Statement of claim");

        post(everything(), claims(caseId), json("text", "  Compensation for material damage  ", "kind", "PRIMARY",
                "submittedByPartyId", plaintiff, "submittedOn", "2026-01-12", "stageId", stage, "sourceDocumentId",
                statement, "notes", "first"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.seq").value(1))
                .andExpect(jsonPath("$.text").value("Compensation for material damage"))
                .andExpect(jsonPath("$.kind").value("PRIMARY"))
                .andExpect(jsonPath("$.status").value("STANDING"))
                .andExpect(jsonPath("$.submittedByPartyName").value("Synthetic plaintiff"))
                .andExpect(jsonPath("$.sourceDocumentTitle").value("Statement of claim"))
                .andExpect(jsonPath("$.stageId").value(stage.toString()))
                .andExpect(jsonPath("$.revision").value(1))
                .andExpect(jsonPath("$.version").value(0));
        post(everything(), claims(caseId), json("text", "Alternatively, a refund", "kind", "ALTERNATIVE"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.seq").value(2));
        post(everything(), claims(caseId), json("text", "Costs", "kind", "ANCILLARY"))
                .andExpect(jsonPath("$.seq").value(3));

        get(everything(), claims(caseId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[*].seq").value(Matchers.contains(1, 2, 3)));
        assertThat(count("select count(*) from claim_revision where rev = 1")).isEqualTo(3);
        assertThat(count("select count(*) from case_event where case_id = ? and type = 'CLAIM_ADDED'", caseId))
                .isEqualTo(3);
        // Another case numbers its own claims from 1.
        post(everything(), claims(newCase()), json("text", "Other", "kind", "OTHER")).andExpect(jsonPath("$.seq").value(1));
    }

    @Test
    void refusesWhatIsNotAClaim() throws Exception {
        UUID caseId = newCase();
        post(everything(), claims(caseId), json("text", "   ", "kind", "PRIMARY")).andExpect(status().isBadRequest());
        post(everything(), claims(caseId), json("kind", "PRIMARY")).andExpect(status().isBadRequest());
        post(everything(), claims(caseId), json("text", "x")).andExpect(status().isBadRequest());
        post(everything(), claims(caseId), json("text", "x", "kind", "SOMETHING")).andExpect(status().isBadRequest());
        assertThat(count("select count(*) from claim")).isZero();
    }

    @Test
    void whatItPointsAtMustBelongToTheCase() throws Exception {
        UUID caseId = newCase();
        UUID elsewhere = newCase();
        post(everything(), claims(caseId), json("text", "x", "kind", "PRIMARY", "stageId", firstInstance(elsewhere)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("stageId"));
        post(everything(), claims(caseId), json("text", "x", "kind", "PRIMARY", "submittedByPartyId", party(elsewhere, "P")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("submittedByPartyId"));
        post(everything(), claims(caseId), json("text", "x", "kind", "PRIMARY", "sourceDocumentId", document(elsewhere, "D")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("sourceDocumentId"));
        assertThat(count("select count(*) from claim")).isZero();
    }

    // S2
    @Test
    void anAmendedClaimKeepsItsPreviousTextWithTheDateAndTheUser() throws Exception {
        UUID caseId = newCase();
        UUID claim = claim(caseId, "Compensation for material and moral damage");
        UUID editor = UUID.randomUUID();

        put(everything(editor), "/api/v1/claims/" + claim, json("text", "Compensation for material damage", "kind", "PRIMARY",
                "status", "STANDING", "reason", "moral damage dropped", "version", 0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("Compensation for material damage"))
                .andExpect(jsonPath("$.status").value("AMENDED"))
                .andExpect(jsonPath("$.revision").value(2))
                .andExpect(jsonPath("$.version").value(1));

        get(everything(), "/api/v1/claims/" + claim + "/revisions").andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].rev").value(2))
                .andExpect(jsonPath("$[0].text").value("Compensation for material damage"))
                .andExpect(jsonPath("$[0].reason").value("moral damage dropped"))
                .andExpect(jsonPath("$[0].changedBy").value(editor.toString()))
                .andExpect(jsonPath("$[0].changedAt").isString())
                .andExpect(jsonPath("$[1].rev").value(1))
                .andExpect(jsonPath("$[1].text").value("Compensation for material and moral damage"))
                .andExpect(jsonPath("$[1].status").value("STANDING"));
        assertThat(count("select count(*) from case_event where case_id = ? and type = 'CLAIM_AMENDED'", caseId))
                .isEqualTo(1);
    }

    @Test
    void aWaivedClaimIsStillThereAndKeepsItsNumber() throws Exception {
        UUID caseId = newCase();
        UUID claim = claim(caseId, "Rent arrears");
        put(everything(), "/api/v1/claims/" + claim, json("text", "Rent arrears", "kind", "PRIMARY", "status", "WAIVED",
                "version", 0)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("WAIVED"));
        get(everything(), "/api/v1/cases/" + caseId + "/claims").andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].seq").value(1));
        UUID next = claim(caseId, "Another");
        get(everything(), "/api/v1/claims/" + next).andExpect(jsonPath("$.seq").value(2));
    }

    @Test
    void anUpdateThatChangesNothingAddsNoRevision() throws Exception {
        UUID claim = claim(newCase(), "Same");
        put(everything(), "/api/v1/claims/" + claim, json("text", "Same", "kind", "PRIMARY", "status", "STANDING",
                "version", 0)).andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(1))
                .andExpect(jsonPath("$.version").value(0));
        assertThat(count("select count(*) from claim_revision where claim_id = ?", claim)).isEqualTo(1);
    }

    @Test
    void aStaleVersionIsRefused() throws Exception {
        UUID claim = claim(newCase(), "First");
        put(everything(), "/api/v1/claims/" + claim, json("text", "Second", "kind", "PRIMARY", "status", "STANDING",
                "version", 0)).andExpect(status().isOk());
        put(everything(), "/api/v1/claims/" + claim, json("text", "Third", "kind", "PRIMARY", "status", "STANDING",
                "version", 0)).andExpect(status().isConflict());
        get(everything(), "/api/v1/claims/" + claim).andExpect(jsonPath("$.text").value("Second"));
    }

    @Test
    void revisionsCannotBeChangedOrRemovedInTheDatabase() throws Exception {
        UUID claim = claim(newCase(), "Immutable");
        assertThatThrownBy(() -> jdbc.update("update claim_revision set text = 'x' where claim_id = ?", claim))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("delete from claim_revision where claim_id = ?", claim))
                .hasMessageContaining("append-only");
    }

    @Test
    void concurrentClaimsGetDifferentNumbers() throws Exception {
        UUID caseId = newCase();
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(6)) {
            List<java.util.concurrent.Future<Integer>> results = new java.util.ArrayList<>();
            for (int i = 0; i < 6; i++) {
                String text = "Claim " + i;
                results.add(pool.submit(() -> post(everything(), claims(caseId), json("text", text, "kind", "OTHER"))
                        .andReturn().getResponse().getStatus()));
            }
            for (var result : results) {
                assertThat(result.get()).isEqualTo(201);
            }
        }
        assertThat(jdbc.queryForList("select seq from claim where case_id = ? order by seq", Integer.class, caseId))
                .containsExactly(1, 2, 3, 4, 5, 6);
    }

    // D3 and the permission model
    @Test
    void whoMaySeeAndWhoMayChange() throws Exception {
        UUID specialist = user("Specialist");
        UUID caseId = newCase(specialist, "COMMERCIAL");
        UUID claim = created(member(specialist), claims(caseId), json("text", "Mine", "kind", "PRIMARY"));
        UUID stranger = UUID.randomUUID();

        // The member changes; a department-wide viewer reads but is refused a change (403); an outsider is told the case is not there (404).
        get(viewerOf(stranger), claims(caseId)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        get(viewerOf(stranger), "/api/v1/claims/" + claim + "/revisions").andExpect(status().isOk());
        post(viewerOf(stranger), claims(caseId), json("text", "x", "kind", "OTHER")).andExpect(status().isForbidden());
        put(viewerOf(stranger), "/api/v1/claims/" + claim, json("text", "x", "kind", "OTHER", "status", "STANDING", "version", 0))
                .andExpect(status().isForbidden());

        get(outsider(stranger), claims(caseId)).andExpect(status().isNotFound());
        get(outsider(stranger), "/api/v1/claims/" + claim).andExpect(status().isNotFound());
        get(outsider(stranger), "/api/v1/claims/" + claim + "/revisions").andExpect(status().isNotFound());
        post(outsider(stranger), claims(caseId), json("text", "x", "kind", "OTHER")).andExpect(status().isNotFound());
        put(outsider(stranger), "/api/v1/claims/" + claim, json("text", "x", "kind", "OTHER", "status", "STANDING", "version", 0))
                .andExpect(status().isNotFound());
        assertThat(count("select count(*) from claim where case_id = ?", caseId)).isEqualTo(1);
    }

    @Test
    void unknownCasesAndClaimsAreNotFound() throws Exception {
        get(everything(), claims(UUID.randomUUID())).andExpect(status().isNotFound());
        post(everything(), claims(UUID.randomUUID()), json("text", "x", "kind", "OTHER")).andExpect(status().isNotFound());
        get(everything(), "/api/v1/claims/" + UUID.randomUUID()).andExpect(status().isNotFound());
        get(everything(), "/api/v1/claims/" + UUID.randomUUID() + "/revisions").andExpect(status().isNotFound());
    }

    @Test
    void readingNeedsClaimViewAndWritingNeedsClaimManage() throws Exception {
        UUID caseId = newCase();
        UUID claim = claim(caseId, "x");
        get(with(Permission.CASE_VIEW), claims(caseId)).andExpect(status().isForbidden());
        get(with(Permission.CLAIM_VIEW), claims(caseId)).andExpect(status().isOk());
        post(with(Permission.CLAIM_VIEW), claims(caseId), json("text", "y", "kind", "OTHER")).andExpect(status().isForbidden());
        put(with(Permission.CLAIM_VIEW), "/api/v1/claims/" + claim, json("text", "y", "kind", "OTHER", "status", "STANDING",
                "version", 0)).andExpect(status().isForbidden());
    }
}
