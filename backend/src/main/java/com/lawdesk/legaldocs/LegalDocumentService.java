package com.lawdesk.legaldocs;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.lawdesk.api.BusinessRuleException;
import com.lawdesk.api.ConflictException;
import com.lawdesk.api.FieldErrorException;
import com.lawdesk.api.NotFoundException;
import com.lawdesk.audit.AuditLog;
import com.lawdesk.cases.CaseAccessPolicy;
import com.lawdesk.cases.CaseLookup;
import com.lawdesk.cases.CaseSummary;
import com.lawdesk.documents.DocumentArchive;
import com.lawdesk.security.ActorRef;
import com.lawdesk.security.CurrentActor;
import com.lawdesk.security.Permission;

/**
 * Memos and correspondence of a case with their approval cycle (requirements section 12; docs/design/LD-DOC-05 section 7):
 * DRAFT, IN_REVIEW, APPROVED, SENT, ARCHIVED. Approving needs MEMO_APPROVE and the right to see the case, not to change it
 * (viewing, changing and approving are separate rights, owner decision D3). SENT is a member's record that the document was
 * sent; {@code sent_via_integration} stays false because no sending integration exists.
 */
@Service
class LegalDocumentService {

    static final String DRAFT = "DRAFT";
    static final String IN_REVIEW = "IN_REVIEW";
    static final String APPROVED = "APPROVED";
    static final String SENT = "SENT";
    static final String ARCHIVED = "ARCHIVED";

    /** Stable rule codes, answered with 422. */
    static final String NOT_DRAFT = "LEGAL_DOCUMENT_NOT_DRAFT";
    static final String TRANSITION = "LEGAL_DOCUMENT_TRANSITION_NOT_ALLOWED";
    static final String NEEDS_FILE = "LEGAL_DOCUMENT_NEEDS_FILE";
    static final String CASE_ARCHIVED = "CASE_ARCHIVED";
    static final String DOCUMENT_NOT_IN_CASE = "LEGAL_DOCUMENT_FILE_NOT_IN_CASE";

    private static final ZoneId KUWAIT = ZoneId.of("Asia/Kuwait");
    private static final String SELECT = """
            select l.id, l.case_id, c.reference_no, c.title as case_title, l.category_id, cat.label_ar, cat.label_en, l.title,
                   l.document_date, l.issuer, l.addressee, l.responsible_member_id, rm.display_name as responsible_name,
                   l.description, l.document_id, d.file_name, d.current_version_no, l.status, l.submitted_by, l.submitted_at,
                   l.approved_by, ap.display_name as approved_name, l.approved_at, l.approval_note, l.sent_recorded, l.sent_on,
                   l.sent_method, l.sent_reference, l.sent_recorded_by, l.sent_recorded_at, l.sent_via_integration,
                   l.archived_at, l.version, l.created_at, l.created_by, l.updated_at
            from legal_document l
            join legal_case c on c.id = l.case_id
            left join lookup_value cat on cat.id = l.category_id
            left join app_user rm on rm.id = l.responsible_member_id
            left join app_user ap on ap.id = l.approved_by
            left join case_document d on d.id = l.document_id""";

    private final JdbcClient jdbc;
    private final CaseAccessPolicy access;
    private final CaseLookup cases;
    private final CurrentActor currentActor;
    private final AuditLog audit;
    private final DocumentArchive archive;

    LegalDocumentService(JdbcClient jdbc, CaseAccessPolicy access, CaseLookup cases, CurrentActor currentActor,
            AuditLog audit, DocumentArchive archive) {
        this.jdbc = jdbc;
        this.access = access;
        this.cases = cases;
        this.currentActor = currentActor;
        this.audit = audit;
        this.archive = archive;
    }

    // ---- reading -----------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    List<LegalDocumentRequests.View> forCase(UUID caseId) {
        access.requireView(caseId);
        return jdbc.sql(SELECT + " where l.case_id = :id order by l.created_at desc, l.id").param("id", caseId)
                .query(LegalDocumentService::view).list();
    }

    /** The register across the cases the user may see, filtered by status, case or category. */
    @Transactional(readOnly = true)
    Page<LegalDocumentRequests.View> register(String status, UUID caseId, UUID categoryId, Pageable pageable) {
        if (status != null && !Set.of(DRAFT, IN_REVIEW, APPROVED, SENT, ARCHIVED).contains(status)) {
            throw new FieldErrorException("status", "Unknown");
        }
        CaseAccessPolicy.Predicate visible = access.visibleCaseIds("l.case_id");
        Map<String, Object> params = new HashMap<>(visible.params());
        StringBuilder where = new StringBuilder(" where ").append(visible.sql());
        if (status != null) {
            params.put("status", status);
            where.append(" and l.status = :status");
        }
        if (caseId != null) {
            params.put("caseId", caseId);
            where.append(" and l.case_id = :caseId");
        }
        if (categoryId != null) {
            params.put("categoryId", categoryId);
            where.append(" and l.category_id = :categoryId");
        }
        Long total = jdbc.sql("select count(*) from legal_document l" + where).params(params).query(Long.class).single();
        params.put("offset", pageable.getOffset());
        params.put("limit", pageable.getPageSize());
        List<LegalDocumentRequests.View> items = jdbc.sql(SELECT + where
                + " order by l.created_at desc, l.id offset :offset limit :limit").params(params)
                .query(LegalDocumentService::view).list();
        return new PageImpl<>(items, PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()), total);
    }

    @Transactional(readOnly = true)
    LegalDocumentRequests.View get(UUID id) {
        LegalDocumentRequests.View found = find(id);
        access.requireView(found.caseId());
        return found;
    }

    @Transactional(readOnly = true)
    List<LegalDocumentRequests.Event> events(UUID id) {
        get(id);
        return jdbc.sql("""
                select e.id, e.from_status, e.to_status, e.note, e.created_at, e.created_by, u.display_name
                from legal_document_event e left join app_user u on u.id = e.created_by
                where e.legal_document_id = :id order by e.created_at, e.id""").param("id", id)
                .query((rs, n) -> new LegalDocumentRequests.Event(rs.getObject("id", UUID.class), rs.getString("from_status"),
                        rs.getString("to_status"), rs.getString("note"), rs.getTimestamp("created_at").toInstant(),
                        rs.getObject("created_by", UUID.class), rs.getString("display_name"))).list();
    }

    // ---- writing -----------------------------------------------------------------------------------------------

    @Transactional
    LegalDocumentRequests.View create(UUID caseId, LegalDocumentRequests.Body body) {
        ActorRef actor = currentActor.require();
        CaseSummary legalCase = writable(caseId);
        check(caseId, body, null);
        UUID id = jdbc.sql("""
                insert into legal_document (case_id, category_id, title, document_date, issuer, addressee,
                       responsible_member_id, description, document_id, created_by, updated_by)
                values (:case, :category, :title, :date, :issuer, :addressee, :member, :description, :document, :actor,
                        :actor) returning id""").param("case", caseId).params(values(body)).param("actor", actor.id())
                .query(UUID.class).single();
        event(id, null, DRAFT, null, actor);
        cases.recordEvent(legalCase.id(), "LEGAL_DOCUMENT_ADDED", "Memo or letter \"%s\" registered (draft)"
                .formatted(clip(body.title())));
        audit.recordWithChange("LEGAL_DOCUMENT_CREATE", actor.id(), actor.username(), "LEGAL_DOCUMENT", id.toString(), null);
        return find(id);
    }

    @Transactional
    LegalDocumentRequests.View update(UUID id, LegalDocumentRequests.Body body) {
        ActorRef actor = currentActor.require();
        LegalDocumentRequests.View current = find(id);
        writable(current.caseId());
        requireDraft(current);
        check(current.caseId(), body, current);
        int updated = jdbc.sql("""
                update legal_document set category_id = :category, title = :title, document_date = :date, issuer = :issuer,
                       addressee = :addressee, responsible_member_id = :member, description = :description,
                       document_id = coalesce(:document, document_id), version = version + 1, updated_at = now(),
                       updated_by = :actor where id = :id and version = :version""").params(values(body)).param("actor", actor.id()).param("id", id)
                .param("version", body.version() == null ? -1 : body.version()).update();
        if (updated == 0) {
            throw new ConflictException("memo or letter " + id + " was changed by someone else");
        }
        cases.recordEvent(current.caseId(), "LEGAL_DOCUMENT_CHANGED", "Memo or letter \"%s\" changed".formatted(clip(body.title())));
        audit.recordWithChange("LEGAL_DOCUMENT_UPDATE", actor.id(), actor.username(), "LEGAL_DOCUMENT", id.toString(), null);
        return find(id);
    }

    /** Stores the electronic copy: the first file creates the archived document, later ones are new versions of it. */
    @Transactional
    LegalDocumentRequests.View attachFile(UUID id, MultipartFile file, String note) {
        ActorRef actor = currentActor.require();
        LegalDocumentRequests.View current = find(id);
        writable(current.caseId());
        requireDraft(current);
        if (current.documentId() == null) {
            UUID documentId = archive.archive(current.caseId(), file, current.title());
            jdbc.sql("update legal_document set document_id = :d, version = version + 1, updated_at = now(), updated_by = :a"
                    + " where id = :id").param("d", documentId).param("a", actor.id()).param("id", id).update();
        } else {
            archive.addVersion(current.documentId(), file, note);
            jdbc.sql("update legal_document set version = version + 1, updated_at = now(), updated_by = :a where id = :id")
                    .param("a", actor.id()).param("id", id).update();
        }
        audit.recordWithChange("LEGAL_DOCUMENT_FILE", actor.id(), actor.username(), "LEGAL_DOCUMENT", id.toString(), null);
        return find(id);
    }

    /** DRAFT to IN_REVIEW: the member asks for approval; the document needs its electronic copy. */
    @Transactional
    LegalDocumentRequests.View submit(UUID id, LegalDocumentRequests.Note body) {
        ActorRef actor = currentActor.require();
        LegalDocumentRequests.View current = find(id);
        writable(current.caseId());
        require(current, DRAFT);
        if (current.documentId() == null) {
            throw new BusinessRuleException(NEEDS_FILE, "attach the electronic copy before asking for approval");
        }
        move(id, body.version(), DRAFT, IN_REVIEW, "submitted_by = :actor, submitted_at = now()", actor, note(body.note()));
        cases.recordEvent(current.caseId(), "LEGAL_DOCUMENT_SUBMITTED", "Memo or letter \"%s\" sent for review"
                .formatted(clip(current.title())));
        return find(id);
    }

    /** IN_REVIEW back to DRAFT: by the approver (who needs only to see the case) or by a member who may change it. */
    @Transactional
    LegalDocumentRequests.View returnToDraft(UUID id, LegalDocumentRequests.Note body) {
        ActorRef actor = currentActor.require();
        LegalDocumentRequests.View current = find(id);
        boolean approver = currentActor.has(Permission.MEMO_APPROVE);
        CaseSummary legalCase = approver ? seen(current.caseId()) : writable(current.caseId());
        require(current, IN_REVIEW);
        String why = requiredNote(body.note());
        move(id, body.version(), IN_REVIEW, DRAFT, "submitted_by = null, submitted_at = null", actor, why);
        caseEvent(approver, legalCase.id(), "LEGAL_DOCUMENT_RETURNED", "Memo or letter \"%s\" returned to draft"
                .formatted(clip(current.title())));
        return find(id);
    }

    /** IN_REVIEW to APPROVED: the name and date of the approver are kept. */
    @Transactional
    LegalDocumentRequests.View approve(UUID id, LegalDocumentRequests.Note body) {
        ActorRef actor = currentActor.require();
        LegalDocumentRequests.View current = find(id);
        CaseSummary legalCase = seen(current.caseId());
        require(current, IN_REVIEW);
        String text = note(body.note());
        move(id, body.version(), IN_REVIEW, APPROVED,
                "approved_by = :actor, approved_at = now(), approval_note = :note", actor, text);
        caseEvent(!access.mayChange(legalCase.id()), legalCase.id(), "LEGAL_DOCUMENT_APPROVED",
                "Memo or letter \"%s\" approved".formatted(clip(current.title())));
        return find(id);
    }

    /** APPROVED back to DRAFT, with a reason: the approval is cleared (the event history keeps it). */
    @Transactional
    LegalDocumentRequests.View reopen(UUID id, LegalDocumentRequests.Note body) {
        ActorRef actor = currentActor.require();
        LegalDocumentRequests.View current = find(id);
        CaseSummary legalCase = seen(current.caseId());
        require(current, APPROVED);
        String why = requiredNote(body.note());
        move(id, body.version(), APPROVED, DRAFT,
                "approved_by = null, approved_at = null, approval_note = null, submitted_by = null, submitted_at = null", actor,
                why);
        caseEvent(!access.mayChange(legalCase.id()), legalCase.id(), "LEGAL_DOCUMENT_REOPENED",
                "Memo or letter \"%s\" reopened as a draft".formatted(clip(current.title())));
        return find(id);
    }

    /** APPROVED to SENT: records that a member sent it (date, method, reference); nothing is delivered by the system. */
    @Transactional
    LegalDocumentRequests.View recordSent(UUID id, LegalDocumentRequests.SentRecord body) {
        ActorRef actor = currentActor.require();
        LegalDocumentRequests.View current = find(id);
        writable(current.caseId());
        require(current, APPROVED);
        if (body.sentOn() == null) {
            throw new FieldErrorException("sentOn", "NotNull");
        }
        if (body.sentOn().isAfter(LocalDate.now(KUWAIT))) {
            throw new FieldErrorException("sentOn", "PastOrPresent");
        }
        String method = required(body.method(), "method", 100);
        String reference = optional(body.reference(), "reference", 200);
        int moved = jdbc.sql("""
                update legal_document set status = 'SENT', sent_recorded = true, sent_on = :on, sent_method = :method,
                       sent_reference = :reference, sent_recorded_by = :actor, sent_recorded_at = now(),
                       version = version + 1, updated_at = now(), updated_by = :actor
                where id = :id and version = :version and status = 'APPROVED'""").param("on", body.sentOn())
                .param("method", method).param("reference", reference).param("actor", actor.id()).param("id", id)
                .param("version", body.version() == null ? -1 : body.version()).update();
        if (moved == 0) {
            throw new ConflictException("memo or letter " + id + " was changed by someone else");
        }
        event(id, APPROVED, SENT, method + (reference == null ? "" : " / " + reference), actor);
        cases.recordEvent(current.caseId(), "LEGAL_DOCUMENT_SENT_RECORDED", "Sending of memo or letter \"%s\" recorded (%s)"
                .formatted(clip(current.title()), method));
        audit.recordWithChange("LEGAL_DOCUMENT_SENT", actor.id(), actor.username(), "LEGAL_DOCUMENT", id.toString(), method);
        return find(id);
    }

    /** APPROVED or SENT to ARCHIVED (محفوظ). */
    @Transactional
    LegalDocumentRequests.View archive(UUID id, LegalDocumentRequests.Note body) {
        ActorRef actor = currentActor.require();
        LegalDocumentRequests.View current = find(id);
        writable(current.caseId());
        if (!Set.of(APPROVED, SENT).contains(current.status())) {
            throw new BusinessRuleException(TRANSITION, "only an approved or sent document is archived");
        }
        move(id, body.version(), current.status(), ARCHIVED, "archived_at = now()", actor, note(body.note()));
        cases.recordEvent(current.caseId(), "LEGAL_DOCUMENT_ARCHIVED", "Memo or letter \"%s\" archived"
                .formatted(clip(current.title())));
        return find(id);
    }

    // ---- helpers -----------------------------------------------------------------------------------------------

    /** Moves the document between statuses, with the optimistic lock, the event and the audit entry. */
    private void move(UUID id, Long version, String from, String to, String extraSet, ActorRef actor, String note) {
        int moved = jdbc.sql("update legal_document set status = :to, " + extraSet + ", version = version + 1,"
                + " updated_at = now(), updated_by = :actor where id = :id and version = :version and status = :from")
                .param("to", to).param("actor", actor.id()).param("note", note).param("id", id)
                .param("version", version == null ? -1 : version).param("from", from).update();
        if (moved == 0) {
            throw new ConflictException("memo or letter " + id + " was changed by someone else");
        }
        event(id, from, to, note, actor);
        audit.recordWithChange("LEGAL_DOCUMENT_" + to, actor.id(), actor.username(), "LEGAL_DOCUMENT", id.toString(), note);
    }

    private void event(UUID id, String from, String to, String note, ActorRef actor) {
        jdbc.sql("""
                insert into legal_document_event (legal_document_id, from_status, to_status, note, created_by)
                values (:id, :from, :to, :note, :actor)""").param("id", id).param("from", from).param("to", to)
                .param("note", note).param("actor", actor.id()).update();
    }

    /** A timeline entry by a user who may see the case but not change it uses the port made for that. */
    private void caseEvent(boolean withoutChangeRight, UUID caseId, String type, String summary) {
        if (withoutChangeRight) {
            cases.recordTaskEvent(caseId, type, summary);
        } else {
            cases.recordEvent(caseId, type, summary);
        }
    }

    private LegalDocumentRequests.View find(UUID id) {
        LegalDocumentRequests.View found = jdbc.sql(SELECT + " where l.id = :id").param("id", id)
                .query(LegalDocumentService::view).optional().orElseThrow(() -> new NotFoundException("memo or letter"));
        // The existence of a document of a case the user may not see is not disclosed.
        if (!access.canView(found.caseId())) {
            throw new NotFoundException("memo or letter");
        }
        return found;
    }

    private CaseSummary seen(UUID caseId) {
        access.requireView(caseId);
        CaseSummary legalCase = cases.find(caseId).orElseThrow(() -> new NotFoundException("case"));
        if ("ARCHIVED".equals(legalCase.status())) {
            throw new BusinessRuleException(CASE_ARCHIVED, "an archived case is read-only");
        }
        return legalCase;
    }

    private CaseSummary writable(UUID caseId) {
        CaseSummary legalCase = cases.find(caseId).orElseThrow(() -> new NotFoundException("case"));
        access.requireChange(caseId);
        if ("ARCHIVED".equals(legalCase.status())) {
            throw new BusinessRuleException(CASE_ARCHIVED, "an archived case is read-only");
        }
        return legalCase;
    }

    private static void requireDraft(LegalDocumentRequests.View current) {
        if (!DRAFT.equals(current.status())) {
            throw new BusinessRuleException(NOT_DRAFT, "only a draft can be changed; return it to draft first");
        }
    }

    private static void require(LegalDocumentRequests.View current, String status) {
        if (!status.equals(current.status())) {
            throw new BusinessRuleException(TRANSITION, "the document is " + current.status() + ", not " + status);
        }
    }

    /** Checks the references of a body: category in the managed list, member exists, file in this case and not withdrawn. */
    private void check(UUID caseId, LegalDocumentRequests.Body body, LegalDocumentRequests.View current) {
        required(body.title(), "title", 300);
        optional(body.issuer(), "issuer", 300);
        optional(body.addressee(), "addressee", 300);
        optional(body.description(), "description", 2000);
        if (body.categoryId() != null && !jdbc.sql("""
                select exists (select 1 from lookup_value where id = :id and list_code = 'DOCUMENT_CATEGORY'
                               and (active or id = :kept))""").param("id", body.categoryId())
                .param("kept", current == null || current.categoryId() == null ? new UUID(0, 0) : current.categoryId())
                .query(Boolean.class).single()) {
            throw new FieldErrorException("categoryId", "Exists");
        }
        if (body.responsibleMemberId() != null && !jdbc.sql("select exists (select 1 from app_user where id = :id)")
                .param("id", body.responsibleMemberId()).query(Boolean.class).single()) {
            throw new FieldErrorException("responsibleMemberId", "Exists");
        }
        if (body.documentId() != null && !jdbc.sql("""
                select exists (select 1 from case_document where id = :id and case_id = :c and withdrawn_at is null)""")
                .param("id", body.documentId()).param("c", caseId).query(Boolean.class).single()) {
            throw new FieldErrorException("documentId", "NotInCase");
        }
    }

    private static Map<String, Object> values(LegalDocumentRequests.Body body) {
        Map<String, Object> values = new HashMap<>();
        values.put("category", body.categoryId());
        values.put("title", body.title() == null ? null : body.title().strip());
        values.put("date", body.documentDate());
        values.put("issuer", blankToNull(body.issuer()));
        values.put("addressee", blankToNull(body.addressee()));
        values.put("member", body.responsibleMemberId());
        values.put("description", blankToNull(body.description()));
        values.put("document", body.documentId());
        return values;
    }

    private static String required(String value, String field, int max) {
        if (value == null || value.isBlank()) {
            throw new FieldErrorException(field, "NotBlank");
        }
        if (value.strip().length() > max) {
            throw new FieldErrorException(field, "Size");
        }
        return value.strip();
    }

    private static String optional(String value, String field, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.strip().length() > max) {
            throw new FieldErrorException(field, "Size");
        }
        return value.strip();
    }

    private static String note(String value) {
        return optional(value, "note", 500);
    }

    private static String requiredNote(String value) {
        return required(value, "note", 500);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String clip(String text) {
        String flat = text == null ? "" : text.strip().replaceAll("\\s+", " ");
        return flat.length() <= 120 ? flat : flat.substring(0, 119) + "…";
    }

    private static LegalDocumentRequests.View view(ResultSet rs, int n) throws SQLException {
        return new LegalDocumentRequests.View(rs.getObject("id", UUID.class), rs.getObject("case_id", UUID.class),
                rs.getString("reference_no"), rs.getString("case_title"), rs.getObject("category_id", UUID.class),
                rs.getString("label_ar"), rs.getString("label_en"), rs.getString("title"),
                rs.getObject("document_date", LocalDate.class), rs.getString("issuer"), rs.getString("addressee"),
                rs.getObject("responsible_member_id", UUID.class), rs.getString("responsible_name"),
                rs.getString("description"), rs.getObject("document_id", UUID.class), rs.getString("file_name"),
                (Integer) rs.getObject("current_version_no"), rs.getString("status"),
                rs.getObject("submitted_by", UUID.class), instant(rs, "submitted_at"), rs.getObject("approved_by", UUID.class),
                rs.getString("approved_name"), instant(rs, "approved_at"), rs.getString("approval_note"),
                rs.getBoolean("sent_recorded"), rs.getObject("sent_on", LocalDate.class), rs.getString("sent_method"),
                rs.getString("sent_reference"), rs.getObject("sent_recorded_by", UUID.class), instant(rs, "sent_recorded_at"),
                rs.getBoolean("sent_via_integration"), instant(rs, "archived_at"), rs.getLong("version"),
                instant(rs, "created_at"), rs.getObject("created_by", UUID.class), instant(rs, "updated_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        java.sql.Timestamp stamp = rs.getTimestamp(column);
        return stamp == null ? null : stamp.toInstant();
    }
}
