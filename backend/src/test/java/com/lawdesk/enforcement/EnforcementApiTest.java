package com.lawdesk.enforcement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.lawdesk.documents.ArchiveFiles;
import com.lawdesk.documents.ArchiveTestSupport;
import com.lawdesk.security.Permission;

/**
 * LD-055 (requirement 11): the enforcement file of a judgment (executing party, amount asked, obstacles, status, actions with
 * dates and results, responsible member) and the lists of judgments not executed and late. Visibility follows the case.
 */
class EnforcementApiTest extends ArchiveTestSupport {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Kuwait"));

    private UUID owner;

    @BeforeEach
    void members() {
        owner = user("Enforcement owner");
    }

    private RequestPostProcessor author() {
        return member(owner);
    }

    /** A case the owner is a member of, with a first-instance proceeding and one judgment. */
    private UUID[] judged(String date) {
        UUID caseId = newCase(owner);
        UUID judgment = judgment(firstInstance(caseId), date);
        return new UUID[] {caseId, judgment};
    }

    private String file(String status, String extra) {
        return "{\"status\":\"" + status + "\"" + (extra.isEmpty() ? "" : "," + extra) + "}";
    }

    private long fileVersion(UUID judgment) {
        return jdbc.queryForObject("select version from enforcement_file where judgment_id = ?", Long.class, judgment);
    }

    private UUID fileId(UUID judgment) {
        return jdbc.queryForObject("select id from enforcement_file where judgment_id = ?", UUID.class, judgment);
    }

    @Test
    void aJudgmentWithoutAFileShowsItsOwnDataAndNoFile() throws Exception {
        UUID[] j = judged("2026-04-01");
        get(author(), "/api/v1/judgments/" + j[1] + "/enforcement").andExpect(status().isOk())
                .andExpect(jsonPath("$.judgment.id").value(j[1].toString())).andExpect(jsonPath("$.judgment.judgmentDate").value("2026-04-01"))
                .andExpect(jsonPath("$.judgment.operativeText").value("the operative part")).andExpect(jsonPath("$.judgment.courtCaseNumber").isString())
                .andExpect(jsonPath("$.judgment.stage").value("FIRST_INSTANCE")).andExpect(jsonPath("$.file").doesNotExist())
                .andExpect(jsonPath("$.actions.length()").value(0));
    }

    @Test
    void anEnforcementFileRecordsEverythingTheRequirementNames() throws Exception {
        UUID[] j = judged("2026-04-01");
        post(author(), "/api/v1/judgments/" + j[1] + "/enforcement", file("IN_PROGRESS", """
                "responsibleMemberId":"%s","executingParty":"Execution Department, Court of First Instance","amountRequired":"1250.500",
                "obstacles":"the debtor's address is unknown","dueOn":"%s","notes":"follow up monthly" """.formatted(owner, TODAY.plusDays(30))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.file.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.file.amountRequired").value("1250.500")).andExpect(jsonPath("$.file.executingParty").value("Execution Department, Court of First Instance"))
                .andExpect(jsonPath("$.file.obstacles").value("the debtor's address is unknown"))
                .andExpect(jsonPath("$.file.responsibleMemberName").value("Enforcement owner")).andExpect(jsonPath("$.file.late").value(false))
                .andExpect(jsonPath("$.file.caseId").value(j[0].toString()));
        assertThat(jdbc.queryForObject("select amount_required::text from enforcement_file where judgment_id = ?", String.class, j[1])).isEqualTo("1250.500");
        assertThat(jdbc.queryForList("select type from case_event where case_id = ? and type like 'ENFORCEMENT%'", String.class, j[0]))
                .containsExactly("ENFORCEMENT_RECORDED");
    }

    @Test
    void aJudgmentHasOneFile() throws Exception {
        UUID[] j = judged("2026-04-01");
        post(author(), "/api/v1/judgments/" + j[1] + "/enforcement", "{}").andExpect(status().isCreated()).andExpect(jsonPath("$.file.status").value("NOT_STARTED"));
        post(author(), "/api/v1/judgments/" + j[1] + "/enforcement", "{}").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("ENFORCEMENT_ALREADY_RECORDED"));
        assertThat(count("select count(*) from enforcement_file where judgment_id = ?", j[1])).isOne();
    }

    @Test
    void actionsAreNumberedKeepTheirDatesAndResultsAndCanBeCorrected() throws Exception {
        UUID[] j = judged("2026-04-01");
        post(author(), "/api/v1/judgments/" + j[1] + "/enforcement", "{}").andExpect(status().isCreated());
        UUID file = fileId(j[1]);
        UUID letter = attach(author(), j[0], pdfFile("letter.pdf", ArchiveFiles.pdfWithText("letter to the debtor")));

        post(author(), "/api/v1/enforcement/" + file + "/actions", "{\"kind\":\"CORRESPONDENCE\",\"actionDate\":\"2026-04-10\",\"description\":\"letter to the debtor\",\"documentId\":\"" + letter + "\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.seq").value(1)).andExpect(jsonPath("$.documentTitle").value("letter.pdf"))
                .andExpect(jsonPath("$.result").doesNotExist());
        String second = id(post(author(), "/api/v1/enforcement/" + file + "/actions", "{\"kind\":\"OBSTACLE\",\"actionDate\":\"2026-04-20\",\"description\":\"no answer\",\"result\":\"returned unopened\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.seq").value(2)));
        put(author(), "/api/v1/enforcement-actions/" + second, "{\"kind\":\"OBSTACLE\",\"actionDate\":\"2026-04-21\",\"description\":\"no answer\",\"result\":\"returned unopened; address checked\",\"version\":0}")
                .andExpect(jsonPath("$.result").value("returned unopened; address checked")).andExpect(jsonPath("$.version").value(1));
        put(author(), "/api/v1/enforcement-actions/" + second, "{\"kind\":\"OBSTACLE\",\"actionDate\":\"2026-04-21\",\"description\":\"x\",\"version\":0}").andExpect(status().isConflict());

        get(author(), "/api/v1/judgments/" + j[1] + "/enforcement").andExpect(jsonPath("$.actions.length()").value(2))
                .andExpect(jsonPath("$.actions[0].actionDate").value("2026-04-10")).andExpect(jsonPath("$.actions[1].kind").value("OBSTACLE"));
        assertThat(jdbc.queryForList("select type from case_event where case_id = ? and type like 'ENFORCEMENT_ACTION%' order by seq", String.class, j[0]))
                .containsExactly("ENFORCEMENT_ACTION_ADDED", "ENFORCEMENT_ACTION_ADDED", "ENFORCEMENT_ACTION_CHANGED");
    }

    @Test
    void executionNeedsADateAndTheFileIsUpdatedWithTheVersion() throws Exception {
        UUID[] j = judged("2026-04-01");
        post(author(), "/api/v1/judgments/" + j[1] + "/enforcement", file("IN_PROGRESS", "")).andExpect(status().isCreated());
        UUID file = fileId(j[1]);
        put(author(), "/api/v1/enforcement/" + file, file("EXECUTED", "\"version\":0")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("completedOn"));
        put(author(), "/api/v1/enforcement/" + file, file("EXECUTED", "\"completedOn\":\"" + TODAY.plusDays(1) + "\",\"version\":0")).andExpect(status().isBadRequest());
        put(author(), "/api/v1/enforcement/" + file, file("EXECUTED", "\"completedOn\":\"2026-05-15\",\"version\":5")).andExpect(status().isConflict());
        put(author(), "/api/v1/enforcement/" + file, file("EXECUTED", "\"completedOn\":\"2026-05-15\",\"version\":0")).andExpect(status().isOk())
                .andExpect(jsonPath("$.file.status").value("EXECUTED")).andExpect(jsonPath("$.file.completedOn").value("2026-05-15")).andExpect(jsonPath("$.file.version").value(1));
        assertThat(count("select count(*) from audit_event where action = 'ENFORCEMENT_UPDATE' and target_id = ?", j[1].toString())).isOne();
    }

    @Test
    void valuesAreChecked() throws Exception {
        UUID[] j = judged("2026-04-01");
        String path = "/api/v1/judgments/" + j[1] + "/enforcement";
        post(author(), path, file("DONE", "")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("status"));
        post(author(), path, file("IN_PROGRESS", "\"amountRequired\":\"-1.000\"")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("amountRequired"));
        post(author(), path, file("IN_PROGRESS", "\"amountRequired\":\"1.2345\"")).andExpect(status().isBadRequest());
        post(author(), path, file("IN_PROGRESS", "\"responsibleMemberId\":\"" + UUID.randomUUID() + "\"")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("responsibleMemberId"));
        post(author(), path, file("IN_PROGRESS", "\"obstacles\":\"" + "o".repeat(4001) + "\"")).andExpect(status().isBadRequest());
        assertThat(count("select count(*) from enforcement_file")).isZero();
        UUID file = null;
        post(author(), path, "{}").andExpect(status().isCreated());
        file = fileId(j[1]);
        String actions = "/api/v1/enforcement/" + file + "/actions";
        post(author(), actions, "{\"kind\":\"RAID\",\"actionDate\":\"2026-04-10\",\"description\":\"x\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("kind"));
        post(author(), actions, "{\"kind\":\"PROCEDURE\",\"description\":\"x\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("actionDate"));
        post(author(), actions, "{\"kind\":\"PROCEDURE\",\"actionDate\":\"2026-04-10\",\"description\":\" \"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("description"));
        UUID foreign = attach(everything(), newCase(), pdfFile("o.pdf", ArchiveFiles.pdfWithText("other case")));
        post(author(), actions, "{\"kind\":\"PROCEDURE\",\"actionDate\":\"2026-04-10\",\"description\":\"x\",\"documentId\":\"" + foreign + "\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("documentId"));
    }

    // ---- the lists -----------------------------------------------------------------------------------------------

    @Test
    void theListShowsJudgmentsNotExecutedAndTheLateOnes() throws Exception {
        UUID[] untracked = judged("2026-01-01");
        UUID[] running = judged("2026-02-01");
        UUID[] late = judged("2026-03-01");
        UUID[] dueToday = judged("2026-03-02");
        UUID[] executed = judged("2026-04-01");
        UUID[] notRequired = judged("2026-05-01");
        post(author(), "/api/v1/judgments/" + running[1] + "/enforcement", file("IN_PROGRESS", "\"dueOn\":\"" + TODAY.plusDays(10) + "\"")).andExpect(status().isCreated());
        post(author(), "/api/v1/judgments/" + late[1] + "/enforcement", file("OBSTRUCTED", "\"dueOn\":\"" + TODAY.minusDays(1) + "\",\"executingParty\":\"Bailiff\"")).andExpect(status().isCreated());
        post(author(), "/api/v1/judgments/" + dueToday[1] + "/enforcement", file("NOT_STARTED", "\"dueOn\":\"" + TODAY + "\"")).andExpect(status().isCreated());
        post(author(), "/api/v1/judgments/" + executed[1] + "/enforcement", file("EXECUTED", "\"completedOn\":\"2026-05-01\",\"dueOn\":\"2026-04-15\"")).andExpect(status().isCreated());
        post(author(), "/api/v1/judgments/" + notRequired[1] + "/enforcement", file("NOT_REQUIRED", "\"dueOn\":\"2026-01-01\"")).andExpect(status().isCreated());

        var admin = everything();
        get(admin, "/api/v1/enforcement/judgments").andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(4))
                .andExpect(jsonPath("$.items[*].judgmentId").value(Matchers.containsInAnyOrder(untracked[1].toString(), running[1].toString(), late[1].toString(), dueToday[1].toString())));
        get(admin, "/api/v1/enforcement/judgments?scope=LATE").andExpect(jsonPath("$.totalItems").value(1)).andExpect(jsonPath("$.items[0].judgmentId").value(late[1].toString()))
                .andExpect(jsonPath("$.items[0].late").value(true)).andExpect(jsonPath("$.items[0].status").value("OBSTRUCTED")).andExpect(jsonPath("$.items[0].executingParty").value("Bailiff"));
        get(admin, "/api/v1/enforcement/judgments?scope=ALL").andExpect(jsonPath("$.totalItems").value(6));
        get(admin, "/api/v1/enforcement/judgments?scope=UNEXECUTED").andExpect(jsonPath("$.items[?(@.judgmentId=='" + untracked[1] + "')].tracked").value(false));
        get(admin, "/api/v1/enforcement/judgments?scope=NONSENSE").andExpect(status().isBadRequest());
        get(admin, "/api/v1/enforcement/judgments?caseId=" + running[0]).andExpect(jsonPath("$.totalItems").value(1));
        get(admin, "/api/v1/enforcement/judgments?size=2&page=1").andExpect(jsonPath("$.items.length()").value(2)).andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void theListsAndTheFilesOfCasesTheUserCannotSeeNeverAppear() throws Exception {
        UUID stranger = user("Stranger");
        UUID[] mine = judged("2026-01-01");
        UUID[] theirs = new UUID[2];
        theirs[0] = newCase(stranger);
        theirs[1] = judgment(firstInstance(theirs[0]), "2026-02-02");
        post(author(), "/api/v1/judgments/" + mine[1] + "/enforcement", file("OBSTRUCTED", "\"dueOn\":\"2026-01-02\",\"obstacles\":\"secret obstacle\"")).andExpect(status().isCreated());

        String listed = body(get(outsider(stranger), "/api/v1/enforcement/judgments?scope=ALL").andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].judgmentId").value(theirs[1].toString())));
        assertThat(listed).doesNotContain(mine[1].toString()).doesNotContain(mine[0].toString());
        get(outsider(stranger), "/api/v1/enforcement/judgments?scope=LATE").andExpect(jsonPath("$.totalItems").value(0));
        get(outsider(stranger), "/api/v1/enforcement/judgments?caseId=" + mine[0]).andExpect(jsonPath("$.totalItems").value(0));
        String direct = body(get(outsider(stranger), "/api/v1/judgments/" + mine[1] + "/enforcement").andExpect(status().isNotFound()));
        assertThat(direct).doesNotContain("secret obstacle");
        post(outsider(stranger), "/api/v1/judgments/" + mine[1] + "/enforcement", "{}").andExpect(status().isNotFound());
        put(outsider(stranger), "/api/v1/enforcement/" + fileId(mine[1]), file("EXECUTED", "\"version\":0")).andExpect(status().isNotFound());
        post(outsider(stranger), "/api/v1/enforcement/" + fileId(mine[1]) + "/actions", "{\"kind\":\"OTHER\",\"actionDate\":\"2026-04-10\",\"description\":\"x\"}").andExpect(status().isNotFound());
        get(viewer(stranger), "/api/v1/enforcement/judgments?scope=LATE").andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void seeingAJudgmentIsNotTheRightToChangeItsEnforcement() throws Exception {
        UUID[] j = judged("2026-04-01");
        UUID viewerId = UUID.randomUUID();
        get(viewer(viewerId), "/api/v1/judgments/" + j[1] + "/enforcement").andExpect(status().isOk());
        post(viewer(viewerId), "/api/v1/judgments/" + j[1] + "/enforcement", "{}").andExpect(status().isForbidden());
        post(author(), "/api/v1/judgments/" + j[1] + "/enforcement", "{}").andExpect(status().isCreated());
        put(viewer(viewerId), "/api/v1/enforcement/" + fileId(j[1]), file("EXECUTED", "\"completedOn\":\"2026-05-01\",\"version\":0")).andExpect(status().isForbidden());
        post(viewer(viewerId), "/api/v1/enforcement/" + fileId(j[1]) + "/actions", "{\"kind\":\"OTHER\",\"actionDate\":\"2026-04-10\",\"description\":\"x\"}").andExpect(status().isForbidden());
        // Permissions: reading and writing are separate rights, and the case rights come on top.
        get(with(Permission.CASE_VIEW, Permission.JUDGMENT_VIEW), "/api/v1/judgments/" + j[1] + "/enforcement").andExpect(status().isForbidden());
        post(with(Permission.ENFORCEMENT_VIEW, Permission.CASE_VIEW), "/api/v1/judgments/" + UUID.randomUUID() + "/enforcement", "{}").andExpect(status().isForbidden());
        get(with(Permission.CASE_VIEW), "/api/v1/enforcement/judgments").andExpect(status().isForbidden());
        get(everything(), "/api/v1/judgments/" + UUID.randomUUID() + "/enforcement").andExpect(status().isNotFound());
    }

    @Test
    void anArchivedCaseIsReadOnly() throws Exception {
        UUID[] j = judged("2026-04-01");
        post(author(), "/api/v1/judgments/" + j[1] + "/enforcement", "{}").andExpect(status().isCreated());
        jdbc.update("update legal_case set status = 'ARCHIVED' where id = ?", j[0]);
        put(author(), "/api/v1/enforcement/" + fileId(j[1]), file("IN_PROGRESS", "\"version\":0")).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.rule").value("CASE_ARCHIVED"));
        get(author(), "/api/v1/judgments/" + j[1] + "/enforcement").andExpect(status().isOk());
        assertThat(fileVersion(j[1])).isZero();
    }

    @Test
    void theDatabaseRefusesAnExecutedFileWithoutADate() {
        UUID[] j = judged("2026-04-01");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update("""
                insert into enforcement_file (judgment_id, case_id, status, created_by, updated_by) values (?, ?, 'EXECUTED', ?, ?)""", j[1], j[0], USER, USER))
                .hasMessageContaining("enforcement_file_executed_has_date");
    }
}
