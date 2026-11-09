package com.lawdesk.documents;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.api.BusinessRuleException;
import com.lawdesk.api.FieldErrorException;
import com.lawdesk.api.NotFoundException;
import com.lawdesk.audit.AuditLog;
import com.lawdesk.cases.CaseAccessPolicy;
import com.lawdesk.cases.CaseLookup;
import com.lawdesk.security.ActorRef;
import com.lawdesk.security.CurrentActor;

/**
 * Links a document to the claim, defence, hearing, judgment or task it concerns (requirement 13, last item). The linked
 * record must belong to the document's case (also enforced by the database); reading links needs only the right to see the
 * case, writing them the right to change it.
 */
@Service
class DocumentLinkService {

    static final List<String> TYPES = List.of("CLAIM", "DEFENSE", "HEARING", "JUDGMENT", "TASK");

    record Link(UUID id, UUID documentId, String documentTitle, String targetType, UUID targetId, String targetLabel,
            String note, Instant createdAt, UUID createdBy) {
    }

    private static final String SELECT = """
            select l.id, l.document_id, d.title as document_title, l.note, l.created_at, l.created_by,
                   case when l.claim_id is not null then 'CLAIM' when l.defense_id is not null then 'DEFENSE'
                        when l.hearing_id is not null then 'HEARING' when l.judgment_id is not null then 'JUDGMENT'
                        else 'TASK' end as target_type,
                   coalesce(l.claim_id, l.defense_id, l.hearing_id, l.judgment_id, l.task_id) as target_id,
                   coalesce('#' || cl.seq || ' ' || left(cl.text, 80), df.title, 'hearing ' || h.hearing_date,
                            'judgment ' || j.judgment_date, t.title) as target_label
            from document_link l
            join case_document d on d.id = l.document_id
            left join claim cl on cl.id = l.claim_id
            left join defense df on df.id = l.defense_id
            left join hearing h on h.id = l.hearing_id
            left join judgment j on j.id = l.judgment_id
            left join task t on t.id = l.task_id""";

    private final JdbcClient jdbc;
    private final DocumentService documents;
    private final CaseAccessPolicy access;
    private final CaseLookup cases;
    private final CurrentActor currentActor;
    private final AuditLog audit;

    DocumentLinkService(JdbcClient jdbc, DocumentService documents, CaseAccessPolicy access, CaseLookup cases,
            CurrentActor currentActor, AuditLog audit) {
        this.jdbc = jdbc;
        this.documents = documents;
        this.access = access;
        this.cases = cases;
        this.currentActor = currentActor;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    List<Link> forDocument(UUID documentId) {
        documents.get(documentId);
        return jdbc.sql(SELECT + " where l.document_id = :id order by l.created_at, l.id").param("id", documentId)
                .query(DocumentLinkService::link).list();
    }

    @Transactional(readOnly = true)
    List<Link> forCase(UUID caseId) {
        access.requireView(caseId);
        return jdbc.sql(SELECT + " where d.case_id = :id and d.withdrawn_at is null order by l.created_at, l.id")
                .param("id", caseId).query(DocumentLinkService::link).list();
    }

    @Transactional
    Link add(UUID documentId, String type, UUID targetId, String note) {
        ActorRef actor = currentActor.require();
        String kind = type == null ? "" : type.toUpperCase(Locale.ROOT);
        if (!TYPES.contains(kind)) {
            throw new FieldErrorException("targetType", "Unknown");
        }
        if (targetId == null) {
            throw new FieldErrorException("targetId", "NotNull");
        }
        String text = note == null || note.isBlank() ? null : note.strip();
        if (text != null && text.length() > 500) {
            throw new FieldErrorException("note", "Size");
        }
        CaseDocument document = documents.get(documentId);
        documents.requireChangeable(document);
        UUID targetCase = caseOfTarget(kind, targetId);
        if (!document.caseId().equals(targetCase)) {
            throw new BusinessRuleException(Rules.LINK_NOT_IN_CASE, "the linked record is not in the document's case");
        }
        String column = switch (kind) {
            case "CLAIM" -> "claim_id";
            case "DEFENSE" -> "defense_id";
            case "HEARING" -> "hearing_id";
            case "JUDGMENT" -> "judgment_id";
            default -> "task_id";
        };
        UUID id;
        try {
            id = jdbc.sql("insert into document_link (document_id, " + column + ", note, created_by) values (:d, :t, :n, :a)"
                    + " returning id").param("d", documentId).param("t", targetId).param("n", text).param("a", actor.id())
                    .query(UUID.class).single();
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new com.lawdesk.api.ConflictException("the document is linked to this record already");
        }
        cases.recordEvent(document.caseId(), "DOCUMENT_LINKED", "Document \"%s\" linked to a %s"
                .formatted(document.title(), kind.toLowerCase(Locale.ROOT)));
        audit.recordWithChange("DOCUMENT_LINK", actor.id(), actor.username(), "DOCUMENT", documentId.toString(), kind);
        return jdbc.sql(SELECT + " where l.id = :id").param("id", id).query(DocumentLinkService::link).single();
    }

    @Transactional
    void remove(UUID linkId) {
        ActorRef actor = currentActor.require();
        UUID documentId = jdbc.sql("select document_id from document_link where id = :id").param("id", linkId)
                .query(UUID.class).optional().orElseThrow(() -> new NotFoundException("link"));
        CaseDocument document = documents.get(documentId);
        documents.requireChangeable(document);
        jdbc.sql("delete from document_link where id = :id").param("id", linkId).update();
        cases.recordEvent(document.caseId(), "DOCUMENT_UNLINKED", "A link of document \"%s\" was removed".formatted(document.title()));
        audit.recordWithChange("DOCUMENT_UNLINK", actor.id(), actor.username(), "DOCUMENT", documentId.toString(), null);
    }

    private UUID caseOfTarget(String kind, UUID id) {
        String sql = switch (kind) {
            case "CLAIM" -> "select case_id from claim where id = :id";
            case "DEFENSE" -> "select case_id from defense where id = :id";
            case "HEARING" -> "select case_id from hearing where id = :id";
            case "JUDGMENT" -> "select p.case_id from judgment j join proceeding p on p.id = j.proceeding_id where j.id = :id";
            default -> "select case_id from task where id = :id";
        };
        return jdbc.sql(sql).param("id", id).query(UUID.class).optional().orElseThrow(() -> new NotFoundException("target"));
    }

    private static Link link(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        return new Link(rs.getObject("id", UUID.class), rs.getObject("document_id", UUID.class),
                rs.getString("document_title"), rs.getString("target_type"), rs.getObject("target_id", UUID.class),
                rs.getString("target_label"), rs.getString("note"), rs.getTimestamp("created_at").toInstant(),
                rs.getObject("created_by", UUID.class));
    }
}
