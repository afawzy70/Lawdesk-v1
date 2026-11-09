package com.lawdesk.directives;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.lawdesk.security.Permission;
import com.lawdesk.wp9.Wp9TestSupport;

/**
 * LD-057, requirements section 25 (LD-DOC-25-001..002): the register of administrative decisions and assignments about
 * cases: number, date, source, addressee, due date and execution status, linked to a case and optionally a task, audited,
 * and visible only to those who may see the case (D3).
 */
class DirectiveApiTest extends Wp9TestSupport {

    private UUID head;
    private UUID member;
    private UUID outsiderUser;
    private UUID caseId;
    private RequestPostProcessor issuer;

    @BeforeEach
    void aCaseAndItsIssuer() {
        head = user("Head");
        member = user("Member");
        outsiderUser = user("Outsider");
        caseId = legalCase(head, null);
        issuer = staff(head, Permission.CASE_VIEW, Permission.DIRECTIVE_MANAGE);
    }

    private String body() {
        return json("caseId", caseId, "kind", "PREPARE_MEMO", "title", "إعداد مذكرة الرد على الدعوى",
                "details", "خلال أسبوع من تاريخه", "directiveDate", today().toString(), "source", "قرار مدير الإدارة رقم ١٢",
                "sourceReferenceNo", "12/2026", "addresseeUserId", member, "dueDate", today().plusDays(7).toString());
    }

    private UUID issue() throws Exception {
        return idOf(mvc.perform(postJson(issuer, "/api/v1/directives", body())).andExpect(status().isCreated()).andReturn());
    }

    @Test
    void aDirectiveRecordsItsNumberDateSourceAddresseeDueDateAndStatus() throws Exception {
        MvcResult created = mvc.perform(postJson(issuer, "/api/v1/directives", body())).andExpect(status().isCreated())
                .andExpect(jsonPath("$.directiveNo").value(org.hamcrest.Matchers.matchesPattern("DIR-\\d{4}-\\d{5}")))
                .andExpect(jsonPath("$.caseId").value(caseId.toString())).andExpect(jsonPath("$.caseReferenceNo").isString())
                .andExpect(jsonPath("$.kind").value("PREPARE_MEMO")).andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.directiveDate").value(today().toString()))
                .andExpect(jsonPath("$.source").value("قرار مدير الإدارة رقم ١٢"))
                .andExpect(jsonPath("$.sourceReferenceNo").value("12/2026"))
                .andExpect(jsonPath("$.addresseeUserId").value(member.toString()))
                .andExpect(jsonPath("$.addresseeName").value("Member"))
                .andExpect(jsonPath("$.dueDate").value(today().plusDays(7).toString()))
                .andExpect(jsonPath("$.overdue").value(false)).andExpect(jsonPath("$.createdBy").value(head.toString())).andReturn();
        assertThat(count("select count(*) from case_event where case_id = ? and type = 'DIRECTIVE_ISSUED'", caseId)).isEqualTo(1);
        assertThat(count("select count(*) from audit_event where action = 'DIRECTIVE_CREATE' and outcome = 'SUCCESS'"
                + " and target_id = ?", idOf(created).toString())).isEqualTo(1);
    }

    @Test
    void theNumberIsGeneratedByTheSystemAndNeverRepeats() throws Exception {
        UUID first = issue();
        UUID second = issue();
        String a = jdbc.queryForObject("select directive_no from directive where id = ?", String.class, first);
        String b = jdbc.queryForObject("select directive_no from directive where id = ?", String.class, second);
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void anAddresseeMayBeOutsideTheSystemButSomeoneMustBeNamed() throws Exception {
        mvc.perform(postJson(issuer, "/api/v1/directives",
                        json("caseId", caseId, "title", "مخاطبة إدارة الشؤون الإدارية", "directiveDate", today().toString(),
                                "source", "رئيس القسم", "addresseeText", "إدارة الشؤون الإدارية")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.addresseeName").value("إدارة الشؤون الإدارية"))
                .andExpect(jsonPath("$.kind").doesNotExist()).andExpect(jsonPath("$.dueDate").doesNotExist());
        mvc.perform(postJson(issuer, "/api/v1/directives", json("caseId", caseId, "title", "x", "directiveDate",
                today().toString(), "source", "s"))).andExpect(status().isBadRequest());
    }

    @Test
    void theRequestIsValidated() throws Exception {
        String ok = body();
        mvc.perform(postJson(issuer, "/api/v1/directives", ok.replace("\"kind\":\"PREPARE_MEMO\"", "\"kind\":\"NOPE\"")))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.rule").value("DIRECTIVE_KIND_UNKNOWN"));
        mvc.perform(postJson(issuer, "/api/v1/directives", json("caseId", caseId, "title", " ", "directiveDate",
                today().toString(), "source", "s", "addresseeText", "a"))).andExpect(status().isBadRequest());
        mvc.perform(postJson(issuer, "/api/v1/directives", json("caseId", caseId, "title", "t", "directiveDate",
                today().toString(), "source", " ", "addresseeText", "a"))).andExpect(status().isBadRequest());
        mvc.perform(postJson(issuer, "/api/v1/directives", json("caseId", caseId, "title", "t", "directiveDate",
                today().toString(), "dueDate", today().minusDays(1).toString(), "source", "s", "addresseeText", "a")))
                .andExpect(status().isBadRequest());
        mvc.perform(postJson(issuer, "/api/v1/directives", json("caseId", caseId, "title", "t", "directiveDate",
                today().toString(), "source", "s", "addresseeUserId", UUID.randomUUID()))).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.rule").value("DIRECTIVE_ADDRESSEE_UNKNOWN"));
    }

    @Test
    void aDirectiveMayPointAtATaskOfTheSameCaseOnly() throws Exception {
        UUID mine = task(caseId, "مهمة", null, member);
        UUID foreign = task(legalCase(head, null), "مهمة أخرى", null, member);
        mvc.perform(postJson(issuer, "/api/v1/directives", body().replace("}", ",\"taskId\":\"" + mine + "\"}")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.taskId").value(mine.toString()));
        mvc.perform(postJson(issuer, "/api/v1/directives", body().replace("}", ",\"taskId\":\"" + foreign + "\"}")))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.rule").value("DIRECTIVE_TASK_NOT_IN_CASE"));
    }

    @Test
    void theIssuerEditsTheDirectiveWithItsVersionAndTheChangeIsAudited() throws Exception {
        UUID id = issue();
        mvc.perform(putJson(issuer, "/api/v1/directives/" + id,
                        json("kind", "PREPARE_MEMO", "title", "إعداد مذكرة الرد المعدلة", "directiveDate", today().toString(),
                                "source", "قرار مدير الإدارة رقم ١٢", "addresseeUserId", member, "dueDate", today().plusDays(14).toString(),
                                "version", 0)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("إعداد مذكرة الرد المعدلة"))
                .andExpect(jsonPath("$.dueDate").value(today().plusDays(14).toString())).andExpect(jsonPath("$.version").value(1));
        mvc.perform(putJson(issuer, "/api/v1/directives/" + id,
                json("title", "t", "directiveDate", today().toString(), "source", "s", "addresseeText", "a", "version", 0)))
                .andExpect(status().isConflict());
        assertThat(count("select count(*) from audit_event where action = 'DIRECTIVE_UPDATE' and target_id = ?", id.toString()))
                .isEqualTo(1);
    }

    @Test
    void executionStatusMovesAndEachMoveIsAuditedAndOnTheCaseTimeline() throws Exception {
        UUID id = issue();
        mvc.perform(postJson(issuer, "/api/v1/directives/" + id + "/status", json("status", "IN_PROGRESS", "version", 0)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        mvc.perform(postJson(issuer, "/api/v1/directives/" + id + "/status",
                        json("status", "DONE", "note", "أُعدت المذكرة وأودعت", "version", 1)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.completedAt").isString()).andExpect(jsonPath("$.statusNote").value("أُعدت المذكرة وأودعت"));
        assertThat(count("select count(*) from audit_event where action = 'DIRECTIVE_STATUS' and target_id = ?", id.toString()))
                .isEqualTo(2);
        assertThat(count("select count(*) from case_event where case_id = ? and type = 'DIRECTIVE_STATUS_CHANGED'", caseId)).isEqualTo(2);
        // Reopening is allowed (flexible transitions, section 33) and clears the completion.
        mvc.perform(postJson(issuer, "/api/v1/directives/" + id + "/status", json("status", "IN_PROGRESS", "version", 2)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.completedAt").doesNotExist());
        mvc.perform(postJson(issuer, "/api/v1/directives/" + id + "/status", json("status", "CANCELLED", "version", 2)))
                .andExpect(status().isConflict());
    }

    @Test
    void theAddresseeMovesTheirOwnDirectiveEvenWithoutTheRightToChangeTheCaseButNobodyElseDoes() throws Exception {
        UUID id = issue();
        RequestPostProcessor addressee = viewer(member, Permission.CASE_VIEW);
        RequestPostProcessor someoneElse = viewer(outsiderUser, Permission.CASE_VIEW, Permission.DIRECTIVE_MANAGE);
        mvc.perform(postJson(someoneElse, "/api/v1/directives/" + id + "/status", json("status", "DONE", "version", 0)))
                .andExpect(status().isForbidden());
        mvc.perform(postJson(addressee, "/api/v1/directives/" + id + "/status", json("status", "IN_PROGRESS", "version", 0)))
                .andExpect(status().isOk());
        // The addressee changes the status only, never the directive itself.
        mvc.perform(putJson(addressee, "/api/v1/directives/" + id, json("title", "t", "directiveDate", today().toString(),
                "source", "s", "addresseeText", "a", "version", 1))).andExpect(status().isForbidden());
    }

    @Test
    void anOpenDirectivePastItsDueDateIsOverdueAndADoneOneIsNot() throws Exception {
        UUID id = issue();
        jdbc.update("update directive set directive_date = current_date - 20, due_date = current_date - 3 where id = ?", id);
        mvc.perform(getJson(issuer, "/api/v1/directives/" + id)).andExpect(jsonPath("$.overdue").value(true));
        mvc.perform(getJson(issuer, "/api/v1/directives?overdue=true")).andExpect(jsonPath("$.totalItems").value(1));
        mvc.perform(postJson(issuer, "/api/v1/directives/" + id + "/status", json("status", "DONE", "version", 0)))
                .andExpect(jsonPath("$.overdue").value(false));
        mvc.perform(getJson(issuer, "/api/v1/directives?overdue=true")).andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void theRegisterListsDirectivesWithFiltersAndPaging() throws Exception {
        UUID first = issue();
        UUID second = issue();
        UUID otherCase = legalCase(head, null);
        mvc.perform(postJson(issuer, "/api/v1/directives", body().replace(caseId.toString(), otherCase.toString())
                .replace("PREPARE_MEMO", "ATTEND_HEARING"))).andExpect(status().isCreated());
        mvc.perform(postJson(issuer, "/api/v1/directives/" + second + "/status", json("status", "DONE", "version", 0)));
        mvc.perform(getJson(issuer, "/api/v1/directives")).andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(3));
        mvc.perform(getJson(issuer, "/api/v1/directives?caseId=" + caseId)).andExpect(jsonPath("$.totalItems").value(2));
        mvc.perform(getJson(issuer, "/api/v1/directives?status=DONE")).andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(second.toString()));
        mvc.perform(getJson(issuer, "/api/v1/directives?kind=ATTEND_HEARING")).andExpect(jsonPath("$.totalItems").value(1));
        mvc.perform(getJson(issuer, "/api/v1/directives?addressee=" + member)).andExpect(jsonPath("$.totalItems").value(3));
        mvc.perform(getJson(issuer, "/api/v1/directives?size=2&page=1")).andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(getJson(issuer, "/api/v1/directives?status=BAD")).andExpect(status().isBadRequest());
        mvc.perform(getJson(issuer, "/api/v1/directives?q=" + "مذكرة")).andExpect(jsonPath("$.totalItems").value(3));
        assertThat(first).isNotNull();
    }

    @Test
    void aUserWhoCannotSeeTheCaseFindsNoDirectiveAnywhereAndTheTotalDoesNotCountIt() throws Exception {
        UUID id = issue();
        RequestPostProcessor nobody = outsider(outsiderUser, Permission.CASE_VIEW, Permission.DIRECTIVE_MANAGE);
        mvc.perform(getJson(nobody, "/api/v1/directives")).andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(0))
                .andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(getJson(nobody, "/api/v1/directives?caseId=" + caseId)).andExpect(jsonPath("$.totalItems").value(0));
        mvc.perform(getJson(nobody, "/api/v1/directives?q=مذكرة")).andExpect(jsonPath("$.totalItems").value(0));
        mvc.perform(getJson(nobody, "/api/v1/directives/" + id)).andExpect(status().isNotFound());
        mvc.perform(putJson(nobody, "/api/v1/directives/" + id, json("title", "t", "directiveDate", today().toString(),
                "source", "s", "addresseeText", "a", "version", 0))).andExpect(status().isNotFound());
        mvc.perform(postJson(nobody, "/api/v1/directives/" + id + "/status", json("status", "DONE", "version", 0)))
                .andExpect(status().isNotFound());
        mvc.perform(postJson(nobody, "/api/v1/directives", body())).andExpect(status().isNotFound());
        // A reader who sees the case lists it but may not issue one.
        RequestPostProcessor reader = viewer(outsiderUser, Permission.CASE_VIEW, Permission.DIRECTIVE_MANAGE);
        mvc.perform(getJson(reader, "/api/v1/directives")).andExpect(jsonPath("$.totalItems").value(1));
        mvc.perform(postJson(reader, "/api/v1/directives", body())).andExpect(status().isForbidden());
        MvcResult mine = mvc.perform(getJson(issuer, "/api/v1/directives")).andReturn();
        assertThat(body(mine)).contains(id.toString());
    }

    @Test
    void thePermissionsGateReadingAndWriting() throws Exception {
        issue();
        mvc.perform(getJson(staff(head), "/api/v1/directives")).andExpect(status().isForbidden());
        mvc.perform(postJson(staff(head, Permission.CASE_VIEW), "/api/v1/directives", body())).andExpect(status().isForbidden());
    }

    @Test
    void anArchivedCaseTakesNoNewDirective() throws Exception {
        jdbc.update("update legal_case set status = 'ARCHIVED' where id = ?", caseId);
        mvc.perform(postJson(issuer, "/api/v1/directives", body())).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.rule").value("CASE_ARCHIVED"));
    }
}
