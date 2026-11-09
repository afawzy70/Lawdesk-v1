package com.lawdesk.library;

import static com.lawdesk.security.TestActors.as;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

/** Shared setup of the LD-052 library tests: the API as the library managers and readers use it, and clean-up. */
@SpringBootTest
@AutoConfigureMockMvc
abstract class LibrarySupport extends PostgresTestSupport {

    static final UUID MANAGER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    static final UUID READER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000a2");
    static final UUID OTHER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000a3");

    static final RequestPostProcessor MANAGER = as(MANAGER_ID, "library.manager", Permission.LIBRARY_VIEW,
            Permission.LIBRARY_MANAGE);
    static final RequestPostProcessor READER = as(READER_ID, "library.reader", Permission.LIBRARY_VIEW);

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void cleanLibrary() {
        OperationsTestData.deleteAll(jdbc);
        jdbc.update("delete from library_judgment_file");
        jdbc.update("delete from library_judgment_class");
        jdbc.update("delete from library_judgment_relation");
        jdbc.update("delete from library_principle");
        jdbc.update("delete from library_judgment");
        jdbc.update("delete from library_classification where origin = 'PROPOSED'");
        jdbc.update("update library_classification set active = true, version = 0, updated_by = null, updated_at = now()"
                + " where origin = 'DOCUMENT'");
    }

    private long auditBaseline;

    @BeforeEach
    void rememberAuditPosition() {
        // The audit trail is append-only: tests count the events they caused, not the ones before them.
        auditBaseline = jdbc.queryForObject("select coalesce(max(id), 0) from audit_event", Long.class);
    }

    /** How many audit events of these actions this test has caused so far. */
    int audited(String... actions) {
        return count("select count(*) from audit_event where id > ? and action in (select unnest(cast(? as text[])))",
                auditBaseline, actions);
    }

    String auditedReason(String action) {
        return jdbc.queryForObject("select reason from audit_event where id > ? and action = ?", String.class,
                auditBaseline, action);
    }

    // ---- JSON ---------------------------------------------------------------------------------------------------------

    /** A JSON object from alternating keys and values (String, Number, Boolean, null, List of String, raw JSON via Raw). */
    static String json(Object... keysAndValues) {
        Map<String, Object> fields = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            fields.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        StringBuilder out = new StringBuilder("{");
        fields.forEach((key, value) -> {
            if (out.length() > 1) {
                out.append(',');
            }
            out.append('"').append(key).append("\":").append(literal(value));
        });
        return out.append('}').toString();
    }

    record Raw(String json) {
    }

    private static String literal(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof Raw raw) {
            return raw.json();
        }
        if (value instanceof List<?> list) {
            return "[" + String.join(",", list.stream().map(LibrarySupport::literal).toList()) + "]";
        }
        return "\"" + value.toString().replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /** The smallest valid judgment: court, subject, source and how it was verified. */
    static String judgmentJson(String subject, Object... more) {
        Object[] base = {"courtName", "محكمة التمييز", "subject", subject, "sourceReference", "مجموعة الأحكام ٢٠٢٠",
            "verificationNote", "قوبلت بالنسخة الرسمية"};
        Object[] all = new Object[base.length + more.length];
        System.arraycopy(base, 0, all, 0, base.length);
        System.arraycopy(more, 0, all, base.length, more.length);
        return json(all);
    }

    // ---- requests -----------------------------------------------------------------------------------------------------

    MockHttpServletRequestBuilder postJson(RequestPostProcessor user, String path, String body) {
        return post(path).with(user).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    MockHttpServletRequestBuilder putJson(RequestPostProcessor user, String path, String body) {
        return put(path).with(user).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    MockHttpServletRequestBuilder deleteAs(RequestPostProcessor user, String path) {
        return delete(path).with(user).with(csrf());
    }

    MockHttpServletRequestBuilder getAs(RequestPostProcessor user, String path) {
        return get(path).with(user);
    }

    String read(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }

    /** Registers a judgment through the API and returns its id. */
    UUID registerJson(String body) throws Exception {
        MvcResult result = mvc.perform(postJson(MANAGER, "/api/v1/library/judgments", body)).andReturn();
        if (result.getResponse().getStatus() != 201) {
            throw new AssertionError("registration answered " + result.getResponse().getStatus() + ": " + read(result));
        }
        return UUID.fromString(JsonPath.read(read(result), "$.id"));
    }

    UUID register(String subject, Object... more) throws Exception {
        return registerJson(judgmentJson(subject, more));
    }

    UUID addPrinciple(UUID judgmentId, String text, Object... more) throws Exception {
        Object[] all = new Object[more.length + 2];
        all[0] = "principleText";
        all[1] = text;
        System.arraycopy(more, 0, all, 2, more.length);
        MvcResult result = mvc.perform(postJson(MANAGER, "/api/v1/library/judgments/" + judgmentId + "/principles",
                json(all))).andReturn();
        if (result.getResponse().getStatus() != 201) {
            throw new AssertionError("principle answered " + result.getResponse().getStatus() + ": " + read(result));
        }
        return UUID.fromString(JsonPath.read(read(result), "$.id"));
    }

    UUID classification(String dimension, String code, String labelAr) throws Exception {
        MvcResult result = mvc.perform(postJson(MANAGER, "/api/v1/library/classifications",
                json("dimension", dimension, "code", code, "labelAr", labelAr))).andReturn();
        if (result.getResponse().getStatus() != 201) {
            throw new AssertionError("classification answered " + result.getResponse().getStatus() + ": " + read(result));
        }
        return UUID.fromString(JsonPath.read(read(result), "$.id"));
    }

    UUID seededClassification(String dimension, String code) {
        return jdbc.queryForObject("select id from library_classification where dimension = ? and code = ?", UUID.class,
                dimension, code);
    }

    /** Ids of the judgments a search answered, in answer order. */
    List<String> searchIds(RequestPostProcessor user, String query) throws Exception {
        String body = read(mvc.perform(getAs(user, "/api/v1/library/judgments?" + query)).andReturn());
        return JsonPath.read(body, "$.items[*].id");
    }

    List<String> searchSubjects(String query) throws Exception {
        String body = read(mvc.perform(getAs(READER, "/api/v1/library/judgments?" + query)).andReturn());
        return JsonPath.read(body, "$.items[*].subject");
    }

    /** A case inserted directly: visible to (and changeable by) its creator, to nobody else without a scope. */
    UUID caseOf(UUID creator, String title, String subject, String facts, String caseType) {
        return jdbc.queryForObject("""
                insert into legal_case (reference_no, title, case_type, subject, facts, opened_on, status, created_by, updated_by)
                values ('LD-T-' || substr(gen_random_uuid()::text, 1, 8), ?, ?, ?, ?, ?, 'OPEN', ?, ?) returning id""",
                UUID.class, title, caseType, subject, facts, LocalDate.of(2026, 1, 10), creator, creator);
    }

    int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }
}
