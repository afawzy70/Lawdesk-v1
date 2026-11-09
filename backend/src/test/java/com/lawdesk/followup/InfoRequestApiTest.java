package com.lawdesk.followup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.lawdesk.security.Permission;

/**
 * LD-053, requirements section 18, scenario S6: a request to a unit of the authority, its replies (several, partial,
 * incomplete), the follow-up of a late reply and its escalation, with the case-visibility rules of D3.
 */
class InfoRequestApiTest extends Wp3TestSupport {

    private static final Permission[] MANAGER = {Permission.CASE_VIEW, Permission.INFO_REQUEST_VIEW,
        Permission.INFO_REQUEST_MANAGE, Permission.INFO_REQUEST_ESCALATE, Permission.DOCUMENT_VIEW,
        Permission.DOCUMENT_MANAGE};
    private static final Permission[] STAFF = {Permission.CASE_VIEW, Permission.INFO_REQUEST_VIEW,
        Permission.INFO_REQUEST_MANAGE, Permission.DOCUMENT_VIEW, Permission.DOCUMENT_MANAGE};

    private UUID member;
    private UUID supervisor;
    private UUID stranger;
    private UUID caseId;
    private UUID unit;
    private RequestPostProcessor specialist;
    private RequestPostProcessor head;

    @BeforeEach
    void aCaseWithItsMembers() {
        member = user("Mona Specialist");
        supervisor = user("Hala Supervisor");
        stranger = user("Stranger");
        caseId = legalCase(member, null);
        unit = authorityUnit("إدارة الشؤون الإدارية");
        specialist = staff(member, STAFF);
        head = staff(supervisor, MANAGER);
    }

    private String create(String subject, Object dueOn) {
        return json("subject", subject, "unitId", unit, "details", "Send the file of the decision",
                "requiredDocuments", "The decision and its notice", "draftedOn", today().toString(), "dueOn", dueOn,
                "responsibleMemberId", member, "addressee", "Mr. Ali");
    }

    private UUID created(String subject, Object dueOn) throws Exception {
        MvcResult result = mvc.perform(postJson(specialist, "/api/v1/cases/" + caseId + "/info-requests",
                create(subject, dueOn))).andExpect(status().isCreated()).andReturn();
        return idOf(result);
    }

    private void moveTo(UUID id, String target, Object... extra) throws Exception {
        long version = jdbc.queryForObject("select version from info_request where id = ?", Long.class, id);
        Object[] all = new Object[extra.length + 4];
        all[0] = "status";
        all[1] = target;
        all[2] = "version";
        all[3] = version;
        System.arraycopy(extra, 0, all, 4, extra.length);
        mvc.perform(postJson(specialist, "/api/v1/info-requests/" + id + "/status", json(all)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(target));
    }

    private UUID sent(String subject, Object dueOn) throws Exception {
        UUID id = created(subject, dueOn);
        moveTo(id, "SENT", "sentOn", today().toString(), "letterNo", "L-12/2026", "letterDate", today().toString());
        return id;
    }

    private void reply(UUID id, String completeness, String summary) throws Exception {
        mvc.perform(postJson(specialist, "/api/v1/info-requests/" + id + "/replies",
                        json("receivedOn", today().toString(), "completeness", completeness, "summary", summary)))
                .andExpect(status().isCreated());
    }

    // --- registration (18.1) -----------------------------------------------------------------------------------

    @Test
    void registersADraftRequestWithASystemGeneratedNumber() throws Exception {
        mvc.perform(postJson(specialist, "/api/v1/cases/" + caseId + "/info-requests",
                        create("Decision file", today().plusDays(10).toString())
                                .replace("{", "{\"requestNo\":\"IR-FAKE\",")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requestNo").value(Matchers.matchesPattern("IR-\\d{4}-\\d{5}")))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.caseId").value(caseId.toString()))
                .andExpect(jsonPath("$.unitId").value(unit.toString()))
                .andExpect(jsonPath("$.unitName").value("إدارة الشؤون الإدارية"))
                .andExpect(jsonPath("$.subject").value("Decision file"))
                .andExpect(jsonPath("$.responsibleMemberName").value("Mona Specialist"))
                .andExpect(jsonPath("$.overdue").value(false))
                .andExpect(jsonPath("$.replies").isEmpty())
                .andExpect(jsonPath("$.version").value(0));
        assertThat(count("select count(*) from info_request where request_no = 'IR-FAKE'")).isZero();
        assertThat(count("select count(*) from case_event where case_id = ? and type = 'INFO_REQUEST_CREATED'", caseId))
                .isEqualTo(1);
    }

    @Test
    void numbersAreUniqueAndKeepTheirUnitNameWhenTheUnitIsRenamed() throws Exception {
        UUID first = created("First", null);
        UUID second = created("Second", null);
        String one = jdbc.queryForObject("select request_no from info_request where id = ?", String.class, first);
        String two = jdbc.queryForObject("select request_no from info_request where id = ?", String.class, second);
        assertThat(one).isNotEqualTo(two);
        jdbc.update("update org_unit set name_ar = 'اسم جديد' where id = ?", unit);
        mvc.perform(getJson(specialist, "/api/v1/info-requests/" + first))
                .andExpect(jsonPath("$.unitName").value("إدارة الشؤون الإدارية"));
    }

    @Test
    void refusesWhatIsNotAValidRequest() throws Exception {
        String path = "/api/v1/cases/" + caseId + "/info-requests";
        mvc.perform(postJson(specialist, path, json("details", "no subject"))).andExpect(status().isBadRequest());
        mvc.perform(postJson(specialist, path, json("subject", "  "))).andExpect(status().isBadRequest());
        mvc.perform(postJson(specialist, path, json("subject", "x", "unitId", UUID.randomUUID())))
                .andExpect(status().isBadRequest());
        mvc.perform(postJson(specialist, path, json("subject", "x", "responsibleMemberId", UUID.randomUUID())))
                .andExpect(status().isBadRequest());
        // A unit of the legal department's own tree is not a unit the authority can be asked.
        UUID legalUnit = jdbc.queryForObject("""
                insert into org_unit (tree, level, name_ar, created_by, updated_by)
                values ('LEGAL', 'SECTION', 'قسم', gen_random_uuid(), gen_random_uuid()) returning id""", UUID.class);
        mvc.perform(postJson(specialist, path, json("subject", "x", "unitId", legalUnit)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anUnspecifiedUnitAndDueDateAreValid() throws Exception {
        mvc.perform(postJson(specialist, "/api/v1/cases/" + caseId + "/info-requests", json("subject", "Open ended")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.unitId").doesNotExist())
                .andExpect(jsonPath("$.dueOn").doesNotExist());
    }

    @Test
    void updatesFieldsUnderTheVersionAndRefusesAStaleOne() throws Exception {
        UUID id = created("Original", null);
        mvc.perform(putJson(specialist, "/api/v1/info-requests/" + id,
                        json("subject", "Changed", "dueOn", today().plusDays(3).toString(), "version", 0)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.subject").value("Changed"))
                .andExpect(jsonPath("$.version").value(1));
        mvc.perform(putJson(specialist, "/api/v1/info-requests/" + id, json("subject", "Again", "version", 0)))
                .andExpect(status().isConflict());
    }

    // --- states (18.2) -----------------------------------------------------------------------------------------

    @Test
    void movesThroughApprovalSendingAndWaiting() throws Exception {
        UUID id = created("Flow", today().plusDays(5).toString());
        moveTo(id, "PENDING_APPROVAL");
        moveTo(id, "SENT", "sentOn", today().toString(), "letterNo", "L-9", "letterDate", today().toString());
        mvc.perform(getJson(specialist, "/api/v1/info-requests/" + id)).andExpect(jsonPath("$.letterNo").value("L-9"))
                .andExpect(jsonPath("$.sentOn").value(today().toString()));
        moveTo(id, "AWAITING");
    }

    @Test
    void refusesATransitionTheFlowDoesNotAllowWithItsRule() throws Exception {
        UUID id = created("Flow", null);
        mvc.perform(postJson(specialist, "/api/v1/info-requests/" + id + "/status",
                        json("status", "AWAITING", "version", 0)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.rule").value("INFO_REQUEST_TRANSITION_NOT_ALLOWED"));
        // States that follow from the replies are not set by hand.
        mvc.perform(postJson(specialist, "/api/v1/info-requests/" + id + "/status",
                        json("status", "COMPLETE", "version", 0))).andExpect(status().isBadRequest());
    }

    @Test
    void closingAnIncompleteRequestNeedsAReasonAndALateOneIsNeverClosedByTheSystem() throws Exception {
        UUID id = sent("Close me", today().minusDays(2).toString());
        mvc.perform(postJson(specialist, "/api/v1/info-requests/" + id + "/status",
                        json("status", "CLOSED", "version", 1))).andExpect(status().isBadRequest());
        mvc.perform(postJson(specialist, "/api/v1/info-requests/" + id + "/status",
                        json("status", "CLOSED", "closeReason", "No longer needed", "version", 1)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.closeReason").value("No longer needed"))
                .andExpect(jsonPath("$.closedOn").value(today().toString()));
        // A closed request is not overdue.
        mvc.perform(getJson(specialist, "/api/v1/info-requests/" + id)).andExpect(jsonPath("$.overdue").value(false));
    }

    // --- replies (18.2, 18.4) ----------------------------------------------------------------------------------

    @Test
    void keepsEveryReplyAndFollowsTheLatestOneUntilOneIsComplete() throws Exception {
        UUID id = sent("Replies", today().plusDays(10).toString());
        reply(id, "PARTIAL", "First part");
        assertThat(statusOf(id)).isEqualTo("PARTIAL");
        reply(id, "INCOMPLETE", "Second part is missing pages");
        assertThat(statusOf(id)).isEqualTo("INCOMPLETE");
        reply(id, "COMPLETE", "Everything");
        assertThat(statusOf(id)).isEqualTo("COMPLETE");
        mvc.perform(getJson(specialist, "/api/v1/cases/" + caseId + "/info-requests"))
                .andExpect(jsonPath("$.items[0].replies.length()").value(3))
                .andExpect(jsonPath("$.items[0].replies[0].completeness").value("PARTIAL"))
                .andExpect(jsonPath("$.items[0].replies[2].completeness").value("COMPLETE"));
        assertThat(count("select count(*) from info_reply where request_id = ?", id)).isEqualTo(3);
    }

    @Test
    void aReplyIsRefusedWhileTheRequestIsNotSentOrAfterItIsClosed() throws Exception {
        UUID id = created("Draft", null);
        mvc.perform(postJson(specialist, "/api/v1/info-requests/" + id + "/replies",
                        json("receivedOn", today().toString(), "completeness", "PARTIAL")))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.rule").value("INFO_REQUEST_NOT_SENT"));
        moveTo(id, "SENT");
        moveTo(id, "CLOSED", "closeReason", "Withdrawn");
        mvc.perform(postJson(specialist, "/api/v1/info-requests/" + id + "/replies",
                        json("receivedOn", today().toString(), "completeness", "PARTIAL")))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.rule").value("INFO_REQUEST_CLOSED"));
    }

    @Test
    void aReplyDateInTheFutureIsRefused() throws Exception {
        UUID id = sent("Future", null);
        mvc.perform(postJson(specialist, "/api/v1/info-requests/" + id + "/replies",
                        json("receivedOn", today().plusDays(2).toString(), "completeness", "PARTIAL")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aReplyDocumentIsArchivedInTheCaseFileAndLinkedToTheReply() throws Exception {
        UUID id = sent("With document", null);
        MockMultipartFile file = new MockMultipartFile("file", "reply.pdf", "application/pdf",
                "%PDF-1.4 reply".getBytes(StandardCharsets.ISO_8859_1));
        MvcResult result = mvc.perform(multipart("/api/v1/info-requests/" + id + "/replies").file(file)
                        .param("receivedOn", today().toString()).param("completeness", "COMPLETE")
                        .param("summary", "Attached").with(specialist).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentId").isString())
                .andReturn();
        UUID documentId = UUID.fromString(read(result, "$.documentId"));
        assertThat(count("select count(*) from case_document where id = ? and case_id = ?", documentId, caseId))
                .isEqualTo(1);
        assertThat(count("select count(*) from case_event where case_id = ? and type = 'DOCUMENT_ATTACHED'", caseId))
                .isEqualTo(1);
        mvc.perform(getJson(specialist, "/api/v1/cases/" + caseId + "/documents"))
                .andExpect(jsonPath("$.items[0].id").value(documentId.toString()));
    }

    @Test
    void aNonPdfReplyDocumentIsRefusedAndNothingIsRecorded() throws Exception {
        UUID id = sent("Bad document", null);
        MockMultipartFile file = new MockMultipartFile("file", "reply.txt", "text/plain", "hello".getBytes());
        mvc.perform(multipart("/api/v1/info-requests/" + id + "/replies").file(file)
                        .param("receivedOn", today().toString()).param("completeness", "COMPLETE")
                        .with(specialist).with(csrf()))
                .andExpect(status().isBadRequest());
        assertThat(count("select count(*) from info_reply where request_id = ?", id)).isZero();
    }

    @Test
    void anExistingCaseDocumentCanBeNamedButOnlyFromTheSameCase() throws Exception {
        UUID id = sent("Existing document", null);
        UUID other = legalCase(member, null);
        UUID foreign = jdbc.queryForObject("""
                insert into case_document (case_id, title, file_name, size_bytes, sha256, created_by)
                values (?, 't', 'a.pdf', 5, repeat('a', 64), ?) returning id""", UUID.class, other, member);
        mvc.perform(postJson(specialist, "/api/v1/info-requests/" + id + "/replies",
                        json("receivedOn", today().toString(), "completeness", "PARTIAL", "documentId", foreign)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.rule").value("REPLY_DOCUMENT_NOT_IN_CASE"));
        UUID own = jdbc.queryForObject("""
                insert into case_document (case_id, title, file_name, size_bytes, sha256, created_by)
                values (?, 't', 'a.pdf', 5, repeat('b', 64), ?) returning id""", UUID.class, caseId, member);
        mvc.perform(postJson(specialist, "/api/v1/info-requests/" + id + "/replies",
                        json("receivedOn", today().toString(), "completeness", "PARTIAL", "documentId", own)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.documentId").value(own.toString()));
    }

    // --- late replies and escalation (18.3) ----------------------------------------------------------------------

    @Test
    void theFollowUpRunMarksALateRequestOverdueNotifiesOnceAndNeverClosesIt() throws Exception {
        UUID id = sent("Late", today().minusDays(3).toString());
        mvc.perform(getJson(specialist, "/api/v1/info-requests/" + id)).andExpect(jsonPath("$.overdue").value(true))
                .andExpect(jsonPath("$.status").value("SENT"));

        mvc.perform(postJson(head, "/api/v1/follow-up/run", "{}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.overdueRequests").value(1)).andExpect(jsonPath("$.notifications").value(1));
        assertThat(statusOf(id)).isEqualTo("OVERDUE");
        assertThat(count("select count(*) from notification where type = 'INFO_REQUEST_OVERDUE' and user_id = ?",
                member)).isEqualTo(1);

        mvc.perform(postJson(head, "/api/v1/follow-up/run", "{}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications").value(0));
        assertThat(count("select count(*) from notification where type = 'INFO_REQUEST_OVERDUE'")).isEqualTo(1);
        assertThat(statusOf(id)).isEqualTo("OVERDUE");

        // The late request is listed, and a later reply moves it on without anyone closing it.
        mvc.perform(getJson(specialist, "/api/v1/info-requests?overdue=true")).andExpect(jsonPath("$.totalItems").value(1));
        reply(id, "PARTIAL", "Late first part");
        assertThat(statusOf(id)).isEqualTo("PARTIAL");
        mvc.perform(getJson(specialist, "/api/v1/info-requests/" + id)).andExpect(jsonPath("$.overdue").value(true));
    }

    @Test
    void theNotificationReachesTheResponsibleMemberAndTheCaseSpecialistOnly() throws Exception {
        UUID other = user("Other responsible");
        UUID id = created("Late", today().minusDays(1).toString());
        jdbc.update("update info_request set responsible_member_id = ? where id = ?", other, id);
        moveTo(id, "SENT");
        mvc.perform(postJson(head, "/api/v1/follow-up/run", "{}")).andExpect(jsonPath("$.notifications").value(2));
        assertThat(jdbc.queryForList("select user_id from notification order by 1", UUID.class))
                .containsExactlyInAnyOrder(member, other);
    }

    @Test
    void movingTheDueDateForwardReturnsAnOverdueRequestToItsEarlierState() throws Exception {
        UUID id = sent("Extended", today().minusDays(1).toString());
        moveTo(id, "AWAITING");
        mvc.perform(postJson(head, "/api/v1/follow-up/run", "{}")).andExpect(status().isOk());
        assertThat(statusOf(id)).isEqualTo("OVERDUE");
        long version = jdbc.queryForObject("select version from info_request where id = ?", Long.class, id);
        mvc.perform(putJson(specialist, "/api/v1/info-requests/" + id, json("subject", "Extended", "dueOn",
                        today().plusDays(7).toString(), "delayReason", "The unit asked for more time", "version", version)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("AWAITING"))
                .andExpect(jsonPath("$.delayReason").value("The unit asked for more time"))
                .andExpect(jsonPath("$.overdue").value(false));
    }

    @Test
    void remindersAndNewLettersAreRecordedInOrderWithTheirDate() throws Exception {
        UUID id = sent("Chase", today().minusDays(1).toString());
        String path = "/api/v1/info-requests/" + id + "/follow-ups";
        mvc.perform(postJson(specialist, path, json("kind", "REMINDER", "note", "Phoned the office")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.kind").value("REMINDER"))
                .andExpect(jsonPath("$.onDate").value(today().toString()));
        mvc.perform(postJson(specialist, path, json("kind", "RE_ADDRESS", "onDate", today().toString(),
                        "note", "Second letter")))
                .andExpect(status().isCreated());
        mvc.perform(getJson(specialist, "/api/v1/info-requests/" + id))
                .andExpect(jsonPath("$.followUps.length()").value(2))
                .andExpect(jsonPath("$.followUps[1].kind").value("RE_ADDRESS"))
                .andExpect(jsonPath("$.followUps[0].byName").value("Mona Specialist"));
    }

    @Test
    void escalationNeedsItsPermissionAndATargetAndNotifiesTheTarget() throws Exception {
        UUID id = sent("Escalate", today().minusDays(1).toString());
        String path = "/api/v1/info-requests/" + id + "/follow-ups";
        // The specialist holds no INFO_REQUEST_ESCALATE.
        mvc.perform(postJson(specialist, path, json("kind", "ESCALATION", "escalatedTo", supervisor)))
                .andExpect(status().isForbidden());
        mvc.perform(postJson(staff(member, MANAGER), path, json("kind", "ESCALATION"))).andExpect(status().isBadRequest());
        mvc.perform(postJson(staff(member, MANAGER), path, json("kind", "ESCALATION", "escalatedTo", UUID.randomUUID())))
                .andExpect(status().isBadRequest());
        mvc.perform(postJson(staff(member, MANAGER), path, json("kind", "REMINDER", "escalatedTo", supervisor)))
                .andExpect(status().isBadRequest());
        mvc.perform(postJson(staff(member, MANAGER), path, json("kind", "ESCALATION", "escalatedTo", supervisor,
                        "note", "Three weeks without an answer")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.escalatedTo").value(supervisor.toString()));
        assertThat(count("select count(*) from notification where type = 'INFO_REQUEST_ESCALATED' and user_id = ?",
                supervisor)).isEqualTo(1);
    }

    @Test
    void followUpIsRefusedOnADraftAndOnAClosedRequest() throws Exception {
        UUID id = created("Draft", null);
        mvc.perform(postJson(specialist, "/api/v1/info-requests/" + id + "/follow-ups", json("kind", "REMINDER")))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.rule").value("INFO_REQUEST_NOT_SENT"));
    }

    // --- who sees and who changes (D3) ------------------------------------------------------------------------------

    @Test
    void anOutsiderCannotFindTheCaseOrItsRequestsAnywhere() throws Exception {
        UUID id = sent("Secret", today().minusDays(1).toString());
        RequestPostProcessor other = outsider(stranger, Permission.CASE_VIEW, Permission.INFO_REQUEST_VIEW,
                Permission.INFO_REQUEST_MANAGE, Permission.INFO_REQUEST_ESCALATE);
        mvc.perform(getJson(other, "/api/v1/cases/" + caseId + "/info-requests")).andExpect(status().isNotFound());
        mvc.perform(getJson(other, "/api/v1/info-requests/" + id)).andExpect(status().isNotFound());
        mvc.perform(postJson(other, "/api/v1/cases/" + caseId + "/info-requests", json("subject", "x")))
                .andExpect(status().isNotFound());
        mvc.perform(putJson(other, "/api/v1/info-requests/" + id, json("subject", "x", "version", 1)))
                .andExpect(status().isNotFound());
        mvc.perform(postJson(other, "/api/v1/info-requests/" + id + "/status", json("status", "CLOSED", "closeReason", "x", "version", 1)))
                .andExpect(status().isNotFound());
        mvc.perform(postJson(other, "/api/v1/info-requests/" + id + "/replies",
                        json("receivedOn", today().toString(), "completeness", "PARTIAL"))).andExpect(status().isNotFound());
        mvc.perform(postJson(other, "/api/v1/info-requests/" + id + "/follow-ups", json("kind", "REMINDER")))
                .andExpect(status().isNotFound());
        mvc.perform(getJson(other, "/api/v1/info-requests")).andExpect(jsonPath("$.totalItems").value(0));
        mvc.perform(getJson(other, "/api/v1/info-requests/report")).andExpect(jsonPath("$.units.length()").value(0));
    }

    @Test
    void someoneWhoOnlySeesTheCaseReadsButDoesNotChangeIt() throws Exception {
        UUID id = sent("Visible", null);
        RequestPostProcessor reader = viewer(stranger, Permission.CASE_VIEW, Permission.INFO_REQUEST_VIEW,
                Permission.INFO_REQUEST_MANAGE, Permission.INFO_REQUEST_ESCALATE);
        mvc.perform(getJson(reader, "/api/v1/info-requests/" + id)).andExpect(status().isOk());
        mvc.perform(getJson(reader, "/api/v1/info-requests")).andExpect(jsonPath("$.totalItems").value(1));
        mvc.perform(postJson(reader, "/api/v1/cases/" + caseId + "/info-requests", json("subject", "x")))
                .andExpect(status().isForbidden());
        mvc.perform(putJson(reader, "/api/v1/info-requests/" + id, json("subject", "x", "version", 1)))
                .andExpect(status().isForbidden());
        mvc.perform(postJson(reader, "/api/v1/info-requests/" + id + "/replies",
                        json("receivedOn", today().toString(), "completeness", "PARTIAL"))).andExpect(status().isForbidden());
        mvc.perform(postJson(reader, "/api/v1/info-requests/" + id + "/follow-ups",
                        json("kind", "ESCALATION", "escalatedTo", supervisor))).andExpect(status().isForbidden());
        mvc.perform(postJson(reader, "/api/v1/info-requests/" + id + "/status", json("status", "AWAITING", "version", 1)))
                .andExpect(status().isForbidden());
    }

    @Test
    void thePermissionsGateTheModule() throws Exception {
        UUID id = sent("Gated", null);
        RequestPostProcessor noView = staff(stranger, Permission.CASE_VIEW);
        mvc.perform(getJson(noView, "/api/v1/info-requests/" + id)).andExpect(status().isForbidden());
        mvc.perform(getJson(noView, "/api/v1/cases/" + caseId + "/info-requests")).andExpect(status().isForbidden());
        RequestPostProcessor viewOnly = staff(member, Permission.CASE_VIEW, Permission.INFO_REQUEST_VIEW);
        mvc.perform(postJson(viewOnly, "/api/v1/cases/" + caseId + "/info-requests", json("subject", "x")))
                .andExpect(status().isForbidden());
        mvc.perform(postJson(viewOnly, "/api/v1/follow-up/run", "{}")).andExpect(status().isForbidden());
    }

    @Test
    void theGlobalListShowsOnlyRequestsOfVisibleCasesAndFiltersByStatusAndUnit() throws Exception {
        UUID mine = sent("Mine", null);
        // The specialist's own scope: only the cases they are a member of (the department-wide actor would see both).
        RequestPostProcessor member = outsider(this.member, STAFF);
        UUID otherCase = legalCase(supervisor, null);
        mvc.perform(postJson(head, "/api/v1/cases/" + otherCase + "/info-requests", json("subject", "Theirs")))
                .andExpect(status().isCreated());
        mvc.perform(getJson(member, "/api/v1/info-requests")).andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(mine.toString()));
        mvc.perform(getJson(member, "/api/v1/info-requests?status=DRAFT")).andExpect(jsonPath("$.totalItems").value(0));
        mvc.perform(getJson(member, "/api/v1/info-requests?status=SENT")).andExpect(jsonPath("$.totalItems").value(1));
        mvc.perform(getJson(member, "/api/v1/info-requests?unitId=" + unit)).andExpect(jsonPath("$.totalItems").value(1));
        mvc.perform(getJson(member, "/api/v1/info-requests?unitId=" + UUID.randomUUID()))
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    // --- the responsiveness report (18.5) ------------------------------------------------------------------------------

    @Test
    void theReportCountsRequestsRepliesDelaysAndIncompleteRepliesPerUnit() throws Exception {
        UUID late = sent("Late one", today().minusDays(2).toString());
        UUID answered = sent("Answered", today().plusDays(5).toString());
        reply(answered, "INCOMPLETE", "Missing pages");
        reply(answered, "COMPLETE", "All of it");
        jdbc.update("update info_request set sent_on = ? where id = ?", today().minusDays(4), answered);
        mvc.perform(getJson(specialist, "/api/v1/info-requests/report")).andExpect(status().isOk())
                .andExpect(jsonPath("$.units.length()").value(1))
                .andExpect(jsonPath("$.units[0].unitId").value(unit.toString()))
                .andExpect(jsonPath("$.units[0].requests").value(2))
                .andExpect(jsonPath("$.units[0].replies").value(2))
                .andExpect(jsonPath("$.units[0].answeredRequests").value(1))
                .andExpect(jsonPath("$.units[0].overdue").value(1))
                .andExpect(jsonPath("$.units[0].incompleteReplies").value(1))
                .andExpect(jsonPath("$.units[0].affectedCases").value(1))
                .andExpect(jsonPath("$.units[0].averageReplyDays").value(4.0));
        assertThat(late).isNotNull();
    }

    private String statusOf(UUID id) {
        return jdbc.queryForObject("select status from info_request where id = ?", String.class, id);
    }
}
