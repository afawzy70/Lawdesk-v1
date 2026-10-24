package com.lawdesk.proceedings;

import static com.lawdesk.security.TestActors.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
 * QAD-024 (LD-020…LD-022): the judicial chronology — filing ≤ own judgment ≤ filing of every appeal or cassation that
 * attacks it — holds across every edit path, and under concurrent edits (docs/delivery/PHASE-4-ACCEPTANCE.md, LD-022
 * rule 4). Concurrent races are made deterministic with a test-only trigger that pauses one statement inside its
 * transaction, while the other request runs.
 */
class JudicialChronologyTest extends JudicialApiTestSupport {

    private static final String RULE = "JUDGMENT_AFTER_CHALLENGE_FILING";

    /** A child (appeal or cassation) filed before the judgment of the proceeding it attacks. */
    private static final String BROKEN_CHALLENGES = """
            select count(*) from proceeding c join judgment j on j.proceeding_id = c.parent_proceeding_id
            where c.filing_date < j.judgment_date""";

    /** A judgment dated before its own proceeding's filing. */
    private static final String BROKEN_JUDGMENTS = """
            select count(*) from judgment j join proceeding p on p.id = j.proceeding_id
            where j.judgment_date < p.filing_date""";

    private record Ladder(UUID caseId, UUID firstCourt, UUID appealCourt, UUID cassationCourt) {
    }

    private Ladder ladder(String suffix) throws Exception {
        return new Ladder(createCase("Chronology " + suffix), createCourt("FI-K" + suffix, "FIRST_INSTANCE"),
                createCourt("AP-K" + suffix, "APPEAL"), createCourt("CS-K" + suffix, "CASSATION"));
    }

    @AfterEach
    void dropPause() {
        jdbc.execute("drop trigger if exists qad024_pause on judgment");
        jdbc.execute("drop trigger if exists qad024_pause on proceeding");
        jdbc.execute("drop function if exists qad024_pause()");
    }

    // Direct API updates

    @Test
    void aJudgmentDateCannotMovePastAnAppealFiledAgainstIt() throws Exception {
        Ladder l = ladder("1");
        UUID first = recordProceeding(l.caseId(), proceeding("FIRST_INSTANCE", l.firstCourt(), "1", 2026,
                "2026-01-02", null));
        UUID judgment = recordJudgment(first, "2026-01-03");
        UUID appeal = recordProceeding(l.caseId(), proceeding("APPEAL", l.appealCourt(), "1", 2026, "2026-01-04",
                first));
        Map<String, Object> before = judgmentRow(judgment);

        mvc.perform(putJudgment(judgment, "2026-01-10", "IN_FAVOUR", 0))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.rule").value(RULE));

        // Nothing of the refused correction is written: date, outcome, version and audit columns stay.
        assertThat(judgmentRow(judgment)).isEqualTo(before);
        assertThat(before).containsEntry("judgment_date", java.sql.Date.valueOf("2026-01-03"))
                .containsEntry("version", 0L);
        assertThat(filingDate(appeal)).isEqualTo("2026-01-04");
        assertThat(rows("proceeding")).isEqualTo(2);
        assertThat(rows("judgment")).isEqualTo(1);
        assertConsistent();
    }

    @Test
    void anAppealJudgmentDateCannotMovePastACassationFiledAgainstIt() throws Exception {
        Ladder l = ladder("2");
        UUID first = recordProceeding(l.caseId(), proceeding("FIRST_INSTANCE", l.firstCourt(), "2", 2026,
                "2026-01-02", null));
        recordJudgment(first, "2026-01-03");
        UUID appeal = recordProceeding(l.caseId(), proceeding("APPEAL", l.appealCourt(), "2", 2026, "2026-01-04",
                first));
        UUID appealJudgment = recordJudgment(appeal, "2026-02-01");
        UUID cassation = recordProceeding(l.caseId(), proceeding("CASSATION", l.cassationCourt(), "2", 2026,
                "2026-03-01", appeal));
        Map<String, Object> before = judgmentRow(appealJudgment);

        mvc.perform(putJudgment(appealJudgment, "2026-03-02", "AGAINST", 0))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value(RULE));

        assertThat(judgmentRow(appealJudgment)).isEqualTo(before);
        assertThat(filingDate(cassation)).isEqualTo("2026-03-01");
        assertConsistent();
    }

    @Test
    void theEarliestOfSeveralAppealsBoundsTheJudgmentDate() throws Exception {
        Ladder l = ladder("3");
        UUID first = recordProceeding(l.caseId(), proceeding("FIRST_INSTANCE", l.firstCourt(), "3", 2026,
                "2026-01-02", null));
        UUID judgment = recordJudgment(first, "2026-01-03");
        recordProceeding(l.caseId(), proceeding("APPEAL", l.appealCourt(), "31", 2026, "2026-01-20", first));
        recordProceeding(l.caseId(), proceeding("APPEAL", l.appealCourt(), "32", 2026, "2026-01-08", first));

        mvc.perform(putJudgment(judgment, "2026-01-09", "AGAINST", 0))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value(RULE));
        assertThat(judgmentRow(judgment)).containsEntry("version", 0L);
        assertConsistent();
    }

    @Test
    void aCorrectionThatKeepsTheChronologySucceeds() throws Exception {
        Ladder l = ladder("4");
        UUID first = recordProceeding(l.caseId(), proceeding("FIRST_INSTANCE", l.firstCourt(), "4", 2026,
                "2026-01-02", null));
        UUID judgment = recordJudgment(first, "2026-01-03");
        UUID appeal = recordProceeding(l.caseId(), proceeding("APPEAL", l.appealCourt(), "4", 2026, "2026-01-10",
                first));
        recordJudgment(appeal, "2026-02-01");
        recordProceeding(l.caseId(), proceeding("CASSATION", l.cassationCourt(), "4", 2026, "2026-03-01", appeal));

        // Later, up to the appeal's own filing day (the same day is allowed).
        mvc.perform(putJudgment(judgment, "2026-01-10", "IN_FAVOUR", 0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.judgmentDate").value("2026-01-10"))
                .andExpect(jsonPath("$.outcome").value("IN_FAVOUR"))
                .andExpect(jsonPath("$.version").value(1));
        // Earlier is always fine for the children.
        mvc.perform(putJudgment(judgment, "2026-01-05", "IN_FAVOUR", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));
        assertThat(judgmentRow(judgment)).containsEntry("judgment_date", java.sql.Date.valueOf("2026-01-05"))
                .containsEntry("version", 2L);
        assertThat(filingDate(appeal)).isEqualTo("2026-01-10");
        assertConsistent();
    }

    // The other edit paths of the same chronology

    @Test
    void anAppealsFilingDateCannotMoveBeforeTheJudgmentItAttacks() throws Exception {
        Ladder l = ladder("5");
        UUID first = recordProceeding(l.caseId(), proceeding("FIRST_INSTANCE", l.firstCourt(), "5", 2026,
                "2026-01-02", null));
        recordJudgment(first, "2026-01-05");
        UUID appeal = recordProceeding(l.caseId(), proceeding("APPEAL", l.appealCourt(), "5", 2026, "2026-01-06",
                first));

        mvc.perform(putProceeding(appeal, l.appealCourt(), "5", "2026-01-04", 0))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("FILED_BEFORE_PARENT_JUDGMENT"));
        assertThat(filingDate(appeal)).isEqualTo("2026-01-06");
        assertThat(version("proceeding", appeal)).isZero();

        mvc.perform(putProceeding(appeal, l.appealCourt(), "5", "2026-01-05", 0))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        assertConsistent();
    }

    @Test
    void aParentProceedingsFilingDateStaysBoundByItsOwnJudgment() throws Exception {
        Ladder l = ladder("6");
        UUID first = recordProceeding(l.caseId(), proceeding("FIRST_INSTANCE", l.firstCourt(), "6", 2026,
                "2026-01-02", null));
        recordJudgment(first, "2026-01-05");
        recordProceeding(l.caseId(), proceeding("APPEAL", l.appealCourt(), "6", 2026, "2026-01-06", first));

        mvc.perform(putProceeding(first, l.firstCourt(), "6", "2026-01-07", 0))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.rule").value("JUDGMENT_BEFORE_FILING"));
        assertThat(filingDate(first)).isEqualTo("2026-01-02");

        mvc.perform(putProceeding(first, l.firstCourt(), "6", "2026-01-05", 0))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        assertConsistent();
    }

    // Concurrent modifications: one request is paused inside its transaction while the other runs

    @Test
    void anAppealFiledWhileTheJudgmentDateMovesLaterIsRefused() throws Exception {
        Ladder l = ladder("7");
        UUID first = recordProceeding(l.caseId(), proceeding("FIRST_INSTANCE", l.firstCourt(), "7", 2026,
                "2026-01-02", null));
        UUID judgment = recordJudgment(first, "2026-01-03");

        List<Integer> statuses = race("update", "judgment",
                () -> mvc.perform(putJudgment(judgment, "2026-01-10", "AGAINST", 0)).andReturn(),
                () -> mvc.perform(postProceeding(l.caseId(), proceeding("APPEAL", l.appealCourt(), "7", 2026,
                        "2026-01-05", first))).andReturn());

        assertThat(statuses).containsExactly(200, 422);
        assertThat(rows("proceeding")).isEqualTo(1);
        assertThat(judgmentRow(judgment)).containsEntry("version", 1L);
        assertConsistent();
    }

    @Test
    void aJudgmentDateMovedWhileAnAppealIsFiledIsRefused() throws Exception {
        Ladder l = ladder("8");
        UUID first = recordProceeding(l.caseId(), proceeding("FIRST_INSTANCE", l.firstCourt(), "8", 2026,
                "2026-01-02", null));
        UUID judgment = recordJudgment(first, "2026-01-03");

        List<Integer> statuses = race("insert", "proceeding",
                () -> mvc.perform(postProceeding(l.caseId(), proceeding("APPEAL", l.appealCourt(), "8", 2026,
                        "2026-01-05", first))).andReturn(),
                () -> mvc.perform(putJudgment(judgment, "2026-01-10", "AGAINST", 0)).andReturn());

        assertThat(statuses).containsExactly(201, 422);
        assertThat(rows("proceeding")).isEqualTo(2);
        assertThat(judgmentRow(judgment)).containsEntry("version", 0L)
                .containsEntry("judgment_date", java.sql.Date.valueOf("2026-01-03"));
        assertConsistent();
    }

    @Test
    void anAppealMovedEarlierWhileTheJudgmentDateMovesLaterIsRefused() throws Exception {
        Ladder l = ladder("9");
        UUID first = recordProceeding(l.caseId(), proceeding("FIRST_INSTANCE", l.firstCourt(), "9", 2026,
                "2026-01-02", null));
        UUID judgment = recordJudgment(first, "2026-01-03");
        UUID appeal = recordProceeding(l.caseId(), proceeding("APPEAL", l.appealCourt(), "9", 2026, "2026-01-20",
                first));

        List<Integer> statuses = race("update", "judgment",
                () -> mvc.perform(putJudgment(judgment, "2026-01-10", "AGAINST", 0)).andReturn(),
                () -> mvc.perform(putProceeding(appeal, l.appealCourt(), "9", "2026-01-05", 0)).andReturn());

        assertThat(statuses).containsExactly(200, 422);
        assertThat(filingDate(appeal)).isEqualTo("2026-01-20");
        assertConsistent();
    }

    @Test
    void aJudgmentDateMovedWhileTheAppealMovesEarlierIsRefused() throws Exception {
        Ladder l = ladder("10");
        UUID first = recordProceeding(l.caseId(), proceeding("FIRST_INSTANCE", l.firstCourt(), "10", 2026,
                "2026-01-02", null));
        UUID judgment = recordJudgment(first, "2026-01-03");
        UUID appeal = recordProceeding(l.caseId(), proceeding("APPEAL", l.appealCourt(), "10", 2026, "2026-01-20",
                first));

        List<Integer> statuses = race("update", "proceeding",
                () -> mvc.perform(putProceeding(appeal, l.appealCourt(), "10", "2026-01-05", 0)).andReturn(),
                () -> mvc.perform(putJudgment(judgment, "2026-01-10", "AGAINST", 0)).andReturn());

        assertThat(statuses).containsExactly(200, 422);
        assertThat(filingDate(appeal)).isEqualTo("2026-01-05");
        assertThat(judgmentRow(judgment)).containsEntry("version", 0L);
        assertConsistent();
    }

    @Test
    void aJudgmentRecordedWhileTheFilingDateMovesLaterIsRefused() throws Exception {
        Ladder l = ladder("11");
        UUID first = recordProceeding(l.caseId(), proceeding("FIRST_INSTANCE", l.firstCourt(), "11", 2026,
                "2026-01-02", null));

        List<Integer> statuses = race("update", "proceeding",
                () -> mvc.perform(putProceeding(first, l.firstCourt(), "11", "2026-01-08", 0)).andReturn(),
                () -> mvc.perform(postJudgment(first, judgment("2026-01-05", "AGAINST", null, false))).andReturn());

        assertThat(statuses).containsExactly(200, 422);
        assertThat(rows("judgment")).isZero();
        assertConsistent();
    }

    // Helpers

    /**
     * Runs {@code paused} until its {@code event} on {@code table} stops in a test-only trigger, then runs
     * {@code other} to completion while {@code paused} still holds its transaction open. Returns both statuses.
     */
    private List<Integer> race(String event, String table, Callable<MvcResult> paused, Callable<MvcResult> other)
            throws Exception {
        jdbc.execute("""
                create function qad024_pause() returns trigger language plpgsql as $$
                begin perform pg_sleep(1.5); return new; end $$""");
        jdbc.execute("create trigger qad024_pause before " + event + " on " + table
                + " for each row execute function qad024_pause()");
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<MvcResult> first = pool.submit(paused);
            awaitPause();
            Future<MvcResult> second = pool.submit(other);
            return List.of(first.get(30, TimeUnit.SECONDS).getResponse().getStatus(),
                    second.get(30, TimeUnit.SECONDS).getResponse().getStatus());
        } finally {
            dropPause();
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

    private void assertConsistent() {
        assertThat(jdbc.queryForObject(BROKEN_CHALLENGES, Integer.class)).isZero();
        assertThat(jdbc.queryForObject(BROKEN_JUDGMENTS, Integer.class)).isZero();
    }

    private MockHttpServletRequestBuilder putJudgment(UUID id, String date, String outcome, long version) {
        return put("/api/v1/judgments/" + id).with(as(CLERK, "clerk", Permission.JUDGMENT_MANAGE)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"judgmentDate":"%s","outcome":"%s","final":false,"version":%d}"""
                        .formatted(date, outcome, version));
    }

    private MockHttpServletRequestBuilder putProceeding(UUID id, UUID court, String number, String filingDate,
            long version) {
        return put("/api/v1/proceedings/" + id).with(as(CLERK, "clerk", Permission.PROCEEDING_MANAGE)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"courtId":"%s","courtCaseNumber":"%s","courtCaseYear":2026,"filingDate":"%s","version":%d}"""
                        .formatted(court, number, filingDate, version));
    }

    private Map<String, Object> judgmentRow(UUID id) {
        return jdbc.queryForMap("""
                select judgment_date, outcome, summary, final, version, updated_at, updated_by
                from judgment where id = ?""", id);
    }

    private String filingDate(UUID proceeding) {
        return jdbc.queryForObject("select filing_date::text from proceeding where id = ?", String.class, proceeding);
    }

    private long version(String table, UUID id) {
        Long v = jdbc.queryForObject("select version from " + table + " where id = ?", Long.class, id);
        return v == null ? -1 : v;
    }
}
