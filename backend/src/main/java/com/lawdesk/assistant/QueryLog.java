package com.lawdesk.assistant;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.assistant.AssistantProvider.Source;
import com.lawdesk.cases.CaseAccessPolicy;
import com.lawdesk.security.ActorRef;
import com.lawdesk.security.CurrentActor;
import com.lawdesk.security.Permission;

import tools.jackson.databind.json.JsonMapper;

/**
 * The assistant's query log (requirements 22.7, owner decision D9): who asked what about which case, which sources were used
 * and what came of it. Append-only; there is no retention policy yet, so nothing is deleted automatically. It stores
 * identifiers and the minimised question only - never an answer and never text from a source. {@link #record} commits in its own
 * transaction, so a refused request (a case the user may not see) is logged too.
 */
@Component
class QueryLog {

    static final int QUESTION_MAX = 500;
    static final int PAGE = 50;

    /** One log row as the API shows it. */
    record Entry(long id, java.time.Instant occurredAt, UUID userId, String username, UUID caseId, String task, String question,
            String outcome, List<SourceRef> sources, int sourceCount, String providerCode, boolean nonProduction, UUID outputId) {
    }

    /** A source as logged: what it is and which record, nothing of its content. */
    record SourceRef(String type, UUID id, UUID caseId) {
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final JdbcClient jdbc;
    private final CurrentActor currentActor;
    private final CaseAccessPolicy access;

    QueryLog(JdbcClient jdbc, CurrentActor currentActor, CaseAccessPolicy access) {
        this.jdbc = jdbc;
        this.currentActor = currentActor;
        this.access = access;
    }

    /** Long digit runs (identity, phone and account numbers) are not kept; the question is cut to {@value #QUESTION_MAX} characters. */
    static String minimise(String question) {
        if (question == null) {
            return null;
        }
        String flat = question.strip().replaceAll("\\s+", " ").replaceAll("\\d{6,}", "•");
        return flat.length() <= QUESTION_MAX ? flat : flat.substring(0, QUESTION_MAX);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    long record(UUID sessionId, UUID caseId, AssistantTask task, String question, String outcome, List<Source> sources,
            AssistantProvider.Info provider, UUID outputId) {
        ActorRef actor = currentActor.require();
        List<SourceRef> refs = sources.stream().map(s -> new SourceRef(s.type(), s.id(), s.caseId())).toList();
        return jdbc.sql("""
                insert into assistant_query_log (user_id, username, session_id, case_id, task, question, outcome, sources,
                                                 source_count, provider_code, non_production, output_id, request_id)
                values (:user, :username, :session, :case, :task, :question, :outcome, cast(:sources as jsonb), :count,
                        :provider, :nonProduction, :output, :request) returning id""")
                .param("user", actor.id()).param("username", truncate(actor.username(), 64)).param("session", sessionId)
                .param("case", caseId).param("task", task.name()).param("question", minimise(question)).param("outcome", outcome)
                .param("sources", JSON.writeValueAsString(refs)).param("count", refs.size()).param("provider", provider.code())
                .param("nonProduction", provider.nonProduction()).param("output", outputId)
                .param("request", org.slf4j.MDC.get(com.lawdesk.platform.RequestCorrelationFilter.MDC_KEY))
                .query(Long.class).single();
    }

    /** The signed-in user's own queries, newest first; {@code caseId} narrows to one case. */
    @Transactional(readOnly = true)
    List<Entry> mine(UUID caseId) {
        java.util.Map<String, Object> params = new java.util.HashMap<>();
        params.put("user", currentActor.require().id());
        String where = "l.user_id = :user" + (caseId == null ? "" : " and l.case_id = :case");
        if (caseId != null) {
            params.put("case", caseId);
        }
        return query(where, params);
    }

    /**
     * Everyone's queries, for the holder of AUDIT_VIEW: the queries about cases that holder may see, and their own. A query
     * about a case the reviewer may not see (or about none) is not shown - the log does not widen what anyone may read.
     */
    @Transactional(readOnly = true)
    List<Entry> reviewed(UUID caseId) {
        if (!currentActor.has(Permission.AUDIT_VIEW)) {
            throw new org.springframework.security.access.AccessDeniedException("missing AUDIT_VIEW");
        }
        CaseAccessPolicy.Predicate visible = access.visibleCaseIds("l.case_id");
        java.util.Map<String, Object> params = new java.util.HashMap<>(visible.params());
        params.put("user", currentActor.require().id());
        String where = "(l.user_id = :user or (l.case_id is not null and " + visible.sql() + "))";
        if (caseId != null) {
            params.put("case", caseId);
            where += " and l.case_id = :case";
        }
        return query(where, params);
    }

    private List<Entry> query(String where, java.util.Map<String, Object> params) {
        return jdbc.sql("select l.id, l.occurred_at, l.user_id, l.username, l.case_id, l.task, l.question, l.outcome,"
                + " cast(l.sources as text) as sources, l.source_count, l.provider_code, l.non_production, l.output_id"
                + " from assistant_query_log l where " + where + " order by l.id desc limit " + PAGE)
                .params(params).query((rs, n) -> new Entry(rs.getLong("id"), rs.getTimestamp("occurred_at").toInstant(),
                        rs.getObject("user_id", UUID.class), rs.getString("username"), rs.getObject("case_id", UUID.class),
                        rs.getString("task"), rs.getString("question"), rs.getString("outcome"),
                        List.of(JSON.readValue(rs.getString("sources"), SourceRef[].class)), rs.getInt("source_count"),
                        rs.getString("provider_code"), rs.getBoolean("non_production"), rs.getObject("output_id", UUID.class)))
                .list();
    }

    private static String truncate(String value, int length) {
        return value == null || value.length() <= length ? value : value.substring(0, length);
    }
}
