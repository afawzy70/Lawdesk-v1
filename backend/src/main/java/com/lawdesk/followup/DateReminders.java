package com.lawdesk.followup;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.lawdesk.calendar.CalendarEngine;
import com.lawdesk.calendar.CalendarEntry;
import com.lawdesk.calendar.CalendarQuery;
import com.lawdesk.calendar.CalendarScope;
import com.lawdesk.calendar.CalendarTime;
import com.lawdesk.notifications.NotificationPublisher;
import com.lawdesk.notifications.NotificationTypes;

/**
 * The notifications that depend on dates and on what happened (requirements section 32; decision D11, in-app only). It
 * reads the calendar as the system, so a new kind of date added to the calendar (the legal deadlines of a later work
 * package) is announced without any change here: an entry of type APPEAL_DEADLINE, HEARING, INVESTIGATION or
 * EXPERT_SESSION ahead raises its notification for the people the entry names. Nothing is announced for a date that does
 * not exist, and every notification is stored once per record, date and person.
 *
 * <p>ASSUMPTIONS (not in the requirements, adjustable): dates are announced {@code upcomingDays} ahead (3, like a task
 * "due soon"); a new case or judgment is "new" for {@code recentDays} (7); conflicts are looked for {@value #CONFLICT_DAYS}
 * days ahead.
 */
@Component
public class DateReminders {

    static final int CONFLICT_DAYS = 30;

    /** Statuses after which an entry no longer lies ahead as something to attend or meet. */
    private static final Set<String> DONE = Set.of("CANCELLED", "DONE", "HELD", "ADJOURNED", "REPORT_SUBMITTED", "COMPLETED");

    private final CalendarEngine calendar;
    private final NotificationPublisher notifications;
    private final JdbcClient jdbc;
    private final int upcomingDays;
    private final int recentDays;

    DateReminders(CalendarEngine calendar, NotificationPublisher notifications, JdbcClient jdbc,
            @Value("${lawdesk.notifications.upcoming-days:3}") int upcomingDays,
            @Value("${lawdesk.notifications.recent-days:7}") int recentDays) {
        this.calendar = calendar;
        this.notifications = notifications;
        this.jdbc = jdbc;
        this.upcomingDays = upcomingDays;
        this.recentDays = recentDays;
    }

    /** @return the number of notifications newly stored */
    public int raise() {
        LocalDate today = CalendarTime.today();
        return upcoming(today) + conflicts(today) + withoutResult(today) + newCases() + newJudgments()
                + missingDocuments() + evidenceGaps();
    }

    // ---- dates ahead -------------------------------------------------------------------------------------------

    int upcoming(LocalDate today) {
        CalendarQuery query = new CalendarQuery(today, today.plusDays(upcomingDays), null, null, Set.of(),
                CalendarEngine.MAX_ENTRIES);
        int raised = 0;
        for (CalendarEntry entry : calendar.collect(query, CalendarScope.system()).entries()) {
            String type = switch (entry.type()) {
                case "HEARING", "INVESTIGATION" -> NotificationTypes.HEARING_UPCOMING;
                case "EXPERT_SESSION" -> NotificationTypes.EXPERT_SESSION_UPCOMING;
                case "APPEAL_DEADLINE" -> NotificationTypes.APPEAL_DEADLINE;
                default -> null;
            };
            if (type == null || entry.status() != null && DONE.contains(entry.status())) {
                continue;
            }
            Map<String, String> params = new TreeMap<>();
            params.put("date", entry.date().toString());
            if (entry.startTime() != null) {
                params.put("time", entry.startTime().toString());
            }
            if (entry.caseReferenceNo() != null) {
                params.put("caseReferenceNo", entry.caseReferenceNo());
            }
            params.put("title", entry.title());
            String token = entry.date() + (entry.startTime() == null ? "" : "T" + entry.startTime());
            for (UUID person : entry.recipients()) {
                if (notifications.publish(person, type, entry.caseId(), entry.refType() + ":" + entry.refId(), params,
                        token)) {
                    raised++;
                }
            }
        }
        return raised;
    }

    /** Dates of one member that cannot both be attended, in the next {@value #CONFLICT_DAYS} days. */
    private int conflicts(LocalDate today) {
        CalendarQuery query = new CalendarQuery(today, today.plusDays(CONFLICT_DAYS), null, null, Set.of(),
                CalendarEngine.MAX_ENTRIES);
        int raised = 0;
        for (CalendarEngine.Conflict conflict : calendar.collect(query, CalendarScope.system()).conflicts()) {
            List<String> ids = new ArrayList<>(conflict.entries().stream().map(CalendarEntry::id).sorted().toList());
            String token = UUID.nameUUIDFromBytes(String.join(",", ids).getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .toString();
            Map<String, String> params = new TreeMap<>();
            params.put("date", conflict.date().toString());
            params.put("time", CalendarTime.time(conflict.startsAt()).toString());
            params.put("count", String.valueOf(ids.size()));
            if (notifications.publish(conflict.ownerId(), NotificationTypes.DATE_CONFLICT, null,
                    "CALENDAR:" + conflict.date(), params, token)) {
                raised++;
            }
        }
        return raised;
    }

    // ---- what happened -----------------------------------------------------------------------------------------

    private record Member(UUID responsible, UUID primary, UUID alternate) {
        List<UUID> all() {
            return java.util.stream.Stream.of(responsible, primary, alternate).filter(java.util.Objects::nonNull)
                    .distinct().collect(Collectors.toList());
        }
    }

    /** A hearing whose day has passed with no result recorded. */
    private int withoutResult(LocalDate today) {
        record Row(UUID id, UUID caseId, String reference, LocalDate date, Member people) {
        }
        List<Row> rows = jdbc.sql("""
                select h.id, h.case_id, c.reference_no, h.hearing_date, h.responsible_member_id, c.primary_member_id,
                       c.alternate_member_id
                from hearing h join legal_case c on c.id = h.case_id
                where h.status = 'SCHEDULED' and h.hearing_date < :today order by h.hearing_date, h.id""")
                .param("today", today).query((rs, n) -> new Row(rs.getObject("id", UUID.class),
                        rs.getObject("case_id", UUID.class), rs.getString("reference_no"),
                        rs.getObject("hearing_date", LocalDate.class),
                        new Member(rs.getObject("responsible_member_id", UUID.class),
                                rs.getObject("primary_member_id", UUID.class), rs.getObject("alternate_member_id", UUID.class))))
                .list();
        int raised = 0;
        for (Row row : rows) {
            for (UUID person : row.people().all()) {
                if (notifications.publish(person, NotificationTypes.HEARING_NO_RESULT, row.caseId(), "HEARING:" + row.id(),
                        Map.of("caseReferenceNo", row.reference(), "hearingDate", row.date().toString()))) {
                    raised++;
                }
            }
        }
        return raised;
    }

    /** A case registered in the last days with a specialist or an alternate; the one who registered it is not told. */
    private int newCases() {
        record Row(UUID id, String reference, UUID primary, UUID alternate, UUID createdBy) {
        }
        List<Row> rows = jdbc.sql("""
                select id, reference_no, primary_member_id, alternate_member_id, created_by from legal_case
                where created_at >= now() - make_interval(days => :days)
                  and (primary_member_id is not null or alternate_member_id is not null) order by created_at, id""")
                .param("days", recentDays).query((rs, n) -> new Row(rs.getObject("id", UUID.class),
                        rs.getString("reference_no"), rs.getObject("primary_member_id", UUID.class),
                        rs.getObject("alternate_member_id", UUID.class), rs.getObject("created_by", UUID.class)))
                .list();
        int raised = 0;
        for (Row row : rows) {
            for (UUID person : new Member(null, row.primary(), row.alternate()).all()) {
                if (!person.equals(row.createdBy()) && notifications.publish(person, NotificationTypes.CASE_NEW, row.id(),
                        "CASE:" + row.id(), Map.of("caseReferenceNo", row.reference()))) {
                    raised++;
                }
            }
        }
        return raised;
    }

    /** A judgment recorded in the last days; the one who recorded it is not told. */
    private int newJudgments() {
        record Row(UUID id, UUID caseId, String reference, LocalDate date, UUID primary, UUID alternate, UUID createdBy) {
        }
        List<Row> rows = jdbc.sql("""
                select j.id, p.case_id, c.reference_no, j.judgment_date, c.primary_member_id, c.alternate_member_id,
                       j.created_by
                from judgment j join proceeding p on p.id = j.proceeding_id join legal_case c on c.id = p.case_id
                where j.created_at >= now() - make_interval(days => :days) order by j.created_at, j.id""")
                .param("days", recentDays).query((rs, n) -> new Row(rs.getObject("id", UUID.class),
                        rs.getObject("case_id", UUID.class), rs.getString("reference_no"),
                        rs.getObject("judgment_date", LocalDate.class), rs.getObject("primary_member_id", UUID.class),
                        rs.getObject("alternate_member_id", UUID.class), rs.getObject("created_by", UUID.class)))
                .list();
        int raised = 0;
        for (Row row : rows) {
            for (UUID person : new Member(null, row.primary(), row.alternate()).all()) {
                if (!person.equals(row.createdBy()) && notifications.publish(person, NotificationTypes.JUDGMENT_NEW,
                        row.caseId(), "JUDGMENT:" + row.id(),
                        Map.of("caseReferenceNo", row.reference(), "judgmentDate", row.date().toString()))) {
                    raised++;
                }
            }
        }
        return raised;
    }

    /** A request whose latest reply is incomplete: the documents asked for have not all arrived. */
    /**
     * Evidence the case needs and does not hold (requirements sections 20 and 32): a REQUIRED item with no stored document or
     * one recorded as UNAVAILABLE, in a case still being worked. It is the same notification kind as an incomplete reply
     * (DOCUMENT_MISSING), about the evidence item, once per item and person. Evidence that exists but was not yet submitted to
     * the court is a task for the member, not a missing document, and raises nothing here.
     */
    private int evidenceGaps() {
        record Row(UUID id, UUID caseId, String name, String reference, Member people) {
        }
        List<Row> rows = jdbc.sql("""
                select e.id, e.case_id, e.name, c.reference_no, c.primary_member_id, c.alternate_member_id
                from evidence_item e join legal_case c on c.id = e.case_id
                where (e.role = 'UNAVAILABLE' or (e.role = 'REQUIRED' and e.document_id is null))
                  and c.status in ('OPEN', 'ACTIVE', 'SUSPENDED') order by e.created_at, e.id""")
                .query((rs, n) -> new Row(rs.getObject("id", UUID.class), rs.getObject("case_id", UUID.class),
                        rs.getString("name"), rs.getString("reference_no"), new Member(null,
                                rs.getObject("primary_member_id", UUID.class), rs.getObject("alternate_member_id", UUID.class))))
                .list();
        int raised = 0;
        for (Row row : rows) {
            for (UUID person : row.people().all()) {
                if (notifications.publish(person, NotificationTypes.DOCUMENT_MISSING, row.caseId(), "EVIDENCE:" + row.id(),
                        Map.of("evidenceName", row.name().length() > 120 ? row.name().substring(0, 120) : row.name(),
                                "caseReferenceNo", row.reference()))) {
                    raised++;
                }
            }
        }
        return raised;
    }

    private int missingDocuments() {
        record Row(UUID id, UUID caseId, String requestNo, String reference, long replies, Member people) {
        }
        List<Row> rows = jdbc.sql("""
                select r.id, r.case_id, r.request_no, c.reference_no, r.responsible_member_id, c.primary_member_id,
                       c.alternate_member_id, (select count(*) from info_reply x where x.request_id = r.id) as replies
                from info_request r join legal_case c on c.id = r.case_id
                where r.status = 'INCOMPLETE' order by r.updated_at, r.id""")
                .query((rs, n) -> new Row(rs.getObject("id", UUID.class), rs.getObject("case_id", UUID.class),
                        rs.getString("request_no"), rs.getString("reference_no"), rs.getLong("replies"),
                        new Member(rs.getObject("responsible_member_id", UUID.class),
                                rs.getObject("primary_member_id", UUID.class), rs.getObject("alternate_member_id", UUID.class))))
                .list();
        int raised = 0;
        for (Row row : rows) {
            for (UUID person : row.people().all()) {
                if (notifications.publish(person, NotificationTypes.DOCUMENT_MISSING, row.caseId(),
                        "INFO_REQUEST:" + row.id(),
                        Map.of("requestNo", row.requestNo(), "caseReferenceNo", row.reference()),
                        "r" + row.replies())) {
                    raised++;
                }
            }
        }
        return raised;
    }
}
