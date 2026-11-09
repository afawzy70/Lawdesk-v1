package com.lawdesk.notifications;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.api.FieldErrorException;
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
    PageResponse<NotificationController.Item> list(boolean unreadOnly, Boolean read, List<String> types, UUID caseId,
            int page, int size) {
        Map<String, Object> params = new HashMap<>();
        StringBuilder where = new StringBuilder(visibleToMe(params));
        if (unreadOnly || Boolean.FALSE.equals(read)) {
            where.append(" and n.read_at is null");
        } else if (Boolean.TRUE.equals(read)) {
            where.append(" and n.read_at is not null");
        }
        if (types != null && !types.isEmpty()) {
            for (String type : types) {
                if (!NotificationTypes.known(type)) {
                    throw new FieldErrorException("type", "Pattern");
                }
            }
            where.append(" and n.type in (:types)");
            params.put("types", types);
        }
        if (caseId != null) {
            where.append(" and n.case_id = :caseId");
            params.put("caseId", caseId);
        }
        long total = jdbc.sql("select count(*) from notification n where " + where).params(params).query(Long.class)
                .single();
        List<NotificationController.Item> items = jdbc.sql("""
                        select n.id, n.type, n.case_id, c.reference_no, n.record_ref, n.params::text as params,
                               n.created_at, n.read_at
                        from notification n left join legal_case c on c.id = n.case_id
                        where """ + " " + where + " order by n.created_at desc, n.id limit :limit offset :offset")
                .params(params).param("limit", size).param("offset", (long) page * size)
                .query((rs, n) -> item(rs)).list();
        return new PageResponse<>(items, page, size, total, (int) ((total + size - 1) / size));
    }

    private static NotificationController.Item item(java.sql.ResultSet rs) throws java.sql.SQLException {
        String ref = rs.getString("record_ref");
        int colon = ref == null ? -1 : ref.indexOf(':');
        return new NotificationController.Item(rs.getObject("id", UUID.class), rs.getString("type"),
                rs.getObject("case_id", UUID.class), rs.getString("reference_no"), ref,
                colon < 0 ? null : ref.substring(0, colon), colon < 0 ? null : ref.substring(colon + 1),
                NotificationPublisher.parse(rs.getString("params")),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getObject("read_at", OffsetDateTime.class) == null ? null
                        : rs.getObject("read_at", OffsetDateTime.class).toInstant());
    }

    /** Every kind, with whether the signed-in user receives it (on unless they switched it off). */
    @Transactional(readOnly = true)
    List<NotificationController.Preference> preferences() {
        UUID me = currentActor.require().id();
        Map<String, Boolean> chosen = new HashMap<>();
        jdbc.sql("select type, enabled from notification_preference where user_id = :me").param("me", me)
                .query((rs, n) -> {
                    chosen.put(rs.getString("type"), rs.getBoolean("enabled"));
                    return null;
                }).list();
        return NotificationTypes.ALL.stream().map(t -> new NotificationController.Preference(t.code(), t.core(),
                t.position(), chosen.getOrDefault(t.code(), true))).toList();
    }

    @Transactional
    NotificationController.Preference setPreference(String type, boolean enabled) {
        if (!NotificationTypes.known(type)) {
            throw new NotFoundException("notification type");
        }
        UUID me = currentActor.require().id();
        jdbc.sql("""
                insert into notification_preference (user_id, type, enabled) values (:me, :type, :enabled)
                on conflict (user_id, type) do update set enabled = excluded.enabled, updated_at = now()""")
                .param("me", me).param("type", type).param("enabled", enabled).update();
        NotificationTypes.Type known = NotificationTypes.ALL.stream().filter(t -> t.code().equals(type)).findFirst()
                .orElseThrow();
        return new NotificationController.Preference(type, known.core(), known.position(), enabled);
    }

    @Transactional
    void markUnread(UUID id) {
        Map<String, Object> params = new HashMap<>();
        String where = visibleToMe(params);
        if (jdbc.sql("select count(*) from notification n where n.id = :id and " + where).params(params).param("id", id)
                .query(Long.class).single() == 0) {
            throw new NotFoundException("notification");
        }
        jdbc.sql("update notification set read_at = null where id = :id").param("id", id).update();
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
