package com.lawdesk.directives;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.data.domain.PageImpl;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.api.BusinessRuleException;
import com.lawdesk.api.ConflictException;
import com.lawdesk.api.FieldErrorException;
import com.lawdesk.api.NotFoundException;
import com.lawdesk.api.PageResponse;
import com.lawdesk.audit.AuditLog;
import com.lawdesk.cases.CaseAccessPolicy;
import com.lawdesk.cases.CaseLookup;
import com.lawdesk.cases.CaseSummary;
import com.lawdesk.directives.DirectiveRequests.Directive;
import com.lawdesk.directives.DirectiveRequests.Status;
import com.lawdesk.library.LibraryText;
import com.lawdesk.lists.ManagedLists;
import com.lawdesk.security.ActorRef;
import com.lawdesk.security.CurrentActor;
import com.lawdesk.security.Permission;

/**
 * The register of administrative decisions and assignments about cases (LD-057, requirements section 25). A directive
 * belongs to a case, so every route follows the case access rules (D3): the register lists only directives of visible cases,
 * and a directive of a case the user may not see is reported missing. Issuing and editing need DIRECTIVE_MANAGE and the right to
 * change the case; the addressee may move the execution status of their own directive without being able to change the case.
 */
@Service
class DirectiveService {

    static final String KIND_UNKNOWN = "DIRECTIVE_KIND_UNKNOWN";
    static final String ADDRESSEE_UNKNOWN = "DIRECTIVE_ADDRESSEE_UNKNOWN";
    static final String TASK_NOT_IN_CASE = "DIRECTIVE_TASK_NOT_IN_CASE";
    static final String STATUS_UNCHANGED = "DIRECTIVE_STATUS_UNCHANGED";
    static final String CASE_ARCHIVED = "CASE_ARCHIVED";

    private static final ZoneId KUWAIT = ZoneId.of("Asia/Kuwait");
    private static final String OPEN = "d.status in ('NEW', 'IN_PROGRESS')";

    private static final String SELECT = "select d.id, d.directive_no, d.case_id, c.reference_no, c.title as case_title,"
            + " d.task_id, d.kind, d.title, d.details, d.directive_date, d.source, d.source_reference_no,"
            + " d.addressee_user_id, d.addressee_text, u.display_name, d.due_date, d.status, d.status_note,"
            + " d.completed_at, d.version, d.created_at, d.created_by, d.updated_at, d.updated_by,"
            + " (" + OPEN + " and d.due_date is not null and d.due_date < :today) as overdue"
            + " from directive d join legal_case c on c.id = d.case_id"
            + " left join app_user u on u.id = d.addressee_user_id";

    private final JdbcClient jdbc;
    private final CaseLookup cases;
    private final CaseAccessPolicy access;
    private final CurrentActor currentActor;
    private final AuditLog audit;
    private final ManagedLists lists;

    DirectiveService(JdbcClient jdbc, CaseLookup cases, CaseAccessPolicy access, CurrentActor currentActor,
            AuditLog audit, ManagedLists lists) {
        this.jdbc = jdbc;
        this.cases = cases;
        this.access = access;
        this.currentActor = currentActor;
        this.audit = audit;
        this.lists = lists;
    }

    private static LocalDate today() {
        return LocalDate.now(KUWAIT);
    }

    // ---- reads ----------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    PageResponse<Directive> search(DirectiveRequests.Search search) {
        CaseAccessPolicy.Predicate visible = access.visibleCaseIds("d.case_id");
        Map<String, Object> params = new HashMap<>(visible.params());
        params.put("today", today());
        StringBuilder where = new StringBuilder(" where ").append(visible.sql());
        if (search.status() != null) {
            where.append(" and d.status = :status");
            params.put("status", search.status().name());
        }
        if (search.kind() != null) {
            where.append(" and d.kind = :kind");
            params.put("kind", search.kind());
        }
        if (search.caseId() != null) {
            where.append(" and d.case_id = :caseId");
            params.put("caseId", search.caseId());
        }
        if (search.addressee() != null) {
            where.append(" and d.addressee_user_id = :addressee");
            params.put("addressee", addresseeFilter(search.addressee()));
        }
        if (Boolean.TRUE.equals(search.overdue())) {
            where.append(" and ").append(OPEN).append(" and d.due_date is not null and d.due_date < :today");
        }
        if (search.q() != null) {
            String normalised = jdbc.sql("select library_normalize(:q)").param("q", search.q()).query(String.class).single();
            String tsQuery = LibraryText.allOfPrefixes(normalised);
            if (tsQuery == null) {
                return new PageResponse<>(List.of(), search.pageOrDefault(), search.sizeOrDefault(), 0, 0);
            }
            where.append(" and d.search_vector @@ to_tsquery('simple', cast(:tsq as text))");
            params.put("tsq", tsQuery);
        }
        long total = jdbc.sql("select count(*) from directive d join legal_case c on c.id = d.case_id" + where)
                .params(params).query(Long.class).single();
        params.put("limit", search.sizeOrDefault());
        params.put("offset", (long) search.pageOrDefault() * search.sizeOrDefault());
        List<Directive> items = jdbc.sql(SELECT + where + " order by (" + OPEN + ") desc, d.due_date asc nulls last,"
                        + " d.directive_date desc, d.id offset :offset limit :limit")
                .params(params).query((rs, n) -> view(rs)).list();
        return PageResponse.from(new PageImpl<>(items,
                org.springframework.data.domain.PageRequest.of(search.pageOrDefault(), search.sizeOrDefault()), total));
    }

    private UUID addresseeFilter(String value) {
        if ("me".equals(value)) {
            return currentActor.require().id();
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new FieldErrorException("addressee", "Pattern");
        }
    }

    @Transactional(readOnly = true)
    Directive get(UUID id) {
        Directive found = load(id);
        access.requireView(found.caseId());
        return found;
    }

    private Directive load(UUID id) {
        return jdbc.sql(SELECT + " where d.id = :id").param("id", id).param("today", today())
                .query((rs, n) -> view(rs)).optional().orElseThrow(() -> new NotFoundException("directive"));
    }

    private static Directive view(ResultSet rs) throws SQLException {
        String text = rs.getString("addressee_text");
        String name = rs.getString("display_name");
        return new Directive(rs.getObject("id", UUID.class), rs.getString("directive_no"),
                rs.getObject("case_id", UUID.class), rs.getString("reference_no"), rs.getString("case_title"),
                rs.getObject("task_id", UUID.class), rs.getString("kind"), rs.getString("title"), rs.getString("details"),
                rs.getObject("directive_date", LocalDate.class), rs.getString("source"),
                rs.getString("source_reference_no"), rs.getObject("addressee_user_id", UUID.class), text,
                name != null ? name : text, rs.getObject("due_date", LocalDate.class), rs.getString("status"),
                rs.getString("status_note"),
                rs.getTimestamp("completed_at") == null ? null : rs.getTimestamp("completed_at").toInstant(),
                rs.getBoolean("overdue"), rs.getLong("version"), rs.getTimestamp("created_at").toInstant(),
                rs.getObject("created_by", UUID.class), rs.getTimestamp("updated_at").toInstant(),
                rs.getObject("updated_by", UUID.class));
    }

    // ---- writes ---------------------------------------------------------------------------------------------------

    @Transactional
    Directive create(DirectiveRequests.Create request) {
        ActorRef actor = currentActor.require();
        access.requireChange(request.caseId());
        requireWritable(request.caseId());
        checkFields(request.caseId(), request.kind(), request.directiveDate(), request.dueDate(), request.addresseeUserId(),
                request.addresseeText(), request.taskId(), true);
        UUID id = jdbc.sql("""
                        insert into directive (directive_no, case_id, task_id, kind, title, details, directive_date, source,
                            source_reference_no, addressee_user_id, addressee_text, due_date, created_by, updated_by)
                        values ('DIR-' || to_char(now() at time zone 'Asia/Kuwait', 'YYYY') || '-'
                                    || lpad(nextval('directive_no_seq')::text, 5, '0'),
                            :caseId, :task, :kind, :title, :details, :date, :source, :sourceRef, :user, :text, :due, :actor,
                            :actor)
                        returning id""")
                .param("caseId", request.caseId()).param("task", request.taskId()).param("kind", request.kind())
                .param("title", request.title()).param("details", request.details()).param("date", request.directiveDate())
                .param("source", request.source()).param("sourceRef", request.sourceReferenceNo())
                .param("user", request.addresseeUserId()).param("text", request.addresseeText())
                .param("due", request.dueDate()).param("actor", actor.id()).query(UUID.class).single();
        Directive created = load(id);
        cases.recordEvent(request.caseId(), "DIRECTIVE_ISSUED", "Directive " + created.directiveNo() + " issued");
        audit.recordWithChange("DIRECTIVE_CREATE", actor.id(), actor.username(), "DIRECTIVE", id.toString(),
                created.directiveNo());
        return created;
    }

    @Transactional
    Directive update(UUID id, DirectiveRequests.Update request) {
        ActorRef actor = currentActor.require();
        Directive current = load(id);
        access.requireChange(current.caseId());
        requireWritable(current.caseId());
        checkFields(current.caseId(), Objects.equals(current.kind(), request.kind()) ? null : request.kind(),
                request.directiveDate(), request.dueDate(), request.addresseeUserId(), request.addresseeText(),
                request.taskId(), !Objects.equals(current.taskId(), request.taskId()));
        int updated = jdbc.sql("""
                        update directive set task_id = :task, kind = :kind, title = :title, details = :details,
                            directive_date = :date, source = :source, source_reference_no = :sourceRef,
                            addressee_user_id = :user, addressee_text = :text, due_date = :due, version = version + 1,
                            updated_at = now(), updated_by = :actor
                        where id = :id and version = :version""")
                .param("task", request.taskId()).param("kind", request.kind()).param("title", request.title())
                .param("details", request.details()).param("date", request.directiveDate())
                .param("source", request.source()).param("sourceRef", request.sourceReferenceNo())
                .param("user", request.addresseeUserId()).param("text", request.addresseeText())
                .param("due", request.dueDate()).param("actor", actor.id()).param("id", id)
                .param("version", request.version()).update();
        if (updated != 1) {
            throw new ConflictException("the directive was changed by someone else");
        }
        cases.recordEvent(current.caseId(), "DIRECTIVE_UPDATED", "Directive " + current.directiveNo() + " updated");
        audit.recordWithChange("DIRECTIVE_UPDATE", actor.id(), actor.username(), "DIRECTIVE", id.toString(),
                current.directiveNo());
        return load(id);
    }

    @Transactional
    Directive changeStatus(UUID id, DirectiveRequests.StatusChange request) {
        ActorRef actor = currentActor.require();
        Directive current = load(id);
        access.requireView(current.caseId());
        boolean addressee = actor.id().equals(current.addresseeUserId());
        boolean editor = currentActor.has(Permission.DIRECTIVE_MANAGE) && access.mayChange(current.caseId());
        if (!addressee && !editor) {
            throw new AccessDeniedException("only the addressee or an editor moves the status of a directive");
        }
        requireWritable(current.caseId());
        Status to = request.status();
        if (to.name().equals(current.status())) {
            throw new BusinessRuleException(STATUS_UNCHANGED, "the directive already has this status");
        }
        int updated = jdbc.sql("""
                        update directive set status = :to, status_note = :note,
                            completed_at = case when :to = 'DONE' then now() end, version = version + 1, updated_at = now(),
                            updated_by = :actor
                        where id = :id and version = :version""")
                .param("to", to.name()).param("note", request.note()).param("actor", actor.id()).param("id", id)
                .param("version", request.version()).update();
        if (updated != 1) {
            throw new ConflictException("the directive was changed by someone else");
        }
        String summary = "Directive " + current.directiveNo() + ": " + current.status() + " -> " + to;
        if (access.mayChange(current.caseId())) {
            cases.recordEvent(current.caseId(), "DIRECTIVE_STATUS_CHANGED", summary);
        } else {
            // The addressee sees the case without being able to change it: the entry is made on the strength of the directive.
            cases.recordTaskEvent(current.caseId(), "DIRECTIVE_STATUS_CHANGED", summary);
        }
        audit.recordWithChange("DIRECTIVE_STATUS", actor.id(), actor.username(), "DIRECTIVE", id.toString(),
                current.status() + "->" + to);
        return load(id);
    }

    // ---- rules ----------------------------------------------------------------------------------------------------

    private void requireWritable(UUID caseId) {
        CaseSummary summary = cases.find(caseId).orElseThrow(() -> new NotFoundException("case"));
        if ("ARCHIVED".equals(summary.status())) {
            throw new BusinessRuleException(CASE_ARCHIVED, "an archived case is read-only");
        }
    }

    private void checkFields(UUID caseId, String newKind, LocalDate date, LocalDate due, UUID addresseeUser,
            String addresseeText, UUID taskId, boolean checkTask) {
        if (addresseeUser == null && addresseeText == null) {
            throw new FieldErrorException("addressee", "NotNull");
        }
        if (due != null && due.isBefore(date)) {
            throw new FieldErrorException("dueDate", "FutureOrPresent");
        }
        if (newKind != null) {
            lists.requireActive(ManagedLists.DIRECTIVE_KIND, newKind, KIND_UNKNOWN);
        }
        if (addresseeUser != null) {
            boolean known = jdbc.sql("select exists (select 1 from app_user where id = :id and enabled)")
                    .param("id", addresseeUser).query(Boolean.class).single();
            if (!known) {
                throw new BusinessRuleException(ADDRESSEE_UNKNOWN, "the addressee is not an enabled user");
            }
        }
        if (taskId != null && checkTask) {
            boolean sameCase = jdbc.sql("select exists (select 1 from task where id = :t and case_id = :c)")
                    .param("t", taskId).param("c", caseId).query(Boolean.class).single();
            if (!sameCase) {
                throw new BusinessRuleException(TASK_NOT_IN_CASE, "the task is not a task of this case");
            }
        }
    }
}
