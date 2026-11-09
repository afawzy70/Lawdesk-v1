package com.lawdesk.calendar;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.lawdesk.cases.CaseAccessPolicy;
import com.lawdesk.security.Permission;

/**
 * The calendar's own sources: hearings, tasks, expert sessions and information-request dates. Each reads its module's table
 * and applies the case access rule of {@link CalendarScope} in SQL, so a case the user cannot see contributes nothing.
 */
final class BuiltInSources {

    private BuiltInSources() {
    }

    /** The recipients of a case entry: the owner, the specialist and the alternate. */
    static List<UUID> people(UUID... ids) {
        Set<UUID> out = new LinkedHashSet<>();
        for (UUID id : ids) {
            if (id != null) {
                out.add(id);
            }
        }
        return List.copyOf(out);
    }

    static LocalTime time(ResultSet rs, String column) throws SQLException {
        Time value = rs.getTime(column);
        return value == null ? null : value.toLocalTime().withSecond(0).withNano(0);
    }

    static Map<String, Object> params(CalendarQuery q, CaseAccessPolicy.Predicate... predicates) {
        Map<String, Object> params = new HashMap<>();
        for (CaseAccessPolicy.Predicate predicate : predicates) {
            params.putAll(predicate.params());
        }
        params.put("from", q.from());
        params.put("to", q.to());
        params.put("limit", q.limit());
        if (q.ownerId() != null) {
            params.put("owner", q.ownerId());
        }
        if (q.caseId() != null) {
            params.put("caseId", q.caseId());
        }
        return params;
    }

    static String filters(CalendarQuery q, String ownerExpression, String caseColumn) {
        return (q.ownerId() == null ? "" : " and " + ownerExpression + " = :owner")
                + (q.caseId() == null ? "" : " and " + caseColumn + " = :caseId");
    }

    /** Court, expert and investigation hearings, adjourned and past ones included. */
    @Component
    static final class Hearings implements CalendarSource {

        private final JdbcClient jdbc;

        Hearings(JdbcClient jdbc) {
            this.jdbc = jdbc;
        }

        @Override
        public Set<String> types() {
            return Set.of("HEARING", "EXPERT_SESSION", "INVESTIGATION", "OTHER");
        }

        @Override
        public List<CalendarEntry> find(CalendarQuery q, CalendarScope scope) {
            if (!scope.may(Permission.HEARING_VIEW)) {
                return List.of();
            }
            CaseAccessPolicy.Predicate visible = scope.cases("h.case_id");
            String sql = """
                    select h.id, h.case_id, c.reference_no, c.title as case_title, c.status as case_status,
                           h.hearing_date, h.hearing_time, h.kind, h.status, h.purpose, p.stage,
                           coalesce(h.responsible_member_id, c.primary_member_id) as owner_id,
                           c.primary_member_id, c.alternate_member_id
                    from hearing h join legal_case c on c.id = h.case_id join proceeding p on p.id = h.proceeding_id
                    where h.hearing_date between :from and :to and """ + " " + visible.sql()
                    + filters(q, "coalesce(h.responsible_member_id, c.primary_member_id)", "h.case_id")
                    + " order by h.hearing_date, h.hearing_time nulls first, h.id limit :limit";
            return jdbc.sql(sql).params(params(q, visible)).query((rs, n) -> {
                String type = switch (rs.getString("kind")) {
                    case "EXPERT" -> "EXPERT_SESSION";
                    case "INVESTIGATION" -> "INVESTIGATION";
                    case "OTHER" -> "OTHER";
                    default -> "HEARING";
                };
                UUID owner = rs.getObject("owner_id", UUID.class);
                UUID alternate = rs.getObject("alternate_member_id", UUID.class);
                return CalendarEntry.of(type, "HEARING", rs.getObject("id", UUID.class),
                        rs.getObject("case_id", UUID.class), rs.getString("reference_no"), rs.getString("case_title"),
                        rs.getString("case_status"), rs.getString("case_title"), rs.getString("purpose"),
                        rs.getString("stage"), rs.getObject("hearing_date", LocalDate.class), time(rs, "hearing_time"),
                        null, owner, rs.getString("status"), alternate,
                        people(owner, rs.getObject("primary_member_id", UUID.class), alternate));
            }).list();
        }
    }

    /** A task is on the calendar on its due date, in its assignee's calendar. */
    @Component
    static final class Tasks implements CalendarSource {

        private final JdbcClient jdbc;

        Tasks(JdbcClient jdbc) {
            this.jdbc = jdbc;
        }

        @Override
        public Set<String> types() {
            return Set.of("TASK");
        }

        @Override
        public List<CalendarEntry> find(CalendarQuery q, CalendarScope scope) {
            if (!scope.may(Permission.TASK_VIEW)) {
                return List.of();
            }
            CaseAccessPolicy.Predicate visible = scope.records("t.assignee_id", "t.created_by", "t.case_id");
            String sql = """
                    select t.id, t.case_id, c.reference_no, c.title as case_title, c.status as case_status, t.title,
                           t.due_date, t.status, t.priority, t.assignee_id, c.alternate_member_id
                    from task t left join legal_case c on c.id = t.case_id
                    where t.due_date between :from and :to and """ + " " + visible.sql()
                    + filters(q, "t.assignee_id", "t.case_id") + " order by t.due_date, t.id limit :limit";
            return jdbc.sql(sql).params(params(q, visible)).query((rs, n) -> {
                UUID assignee = rs.getObject("assignee_id", UUID.class);
                return CalendarEntry.of("TASK", "TASK", rs.getObject("id", UUID.class), rs.getObject("case_id", UUID.class),
                        rs.getString("reference_no"), rs.getString("case_title"), rs.getString("case_status"),
                        rs.getString("title"), rs.getString("priority"), null, rs.getObject("due_date", LocalDate.class),
                        null, null, assignee, rs.getString("status"), rs.getObject("alternate_member_id", UUID.class),
                        people(assignee));
            }).list();
        }
    }

    /** Sessions of the experts of a case. */
    @Component
    static final class ExpertSessions implements CalendarSource {

        private final JdbcClient jdbc;

        ExpertSessions(JdbcClient jdbc) {
            this.jdbc = jdbc;
        }

        @Override
        public Set<String> types() {
            return Set.of("EXPERT_SESSION");
        }

        @Override
        public List<CalendarEntry> find(CalendarQuery q, CalendarScope scope) {
            if (!scope.may(Permission.EXPERT_VIEW)) {
                return List.of();
            }
            CaseAccessPolicy.Predicate visible = scope.cases("r.case_id");
            String sql = """
                    select s.id, r.case_id, c.reference_no, c.title as case_title, c.status as case_status, e.name,
                           s.session_date, s.session_time, r.status, c.primary_member_id, c.alternate_member_id
                    from expert_session s join expert_referral r on r.id = s.referral_id
                         join expert e on e.id = r.expert_id join legal_case c on c.id = r.case_id
                    where s.session_date between :from and :to and """ + " " + visible.sql()
                    + filters(q, "c.primary_member_id", "r.case_id") + " order by s.session_date, s.session_time nulls first, s.id limit :limit";
            return jdbc.sql(sql).params(params(q, visible)).query((rs, n) -> {
                UUID owner = rs.getObject("primary_member_id", UUID.class);
                UUID alternate = rs.getObject("alternate_member_id", UUID.class);
                return CalendarEntry.of("EXPERT_SESSION", "EXPERT_SESSION", rs.getObject("id", UUID.class),
                        rs.getObject("case_id", UUID.class), rs.getString("reference_no"), rs.getString("case_title"),
                        rs.getString("case_status"), rs.getString("name"), null, null,
                        rs.getObject("session_date", LocalDate.class), time(rs, "session_time"), null, owner,
                        rs.getString("status"), alternate, people(owner, alternate));
            }).list();
        }
    }

    /** The date by which a department's reply to an information request is due, while the request is open. */
    @Component
    static final class InfoRequestDates implements CalendarSource {

        private final JdbcClient jdbc;

        InfoRequestDates(JdbcClient jdbc) {
            this.jdbc = jdbc;
        }

        @Override
        public Set<String> types() {
            return Set.of("INFO_REQUEST_DUE");
        }

        @Override
        public List<CalendarEntry> find(CalendarQuery q, CalendarScope scope) {
            if (!scope.may(Permission.INFO_REQUEST_VIEW)) {
                return List.of();
            }
            CaseAccessPolicy.Predicate visible = scope.cases("r.case_id");
            String sql = """
                    select r.id, r.case_id, c.reference_no, c.title as case_title, c.status as case_status, r.subject,
                           r.request_no, r.due_on, r.status,
                           coalesce(r.responsible_member_id, c.primary_member_id) as owner_id, c.alternate_member_id,
                           c.primary_member_id
                    from info_request r join legal_case c on c.id = r.case_id
                    where r.due_on between :from and :to
                      and r.status in ('SENT', 'AWAITING', 'PARTIAL', 'INCOMPLETE', 'OVERDUE') and """ + " " + visible.sql()
                    + filters(q, "coalesce(r.responsible_member_id, c.primary_member_id)", "r.case_id")
                    + " order by r.due_on, r.id limit :limit";
            List<CalendarEntry> out = new ArrayList<>();
            jdbc.sql(sql).params(params(q, visible)).query((rs, n) -> {
                UUID owner = rs.getObject("owner_id", UUID.class);
                UUID alternate = rs.getObject("alternate_member_id", UUID.class);
                return CalendarEntry.of("INFO_REQUEST_DUE", "INFO_REQUEST", rs.getObject("id", UUID.class),
                        rs.getObject("case_id", UUID.class), rs.getString("reference_no"), rs.getString("case_title"),
                        rs.getString("case_status"), rs.getString("subject"), rs.getString("request_no"), null,
                        rs.getObject("due_on", LocalDate.class), null, null, owner, rs.getString("status"), alternate,
                        people(owner, rs.getObject("primary_member_id", UUID.class), alternate));
            }).list().forEach(out::add);
            return out;
        }
    }
}
