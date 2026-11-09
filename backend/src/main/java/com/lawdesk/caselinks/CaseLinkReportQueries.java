package com.lawdesk.caselinks;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.cases.CaseAccessPolicy;

/**
 * The read-only case-link port for reports (WP8, LD-058). A case is "linked" while it has an ACTIVE link on either side;
 * requested, rejected and ended links do not make a case linked. A link joins two cases and counts once.
 */
@Component
@Transactional(readOnly = true)
public class CaseLinkReportQueries {

    private final JdbcClient jdbc;

    CaseLinkReportQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Cases with an active link on either side, as a condition over {@code legal_case}. */
    public static CaseAccessPolicy.Predicate linked() {
        return new CaseAccessPolicy.Predicate("exists (select 1 from case_link cl where cl.status = 'ACTIVE'"
                + " and (cl.case_id = legal_case.id or cl.other_case_id = legal_case.id))", Map.of());
    }

    /** Links of one kind and state, each counted once, and how many of them are court decisions. */
    public record KindRow(String kind, String status, long links, long courtDecisions) {
    }

    /** The links with at least one side in {@code caseScope}, by kind and status. */
    public List<KindRow> byKind(CaseAccessPolicy.Predicate caseScope) {
        return jdbc.sql("select l.kind, l.status, count(*) as n, count(*) filter (where l.court_decision) as decided"
                + " from case_link l where exists (select 1 from legal_case where legal_case.id = l.case_id and "
                + caseScope.sql() + ") or exists (select 1 from legal_case where legal_case.id = l.other_case_id and "
                + caseScope.sql() + ") group by l.kind, l.status order by l.kind, l.status")
                .params(caseScope.params())
                .query((rs, i) -> new KindRow(rs.getString("kind"), rs.getString("status"), rs.getLong("n"),
                        rs.getLong("decided"))).list();
    }
}
