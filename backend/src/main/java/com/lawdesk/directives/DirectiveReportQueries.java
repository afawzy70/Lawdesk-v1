package com.lawdesk.directives;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.cases.CaseAccessPolicy;

/** The read-only directive port for reports (WP8, LD-058): objective counts of the directives addressed to a member. */
@Component
@Transactional(readOnly = true)
public class DirectiveReportQueries {

    private final JdbcClient jdbc;

    DirectiveReportQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Directives dated in the period addressed to a member: all, done, and open past their due date. */
    public record MemberDirectives(UUID memberId, long directives, long done, long overdue) {
    }

    public List<MemberDirectives> byAddressee(CaseAccessPolicy.Predicate caseScope, LocalDate from, LocalDate to,
            LocalDate today) {
        return jdbc.sql("select d.addressee_user_id as member, count(*) as n, count(*) filter (where d.status = 'DONE') as done,"
                + " count(*) filter (where d.status in ('NEW', 'IN_PROGRESS') and d.due_date < :today) as overdue"
                + " from directive d join legal_case on legal_case.id = d.case_id where d.addressee_user_id is not null"
                + " and d.directive_date between :from and :to and " + caseScope.sql()
                + " group by d.addressee_user_id").params(caseScope.params()).param("from", from).param("to", to)
                .param("today", today)
                .query((rs, i) -> new MemberDirectives(rs.getObject("member", UUID.class), rs.getLong("n"),
                        rs.getLong("done"), rs.getLong("overdue"))).list();
    }
}
