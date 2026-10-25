package com.lawdesk.hearings;

import static com.lawdesk.security.TestActors.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.jayway.jsonpath.JsonPath;
import com.lawdesk.security.Permission;

/**
 * QAD-033: the database itself keeps the hearing chronology and the proceeding/case relation, not only the API. Each
 * statement below is the direct SQL an independent QA probe ran as the schema owner; every one must be refused with a
 * check violation naming the invariant, and leave the data as it was. The API keeps answering its 422 business rules
 * first (its pre-check runs under the proceeding row lock), so a refusal by the database never reaches a client.
 */
class HearingDatabaseIntegrityTest extends HearingTestSupport {

    @Autowired
    PlatformTransactionManager transactionManager;

    private LocalDate filingOf(UUID proceeding) {
        return jdbc.queryForObject("select filing_date from proceeding where id = ?", LocalDate.class, proceeding);
    }

    private LocalDate dateOf(UUID hearing) {
        return jdbc.queryForObject("select hearing_date from hearing where id = ?", LocalDate.class, hearing);
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

    /** Runs the statement, expects the database to refuse it as {@code constraint}, and leaves nothing changed. */
    private void refused(String constraint, String sql, Object... args) {
        assertThatThrownBy(() -> jdbc.update(sql, args)).isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> assertThat(constraintOf(e)).isEqualTo(constraint));
    }

    // QAD-033 probe 1: UPDATE proceeding SET filing_date = <after an existing hearing>

    @Test
    void theDatabaseRefusesAFilingDateAfterAnExistingHearing() throws Exception {
        UUID proceeding = createProceeding(createCase("QAD-033 one"), "810");
        UUID hearing = schedule(proceeding, "2025-01-11");

        refused("proceeding_filing_not_after_hearing", "update proceeding set filing_date = date '2025-01-12' where id = ?",
                proceeding);
        assertThat(filingOf(proceeding)).isEqualTo(LocalDate.parse(FILED));
        assertThat(dateOf(hearing)).isEqualTo(LocalDate.parse("2025-01-11"));

        // The same day as the earliest hearing, and any earlier day, are valid.
        assertThat(jdbc.update("update proceeding set filing_date = date '2025-01-11' where id = ?", proceeding)).isOne();
        assertThat(jdbc.update("update proceeding set filing_date = date '2025-01-02' where id = ?", proceeding)).isOne();
    }

    @Test
    void aCancelledHearingStillBoundsTheFilingDateInTheDatabase() throws Exception {
        UUID proceeding = createProceeding(createCase("QAD-033 cancelled"), "811");
        UUID hearing = schedule(proceeding, "2025-01-11");
        mvc.perform(postResult(hearing, HearingApiTest.result("CANCELLED", null, null, 0))).andExpect(status().isOk());
        refused("proceeding_filing_not_after_hearing", "update proceeding set filing_date = date '2025-01-12' where id = ?",
                proceeding);
    }

    // QAD-033 probe 2: UPDATE hearing SET hearing_date = <before the proceeding's filing date>

    @Test
    void theDatabaseRefusesAHearingBeforeTheFilingDate() throws Exception {
        UUID proceeding = createProceeding(createCase("QAD-033 two"), "812");
        UUID hearing = schedule(proceeding, "2025-01-11");
        UUID caseId = jdbc.queryForObject("select case_id from proceeding where id = ?", UUID.class, proceeding);

        refused("hearing_not_before_filing", "update hearing set hearing_date = date '2025-01-09' where id = ?", hearing);
        assertThat(dateOf(hearing)).isEqualTo(LocalDate.parse("2025-01-11"));
        // Inserting one directly is refused too.
        refused("hearing_not_before_filing", """
                insert into hearing (case_id, proceeding_id, hearing_date, purpose, created_by, updated_by)
                values (?, ?, date '2025-01-09', 'OTHER', ?, ?)""", caseId, proceeding, CLERK, CLERK);
        assertThat(rows("hearing")).isEqualTo(1);

        // The filing day itself is valid.
        assertThat(jdbc.update("update hearing set hearing_date = date '2025-01-10' where id = ?", hearing)).isOne();
    }

    // QAD-033 probe 3: UPDATE proceeding SET case_id = <another case>

    @Test
    void aProceedingNeverChangesItsCase() throws Exception {
        UUID caseId = createCase("QAD-033 three");
        UUID other = createCase("QAD-033 other");
        UUID proceeding = createProceeding(caseId, "814");
        schedule(proceeding, "2025-01-11");

        refused("proceeding_case_immutable", "update proceeding set case_id = ? where id = ?", other, proceeding);
        assertThat(jdbc.queryForObject("select case_id from proceeding where id = ?", UUID.class, proceeding))
                .isEqualTo(caseId);
        assertThat(jdbc.queryForObject("""
                select count(*) from hearing h join proceeding p on p.id = h.proceeding_id
                where h.case_id <> p.case_id""", Integer.class)).isZero();
        // Naming the same case again is no change.
        assertThat(jdbc.update("update proceeding set case_id = ? where id = ?", caseId, proceeding)).isOne();
    }

    // The adjournment chain: the next hearing always follows the one it continues.

    @Test
    void theDatabaseKeepsAnAdjournedChainInOrder() throws Exception {
        UUID proceeding = createProceeding(createCase("QAD-033 chain"), "815");
        UUID first = schedule(proceeding, "2025-01-11");
        MvcResult adjourned = mvc.perform(postResult(first, HearingApiTest.result("ADJOURNED", null, "2025-02-01", 0)))
                .andExpect(status().isOk()).andReturn();
        UUID next = UUID.fromString(JsonPath.read(adjourned.getResponse().getContentAsString(), "$.nextHearingId"));

        refused("hearing_follows_adjourned", "update hearing set hearing_date = date '2025-01-10' where id = ?", next);
        refused("hearing_follows_adjourned", "update hearing set hearing_date = date '2025-02-02' where id = ?", first);
        assertThat(dateOf(first)).isEqualTo(LocalDate.parse("2025-01-11"));
        assertThat(dateOf(next)).isEqualTo(LocalDate.parse("2025-02-01"));
        assertThat(jdbc.update("update hearing set hearing_date = date '2025-01-20' where id = ?", first)).isOne();
    }

    // Two direct-SQL writers racing, without the API's lock: the triggers serialise them on the proceeding row.

    @Test
    void twoSqlWritersCannotInterleaveAHearingAndAFilingDateCorrection() throws Exception {
        UUID caseId = createCase("QAD-033 race");
        UUID proceeding = createProceeding(caseId, "817");
        var transactions = new TransactionTemplate(transactionManager);
        String insert = """
                insert into hearing (case_id, proceeding_id, hearing_date, purpose, created_by, updated_by)
                values (?, ?, date '2025-01-15', 'OTHER', ?, ?)""";

        // The hearing is written first and its transaction stays open: the correction waits, then sees the hearing.
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> writer = pool.submit(() -> transactions.executeWithoutResult(status -> {
                jdbc.update(insert, caseId, proceeding, CLERK, CLERK);
                jdbc.execute("select pg_sleep(1.5)");
            }));
            awaitSleeper();
            Future<?> correction = pool.submit(() ->
                    jdbc.update("update proceeding set filing_date = date '2025-01-20' where id = ?", proceeding));
            writer.get(30, TimeUnit.SECONDS);
            assertThatThrownBy(() -> correction.get(30, TimeUnit.SECONDS)).hasRootCauseInstanceOf(PSQLException.class)
                    .satisfies(e -> assertThat(constraintOf(e)).isEqualTo("proceeding_filing_not_after_hearing"));
        }
        assertThat(filingOf(proceeding)).isEqualTo(LocalDate.parse(FILED));
        assertThat(rows("hearing")).isEqualTo(1);
        jdbc.update("delete from hearing");

        // The correction is under way first: the hearing waits for it, then sees the new filing date.
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> correction = pool.submit(() -> transactions.executeWithoutResult(status -> {
                jdbc.update("update proceeding set filing_date = date '2025-01-20' where id = ?", proceeding);
                jdbc.execute("select pg_sleep(1.5)");
            }));
            awaitSleeper();
            Future<?> writer = pool.submit(() -> jdbc.update(insert, caseId, proceeding, CLERK, CLERK));
            correction.get(30, TimeUnit.SECONDS);
            assertThatThrownBy(() -> writer.get(30, TimeUnit.SECONDS)).hasRootCauseInstanceOf(PSQLException.class)
                    .satisfies(e -> assertThat(constraintOf(e)).isEqualTo("hearing_not_before_filing"));
        }
        assertThat(filingOf(proceeding)).isEqualTo(LocalDate.parse("2025-01-20"));
        assertThat(rows("hearing")).isZero();
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

    // The API keeps answering its business rules first.

    @Test
    void theApiStillAnswers422NotAServerError() throws Exception {
        UUID proceeding = createProceeding(createCase("QAD-033 api"), "816");
        UUID hearing = schedule(proceeding, "2025-01-11");
        UUID court = jdbc.queryForObject("select court_id from proceeding where id = ?", UUID.class, proceeding);

        mvc.perform(put("/api/v1/proceedings/" + proceeding).with(as(Permission.PROCEEDING_MANAGE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courtId":"%s","courtCaseNumber":"816","courtCaseYear":2025,"filingDate":"2025-01-12",
                                 "version":0}""".formatted(court)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("FILING_AFTER_DEPENDENT_HEARING"));
        mvc.perform(put("/api/v1/hearings/" + hearing).with(as(Permission.HEARING_MANAGE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(HearingApiTest.update("2025-01-09", null, "PLEADING", null, 0)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("HEARING_BEFORE_FILING"));
        // The same-day edit through the API still works.
        mvc.perform(put("/api/v1/proceedings/" + proceeding).with(as(Permission.PROCEEDING_MANAGE)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courtId":"%s","courtCaseNumber":"816","courtCaseYear":2025,"filingDate":"2025-01-11",
                                 "version":0}""".formatted(court)))
                .andExpect(status().isOk());
        assertThat(filingOf(proceeding)).isEqualTo(LocalDate.parse("2025-01-11"));
    }
}
