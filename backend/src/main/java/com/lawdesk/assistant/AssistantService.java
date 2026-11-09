package com.lawdesk.assistant;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.lawdesk.api.NotFoundException;
import com.lawdesk.audit.AuditLog;
import com.lawdesk.cases.CaseAccessPolicy;
import com.lawdesk.security.ActorRef;
import com.lawdesk.security.CurrentActor;
import com.lawdesk.security.Permission;

/**
 * The legal assistant (requirements section 22; owner decision D8): a READ-ONLY layer over what the user may already see.
 *
 * <ul>
 *   <li>It finds its sources only through {@link Retriever}, i.e. through the modules that enforce the case-access rule, so a
 *       case the user may not see is reported as missing (404) and none of its content can reach an answer.</li>
 *   <li>It answers only from those sources and says which ({@code sources}, numbered); when retrieval finds nothing adequate it
 *       refuses with {@code INSUFFICIENT_SOURCES} instead of inventing.</li>
 *   <li>Text in documents and notes is data. It is quoted; it is never obeyed, and nothing here can act on a record: the only
 *       rows written are the assistant's own session, draft and log.</li>
 *   <li>Every question is logged ({@link QueryLog}), including a refused one, and audited.</li>
 *   <li>It sends nothing out of the system: the only provider is the local test provider, and there is no client of any kind.</li>
 * </ul>
 */
@Service
class AssistantService {

    private final Retriever retriever;
    private final AssistantProvider provider;
    private final AssistantStatus status;
    private final AssistantOutputs outputs;
    private final QueryLog log;
    private final CaseAccessPolicy access;
    private final CurrentActor currentActor;
    private final AuditLog audit;
    private final JdbcClient jdbc;

    AssistantService(Retriever retriever, AssistantProvider provider, AssistantStatus status, AssistantOutputs outputs, QueryLog log,
            CaseAccessPolicy access, CurrentActor currentActor, AuditLog audit, JdbcClient jdbc) {
        this.retriever = retriever;
        this.provider = provider;
        this.status = status;
        this.outputs = outputs;
        this.log = log;
        this.access = access;
        this.currentActor = currentActor;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    /**
     * Not one transaction on purpose: retrieval reads in its own read-only transaction, and a refusal (a case the user may not see)
     * must not leave a transaction marked for rollback behind the log entry it has just written.
     */
    AssistantRequests.Answer ask(AssistantRequests.Ask request) {
        AssistantTask task = request.task();
        Retriever.requireShape(task, request.caseId(), request.documentId(), request.otherDocumentId(), request.question());
        ActorRef actor = currentActor.require();
        UUID sessionId = request.sessionId();
        if (sessionId != null && ownSession(sessionId) == 0) {
            throw new NotFoundException("session");
        }
        AssistantStatus.Status state = status.current();
        Plan plan;
        try {
            plan = plan(request);
        } catch (NotFoundException e) {
            log.record(sessionId, request.caseId(), task, request.question(), "DENIED_NO_CASE_ACCESS", List.of(), provider.info(), null);
            audit.record("ASSISTANT_QUERY", AuditLog.Outcome.DENIED, actor.id(), actor.username(), "CASE",
                    request.caseId() == null ? null : request.caseId().toString(), "NOT_VISIBLE");
            throw e;
        } catch (AccessDeniedException e) {
            log.record(sessionId, request.caseId(), task, request.question(), "DENIED_PERMISSION", List.of(), provider.info(), null);
            audit.record("ASSISTANT_QUERY", AuditLog.Outcome.DENIED, actor.id(), actor.username(), "CASE",
                    request.caseId() == null ? null : request.caseId().toString(), "PERMISSION");
            throw e;
        }
        UUID caseId = plan.caseId();
        UUID session = sessionId != null ? touch(sessionId) : newSession(caseId);
        String target = caseId == null ? null : caseId.toString();
        if (plan.refusal() != null) {
            long queryId = log.record(session, caseId, task, request.question(), "INSUFFICIENT_SOURCES", List.of(), provider.info(), null);
            audit.record("ASSISTANT_QUERY", AuditLog.Outcome.SUCCESS, actor.id(), actor.username(), "CASE", target, "INSUFFICIENT_SOURCES");
            return new AssistantRequests.Answer(queryId, session, task, "INSUFFICIENT_SOURCES", plan.refusal(), state.operational(),
                    state.reason(), state.mode(), provider.info(), caseId, List.of(), List.of(), null, false);
        }
        AssistantProvider.Composition composition = provider.compose(
                new AssistantProvider.Request(task, request.question(), plan.sections(), plan.sources()));
        UUID outputId = null;
        if (task == AssistantTask.MEMO_DRAFT || request.keep()) {
            outputId = outputs.save(session, caseId, task, request.question(), composition.blocks(), plan.sources(), provider.info());
        }
        long queryId = log.record(session, caseId, task, request.question(), "ANSWERED", plan.sources(), provider.info(), outputId);
        audit.record("ASSISTANT_QUERY", AuditLog.Outcome.SUCCESS, actor.id(), actor.username(), "CASE", target, "ANSWERED");
        return new AssistantRequests.Answer(queryId, session, task, "ANSWERED", null, state.operational(), state.reason(), state.mode(),
                provider.info(), caseId, composition.blocks(), plan.sources(), outputId, false);
    }

    private Plan plan(AssistantRequests.Ask r) {
        boolean readsCases = r.caseId() != null || r.documentId() != null;
        if (readsCases && !currentActor.has(Permission.CASE_VIEW)) {
            throw new AccessDeniedException("missing CASE_VIEW");
        }
        if (r.caseId() != null) {
            access.requireView(r.caseId());
        }
        return switch (r.task()) {
            case QUESTION -> retriever.question(r.caseId(), r.question());
            case CASE_SUMMARY -> retriever.summary(r.caseId());
            case MEMO_DRAFT -> retriever.memo(r.caseId());
            case PRECEDENTS -> retriever.precedents(r.caseId(), r.question());
            case DOCUMENT_ANALYSIS -> retriever.analysis(r.documentId());
            case COMPARISON -> retriever.comparison(r.documentId(), r.otherDocumentId());
        };
    }

    private int ownSession(UUID id) {
        return jdbc.sql("select count(*) from assistant_session where id = :id and user_id = :user").param("id", id)
                .param("user", currentActor.require().id()).query(Integer.class).single();
    }

    private UUID touch(UUID id) {
        jdbc.sql("update assistant_session set last_used_at = now() where id = :id").param("id", id).update();
        return id;
    }

    private UUID newSession(UUID caseId) {
        return jdbc.sql("insert into assistant_session (user_id, case_id) values (:user, :case) returning id")
                .param("user", currentActor.require().id()).param("case", caseId).query(UUID.class).single();
    }
}
