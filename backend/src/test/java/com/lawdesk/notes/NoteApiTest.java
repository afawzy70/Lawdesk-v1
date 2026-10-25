package com.lawdesk.notes;

import static com.lawdesk.security.TestActors.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.lawdesk.platform.OperationsTestData;
import com.lawdesk.cases.CaseLookup;
import com.lawdesk.platform.PostgresTestSupport;
import com.lawdesk.security.Permission;

/** LD-029 legal notes (docs/delivery/PHASE-5-ACCEPTANCE.md): QA-0225…QA-0231. */
@SpringBootTest
@AutoConfigureMockMvc
class NoteApiTest extends PostgresTestSupport {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoSpyBean
    CaseLookup caseLookup;

    @AfterEach
    void removeNotes() {
        OperationsTestData.deleteAll(jdbc);
    }

    private UUID author;

    UUID user(String displayName) {
        String username = "note" + UUID.randomUUID().toString().substring(0, 8);
        return jdbc.queryForObject("""
                insert into app_user (username, email, display_name, password_hash, enabled, created_by, updated_by)
                values (?, ?, ?, 'x', true, gen_random_uuid(), gen_random_uuid()) returning id""", UUID.class,
                username, username + "@example.invalid", displayName);
    }

    UUID createCase(String title) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/cases").with(as(Permission.CASE_CREATE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","caseType":"CIVIL","openedOn":"2024-01-10"}""".formatted(title)))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    static String body(String text) {
        return text == null ? "{}" : "{\"body\":\"%s\"}".formatted(text);
    }

    MockHttpServletRequestBuilder postNote(UUID caseId, UUID actor, String json) {
        return post("/api/v1/cases/" + caseId + "/notes").with(as(actor, "author", Permission.NOTE_MANAGE))
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json);
    }

    UUID note(UUID caseId, UUID actor, String text) throws Exception {
        MvcResult result = mvc.perform(postNote(caseId, actor, body(text))).andExpect(status().isCreated()).andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    MockHttpServletRequestBuilder putNote(UUID id, UUID actor, String text, long version) {
        return put("/api/v1/notes/" + id).with(as(actor, "author", Permission.NOTE_MANAGE)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"%s\",\"version\":%d}".formatted(text, version));
    }

    MockHttpServletRequestBuilder deleteNote(UUID id, UUID actor, Object version) {
        MockHttpServletRequestBuilder request = delete("/api/v1/notes/" + id)
                .with(as(actor, "author", Permission.NOTE_MANAGE)).with(csrf());
        return version == null ? request : request.param("version", version.toString());
    }

    // QA-0225 authorized happy path

    @Test
    void writesReadsEditsAndRemovesALegalNote() throws Exception {
        author = user("Mona Saleh");
        UUID caseId = createCase("Notes");
        MvcResult created = mvc.perform(postNote(caseId, author, body("  Client prefers settlement before the appeal. ")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.caseId").value(caseId.toString()))
                .andExpect(jsonPath("$.body").value("Client prefers settlement before the appeal."))
                .andExpect(jsonPath("$.authorId").value(author.toString()))
                .andExpect(jsonPath("$.authorName").value("Mona Saleh"))
                .andExpect(jsonPath("$.editable").value(true))
                .andExpect(jsonPath("$.edited").value(false))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn();
        UUID id = UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.id"));
        assertThat(created.getResponse().getHeader("Location")).endsWith("/api/v1/notes/" + id);
        // The timeline names the author, never the note's text (it is internal to the office).
        verify(caseLookup).recordEvent(eq(caseId), eq("NOTE_ADDED"),
                argThat(summary -> summary.contains("Mona Saleh") && !summary.contains("settlement")));

        UUID later = note(caseId, author, "Second thought");
        mvc.perform(get("/api/v1/cases/" + caseId + "/notes").with(as(UUID.randomUUID(), "reader", Permission.CASE_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id").value(Matchers.contains(later.toString(), id.toString())))
                .andExpect(jsonPath("$.items[0].editable").value(false))
                .andExpect(jsonPath("$.totalItems").value(2));

        mvc.perform(putNote(id, author, "Client prefers settlement.", 0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("Client prefers settlement."))
                .andExpect(jsonPath("$.edited").value(true))
                .andExpect(jsonPath("$.version").value(1));
        mvc.perform(deleteNote(id, author, 1)).andExpect(status().isNoContent());
        verify(caseLookup).recordEvent(eq(caseId), eq("NOTE_REMOVED"),
                argThat(summary -> summary.contains("Mona Saleh") && !summary.contains("settlement")));
        assertThat(jdbc.queryForObject("select count(*) from case_note where case_id = ?", Integer.class, caseId))
                .isEqualTo(1);
    }

    // QA-0226 required-field validation, QA-0227 invalid input boundary

    @Test
    void theBodyIsRequiredAndBounded() throws Exception {
        author = user("Bounds");
        UUID caseId = createCase("Bounds");
        mvc.perform(postNote(caseId, author, "{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("body"))
                .andExpect(jsonPath("$.errors[0].code").value("NotBlank"));
        mvc.perform(postNote(caseId, author, body("   "))).andExpect(status().isBadRequest());
        mvc.perform(postNote(caseId, author, body("n".repeat(4001))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].code").value("Size"));
        UUID id = note(caseId, author, "n".repeat(4000));
        mvc.perform(put("/api/v1/notes/" + id).with(as(author, "author", Permission.NOTE_MANAGE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(Matchers.containsInAnyOrder("body", "version")));
        mvc.perform(deleteNote(id, author, null))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("version"));
        mvc.perform(postNote(UUID.randomUUID(), author, body("x"))).andExpect(status().isNotFound());
        mvc.perform(putNote(UUID.randomUUID(), author, "x", 0)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/cases/" + UUID.randomUUID() + "/notes").with(as(Permission.CASE_VIEW)))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select count(*) from case_note", Integer.class)).isEqualTo(1);
    }

    @Test
    void onlyTheAuthorChangesANoteAndArchivedCasesTakeNone() throws Exception {
        author = user("Author");
        UUID other = user("Colleague");
        UUID caseId = createCase("Authors");
        UUID id = note(caseId, author, "Mine");
        mvc.perform(putNote(id, other, "Changed", 0))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.rule").value("NOTE_AUTHOR_ONLY"));
        mvc.perform(deleteNote(id, other, 0))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.rule").value("NOTE_AUTHOR_ONLY"));

        for (String[] step : new String[][] {{"CLOSED", "0"}, {"ARCHIVED", "1"}}) {
            mvc.perform(post("/api/v1/cases/" + caseId + "/status").with(as(Permission.CASE_STATUS_CHANGE))
                    .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"status\":\"%s\",\"version\":%s}".formatted(step[0], step[1])))
                    .andExpect(status().isOk());
        }
        mvc.perform(postNote(caseId, author, body("After archiving")))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.rule").value("CASE_ARCHIVED"));
        mvc.perform(putNote(id, author, "Changed", 0))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.rule").value("CASE_ARCHIVED"));
        mvc.perform(get("/api/v1/cases/" + caseId + "/notes").with(as(Permission.CASE_VIEW)))
                .andExpect(jsonPath("$.items[0].body").value("Mine"));
    }

    // QA-0228 unauthenticated access, QA-0229 insufficient permission

    @Test
    void notesNeedASessionAndTheirPermissions() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(get("/api/v1/cases/" + id + "/notes")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/cases/" + id + "/notes").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/notes/" + id).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/notes/" + id).param("version", "0").with(csrf()))
                .andExpect(status().isUnauthorized());

        author = user("Writer");
        UUID caseId = createCase("Permissions");
        UUID noteId = note(caseId, author, "Guarded");
        UUID actor = UUID.randomUUID();
        mvc.perform(post("/api/v1/cases/" + caseId + "/notes").with(as(actor, "reader-" + actor, Permission.CASE_VIEW))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body("x")))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("""
                select count(*) from audit_event where action = 'ACCESS_DENIED' and actor_id = ? and target_id = ?""",
                Integer.class, actor, "POST /api/v1/cases/" + caseId + "/notes")).isEqualTo(1);
        mvc.perform(put("/api/v1/notes/" + noteId).with(as(author, "author", Permission.CASE_VIEW)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"x\",\"version\":0}"))
                .andExpect(status().isForbidden());
        // Reading notes needs CASE_VIEW (§8.2); NOTE_MANAGE alone does not open them.
        mvc.perform(get("/api/v1/cases/" + caseId + "/notes").with(as(Permission.NOTE_MANAGE)))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("select body from case_note where id = ?", String.class, noteId))
                .isEqualTo("Guarded");
    }

    // QA-0230 record integrity and rollback, QA-0231 concurrent or repeat request

    @Test
    void aFailingTimelineEventRollsTheNoteBackAndStaleVersionsConflict() throws Exception {
        author = user("Rollback");
        UUID caseId = createCase("Rollback");
        UUID id = note(caseId, author, "Kept");
        mvc.perform(putNote(id, author, "Edited", 0)).andExpect(status().isOk());
        mvc.perform(putNote(id, author, "Stale", 0)).andExpect(status().isConflict());
        mvc.perform(deleteNote(id, author, 0)).andExpect(status().isConflict());

        doThrow(new IllegalStateException("timeline unavailable")).when(caseLookup)
                .recordEvent(eq(caseId), anyString(), anyString());
        mvc.perform(postNote(caseId, author, body("Lost"))).andExpect(status().isInternalServerError());
        mvc.perform(deleteNote(id, author, 1)).andExpect(status().isInternalServerError());
        assertThat(jdbc.queryForList("select body from case_note where case_id = ?", String.class, caseId))
                .isEqualTo(List.of("Edited"));
        assertThatThrownBy(() -> jdbc.update("""
                insert into case_note (case_id, body, created_by, updated_by) values (?, '  ', ?, ?)""", caseId,
                author, author)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
