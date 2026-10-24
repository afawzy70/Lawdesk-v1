package com.lawdesk.proceedings;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * The read-only port reports use to count proceedings and place cases on the judicial ladder
 * (architecture/CONTRACTS.md §8.3; Workstream E, Phase 6). It never writes. A proceeding awaits its judgment while no
 * judgment is recorded for it (one judgment ends a proceeding, LD-022).
 *
 * <p>ASSUMPTION (docs/delivery/PHASE-6-ACCEPTANCE.md, LD-031 criterion 2): a case's <em>current stage</em> is the
 * highest stage among its proceedings, and its current court is the court of the latest-filed proceeding at that
 * stage.
 */
@Component
public class ProceedingReportQueries {

    /** The proceedings of one stage, and how many of them have no judgment yet. */
    public record StageCount(Stage stage, long total, long awaitingJudgment) {
    }

    /** A court as reports name it. */
    public record CourtRef(UUID id, String code, String nameAr, String nameEn) {
    }

    /** Where a case stands: its current stage and court, and how many proceedings it has. */
    public record CasePosition(UUID caseId, Stage currentStage, CourtRef currentCourt, long proceedingCount) {
    }

    /** Ladder rank of a stage, from the {@link Stage} order. */
    private static final String RANK = Arrays.stream(Stage.values())
            .map(stage -> "when '" + stage.name() + "' then " + stage.ordinal())
            .collect(Collectors.joining(" ", "case p.stage ", " end"));

    /** One row per case that has proceedings: its current stage and court. */
    private static final String CURRENT = """
            select distinct on (p.case_id) p.case_id, p.stage, p.court_id, c.code, c.name_ar, c.name_en
            from proceeding p join court c on c.id = p.court_id
            %s
            order by p.case_id, %s desc, p.filing_date desc, p.created_at desc, p.id desc""";

    private final JdbcClient jdbc;

    ProceedingReportQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Every stage in ladder order, zeros included. */
    public List<StageCount> byStage() {
        Map<Stage, StageCount> counts = new EnumMap<>(Stage.class);
        jdbc.sql("""
                select p.stage, count(*) as total, count(*) filter (where j.id is null) as awaiting
                from proceeding p left join judgment j on j.proceeding_id = p.id
                group by p.stage""").query(rs -> {
                    Stage stage = Stage.valueOf(rs.getString("stage"));
                    counts.put(stage, new StageCount(stage, rs.getLong("total"), rs.getLong("awaiting")));
                });
        return Arrays.stream(Stage.values()).map(stage -> counts.getOrDefault(stage, new StageCount(stage, 0, 0)))
                .toList();
    }

    /**
     * The cases whose current stage is one of {@code currentStages} (any stage when empty) and that have a proceeding
     * before {@code courtId} (any court when null).
     */
    public Set<UUID> casesMatching(Set<Stage> currentStages, UUID courtId) {
        StringBuilder sql = new StringBuilder("select cur.case_id from (" + CURRENT.formatted("", RANK) + ") cur"
                + " where true");
        Map<String, Object> params = new HashMap<>();
        if (!currentStages.isEmpty()) {
            sql.append(" and cur.stage in (:stages)");
            params.put("stages", currentStages.stream().map(Enum::name).toList());
        }
        if (courtId != null) {
            sql.append(" and cur.case_id in (select case_id from proceeding where court_id = :court)");
            params.put("court", courtId);
        }
        return new HashSet<>(jdbc.sql(sql.toString()).params(params).query(UUID.class).list());
    }

    /** The position of each of these cases that has proceedings; cases without any are left out. */
    public Map<UUID, CasePosition> positions(Collection<UUID> caseIds) {
        Map<UUID, CasePosition> positions = new HashMap<>();
        if (caseIds.isEmpty()) {
            return positions;
        }
        jdbc.sql("select cur.*, (select count(*) from proceeding x where x.case_id = cur.case_id) as proceedings from ("
                        + CURRENT.formatted("where p.case_id = any(cast(:ids as uuid[]))", RANK) + ") cur")
                .param("ids", uuidArray(caseIds)).query(rs -> {
                    UUID caseId = rs.getObject("case_id", UUID.class);
                    positions.put(caseId, new CasePosition(caseId, Stage.valueOf(rs.getString("stage")),
                            new CourtRef(rs.getObject("court_id", UUID.class), rs.getString("code"),
                                    rs.getString("name_ar"), rs.getString("name_en")),
                            rs.getLong("proceedings")));
                });
        return positions;
    }

    /** How many of these cases stand at each stage; cases without proceedings are not counted. */
    public Map<Stage, Long> currentStageCounts(Collection<UUID> caseIds) {
        Map<Stage, Long> counts = new EnumMap<>(Stage.class);
        for (Stage stage : Stage.values()) {
            counts.put(stage, 0L);
        }
        if (caseIds.isEmpty()) {
            return counts;
        }
        jdbc.sql("select cur.stage, count(*) as n from ("
                        + CURRENT.formatted("where p.case_id = any(cast(:ids as uuid[]))", RANK) + ") cur"
                        + " group by cur.stage")
                .param("ids", uuidArray(caseIds))
                .query(rs -> {
                    counts.put(Stage.valueOf(rs.getString("stage")), rs.getLong("n"));
                });
        return counts;
    }

    /** A PostgreSQL array literal of ids; UUIDs hold only hex digits and hyphens, so nothing needs quoting. */
    private static String uuidArray(Collection<UUID> ids) {
        return ids.stream().map(UUID::toString).collect(Collectors.joining(",", "{", "}"));
    }
}
