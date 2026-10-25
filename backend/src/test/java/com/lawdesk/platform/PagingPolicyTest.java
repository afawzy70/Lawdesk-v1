package com.lawdesk.platform;

import static com.lawdesk.security.TestActors.as;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;
import com.lawdesk.security.Permission;

/**
 * QAD-029 (ASSUMPTION for owner review, architecture/CONTRACTS.md §4): an invalid {@code page} or {@code size} falls
 * back to the defaults (page 0, size 20) on the lists built on Spring Data paging, whereas the Phase 3 case list and
 * timeline answer 400. No behaviour changes here; the test records the policy so a change is a conscious one.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PagingPolicyTest extends PostgresTestSupport {

    private static final String[][] INVALID = {{"page", "-1"}, {"page", "bad"}, {"size", "-1"}, {"size", "0"},
            {"size", "bad"}};

    @Autowired
    MockMvc mvc;

    @Test
    void invalidPagingFallsBackToTheDefaultsOnTheSpringDataLists() throws Exception {
        UUID caseId = createCase();
        record Endpoint(String path, Permission permission) {
        }
        for (Endpoint endpoint : new Endpoint[] {new Endpoint("/api/v1/hearings", Permission.HEARING_VIEW),
                new Endpoint("/api/v1/tasks", Permission.TASK_VIEW),
                new Endpoint("/api/v1/experts", Permission.EXPERT_VIEW),
                new Endpoint("/api/v1/documents", Permission.DOCUMENT_VIEW),
                new Endpoint("/api/v1/courts", Permission.COURT_VIEW),
                new Endpoint("/api/v1/audit", Permission.AUDIT_VIEW),
                new Endpoint("/api/v1/cases/" + caseId + "/hearings", Permission.HEARING_VIEW),
                new Endpoint("/api/v1/cases/" + caseId + "/notes", Permission.CASE_VIEW)}) {
            for (String[] parameter : INVALID) {
                mvc.perform(get(endpoint.path()).param(parameter[0], parameter[1]).with(as(endpoint.permission())))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.page").value(0))
                        .andExpect(jsonPath("$.size").value(20));
            }
        }
    }

    @Test
    void theCaseListAndTimelineStillAnswer400() throws Exception {
        UUID caseId = createCase();
        for (String[] parameter : INVALID) {
            mvc.perform(get("/api/v1/cases").param(parameter[0], parameter[1]).with(as(Permission.CASE_VIEW)))
                    .andExpect(status().isBadRequest());
            mvc.perform(get("/api/v1/cases/" + caseId + "/timeline").param(parameter[0], parameter[1])
                    .with(as(Permission.CASE_VIEW))).andExpect(status().isBadRequest());
        }
    }

    private UUID createCase() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/cases").with(as(Permission.CASE_CREATE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Paging\",\"caseType\":\"CIVIL\",\"openedOn\":\"2024-01-10\"}"))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }
}
