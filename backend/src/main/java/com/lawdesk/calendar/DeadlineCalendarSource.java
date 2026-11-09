package com.lawdesk.calendar;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.lawdesk.cases.CaseAccessPolicy;
import com.lawdesk.security.Permission;

/**
 * The legal deadlines of WP4 on their due date (requirements section 15: appeal, grievance, memorandum and enforcement
 * dates). Only a deadline that exists as a date is shown: one entered by hand or approved; a proposal nobody approved has no
 * authority (decision D6). Done and missed deadlines stay on the calendar with their status. The deadlines raise their own
 * in-app reminders (DEADLINE_APPROACHING, DEADLINE_OVERDUE), so the date notifications of this package do not repeat them.
 */
@Component
class DeadlineCalendarSource implements CalendarSource {

    private final JdbcClient jdbc;

    DeadlineCalendarSource(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<String> types() {
        return Set.of("DEADLINE");
    }

    @Override
    public List<CalendarEntry> find(CalendarQuery q, CalendarScope scope) {
        if (!scope.may(Permission.DEADLINE_VIEW)) {
            return List.of();
        }
        CaseAccessPolicy.Predicate visible = scope.cases("d.case_id");
        String sql = """
                select d.id, d.case_id, c.reference_no, c.title as case_title, c.status as case_status, d.action, d.due_on,
                       d.status, d.calc_status, coalesce(d.owner_id, c.primary_member_id) as owner_id, c.primary_member_id,
                       c.alternate_member_id
                from deadline d join legal_case c on c.id = d.case_id
                where d.due_on between :from and :to and d.calc_status in ('MANUAL', 'APPROVED') and """ + " " + visible.sql()
                + BuiltInSources.filters(q, "coalesce(d.owner_id, c.primary_member_id)", "d.case_id")
                + " order by d.due_on, d.id limit :limit";
        return jdbc.sql(sql).params(BuiltInSources.params(q, visible)).query((rs, n) -> {
            UUID owner = rs.getObject("owner_id", UUID.class);
            UUID alternate = rs.getObject("alternate_member_id", UUID.class);
            return CalendarEntry.of("DEADLINE", "DEADLINE", rs.getObject("id", UUID.class), rs.getObject("case_id", UUID.class),
                    rs.getString("reference_no"), rs.getString("case_title"), rs.getString("case_status"),
                    rs.getString("action"), rs.getString("calc_status"), null, rs.getObject("due_on", LocalDate.class), null,
                    null, owner, rs.getString("status"), alternate,
                    BuiltInSources.people(owner, rs.getObject("primary_member_id", UUID.class), alternate));
        }).list();
    }
}
