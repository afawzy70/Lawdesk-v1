package com.lawdesk.cases;

import static com.lawdesk.security.TestActors.as;
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
 * Requirements sections 2 and 3 with owner decision D4: the civil or establishment number is shown in full only to those
 * who may see identifiers, a party's cases are listed within the viewer's rights, counsel is recorded per case, and a
 * possible duplicate registration is warned about, never blocked by itself and never merged.
 */
class PartyIdentityAndDuplicateApiTest extends GovernmentIntakeTestSupport {

    private UUID specialist;
    private UUID court;
    private RequestPostProcessor clerk;
    private RequestPostProcessor withIds;
    private RequestPostProcessor withoutIds;

    @BeforeEach
    void actors() {
        specialist = member("Specialist");
        court = firstInstanceCourt();
        clerk = registrar(member("Clerk"));
        withIds = as(member("Id reader"), "idr", Permission.CASE_VIEW, Permission.PARTY_MANAGE, Permission.PARTY_ID_VIEW);
        withoutIds = as(member("Editor"), "ed", Permission.CASE_VIEW, Permission.PARTY_MANAGE);
    }

    private String party(String kind, String name, String idField, String id, String extra) {
        return "{\"kind\":\"" + kind + "\",\"name\":\"" + name + "\"," + (id == null ? "" : "\"" + idField + "\":\"" + id + "\",")
                + (extra == null ? "" : extra + ",") + "\"confidential\":false}";
    }

    private UUID createParty(String json) throws Exception {
        String body = mvc.perform(post("/api/v1/parties").with(withIds).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    @Test
    void theCivilNumberAndTheEstablishmentNumberAreShownInFullOnlyToThoseWhoMaySeeThem() throws Exception {
        UUID person = createParty(party("PERSON", "Mona Hassan", "nationalId", "287123456789", null));
        UUID company = createParty(party("ORGANIZATION", "Nile Trading", "registrationNo", "CR-445566", null));

        mvc.perform(get("/api/v1/parties/" + person).with(withIds)).andExpect(jsonPath("$.nationalId").value("287123456789"));
        mvc.perform(get("/api/v1/parties/" + person).with(withoutIds))
                .andExpect(jsonPath("$.nationalId").value("*********789"));
        mvc.perform(get("/api/v1/parties/" + company).with(withoutIds))
                .andExpect(jsonPath("$.registrationNo").value("******566"));

        // The list is masked the same way, and an identifier is not a way to find a party without the right to see it.
        mvc.perform(get("/api/v1/parties?q=287123").with(withoutIds)).andExpect(jsonPath("$.totalItems").value(0));
        mvc.perform(get("/api/v1/parties?q=287123").with(withIds)).andExpect(jsonPath("$.totalItems").value(1));
        mvc.perform(get("/api/v1/parties?q=Mona").with(withoutIds)).andExpect(jsonPath("$.items[0].nationalId").value("*********789"));
    }

    @Test
    void anEditorWhoCannotSeeTheIdentifiersCannotOverwriteThem() throws Exception {
        UUID person = createParty(party("PERSON", "Mona Hassan", "nationalId", "287123456789", null));
        mvc.perform(put("/api/v1/parties/" + person).with(withoutIds).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"kind\":\"PERSON\",\"name\":\"Mona H. Hassan\",\"nationalId\":\"*********789\",\"version\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Mona H. Hassan"))
                .andExpect(jsonPath("$.nationalId").value("*********789"));
        assertThat(jdbc.queryForObject("select national_id from party where id = ?", String.class, person))
                .isEqualTo("287123456789");
        // Old values of any shape are kept as they are: nothing is validated against an undocumented format.
        UUID odd = createParty(party("PERSON", "Odd", "nationalId", "A-12/ب", null));
        mvc.perform(get("/api/v1/parties/" + odd).with(withIds)).andExpect(jsonPath("$.nationalId").value("A-12/ب"));
    }

    @Test
    void aConfidentialPartyShowsNoContactDetailsToThoseWhoCannotSeeIdentifiers() throws Exception {
        UUID person = createParty(party("PERSON", "Hidden", "nationalId", "111222333", "\"phone\":\"22334455\",\"email\":\"h@example.invalid\",\"address\":\"Block 4\"")
                .replace("\"confidential\":false", "\"confidential\":true"));
        mvc.perform(get("/api/v1/parties/" + person).with(withoutIds)).andExpect(jsonPath("$.phone").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist()).andExpect(jsonPath("$.address").doesNotExist());
        mvc.perform(get("/api/v1/parties/" + person).with(withIds)).andExpect(jsonPath("$.phone").value("22334455"))
                .andExpect(jsonPath("$.address").value("Block 4"));
    }

    @Test
    void aPartysCasesAreListedWithinTheViewersRights() throws Exception {
        UUID person = createParty(party("PERSON", "Shared Opponent", "nationalId", "555666777", null));
        UUID first = registered(clerk, intake(court, specialist, "\"parties\":[{\"partyId\":\"" + person + "\",\"side\":\"PLAINTIFF\"}]"));
        UUID other = member("Other specialist");
        String second = intake(firstInstanceCourt(), other, "\"parties\":[{\"partyId\":\"" + person + "\",\"side\":\"PLAINTIFF\"}]")
                .replace("000123", "000999").replace("Ministry v. Nile Trading", "Ministry v. Another Party");
        UUID secondId = registered(registrar(member("Clerk 2")), second);
        jdbc.update("update legal_case set confidentiality = 'RESTRICTED' where id = ?", secondId);

        mvc.perform(get("/api/v1/parties/" + person + "/cases").with(scoped(specialist, "sp", List.of(), Permission.CASE_VIEW)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].caseId").value(first.toString()));
        mvc.perform(get("/api/v1/parties/" + person + "/cases").with(as(Permission.CASE_VIEW)))
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/v1/parties/" + UUID.randomUUID() + "/cases").with(as(Permission.CASE_VIEW)))
                .andExpect(status().isNotFound());
    }

    @Test
    void counselIsRecordedPerCaseAndPartyAndRemovedWithoutLosingTheRecord() throws Exception {
        UUID person = createParty(party("PERSON", "Client", "nationalId", "999888777", null));
        UUID caseId = registered(clerk, intake(court, specialist, "\"parties\":[{\"partyId\":\"" + person + "\",\"side\":\"PLAINTIFF\"}]"));
        RequestPostProcessor member = scoped(specialist, "sp", List.of("ALL"), Permission.CASE_VIEW, Permission.PARTY_MANAGE);
        String base = "/api/v1/cases/" + caseId + "/parties";
        String created = mvc.perform(post(base + "/" + person + "/counsel").with(member).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"LAWYER\",\"name\":\"Adv. Khaled\",\"phone\":\"22001100\",\"notes\":\"agent\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        mvc.perform(get(base + "/counsel").with(member)).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Adv. Khaled"));
        mvc.perform(post(base + "/" + UUID.randomUUID() + "/counsel").with(member).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"kind\":\"AGENT\",\"name\":\"x\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post(base + "/" + person + "/counsel").with(member).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"kind\":\"BOSS\",\"name\":\"x\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(base + "/" + person + "/counsel")
                .with(scoped(member("Reader"), "rd", List.of("ALL"), Permission.CASE_VIEW, Permission.PARTY_MANAGE)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"kind\":\"AGENT\",\"name\":\"x\"}"))
                .andExpect(status().isForbidden());
        String counselId = JsonPath.read(created, "$.id");
        mvc.perform(delete(base + "/counsel/" + counselId).with(member).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get(base + "/counsel").with(member)).andExpect(jsonPath("$.length()").value(0));
        assertThat(count("select count(*) from case_party_counsel where id = ?::uuid and removed_at is not null", counselId))
                .isEqualTo(1);
    }

    @Test
    void aPossibleDuplicateIsWarnedAboutAndTheRegistrarsDecisionIsKept() throws Exception {
        UUID existing = registered(clerk, intake(court, specialist, null));
        // Same judicial automated number at another court: a new stage or an independent claim, or a repeat.
        String again = intake(court, specialist, null).replace("\"courtCaseNumber\":\"4471\"", "\"courtCaseNumber\":\"7788\"");
        mvc.perform(registerStrict(clerk, again)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("DUPLICATE_SUSPECTED"));
        assertThat(count("select count(*) from legal_case")).isEqualTo(1);

        String check = "/api/v1/cases/duplicate-check?automatedNo=000123&courtId=" + court;
        mvc.perform(get(check).with(as(Permission.CASE_CREATE, Permission.CASE_VIEW))).andExpect(status().isOk())
                .andExpect(jsonPath("$.visible[0].caseId").value(existing.toString()))
                .andExpect(jsonPath("$.visible[0].kind").value("SIMILAR"))
                .andExpect(jsonPath("$.visible[0].reasons[0]").value("AUTOMATED_NO"));
        // Someone who cannot see the case learns only that there is one.
        mvc.perform(get(check).with(scoped(member("Blind"), "bl", List.of(), Permission.CASE_CREATE)))
                .andExpect(jsonPath("$.visible.length()").value(0)).andExpect(jsonPath("$.hidden").value(1));

        UUID second = registered(clerk, again.replace("\"financialImpact\"", "\"confirmDistinct\":true,\"distinctNote\":\"new stage\",\"financialImpact\""));
        assertThat(count("select count(*) from case_duplicate_hint where case_id = ? and other_case_id = ?"
                + " and decision = 'DISTINCT' and note = 'new stage'", second, existing)).isEqualTo(1);
        mvc.perform(get("/api/v1/cases/" + second + "/timeline").with(as(Permission.CASE_VIEW)))
                .andExpect(jsonPath("$.items[?(@.type == 'DUPLICATE_REVIEWED')]").isNotEmpty());
        assertThat(count("select count(*) from legal_case")).isEqualTo(2);
    }

    @Test
    void theSameCourtNumberIsSuspectedAndStillNotRegisteredTwiceAtTheSameStage() throws Exception {
        registered(clerk, intake(court, specialist, null));
        String same = intake(court, specialist, null).replace("000123", "000555");
        mvc.perform(registerStrict(clerk, same)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("DUPLICATE_SUSPECTED"));
        // Even a confirmed registration cannot repeat the number and year at one court and stage.
        mvc.perform(registerStrict(clerk, same.replace("\"financialImpact\"", "\"confirmDistinct\":true,\"financialImpact\"")))
                .andExpect(status().isConflict());
        assertThat(count("select count(*) from legal_case")).isEqualTo(1);
    }
}
