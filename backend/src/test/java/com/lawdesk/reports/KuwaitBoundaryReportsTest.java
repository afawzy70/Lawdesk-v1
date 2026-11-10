package com.lawdesk.reports;

import static com.lawdesk.security.TestActors.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;
import com.lawdesk.platform.PostgresTestSupport;
import com.lawdesk.security.Permission;

/**
 * PINNED-CLOCK tests (LD-DOC r3), dashboards and exports at the Kuwait midnight. The production code reads "today" with
 * {@code LocalDate.now(ZoneId)} / {@code LocalDate.now(Clock)} and has no Clock injection point for it, so these tests pin those
 * two static calls to a fixed instant for the test thread only (Mockito static mock, test code only; production code is
 * unchanged). Nothing here observes the live server clock. The database's own {@code now()} (row creation times) is NOT pinned:
 * rows are created with explicit dates, so only the "today" the application computes is under test.
 */
@SpringBootTest
@AutoConfigureMockMvc
class KuwaitBoundaryReportsTest extends PostgresTestSupport {

    static Stream<Arguments> instants() {
        return Stream.of(
                Arguments.of("2026-11-09T20:59:59Z", "2026-11-09"),
                Arguments.of("2026-11-09T21:00:00Z", "2026-11-10"),
                Arguments.of("2026-11-09T23:59:59Z", "2026-11-10"),
                Arguments.of("2026-11-10T00:00:00Z", "2026-11-10"),
                Arguments.of("2026-11-10T02:59:59Z", "2026-11-10"));
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private ReportTestData data;
    private MockedStatic<LocalDate> pinned;

    @BeforeEach
    void rows() {
        data = new ReportTestData(jdbc);
    }

    @AfterEach
    void release() {
        if (pinned != null) {
            pinned.close();
        }
        data.deleteCreated();
    }

    private void pin(String utc) {
        Instant now = Instant.parse(utc);
        pinned = Mockito.mockStatic(LocalDate.class, CALLS_REAL_METHODS);
        pinned.when(() -> LocalDate.now(any(ZoneId.class))).thenAnswer(i -> now.atZone(i.<ZoneId>getArgument(0)).toLocalDate());
        pinned.when(() -> LocalDate.now(any(Clock.class))).thenAnswer(i -> now.atZone(i.<Clock>getArgument(0).getZone()).toLocalDate());
    }

    private int overdueTasks() throws Exception {
        String body = mvc.perform(get("/api/v1/reports/dashboard").with(as(Permission.REPORT_VIEW))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.operations.overdueTasks");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("instants")
    void dashboardOverdueMonthAndExportDateFollowTheKuwaitDay(String utc, String kuwaitDay) throws Exception {
        LocalDate today = LocalDate.parse(kuwaitDay);
        UUID caseId = data.insertCase(data.marker() + " boundary", "CIVIL", "ACTIVE", today.minusDays(5));
        UUID assignee = data.insertUser("Boundary " + data.marker());
        pin(utc);
        int before = overdueTasks();

        data.insertTask(caseId, assignee, "due yesterday", today.minusDays(1), "OPEN");
        data.insertTask(caseId, assignee, "due today", today, "OPEN");
        data.insertTask(caseId, assignee, "due tomorrow", today.plusDays(1), "OPEN");

        // Only the task due before the Kuwait day is overdue; the task due on it is not. Judged by the UTC date, "due today"
        // would count as overdue inside the 21:00:00Z-23:59:59Z window and this difference would be 2.
        assertThat(overdueTasks() - before).isEqualTo(1);

        mvc.perform(get("/api/v1/reports/dashboard").with(as(Permission.REPORT_VIEW)))
                .andExpect(jsonPath("$.month").value(kuwaitDay.substring(0, 7)));

        MvcResult export = mvc.perform(get("/api/v1/reports/workload/export").param("format", "csv").param("lang", "en")
                .with(as(UUID.randomUUID(), "boundary", Permission.REPORT_EXPORT)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"workload-" + kuwaitDay + ".csv\""))
                .andReturn();
        assertThat(export.getResponse().getContentAsByteArray()).isNotEmpty();
    }
}
