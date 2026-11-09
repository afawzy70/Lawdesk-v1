package com.lawdesk.calendar;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.api.BusinessRuleException;
import com.lawdesk.api.ConflictException;
import com.lawdesk.api.FieldErrorException;
import com.lawdesk.api.NotFoundException;
import com.lawdesk.cases.CaseAccessPolicy;
import com.lawdesk.cases.CaseLookup;
import com.lawdesk.cases.CaseSummary;
import com.lawdesk.security.ActorRef;
import com.lawdesk.security.CurrentActor;
import com.lawdesk.users.UserDirectory;
import com.lawdesk.users.UserDirectory.UserSummary;

/**
 * Manual calendar dates: meetings and dates that no other record holds. A date on a case follows the case's access rules
 * (404 for a case the user may not see, 403 for one they see but may not change); a date without a case belongs to its
 * owner and creator, and to the department-wide scope for reading.
 */
@Component
class ManualDateService {

    static final String OWNER_NOT_ASSIGNABLE = "OWNER_NOT_ASSIGNABLE";
    static final String CASE_ARCHIVED = "CASE_ARCHIVED";

    private record Row(UUID id, ManualDateKind kind, String title, String notes, UUID caseId, Instant startsAt,
            Instant endsAt, boolean allDay, UUID ownerId, ManualDateStatus status, long version, Instant createdAt,
            UUID createdBy) {
    }

    private final JdbcClient jdbc;
    private final CaseLookup cases;
    private final CaseAccessPolicy access;
    private final UserDirectory users;
    private final CurrentActor currentActor;

    ManualDateService(JdbcClient jdbc, CaseLookup cases, CaseAccessPolicy access, UserDirectory users,
            CurrentActor currentActor) {
        this.jdbc = jdbc;
        this.cases = cases;
        this.access = access;
        this.users = users;
        this.currentActor = currentActor;
    }

    @Transactional
    ManualDateRequests.Response create(ManualDateRequests.Create request) {
        ActorRef actor = currentActor.require();
        requireTimes(request.startTime(), request.endTime());
        CaseSummary legalCase = null;
        if (request.caseId() != null) {
            legalCase = cases.find(request.caseId()).orElseThrow(() -> new NotFoundException("case"));
            access.requireChange(request.caseId());
            if ("ARCHIVED".equals(legalCase.status())) {
                throw new BusinessRuleException(CASE_ARCHIVED, "an archived case is read-only");
            }
        }
        UUID owner = owner(request.ownerId() == null ? actor.id() : request.ownerId());
        Instant starts = CalendarTime.instant(request.date(), request.startTime());
        Instant ends = request.startTime() == null || request.endTime() == null ? null
                : CalendarTime.instant(request.date(), request.endTime());
        UUID id = jdbc.sql("""
                insert into calendar_date (case_id, kind, title, notes, starts_at, ends_at, all_day, owner_id,
                                           created_by, updated_by)
                values (:case, :kind, :title, :notes, :starts, :ends, :allDay, :owner, :me, :me) returning id""")
                .param("case", request.caseId()).param("kind", request.kind().name()).param("title", request.title())
                .param("notes", request.notes()).param("starts", Timestamp.from(starts))
                .param("ends", ends == null ? null : Timestamp.from(ends)).param("allDay", request.startTime() == null)
                .param("owner", owner).param("me", actor.id()).query(UUID.class).single();
        if (legalCase != null) {
            cases.recordEvent(legalCase.id(), "CALENDAR_DATE_ADDED", "Date \"%s\" added to the calendar for %s".formatted(
                    request.title(), request.date()));
        }
        return respond(row(id).orElseThrow());
    }

    @Transactional(readOnly = true)
    ManualDateRequests.Response get(UUID id) {
        Row row = row(id).filter(this::mayRead).orElseThrow(() -> new NotFoundException("calendar date"));
        return respond(row);
    }

    @Transactional
    ManualDateRequests.Response update(UUID id, ManualDateRequests.Update request) {
        ActorRef actor = currentActor.require();
        requireTimes(request.startTime(), request.endTime());
        Row row = locked(id, request.version());
        requireChange(row);
        UUID owner = request.ownerId() == null || request.ownerId().equals(row.ownerId()) ? row.ownerId()
                : owner(request.ownerId());
        Instant starts = CalendarTime.instant(request.date(), request.startTime());
        Instant ends = request.startTime() == null || request.endTime() == null ? null
                : CalendarTime.instant(request.date(), request.endTime());
        jdbc.sql("""
                update calendar_date set kind = :kind, title = :title, notes = :notes, starts_at = :starts, ends_at = :ends,
                       all_day = :allDay, owner_id = :owner, version = version + 1, updated_at = now(), updated_by = :me
                where id = :id""")
                .param("kind", request.kind().name()).param("title", request.title()).param("notes", request.notes())
                .param("starts", Timestamp.from(starts)).param("ends", ends == null ? null : Timestamp.from(ends))
                .param("allDay", request.startTime() == null).param("owner", owner).param("me", actor.id())
                .param("id", id).update();
        return respond(row(id).orElseThrow());
    }

    @Transactional
    ManualDateRequests.Response changeStatus(UUID id, ManualDateRequests.StatusChange request) {
        ActorRef actor = currentActor.require();
        Row row = locked(id, request.version());
        requireChange(row);
        jdbc.sql("""
                update calendar_date set status = :status, version = version + 1, updated_at = now(), updated_by = :me
                where id = :id""").param("status", request.status().name()).param("me", actor.id()).param("id", id).update();
        return respond(row(id).orElseThrow());
    }

    // ---- rules -------------------------------------------------------------------------------------------------

    private static void requireTimes(LocalTime start, LocalTime end) {
        if (end != null && start == null) {
            throw new FieldErrorException("endTime", "RequiresStartTime");
        }
        if (end != null && !end.isAfter(start)) {
            throw new FieldErrorException("endTime", "AfterStartTime");
        }
    }

    private UUID owner(UUID userId) {
        return users.find(userId).filter(UserSummary::enabled).map(UserSummary::id).orElseThrow(
                () -> new BusinessRuleException(OWNER_NOT_ASSIGNABLE, "dates are given to enabled users only"));
    }

    private boolean mayRead(Row row) {
        if (row.caseId() != null) {
            return access.canView(row.caseId());
        }
        UUID me = currentActor.require().id();
        return me.equals(row.ownerId()) || me.equals(row.createdBy()) || access.scope().all();
    }

    /** Read first, so that someone who cannot see the date is told it does not exist. */
    private Row locked(UUID id, long version) {
        Row row = jdbc.sql(SELECT + " where d.id = :id for update").param("id", id).query(this::map).optional()
                .filter(this::mayRead).orElseThrow(() -> new NotFoundException("calendar date"));
        if (row.version() != version) {
            throw new ConflictException("calendar date was changed by someone else");
        }
        return row;
    }

    private void requireChange(Row row) {
        if (row.caseId() != null) {
            access.requireChange(row.caseId());
            return;
        }
        UUID me = currentActor.require().id();
        if (!me.equals(row.ownerId()) && !me.equals(row.createdBy())) {
            throw new AccessDeniedException("only the owner or the creator changes a date that has no case");
        }
    }

    // ---- reading -----------------------------------------------------------------------------------------------

    private static final String SELECT = """
            select d.id, d.kind, d.title, d.notes, d.case_id, d.starts_at, d.ends_at, d.all_day, d.owner_id, d.status,
                   d.version, d.created_at, d.created_by from calendar_date d""";

    private Optional<Row> row(UUID id) {
        return jdbc.sql(SELECT + " where d.id = :id").param("id", id).query(this::map).optional();
    }

    private Row map(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        Timestamp ends = rs.getTimestamp("ends_at");
        return new Row(rs.getObject("id", UUID.class), ManualDateKind.valueOf(rs.getString("kind")), rs.getString("title"),
                rs.getString("notes"), rs.getObject("case_id", UUID.class), rs.getTimestamp("starts_at").toInstant(),
                ends == null ? null : ends.toInstant(), rs.getBoolean("all_day"), rs.getObject("owner_id", UUID.class),
                ManualDateStatus.valueOf(rs.getString("status")), rs.getLong("version"),
                rs.getTimestamp("created_at").toInstant(), rs.getObject("created_by", UUID.class));
    }

    private ManualDateRequests.Response respond(Row row) {
        CaseSummary legalCase = row.caseId() == null ? null : cases.find(row.caseId()).orElse(null);
        UserSummary owner = users.find(row.ownerId()).orElse(null);
        return new ManualDateRequests.Response(row.id(), row.kind(), row.title(), row.notes(), row.caseId(),
                legalCase == null ? null : legalCase.referenceNo(), CalendarTime.day(row.startsAt()),
                row.allDay() ? null : CalendarTime.time(row.startsAt()),
                row.endsAt() == null ? null : CalendarTime.time(row.endsAt()), row.allDay(), row.startsAt(), row.endsAt(),
                row.ownerId(), owner == null ? null : owner.displayName(), row.status(), row.version(), row.createdAt(),
                row.createdBy());
    }

    /** The dates the calendar shows, as entries (used by the manual-date source). */
    List<CalendarEntry> entries(CalendarQuery q, CalendarScope scope) {
        CaseAccessPolicy.Predicate visible = scope.records("d.owner_id", "d.created_by", "d.case_id");
        Map<String, Object> params = new HashMap<>(visible.params());
        params.put("from", Timestamp.from(q.fromInstant()));
        params.put("to", Timestamp.from(q.toExclusiveInstant()));
        params.put("limit", q.limit());
        if (q.ownerId() != null) {
            params.put("owner", q.ownerId());
        }
        if (q.caseId() != null) {
            params.put("caseId", q.caseId());
        }
        String sql = """
                select d.id, d.kind, d.title, d.case_id, d.starts_at, d.ends_at, d.all_day, d.owner_id, d.status,
                       c.reference_no, c.title as case_title, c.status as case_status, c.primary_member_id,
                       c.alternate_member_id
                from calendar_date d left join legal_case c on c.id = d.case_id
                where d.starts_at >= :from and d.starts_at < :to and """ + " " + visible.sql()
                + BuiltInSources.filters(q, "d.owner_id", "d.case_id") + " order by d.starts_at, d.id limit :limit";
        return jdbc.sql(sql).params(params).query((rs, n) -> {
            Instant starts = rs.getTimestamp("starts_at").toInstant();
            Timestamp endsAt = rs.getTimestamp("ends_at");
            boolean allDay = rs.getBoolean("all_day");
            UUID owner = rs.getObject("owner_id", UUID.class);
            UUID alternate = rs.getObject("alternate_member_id", UUID.class);
            String kind = rs.getString("kind");
            return CalendarEntry.of(kind, "CALENDAR_DATE", rs.getObject("id", UUID.class), rs.getObject("case_id", UUID.class),
                    rs.getString("reference_no"), rs.getString("case_title"), rs.getString("case_status"),
                    rs.getString("title"), kind, null, CalendarTime.day(starts), allDay ? null : CalendarTime.time(starts),
                    allDay || endsAt == null ? null : CalendarTime.time(endsAt.toInstant()), owner, rs.getString("status"),
                    alternate, BuiltInSources.people(owner, rs.getObject("primary_member_id", UUID.class), alternate));
        }).list();
    }
}
