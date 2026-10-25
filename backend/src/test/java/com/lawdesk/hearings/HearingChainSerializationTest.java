package com.lawdesk.hearings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;
import com.lawdesk.platform.TwoConnectionRace;
import com.lawdesk.platform.TwoConnectionRace.Write;

/**
 * QAD-038: two ordinary READ COMMITTED connections editing the two ends of an adjournment chain (parent hearing and
 * its successor) must not both commit a successor dated on or before its parent. Plain JDBC connections with explicit
 * transactions, no MockMvc: the first writer stays uncommitted, the second must <em>wait</em> (proved in
 * pg_stat_activity, not with sleeps) and, once the first commits, is refused with a check violation (23514). Each race
 * ends with exactly one winner and no invalid parent/successor pair, in both orders and also when the successor is
 * inserted while the parent is edited.
 */
class HearingChainSerializationTest extends HearingTestSupport {

    private static final String PARENT_DATE = "2025-02-01";
    private static final String SUCCESSOR_DATE = "2025-03-01";
    private static final String MOVE = "update hearing set hearing_date = ? where id = ?";
    private static final String INSERT_SUCCESSOR = """
            insert into hearing (case_id, proceeding_id, hearing_date, purpose, adjourned_from_id, created_by, updated_by)
            values (?, ?, ?, 'OTHER', ?, ?, ?)""";

    @Autowired
    DataSource dataSource;

    private record Chain(UUID caseId, UUID proceeding, UUID parent, UUID successor) {
    }

    /** A parent hearing on 2025-02-01 adjourned (through the API) to a successor on 2025-03-01. */
    private Chain chain(String number) throws Exception {
        UUID caseId = createCase("QAD-038 " + number);
        UUID proceeding = createProceeding(caseId, number);
        UUID parent = schedule(proceeding, PARENT_DATE);
        MvcResult adjourned = mvc.perform(postResult(parent, HearingApiTest.result("ADJOURNED", null, SUCCESSOR_DATE, 0)))
                .andExpect(status().isOk()).andReturn();
        UUID successor = UUID.fromString(JsonPath.read(adjourned.getResponse().getContentAsString(), "$.nextHearingId"));
        return new Chain(caseId, proceeding, parent, successor);
    }

    private int invalidPairs() {
        Integer count = jdbc.queryForObject("""
                select count(*) from hearing s join hearing p on p.id = s.adjourned_from_id
                where s.hearing_date <= p.hearing_date""", Integer.class);
        return count == null ? -1 : count;
    }

    private LocalDate dateOf(UUID hearing) {
        return jdbc.queryForObject("select hearing_date from hearing where id = ?", LocalDate.class, hearing);
    }

    private static LocalDate d(String date) {
        return LocalDate.parse(date);
    }

    @Test
    void aParentEditAndASuccessorEditCannotBothCommit() throws Exception {
        Chain chain = chain("830");
        // The parent moves to 02-20 (still before the successor on 03-01); the successor moves to 02-10 (still after
        // the parent's committed 02-01). Each is valid alone; together the successor precedes its parent.
        TwoConnectionRace.assertRefusedAfterWaiting(dataSource, jdbc, new Write(MOVE, d("2025-02-20"), chain.parent()),
                new Write(MOVE, d("2025-02-10"), chain.successor()));
        assertThat(dateOf(chain.parent())).isEqualTo(d("2025-02-20"));
        assertThat(dateOf(chain.successor())).isEqualTo(d(SUCCESSOR_DATE));
        assertThat(invalidPairs()).isZero();
    }

    @Test
    void aSuccessorEditAndAParentEditCannotBothCommit() throws Exception {
        Chain chain = chain("831");
        TwoConnectionRace.assertRefusedAfterWaiting(dataSource, jdbc,
                new Write(MOVE, d("2025-02-10"), chain.successor()), new Write(MOVE, d("2025-02-20"), chain.parent()));
        assertThat(dateOf(chain.successor())).isEqualTo(d("2025-02-10"));
        assertThat(dateOf(chain.parent())).isEqualTo(d(PARENT_DATE));
        assertThat(invalidPairs()).isZero();
    }

    @Test
    void aSuccessorInsertedWhileTheParentIsEditedCannotBothCommit() throws Exception {
        UUID caseId = createCase("QAD-038 insert");
        UUID proceeding = createProceeding(caseId, "832");
        UUID parent = schedule(proceeding, PARENT_DATE);
        TwoConnectionRace.assertRefusedAfterWaiting(dataSource, jdbc, new Write(MOVE, d("2025-02-20"), parent),
                new Write(INSERT_SUCCESSOR, caseId, proceeding, d("2025-02-10"), parent, CLERK, CLERK));
        assertThat(dateOf(parent)).isEqualTo(d("2025-02-20"));
        assertThat(rows("hearing")).isEqualTo(1);
        assertThat(invalidPairs()).isZero();
    }

    @Test
    void aParentEditedWhileItsSuccessorIsInsertedCannotBothCommit() throws Exception {
        UUID caseId = createCase("QAD-038 insert first");
        UUID proceeding = createProceeding(caseId, "833");
        UUID parent = schedule(proceeding, PARENT_DATE);
        TwoConnectionRace.assertRefusedAfterWaiting(dataSource, jdbc,
                new Write(INSERT_SUCCESSOR, caseId, proceeding, d("2025-02-10"), parent, CLERK, CLERK),
                new Write(MOVE, d("2025-02-20"), parent));
        assertThat(dateOf(parent)).isEqualTo(d(PARENT_DATE));
        assertThat(rows("hearing")).isEqualTo(2);
        assertThat(invalidPairs()).isZero();
    }

    @Test
    void compatibleWritersAreOnlyHeldUpUntilTheFirstCommits() throws Exception {
        Chain chain = chain("834");
        // The parent moves earlier and the successor later: both commit, one after the other (the second waited).
        String outcome = TwoConnectionRace.run(dataSource, jdbc, new Write(MOVE, d("2025-02-05"), chain.parent()),
                new Write(MOVE, d("2025-03-05"), chain.successor()));
        assertThat(outcome).isEqualTo("COMMITTED");
        assertThat(dateOf(chain.parent())).isEqualTo(d("2025-02-05"));
        assertThat(dateOf(chain.successor())).isEqualTo(d("2025-03-05"));
        assertThat(invalidPairs()).isZero();
    }

    @Test
    void aCancelledSuccessorStillBoundsItsParent() throws Exception {
        Chain chain = chain("835");
        mvc.perform(postResult(chain.successor(), HearingApiTest.result("CANCELLED", null, null, 0)))
                .andExpect(status().isOk());
        // The cancelled successor still follows its parent: moving the parent past it is refused (history is kept).
        assertThatThrownBy(() -> jdbc.update(MOVE, d("2025-03-10"), chain.parent()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(dateOf(chain.parent())).isEqualTo(d(PARENT_DATE));
    }
}
