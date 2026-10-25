package com.lawdesk.api;

import static com.lawdesk.security.TestActors.as;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.lawdesk.platform.PostgresTestSupport;
import com.lawdesk.security.Permission;

/**
 * The shared API contract every workstream builds on (architecture/CONTRACTS.md §3–§4), through the real security
 * filter chain with method security.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ContractTestController.class)
class ApiContractTest extends PostgresTestSupport {

    private static final String BASE = "/api/v1/contract-test";
    private static final MediaType PROBLEM = MediaType.APPLICATION_PROBLEM_JSON;

    @Autowired
    private MockMvc mvc;

    @Test
    void withoutASessionTheAnswerIs401() throws Exception {
        mvc.perform(get(BASE + "/missing")).andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    void withoutThePermissionTheAnswerIs403() throws Exception {
        mvc.perform(get(BASE + "/missing").with(as(Permission.CASE_CREATE))).andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void validationFailuresListFieldsAndConstraintsWithoutEchoingValues() throws Exception {
        mvc.perform(post(BASE + "/validated").with(as(Permission.CASE_CREATE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"far too long a name\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].code").value("Size"))
                .andExpect(content().string(Matchers.not(Matchers.containsString("far too long"))));
        mvc.perform(post(BASE + "/validated").with(as(Permission.CASE_CREATE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("NotBlank"));
    }

    @Test
    void malformedJsonIsABadRequestProblem() throws Exception {
        mvc.perform(post(BASE + "/validated").with(as(Permission.CASE_CREATE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    void unsafeRequestsNeedTheCsrfToken() throws Exception {
        mvc.perform(post(BASE + "/validated").with(as(Permission.CASE_CREATE))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"ok\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void domainExceptionsMapToTheirStatus() throws Exception {
        mvc.perform(get(BASE + "/missing").with(as(Permission.CASE_VIEW))).andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
        mvc.perform(get(BASE + "/conflict").with(as(Permission.CASE_VIEW))).andExpect(status().isConflict());
        mvc.perform(get(BASE + "/stale").with(as(Permission.CASE_VIEW))).andExpect(status().isConflict());
        mvc.perform(get(BASE + "/rule").with(as(Permission.CASE_VIEW))).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("APPEAL_REQUIRES_FIRST_INSTANCE_JUDGMENT"))
                .andExpect(content().string(Matchers.not(Matchers.containsString("secret"))));
    }

    @Test
    void malformedParametersUnknownPathsAndWrongMethodsAreClientErrors() throws Exception {
        // Asked for by workstreams B and C: these answered 500 through the catch-all.
        mvc.perform(get(BASE + "/typed/not-a-uuid").with(as(Permission.CASE_VIEW))).andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(content().string(Matchers.not(Matchers.containsString("MethodArgumentTypeMismatch"))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("UUID"))));
        mvc.perform(get("/api/v1/no-such-endpoint").with(as(Permission.CASE_VIEW))).andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
        mvc.perform(post(BASE + "/missing").with(as(Permission.CASE_VIEW)).with(csrf()))
                .andExpect(status().isMethodNotAllowed()).andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    void unexpectedErrorsRevealNothing() throws Exception {
        mvc.perform(get(BASE + "/boom").with(as(Permission.CASE_VIEW))).andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(content().string(Matchers.not(Matchers.containsString("secret"))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("IllegalStateException"))));
    }

    @Test
    void theActingUserComesFromTheSession() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(get(BASE + "/me").with(as(id, "maha", Permission.CASE_VIEW))).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.username").value("maha"));
    }

    @Test
    void pagesHaveTheSharedShapeAndRefuseMoreThanOneHundred() throws Exception {
        mvc.perform(get(BASE + "/page").param("page", "1").param("size", "100").with(as(Permission.CASE_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.totalItems").value(250))
                .andExpect(jsonPath("$.totalPages").value(3));
        mvc.perform(get(BASE + "/page").param("size", "500").with(as(Permission.CASE_VIEW)))
                .andExpect(status().isBadRequest());
        mvc.perform(get(BASE + "/page").with(as(Permission.CASE_VIEW)))
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20));
    }
}
