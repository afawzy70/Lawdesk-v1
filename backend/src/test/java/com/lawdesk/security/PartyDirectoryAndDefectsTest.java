package com.lawdesk.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;
import com.lawdesk.wp9.Wp9TestSupport;

/**
 * LD-DOC-R3 fixes: the party directory shows a party only where the caller can see one of its cases (or it is on no case, or the
 * caller made it), linking an existing party still works for a legitimate user, and the two in-scope 500s answer with a
 * proper status.
 */
class PartyDirectoryAndDefectsTest extends Wp9TestSupport {

    private String tag;
    private UUID owner;
    private UUID other;
    private UUID secretCase;
    private UUID myCase;
    private UUID hiddenOnly;
    private UUID shared;
    private UUID unlinked;
    private RequestPostProcessor ownerAs;
    private RequestPostProcessor otherAs;

    @BeforeEach
    void fixture() {
        tag = "PDX" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        owner = user("Owner");
        other = user("Other");
        secretCase = legalCase(owner, null);
        myCase = legalCase(other, null);
        hiddenOnly = party(tag + " hidden", "PERSON", null, false, null);
        shared = party(tag + " shared", "PERSON", null, false, null);
        unlinked = party(tag + " unlinked", "PERSON", null, false, null);
        onCase(secretCase, hiddenOnly, "CLIENT");
        onCase(secretCase, shared, "CLIENT");
        onCase(myCase, shared, "OPPONENT");
        ownerAs = outsider(owner, Permission.values());
        otherAs = outsider(other, Permission.values());
    }

    private MockHttpServletResponse get(RequestPostProcessor who, String url) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get(url).with(who)).andReturn().getResponse();
    }

    private int total(RequestPostProcessor who, String q) throws Exception {
        return JsonPath.read(get(who, "/api/v1/parties?size=100&q=" + q).getContentAsString(), "$.totalItems");
    }

    private MockHttpServletResponse link(RequestPostProcessor who, UUID caseId, UUID party) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post("/api/v1/cases/" + caseId + "/parties").with(who).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json("partyId", party.toString(), "role", "CLIENT")))
                .andReturn().getResponse();
    }

    @Test
    void aPartyOnlyOnCasesTheCallerCannotSeeIsNotListedOrReadable() throws Exception {
        String listed = get(otherAs, "/api/v1/parties?size=100&q=" + tag).getContentAsString();
        assertThat(listed).doesNotContain(tag + " hidden").contains(tag + " shared").contains(tag + " unlinked");
        assertThat(total(otherAs, tag)).as("shared (on a visible case too) and unlinked").isEqualTo(2);
        assertThat(total(ownerAs, tag)).as("the owner sees all three").isEqualTo(3);
        var refused = get(otherAs, "/api/v1/parties/" + hiddenOnly);
        var absent = get(otherAs, "/api/v1/parties/" + UUID.randomUUID());
        assertThat(refused.getStatus()).isEqualTo(404);
        assertThat(refused.getContentAsString().replaceAll("[0-9a-f-]{36}", "<id>"))
                .isEqualTo(absent.getContentAsString().replaceAll("[0-9a-f-]{36}", "<id>"));
        assertThat(get(otherAs, "/api/v1/parties/" + shared).getStatus()).isEqualTo(200);
        assertThat(get(otherAs, "/api/v1/parties/" + unlinked).getStatus()).isEqualTo(200);
        // The shared party's case list still hides the case the caller cannot see.
        assertThat(get(otherAs, "/api/v1/parties/" + shared + "/cases").getContentAsString()).contains(myCase.toString())
                .doesNotContain(secretCase.toString());
    }

    @Test
    void aUserCanStillLinkAnExistingPartyThatTheyCanSee() throws Exception {
        assertThat(link(otherAs, myCase, unlinked).getStatus()).as("an unlinked party").isEqualTo(201);
        UUID second = legalCase(other, null);
        assertThat(link(otherAs, second, shared).getStatus()).as("a party on a case the caller sees").isEqualTo(201);
        var hidden = link(otherAs, second, hiddenOnly);
        var absent = link(otherAs, second, UUID.randomUUID());
        assertThat(hidden.getStatus()).as("a party only on a hidden case").isEqualTo(404);
        assertThat(absent.getStatus()).isEqualTo(404);
        assertThat(jdbc.queryForObject("select count(*) from case_party where case_id = ? and party_id = ?", Long.class, second,
                hiddenOnly)).isZero();
        // A party the caller creates is listed to them even before it is on any case, and can be linked.
        var created = mvc.perform(MockMvcRequestBuilders.post("/api/v1/parties").with(otherAs).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json("kind", "PERSON", "name", tag + " made")))
                .andReturn().getResponse();
        assertThat(created.getStatus()).isEqualTo(201);
        UUID made = UUID.fromString(JsonPath.read(created.getContentAsString(), "$.id"));
        assertThat(total(otherAs, tag + " made")).isEqualTo(1);
        assertThat(link(otherAs, second, made).getStatus()).isEqualTo(201);
    }

    @Test
    void aGrantMakesTheHiddenPartyVisibleAndRevokingItHidesItAgain() throws Exception {
        assertThat(total(otherAs, tag + " hidden")).isZero();
        UUID grant = jdbc.queryForObject("""
                insert into case_grant (case_id, user_id, level, reason, granted_by) values (?, ?, 'VIEW', 'PDX', ?) returning id""",
                UUID.class, secretCase, other, owner);
        assertThat(total(otherAs, tag + " hidden")).as("granted").isEqualTo(1);
        assertThat(get(otherAs, "/api/v1/parties/" + hiddenOnly).getStatus()).isEqualTo(200);
        jdbc.update("update case_grant set revoked_at = now(), revoked_by = ? where id = ?", owner, grant);
        assertThat(total(otherAs, tag + " hidden")).as("revoked").isZero();
        assertThat(get(otherAs, "/api/v1/parties/" + hiddenOnly).getStatus()).isEqualTo(404);
    }

    @Test
    void unlinkingAPartyThatHasCounselIsAConflictNotAServerError() throws Exception {
        jdbc.update("insert into case_party_counsel (case_id, party_id, kind, name, created_by) values (?, ?, 'LAWYER', 'PDX', ?)",
                secretCase, hiddenOnly, owner);
        var refused = mvc.perform(MockMvcRequestBuilders.delete("/api/v1/cases/" + secretCase + "/parties/" + hiddenOnly)
                .with(ownerAs).with(csrf())).andReturn().getResponse();
        assertThat(refused.getStatus()).as(refused.getContentAsString()).isEqualTo(409);
        assertThat(jdbc.queryForObject("select count(*) from case_party where case_id = ? and party_id = ?", Long.class,
                secretCase, hiddenOnly)).isEqualTo(1);
        var plain = mvc.perform(MockMvcRequestBuilders.delete("/api/v1/cases/" + secretCase + "/parties/" + shared)
                .with(ownerAs).with(csrf())).andReturn().getResponse();
        assertThat(plain.getStatus()).as("without counsel it still works").isEqualTo(204);
    }

    @Test
    void theTextOfADocumentWithNoStoredVersionIsNotAServerError() throws Exception {
        UUID doc = document(secretCase, tag + " doc", "x.pdf");
        var text = get(ownerAs, "/api/v1/documents/" + doc + "/text");
        assertThat(text.getStatus()).as(text.getContentAsString()).isEqualTo(404);
        var again = mvc.perform(MockMvcRequestBuilders.post("/api/v1/documents/" + doc + "/text/reprocess").with(ownerAs).with(csrf()))
                .andReturn().getResponse();
        assertThat(again.getStatus()).as(again.getContentAsString()).isEqualTo(422);
        assertThat(again.getContentAsString()).contains("DOCUMENT_HAS_NO_VERSION");
    }
}
