package com.lawdesk.proceedings;

import java.time.LocalDate;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * The read-only port reports use to count and list judgments (architecture/CONTRACTS.md §8.3; Workstream E, Phase 6).
 * It never writes. A judgment is final when it was recorded as final (حكم نهائي / بات, LD-022).
 */
@Component
public class JudgmentReportQueries {

    /** Judgments of a period: how many, how many final, and per outcome (every outcome, zeros included). */
    public record JudgmentCounts(long recorded, long finalJudgments, Map<Outcome, Long> byOutcome) {
    }

    private final JdbcClient jdbc;

    JudgmentReportQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** The judgments dated between the two days, both included. */
    public JudgmentCounts between(LocalDate from, LocalDate to) {
        Map<Outcome, Long> byOutcome = new EnumMap<>(Outcome.class);
        for (Outcome outcome : Outcome.values()) {
            byOutcome.put(outcome, 0L);
        }
        long[] totals = {0, 0};
        jdbc.sql("""
                select outcome, count(*) as n, count(*) filter (where final) as final_n
                from judgment where judgment_date between :from and :to
                group by outcome""").param("from", from).param("to", to).query(rs -> {
                    long n = rs.getLong("n");
                    byOutcome.put(Outcome.valueOf(rs.getString("outcome")), n);
                    totals[0] += n;
                    totals[1] += rs.getLong("final_n");
                });
        return new JudgmentCounts(totals[0], totals[1], Collections.unmodifiableMap(byOutcome));
    }
}
