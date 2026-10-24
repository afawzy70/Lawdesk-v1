package com.lawdesk.courts;

import static com.lawdesk.security.TestActors.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.lawdesk.platform.PostgresTestSupport;
import com.lawdesk.security.Permission;

/**
 * LD-018 court reference data (docs/delivery/PHASE-4-ACCEPTANCE.md): QA-0137…QA-0143, through the real security
 * filter chain and PostgreSQL.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CourtApiTest extends PostgresTestSupport {

    private static final String COURTS = "/api/v1/courts";
    private static final MediaType PROBLEM = MediaType.APPLICATION_PROBLEM_JSON;
    private static final UUID MANAGER = UUID.fromString("00000000-0000-4000-8000-0000000000aa");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void removeCourts() {
        jdbc.update("delete from court");
    }

    private static String body(String code, String nameAr, String nameEn, String level, String governorate) {
        return """
                {"code":%s,"nameAr":%s,"nameEn":%s,"level":%s,"governorate":%s}"""
                .formatted(quote(code), quote(nameAr), quote(nameEn), quote(level), quote(governorate));
    }

    private static String quote(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private static String validBody(String code) {
        return body(code, "محكمة نموذجية", "Sample Court", "FIRST_INSTANCE", "Cairo");
    }

    private MockHttpServletRequestBuilder create(String json) {
        return post(COURTS).with(as(MANAGER, "manager", Permission.COURT_MANAGE)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private String createCourt(String json) throws Exception {
        MvcResult result = mvc.perform(create(json)).andExpect(status().isCreated()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private int courtRows() {
        Integer rows = jdbc.queryForObject("select count(*) from court", Integer.class);
        return rows == null ? 0 : rows;
    }

    // QA-0137 authorized happy path

    @Test
    void createsACourtAndAnswersWithLocationAndTheStoredRepresentation() throws Exception {
        MvcResult result = mvc.perform(create(body("cai-fi-01", "محكمة نموذجية", "Sample Court", "FIRST_INSTANCE",
                        "Cairo")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.code").value("CAI-FI-01"))
                .andExpect(jsonPath("$.nameAr").value("محكمة نموذجية"))
                .andExpect(jsonPath("$.nameEn").value("Sample Court"))
                .andExpect(jsonPath("$.level").value("FIRST_INSTANCE"))
                .andExpect(jsonPath("$.governorate").value("Cairo"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.updatedAt").isString())
                .andReturn();
        String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        assertThat(result.getResponse().getHeader("Location")).endsWith(COURTS + "/" + id);

        assertThat(jdbc.queryForObject("select created_by from court where id = ?::uuid", UUID.class, id))
                .isEqualTo(MANAGER);
        assertThat(jdbc.queryForObject("select updated_by from court where id = ?::uuid", UUID.class, id))
                .isEqualTo(MANAGER);

        mvc.perform(get(COURTS + "/" + id).with(as(Permission.COURT_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("CAI-FI-01"));
    }

    @Test
    void theGovernorateIsOptional() throws Exception {
        mvc.perform(create(body("NAT-CASS", "محكمة النقض", "Court of Cassation", "CASSATION", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.governorate").value(Matchers.nullValue()));
    }

    @Test
    void listsCourtsByCodeAndFiltersByLevelAndActive() throws Exception {
        createCourt(body("B-APP", "استئناف", "Appeal", "APPEAL", null));
        createCourt(body("A-FI", "ابتدائية", "First", "FIRST_INSTANCE", null));
        String inactive = createCourt(body("C-FI", "جزئية", "Partial", "FIRST_INSTANCE", null));
        mvc.perform(put(COURTS + "/" + inactive).with(as(Permission.COURT_MANAGE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"C-FI","nameAr":"جزئية","nameEn":"Partial","level":"FIRST_INSTANCE",
                                 "active":false,"version":0}"""))
                .andExpect(status().isOk());

        mvc.perform(get(COURTS).with(as(Permission.COURT_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].code").value(Matchers.contains("A-FI", "B-APP", "C-FI")))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.totalPages").value(1));
        mvc.perform(get(COURTS).param("level", "FIRST_INSTANCE").with(as(Permission.COURT_VIEW)))
                .andExpect(jsonPath("$.items[*].code").value(Matchers.contains("A-FI", "C-FI")));
        mvc.perform(get(COURTS).param("active", "true").with(as(Permission.COURT_VIEW)))
                .andExpect(jsonPath("$.items[*].code").value(Matchers.contains("A-FI", "B-APP")));
        mvc.perform(get(COURTS).param("level", "FIRST_INSTANCE").param("active", "false")
                        .with(as(Permission.COURT_VIEW)))
                .andExpect(jsonPath("$.items[*].code").value(Matchers.contains("C-FI")));
        mvc.perform(get(COURTS).param("sort", "nameEn,desc").with(as(Permission.COURT_VIEW)))
                .andExpect(jsonPath("$.items[*].code").value(Matchers.contains("C-FI", "A-FI", "B-APP")));
        mvc.perform(get(COURTS).param("size", "2").param("page", "1").with(as(Permission.COURT_VIEW)))
                .andExpect(jsonPath("$.items[*].code").value(Matchers.contains("C-FI")))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void unknownSortFieldsAreIgnored() throws Exception {
        createCourt(validBody("Z-1"));
        mvc.perform(get(COURTS).param("sort", "createdBy,desc").with(as(Permission.COURT_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].code").value(Matchers.contains("Z-1")));
    }

    @Test
    void anUnknownLevelFilterIsABadRequest() throws Exception {
        mvc.perform(get(COURTS).param("level", "SUPREME").with(as(Permission.COURT_VIEW)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    void updatesACourtAndRecordsWhoChangedIt() throws Exception {
        String id = createCourt(validBody("UPD-1"));
        UUID editor = UUID.randomUUID();
        mvc.perform(put(COURTS + "/" + id).with(as(editor, "editor", Permission.COURT_MANAGE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"upd-2","nameAr":"محكمة استئناف نموذجية","nameEn":"Sample Appeal Court",
                                 "level":"APPEAL","governorate":"Giza","active":false,"version":0}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("UPD-2"))
                .andExpect(jsonPath("$.level").value("APPEAL"))
                .andExpect(jsonPath("$.governorate").value("Giza"))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.version").value(1));

        assertThat(jdbc.queryForObject("select updated_by from court where id = ?::uuid", UUID.class, id))
                .isEqualTo(editor);
        assertThat(jdbc.queryForObject("select created_by from court where id = ?::uuid", UUID.class, id))
                .isEqualTo(MANAGER);
    }

    @Test
    void aDeactivatedCourtCanBeReactivated() throws Exception {
        String id = createCourt(validBody("REACT"));
        String update = """
                {"code":"REACT","nameAr":"محكمة نموذجية","nameEn":"Sample Court","level":"FIRST_INSTANCE",
                 "active":%s,"version":%d}""";
        mvc.perform(put(COURTS + "/" + id).with(as(Permission.COURT_MANAGE)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(update.formatted("false", 0)))
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(put(COURTS + "/" + id).with(as(Permission.COURT_MANAGE)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(update.formatted("true", 1)))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.version").value(2));
    }

    @Test
    void anUnknownCourtIsNotFound() throws Exception {
        String missing = COURTS + "/" + UUID.randomUUID();
        mvc.perform(get(missing).with(as(Permission.COURT_VIEW)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
        mvc.perform(put(missing).with(as(Permission.COURT_MANAGE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"X1","nameAr":"س","nameEn":"x","level":"APPEAL","active":true,"version":0}"""))
                .andExpect(status().isNotFound());
    }

    @Test
    void aMalformedIdIsABadRequest() throws Exception {
        mvc.perform(get(COURTS + "/not-a-uuid").with(as(Permission.COURT_VIEW)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.detail").doesNotExist());
    }

    // QA-0138 required-field validation

    @Test
    void requiredFieldsAreValidatedWithFieldCodes() throws Exception {
        mvc.perform(create("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.errors[?(@.field == 'code')].code").value(Matchers.hasItem("NotBlank")))
                .andExpect(jsonPath("$.errors[?(@.field == 'nameAr')].code").value(Matchers.hasItem("NotBlank")))
                .andExpect(jsonPath("$.errors[?(@.field == 'nameEn')].code").value(Matchers.hasItem("NotBlank")))
                .andExpect(jsonPath("$.errors[?(@.field == 'level')].code").value(Matchers.hasItem("NotNull")));
        assertThat(courtRows()).isZero();
    }

    @Test
    void anUpdateNeedsTheVersionAndTheActiveFlag() throws Exception {
        String id = createCourt(validBody("NEEDS-V"));
        mvc.perform(put(COURTS + "/" + id).with(as(Permission.COURT_MANAGE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"NEEDS-V","nameAr":"س","nameEn":"x","level":"APPEAL"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'version')].code").value(Matchers.hasItem("NotNull")))
                .andExpect(jsonPath("$.errors[?(@.field == 'active')].code").value(Matchers.hasItem("NotNull")));
    }

    // QA-0139 invalid input boundary

    @Test
    void namesMayHaveTwoHundredCharactersButNotMore() throws Exception {
        String max = "ن".repeat(200);
        mvc.perform(create(body("LEN-200", max, "e".repeat(200), "APPEAL", "g".repeat(100))))
                .andExpect(status().isCreated());
        mvc.perform(create(body("LEN-201", max + "ن", "e".repeat(201), "APPEAL", "g".repeat(101))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'nameAr')].code").value(Matchers.hasItem("Size")))
                .andExpect(jsonPath("$.errors[?(@.field == 'nameEn')].code").value(Matchers.hasItem("Size")))
                .andExpect(jsonPath("$.errors[?(@.field == 'governorate')].code").value(Matchers.hasItem("Size")))
                .andExpect(content().string(Matchers.not(Matchers.containsString(max))));
        assertThat(courtRows()).isEqualTo(1);
    }

    @Test
    void malformedCodesAreRejected() throws Exception {
        for (String code : new String[] {"A", "has space", "a/b", "x".repeat(33), "قضاء"}) {
            mvc.perform(create(validBody(code)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[?(@.field == 'code')].code").value(Matchers.hasItem("Pattern")));
        }
        mvc.perform(create(validBody("x".repeat(32)))).andExpect(status().isCreated());
    }

    @Test
    void whitespaceOnlyNamesAreRejected() throws Exception {
        mvc.perform(create(body("WS-1", "   ", "\\t", "APPEAL", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'nameAr')].code").value(Matchers.hasItem("NotBlank")))
                .andExpect(jsonPath("$.errors[?(@.field == 'nameEn')].code").value(Matchers.hasItem("NotBlank")));
    }

    @Test
    void anUnknownLevelIsABadRequest() throws Exception {
        mvc.perform(create(body("LVL-1", "س", "x", "SUPREME", null)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(content().string(Matchers.not(Matchers.containsString("SUPREME"))));
        assertThat(courtRows()).isZero();
    }

    @Test
    void namesAreStoredTrimmed() throws Exception {
        mvc.perform(create(body(" TRIM-1 ", "  محكمة  ", " Court ", "APPEAL", " Alexandria ")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("TRIM-1"))
                .andExpect(jsonPath("$.nameAr").value("محكمة"))
                .andExpect(jsonPath("$.nameEn").value("Court"))
                .andExpect(jsonPath("$.governorate").value("Alexandria"));
    }

    @Test
    void aBlankGovernorateIsStoredAsNone() throws Exception {
        mvc.perform(create(body("GOV-0", "س", "x", "APPEAL", "  ")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.governorate").value(Matchers.nullValue()));
    }

    // QA-0140 unauthenticated access

    @Test
    void everyEndpointNeedsASession() throws Exception {
        String id = COURTS + "/" + UUID.randomUUID();
        mvc.perform(get(COURTS)).andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
        mvc.perform(get(id)).andExpect(status().isUnauthorized());
        mvc.perform(post(COURTS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(validBody("ANON")))
                .andExpect(status().isUnauthorized());
        mvc.perform(put(id).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(validBody("ANON")))
                .andExpect(status().isUnauthorized());
        assertThat(courtRows()).isZero();
    }

    // QA-0141 insufficient permission

    @Test
    void viewersCannotWriteAndOthersCannotRead() throws Exception {
        String id = createCourt(validBody("PERM-1"));
        mvc.perform(post(COURTS).with(as(Permission.COURT_VIEW)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(validBody("PERM-2")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
        mvc.perform(put(COURTS + "/" + id).with(as(Permission.COURT_VIEW, Permission.CASE_EDIT)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"PERM-1","nameAr":"س","nameEn":"x","level":"APPEAL","active":false,"version":0}"""))
                .andExpect(status().isForbidden());
        mvc.perform(get(COURTS).with(as(Permission.CASE_VIEW))).andExpect(status().isForbidden());
        mvc.perform(get(COURTS + "/" + id).with(as(Permission.PROCEEDING_VIEW))).andExpect(status().isForbidden());

        assertThat(courtRows()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select active from court where id = ?::uuid", Boolean.class, id)).isTrue();
    }

    @Test
    void writesNeedTheCsrfToken() throws Exception {
        mvc.perform(post(COURTS).with(as(Permission.COURT_MANAGE))
                        .contentType(MediaType.APPLICATION_JSON).content(validBody("NO-CSRF")))
                .andExpect(status().isForbidden());
        assertThat(courtRows()).isZero();
    }

    // QA-0142 record integrity and rollback; QA-0143 concurrent or repeat request

    @Test
    void aDuplicateCodeInAnyLetterCaseIsAConflictAndStoresNothing() throws Exception {
        createCourt(validBody("DUP-1"));
        mvc.perform(create(validBody("dup-1")))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
        mvc.perform(create(validBody("DUP-1"))).andExpect(status().isConflict());
        assertThat(courtRows()).isEqualTo(1);
    }

    @Test
    void renamingToACodeInUseIsAConflictAndChangesNothing() throws Exception {
        createCourt(validBody("TAKEN"));
        String id = createCourt(validBody("FREE"));
        mvc.perform(put(COURTS + "/" + id).with(as(Permission.COURT_MANAGE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"taken","nameAr":"جديد","nameEn":"New","level":"APPEAL","active":true,
                                 "version":0}"""))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select name_en from court where id = ?::uuid", String.class, id))
                .isEqualTo("Sample Court");
    }

    @Test
    void keepingTheOwnCodeIsNoConflict() throws Exception {
        String id = createCourt(validBody("SAME"));
        mvc.perform(put(COURTS + "/" + id).with(as(Permission.COURT_MANAGE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"same","nameAr":"س","nameEn":"Renamed","level":"APPEAL","active":true,
                                 "version":0}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nameEn").value("Renamed"));
    }

    @Test
    void aStaleVersionIsAConflictAndChangesNothing() throws Exception {
        String id = createCourt(validBody("STALE"));
        String update = """
                {"code":"STALE","nameAr":"س","nameEn":"%s","level":"APPEAL","active":true,"version":0}""";
        mvc.perform(put(COURTS + "/" + id).with(as(Permission.COURT_MANAGE)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(update.formatted("First"))).andExpect(status().isOk());
        mvc.perform(put(COURTS + "/" + id).with(as(Permission.COURT_MANAGE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(update.formatted("Second")))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select name_en from court where id = ?::uuid", String.class, id))
                .isEqualTo("First");
    }

    @Test
    void theDatabaseRefusesDuplicateAndLowerCaseCodesOnItsOwn() {
        jdbc.update("""
                insert into court (code, name_ar, name_en, level, created_by, updated_by)
                values ('DB-1', 'س', 'x', 'APPEAL', gen_random_uuid(), gen_random_uuid())""");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update("""
                insert into court (code, name_ar, name_en, level, created_by, updated_by)
                values ('DB-1', 'س', 'x', 'APPEAL', gen_random_uuid(), gen_random_uuid())"""))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update("""
                insert into court (code, name_ar, name_en, level, created_by, updated_by)
                values ('db-2', 'س', 'x', 'APPEAL', gen_random_uuid(), gen_random_uuid())"""))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update("""
                insert into court (code, name_ar, name_en, level, created_by, updated_by)
                values ('DB-3', 'س', 'x', 'SUPREME', gen_random_uuid(), gen_random_uuid())"""))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void noCourtsAreSeeded() {
        assertThat(courtRows()).isZero();
    }

    @Test
    void theLocationHeaderPointsAtTheNewCourt() throws Exception {
        mvc.perform(create(validBody("LOC-1")))
                .andExpect(header().string("Location", Matchers.matchesPattern(".*/api/v1/courts/[0-9a-f-]{36}")));
    }
}
