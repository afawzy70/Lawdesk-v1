package com.lawdesk.users;

import static com.lawdesk.security.TestActors.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Locale;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.lawdesk.platform.PostgresTestSupport;
import com.lawdesk.security.Permission;

/** LD-009.1 role administration: creating roles, renaming them and changing their permissions need ROLE_MANAGE. */
@SpringBootTest
@AutoConfigureMockMvc
class RoleAdministrationTest extends PostgresTestSupport {

    private static final String ROLES = "/api/v1/roles";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private static RequestPostProcessor roleManager() {
        return as(Permission.ROLE_VIEW, Permission.ROLE_MANAGE);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static String uniqueCode() {
        return "R" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(Locale.ROOT);
    }

    private static String newRole(String code, String... permissions) {
        String list = String.join(",", java.util.Arrays.stream(permissions).map(p -> "\"" + p + "\"").toList());
        return """
                {"code":"%s","name":"Archive clerk","permissions":[%s]}""".formatted(code, list);
    }

    private String create(String code, String... permissions) throws Exception {
        String location = mvc.perform(json(post(ROLES), newRole(code, permissions)).with(roleManager()))
                .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");
        return location.substring(location.lastIndexOf('/') + 1);
    }

    private int audited(String action, String target) {
        return jdbc.queryForObject("select count(*) from audit_event where action = ? and target_id = ?",
                Integer.class, action, target);
    }

    @Test
    void aRoleManagerCreatesARoleWithPermissions() throws Exception {
        String code = uniqueCode();

        String id = mvc.perform(json(post(ROLES), newRole(code, "CASE_VIEW", "COURT_VIEW")).with(roleManager()))
                .andExpect(status().isCreated()).andExpect(header().string("Location", Matchers.startsWith(ROLES + "/")))
                .andExpect(jsonPath("$.code").value(code)).andExpect(jsonPath("$.name").value("Archive clerk"))
                .andExpect(jsonPath("$.system").value(false)).andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.permissions", Matchers.contains("CASE_VIEW", "COURT_VIEW")))
                .andReturn().getResponse().getHeader("Location").substring(ROLES.length() + 1);

        mvc.perform(get(ROLES + "/" + id).with(as(Permission.ROLE_VIEW))).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code));
        assertThat(audited("ROLE_CREATE", id)).isEqualTo(1);
    }

    @Test
    void roleCodesAreUniqueAndValidated() throws Exception {
        String code = uniqueCode();
        create(code, "CASE_VIEW");

        mvc.perform(json(post(ROLES), newRole(code, "CASE_VIEW")).with(roleManager())).andExpect(status().isConflict());
        mvc.perform(json(post(ROLES), "{\"code\":\"lower case\",\"name\":\"\",\"permissions\":[]}").with(roleManager()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", Matchers.containsInAnyOrder("code", "name")))
                .andExpect(jsonPath("$.detail").doesNotExist());
    }

    @Test
    void unknownPermissionCodesAreRefused() throws Exception {
        mvc.perform(json(post(ROLES), newRole(uniqueCode(), "CASE_VIEW", "EVERYTHING")).with(roleManager()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("permissions"))
                .andExpect(jsonPath("$.errors[0].code").value("UnknownPermission"));
    }

    // The QAD-025 class of input: a null element is a field error, never a 500.
    @Test
    void nullPermissionCodesAreFieldErrors() throws Exception {
        String code = uniqueCode();

        mvc.perform(json(post(ROLES), "{\"code\":\"%s\",\"name\":\"x\",\"permissions\":[null]}".formatted(code))
                        .with(roleManager())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("permissions[0]"));
        assertThat(jdbc.queryForObject("select count(*) from app_role where code = ?", Integer.class, code)).isZero();
    }

    @Test
    void aRoleIsRenamedAndItsPermissionsChangedWithOptimisticLocking() throws Exception {
        String id = create(uniqueCode(), "CASE_VIEW");
        String update = """
                {"name":"Senior archive clerk","permissions":["CASE_VIEW","CASE_EDIT"],"version":0}""";

        mvc.perform(json(put(ROLES + "/" + id), update).with(roleManager())).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Senior archive clerk"))
                .andExpect(jsonPath("$.permissions", Matchers.contains("CASE_EDIT", "CASE_VIEW")))
                .andExpect(jsonPath("$.version").value(1));
        mvc.perform(json(put(ROLES + "/" + id), update).with(roleManager())).andExpect(status().isConflict());

        assertThat(audited("ROLE_UPDATE", id)).isEqualTo(1);
        assertThat(audited("ROLE_PERMISSIONS_CHANGE", id)).isEqualTo(1);
    }

    @Test
    void anUnchangedPermissionSetIsNotAuditedAsAPermissionChange() throws Exception {
        String id = create(uniqueCode(), "CASE_VIEW");

        mvc.perform(json(put(ROLES + "/" + id), "{\"name\":\"Other name\",\"permissions\":[\"CASE_VIEW\"],\"version\":0}")
                .with(roleManager())).andExpect(status().isOk());

        assertThat(audited("ROLE_UPDATE", id)).isEqualTo(1);
        assertThat(audited("ROLE_PERMISSIONS_CHANGE", id)).isZero();
    }

    @Test
    void theAdministratorRoleKeepsEveryPermission() throws Exception {
        var admin = jdbc.queryForMap("select id, version from app_role where code = 'ADMIN'");
        String body = """
                {"name":"Administrator","permissions":["CASE_VIEW"],"version":%d}""".formatted((Long) admin.get("version"));

        mvc.perform(json(put(ROLES + "/" + admin.get("id")), body).with(roleManager()))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.rule").value("ADMIN_ROLE_FIXED"));
        assertThat(jdbc.queryForObject("""
                select count(*) from role_permission rp join app_role r on r.id = rp.role_id where r.code = 'ADMIN'""",
                Integer.class)).isEqualTo(Permission.values().length);
    }

    @Test
    void rolesAreNeverDeletedThroughTheApi() throws Exception {
        String id = create(uniqueCode(), "CASE_VIEW");

        mvc.perform(delete(ROLES + "/" + id).with(csrf()).with(roleManager())).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void changingRolesNeedsRoleManage() throws Exception {
        String id = create(uniqueCode(), "CASE_VIEW");

        for (RequestPostProcessor actor : new RequestPostProcessor[] {as(Permission.ROLE_VIEW), as(Permission.USER_MANAGE)}) {
            mvc.perform(json(post(ROLES), newRole(uniqueCode(), "CASE_VIEW")).with(actor)).andExpect(status().isForbidden());
            mvc.perform(json(put(ROLES + "/" + id), "{\"name\":\"x\",\"permissions\":[],\"version\":0}").with(actor))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get(ROLES + "/" + id).with(as(Permission.CASE_VIEW))).andExpect(status().isForbidden());
        mvc.perform(get(ROLES + "/" + UUID.randomUUID()).with(as(Permission.ROLE_VIEW))).andExpect(status().isNotFound());
    }
}
