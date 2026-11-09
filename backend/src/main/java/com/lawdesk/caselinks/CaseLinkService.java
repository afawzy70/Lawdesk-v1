package com.lawdesk.caselinks;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.api.BusinessRuleException;
import com.lawdesk.api.ConflictException;
import com.lawdesk.api.FieldErrorException;
import com.lawdesk.api.NotFoundException;
import com.lawdesk.cases.CaseAccessPolicy;
import com.lawdesk.cases.CaseLookup;
import com.lawdesk.cases.CaseSummary;
import com.lawdesk.documents.DocumentArchive;
import com.lawdesk.security.ActorRef;
import com.lawdesk.security.CurrentActor;

/**
 * Linked cases (LD-053, requirements section 8). One row links two cases and shows in both files; ending or closing one case
 * never touches the other. A link shows its other case only to users who may see that case (D3): the rest get a count.
 */
@Service
class CaseLinkService {

    static final String CASE_LINK_SAME_CASE = "CASE_LINK_SAME_CASE";
    static final String LINK_DOCUMENT_NOT_IN_CASE = "LINK_DOCUMENT_NOT_IN_CASE";
    static final String CASE_ARCHIVED = "CASE_ARCHIVED";

    private final JdbcClient jdbc;
    private final CaseLookup cases;
    private final CaseAccessPolicy access;
    private final CurrentActor currentActor;
    private final DocumentArchive documents;

    CaseLinkService(JdbcClient jdbc, CaseLookup cases, CaseAccessPolicy access, CurrentActor currentActor,
            DocumentArchive documents) {
        this.jdbc = jdbc;
        this.cases = cases;
        this.access = access;
        this.currentActor = currentActor;
        this.documents = documents;
    }

    private record Row(UUID id, UUID caseId, UUID otherCaseId, String kind, String reason, String requestedBy,
            LocalDate requestedOn, boolean courtDecision, LocalDate decisionDate, String operative, UUID documentId,
            String status, long version, java.time.Instant createdAt) {
    }

    private static final String SELECT = """
            select id, case_id, other_case_id, kind, reason, requested_by, requested_on, court_decision, decision_date,
                   operative, decision_document_id, status, version, created_at from case_link""";

    @Transactional(readOnly = true)
    CaseLinkRequests.Links forCase(UUID caseId) {
        access.requireView(caseId);
        List<Row> rows = jdbc.sql(SELECT + " where case_id = :id or other_case_id = :id order by created_at, id")
                .param("id", caseId).query((rs, n) -> row(rs)).list();
        List<CaseLinkRequests.Link> items = new ArrayList<>();
        int hidden = 0;
        for (Row row : rows) {
            boolean outgoing = row.caseId().equals(caseId);
            UUID otherId = outgoing ? row.otherCaseId() : row.caseId();
            Optional<CaseSummary> other = cases.find(otherId);
            if (other.isEmpty()) {
                hidden++;
                continue;
            }
            items.add(link(row, caseId, outgoing, other.get()));
        }
        return new CaseLinkRequests.Links(items, hidden);
    }

    @Transactional
    CaseLinkRequests.Link create(UUID caseId, CaseLinkRequests.Create request) {
        ActorRef actor = currentActor.require();
        CaseSummary source = cases.find(caseId).orElseThrow(() -> new NotFoundException("case"));
        access.requireChange(caseId);
        if ("ARCHIVED".equals(source.status())) {
            throw new BusinessRuleException(CASE_ARCHIVED, "an archived case is read-only");
        }
        if (caseId.equals(request.otherCaseId())) {
            throw new BusinessRuleException(CASE_LINK_SAME_CASE, "a case cannot be linked to itself");
        }
        // A case the user may not see is reported missing, so a link cannot be used to probe for it.
        CaseSummary other = cases.find(request.otherCaseId()).orElseThrow(() -> new NotFoundException("case"));
        boolean decided = Boolean.TRUE.equals(request.courtDecision());
        requireDecisionFields(decided, request.decisionDate(), request.operative(), request.decisionDocumentId());
        requireDocument(request.decisionDocumentId(), caseId, request.otherCaseId());
        CaseLinkRequests.Status status = request.status() != null ? request.status()
                : request.kind() == CaseLinkRequests.Kind.JOINDER_REQUEST ? CaseLinkRequests.Status.REQUESTED
                        : CaseLinkRequests.Status.ACTIVE;
        UUID id;
        try {
            id = jdbc.sql("""
                            insert into case_link (case_id, other_case_id, kind, reason, requested_by, requested_on,
                                court_decision, decision_date, operative, decision_document_id, status, created_by, updated_by)
                            values (:caseId, :other, :kind, :reason, :requestedBy, :requestedOn, :decided, :decisionDate,
                                :operative, :document, :status, :actor, :actor)
                            returning id""")
                    .param("caseId", caseId).param("other", request.otherCaseId()).param("kind", request.kind().name())
                    .param("reason", request.reason()).param("requestedBy", request.requestedBy())
                    .param("requestedOn", request.requestedOn()).param("decided", decided)
                    .param("decisionDate", request.decisionDate()).param("operative", request.operative())
                    .param("document", request.decisionDocumentId()).param("status", status.name())
                    .param("actor", actor.id()).query(UUID.class).single();
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("these cases are already linked in this way");
        }
        cases.recordEvent(caseId, "CASE_LINK_CREATED", "Linked to case %s (%s)".formatted(other.referenceNo(),
                request.kind().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ')));
        return link(find(id).orElseThrow(), caseId, true, other);
    }

    @Transactional
    CaseLinkRequests.Link update(UUID id, CaseLinkRequests.Update request) {
        ActorRef actor = currentActor.require();
        Row row = find(id).orElseThrow(() -> new NotFoundException("case link"));
        boolean seesSource = access.canView(row.caseId());
        boolean seesOther = access.canView(row.otherCaseId());
        if (!seesSource && !seesOther) {
            throw new NotFoundException("case link");
        }
        // Either case may be the one the user works on; changing one of the two is enough to manage the link.
        UUID editing = null;
        for (UUID candidate : new UUID[] {row.caseId(), row.otherCaseId()}) {
            if (editing == null && access.canView(candidate) && access.mayChange(candidate)) {
                editing = candidate;
            }
        }
        if (editing == null) {
            throw new AccessDeniedException("not a member of either case");
        }
        if (row.version() != request.version()) {
            throw new ConflictException("the link was changed by someone else");
        }
        boolean decided = Boolean.TRUE.equals(request.courtDecision());
        requireDecisionFields(decided, request.decisionDate(), request.operative(), request.decisionDocumentId());
        requireDocument(request.decisionDocumentId(), row.caseId(), row.otherCaseId());
        int updated = jdbc.sql("""
                        update case_link set reason = :reason, requested_by = :requestedBy, requested_on = :requestedOn,
                            status = :status, court_decision = :decided, decision_date = :decisionDate,
                            operative = :operative, decision_document_id = :document, version = version + 1,
                            updated_at = now(), updated_by = :actor
                        where id = :id and version = :version""")
                .param("id", id).param("version", request.version()).param("reason", request.reason())
                .param("requestedBy", request.requestedBy()).param("requestedOn", request.requestedOn())
                .param("status", request.status().name()).param("decided", decided)
                .param("decisionDate", request.decisionDate()).param("operative", request.operative())
                .param("document", request.decisionDocumentId()).param("actor", actor.id()).update();
        if (updated != 1) {
            throw new ConflictException("the link was changed by someone else");
        }
        cases.recordEvent(editing, "CASE_LINK_UPDATED", "Case link updated: " + request.status().name());
        Row changed = find(id).orElseThrow();
        boolean outgoing = changed.caseId().equals(editing);
        UUID otherId = outgoing ? changed.otherCaseId() : changed.caseId();
        CaseSummary other = cases.find(otherId).orElseThrow(() -> new NotFoundException("case"));
        return link(changed, editing, outgoing, other);
    }

    private Optional<Row> find(UUID id) {
        return jdbc.sql(SELECT + " where id = :id").param("id", id).query((rs, n) -> row(rs)).optional();
    }

    private static void requireDecisionFields(boolean decided, LocalDate date, String operative, UUID document) {
        if (!decided && (date != null || operative != null || document != null)) {
            throw new FieldErrorException("courtDecision", "AssertTrue");
        }
    }

    private void requireDocument(UUID documentId, UUID caseId, UUID otherCaseId) {
        if (documentId != null && !documents.belongsToCase(documentId, caseId)
                && !documents.belongsToCase(documentId, otherCaseId)) {
            throw new BusinessRuleException(LINK_DOCUMENT_NOT_IN_CASE, "the decision copy is not in either case file");
        }
    }

    private static CaseLinkRequests.Link link(Row row, UUID viewedFrom, boolean outgoing, CaseSummary other) {
        return new CaseLinkRequests.Link(row.id(), viewedFrom, outgoing ? "OUTGOING" : "INCOMING", other.id(),
                other.referenceNo(), other.title(), other.status(), row.kind(), row.reason(), row.requestedBy(),
                row.requestedOn(), row.courtDecision(), row.decisionDate(), row.operative(), row.documentId(),
                row.status(), row.version(), row.createdAt());
    }

    private static Row row(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Row(rs.getObject("id", UUID.class), rs.getObject("case_id", UUID.class),
                rs.getObject("other_case_id", UUID.class), rs.getString("kind"), rs.getString("reason"),
                rs.getString("requested_by"), rs.getObject("requested_on", LocalDate.class),
                rs.getBoolean("court_decision"), rs.getObject("decision_date", LocalDate.class),
                rs.getString("operative"), rs.getObject("decision_document_id", UUID.class), rs.getString("status"),
                rs.getLong("version"), rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }
}
