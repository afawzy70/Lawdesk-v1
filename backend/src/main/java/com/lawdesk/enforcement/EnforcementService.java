package com.lawdesk.enforcement;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.api.BusinessRuleException;
import com.lawdesk.api.ConflictException;
import com.lawdesk.api.FieldErrorException;
import com.lawdesk.api.NotFoundException;
import com.lawdesk.audit.AuditLog;
import com.lawdesk.cases.CaseAccessPolicy;
import com.lawdesk.cases.CaseLookup;
import com.lawdesk.cases.CaseSummary;
import com.lawdesk.security.ActorRef;
import com.lawdesk.security.CurrentActor;

/**
 * The enforcement file of a judgment (requirements section 11): who must enforce it, the amount asked, the obstacles, the
 * status and the actions taken with their dates and results, plus the lists of judgments not executed and late. A date
 * the file calls late is a target entered by the member ({@code due_on}), never a legal period computed by the system.
 * Every read and write goes through the case access rule: a judgment of a case the user may not see is reported missing.
 */
@Service
class EnforcementService {

    static final Set<String> STATUSES = Set.of("NOT_STARTED", "IN_PROGRESS", "PARTIALLY_EXECUTED", "EXECUTED", "OBSTRUCTED",
            "NOT_REQUIRED");
    static final Set<String> KINDS = Set.of("PROCEDURE", "CORRESPONDENCE", "OBSTACLE", "OTHER");
    static final String ALREADY_RECORDED = "ENFORCEMENT_ALREADY_RECORDED";
    static final String CASE_ARCHIVED = "CASE_ARCHIVED";

    private static final ZoneId KUWAIT = ZoneId.of("Asia/Kuwait");
    private static final String DONE = "('EXECUTED', 'NOT_REQUIRED')";

    private static final String JUDGMENT = """
            select j.id, p.case_id, c.reference_no, c.title, p.stage, p.court_case_number, p.court_case_year,
                   j.judgment_date, j.operative_text, j.summary, j.final
            from judgment j join proceeding p on p.id = j.proceeding_id join legal_case c on c.id = p.case_id""";
    private static final String FILE = """
            select f.id, f.judgment_id, f.case_id, f.status, f.responsible_member_id, u.display_name, f.executing_party,
                   f.amount_required, f.obstacles, f.due_on, f.completed_on, f.notes, f.version, f.updated_at,
                   (f.status not in ('EXECUTED', 'NOT_REQUIRED') and f.due_on is not null and f.due_on < :today) as late
            from enforcement_file f left join app_user u on u.id = f.responsible_member_id""";

    private final JdbcClient jdbc;
    private final CaseAccessPolicy access;
    private final CaseLookup cases;
    private final CurrentActor currentActor;
    private final AuditLog audit;

    EnforcementService(JdbcClient jdbc, CaseAccessPolicy access, CaseLookup cases, CurrentActor currentActor, AuditLog audit) {
        this.jdbc = jdbc;
        this.access = access;
        this.cases = cases;
        this.currentActor = currentActor;
        this.audit = audit;
    }

    // ---- one judgment ------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    EnforcementRequests.Overview overview(UUID judgmentId) {
        EnforcementRequests.JudgmentRef judgment = judgment(judgmentId);
        access.requireView(judgment.caseId());
        EnforcementRequests.FileView file = jdbc.sql(FILE + " where f.judgment_id = :id").param("id", judgmentId)
                .param("today", today()).query(EnforcementService::file).optional().orElse(null);
        return new EnforcementRequests.Overview(judgment, file, file == null ? List.of() : actions(file.id()));
    }

    @Transactional
    EnforcementRequests.Overview create(UUID judgmentId, EnforcementRequests.FileBody body) {
        ActorRef actor = currentActor.require();
        EnforcementRequests.JudgmentRef judgment = judgment(judgmentId);
        CaseSummary legalCase = writable(judgment.caseId());
        Values values = check(body, "NOT_STARTED");
        try {
            jdbc.sql("""
                    insert into enforcement_file (judgment_id, case_id, status, responsible_member_id, executing_party,
                           amount_required, obstacles, due_on, completed_on, notes, created_by, updated_by)
                    values (:judgment, :case, :status, :member, :party, :amount, :obstacles, :due, :completed, :notes,
                            :actor, :actor)""").param("judgment", judgmentId).param("case", judgment.caseId())
                    .params(values.map()).param("actor", actor.id()).update();
        } catch (DuplicateKeyException e) {
            throw new BusinessRuleException(ALREADY_RECORDED, "the judgment has an enforcement file already");
        }
        cases.recordEvent(legalCase.id(), "ENFORCEMENT_RECORDED", "Enforcement file opened for the judgment of %s"
                .formatted(judgment.judgmentDate()));
        audit.recordWithChange("ENFORCEMENT_CREATE", actor.id(), actor.username(), "JUDGMENT", judgmentId.toString(), null);
        return overview(judgmentId);
    }

    @Transactional
    EnforcementRequests.Overview update(UUID fileId, EnforcementRequests.FileBody body) {
        ActorRef actor = currentActor.require();
        UUID judgmentId = jdbc.sql("select judgment_id from enforcement_file where id = :id").param("id", fileId)
                .query(UUID.class).optional().orElseThrow(() -> new NotFoundException("enforcement file"));
        EnforcementRequests.JudgmentRef judgment = judgment(judgmentId);
        CaseSummary legalCase = writable(judgment.caseId());
        Values values = check(body, null);
        int updated = jdbc.sql("""
                update enforcement_file set status = :status, responsible_member_id = :member, executing_party = :party,
                       amount_required = :amount, obstacles = :obstacles, due_on = :due, completed_on = :completed,
                       notes = :notes, version = version + 1, updated_at = now(), updated_by = :actor
                where id = :id and version = :version""").params(values.map()).param("actor", actor.id()).param("id", fileId)
                .param("version", body.version() == null ? -1 : body.version()).update();
        if (updated == 0) {
            throw new ConflictException("enforcement file " + fileId + " was changed by someone else");
        }
        cases.recordEvent(legalCase.id(), "ENFORCEMENT_CHANGED", "Enforcement of the judgment of %s: %s"
                .formatted(judgment.judgmentDate(), values.map().get("status")));
        audit.recordWithChange("ENFORCEMENT_UPDATE", actor.id(), actor.username(), "JUDGMENT", judgmentId.toString(),
                String.valueOf(values.map().get("status")));
        return overview(judgmentId);
    }

    @Transactional
    EnforcementRequests.ActionView addAction(UUID fileId, EnforcementRequests.ActionBody body) {
        ActorRef actor = currentActor.require();
        UUID caseId = caseOfFile(fileId);
        CaseSummary legalCase = writable(caseId);
        checkAction(body, caseId);
        // The file row is locked so two actions never take the same number.
        jdbc.sql("select id from enforcement_file where id = :id for update").param("id", fileId).query(UUID.class).list();
        int seq = jdbc.sql("select coalesce(max(seq), 0) + 1 from enforcement_action where enforcement_id = :id")
                .param("id", fileId).query(Integer.class).single();
        UUID id = jdbc.sql("""
                insert into enforcement_action (enforcement_id, seq, kind, action_date, description, result, document_id,
                       created_by, updated_by)
                values (:file, :seq, :kind, :date, :description, :result, :document, :actor, :actor) returning id""")
                .param("file", fileId).param("seq", seq).params(actionValues(body)).param("actor", actor.id())
                .query(UUID.class).single();
        cases.recordEvent(legalCase.id(), "ENFORCEMENT_ACTION_ADDED", "Enforcement action %d recorded (%s)".formatted(seq,
                body.kind()));
        audit.recordWithChange("ENFORCEMENT_ACTION_CREATE", actor.id(), actor.username(), "ENFORCEMENT", id.toString(), null);
        return action(id);
    }

    @Transactional
    EnforcementRequests.ActionView updateAction(UUID id, EnforcementRequests.ActionBody body) {
        ActorRef actor = currentActor.require();
        UUID caseId = jdbc.sql("""
                select f.case_id from enforcement_action a join enforcement_file f on f.id = a.enforcement_id
                where a.id = :id""").param("id", id).query(UUID.class).optional()
                .orElseThrow(() -> new NotFoundException("enforcement action"));
        CaseSummary legalCase = writable(caseId);
        checkAction(body, caseId);
        int updated = jdbc.sql("""
                update enforcement_action set kind = :kind, action_date = :date, description = :description,
                       result = :result, document_id = :document, version = version + 1, updated_at = now(),
                       updated_by = :actor where id = :id and version = :version""").params(actionValues(body))
                .param("actor", actor.id()).param("id", id).param("version", body.version() == null ? -1 : body.version())
                .update();
        if (updated == 0) {
            throw new ConflictException("enforcement action " + id + " was changed by someone else");
        }
        cases.recordEvent(legalCase.id(), "ENFORCEMENT_ACTION_CHANGED", "Enforcement action changed (%s)".formatted(body.kind()));
        audit.recordWithChange("ENFORCEMENT_ACTION_UPDATE", actor.id(), actor.username(), "ENFORCEMENT", id.toString(), null);
        return action(id);
    }

    // ---- the lists ---------------------------------------------------------------------------------------------

    /**
     * Judgments of the cases the user may see that are not executed ({@code UNEXECUTED}: no file or a file not EXECUTED or
     * NOT_REQUIRED) or late ({@code LATE}: also past the target date), or all judgments ({@code ALL}).
     */
    @Transactional(readOnly = true)
    Page<EnforcementRequests.ListItem> list(String scope, UUID caseId, Pageable pageable) {
        String which = scope == null ? "UNEXECUTED" : scope;
        if (!Set.of("UNEXECUTED", "LATE", "ALL").contains(which)) {
            throw new FieldErrorException("scope", "Unknown");
        }
        CaseAccessPolicy.Predicate visible = access.visibleCaseIds("p.case_id");
        Map<String, Object> params = new HashMap<>(visible.params());
        params.put("today", today());
        StringBuilder where = new StringBuilder(" where ").append(visible.sql());
        if (caseId != null) {
            params.put("caseId", caseId);
            where.append(" and p.case_id = :caseId");
        }
        if ("UNEXECUTED".equals(which)) {
            where.append(" and (f.id is null or f.status not in ").append(DONE).append(')');
        } else if ("LATE".equals(which)) {
            where.append(" and f.status not in ").append(DONE).append(" and f.due_on is not null and f.due_on < :today");
        }
        String from = """
                 from judgment j join proceeding p on p.id = j.proceeding_id join legal_case c on c.id = p.case_id
                 left join enforcement_file f on f.judgment_id = j.id left join app_user u on u.id = f.responsible_member_id""";
        Long total = jdbc.sql("select count(*)" + from + where).params(params).query(Long.class).single();
        params.put("offset", pageable.getOffset());
        params.put("limit", pageable.getPageSize());
        List<EnforcementRequests.ListItem> items = jdbc.sql("""
                select j.id, p.case_id, c.reference_no, c.title, p.stage, p.court_case_number, p.court_case_year,
                       j.judgment_date, f.status, f.due_on, f.responsible_member_id, u.display_name, f.executing_party,
                       f.amount_required,
                       (f.status not in ('EXECUTED', 'NOT_REQUIRED') and f.due_on is not null and f.due_on < :today) as late
                """ + from + where + " order by (f.due_on is null), f.due_on, j.judgment_date, j.id offset :offset limit :limit")
                .params(params).query((rs, n) -> new EnforcementRequests.ListItem(rs.getObject("id", UUID.class),
                        rs.getObject("case_id", UUID.class), rs.getString("reference_no"), rs.getString("title"),
                        rs.getString("stage"), rs.getString("court_case_number"), (Integer) rs.getObject("court_case_year"),
                        rs.getObject("judgment_date", LocalDate.class), rs.getString("status"), rs.getString("status") != null,
                        rs.getObject("due_on", LocalDate.class), rs.getBoolean("late"),
                        rs.getObject("responsible_member_id", UUID.class), rs.getString("display_name"),
                        rs.getString("executing_party"), rs.getBigDecimal("amount_required"))).list();
        return new PageImpl<>(items, PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()), total);
    }

    // ---- helpers -----------------------------------------------------------------------------------------------

    private record Values(Map<String, Object> map) {
    }

    private Values check(EnforcementRequests.FileBody body, String defaultStatus) {
        String status = body.status() == null ? defaultStatus : body.status();
        if (status == null) {
            throw new FieldErrorException("status", "NotNull");
        }
        if (!STATUSES.contains(status)) {
            throw new FieldErrorException("status", "Unknown");
        }
        if ("EXECUTED".equals(status) && body.completedOn() == null) {
            throw new FieldErrorException("completedOn", "NotNull");
        }
        if (body.completedOn() != null && body.completedOn().isAfter(today())) {
            throw new FieldErrorException("completedOn", "PastOrPresent");
        }
        BigDecimal amount = body.amountRequired();
        if (amount != null && (amount.signum() < 0 || amount.scale() > 3 || amount.precision() - amount.scale() > 12)) {
            throw new FieldErrorException("amountRequired", "Digits");
        }
        if (body.responsibleMemberId() != null && !jdbc.sql("select exists (select 1 from app_user where id = :id)")
                .param("id", body.responsibleMemberId()).query(Boolean.class).single()) {
            throw new FieldErrorException("responsibleMemberId", "Exists");
        }
        Map<String, Object> map = new HashMap<>();
        map.put("status", status);
        map.put("member", body.responsibleMemberId());
        map.put("party", text(body.executingParty(), "executingParty", 300));
        map.put("amount", amount);
        map.put("obstacles", text(body.obstacles(), "obstacles", 4000));
        map.put("due", body.dueOn());
        map.put("completed", body.completedOn());
        map.put("notes", text(body.notes(), "notes", 4000));
        return new Values(map);
    }

    private void checkAction(EnforcementRequests.ActionBody body, UUID caseId) {
        if (body.kind() == null || !KINDS.contains(body.kind())) {
            throw new FieldErrorException("kind", body.kind() == null ? "NotNull" : "Unknown");
        }
        if (body.actionDate() == null) {
            throw new FieldErrorException("actionDate", "NotNull");
        }
        if (body.description() == null || body.description().isBlank()) {
            throw new FieldErrorException("description", "NotBlank");
        }
        text(body.description(), "description", 4000);
        text(body.result(), "result", 4000);
        if (body.documentId() != null && !jdbc.sql("""
                select exists (select 1 from case_document where id = :id and case_id = :c and withdrawn_at is null)""")
                .param("id", body.documentId()).param("c", caseId).query(Boolean.class).single()) {
            throw new FieldErrorException("documentId", "NotInCase");
        }
    }

    private static Map<String, Object> actionValues(EnforcementRequests.ActionBody body) {
        Map<String, Object> map = new HashMap<>();
        map.put("kind", body.kind());
        map.put("date", body.actionDate());
        map.put("description", body.description().strip());
        map.put("result", body.result() == null || body.result().isBlank() ? null : body.result().strip());
        map.put("document", body.documentId());
        return map;
    }

    private static String text(String value, String field, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.strip().length() > max) {
            throw new FieldErrorException(field, "Size");
        }
        return value.strip();
    }

    private EnforcementRequests.JudgmentRef judgment(UUID id) {
        EnforcementRequests.JudgmentRef found = jdbc.sql(JUDGMENT + " where j.id = :id").param("id", id)
                .query((rs, n) -> new EnforcementRequests.JudgmentRef(rs.getObject("id", UUID.class),
                        rs.getObject("case_id", UUID.class), rs.getString("reference_no"), rs.getString("title"),
                        rs.getString("stage"), rs.getString("court_case_number"), (Integer) rs.getObject("court_case_year"),
                        rs.getObject("judgment_date", LocalDate.class), rs.getString("operative_text"),
                        rs.getString("summary"), rs.getBoolean("final")))
                .optional().orElseThrow(() -> new NotFoundException("judgment"));
        if (!access.canView(found.caseId())) {
            throw new NotFoundException("judgment");
        }
        return found;
    }

    private UUID caseOfFile(UUID fileId) {
        UUID caseId = jdbc.sql("select case_id from enforcement_file where id = :id").param("id", fileId).query(UUID.class)
                .optional().orElseThrow(() -> new NotFoundException("enforcement file"));
        access.requireView(caseId);
        return caseId;
    }

    private CaseSummary writable(UUID caseId) {
        CaseSummary legalCase = cases.find(caseId).orElseThrow(() -> new NotFoundException("case"));
        access.requireChange(caseId);
        if ("ARCHIVED".equals(legalCase.status())) {
            throw new BusinessRuleException(CASE_ARCHIVED, "an archived case is read-only");
        }
        return legalCase;
    }

    private List<EnforcementRequests.ActionView> actions(UUID fileId) {
        return jdbc.sql(ACTION + " where a.enforcement_id = :id order by a.seq").param("id", fileId)
                .query(EnforcementService::action).list();
    }

    private EnforcementRequests.ActionView action(UUID id) {
        return jdbc.sql(ACTION + " where a.id = :id").param("id", id).query(EnforcementService::action).single();
    }

    private static final String ACTION = """
            select a.id, a.enforcement_id, a.seq, a.kind, a.action_date, a.description, a.result, a.document_id,
                   d.title as document_title, a.version, a.created_at, a.created_by
            from enforcement_action a left join case_document d on d.id = a.document_id""";

    private static EnforcementRequests.ActionView action(ResultSet rs, int n) throws SQLException {
        return new EnforcementRequests.ActionView(rs.getObject("id", UUID.class), rs.getObject("enforcement_id", UUID.class),
                rs.getInt("seq"), rs.getString("kind"), rs.getObject("action_date", LocalDate.class),
                rs.getString("description"), rs.getString("result"), rs.getObject("document_id", UUID.class),
                rs.getString("document_title"), rs.getLong("version"), rs.getTimestamp("created_at").toInstant(),
                rs.getObject("created_by", UUID.class));
    }

    private static EnforcementRequests.FileView file(ResultSet rs, int n) throws SQLException {
        return new EnforcementRequests.FileView(rs.getObject("id", UUID.class), rs.getObject("judgment_id", UUID.class),
                rs.getObject("case_id", UUID.class), rs.getString("status"), rs.getObject("responsible_member_id", UUID.class),
                rs.getString("display_name"), rs.getString("executing_party"), rs.getBigDecimal("amount_required"),
                rs.getString("obstacles"), rs.getObject("due_on", LocalDate.class),
                rs.getObject("completed_on", LocalDate.class), rs.getString("notes"), rs.getBoolean("late") , rs.getLong("version"),
                rs.getTimestamp("updated_at").toInstant());
    }

    private static LocalDate today() {
        return LocalDate.now(KUWAIT);
    }
}
