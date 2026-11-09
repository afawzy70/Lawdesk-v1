package com.lawdesk.pleadings;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.api.FieldErrorException;
import com.lawdesk.security.ActorRef;

/**
 * What a judgment decided on each claim and each defence (sections 5, 9 and 17; scenarios S1, S3, S13). Every judgment has
 * its own rows, so recording a judgment of another stage never changes an earlier one. Reading lists every claim (and
 * defence) of the case, with no result where none is recorded yet; writing upserts the entries it is given.
 */
@Service
class ResultService {

    private static final String STAGE_ORDER = "case p.stage when 'FIRST_INSTANCE' then 0 when 'APPEAL' then 1 else 2 end";

    private final JdbcClient jdbc;
    private final CaseRows rows;

    ResultService(JdbcClient jdbc, CaseRows rows) {
        this.jdbc = jdbc;
        this.rows = rows;
    }

    // ---- claims -----------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    List<ResultRequests.ClaimResultView> claimResults(UUID judgmentId) {
        rows.view(rows.caseOfJudgment(judgmentId));
        return claimViews(judgmentId, rows.caseOfJudgment(judgmentId));
    }

    @Transactional
    List<ResultRequests.ClaimResultView> recordClaimResults(UUID judgmentId, ResultRequests.ClaimResults request) {
        UUID caseId = rows.caseOfJudgment(judgmentId);
        ActorRef actor = rows.change(caseId);
        Set<UUID> seen = new HashSet<>();
        for (int i = 0; i < request.results().size(); i++) {
            UUID claimId = request.results().get(i).claimId();
            if (!seen.add(claimId)) {
                throw new FieldErrorException("results[" + i + "].claimId", "Unique");
            }
            if (!jdbc.sql("select exists (select 1 from claim where id = :id and case_id = :c)").param("id", claimId)
                    .param("c", caseId).query(Boolean.class).single()) {
                throw new FieldErrorException("results[" + i + "].claimId", "NotInCase");
            }
        }
        for (ResultRequests.ClaimEntry entry : request.results()) {
            jdbc.sql("""
                    insert into claim_result (judgment_id, claim_id, result, operative, reasons, awarded_amount, notes,
                                              created_by, updated_by)
                    values (:judgment, :claim, :result, :operative, :reasons, :amount, :notes, :actor, :actor)
                    on conflict (judgment_id, claim_id) do update set result = excluded.result,
                        operative = excluded.operative, reasons = excluded.reasons,
                        awarded_amount = excluded.awarded_amount, notes = excluded.notes,
                        version = claim_result.version + 1, updated_at = now(), updated_by = excluded.updated_by""")
                    .param("judgment", judgmentId).param("claim", entry.claimId()).param("result", entry.result().name())
                    .param("operative", entry.operative()).param("reasons", entry.reasons())
                    .param("amount", entry.awardedAmount()).param("notes", entry.notes()).param("actor", actor.id())
                    .update();
        }
        rows.event(caseId, "CLAIM_RESULTS_RECORDED", request.results().size()
                + " claim result(s) recorded for the judgment of " + judgmentDate(judgmentId));
        rows.audit("CLAIM_RESULTS_RECORD", actor, caseId, "judgment=" + judgmentId + " claims=" + request.results().size());
        return claimViews(judgmentId, caseId);
    }

    @Transactional(readOnly = true)
    List<ResultRequests.CaseClaimResult> claimResultsOfCase(UUID caseId) {
        rows.view(caseId);
        return jdbc.sql("""
                select j.id as judgment_id, p.id as proceeding_id, p.stage, j.judgment_date, c.id as claim_id, c.seq,
                       r.result, r.operative, r.reasons, r.awarded_amount, r.notes
                from claim_result r join judgment j on j.id = r.judgment_id join proceeding p on p.id = j.proceeding_id
                     join claim c on c.id = r.claim_id
                where p.case_id = :case order by\s""" + STAGE_ORDER + ", j.judgment_date, j.created_at, c.seq")
                .param("case", caseId)
                .query((rs, n) -> new ResultRequests.CaseClaimResult(rs.getObject("judgment_id", UUID.class),
                        rs.getObject("proceeding_id", UUID.class), rs.getString("stage"),
                        rs.getObject("judgment_date", LocalDate.class), rs.getObject("claim_id", UUID.class),
                        rs.getInt("seq"), ClaimResult.valueOf(rs.getString("result")), rs.getString("operative"),
                        rs.getString("reasons"), rs.getBigDecimal("awarded_amount"), rs.getString("notes")))
                .list();
    }

    private List<ResultRequests.ClaimResultView> claimViews(UUID judgmentId, UUID caseId) {
        return jdbc.sql("""
                select c.id, c.seq, c.text, c.kind, c.status, r.result, r.operative, r.reasons, r.awarded_amount, r.notes,
                       r.version
                from claim c left join claim_result r on r.claim_id = c.id and r.judgment_id = :judgment
                where c.case_id = :case order by c.seq""").param("judgment", judgmentId).param("case", caseId)
                .query((rs, n) -> {
                    String result = rs.getString("result");
                    long version = rs.getLong("version");
                    return new ResultRequests.ClaimResultView(rs.getObject("id", UUID.class), rs.getInt("seq"),
                            rs.getString("text"), ClaimKind.valueOf(rs.getString("kind")),
                            ClaimStatus.valueOf(rs.getString("status")), result == null ? null : ClaimResult.valueOf(result),
                            rs.getString("operative"), rs.getString("reasons"), rs.getBigDecimal("awarded_amount"),
                            rs.getString("notes"), result == null ? null : version);
                }).list();
    }

    // ---- defences ---------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    List<ResultRequests.DefensePositionView> defensePositions(UUID judgmentId) {
        UUID caseId = rows.caseOfJudgment(judgmentId);
        rows.view(caseId);
        return defenseViews(judgmentId, caseId);
    }

    @Transactional
    List<ResultRequests.DefensePositionView> recordDefensePositions(UUID judgmentId, ResultRequests.DefensePositions request) {
        UUID caseId = rows.caseOfJudgment(judgmentId);
        ActorRef actor = rows.change(caseId);
        Set<UUID> seen = new HashSet<>();
        for (int i = 0; i < request.positions().size(); i++) {
            UUID defenseId = request.positions().get(i).defenseId();
            if (!seen.add(defenseId)) {
                throw new FieldErrorException("positions[" + i + "].defenseId", "Unique");
            }
            if (!jdbc.sql("select exists (select 1 from defense where id = :id and case_id = :c)").param("id", defenseId)
                    .param("c", caseId).query(Boolean.class).single()) {
                throw new FieldErrorException("positions[" + i + "].defenseId", "NotInCase");
            }
        }
        for (ResultRequests.DefenseEntry entry : request.positions()) {
            jdbc.sql("""
                    insert into defense_position (defense_id, judgment_id, position, reasons, operative, created_by,
                                                  updated_by)
                    values (:defense, :judgment, :position, :reasons, :operative, :actor, :actor)
                    on conflict (defense_id, judgment_id) do update set position = excluded.position,
                        reasons = excluded.reasons, operative = excluded.operative,
                        version = defense_position.version + 1, updated_at = now(), updated_by = excluded.updated_by""")
                    .param("defense", entry.defenseId()).param("judgment", judgmentId)
                    .param("position", entry.position().name()).param("reasons", entry.reasons())
                    .param("operative", entry.operative()).param("actor", actor.id()).update();
        }
        rows.event(caseId, "DEFENSE_POSITIONS_RECORDED", request.positions().size()
                + " defence position(s) recorded for the judgment of " + judgmentDate(judgmentId));
        rows.audit("DEFENSE_POSITIONS_RECORD", actor, caseId,
                "judgment=" + judgmentId + " defenses=" + request.positions().size());
        return defenseViews(judgmentId, caseId);
    }

    @Transactional(readOnly = true)
    List<ResultRequests.CaseDefensePosition> defensePositionsOfCase(UUID caseId) {
        rows.view(caseId);
        return new ArrayList<>(jdbc.sql("""
                select j.id as judgment_id, p.id as proceeding_id, p.stage, j.judgment_date, d.id as defense_id, d.seq,
                       r.position, r.reasons, r.operative
                from defense_position r join judgment j on j.id = r.judgment_id join proceeding p on p.id = j.proceeding_id
                     join defense d on d.id = r.defense_id
                where p.case_id = :case order by\s""" + STAGE_ORDER + ", j.judgment_date, j.created_at, d.seq")
                .param("case", caseId)
                .query((rs, n) -> new ResultRequests.CaseDefensePosition(rs.getObject("judgment_id", UUID.class),
                        rs.getObject("proceeding_id", UUID.class), rs.getString("stage"),
                        rs.getObject("judgment_date", LocalDate.class), rs.getObject("defense_id", UUID.class),
                        rs.getInt("seq"), DefensePosition.valueOf(rs.getString("position")), rs.getString("reasons"),
                        rs.getString("operative")))
                .list());
    }

    private List<ResultRequests.DefensePositionView> defenseViews(UUID judgmentId, UUID caseId) {
        return jdbc.sql("""
                select d.id, d.seq, d.title, d.kind, r.position, r.reasons, r.operative, r.version
                from defense d left join defense_position r on r.defense_id = d.id and r.judgment_id = :judgment
                where d.case_id = :case order by d.seq""").param("judgment", judgmentId).param("case", caseId)
                .query((rs, n) -> {
                    String position = rs.getString("position");
                    return new ResultRequests.DefensePositionView(rs.getObject("id", UUID.class), rs.getInt("seq"),
                            rs.getString("title"), DefenseKind.valueOf(rs.getString("kind")),
                            position == null ? null : DefensePosition.valueOf(position), rs.getString("reasons"),
                            rs.getString("operative"), position == null ? null : rs.getLong("version"));
                }).list();
    }

    private LocalDate judgmentDate(UUID judgmentId) {
        return jdbc.sql("select judgment_date from judgment where id = :id").param("id", judgmentId)
                .query(LocalDate.class).single();
    }
}
