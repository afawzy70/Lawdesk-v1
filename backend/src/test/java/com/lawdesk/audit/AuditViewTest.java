package com.lawdesk.audit;

import static com.lawdesk.security.TestActors.as;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import com.lawdesk.platform.PostgresTestSupport;
import com.lawdesk.security.Permission;

/** LD-011.5: the audit trail is read page by page, newest first, filtered by action, actor, outcome and time. */
@SpringBootTest
@AutoConfigureMockMvc
class AuditViewTest extends PostgresTestSupport {

    private static final String AUDIT = "/api/v1/audit";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AuditLog audit;

    private static String unique() {
        return "a" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void auditorsReadTheTrailOfOneActorNewestFirst() throws Exception {
        String actor = unique();
        UUID actorId = UUID.randomUUID();
        audit.record("LOGIN", AuditLog.Outcome.SUCCESS, actorId, actor, "USER", actorId.toString(), null);
        audit.record("ACCESS_DENIED", AuditLog.Outcome.DENIED, actorId, actor, "HTTP", "GET /api/v1/users", null);
        audit.record("LOGOUT", AuditLog.Outcome.SUCCESS, actorId, actor, "USER", actorId.toString(), null);

        mvc.perform(get(AUDIT).param("actor", actor.toUpperCase()).with(as(Permission.AUDIT_VIEW)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.items[*].action", Matchers.contains("LOGOUT", "ACCESS_DENIED", "LOGIN")))
                .andExpect(jsonPath("$.items[1].outcome").value("DENIED"))
                .andExpect(jsonPath("$.items[1].targetType").value("HTTP"))
                .andExpect(jsonPath("$.items[1].targetId").value("GET /api/v1/users"))
                .andExpect(jsonPath("$.items[0].actorId").value(actorId.toString()))
                .andExpect(jsonPath("$.items[0].actorUsername").value(actor))
                .andExpect(jsonPath("$.items[0].occurredAt").isString())
                .andExpect(jsonPath("$.items[0].id").isNumber());
    }

    @Test
    void filtersCombineAndThePageSizeIsHonoured() throws Exception {
        String actor = unique();
        Instant before = Instant.now();
        for (int i = 0; i < 3; i++) {
            audit.record("LOGIN", AuditLog.Outcome.FAILURE, null, actor, "USER", null, "BAD_CREDENTIALS");
        }
        audit.record("LOGIN", AuditLog.Outcome.SUCCESS, null, actor, "USER", null, null);

        mvc.perform(get(AUDIT).param("actor", actor).param("action", "LOGIN").param("outcome", "FAILURE")
                        .param("from", before.toString()).param("size", "2").with(as(Permission.AUDIT_VIEW)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].reason").value("BAD_CREDENTIALS"));
        mvc.perform(get(AUDIT).param("actor", actor).param("to", before.toString()).with(as(Permission.AUDIT_VIEW)))
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void invalidFiltersAreBadRequests() throws Exception {
        mvc.perform(get(AUDIT).param("outcome", "MAYBE").with(as(Permission.AUDIT_VIEW)))
                .andExpect(status().isBadRequest());
        mvc.perform(get(AUDIT).param("from", "yesterday").with(as(Permission.AUDIT_VIEW)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void readingTheTrailNeedsAuditView() throws Exception {
        mvc.perform(get(AUDIT)).andExpect(status().isUnauthorized());
        mvc.perform(get(AUDIT).with(as(Permission.USER_MANAGE, Permission.ROLE_MANAGE))).andExpect(status().isForbidden());
    }
}
