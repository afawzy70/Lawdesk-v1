package com.lawdesk.hearings;

import static com.lawdesk.security.TestActors.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.lawdesk.security.Permission;

/**
 * QAD-032 (LD-019 × LD-024): a proceeding's filing date and its hearings keep the chronology filing ≤ every hearing,
 * whichever side is edited and also when both are edited at once. Editing the filing date to after an existing hearing
 * is refused with nothing written; hearing writes and filing-date edits take the same proceeding row lock, so racing
 * writes are serialised and the second one sees the first one's committed date (proved with a test-only trigger that
 * pauses the first write inside its transaction, as in the Phase 4 QAD-024 races).
 */
class HearingFilingChronologyTest extends HearingTestSupport {

    @AfterEach
    void dropRacePause() {
        dropRaceTrigger();
    }

    private MockHttpServletRequestBuilder moveFiling(UUID proceeding, String filingDate, long version) {
        UUID court = jdbc.queryForObject("select court_id from proceeding where id = ?", UUID.class, proceeding);
        String number = jdbc.queryForObject("select court_case_number from proceeding where id = ?", String.class,
                proceeding);
        return put("/api/v1/proceedings/" + proceeding).with(as(Permission.PROCEEDING_MANAGE)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"courtId":"%s","courtCaseNumber":"%s","courtCaseYear":2025,"filingDate":"%s","version":%d}"""
                        .formatted(court, number, filingDate, version));
    }

    private LocalDate filingOf(UUID proceeding) {
        return jdbc.queryForObject("select filing_date from proceeding where id = ?", LocalDate.class, proceeding);
    }

    private long versionOf(UUID proceeding) {
        Long version = jdbc.queryForObject("select version from proceeding where id = ?", Long.class, proceeding);
        return version == null ? -1 : version;
    }

    /** No hearing of the proceeding is dated before its filing date. */
    private void assertChronology(UUID proceeding) {
        Integer early = jdbc.queryForObject("""
                select count(*) from hearing h join proceeding p on p.id = h.proceeding_id
                where p.id = ? and h.hearing_date < p.filing_date""", Integer.class, proceeding);
        assertThat(early).isZero();
    }

    @Test
    void theFilingDateCannotMoveAfterAnExistingHearing() throws Exception {
        UUID proceeding = createProceeding(createCase("QAD-032"), "800");
        schedule(proceeding, "2025-01-11");

        mvc.perform(moveFiling(proceeding, "2025-01-12", 0))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("FILING_AFTER_DEPENDENT_HEARING"));
        assertThat(filingOf(proceeding)).isEqualTo(LocalDate.parse(FILED));
        assertThat(versionOf(proceeding)).isZero();

        // The same day as the earliest hearing is allowed; earlier is allowed.
        mvc.perform(moveFiling(proceeding, "2025-01-11", 0)).andExpect(status().isOk());
        mvc.perform(moveFiling(proceeding, "2025-01-05", 1)).andExpect(status().isOk());
        assertChronology(proceeding);
    }

    @Test
    void aCancelledHearingStillBoundsTheFilingDate() throws Exception {
        UUID proceeding = createProceeding(createCase("Cancelled bound"), "801");
        UUID cancelled = schedule(proceeding, "2025-01-11");
        mvc.perform(postResult(cancelled, HearingApiTest.result("CANCELLED", null, null, 0))).andExpect(status().isOk());
        schedule(proceeding, "2025-02-01");

        mvc.perform(moveFiling(proceeding, "2025-01-20", 0))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("FILING_AFTER_DEPENDENT_HEARING"));
        assertThat(filingOf(proceeding)).isEqualTo(LocalDate.parse(FILED));
    }

    @Test
    void aHearingScheduledWhileTheFilingDateMovesLaterIsRefused() throws Exception {
        UUID proceeding = createProceeding(createCase("Race filing first"), "802");
        List<Integer> statuses = race("update", "proceeding",
                () -> mvc.perform(moveFiling(proceeding, "2025-01-20", 0)).andReturn(),
                () -> mvc.perform(postHearing(proceeding, hearing("2025-01-15", null, "PLEADING", null))).andReturn());
        assertThat(statuses).containsExactly(200, 422);
        assertThat(filingOf(proceeding)).isEqualTo(LocalDate.parse("2025-01-20"));
        assertThat(rows("hearing")).isZero();
        assertChronology(proceeding);
    }

    @Test
    void aFilingDateMovedWhileAHearingIsScheduledIsRefused() throws Exception {
        UUID proceeding = createProceeding(createCase("Race hearing first"), "803");
        List<Integer> statuses = race("insert", "hearing",
                () -> mvc.perform(postHearing(proceeding, hearing("2025-01-15", null, "PLEADING", null))).andReturn(),
                () -> mvc.perform(moveFiling(proceeding, "2025-01-20", 0)).andReturn());
        assertThat(statuses).containsExactly(201, 422);
        assertThat(filingOf(proceeding)).isEqualTo(LocalDate.parse(FILED));
        assertChronology(proceeding);
    }

    @Test
    void aFilingDateMovedWhileAHearingIsRescheduledEarlierIsRefused() throws Exception {
        UUID proceeding = createProceeding(createCase("Race reschedule"), "804");
        UUID id = schedule(proceeding, "2025-03-01");
        List<Integer> statuses = race("update", "hearing",
                () -> mvc.perform(put("/api/v1/hearings/" + id).with(as(Permission.HEARING_MANAGE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(HearingApiTest.update("2025-01-15", null, "PLEADING", null, 0))).andReturn(),
                () -> mvc.perform(moveFiling(proceeding, "2025-02-01", 0)).andReturn());
        assertThat(statuses).containsExactly(200, 422);
        assertThat(filingOf(proceeding)).isEqualTo(LocalDate.parse(FILED));
        assertChronology(proceeding);
    }

    @Test
    void aNextHearingAdjournedToWhileTheFilingDateMovesIsCheckedAgainstTheNewDate() throws Exception {
        UUID proceeding = createProceeding(createCase("Race adjourn"), "805");
        UUID id = schedule(proceeding, "2025-01-11");
        // The filing date cannot pass the existing hearing either way; the adjournment and the edit are serialised.
        List<Integer> statuses = race("update", "hearing",
                () -> mvc.perform(postResult(id, HearingApiTest.result("ADJOURNED", null, "2025-03-01", 0))).andReturn(),
                () -> mvc.perform(moveFiling(proceeding, "2025-02-01", 0)).andReturn());
        assertThat(statuses).containsExactly(200, 422);
        assertThat(rows("hearing")).isEqualTo(2);
        assertChronology(proceeding);
    }

    /**
     * Runs {@code paused} until its {@code event} on {@code table} stops in a test-only trigger, then runs
     * {@code other} to completion while {@code paused} still holds its transaction open; returns both statuses.
     */
    private List<Integer> race(String event, String table, Callable<MvcResult> paused, Callable<MvcResult> other)
            throws Exception {
        jdbc.execute("""
                create function qad032_pause() returns trigger language plpgsql as $$
                begin perform pg_sleep(1.5); return new; end $$""");
        jdbc.execute("create trigger qad032_pause before " + event + " on " + table
                + " for each row execute function qad032_pause()");
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<MvcResult> first = pool.submit(paused);
            awaitPause();
            Future<MvcResult> second = pool.submit(other);
            List<Integer> statuses = new ArrayList<>();
            statuses.add(first.get(30, TimeUnit.SECONDS).getResponse().getStatus());
            statuses.add(second.get(30, TimeUnit.SECONDS).getResponse().getStatus());
            return statuses;
        } finally {
            dropRaceTrigger();
        }
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
        throw new AssertionError("the paused request never reached its trigger");
    }

    private void dropRaceTrigger() {
        for (String table : List.of("proceeding", "hearing")) {
            jdbc.execute("drop trigger if exists qad032_pause on " + table);
        }
        jdbc.execute("drop function if exists qad032_pause()");
    }
}
