package com.lawdesk.cases;

import static com.lawdesk.security.TestActors.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.lawdesk.security.Permission;

/**
 * LD-045 the specialist (العضو المختص) and the alternate member (العضو البديل): content of a case can be changed only by
 * them and by case supervisors, enforced in the services and re-evaluated on every request, so a reassignment applies
 * at once. Reading is unchanged, and a case without a specialist keeps the old rule.
 */
class CaseMembershipAccessTest extends GovernmentIntakeTestSupport {

    private static final Permission[] STAFF = {Permission.CASE_VIEW, Permission.CASE_EDIT, Permission.PARTY_MANAGE,
        Permission.PROCEEDING_VIEW, Permission.PROCEEDING_MANAGE, Permission.COURT_VIEW, Permission.TASK_VIEW,
        Permission.TASK_MANAGE, Permission.NOTE_MANAGE, Permission.HEARING_VIEW, Permission.HEARING_MANAGE};
    private static final Permission[] SUPERVISOR = {Permission.CASE_VIEW, Permission.CASE_EDIT,
        Permission.CASE_STATUS_CHANGE, Permission.PARTY_MANAGE, Permission.PROCEEDING_VIEW,
        Permission.PROCEEDING_MANAGE, Permission.TASK_MANAGE, Permission.NOTE_MANAGE};

    private UUID specialist;
    private UUID alternate;
    private UUID outsider;
    private UUID supervisor;
    private UUID creator;
    private UUID caseId;
    private UUID court;

    private RequestPostProcessor by(UUID id, Permission... permissions) {
        return as(id, "u" + id.toString().substring(0, 6), permissions);
    }

    @BeforeEach
    void caseWithMembers() throws Exception {
        specialist = member("Specialist");
        alternate = member("Alternate");
        outsider = member("Outsider");
        supervisor = member("Manager", "LEGAL_MANAGER", true);
        creator = member("Registering Clerk");
        court = firstInstanceCourt();
        caseId = registered(by(creator, REGISTRAR), intake(court, specialist, "\"alternateMemberId\":\"" + alternate + "\""));
    }

    private String edit(long version) {
        return "{\"title\":\"Changed " + version + "\",\"caseType\":\"COMMERCIAL\",\"openedOn\":\"2026-01-10\","
                + "\"version\":" + version + "}";
    }

    private int change(RequestPostProcessor user, long version) throws Exception {
        return mvc.perform(putCase(user, caseId, edit(version))).andReturn().getResponse().getStatus();
    }

    @Test
    void onlyTheSpecialistTheAlternateAndSupervisorsEditTheCase() throws Exception {
        assertThat(change(by(outsider, STAFF), 0)).as("a department member who is not assigned").isEqualTo(403);
        assertThat(change(by(creator, STAFF), 0)).as("the registrar who assigned someone else").isEqualTo(403);
        assertThat(change(by(specialist, STAFF), 0)).isEqualTo(200);
        assertThat(change(by(alternate, STAFF), 1)).as("the alternate has the specialist's rights at once").isEqualTo(200);
        assertThat(change(by(supervisor, SUPERVISOR), 2)).as("supervisors keep their rights").isEqualTo(200);
    }

    @Test
    void aMemberStillNeedsThePermissionForWhatTheyDo() throws Exception {
        mvc.perform(putCase(by(specialist, Permission.CASE_VIEW), caseId, edit(0))).andExpect(status().isForbidden());
    }

    @Test
    void everyWayOfAddingContentToTheCaseIsRefusedToNonMembers() throws Exception {
        RequestPostProcessor other = by(outsider, STAFF);
        mvc.perform(post("/api/v1/cases/" + caseId + "/notes").with(other).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"hello\"}"))
                .andExpect(status().isForbidden()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(post("/api/v1/tasks").with(other).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"caseId\":\"" + caseId + "\",\"assigneeId\":\"" + alternate + "\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/cases/" + caseId + "/proceedings").with(other).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stage\":\"FIRST_INSTANCE\",\"courtId\":\"" + court
                                + "\",\"courtCaseNumber\":\"9\",\"courtCaseYear\":2026,\"filingDate\":\"2026-01-12\"}"))
                .andExpect(status().isForbidden());
        UUID party = jdbc.queryForObject("insert into party (kind, name, created_by, updated_by) values ('PERSON',"
                + " 'P', gen_random_uuid(), gen_random_uuid()) returning id", UUID.class);
        mvc.perform(post("/api/v1/cases/" + caseId + "/parties").with(other).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"partyId\":\"" + party + "\",\"role\":\"OTHER\",\"side\":\"DEFENDANT\"}"))
                .andExpect(status().isForbidden());
        assertThat(count("select count(*) from case_note")).isZero();
        assertThat(count("select count(*) from task")).isZero();
        assertThat(count("select count(*) from proceeding")).isEqualTo(1);
        assertThat(count("select count(*) from case_party where party_id = ?", party)).isZero();

        RequestPostProcessor member = by(specialist, STAFF);
        mvc.perform(post("/api/v1/cases/" + caseId + "/notes").with(member).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"hello\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/tasks").with(member).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"caseId\":\"" + caseId + "\",\"assigneeId\":\"" + alternate + "\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/cases/" + caseId + "/parties").with(member).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"partyId\":\"" + party + "\",\"role\":\"OTHER\",\"side\":\"DEFENDANT\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.side").value("DEFENDANT"))
                .andExpect(jsonPath("$.role").value("OPPONENT"));
    }

    @Test
    void readingTheCaseIsNotNarrowed() throws Exception {
        RequestPostProcessor other = by(outsider, Permission.CASE_VIEW, Permission.PROCEEDING_VIEW);
        mvc.perform(getCase(other, caseId)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/cases/" + caseId + "/parties").with(other)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/cases/" + caseId + "/timeline").with(other)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/cases").with(other)).andExpect(status().isOk());
    }

    @Test
    void reassignmentAppliesImmediatelyToThePreviousAndTheNewMembers() throws Exception {
        UUID successor = member("Successor");
        mvc.perform(assign(by(supervisor, SUPERVISOR), caseId,
                        "{\"primaryMemberId\":\"" + successor + "\",\"alternateMemberId\":null,\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryMember.id").value(successor.toString()))
                .andExpect(jsonPath("$.alternateMember").doesNotExist())
                .andExpect(jsonPath("$.version").value(1));

        assertThat(change(by(specialist, STAFF), 1)).as("the former specialist").isEqualTo(403);
        assertThat(change(by(alternate, STAFF), 1)).as("the former alternate").isEqualTo(403);
        assertThat(change(by(successor, STAFF), 1)).as("the new specialist").isEqualTo(200);

        assertThat(jdbc.queryForList("select reason from audit_event where action = 'CASE_ASSIGNMENT_CHANGE'"
                + " and target_id = ? order by id", String.class, caseId.toString()))
                .containsExactly("primary=" + specialist, "alternate=" + alternate, "primary=" + successor,
                        "alternate=none");
        mvc.perform(get("/api/v1/cases/" + caseId + "/timeline").with(as(Permission.CASE_VIEW)))
                .andExpect(jsonPath("$.items[?(@.type == 'CASE_ASSIGNED')]").isNotEmpty());
    }

    @Test
    void onlySupervisorsReassignAndTheRulesOfEligibilityApply() throws Exception {
        String body = "{\"primaryMemberId\":\"" + outsider + "\",\"alternateMemberId\":null,\"version\":0}";
        mvc.perform(assign(by(specialist, STAFF), caseId, body)).andExpect(status().isForbidden());
        mvc.perform(assign(by(outsider, STAFF), caseId, body)).andExpect(status().isForbidden());
        mvc.perform(assign(by(supervisor, Permission.CASE_EDIT), caseId, body)).andExpect(status().isForbidden());
        mvc.perform(assign(by(supervisor, SUPERVISOR), caseId,
                        "{\"primaryMemberId\":\"" + outsider + "\",\"alternateMemberId\":\"" + outsider + "\",\"version\":0}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].code").value("SameAsPrimary"));
        mvc.perform(assign(by(supervisor, SUPERVISOR), caseId,
                        "{\"primaryMemberId\":\"" + member("Gone", "LEGAL_STAFF", false) + "\",\"version\":0}"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.rule").value("MEMBER_NOT_ELIGIBLE"));
        mvc.perform(assign(by(supervisor, SUPERVISOR), caseId,
                        "{\"alternateMemberId\":\"" + outsider + "\",\"version\":0}"))
                .andExpect(status().isBadRequest());
        mvc.perform(assign(by(supervisor, SUPERVISOR), caseId,
                        "{\"primaryMemberId\":\"" + outsider + "\",\"version\":7}"))
                .andExpect(status().isConflict());
        // Unchanged members: nothing is written.
        mvc.perform(assign(by(supervisor, SUPERVISOR), caseId,
                        "{\"primaryMemberId\":\"" + specialist + "\",\"alternateMemberId\":\"" + alternate + "\",\"version\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(0));
        assertThat(change(by(outsider, STAFF), 0)).isEqualTo(403);
    }

    @Test
    void aCaseWithoutASpecialistKeepsTheOldRule() throws Exception {
        UUID legacy = jdbc.queryForObject("""
                insert into legal_case (reference_no, title, case_type, opened_on, created_by, updated_by)
                values ('OLD-2', 'Legacy', 'CIVIL', '2020-01-01', gen_random_uuid(), gen_random_uuid()) returning id""",
                UUID.class);
        mvc.perform(putCase(by(outsider, STAFF), legacy,
                        "{\"title\":\"Legacy 2\",\"caseType\":\"CIVIL\",\"openedOn\":\"2020-01-01\",\"version\":0}"))
                .andExpect(status().isOk());
        // A supervisor can give it a specialist, from then on the new rule holds.
        mvc.perform(assign(by(supervisor, SUPERVISOR), legacy,
                        "{\"primaryMemberId\":\"" + specialist + "\",\"version\":1}"))
                .andExpect(status().isOk());
        mvc.perform(putCase(by(outsider, STAFF), legacy,
                        "{\"title\":\"Legacy 3\",\"caseType\":\"CIVIL\",\"openedOn\":\"2020-01-01\",\"version\":2}"))
                .andExpect(status().isForbidden());
    }
}
