package com.lawdesk.cases;

import static com.lawdesk.security.TestActors.as;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;
import com.lawdesk.platform.OperationsTestData;
import com.lawdesk.platform.PostgresTestSupport;
import com.lawdesk.security.Permission;

/** Shared setup of the LD-045 government intake tests: real users with department roles, courts, and the API. */
@SpringBootTest
@AutoConfigureMockMvc
abstract class GovernmentIntakeTestSupport extends PostgresTestSupport {

    static final String INTAKE = "/api/v1/cases/intake";

    /** Holds what registering a case needs (the legal staff role). */
    static final Permission[] REGISTRAR = {Permission.CASE_VIEW, Permission.CASE_CREATE, Permission.CASE_EDIT,
        Permission.PARTY_MANAGE, Permission.PROCEEDING_MANAGE, Permission.PROCEEDING_VIEW, Permission.COURT_VIEW};

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void removeData() {
        OperationsTestData.deleteAll(jdbc);
        jdbc.update("delete from party");
        jdbc.update("delete from user_role where user_id in (select id from app_user where username like 'gi%')");
        jdbc.update("delete from app_user where username like 'gi%'");
    }

    /** A user in the legal department (role LEGAL_STAFF or LEGAL_MANAGER), enabled unless said otherwise. */
    UUID member(String displayName, String role, boolean enabled) {
        String username = "gi" + UUID.randomUUID().toString().substring(0, 8);
        UUID id = jdbc.queryForObject("""
                insert into app_user (username, email, display_name, password_hash, enabled, created_by, updated_by)
                values (?, ?, ?, 'x', ?, gen_random_uuid(), gen_random_uuid()) returning id""", UUID.class,
                username, username + "@example.invalid", displayName, enabled);
        if (role != null) {
            jdbc.update("insert into user_role (user_id, role_id) select ?, id from app_role where code = ?", id, role);
        }
        return id;
    }

    UUID member(String displayName) {
        return member(displayName, "LEGAL_STAFF", true);
    }

    UUID court(String code, String level, boolean active) {
        return jdbc.queryForObject("""
                insert into court (code, name_ar, name_en, level, active, created_by, updated_by)
                values (?, 'محكمة ' || ?, 'Court ' || ?, ?, ?, gen_random_uuid(), gen_random_uuid()) returning id""",
                UUID.class, code, code, code, level, active);
    }

    UUID firstInstanceCourt() {
        return court("FI-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(), "FIRST_INSTANCE", true);
    }

    static RequestPostProcessor registrar(UUID id) {
        return as(id, "registrar", REGISTRAR);
    }

    /** A valid registration; {@code extra} is spliced in as additional members of the JSON object. */
    static String intake(UUID court, UUID primary, String extra) {
        return """
                {"automatedNo":"000123","title":"Ministry v. Nile Trading","caseType":"COMMERCIAL",
                 "subject":"Breach of supply contract","openedOn":"2026-01-10",
                 "firstInstance":{"courtId":"%s","courtCaseNumber":"4471","courtCaseYear":2026,"filingDate":"2026-01-12"},
                 "authorityPosition":"PLAINTIFF","primaryMemberId":"%s","financialImpact":"NO"%s}"""
                .formatted(court, primary, extra == null || extra.isEmpty() ? "" : "," + extra);
    }

    MockHttpServletRequestBuilder register(RequestPostProcessor user, String json) {
        return post(INTAKE).with(user).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json);
    }

    UUID registered(RequestPostProcessor user, String json) throws Exception {
        MvcResult result = mvc.perform(register(user, json)).andReturn();
        if (result.getResponse().getStatus() != 201) {
            throw new AssertionError("registration answered " + result.getResponse().getStatus() + ": "
                    + result.getResponse().getContentAsString());
        }
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    MockHttpServletRequestBuilder getCase(RequestPostProcessor user, UUID id) {
        return get("/api/v1/cases/" + id).with(user);
    }

    MockHttpServletRequestBuilder putCase(RequestPostProcessor user, UUID id, String json) {
        return put("/api/v1/cases/" + id).with(user).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json);
    }

    MockHttpServletRequestBuilder assign(RequestPostProcessor user, UUID id, String json) {
        return put("/api/v1/cases/" + id + "/assignment").with(user).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json);
    }

    int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }
}
