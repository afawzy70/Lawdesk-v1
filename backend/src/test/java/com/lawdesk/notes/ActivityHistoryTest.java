package com.lawdesk.notes;

import static com.lawdesk.security.TestActors.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;
import com.lawdesk.platform.PostgresTestSupport;
import com.lawdesk.security.Permission;

/**
 * LD-029 activity history: the case timeline of LD-014 is the activity history, and every Phase 5 module writes its
 * events there through {@code CaseLookup#recordEvent} (PHASE-5-ACCEPTANCE LD-029 criterion 3). One case gets a
 * hearing, a task, a document, an expert referral and a note, and its timeline shows all of them, newest first.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ActivityHistoryTest extends PostgresTestSupport {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void removeOperationsRecords() {
        for (String table : List.of("case_note", "case_document", "case_document_quota", "task", "expert_session",
                "expert_referral", "expert")) {
            jdbc.update("delete from " + table);
        }
        jdbc.update("delete from hearing where adjourned_from_id is not null");
        jdbc.update("delete from hearing");
        jdbc.update("delete from proceeding");
        jdbc.update("delete from court");
    }

    private String id(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private MvcResult postJson(String path, Permission permission, String json) throws Exception {
        return mvc.perform(post(path).with(as(permission)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json)).andExpect(status().isCreated()).andReturn();
    }

    @Test
    void theCaseTimelineShowsEveryOperationOfTheCase() throws Exception {
        String caseId = id(postJson("/api/v1/cases", Permission.CASE_CREATE,
                "{\"title\":\"Activity\",\"caseType\":\"CIVIL\",\"openedOn\":\"2024-01-10\"}"));
        String court = id(postJson("/api/v1/courts", Permission.COURT_MANAGE,
                "{\"code\":\"ACT-1\",\"nameAr\":\"محكمة\",\"nameEn\":\"Court\",\"level\":\"FIRST_INSTANCE\"}"));
        String proceeding = id(postJson("/api/v1/cases/" + caseId + "/proceedings", Permission.PROCEEDING_MANAGE, """
                {"stage":"FIRST_INSTANCE","courtId":"%s","courtCaseNumber":"700","courtCaseYear":2025,
                 "filingDate":"2025-01-10"}""".formatted(court)));
        postJson("/api/v1/proceedings/" + proceeding + "/hearings", Permission.HEARING_MANAGE,
                "{\"hearingDate\":\"2025-02-01\",\"purpose\":\"FIRST_SESSION\"}");
        String assignee = jdbc.queryForObject("""
                insert into app_user (username, email, display_name, password_hash, enabled, created_by, updated_by)
                values ('activity-user', 'activity@example.invalid', 'Activity User', 'x', true, gen_random_uuid(),
                        gen_random_uuid()) returning id""", String.class);
        postJson("/api/v1/tasks", Permission.TASK_MANAGE,
                "{\"title\":\"Prepare\",\"caseId\":\"%s\",\"assigneeId\":\"%s\"}".formatted(caseId, assignee));
        mvc.perform(multipart("/api/v1/cases/" + caseId + "/documents")
                        .file(new MockMultipartFile("file", "claim.pdf", "application/pdf",
                                "%PDF-1.7 claim".getBytes(StandardCharsets.ISO_8859_1)))
                        .with(as(Permission.DOCUMENT_MANAGE)).with(csrf()))
                .andExpect(status().isCreated());
        String expert = id(postJson("/api/v1/experts", Permission.EXPERT_MANAGE,
                "{\"name\":\"Expert\",\"specialty\":\"Engineering\"}"));
        postJson("/api/v1/proceedings/" + proceeding + "/expert-referrals", Permission.EXPERT_MANAGE,
                "{\"expertId\":\"%s\",\"referralDate\":\"2025-02-02\"}".formatted(expert));
        postJson("/api/v1/cases/" + caseId + "/notes", Permission.NOTE_MANAGE, "{\"body\":\"Internal\"}");

        MvcResult timeline = mvc.perform(get("/api/v1/cases/" + caseId + "/timeline").with(as(Permission.CASE_VIEW)))
                .andExpect(status().isOk()).andReturn();
        List<String> types = JsonPath.read(timeline.getResponse().getContentAsString(), "$.items[*].type");
        assertThat(types).containsExactly("NOTE_ADDED", "EXPERT_REFERRED", "DOCUMENT_ATTACHED", "TASK_CREATED",
                "HEARING_SCHEDULED", "PROCEEDING_RECORDED", "CASE_CREATED");
        jdbc.update("delete from app_user where id = ?::uuid", assignee);
    }
}
