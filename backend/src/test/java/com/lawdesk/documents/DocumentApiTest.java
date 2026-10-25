package com.lawdesk.documents;

import static com.lawdesk.security.TestActors.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.lawdesk.platform.OperationsTestData;
import com.lawdesk.cases.CaseLookup;
import com.lawdesk.platform.PostgresTestSupport;
import com.lawdesk.security.Permission;

/** LD-026 legal PDF attachments, up to ten per case (docs/delivery/PHASE-5-ACCEPTANCE.md): QA-0201…QA-0207. */
@SpringBootTest
@AutoConfigureMockMvc
class DocumentApiTest extends PostgresTestSupport {

    static final UUID CLERK = UUID.fromString("00000000-0000-4000-8000-0000000000f1");
    static final int MIB = 1024 * 1024;

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoSpyBean
    CaseLookup caseLookup;

    @AfterEach
    void removeDocuments() {
        dropPause();
        jdbc.update("delete from case_document");
        jdbc.update("delete from case_document_quota");
        OperationsTestData.deleteAll(jdbc);
    }

    static byte[] pdf(String text) {
        return ("%PDF-1.7\n" + text + "\n%%EOF").getBytes(StandardCharsets.ISO_8859_1);
    }

    static byte[] pdfOfSize(int size) {
        byte[] bytes = new byte[size];
        Arrays.fill(bytes, (byte) ' ');
        System.arraycopy("%PDF-".getBytes(StandardCharsets.ISO_8859_1), 0, bytes, 0, 5);
        return bytes;
    }

    static MockMultipartFile file(String name, String type, byte[] bytes) {
        return new MockMultipartFile("file", name, type, bytes);
    }

    UUID createCase(String title) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/cases").with(as(Permission.CASE_CREATE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","caseType":"CIVIL","openedOn":"2024-01-10"}""".formatted(title)))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    MockMultipartHttpServletRequestBuilder upload(UUID caseId, MockMultipartFile file) {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/v1/cases/" + caseId + "/documents");
        if (file != null) {
            request.file(file);
        }
        request.with(as(CLERK, "clerk", Permission.DOCUMENT_MANAGE)).with(csrf());
        return request;
    }

    UUID attach(UUID caseId, String name) throws Exception {
        MvcResult result = mvc.perform(upload(caseId, file(name, "application/pdf", pdf(name))))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    int documents() {
        Integer count = jdbc.queryForObject("select count(*) from case_document", Integer.class);
        return count == null ? 0 : count;
    }

    // QA-0201 authorized happy path

    @Test
    void attachesAPdfAndRecordsATimelineEvent() throws Exception {
        UUID caseId = createCase("Lease");
        byte[] bytes = pdf("statement of claim");
        String sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));

        MvcResult result = mvc.perform(upload(caseId, file("C:\\scans\\صحيفة الدعوى.pdf", "application/pdf", bytes))
                        .param("title", "  Statement of claim "))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.caseId").value(caseId.toString()))
                .andExpect(jsonPath("$.title").value("Statement of claim"))
                .andExpect(jsonPath("$.fileName").value("صحيفة الدعوى.pdf"))
                .andExpect(jsonPath("$.sizeBytes").value(bytes.length))
                .andExpect(jsonPath("$.sha256").value(sha256))
                .andExpect(jsonPath("$.createdBy").value(CLERK.toString()))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.content").doesNotExist())
                .andReturn();
        String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        assertThat(result.getResponse().getHeader("Location")).endsWith("/api/v1/documents/" + id);
        verify(caseLookup).recordEvent(eq(caseId), eq("DOCUMENT_ATTACHED"), contains("Statement of claim"));

        mvc.perform(get("/api/v1/documents/" + id).with(as(Permission.DOCUMENT_VIEW)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fileName").value("صحيفة الدعوى.pdf"));
    }

    @Test
    void downloadsTheFileAsAnAttachmentAndAuditsIt() throws Exception {
        UUID caseId = createCase("Download");
        byte[] bytes = pdf("judgment copy");
        MvcResult created = mvc.perform(upload(caseId, file("حكم أول درجة.pdf", "application/pdf", bytes)))
                .andExpect(status().isCreated()).andReturn();
        String id = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        UUID reader = UUID.randomUUID();

        mvc.perform(get("/api/v1/documents/" + id + "/content").with(as(reader, "reader", Permission.DOCUMENT_VIEW)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(content().bytes(bytes))
                .andExpect(header().string("Content-Disposition", Matchers.startsWith("attachment;")))
                .andExpect(header().string("Content-Disposition",
                        Matchers.containsString("filename*=UTF-8''%D8%AD%D9%83%D9%85")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", Matchers.containsString("no-store")));
        assertThat(jdbc.queryForObject("""
                select count(*) from audit_event where action = 'DOCUMENT_DOWNLOAD' and actor_id = ? and target_id = ?
                and outcome = 'SUCCESS'""", Integer.class, reader, id)).isEqualTo(1);
    }

    @Test
    void listsTheDocumentsOfOneCaseNewestFirst() throws Exception {
        UUID caseId = createCase("List");
        UUID first = attach(caseId, "first.pdf");
        UUID second = attach(caseId, "second.pdf");
        attach(createCase("Other"), "other.pdf");
        mvc.perform(get("/api/v1/cases/" + caseId + "/documents").with(as(Permission.DOCUMENT_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id").value(Matchers.contains(second.toString(), first.toString())))
                .andExpect(jsonPath("$.items[0].title").value("second.pdf"))
                .andExpect(jsonPath("$.totalItems").value(2));
        mvc.perform(get("/api/v1/cases/" + UUID.randomUUID() + "/documents").with(as(Permission.DOCUMENT_VIEW)))
                .andExpect(status().isNotFound());
    }

    @Test
    void listsTheLatestDocumentsOfAllCasesWithTheirCase() throws Exception {
        UUID lease = createCase("Lease file");
        UUID other = createCase("Labour file");
        UUID first = attach(lease, "first.pdf");
        UUID second = attach(other, "second.pdf");
        UUID third = attach(lease, "third.pdf");
        mvc.perform(get("/api/v1/documents").param("size", "2").with(as(Permission.DOCUMENT_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id").value(Matchers.contains(third.toString(), second.toString())))
                .andExpect(jsonPath("$.items[0].caseTitle").value("Lease file"))
                .andExpect(jsonPath("$.items[0].caseReferenceNo").isString())
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get("/api/v1/documents").param("size", "2").param("page", "1").with(as(Permission.DOCUMENT_VIEW)))
                .andExpect(jsonPath("$.items[*].id").value(Matchers.contains(first.toString())));
        mvc.perform(get("/api/v1/documents").with(as(Permission.CASE_VIEW))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/documents")).andExpect(status().isUnauthorized());
    }

    @Test
    void removesADocumentWithItsVersionAndFreesTheSlot() throws Exception {
        UUID caseId = createCase("Remove");
        UUID id = attach(caseId, "draft.pdf");
        mvc.perform(delete("/api/v1/documents/" + id).param("version", "1")
                        .with(as(Permission.DOCUMENT_MANAGE)).with(csrf()))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/v1/documents/" + id).with(as(Permission.DOCUMENT_MANAGE)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("version"));
        mvc.perform(delete("/api/v1/documents/" + id).param("version", "0")
                        .with(as(Permission.DOCUMENT_MANAGE)).with(csrf()))
                .andExpect(status().isNoContent());
        verify(caseLookup).recordEvent(eq(caseId), eq("DOCUMENT_REMOVED"), contains("draft.pdf"));
        assertThat(documents()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from case_document_content", Integer.class)).isZero();
        mvc.perform(get("/api/v1/documents/" + id + "/content").with(as(Permission.DOCUMENT_VIEW)))
                .andExpect(status().isNotFound());
    }

    // QA-0202 required-field validation

    @Test
    void aFileIsRequiredAndMustNotBeEmpty() throws Exception {
        UUID caseId = createCase("Required");
        mvc.perform(upload(caseId, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("file"))
                .andExpect(jsonPath("$.errors[0].code").value("NotNull"));
        mvc.perform(upload(caseId, file("empty.pdf", "application/pdf", new byte[0])))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("NotEmpty"));
        mvc.perform(post("/api/v1/cases/" + caseId + "/documents").with(as(Permission.DOCUMENT_MANAGE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnsupportedMediaType());
        assertThat(documents()).isZero();
    }

    // QA-0203 invalid input boundary

    @Test
    void onlyPdfFilesUpToTenMebibytesAreAccepted() throws Exception {
        UUID caseId = createCase("Bounds");
        mvc.perform(upload(caseId, file("photo.pdf", "image/png", pdf("not really"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("file"))
                .andExpect(jsonPath("$.errors[0].code").value("PdfOnly"));
        mvc.perform(upload(caseId, file("fake.pdf", "application/pdf",
                        "<html>%PDF-".getBytes(StandardCharsets.ISO_8859_1))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].code").value("PdfOnly"));
        mvc.perform(upload(caseId, file("big.pdf", "application/pdf", pdfOfSize(10 * MIB + 1))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].code").value("MaxSize"));
        mvc.perform(upload(caseId, file("limit.pdf", "application/pdf", pdfOfSize(10 * MIB))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.sizeBytes").value(10 * MIB));
        mvc.perform(upload(caseId, file("titled.pdf", "application/pdf", pdf("x"))).param("title", "t".repeat(201)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("title"));
        assertThat(documents()).isEqualTo(1);
    }

    @Test
    void fileNamesAreReducedToASafeLastSegment() throws Exception {
        UUID caseId = createCase("Names");
        mvc.perform(upload(caseId, file("../../etc/pass\u0007wd.pdf", "application/pdf", pdf("a"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("passwd.pdf"))
                .andExpect(jsonPath("$.title").value("passwd.pdf"));
        mvc.perform(upload(caseId, file("   ", "application/pdf", pdf("b"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.fileName").value("document.pdf"));
        mvc.perform(upload(caseId, file("n".repeat(300) + ".pdf", "application/pdf", pdf("c"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value(Matchers.hasLength(255)));
    }

    @Test
    void aCaseHoldsAtMostTenDocuments() throws Exception {
        UUID caseId = createCase("Ten");
        for (int i = 1; i <= 10; i++) {
            attach(caseId, "doc" + i + ".pdf");
        }
        mvc.perform(upload(caseId, file("eleven.pdf", "application/pdf", pdf("11"))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("DOCUMENT_LIMIT_REACHED"));
        assertThat(documents()).isEqualTo(10);
        attach(createCase("Another case"), "fine.pdf");
    }

    @Test
    void anArchivedOrUnknownCaseTakesNoDocuments() throws Exception {
        UUID caseId = createCase("Archived");
        UUID id = attach(caseId, "kept.pdf");
        for (String[] step : new String[][] {{"CLOSED", "0"}, {"ARCHIVED", "1"}}) {
            mvc.perform(post("/api/v1/cases/" + caseId + "/status").with(as(Permission.CASE_STATUS_CHANGE))
                    .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"status\":\"%s\",\"version\":%s}".formatted(step[0], step[1])))
                    .andExpect(status().isOk());
        }
        mvc.perform(upload(caseId, file("late.pdf", "application/pdf", pdf("late"))))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.rule").value("CASE_ARCHIVED"));
        mvc.perform(delete("/api/v1/documents/" + id).param("version", "0")
                        .with(as(Permission.DOCUMENT_MANAGE)).with(csrf()))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.rule").value("CASE_ARCHIVED"));
        mvc.perform(get("/api/v1/documents/" + id + "/content").with(as(Permission.DOCUMENT_VIEW)))
                .andExpect(status().isOk());
        mvc.perform(upload(UUID.randomUUID(), file("x.pdf", "application/pdf", pdf("x"))))
                .andExpect(status().isNotFound());
        assertThat(documents()).isEqualTo(1);
    }

    // QA-0204 unauthenticated access

    @Test
    void everyEndpointNeedsASession() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(get("/api/v1/cases/" + id + "/documents")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/documents/" + id)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/documents/" + id + "/content")).andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/v1/cases/" + id + "/documents").file(file("a.pdf", "application/pdf", pdf("a")))
                .with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/documents/" + id).param("version", "0").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    // QA-0205 insufficient permission

    @Test
    void viewersCannotWriteAndOthersCannotRead() throws Exception {
        UUID caseId = createCase("Permissions");
        UUID actor = UUID.randomUUID();
        mvc.perform(multipart("/api/v1/cases/" + caseId + "/documents")
                        .file(file("a.pdf", "application/pdf", pdf("a")))
                        .with(as(actor, "viewer-" + actor, Permission.DOCUMENT_VIEW, Permission.CASE_EDIT)).with(csrf()))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("""
                select count(*) from audit_event where action = 'ACCESS_DENIED' and actor_id = ?
                and target_id = ?""", Integer.class, actor, "POST /api/v1/cases/" + caseId + "/documents"))
                .isEqualTo(1);
        UUID id = attach(caseId, "guarded.pdf");
        mvc.perform(delete("/api/v1/documents/" + id).param("version", "0")
                        .with(as(Permission.DOCUMENT_VIEW)).with(csrf()))
                .andExpect(status().isForbidden());
        for (String path : List.of("/api/v1/cases/" + caseId + "/documents", "/api/v1/documents/" + id,
                "/api/v1/documents/" + id + "/content")) {
            mvc.perform(get(path).with(as(Permission.CASE_VIEW))).andExpect(status().isForbidden());
        }
        assertThat(documents()).isEqualTo(1);
    }

    @Test
    void anUploadWithoutTheCsrfTokenIsRefused() throws Exception {
        UUID caseId = createCase("CSRF");
        mvc.perform(multipart("/api/v1/cases/" + caseId + "/documents")
                        .file(file("a.pdf", "application/pdf", pdf("a")))
                        .with(as(CLERK, "clerk", Permission.DOCUMENT_MANAGE)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/documents/" + UUID.randomUUID()).param("version", "0")
                        .with(as(Permission.DOCUMENT_MANAGE)))
                .andExpect(status().isForbidden());
        assertThat(documents()).isZero();
    }

    // QA-0206 record integrity and rollback

    @Test
    void aFailingTimelineEventStoresNothing() throws Exception {
        UUID caseId = createCase("Rollback");
        UUID id = attach(caseId, "kept.pdf");
        doThrow(new IllegalStateException("timeline unavailable")).when(caseLookup)
                .recordEvent(eq(caseId), anyString(), anyString());
        mvc.perform(upload(caseId, file("lost.pdf", "application/pdf", pdf("lost"))))
                .andExpect(status().isInternalServerError());
        mvc.perform(delete("/api/v1/documents/" + id).param("version", "0")
                        .with(as(Permission.DOCUMENT_MANAGE)).with(csrf()))
                .andExpect(status().isInternalServerError());
        assertThat(documents()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from case_document_content", Integer.class)).isEqualTo(1);
    }

    @Test
    void unknownDocumentsAreNotFound() throws Exception {
        mvc.perform(get("/api/v1/documents/" + UUID.randomUUID()).with(as(Permission.DOCUMENT_VIEW)))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/documents/" + UUID.randomUUID()).param("version", "0")
                        .with(as(Permission.DOCUMENT_MANAGE)).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/documents/not-a-uuid").with(as(Permission.DOCUMENT_VIEW)))
                .andExpect(status().isBadRequest());
    }

    // QA-0207 concurrent or repeat request

    @Test
    void twoConcurrentUploadsToACaseHoldingNineStoreExactlyOne() throws Exception {
        UUID caseId = createCase("Race");
        for (int i = 1; i <= 9; i++) {
            attach(caseId, "doc" + i + ".pdf");
        }
        jdbc.execute("""
                create function ld026_pause() returns trigger language plpgsql as $$
                begin perform pg_sleep(1.5); return new; end $$""");
        jdbc.execute("create trigger ld026_pause before insert on case_document for each row "
                + "execute function ld026_pause()");
        List<Integer> statuses;
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<MvcResult> first = pool.submit(() -> mvc.perform(
                    upload(caseId, file("a.pdf", "application/pdf", pdf("a")))).andReturn());
            awaitPause();
            Future<MvcResult> second = pool.submit(() -> mvc.perform(
                    upload(caseId, file("b.pdf", "application/pdf", pdf("b")))).andReturn());
            statuses = List.of(first.get(30, TimeUnit.SECONDS).getResponse().getStatus(),
                    second.get(30, TimeUnit.SECONDS).getResponse().getStatus());
        } finally {
            dropPause();
        }
        assertThat(statuses).containsExactly(201, 422);
        assertThat(documents()).isEqualTo(10);
    }

    private void awaitPause() throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (Instant.now().isBefore(deadline)) {
            Integer sleeping = jdbc.queryForObject(
                    "select count(*) from pg_stat_activity where wait_event = 'PgSleep' and pid <> pg_backend_pid()",
                    Integer.class);
            if (sleeping != null && sleeping > 0) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("the paused upload never reached its trigger");
    }

    private void dropPause() {
        jdbc.execute("drop trigger if exists ld026_pause on case_document");
        jdbc.execute("drop function if exists ld026_pause()");
    }
}
