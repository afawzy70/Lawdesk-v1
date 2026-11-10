package com.lawdesk.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.lawdesk.platform.OperationsTestData;
import com.lawdesk.platform.PostgresTestSupport;

/**
 * Decision D3 as a test that fails (LD-DOC-04 section 5, LD-DOC-08): it walks every {@code /api/**} handler that names a
 * record by a path variable, builds the request for a real record of a seeded case and calls it as a user who holds EVERY
 * permission but is not a member of the case and has no scope. The answer must be 404 (the case is not visible to them) or
 * 403; 2xx, 409, 422 or 5xx would mean the handler reached the record without the case access policy. A control call as a
 * department-wide user must find the same record (not 404), so a wrongly built request cannot pass for a refusal.
 *
 * <p>Nothing escapes classification: a handler with a path variable is either case-bound (its variables are resolved from
 * the segment before them) or on the explicit {@link #NOT_CASE_BOUND} list with the reason; a new unclassified handler
 * fails the test. A 400 never counts as protection: handlers whose body cannot be generated carry a hand-built legitimate
 * body in {@link #BUILT_BODIES}, and the insider must get a 2xx from it.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CaseBoundEndpointsAccessTest extends PostgresTestSupport {

    /** First path segments (after /api/v1) whose records are not owned by a case, with why. */
    private static final Map<String, String> NOT_CASE_BOUND = Map.ofEntries(
            Map.entry("users", "administration of accounts"), Map.entry("roles", "administration of roles"),
            Map.entry("courts", "reference list"), Map.entry("lists", "managed reference lists"),
            Map.entry("lookups", "managed reference lists"), Map.entry("library", "the department library holds no case data"),
            Map.entry("org-units", "organisation tree"), Map.entry("access-scopes", "administration of scopes"),
            Map.entry("deadline-rules", "settings"), Map.entry("official-holidays", "settings"),
            Map.entry("templates", "legal templates"), Map.entry("template-versions", "legal templates"),
            Map.entry("defense-templates", "wording library"), Map.entry("experts", "register of experts"),
            Map.entry("notifications", "the signed-in user's own notifications"),
            Map.entry("calendar", "personal dates of the signed-in user"),
            Map.entry("assistant", "the signed-in user's own sessions; retrieval is scoped by the access policy"),
            Map.entry("parties", "a party is shared across cases; its case list is filtered by the policy"),
            Map.entry("contract-test", "test-only controller"), Map.entry("auth", "sign-in"), Map.entry("audit", "audit trail"), Map.entry("reports", "report ports filter by the policy"));

    /** Segment before a variable to the table whose record the variable names. */
    private static final Map<String, String> TABLE_OF_SEGMENT = Map.ofEntries(
            Map.entry("cases", "legal_case"), Map.entry("tasks", "task"), Map.entry("directives", "directive"),
            Map.entry("documents", "case_document"), Map.entry("evidence", "evidence_item"),
            Map.entry("case-links", "case_link"), Map.entry("deadlines", "deadline"), Map.entry("hearings", "hearing"),
            Map.entry("proceedings", "proceeding"), Map.entry("judgments", "judgment"),
            Map.entry("legal-documents", "legal_document"), Map.entry("risk-assessments", "risk_assessment"),
            Map.entry("risk-remediations", "risk_remediation"), Map.entry("settlements", "settlement"),
            Map.entry("expert-referrals", "expert_referral"), Map.entry("claims", "claim"), Map.entry("defenses", "defense"),
            Map.entry("notes", "case_note"), Map.entry("enforcement-actions", "enforcement_action"),
            Map.entry("enforcement", "enforcement_file"), Map.entry("special-decisions", "special_decision"),
            Map.entry("info-requests", "info_request"), Map.entry("document-links", "document_link"),
            Map.entry("precedents", "case_precedent_link"), Map.entry("followups", "gap_followup"),
            Map.entry("grants", "case_grant"), Map.entry("involvements", "case_org_involvement"),
            Map.entry("assertions", "proceeding"), Map.entry("versions", "#int"), Map.entry("counsel", "case_party_counsel"),
            Map.entry("links", "evidence_link"), Map.entry("facts", "case_fact"), Map.entry("parties", "party"));

    /**
     * Handlers whose body cannot be generated from the DTO alone (cross-field rules, ids of other records, business enums).
     * Each gets a hand-built, legitimate body. For these the insider control call must succeed with a 2xx, so a body that
     * is wrong can never pass as proof of protection. There is no "body not built" category any more: a 400 from an outsider
     * fails the walk.
     */
    private static final Map<String, BiFunction<Map<String, UUID>, Long, String>> BUILT_BODIES = Map.ofEntries(
            Map.entry("AccessController#confidentiality /api/v1/cases/{caseId}/confidentiality PUT",
                    (ids, v) -> "{\"level\":\"NORMAL\",\"reason\":\"SYNTH-DOC\",\"version\":" + v + "}"),
            Map.entry("AccessController#grant /api/v1/cases/{caseId}/grants POST",
                    (ids, v) -> "{\"userId\":\"" + ids.get("x_member") + "\",\"level\":\"VIEW\",\"reason\":\"SYNTH-DOC\"}"),
            Map.entry("CaseController#changeStatus /api/v1/cases/{id}/status POST",
                    (ids, v) -> "{\"status\":\"ACTIVE\",\"reason\":\"SYNTH-DOC\",\"version\":" + v
                            + ",\"acknowledgeOpenItems\":true}"),
            Map.entry("CaseController#update /api/v1/cases/{id} PUT",
                    (ids, v) -> "{\"title\":\"SYNTH-DOC renamed\",\"caseType\":\"COMMERCIAL\",\"subject\":\"SYNTH-DOC\","
                            + "\"openedOn\":\"2026-01-10\",\"version\":" + v + "}"),
            Map.entry("CasePrecedentController#link /api/v1/cases/{caseId}/precedents POST",
                    (ids, v) -> "{\"judgmentId\":\"" + ids.get("x_library_judgment")
                            + "\",\"reason\":\"SYNTH-DOC\",\"origin\":\"MANUAL\"}"),
            Map.entry("DocumentLinkController#add /api/v1/documents/{id}/links POST",
                    (ids, v) -> "{\"targetType\":\"CLAIM\",\"targetId\":\"" + ids.get("claim") + "\",\"note\":\"SYNTH-DOC\"}"),
            Map.entry("InfoRequestController#replyWithDocument /api/v1/info-requests/{id}/replies POST", (ids, v) -> "#multipart"),
            Map.entry("PartyController#addCounsel /api/v1/cases/{id}/parties/{partyId}/counsel POST",
                    (ids, v) -> "{\"kind\":\"LAWYER\",\"name\":\"SYNTH-DOC counsel\"}"),
            Map.entry("PartyController#link /api/v1/cases/{id}/parties POST",
                    (ids, v) -> "{\"partyId\":\"" + ids.get("x_party2") + "\",\"role\":\"OPPONENT\"}"),
            Map.entry("ProceedingController#create /api/v1/cases/{caseId}/proceedings POST",
                    (ids, v) -> "{\"stage\":\"FIRST_INSTANCE\",\"courtId\":\"" + ids.get("x_court")
                            + "\",\"courtCaseNumber\":\"2\",\"courtCaseYear\":2026,\"filingDate\":\"2026-01-12\"}"),
            // Three more handlers became reachable once the seed was repaired (the court code had been refused, so every
            // proceeding-bound table was silently unseeded and their handlers were never walked).
            Map.entry("ProceedingController#update /api/v1/proceedings/{id} PUT",
                    (ids, v) -> "{\"courtId\":\"" + ids.get("x_court")
                            + "\",\"courtCaseNumber\":\"1\",\"courtCaseYear\":2026,\"filingDate\":\"2026-01-12\",\"version\":" + v + "}"),
            Map.entry("ResultController#recordClaimResults /api/v1/judgments/{id}/claim-results PUT",
                    (ids, v) -> "{\"results\":[{\"claimId\":\"" + ids.get("claim") + "\",\"result\":\"ACCEPTED\"}]}"),
            Map.entry("ResultController#recordDefensePositions /api/v1/judgments/{id}/defense-positions PUT",
                    (ids, v) -> "{\"positions\":[{\"defenseId\":\"" + ids.get("defense") + "\",\"position\":\"ACCEPTED\"}]}"));

    private static final Pattern VARIABLE = Pattern.compile("\\{([A-Za-z]+)}");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mappings;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void clean() {
        OperationsTestData.deleteAll(jdbc);
        jdbc.update("delete from party");
        jdbc.update("delete from expert where name = 'SYNTH-DOC expert'");
        jdbc.update("delete from library_judgment where court_name = 'SYNTH-DOC court'");
    }

    private long versionOffset;

    private static final UUID USER = UUID.fromString("00000000-0000-4000-8000-0000000000b1");

    private UUID insert(String sql, Object... args) {
        try {
            return jdbc.queryForObject(sql, UUID.class, args);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** One case with a record in every table a path variable can name; a table that could not be seeded is reported. */
    private Map<String, UUID> seed(List<String> unseeded) {
        Map<String, UUID> ids = new LinkedHashMap<>();
        UUID member = insert("""
                insert into app_user (username, email, display_name, password_hash, enabled, created_by, updated_by)
                values ('cb' || substr(gen_random_uuid()::text, 1, 8), 'cb' || substr(gen_random_uuid()::text, 1, 8) || '@example.invalid', 'SYNTH-DOC member', 'x', true,
                        gen_random_uuid(), gen_random_uuid()) returning id""");
        UUID legalCase = insert("""
                insert into legal_case (reference_no, title, case_type, opened_on, status, primary_member_id, created_by, updated_by)
                values ('CB-' || gen_random_uuid(), 'SYNTH-DOC bound case', 'COMMERCIAL', '2026-01-10', 'OPEN', ?, ?, ?)
                returning id""", member, USER, USER);
        UUID other = insert("""
                insert into legal_case (reference_no, title, case_type, opened_on, status, created_by, updated_by)
                values ('CB-' || gen_random_uuid(), 'SYNTH-DOC other case', 'COMMERCIAL', '2026-01-10', 'OPEN', ?, ?)
                returning id""", USER, USER);
        ids.put("legal_case", legalCase);
        ids.put("x_member", member);
        UUID court = insert("""
                insert into court (code, name_ar, name_en, level, active, created_by, updated_by)
                values ('CB-' || upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 8)), 'محكمة', 'Court', 'FIRST_INSTANCE', true, gen_random_uuid(),
                        gen_random_uuid()) returning id""");
        UUID proceeding = insert("""
                insert into proceeding (case_id, stage, court_id, court_case_number, court_case_year, filing_date, created_by,
                                        updated_by) values (?, 'FIRST_INSTANCE', ?, '1', 2026, '2026-01-12', ?, ?) returning id""",
                legalCase, court, USER, USER);
        ids.put("x_court", court);
        ids.put("proceeding", proceeding);
        UUID judgment = insert("""
                insert into judgment (proceeding_id, judgment_date, outcome, final, created_by, updated_by)
                values (?, '2026-02-01', 'PARTIAL', false, ?, ?) returning id""", proceeding, USER, USER);
        ids.put("judgment", judgment);
        ids.put("hearing", insert("""
                insert into hearing (case_id, proceeding_id, hearing_date, purpose, created_by, updated_by)
                values (?, ?, '2026-12-01', 'FIRST_SESSION', ?, ?) returning id""", legalCase, proceeding, USER, USER));
        ids.put("claim", insert("""
                insert into claim (case_id, seq, text, kind, created_by, updated_by) values (?, 1, 'SYNTH-DOC', 'PRIMARY', ?, ?)
                returning id""", legalCase, USER, USER));
        ids.put("defense", insert("""
                insert into defense (case_id, seq, title, kind, created_by, updated_by) values (?, 1, 'SYNTH-DOC', 'FORMAL', ?, ?)
                returning id""", legalCase, USER, USER));
        UUID evidence = insert("""
                insert into evidence_item (case_id, name, role, created_by, updated_by) values (?, 'SYNTH-DOC', 'REQUIRED', ?, ?)
                returning id""", legalCase, USER, USER);
        ids.put("evidence_item", evidence);
        UUID task = insert("""
                insert into task (case_id, title, assignee_id, created_by, updated_by) values (?, 'SYNTH-DOC', ?, ?, ?)
                returning id""", legalCase, USER, USER, USER);
        ids.put("task", task);
        ids.put("case_note", insert("""
                insert into case_note (case_id, body, created_by, updated_by) values (?, 'SYNTH-DOC', ?, ?) returning id""",
                legalCase, USER, USER));
        UUID document = insert("""
                insert into case_document (case_id, title, file_name, size_bytes, sha256, created_by)
                values (?, 'SYNTH-DOC', 'f.pdf', 10, repeat('a', 64), ?) returning id""", legalCase, USER);
        ids.put("case_document", document);
        ids.put("deadline", insert("""
                insert into deadline (case_id, source_type, source_text, action, due_on, calc_status, created_by, updated_by)
                values (?, 'TEXT', 'SYNTH-DOC', 'SYNTH-DOC', '2026-12-01', 'MANUAL', ?, ?) returning id""", legalCase, USER, USER));
        ids.put("info_request", insert("""
                insert into info_request (case_id, request_no, subject, status, sent_on, created_by, updated_by)
                values (?, 'IR-CB-' || substr(gen_random_uuid()::text, 1, 6), 'SYNTH-DOC', 'AWAITING', '2026-02-01', ?, ?) returning id""",
                legalCase, USER, USER));
        ids.put("settlement", insert("""
                insert into settlement (case_id, kind, created_by, updated_by) values (?, 'SETTLEMENT', ?, ?) returning id""",
                legalCase, USER, USER));
        UUID file = insert("""
                insert into enforcement_file (judgment_id, case_id, created_by, updated_by) values (?, ?, ?, ?) returning id""",
                judgment, legalCase, USER, USER);
        ids.put("enforcement_file", file);
        ids.put("enforcement_action", insert("""
                insert into enforcement_action (enforcement_id, seq, kind, action_date, description, created_by, updated_by)
                values (?, 1, 'PROCEDURE', '2026-03-01', 'SYNTH-DOC', ?, ?) returning id""", file, USER, USER));
        ids.put("special_decision", insert("""
                insert into special_decision (proceeding_id, decision_type, decision_date, effect, created_by, updated_by)
                values (?, 'URGENT_MEASURE', '2026-03-01', 'GRANTED', ?, ?) returning id""", proceeding, USER, USER));
        ids.put("directive", insert("""
                insert into directive (directive_no, case_id, title, directive_date, source, addressee_text, created_by, updated_by)
                values ('DIR-CB-' || substr(gen_random_uuid()::text, 1, 6), ?, 'SYNTH-DOC', '2026-03-01', 'SYNTH-DOC', 'x', ?, ?)
                returning id""", legalCase, USER, USER));
        ids.put("legal_document", insert("""
                insert into legal_document (case_id, title, created_by, updated_by) values (?, 'SYNTH-DOC', ?, ?) returning id""",
                legalCase, USER, USER));
        ids.put("document_link", insert("""
                insert into document_link (document_id, task_id, created_by) values (?, ?, ?) returning id""", document, task, USER));
        ids.put("case_link", insert("""
                insert into case_link (case_id, other_case_id, kind, reason, created_by, updated_by)
                values (?, ?, 'SUBJECT_LINK', 'SYNTH-DOC', ?, ?) returning id""", legalCase, other, USER, USER));
        ids.put("gap_followup", insert("""
                insert into gap_followup (evidence_id, task_id, created_by) values (?, ?, ?) returning id""", evidence, task, USER));
        UUID fact = insert("""
                insert into case_fact (case_id, seq, text, created_by, updated_by) values (?, 1, 'SYNTH-DOC', ?, ?) returning id""",
                legalCase, USER, USER);
        ids.put("case_fact", fact);
        ids.put("evidence_link", insert("""
                insert into evidence_link (evidence_id, fact_id, created_by) values (?, ?, ?) returning id""", evidence, fact, USER));
        UUID party = insert("""
                insert into party (kind, name, created_by, updated_by) values ('PERSON', 'SYNTH-DOC', ?, ?) returning id""", USER, USER);
        ids.put("party", party);
        jdbc.update("insert into case_party (case_id, party_id, role, created_by) values (?, ?, 'CLIENT', ?)", legalCase, party, USER);
        ids.put("x_party2", insert("""
                insert into party (kind, name, created_by, updated_by) values ('PERSON', 'SYNTH-DOC second', ?, ?) returning id""",
                USER, USER));
        ids.put("x_library_judgment_seeded", insert("""
                insert into library_judgment (court_name, subject, source_reference, verification_note, created_by, updated_by)
                values ('SYNTH-DOC court', 'SYNTH-DOC linked', 'SYNTH-DOC source', 'SYNTH-DOC verified', ?, ?) returning id""",
                USER, USER));
        ids.put("x_library_judgment", insert("""
                insert into library_judgment (court_name, subject, source_reference, verification_note, created_by, updated_by)
                values ('SYNTH-DOC court', 'SYNTH-DOC', 'SYNTH-DOC source', 'SYNTH-DOC verified', ?, ?) returning id""", USER, USER));
        ids.put("case_precedent_link", insert("""
                insert into case_precedent_link (case_id, library_judgment_id, reason, origin, created_by)
                values (?, ?, 'SYNTH-DOC', 'MANUAL', ?) returning id""", legalCase, ids.get("x_library_judgment_seeded"), USER));
        ids.put("risk_assessment", insert("""
                insert into risk_assessment (case_id, version_no, level, level_reasons, trigger_kind, assessed_by)
                values (?, 1, 'LOW', 'SYNTH-DOC', 'MANUAL', ?) returning id""", legalCase, USER));
        ids.put("case_grant", insert("""
                insert into case_grant (case_id, user_id, level, reason, granted_by) values (?, ?, 'VIEW', 'SYNTH-DOC', ?) returning id""",
                legalCase, member, USER));
        ids.put("case_org_involvement", insert("""
                insert into case_org_involvement (case_id, sector_name_snapshot, created_by, updated_by) values (?, 'SYNTH-DOC', ?, ?) returning id""",
                legalCase, USER, USER));
        ids.put("case_party_counsel", insert("""
                insert into case_party_counsel (case_id, party_id, kind, name, created_by) values (?, ?, 'LAWYER', 'SYNTH-DOC', ?)
                returning id""", legalCase, party, USER));
        UUID expert = insert("""
                insert into expert (name, specialty, created_by, updated_by) values ('SYNTH-DOC expert', 'SYNTH-DOC', ?, ?) returning id""",
                USER, USER);
        ids.put("expert_referral", insert("""
                insert into expert_referral (case_id, proceeding_id, expert_id, referral_date, mission, created_by, updated_by)
                values (?, ?, ?, '2026-02-01', 'SYNTH-DOC', ?, ?) returning id""", legalCase, proceeding, expert, USER, USER));
        ids.put("risk_remediation", insert("""
                insert into risk_remediation (assessment_id, case_id, cause, action, created_by, updated_by)
                values (?, ?, 'SYNTH-DOC', 'SYNTH-DOC', ?, ?) returning id""", ids.get("risk_assessment"), legalCase, USER, USER));
        ids.forEach((table, id) -> {
            if (id == null) {
                unseeded.add(table);
            }
        });
        ids.values().removeIf(java.util.Objects::isNull);
        return ids;
    }

    private static boolean isApi(RequestMappingInfo info) {
        return info.getPathPatternsCondition() != null
                && info.getPathPatternsCondition().getPatternValues().stream().anyMatch(p -> p.startsWith("/api/"));
    }

    private static String firstSegment(String pattern) {
        String rest = pattern.substring("/api/v1/".length());
        int slash = rest.indexOf('/');
        return slash < 0 ? rest : rest.substring(0, slash);
    }

    /** The URL with each variable replaced by the record named by the segment before it; null when no record exists. */
    private static String resolve(String pattern, Map<String, UUID> ids, UUID caseId) {
        return resolve(pattern, ids, caseId, false);
    }

    /** As above; with {@code missing} each variable names a random record that does not exist. */
    private static String resolve(String pattern, Map<String, UUID> ids, UUID caseId, boolean missing) {
        Matcher m = VARIABLE.matcher(pattern);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String before = pattern.substring(0, m.start());
            String segment = before.substring(before.lastIndexOf('/', before.length() - 2) + 1).replace("/", "");
            String variable = m.group(1);
            String table = variable.equals("caseId") ? "legal_case" : variable.equals("documentId") ? "case_document"
                    : variable.equals("proceedingId") ? "proceeding" : variable.equals("judgmentId") ? "judgment"
                    : variable.equals("fileId") ? "enforcement_file" : TABLE_OF_SEGMENT.get(segment);
            String value;
            if ("#int".equals(table)) {
                value = "1";
            } else if (table == null || ids.get(table) == null) {
                return null;
            } else {
                value = missing ? UUID.randomUUID().toString() : ids.get(table).toString();
            }
            m.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** A minimal JSON value for a type: enough to pass bean validation in the usual cases. */
    private static String sample(Class<?> type, Long version) {
        if (type == String.class) {
            return "\"SYNTH-DOC\"";
        } else if (type == UUID.class) {
            return "\"" + UUID.randomUUID() + "\"";
        } else if (type == LocalDate.class) {
            return "\"2026-06-01\"";
        } else if (type == boolean.class || type == Boolean.class) {
            return "true";
        } else if (Number.class.isAssignableFrom(type) || type.isPrimitive()) {
            return "1";
        } else if (type.isEnum()) {
            return "\"" + type.getEnumConstants()[0] + "\"";
        } else if (List.class.isAssignableFrom(type)) {
            return "[]";
        } else if (type.isRecord()) {
            List<String> fields = new ArrayList<>();
            for (RecordComponent c : type.getRecordComponents()) {
                fields.add("\"" + c.getName() + "\":"
                        + (c.getName().equals("version") && version != null ? String.valueOf(version) : sample(c.getType(), version)));
            }
            return "{" + String.join(",", fields) + "}";
        }
        return "null";
    }

    private static String body(Method method, Long version) {
        for (Parameter p : method.getParameters()) {
            if (p.isAnnotationPresent(RequestBody.class)) {
                return sample(p.getType(), version);
            }
        }
        return "{}";
    }

    private static final Permission[] EVERYTHING = Permission.values();

    @Test
    void everyCaseBoundHandlerRefusesAUserWhoCannotSeeTheCase() throws Exception {
        walk(0);
    }

    /** The same walk with a version that is wrong by a margin: an outsider must still get 404/403, never 409 (existence is not disclosed). */
    @Test
    void aWrongVersionNeverTellsAnOutsiderThatTheRecordExists() throws Exception {
        walk(1000);
    }

    /**
     * Existence is not disclosed: for every case-bound handler an outsider holding every permission gets the SAME status,
     * content type and body shape (ids and the path they sit in normalised) for a record that exists in a case they cannot
     * see as for a record that does not exist at all, and both are 404/403 (a 400 would hide the comparison).
     */
    @Test
    void anOutsiderCannotTellARecordThatExistsFromOneThatDoesNot() throws Exception {
        walk(0, true);
    }

    private static final Pattern UUID_TEXT = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private static String shape(org.springframework.mock.web.MockHttpServletResponse response) throws Exception {
        return response.getStatus() + " " + response.getContentType() + " "
                + UUID_TEXT.matcher(response.getContentAsString()).replaceAll("<id>");
    }

    private org.springframework.mock.web.MockHttpServletResponse respond(RequestPostProcessor user, HttpMethod method, String url,
            String json, boolean multipart) throws Exception {
        if (multipart) {
            var file = new org.springframework.mock.web.MockMultipartFile("file", "x.pdf", "application/pdf",
                    "%PDF-1.4\n%%EOF\n".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
            return mvc.perform(MockMvcRequestBuilders.multipart(url).file(file).param("receivedOn", "2026-03-01")
                    .param("completeness", "COMPLETE").param("summary", "SYNTH-DOC").with(user).with(csrf())).andReturn().getResponse();
        }
        var builder = MockMvcRequestBuilders.request(method, url).with(user).with(csrf());
        if (json != null) {
            builder = builder.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mvc.perform(builder).andReturn().getResponse();
    }

    private void walk(long versionOffset) throws Exception {
        walk(versionOffset, false);
    }

    private void walk(long versionOffset, boolean existence) throws Exception {
        this.versionOffset = versionOffset;
        Set<String> disclosed = new TreeSet<>();
        List<String> unseeded = new ArrayList<>();
        Map<String, UUID> ids = seed(unseeded);
        UUID caseId = ids.get("legal_case");
        RequestPostProcessor outsider = TestActors.scoped(UUID.fromString("00000000-0000-4000-8000-0000000000b2"), "outsider",
                List.of(), EVERYTHING);
        RequestPostProcessor insider = TestActors.scoped(UUID.fromString("00000000-0000-4000-8000-0000000000b3"), "insider",
                List.of("ALL"), EVERYTHING);

        Set<String> unclassified = new TreeSet<>();
        Set<String> unresolved = new TreeSet<>();
        Set<String> leaked = new TreeSet<>();
        Set<String> badRequest = new TreeSet<>();
        Set<String> builtSeen = new TreeSet<>();
        Set<String> builtControlFailed = new TreeSet<>();
        Set<String> controlMissing = new TreeSet<>();
        int checked = 0;
        // Hand-built handlers go first: a DELETE or a withdrawal elsewhere in the walk would otherwise change the records they need.
        List<Map.Entry<RequestMappingInfo, HandlerMethod>> ordered = new ArrayList<>(mappings.getHandlerMethods().entrySet());
        ordered.sort(java.util.Comparator.comparingInt(e -> BUILT_BODIES.keySet().stream()
                .anyMatch(k -> k.startsWith(e.getValue().getBeanType().getSimpleName() + "#" + e.getValue().getMethod().getName() + " ")) ? 0 : 1));
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : ordered) {
            if (!isApi(entry.getKey())) {
                continue;
            }
            for (String pattern : entry.getKey().getPathPatternsCondition().getPatternValues()) {
                if (!pattern.startsWith("/api/v1/") || !VARIABLE.matcher(pattern).find()
                        || NOT_CASE_BOUND.containsKey(firstSegment(pattern))) {
                    continue;
                }
                String url = resolve(pattern, ids, caseId);
                if (url != null && entry.getKey().getMethodsCondition().getMethods()
                        .contains(org.springframework.web.bind.annotation.RequestMethod.DELETE)) {
                    Long current = currentVersion(pattern, ids);
                    url = url + "?version=" + (current == null ? 0 : current);
                }
                String name = entry.getValue().getBeanType().getSimpleName() + "#" + entry.getValue().getMethod().getName()
                        + " " + pattern;
                if (url == null) {
                    // A variable whose segment is unknown is a classification gap; a known one with no record is a seed gap.
                    Matcher m = VARIABLE.matcher(pattern);
                    boolean known = true;
                    while (m.find()) {
                        String before = pattern.substring(0, m.start());
                        String segment = before.substring(before.lastIndexOf('/', before.length() - 2) + 1).replace("/", "");
                        known &= List.of("caseId", "documentId", "proceedingId", "judgmentId", "fileId").contains(m.group(1))
                                || TABLE_OF_SEGMENT.containsKey(segment);
                    }
                    (known ? unresolved : unclassified).add(name);
                    continue;
                }
                for (RequestMappingInfo mapping : List.of(entry.getKey())) {
                    Set<org.springframework.web.bind.annotation.RequestMethod> methods = mapping.getMethodsCondition().getMethods();
                    for (var requestMethod : methods) {
                        HttpMethod http = HttpMethod.valueOf(requestMethod.name());
                        boolean hasBody = http == HttpMethod.POST || http == HttpMethod.PUT || http == HttpMethod.PATCH;
                        boolean multipart = Arrays.stream(entry.getValue().getMethod().getParameters())
                                .anyMatch(p -> p.isAnnotationPresent(org.springframework.web.bind.annotation.RequestPart.class));
                        String key = name + " " + http;
                        var built = BUILT_BODIES.get(key);
                        String json = hasBody && !multipart
                                ? (built != null ? built.apply(ids, currentVersion(pattern, ids)) : body(entry.getValue().getMethod(), currentVersion(pattern, ids)))
                                : null;
                        if (existence) {
                            String missingUrl = resolve(pattern, ids, caseId, true);
                            if (url.contains("?")) {
                                missingUrl = missingUrl + url.substring(url.indexOf('?'));
                            }
                            var real = respond(outsider, http, url, json, multipart);
                            var absent = respond(outsider, http, missingUrl, json, multipart);
                            checked++;
                            if ((real.getStatus() != 404 && real.getStatus() != 403) || (absent.getStatus() != 404 && absent.getStatus() != 403)) {
                                disclosed.add(key + " exists=" + real.getStatus() + " missing=" + absent.getStatus());
                            } else if (!shape(real).equals(shape(absent))) {
                                disclosed.add(key + " exists=[" + shape(real) + "] missing=[" + shape(absent) + "]");
                            }
                            continue;
                        }
                        // The outsider goes first: a refused call changes nothing, so the insider control still sees a clean record.
                        int denied = multipart ? upload(outsider, url) : status(outsider, http, url, json);
                        int control = multipart ? upload(insider, url) : status(insider, http, url, json);
                        checked++;
                        if (built != null) {
                            builtSeen.add(key);
                            if (versionOffset == 0 && (control < 200 || control > 299)) {
                                builtControlFailed.add(key + " -> " + control);
                            }
                        }
                        if (denied == 400) {
                            badRequest.add(key);
                        } else if (denied != 404 && denied != 403) {
                            leaked.add(key + " -> " + denied);
                        }
                        if (control == 404 && denied != 404) {
                            controlMissing.add(key);
                        }
                    }
                }
            }
        }

        assertThat(disclosed).as("an outsider could tell an existing record from a missing one").isEmpty();
        assertThat(unseeded).as("a table that could not be seeded silently removes its handlers from the walk").isEmpty();
        assertThat(checked).as("handlers checked").isGreaterThan(40);
        assertThat(unclassified).as("handlers with a path variable that are neither case-bound nor listed as not case-bound").isEmpty();
        assertThat(leaked).as("case-bound handlers that answered an outsider other than 404/403").isEmpty();
        assertThat(controlMissing).as("the control user could not find the record: the request was built wrongly").isEmpty();
        assertThat(badRequest).as("a 400 hides the access result and never counts as protection").isEmpty();
        if (!existence) {
            assertThat(builtSeen).as("every hand-built body was exercised").isEqualTo(BUILT_BODIES.keySet());
        }
        assertThat(builtControlFailed).as("an in-scope user must get a 2xx from each hand-built request").isEmpty();
        // What could not be seeded or resolved is reported, never silently skipped.
        unresolved.forEach(u -> System.out.println("CASE_BOUND_UNRESOLVED " + u));
        System.out.println("CASE_BOUND_WALK checked=" + checked + " unseeded=" + unseeded + " unresolved=" + unresolved
                + " built=" + builtSeen.size());
        assertThat(new LinkedHashSet<>(unresolved)).as("a handler whose record could not be resolved is not walked").isEmpty();
    }

    /** The real current version of the record the last path variable names, so a 409 can only come from a real conflict. */
    private Long currentVersion(String pattern, Map<String, UUID> ids) {
        Matcher m = VARIABLE.matcher(pattern);
        String table = null;
        while (m.find()) {
            String before = pattern.substring(0, m.start());
            String segment = before.substring(before.lastIndexOf('/', before.length() - 2) + 1).replace("/", "");
            String variable = m.group(1);
            table = variable.equals("caseId") ? "legal_case" : variable.equals("documentId") ? "case_document"
                    : variable.equals("proceedingId") ? "proceeding" : variable.equals("judgmentId") ? "judgment"
                    : variable.equals("fileId") ? "enforcement_file" : TABLE_OF_SEGMENT.get(segment);
        }
        if (table == null || table.startsWith("#") || ids.get(table) == null) {
            return null;
        }
        try {
            Long version = jdbc.queryForObject("select version from " + table + " where id = ?", Long.class, ids.get(table));
            return version == null ? null : version + versionOffset;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** A real (tiny) PDF as the multipart "file" part, for the upload handlers. */
    private int upload(RequestPostProcessor user, String url) throws Exception {
        var file = new org.springframework.mock.web.MockMultipartFile("file", "x.pdf", "application/pdf",
                "%PDF-1.4\n%%EOF\n".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
        return mvc.perform(MockMvcRequestBuilders.multipart(url).file(file)
                .param("receivedOn", "2026-03-01").param("completeness", "COMPLETE").param("summary", "SYNTH-DOC")
                .with(user).with(csrf())).andReturn().getResponse()
                .getStatus();
    }

    private int status(RequestPostProcessor user, HttpMethod method, String url, String json) throws Exception {
        var builder = MockMvcRequestBuilders.request(method, url).with(user).with(csrf());
        if (json != null) {
            builder = builder.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mvc.perform(builder).andReturn().getResponse().getStatus();
    }

    @SuppressWarnings("unused")
    private static String describe(Method m) {
        return Arrays.toString(m.getParameterTypes());
    }
}
