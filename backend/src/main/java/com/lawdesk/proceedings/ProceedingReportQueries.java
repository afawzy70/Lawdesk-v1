package com.lawdesk.proceedings;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * The read-only port reports use to count proceedings (architecture/CONTRACTS.md §8.3; Workstream E, Phase 6). It
 * never writes. A proceeding awaits its judgment while no judgment is recorded for it (one judgment ends a
 * proceeding, LD-022).
 */
@Component
public class ProceedingReportQueries {

    /** The proceedings of one stage, and how many of them have no judgment yet. */
    public record StageCount(Stage stage, long total, long awaitingJudgment) {
    }

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
}
