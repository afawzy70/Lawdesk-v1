package com.lawdesk.deadlines;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.lawdesk.security.Permission;

/**
 * LD-054, requirements section 10, owner decision D6, scenario S14: a deadline typed by a user is a MANUAL deadline until a
 * competent member approves it; a computed date exists only when an approved rule exists, is PROPOSED, and becomes official
 * only on approval. Rules and holidays are TEST-ONLY data made by the tests: the application has none of its own.
 */
class DeadlineApiTest extends DeadlineTestSupport {

    private UUID member;
    private UUID supervisor;
    private UUID caseId;
    private RequestPostProcessor specialist;
    private RequestPostProcessor competent;

    @BeforeEach
    void aCaseWithItsMembers() {
        member = user("Mona Specialist");
        supervisor = user("Hala Supervisor");
        caseId = legalCase(member, null);
        specialist = staff(member, SPECIALIST);
        competent = staff(supervisor, COMPETENT);
    }

    private String path() {
        return "/api/v1/cases/" + caseId + "/deadlines";
    }

    private String manual(String action, Object dueOn) {
        return json("sourceType", "TEXT", "sourceText", "Notice served on the authority", "startsOn", today().minusDays(60).toString(),
                "dueOn", dueOn, "action", action, "ownerId", member, "notes", "Check the file");
    }

    private UUID recorded(String action, Object dueOn) throws Exception {
        MvcResult result = mvc.perform(postJson(specialist, path(), manual(action, dueOn)))
                .andExpect(status().isCreated()).andReturn();
        return idOf(result);
    }

    private long version(UUID id) {
        return jdbc.queryForObject("select version from deadline where id = ?", Long.class, id);
    }

    private void approve(UUID id) throws Exception {
        mvc.perform(postJson(competent, "/api/v1/deadlines/" + id + "/approve", json("version", version(id))))
                .andExpect(status().isOk());
    }

    // --- the database starts empty (D6) ------------------------------------------------------------------------

    @Test
    void theApplicationShipsNoRuleNoHolidayAndNoReminderSetting() throws Exception {
        assertThat(count("select count(*) from deadline_rule")).isZero();
        assertThat(count("select count(*) from official_holiday")).isZero();
        assertThat(count("select count(*) from deadline_reminder_setting")).isZero();
        mvc.perform(getJson(specialist, "/api/v1/deadline-rules")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
        mvc.perform(getJson(specialist, "/api/v1/official-holidays")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
        mvc.perform(getJson(specialist, "/api/v1/deadline-settings/reminders")).andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false)).andExpect(jsonPath("$.leadDays").doesNotExist());
    }

    // --- (a) a manual deadline ---------------------------------------------------------------------------------

    @Test
    void aManualDeadlineIsNotOfficialUntilACompetentMemberApprovesIt() throws Exception {
        String due = today().plusDays(30).toString();
        mvc.perform(postJson(specialist, path(), manual("File the appeal", due)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.calcStatus").value("MANUAL"))
                .andExpect(jsonPath("$.official").value(false))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.sourceType").value("TEXT"))
                .andExpect(jsonPath("$.sourceText").value("Notice served on the authority"))
                .andExpect(jsonPath("$.dueOn").value(due))
                .andExpect(jsonPath("$.action").value("File the appeal"))
                .andExpect(jsonPath("$.ownerName").value("Mona Specialist"))
                .andExpect(jsonPath("$.approvedById").doesNotExist())
                .andExpect(jsonPath("$.daysRemaining").value(30))
                .andExpect(jsonPath("$.overdue").value(false));
        assertThat(count("select count(*) from case_event where case_id = ? and type = 'DEADLINE_CREATED'", caseId)).isEqualTo(1);
    }

    @Test
    void approvalKeepsWhoAndWhenAndNeedsThePermission() throws Exception {
        UUID id = recorded("File the appeal", today().plusDays(30).toString());
        String approvePath = "/api/v1/deadlines/" + id + "/approve";
        // The specialist who typed it holds no approval permission: refused by permission.
        mvc.perform(postJson(specialist, approvePath, json("version", 0))).andExpect(status().isForbidden());
        // Seeing the case is not enough: the approver must also be able to change it (404 for who cannot see, 403 for who only sees).
        mvc.perform(postJson(viewer(UUID.randomUUID(), COMPETENT), approvePath, json("version", 0)))
                .andExpect(status().isForbidden());
        mvc.perform(postJson(outsider(UUID.randomUUID(), COMPETENT), approvePath, json("version", 0)))
                .andExpect(status().isNotFound());
        assertThat(count("select count(*) from deadline where calc_status = 'APPROVED'")).isZero();

        mvc.perform(postJson(competent, approvePath, json("version", 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calcStatus").value("APPROVED"))
                .andExpect(jsonPath("$.official").value(true))
                .andExpect(jsonPath("$.approvedById").value(supervisor.toString()))
                .andExpect(jsonPath("$.approvedByName").value("Hala Supervisor"))
                .andExpect(jsonPath("$.approvedAt").exists())
                .andExpect(jsonPath("$.version").value(1));
        assertThat(count("select count(*) from audit_event where action = 'DEADLINE_APPROVE' and target_id = ?",
                id.toString())).isEqualTo(1);
        assertThat(count("select count(*) from case_event where case_id = ? and type = 'DEADLINE_APPROVED'", caseId)).isEqualTo(1);
        // Approving twice is refused with a stable rule code.
        mvc.perform(postJson(competent, approvePath, json("version", 1))).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("DEADLINE_ALREADY_APPROVED"));
        // A stale version is a conflict.
        UUID second = recorded("Another", today().plusDays(5).toString());
        mvc.perform(postJson(competent, "/api/v1/deadlines/" + second + "/approve", json("version", 9)))
                .andExpect(status().isConflict());
    }

    @Test
    void aPeriodAloneIsAValidRecordButCannotBeApprovedWithoutADueDate() throws Exception {
        MvcResult result = mvc.perform(postJson(specialist, path(), json("sourceType", "TEXT", "sourceText", "Letter of 1 March",
                "periodDays", 60, "action", "Reply to the letter")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.periodDays").value(60))
                .andExpect(jsonPath("$.dueOn").doesNotExist())
                .andExpect(jsonPath("$.daysRemaining").doesNotExist())
                .andExpect(jsonPath("$.overdue").value(false)).andReturn();
        UUID id = idOf(result);
        mvc.perform(postJson(competent, "/api/v1/deadlines/" + id + "/approve", json("version", 0)))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.rule").value("DEADLINE_NO_DUE_DATE"));
        // The system never computes the date of a manual deadline from its period: the user supplies it.
        assertThat(count("select count(*) from deadline where id = ? and due_on is null", id)).isEqualTo(1);
    }

    @Test
    void refusesWhatIsNotAValidDeadline() throws Exception {
        String due = today().plusDays(3).toString();
        mvc.perform(postJson(specialist, path(), json("sourceType", "TEXT", "sourceText", "x", "dueOn", due)))
                .andExpect(status().isBadRequest());
        mvc.perform(postJson(specialist, path(), json("sourceType", "TEXT", "sourceText", "x", "dueOn", due, "action", "  ")))
                .andExpect(status().isBadRequest());
        mvc.perform(postJson(specialist, path(), json("sourceType", "TEXT", "dueOn", due, "action", "a")))
                .andExpect(status().isBadRequest());
        mvc.perform(postJson(specialist, path(), json("sourceType", "TEXT", "sourceText", "x", "action", "a")))
                .andExpect(status().isBadRequest());
        mvc.perform(postJson(specialist, path(), json("sourceType", "TEXT", "sourceText", "x", "dueOn", due, "action", "a",
                "periodDays", 0))).andExpect(status().isBadRequest());
        mvc.perform(postJson(specialist, path(), json("sourceType", "TEXT", "sourceText", "x", "dueOn", due, "action", "a",
                "ownerId", UUID.randomUUID()))).andExpect(status().isBadRequest());
        mvc.perform(postJson(specialist, path(), json("sourceType", "TEXT", "sourceText", "x", "action", "a",
                "startsOn", today().plusDays(5).toString(), "dueOn", today().toString()))).andExpect(status().isBadRequest());
        mvc.perform(postJson(specialist, path(), json("sourceType", "JUDGMENT", "dueOn", due, "action", "a")))
                .andExpect(status().isBadRequest());
        mvc.perform(postJson(specialist, path(), json("sourceType", "JUDGMENT", "sourceId", UUID.randomUUID(),
                "sourceText", "x", "dueOn", due, "action", "a"))).andExpect(status().isBadRequest());
        mvc.perform(postJson(specialist, path(), json("sourceType", "TEXT", "sourceId", UUID.randomUUID(), "sourceText", "x",
                "dueOn", due, "action", "a"))).andExpect(status().isBadRequest());
        // A source that is not in this case (or does not exist) is a business refusal.
        mvc.perform(postJson(specialist, path(), json("sourceType", "JUDGMENT", "sourceId", UUID.randomUUID(), "dueOn", due,
                "action", "a"))).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("DEADLINE_SOURCE_NOT_IN_CASE"));
        assertThat(count("select count(*) from deadline")).isZero();
    }

    @Test
    void aDeadlineRunsFromAJudgmentAHearingAProceedingOrATaskOfTheSameCaseOnly() throws Exception {
        UUID proceeding = proceeding(caseId);
        UUID judgment = judgment(proceeding, LocalDate.of(2026, 3, 1));
        UUID hearing = hearing(caseId, proceeding, LocalDate.of(2026, 2, 1));
        UUID task = task(caseId, member);
        String due = today().plusDays(20).toString();
        for (Object[] source : new Object[][] {{"JUDGMENT", judgment, "2026-03-01"}, {"HEARING", hearing, "2026-02-01"},
            {"PROCEEDING", proceeding, "100/2026"}, {"TASK", task, "Collect the file"}}) {
            mvc.perform(postJson(specialist, path(), json("sourceType", source[0], "sourceId", source[1], "dueOn", due,
                            "action", "Act on " + source[0])))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.sourceType").value(source[0]))
                    .andExpect(jsonPath("$.sourceId").value(source[1].toString()))
                    .andExpect(jsonPath("$.sourceLabel").value(source[2]));
        }
        // Another case's judgment is not a source for this case.
        UUID otherCase = legalCase(member, null);
        UUID otherJudgment = judgment(proceeding(otherCase), LocalDate.of(2026, 3, 2));
        mvc.perform(postJson(specialist, path(), json("sourceType", "JUDGMENT", "sourceId", otherJudgment, "dueOn", due,
                "action", "a"))).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("DEADLINE_SOURCE_NOT_IN_CASE"));
    }

    @Test
    void editingTheDatesOfAnApprovedDeadlineNeedsAReasonAndSendsItBackForApproval() throws Exception {
        UUID id = recorded("File the appeal", today().plusDays(30).toString());
        approve(id);
        String newDue = today().plusDays(45).toString();
        String edit = json("dueOn", newDue, "action", "File the appeal", "ownerId", member, "version", version(id));
        mvc.perform(putJson(specialist, "/api/v1/deadlines/" + id, edit)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("DEADLINE_CHANGE_REASON_REQUIRED"));
        mvc.perform(putJson(specialist, "/api/v1/deadlines/" + id, json("dueOn", newDue, "action", "File the appeal",
                        "ownerId", member, "changeReason", "Notice was served later", "version", version(id))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dueOn").value(newDue))
                .andExpect(jsonPath("$.calcStatus").value("MANUAL"))
                .andExpect(jsonPath("$.official").value(false))
                .andExpect(jsonPath("$.approvedById").doesNotExist())
                .andExpect(jsonPath("$.changeReason").value("Notice was served later"));
        // Changing only the action or the notes keeps an approval.
        approve(id);
        mvc.perform(putJson(specialist, "/api/v1/deadlines/" + id, json("dueOn", newDue, "action", "File the appeal today",
                        "ownerId", member, "version", version(id))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.calcStatus").value("APPROVED"))
                .andExpect(jsonPath("$.approvedByName").value("Hala Supervisor"));
    }

    // --- completion, missing and reopening are done only by a user -------------------------------------------

    @Test
    void completionNeedsAUserAndTakesAnOptionalProofDocumentOfTheSameCase() throws Exception {
        UUID id = recorded("File the appeal", today().plusDays(10).toString());
        approve(id);
        UUID proof = document(caseId);
        UUID foreignProof = document(legalCase(member, null));
        String statusPath = "/api/v1/deadlines/" + id + "/status";
        mvc.perform(postJson(specialist, statusPath, json("status", "COMPLETED", "proofDocumentId", foreignProof,
                "version", version(id)))).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("DEADLINE_PROOF_NOT_IN_CASE"));
        mvc.perform(postJson(specialist, statusPath, json("status", "COMPLETED", "completedOn", today().plusDays(1).toString(),
                "version", version(id)))).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("DEADLINE_COMPLETION_IN_FUTURE"));
        mvc.perform(postJson(specialist, statusPath, json("status", "COMPLETED", "proofDocumentId", proof,
                        "version", version(id))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completedOn").value(today().toString()))
                .andExpect(jsonPath("$.proofDocumentId").value(proof.toString()))
                .andExpect(jsonPath("$.proofDocumentTitle").value("Proof of filing"))
                .andExpect(jsonPath("$.overdue").value(false));
        // An edit of a closed deadline is refused until it is reopened.
        mvc.perform(putJson(specialist, "/api/v1/deadlines/" + id, json("dueOn", today().plusDays(11).toString(),
                "action", "x", "version", version(id)))).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("DEADLINE_NOT_OPEN"));
        mvc.perform(postJson(specialist, statusPath, json("status", "MISSED", "version", version(id))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("DEADLINE_TRANSITION_NOT_ALLOWED"));
        mvc.perform(postJson(specialist, statusPath, json("status", "OPEN", "version", version(id))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.completedOn").doesNotExist()).andExpect(jsonPath("$.proofDocumentId").doesNotExist());
        mvc.perform(postJson(specialist, statusPath, json("status", "MISSED", "version", version(id))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("MISSED"));
    }

    @Test
    void anOverdueDeadlineStaysOpenUntilAUserActs() throws Exception {
        UUID id = recorded("Late filing", today().minusDays(3).toString());
        approve(id);
        mvc.perform(getJson(specialist, "/api/v1/deadlines/" + id)).andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.overdue").value(true)).andExpect(jsonPath("$.daysRemaining").value(-3));
        // The reminders and the follow-up run never complete, miss or close it.
        mvc.perform(postJson(competent, "/api/v1/deadlines/reminders/run", "{}")).andExpect(status().isOk());
        assertThat(count("select count(*) from deadline where id = ? and status = 'OPEN'", id)).isEqualTo(1);
    }

    // --- (b) rules and holidays are entered and approved by people ---------------------------------------------

    @Test
    void aRuleIsDraftedByAManagerApprovedByACompetentMemberAndThenFrozen() throws Exception {
        UUID id = draftRule(specialist, "TEST-APPEAL", 30, "CALENDAR", false, false, List.of(), "2000-01-01");
        mvc.perform(getJson(specialist, "/api/v1/deadline-rules/" + id)).andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.legalSource").value("Test-only source for TEST-APPEAL"))
                .andExpect(jsonPath("$.approvedById").doesNotExist());
        // Approval needs the approval permission.
        mvc.perform(postJson(specialist, "/api/v1/deadline-rules/" + id + "/approve", json("version", 0)))
                .andExpect(status().isForbidden());
        mvc.perform(putJson(specialist, "/api/v1/deadline-rules/" + id, json("code", "TEST-APPEAL", "label", "Edited",
                        "periodDays", 15, "countMethod", "CALENDAR", "countsStartDay", true, "rollForward", false,
                        "closedWeekdays", List.of(), "legalSource", "Edited source", "effectiveFrom", "2000-01-01",
                        "version", 0)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.periodDays").value(15))
                .andExpect(jsonPath("$.version").value(1));
        mvc.perform(postJson(competent, "/api/v1/deadline-rules/" + id + "/approve", json("version", 1)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.approvedByName").value("Hala Supervisor")).andExpect(jsonPath("$.approvedAt").exists());
        mvc.perform(putJson(specialist, "/api/v1/deadline-rules/" + id, json("code", "TEST-APPEAL", "label", "Again",
                        "periodDays", 20, "countMethod", "CALENDAR", "countsStartDay", true, "rollForward", false,
                        "closedWeekdays", List.of(), "legalSource", "s", "effectiveFrom", "2000-01-01", "version", 2)))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.rule").value("RULE_NOT_DRAFT"));
        mvc.perform(postJson(competent, "/api/v1/deadline-rules/" + id + "/approve", json("version", 2)))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.rule").value("RULE_NOT_DRAFT"));
        assertThat(count("select count(*) from audit_event where target_id = ? and action like 'DEADLINE_RULE_%'", id.toString()))
                .isEqualTo(3);
    }

    @Test
    void aRuleMustStateItsSourceAndHowItCounts() throws Exception {
        String base = "/api/v1/deadline-rules";
        mvc.perform(postJson(specialist, base, json("code", "T1", "label", "x", "periodDays", 10, "countMethod", "CALENDAR",
                "countsStartDay", false, "rollForward", false, "closedWeekdays", List.of(), "effectiveFrom", "2000-01-01")))
                .andExpect(status().isBadRequest());
        mvc.perform(postJson(specialist, base, json("code", "T1", "label", "x", "periodDays", 10, "countMethod", "CALENDAR",
                "countsStartDay", false, "rollForward", false, "closedWeekdays", List.of(), "legalSource", "  ",
                "effectiveFrom", "2000-01-01"))).andExpect(status().isBadRequest());
        // No period, no counting choice, no default.
        mvc.perform(postJson(specialist, base, json("code", "T1", "label", "x", "countMethod", "CALENDAR", "countsStartDay",
                false, "rollForward", false, "closedWeekdays", List.of(), "legalSource", "s", "effectiveFrom", "2000-01-01")))
                .andExpect(status().isBadRequest());
        mvc.perform(postJson(specialist, base, json("code", "T1", "label", "x", "periodDays", 10, "countMethod", "CALENDAR",
                "rollForward", false, "closedWeekdays", List.of(), "legalSource", "s", "effectiveFrom", "2000-01-01")))
                .andExpect(status().isBadRequest());
        mvc.perform(postJson(specialist, base, json("code", "T1", "label", "x", "periodDays", 10, "countMethod", "CALENDAR",
                "countsStartDay", false, "rollForward", false, "legalSource", "s", "effectiveFrom", "2000-01-01")))
                .andExpect(status().isBadRequest());
        mvc.perform(postJson(specialist, base, json("code", "T1", "label", "x", "periodDays", 10, "countMethod", "CALENDAR",
                "countsStartDay", false, "rollForward", false, "closedWeekdays", List.of(8), "legalSource", "s",
                "effectiveFrom", "2000-01-01"))).andExpect(status().isBadRequest());
        mvc.perform(postJson(specialist, base, json("code", "T1", "label", "x", "periodDays", 10, "countMethod", "WORKING",
                "countsStartDay", false, "rollForward", false, "closedWeekdays", List.of(1, 2, 3, 4, 5, 6, 7),
                "legalSource", "s", "effectiveFrom", "2000-01-01"))).andExpect(status().isBadRequest());
        mvc.perform(postJson(specialist, base, json("code", "bad code!", "label", "x", "periodDays", 10, "countMethod",
                "CALENDAR", "countsStartDay", false, "rollForward", false, "closedWeekdays", List.of(), "legalSource", "s",
                "effectiveFrom", "2000-01-01"))).andExpect(status().isBadRequest());
        assertThat(count("select count(*) from deadline_rule")).isZero();
        // The same code with the same effective date is a conflict; another date is a new version of the rule.
        draftRule(specialist, "T1", 10, "CALENDAR", false, false, List.of(), "2000-01-01");
        mvc.perform(postJson(specialist, base, json("code", "T1", "label", "x", "periodDays", 10, "countMethod", "CALENDAR",
                "countsStartDay", false, "rollForward", false, "closedWeekdays", List.of(), "legalSource", "s",
                "effectiveFrom", "2000-01-01"))).andExpect(status().isConflict());
        draftRule(specialist, "T1", 12, "CALENDAR", false, false, List.of(), "2010-01-01");
    }

    @Test
    void viewersCannotEnterRulesOrHolidaysAndHolidaysAreDraftApprovedRetired() throws Exception {
        RequestPostProcessor reader = viewer(UUID.randomUUID(), VIEW_ONLY);
        mvc.perform(postJson(reader, "/api/v1/official-holidays", json("holidayOn", "2031-05-01", "label", "x",
                "legalSource", "s"))).andExpect(status().isForbidden());
        mvc.perform(postJson(reader, "/api/v1/deadline-rules", "{}")).andExpect(status().isForbidden());
        mvc.perform(putJson(reader, "/api/v1/deadline-settings/reminders", json("leadDays", List.of(1))))
                .andExpect(status().isForbidden());
        MvcResult result = mvc.perform(postJson(specialist, "/api/v1/official-holidays", json("holidayOn", "2031-05-01",
                "label", "Test holiday", "legalSource", "Test-only source"))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT")).andReturn();
        UUID id = idOf(result);
        mvc.perform(postJson(specialist, "/api/v1/official-holidays/" + id + "/approve", json("version", 0)))
                .andExpect(status().isForbidden());
        // One entry per date.
        mvc.perform(postJson(specialist, "/api/v1/official-holidays", json("holidayOn", "2031-05-01", "label", "again",
                "legalSource", "s"))).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("HOLIDAY_DATE_TAKEN"));
        mvc.perform(putJson(specialist, "/api/v1/official-holidays/" + id, json("holidayOn", "2031-05-02", "label",
                "Moved", "legalSource", "s", "version", 0))).andExpect(status().isOk())
                .andExpect(jsonPath("$.holidayOn").value("2031-05-02"));
        mvc.perform(postJson(competent, "/api/v1/official-holidays/" + id + "/approve", json("version", 1)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.approvedByName").value("Hala Supervisor"));
        mvc.perform(putJson(specialist, "/api/v1/official-holidays/" + id, json("holidayOn", "2031-05-03", "label",
                "x", "legalSource", "s", "version", 2))).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("HOLIDAY_NOT_DRAFT"));
        mvc.perform(getJson(specialist, "/api/v1/official-holidays?year=2031&status=APPROVED"))
                .andExpect(jsonPath("$.items", hasSize(1)));
        mvc.perform(getJson(specialist, "/api/v1/official-holidays?year=2030")).andExpect(jsonPath("$.items", hasSize(0)));
        mvc.perform(getJson(specialist, "/api/v1/official-holidays?status=NONSENSE")).andExpect(status().isBadRequest());
        mvc.perform(postJson(competent, "/api/v1/official-holidays/" + id + "/retire", json("version", 2)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RETIRED"));
        // A retired date can be entered again, correctly.
        mvc.perform(postJson(specialist, "/api/v1/official-holidays", json("holidayOn", "2031-05-02", "label", "Right",
                "legalSource", "s"))).andExpect(status().isCreated());
    }

    // --- (c) a computed date is only a proposal, and only from an approved rule --------------------------------

    private String proposal(Object ruleId, String startsOn) {
        return json("sourceType", "TEXT", "sourceText", "Judgment notified", "ruleId", ruleId, "startsOn", startsOn,
                "action", "File the appeal", "ownerId", member);
    }

    @Test
    void withNoApprovedRuleTheProposeEndpointAnswersAClear422() throws Exception {
        String path = path() + "/propose";
        // No rule at all.
        mvc.perform(postJson(specialist, path, proposal(null, "2031-03-03"))).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("NO_APPROVED_RULE"));
        mvc.perform(postJson(specialist, path, proposal(UUID.randomUUID(), "2031-03-03")))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.rule").value("NO_APPROVED_RULE"));
        // A rule that is only a draft, or that was retired, does not count either.
        UUID draft = draftRule(specialist, "TEST-DRAFT", 10, "CALENDAR", false, false, List.of(), "2000-01-01");
        mvc.perform(postJson(specialist, path, proposal(draft, "2031-03-03"))).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("NO_APPROVED_RULE"));
        mvc.perform(postJson(specialist, path, proposal(null, "2031-03-03"))).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("NO_APPROVED_RULE"));
        UUID retired = approvedRule(specialist, competent, "TEST-RETIRED", 10, "CALENDAR", false, false, List.of(), "2000-01-01");
        mvc.perform(postJson(competent, "/api/v1/deadline-rules/" + retired + "/retire", json("version", 1)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RETIRED"));
        mvc.perform(postJson(specialist, path, proposal(retired, "2031-03-03"))).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("NO_APPROVED_RULE"));
        assertThat(count("select count(*) from deadline")).isZero();
    }

    @Test
    void anApprovedRuleProposesADateWithItsArithmeticAndTheDateIsOfficialOnlyOnceApproved() throws Exception {
        // TEST-ONLY: 5 working days, Saturday and Sunday closed, a holiday on Wednesday 5 March 2031.
        UUID rule = approvedRule(specialist, competent, "TEST-WORKING", 5, "WORKING", false, false, List.of(6, 7), "2000-01-01");
        approvedHoliday(specialist, competent, "2031-03-05", "Test holiday");
        MvcResult result = mvc.perform(postJson(specialist, path() + "/propose", proposal(rule, "2031-03-03")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.calcStatus").value("PROPOSED"))
                .andExpect(jsonPath("$.official").value(false))
                .andExpect(jsonPath("$.dueOn").value("2031-03-11"))
                .andExpect(jsonPath("$.ruleId").value(rule.toString()))
                .andExpect(jsonPath("$.ruleCode").value("TEST-WORKING"))
                .andExpect(jsonPath("$.periodDays").value(5))
                .andExpect(jsonPath("$.calculation.legalSource").value("Test-only source for TEST-WORKING"))
                .andExpect(jsonPath("$.calculation.countMethod").value("WORKING"))
                .andExpect(jsonPath("$.calculation.startsOn").value("2031-03-03"))
                .andExpect(jsonPath("$.calculation.firstCountedDay").value("2031-03-04"))
                .andExpect(jsonPath("$.calculation.dueOn").value("2031-03-11"))
                .andExpect(jsonPath("$.calculation.skipped[*].kind", contains("HOLIDAY", "WEEKDAY", "WEEKDAY")))
                .andExpect(jsonPath("$.calculation.skipped[0].label").value("Test holiday"))
                .andExpect(jsonPath("$.approvedById").doesNotExist()).andReturn();
        UUID id = idOf(result);

        // A holiday entered afterwards does not rewrite what the reviewer was shown.
        approvedHoliday(specialist, competent, "2031-03-06", "Late holiday");
        mvc.perform(getJson(specialist, "/api/v1/deadlines/" + id)).andExpect(jsonPath("$.dueOn").value("2031-03-11"))
                .andExpect(jsonPath("$.calculation.dueOn").value("2031-03-11"));

        // It cannot be completed while it is only a proposal; the specialist cannot approve it; the member can.
        mvc.perform(postJson(specialist, "/api/v1/deadlines/" + id + "/status", json("status", "COMPLETED", "version", 0)))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.rule").value("DEADLINE_PROPOSAL_PENDING"));
        mvc.perform(postJson(specialist, "/api/v1/deadlines/" + id + "/approve", json("version", 0)))
                .andExpect(status().isForbidden());
        mvc.perform(postJson(competent, "/api/v1/deadlines/" + id + "/approve", json("version", 0)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.calcStatus").value("APPROVED"))
                .andExpect(jsonPath("$.official").value(true)).andExpect(jsonPath("$.approvedByName").value("Hala Supervisor"))
                .andExpect(jsonPath("$.calculation.dueOn").value("2031-03-11")).andExpect(jsonPath("$.ruleCode").value("TEST-WORKING"));
    }

    @Test
    void editingAProposedDateByHandMakesItManualAndForgetsTheRule() throws Exception {
        UUID rule = approvedRule(specialist, competent, "TEST-CAL", 10, "CALENDAR", false, false, List.of(), "2000-01-01");
        UUID id = idOf(mvc.perform(postJson(specialist, path() + "/propose", proposal(rule, "2031-03-03")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.dueOn").value("2031-03-13")).andReturn());
        mvc.perform(putJson(specialist, "/api/v1/deadlines/" + id, json("dueOn", "2031-03-14", "action", "File the appeal",
                "version", 0))).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("DEADLINE_CHANGE_REASON_REQUIRED"));
        mvc.perform(putJson(specialist, "/api/v1/deadlines/" + id, json("startsOn", "2031-03-03", "periodDays", 10,
                "dueOn", "2031-03-14", "action", "File the appeal", "changeReason", "The court said so", "version", 0)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.calcStatus").value("MANUAL"))
                .andExpect(jsonPath("$.dueOn").value("2031-03-14")).andExpect(jsonPath("$.ruleId").doesNotExist())
                .andExpect(jsonPath("$.calculation").doesNotExist());
    }

    @Test
    void aRuleIsNotUsedBeforeItsEffectiveDate() throws Exception {
        UUID rule = approvedRule(specialist, competent, "TEST-LATER", 10, "CALENDAR", false, false, List.of(), "2040-01-01");
        mvc.perform(postJson(specialist, path() + "/propose", proposal(rule, "2031-03-03")))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.rule").value("RULE_NOT_EFFECTIVE"));
        mvc.perform(postJson(specialist, path() + "/propose", proposal(rule, "2040-01-01"))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.dueOn").value("2040-01-11"));
    }

    @Test
    void whenSeveralApprovedRulesExistTheCallerMustChooseOne() throws Exception {
        approvedRule(specialist, competent, "TEST-ONE", 10, "CALENDAR", false, false, List.of(), "2000-01-01");
        mvc.perform(postJson(specialist, path() + "/propose", proposal(null, "2031-03-03"))).andExpect(status().isBadRequest());
        mvc.perform(postJson(specialist, path() + "/propose", json("sourceType", "TEXT", "sourceText", "x", "ruleId",
                UUID.randomUUID(), "action", "a"))).andExpect(status().isBadRequest());
    }

    // --- the register, its filters and the per-judgment view ------------------------------------------------------

    @Test
    void theRegisterFiltersByStatusOwnerDueWindowAndOverdue() throws Exception {
        UUID other = user("Other Member");
        UUID late = recorded("Late", today().minusDays(2).toString());
        UUID soon = recorded("Soon", today().plusDays(2).toString());
        UUID far = recorded("Far", today().plusDays(40).toString());
        approve(late);
        approve(soon);
        mvc.perform(postJson(specialist, "/api/v1/deadlines/" + far + "/status", json("status", "MISSED", "version", 0)))
                .andExpect(status().isOk());
        jdbc.update("update deadline set owner_id = ? where id = ?", other, soon);

        mvc.perform(getJson(specialist, "/api/v1/deadlines")).andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.items[*].action", contains("Late", "Soon", "Far")));
        mvc.perform(getJson(specialist, "/api/v1/deadlines?overdue=true")).andExpect(jsonPath("$.items[*].action", contains("Late")));
        mvc.perform(getJson(specialist, "/api/v1/deadlines?overdue=false")).andExpect(jsonPath("$.totalItems").value(2));
        mvc.perform(getJson(specialist, "/api/v1/deadlines?status=MISSED")).andExpect(jsonPath("$.items[*].action", contains("Far")));
        mvc.perform(getJson(specialist, "/api/v1/deadlines?ownerId=" + other)).andExpect(jsonPath("$.items[*].action", contains("Soon")));
        mvc.perform(getJson(specialist, "/api/v1/deadlines?calcStatus=MANUAL")).andExpect(jsonPath("$.items[*].action", contains("Far")));
        mvc.perform(getJson(specialist, "/api/v1/deadlines?calcStatus=APPROVED")).andExpect(jsonPath("$.totalItems").value(2));
        mvc.perform(getJson(specialist, "/api/v1/deadlines?from=" + today() + "&to=" + today().plusDays(10)))
                .andExpect(jsonPath("$.items[*].action", contains("Soon")));
        mvc.perform(getJson(specialist, "/api/v1/deadlines?caseId=" + caseId + "&sourceType=TEXT"))
                .andExpect(jsonPath("$.totalItems").value(3));
        mvc.perform(getJson(specialist, "/api/v1/deadlines?sourceType=JUDGMENT")).andExpect(jsonPath("$.totalItems").value(0));
        mvc.perform(getJson(specialist, "/api/v1/deadlines?status=NONSENSE")).andExpect(status().isBadRequest());
        mvc.perform(getJson(specialist, "/api/v1/cases/" + caseId + "/deadlines?status=OPEN"))
                .andExpect(jsonPath("$.totalItems").value(2));
    }

    @Test
    void theJudgmentViewShowsTheDeadlinesThatRunFromThatJudgmentOnly() throws Exception {
        UUID judgment = judgment(proceeding(caseId), LocalDate.of(2026, 3, 1));
        UUID otherJudgment = judgment(proceeding(caseId), LocalDate.of(2026, 3, 9));
        String due = today().plusDays(15).toString();
        mvc.perform(postJson(specialist, path(), json("sourceType", "JUDGMENT", "sourceId", judgment, "dueOn", due,
                "action", "Appeal"))).andExpect(status().isCreated());
        mvc.perform(postJson(specialist, path(), json("sourceType", "JUDGMENT", "sourceId", otherJudgment, "dueOn", due,
                "action", "Other appeal"))).andExpect(status().isCreated());
        recorded("Free text", due);
        mvc.perform(getJson(specialist, "/api/v1/judgments/" + judgment + "/deadlines"))
                .andExpect(jsonPath("$.items[*].action", contains("Appeal")));
        mvc.perform(getJson(specialist, path() + "?sourceType=JUDGMENT&sourceId=" + otherJudgment))
                .andExpect(jsonPath("$.items[*].action", contains("Other appeal")));
        mvc.perform(getJson(specialist, "/api/v1/judgments/" + UUID.randomUUID() + "/deadlines")).andExpect(status().isNotFound());
    }

    // --- visibility (D3) -------------------------------------------------------------------------------------------

    @Test
    void aUserWhoCannotSeeTheCaseSeesNothingOfItsDeadlines() throws Exception {
        UUID id = recorded("Secret", today().plusDays(9).toString());
        UUID judgment = judgment(proceeding(caseId), LocalDate.of(2026, 3, 1));
        RequestPostProcessor stranger = outsider(UUID.randomUUID(), COMPETENT);
        mvc.perform(getJson(stranger, "/api/v1/deadlines/" + id)).andExpect(status().isNotFound());
        mvc.perform(getJson(stranger, path())).andExpect(status().isNotFound());
        mvc.perform(getJson(stranger, "/api/v1/judgments/" + judgment + "/deadlines")).andExpect(status().isNotFound());
        mvc.perform(postJson(stranger, path(), manual("Nope", today().plusDays(1).toString()))).andExpect(status().isNotFound());
        mvc.perform(postJson(stranger, path() + "/propose", proposal(UUID.randomUUID(), "2031-03-03")))
                .andExpect(status().isNotFound());
        mvc.perform(putJson(stranger, "/api/v1/deadlines/" + id, json("dueOn", today().toString(), "action", "x", "version", 0)))
                .andExpect(status().isNotFound());
        mvc.perform(postJson(stranger, "/api/v1/deadlines/" + id + "/status", json("status", "MISSED", "version", 0)))
                .andExpect(status().isNotFound());
        mvc.perform(getJson(stranger, "/api/v1/deadlines")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0)).andExpect(jsonPath("$.items", hasSize(0)));
        mvc.perform(getJson(stranger, "/api/v1/deadlines?caseId=" + caseId)).andExpect(jsonPath("$.totalItems").value(0));
        // The unified timeline and the summary of the case are hidden as well.
        mvc.perform(getJson(stranger, "/api/v1/cases/" + caseId + "/unified-timeline")).andExpect(status().isNotFound());
        // A viewer sees the deadline but cannot change anything.
        RequestPostProcessor reader = viewer(UUID.randomUUID(), COMPETENT);
        mvc.perform(getJson(reader, "/api/v1/deadlines/" + id)).andExpect(status().isOk());
        mvc.perform(postJson(reader, path(), manual("Nope", today().plusDays(1).toString()))).andExpect(status().isForbidden());
        mvc.perform(postJson(reader, "/api/v1/deadlines/" + id + "/status", json("status", "MISSED", "version", 0)))
                .andExpect(status().isForbidden());
        mvc.perform(getJson(reader, "/api/v1/deadlines")).andExpect(jsonPath("$.totalItems").value(1));
        // Without the deadline permission the area is closed whatever the case access.
        RequestPostProcessor noDeadlines = staff(UUID.randomUUID(), Permission.CASE_VIEW);
        mvc.perform(getJson(noDeadlines, "/api/v1/deadlines")).andExpect(status().isForbidden());
        mvc.perform(getJson(noDeadlines, path())).andExpect(status().isForbidden());
    }

    @Test
    void anArchivedCaseTakesNoNewDeadline() throws Exception {
        jdbc.update("update legal_case set status = 'ARCHIVED' where id = ?", caseId);
        mvc.perform(postJson(specialist, path(), manual("Late", today().plusDays(1).toString())))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.rule").value("CASE_ARCHIVED"));
    }
}
