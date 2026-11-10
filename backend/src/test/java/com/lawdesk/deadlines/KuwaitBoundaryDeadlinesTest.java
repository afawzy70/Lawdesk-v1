package com.lawdesk.deadlines;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * PINNED-CLOCK tests (LD-DOC r3): a deadline's due date (a date-only field) against "today" at the Kuwait midnight. The deadline
 * code reads today with {@code LocalDate.now(ZoneId)} and has no Clock injection point, so the test pins that static call to a
 * fixed instant for the test thread only (Mockito static mock; production code unchanged). Nothing here observes the live clock.
 */
class KuwaitBoundaryDeadlinesTest extends DeadlineTestSupport {

    static Stream<Arguments> instants() {
        return Stream.of(
                Arguments.of("2026-11-09T20:59:59Z", "2026-11-09"),
                Arguments.of("2026-11-09T21:00:00Z", "2026-11-10"),
                Arguments.of("2026-11-09T23:59:59Z", "2026-11-10"),
                Arguments.of("2026-11-10T00:00:00Z", "2026-11-10"),
                Arguments.of("2026-11-10T02:59:59Z", "2026-11-10"));
    }

    @Autowired
    private DeadlineReminderRun run;

    private MockedStatic<LocalDate> pinned;

    @AfterEach
    void release() {
        if (pinned != null) {
            pinned.close();
        }
    }

    private void pin(String utc) {
        Instant now = Instant.parse(utc);
        pinned = Mockito.mockStatic(LocalDate.class, CALLS_REAL_METHODS);
        pinned.when(() -> LocalDate.now(any(ZoneId.class))).thenAnswer(i -> now.atZone(i.<ZoneId>getArgument(0)).toLocalDate());
        pinned.when(() -> LocalDate.now(any(Clock.class))).thenAnswer(i -> now.atZone(i.<Clock>getArgument(0).getZone()).toLocalDate());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("instants")
    void aDeadlineIsOverdueOnlyWhenItsDueDateIsBeforeTheKuwaitDay(String utc, String kuwaitDay) throws Exception {
        UUID member = user("Mona Boundary");
        UUID caseId = legalCase(member, null);
        RequestPostProcessor specialist = staff(member, SPECIALIST);
        RequestPostProcessor competent = staff(user("Hala Boundary"), COMPETENT);
        LocalDate today = LocalDate.parse(kuwaitDay);
        for (String[] d : new String[][] {{"Yesterday", today.minusDays(1).toString()}, {"Today", today.toString()},
                {"Tomorrow", today.plusDays(1).toString()}}) {
            UUID id = idOf(mvc.perform(postJson(specialist, "/api/v1/cases/" + caseId + "/deadlines",
                    json("sourceType", "TEXT", "sourceText", "Notice", "startsOn", today.minusDays(90).toString(), "dueOn",
                            d[1], "action", d[0], "ownerId", member)))
                    .andExpect(status().isCreated()).andReturn());
            mvc.perform(postJson(competent, "/api/v1/deadlines/" + id + "/approve", json("version", 0))).andExpect(status().isOk());
        }
        pin(utc);

        mvc.perform(getJson(specialist, "/api/v1/deadlines?overdue=true&caseId=" + caseId))
                .andExpect(jsonPath("$.items[*].action", contains("Yesterday")));
        mvc.perform(getJson(specialist, "/api/v1/deadlines?overdue=false&caseId=" + caseId))
                .andExpect(jsonPath("$.totalItems").value(2));
        // The date-only field comes back as stored, not shifted by any zone.
        mvc.perform(getJson(specialist, "/api/v1/deadlines?caseId=" + caseId))
                .andExpect(jsonPath("$.items[*].dueOn",
                        contains(today.minusDays(1).toString(), today.toString(), today.plusDays(1).toString())));

        // The reminder run reports overdue only for the deadline due before the Kuwait day.
        assertThat(run.run().overdue()).isGreaterThanOrEqualTo(1);
        assertThat(count("select count(distinct record_ref) from notification where type = 'DEADLINE_OVERDUE' and case_id = ?",
                caseId)).isEqualTo(1);
    }
}
