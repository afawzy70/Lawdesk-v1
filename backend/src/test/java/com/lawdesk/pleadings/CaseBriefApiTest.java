package com.lawdesk.pleadings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.lawdesk.security.Permission;
import com.lawdesk.security.TestActors;

/**
 * The case brief: answers read from the case's own records, with the record each one comes from, "not recorded" where the
 * records are silent, and the same case access as every other screen. Synthetic data only (SYNTH-DOC).
 */
class CaseBriefApiTest extends PleadingsTestSupport {

    private static String brief(UUID caseId) {
        return "/api/v1/cases/" + caseId + "/brief";
    }

    private UUID hearing(UUID caseId, UUID proceeding, String date, String status, String requiredAction) {
        return jdbc.queryForObject("""
                insert into hearing (case_id, proceeding_id, hearing_date, purpose, status, required_action,
                                     result_recorded_at, result_recorded_by, created_by, updated_by)
                values (?, ?, ?::date, 'PLEADING', ?, ?, case when ?::text = 'SCHEDULED' then null else now() end,
                        case when ?::text = 'SCHEDULED' then null else cast(? as uuid) end, ?, ?) returning id""", UUID.class,
                caseId, proceeding, date, status, requiredAction, status, status, USER, USER, USER);
    }

    private UUID deadline(UUID caseId, String sourceType, UUID sourceId, String action, String dueOn) {
        return jdbc.queryForObject("""
                insert into deadline (case_id, source_type, source_id, action, due_on, calc_status, created_by, updated_by)
                values (?, ?, ?, ?, ?::date, 'MANUAL', ?, ?) returning id""", UUID.class, caseId, sourceType, sourceId, action,
                dueOn, USER, USER);
    }

    private UUID defense(UUID caseId, String title) throws Exception {
        return created(everything(), "/api/v1/cases/" + caseId + "/defenses", json("title", title, "kind", "SUBSTANTIVE",
                "argument", "SYNTH-DOC argument of " + title, "legalTexts", "SYNTH-DOC article 12"));
    }

    @Test
    void theBriefAnswersFromTheRecordsAndNamesTheRecordOfEveryItem() throws Exception {
        UUID caseId = newCase();
        UUID proceeding = firstInstance(caseId);
        UUID judgment = judgment(proceeding, "2026-03-20");
        UUID rejected = claim(caseId, "SYNTH-DOC request one");
        UUID undecided = claim(caseId, "SYNTH-DOC request two");
        UUID ruled = defense(caseId, "SYNTH-DOC ruled defence");
        UUID ignored = defense(caseId, "SYNTH-DOC ignored defence");
        UUID unknown = defense(caseId, "SYNTH-DOC unknown defence");
        jdbc.update("insert into claim_result (judgment_id, claim_id, result, created_by, updated_by) values (?, ?, 'REJECTED', ?, ?)",
                judgment, rejected, USER, USER);
        jdbc.update("insert into defense_position (defense_id, judgment_id, position, created_by, updated_by)"
                + " values (?, ?, 'ACCEPTED', ?, ?)", ruled, judgment, USER, USER);
        jdbc.update("insert into defense_position (defense_id, judgment_id, position, created_by, updated_by)"
                + " values (?, ?, 'NOT_ADDRESSED', ?, ?)", ignored, judgment, USER, USER);
        UUID held = hearing(caseId, proceeding, "2026-03-01", "HELD", "SYNTH-DOC file a reply memo");
        UUID upcoming = hearing(caseId, proceeding, today().plusDays(30).toString(), "SCHEDULED", null);
        UUID appeal = deadline(caseId, "JUDGMENT", judgment, "SYNTH-DOC lodge appeal", today().plusDays(10).toString());
        jdbc.update("update judgment set appeal_deadline_id = ? where id = ?", appeal, judgment);
        UUID fromHearing = deadline(caseId, "HEARING", held, "SYNTH-DOC deposit reply", today().plusDays(5).toString());
        jdbc.update("insert into evidence_item (case_id, name, role, created_by, updated_by) values (?, 'SYNTH-DOC contract',"
                + " 'REQUIRED', ?, ?)", caseId, USER, USER);
        RequestPostProcessor user = withLibrary();

        String body = body(get(user, brief(caseId)));
        get(user, brief(caseId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.modelGenerated").value(false))
                .andExpect(jsonPath("$.origin").value("RECORDS"))
                .andExpect(jsonPath("$.notice").value(org.hamcrest.Matchers.containsString("not model-generated")))
                // timeline is chronological and every entry names its record
                .andExpect(jsonPath("$.timeline.status").value("RECORDED"))
                .andExpect(jsonPath("$.timeline.items[0].source.type").value("PROCEEDING"))
                .andExpect(jsonPath("$.timeline.items[0].source.id").value(proceeding.toString()))
                .andExpect(jsonPath("$.timeline.items[2].source.type").value("JUDGMENT"))
                // last action = the judgment of 2026-03-20; next hearing = the scheduled one
                .andExpect(jsonPath("$.lastAction.items[0].source.id").value(judgment.toString()))
                .andExpect(jsonPath("$.nextHearing.items[0].source.id").value(upcoming.toString()))
                // after the last hearing: the recorded required action and the deadline that runs from the hearing
                .andExpect(jsonPath("$.afterLastHearing.items[0].text").value("SYNTH-DOC file a reply memo"))
                .andExpect(jsonPath("$.afterLastHearing.items[0].source.id").value(held.toString()))
                .andExpect(jsonPath("$.afterLastHearing.items[1].source.id").value(fromHearing.toString()))
                // requests: position from the judgment, or NOT_RECORDED when no judgment ruled
                .andExpect(jsonPath("$.requests.items[?(@.source.id=='" + rejected + "')].status").value("REJECTED"))
                .andExpect(jsonPath("$.requests.items[?(@.source.id=='" + undecided + "')].status").value("NOT_RECORDED"))
                .andExpect(jsonPath("$.rejectedRequests.items.length()").value(1))
                .andExpect(jsonPath("$.rejectedRequests.items[0].children[0].source.id").value(judgment.toString()))
                // defences
                .andExpect(jsonPath("$.defenses.items[?(@.source.id=='" + ruled + "')].status").value("ACCEPTED"))
                .andExpect(jsonPath("$.defenses.items[?(@.source.id=='" + unknown + "')].status").value("NOT_RECORDED"))
                .andExpect(jsonPath("$.defensesNotRuledOn.items.length()").value(1))
                .andExpect(jsonPath("$.defensesNotRuledOn.items[0].source.id").value(ignored.toString()))
                // deadlines, taken from the stored rows only
                .andExpect(jsonPath("$.openDeadlines.items.length()").value(2))
                .andExpect(jsonPath("$.appealDeadlines.items[0].source.id").value(appeal.toString()))
                .andExpect(jsonPath("$.appealDeadlines.items[0].date").value(today().plusDays(10).toString()))
                // the judgment has no copy attached, and so does the evidence item
                .andExpect(jsonPath("$.missingFiles.items[?(@.kind=='JUDGMENT')].status").value("NO_FILE_ATTACHED"))
                .andExpect(jsonPath("$.missingFiles.items[?(@.kind=='EVIDENCE')].status").value("NO_FILE_ATTACHED"))
                .andExpect(jsonPath("$.legalTexts.items.length()").value(3))
                .andExpect(jsonPath("$.similarJudgments.status").exists())
                .andExpect(jsonPath("$.nextRequiredActions.items[0].text").value("SYNTH-DOC file a reply memo"));
        // no page is ever invented
        assertThat(body).doesNotContain("\"page\":1").doesNotContain("\"page\":2");
    }

    @Test
    void whatTheRecordsDoNotHoldIsReportedAsNotRecordedNotGuessed() throws Exception {
        UUID caseId = newCase();
        UUID proceeding = firstInstance(caseId);
        UUID judgment = judgment(proceeding, "2026-03-20"); // appealable and notified_on unknown, no appeal deadline named
        RequestPostProcessor user = withLibrary();
        get(user, brief(caseId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.nextHearing.status").value("NOT_RECORDED"))
                .andExpect(jsonPath("$.afterLastHearing.status").value("NOT_RECORDED"))
                .andExpect(jsonPath("$.nextRequiredActions.status").value("NOT_RECORDED"))
                .andExpect(jsonPath("$.requests.status").value("NOT_RECORDED"))
                .andExpect(jsonPath("$.openDeadlines.status").value("NOT_RECORDED"))
                .andExpect(jsonPath("$.appealDeadlines.items[0].status").value("NOT_RECORDED"))
                .andExpect(jsonPath("$.appealDeadlines.items[0].source.id").value(judgment.toString()))
                .andExpect(jsonPath("$.appealDeadlines.items[0].fields.appealable").value("NOT_RECORDED"))
                .andExpect(jsonPath("$.appealDeadlines.items[0].fields.dueOn").value("NOT_RECORDED"));
        // A case with no record at all has no last action.
        UUID empty = newCase();
        get(user, brief(empty)).andExpect(status().isOk()).andExpect(jsonPath("$.lastAction.status").value("NOT_RECORDED"))
                .andExpect(jsonPath("$.timeline.status").value("NOT_RECORDED"));
    }

    @Test
    void aHearingWithNoRecordedRequiredActionSaysSo() throws Exception {
        UUID caseId = newCase();
        UUID proceeding = firstInstance(caseId);
        UUID held = hearing(caseId, proceeding, "2026-03-01", "HELD", null);
        get(withLibrary(), brief(caseId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.afterLastHearing.items[0].status").value("NOT_RECORDED"))
                .andExpect(jsonPath("$.afterLastHearing.items[0].source.id").value(held.toString()))
                .andExpect(jsonPath("$.nextRequiredActions.status").value("NOT_RECORDED"));
    }

    @Test
    void aCopyThatWasWithdrawnIsReportedAndTheDocumentIsNamedOnlyToThoseWhoMayOpenDocuments() throws Exception {
        UUID caseId = newCase();
        UUID judgment = judgment(firstInstance(caseId), "2026-03-20");
        UUID doc = document(caseId, "SYNTH-DOC copy");
        jdbc.update("update judgment set copy_document_id = ? where id = ?", doc, judgment);
        jdbc.update("update case_document set withdrawn_at = now(), withdrawn_by = ?, withdrawn_reason = 'x' where id = ?", USER, doc);
        get(withLibrary(), brief(caseId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.missingFiles.items[0].status").value("FILE_WITHDRAWN"))
                .andExpect(jsonPath("$.missingFiles.items[0].source.documentTitle").value("SYNTH-DOC copy"));
        RequestPostProcessor noDocuments = TestActors.scoped(USER, "nodocs", List.of("ALL"), Permission.CASE_VIEW,
                Permission.JUDGMENT_VIEW);
        get(noDocuments, brief(caseId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.missingFiles.items[0].source.documentTitle").doesNotExist())
                .andExpect(jsonPath("$.missingFiles.items[0].source.documentId").doesNotExist());
    }

    @Test
    void aPartIsNotPermittedWhenTheUserMayNotReadItsRecords() throws Exception {
        UUID caseId = newCase();
        claim(caseId, "SYNTH-DOC request");
        RequestPostProcessor caseOnly = TestActors.scoped(USER, "caseonly", List.of("ALL"), Permission.CASE_VIEW);
        get(caseOnly, brief(caseId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.requests.status").value("NOT_PERMITTED"))
                .andExpect(jsonPath("$.defenses.status").value("NOT_PERMITTED"))
                .andExpect(jsonPath("$.openDeadlines.status").value("NOT_PERMITTED"))
                .andExpect(jsonPath("$.nextHearing.status").value("NOT_PERMITTED"))
                .andExpect(jsonPath("$.similarJudgments.status").value("NOT_PERMITTED"));
        // Without the case permission at all the route is refused.
        get(TestActors.scoped(USER, "nothing", List.of("ALL"), Permission.CLAIM_VIEW), brief(caseId))
                .andExpect(status().isForbidden());
    }

    @Test
    void aCaseOutsideTheUsersScopeIsMissingNotRefused() throws Exception {
        UUID member = user("Member");
        UUID caseId = newCase(member, "COMMERCIAL");
        UUID other = user("Other");
        get(outsider(other), brief(caseId)).andExpect(status().isNotFound());
        get(member(member), brief(caseId)).andExpect(status().isOk());
        get(outsider(other), "/api/v1/cases/" + UUID.randomUUID() + "/brief").andExpect(status().isNotFound());
    }

    @Test
    void aRestrictedCaseIsHiddenFromTheScopeAndOpenedByAGrant() throws Exception {
        UUID caseId = newCase();
        jdbc.update("update legal_case set confidentiality = 'RESTRICTED' where id = ?", caseId);
        UUID reader = user("Reader");
        RequestPostProcessor scoped = scopedReader(reader);
        get(scoped, brief(caseId)).andExpect(status().isNotFound());
        jdbc.update("insert into case_grant (case_id, user_id, level, reason, granted_by) values (?, ?, 'VIEW', 'SYNTH-DOC', ?)",
                caseId, reader, USER);
        get(scoped, brief(caseId)).andExpect(status().isOk());
        jdbc.update("update case_grant set revoked_at = now(), revoked_by = ? where case_id = ?", USER, caseId);
        get(scoped, brief(caseId)).andExpect(status().isNotFound());
    }

    @Test
    void accessIsDecidedBeforeTheRequestIsValidated() throws Exception {
        UUID member = user("Member");
        UUID caseId = newCase(member, "COMMERCIAL");
        UUID other = user("Other");
        // An invalid limit must not turn the answer for an out-of-scope user into 400: the case is missing.
        get(outsider(other), brief(caseId) + "?similarLimit=0").andExpect(status().isNotFound());
        get(outsider(other), brief(caseId) + "?similarLimit=999").andExpect(status().isNotFound());
        // A user who may see the case does get the validation error.
        get(member(member), brief(caseId) + "?similarLimit=0").andExpect(status().isBadRequest());
        get(member(member), brief(caseId) + "?similarLimit=26").andExpect(status().isBadRequest());
    }

    private RequestPostProcessor scopedReader(UUID id) {
        return TestActors.scoped(id, "reader", List.of("ALL"), ALL_PLEADINGS);
    }

    private RequestPostProcessor withLibrary() {
        List<Permission> all = new java.util.ArrayList<>(List.of(ALL_PLEADINGS));
        all.add(Permission.LIBRARY_VIEW);
        all.add(Permission.HEARING_VIEW);
        all.add(Permission.DEADLINE_VIEW);
        return TestActors.scoped(USER, "briefer", List.of("ALL"), all.toArray(Permission[]::new));
    }
}
