package com.lawdesk.cases;

import static com.lawdesk.cases.CaseTestData.insertCase;
import static com.lawdesk.security.TestActors.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.lawdesk.platform.PostgresTestSupport;
import com.lawdesk.security.Permission;

/** LD-013 edit a case with optimistic locking (docs/delivery/PHASE-3-ACCEPTANCE.md, LD-013; QA-0097…QA-0103). */
@SpringBootTest
@AutoConfigureMockMvc
class CaseEditApiTest extends PostgresTestSupport {

    private static final String CASES = "/api/v1/cases/";
    private static final UUID EDITOR = UUID.fromString("00000000-0000-4000-8000-0000000000e1");

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private UUID id;

    @BeforeEach
    void aCase() {
        id = insertCase(jdbc, "R-1", "Original title", CaseType.CIVIL, CaseStatus.ACTIVE, LocalDate.of(2026, 1, 5));
    }

    @AfterEach
    void removeRows() {
        CaseTestData.deleteAll(jdbc);
    }

    private static String body(String title, String caseType, String subject, String openedOn, Long version) {
        StringBuilder json = new StringBuilder("{\"x\":0");
        if (title != null) json.append(",\"title\":\"").append(title).append('"');
        if (caseType != null) json.append(",\"caseType\":\"").append(caseType).append('"');
        if (subject != null) json.append(",\"subject\":\"").append(subject).append('"');
        if (openedOn != null) json.append(",\"openedOn\":\"").append(openedOn).append('"');
        if (version != null) json.append(",\"version\":").append(version);
        return json.append('}').toString();
    }

    private ResultActions edit(UUID caseId, String json) throws Exception {
        return mvc.perform(put(CASES + caseId).with(as(EDITOR, "editor", Permission.CASE_EDIT)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private List<Map<String, Object>> events() {
        return jdbc.queryForList("select * from case_event where case_id = ? order by seq", id);
    }

    private Map<String, Object> row() {
        return jdbc.queryForMap("select * from legal_case where id = ?", id);
    }

    @Test
    void updatesTheEditableFieldsAndIncrementsTheVersion() throws Exception {
        edit(id, body(" New title ", "CIVIL", "Boundary dispute", "2026-01-05", 0L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.title").value("New title"))
                .andExpect(jsonPath("$.subject").value("Boundary dispute"))
                .andExpect(jsonPath("$.referenceNo").value("R-1"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.updatedBy").value(EDITOR.toString()))
                .andExpect(jsonPath("$.createdBy").value(CaseTestData.CREATOR.toString()));

        assertThat(row()).containsEntry("title", "New title").containsEntry("version", 1L)
                .containsEntry("updated_by", EDITOR);
        assertThat(events()).singleElement().satisfies(event -> {
            assertThat(event).containsEntry("type", "CASE_UPDATED").containsEntry("created_by", EDITOR);
            assertThat((String) event.get("summary")).contains("title", "subject").doesNotContain("caseType");
        });
    }

    @Test
    void changesTypeAndOpenedDateAndClearsTheSubject() throws Exception {
        jdbc.update("update legal_case set subject = 'old' where id = ?", id);

        edit(id, body("Original title", "LABOR", " ", "2025-12-31", 0L)).andExpect(status().isOk())
                .andExpect(jsonPath("$.caseType").value("LABOR"))
                .andExpect(jsonPath("$.openedOn").value("2025-12-31"))
                .andExpect(jsonPath("$.subject").doesNotExist());
        assertThat((String) events().getFirst().get("summary")).contains("caseType", "subject", "openedOn");
    }

    @Test
    void referenceNumberAndStatusAreNotEditableHere() throws Exception {
        edit(id, "{\"title\":\"T\",\"caseType\":\"CIVIL\",\"openedOn\":\"2026-01-05\",\"version\":0,"
                + "\"referenceNo\":\"OTHER\",\"status\":\"CLOSED\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.referenceNo").value("R-1"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void aStaleVersionIsAConflictAndChangesNothing() throws Exception {
        edit(id, body("First", "CIVIL", null, "2026-01-05", 0L)).andExpect(status().isOk());

        edit(id, body("Second", "CIVIL", null, "2026-01-05", 0L)).andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        edit(id, body("Second", "CIVIL", null, "2026-01-05", 7L)).andExpect(status().isConflict());

        assertThat(row()).containsEntry("title", "First").containsEntry("version", 1L);
        assertThat(events()).hasSize(1);
    }

    @Test
    void concurrentEditsOfTheSameVersionLetExactlyOneWin() {
        int editors = 6;
        CyclicBarrier start = new CyclicBarrier(editors);
        List<Integer> statuses = IntStream.range(0, editors)
                .mapToObj(i -> CompletableFuture.supplyAsync(() -> {
                    try {
                        start.await();
                        return edit(id, body("Title " + i, "CIVIL", null, "2026-01-05", 0L)).andReturn()
                                .getResponse().getStatus();
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }, Executors.newVirtualThreadPerTaskExecutor()))
                .toList().stream().map(CompletableFuture::join).toList();

        assertThat(statuses).containsOnly(200, 409).filteredOn(s -> s == 200).hasSize(1);
        assertThat(row()).containsEntry("version", 1L);
        assertThat(events()).hasSize(1);
    }

    @Test
    void anUnchangedRequestKeepsTheVersionAndRecordsNothing() throws Exception {
        edit(id, body("Original title", "CIVIL", "  ", "2026-01-05", 0L)).andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(0));

        assertThat(row()).containsEntry("version", 0L).containsEntry("updated_by", CaseTestData.CREATOR);
        assertThat(events()).isEmpty();
    }

    @Test
    void missingFieldsAreListedAndNothingChanges() throws Exception {
        edit(id, "{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("title", "caseType", "openedOn",
                        "version")))
                .andExpect(jsonPath("$.errors[?(@.field == 'version')].code").value("NotNull"));

        assertThat(row()).containsEntry("title", "Original title").containsEntry("version", 0L);
    }

    @Test
    void theCreationRulesApplyToEveryField() throws Exception {
        edit(id, body("t".repeat(201), "TAX", "s".repeat(4001), LocalDate.now().plusDays(1).toString(), 0L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].code", containsInAnyOrder("Size", "Pattern", "Size",
                        "PastOrPresent")));
        edit(id, body("t".repeat(200), "CIVIL", "s".repeat(4000), LocalDate.now().toString(), 0L))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @EnumSource(value = CaseStatus.class, names = {"CLOSED", "ARCHIVED"})
    void closedAndArchivedCasesAreReadOnly(CaseStatus readOnly) throws Exception {
        jdbc.update("update legal_case set status = ? where id = ?", readOnly.name(), id);

        edit(id, body("Changed", "CIVIL", null, "2026-01-05", 0L)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("CASE_READ_ONLY"));
        assertThat(row()).containsEntry("title", "Original title");
        assertThat(events()).isEmpty();
    }

    @Test
    void anUnknownOrMalformedCaseIsNotFound() throws Exception {
        edit(UUID.randomUUID(), body("T", "CIVIL", null, "2026-01-05", 0L)).andExpect(status().isNotFound());
        mvc.perform(put(CASES + "nope").with(as(Permission.CASE_EDIT)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body("T", "CIVIL", null, "2026-01-05", 0L)))
                .andExpect(status().isNotFound());
    }

    @Test
    void withoutASessionThePermissionOrTheCsrfTokenNothingChanges() throws Exception {
        String json = body("Changed", "CIVIL", null, "2026-01-05", 0L);
        mvc.perform(put(CASES + id).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isUnauthorized());
        mvc.perform(put(CASES + id).with(as(Permission.CASE_VIEW, Permission.CASE_CREATE)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isForbidden());
        mvc.perform(put(CASES + id).with(as(Permission.CASE_EDIT)).contentType(MediaType.APPLICATION_JSON)
                .content(json)).andExpect(status().isForbidden());

        assertThat(row()).containsEntry("title", "Original title").containsEntry("version", 0L);
    }
}
