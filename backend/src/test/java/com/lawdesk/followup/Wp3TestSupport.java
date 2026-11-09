package com.lawdesk.followup;

import static com.lawdesk.security.TestActors.as;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
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

import tools.jackson.databind.json.JsonMapper;

/**
 * Shared setup of the WP3 tests (LD-053): real users, cases inserted with their members, requests through MockMvc. A
 * user without a scope sees a case only as a member; {@link #viewer} sees every case but changes none.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class Wp3TestSupport extends PostgresTestSupport {

    static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcTemplate jdbc;

    private int cases;

    @AfterEach
    void removeWp3Data() {
        OperationsTestData.deleteAll(jdbc);
        jdbc.update("delete from user_role where user_id in (select id from app_user where username like 'wp3%')");
        jdbc.update("delete from app_user where username like 'wp3%'");
    }

    public static LocalDate today() {
        return LocalDate.now(ZoneId.of("Asia/Kuwait"));
    }

    protected UUID user(String displayName) {
        String username = "wp3" + UUID.randomUUID().toString().substring(0, 8);
        return jdbc.queryForObject("""
                insert into app_user (username, email, display_name, password_hash, enabled, created_by, updated_by)
                values (?, ?, ?, 'x', true, gen_random_uuid(), gen_random_uuid()) returning id""", UUID.class,
                username, username + "@example.invalid", displayName);
    }

    /** A case whose specialist is {@code primary} (and alternate {@code alternate}); nobody else sees it without a scope. */
    protected UUID legalCase(UUID primary, UUID alternate) {
        String reference = "TST-" + (++cases) + "-" + UUID.randomUUID().toString().substring(0, 6);
        return jdbc.queryForObject("""
                insert into legal_case (reference_no, title, case_type, opened_on, status, primary_member_id,
                                        alternate_member_id, created_by, updated_by)
                values (?, ?, 'ADMINISTRATIVE', '2026-01-10', 'OPEN', ?, ?, ?, ?) returning id""", UUID.class,
                reference, "Case " + reference, primary, alternate, primary, primary);
    }

    /** Signs in as the user with the permissions plus the department scope and the right to edit in it. */
    protected RequestPostProcessor staff(UUID id, Permission... permissions) {
        return as(id, "u" + id.toString().substring(0, 6), permissions);
    }

    /** Sees every case (scope ALL) but may change none: no CASE_EDIT_IN_SCOPE, not a member. */
    protected RequestPostProcessor viewer(UUID id, Permission... permissions) {
        return com.lawdesk.security.TestActors.scoped(id, "v" + id.toString().substring(0, 6),
                java.util.List.of("ALL"), permissions);
    }

    /** Sees nothing it is not a member of. */
    protected RequestPostProcessor outsider(UUID id, Permission... permissions) {
        return com.lawdesk.security.TestActors.scoped(id, "o" + id.toString().substring(0, 6), java.util.List.of(),
                permissions);
    }

    protected static String json(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return JSON.writeValueAsString(map);
    }

    protected MockHttpServletRequestBuilder postJson(RequestPostProcessor actor, String path, String body) {
        return post(path).with(actor).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    protected MockHttpServletRequestBuilder putJson(RequestPostProcessor actor, String path, String body) {
        return put(path).with(actor).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    protected MockHttpServletRequestBuilder getJson(RequestPostProcessor actor, String path) {
        return get(path).with(actor);
    }

    protected String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }

    protected <T> T read(MvcResult result, String path) throws Exception {
        return JsonPath.read(body(result), path);
    }

    protected UUID idOf(MvcResult result) throws Exception {
        return UUID.fromString(read(result, "$.id"));
    }

    protected int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    protected UUID authorityUnit(String name) {
        return jdbc.queryForObject("""
                insert into org_unit (tree, parent_id, level, name_ar, created_by, updated_by)
                values ('AUTHORITY', null, 'SECTOR', ?, gen_random_uuid(), gen_random_uuid()) returning id""",
                UUID.class, name);
    }
}
