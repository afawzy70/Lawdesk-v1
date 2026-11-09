package com.lawdesk.followup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.lawdesk.security.Permission;

/**
 * LD-053, requirements section 8, scenario S10: linking two cases, the link shown in both files, the distinction between
 * a subject link, a request to join, a decision to join and a referral, and the rule that closing one case never closes the
 * other. A link shows its other case only to those who may see that case (D3).
 */
class CaseLinkApiTest extends Wp3TestSupport {

    private static final Permission[] LINKER = {Permission.CASE_VIEW, Permission.CASE_LINK_MANAGE,
        Permission.CASE_STATUS_CHANGE, Permission.ASSIGN_CASE};

    private UUID owner;
    private UUID other;
    private UUID first;
    private UUID second;
    private RequestPostProcessor linker;

    @BeforeEach
    void twoCases() {
        owner = user("Case owner");
        other = user("Other owner");
        first = legalCase(owner, null);
        second = legalCase(owner, null);
        linker = staff(owner, LINKER);
    }

    private UUID link(String kind, String reason) throws Exception {
        MvcResult result = mvc.perform(postJson(linker, "/api/v1/cases/" + first + "/links",
                json("otherCaseId", second, "kind", kind, "reason", reason, "requestedBy", "المدعي", "requestedOn",
                        today().toString()))).andExpect(status().isCreated()).andReturn();
        return idOf(result);
    }

    @Test
    void theLinkAppearsInTheFileOfBothCasesWithItsKindAndReason() throws Exception {
        UUID id = link("SUBJECT_LINK", "Same decision challenged");
        mvc.perform(getJson(linker, "/api/v1/cases/" + first + "/links")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.hiddenCount").value(0))
                .andExpect(jsonPath("$.items[0].id").value(id.toString()))
                .andExpect(jsonPath("$.items[0].direction").value("OUTGOING"))
                .andExpect(jsonPath("$.items[0].otherCaseId").value(second.toString()))
                .andExpect(jsonPath("$.items[0].otherCaseReferenceNo").isString())
                .andExpect(jsonPath("$.items[0].kind").value("SUBJECT_LINK"))
                .andExpect(jsonPath("$.items[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.items[0].courtDecision").value(false));
        mvc.perform(getJson(linker, "/api/v1/cases/" + second + "/links")).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].direction").value("INCOMING"))
                .andExpect(jsonPath("$.items[0].otherCaseId").value(first.toString()))
                .andExpect(jsonPath("$.items[0].reason").value("Same decision challenged"));
        assertThat(count("select count(*) from case_event where case_id = ? and type = 'CASE_LINK_CREATED'", first)).isEqualTo(1);
    }

    @Test
    void aRequestToJoinIsNotADecisionToJoin() throws Exception {
        UUID id = link("JOINDER_REQUEST", "Requested by the plaintiff");
        mvc.perform(getJson(linker, "/api/v1/cases/" + first + "/links"))
                .andExpect(jsonPath("$.items[0].status").value("REQUESTED"))
                .andExpect(jsonPath("$.items[0].courtDecision").value(false));
        MvcResult decided = mvc.perform(putJson(linker, "/api/v1/case-links/" + id,
                        json("reason", "Requested by the plaintiff", "status", "ACTIVE", "courtDecision", true,
                                "decisionDate", today().toString(), "operative", "ضم القضية للارتباط", "version", 0)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.courtDecision").value(true))
                .andExpect(jsonPath("$.operative").value("ضم القضية للارتباط")).andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.version").value(1)).andReturn();
        assertThat(read(decided, "$.decisionDate").toString()).isEqualTo(today().toString());
    }

    @Test
    void decisionDetailsNeedTheFlagThatACourtDecidedAndAStaleVersionConflicts() throws Exception {
        UUID id = link("JOINDER_REQUEST", "r");
        mvc.perform(putJson(linker, "/api/v1/case-links/" + id,
                        json("reason", "r", "status", "REQUESTED", "courtDecision", false, "operative", "text", "version", 0)))
                .andExpect(status().isBadRequest());
        mvc.perform(putJson(linker, "/api/v1/case-links/" + id, json("reason", "r", "status", "REJECTED", "version", 0)))
                .andExpect(status().isOk());
        mvc.perform(putJson(linker, "/api/v1/case-links/" + id, json("reason", "r", "status", "ACTIVE", "version", 0)))
                .andExpect(status().isConflict());
    }

    @Test
    void refusesALinkToTheSameCaseAnUnknownCaseAndARepeatedLink() throws Exception {
        String path = "/api/v1/cases/" + first + "/links";
        mvc.perform(postJson(linker, path, json("otherCaseId", first, "kind", "SUBJECT_LINK", "reason", "x")))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.rule").value("CASE_LINK_SAME_CASE"));
        mvc.perform(postJson(linker, path, json("otherCaseId", UUID.randomUUID(), "kind", "SUBJECT_LINK", "reason", "x")))
                .andExpect(status().isNotFound());
        mvc.perform(postJson(linker, path, json("otherCaseId", second, "kind", "OTHER", "reason", "x")))
                .andExpect(status().isBadRequest());
        mvc.perform(postJson(linker, path, json("otherCaseId", second, "kind", "SUBJECT_LINK", "reason", " ")))
                .andExpect(status().isBadRequest());
        link("SUBJECT_LINK", "first");
        mvc.perform(postJson(linker, path, json("otherCaseId", second, "kind", "SUBJECT_LINK", "reason", "again")))
                .andExpect(status().isConflict());
        // From the other side it is the same link.
        mvc.perform(postJson(linker, "/api/v1/cases/" + second + "/links",
                        json("otherCaseId", first, "kind", "SUBJECT_LINK", "reason", "reverse"))).andExpect(status().isConflict());
        // A different kind of link between the same cases is another record.
        mvc.perform(postJson(linker, path, json("otherCaseId", second, "kind", "REFERRAL", "reason", "referred")))
                .andExpect(status().isCreated());
    }

    @Test
    void closingOrArchivingOneCaseNeverClosesTheOtherAndTheLinkStays() throws Exception {
        link("SUBJECT_LINK", "r");
        mvc.perform(postJson(linker, "/api/v1/cases/" + first + "/status", json("status", "CLOSED", "version", 0)))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select status from legal_case where id = ?", String.class, first)).isEqualTo("CLOSED");
        assertThat(jdbc.queryForObject("select status from legal_case where id = ?", String.class, second)).isEqualTo("OPEN");
        mvc.perform(getJson(linker, "/api/v1/cases/" + second + "/links")).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].otherCaseStatus").value("CLOSED"));
        mvc.perform(getJson(linker, "/api/v1/cases/" + first + "/links")).andExpect(jsonPath("$.items.length()").value(1));
        assertThat(count("select count(*) from case_link where status = 'ENDED'")).isZero();
    }

    @Test
    void aLinkShowsItsOtherCaseOnlyToThoseWhoMaySeeItAndOthersGetACount() throws Exception {
        link("SUBJECT_LINK", "r");
        // `other` is the alternate member of the first case only.
        jdbc.update("update legal_case set alternate_member_id = ? where id = ?", other, first);
        RequestPostProcessor firstOnly = outsider(other, Permission.CASE_VIEW, Permission.CASE_LINK_MANAGE);
        mvc.perform(getJson(firstOnly, "/api/v1/cases/" + first + "/links")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0)).andExpect(jsonPath("$.hiddenCount").value(1));
        // Nor can they link to a case they cannot see: it is reported missing.
        mvc.perform(postJson(firstOnly, "/api/v1/cases/" + first + "/links",
                        json("otherCaseId", second, "kind", "REFERRAL", "reason", "x"))).andExpect(status().isNotFound());
        // The owner of both sees the other case's details.
        mvc.perform(getJson(outsider(owner, Permission.CASE_VIEW), "/api/v1/cases/" + first + "/links"))
                .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.hiddenCount").value(0));
    }

    @Test
    void anOutsiderCannotReadOrWriteLinksOfACaseTheyCannotSee() throws Exception {
        UUID id = link("SUBJECT_LINK", "r");
        RequestPostProcessor nobody = outsider(other, Permission.CASE_VIEW, Permission.CASE_LINK_MANAGE);
        mvc.perform(getJson(nobody, "/api/v1/cases/" + first + "/links")).andExpect(status().isNotFound());
        mvc.perform(postJson(nobody, "/api/v1/cases/" + first + "/links",
                        json("otherCaseId", second, "kind", "REFERRAL", "reason", "x"))).andExpect(status().isNotFound());
        mvc.perform(putJson(nobody, "/api/v1/case-links/" + id, json("reason", "r", "status", "ENDED", "version", 0)))
                .andExpect(status().isNotFound());
    }

    @Test
    void seeingBothCasesDoesNotAllowChangingTheLinkWithoutBeingAbleToChangeOne() throws Exception {
        UUID id = link("SUBJECT_LINK", "r");
        RequestPostProcessor reader = viewer(other, Permission.CASE_VIEW, Permission.CASE_LINK_MANAGE);
        mvc.perform(postJson(reader, "/api/v1/cases/" + first + "/links",
                        json("otherCaseId", second, "kind", "REFERRAL", "reason", "x"))).andExpect(status().isForbidden());
        mvc.perform(putJson(reader, "/api/v1/case-links/" + id, json("reason", "r", "status", "ENDED", "version", 0)))
                .andExpect(status().isForbidden());
        mvc.perform(getJson(reader, "/api/v1/cases/" + first + "/links")).andExpect(status().isOk());
    }

    @Test
    void thePermissionsGateReadingAndWriting() throws Exception {
        link("SUBJECT_LINK", "r");
        mvc.perform(getJson(staff(owner), "/api/v1/cases/" + first + "/links")).andExpect(status().isForbidden());
        mvc.perform(postJson(staff(owner, Permission.CASE_VIEW), "/api/v1/cases/" + first + "/links",
                json("otherCaseId", second, "kind", "REFERRAL", "reason", "x"))).andExpect(status().isForbidden());
    }
}
