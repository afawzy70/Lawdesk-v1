package com.lawdesk.pleadings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;
import com.lawdesk.security.Permission;

/**
 * Requirement section 20 and scenario S8: facts, evidence with its role and importance, many-to-many links to facts,
 * defences and claims, tracking of what was submitted to the court, and the detection of evidence that is missing.
 */
class EvidenceApiTest extends PleadingsTestSupport {

    private String facts(UUID caseId) {
        return "/api/v1/cases/" + caseId + "/facts";
    }

    private String evidence(UUID caseId) {
        return "/api/v1/cases/" + caseId + "/evidence";
    }

    private UUID item(UUID caseId, String name, String role, Object... more) throws Exception {
        Object[] pairs = new Object[more.length + 4];
        pairs[0] = "name";
        pairs[1] = name;
        pairs[2] = "role";
        pairs[3] = role;
        System.arraycopy(more, 0, pairs, 4, more.length);
        return created(everything(), evidence(caseId), json(pairs));
    }

    @Test
    void recordsNumberedFacts() throws Exception {
        UUID caseId = newCase();
        post(everything(), facts(caseId), json("text", "  Date the decision was notified  ", "factDate", "2026-01-05"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.seq").value(1))
                .andExpect(jsonPath("$.text").value("Date the decision was notified"))
                .andExpect(jsonPath("$.factDate").value("2026-01-05")).andExpect(jsonPath("$.evidenceCount").value(0));
        UUID second = created(everything(), facts(caseId), json("text", "The investigation record"));
        get(everything(), facts(caseId)).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].seq").value(Matchers.contains(1, 2)));
        put(everything(), "/api/v1/facts/" + second, json("text", "The investigation record (corrected)", "factDate", "2026-02-01",
                "version", 0)).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        put(everything(), "/api/v1/facts/" + second, json("text", "stale", "version", 0)).andExpect(status().isConflict());
        post(everything(), facts(caseId), json("text", " ")).andExpect(status().isBadRequest());
        assertThat(count("select count(*) from case_event where case_id = ? and type = 'FACT_ADDED'", caseId)).isEqualTo(2);
    }

    @Test
    void evidenceHasARoleAnImportanceAndATrackOfWhatWasSubmitted() throws Exception {
        UUID caseId = newCase();
        UUID document = document(caseId, "Decision 118");
        for (String role : new String[] {"SUPPORTING", "OPPONENT", "DISPUTED", "REQUIRED", "UNAVAILABLE", "SUPPLEMENTARY"}) {
            post(everything(), evidence(caseId), json("name", "Item " + role, "role", role)).andExpect(status().isCreated())
                    .andExpect(jsonPath("$.role").value(role)).andExpect(jsonPath("$.submittedToCourt").value(false));
        }
        post(everything(), evidence(caseId), json("name", "Decision 118", "docType", "decision", "issuedOn", "2025-12-01", "issuer",
                "The authority", "receivedOn", "2026-01-02", "source", "Personnel affairs", "role", "SUPPORTING", "importance",
                "DECISIVE", "documentId", document, "submittedToCourt", true, "submittedOn", "2026-03-30", "submittedIn",
                "Memo of 2026-03-30", "opponentResponded", true, "opponentResponse", "denied", "courtPosition", "pending",
                "notes", "key document"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentTitle").value("Decision 118"))
                .andExpect(jsonPath("$.importance").value("DECISIVE"))
                .andExpect(jsonPath("$.submittedOn").value("2026-03-30"))
                .andExpect(jsonPath("$.submittedIn").value("Memo of 2026-03-30"))
                .andExpect(jsonPath("$.opponentResponded").value(true))
                .andExpect(jsonPath("$.links.length()").value(0));
        get(everything(), evidence(caseId)).andExpect(jsonPath("$.length()").value(7));

        post(everything(), evidence(caseId), json("name", "x", "role", "BOGUS")).andExpect(status().isBadRequest());
        post(everything(), evidence(caseId), json("name", "x", "role", "SUPPORTING", "importance", "HUGE")).andExpect(status().isBadRequest());
        post(everything(), evidence(caseId), json("name", " ", "role", "SUPPORTING")).andExpect(status().isBadRequest());
        post(everything(), evidence(caseId), json("name", "x", "role", "SUPPORTING", "submittedToCourt", false, "submittedOn",
                "2026-03-30")).andExpect(status().isBadRequest());
        post(everything(), evidence(caseId), json("name", "x", "role", "SUPPORTING", "documentId", document(newCase(), "d")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("documentId"));
        assertThat(count("select count(*) from evidence_item where case_id = ?", caseId)).isEqualTo(7);
    }

    @Test
    void evidenceIsUpdatedWithTheCurrentVersionOnly() throws Exception {
        UUID caseId = newCase();
        UUID id = item(caseId, "Record", "REQUIRED");
        put(everything(), "/api/v1/evidence/" + id, json("name", "Record", "role", "SUPPORTING", "submittedToCourt", true,
                "submittedOn", "2026-05-01", "version", 0)).andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("SUPPORTING")).andExpect(jsonPath("$.version").value(1));
        put(everything(), "/api/v1/evidence/" + id, json("name", "Record", "role", "SUPPORTING", "version", 0))
                .andExpect(status().isConflict());
    }

    // S8
    @Test
    void oneDocumentIsLinkedToSeveralFactsDefencesAndClaims() throws Exception {
        UUID caseId = newCase();
        UUID document = document(caseId, "Receipt statement");
        UUID fact1 = created(everything(), facts(caseId), json("text", "Notification date"));
        UUID fact2 = created(everything(), facts(caseId), json("text", "Investigation"));
        UUID defense = created(everything(), "/api/v1/cases/" + caseId + "/defenses", json("title", "Late filing", "kind", "FORMAL"));
        UUID claim = claim(caseId, "Compensation");
        UUID evidence = item(caseId, "Receipt statement", "SUPPORTING", "documentId", document, "importance", "HIGH");
        String links = "/api/v1/evidence/" + evidence + "/links";

        post(everything(), links, json("factId", fact1)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("FACT")).andExpect(jsonPath("$.targetId").value(fact1.toString()));
        post(everything(), links, json("factId", fact2)).andExpect(status().isCreated());
        UUID defenseLink = UUID.fromString(JsonPath.read(body(post(everything(), links, json("defenseId", defense))), "$.id"));
        post(everything(), links, json("claimId", claim)).andExpect(status().isCreated());

        get(everything(), evidence(caseId)).andExpect(jsonPath("$[0].links.length()").value(4))
                .andExpect(jsonPath("$[0].links[?(@.kind=='DEFENSE')].label").value(Matchers.contains("Defence 1: Late filing")))
                .andExpect(jsonPath("$[0].links[?(@.kind=='FACT')].label").value(Matchers.hasItem("Fact 1: Notification date")))
                .andExpect(jsonPath("$[0].links[?(@.kind=='CLAIM')].label").value(Matchers.contains("Claim 1")));
        get(everything(), facts(caseId)).andExpect(jsonPath("$[0].evidenceCount").value(1));
        // The defence lists the documents that support it.
        get(everything(), "/api/v1/defenses/" + defense).andExpect(jsonPath("$.evidence.length()").value(1))
                .andExpect(jsonPath("$.evidence[0].name").value("Receipt statement"))
                .andExpect(jsonPath("$.evidence[0].documentId").value(document.toString()))
                .andExpect(jsonPath("$.defense.evidenceCount").value(1));

        // Shown with the case file: where the document is used, with what it is linked to.
        get(everything(), "/api/v1/cases/" + caseId + "/document-usage").andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].documentId").value(document.toString()))
                .andExpect(jsonPath("$[0].type").value("EVIDENCE"))
                .andExpect(jsonPath("$[0].label").value("Receipt statement"))
                .andExpect(jsonPath("$[0].links.length()").value(4));

        post(everything(), links, json("defenseId", defense)).andExpect(status().isConflict());
        post(everything(), links, json("factId", fact1, "defenseId", defense)).andExpect(status().isBadRequest());
        post(everything(), links, "{}").andExpect(status().isBadRequest());
        UUID other = newCase();
        post(everything(), links, json("factId", created(everything(), facts(other), json("text", "elsewhere"))))
                .andExpect(status().isBadRequest());
        delete(everything(), links + "/" + defenseLink).andExpect(status().isNoContent());
        delete(everything(), links + "/" + defenseLink).andExpect(status().isNotFound());
        get(everything(), evidence(caseId)).andExpect(jsonPath("$[0].links.length()").value(3));
        assertThatThrownBy(() -> jdbc.update("""
                insert into evidence_link (evidence_id, fact_id, defense_id, created_by) values (?, ?, ?, ?)""", evidence, fact1,
                defense, USER)).hasMessageContaining("evidence_link_one_target");
    }

    @Test
    void documentUsageShowsClaimSourcesMemosAndJudgmentCopies() throws Exception {
        UUID caseId = newCase();
        UUID statement = document(caseId, "Statement of claim");
        UUID memo = document(caseId, "Memo");
        UUID copy = document(caseId, "Judgment copy");
        document(caseId, "Unused");
        created(everything(), "/api/v1/cases/" + caseId + "/claims", json("text", "c", "kind", "PRIMARY", "sourceDocumentId", statement));
        created(everything(), "/api/v1/cases/" + caseId + "/defenses", json("title", "d", "kind", "OTHER", "memoDocumentId", memo));
        UUID proceeding = firstInstance(caseId);
        post(everything(), "/api/v1/proceedings/" + proceeding + "/judgment", """
                {"judgmentDate":"2026-03-01","outcome":"PARTIAL","details":{"copyDocumentId":"%s"}}""".formatted(copy))
                .andExpect(status().isCreated());

        get(everything(), "/api/v1/cases/" + caseId + "/document-usage").andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[?(@.type=='CLAIM_SOURCE')].label").value(Matchers.contains("Claim 1")))
                .andExpect(jsonPath("$[?(@.type=='DEFENSE_MEMO')].label").value(Matchers.contains("Defence 1: d")))
                .andExpect(jsonPath("$[?(@.type=='JUDGMENT_COPY')].documentId").value(Matchers.contains(copy.toString())));
    }

    @Test
    void detectsEvidenceThatIsNeededButMissingOrNotSubmitted() throws Exception {
        UUID caseId = newCase();
        UUID document = document(caseId, "Copy");
        UUID missing = item(caseId, "Receipt statement", "REQUIRED", "importance", "HIGH");
        UUID unavailable = item(caseId, "Attendance register", "UNAVAILABLE");
        UUID unsubmitted = item(caseId, "Decision 118", "SUPPORTING", "documentId", document);
        item(caseId, "Submitted one", "SUPPORTING", "documentId", document, "submittedToCourt", true);
        item(caseId, "Opponent letter", "OPPONENT", "documentId", document);
        item(caseId, "Obtained and required", "REQUIRED", "documentId", document, "submittedToCourt", true);

        get(everything(), evidence(caseId) + "/gaps").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[?(@.id=='" + missing + "')].gap").value(Matchers.contains("NOT_OBTAINED")))
                .andExpect(jsonPath("$[?(@.id=='" + unavailable + "')].gap").value(Matchers.contains("NOT_OBTAINED")))
                .andExpect(jsonPath("$[?(@.id=='" + unsubmitted + "')].gap").value(Matchers.contains("NOT_SUBMITTED")));

        // Follow-up: a task of the same case, linked once.
        UUID task = task(caseId, "Ask personnel affairs for the receipt statement");
        post(everything(), "/api/v1/evidence/" + missing + "/followups", json("taskId", task)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.taskId").value(task.toString()))
                .andExpect(jsonPath("$.taskTitle").value("Ask personnel affairs for the receipt statement"));
        get(everything(), evidence(caseId) + "/gaps").andExpect(jsonPath("$[?(@.id=='" + missing + "')].followups[0].taskId")
                .value(Matchers.contains(task.toString())));
        post(everything(), "/api/v1/evidence/" + missing + "/followups", json("taskId", task)).andExpect(status().isConflict());
        post(everything(), "/api/v1/evidence/" + missing + "/followups", json("taskId", task(newCase(), "elsewhere")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("taskId"));
        post(everything(), "/api/v1/evidence/" + missing + "/followups", json("taskId", UUID.randomUUID())).andExpect(status().isBadRequest());
        post(everything(), "/api/v1/evidence/" + missing + "/followups", "{}").andExpect(status().isBadRequest());
        UUID followupId = jdbc.queryForObject("select id from gap_followup where evidence_id = ?", UUID.class, missing);
        delete(everything(), "/api/v1/evidence/" + missing + "/followups/" + followupId).andExpect(status().isNoContent());
        delete(everything(), "/api/v1/evidence/" + missing + "/followups/" + followupId).andExpect(status().isNotFound());
        assertThat(count("select count(*) from case_event where case_id = ? and type = 'EVIDENCE_ADDED'", caseId)).isEqualTo(6);
    }

    @Test
    void whoMayReadAndWriteFactsAndEvidence() throws Exception {
        UUID specialist = user("Specialist");
        UUID caseId = newCase(specialist, "CIVIL");
        UUID fact = created(member(specialist), facts(caseId), json("text", "A fact"));
        UUID evidence = created(member(specialist), evidence(caseId), json("name", "Item", "role", "REQUIRED"));
        UUID stranger = UUID.randomUUID();

        for (String path : new String[] {facts(caseId), evidence(caseId), evidence(caseId) + "/gaps",
            "/api/v1/cases/" + caseId + "/document-usage"}) {
            get(outsider(stranger), path).andExpect(status().isNotFound());
            get(viewerOf(stranger), path).andExpect(status().isOk());
        }
        post(outsider(stranger), facts(caseId), json("text", "x")).andExpect(status().isNotFound());
        post(viewerOf(stranger), facts(caseId), json("text", "x")).andExpect(status().isForbidden());
        put(viewerOf(stranger), "/api/v1/facts/" + fact, json("text", "x", "version", 0)).andExpect(status().isForbidden());
        put(outsider(stranger), "/api/v1/facts/" + fact, json("text", "x", "version", 0)).andExpect(status().isNotFound());
        post(outsider(stranger), evidence(caseId), json("name", "x", "role", "REQUIRED")).andExpect(status().isNotFound());
        post(viewerOf(stranger), evidence(caseId), json("name", "x", "role", "REQUIRED")).andExpect(status().isForbidden());
        put(viewerOf(stranger), "/api/v1/evidence/" + evidence, json("name", "x", "role", "REQUIRED", "version", 0))
                .andExpect(status().isForbidden());
        put(outsider(stranger), "/api/v1/evidence/" + evidence, json("name", "x", "role", "REQUIRED", "version", 0))
                .andExpect(status().isNotFound());
        post(viewerOf(stranger), "/api/v1/evidence/" + evidence + "/links", json("factId", fact)).andExpect(status().isForbidden());
        post(outsider(stranger), "/api/v1/evidence/" + evidence + "/links", json("factId", fact)).andExpect(status().isNotFound());
        post(viewerOf(stranger), "/api/v1/evidence/" + evidence + "/followups", json("taskId", UUID.randomUUID()))
                .andExpect(status().isForbidden());
        post(outsider(stranger), "/api/v1/evidence/" + evidence + "/followups", json("taskId", UUID.randomUUID()))
                .andExpect(status().isNotFound());
        delete(outsider(stranger), "/api/v1/evidence/" + evidence + "/links/" + UUID.randomUUID()).andExpect(status().isNotFound());
        delete(viewerOf(stranger), "/api/v1/evidence/" + evidence + "/links/" + UUID.randomUUID()).andExpect(status().isForbidden());
        get(with(Permission.CASE_VIEW), facts(caseId)).andExpect(status().isForbidden());
        get(with(Permission.EVIDENCE_VIEW), "/api/v1/cases/" + caseId + "/document-usage").andExpect(status().isForbidden());
        post(with(Permission.EVIDENCE_VIEW), evidence(caseId), json("name", "x", "role", "REQUIRED")).andExpect(status().isForbidden());
        get(everything(), "/api/v1/evidence/" + UUID.randomUUID() + "/links").andExpect(status().isMethodNotAllowed());
        put(everything(), "/api/v1/evidence/" + UUID.randomUUID(), json("name", "x", "role", "REQUIRED", "version", 0))
                .andExpect(status().isNotFound());
        get(everything(), facts(UUID.randomUUID())).andExpect(status().isNotFound());
        assertThat(count("select count(*) from evidence_item")).isEqualTo(1);
    }
}
