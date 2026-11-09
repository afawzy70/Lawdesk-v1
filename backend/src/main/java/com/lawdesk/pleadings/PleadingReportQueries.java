package com.lawdesk.pleadings;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.cases.CaseAccessPolicy;

/**
 * The read-only pleadings port for reports (WP8, LD-058): the results of claims, the positions on defences and the
 * evidence still missing. Every figure counts a claim, a defence or an item <em>once</em>: when several judgments (first
 * instance, appeal, cassation) decided the same claim, only the latest decision is its current result, so awarded
 * amounts of the same claim at different levels are never added together. Each method takes a condition over
 * {@code legal_case} that already includes the visibility rule ({@code CaseReportQueries#scope}).
 */
@Component
@Transactional(readOnly = true)
public class PleadingReportQueries {

    private final JdbcClient jdbc;

    PleadingReportQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Claims whose current result is {@code result}; the awarded amount is the judged amount, its own kind (D5). */
    public record ResultRow(String result, long claims, BigDecimal awardedAmount) {
    }

    /** Every result in declaration order, zeros included, then claims no judgment has decided yet (null result). */
    public List<ResultRow> claimResults(CaseAccessPolicy.Predicate caseScope) {
        Map<String, ResultRow> found = new HashMap<>();
        jdbc.sql("select cur.result, count(*) as n, coalesce(sum(cur.awarded_amount), 0) as awarded from ("
                + " select distinct on (r.claim_id) r.claim_id, r.result, r.awarded_amount from claim_result r"
                + " join judgment j on j.id = r.judgment_id join proceeding p on p.id = j.proceeding_id"
                + " join legal_case on legal_case.id = p.case_id where " + caseScope.sql()
                + " order by r.claim_id, j.judgment_date desc, p.filing_date desc, j.created_at desc, j.id desc) cur"
                + " group by cur.result").params(caseScope.params()).query(rs -> {
                    found.put(rs.getString("result"), new ResultRow(rs.getString("result"), rs.getLong("n"),
                            rs.getBigDecimal("awarded").setScale(3)));
                });
        long undecided = jdbc.sql("select count(*) from claim c join legal_case on legal_case.id = c.case_id where "
                + caseScope.sql() + " and not exists (select 1 from claim_result r where r.claim_id = c.id)")
                .params(caseScope.params()).query(Long.class).single();
        List<ResultRow> rows = new ArrayList<>();
        for (ClaimResult result : ClaimResult.values()) {
            rows.add(found.getOrDefault(result.name(), new ResultRow(result.name(), 0, BigDecimal.ZERO.setScale(3))));
        }
        rows.add(new ResultRow(null, undecided, BigDecimal.ZERO.setScale(3)));
        return rows;
    }

    /** Defences of one kind whose current position is {@code position} (null: no judgment has addressed them yet). */
    public record PositionRow(String kind, String position, long defenses) {
    }

    /** Each defence once, at its latest recorded position, grouped by kind and position. */
    public List<PositionRow> defensePositions(CaseAccessPolicy.Predicate caseScope) {
        return jdbc.sql("select d.kind, cur.position, count(*) as n from defense d"
                + " join legal_case on legal_case.id = d.case_id left join ("
                + " select distinct on (x.defense_id) x.defense_id, x.position from defense_position x"
                + " join judgment j on j.id = x.judgment_id order by x.defense_id, j.judgment_date desc, j.created_at desc, j.id desc)"
                + " cur on cur.defense_id = d.id where " + caseScope.sql()
                + " group by d.kind, cur.position order by d.kind, cur.position nulls last")
                .params(caseScope.params())
                .query((rs, i) -> new PositionRow(rs.getString("kind"), rs.getString("position"), rs.getLong("n")))
                .list();
    }

    /** Evidence still missing: items the case needs or cannot obtain, with how many have no document attached. */
    public record MissingRow(String role, long items, long withoutDocument, long cases) {
    }

    /** Items with role REQUIRED or UNAVAILABLE, per role; {@code cases} counts distinct cases within the role only. */
    public List<MissingRow> missingEvidence(CaseAccessPolicy.Predicate caseScope) {
        return jdbc.sql("select e.role, count(*) as n, count(*) filter (where e.document_id is null) as nodoc,"
                + " count(distinct e.case_id) as cases from evidence_item e join legal_case on legal_case.id = e.case_id"
                + " where e.role in ('REQUIRED', 'UNAVAILABLE') and " + caseScope.sql() + " group by e.role order by e.role")
                .params(caseScope.params())
                .query((rs, i) -> new MissingRow(rs.getString("role"), rs.getLong("n"), rs.getLong("nodoc"),
                        rs.getLong("cases"))).list();
    }

    /** Defence memos a member prepared (a defence with a memo document), counted by its recorded preparer. */
    public record MemberMemos(UUID memberId, long memos) {
    }

    /** Defences with a memo document, per preparer, created in the period, over the cases in {@code caseScope}. */
    public List<MemberMemos> memosByPreparer(CaseAccessPolicy.Predicate caseScope, LocalDate from, LocalDate to) {
        return jdbc.sql("select d.prepared_by as member, count(*) as n from defense d"
                + " join legal_case on legal_case.id = d.case_id where d.prepared_by is not null"
                + " and d.memo_document_id is not null and cast(d.created_at as date) between :from and :to and "
                + caseScope.sql() + " group by d.prepared_by").params(caseScope.params()).param("from", from)
                .param("to", to).query((rs, i) -> new MemberMemos(rs.getObject("member", UUID.class), rs.getLong("n")))
                .list();
    }
}
