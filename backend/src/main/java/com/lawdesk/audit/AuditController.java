package com.lawdesk.audit;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lawdesk.api.PageResponse;
import com.lawdesk.security.Permission;

/**
 * {@code GET /api/v1/audit}: the security audit trail, newest first (LD-011.5). Filters combine; the actor is matched
 * by username, ignoring case, and the time range is half-open ({@code from} inclusive, {@code to} exclusive).
 */
@RestController
@RequestMapping("/api/v1/audit")
class AuditController {

    /** One recorded event, as stored; it never holds passwords, hashes, session ids or tokens. */
    record AuditEventView(long id, Instant occurredAt, UUID actorId, String actorUsername, String action,
            String targetType, String targetId, AuditLog.Outcome outcome, String reason, String requestId) {
    }

    private final JdbcTemplate jdbc;

    AuditController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    @PreAuthorize(Permission.Codes.AUDIT_VIEW)
    PageResponse<AuditEventView> list(@RequestParam(required = false) String action,
            @RequestParam(required = false) String actor, @RequestParam(required = false) AuditLog.Outcome outcome,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            Pageable page) {
        List<String> conditions = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (action != null && !action.isBlank()) {
            conditions.add("action = ?");
            args.add(action.strip().toUpperCase(Locale.ROOT));
        }
        if (actor != null && !actor.isBlank()) {
            conditions.add("lower(actor_username) = ?");
            args.add(actor.strip().toLowerCase(Locale.ROOT));
        }
        if (outcome != null) {
            conditions.add("outcome = ?");
            args.add(outcome.name());
        }
        if (from != null) {
            conditions.add("occurred_at >= ?");
            args.add(Timestamp.from(from));
        }
        if (to != null) {
            conditions.add("occurred_at < ?");
            args.add(Timestamp.from(to));
        }
        String where = conditions.isEmpty() ? "" : " where " + String.join(" and ", conditions);
        long total = jdbc.queryForObject("select count(*) from audit_event" + where, Long.class, args.toArray());
        List<Object> paged = new ArrayList<>(args);
        paged.add(page.getPageSize());
        paged.add(page.getOffset());
        List<AuditEventView> items = jdbc.query("""
                select id, occurred_at, actor_id, actor_username, action, target_type, target_id, outcome, reason,
                       request_id
                from audit_event""" + where + " order by occurred_at desc, id desc limit ? offset ?",
                (rs, row) -> view(rs), paged.toArray());
        return PageResponse.from(new PageImpl<>(items, page, total));
    }

    private static AuditEventView view(ResultSet rs) throws SQLException {
        return new AuditEventView(rs.getLong("id"), rs.getTimestamp("occurred_at").toInstant(),
                rs.getObject("actor_id", UUID.class), rs.getString("actor_username"), rs.getString("action"),
                rs.getString("target_type"), rs.getString("target_id"),
                AuditLog.Outcome.valueOf(rs.getString("outcome")), rs.getString("reason"), rs.getString("request_id"));
    }
}
