package com.lawdesk.cases;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.lawdesk.api.PageResponse;

/** The append-only case timeline, {@code case_event} (LD-014). Events are written in the caller's transaction. */
@Repository
class CaseTimeline {

    static final String CASE_CREATED = "CASE_CREATED";
    static final String CASE_UPDATED = "CASE_UPDATED";
    static final String CASE_STATUS_CHANGED = "CASE_STATUS_CHANGED";
    static final String PARTY_ADDED = "PARTY_ADDED";
    static final String PARTY_REMOVED = "PARTY_REMOVED";

    private static final Pattern TYPE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
    private static final int SUMMARY_MAX = 500;

    private final JdbcClient jdbc;

    CaseTimeline(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Shortens a summary built from user input (a 500-character reason plus its prefix) to the column limit. */
    static String fit(String summary) {
        return summary.length() <= SUMMARY_MAX ? summary : summary.substring(0, SUMMARY_MAX - 1) + "…";
    }

    /** Refuses what the {@code case_event} checks would refuse, before anything is written. */
    static String checkedSummary(String type, String summary) {
        if (type == null || !TYPE.matcher(type).matches()) {
            throw new IllegalArgumentException("event type must match " + TYPE.pattern());
        }
        String trimmed = Text.trimToNull(summary);
        if (trimmed == null || trimmed.length() > SUMMARY_MAX) {
            throw new IllegalArgumentException("event summary must have 1 to " + SUMMARY_MAX + " characters");
        }
        return trimmed;
    }

    /** Newest first; {@code seq} orders events of one transaction, which share {@code occurred_at}. */
    PageResponse<CaseEvent> page(UUID caseId, Paging paging) {
        long total = jdbc.sql("select count(*) from case_event where case_id = :caseId").param("caseId", caseId)
                .query(Long.class).single();
        List<CaseEvent> items = jdbc.sql("""
                        select id, type, summary, occurred_at, created_by from case_event where case_id = :caseId
                        order by seq desc limit :limit offset :offset""")
                .param("caseId", caseId).param("limit", paging.sizeOrDefault()).param("offset", paging.offset())
                .query((rs, rowNum) -> new CaseEvent(rs.getObject("id", UUID.class), rs.getString("type"),
                        rs.getString("summary"), rs.getObject("occurred_at", OffsetDateTime.class).toInstant(),
                        rs.getObject("created_by", UUID.class)))
                .list();
        return paging.page(items, total);
    }

    void append(UUID caseId, String type, String summary, UUID actor) {
        jdbc.sql("""
                        insert into case_event (case_id, type, summary, created_by)
                        values (:caseId, :type, :summary, :actor)""")
                .param("caseId", caseId).param("type", type).param("summary", checkedSummary(type, summary))
                .param("actor", actor).update();
    }
}
