package com.lawdesk.documents;

import static com.lawdesk.security.TestActors.as;
import static com.lawdesk.security.TestActors.scoped;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;
import com.lawdesk.platform.OperationsTestData;
import com.lawdesk.platform.PleadingsTestData;
import com.lawdesk.platform.PostgresTestSupport;
import com.lawdesk.security.Permission;

/**
 * Shared setup of the LD-055 archive tests. Records the module does not create itself are inserted straight into the
 * database so each test controls which case, members and judgments exist; everything the archive does goes through the real
 * API. The recognition engine is a stub the test controls.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ArchiveTestSupport.Engine.class)
public abstract class ArchiveTestSupport extends PostgresTestSupport {

    @TestConfiguration
    public static class Engine {
        @Bean
        @Primary
        StubOcrEngine stubOcrEngine() {
            return new StubOcrEngine();
        }
    }

    public static final Permission[] ALL = {Permission.CASE_VIEW, Permission.DOCUMENT_VIEW, Permission.DOCUMENT_MANAGE,
        Permission.ENFORCEMENT_VIEW, Permission.ENFORCEMENT_MANAGE, Permission.JUDGMENT_VIEW, Permission.JUDGMENT_MANAGE,
        Permission.CLAIM_VIEW, Permission.CLAIM_MANAGE, Permission.DEFENSE_VIEW, Permission.EVIDENCE_VIEW,
        Permission.PROCEEDING_VIEW, Permission.TASK_VIEW};
    public static final UUID USER = UUID.fromString("00000000-0000-4000-8000-0000000000a5");
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected StubOcrEngine ocr;

    @BeforeEach
    void resetEngine() {
        ocr.reset();
    }

    @AfterEach
    void removeArchive() {
        OperationsTestData.deleteAll(jdbc);
        PleadingsTestData.deleteTestLookups(jdbc);
        jdbc.update("update archive_setting set int_value = case code when 'MAX_FILE_BYTES' then 10485760"
                + " when 'MAX_DOCUMENTS_PER_CASE' then 100 when 'MAX_VERSIONS_PER_DOCUMENT' then 20"
                + " when 'MAX_OCR_PAGES' then 50 else 2000000 end");
        jdbc.update("update archive_format set enabled = true");
        jdbc.update("delete from party");
        jdbc.update("delete from user_role where user_id in (select id from app_user where username like 'ar%')");
        jdbc.update("delete from app_user where username like 'ar%'");
    }

    // ---- people -------------------------------------------------------------------------------------------------

    /** Holds every archive permission and sees and changes every case (department scope plus edit in scope). */
    public static RequestPostProcessor everything() {
        return as(USER, "archivist", ALL);
    }

    public static RequestPostProcessor with(Permission... permissions) {
        return as(USER, "archivist", permissions);
    }

    public static RequestPostProcessor withId(UUID id, Permission... permissions) {
        return as(id, "archivist", permissions);
    }

    /** Sees every case but changes none: not a member, no edit in scope. */
    public static RequestPostProcessor viewer(UUID id, Permission... permissions) {
        return scoped(id, "viewer", List.of("ALL"), permissions.length == 0 ? ALL : permissions);
    }

    /** Holds the permissions but no scope: sees only the cases it is a member of. */
    public static RequestPostProcessor outsider(UUID id, Permission... permissions) {
        return scoped(id, "outsider", List.of(), permissions.length == 0 ? ALL : permissions);
    }

    /** A member of the case it is given as the primary member: sees and changes it, and nothing else. */
    public static RequestPostProcessor member(UUID id, Permission... permissions) {
        return scoped(id, "member", List.of(), permissions.length == 0 ? ALL : permissions);
    }

    // ---- fixtures -----------------------------------------------------------------------------------------------

    protected UUID user(String displayName) {
        String username = "ar" + UUID.randomUUID().toString().substring(0, 8);
        return jdbc.queryForObject("""
                insert into app_user (username, email, display_name, password_hash, enabled, created_by, updated_by)
                values (?, ?, ?, 'x', true, gen_random_uuid(), gen_random_uuid()) returning id""", UUID.class, username,
                username + "@example.invalid", displayName);
    }

    protected UUID newCase() {
        return newCase(null);
    }

    protected UUID newCase(UUID primaryMember) {
        int n = SEQUENCE.incrementAndGet();
        return jdbc.queryForObject("""
                insert into legal_case (reference_no, title, case_type, opened_on, status, primary_member_id, created_by,
                                        updated_by)
                values (?, ?, 'COMMERCIAL', '2026-01-10', 'OPEN', ?, ?, ?) returning id""", UUID.class,
                "AR-" + System.nanoTime() + "-" + n, "Archive case " + n, primaryMember, USER, USER);
    }

    protected UUID restrictedCase(UUID primaryMember) {
        UUID id = newCase(primaryMember);
        jdbc.update("update legal_case set confidentiality = 'RESTRICTED' where id = ?", id);
        return id;
    }

    protected UUID firstInstance(UUID caseId) {
        UUID court = jdbc.queryForObject("""
                insert into court (code, name_ar, name_en, level, active, created_by, updated_by)
                values (?, 'محكمة', 'Court', 'FIRST_INSTANCE', true, gen_random_uuid(), gen_random_uuid()) returning id""",
                UUID.class, "AR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        return jdbc.queryForObject("""
                insert into proceeding (case_id, stage, court_id, court_case_number, court_case_year, filing_date, created_by,
                                        updated_by)
                values (?, 'FIRST_INSTANCE', ?, ?, 2026, '2026-01-12', ?, ?) returning id""", UUID.class, caseId, court,
                String.valueOf(1000 + SEQUENCE.incrementAndGet()), USER, USER);
    }

    protected UUID judgment(UUID proceedingId, String date) {
        return jdbc.queryForObject("""
                insert into judgment (proceeding_id, judgment_date, outcome, final, operative_text, created_by, updated_by)
                values (?, ?::date, 'PARTIAL', false, 'the operative part', ?, ?) returning id""", UUID.class, proceedingId,
                date, USER, USER);
    }

    protected UUID claim(UUID caseId, int seq) {
        return jdbc.queryForObject("""
                insert into claim (case_id, seq, text, kind, created_by, updated_by)
                values (?, ?, 'claim text', 'PRIMARY', ?, ?) returning id""", UUID.class, caseId, seq, USER, USER);
    }

    protected UUID defense(UUID caseId, int seq) {
        return jdbc.queryForObject("""
                insert into defense (case_id, seq, title, kind, created_by, updated_by)
                values (?, ?, 'defence title', 'FORMAL', ?, ?) returning id""", UUID.class, caseId, seq, USER, USER);
    }

    protected UUID hearing(UUID caseId, UUID proceedingId) {
        return jdbc.queryForObject("""
                insert into hearing (case_id, proceeding_id, hearing_date, purpose, created_by, updated_by)
                values (?, ?, '2026-03-01', 'PLEADING', ?, ?) returning id""", UUID.class, caseId, proceedingId, USER, USER);
    }

    protected UUID task(UUID caseId, String title) {
        return jdbc.queryForObject("""
                insert into task (case_id, title, assignee_id, created_by, updated_by) values (?, ?, ?, ?, ?)
                returning id""", UUID.class, caseId, title, USER, USER, USER);
    }

    protected UUID categoryId(String code) {
        return jdbc.queryForObject("select id from lookup_value where list_code = 'DOCUMENT_CATEGORY' and code = ?", UUID.class, code);
    }

    // ---- requests -----------------------------------------------------------------------------------------------

    protected static MockMultipartFile file(String name, String type, byte[] bytes) {
        return new MockMultipartFile("file", name, type, bytes);
    }

    protected static MockMultipartFile pdfFile(String name, byte[] bytes) {
        return file(name, "application/pdf", bytes);
    }

    protected ResultActions get(RequestPostProcessor user, String path) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get(path).with(user));
    }

    protected ResultActions post(RequestPostProcessor user, String path, String json) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post(path).with(user).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    protected ResultActions put(RequestPostProcessor user, String path, String json) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.put(path).with(user).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    protected ResultActions delete(RequestPostProcessor user, String path) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.delete(path).with(user).with(csrf()));
    }

    protected ResultActions upload(RequestPostProcessor user, String path, MockMultipartFile file) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.multipart(path).file(file).with(user).with(csrf()));
    }

    protected ResultActions upload(RequestPostProcessor user, String path, MockMultipartFile file, String param, String value)
            throws Exception {
        return mvc.perform(MockMvcRequestBuilders.multipart(path).file(file).param(param, value).with(user).with(csrf()));
    }

    protected static String body(ResultActions actions) throws Exception {
        return actions.andReturn().getResponse().getContentAsString();
    }

    protected static String id(ResultActions actions) throws Exception {
        MvcResult result = actions.andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    /** Uploads a file to the case as the given user and returns the new document's id, failing unless it answers 201. */
    protected UUID attach(RequestPostProcessor user, UUID caseId, MockMultipartFile file) throws Exception {
        MvcResult result = upload(user, "/api/v1/cases/" + caseId + "/documents", file).andReturn();
        if (result.getResponse().getStatus() != 201) {
            throw new AssertionError("upload answered " + result.getResponse().getStatus() + ": "
                    + result.getResponse().getContentAsString());
        }
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    protected int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    /** Runs the recognition worker once. */
    @Autowired
    protected DocumentTextService texts;

    protected int runRecognition() {
        return texts.processPending(50);
    }

}
