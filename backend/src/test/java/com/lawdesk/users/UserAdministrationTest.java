package com.lawdesk.users;

import static com.lawdesk.security.TestActors.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.lawdesk.platform.PostgresTestSupport;
import com.lawdesk.security.Permission;
import com.lawdesk.security.TestUsers;

/** LD-009 user administration and LD-008.6 administrative password reset, through the real security chain. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestUsers.class)
class UserAdministrationTest extends PostgresTestSupport {

    private static final String USERS = "/api/v1/users";
    private static final String TEMPORARY = "temporary passphrase 1";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TestUsers users;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder encoder;

    /** A real administrator acting through TestActors (the principal the guards compare against). */
    private RequestPostProcessor admin() {
        var created = users.create("ADMIN");
        return as(created.id(), created.username(), Permission.values());
    }

    private static String newUser(String username, String email) {
        return """
                {"username":"%s","email":"%s","displayName":"New Person","locale":"ar","roles":["LEGAL_STAFF"],
                 "temporaryPassword":"%s"}""".formatted(username, email, TEMPORARY);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private String unique() {
        return "n" + UUID.randomUUID().toString().substring(0, 8);
    }

    private UUID created(String location) {
        return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }

    @Test
    void anAdministratorCreatesAUserWhoMustChangeTheTemporaryPassword() throws Exception {
        String name = unique();
        var result = mvc.perform(json(post(USERS), newUser(name, name + "@example.invalid")).with(admin()))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.username").value(name))
                .andExpect(jsonPath("$.roles[0]").value("LEGAL_STAFF"))
                .andExpect(jsonPath("$.mustChangePassword").value(true))
                .andExpect(jsonPath("$.enabled").value(true))
                .andReturn();

        UUID id = created(result.getResponse().getHeader("Location"));
        String hash = jdbc.queryForObject("select password_hash from app_user where id = ?", String.class, id);
        assertThat(encoder.matches(TEMPORARY, hash)).isTrue();
        assertThat(result.getResponse().getContentAsString()).doesNotContain(TEMPORARY).doesNotContain("hash");
        assertThat(jdbc.queryForObject("select count(*) from audit_event where action = 'USER_CREATE' and target_id = ?",
                Integer.class, id.toString())).isEqualTo(1);
    }

    @Test
    void usernamesAndEmailsAreUniqueIgnoringCase() throws Exception {
        String name = unique();
        mvc.perform(json(post(USERS), newUser(name, name + "@example.invalid")).with(admin()))
                .andExpect(status().isCreated());

        mvc.perform(json(post(USERS), newUser(name.toUpperCase(), "other-" + name + "@example.invalid")).with(admin()))
                .andExpect(status().isConflict());
        mvc.perform(json(post(USERS), newUser("o" + name, name.toUpperCase() + "@EXAMPLE.INVALID")).with(admin()))
                .andExpect(status().isConflict());
    }

    @Test
    void fieldsAreValidatedWithoutEchoingValues() throws Exception {
        String body = """
                {"username":"No Spaces!","email":"not-an-email","displayName":"","locale":"fr","roles":["LEGAL_STAFF"],
                 "temporaryPassword":"short"}""";

        String answer = mvc.perform(json(post(USERS), body).with(admin())).andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        for (String field : List.of("username", "email", "displayName", "locale", "temporaryPassword")) {
            assertThat(answer).contains("\"field\":\"" + field + "\"");
        }
        assertThat(answer).doesNotContain("No Spaces!").doesNotContain("not-an-email").doesNotContain("short\"");
    }

    @Test
    void unknownRolesAreRefused() throws Exception {
        String name = unique();
        String body = newUser(name, name + "@example.invalid").replace("LEGAL_STAFF", "SUPERUSER");

        mvc.perform(json(post(USERS), body).with(admin())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("roles"));
    }

    // QAD-022: temporary passwords over bcrypt's 72 bytes are a policy error on create and reset, never a 500.
    @Test
    void temporaryPasswordsOverSeventyTwoBytesAreRefused() throws Exception {
        String name = unique();
        String tooLong = "Q".repeat(73);

        mvc.perform(json(post(USERS), newUser(name, name + "@example.invalid").replace(TEMPORARY, tooLong))
                        .with(admin())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("PasswordPolicy"));
        var target = users.create("LEGAL_STAFF");
        mvc.perform(json(post(USERS + "/" + target.id() + "/password-reset"),
                        "{\"temporaryPassword\":\"" + tooLong + "\"}").with(admin()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].code").value("PasswordPolicy"));
        assertThat(jdbc.queryForObject("select count(*) from app_user where username = ?", Integer.class, name))
                .isZero();
    }

    // QAD-025: a null role code is a field error, not a server error, and nothing is stored.
    @Test
    void nullRoleCodesAreFieldErrors() throws Exception {
        String name = unique();
        String body = newUser(name, name + "@example.invalid").replace("[\"LEGAL_STAFF\"]", "[null]");

        String answer = mvc.perform(json(post(USERS), body).with(admin())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("roles[0]"))
                .andReturn().getResponse().getContentAsString();
        assertThat(answer).doesNotContainIgnoringCase("exception").doesNotContain("TreeSet");
        assertThat(jdbc.queryForObject("select count(*) from app_user where username = ?", Integer.class, name))
                .isZero();

        var target = users.create("LEGAL_STAFF");
        mvc.perform(json(put(USERS + "/" + target.id() + "/roles"), "{\"roles\":[\"LEGAL_STAFF\",null]}")
                .with(admin())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("roles[1]"));
        assertThat(jdbc.queryForList("""
                select r.code from user_role ur join app_role r on r.id = ur.role_id where ur.user_id = ?""",
                String.class, target.id())).containsExactly("LEGAL_STAFF");
    }

    @Test
    void readingUsersNeedsUserViewAndChangingThemUserManage() throws Exception {
        var target = users.create("LEGAL_STAFF");
        mvc.perform(get(USERS)).andExpect(status().isUnauthorized());
        mvc.perform(get(USERS).with(as(Permission.CASE_VIEW))).andExpect(status().isForbidden());
        mvc.perform(get(USERS).with(as(Permission.USER_VIEW))).andExpect(status().isOk());
        mvc.perform(get(USERS + "/" + target.id()).with(as(Permission.USER_VIEW))).andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(target.username()))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        String name = unique();
        mvc.perform(json(post(USERS), newUser(name, name + "@example.invalid")).with(as(Permission.USER_VIEW)))
                .andExpect(status().isForbidden());
    }

    @Test
    void theListIsPagedAndSearchesNameUsernameAndEmail() throws Exception {
        String marker = unique();
        for (int i = 0; i < 3; i++) {
            String name = marker + i;
            mvc.perform(json(post(USERS), newUser(name, name + "@example.invalid")).with(admin()))
                    .andExpect(status().isCreated());
        }

        mvc.perform(get(USERS).param("q", marker.toUpperCase()).param("size", "2").with(as(Permission.USER_VIEW)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.items.length()").value(2)).andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.items[0].username").value(marker + "0"));
        mvc.perform(get(USERS).param("q", "nobody-matches-" + marker).with(as(Permission.USER_VIEW)))
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void updatesUseOptimisticLocking() throws Exception {
        var target = users.create("LEGAL_STAFF");
        String body = """
                {"email":"%s","displayName":"Renamed","locale":"en","version":0}""".formatted(target.email());

        mvc.perform(json(put(USERS + "/" + target.id()), body).with(admin())).andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Renamed")).andExpect(jsonPath("$.version").value(1));
        mvc.perform(json(put(USERS + "/" + target.id()), body).with(admin())).andExpect(status().isConflict());
    }

    @Test
    void anAdministratorDisablesAndEnablesAUser() throws Exception {
        var target = users.create("LEGAL_STAFF");

        mvc.perform(json(put(USERS + "/" + target.id() + "/enabled"), "{\"enabled\":false}").with(admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));
        mvc.perform(json(put(USERS + "/" + target.id() + "/enabled"), "{\"enabled\":true}").with(admin()))
                .andExpect(jsonPath("$.enabled").value(true));

        assertThat(jdbc.queryForList("select action from audit_event where target_id = ? order by id", String.class,
                target.id().toString())).containsSubsequence("USER_DISABLE", "USER_ENABLE");
    }

    @Test
    void nobodyChangesTheirOwnRolesOrStatus() throws Exception {
        var self = users.create("ADMIN");
        var me = as(self.id(), self.username(), Permission.values());

        mvc.perform(json(put(USERS + "/" + self.id() + "/enabled"), "{\"enabled\":false}").with(me))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.rule").value("SELF_CHANGE"));
        mvc.perform(json(put(USERS + "/" + self.id() + "/roles"), "{\"roles\":[\"LEGAL_STAFF\"]}").with(me))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.rule").value("SELF_CHANGE"));
    }

    @Test
    void onlyARoleManagerGrantsRolesThatManageUsersOrRoles() throws Exception {
        var manager = users.create(null);
        var userManager = as(manager.id(), manager.username(), Permission.USER_VIEW, Permission.USER_MANAGE);
        var target = users.create("LEGAL_STAFF");

        mvc.perform(json(put(USERS + "/" + target.id() + "/roles"), "{\"roles\":[\"ADMIN\"]}").with(userManager))
                .andExpect(status().isForbidden());
        mvc.perform(json(put(USERS + "/" + target.id() + "/roles"), "{\"roles\":[\"LEGAL_MANAGER\"]}").with(userManager))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roles[0]").value("LEGAL_MANAGER"));
        mvc.perform(json(put(USERS + "/" + target.id() + "/roles"), "{\"roles\":[\"ADMIN\"]}").with(admin()))
                .andExpect(status().isOk());
    }

    @Test
    void theLastEnabledAdministratorStaysAnAdministrator() throws Exception {
        jdbc.update("update app_user set enabled = false where id in (select user_id from user_role ur join app_role r on r.id = ur.role_id where r.code = 'ADMIN')");
        var lastAdmin = users.create("ADMIN");
        var other = users.create(null);
        var actor = as(other.id(), other.username(), Permission.values());

        mvc.perform(json(put(USERS + "/" + lastAdmin.id() + "/enabled"), "{\"enabled\":false}").with(actor))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.rule").value("LAST_ADMIN"));
        mvc.perform(json(put(USERS + "/" + lastAdmin.id() + "/roles"), "{\"roles\":[\"LEGAL_STAFF\"]}").with(actor))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.rule").value("LAST_ADMIN"));
    }

    @Test
    void twoAdministratorsCannotDisableEachOtherAtTheSameTime() throws Exception {
        jdbc.update("update app_user set enabled = false where id in (select user_id from user_role ur join app_role r on r.id = ur.role_id where r.code = 'ADMIN')");
        var first = users.create("ADMIN");
        var second = users.create("ADMIN");
        List<Callable<Integer>> attempts = new ArrayList<>();
        for (int round = 0; round < 5; round++) {
            attempts.add(() -> mvc.perform(json(put(USERS + "/" + second.id() + "/enabled"), "{\"enabled\":false}")
                    .with(as(first.id(), first.username(), Permission.values()))).andReturn().getResponse().getStatus());
            attempts.add(() -> mvc.perform(json(put(USERS + "/" + first.id() + "/enabled"), "{\"enabled\":false}")
                    .with(as(second.id(), second.username(), Permission.values()))).andReturn().getResponse().getStatus());
        }
        try (var pool = Executors.newFixedThreadPool(4)) {
            pool.invokeAll(attempts);
        }

        Integer enabledAdmins = jdbc.queryForObject("""
                select count(*) from app_user u join user_role ur on ur.user_id = u.id join app_role r on r.id = ur.role_id
                where r.code = 'ADMIN' and u.enabled""", Integer.class);
        assertThat(enabledAdmins).isEqualTo(1);
    }

    @Test
    void anAdministrativeResetSetsATemporaryPasswordThatMustBeChanged() throws Exception {
        var target = users.create("LEGAL_STAFF");

        mvc.perform(json(post(USERS + "/" + target.id() + "/password-reset"),
                        "{\"temporaryPassword\":\"" + TEMPORARY + "\"}").with(admin()))
                .andExpect(status().isNoContent());

        var row = jdbc.queryForMap("select password_hash, must_change_password from app_user where id = ?", target.id());
        assertThat(encoder.matches(TEMPORARY, (String) row.get("password_hash"))).isTrue();
        assertThat(row.get("must_change_password")).isEqualTo(true);
        mvc.perform(json(post(USERS + "/" + target.id() + "/password-reset"), "{\"temporaryPassword\":\"short\"}")
                .with(admin())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("PasswordPolicy"));
        assertThat(jdbc.queryForObject("select count(*) from audit_event where action = 'PASSWORD_RESET' and target_id = ?",
                Integer.class, target.id().toString())).isEqualTo(1);
    }

    @Test
    void anUnknownUserIs404() throws Exception {
        mvc.perform(get(USERS + "/" + UUID.randomUUID()).with(as(Permission.USER_VIEW))).andExpect(status().isNotFound());
    }
}
