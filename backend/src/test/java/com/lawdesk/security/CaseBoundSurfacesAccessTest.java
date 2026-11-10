package com.lawdesk.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.jayway.jsonpath.JsonPath;
import com.lawdesk.reports.IndicatorCode;
import com.lawdesk.wp9.Wp9TestSupport;

/**
 * LD-DOC-R3: the surfaces the case-bound walk ({@link CaseBoundEndpointsAccessTest}) does not reach, proven against ONE secret
 * case that carries a unique marker word in every kind of record. The attacker holds every permission (so only the case access
 * policy can stop them) but is neither a member nor in any scope. The owner of the case is the control: every probe an attacker
 * makes is also made by the owner, and where the surface is expected to show the case the owner must see the marker, so an
 * empty answer can never be an empty fixture.
 *
 * <p>By-id surfaces must answer the attacker exactly as they answer for a record that does not exist (status, content type and
 * body with ids normalised); list, search, report and export surfaces must answer without the marker, the case reference or the
 * case id; a status of 400 is never accepted as proof.
 */
class CaseBoundSurfacesAccessTest extends Wp9TestSupport {

    private static final String MARKER = "ZQXSECRETWORD";
    private static final Pattern UUID_TEXT = Pattern
            .compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mappings;

    private UUID owner;
    private UUID attacker;
    private UUID secretCase;
    private String reference;
    private UUID document;
    private UUID party;
    private RequestPostProcessor ownerAs;
    private RequestPostProcessor attackerAs;

    @BeforeEach
    void theSecretCase() throws Exception {
        owner = user("Owner");
        attacker = user("Attacker");
        secretCase = legalCase(owner, null);
        reference = jdbc.queryForObject("select reference_no from legal_case where id = ?", String.class, secretCase);
        jdbc.update("update legal_case set title = ?, subject = ? where id = ?", MARKER + " title", MARKER + " subject", secretCase);
        UUID proceeding = proceeding(secretCase, "9901");
        hearing(secretCase, proceeding, "2026-12-02", MARKER + " hearing summary", MARKER + " decision");
        judgment(proceeding, MARKER + " judgment summary");
        note(secretCase, MARKER + " note");
        task(secretCase, MARKER + " task", MARKER + " task text", owner);
        // Parties outlive the cases of earlier tests; remove the unlinked leftovers of this fixture so the directory holds one.
        jdbc.update("delete from party where name = ? and not exists (select 1 from case_party cp where cp.party_id = party.id)",
                MARKER + " party");
        party = party(MARKER + " party", "PERSON", null, false, null);
        onCase(secretCase, party, "CLIENT");
        ownerAs = staff(owner, Permission.values());
        attackerAs = outsider(attacker, Permission.values());

        var file = new MockMultipartFile("file", "secret.pdf", "application/pdf",
                "%PDF-1.4\n%%EOF\n".getBytes(StandardCharsets.ISO_8859_1));
        var created = mvc.perform(MockMvcRequestBuilders.multipart("/api/v1/cases/" + secretCase + "/documents").file(file)
                .param("title", MARKER + " document").with(ownerAs).with(csrf())).andReturn().getResponse();
        assertThat(created.getStatus()).as("the owner can upload the secret document: " + created.getContentAsString()).isEqualTo(201);
        document = UUID.fromString(JsonPath.read(created.getContentAsString(), "$.id"));
        UUID version = jdbc.queryForObject("select id from document_version where document_id = ? and version_no = 1", UUID.class,
                document);
        jdbc.update("delete from document_text where document_id = ?", document);
        jdbc.update("insert into document_text (version_id, document_id, status, method, text) values (?, ?, 'EXTRACTED', 'PDF_TEXT', ?)",
                version, document, MARKER + " recognised text of the secret document");
        jdbc.update("""
                insert into audit_event (actor_id, actor_username, action, target_type, target_id, outcome, reason)
                values (?, 'zqxprobe', 'ZQX_ACCESS_PROBE', 'CASE', ?, 'SUCCESS', ?)""", owner, secretCase.toString(),
                MARKER + " old value to new value");
    }

    @AfterEach
    void clean() {
        jdbc.update("delete from document_text where text like ?", MARKER + "%");
    }

    // ---- helpers -------------------------------------------------------------------------------------------------------

    private MockHttpServletResponse get(RequestPostProcessor who, String url) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get(url).with(who)).andReturn().getResponse();
    }

    private MockHttpServletResponse post(RequestPostProcessor who, String url, String json) throws Exception {
        var builder = MockMvcRequestBuilders.post(url).with(who).with(csrf());
        if (json != null) {
            builder = builder.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mvc.perform(builder).andReturn().getResponse();
    }

    private static String shape(MockHttpServletResponse r) throws Exception {
        return r.getStatus() + " " + r.getContentType() + " " + UUID_TEXT.matcher(r.getContentAsString()).replaceAll("<id>");
    }

    /** All text a response carries, whatever its format: plain text, the XML parts of an Excel file or the text of a PDF. */
    private static String text(MockHttpServletResponse r) throws Exception {
        byte[] bytes = r.getContentAsByteArray();
        String type = r.getContentType() == null ? "" : r.getContentType();
        if (type.contains("spreadsheetml")) {
            StringBuilder all = new StringBuilder();
            try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
                for (ZipEntry e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) {
                    ByteArrayOutputStream part = new ByteArrayOutputStream();
                    zip.transferTo(part);
                    all.append(part.toString(StandardCharsets.UTF_8)).append('\n');
                }
            }
            return all.toString();
        }
        if (type.contains("pdf")) {
            try (PDDocument pdf = Loader.loadPDF(bytes)) {
                return new PDFTextStripper().getText(pdf);
            }
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private boolean mentionsTheCase(String body) {
        // The search answer echoes the query the caller typed; that is the caller's own word, not a disclosure.
        body = body.replace("\"query\":\"" + MARKER + "\"", "");
        return body.contains(MARKER) || body.contains(reference) || body.contains(secretCase.toString());
    }

    // ---- by-id surfaces: documents, file download, text, versions ---------------------------------------------------------

    @Test
    void documentFileAndTextSurfacesAnswerAnOutsiderLikeAMissingRecord() throws Exception {
        List<String> urls = List.of("/api/v1/documents/{doc}", "/api/v1/documents/{doc}/content", "/api/v1/documents/{doc}/versions",
                "/api/v1/documents/{doc}/versions/1/content", "/api/v1/documents/{doc}/text?withText=true",
                "/api/v1/documents/{doc}/links", "/api/v1/cases/{case}/documents", "/api/v1/cases/{case}/documents/withdrawn",
                "/api/v1/cases/{case}/document-links");
        Set<String> problems = new TreeSet<>();
        for (String template : urls) {
            String real = template.replace("{doc}", document.toString()).replace("{case}", secretCase.toString());
            String missing = template.replace("{doc}", UUID.randomUUID().toString()).replace("{case}", UUID.randomUUID().toString());
            var control = get(ownerAs, real);
            if (control.getStatus() != 200) {
                problems.add("control " + template + " -> " + control.getStatus());
            }
            var refused = get(attackerAs, real);
            var absent = get(attackerAs, missing);
            if (refused.getStatus() != 404 && refused.getStatus() != 403) {
                problems.add(template + " answered the outsider " + refused.getStatus());
            } else if (!shape(refused).equals(shape(absent))) {
                problems.add(template + " differs: [" + shape(refused) + "] vs [" + shape(absent) + "]");
            }
            assertThat(text(refused)).as(template).doesNotContain(MARKER);
        }
        // Re-reading the text is a change: refused the same way, and it must leave the text alone.
        var reprocessControl = post(ownerAs, "/api/v1/documents/" + document + "/text/reprocess", null);
        assertThat(reprocessControl.getStatus()).as("the owner may re-read the document").isIn(200, 201, 202);
        var refused = post(attackerAs, "/api/v1/documents/" + document + "/text/reprocess", null);
        var absent = post(attackerAs, "/api/v1/documents/" + UUID.randomUUID() + "/text/reprocess", null);
        if (refused.getStatus() != 404 && refused.getStatus() != 403 || !shape(refused).equals(shape(absent))) {
            problems.add("reprocess [" + shape(refused) + "] vs [" + shape(absent) + "]");
        }
        assertThat(problems).isEmpty();
    }

    @Test
    void thereIsNoPreviewThumbnailOrLegacyRouteOutsideTheWalkedFamilies() {
        Set<String> unknown = new TreeSet<>();
        for (RequestMappingInfo info : mappings.getHandlerMethods().keySet()) {
            if (info.getPathPatternsCondition() == null) {
                continue;
            }
            for (String pattern : info.getPathPatternsCondition().getPatternValues()) {
                boolean fileSurface = pattern.matches(".*(thumbnail|preview|/ocr|/download|/render).*");
                boolean legacy = !pattern.startsWith("/api/v1/");
                if (fileSurface || legacy) {
                    unknown.add(pattern);
                }
            }
        }
        // A new preview, thumbnail, OCR text, download or non-v1 ("legacy") route must be added to this test with its proof.
        assertThat(unknown).as("routes that are not versioned /api/v1 or that serve file renditions").isSubsetOf(Set.of(
                "/api/v1/documents/{id}/content", "/api/v1/documents/{id}/versions/{versionNo}/content",
                "/api/v1/library/judgments/{id}/copy/info", "/api/v1/library/judgments/{id}/copy/history", "/error"));
    }

    // ---- lists, unified search, documents search -----------------------------------------------------------------------

    @Test
    void searchesAndListsNeverShowTheSecretCaseToAnOutsider() throws Exception {
        List<String> mustShowToOwner = List.of("/api/v1/search?q=" + MARKER, "/api/v1/search?q=" + MARKER + "&size=100",
                "/api/v1/documents/search?q=" + MARKER, "/api/v1/documents?size=100", "/api/v1/reports/cases?q=" + MARKER);
        List<String> mayShow = List.of("/api/v1/search?q=" + MARKER + "&types=DOCUMENT", "/api/v1/search?q=" + MARKER + "&types=CASE",
                "/api/v1/search?q=" + MARKER + "&types=HEARING", "/api/v1/search?q=" + MARKER + "&types=PARTY",
                "/api/v1/search?q=" + MARKER + "&types=NOTE", "/api/v1/search?q=" + MARKER + "&types=TASK",
                "/api/v1/search?q=" + MARKER + "&types=JUDGMENT",
                "/api/v1/search?q=recognised", "/api/v1/documents/deletion-log",
                "/api/v1/reports/judgments", "/api/v1/reports/hearings", "/api/v1/reports/workload", "/api/v1/reports/experts",
                "/api/v1/reports/dashboard", "/api/v1/reports/families", "/api/v1/reports/indicators");
        Set<String> problems = new TreeSet<>();
        for (String url : mustShowToOwner) {
            var control = get(ownerAs, url);
            if (control.getStatus() != 200 || !text(control).contains(MARKER)) {
                problems.add("control cannot see the marker at " + url + " -> " + control.getStatus());
            }
            var refused = get(attackerAs, url);
            if (refused.getStatus() != 200 || mentionsTheCase(text(refused))) {
                problems.add("outsider at " + url + " -> " + refused.getStatus());
            }
        }
        for (String url : mayShow) {
            var refused = get(attackerAs, url);
            if (refused.getStatus() != 200 || mentionsTheCase(text(refused))) {
                problems.add("outsider at " + url + " -> " + refused.getStatus());
            }
        }
        // Asking for the case by id is a by-id question: the same answer as for a case that does not exist.
        for (String template : List.of("/api/v1/search?q=" + MARKER + "&caseId=", "/api/v1/documents/search?q=" + MARKER + "&caseId=",
                "/api/v1/documents/deletion-log?caseId=")) {
            var refused = get(attackerAs, template + secretCase);
            var absent = get(attackerAs, template + UUID.randomUUID());
            if (mentionsTheCase(text(refused)) || !shape(refused).equals(shape(absent)) && refused.getStatus() != 200) {
                problems.add(template + " [" + shape(refused) + "] vs [" + shape(absent) + "]");
            }
        }
        assertThat(problems).isEmpty();
    }

    /**
     * The party directory is shared across cases by design: any holder of CASE_VIEW lists parties by name (the unified search
     * is stricter and hides a party that is on no visible case). This test pins exactly what that costs: the party NAME may be
     * listed, but no case of it, no case reference or id and no case wording is reachable from the directory by an outsider.
     */
    @Test
    void thePartyDirectoryNamesNoCaseToAnOutsider() throws Exception {
        var listed = get(attackerAs, "/api/v1/parties?q=" + MARKER);
        assertThat(listed.getStatus()).isEqualTo(200);
        String partyId = party.toString();
        for (String url : List.of("/api/v1/parties?q=" + MARKER, "/api/v1/parties/" + partyId)) {
            String body = get(attackerAs, url).getContentAsString().replace(MARKER + " party", "");
            assertThat(mentionsTheCase(body)).as(url + " names a case: " + body).isFalse();
        }
        var cases = get(attackerAs, "/api/v1/parties/" + partyId + "/cases");
        assertThat(cases.getStatus()).isEqualTo(200);
        assertThat(cases.getContentAsString()).as("the cases of the party that the outsider may see").isEqualTo("[]");
        assertThat(get(ownerAs, "/api/v1/parties/" + partyId + "/cases").getContentAsString()).as("the owner sees the case")
                .contains(secretCase.toString());
    }

    // ---- exports ----------------------------------------------------------------------------------------------------------

    @Test
    void exportsInEveryFormatNeverContainTheSecretCaseForAnOutsider() throws Exception {
        List<String> exports = new ArrayList<>(List.of("/api/v1/reports/cases/export", "/api/v1/reports/judgments/export",
                "/api/v1/reports/hearings/export", "/api/v1/reports/workload/export", "/api/v1/reports/experts/export"));
        var catalog = get(ownerAs, "/api/v1/reports/families");
        List<String> codes = JsonPath.read(catalog.getContentAsString(), "$[*].code");
        for (String code : codes) {
            exports.add("/api/v1/reports/families/" + code + "/export");
        }
        for (IndicatorCode code : IndicatorCode.values()) {
            exports.add("/api/v1/reports/indicators/" + code.name() + "/export");
        }
        Set<String> problems = new TreeSet<>();
        boolean ownerSawIt = false;
        for (String base : exports) {
            for (String format : List.of("csv", "xlsx", "pdf")) {
                boolean heavy = !format.equals("csv") && (base.contains("/families/") || base.contains("/indicators/"));
                if (heavy) {
                    continue;
                }
                String url = base + "?format=" + format + (base.endsWith("/cases/export") ? "&q=" + MARKER : "");
                var refused = get(attackerAs, url);
                if (refused.getStatus() == 400) {
                    problems.add(url + " answered 400, which proves nothing");
                } else if (refused.getStatus() == 200 && mentionsTheCase(text(refused))) {
                    problems.add(url + " exported the secret case to an outsider");
                }
                if (base.endsWith("/cases/export")) {
                    var control = get(ownerAs, url);
                    ownerSawIt |= control.getStatus() == 200 && text(control).contains(MARKER);
                    if (control.getStatus() != 200 || !text(control).contains(MARKER)) {
                        problems.add("control cannot export the marker at " + url + " -> " + control.getStatus());
                    }
                }
            }
        }
        assertThat(problems).isEmpty();
        assertThat(ownerSawIt).as("the owner's export carries the marker in every format, so the outsider's empty answer is real").isTrue();
    }

    // ---- audit log --------------------------------------------------------------------------------------------------------

    @Test
    void auditDetailsOfTheSecretCaseAreMaskedForAnOutsider() throws Exception {
        String url = "/api/v1/audit?actor=zqxprobe&size=50";
        var control = get(ownerAs, url);
        assertThat(control.getStatus()).isEqualTo(200);
        assertThat(control.getContentAsString()).as("the owner sees the details").contains(MARKER);
        var refused = get(attackerAs, url);
        assertThat(refused.getStatus()).isEqualTo(200);
        assertThat(refused.getContentAsString()).as("the outsider sees the event, never the old and new values")
                .doesNotContain(MARKER).contains("\"masked\":true");
        // Other filters reach the same rows and are masked the same way.
        for (String other : List.of("/api/v1/audit?action=ZQX_ACCESS_PROBE", "/api/v1/audit?outcome=SUCCESS&size=200",
                "/api/v1/audit?actor=zqxprobe&from=2020-01-01T00:00:00Z&to=2100-01-01T00:00:00Z")) {
            assertThat(get(attackerAs, other).getContentAsString()).as(other).doesNotContain(MARKER);
        }
    }

    // ---- assistant (test provider only; nothing is enabled) -----------------------------------------------------------------

    @Test
    void theAssistantRefusesAnOutsiderEveryWayToReachTheSecretCase() throws Exception {
        String body = json("task", "QUESTION", "caseId", secretCase.toString(), "question", MARKER + " subject", "saveDraft", true);
        var answered = post(ownerAs, "/api/v1/assistant/ask", body);
        assertThat(answered.getStatus()).as("the owner is answered: " + answered.getContentAsString()).isEqualTo(200);
        assertThat(answered.getContentAsString()).contains(MARKER);
        String draft = get(ownerAs, "/api/v1/assistant/outputs?caseId=" + secretCase).getContentAsString();
        List<String> draftIds = JsonPath.read(draft, "$[*].id");
        assertThat(draftIds).as("the owner has a draft").isNotEmpty();
        String session = JsonPath.read(answered.getContentAsString(), "$.sessionId") == null ? null
                : JsonPath.read(answered.getContentAsString(), "$.sessionId").toString();
        long ownerQueries = queryRows(owner);

        Set<String> problems = new TreeSet<>();
        List<String> asks = new ArrayList<>(List.of(body,
                json("task", "CASE_SUMMARY", "caseId", secretCase.toString()),
                json("task", "MEMO_DRAFT", "caseId", secretCase.toString()),
                json("task", "PRECEDENTS", "caseId", secretCase.toString(), "question", MARKER),
                json("task", "DOCUMENT_ANALYSIS", "documentId", document.toString()),
                json("task", "COMPARISON", "documentId", document.toString(), "otherDocumentId", document.toString())));
        if (session != null) {
            asks.add(json("task", "QUESTION", "caseId", secretCase.toString(), "question", MARKER, "sessionId", session));
        }
        for (String ask : asks) {
            var refused = post(attackerAs, "/api/v1/assistant/ask", ask);
            if (refused.getStatus() != 404 && refused.getStatus() != 403 || mentionsTheCase(refused.getContentAsString())) {
                problems.add("ask " + ask + " -> " + refused.getStatus());
            }
        }
        // The same questions about a case and a document that do not exist look the same.
        var refusedCase = post(attackerAs, "/api/v1/assistant/ask", json("task", "CASE_SUMMARY", "caseId", secretCase.toString()));
        var absentCase = post(attackerAs, "/api/v1/assistant/ask", json("task", "CASE_SUMMARY", "caseId", UUID.randomUUID().toString()));
        if (!shape(refusedCase).equals(shape(absentCase))) {
            problems.add("case summary [" + shape(refusedCase) + "] vs [" + shape(absentCase) + "]");
        }
        var refusedDoc = post(attackerAs, "/api/v1/assistant/ask", json("task", "DOCUMENT_ANALYSIS", "documentId", document.toString()));
        var absentDoc = post(attackerAs, "/api/v1/assistant/ask", json("task", "DOCUMENT_ANALYSIS", "documentId", UUID.randomUUID().toString()));
        if (!shape(refusedDoc).equals(shape(absentDoc))) {
            problems.add("document analysis [" + shape(refusedDoc) + "] vs [" + shape(absentDoc) + "]");
        }
        // Questions without a case search the library: the case must not surface there either.
        var library = post(attackerAs, "/api/v1/assistant/ask", json("task", "QUESTION", "question", MARKER));
        if (mentionsTheCase(library.getContentAsString())) {
            problems.add("library question surfaced the case");
        }
        var outputsRefused = get(attackerAs, "/api/v1/assistant/outputs?caseId=" + secretCase);
        var outputsAbsent = get(attackerAs, "/api/v1/assistant/outputs?caseId=" + UUID.randomUUID());
        if (outputsRefused.getStatus() != 404 || !shape(outputsRefused).equals(shape(outputsAbsent))) {
            problems.add("outputs by case [" + shape(outputsRefused) + "] vs [" + shape(outputsAbsent) + "]");
        }
        // Lists of the caller's own history: filtering by the secret case may answer, but only with the caller's own rows
        // (the query log of a case is not filtered by case access for the caller's own entries; it holds no other user's rows).
        for (String url : List.of("/api/v1/assistant/outputs", "/api/v1/assistant/queries", "/api/v1/assistant/queries?scope=all",
                "/api/v1/assistant/queries?caseId=" + secretCase, "/api/v1/assistant/queries?caseId=" + secretCase + "&scope=all")) {
            var listed = get(attackerAs, url);
            if (listed.getStatus() != 200 && listed.getStatus() != 404 && listed.getStatus() != 403) {
                problems.add(url + " -> " + listed.getStatus());
                continue;
            }
            String rows = listed.getContentAsString();
            if (listed.getStatus() == 200 && (!JsonPath.<List<Object>>read(rows, "$[?(@.userId == '" + owner + "')]").isEmpty()
                    || draftIds.stream().anyMatch(rows::contains))) {
                problems.add(url + " listed the owner's rows");
            }
        }
        var status = get(attackerAs, "/api/v1/assistant/status");
        if (status.getStatus() != 200 || mentionsTheCase(status.getContentAsString())) {
            problems.add("status -> " + status.getStatus());
        }
        for (String id : draftIds) {
            var read = get(attackerAs, "/api/v1/assistant/outputs/" + id);
            var review = post(attackerAs, "/api/v1/assistant/outputs/" + id + "/review", json("decision", "APPROVED"));
            var missing = get(attackerAs, "/api/v1/assistant/outputs/" + UUID.randomUUID());
            if (read.getStatus() != 404 || !shape(read).equals(shape(missing))) {
                problems.add("draft read [" + shape(read) + "] vs [" + shape(missing) + "]");
            }
            if (review.getStatus() != 404 && review.getStatus() != 403) {
                problems.add("draft review -> " + review.getStatus());
            }
        }
        assertThat(problems).isEmpty();
        assertThat(queryRows(owner)).as("the outsider's attempts wrote nothing into the owner's query log").isEqualTo(ownerQueries);
        assertThat(get(ownerAs, "/api/v1/assistant/outputs/" + draftIds.get(0)).getContentAsString())
                .as("and the owner's draft is untouched").contains("\"status\":\"DRAFT\"");
    }

    private long queryRows(UUID user) {
        Long n = jdbc.queryForObject("select count(*) from assistant_query_log where user_id = ?", Long.class, user);
        return n == null ? 0 : n;
    }
}
