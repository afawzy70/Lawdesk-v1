package com.lawdesk.pleadings;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.api.FieldErrorException;
import com.lawdesk.cases.CaseAccessPolicy;
import com.lawdesk.cases.CaseType;
import com.lawdesk.proceedings.Stage;

/**
 * Statistics of the results courts gave to claims and defences (sections 5 and 17), counted from {@code claim_result} and
 * {@code defense_position} themselves, never from a second store, and only over the cases the signed-in user may see.
 */
@Service
class StatisticsService {

    private static final String FROM = """
            from claim_result r join judgment j on j.id = r.judgment_id join proceeding p on p.id = j.proceeding_id
                 join legal_case c on c.id = p.case_id""";
    private static final String DEFENSE_FROM = """
            from defense_position r join judgment j on j.id = r.judgment_id join proceeding p on p.id = j.proceeding_id
                 join legal_case c on c.id = p.case_id join defense d on d.id = r.defense_id""";

    private final JdbcClient jdbc;
    private final CaseAccessPolicy access;

    StatisticsService(JdbcClient jdbc, CaseAccessPolicy access) {
        this.jdbc = jdbc;
        this.access = access;
    }

    private record Filter(String where, Map<String, Object> params) {
    }

    private Filter filter(String stage, String caseType, LocalDate from, LocalDate to) {
        if (stage != null) {
            try {
                Stage.valueOf(stage);
            } catch (IllegalArgumentException e) {
                throw new FieldErrorException("stage", "Known");
            }
        }
        if (caseType != null) {
            try {
                CaseType.valueOf(caseType);
            } catch (IllegalArgumentException e) {
                throw new FieldErrorException("caseType", "Known");
            }
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new FieldErrorException("from", "BeforeTo");
        }
        CaseAccessPolicy.Predicate visible = access.visibleCaseIds("p.case_id");
        Map<String, Object> params = new HashMap<>(visible.params());
        params.put("stage", stage);
        params.put("type", caseType);
        params.put("from", from);
        params.put("to", to);
        String where = " where " + visible.sql() + " and (cast(:stage as text) is null or p.stage = cast(:stage as text))"
                + " and (cast(:type as text) is null or c.case_type = cast(:type as text))"
                + " and (cast(:from as date) is null or j.judgment_date >= cast(:from as date))"
                + " and (cast(:to as date) is null or j.judgment_date <= cast(:to as date))";
        return new Filter(where, params);
    }

    @Transactional(readOnly = true)
    StatisticsRequests.ClaimResults claimResults(String stage, String caseType, LocalDate from, LocalDate to) {
        Filter f = filter(stage, caseType, from, to);
        Map<ClaimResult, long[]> counts = new HashMap<>();
        Map<ClaimResult, BigDecimal> totals = new HashMap<>();
        Map<String, Long> stages = new HashMap<>();
        List<StatisticsRequests.ByStageAndResult> cross = new ArrayList<>();
        jdbc.sql("select p.stage, r.result, count(*) as n, coalesce(sum(r.awarded_amount), 0) as total " + FROM + f.where()
                + " group by p.stage, r.result order by case p.stage when 'FIRST_INSTANCE' then 0 when 'APPEAL' then 1 else 2 end,"
                + " r.result").params(f.params()).query(rs -> {
            ClaimResult result = ClaimResult.valueOf(rs.getString("result"));
            long n = rs.getLong("n");
            counts.computeIfAbsent(result, k -> new long[1])[0] += n;
            totals.merge(result, rs.getBigDecimal("total"), BigDecimal::add);
            stages.merge(rs.getString("stage"), n, Long::sum);
            cross.add(new StatisticsRequests.ByStageAndResult(rs.getString("stage"), result, n));
        });
        List<StatisticsRequests.ByResult> byResult = new ArrayList<>();
        long total = 0;
        for (ClaimResult result : ClaimResult.values()) {
            long n = counts.containsKey(result) ? counts.get(result)[0] : 0;
            total += n;
            byResult.add(new StatisticsRequests.ByResult(result, n,
                    totals.getOrDefault(result, BigDecimal.ZERO).setScale(3, java.math.RoundingMode.UNNECESSARY)));
        }
        return new StatisticsRequests.ClaimResults(total, byResult, byStage(stages), cross);
    }

    @Transactional(readOnly = true)
    StatisticsRequests.DefensePositions defensePositions(String stage, String caseType, LocalDate from, LocalDate to) {
        Filter f = filter(stage, caseType, from, to);
        Map<DefensePosition, Long> positions = new HashMap<>();
        Map<String, Long> stages = new HashMap<>();
        List<StatisticsRequests.ByKindAndPosition> kinds = new ArrayList<>();
        jdbc.sql("select p.stage, d.kind, r.position, count(*) as n " + DEFENSE_FROM + f.where()
                + " group by p.stage, d.kind, r.position order by d.kind, r.position").params(f.params()).query(rs -> {
            DefensePosition position = DefensePosition.valueOf(rs.getString("position"));
            long n = rs.getLong("n");
            positions.merge(position, n, Long::sum);
            stages.merge(rs.getString("stage"), n, Long::sum);
            DefenseKind kind = DefenseKind.valueOf(rs.getString("kind"));
            kinds.stream().filter(k -> k.kind() == kind && k.position() == position).findFirst().ifPresentOrElse(
                    k -> kinds.set(kinds.indexOf(k), new StatisticsRequests.ByKindAndPosition(kind, position, k.count() + n)),
                    () -> kinds.add(new StatisticsRequests.ByKindAndPosition(kind, position, n)));
        });
        List<StatisticsRequests.ByPosition> byPosition = new ArrayList<>();
        long total = 0;
        for (DefensePosition position : DefensePosition.values()) {
            long n = positions.getOrDefault(position, 0L);
            total += n;
            byPosition.add(new StatisticsRequests.ByPosition(position, n));
        }
        // The defences themselves, not their positions: how often the same defence title is raised across the visible cases.
        CaseAccessPolicy.Predicate visible = access.visibleCaseIds("d.case_id");
        Map<String, Object> params = new HashMap<>(visible.params());
        params.put("type", caseType);
        List<StatisticsRequests.MostUsed> mostUsed = jdbc.sql("""
                select min(d.title) as title, count(*) as n from defense d join legal_case c on c.id = d.case_id
                where\s""" + visible.sql() + " and (cast(:type as text) is null or c.case_type = cast(:type as text))"
                + " group by lower(btrim(d.title)) order by n desc, lower(btrim(d.title)) limit 20").params(params)
                .query((rs, n) -> new StatisticsRequests.MostUsed(rs.getString("title"), rs.getLong("n"))).list();
        return new StatisticsRequests.DefensePositions(total, byPosition, byStage(stages), kinds, mostUsed);
    }

    private static List<StatisticsRequests.ByStage> byStage(Map<String, Long> stages) {
        List<StatisticsRequests.ByStage> out = new ArrayList<>();
        for (Stage stage : Stage.values()) {
            out.add(new StatisticsRequests.ByStage(stage.name(), stages.getOrDefault(stage.name(), 0L)));
        }
        return out;
    }
}
