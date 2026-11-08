package com.lawdesk.org;

import static com.lawdesk.security.TestActors.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;
import com.lawdesk.platform.PostgresTestSupport;
import com.lawdesk.security.Permission;

/** LD-050: the organisation tree is managed by ORG_MANAGE, keeps its shape, and never loses a unit. */
@SpringBootTest
@AutoConfigureMockMvc
class OrgUnitApiTest extends PostgresTestSupport {

    private static final RequestPostProcessor MANAGER = as(Permission.ORG_MANAGE, Permission.CASE_VIEW);

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void clean() {
        jdbc.update("update app_user set org_unit_id = null where org_unit_id is not null");
        jdbc.update("delete from org_unit");
        jdbc.update("delete from app_user where username like 'org%'");
    }

    private UUID create(String json) throws Exception {
        String body = mvc.perform(post("/api/v1/org-units").with(MANAGER).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    private static String unit(String tree, UUID parent, String level, String name) {
        return "{\"tree\":\"" + tree + "\",\"parentId\":" + (parent == null ? "null" : "\"" + parent + "\"")
                + ",\"level\":\"" + level + "\",\"nameAr\":\"" + name + "\"}";
    }

    @Test
    void buildsATreeAndKeepsItsShape() throws Exception {
        UUID department = create(unit("LEGAL", null, "DEPARTMENT", "الإدارة القانونية"));
        UUID section = create(unit("LEGAL", department, "SECTION", "قسم القضايا"));
        mvc.perform(get("/api/v1/org-units?tree=LEGAL").with(MANAGER)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // A section cannot hold a department, and trees do not mix.
        mvc.perform(post("/api/v1/org-units").with(MANAGER).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(unit("LEGAL", section, "DEPARTMENT", "x"))).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("ORG_LEVEL_NOT_BELOW_PARENT"));
        mvc.perform(post("/api/v1/org-units").with(MANAGER).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(unit("AUTHORITY", department, "SECTION", "y"))).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("ORG_PARENT_TREE_MISMATCH"));
        // A unit cannot move below itself.
        mvc.perform(put("/api/v1/org-units/" + department).with(MANAGER).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"parentId\":\"" + section + "\",\"nameAr\":\"الإدارة القانونية\",\"active\":true,\"version\":0}"))
                .andExpect(status().isUnprocessableContent());
        // The same name twice under one parent is a conflict.
        mvc.perform(post("/api/v1/org-units").with(MANAGER).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(unit("LEGAL", department, "SECTION", "قسم القضايا"))).andExpect(status().isConflict());
    }

    @Test
    void renamingAndDeactivatingKeepTheUnitAndAreAudited() throws Exception {
        UUID department = create(unit("AUTHORITY", null, "SECTOR", "قطاع الخدمات المساندة"));
        mvc.perform(put("/api/v1/org-units/" + department).with(MANAGER).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nameAr\":\"قطاع الخدمات\",\"active\":false,\"version\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.version").value(1));
        mvc.perform(get("/api/v1/org-units?tree=AUTHORITY").with(MANAGER)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/org-units?tree=AUTHORITY&includeInactive=true").with(MANAGER))
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(put("/api/v1/org-units/" + department).with(MANAGER).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nameAr\":\"stale\",\"active\":true,\"version\":0}")).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject(
                "select count(*) from audit_event where action in ('ORG_UNIT_CREATE', 'ORG_UNIT_UPDATE') and target_id = ?",
                Integer.class, department.toString()))
                .isEqualTo(2);
    }

    @Test
    void onlyManagersChangeTheTreeAndUsersJoinLegalUnitsOnly() throws Exception {
        mvc.perform(post("/api/v1/org-units").with(as(Permission.CASE_VIEW)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(unit("LEGAL", null, "DEPARTMENT", "z")))
                .andExpect(status().isForbidden());
        UUID legal = create(unit("LEGAL", null, "DEPARTMENT", "الإدارة القانونية"));
        UUID authority = create(unit("AUTHORITY", null, "SECTOR", "قطاع"));
        UUID user = jdbc.queryForObject("""
                insert into app_user (username, email, display_name, password_hash, created_by, updated_by)
                values ('org' || substr(gen_random_uuid()::text, 1, 8), gen_random_uuid() || '@example.invalid', 'U', 'x',
                        gen_random_uuid(), gen_random_uuid()) returning id""", UUID.class);
        mvc.perform(put("/api/v1/users/" + user + "/org-unit").with(MANAGER).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"orgUnitId\":\"" + authority + "\"}"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.rule").value("ORG_NOT_LEGAL_UNIT"));
        mvc.perform(put("/api/v1/users/" + user + "/org-unit").with(MANAGER).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"orgUnitId\":\"" + legal + "\"}"))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select org_unit_id from app_user where id = ?", UUID.class, user)).isEqualTo(legal);
        mvc.perform(put("/api/v1/users/" + user + "/org-unit").with(MANAGER).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"orgUnitId\":null}")).andExpect(status().isNoContent());
    }
}
