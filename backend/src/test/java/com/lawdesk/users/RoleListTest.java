package com.lawdesk.users;

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

/** LD-009.1: the seeded roles and their permissions, for role administrators and for assigning roles to users. */
@SpringBootTest
@AutoConfigureMockMvc
class RoleListTest extends PostgresTestSupport {

    @Autowired
    private MockMvc mvc;

    @Test
    void roleViewersAndUserManagersSeeTheRolesWithTheirPermissions() throws Exception {
        for (Permission permission : new Permission[] {Permission.ROLE_VIEW, Permission.USER_MANAGE}) {
            mvc.perform(get("/api/v1/roles").with(as(permission))).andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].code", Matchers.containsInRelativeOrder("ADMIN", "LEGAL_MANAGER", "LEGAL_STAFF")))
                    .andExpect(jsonPath("$[?(@.code == 'LEGAL_STAFF')].permissions[*]", Matchers.hasItem("CASE_CREATE")))
                    .andExpect(jsonPath("$[?(@.code == 'LEGAL_STAFF')].permissions[*]", Matchers.not(Matchers.hasItem("USER_MANAGE"))))
                    .andExpect(jsonPath("$[?(@.code == 'ADMIN')].system", Matchers.hasItem(true)));
        }
    }

    @Test
    void othersMayNotListRoles() throws Exception {
        mvc.perform(get("/api/v1/roles").with(as(Permission.USER_VIEW))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/roles")).andExpect(status().isUnauthorized());
    }
}
