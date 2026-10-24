package com.lawdesk.proceedings;

import static com.lawdesk.security.TestActors.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;
import com.lawdesk.security.Permission;

/** LD-019 first-instance proceedings (docs/delivery/PHASE-4-ACCEPTANCE.md): QA-0145…QA-0151. */
class ProceedingApiTest extends JudicialApiTestSupport {

    // QA-0145 authorized happy path

    @Test
    void recordsAFirstInstanceProceedingAndATimelineEvent() throws Exception {
        UUID caseId = createCase("Rent dispute");
        UUID court = createCourt("FI-1", "FIRST_INSTANCE");

        MvcResult result = mvc.perform(postProceeding(caseId,
                        proceeding("FIRST_INSTANCE", court, " 1234 ", 2025, "2025-02-03", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.caseId").value(caseId.toString()))
                .andExpect(jsonPath("$.stage").value("FIRST_INSTANCE"))
                .andExpect(jsonPath("$.courtId").value(court.toString()))
                .andExpect(jsonPath("$.courtCode").value("FI-1"))
                .andExpect(jsonPath("$.courtNameAr").value("محكمة FI-1"))
                .andExpect(jsonPath("$.courtNameEn").value("Court FI-1"))
                .andExpect(jsonPath("$.courtCaseNumber").value("1234"))
                .andExpect(jsonPath("$.courtCaseYear").value(2025))
                .andExpect(jsonPath("$.filingDate").value("2025-02-03"))
                .andExpect(jsonPath("$.parentProceedingId").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.hasJudgment").value(false))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn();
        String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        assertThat(result.getResponse().getHeader("Location")).endsWith("/api/v1/proceedings/" + id);
        assertThat(jdbc.queryForObject("select created_by from proceeding where id = ?::uuid", UUID.class, id))
                .isEqualTo(CLERK);

        verify(caseLookup).recordEvent(eq(caseId), eq("PROCEEDING_RECORDED"), contains("1234/2025"));

        mvc.perform(get("/api/v1/proceedings/" + id).with(as(Permission.PROCEEDING_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courtCaseNumber").value("1234"));
    }

    @Test
    void listsTheProceedingsOfOneCaseByStageThenFilingDate() throws Exception {
        UUID caseId = createCase("Ordered");
        UUID other = createCase("Other");
        UUID first = createCourt("FI-2", "FIRST_INSTANCE");
        UUID appeal = createCourt("AP-2", "APPEAL");
        UUID later = recordProceeding(caseId, proceeding("FIRST_INSTANCE", first, "20", 2025, "2025-05-01", null));
        UUID earlier = recordProceeding(caseId, proceeding("FIRST_INSTANCE", first, "10", 2025, "2025-01-01", null));
        recordJudgment(earlier, "2025-03-01");
        UUID appealed = recordProceeding(caseId, proceeding("APPEAL", appeal, "5", 2025, "2025-03-10", earlier));
        recordProceeding(other, proceeding("FIRST_INSTANCE", first, "30", 2025, "2025-01-01", null));

        mvc.perform(get("/api/v1/cases/" + caseId + "/proceedings").with(as(Permission.PROCEEDING_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id").value(Matchers.contains(earlier.toString(), later.toString(),
                        appealed.toString())))
                .andExpect(jsonPath("$.items[0].hasJudgment").value(true))
                .andExpect(jsonPath("$.items[2].parentProceedingId").value(earlier.toString()))
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));
        mvc.perform(get("/api/v1/cases/" + caseId + "/proceedings").param("size", "2").param("page", "1")
                        .with(as(Permission.PROCEEDING_VIEW)))
                .andExpect(jsonPath("$.items[*].id").value(Matchers.contains(appealed.toString())))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void updatesCourtNumberYearAndFilingDate() throws Exception {
        UUID caseId = createCase("Update");
        UUID court = createCourt("FI-3", "FIRST_INSTANCE");
        UUID other = createCourt("FI-4", "FIRST_INSTANCE");
        UUID id = recordProceeding(caseId, proceeding("FIRST_INSTANCE", court, "1", 2024, "2024-02-01", null));
        UUID editor = UUID.randomUUID();

        mvc.perform(put("/api/v1/proceedings/" + id).with(as(editor, "editor", Permission.PROCEEDING_MANAGE))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courtId":"%s","courtCaseNumber":"77","courtCaseYear":2024,"filingDate":"2024-02-05",
                                 "version":0}""".formatted(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courtId").value(other.toString()))
                .andExpect(jsonPath("$.courtCode").value("FI-4"))
                .andExpect(jsonPath("$.courtCaseNumber").value("77"))
                .andExpect(jsonPath("$.filingDate").value("2024-02-05"))
                .andExpect(jsonPath("$.stage").value("FIRST_INSTANCE"))
                .andExpect(jsonPath("$.version").value(1));
        assertThat(jdbc.queryForObject("select updated_by from proceeding where id = ?::uuid", UUID.class, id))
                .isEqualTo(editor);
    }

    @Test
    void anUnknownCaseCourtOrProceedingIsNotFound() throws Exception {
        UUID caseId = createCase("Missing");
        UUID court = createCourt("FI-5", "FIRST_INSTANCE");
        mvc.perform(postProceeding(UUID.randomUUID(), proceeding("FIRST_INSTANCE", court, "1", 2025, "2025-01-01",
                        null)))
                .andExpect(status().isNotFound()).andExpect(content().contentTypeCompatibleWith(PROBLEM));
        mvc.perform(postProceeding(caseId, proceeding("FIRST_INSTANCE", UUID.randomUUID(), "1", 2025, "2025-01-01",
                        null)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/proceedings/" + UUID.randomUUID()).with(as(Permission.PROCEEDING_VIEW)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/cases/" + UUID.randomUUID() + "/proceedings").with(as(Permission.PROCEEDING_VIEW)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/proceedings/not-a-uuid").with(as(Permission.PROCEEDING_VIEW)))
                .andExpect(status().isBadRequest());
        assertThat(rows("proceeding")).isZero();
    }

    // QA-0146 required-field validation

    @Test
    void requiredFieldsAreValidated() throws Exception {
        UUID caseId = createCase("Required");
        mvc.perform(postProceeding(caseId, "{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'stage')].code").value(Matchers.hasItem("NotNull")))
                .andExpect(jsonPath("$.errors[?(@.field == 'courtId')].code").value(Matchers.hasItem("NotNull")))
                .andExpect(jsonPath("$.errors[?(@.field == 'courtCaseNumber')].code")
                        .value(Matchers.hasItem("NotBlank")))
                .andExpect(jsonPath("$.errors[?(@.field == 'courtCaseYear')].code").value(Matchers.hasItem("NotNull")))
                .andExpect(jsonPath("$.errors[?(@.field == 'filingDate')].code").value(Matchers.hasItem("NotNull")));
        assertThat(rows("proceeding")).isZero();
        verify(caseLookup, never()).recordEvent(any(), anyString(), anyString());
    }

    // QA-0147 invalid input boundary

    @Test
    void boundariesOfNumberYearAndFilingDate() throws Exception {
        UUID caseId = createCase("Bounds");
        UUID court = createCourt("FI-6", "FIRST_INSTANCE");
        String tomorrow = LocalDate.now().plusDays(2).toString();
        mvc.perform(postProceeding(caseId, proceeding("FIRST_INSTANCE", court, "9".repeat(33), 1899, tomorrow, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'courtCaseNumber')].code").value(Matchers.hasItem("Size")))
                .andExpect(jsonPath("$.errors[?(@.field == 'courtCaseYear')].code").value(Matchers.hasItem("Min")))
                .andExpect(jsonPath("$.errors[?(@.field == 'filingDate')].code")
                        .value(Matchers.hasItem("PastOrPresent")));
        mvc.perform(postProceeding(caseId, proceeding("FIRST_INSTANCE", court, "1", 2101, "2025-01-01", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'courtCaseYear')].code").value(Matchers.hasItem("Max")));
        mvc.perform(postProceeding(caseId, proceeding("FIRST_INSTANCE", court, "   ", 2025, "2025-01-01", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'courtCaseNumber')].code")
                        .value(Matchers.hasItem("NotBlank")));
        mvc.perform(postProceeding(caseId, proceeding("SUPREME", court, "1", 2025, "2025-01-01", null)))
                .andExpect(status().isBadRequest());
        mvc.perform(postProceeding(caseId, proceeding("FIRST_INSTANCE", court, "1", 2025, "03/02/2025", null)))
                .andExpect(status().isBadRequest());
        assertThat(rows("proceeding")).isZero();

        mvc.perform(postProceeding(caseId, proceeding("FIRST_INSTANCE", court, "9".repeat(32), 1900,
                        LocalDate.now().toString(), null)))
                .andExpect(status().isCreated());
        mvc.perform(postProceeding(caseId, proceeding("FIRST_INSTANCE", court, "8", 2100, "2025-01-01", null)))
                .andExpect(status().isCreated());
    }

    @Test
    void theCourtMustMatchTheStageAndBeActive() throws Exception {
        UUID caseId = createCase("Court rules");
        UUID appealCourt = createCourt("AP-7", "APPEAL");
        UUID closed = createCourt("FI-7", "FIRST_INSTANCE");
        deactivateCourt(closed, "FI-7", "FIRST_INSTANCE");

        mvc.perform(postProceeding(caseId, proceeding("FIRST_INSTANCE", appealCourt, "1", 2025, "2025-01-01", null)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("COURT_LEVEL_MISMATCH"));
        mvc.perform(postProceeding(caseId, proceeding("FIRST_INSTANCE", closed, "1", 2025, "2025-01-01", null)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("COURT_INACTIVE"));
        assertThat(rows("proceeding")).isZero();
    }

    @Test
    void aFirstInstanceProceedingHasNoParent() throws Exception {
        UUID caseId = createCase("No parent");
        UUID court = createCourt("FI-8", "FIRST_INSTANCE");
        UUID first = recordProceeding(caseId, proceeding("FIRST_INSTANCE", court, "1", 2025, "2025-01-01", null));
        mvc.perform(postProceeding(caseId, proceeding("FIRST_INSTANCE", court, "2", 2025, "2025-01-01", first)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("FIRST_INSTANCE_HAS_NO_PARENT"));
    }

    @Test
    void anUpdateKeepsTheCourtRules() throws Exception {
        UUID caseId = createCase("Update rules");
        UUID court = createCourt("FI-9", "FIRST_INSTANCE");
        UUID appealCourt = createCourt("AP-9", "APPEAL");
        UUID id = recordProceeding(caseId, proceeding("FIRST_INSTANCE", court, "1", 2025, "2025-01-01", null));
        mvc.perform(put("/api/v1/proceedings/" + id).with(as(Permission.PROCEEDING_MANAGE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courtId":"%s","courtCaseNumber":"1","courtCaseYear":2025,"filingDate":"2025-01-01",
                                 "version":0}""".formatted(appealCourt)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("COURT_LEVEL_MISMATCH"));

        // A court deactivated after the proceeding was recorded may stay on it.
        deactivateCourt(court, "FI-9", "FIRST_INSTANCE");
        mvc.perform(put("/api/v1/proceedings/" + id).with(as(Permission.PROCEEDING_MANAGE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courtId":"%s","courtCaseNumber":"2","courtCaseYear":2025,"filingDate":"2025-01-01",
                                 "version":0}""".formatted(court)))
                .andExpect(status().isOk());
    }

    // QA-0148 unauthenticated access

    @Test
    void everyEndpointNeedsASession() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(get("/api/v1/cases/" + id + "/proceedings")).andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
        mvc.perform(post("/api/v1/cases/" + id + "/proceedings").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/proceedings/" + id)).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/proceedings/" + id).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isUnauthorized());
    }

    // QA-0149 insufficient permission

    @Test
    void viewersCannotWriteAndOthersCannotRead() throws Exception {
        UUID caseId = createCase("Permissions");
        UUID court = createCourt("FI-10", "FIRST_INSTANCE");
        String body = proceeding("FIRST_INSTANCE", court, "1", 2025, "2025-01-01", null);
        mvc.perform(post("/api/v1/cases/" + caseId + "/proceedings")
                        .with(as(Permission.PROCEEDING_VIEW, Permission.CASE_EDIT, Permission.JUDGMENT_MANAGE))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden()).andExpect(content().contentTypeCompatibleWith(PROBLEM));
        UUID id = recordProceeding(caseId, body);
        mvc.perform(put("/api/v1/proceedings/" + id).with(as(Permission.PROCEEDING_VIEW)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courtId":"%s","courtCaseNumber":"9","courtCaseYear":2025,"filingDate":"2025-01-01",
                                 "version":0}""".formatted(court)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/cases/" + caseId + "/proceedings").with(as(Permission.CASE_VIEW)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/proceedings/" + id).with(as(Permission.COURT_VIEW)))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("select court_case_number from proceeding where id = ?::uuid", String.class,
                id)).isEqualTo("1");
    }

    // QA-0150 record integrity and rollback

    @Test
    void aFailingTimelineEventRollsTheProceedingBack() throws Exception {
        UUID caseId = createCase("Rollback");
        UUID court = createCourt("FI-11", "FIRST_INSTANCE");
        doThrow(new IllegalStateException("timeline unavailable")).when(caseLookup)
                .recordEvent(eq(caseId), anyString(), anyString());
        mvc.perform(postProceeding(caseId, proceeding("FIRST_INSTANCE", court, "1", 2025, "2025-01-01", null)))
                .andExpect(status().isInternalServerError());
        assertThat(rows("proceeding")).isZero();
    }

    // QA-0151 concurrent or repeat request

    @Test
    void theSameNumberAndYearAtTheSameCourtIsAConflict() throws Exception {
        UUID caseId = createCase("Duplicate");
        UUID court = createCourt("FI-12", "FIRST_INSTANCE");
        UUID otherCourt = createCourt("FI-13", "FIRST_INSTANCE");
        recordProceeding(caseId, proceeding("FIRST_INSTANCE", court, "55", 2025, "2025-01-01", null));
        mvc.perform(postProceeding(caseId, proceeding("FIRST_INSTANCE", court, "55", 2025, "2025-01-02", null)))
                .andExpect(status().isConflict()).andExpect(content().contentTypeCompatibleWith(PROBLEM));
        recordProceeding(caseId, proceeding("FIRST_INSTANCE", court, "55", 2024, "2025-01-01", null));
        recordProceeding(caseId, proceeding("FIRST_INSTANCE", otherCourt, "55", 2025, "2025-01-01", null));
        assertThat(rows("proceeding")).isEqualTo(3);
    }

    @Test
    void aStaleVersionIsAConflictAndChangesNothing() throws Exception {
        UUID caseId = createCase("Stale");
        UUID court = createCourt("FI-14", "FIRST_INSTANCE");
        UUID id = recordProceeding(caseId, proceeding("FIRST_INSTANCE", court, "1", 2025, "2025-01-01", null));
        String update = """
                {"courtId":"%s","courtCaseNumber":"%s","courtCaseYear":2025,"filingDate":"2025-01-01","version":0}""";
        mvc.perform(put("/api/v1/proceedings/" + id).with(as(Permission.PROCEEDING_MANAGE)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(update.formatted(court, "2")))
                .andExpect(status().isOk());
        mvc.perform(put("/api/v1/proceedings/" + id).with(as(Permission.PROCEEDING_MANAGE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(update.formatted(court, "3")))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select court_case_number from proceeding where id = ?::uuid", String.class,
                id)).isEqualTo("2");
    }

    @Test
    void theDatabaseEnforcesStageAndParentConsistency() throws Exception {
        UUID caseId = createCase("Schema");
        UUID court = createCourt("FI-15", "FIRST_INSTANCE");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update("""
                insert into proceeding (case_id, stage, court_id, court_case_number, court_case_year, filing_date,
                                        created_by, updated_by)
                values (?, 'APPEAL', ?, '1', 2025, date '2025-01-01', gen_random_uuid(), gen_random_uuid())""",
                caseId, court)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
