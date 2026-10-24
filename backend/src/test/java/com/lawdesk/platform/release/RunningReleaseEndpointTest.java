package com.lawdesk.platform.release;

import static com.lawdesk.security.TestActors.as;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import com.lawdesk.platform.PostgresTestSupport;
import com.lawdesk.security.Permission;

/**
 * QA could not tell which backend commit runs on UAT: /actuator/info is never forwarded by the proxies (on purpose).
 * {@code GET /api/v1/release} gives signed-in users this process's release, the deployed commit, and nothing else.
 */
@SpringBootTest(properties = "lawdesk.release=0123456789abcdef0123456789abcdef01234567")
@AutoConfigureMockMvc
class RunningReleaseEndpointTest extends PostgresTestSupport {

    @Autowired
    MockMvc mvc;

    @Test
    void aSignedInUserSeesTheRunningCommitOnly() throws Exception {
        mvc.perform(get("/api/v1/release").with(as(Permission.CASE_VIEW))).andExpect(status().isOk())
                .andExpect(jsonPath("$.release").value("0123456789abcdef0123456789abcdef01234567"))
                .andExpect(jsonPath("$.*", Matchers.hasSize(1)));
    }

    @Test
    void itNeedsNoPermissionButASession() throws Exception {
        mvc.perform(get("/api/v1/release").with(as())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/release")).andExpect(status().isUnauthorized());
    }
}
