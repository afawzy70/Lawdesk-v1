package com.lawdesk.documents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/**
 * LD-055 / owner decision D3: every endpoint of the archive, the memo register and the enforcement module that touches a
 * case's data answers 404 to a user who may not see the case, and 403 to one who sees it but may not change it. One table
 * walks through them all, so a new endpoint added without the check shows up here as soon as it is listed.
 */
class ArchiveVisibilityApiTest extends ArchiveTestSupport {

    private record Call(HttpMethod method, String path, String json, boolean write) {
    }

    private List<Call> calls(UUID caseId, UUID doc, UUID legal, UUID judgment, UUID file, UUID action, UUID link, UUID claim) {
        String note = "{\"note\":\"x\",\"version\":0}";
        return List.of(
                new Call(HttpMethod.GET, "/api/v1/cases/" + caseId + "/documents", null, false),
                new Call(HttpMethod.GET, "/api/v1/cases/" + caseId + "/documents/withdrawn", null, false),
                new Call(HttpMethod.GET, "/api/v1/documents/" + doc, null, false),
                new Call(HttpMethod.GET, "/api/v1/documents/" + doc + "/content", null, false),
                new Call(HttpMethod.GET, "/api/v1/documents/" + doc + "/versions", null, false),
                new Call(HttpMethod.GET, "/api/v1/documents/" + doc + "/versions/1/content", null, false),
                new Call(HttpMethod.GET, "/api/v1/documents/" + doc + "/text?withText=true", null, false),
                new Call(HttpMethod.GET, "/api/v1/documents/" + doc + "/links", null, false),
                new Call(HttpMethod.GET, "/api/v1/cases/" + caseId + "/document-links", null, false),
                new Call(HttpMethod.POST, "/api/v1/documents/" + doc + "/links", "{\"targetType\":\"CLAIM\",\"targetId\":\"" + claim + "\"}", true),
                new Call(HttpMethod.DELETE, "/api/v1/document-links/" + link, null, true),
                new Call(HttpMethod.PUT, "/api/v1/documents/" + doc + "/classification", "{\"version\":0}", true),
                new Call(HttpMethod.POST, "/api/v1/documents/" + doc + "/text/reprocess", "{}", true),
                new Call(HttpMethod.POST, "/api/v1/documents/" + doc + "/withdraw", "{\"reason\":\"x\",\"version\":0}", true),
                new Call(HttpMethod.POST, "/api/v1/documents/" + doc + "/restore", "{\"version\":0}", true),
                new Call(HttpMethod.GET, "/api/v1/cases/" + caseId + "/legal-documents", null, false),
                new Call(HttpMethod.GET, "/api/v1/legal-documents/" + legal, null, false),
                new Call(HttpMethod.GET, "/api/v1/legal-documents/" + legal + "/events", null, false),
                new Call(HttpMethod.POST, "/api/v1/cases/" + caseId + "/legal-documents", "{\"title\":\"x\"}", true),
                new Call(HttpMethod.PUT, "/api/v1/legal-documents/" + legal, "{\"title\":\"x\",\"version\":0}", true),
                new Call(HttpMethod.POST, "/api/v1/legal-documents/" + legal + "/submit", note, true),
                new Call(HttpMethod.POST, "/api/v1/legal-documents/" + legal + "/return", note, true),
                new Call(HttpMethod.POST, "/api/v1/legal-documents/" + legal + "/record-sent", "{\"sentOn\":\"2026-01-01\",\"method\":\"x\",\"version\":0}", true),
                new Call(HttpMethod.POST, "/api/v1/legal-documents/" + legal + "/archive", note, true),
                new Call(HttpMethod.GET, "/api/v1/judgments/" + judgment + "/enforcement", null, false),
                new Call(HttpMethod.POST, "/api/v1/judgments/" + judgment + "/enforcement", "{}", true),
                new Call(HttpMethod.PUT, "/api/v1/enforcement/" + file, "{\"status\":\"IN_PROGRESS\",\"version\":0}", true),
                new Call(HttpMethod.POST, "/api/v1/enforcement/" + file + "/actions", "{\"kind\":\"OTHER\",\"actionDate\":\"2026-01-01\",\"description\":\"x\"}", true),
                new Call(HttpMethod.PUT, "/api/v1/enforcement-actions/" + action, "{\"kind\":\"OTHER\",\"actionDate\":\"2026-01-01\",\"description\":\"x\",\"version\":0}", true));
    }

    private int answer(RequestPostProcessor user, Call call) throws Exception {
        var request = switch (call.method()) {
            case GET -> MockMvcRequestBuilders.get(call.path());
            case DELETE -> MockMvcRequestBuilders.delete(call.path());
            case PUT -> MockMvcRequestBuilders.put(call.path());
            default -> MockMvcRequestBuilders.post(call.path());
        };
        request.with(user).with(csrf());
        if (call.json() != null) {
            request.contentType("application/json").content(call.json());
        }
        return mvc.perform(request).andReturn().getResponse().getStatus();
    }

    @Test
    void everyEndpointFollowsTheCaseAccessRule() throws Exception {
        UUID owner = user("Owner");
        UUID stranger = UUID.randomUUID();
        UUID caseId = newCase(owner);
        UUID proceeding = firstInstance(caseId);
        UUID judgment = judgment(proceeding, "2026-04-01");
        UUID claim = claim(caseId, 1);
        UUID doc = attach(member(owner), caseId, pdfFile("d.pdf", ArchiveFiles.pdfWithText("visible only to the members")));
        UUID legal = UUID.fromString(id(post(member(owner), "/api/v1/cases/" + caseId + "/legal-documents", "{\"title\":\"Memo\"}").andExpect(status().isCreated())));
        post(member(owner), "/api/v1/judgments/" + judgment + "/enforcement", "{}").andExpect(status().isCreated());
        UUID file = jdbc.queryForObject("select id from enforcement_file where judgment_id = ?", UUID.class, judgment);
        UUID action = UUID.fromString(id(post(member(owner), "/api/v1/enforcement/" + file + "/actions", "{\"kind\":\"OTHER\",\"actionDate\":\"2026-01-01\",\"description\":\"x\"}").andExpect(status().isCreated())));
        UUID link = UUID.fromString(id(post(member(owner), "/api/v1/documents/" + doc + "/links", "{\"targetType\":\"CLAIM\",\"targetId\":\"" + claim + "\"}").andExpect(status().isCreated())));

        List<Call> all = calls(caseId, doc, legal, judgment, file, action, link, claim);
        assertThat(all).hasSizeGreaterThan(25);
        for (Call call : all) {
            assertThat(answer(outsider(stranger), call)).as("a user who cannot see the case: %s %s", call.method(), call.path()).isEqualTo(404);
        }
        for (Call call : all) {
            int viewing = answer(viewer(stranger), call);
            if (call.write()) {
                assertThat(viewing).as("a user who sees but may not change: %s %s", call.method(), call.path()).isEqualTo(403);
            } else {
                assertThat(viewing).as("a user who sees the case: %s %s", call.method(), call.path()).isIn(200, 204);
            }
        }
        // The search lists documents of visible cases only.
        assertThat(body(get(outsider(stranger), "/api/v1/documents/search?q=members")))
                .doesNotContain(doc.toString()).doesNotContain("visible only");
        assertThat(body(get(outsider(stranger), "/api/v1/documents"))).doesNotContain(doc.toString());
        assertThat(body(get(outsider(stranger), "/api/v1/legal-documents"))).doesNotContain(legal.toString());
        assertThat(body(get(outsider(stranger), "/api/v1/enforcement/judgments?scope=ALL"))).doesNotContain(judgment.toString());
        // Uploads to the case follow the same rule.
        mvc.perform(multipart("/api/v1/cases/" + caseId + "/documents").file(pdfFile("x.pdf", ArchiveFiles.pdfWithText("x"))).with(outsider(stranger)).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(multipart("/api/v1/cases/" + caseId + "/documents").file(pdfFile("x.pdf", ArchiveFiles.pdfWithText("x"))).with(viewer(stranger)).with(csrf()))
                .andExpect(status().isForbidden());
    }
}
