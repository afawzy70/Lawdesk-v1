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
 * The due date of a judgment's enforcement file (requirements section 15 lists the enforcement dates among the calendar's
 * dates). A file with no due date has no entry (nothing is invented), and a file already executed or not required stays on
 * its day as DONE so the history of the calendar is kept. Seen only by those who may read enforcement, within their cases.
 */
@Component
class EnforcementCalendarSource implements CalendarSource {

    private final JdbcClient jdbc;

    EnforcementCalendarSource(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<String> types() {
        return Set.of("ENFORCEMENT");
    }

    @Override
    public List<CalendarEntry> find(CalendarQuery q, CalendarScope scope) {
        if (!scope.may(Permission.ENFORCEMENT_VIEW)) {
            return List.of();
        }
        CaseAccessPolicy.Predicate visible = scope.cases("e.case_id");
        String sql = """
                select e.id, e.case_id, c.reference_no, c.title as case_title, c.status as case_status, e.due_on, e.status,
                       e.executing_party, coalesce(e.responsible_member_id, c.primary_member_id) as owner_id,
                       c.primary_member_id, c.alternate_member_id
                from enforcement_file e join legal_case c on c.id = e.case_id
                where e.due_on between :from and :to and """ + " " + visible.sql()
                + BuiltInSources.filters(q, "coalesce(e.responsible_member_id, c.primary_member_id)", "e.case_id")
                + " order by e.due_on, e.id limit :limit";
        return jdbc.sql(sql).params(BuiltInSources.params(q, visible)).query((rs, n) -> {
            UUID owner = rs.getObject("owner_id", UUID.class);
            UUID alternate = rs.getObject("alternate_member_id", UUID.class);
            String status = rs.getString("status");
            boolean finished = "EXECUTED".equals(status) || "NOT_REQUIRED".equals(status);
            return CalendarEntry.of("ENFORCEMENT", "ENFORCEMENT_FILE", rs.getObject("id", UUID.class),
                    rs.getObject("case_id", UUID.class), rs.getString("reference_no"), rs.getString("case_title"),
                    rs.getString("case_status"), rs.getString("case_title"), status, null,
                    rs.getObject("due_on", LocalDate.class), null, null, owner, finished ? "DONE" : status, alternate,
                    BuiltInSources.people(owner, rs.getObject("primary_member_id", UUID.class), alternate));
        }).list();
    }
}
