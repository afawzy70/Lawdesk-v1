package com.lawdesk.cases;

import static com.lawdesk.cases.CaseTestData.insertCase;
import static com.lawdesk.security.TestActors.as;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.lawdesk.platform.PostgresTestSupport;
import com.lawdesk.security.Permission;

/**
 * LD-015 case list, search, filters and paging (docs/delivery/PHASE-3-ACCEPTANCE.md, LD-015; QA-0113…QA-0119).
 */
@SpringBootTest
@AutoConfigureMockMvc
class CaseSearchApiTest extends PostgresTestSupport {

    private static final String CASES = "/api/v1/cases";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private UUID nile;

    /** Inserted in this order, so the default order (newest first) is X-9, LAB-7, 123/2025, LD-2026-00001. */
    @BeforeEach
    void fourCases() {
        nile = insertCase(jdbc, "LD-2026-00001", "Ahmed v. Nile Trading", CaseType.COMMERCIAL, CaseStatus.OPEN,
                LocalDate.of(2026, 1, 10));
        insertCase(jdbc, "123/2025", "Estate of Hassan", CaseType.FAMILY, CaseStatus.ACTIVE,
                LocalDate.of(2025, 6, 1));
        insertCase(jdbc, "LAB-7", "Workers v. Delta Mills", CaseType.LABOR, CaseStatus.CLOSED,
                LocalDate.of(2026, 2, 20));
        insertCase(jdbc, "X-9", "100% discount dispute", CaseType.CIVIL, CaseStatus.SUSPENDED,
                LocalDate.of(2024, 12, 31));
    }

    @AfterEach
    void removeRows() {
        CaseTestData.deleteAll(jdbc);
    }

    private ResultActions list(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.with(as(Permission.CASE_VIEW)));
    }

    private static MockHttpServletRequestBuilder cases() {
        return get(CASES);
    }

    @Test
    void listsAllCasesNewestFirstInTheSharedPageShape() throws Exception {
        list(cases()).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.items[*].referenceNo", contains("X-9", "LAB-7", "123/2025", "LD-2026-00001")))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalItems").value(4))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.items[3].id").value(nile.toString()))
                .andExpect(jsonPath("$.items[3].title").value("Ahmed v. Nile Trading"))
                .andExpect(jsonPath("$.items[3].caseType").value("COMMERCIAL"))
                .andExpect(jsonPath("$.items[3].status").value("OPEN"))
                .andExpect(jsonPath("$.items[3].openedOn").value("2026-01-10"))
                .andExpect(jsonPath("$.items[3].updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.items[3].subject").doesNotExist());
    }

    @ParameterizedTest
    @CsvSource({"nile, LD-2026-00001", "123/, 123/2025", "DELTA, LAB-7", "' ld-2026 ', LD-2026-00001",
            "100%, X-9", "%, X-9"})
    void searchesReferenceAndTitleIgnoringCaseWithLiteralWildcards(String q, String expected) throws Exception {
        list(cases().param("q", q)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].referenceNo", contains(expected)))
                .andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void anUnderscoreIsNotAWildcardAndABlankSearchFiltersNothing() throws Exception {
        list(cases().param("q", "_")).andExpect(jsonPath("$.items", empty())).andExpect(jsonPath("$.totalItems")
                .value(0));
        list(cases().param("q", "   ")).andExpect(jsonPath("$.totalItems").value(4));
    }

    @Test
    void filtersByOneOrMoreStatusesAndTypes() throws Exception {
        list(cases().param("status", "OPEN", "CLOSED"))
                .andExpect(jsonPath("$.items[*].referenceNo", contains("LAB-7", "LD-2026-00001")));
        list(cases().param("caseType", "FAMILY"))
                .andExpect(jsonPath("$.items[*].referenceNo", contains("123/2025")));
        list(cases().param("caseType", "FAMILY", "CIVIL").param("status", "SUSPENDED"))
                .andExpect(jsonPath("$.items[*].referenceNo", contains("X-9")));
        list(cases().param("status", "ACTIVE").param("caseType", "LABOR")).andExpect(jsonPath("$.items", empty()));
    }

    @Test
    void filtersByAnInclusiveOpenedDateRange() throws Exception {
        list(cases().param("openedFrom", "2026-01-10").param("openedTo", "2026-02-20"))
                .andExpect(jsonPath("$.items[*].referenceNo", contains("LAB-7", "LD-2026-00001")));
        list(cases().param("openedTo", "2025-06-01"))
                .andExpect(jsonPath("$.items[*].referenceNo", contains("X-9", "123/2025")));
        list(cases().param("openedFrom", "2026-02-20").param("openedTo", "2026-02-20"))
                .andExpect(jsonPath("$.items[*].referenceNo", contains("LAB-7")));
    }

    @Test
    void anInvertedDateRangeBreaksARule() throws Exception {
        list(cases().param("openedFrom", "2026-02-21").param("openedTo", "2026-02-20"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("DATE_RANGE_INVALID"));
    }

    @Test
    void sortsByTheAllowedFields() throws Exception {
        list(cases().param("sort", "referenceNo,asc"))
                .andExpect(jsonPath("$.items[*].referenceNo", contains("123/2025", "LAB-7", "LD-2026-00001", "X-9")));
        list(cases().param("sort", "openedOn,desc"))
                .andExpect(jsonPath("$.items[*].referenceNo", contains("LAB-7", "LD-2026-00001", "123/2025", "X-9")));
        list(cases().param("sort", "openedOn"))
                .andExpect(jsonPath("$.items[*].referenceNo", contains("X-9", "123/2025", "LD-2026-00001", "LAB-7")));
        list(cases().param("sort", "createdAt,asc"))
                .andExpect(jsonPath("$.items[*].referenceNo", contains("LD-2026-00001", "123/2025", "LAB-7", "X-9")));
        list(cases().param("sort", "status,asc"))
                .andExpect(jsonPath("$.items[*].status", contains("ACTIVE", "CLOSED", "OPEN", "SUSPENDED")));
        list(cases().param("sort", "title,desc")).andExpect(status().isOk());
        list(cases().param("sort", "updatedAt,desc")).andExpect(status().isOk());
    }

    @Test
    void pagesThroughTheResultsAndCapsThePageSize() throws Exception {
        list(cases().param("size", "3").param("page", "1"))
                .andExpect(jsonPath("$.items[*].referenceNo", contains("LD-2026-00001")))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(3))
                .andExpect(jsonPath("$.totalItems").value(4))
                .andExpect(jsonPath("$.totalPages").value(2));
        list(cases().param("size", "100")).andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.totalItems").value(4));
        list(cases().param("size", "500")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("size")).andExpect(jsonPath("$.errors[0].code").value("Max"));
        list(cases().param("page", "9")).andExpect(jsonPath("$.items", empty()))
                .andExpect(jsonPath("$.totalItems").value(4));
    }

    @ParameterizedTest
    @CsvSource({"status, DELETED, typeMismatch", "caseType, TAX, typeMismatch",
            "openedFrom, 01/01/2026, typeMismatch", "openedTo, 2026-13-01, typeMismatch",
            "sort, 'subject,asc', Pattern", "sort, 'title,up', Pattern", "page, -1, Min", "size, 0, Min",
            "page, x, Digits", "page, '', Digits", "size, '', Digits", "size, 101, Max"})
    void invalidParametersAreFieldErrors(String parameter, String value, String code) throws Exception {
        list(cases().param(parameter, value)).andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value(parameter))
                .andExpect(jsonPath("$.errors[0].code").value(code));
    }

    @Test
    void aSearchLongerThanOneHundredCharactersIsAFieldError() throws Exception {
        list(cases().param("q", "q".repeat(100))).andExpect(status().isOk());
        list(cases().param("q", "q".repeat(101))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("q"))
                .andExpect(jsonPath("$.errors[0].code").value("Size"));
    }

    @Test
    void withoutASessionOrThePermissionTheListIsRefused() throws Exception {
        mvc.perform(cases()).andExpect(status().isUnauthorized());
        mvc.perform(cases().with(as(Permission.CASE_CREATE, Permission.CASE_EDIT))).andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }
}
