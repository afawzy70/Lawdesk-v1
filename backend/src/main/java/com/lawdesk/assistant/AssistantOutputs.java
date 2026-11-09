package com.lawdesk.assistant;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.api.BusinessRuleException;
import com.lawdesk.api.NotFoundException;
import com.lawdesk.assistant.AssistantProvider.Block;
import com.lawdesk.assistant.AssistantProvider.Source;
import com.lawdesk.audit.AuditLog;
import com.lawdesk.cases.CaseAccessPolicy;
import com.lawdesk.security.ActorRef;
import com.lawdesk.security.CurrentActor;

import tools.jackson.databind.json.JsonMapper;

/**
 * The drafts the assistant produced (requirements 22.7): a memorandum draft, a summary or a comparison kept for the member. A
 * draft is NEVER final (the database refuses it), belongs to the member who asked for it, and is visible to that member only
 * while they may still see its case. Approving one means only that the member has reviewed it; it creates no memorandum,
 * document or other record, and nothing here changes a case.
 */
@Component
class AssistantOutputs {

    static final String ALREADY_REVIEWED = "ASSISTANT_OUTPUT_ALREADY_REVIEWED";
    static final int PAGE = 50;

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String COLUMNS = "id, session_id, case_id, kind, title, status, is_final, non_production, content,"
            + " reviewed_by, reviewed_at, review_note, created_at";

    private final JdbcClient jdbc;
    private final CurrentActor currentActor;
    private final CaseAccessPolicy access;
    private final AuditLog audit;

    AssistantOutputs(JdbcClient jdbc, CurrentActor currentActor, CaseAccessPolicy access, AuditLog audit) {
        this.jdbc = jdbc;
        this.currentActor = currentActor;
        this.access = access;
        this.audit = audit;
    }

    static String kindOf(AssistantTask task) {
        return switch (task) {
            case PRECEDENTS -> "PRECEDENT_LIST";
            case QUESTION -> "ANSWER";
            default -> task.name();
        };
    }

    @Transactional
    UUID save(UUID sessionId, UUID caseId, AssistantTask task, String question, List<Block> blocks, List<Source> sources,
            AssistantProvider.Info provider) {
        Map<String, Object> content = new HashMap<>();
        content.put("task", task.name());
        content.put("question", QueryLog.minimise(question));
        content.put("blocks", blocks);
        content.put("sources", sources);
        content.put("provider", provider);
        String title = question != null ? QueryLog.minimise(question) : task.name();
        return jdbc.sql("""
                insert into assistant_output (session_id, user_id, case_id, kind, title, content, non_production)
                values (:session, :user, :case, :kind, :title, cast(:content as jsonb), :nonProduction) returning id""")
                .param("session", sessionId).param("user", currentActor.require().id()).param("case", caseId)
                .param("kind", kindOf(task)).param("title", title.length() > 300 ? title.substring(0, 300) : title)
                .param("content", JSON.writeValueAsString(content)).param("nonProduction", provider.nonProduction())
                .query(UUID.class).single();
    }

    /** The member's own drafts, newest first; for a case, only if the member may still see it (404 otherwise). */
    @Transactional(readOnly = true)
    List<AssistantRequests.Output> mine(UUID caseId) {
        if (caseId != null) {
            access.requireView(caseId);
        }
        CaseAccessPolicy.Predicate visible = access.visibleCaseIds("o.case_id");
        Map<String, Object> params = new HashMap<>(visible.params());
        params.put("user", currentActor.require().id());
        String where = "o.user_id = :user and (o.case_id is null or " + visible.sql() + ")";
        if (caseId != null) {
            params.put("case", caseId);
            where += " and o.case_id = :case";
        }
        return jdbc.sql("select " + COLUMNS.replaceAll("(\\w+)", "o.$1") + " from assistant_output o where " + where
                + " order by o.created_at desc, o.id limit " + PAGE).params(params).query((rs, n) -> row(rs)).list();
    }

    @Transactional(readOnly = true)
    AssistantRequests.Output get(UUID id) {
        return find(id);
    }

    @Transactional
    AssistantRequests.Output review(UUID id, AssistantRequests.Review review) {
        AssistantRequests.Output draft = find(id);
        if (!"DRAFT".equals(draft.status())) {
            throw new BusinessRuleException(ALREADY_REVIEWED, "the draft has already been reviewed");
        }
        ActorRef actor = currentActor.require();
        jdbc.sql("""
                update assistant_output set status = :status, reviewed_by = :by, reviewed_at = now(), review_note = :note
                where id = :id""").param("status", "APPROVED".equals(review.decision()) ? "USER_APPROVED" : "DISCARDED")
                .param("by", actor.id()).param("note", review.note() == null || review.note().isBlank() ? null : review.note().strip())
                .param("id", id).update();
        audit.recordWithChange("ASSISTANT_OUTPUT_" + review.decision(), actor.id(), actor.username(), "ASSISTANT_OUTPUT",
                id.toString(), null);
        return find(id);
    }

    /** Own drafts only, and only while the case is still visible: anyone else is told it does not exist. */
    private AssistantRequests.Output find(UUID id) {
        AssistantRequests.Output found = jdbc.sql("select " + COLUMNS + " from assistant_output where id = :id and user_id = :user")
                .param("id", id).param("user", currentActor.require().id()).query((rs, n) -> row(rs)).optional()
                .orElseThrow(() -> new NotFoundException("assistant output"));
        if (found.caseId() != null) {
            access.requireView(found.caseId());
        }
        return found;
    }

    private static AssistantRequests.Output row(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new AssistantRequests.Output(rs.getObject("id", UUID.class), rs.getObject("session_id", UUID.class),
                rs.getObject("case_id", UUID.class), rs.getString("kind"), rs.getString("title"), rs.getString("status"),
                rs.getBoolean("is_final"), rs.getBoolean("non_production"), JSON.readTree(rs.getString("content")),
                rs.getObject("reviewed_by", UUID.class),
                rs.getTimestamp("reviewed_at") == null ? null : rs.getTimestamp("reviewed_at").toInstant(),
                rs.getString("review_note"), rs.getTimestamp("created_at").toInstant());
    }
}
