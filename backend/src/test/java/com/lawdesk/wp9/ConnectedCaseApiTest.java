package com.lawdesk.wp9;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.lawdesk.security.Permission;

/**
 * LD-DOC-00-001/002 (the preamble asks for an interconnected system rather than isolated screens): one case with a claim,
 * a defence, a hearing, a judgment, a document, a deadline, a risk assessment, a task and a note is read back through its
 * summary, its timeline, the calendar, the unified search, the dashboard and the case inventory; and a change made in one
 * place (a new task) appears in the others. Synthetic data only (SYNTH-DOC); the marker word makes the search exact.
 */
class ConnectedCaseApiTest extends Wp9TestSupport {

    private static final String MARK = "zqxmark";

    private String read(RequestPostProcessor user, String path) throws Exception {
        return mvc.perform(get(path).with(user)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void oneCasesDataIsLinkedAndAChangeInOnePlaceAppearsInTheOthers() throws Exception {
        UUID member = user("Member");
        UUID caseId = legalCase(member, null);
        jdbc.update("update legal_case set title = ?, subject = ? where id = ?", "SYNTH-DOC " + MARK + " dispute",
                MARK + " subject", caseId);
        UUID proceeding = proceeding(caseId, "9" + System.nanoTime() % 100000);
        LocalDate hearingDay = today().plusDays(5);
        hearing(caseId, proceeding, hearingDay.toString(), MARK + " pleadings", "adjourned for documents");
        UUID judgment = judgment(proceeding, MARK + " judgment summary");
        document(caseId, MARK + " contract", "contract.pdf");
        note(caseId, MARK + " note");
        UUID claim = jdbc.queryForObject("""
                insert into claim (case_id, seq, text, kind, created_by, updated_by)
                values (?, 1, ?, 'PRIMARY', ?, ?) returning id""", UUID.class, caseId, MARK + " claim", member, member);
        jdbc.update("""
                insert into claim_result (judgment_id, claim_id, result, created_by, updated_by) values (?, ?, 'PARTIAL', ?, ?)""",
                judgment, claim, member, member);
        jdbc.update("""
                insert into defense (case_id, seq, title, kind, argument, created_by, updated_by)
                values (?, 1, ?, 'SUBSTANTIVE', 'x', ?, ?)""", caseId, MARK + " defence", member, member);
        UUID deadline = jdbc.queryForObject("""
                insert into deadline (case_id, source_type, source_id, action, due_on, calc_status, created_by, updated_by)
                values (?, 'JUDGMENT', ?, ?, ?, 'MANUAL', ?, ?) returning id""", UUID.class, caseId, judgment,
                MARK + " appeal", today().plusDays(9), member, member);
        jdbc.update("update judgment set appeal_deadline_id = ? where id = ?", deadline, judgment);
        jdbc.update("""
                insert into risk_assessment (case_id, version_no, level, level_reasons, trigger_kind, assessed_by)
                values (?, 1, 'HIGH', ?, 'MANUAL', ?)""", caseId, MARK + " risk", member);
        UUID firstTask = task(caseId, MARK + " first task", "SYNTH-DOC", member);
        jdbc.update("update task set due_date = ? where id = ?", today().plusDays(2), firstTask);

        RequestPostProcessor reader = staff(member, Permission.CASE_VIEW, Permission.HEARING_VIEW, Permission.JUDGMENT_VIEW,
                Permission.DOCUMENT_VIEW, Permission.TASK_VIEW, Permission.DEADLINE_VIEW, Permission.CALENDAR_VIEW,
                Permission.SEARCH_ALL, Permission.REPORT_VIEW, Permission.RISK_VIEW, Permission.CLAIM_VIEW,
                Permission.DEFENSE_VIEW, Permission.INFO_REQUEST_VIEW);
        String from = today().toString();
        String to = today().plusDays(30).toString();

        // The case summary gathers hearings, tasks and deadlines of the case.
        mvc.perform(get("/api/v1/cases/" + caseId + "/summary").with(reader)).andExpect(status().isOk())
                .andExpect(jsonPath("$.hearings.total").value(1)).andExpect(jsonPath("$.tasks.open").value(1))
                .andExpect(jsonPath("$.deadlines.open").value(1));
        // The timeline carries the hearing, the judgment and the deadline of the same case.
        String timeline = read(reader, "/api/v1/cases/" + caseId + "/unified-timeline");
        assertThat(timeline).contains("HEARING").contains("JUDGMENT").contains("DEADLINE");
        // The calendar shows the hearing, the task and the deadline.
        String calendar = read(reader, "/api/v1/calendar/events?from=" + from + "&to=" + to + "&caseId=" + caseId);
        assertThat(calendar).contains("\"type\":\"HEARING\"").contains("\"type\":\"TASK\"").contains("\"type\":\"DEADLINE\"");
        // The unified search finds the case and its records by the same word.
        String found = read(reader, "/api/v1/search?q=" + MARK);
        assertThat(found).contains("CASE").contains("HEARING").contains("JUDGMENT").contains("DOCUMENT").contains("NOTE")
                .contains("TASK");
        // The judgment names its appeal deadline, and the claim's result is recorded against this judgment.
        mvc.perform(get("/api/v1/judgments/" + judgment).with(reader)).andExpect(status().isOk())
                .andExpect(jsonPath("$.appealDeadline.id").value(deadline.toString()));
        assertThat(read(reader, "/api/v1/judgments/" + judgment + "/claim-results")).contains(claim.toString());
        // The case inventory lists the case, and the dashboard answers for the same data.
        assertThat(read(reader, "/api/v1/reports/cases")).contains("SYNTH-DOC " + MARK + " dispute");
        String dashboardBefore = read(reader, "/api/v1/reports/dashboard");

        // A change in one place: a second task is added; the summary, the calendar and the dashboard follow.
        UUID secondTask = task(caseId, MARK + " second task", "SYNTH-DOC", member);
        jdbc.update("update task set due_date = ? where id = ?", today().plusDays(3), secondTask);
        mvc.perform(get("/api/v1/cases/" + caseId + "/summary").with(reader)).andExpect(jsonPath("$.tasks.open").value(2));
        assertThat(read(reader, "/api/v1/calendar/events?from=" + from + "&to=" + to + "&caseId=" + caseId))
                .contains(secondTask.toString());
        assertThat(read(reader, "/api/v1/search?q=" + MARK + "&types=TASK")).contains("second task");
        assertThat(read(reader, "/api/v1/reports/dashboard")).isNotEqualTo(dashboardBefore);

        // A user who cannot see the case sees none of it anywhere.
        RequestPostProcessor stranger = outsider(user("Stranger"), Permission.CASE_VIEW, Permission.SEARCH_ALL,
                Permission.CALENDAR_VIEW, Permission.REPORT_VIEW, Permission.TASK_VIEW, Permission.HEARING_VIEW);
        assertThat(read(stranger, "/api/v1/search?q=" + MARK)).doesNotContain(caseId.toString());
        assertThat(read(stranger, "/api/v1/calendar/events?from=" + from + "&to=" + to)).doesNotContain(caseId.toString());
        mvc.perform(get("/api/v1/cases/" + caseId + "/summary").with(stranger)).andExpect(status().isNotFound());
    }
}
