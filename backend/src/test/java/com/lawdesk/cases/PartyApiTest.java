package com.lawdesk.cases;

import static com.lawdesk.cases.CaseTestData.insertParty;
import static com.lawdesk.security.TestActors.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.lawdesk.platform.PostgresTestSupport;
import com.lawdesk.security.Permission;

/** LD-016 legal parties (docs/delivery/PHASE-3-ACCEPTANCE.md, LD-016 criterion 1; QA-0121…QA-0127). */
@SpringBootTest
@AutoConfigureMockMvc
class PartyApiTest extends PostgresTestSupport {

    private static final String PARTIES = "/api/v1/parties";
    private static final UUID CLERK = UUID.fromString("00000000-0000-4000-8000-00000000c1e4");

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void removeRows() {
        CaseTestData.deleteAll(jdbc);
    }

    private ResultActions create(String json) throws Exception {
        return mvc.perform(post(PARTIES).with(as(CLERK, "clerk", Permission.PARTY_MANAGE)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static final String PERSON = """
            {"kind":"PERSON","name":" Ahmed Hassan ","nationalId":"29001011234567","phone":"+20 100 123 4567",
             "email":"ahmed@example.com","address":"12 Tahrir St, Cairo"}""";

    @Test
    void createsAPersonAndAnOrganisation() throws Exception {
        String body = create(PERSON).andExpect(status().isCreated())
                .andExpect(header().string("Location", Matchers.containsString(PARTIES + "/")))
                .andExpect(jsonPath("$.kind").value("PERSON"))
                .andExpect(jsonPath("$.name").value("Ahmed Hassan"))
                .andExpect(jsonPath("$.nationalId").value("29001011234567"))
                .andExpect(jsonPath("$.phone").value("+20 100 123 4567"))
                .andExpect(jsonPath("$.email").value("ahmed@example.com"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.createdBy").value(CLERK.toString()))
                .andReturn().getResponse().getContentAsString();
        create("{\"kind\":\"ORGANIZATION\",\"name\":\"Nile Trading Co.\",\"registrationNo\":\"CR-12345\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.registrationNo").value("CR-12345"))
                .andExpect(jsonPath("$.nationalId").doesNotExist());

        mvc.perform(get(PARTIES + "/" + JsonPath.read(body, "$.id")).with(as(Permission.CASE_VIEW)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Ahmed Hassan"));
    }

    @Test
    void validatesEveryField() throws Exception {
        create("{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("kind", "name")));
        create("""
                {"kind":"ROBOT","name":"%s","nationalId":"123","registrationNo":"%s","phone":"call me",
                 "email":"not-an-email","address":"%s"}""".formatted("n".repeat(201), "r".repeat(51), "a".repeat(501)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("kind", "name", "nationalId",
                        "registrationNo", "phone", "email", "address")));
        assertThat(jdbc.queryForObject("select count(*) from party", Long.class)).isZero();
    }

    @Test
    void identifiersMustMatchTheKindOfParty() throws Exception {
        create("{\"kind\":\"ORGANIZATION\",\"name\":\"Co\",\"nationalId\":\"29001011234567\"}")
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.rule").value("PARTY_KIND_MISMATCH"));
        create("{\"kind\":\"PERSON\",\"name\":\"P\",\"registrationNo\":\"CR-1\"}")
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.rule").value("PARTY_KIND_MISMATCH"));
    }

    @Test
    void aNationalIdBelongsToOnePartyOnly() throws Exception {
        create(PERSON).andExpect(status().isCreated());
        create(PERSON.replace("Ahmed Hassan", "Someone Else")).andExpect(status().isConflict());
        assertThatThrownBy(() -> insertParty(jdbc, "PERSON", "X", "29001011234567"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void editsAPartyWithOptimisticLocking() throws Exception {
        UUID id = insertParty(jdbc, "PERSON", "Old Name", null);
        String path = PARTIES + "/" + id;
        String json = "{\"kind\":\"PERSON\",\"name\":\"New Name\",\"phone\":\"0100\",\"version\":0}";

        mvc.perform(put(path).with(as(CLERK, "clerk", Permission.PARTY_MANAGE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("New Name"))
                .andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.updatedBy").value(CLERK.toString()));
        mvc.perform(put(path).with(as(Permission.PARTY_MANAGE)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isConflict());
        mvc.perform(put(path).with(as(Permission.PARTY_MANAGE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"kind\":\"PERSON\",\"name\":\"N\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("version"));
        mvc.perform(put(PARTIES + "/" + UUID.randomUUID()).with(as(Permission.PARTY_MANAGE)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isNotFound());
    }

    @Test
    void searchesPartiesByNameOrIdentifier() throws Exception {
        insertParty(jdbc, "PERSON", "Ahmed Hassan", "29001011234567");
        insertParty(jdbc, "ORGANIZATION", "Nile Trading Co.", null);
        insertParty(jdbc, "PERSON", "Mona Ahmed", null);

        mvc.perform(get(PARTIES).param("q", "ahmed").with(as(Permission.CASE_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].name", contains("Ahmed Hassan", "Mona Ahmed")))
                .andExpect(jsonPath("$.totalItems").value(2));
        mvc.perform(get(PARTIES).param("q", "2900101").with(as(Permission.CASE_VIEW)))
                .andExpect(jsonPath("$.items[*].name", contains("Ahmed Hassan")));
        mvc.perform(get(PARTIES).with(as(Permission.CASE_VIEW))).andExpect(jsonPath("$.totalItems").value(3));
        mvc.perform(get(PARTIES).param("q", "q".repeat(101)).with(as(Permission.CASE_VIEW)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void needsASessionAndTheRightPermission() throws Exception {
        mvc.perform(post(PARTIES).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(PERSON))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(PARTIES).with(as(Permission.CASE_VIEW, Permission.CASE_EDIT)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(PERSON)).andExpect(status().isForbidden());
        mvc.perform(get(PARTIES).with(as(Permission.PARTY_MANAGE))).andExpect(status().isForbidden());
        mvc.perform(get(PARTIES + "/" + UUID.randomUUID()).with(as(Permission.CASE_VIEW)))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select count(*) from party", Long.class)).isZero();
    }
}
