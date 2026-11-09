package com.lawdesk.notifications;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.api.NotFoundException;
import com.lawdesk.api.PageResponse;
import com.lawdesk.cases.CaseAccessPolicy;
import com.lawdesk.security.CurrentActor;

/**
 * A user's notifications. They are the user's own, and only those about a case the user may still see are listed: a
 * notification raised for a case the user lost access to (or never could see) is not shown and cannot be read through.
 */
@Service
class NotificationService {

    private final JdbcClient jdbc;
    private final CurrentActor currentActor;
    private final CaseAccessPolicy access;

    NotificationService(JdbcClient jdbc, CurrentActor currentActor, CaseAccessPolicy access) {
        this.jdbc = jdbc;
        this.currentActor = currentActor;
        this.access = access;
    }

    /** The notifications the signed-in user may see: their own, whose case (if any) is visible to them. */
    private String visibleToMe(Map<String, Object> params) {
        CaseAccessPolicy.Predicate visible = access.visibleCaseIds("n.case_id");
        params.putAll(visible.params());
        params.put("me", currentActor.require().id());
        return "n.user_id = :me and (n.case_id is null or " + visible.sql() + ")";
    }

    @Transactional(readOnly = true)
    PageResponse<NotificationController.Item> list(boolean unreadOnly, int page, int size) {
        Map<String, Object> params = new HashMap<>();
        String where = visibleToMe(params) + (unreadOnly ? " and n.read_at is null" : "");
        long total = jdbc.sql("select count(*) from notification n where " + where).params(params).query(Long.class)
                .single();
        List<NotificationController.Item> items = jdbc.sql("""
                        select n.id, n.type, n.case_id, c.reference_no, n.record_ref, n.params::text as params,
                               n.created_at, n.read_at
                        from notification n left join legal_case c on c.id = n.case_id
                        where """ + " " + where + " order by n.created_at desc, n.id limit :limit offset :offset")
                .params(params).param("limit", size).param("offset", (long) page * size)
                .query((rs, n) -> new NotificationController.Item(rs.getObject("id", UUID.class), rs.getString("type"),
                        rs.getObject("case_id", UUID.class), rs.getString("reference_no"), rs.getString("record_ref"),
                        NotificationPublisher.parse(rs.getString("params")),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                        rs.getObject("read_at", OffsetDateTime.class) == null ? null
                                : rs.getObject("read_at", OffsetDateTime.class).toInstant()))
                .list();
        return new PageResponse<>(items, page, size, total, (int) ((total + size - 1) / size));
    }

    @Transactional(readOnly = true)
    long unread() {
        Map<String, Object> params = new HashMap<>();
        return jdbc.sql("select count(*) from notification n where " + visibleToMe(params) + " and n.read_at is null")
                .params(params).query(Long.class).single();
    }

    @Transactional
    void markRead(UUID id) {
        Map<String, Object> params = new HashMap<>();
        String where = visibleToMe(params);
        if (jdbc.sql("select count(*) from notification n where n.id = :id and " + where).params(params).param("id", id)
                .query(Long.class).single() == 0) {
            throw new NotFoundException("notification");
        }
        jdbc.sql("update notification set read_at = coalesce(read_at, now()) where id = :id").param("id", id).update();
    }

    @Transactional
    void markAllRead() {
        Map<String, Object> params = new HashMap<>();
        String where = visibleToMe(params);
        jdbc.sql("update notification n set read_at = now() where n.read_at is null and " + where).params(params)
                .update();
    }
}
