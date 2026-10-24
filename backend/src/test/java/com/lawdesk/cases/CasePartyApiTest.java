package com.lawdesk.cases;

import static com.lawdesk.cases.CaseTestData.insertCase;
import static com.lawdesk.cases.CaseTestData.insertParty;
import static com.lawdesk.security.TestActors.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.lawdesk.platform.PostgresTestSupport;
import com.lawdesk.security.Permission;

/** LD-016 parties of a case (docs/delivery/PHASE-3-ACCEPTANCE.md, LD-016 criteria 2–5; QA-0121…QA-0127). */
@SpringBootTest
@AutoConfigureMockMvc
class CasePartyApiTest extends PostgresTestSupport {

    private static final UUID CLERK = UUID.fromString("00000000-0000-4000-8000-0000000c1e45");

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private UUID caseId;
    private UUID client;
    private UUID opponent;

    @BeforeEach
    void aCaseAndTwoParties() {
        caseId = insertCase(jdbc, "R-1", "Ahmed v. Nile", CaseType.COMMERCIAL, CaseStatus.ACTIVE,
                LocalDate.of(2026, 1, 5));
        client = insertParty(jdbc, "PERSON", "Ahmed Hassan", null);
        opponent = insertParty(jdbc, "ORGANIZATION", "Nile Trading Co.", null);
    }

    @AfterEach
    void removeRows() {
        CaseTestData.deleteAll(jdbc);
    }

    private String parties() {
        return "/api/v1/cases/" + caseId + "/parties";
    }

    private ResultActions link(UUID partyId, String role) throws Exception {
        return mvc.perform(post(parties()).with(as(CLERK, "clerk", Permission.PARTY_MANAGE)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"partyId\":\"%s\",\"role\":\"%s\"}".formatted(partyId, role)));
    }

    private List<Map<String, Object>> events() {
        return jdbc.queryForList("select * from case_event where case_id = ? order by seq", caseId);
    }

    @Test
    void linksPartiesWithTheirRolesAndListsThem() throws Exception {
        link(client, "CLIENT").andExpect(status().isCreated())
                .andExpect(jsonPath("$.partyId").value(client.toString()))
                .andExpect(jsonPath("$.name").value("Ahmed Hassan"))
                .andExpect(jsonPath("$.kind").value("PERSON"))
                .andExpect(jsonPath("$.role").value("CLIENT"));
        link(opponent, "OPPONENT").andExpect(status().isCreated());

        mvc.perform(get(parties()).with(as(Permission.CASE_VIEW))).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", contains("Ahmed Hassan", "Nile Trading Co.")))
                .andExpect(jsonPath("$[*].role", contains("CLIENT", "OPPONENT")));
        assertThat(events()).extracting(e -> e.get("type")).containsExactly("PARTY_ADDED", "PARTY_ADDED");
        assertThat((String) events().getFirst().get("summary")).contains("Ahmed Hassan", "CLIENT");
        assertThat(events().getFirst()).containsEntry("created_by", CLERK);
    }

    @Test
    void aPartyAppearsOnceOnACase() throws Exception {
        link(client, "CLIENT").andExpect(status().isCreated());

        link(client, "OPPONENT").andExpect(status().isConflict());
        assertThat(events()).hasSize(1);
    }

    @Test
    void unlinksAPartyAndRecordsIt() throws Exception {
        link(client, "CLIENT");

        mvc.perform(delete(parties() + "/" + client).with(as(Permission.PARTY_MANAGE)).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(delete(parties() + "/" + client).with(as(Permission.PARTY_MANAGE)).with(csrf()))
                .andExpect(status().isNotFound());

        mvc.perform(get(parties()).with(as(Permission.CASE_VIEW))).andExpect(jsonPath("$.length()").value(0));
        assertThat(events()).extracting(e -> e.get("type")).containsExactly("PARTY_ADDED", "PARTY_REMOVED");
        assertThat((String) events().getLast().get("summary")).contains("Ahmed Hassan", "CLIENT");
    }

    @Test
    void validatesTheLink() throws Exception {
        mvc.perform(post(parties()).with(as(Permission.PARTY_MANAGE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'partyId')].code").value("NotNull"))
                .andExpect(jsonPath("$.errors[?(@.field == 'role')].code").value("NotNull"));
        link(client, "JUDGE").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("role"));
        link(UUID.randomUUID(), "CLIENT").andExpect(status().isNotFound());
        caseId = UUID.randomUUID();
        link(client, "CLIENT").andExpect(status().isNotFound());
    }

    @Test
    void partiesOfAnArchivedCaseCannotChange() throws Exception {
        link(client, "CLIENT");
        jdbc.update("update legal_case set status = 'ARCHIVED' where id = ?", caseId);

        link(opponent, "OPPONENT").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("CASE_READ_ONLY"));
        mvc.perform(delete(parties() + "/" + client).with(as(Permission.PARTY_MANAGE)).with(csrf()))
                .andExpect(status().isUnprocessableContent());
        assertThat(events()).hasSize(1);
    }

    @Test
    void theCaseSearchFindsCasesByPartyName() throws Exception {
        link(opponent, "OPPONENT");
        insertCase(jdbc, "R-2", "Unrelated", CaseType.CIVIL, CaseStatus.OPEN, LocalDate.of(2026, 1, 6));

        mvc.perform(get("/api/v1/cases").param("q", "nile trading").with(as(Permission.CASE_VIEW)))
                .andExpect(jsonPath("$.items[*].referenceNo", contains("R-1")));
    }

    @Test
    void needsASessionAndTheRightPermission() throws Exception {
        String json = "{\"partyId\":\"%s\",\"role\":\"CLIENT\"}".formatted(client);
        mvc.perform(post(parties()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(parties()).with(as(Permission.CASE_EDIT, Permission.CASE_VIEW)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isForbidden());
        mvc.perform(get(parties()).with(as(Permission.PARTY_MANAGE))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/cases/" + UUID.randomUUID() + "/parties").with(as(Permission.CASE_VIEW)))
                .andExpect(status().isNotFound());
        assertThat(events()).isEmpty();
    }
}
