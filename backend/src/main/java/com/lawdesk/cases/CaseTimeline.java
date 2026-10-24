package com.lawdesk.cases;

import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** The append-only case timeline, {@code case_event} (LD-014). Events are written in the caller's transaction. */
@Repository
class CaseTimeline {

    static final String CASE_CREATED = "CASE_CREATED";

    private static final Pattern TYPE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
    private static final int SUMMARY_MAX = 500;

    private final JdbcClient jdbc;

    CaseTimeline(JdbcClient jdbc) {
        this.jdbc = jdbc;
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

    void append(UUID caseId, String type, String summary, UUID actor) {
        jdbc.sql("""
                        insert into case_event (case_id, type, summary, created_by)
                        values (:caseId, :type, :summary, :actor)""")
                .param("caseId", caseId).param("type", type).param("summary", checkedSummary(type, summary))
                .param("actor", actor).update();
    }
}
