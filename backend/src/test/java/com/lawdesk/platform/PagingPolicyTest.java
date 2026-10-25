package com.lawdesk.platform;

import static com.lawdesk.security.TestActors.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.jayway.jsonpath.JsonPath;
import com.lawdesk.security.Permission;

/**
 * Owner decision 2026-10-25 (QAD-029, architecture/CONTRACTS.md §4): an invalid {@code page} or {@code size} is
 * refused with 400 on every list, a missing or blank one takes the default (page 0, size 20), and a {@code size} above
 * 100 is capped. Until then the lists built on Spring Data paging silently fell back to the defaults whereas the case
 * list and timeline answered 400. A caller without the permission is still answered 403 first (QAD-026).
 */
@SpringBootTest
@AutoConfigureMockMvc
class PagingPolicyTest extends PostgresTestSupport {

    private static final String[][] INVALID = {{"page", "-1"}, {"page", "bad"}, {"page", "1.5"},
            {"page", "2147483648"}, {"size", "-1"}, {"size", "0"}, {"size", "bad"}, {"size", "2147483648"}};

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mappings;

    @AfterEach
    void removeTheCase() {
        OperationsTestData.deleteAll(jdbc);
    }

    record Endpoint(String path, Permission permission) {
    }

    private Endpoint[] lists(UUID caseId) {
        return new Endpoint[] {new Endpoint("/api/v1/hearings", Permission.HEARING_VIEW),
                new Endpoint("/api/v1/tasks", Permission.TASK_VIEW),
                new Endpoint("/api/v1/experts", Permission.EXPERT_VIEW),
                new Endpoint("/api/v1/documents", Permission.DOCUMENT_VIEW),
                new Endpoint("/api/v1/courts", Permission.COURT_VIEW),
                new Endpoint("/api/v1/audit", Permission.AUDIT_VIEW),
                new Endpoint("/api/v1/cases/" + caseId + "/hearings", Permission.HEARING_VIEW),
                new Endpoint("/api/v1/cases/" + caseId + "/notes", Permission.CASE_VIEW)};
    }

    @Test
    void invalidPagingIsRefusedWith400AndNamesTheParameter() throws Exception {
        UUID caseId = createCase();
        for (Endpoint endpoint : lists(caseId)) {
            for (String[] parameter : INVALID) {
                mvc.perform(get(endpoint.path()).param(parameter[0], parameter[1]).with(as(endpoint.permission())))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.errors[0].field").value(parameter[0]));
            }
        }
    }

    @Test
    void missingOrBlankPagingTakesTheDefaultsAndAHugeSizeIsCapped() throws Exception {
        UUID caseId = createCase();
        for (Endpoint endpoint : lists(caseId)) {
            mvc.perform(get(endpoint.path()).with(as(endpoint.permission()))).andExpect(status().isOk())
                    .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20));
            mvc.perform(get(endpoint.path()).param("page", "").param("size", "").with(as(endpoint.permission())))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(20));
            mvc.perform(get(endpoint.path()).param("size", "500").with(as(endpoint.permission())))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.size").value(100));
            mvc.perform(get(endpoint.path()).param("page", "0").param("size", "1").with(as(endpoint.permission())))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.size").value(1));
        }
    }

    @Test
    void aCallerWithoutThePermissionStillGets403BeforeAnyParameterRule() throws Exception {
        UUID caseId = createCase();
        for (Endpoint endpoint : lists(caseId)) {
            mvc.perform(get(endpoint.path()).param("page", "-1").with(as())).andExpect(status().isForbidden());
        }
    }

    @Test
    void everyHandlerThatTakesAPageableIsCovered() throws Exception {
        // Discovered, not listed: a future list built on Spring Data paging is validated without anyone remembering to.
        List<String> checked = new ArrayList<>();
        for (var entry : mappings.getHandlerMethods().entrySet()) {
            boolean paged = Arrays.stream(entry.getValue().getMethodParameters())
                    .anyMatch(p -> Pageable.class.isAssignableFrom(p.getParameterType()));
            var methods = entry.getKey().getMethodsCondition().getMethods();
            if (!paged || !(methods.isEmpty() || methods.contains(RequestMethod.GET))) {
                continue;
            }
            String pattern = entry.getKey().getPathPatternsCondition().getPatterns().iterator().next().getPatternString();
            String path = pattern.replaceAll("\\{[^}]+}", UUID.randomUUID().toString());
            mvc.perform(get(path).param("page", "-1").with(as(Permission.values()))).andExpect(status().isBadRequest());
            checked.add(path);
        }
        assertThat(checked.size()).as("paged GET handlers found: %s", checked).isGreaterThanOrEqualTo(10);
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
