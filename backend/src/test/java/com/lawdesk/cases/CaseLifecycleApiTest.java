package com.lawdesk.cases;

import static com.lawdesk.cases.CaseTestData.insertCase;
import static com.lawdesk.security.TestActors.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.lawdesk.platform.PostgresTestSupport;
import com.lawdesk.security.Permission;

/** LD-017 case lifecycle status (docs/delivery/PHASE-3-ACCEPTANCE.md, LD-017; QA-0129…QA-0135). */
@SpringBootTest
@AutoConfigureMockMvc
class CaseLifecycleApiTest extends PostgresTestSupport {

    private static final UUID MANAGER = UUID.fromString("00000000-0000-4000-8000-0000000000a7");

    /** The transition table of the acceptance criteria. */
    private static final Map<CaseStatus, Set<CaseStatus>> ALLOWED = Map.of(
            CaseStatus.OPEN, EnumSet.of(CaseStatus.ACTIVE, CaseStatus.CLOSED),
            CaseStatus.ACTIVE, EnumSet.of(CaseStatus.SUSPENDED, CaseStatus.CLOSED),
            CaseStatus.SUSPENDED, EnumSet.of(CaseStatus.ACTIVE, CaseStatus.CLOSED),
            CaseStatus.CLOSED, EnumSet.of(CaseStatus.ACTIVE, CaseStatus.ARCHIVED),
            CaseStatus.ARCHIVED, EnumSet.noneOf(CaseStatus.class));

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private UUID id;

    @BeforeEach
    void anOpenCase() {
        id = insertCase(jdbc, "R-1", "T", CaseType.CIVIL, CaseStatus.OPEN, LocalDate.of(2026, 1, 5));
    }

    @AfterEach
    void removeRows() {
        CaseTestData.deleteAll(jdbc);
    }

    private ResultActions change(String json) throws Exception {
        return mvc.perform(post("/api/v1/cases/" + id + "/status")
                .with(as(MANAGER, "manager", Permission.CASE_STATUS_CHANGE)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String body(String status, String reason, long version) {
        return "{\"status\":\"%s\",%s\"version\":%d}".formatted(status,
                reason == null ? "" : "\"reason\":\"" + reason + "\",", version);
    }

    private void setStatus(CaseStatus status) {
        jdbc.update("update legal_case set status = ?, version = 0 where id = ?", status.name(), id);
    }

    private List<Map<String, Object>> events() {
        return jdbc.queryForList("select * from case_event where case_id = ? order by seq", id);
    }

    @Test
    void theDomainAllowsExactlyTheDocumentedTransitions() {
        for (CaseStatus from : CaseStatus.values()) {
            for (CaseStatus to : CaseStatus.values()) {
                assertThat(from.canChangeTo(to)).as("%s -> %s", from, to).isEqualTo(ALLOWED.get(from).contains(to));
            }
        }
    }

    @Test
    void appliesAnAllowedTransitionAndRecordsItInTheTimeline() throws Exception {
        change(body("ACTIVE", " First hearing scheduled ", 0)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.updatedBy").value(MANAGER.toString()));

        assertThat(events()).singleElement().satisfies(event -> {
            assertThat(event).containsEntry("type", "CASE_STATUS_CHANGED").containsEntry("created_by", MANAGER);
            assertThat((String) event.get("summary")).contains("OPEN", "ACTIVE", "First hearing scheduled");
        });
    }

    @Test
    void walksTheWholeLifecycle() throws Exception {
        long version = 0;
        for (String next : List.of("ACTIVE", "SUSPENDED", "ACTIVE", "CLOSED", "ARCHIVED")) {
            change(body(next, null, version++)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value(next));
        }
        assertThat(events()).hasSize(5);
    }

    @ParameterizedTest
    @CsvSource({"OPEN, SUSPENDED", "OPEN, ARCHIVED", "OPEN, OPEN", "ACTIVE, OPEN", "ACTIVE, ARCHIVED",
            "SUSPENDED, ARCHIVED", "CLOSED, SUSPENDED", "ARCHIVED, ACTIVE", "ARCHIVED, CLOSED"})
    void refusesATransitionNotInTheTable(CaseStatus from, CaseStatus to) throws Exception {
        setStatus(from);

        change(body(to.name(), "x", 0)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("STATUS_TRANSITION_NOT_ALLOWED"));
        assertThat(jdbc.queryForObject("select status from legal_case where id = ?", String.class, id))
                .isEqualTo(from.name());
        assertThat(events()).isEmpty();
    }

    @Test
    void reopeningAClosedCaseNeedsAReason() throws Exception {
        setStatus(CaseStatus.CLOSED);

        change(body("ACTIVE", null, 0)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("REASON_REQUIRED"));
        change(body("ACTIVE", "   ", 0)).andExpect(jsonPath("$.rule").value("REASON_REQUIRED"));
        change(body("ACTIVE", "Appeal accepted", 0)).andExpect(status().isOk());
    }

    @Test
    void aStaleVersionIsAConflict() throws Exception {
        change(body("ACTIVE", null, 0)).andExpect(status().isOk());

        change(body("CLOSED", null, 0)).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select status from legal_case where id = ?", String.class, id))
                .isEqualTo("ACTIVE");
    }

    @Test
    void invalidInputIsAFieldError() throws Exception {
        change("{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'status')].code").value("NotNull"))
                .andExpect(jsonPath("$.errors[?(@.field == 'version')].code").value("NotNull"));
        change(body("DELETED", null, 0)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("status"))
                .andExpect(jsonPath("$.errors[0].code").value("Pattern"));
        change(body("ACTIVE", "r".repeat(501), 0)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("reason"))
                .andExpect(jsonPath("$.errors[0].code").value("Size"));
        change(body("ACTIVE", "r".repeat(500), 0)).andExpect(status().isOk());
        // The event summary keeps the transition and as much of the reason as its 500 characters allow.
        assertThat((String) events().getFirst().get("summary")).hasSize(500).startsWith("OPEN -> ACTIVE: rrr")
                .endsWith("…");
    }

    @Test
    void anUnknownCaseIsNotFound() throws Exception {
        mvc.perform(post("/api/v1/cases/" + UUID.randomUUID() + "/status")
                        .with(as(Permission.CASE_STATUS_CHANGE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body("ACTIVE", null, 0)))
                .andExpect(status().isNotFound());
    }

    @Test
    void needsASessionAndTheStatusChangePermission() throws Exception {
        String json = body("ACTIVE", null, 0);
        String path = "/api/v1/cases/" + id + "/status";
        mvc.perform(post(path).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isUnauthorized());
        // LEGAL_STAFF holds CASE_EDIT but not CASE_STATUS_CHANGE by default (CONTRACTS §3).
        mvc.perform(post(path).with(as(Permission.CASE_VIEW, Permission.CASE_EDIT)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isForbidden());

        assertThat(jdbc.queryForObject("select status from legal_case where id = ?", String.class, id))
                .isEqualTo("OPEN");
    }
}
