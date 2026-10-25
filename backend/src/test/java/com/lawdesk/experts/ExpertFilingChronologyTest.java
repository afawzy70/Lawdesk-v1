package com.lawdesk.experts;

import static com.lawdesk.security.TestActors.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
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
 * QAD-032 applied to LD-025: a referral is never dated before its proceeding's filing date, whichever side is edited.
 * Correcting the filing date to after a referral is refused, and a referral and a filing-date correction take the
 * same proceeding row lock, so racing writes are serialised (test-only pause trigger, both orders).
 */
class ExpertFilingChronologyTest extends ExpertTestSupport {

    @AfterEach
    void dropRacePause() {
        dropRaceTrigger();
    }

    private MockHttpServletRequestBuilder moveFiling(UUID proceeding, String filingDate) {
        UUID court = jdbc.queryForObject("select court_id from proceeding where id = ?", UUID.class, proceeding);
        String number = jdbc.queryForObject("select court_case_number from proceeding where id = ?", String.class,
                proceeding);
        return put("/api/v1/proceedings/" + proceeding).with(as(Permission.PROCEEDING_MANAGE)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"courtId":"%s","courtCaseNumber":"%s","courtCaseYear":2025,"filingDate":"%s","version":0}"""
                        .formatted(court, number, filingDate));
    }

    private LocalDate filingOf(UUID proceeding) {
        return jdbc.queryForObject("select filing_date from proceeding where id = ?", LocalDate.class, proceeding);
    }

    private void assertChronology(UUID proceeding) {
        Integer early = jdbc.queryForObject("""
                select count(*) from expert_referral r join proceeding p on p.id = r.proceeding_id
                where p.id = ? and r.referral_date < p.filing_date""", Integer.class, proceeding);
        assertThat(early).isZero();
    }

    @Test
    void theFilingDateCannotMoveAfterAReferral() throws Exception {
        UUID proceeding = createProceeding(createCase("Referral bound"), "900");
        UUID referral = refer(proceeding, createExpert("Bound"), "2025-01-11", null);
        mvc.perform(action(referral, "cancel", "{\"version\":0}")).andExpect(status().isOk());

        // A cancelled referral still counts (ASSUMPTION), so the record never shows a referral before the filing.
        mvc.perform(moveFiling(proceeding, "2025-01-12"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("FILING_AFTER_DEPENDENT_REFERRAL"));
        assertThat(filingOf(proceeding)).isEqualTo(LocalDate.parse(FILED));
        mvc.perform(moveFiling(proceeding, "2025-01-11")).andExpect(status().isOk());
        assertChronology(proceeding);
    }

    @Test
    void aReferralMadeWhileTheFilingDateMovesLaterIsRefused() throws Exception {
        UUID proceeding = createProceeding(createCase("Race filing first"), "901");
        UUID expert = createExpert("Racer");
        List<Integer> statuses = race("update", "proceeding",
                () -> mvc.perform(moveFiling(proceeding, "2025-01-20")).andReturn(),
                () -> mvc.perform(postReferral(proceeding, referral(expert, "2025-01-15", null, null))).andReturn());
        assertThat(statuses).containsExactly(200, 422);
        assertThat(rows("expert_referral")).isZero();
        assertChronology(proceeding);
    }

    @Test
    void aFilingDateMovedWhileAReferralIsMadeIsRefused() throws Exception {
        UUID proceeding = createProceeding(createCase("Race referral first"), "902");
        UUID expert = createExpert("Racer two");
        List<Integer> statuses = race("insert", "expert_referral",
                () -> mvc.perform(postReferral(proceeding, referral(expert, "2025-01-15", null, null))).andReturn(),
                () -> mvc.perform(moveFiling(proceeding, "2025-01-20")).andReturn());
        assertThat(statuses).containsExactly(201, 422);
        assertThat(filingOf(proceeding)).isEqualTo(LocalDate.parse(FILED));
        assertChronology(proceeding);
    }

    private List<Integer> race(String event, String table, Callable<MvcResult> paused, Callable<MvcResult> other)
            throws Exception {
        jdbc.execute("""
                create function qad032_expert_pause() returns trigger language plpgsql as $$
                begin perform pg_sleep(1.5); return new; end $$""");
        jdbc.execute("create trigger qad032_expert_pause before " + event + " on " + table
                + " for each row execute function qad032_expert_pause()");
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<MvcResult> first = pool.submit(paused);
            awaitPause();
            Future<MvcResult> second = pool.submit(other);
            return List.of(first.get(30, TimeUnit.SECONDS).getResponse().getStatus(),
                    second.get(30, TimeUnit.SECONDS).getResponse().getStatus());
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
        for (String table : List.of("proceeding", "expert_referral")) {
            jdbc.execute("drop trigger if exists qad032_expert_pause on " + table);
        }
        jdbc.execute("drop function if exists qad032_expert_pause()");
    }
}
