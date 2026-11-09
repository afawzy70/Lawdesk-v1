package com.lawdesk.risk;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.cases.CaseAccessPolicy;

/**
 * The read-only risk port for reports (WP8, LD-058; architecture/CONTRACTS.md §8.3). The rule "a high-risk case" lives here:
 * the case's latest assessment version is HIGH or VERY_HIGH. An assessment is a decision aid, never a prediction.
 */
@Component
@Transactional(readOnly = true)
public class RiskReportQueries {

    private final JdbcClient jdbc;

    RiskReportQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Cases whose latest assessment is HIGH or VERY_HIGH, as a condition over {@code legal_case}. */
    public static CaseAccessPolicy.Predicate highRisk() {
        return new CaseAccessPolicy.Predicate("legal_case.id in (select ra.case_id from risk_assessment ra"
                + " where ra.level in ('HIGH', 'VERY_HIGH') and ra.version_no = (select max(m.version_no)"
                + " from risk_assessment m where m.case_id = ra.case_id))", Map.of());
    }

    /**
     * Cases of one level of their latest assessment (null level: cases with no assessment yet) and the potential loss
     * of those assessments. The potential loss is its own amount (decision D5); cases without a recorded loss add nothing.
     */
    public record LevelRow(String level, long cases, BigDecimal potentialLoss, long withoutLoss) {
    }

    /** Every level in severity order, zeros included, then the cases not assessed yet. */
    public List<LevelRow> byLevel(CaseAccessPolicy.Predicate caseScope) {
        Map<String, LevelRow> found = new java.util.HashMap<>();
        jdbc.sql("select a.level, count(*) as n, coalesce(sum(a.potential_loss), 0) as loss,"
                + " count(*) filter (where a.potential_loss is null) as unknown from risk_assessment a"
                + " join legal_case on legal_case.id = a.case_id where a.version_no = (select max(m.version_no)"
                + " from risk_assessment m where m.case_id = a.case_id) and " + caseScope.sql() + " group by a.level")
                .params(caseScope.params()).query(rs -> {
                    found.put(rs.getString("level"), new LevelRow(rs.getString("level"), rs.getLong("n"),
                            rs.getBigDecimal("loss").setScale(3), rs.getLong("unknown")));
                });
        long notAssessed = jdbc.sql("select count(*) from legal_case where " + caseScope.sql()
                + " and not exists (select 1 from risk_assessment a where a.case_id = legal_case.id)")
                .params(caseScope.params()).query(Long.class).single();
        java.util.ArrayList<LevelRow> rows = new java.util.ArrayList<>();
        for (String level : List.of("VERY_HIGH", "HIGH", "MEDIUM", "LOW")) {
            rows.add(found.getOrDefault(level, new LevelRow(level, 0, BigDecimal.ZERO.setScale(3), 0)));
        }
        rows.add(new LevelRow(null, notAssessed, BigDecimal.ZERO.setScale(3), 0));
        return rows;
    }
}
