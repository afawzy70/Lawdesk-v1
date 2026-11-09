package com.lawdesk.library;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.api.ConflictException;
import com.lawdesk.api.FieldErrorException;
import com.lawdesk.api.NotFoundException;
import com.lawdesk.audit.AuditLog;
import com.lawdesk.library.LibraryViews.Principle;
import com.lawdesk.security.ActorRef;
import com.lawdesk.security.CurrentActor;

/**
 * Principles extracted from a judgment (requirements section 16.2). A principle is a member's summary kept apart from the
 * judgment's own text and never a replacement for it; changing its text takes it back to DRAFT because the review was of
 * the earlier wording.
 */
@Service
class LibraryPrincipleService {

    private static final String SELECT = "select p.id, p.judgment_id, p.principle_text, p.legal_topic, p.legal_basis,"
            + " p.facts_applied, p.scope_note, p.member_notes, p.review_status, p.reviewed_by, p.reviewed_at, p.version,"
            + " p.created_at, p.created_by, p.updated_at, j.court_name, j.case_number, j.case_year, j.judgment_date"
            + " from library_principle p join library_judgment j on j.id = p.judgment_id";

    private final JdbcClient jdbc;
    private final CurrentActor currentActor;
    private final AuditLog audit;

    LibraryPrincipleService(JdbcClient jdbc, CurrentActor currentActor, AuditLog audit) {
        this.jdbc = jdbc;
        this.currentActor = currentActor;
        this.audit = audit;
    }

    private static Principle row(ResultSet rs, int n) throws SQLException {
        return new Principle(rs.getObject("id", UUID.class), rs.getObject("judgment_id", UUID.class),
                rs.getString("principle_text"), rs.getString("legal_topic"), rs.getString("legal_basis"),
                rs.getString("facts_applied"), rs.getString("scope_note"), rs.getString("member_notes"),
                rs.getString("review_status"), rs.getObject("reviewed_by", UUID.class),
                rs.getTimestamp("reviewed_at") == null ? null : rs.getTimestamp("reviewed_at").toInstant(),
                rs.getLong("version"), rs.getTimestamp("created_at").toInstant(), rs.getObject("created_by", UUID.class),
                rs.getTimestamp("updated_at").toInstant(), rs.getString("court_name"), rs.getString("case_number"),
                rs.getObject("case_year") == null ? null : rs.getInt("case_year"),
                rs.getObject("judgment_date", LocalDate.class));
    }

    @Transactional(readOnly = true)
    List<Principle> forJudgment(UUID judgmentId) {
        if (!jdbc.sql("select exists (select 1 from library_judgment where id = :id)").param("id", judgmentId)
                .query(Boolean.class).single()) {
            throw new NotFoundException("judgment");
        }
        return of(judgmentId);
    }

    List<Principle> of(UUID judgmentId) {
        return jdbc.sql(SELECT + " where p.judgment_id = :id order by p.created_at, p.id").param("id", judgmentId)
                .query(LibraryPrincipleService::row).list();
    }

    private Principle find(UUID id) {
        return jdbc.sql(SELECT + " where p.id = :id").param("id", id).query(LibraryPrincipleService::row).optional()
                .orElseThrow(() -> new NotFoundException("principle"));
    }

    @Transactional
    Principle create(UUID judgmentId, LibraryRequests.PrincipleWrite request) {
        ActorRef actor = currentActor.require();
        if (!jdbc.sql("select exists (select 1 from library_judgment where id = :id for share)").param("id", judgmentId)
                .query(Boolean.class).single()) {
            throw new NotFoundException("judgment");
        }
        UUID id = jdbc.sql("insert into library_principle (judgment_id, principle_text, legal_topic, legal_basis,"
                        + " facts_applied, scope_note, member_notes, created_by, updated_by) values (:judgment, :text,"
                        + " :topic, :basis, :facts, :scope, :notes, :actor, :actor) returning id")
                .param("judgment", judgmentId).param("text", request.principleText().strip())
                .param("topic", LibraryText.trimToNull(request.legalTopic()))
                .param("basis", LibraryText.trimToNull(request.legalBasis()))
                .param("facts", LibraryText.trimToNull(request.factsApplied()))
                .param("scope", LibraryText.trimToNull(request.scopeNote()))
                .param("notes", LibraryText.trimToNull(request.memberNotes())).param("actor", actor.id())
                .query(UUID.class).single();
        audit.recordWithChange("LIBRARY_PRINCIPLE_CREATE", actor.id(), actor.username(), "LIBRARY_PRINCIPLE",
                id.toString(), judgmentId.toString());
        return find(id);
    }

    @Transactional
    Principle update(UUID id, LibraryRequests.PrincipleWrite request) {
        ActorRef actor = currentActor.require();
        if (request.version() == null) {
            throw new FieldErrorException("version", "NotNull");
        }
        checkVersion(id, request.version());
        jdbc.sql("update library_principle set principle_text = :text, legal_topic = :topic, legal_basis = :basis,"
                        + " facts_applied = :facts, scope_note = :scope, member_notes = :notes, review_status = 'DRAFT',"
                        + " reviewed_by = null, reviewed_at = null, version = version + 1, updated_at = now(),"
                        + " updated_by = :actor where id = :id")
                .param("id", id).param("text", request.principleText().strip())
                .param("topic", LibraryText.trimToNull(request.legalTopic()))
                .param("basis", LibraryText.trimToNull(request.legalBasis()))
                .param("facts", LibraryText.trimToNull(request.factsApplied()))
                .param("scope", LibraryText.trimToNull(request.scopeNote()))
                .param("notes", LibraryText.trimToNull(request.memberNotes())).param("actor", actor.id()).update();
        audit.recordWithChange("LIBRARY_PRINCIPLE_UPDATE", actor.id(), actor.username(), "LIBRARY_PRINCIPLE",
                id.toString(), "edit");
        return find(id);
    }

    @Transactional
    Principle review(UUID id, long version) {
        ActorRef actor = currentActor.require();
        checkVersion(id, version);
        jdbc.sql("update library_principle set review_status = 'REVIEWED', reviewed_by = :actor, reviewed_at = now(),"
                        + " version = version + 1, updated_at = now(), updated_by = :actor where id = :id")
                .param("id", id).param("actor", actor.id()).update();
        audit.recordWithChange("LIBRARY_PRINCIPLE_REVIEW", actor.id(), actor.username(), "LIBRARY_PRINCIPLE",
                id.toString(), "reviewed");
        return find(id);
    }

    private void checkVersion(UUID id, long version) {
        Long current = jdbc.sql("select version from library_principle where id = :id for update").param("id", id)
                .query(Long.class).optional().orElseThrow(() -> new NotFoundException("principle"));
        if (current != version) {
            throw new ConflictException("principle " + id + " has changed since version " + version);
        }
    }
}
