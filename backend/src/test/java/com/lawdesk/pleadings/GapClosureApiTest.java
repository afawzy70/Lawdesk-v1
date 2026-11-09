package com.lawdesk.pleadings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.lawdesk.followup.FollowUpRun;
import com.lawdesk.security.Permission;
import com.lawdesk.security.TestActors;

/**
 * Gap closure (round 2): the defence-to-precedent link, the information-request follow-up of a missing evidence item, the
 * closing check of a case, the appeal deadline named by a judgment, the sources of the similar-judgment suggestions, the
 * evidence-gap alert and the enforcement date on the calendar. Synthetic data only (SYNTH-DOC).
 */
class GapClosureApiTest extends PleadingsTestSupport {

    @Autowired
    FollowUpRun followUp;

    private UUID libraryJudgment(String subject, String status) {
        UUID id = jdbc.queryForObject("""
                insert into library_judgment (court_name, subject, source_reference, verification_note, created_by, updated_by)
                values ('SYNTH-DOC court', ?, 'SYNTH-DOC source', 'SYNTH-DOC verified', ?, ?) returning id""", UUID.class,
                subject, USER, USER);
        if ("WITHDRAWN".equals(status)) {
            jdbc.update("update library_judgment set status = 'WITHDRAWN', withdrawn_reason = 'x', withdrawn_at = now(),"
                    + " withdrawn_by = ? where id = ?", USER, id);
        }
        return id;
    }

    private UUID defense(UUID caseId, String title) throws Exception {
        return created(everything(), "/api/v1/cases/" + caseId + "/defenses", json("title", title, "kind", "SUBSTANTIVE",
                "argument", "SYNTH-DOC " + title));
    }

    private RequestPostProcessor withLibrary(UUID id) {
        List<Permission> all = new java.util.ArrayList<>(List.of(ALL_PLEADINGS));
        all.add(Permission.LIBRARY_VIEW);
        return TestActors.scoped(id, "libpleader", List.of("ALL"), all.toArray(Permission[]::new));
    }

    @Test
    void aDefenceKeepsItsSupportingPrecedentsAndTheirHistory() throws Exception {
        UUID caseId = newCase();
        UUID defense = defense(caseId, "Prescription");
        UUID judgment = libraryJudgment("SYNTH-DOC prescription", "ACTIVE");
        UUID withdrawn = libraryJudgment("SYNTH-DOC withdrawn", "WITHDRAWN");
        RequestPostProcessor user = TestActors.as(USER, "pleader", withLibraryPermissions());

        UUID link = created(user, "/api/v1/defenses/" + defense + "/precedents",
                json("judgmentId", judgment, "note", "same limitation period"));
        post(user, "/api/v1/defenses/" + defense + "/precedents", json("judgmentId", judgment))
                .andExpect(status().isConflict());
        post(user, "/api/v1/defenses/" + defense + "/precedents", json("judgmentId", withdrawn))
                .andExpect(status().isBadRequest());
        // A principle of another judgment is refused.
        UUID foreignPrinciple = jdbc.queryForObject("""
                insert into library_principle (judgment_id, principle_text, created_by, updated_by)
                values (?, 'SYNTH-DOC principle', ?, ?) returning id""", UUID.class, withdrawn, USER, USER);
        post(user, "/api/v1/defenses/" + defense + "/precedents", json("judgmentId", judgment, "principleId", foreignPrinciple))
                .andExpect(status().isBadRequest());

        get(user, "/api/v1/defenses/" + defense).andExpect(status().isOk())
                .andExpect(jsonPath("$.precedents.length()").value(1))
                .andExpect(jsonPath("$.precedents[0].subject").value("SYNTH-DOC prescription"));
        // Without the library permission the link is refused; the case is still visible.
        post(everything(), "/api/v1/defenses/" + defense + "/precedents", json("judgmentId", judgment))
                .andExpect(status().isForbidden());
        // An outsider does not even see the defence.
        UUID outsider = user("Outsider");
        get(TestActors.scoped(outsider, "outsider", List.of(), ALL_PLEADINGS), "/api/v1/defenses/" + defense + "/precedents")
                .andExpect(status().isNotFound());

        delete(user, "/api/v1/defenses/" + defense + "/precedents/" + link).andExpect(status().isNoContent());
        get(user, "/api/v1/defenses/" + defense + "/precedents").andExpect(jsonPath("$.length()").value(0));
        assertThat(count("select count(*) from defense_precedent where defense_id = ? and removed_at is not null", defense))
                .isEqualTo(1);
    }

    private static Permission[] withLibraryPermissions() {
        Permission[] all = java.util.Arrays.copyOf(ALL_PLEADINGS, ALL_PLEADINGS.length + 1);
        all[all.length - 1] = Permission.LIBRARY_VIEW;
        return all;
    }

    @Test
    void missingEvidenceCanBeFollowedByATaskOrAnInformationRequestOfTheSameCase() throws Exception {
        UUID caseId = newCase();
        UUID other = newCase();
        UUID evidence = created(everything(), "/api/v1/cases/" + caseId + "/evidence",
                json("name", "SYNTH-DOC lease", "role", "REQUIRED"));
        UUID request = jdbc.queryForObject("""
                insert into info_request (case_id, request_no, subject, created_by, updated_by)
                values (?, 'IR-SYNTH-1', 'SYNTH-DOC request', ?, ?) returning id""", UUID.class, caseId, USER, USER);
        UUID foreign = jdbc.queryForObject("""
                insert into info_request (case_id, request_no, subject, created_by, updated_by)
                values (?, 'IR-SYNTH-2', 'SYNTH-DOC request', ?, ?) returning id""", UUID.class, other, USER, USER);

        post(everything(), "/api/v1/evidence/" + evidence + "/followups", json("infoRequestId", request))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.requestNo").value("IR-SYNTH-1"));
        post(everything(), "/api/v1/evidence/" + evidence + "/followups", json("infoRequestId", request))
                .andExpect(status().isConflict());
        post(everything(), "/api/v1/evidence/" + evidence + "/followups", json("infoRequestId", foreign))
                .andExpect(status().isBadRequest());
        post(everything(), "/api/v1/evidence/" + evidence + "/followups", "{}").andExpect(status().isBadRequest());
        post(everything(), "/api/v1/evidence/" + evidence + "/followups",
                json("infoRequestId", request, "taskId", task(caseId, "SYNTH-DOC follow")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void closingACaseWithOpenItemsNeedsAnAcknowledgementAndSaysWhichItemsAreOpen() throws Exception {
        UUID caseId = newCase();
        jdbc.update("""
                insert into settlement (case_id, kind, status, created_by, updated_by) values (?, 'SETTLEMENT', 'PROPOSED', ?, ?)""",
                caseId, USER, USER);
        task(caseId, "SYNTH-DOC open task");
        RequestPostProcessor manager = TestActors.as(USER, "closer", Permission.CASE_VIEW, Permission.CASE_STATUS_CHANGE);

        get(manager, "/api/v1/cases/" + caseId + "/closing-check").andExpect(status().isOk())
                .andExpect(jsonPath("$.hasWarnings").value(true))
                .andExpect(jsonPath("$.findings[?(@.code=='UNFINISHED_SETTLEMENT')].count").value(1))
                .andExpect(jsonPath("$.findings[?(@.code=='OPEN_TASK')].count").value(1));
        long version = jdbc.queryForObject("select version from legal_case where id = ?", Long.class, caseId);
        post(manager, "/api/v1/cases/" + caseId + "/status", json("status", "CLOSED", "version", version))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("CLOSING_OPEN_ITEMS_NOT_ACKNOWLEDGED"));
        post(manager, "/api/v1/cases/" + caseId + "/status",
                json("status", "CLOSED", "version", version, "acknowledgeOpenItems", true)).andExpect(status().isOk());
        assertThat(count("select count(*) from case_event where case_id = ? and summary like '%UNFINISHED_SETTLEMENT=1%'",
                caseId)).isEqualTo(1);
        // Nothing was deleted by closing.
        assertThat(count("select count(*) from settlement where case_id = ?", caseId)).isEqualTo(1);
    }

    @Test
    void archivingAfterClosingAlsoNeedsTheAcknowledgementAndDeletesNothing() throws Exception {
        UUID caseId = newCase();
        UUID task = task(caseId, "SYNTH-DOC open task");
        RequestPostProcessor manager = TestActors.as(USER, "closer", Permission.CASE_VIEW, Permission.CASE_STATUS_CHANGE);
        long version = jdbc.queryForObject("select version from legal_case where id = ?", Long.class, caseId);
        post(manager, "/api/v1/cases/" + caseId + "/status", json("status", "CLOSED", "version", version))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("CLOSING_OPEN_ITEMS_NOT_ACKNOWLEDGED"));
        assertThat(jdbc.queryForObject("select status from legal_case where id = ?", String.class, caseId)).isEqualTo("OPEN");
        assertThat(count("select count(*) from task where id = ?", task)).isEqualTo(1);
        post(manager, "/api/v1/cases/" + caseId + "/status",
                json("status", "CLOSED", "version", version, "acknowledgeOpenItems", true)).andExpect(status().isOk());
        long closed = jdbc.queryForObject("select version from legal_case where id = ?", Long.class, caseId);
        post(manager, "/api/v1/cases/" + caseId + "/status", json("status", "ARCHIVED", "version", closed))
                .andExpect(status().isOk());
        assertThat(count("select count(*) from task where id = ?", task)).isEqualTo(1);
    }

    @Test
    void aCaseWithNothingOpenClosesWithoutAcknowledgement() throws Exception {
        UUID caseId = newCase();
        RequestPostProcessor manager = TestActors.as(USER, "closer", Permission.CASE_VIEW, Permission.CASE_STATUS_CHANGE);
        long version = jdbc.queryForObject("select version from legal_case where id = ?", Long.class, caseId);
        post(manager, "/api/v1/cases/" + caseId + "/status", json("status", "CLOSED", "version", version))
                .andExpect(status().isOk());
    }

    @Test
    void aJudgmentNamesItsOwnAppealDeadlineOnly() throws Exception {
        UUID caseId = newCase();
        UUID proceeding = firstInstance(caseId);
        UUID judgment = judgment(proceeding, "2026-02-01");
        UUID otherJudgment = judgment(proceeding(caseId, "APPEAL", proceeding, "2026-03-01"), "2026-04-01");
        UUID mine = jdbc.queryForObject("""
                insert into deadline (case_id, source_type, source_id, action, due_on, calc_status, created_by, updated_by)
                values (?, 'JUDGMENT', ?, 'SYNTH-DOC appeal', '2026-03-05', 'MANUAL', ?, ?) returning id""", UUID.class,
                caseId, judgment, USER, USER);
        UUID theirs = jdbc.queryForObject("""
                insert into deadline (case_id, source_type, source_id, action, due_on, calc_status, created_by, updated_by)
                values (?, 'JUDGMENT', ?, 'SYNTH-DOC other', '2026-05-05', 'MANUAL', ?, ?) returning id""", UUID.class,
                caseId, otherJudgment, USER, USER);

        put(everything(), "/api/v1/judgments/" + judgment + "/appeal-deadline", json("deadlineId", theirs))
                .andExpect(status().isBadRequest());
        put(everything(), "/api/v1/judgments/" + judgment + "/appeal-deadline", json("deadlineId", mine))
                .andExpect(status().isOk()).andExpect(jsonPath("$.appealDeadline.dueOn").value("2026-03-05"));
        get(everything(), "/api/v1/judgments/" + judgment).andExpect(jsonPath("$.appealDeadline.id").value(mine.toString()));
        put(everything(), "/api/v1/judgments/" + judgment + "/appeal-deadline", json("deadlineId", null))
                .andExpect(status().isOk()).andExpect(jsonPath("$.appealDeadline").doesNotExist());
    }

    @Test
    void similarJudgmentsAreFoundFromADefenceAloneAndFromTheClaimsAndDefencesOfTheCase() throws Exception {
        UUID caseId = newCase();
        UUID defense = created(everything(), "/api/v1/cases/" + caseId + "/defenses", json("title", "SYNTH-DOC quasar",
                "kind", "SUBSTANTIVE", "argument", "zyxwv nebula limitation"));
        claim(caseId, "SYNTH-DOC claim about frobnicate compensation");
        UUID byDefense = libraryJudgment("zyxwv nebula limitation ruling", "ACTIVE");
        UUID byClaim = libraryJudgment("frobnicate compensation ruling", "ACTIVE");
        RequestPostProcessor user = withLibrary(USER);

        String whole = body(get(user, "/api/v1/cases/" + caseId + "/similar-judgments"));
        assertThat(whole).contains(byDefense.toString()).contains(byClaim.toString())
                .contains("\"defensesIncluded\":true").contains("\"claimsIncluded\":true");
        String memo = body(get(user, "/api/v1/cases/" + caseId + "/similar-judgments?defenseId=" + defense));
        assertThat(memo).contains(byDefense.toString()).doesNotContain(byClaim.toString());
        // Without the permission to read defences the defence text is not used.
        RequestPostProcessor noDefenses = TestActors.scoped(USER, "nodef", List.of("ALL"), Permission.CASE_VIEW,
                Permission.LIBRARY_VIEW, Permission.CLAIM_VIEW);
        assertThat(body(get(noDefenses, "/api/v1/cases/" + caseId + "/similar-judgments"))).doesNotContain(byDefense.toString());
        get(noDefenses, "/api/v1/cases/" + caseId + "/similar-judgments?defenseId=" + defense)
                .andExpect(status().isForbidden());
    }

    @Test
    void aMissingRequiredEvidenceItemRaisesOneDocumentMissingNotificationPerMember() throws Exception {
        UUID member = user("Member");
        UUID caseId = newCase(member, "COMMERCIAL");
        created(everything(), "/api/v1/cases/" + caseId + "/evidence", json("name", "SYNTH-DOC contract", "role", "REQUIRED"));
        followUp.run();
        followUp.run();
        assertThat(count("select count(*) from notification where type = 'DOCUMENT_MISSING' and user_id = ?"
                + " and record_ref like 'EVIDENCE:%'", member)).isEqualTo(1);
    }

    @Test
    void theEnforcementDueDateIsOnTheCalendarOfThoseWhoMayReadEnforcement() throws Exception {
        UUID caseId = newCase();
        UUID judgment = judgment(firstInstance(caseId), "2026-02-01");
        jdbc.update("""
                insert into enforcement_file (judgment_id, case_id, status, due_on, created_by, updated_by)
                values (?, ?, 'IN_PROGRESS', '2026-06-15', ?, ?)""", judgment, caseId, USER, USER);
        RequestPostProcessor reader = TestActors.as(USER, "cal", Permission.CASE_VIEW, Permission.CALENDAR_VIEW,
                Permission.ENFORCEMENT_VIEW);
        String path = "/api/v1/calendar/events?from=2026-06-01&to=2026-06-30";
        assertThat(body(get(reader, path))).contains("ENFORCEMENT_FILE");
        RequestPostProcessor without = TestActors.as(USER, "cal2", Permission.CASE_VIEW, Permission.CALENDAR_VIEW);
        assertThat(body(get(without, path))).doesNotContain("ENFORCEMENT_FILE");
    }
}
