package com.lawdesk.cases;

import static com.lawdesk.cases.CaseTestData.insertCase;
import static com.lawdesk.security.TestActors.as;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.lawdesk.platform.PostgresTestSupport;
import com.lawdesk.security.Permission;

/** LD-014 case timeline (docs/delivery/PHASE-3-ACCEPTANCE.md, LD-014; QA-0105…QA-0111). */
@SpringBootTest
@AutoConfigureMockMvc
class CaseTimelineApiTest extends PostgresTestSupport {

    private static final UUID ACTOR = UUID.fromString("00000000-0000-4000-8000-00000000071e");

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private UUID id;

    /** Three events in one transaction: they share occurred_at, so only seq orders them. */
    @BeforeEach
    void aCaseWithHistory() {
        id = insertCase(jdbc, "R-1", "T", CaseType.CIVIL, CaseStatus.OPEN, LocalDate.of(2026, 1, 5));
        jdbc.update("""
                insert into case_event (case_id, type, summary, created_by)
                values (?, 'CASE_CREATED', 'first', ?), (?, 'CASE_UPDATED', 'second', ?),
                       (?, 'PROCEEDING_RECORDED', 'third', ?)""", id, ACTOR, id, ACTOR, id, ACTOR);
    }

    @AfterEach
    void removeRows() {
        CaseTestData.deleteAll(jdbc);
    }

    @Test
    void listsTheEventsNewestFirstInTheSharedPageShape() throws Exception {
        mvc.perform(get("/api/v1/cases/" + id + "/timeline").with(as(Permission.CASE_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].summary", contains("third", "second", "first")))
                .andExpect(jsonPath("$.items[0].type").value("PROCEEDING_RECORDED"))
                .andExpect(jsonPath("$.items[0].actorId").value(ACTOR.toString()))
                .andExpect(jsonPath("$.items[0].occurredAt").isNotEmpty())
                .andExpect(jsonPath("$.items[0].id").isNotEmpty())
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void pagesAndCapsThePageSize() throws Exception {
        mvc.perform(get("/api/v1/cases/" + id + "/timeline").param("size", "2").param("page", "1")
                        .with(as(Permission.CASE_VIEW)))
                .andExpect(jsonPath("$.items[*].summary", contains("first")))
                .andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get("/api/v1/cases/" + id + "/timeline").param("size", "1000").with(as(Permission.CASE_VIEW)))
                .andExpect(jsonPath("$.size").value(100));
        mvc.perform(get("/api/v1/cases/" + id + "/timeline").param("page", "-1").with(as(Permission.CASE_VIEW)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("page"));
    }

    @Test
    void anUnknownCaseIsNotFound() throws Exception {
        mvc.perform(get("/api/v1/cases/" + UUID.randomUUID() + "/timeline").with(as(Permission.CASE_VIEW)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/cases/x/timeline").with(as(Permission.CASE_VIEW))).andExpect(status().isNotFound());
    }

    @Test
    void needsASessionAndCaseView() throws Exception {
        mvc.perform(get("/api/v1/cases/" + id + "/timeline")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/cases/" + id + "/timeline").with(as(Permission.CASE_CREATE)))
                .andExpect(status().isForbidden());
    }
}
