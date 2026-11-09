package com.lawdesk.notifications;

import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * The port other modules use to raise an in-app notification (requirements section 32, owner decision D11). It writes in
 * the caller's transaction and is idempotent: the same type, record and user is stored once, however often it is raised.
 * Nothing leaves the system.
 */
@Component
public class NotificationPublisher {

    /** Types raised by the follow-up of WP3; WP6 adds the rest of the list in the requirements. */
    public static final String INFO_REQUEST_OVERDUE = "INFO_REQUEST_OVERDUE";
    public static final String INFO_REQUEST_ESCALATED = "INFO_REQUEST_ESCALATED";
    public static final String HEARING_NO_NEXT_DATE = "HEARING_NO_NEXT_DATE";
    public static final String RISK_HIGH = "RISK_HIGH";

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final JdbcClient jdbc;

    NotificationPublisher(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Stores the notification for {@code userId} unless the same one exists.
     *
     * @param recordRef the record it is about as {@code TYPE:id}
     * @param params    values for the text of the type; strings only
     * @return true when a new notification was stored
     */
    public boolean publish(UUID userId, String type, UUID caseId, String recordRef, Map<String, String> params) {
        if (userId == null) {
            return false;
        }
        String dedupeKey = type + ":" + recordRef + ":" + userId;
        return jdbc.sql("""
                        insert into notification (user_id, type, case_id, record_ref, params, dedupe_key)
                        select u.id, :type, :case, :ref, cast(:params as jsonb), :dedupe
                        from app_user u where u.id = :user
                        on conflict (dedupe_key) do nothing""")
                .param("user", userId).param("type", type).param("case", caseId).param("ref", recordRef)
                .param("params", json(params)).param("dedupe", dedupeKey).update() == 1;
    }

    /**
     * Raises "this adjourned hearing has no next date" for the hearing's responsible member and the case's specialist;
     * when the hearing and the case name nobody, for {@code fallbackUser} (the one who recorded it). Repeats are ignored.
     *
     * @return the number of notifications newly stored
     */
    public int publishHearingWithoutDate(UUID hearingId, UUID fallbackUser) {
        record Row(UUID caseId, UUID responsible, UUID specialist, String reference, String date) {
        }
        Row row = jdbc.sql("""
                        select h.case_id, h.responsible_member_id, c.primary_member_id, c.reference_no, h.hearing_date::text as d
                        from hearing h join legal_case c on c.id = h.case_id where h.id = :id""")
                .param("id", hearingId)
                .query((rs, n) -> new Row(rs.getObject("case_id", UUID.class), rs.getObject("responsible_member_id", UUID.class),
                        rs.getObject("primary_member_id", UUID.class), rs.getString("reference_no"), rs.getString("d")))
                .optional().orElse(null);
        if (row == null) {
            return 0;
        }
        java.util.Set<UUID> users = new java.util.LinkedHashSet<>();
        if (row.responsible() != null) {
            users.add(row.responsible());
        }
        if (row.specialist() != null) {
            users.add(row.specialist());
        }
        if (users.isEmpty() && fallbackUser != null) {
            users.add(fallbackUser);
        }
        int raised = 0;
        for (UUID user : users) {
            if (publish(user, HEARING_NO_NEXT_DATE, row.caseId(), "HEARING:" + hearingId,
                    Map.of("caseReferenceNo", row.reference(), "hearingDate", row.date()))) {
                raised++;
            }
        }
        return raised;
    }

    /** A flat JSON object of strings. */
    static String json(Map<String, String> params) {
        return MAPPER.writeValueAsString(new java.util.TreeMap<>(params));
    }

    static Map<String, String> parse(String json) {
        return MAPPER.readValue(json, new TypeReference<Map<String, String>>() {
        });
    }
}
