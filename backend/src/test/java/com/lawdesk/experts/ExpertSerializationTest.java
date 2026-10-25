package com.lawdesk.experts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.lawdesk.platform.TwoConnectionRace;
import com.lawdesk.platform.TwoConnectionRace.Write;

/**
 * QAD-038 applied to LD-025 (same class of race): a referral, its sessions and the filing date of its proceeding are
 * edited by two ordinary READ COMMITTED connections with explicit transactions, no MockMvc. The second writer must wait
 * (proved in pg_stat_activity) and be refused with a check violation once the first commits, in both orders; there is
 * exactly one winner and never a session dated before its referral.
 */
class ExpertSerializationTest extends ExpertTestSupport {

    private static final String MOVE_REFERRAL = "update expert_referral set referral_date = ? where id = ?";
    private static final String MOVE_SESSION = "update expert_session set session_date = ? where referral_id = ?";
    private static final String INSERT_SESSION = "insert into expert_session (referral_id, session_date, created_by) values (?, ?, ?)";

    @Autowired
    DataSource dataSource;

    /** A referral dated 2025-02-01 with one session on 2025-02-10. */
    private UUID referralWithASession(String number) throws Exception {
        UUID proceeding = createProceeding(createCase("QAD-038 " + number), number);
        UUID referral = refer(proceeding, createExpert("Racer " + number), "2025-02-01", null);
        mvc.perform(action(referral, "sessions", ExpertReferralApiTest.session("2025-02-10", null, null, null)))
                .andExpect(status().isCreated());
        return referral;
    }

    private int sessionsBeforeTheirReferral() {
        Integer count = jdbc.queryForObject("""
                select count(*) from expert_session s join expert_referral r on r.id = s.referral_id
                where s.session_date < r.referral_date""", Integer.class);
        return count == null ? -1 : count;
    }

    private static LocalDate d(String date) {
        return LocalDate.parse(date);
    }

    private LocalDate referralDateOf(UUID referral) {
        return jdbc.queryForObject("select referral_date from expert_referral where id = ?", LocalDate.class, referral);
    }

    @Test
    void aReferralEditAndASessionEditCannotBothCommit() throws Exception {
        UUID referral = referralWithASession("840");
        // The referral moves to 02-08 (still before the session on 02-10); the session moves to 02-05 (still after
        // the committed 02-01). Each is valid alone; together the session precedes its referral.
        TwoConnectionRace.assertRefusedAfterWaiting(dataSource, jdbc, new Write(MOVE_REFERRAL, d("2025-02-08"), referral),
                new Write(MOVE_SESSION, d("2025-02-05"), referral));
        assertThat(referralDateOf(referral)).isEqualTo(d("2025-02-08"));
        assertThat(sessionsBeforeTheirReferral()).isZero();
    }

    @Test
    void aSessionEditAndAReferralEditCannotBothCommit() throws Exception {
        UUID referral = referralWithASession("841");
        TwoConnectionRace.assertRefusedAfterWaiting(dataSource, jdbc, new Write(MOVE_SESSION, d("2025-02-05"), referral),
                new Write(MOVE_REFERRAL, d("2025-02-08"), referral));
        assertThat(referralDateOf(referral)).isEqualTo(d("2025-02-01"));
        assertThat(sessionsBeforeTheirReferral()).isZero();
    }

    @Test
    void aSessionInsertedWhileTheReferralIsEditedCannotBothCommit() throws Exception {
        UUID referral = referralWithASession("842");
        TwoConnectionRace.assertRefusedAfterWaiting(dataSource, jdbc, new Write(MOVE_REFERRAL, d("2025-02-08"), referral),
                new Write(INSERT_SESSION, referral, d("2025-02-05"), CLERK));
        assertThat(referralDateOf(referral)).isEqualTo(d("2025-02-08"));
        assertThat(rows("expert_session")).isEqualTo(1);
        assertThat(sessionsBeforeTheirReferral()).isZero();
    }

    @Test
    void aReferralEditedWhileASessionIsInsertedCannotBothCommit() throws Exception {
        UUID referral = referralWithASession("843");
        TwoConnectionRace.assertRefusedAfterWaiting(dataSource, jdbc,
                new Write(INSERT_SESSION, referral, d("2025-02-05"), CLERK), new Write(MOVE_REFERRAL, d("2025-02-08"), referral));
        assertThat(referralDateOf(referral)).isEqualTo(d("2025-02-01"));
        assertThat(rows("expert_session")).isEqualTo(2);
        assertThat(sessionsBeforeTheirReferral()).isZero();
    }

    @Test
    void aFilingDateCorrectionAndAReferralEditCannotBothCommit() throws Exception {
        UUID caseId = createCase("QAD-038 filing");
        UUID proceeding = createProceeding(caseId, "844");
        UUID referral = refer(proceeding, createExpert("Filer"), "2025-02-01", null);
        // The referral moves to 01-15 (after the filing on 01-10) while the filing date moves to 01-20 (before the
        // committed referral on 02-01). Each is valid alone; together the referral precedes the filing.
        TwoConnectionRace.assertRefusedAfterWaiting(dataSource, jdbc, new Write(MOVE_REFERRAL, d("2025-01-15"), referral),
                new Write("update proceeding set filing_date = ? where id = ?", d("2025-01-20"), proceeding));
        assertThat(referralDateOf(referral)).isEqualTo(d("2025-01-15"));
        assertThat(jdbc.queryForObject("select filing_date from proceeding where id = ?", LocalDate.class, proceeding))
                .isEqualTo(d(FILED));
    }
}
