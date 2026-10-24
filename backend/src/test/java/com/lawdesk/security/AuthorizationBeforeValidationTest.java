package com.lawdesk.security;

import static com.lawdesk.security.TestActors.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.lawdesk.platform.PostgresTestSupport;

/**
 * QAD-026 / LD-010.2: a caller without the endpoint's permission gets 403 whatever the request holds. Validation,
 * JSON parsing and path conversion only answer callers who may use the endpoint, so a refused caller learns nothing
 * about field names or rules.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthorizationBeforeValidationTest extends PostgresTestSupport {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void anInvalidBodyFromACallerWithoutThePermissionIsForbidden() throws Exception {
        String body = mvc.perform(post("/api/v1/courts").with(csrf()).with(as(Permission.COURT_VIEW))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden()).andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("errors").doesNotContain("field");
    }

    @Test
    void unreadableJsonAndBadPathValuesFromACallerWithoutThePermissionAreForbidden() throws Exception {
        mvc.perform(post("/api/v1/courts").with(csrf()).with(as(Permission.CASE_VIEW))
                .contentType(MediaType.APPLICATION_JSON).content("{not json")).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/courts/not-a-uuid").with(csrf()).with(as(Permission.COURT_VIEW))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/courts/not-a-uuid").with(as(Permission.CASE_VIEW))).andExpect(status().isForbidden());
    }

    @Test
    void callersWithThePermissionStillGetValidationErrors() throws Exception {
        mvc.perform(post("/api/v1/courts").with(csrf()).with(as(Permission.COURT_MANAGE))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors").isArray());
        mvc.perform(get("/api/v1/courts/not-a-uuid").with(as(Permission.COURT_VIEW))).andExpect(status().isBadRequest());
    }

    @Test
    void theRefusalIsAuditedAsADeniedAccess() throws Exception {
        UUID actor = UUID.randomUUID();
        mvc.perform(post("/api/v1/courts").with(csrf()).with(as(actor, "refused-" + actor, Permission.COURT_VIEW))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());

        assertThat(jdbc.queryForObject("""
                select count(*) from audit_event where action = 'ACCESS_DENIED' and actor_id = ?
                and target_id = 'POST /api/v1/courts'""", Integer.class, actor)).isEqualTo(1);
    }
}
