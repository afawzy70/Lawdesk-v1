package com.lawdesk.experts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * QAD-033 applied to LD-025: the database itself keeps the dates of expert referrals and sessions consistent with the
 * proceeding's filing date and with each other, for any writer. Each refused statement leaves the data as it was.
 */
class ExpertDatabaseIntegrityTest extends ExpertTestSupport {

    @Autowired
    PlatformTransactionManager transactionManager;

    private LocalDate filingOf(UUID proceeding) {
        return jdbc.queryForObject("select filing_date from proceeding where id = ?", LocalDate.class, proceeding);
    }

    private LocalDate referralDateOf(UUID referral) {
        return jdbc.queryForObject("select referral_date from expert_referral where id = ?", LocalDate.class, referral);
    }

    private String constraintOf(Throwable thrown) {
        Throwable cause = thrown;
        while (cause != null && !(cause instanceof PSQLException)) {
            cause = cause.getCause();
        }
        assertThat(cause).as("a PostgreSQL error").isNotNull();
        PSQLException error = (PSQLException) cause;
        assertThat(error.getSQLState()).isEqualTo("23514");
        return error.getServerErrorMessage() == null ? "" : error.getServerErrorMessage().getConstraint();
    }

    private void refused(String constraint, String sql, Object... args) {
        assertThatThrownBy(() -> jdbc.update(sql, args)).isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> assertThat(constraintOf(e)).isEqualTo(constraint));
    }

    @Test
    void theDatabaseRefusesAFilingDateAfterAnExpertReferral() throws Exception {
        UUID proceeding = createProceeding(createCase("QAD-033 expert one"), "820");
        UUID referral = refer(proceeding, createExpert("Bound"), "2025-01-11", null);
        mvc.perform(action(referral, "cancel", "{\"version\":0}")).andExpect(status().isOk());

        // A cancelled referral still bounds the date (as in the API).
        refused("proceeding_filing_not_after_referral", "update proceeding set filing_date = date '2025-01-12' where id = ?",
                proceeding);
        assertThat(filingOf(proceeding)).isEqualTo(LocalDate.parse(FILED));
        assertThat(jdbc.update("update proceeding set filing_date = date '2025-01-11' where id = ?", proceeding)).isOne();
        assertThat(jdbc.update("update proceeding set filing_date = date '2025-01-02' where id = ?", proceeding)).isOne();
    }

    @Test
    void theDatabaseRefusesAReferralBeforeTheFilingDate() throws Exception {
        UUID caseId = createCase("QAD-033 expert two");
        UUID proceeding = createProceeding(caseId, "821");
        UUID expert = createExpert("Early");
        UUID referral = refer(proceeding, expert, "2025-01-11", null);

        refused("expert_referral_not_before_filing", "update expert_referral set referral_date = date '2025-01-09' where id = ?",
                referral);
        refused("expert_referral_not_before_filing", """
                insert into expert_referral (case_id, proceeding_id, expert_id, referral_date, created_by, updated_by)
                values (?, ?, ?, date '2025-01-09', ?, ?)""", caseId, proceeding, expert, CLERK, CLERK);
        assertThat(referralDateOf(referral)).isEqualTo(LocalDate.parse("2025-01-11"));
        assertThat(rows("expert_referral")).isEqualTo(1);
        assertThat(jdbc.update("update expert_referral set referral_date = date '2025-01-10' where id = ?", referral))
                .isOne();
    }

    @Test
    void theDatabaseKeepsSessionsOnOrAfterTheReferral() throws Exception {
        UUID proceeding = createProceeding(createCase("QAD-033 expert three"), "822");
        UUID referral = refer(proceeding, createExpert("Sessions"), "2025-01-15", null);
        mvc.perform(action(referral, "sessions", ExpertReferralApiTest.session("2025-01-20", null, null, null)))
                .andExpect(status().isCreated());

        refused("expert_session_not_before_referral", """
                insert into expert_session (referral_id, session_date, created_by) values (?, date '2025-01-14', ?)""",
                referral, CLERK);
        // The referral date cannot move past its earliest session; the session day itself is fine.
        refused("expert_session_not_before_referral", "update expert_referral set referral_date = date '2025-01-21' where id = ?",
                referral);
        refused("expert_session_not_before_referral", "update expert_session set session_date = date '2025-01-14' where referral_id = ?",
                referral);
        assertThat(referralDateOf(referral)).isEqualTo(LocalDate.parse("2025-01-15"));
        assertThat(jdbc.update("update expert_referral set referral_date = date '2025-01-20' where id = ?", referral))
                .isOne();
    }

    @Test
    void twoSqlWritersCannotInterleaveAReferralAndAFilingDateCorrection() throws Exception {
        UUID caseId = createCase("QAD-033 expert race");
        UUID proceeding = createProceeding(caseId, "823");
        UUID expert = createExpert("Racer");
        var transactions = new TransactionTemplate(transactionManager);
        String insert = """
                insert into expert_referral (case_id, proceeding_id, expert_id, referral_date, created_by, updated_by)
                values (?, ?, ?, date '2025-01-15', ?, ?)""";

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> writer = pool.submit(() -> transactions.executeWithoutResult(status -> {
                jdbc.update(insert, caseId, proceeding, expert, CLERK, CLERK);
                jdbc.execute("select pg_sleep(1.5)");
            }));
            awaitSleeper();
            Future<?> correction = pool.submit(() ->
                    jdbc.update("update proceeding set filing_date = date '2025-01-20' where id = ?", proceeding));
            writer.get(30, TimeUnit.SECONDS);
            assertThatThrownBy(() -> correction.get(30, TimeUnit.SECONDS)).hasRootCauseInstanceOf(PSQLException.class)
                    .satisfies(e -> assertThat(constraintOf(e)).isEqualTo("proceeding_filing_not_after_referral"));
        }
        assertThat(filingOf(proceeding)).isEqualTo(LocalDate.parse(FILED));
        jdbc.update("delete from expert_referral");

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> correction = pool.submit(() -> transactions.executeWithoutResult(status -> {
                jdbc.update("update proceeding set filing_date = date '2025-01-20' where id = ?", proceeding);
                jdbc.execute("select pg_sleep(1.5)");
            }));
            awaitSleeper();
            Future<?> writer = pool.submit(() -> jdbc.update(insert, caseId, proceeding, expert, CLERK, CLERK));
            correction.get(30, TimeUnit.SECONDS);
            assertThatThrownBy(() -> writer.get(30, TimeUnit.SECONDS)).hasRootCauseInstanceOf(PSQLException.class)
                    .satisfies(e -> assertThat(constraintOf(e)).isEqualTo("expert_referral_not_before_filing"));
        }
        assertThat(filingOf(proceeding)).isEqualTo(LocalDate.parse("2025-01-20"));
        assertThat(rows("expert_referral")).isZero();
    }

    private void awaitSleeper() throws InterruptedException {
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
        throw new AssertionError("the first writer never reached its pause");
    }
}
