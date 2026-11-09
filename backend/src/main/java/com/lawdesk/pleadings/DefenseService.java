package com.lawdesk.pleadings;

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
import com.lawdesk.security.ActorRef;
import com.lawdesk.security.CurrentActor;
import com.lawdesk.security.Permission;

/**
 * The defences of a case (requirements section 17; scenarios S3, S13). A defence is never deleted. Where it was raised
 * becomes its first assertion; its later fate in each stage is {@code defense_assertion}, and the court's reaction in
 * each judgment is recorded by {@link ResultService}. The library ({@code defense_template}) is wording only: nothing
 * is copied from it into a case by the system.
 */
@Service
class DefenseService {

    private static final String SELECT = """
            select d.id, d.case_id, d.seq, d.title, d.kind, d.argument, d.legal_basis, d.facts, d.legal_texts,
                   d.memo_document_id, m.title as memo_title, d.raised_on, d.raised_stage_id, d.prepared_by,
                   u.display_name as prepared_name, d.notes, d.template_id, d.version, d.created_at, d.updated_at,
                   (select count(*) from evidence_link l where l.defense_id = d.id) as evidence_count
            from defense d left join case_document m on m.id = d.memo_document_id
                 left join app_user u on u.id = d.prepared_by""";

    private final JdbcClient jdbc;
    private final CaseRows rows;
    private final ResultService results;
    private final CurrentActor currentActor;

    DefenseService(JdbcClient jdbc, CaseRows rows, ResultService results, CurrentActor currentActor) {
        this.jdbc = jdbc;
        this.rows = rows;
        this.results = results;
        this.currentActor = currentActor;
    }

    @Transactional(readOnly = true)
    List<DefenseRequests.View> list(UUID caseId) {
        rows.view(caseId);
        return jdbc.sql(SELECT + " where d.case_id = :id order by d.seq").param("id", caseId).query(DefenseService::view).list();
    }

    @Transactional(readOnly = true)
    DefenseRequests.Detail detail(UUID id) {
        UUID caseId = rows.caseOf("defense", id, "defense");
        rows.view(caseId);
        List<DefenseRequests.AssertionView> assertions = assertions(caseId).stream().filter(a -> a.defenseId().equals(id)).toList();
        List<ResultRequests.CaseDefensePosition> positions = results.defensePositionsOfCase(caseId).stream()
                .filter(p -> p.defenseId().equals(id)).toList();
        List<DefenseRequests.LinkedEvidence> evidence = jdbc.sql("""
                select e.id, e.name, e.role, e.importance, e.document_id, e.submitted_to_court
                from evidence_link l join evidence_item e on e.id = l.evidence_id where l.defense_id = :id
                order by e.created_at, e.id""").param("id", id)
                .query((rs, n) -> new DefenseRequests.LinkedEvidence(rs.getObject("id", UUID.class), rs.getString("name"),
                        EvidenceRole.valueOf(rs.getString("role")),
                        rs.getString("importance") == null ? null : EvidenceImportance.valueOf(rs.getString("importance")),
                        rs.getObject("document_id", UUID.class), rs.getBoolean("submitted_to_court")))
                .list();
        return new DefenseRequests.Detail(find(id), assertions, positions, evidence, precedents(id));
    }

    // ---- supporting precedents (section 17: "الأحكام والسوابق القضائية المؤيدة") ----------------------------------

    private static final String PRECEDENT_SELECT = """
            select dp.id, dp.defense_id, dp.library_judgment_id, j.court_name, j.case_number, j.case_year, j.judgment_date,
                   j.subject, j.finality, j.status, dp.principle_id, p.principle_text, dp.note, dp.created_at, dp.created_by
            from defense_precedent dp join library_judgment j on j.id = dp.library_judgment_id
                 left join library_principle p on p.id = dp.principle_id""";

    private List<DefenseRequests.PrecedentView> precedents(UUID defenseId) {
        return jdbc.sql(PRECEDENT_SELECT + " where dp.defense_id = :id and dp.removed_at is null order by dp.created_at, dp.id")
                .param("id", defenseId).query(DefenseService::precedent).list();
    }

    @Transactional(readOnly = true)
    List<DefenseRequests.PrecedentView> precedentsOf(UUID defenseId) {
        rows.view(rows.caseOf("defense", defenseId, "defense"));
        return precedents(defenseId);
    }

    /** Links a library judgment to the defence; the case must be changeable and the user must be able to read the library. */
    @Transactional
    DefenseRequests.PrecedentView linkPrecedent(UUID defenseId, DefenseRequests.PrecedentBody body) {
        UUID caseId = rows.caseOf("defense", defenseId, "defense");
        ActorRef actor = rows.change(caseId);
        if (!currentActor.has(Permission.LIBRARY_VIEW)) {
            throw new org.springframework.security.access.AccessDeniedException("the library is not open to this user");
        }
        String status = jdbc.sql("select status from library_judgment where id = :id").param("id", body.judgmentId())
                .query(String.class).optional().orElseThrow(() -> new FieldErrorException("judgmentId", "Exists"));
        if ("WITHDRAWN".equals(status)) {
            throw new FieldErrorException("judgmentId", "Withdrawn");
        }
        if (body.principleId() != null && !jdbc.sql(
                "select exists (select 1 from library_principle where id = :p and judgment_id = :j)")
                .param("p", body.principleId()).param("j", body.judgmentId()).query(Boolean.class).single()) {
            throw new FieldErrorException("principleId", "NotInJudgment");
        }
        UUID id;
        try {
            id = jdbc.sql("""
                    insert into defense_precedent (defense_id, library_judgment_id, principle_id, note, created_by)
                    values (:defense, :judgment, :principle, :note, :actor) returning id""").param("defense", defenseId)
                    .param("judgment", body.judgmentId()).param("principle", body.principleId())
                    .param("note", CaseRows.blankToNull(body.note())).param("actor", actor.id()).query(UUID.class).single();
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new ConflictException("this judgment already supports the defence");
        }
        DefenseRequests.View defense = find(defenseId);
        rows.event(caseId, "DEFENSE_PRECEDENT_LINKED", "A library judgment supports defence " + defense.seq());
        rows.audit("DEFENSE_PRECEDENT_LINK", actor, caseId, "defense=" + defense.seq() + " judgment=" + body.judgmentId());
        return jdbc.sql(PRECEDENT_SELECT + " where dp.id = :id").param("id", id).query(DefenseService::precedent).single();
    }

    @Transactional
    void unlinkPrecedent(UUID defenseId, UUID linkId) {
        UUID caseId = rows.caseOf("defense", defenseId, "defense");
        ActorRef actor = rows.change(caseId);
        if (jdbc.sql("""
                update defense_precedent set removed_at = now(), removed_by = :actor
                where id = :id and defense_id = :defense and removed_at is null""").param("actor", actor.id())
                .param("id", linkId).param("defense", defenseId).update() == 0) {
            throw new NotFoundException("precedent link");
        }
        DefenseRequests.View defense = find(defenseId);
        rows.event(caseId, "DEFENSE_PRECEDENT_UNLINKED", "A supporting judgment was removed from defence " + defense.seq());
        rows.audit("DEFENSE_PRECEDENT_UNLINK", actor, caseId, "defense=" + defense.seq());
    }

    private static DefenseRequests.PrecedentView precedent(ResultSet rs, int n) throws SQLException {
        return new DefenseRequests.PrecedentView(rs.getObject("id", UUID.class), rs.getObject("defense_id", UUID.class),
                rs.getObject("library_judgment_id", UUID.class), rs.getString("court_name"), rs.getString("case_number"),
                rs.getObject("case_year") == null ? null : rs.getInt("case_year"),
                rs.getObject("judgment_date", LocalDate.class), rs.getString("subject"), rs.getString("finality"),
                rs.getString("status"), rs.getObject("principle_id", UUID.class), rs.getString("principle_text"),
                rs.getString("note"), rs.getTimestamp("created_at").toInstant(), rs.getObject("created_by", UUID.class));
    }

    @Transactional
    DefenseRequests.View create(UUID caseId, DefenseRequests.Create request) {
        ActorRef actor = rows.change(caseId);
        check(caseId, request);
        int seq = rows.nextSeq("defense", caseId);
        UUID id = jdbc.sql("""
                insert into defense (case_id, seq, title, kind, argument, legal_basis, facts, legal_texts, memo_document_id,
                                     raised_on, raised_stage_id, prepared_by, notes, template_id, created_by, updated_by)
                values (:case, :seq, :title, :kind, :argument, :basis, :facts, :texts, :memo, :on, :stage, :prepared,
                        :notes, :template, :actor, :actor) returning id""").param("case", caseId).param("seq", seq)
                .param("title", request.title()).param("kind", request.kind().name()).param("argument", request.argument())
                .param("basis", request.legalBasis()).param("facts", request.facts()).param("texts", request.legalTexts())
                .param("memo", request.memoDocumentId()).param("on", request.raisedOn())
                .param("stage", request.raisedStageId()).param("prepared", request.preparedBy())
                .param("notes", request.notes()).param("template", request.templateId()).param("actor", actor.id())
                .query(UUID.class).single();
        if (request.raisedStageId() != null) {
            assertIn(id, request.raisedStageId(), AssertionStatus.RAISED, request.raisedOn(), request.memoDocumentId(), actor);
        }
        rows.event(caseId, "DEFENSE_ADDED", "Defence " + seq + " added: " + CaseRows.clip(request.title(), 120));
        rows.audit("DEFENSE_CREATE", actor, caseId, "defense=" + seq);
        return find(id);
    }

    @Transactional
    DefenseRequests.View update(UUID id, DefenseRequests.Update request) {
        UUID caseId = rows.caseOf("defense", id, "defense");
        ActorRef actor = rows.change(caseId);
        DefenseRequests.View current = find(id);
        if (current.version() != request.version()) {
            throw new ConflictException("defense " + id + " has changed since version " + request.version());
        }
        check(caseId, request.asCreate());
        int updated = jdbc.sql("""
                update defense set title = :title, kind = :kind, argument = :argument, legal_basis = :basis, facts = :facts,
                       legal_texts = :texts, memo_document_id = :memo, raised_on = :on, raised_stage_id = :stage,
                       prepared_by = :prepared, notes = :notes, template_id = :template, version = version + 1,
                       updated_at = now(), updated_by = :actor where id = :id and version = :version""")
                .param("title", request.title()).param("kind", request.kind().name()).param("argument", request.argument())
                .param("basis", request.legalBasis()).param("facts", request.facts()).param("texts", request.legalTexts())
                .param("memo", request.memoDocumentId()).param("on", request.raisedOn())
                .param("stage", request.raisedStageId()).param("prepared", request.preparedBy())
                .param("notes", request.notes()).param("template", request.templateId()).param("actor", actor.id())
                .param("id", id).param("version", request.version()).update();
        if (updated == 0) {
            throw new ConflictException("defense " + id + " was changed by someone else");
        }
        rows.event(caseId, "DEFENSE_CHANGED", "Defence " + current.seq() + " changed: " + CaseRows.clip(request.title(), 120));
        rows.audit("DEFENSE_UPDATE", actor, caseId, "defense=" + current.seq());
        return find(id);
    }

    @Transactional
    DefenseRequests.AssertionView assertIn(UUID defenseId, UUID proceedingId, DefenseRequests.AssertionBody body) {
        UUID caseId = rows.caseOf("defense", defenseId, "defense");
        ActorRef actor = rows.change(caseId);
        rows.sameCase("proceeding", proceedingId, caseId, "proceedingId");
        rows.sameCase("case_document", body.memoDocumentId(), caseId, "memoDocumentId");
        assertIn(defenseId, proceedingId, body.status(), body.onDate(), body.memoDocumentId(), actor);
        DefenseRequests.View defense = find(defenseId);
        rows.event(caseId, "DEFENSE_ASSERTION_RECORDED", "Defence " + defense.seq() + ": " + body.status() + " in a proceeding");
        rows.audit("DEFENSE_ASSERTION", actor, caseId, "defense=" + defense.seq() + " " + body.status());
        return assertions(caseId).stream().filter(a -> a.defenseId().equals(defenseId) && a.proceedingId().equals(proceedingId))
                .findFirst().orElseThrow(() -> new NotFoundException("assertion"));
    }

    @Transactional(readOnly = true)
    List<DefenseRequests.AssertionView> assertionsOfCase(UUID caseId) {
        rows.view(caseId);
        return assertions(caseId);
    }

    private void assertIn(UUID defenseId, UUID proceedingId, AssertionStatus status, LocalDate on, UUID memo, ActorRef actor) {
        jdbc.sql("""
                insert into defense_assertion (defense_id, proceeding_id, status, on_date, memo_document_id, created_by,
                                               updated_by)
                values (:defense, :proceeding, :status, :on, :memo, :actor, :actor)
                on conflict (defense_id, proceeding_id) do update set status = excluded.status, on_date = excluded.on_date,
                    memo_document_id = excluded.memo_document_id, version = defense_assertion.version + 1,
                    updated_at = now(), updated_by = excluded.updated_by""").param("defense", defenseId)
                .param("proceeding", proceedingId).param("status", status.name()).param("on", on).param("memo", memo)
                .param("actor", actor.id()).update();
    }

    private List<DefenseRequests.AssertionView> assertions(UUID caseId) {
        return jdbc.sql("""
                select d.id as defense_id, d.seq, p.id as proceeding_id, p.stage, a.status, a.on_date, a.memo_document_id,
                       m.title as memo_title
                from defense_assertion a join defense d on d.id = a.defense_id join proceeding p on p.id = a.proceeding_id
                     left join case_document m on m.id = a.memo_document_id
                where d.case_id = :case order by\s""" + CaseRows.STAGE_ORDER + ", p.filing_date, d.seq").param("case", caseId)
                .query((rs, n) -> new DefenseRequests.AssertionView(rs.getObject("defense_id", UUID.class), rs.getInt("seq"),
                        rs.getObject("proceeding_id", UUID.class), rs.getString("stage"),
                        AssertionStatus.valueOf(rs.getString("status")), rs.getObject("on_date", LocalDate.class),
                        rs.getObject("memo_document_id", UUID.class), rs.getString("memo_title")))
                .list();
    }

    private void check(UUID caseId, DefenseRequests.Create request) {
        rows.sameCase("case_document", request.memoDocumentId(), caseId, "memoDocumentId");
        rows.sameCase("proceeding", request.raisedStageId(), caseId, "raisedStageId");
        rows.user(request.preparedBy(), "preparedBy");
        if (request.templateId() != null && !jdbc.sql("select exists (select 1 from defense_template where id = :id)")
                .param("id", request.templateId()).query(Boolean.class).single()) {
            throw new FieldErrorException("templateId", "Exists");
        }
    }

    private DefenseRequests.View find(UUID id) {
        return jdbc.sql(SELECT + " where d.id = :id").param("id", id).query(DefenseService::view).optional()
                .orElseThrow(() -> new NotFoundException("defense"));
    }

    private static DefenseRequests.View view(ResultSet rs, int n) throws SQLException {
        return new DefenseRequests.View(rs.getObject("id", UUID.class), rs.getObject("case_id", UUID.class), rs.getInt("seq"),
                rs.getString("title"), DefenseKind.valueOf(rs.getString("kind")), rs.getString("argument"),
                rs.getString("legal_basis"), rs.getString("facts"), rs.getString("legal_texts"),
                rs.getObject("memo_document_id", UUID.class), rs.getString("memo_title"),
                rs.getObject("raised_on", LocalDate.class), rs.getObject("raised_stage_id", UUID.class),
                rs.getObject("prepared_by", UUID.class), rs.getString("prepared_name"), rs.getString("notes"),
                rs.getObject("template_id", UUID.class), rs.getInt("evidence_count"), rs.getLong("version"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }
}
