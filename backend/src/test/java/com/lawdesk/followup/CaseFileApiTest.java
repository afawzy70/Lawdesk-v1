package com.lawdesk.followup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.lawdesk.security.Permission;

/**
 * LD-053, requirements section 21, scenarios S1/S12: the executive summary assembled from stored data and the unified
 * timeline of past and future events, each linked to its source record; closing a case keeps its hearings and history.
 */
class CaseFileApiTest extends Wp3TestSupport {

    private static final Permission[] ALL_AREAS = {Permission.CASE_VIEW, Permission.HEARING_VIEW, Permission.JUDGMENT_VIEW,
        Permission.RISK_VIEW, Permission.INFO_REQUEST_VIEW, Permission.TASK_VIEW, Permission.CASE_STATUS_CHANGE,
        Permission.ASSIGN_CASE};

    private UUID specialist;
    private UUID caseId;
    private UUID proceeding;
    private RequestPostProcessor reader;

    @BeforeEach
    void aCaseWithItsHistory() {
        specialist = user("Specialist");
        caseId = legalCase(specialist, null);
        reader = staff(specialist, ALL_AREAS);
        UUID court = jdbc.queryForObject("""
                insert into court (code, name_ar, name_en, level, active, created_by, updated_by)
                values ('WP3-C', 'محكمة', 'Court', 'FIRST_INSTANCE', true, gen_random_uuid(), gen_random_uuid()) returning id""",
                UUID.class);
        proceeding = jdbc.queryForObject("""
                insert into proceeding (case_id, stage, court_id, court_case_number, court_case_year, filing_date, created_by, updated_by)
                values (?, 'FIRST_INSTANCE', ?, '77', 2026, '2026-01-12', ?, ?) returning id""", UUID.class, caseId, court,
                specialist, specialist);
    }

    private UUID hearing(LocalDate date, String status, String decision) {
        boolean scheduled = status.equals("SCHEDULED");
        return jdbc.queryForObject("""
                insert into hearing (case_id, proceeding_id, hearing_date, purpose, status, decision, result_recorded_at,
                                     result_recorded_by, created_by, updated_by)
                values (?, ?, ?, 'PLEADING', ?, ?, case when ? then null else now() end, case when ? then null else cast(? as uuid) end, ?, ?)
                returning id""", UUID.class, caseId, proceeding, date, status, decision, scheduled, scheduled,
                specialist.toString(), specialist, specialist);
    }

    private void event(String type, String summary) {
        jdbc.update("insert into case_event (case_id, type, summary, created_by) values (?, ?, ?, ?)", caseId, type, summary,
                specialist);
    }

    // --- executive summary (21.1) -----------------------------------------------------------------------------------------

    @Test
    void theSummaryIsAssembledFromWhatIsStored() throws Exception {
        jdbc.update("update legal_case set subject = 'Challenge of a decision', authority_position = 'DEFENDANT', claimed_amount = 12500.500 where id = ?", caseId);
        UUID party = jdbc.queryForObject("insert into party (kind, name, created_by, updated_by) values ('PERSON', 'Plaintiff Ali', gen_random_uuid(), gen_random_uuid()) returning id", UUID.class);
        jdbc.update("insert into case_party (case_id, party_id, role, created_by) values (?, ?, 'OPPONENT', ?)", caseId, party, specialist);
        jdbc.update("insert into case_org_involvement (case_id, sector_name_snapshot, department_name_snapshot, is_primary, created_by, updated_by) values (?, 'Services', 'Admin affairs', true, ?, ?)", caseId, specialist, specialist);
        hearing(today().minusDays(10), "ADJOURNED", "Adjourned for the reply");
        hearing(today().minusDays(3), "HELD", "Reserved for judgment");
        UUID next = hearing(today().plusDays(9), "SCHEDULED", null);
        jdbc.update("insert into judgment (proceeding_id, judgment_date, outcome, summary, final, created_by, updated_by) values (?, ?, 'PARTIAL', 'Partly accepted', false, ?, ?)", proceeding, today().minusDays(1), specialist, specialist);
        event("DOCUMENT_ATTACHED", "Document attached");
        jdbc.update("insert into risk_assessment (case_id, version_no, level, level_reasons, potential_loss, trigger_kind, assessed_by) values (?, 1, 'HIGH', 'r', 4000.125, 'MANUAL', ?)", caseId, specialist);
        jdbc.update("insert into info_request (case_id, request_no, subject, status, due_on, created_by, updated_by) values (?, 'IR-2026-90001', 'Missing file', 'SENT', ?, ?, ?)", caseId, today().minusDays(2), specialist, specialist);
        jdbc.update("insert into task (case_id, title, assignee_id, due_date, created_by, updated_by) values (?, 'Prepare memo', ?, ?, ?, ?)", caseId, specialist, today().minusDays(1), specialist, specialist);

        mvc.perform(getJson(reader, "/api/v1/cases/" + caseId + "/summary")).andExpect(status().isOk())
                .andExpect(jsonPath("$.caseId").value(caseId.toString()))
                .andExpect(jsonPath("$.subject").value("Challenge of a decision"))
                .andExpect(jsonPath("$.authorityPosition").value("DEFENDANT"))
                .andExpect(jsonPath("$.claimedAmount").value("12500.500"))
                .andExpect(jsonPath("$.primaryMember.displayName").value("Specialist"))
                .andExpect(jsonPath("$.parties[0].name").value("Plaintiff Ali"))
                .andExpect(jsonPath("$.parties[0].role").value("OPPONENT"))
                .andExpect(jsonPath("$.involvedUnits[0].name").value("Services / Admin affairs"))
                .andExpect(jsonPath("$.involvedUnits[0].primary").value(true))
                .andExpect(jsonPath("$.hearings.total").value(3))
                .andExpect(jsonPath("$.hearings.adjourned").value(1))
                .andExpect(jsonPath("$.hearings.held").value(1))
                .andExpect(jsonPath("$.hearings.lastHearing.decision").value("Reserved for judgment"))
                .andExpect(jsonPath("$.hearings.nextHearing.id").value(next.toString()))
                .andExpect(jsonPath("$.lastJudgment.outcome").value("PARTIAL"))
                .andExpect(jsonPath("$.lastProcedure.type").value("DOCUMENT_ATTACHED"))
                .andExpect(jsonPath("$.risk.level").value("HIGH"))
                .andExpect(jsonPath("$.risk.potentialLoss").value("4000.125"))
                .andExpect(jsonPath("$.infoRequests.open").value(1))
                .andExpect(jsonPath("$.infoRequests.overdue").value(1))
                .andExpect(jsonPath("$.infoRequests.pending[0].subject").value("Missing file"))
                .andExpect(jsonPath("$.tasks.open").value(1))
                .andExpect(jsonPath("$.tasks.overdue").value(1))
                .andExpect(jsonPath("$.links.count").value(0))
                .andExpect(jsonPath("$.pendingModules", Matchers.hasItems("CLAIMS", "DEFENSES", "EVIDENCE", "DEADLINES",
                        "ENFORCEMENT")));
    }

    @Test
    void anEmptyCaseSaysWhatIsNotRecordedInsteadOfInventingIt() throws Exception {
        mvc.perform(getJson(reader, "/api/v1/cases/" + caseId + "/summary")).andExpect(status().isOk())
                .andExpect(jsonPath("$.hearings.total").value(0)).andExpect(jsonPath("$.hearings.nextHearing").doesNotExist())
                .andExpect(jsonPath("$.lastJudgment").doesNotExist()).andExpect(jsonPath("$.risk").doesNotExist())
                .andExpect(jsonPath("$.authorityPosition").doesNotExist()).andExpect(jsonPath("$.infoRequests.open").value(0))
                .andExpect(jsonPath("$.parties.length()").value(0));
    }

    @Test
    void theSummaryUpdatesWithTheData() throws Exception {
        mvc.perform(getJson(reader, "/api/v1/cases/" + caseId + "/summary")).andExpect(jsonPath("$.hearings.total").value(0));
        hearing(today().plusDays(2), "SCHEDULED", null);
        mvc.perform(getJson(reader, "/api/v1/cases/" + caseId + "/summary")).andExpect(jsonPath("$.hearings.total").value(1))
                .andExpect(jsonPath("$.hearings.nextHearing.hearingDate").value(today().plusDays(2).toString()));
    }

    @Test
    void sectionsOfAreasTheUserMayNotOpenAreLeftOut() throws Exception {
        hearing(today().plusDays(2), "SCHEDULED", null);
        jdbc.update("insert into risk_assessment (case_id, version_no, level, level_reasons, trigger_kind, assessed_by) values (?, 1, 'LOW', 'r', 'MANUAL', ?)", caseId, specialist);
        mvc.perform(getJson(staff(specialist, Permission.CASE_VIEW), "/api/v1/cases/" + caseId + "/summary"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.hearings").doesNotExist())
                .andExpect(jsonPath("$.risk").doesNotExist()).andExpect(jsonPath("$.infoRequests").doesNotExist())
                .andExpect(jsonPath("$.tasks").doesNotExist()).andExpect(jsonPath("$.lastJudgment").doesNotExist())
                .andExpect(jsonPath("$.title").isString());
    }

    @Test
    void thePendingLinksAreCountedAndOnesToUnseenCasesOnlyAsAnumber() throws Exception {
        UUID visible = legalCase(specialist, null);
        UUID hidden = legalCase(user("Other"), null);
        jdbc.update("insert into case_link (case_id, other_case_id, kind, reason, created_by, updated_by) values (?, ?, 'SUBJECT_LINK', 'r', ?, ?)", caseId, visible, specialist, specialist);
        jdbc.update("insert into case_link (case_id, other_case_id, kind, reason, created_by, updated_by) values (?, ?, 'REFERRAL', 'r', ?, ?)", hidden, caseId, specialist, specialist);
        mvc.perform(getJson(outsider(specialist, ALL_AREAS), "/api/v1/cases/" + caseId + "/summary"))
                .andExpect(jsonPath("$.links.count").value(1)).andExpect(jsonPath("$.links.hiddenCount").value(1));
    }

    @Test
    void anOutsiderCannotSeeTheSummaryOrTheTimeline() throws Exception {
        RequestPostProcessor nobody = outsider(user("Nobody"), ALL_AREAS);
        mvc.perform(getJson(nobody, "/api/v1/cases/" + caseId + "/summary")).andExpect(status().isNotFound());
        mvc.perform(getJson(nobody, "/api/v1/cases/" + caseId + "/unified-timeline")).andExpect(status().isNotFound());
        mvc.perform(getJson(staff(specialist), "/api/v1/cases/" + caseId + "/summary")).andExpect(status().isForbidden());
    }

    // --- unified timeline (21.2, 21.3) ------------------------------------------------------------------------------------------

    @Test
    void thePastAndFutureAreOneTimelineEachEntryLinkedToItsSourceAndMarkedFuture() throws Exception {
        UUID held = hearing(today().minusDays(5), "HELD", "Reserved");
        UUID upcoming = hearing(today().plusDays(7), "SCHEDULED", null);
        event("CASE_CREATED", "Case created");
        UUID request = jdbc.queryForObject("insert into info_request (case_id, request_no, subject, status, sent_on, due_on, created_by, updated_by) values (?, 'IR-2026-90002', 'File', 'SENT', ?, ?, ?, ?) returning id", UUID.class, caseId, today().minusDays(4), today().plusDays(3), specialist, specialist);
        jdbc.update("insert into info_reply (request_id, case_id, received_on, completeness, created_by) values (?, ?, ?, 'PARTIAL', ?)", request, caseId, today().minusDays(1), specialist);
        UUID risk = jdbc.queryForObject("insert into risk_assessment (case_id, version_no, level, level_reasons, trigger_kind, assessed_by) values (?, 1, 'LOW', 'r', 'MANUAL', ?) returning id", UUID.class, caseId, specialist);
        jdbc.update("insert into judgment (proceeding_id, judgment_date, outcome, final, created_by, updated_by) values (?, ?, 'AGAINST', false, ?, ?)", proceeding, today().minusDays(2), specialist, specialist);

        mvc.perform(getJson(reader, "/api/v1/cases/" + caseId + "/unified-timeline?size=50")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.refType=='HEARING' && @.refId=='" + upcoming + "')].isFuture").value(true))
                .andExpect(jsonPath("$.items[?(@.refType=='HEARING' && @.refId=='" + held + "')].isFuture").value(false))
                .andExpect(jsonPath("$.items[?(@.refType=='RISK_ASSESSMENT' && @.refId=='" + risk + "')].kind").value("RISK"))
                .andExpect(jsonPath("$.items[?(@.kind=='INFO_REQUEST_DUE')].isFuture").value(true))
                .andExpect(jsonPath("$.items[?(@.kind=='INFO_REPLY')].refType").value("INFO_REQUEST"))
                .andExpect(jsonPath("$.items[?(@.kind=='JUDGMENT')].summary").isNotEmpty())
                .andExpect(jsonPath("$.items[?(@.kind=='EVENT')].summary").value("Case created"))
                .andExpect(jsonPath("$.deadlinesAvailable").value(false));
    }

    @Test
    void itIsOrderedByDateAndTheFutureComesFirstNewestFirstOrOldestFirstOnRequest() throws Exception {
        hearing(today().minusDays(30), "HELD", null);
        hearing(today().plusDays(30), "SCHEDULED", null);
        hearing(today().plusDays(5), "SCHEDULED", null);
        var newest = mvc.perform(getJson(reader, "/api/v1/cases/" + caseId + "/unified-timeline?types=HEARING"))
                .andExpect(jsonPath("$.items.length()").value(3)).andReturn();
        List<String> dates = com.jayway.jsonpath.JsonPath.read(body(newest), "$.items[*].at");
        assertThat(dates).isSortedAccordingTo(java.util.Comparator.reverseOrder());
        var oldest = mvc.perform(getJson(reader, "/api/v1/cases/" + caseId + "/unified-timeline?types=HEARING&order=asc"))
                .andReturn();
        List<String> ascending = com.jayway.jsonpath.JsonPath.read(body(oldest), "$.items[*].at");
        assertThat(ascending).isSorted();
    }

    @Test
    void itFiltersByTypeAndPages() throws Exception {
        hearing(today().plusDays(3), "SCHEDULED", null);
        event("CASE_UPDATED", "Edited");
        event("CASE_UPDATED", "Edited again");
        mvc.perform(getJson(reader, "/api/v1/cases/" + caseId + "/unified-timeline?types=EVENT"))
                .andExpect(jsonPath("$.totalItems").value(2));
        mvc.perform(getJson(reader, "/api/v1/cases/" + caseId + "/unified-timeline?types=EVENT,HEARING&size=2"))
                .andExpect(jsonPath("$.items.length()").value(2)).andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(getJson(reader, "/api/v1/cases/" + caseId + "/unified-timeline?types=NOT_A_TYPE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void overdueRepliesAndHearingsWithoutAResultAreMarkedAsNeedingAction() throws Exception {
        hearing(today().minusDays(4), "SCHEDULED", null);
        jdbc.update("insert into info_request (case_id, request_no, subject, status, sent_on, due_on, created_by, updated_by) values (?, 'IR-2026-90003', 'Late', 'SENT', ?, ?, ?, ?)", caseId, today().minusDays(9), today().minusDays(2), specialist, specialist);
        mvc.perform(getJson(reader, "/api/v1/cases/" + caseId + "/unified-timeline?size=50"))
                .andExpect(jsonPath("$.items[?(@.kind=='HEARING')].needsAction").value(true))
                .andExpect(jsonPath("$.items[?(@.kind=='INFO_REQUEST_DUE')].needsAction").value(true));
    }

    @Test
    void entriesOfAreasTheUserMayNotOpenAreLeftOut() throws Exception {
        hearing(today().plusDays(3), "SCHEDULED", null);
        event("CASE_UPDATED", "Edited");
        mvc.perform(getJson(staff(specialist, Permission.CASE_VIEW), "/api/v1/cases/" + caseId + "/unified-timeline"))
                .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].kind").value("EVENT"));
    }

    // --- S12: closing a case keeps its hearings and history ------------------------------------------------------------------------

    @Test
    void closingTheCaseKeepsItsHearingsItsHistoryAndItsSummary() throws Exception {
        hearing(today().minusDays(6), "ADJOURNED", "Adjourned");
        hearing(today().minusDays(1), "HELD", "Reserved");
        jdbc.update("insert into info_request (case_id, request_no, subject, status, created_by, updated_by) values (?, 'IR-2026-90004', 'Open', 'DRAFT', ?, ?)", caseId, specialist, specialist);
        int hearingsBefore = count("select count(*) from hearing where case_id = ?", caseId);
        int eventsBefore = count("select count(*) from case_event where case_id = ?", caseId);

        mvc.perform(postJson(reader, "/api/v1/cases/" + caseId + "/status", json("status", "CLOSED", "version", 0)))
                .andExpect(status().isOk());

        assertThat(count("select count(*) from hearing where case_id = ?", caseId)).isEqualTo(hearingsBefore);
        assertThat(count("select count(*) from case_event where case_id = ?", caseId)).isGreaterThan(eventsBefore);
        mvc.perform(getJson(reader, "/api/v1/cases/" + caseId + "/hearings")).andExpect(jsonPath("$.totalItems").value(2));
        mvc.perform(getJson(reader, "/api/v1/cases/" + caseId + "/summary")).andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.hearings.total").value(2)).andExpect(jsonPath("$.infoRequests.open").value(0));
        mvc.perform(getJson(reader, "/api/v1/cases/" + caseId + "/unified-timeline?types=HEARING"))
                .andExpect(jsonPath("$.totalItems").value(2));
        mvc.perform(getJson(reader, "/api/v1/cases/" + caseId + "/unified-timeline?types=EVENT"))
                .andExpect(jsonPath("$.items[0].summary").value(Matchers.containsString("OPEN -> CLOSED")));
    }
}
