package com.lawdesk.pleadings;

import static com.lawdesk.security.TestActors.as;
import static com.lawdesk.security.TestActors.scoped;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;
import com.lawdesk.platform.OperationsTestData;
import com.lawdesk.platform.PleadingsTestData;
import com.lawdesk.platform.PostgresTestSupport;
import com.lawdesk.security.Permission;

/**
 * Shared setup of the LD-051 tests (claims, judgments, defences, facts, evidence). Records are inserted straight into the
 * database so each test controls exactly which case, proceedings and members it has; everything the module itself does
 * goes through the real API.
 */
@SpringBootTest
@AutoConfigureMockMvc
abstract class PleadingsTestSupport extends PostgresTestSupport {

    static final Permission[] ALL_PLEADINGS = {Permission.CASE_VIEW, Permission.CLAIM_VIEW, Permission.CLAIM_MANAGE,
        Permission.DEFENSE_VIEW, Permission.DEFENSE_MANAGE, Permission.EVIDENCE_VIEW, Permission.EVIDENCE_MANAGE,
        Permission.JUDGMENT_VIEW, Permission.JUDGMENT_MANAGE, Permission.PROCEEDING_VIEW, Permission.DOCUMENT_VIEW,
        Permission.TASK_VIEW, Permission.REPORT_VIEW, Permission.LOOKUP_MANAGE};
    static final UUID USER = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void removePleadings() {
        OperationsTestData.deleteAll(jdbc);
        PleadingsTestData.deleteTestLookups(jdbc);
        jdbc.update("delete from party");
        jdbc.update("delete from user_role where user_id in (select id from app_user where username like 'pl%')");
        jdbc.update("delete from app_user where username like 'pl%'");
    }

    /** A department-wide user who may change cases in scope and holds every permission of the module. */
    static RequestPostProcessor everything() {
        return as(USER, "pleader", ALL_PLEADINGS);
    }

    static RequestPostProcessor everything(UUID id) {
        return as(id, "pleader", ALL_PLEADINGS);
    }

    static RequestPostProcessor with(Permission... permissions) {
        return as(USER, "pleader", permissions);
    }

    /** Sees every non-restricted case (scope ALL) but cannot change any: not a member, no edit-in-scope. */
    static RequestPostProcessor viewerOf(UUID id) {
        return scoped(id, "viewer", List.of("ALL"), ALL_PLEADINGS);
    }

    /** Holds the permissions but no scope: sees only the cases they are a member of. */
    static RequestPostProcessor outsider(UUID id) {
        return scoped(id, "outsider", List.of(), ALL_PLEADINGS);
    }

    /** A member's own view: no scope, so only a case naming them as its specialist is theirs. */
    static RequestPostProcessor member(UUID id) {
        return scoped(id, "member", List.of(), ALL_PLEADINGS);
    }

    // ---- fixtures ---------------------------------------------------------------------------------------------

    UUID user(String displayName) {
        String username = "pl" + UUID.randomUUID().toString().substring(0, 8);
        return jdbc.queryForObject("""
                insert into app_user (username, email, display_name, password_hash, enabled, created_by, updated_by)
                values (?, ?, ?, 'x', true, gen_random_uuid(), gen_random_uuid()) returning id""", UUID.class, username,
                username + "@example.invalid", displayName);
    }

    UUID newCase() {
        return newCase(null, "COMMERCIAL");
    }

    UUID newCase(UUID primaryMember, String type) {
        int n = SEQUENCE.incrementAndGet();
        return jdbc.queryForObject("""
                insert into legal_case (reference_no, title, case_type, opened_on, status, primary_member_id, created_by,
                                        updated_by)
                values (?, ?, ?, '2026-01-10', 'OPEN', ?, ?, ?) returning id""", UUID.class,
                "PL-" + System.nanoTime() + "-" + n, "Pleadings case " + n, type, primaryMember, USER, USER);
    }

    UUID court(String level) {
        String code = "PL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return jdbc.queryForObject("""
                insert into court (code, name_ar, name_en, level, active, created_by, updated_by)
                values (?, 'محكمة ' || ?, 'Court ' || ?, ?, true, gen_random_uuid(), gen_random_uuid()) returning id""",
                UUID.class, code, code, code, level);
    }

    /** A first-instance proceeding of the case. */
    UUID firstInstance(UUID caseId) {
        return proceeding(caseId, "FIRST_INSTANCE", null, "2026-01-12");
    }

    UUID proceeding(UUID caseId, String stage, UUID parent, String filingDate) {
        String level = stage;
        return jdbc.queryForObject("""
                insert into proceeding (case_id, stage, court_id, court_case_number, court_case_year, filing_date,
                                        parent_proceeding_id, created_by, updated_by)
                values (?, ?, ?, ?, 2026, ?::date, ?, ?, ?) returning id""", UUID.class, caseId, stage, court(level),
                String.valueOf(1000 + SEQUENCE.incrementAndGet()), filingDate, parent, USER, USER);
    }

    UUID judgment(UUID proceedingId, String date) {
        return jdbc.queryForObject("""
                insert into judgment (proceeding_id, judgment_date, outcome, final, created_by, updated_by)
                values (?, ?::date, 'PARTIAL', false, ?, ?) returning id""", UUID.class, proceedingId, date, USER, USER);
    }

    UUID document(UUID caseId, String title) {
        return jdbc.queryForObject("""
                insert into case_document (case_id, title, file_name, size_bytes, sha256, created_by)
                values (?, ?, 'f.pdf', 10, repeat('a', 64), ?) returning id""", UUID.class, caseId, title, USER);
    }

    UUID party(UUID caseId, String name) {
        UUID party = jdbc.queryForObject("""
                insert into party (kind, name, created_by, updated_by) values ('PERSON', ?, ?, ?) returning id""",
                UUID.class, name, USER, USER);
        jdbc.update("insert into case_party (case_id, party_id, role, created_by) values (?, ?, 'CLIENT', ?)", caseId,
                party, USER);
        return party;
    }

    UUID task(UUID caseId, String title) {
        return jdbc.queryForObject("""
                insert into task (case_id, title, assignee_id, created_by, updated_by) values (?, ?, ?, ?, ?)
                returning id""", UUID.class, caseId, title, USER, USER, USER);
    }

    // ---- requests ---------------------------------------------------------------------------------------------

    ResultActions get(RequestPostProcessor user, String path) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get(path).with(user));
    }

    ResultActions post(RequestPostProcessor user, String path, String json) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post(path).with(user).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    ResultActions put(RequestPostProcessor user, String path, String json) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.put(path).with(user).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    ResultActions delete(RequestPostProcessor user, String path) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.delete(path).with(user).with(csrf()));
    }

    /** POSTs and returns the new record's id, failing unless the answer is 201. */
    UUID created(RequestPostProcessor user, String path, String json) throws Exception {
        MvcResult result = post(user, path, json).andReturn();
        if (result.getResponse().getStatus() != 201) {
            throw new AssertionError(path + " answered " + result.getResponse().getStatus() + ": "
                    + result.getResponse().getContentAsString());
        }
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    String body(ResultActions actions) throws Exception {
        return actions.andReturn().getResponse().getContentAsString();
    }

    int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    UUID claim(UUID caseId, String text) throws Exception {
        return created(everything(), "/api/v1/cases/" + caseId + "/claims", """
                {"text":"%s","kind":"PRIMARY"}""".formatted(text));
    }

    static String json(Object... pairs) {
        StringBuilder out = new StringBuilder("{");
        for (int i = 0; i < pairs.length; i += 2) {
            if (i > 0) {
                out.append(',');
            }
            Object value = pairs[i + 1];
            out.append('"').append(pairs[i]).append("\":")
                    .append(value == null ? "null" : value instanceof Number || value instanceof Boolean ? value
                            : '"' + String.valueOf(value) + '"');
        }
        return out.append('}').toString();
    }

    static LocalDate today() {
        return LocalDate.now(java.time.ZoneId.of("Asia/Kuwait"));
    }

    static List<Permission> permissionsExcept(Permission... removed) {
        List<Permission> out = new java.util.ArrayList<>(Arrays.asList(ALL_PLEADINGS));
        out.removeAll(Arrays.asList(removed));
        return out;
    }
}
