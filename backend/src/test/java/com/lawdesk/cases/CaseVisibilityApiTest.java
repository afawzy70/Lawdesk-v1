package com.lawdesk.cases;

import static com.lawdesk.security.TestActors.scoped;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;
import com.lawdesk.security.Permission;

/**
 * Owner decision D3 (LD-050): who sees which case. A case is seen by its specialist and alternate, by a supervisor whose
 * scope covers its owning unit, by the legal manager at department level, by a user with a task on it or a grant, and not
 * by anyone else — the administrator included, whatever permissions they hold. A RESTRICTED case needs a member or a
 * grant. Every route to a case's content answers "not found" to those who may not see it.
 */
class CaseVisibilityApiTest extends GovernmentIntakeTestSupport {

    private static final Permission[] READER = {Permission.CASE_VIEW, Permission.PROCEEDING_VIEW,
        Permission.JUDGMENT_VIEW, Permission.HEARING_VIEW, Permission.EXPERT_VIEW, Permission.DOCUMENT_VIEW,
        Permission.TASK_VIEW, Permission.REPORT_VIEW, Permission.REPORT_EXPORT, Permission.NOTE_MANAGE,
        Permission.COURT_VIEW};
    private static final Permission[] SUPERVISOR = {Permission.CASE_VIEW, Permission.CASE_EDIT,
        Permission.CASE_STATUS_CHANGE, Permission.ASSIGN_CASE, Permission.CASE_EDIT_IN_SCOPE,
        Permission.CASE_GRANT_MANAGE, Permission.TASK_VIEW, Permission.TASK_MANAGE};

    private UUID unitRoot;
    private UUID sectionA;
    private UUID sectionB;
    private UUID specialist;
    private UUID alternate;
    private UUID outsider;
    private UUID headA;
    private UUID headB;
    private UUID manager;
    private UUID admin;
    private UUID assignee;
    private UUID grantee;
    private UUID caseId;

    private RequestPostProcessor reader(UUID id, String... scopes) {
        return scoped(id, "u" + id.toString().substring(0, 6), List.of(scopes), READER);
    }

    private RequestPostProcessor supervisor(UUID id, String... scopes) {
        return scoped(id, "s" + id.toString().substring(0, 6), List.of(scopes), SUPERVISOR);
    }

    private UUID unit(String name, String level, UUID parent) {
        return jdbc.queryForObject("""
                insert into org_unit (tree, parent_id, level, name_ar, created_by, updated_by)
                values ('LEGAL', ?, ?, ?, gen_random_uuid(), gen_random_uuid()) returning id""", UUID.class, parent,
                level, name);
    }

    @BeforeEach
    void caseInSectionA() throws Exception {
        unitRoot = unit("الإدارة القانونية", "DEPARTMENT", null);
        sectionA = unit("قسم أ", "SECTION", unitRoot);
        sectionB = unit("قسم ب", "SECTION", unitRoot);
        specialist = member("Specialist");
        alternate = member("Alternate");
        outsider = member("Outsider");
        headA = member("Head A", "LEGAL_MANAGER", true);
        headB = member("Head B", "LEGAL_MANAGER", true);
        manager = member("Manager", "LEGAL_MANAGER", true);
        admin = member("Admin", null, true);
        assignee = member("Task assignee");
        grantee = member("Grantee");
        jdbc.update("update app_user set org_unit_id = ? where id in (?, ?)", sectionA, specialist, alternate);
        caseId = registered(registrar(member("Registering clerk")), intake(firstInstanceCourt(), specialist,
                "\"alternateMemberId\":\"" + alternate + "\""));
    }

    private int codeOf(RequestPostProcessor user, String path) throws Exception {
        return mvc.perform(get(path).with(user)).andReturn().getResponse().getStatus();
    }

    private int listed(RequestPostProcessor user) throws Exception {
        return JsonPath.<Integer>read(mvc.perform(get("/api/v1/cases?size=100").with(user)).andReturn().getResponse()
                .getContentAsString(), "$.totalItems");
    }

    @Test
    void theCaseGoesToItsOwningUnitFromTheSpecialist() {
        assertThat(jdbc.queryForObject("select owning_unit_id from legal_case where id = ?", UUID.class, caseId))
                .isEqualTo(sectionA);
    }

    @Test
    void membersSupervisorsOfTheUnitAndTheDepartmentManagerSeeTheCase() throws Exception {
        for (RequestPostProcessor user : List.of(reader(specialist), reader(alternate), reader(headA, "UNIT:" + sectionA),
                reader(headB, "UNIT:" + unitRoot), reader(manager, "ALL"))) {
            assertThat(codeOf(user, "/api/v1/cases/" + caseId)).isEqualTo(200);
            assertThat(listed(user)).isEqualTo(1);
        }
    }

    @Test
    void othersDoNotSeeTheCaseAndTheAdministratorIsNotAmongThemByRight() throws Exception {
        RequestPostProcessor administrator = scoped(admin, "admin", List.of(), Permission.values());
        for (RequestPostProcessor user : List.of(reader(outsider), reader(headB, "UNIT:" + sectionB), administrator)) {
            assertThat(codeOf(user, "/api/v1/cases/" + caseId)).isEqualTo(404);
            assertThat(listed(user)).isZero();
        }
    }

    @Test
    void everyRouteToTheContentAnswersNotFoundToAnOutsider() throws Exception {
        RequestPostProcessor other = reader(outsider);
        for (String path : List.of("/timeline", "/parties", "/proceedings", "/judgments", "/special-decisions",
                "/hearings", "/documents", "/notes", "/expert-referrals")) {
            assertThat(codeOf(other, "/api/v1/cases/" + caseId + path)).as(path).isEqualTo(404);
        }
        assertThat(codeOf(other, "/api/v1/cases/" + caseId + "/timeline")).isEqualTo(404);
        // Lists across cases hold nothing of it.
        for (String path : List.of("/api/v1/hearings", "/api/v1/documents", "/api/v1/tasks")) {
            mvc.perform(get(path).with(other)).andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(0));
        }
    }

    @Test
    void cantEditWhatYouOnlySee() throws Exception {
        String body = "{\"title\":\"x\",\"caseType\":\"COMMERCIAL\",\"openedOn\":\"2026-01-10\",\"version\":0}";
        mvc.perform(put("/api/v1/cases/" + caseId).with(reader(headA, "UNIT:" + sectionA)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        // Seeing and the right to change in scope are separate: the supervisor holds both, the viewer only the first.
        mvc.perform(put("/api/v1/cases/" + caseId).with(supervisor(headA, "UNIT:" + sectionA)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        mvc.perform(put("/api/v1/cases/" + caseId).with(supervisor(outsider)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body.replace("\"version\":0", "\"version\":1")))
                .andExpect(status().isNotFound());
    }

    @Test
    void restrictedCasesNeedAMembershipOrAGrantNotAScope() throws Exception {
        RequestPostProcessor head = supervisor(manager, "ALL");
        mvc.perform(put("/api/v1/cases/" + caseId + "/confidentiality").with(supervisor(manager, "ALL")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"level\":\"RESTRICTED\",\"reason\":\"sensitive\",\"version\":0}"))
                .andExpect(status().isNoContent());
        assertThat(codeOf(head, "/api/v1/cases/" + caseId)).as("department level, restricted").isEqualTo(404);
        assertThat(codeOf(reader(specialist), "/api/v1/cases/" + caseId)).as("the specialist").isEqualTo(200);

        // The department manager cannot see it any more, so cannot grant on it either; the specialist can.
        String grant = "{\"userId\":\"" + grantee + "\",\"level\":\"VIEW\",\"reason\":\"second opinion\"}";
        mvc.perform(post("/api/v1/cases/" + caseId + "/grants").with(head).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(grant)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/cases/" + caseId + "/grants").with(supervisor(specialist)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(grant)).andExpect(status().isCreated());
    }

    @Test
    void aGrantGivesViewOrContributeUntilItIsWithdrawn() throws Exception {
        mvc.perform(put("/api/v1/cases/" + caseId + "/confidentiality").with(supervisor(specialist)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"level\":\"RESTRICTED\",\"reason\":\"sensitive\",\"version\":0}"))
                .andExpect(status().isNoContent());
        RequestPostProcessor owner = supervisor(specialist);
        assertThat(codeOf(reader(grantee), "/api/v1/cases/" + caseId)).isEqualTo(404);
        String created = mvc.perform(post("/api/v1/cases/" + caseId + "/grants").with(owner).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + grantee + "\",\"level\":\"VIEW\",\"reason\":\"second opinion\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertThat(codeOf(reader(grantee), "/api/v1/cases/" + caseId)).isEqualTo(200);
        assertThat(listed(reader(grantee))).isEqualTo(1);
        String edit = "{\"title\":\"x\",\"caseType\":\"COMMERCIAL\",\"openedOn\":\"2026-01-10\",\"version\":2}";
        RequestPostProcessor granteeEditor = scoped(grantee, "g", List.of(), Permission.CASE_VIEW, Permission.CASE_EDIT);
        mvc.perform(put("/api/v1/cases/" + caseId).with(granteeEditor).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(edit)).andExpect(status().isForbidden());

        String grantId = JsonPath.read(created, "$.id");
        mvc.perform(delete("/api/v1/cases/" + caseId + "/grants/" + grantId).with(owner).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(codeOf(reader(grantee), "/api/v1/cases/" + caseId)).isEqualTo(404);
        mvc.perform(delete("/api/v1/cases/" + caseId + "/grants/" + grantId).with(owner).with(csrf()))
                .andExpect(status().isUnprocessableEntity());
        assertThat(count("select count(*) from audit_event where action in ('CASE_GRANT_CREATE', 'CASE_GRANT_REVOKE')"))
                .isEqualTo(2);
    }

    @Test
    void nobodyGrantsThemselfAccessAndTheAdminRoleHasNoScope() throws Exception {
        mvc.perform(post("/api/v1/cases/" + caseId + "/grants").with(supervisor(specialist)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + specialist + "\",\"level\":\"VIEW\",\"reason\":\"me\"}"))
                .andExpect(status().isUnprocessableEntity());
        RequestPostProcessor scopes = supervisor(manager, "ALL");
        mvc.perform(post("/api/v1/access-scopes").with(scopes).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"subjectType\":\"ROLE\",\"subjectRef\":\"ADMIN\",\"scopeType\":\"ALL\"}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/v1/access-scopes").with(scopes).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"subjectType\":\"USER\",\"subjectRef\":\"" + manager + "\",\"scopeType\":\"ALL\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void aTaskGivesSightOfTheCaseAndTheRightToItsOwnTaskOnly() throws Exception {
        RequestPostProcessor lead = scoped(specialist, "lead", List.of(), Permission.CASE_VIEW, Permission.TASK_MANAGE,
                Permission.TASK_VIEW);
        String task = mvc.perform(post("/api/v1/tasks").with(lead).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Collect the contract\",\"caseId\":\"" + caseId + "\",\"assigneeId\":\""
                                + assignee + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String taskId = JsonPath.read(task, "$.id");
        RequestPostProcessor worker = scoped(assignee, "w", List.of(), Permission.CASE_VIEW, Permission.CASE_EDIT,
                Permission.TASK_VIEW, Permission.TASK_MANAGE, Permission.NOTE_MANAGE);
        assertThat(codeOf(worker, "/api/v1/cases/" + caseId)).as("sees the case it holds a task on").isEqualTo(200);
        mvc.perform(put("/api/v1/cases/" + caseId).with(worker).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"x\",\"caseType\":\"COMMERCIAL\",\"openedOn\":\"2026-01-10\",\"version\":0}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/cases/" + caseId + "/notes").with(worker).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"n\"}")).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/tasks/" + taskId).with(worker).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Mine now\",\"assigneeId\":\"" + assignee + "\",\"version\":0}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/tasks/" + taskId + "/status").with(worker).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DONE\",\"version\":0}"))
                .andExpect(status().isOk());
        assertThat(count("select count(*) from case_event where case_id = ? and type = 'TASK_COMPLETED'", caseId))
                .isEqualTo(1);
    }

    @Test
    void reportsCountOnlyWhatTheUserSees() throws Exception {
        for (String path : List.of("/api/v1/reports/cases", "/api/v1/reports/judgments", "/api/v1/reports/hearings",
                "/api/v1/reports/workload", "/api/v1/reports/experts")) {
            assertThat(codeOf(reader(manager, "ALL"), path)).as(path).isEqualTo(200);
            assertThat(codeOf(reader(outsider), path)).as(path).isEqualTo(200);
        }
        String seen = mvc.perform(get("/api/v1/reports/cases").with(reader(manager, "ALL"))).andReturn().getResponse()
                .getContentAsString();
        String unseen = mvc.perform(get("/api/v1/reports/cases").with(reader(outsider))).andReturn().getResponse()
                .getContentAsString();
        assertThat(JsonPath.<Integer>read(seen, "$.summary.total")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(unseen, "$.summary.total")).isZero();
        mvc.perform(get("/api/v1/reports/cases/export?format=csv").with(reader(outsider))).andExpect(status().isOk());
    }
}
