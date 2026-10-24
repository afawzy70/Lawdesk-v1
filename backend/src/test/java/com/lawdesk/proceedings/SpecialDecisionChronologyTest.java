package com.lawdesk.proceedings;

import static com.lawdesk.security.TestActors.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.lawdesk.security.Permission;

/**
 * QAD-024 applied to LD-023: a special decision is never dated before its proceeding's filing
 * ({@code DECISION_BEFORE_FILING}), whichever of the two is edited, and also under concurrent edits.
 */
class SpecialDecisionChronologyTest extends JudicialApiTestSupport {

    private static final String BROKEN_DECISIONS = """
            select count(*) from special_decision d join proceeding p on p.id = d.proceeding_id
            where d.decision_date < p.filing_date""";

    private record Filed(UUID caseId, UUID court, UUID proceeding) {
    }

    private Filed filed(String suffix) throws Exception {
        UUID caseId = createCase("Decision chronology " + suffix);
        UUID court = createCourt("FI-D" + suffix, "FIRST_INSTANCE");
        return new Filed(caseId, court, recordProceeding(caseId, proceeding("FIRST_INSTANCE", court, "7", 2026,
                "2026-01-02", null)));
    }

    @Test
    void theFilingDateCannotMoveAfterASpecialDecision() throws Exception {
        Filed f = filed("1");
        recordDecision(f.proceeding(), "2026-01-05");
        Map<String, Object> before = proceedingRow(f.proceeding());

        mvc.perform(putProceeding(f, "2026-01-08", 0))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("DECISION_BEFORE_FILING"));
        assertThat(proceedingRow(f.proceeding())).isEqualTo(before);

        // Up to the decision's own day is fine.
        mvc.perform(putProceeding(f, "2026-01-05", 0))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        assertConsistent();
    }

    @Test
    void aDecisionRecordedWhileTheFilingDateMovesLaterIsRefused() throws Exception {
        Filed f = filed("2");

        List<Integer> statuses = race("update", "proceeding",
                () -> mvc.perform(putProceeding(f, "2026-01-08", 0)).andReturn(),
                () -> mvc.perform(postDecision(f.proceeding(), "2026-01-05")).andReturn());

        assertThat(statuses).containsExactly(200, 422);
        assertThat(rows("special_decision")).isZero();
        assertConsistent();
    }

    @Test
    void theFilingDateMovedWhileADecisionIsRecordedIsRefused() throws Exception {
        Filed f = filed("3");

        List<Integer> statuses = race("insert", "special_decision",
                () -> mvc.perform(postDecision(f.proceeding(), "2026-01-05")).andReturn(),
                () -> mvc.perform(putProceeding(f, "2026-01-08", 0)).andReturn());

        assertThat(statuses).containsExactly(201, 422);
        assertThat(jdbc.queryForObject("select filing_date::text from proceeding where id = ?", String.class,
                f.proceeding())).isEqualTo("2026-01-02");
        assertConsistent();
    }

    @Test
    void aDecisionMovedEarlierWhileTheFilingDateMovesLaterIsRefused() throws Exception {
        Filed f = filed("4");
        UUID decision = recordDecision(f.proceeding(), "2026-01-20");

        List<Integer> statuses = race("update", "proceeding",
                () -> mvc.perform(putProceeding(f, "2026-01-08", 0)).andReturn(),
                () -> mvc.perform(putDecision(decision, "2026-01-05", 0)).andReturn());

        assertThat(statuses).containsExactly(200, 422);
        assertThat(jdbc.queryForObject("select version from special_decision where id = ?", Long.class, decision))
                .isZero();
        assertConsistent();
    }

    private void assertConsistent() {
        assertThat(jdbc.queryForObject(BROKEN_DECISIONS, Integer.class)).isZero();
    }

    private MockHttpServletRequestBuilder postDecision(UUID proceeding, String date) {
        return post("/api/v1/proceedings/" + proceeding + "/special-decisions")
                .with(as(CLERK, "clerk", Permission.JUDGMENT_MANAGE)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"decisionType":"URGENT_MEASURE","decisionDate":"%s","effect":"GRANTED"}""".formatted(date));
    }

    private UUID recordDecision(UUID proceeding, String date) throws Exception {
        MvcResult result = mvc.perform(postDecision(proceeding, date)).andExpect(status().isCreated()).andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    private MockHttpServletRequestBuilder putDecision(UUID id, String date, long version) {
        return put("/api/v1/special-decisions/" + id).with(as(CLERK, "clerk", Permission.JUDGMENT_MANAGE))
                .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"decisionType":"URGENT_MEASURE","decisionDate":"%s","effect":"GRANTED","version":%d}"""
                        .formatted(date, version));
    }

    private MockHttpServletRequestBuilder putProceeding(Filed f, String filingDate, long version) {
        return put("/api/v1/proceedings/" + f.proceeding()).with(as(CLERK, "clerk", Permission.PROCEEDING_MANAGE))
                .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"courtId":"%s","courtCaseNumber":"%s","courtCaseYear":2026,"filingDate":"%s","version":%d}"""
                        .formatted(f.court(), "7", filingDate, version));
    }

    private Map<String, Object> proceedingRow(UUID id) {
        return jdbc.queryForMap("select filing_date, court_case_number, version, updated_at, updated_by "
                + "from proceeding where id = ?", id);
    }
}
