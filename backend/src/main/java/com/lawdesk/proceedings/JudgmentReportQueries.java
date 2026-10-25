package com.lawdesk.proceedings;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.lawdesk.proceedings.ProceedingReportQueries.CourtRef;

/**
 * The read-only port reports use to count and list judgments (architecture/CONTRACTS.md §8.3; Workstream E, Phase 6).
 * It never writes. A judgment is final when it was recorded as final (حكم نهائي / بات, LD-022).
 */
@Component
public class JudgmentReportQueries {

    /** Judgments of a period: how many, how many final, and per outcome (every outcome, zeros included). */
    public record JudgmentCounts(long recorded, long finalJudgments, Map<Outcome, Long> byOutcome) {
    }

    /**
     * Which judgments to read: those dated in the period (both days included) that match every given condition.
     * Empty sets and null values do not filter.
     */
    public record JudgmentFilter(LocalDate from, LocalDate to, Set<Outcome> outcomes, Set<Stage> stages,
            UUID courtId, Boolean finalJudgment) {

        public JudgmentFilter {
            outcomes = outcomes == null ? Set.of() : Set.copyOf(outcomes);
            stages = stages == null ? Set.of() : Set.copyOf(stages);
        }
    }

    /** Matching judgments counted in total, final, per outcome and per stage (all values, zeros included). */
    public record JudgmentSummary(long total, long finalJudgments, Map<Outcome, Long> byOutcome,
            Map<Stage, Long> byStage) {
    }

    /** One judgment as reports list it, with the proceeding and court it ended. */
    public record JudgmentRow(UUID id, LocalDate judgmentDate, Outcome outcome, boolean finalJudgment, Stage stage,
            CourtRef court, String courtCaseNumber, int courtCaseYear, UUID caseId) {
    }

    /** The orders reports may ask for; outcome and stage follow their declaration order. */
    public enum SortField {
        JUDGMENT_DATE("j.judgment_date"),
        OUTCOME(declarationOrder("j.outcome", Outcome.values())),
        STAGE(declarationOrder("p.stage", Stage.values()));

        private final String sql;

        SortField(String sql) {
            this.sql = sql;
        }
    }

    private static final String FROM =
            " from judgment j join proceeding p on p.id = j.proceeding_id join court c on c.id = p.court_id";

    private final JdbcClient jdbc;

    JudgmentReportQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** The judgments dated between the two days, both included. */
    public JudgmentCounts between(LocalDate from, LocalDate to) {
        JudgmentSummary summary = summarize(new JudgmentFilter(from, to, null, null, null, null));
        return new JudgmentCounts(summary.total(), summary.finalJudgments(), summary.byOutcome());
    }

    /** The matching judgments, counted. */
    public JudgmentSummary summarize(JudgmentFilter filter) {
        Map<Outcome, Long> byOutcome = zeros(Outcome.class);
        Map<Stage, Long> byStage = zeros(Stage.class);
        long[] totals = {0, 0};
        Where where = where(filter);
        jdbc.sql("select j.outcome, p.stage, count(*) as n, count(*) filter (where j.final) as final_n" + FROM
                        + where.sql + " group by j.outcome, p.stage")
                .params(where.params).query(rs -> {
                    long n = rs.getLong("n");
                    byOutcome.merge(Outcome.valueOf(rs.getString("outcome")), n, Long::sum);
                    byStage.merge(Stage.valueOf(rs.getString("stage")), n, Long::sum);
                    totals[0] += n;
                    totals[1] += rs.getLong("final_n");
                });
        return new JudgmentSummary(totals[0], totals[1], Collections.unmodifiableMap(byOutcome),
                Collections.unmodifiableMap(byStage));
    }

    /**
     * A page of the matching judgments in the given order; ties are broken by the newest judgment date, then id, so
     * paging never repeats or skips a judgment.
     */
    public List<JudgmentRow> list(JudgmentFilter filter, SortField sort, boolean descending, long offset, int limit) {
        Where where = where(filter);
        Map<String, Object> params = new HashMap<>(where.params);
        params.put("limit", limit);
        params.put("offset", offset);
        return jdbc.sql("""
                        select j.id, j.judgment_date, j.outcome, j.final, p.stage, p.court_case_number, p.court_case_year,
                               p.case_id, c.id as court_id, c.code, c.name_ar, c.name_en""" + FROM + where.sql
                        + " order by " + sort.sql + (descending ? " desc" : " asc")
                        + ", j.judgment_date desc, j.id limit :limit offset :offset")
                .params(params)
                .query((rs, n) -> new JudgmentRow(rs.getObject("id", UUID.class),
                        rs.getObject("judgment_date", LocalDate.class), Outcome.valueOf(rs.getString("outcome")),
                        rs.getBoolean("final"), Stage.valueOf(rs.getString("stage")),
                        new CourtRef(rs.getObject("court_id", UUID.class), rs.getString("code"),
                                rs.getString("name_ar"), rs.getString("name_en")),
                        rs.getString("court_case_number"), rs.getInt("court_case_year"),
                        rs.getObject("case_id", UUID.class)))
                .list();
    }

    private record Where(String sql, Map<String, Object> params) {
    }

    private static Where where(JudgmentFilter filter) {
        StringBuilder sql = new StringBuilder(" where j.judgment_date between :from and :to");
        Map<String, Object> params = new HashMap<>();
        params.put("from", filter.from());
        params.put("to", filter.to());
        if (!filter.outcomes().isEmpty()) {
            sql.append(" and j.outcome in (:outcomes)");
            params.put("outcomes", filter.outcomes().stream().map(Enum::name).toList());
        }
        if (!filter.stages().isEmpty()) {
            sql.append(" and p.stage in (:stages)");
            params.put("stages", filter.stages().stream().map(Enum::name).toList());
        }
        if (filter.courtId() != null) {
            sql.append(" and p.court_id = :court");
            params.put("court", filter.courtId());
        }
        if (filter.finalJudgment() != null) {
            sql.append(" and j.final = :final");
            params.put("final", filter.finalJudgment());
        }
        return new Where(sql.toString(), params);
    }

    private static String declarationOrder(String column, Enum<?>[] values) {
        return Arrays.stream(values).map(value -> "when '" + value.name() + "' then " + value.ordinal())
                .collect(Collectors.joining(" ", "case " + column + " ", " end"));
    }

    private static <E extends Enum<E>> Map<E, Long> zeros(Class<E> type) {
        Map<E, Long> counts = new EnumMap<>(type);
        for (E value : type.getEnumConstants()) {
            counts.put(value, 0L);
        }
        return counts;
    }
}
