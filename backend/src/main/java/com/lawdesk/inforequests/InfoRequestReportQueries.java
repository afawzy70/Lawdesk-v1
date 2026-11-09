package com.lawdesk.inforequests;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.cases.CaseAccessPolicy;

/**
 * The read-only information-request port for reports (WP8, LD-058). "Overdue" is the module's own rule: a request that
 * is waiting for a reply (sent, awaiting, partial, incomplete or flagged overdue) whose due date has passed.
 */
@Component
@Transactional(readOnly = true)
public class InfoRequestReportQueries {

    /** An overdue request as lists show it. */
    public record Item(UUID id, UUID caseId, String requestNo, String subject, String unitName, LocalDate dueOn,
            String status) {
    }

    private static final String WAITING = "('SENT', 'AWAITING', 'PARTIAL', 'INCOMPLETE', 'OVERDUE')";
    private static final String OVERDUE = "r.due_on is not null and r.due_on < :today and r.status in " + WAITING;

    private final JdbcClient jdbc;
    private final CaseAccessPolicy access;
    private final InfoRequestStore store;

    InfoRequestReportQueries(JdbcClient jdbc, CaseAccessPolicy access, InfoRequestStore store) {
        this.jdbc = jdbc;
        this.access = access;
        this.store = store;
    }

    /** Overdue requests of the cases the signed-in user may see. */
    public long overdueCount(LocalDate today) {
        CaseAccessPolicy.Predicate visible = access.visibleCaseIds("r.case_id");
        return jdbc.sql("select count(*) from info_request r where " + OVERDUE + " and " + visible.sql())
                .params(visible.params()).param("today", today).query(Long.class).single();
    }

    /** A page of overdue requests, most overdue first. */
    public List<Item> overdue(LocalDate today, long offset, int limit) {
        CaseAccessPolicy.Predicate visible = access.visibleCaseIds("r.case_id");
        return jdbc.sql("select r.id, r.case_id, r.request_no, r.subject, r.unit_name_snapshot, r.due_on, r.status"
                + " from info_request r where " + OVERDUE + " and " + visible.sql()
                + " order by r.due_on, r.request_no, r.id limit :limit offset :offset")
                .params(visible.params()).param("today", today).param("limit", limit).param("offset", offset)
                .query((rs, n) -> new Item(rs.getObject("id", UUID.class), rs.getObject("case_id", UUID.class),
                        rs.getString("request_no"), rs.getString("subject"), rs.getString("unit_name_snapshot"),
                        rs.getObject("due_on", LocalDate.class), rs.getString("status"))).list();
    }

    /** What one unit of the authority did with the requests addressed to it. */
    public record UnitRow(UUID unitId, String unitName, long requests, long replies, long answered, long overdue,
            long incomplete, long casesAffected, Double averageReplyDays) {
    }

    /** One row per unit over the requests of the cases in {@code caseScope} (a condition over {@code legal_case}). */
    public List<UnitRow> byUnit(CaseAccessPolicy.Predicate caseScope, LocalDate today) {
        Map<String, Object> params = new HashMap<>(caseScope.params());
        CaseAccessPolicy.Predicate scoped = new CaseAccessPolicy.Predicate(
                "r.case_id in (select legal_case.id from legal_case where " + caseScope.sql() + ")", params);
        return store.report(scoped, today).stream().map(u -> new UnitRow(u.unitId(), u.unitName(), u.requests(),
                u.replies(), u.answeredRequests(), u.overdue(), u.incompleteReplies(), u.affectedCases(), u.averageReplyDays())).toList();
    }
}
