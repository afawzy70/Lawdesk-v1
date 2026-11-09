package com.lawdesk.pleadings;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.api.ConflictException;
import com.lawdesk.api.NotFoundException;
import com.lawdesk.security.ActorRef;

/**
 * The claims of a case (requirements section 5; scenarios S1, S2). A claim is never deleted; each edit appends a full
 * snapshot to {@code claim_revision}, so the previous text, the date and the user are always there.
 */
@Service
class ClaimService {

    private static final String SELECT = """
            select c.id, c.case_id, c.seq, c.text, c.kind, c.status, c.submitted_by_party_id, p.name as party_name,
                   c.submitted_on, c.stage_id, c.source_document_id, d.title as document_title, c.notes, c.version,
                   c.created_at, c.updated_at,
                   (select max(r.rev) from claim_revision r where r.claim_id = c.id) as revision
            from claim c left join party p on p.id = c.submitted_by_party_id
                 left join case_document d on d.id = c.source_document_id""";

    private final JdbcClient jdbc;
    private final CaseRows rows;

    ClaimService(JdbcClient jdbc, CaseRows rows) {
        this.jdbc = jdbc;
        this.rows = rows;
    }

    @Transactional(readOnly = true)
    List<ClaimRequests.View> list(UUID caseId) {
        rows.view(caseId);
        return jdbc.sql(SELECT + " where c.case_id = :id order by c.seq").param("id", caseId)
                .query(ClaimService::view).list();
    }

    @Transactional(readOnly = true)
    ClaimRequests.View get(UUID id) {
        rows.view(rows.caseOf("claim", id, "claim"));
        return find(id);
    }

    @Transactional(readOnly = true)
    List<ClaimRequests.Revision> revisions(UUID id) {
        rows.view(rows.caseOf("claim", id, "claim"));
        return jdbc.sql("""
                select rev, text, kind, status, submitted_by_party_id, submitted_on, stage_id, source_document_id, notes,
                       reason, changed_by, changed_at
                from claim_revision where claim_id = :id order by rev desc""").param("id", id)
                .query((rs, n) -> new ClaimRequests.Revision(rs.getInt("rev"), rs.getString("text"),
                        ClaimKind.valueOf(rs.getString("kind")), ClaimStatus.valueOf(rs.getString("status")),
                        rs.getObject("submitted_by_party_id", UUID.class), rs.getObject("submitted_on", LocalDate.class),
                        rs.getObject("stage_id", UUID.class), rs.getObject("source_document_id", UUID.class),
                        rs.getString("notes"), rs.getString("reason"), rs.getObject("changed_by", UUID.class),
                        rs.getTimestamp("changed_at").toInstant()))
                .list();
    }

    @Transactional
    ClaimRequests.View create(UUID caseId, ClaimRequests.Create request) {
        ActorRef actor = rows.change(caseId);
        check(caseId, request.stageId(), request.sourceDocumentId(), request.submittedByPartyId());
        int seq = rows.nextSeq("claim", caseId);
        UUID id = jdbc.sql("""
                insert into claim (case_id, seq, text, kind, submitted_by_party_id, submitted_on, stage_id,
                                   source_document_id, notes, created_by, updated_by)
                values (:case, :seq, :text, :kind, :party, :on, :stage, :document, :notes, :actor, :actor)
                returning id""").param("case", caseId).param("seq", seq).param("text", request.text())
                .param("kind", request.kind().name()).param("party", request.submittedByPartyId())
                .param("on", request.submittedOn()).param("stage", request.stageId())
                .param("document", request.sourceDocumentId()).param("notes", request.notes())
                .param("actor", actor.id()).query(UUID.class).single();
        snapshot(id, 1, null, actor);
        rows.event(caseId, "CLAIM_ADDED", "Claim " + seq + " added: " + CaseRows.clip(request.text(), 120));
        rows.audit("CLAIM_CREATE", actor, caseId, "claim=" + seq);
        return find(id);
    }

    @Transactional
    ClaimRequests.View update(UUID id, ClaimRequests.Update request) {
        UUID caseId = rows.caseOf("claim", id, "claim");
        ActorRef actor = rows.change(caseId);
        ClaimRequests.View current = find(id);
        if (current.version() != request.version()) {
            throw new ConflictException("claim " + id + " has changed since version " + request.version());
        }
        check(caseId, request.stageId(), request.sourceDocumentId(), request.submittedByPartyId());
        boolean textChanged = !current.text().equals(request.text()) || current.kind() != request.kind();
        // Changing the wording of a standing claim makes it an amended one unless the user states another status.
        ClaimStatus status = textChanged && current.status() == ClaimStatus.STANDING
                && request.status() == ClaimStatus.STANDING ? ClaimStatus.AMENDED : request.status();
        boolean changed = textChanged || status != current.status()
                || !Objects.equals(current.submittedByPartyId(), request.submittedByPartyId())
                || !Objects.equals(current.submittedOn(), request.submittedOn())
                || !Objects.equals(current.stageId(), request.stageId())
                || !Objects.equals(current.sourceDocumentId(), request.sourceDocumentId())
                || !Objects.equals(current.notes(), request.notes());
        if (!changed) {
            return current;
        }
        int updated = jdbc.sql("""
                update claim set text = :text, kind = :kind, status = :status, submitted_by_party_id = :party,
                       submitted_on = :on, stage_id = :stage, source_document_id = :document, notes = :notes,
                       version = version + 1, updated_at = now(), updated_by = :actor
                where id = :id and version = :version""").param("text", request.text())
                .param("kind", request.kind().name()).param("status", status.name())
                .param("party", request.submittedByPartyId()).param("on", request.submittedOn())
                .param("stage", request.stageId()).param("document", request.sourceDocumentId())
                .param("notes", request.notes()).param("actor", actor.id()).param("id", id)
                .param("version", request.version()).update();
        if (updated == 0) {
            throw new ConflictException("claim " + id + " was changed by someone else");
        }
        snapshot(id, current.revision() + 1, request.reason(), actor);
        rows.event(caseId, "CLAIM_AMENDED", "Claim " + current.seq() + " amended (revision " + (current.revision() + 1)
                + (status != current.status() ? ", status " + status : "") + ")");
        rows.audit("CLAIM_AMEND", actor, caseId, "claim=" + current.seq() + " rev=" + (current.revision() + 1));
        return find(id);
    }

    private void check(UUID caseId, UUID stageId, UUID documentId, UUID partyId) {
        rows.sameCase("proceeding", stageId, caseId, "stageId");
        rows.sameCase("case_document", documentId, caseId, "sourceDocumentId");
        rows.partyOfCase(partyId, caseId, "submittedByPartyId");
    }

    /** Appends the full state of the claim as it now stands. */
    private void snapshot(UUID id, int rev, String reason, ActorRef actor) {
        jdbc.sql("""
                insert into claim_revision (claim_id, rev, text, kind, status, submitted_by_party_id, submitted_on,
                                            stage_id, source_document_id, notes, reason, changed_by)
                select id, :rev, text, kind, status, submitted_by_party_id, submitted_on, stage_id, source_document_id,
                       notes, :reason, :actor from claim where id = :id""").param("rev", rev).param("reason", reason)
                .param("actor", actor.id()).param("id", id).update();
    }

    private ClaimRequests.View find(UUID id) {
        return jdbc.sql(SELECT + " where c.id = :id").param("id", id).query(ClaimService::view).optional()
                .orElseThrow(() -> new NotFoundException("claim"));
    }

    private static ClaimRequests.View view(ResultSet rs, int n) throws SQLException {
        return new ClaimRequests.View(rs.getObject("id", UUID.class), rs.getObject("case_id", UUID.class),
                rs.getInt("seq"), rs.getString("text"), ClaimKind.valueOf(rs.getString("kind")),
                ClaimStatus.valueOf(rs.getString("status")), rs.getObject("submitted_by_party_id", UUID.class),
                rs.getString("party_name"), rs.getObject("submitted_on", LocalDate.class),
                rs.getObject("stage_id", UUID.class), rs.getObject("source_document_id", UUID.class),
                rs.getString("document_title"), rs.getString("notes"), rs.getInt("revision"), rs.getLong("version"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }
}
