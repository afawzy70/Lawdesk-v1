package com.lawdesk.pleadings;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.api.ConflictException;
import com.lawdesk.api.FieldErrorException;
import com.lawdesk.api.NotFoundException;
import com.lawdesk.security.ActorRef;

/**
 * Facts and evidence (requirement section 20; scenario S8). Evidence refers to the stored documents of the case
 * ({@code case_document}), which are never moved or copied; one item has any number of links to facts, defences and
 * claims. A gap is evidence the case needs and lacks, or has and has not yet put before the court.
 */
@Service
class EvidenceService {

    static final String NOT_OBTAINED = "NOT_OBTAINED";
    static final String NOT_SUBMITTED = "NOT_SUBMITTED";

    private static final String ITEM = """
            select e.id, e.case_id, e.document_id, d.title as document_title, e.name, e.doc_type, e.issued_on, e.issuer,
                   e.received_on, e.source, e.role, e.importance, e.submitted_to_court, e.submitted_on, e.submitted_in,
                   e.opponent_responded, e.opponent_response, e.court_position, e.notes, e.version
            from evidence_item e left join case_document d on d.id = e.document_id""";

    private final JdbcClient jdbc;
    private final CaseRows rows;

    EvidenceService(JdbcClient jdbc, CaseRows rows) {
        this.jdbc = jdbc;
        this.rows = rows;
    }

    // ---- facts ------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    List<EvidenceRequests.FactView> facts(UUID caseId) {
        rows.view(caseId);
        return jdbc.sql(factSelect() + " where f.case_id = :id order by f.seq").param("id", caseId)
                .query(EvidenceService::fact).list();
    }

    @Transactional
    EvidenceRequests.FactView addFact(UUID caseId, EvidenceRequests.FactBody body) {
        ActorRef actor = rows.change(caseId);
        int seq = rows.nextSeq("case_fact", caseId);
        UUID id = jdbc.sql("""
                insert into case_fact (case_id, seq, text, fact_date, created_by, updated_by)
                values (:case, :seq, :text, :date, :actor, :actor) returning id""").param("case", caseId).param("seq", seq)
                .param("text", body.text()).param("date", body.factDate()).param("actor", actor.id()).query(UUID.class)
                .single();
        rows.event(caseId, "FACT_ADDED", "Fact " + seq + " added: " + CaseRows.clip(body.text(), 120));
        rows.audit("FACT_CREATE", actor, caseId, "fact=" + seq);
        return fact(id);
    }

    @Transactional
    EvidenceRequests.FactView updateFact(UUID id, EvidenceRequests.FactBody body) {
        UUID caseId = rows.caseOf("case_fact", id, "fact");
        ActorRef actor = rows.change(caseId);
        int updated = jdbc.sql("""
                update case_fact set text = :text, fact_date = :date, version = version + 1, updated_at = now(),
                       updated_by = :actor where id = :id and version = :version""").param("text", body.text())
                .param("date", body.factDate()).param("actor", actor.id()).param("id", id)
                .param("version", body.version() == null ? -1 : body.version()).update();
        if (updated == 0) {
            throw new ConflictException("fact " + id + " was changed by someone else");
        }
        rows.event(caseId, "FACT_CHANGED", "Fact changed: " + CaseRows.clip(body.text(), 120));
        rows.audit("FACT_UPDATE", actor, caseId, "fact=" + id);
        return fact(id);
    }

    private static String factSelect() {
        return """
                select f.id, f.case_id, f.seq, f.text, f.fact_date, f.version, f.updated_at,
                       (select count(*) from evidence_link l where l.fact_id = f.id) as evidence_count
                from case_fact f""";
    }

    private EvidenceRequests.FactView fact(UUID id) {
        return jdbc.sql(factSelect() + " where f.id = :id").param("id", id).query(EvidenceService::fact).optional()
                .orElseThrow(() -> new NotFoundException("fact"));
    }

    private static EvidenceRequests.FactView fact(ResultSet rs, int n) throws SQLException {
        return new EvidenceRequests.FactView(rs.getObject("id", UUID.class), rs.getObject("case_id", UUID.class),
                rs.getInt("seq"), rs.getString("text"), rs.getObject("fact_date", LocalDate.class),
                rs.getInt("evidence_count"), rs.getLong("version"), rs.getTimestamp("updated_at").toInstant());
    }

    // ---- evidence ---------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    List<EvidenceRequests.ItemView> items(UUID caseId) {
        rows.view(caseId);
        return itemsOf(caseId, false);
    }

    @Transactional(readOnly = true)
    List<EvidenceRequests.ItemView> gaps(UUID caseId) {
        rows.view(caseId);
        return itemsOf(caseId, true);
    }

    @Transactional
    EvidenceRequests.ItemView add(UUID caseId, EvidenceRequests.ItemBody body) {
        ActorRef actor = rows.change(caseId);
        check(caseId, body);
        UUID id = jdbc.sql("""
                insert into evidence_item (case_id, document_id, name, doc_type, issued_on, issuer, received_on, source, role,
                                           importance, submitted_to_court, submitted_on, submitted_in, opponent_responded,
                                           opponent_response, court_position, notes, created_by, updated_by)
                values (:case, :document, :name, :type, :issued, :issuer, :received, :source, :role, :importance, :submitted,
                        :submittedOn, :submittedIn, :responded, :response, :position, :notes, :actor, :actor)
                returning id""").param("case", caseId).params(values(body)).param("actor", actor.id()).query(UUID.class)
                .single();
        rows.event(caseId, "EVIDENCE_ADDED", "Evidence added (" + body.role() + "): " + CaseRows.clip(body.name(), 120));
        rows.audit("EVIDENCE_CREATE", actor, caseId, "evidence=" + id);
        return item(id);
    }

    @Transactional
    EvidenceRequests.ItemView update(UUID id, EvidenceRequests.ItemBody body) {
        UUID caseId = rows.caseOf("evidence_item", id, "evidence");
        ActorRef actor = rows.change(caseId);
        check(caseId, body);
        int updated = jdbc.sql("""
                update evidence_item set document_id = :document, name = :name, doc_type = :type, issued_on = :issued,
                       issuer = :issuer, received_on = :received, source = :source, role = :role, importance = :importance,
                       submitted_to_court = :submitted, submitted_on = :submittedOn, submitted_in = :submittedIn,
                       opponent_responded = :responded, opponent_response = :response, court_position = :position,
                       notes = :notes, version = version + 1, updated_at = now(), updated_by = :actor
                where id = :id and version = :version""").params(values(body)).param("actor", actor.id()).param("id", id)
                .param("version", body.version() == null ? -1 : body.version()).update();
        if (updated == 0) {
            throw new ConflictException("evidence " + id + " was changed by someone else");
        }
        rows.event(caseId, "EVIDENCE_CHANGED", "Evidence changed (" + body.role() + "): " + CaseRows.clip(body.name(), 120));
        rows.audit("EVIDENCE_UPDATE", actor, caseId, "evidence=" + id);
        return item(id);
    }

    private void check(UUID caseId, EvidenceRequests.ItemBody body) {
        rows.sameCase("case_document", body.documentId(), caseId, "documentId");
        if (!body.submitted() && (body.submittedOn() != null || body.submittedIn() != null)) {
            throw new FieldErrorException("submittedToCourt", "Consistent");
        }
    }

    private static Map<String, Object> values(EvidenceRequests.ItemBody body) {
        Map<String, Object> out = new HashMap<>();
        out.put("document", body.documentId());
        out.put("name", body.name());
        out.put("type", body.docType());
        out.put("issued", body.issuedOn());
        out.put("issuer", body.issuer());
        out.put("received", body.receivedOn());
        out.put("source", body.source());
        out.put("role", body.role().name());
        out.put("importance", body.importance() == null ? null : body.importance().name());
        out.put("submitted", body.submitted());
        out.put("submittedOn", body.submittedOn());
        out.put("submittedIn", body.submittedIn());
        out.put("responded", body.opponentResponded());
        out.put("response", body.opponentResponse());
        out.put("position", body.courtPosition());
        out.put("notes", body.notes());
        return out;
    }

    // ---- links ------------------------------------------------------------------------------------------------

    @Transactional
    EvidenceRequests.LinkView link(UUID evidenceId, EvidenceRequests.LinkBody body) {
        UUID caseId = rows.caseOf("evidence_item", evidenceId, "evidence");
        ActorRef actor = rows.change(caseId);
        int targets = (body.factId() == null ? 0 : 1) + (body.defenseId() == null ? 0 : 1) + (body.claimId() == null ? 0 : 1);
        if (targets != 1) {
            throw new FieldErrorException("factId", "ExactlyOne");
        }
        rows.sameCase("case_fact", body.factId(), caseId, "factId");
        rows.sameCase("defense", body.defenseId(), caseId, "defenseId");
        rows.sameCase("claim", body.claimId(), caseId, "claimId");
        UUID id;
        try {
            id = jdbc.sql("""
                    insert into evidence_link (evidence_id, fact_id, defense_id, claim_id, created_by)
                    values (:evidence, :fact, :defense, :claim, :actor) returning id""").param("evidence", evidenceId)
                    .param("fact", body.factId()).param("defense", body.defenseId()).param("claim", body.claimId())
                    .param("actor", actor.id()).query(UUID.class).single();
        } catch (DuplicateKeyException e) {
            throw new ConflictException("the evidence is linked to it already");
        }
        EvidenceRequests.LinkView view = links(evidenceId).stream().filter(l -> l.id().equals(id)).findFirst().orElseThrow();
        rows.event(caseId, "EVIDENCE_LINKED", "Evidence linked to " + view.label());
        rows.audit("EVIDENCE_LINK", actor, caseId, "evidence=" + evidenceId + " " + view.kind());
        return view;
    }

    @Transactional
    void unlink(UUID evidenceId, UUID linkId) {
        UUID caseId = rows.caseOf("evidence_item", evidenceId, "evidence");
        ActorRef actor = rows.change(caseId);
        if (jdbc.sql("delete from evidence_link where id = :id and evidence_id = :evidence").param("id", linkId)
                .param("evidence", evidenceId).update() == 0) {
            throw new NotFoundException("link");
        }
        rows.event(caseId, "EVIDENCE_UNLINKED", "A link of an evidence item was removed");
        rows.audit("EVIDENCE_UNLINK", actor, caseId, "evidence=" + evidenceId);
    }

    @Transactional
    EvidenceRequests.FollowupView follow(UUID evidenceId, EvidenceRequests.FollowupBody body) {
        UUID caseId = rows.caseOf("evidence_item", evidenceId, "evidence");
        ActorRef actor = rows.change(caseId);
        rows.sameCase("task", body.taskId(), caseId, "taskId");
        UUID id;
        try {
            id = jdbc.sql("""
                    insert into gap_followup (evidence_id, task_id, created_by) values (:evidence, :task, :actor)
                    returning id""").param("evidence", evidenceId).param("task", body.taskId()).param("actor", actor.id())
                    .query(UUID.class).single();
        } catch (DuplicateKeyException e) {
            throw new ConflictException("the task follows this evidence already");
        }
        rows.event(caseId, "EVIDENCE_FOLLOWUP_LINKED", "A task follows up missing evidence");
        rows.audit("EVIDENCE_FOLLOWUP", actor, caseId, "evidence=" + evidenceId);
        return followups(evidenceId).stream().filter(f -> f.id().equals(id)).findFirst().orElseThrow();
    }

    @Transactional
    void unfollow(UUID evidenceId, UUID followupId) {
        UUID caseId = rows.caseOf("evidence_item", evidenceId, "evidence");
        rows.change(caseId);
        if (jdbc.sql("delete from gap_followup where id = :id and evidence_id = :evidence").param("id", followupId)
                .param("evidence", evidenceId).update() == 0) {
            throw new NotFoundException("follow-up");
        }
    }

    // ---- document usage ---------------------------------------------------------------------------------------

    /** Where each stored document of the case is used, for the documents screen of the case file. */
    @Transactional(readOnly = true)
    List<EvidenceRequests.DocumentUse> documentUsage(UUID caseId) {
        rows.view(caseId);
        List<EvidenceRequests.DocumentUse> uses = new ArrayList<>();
        for (EvidenceRequests.ItemView e : itemsOf(caseId, false)) {
            if (e.documentId() != null) {
                uses.add(new EvidenceRequests.DocumentUse(e.documentId(), "EVIDENCE", e.id(), e.name(), e.links()));
            }
        }
        jdbc.sql("select source_document_id, id, seq from claim where case_id = :c and source_document_id is not null order by seq")
                .param("c", caseId).query(rs -> {
                    uses.add(new EvidenceRequests.DocumentUse(rs.getObject(1, UUID.class), "CLAIM_SOURCE",
                            rs.getObject(2, UUID.class), "Claim " + rs.getInt(3), List.of()));
                });
        jdbc.sql("select memo_document_id, id, seq, title from defense where case_id = :c and memo_document_id is not null order by seq")
                .param("c", caseId).query(rs -> {
                    uses.add(new EvidenceRequests.DocumentUse(rs.getObject(1, UUID.class), "DEFENSE_MEMO",
                            rs.getObject(2, UUID.class), "Defence " + rs.getInt(3) + ": " + rs.getString(4), List.of()));
                });
        jdbc.sql("""
                select a.memo_document_id, d.id, d.seq, d.title from defense_assertion a join defense d on d.id = a.defense_id
                where d.case_id = :c and a.memo_document_id is not null order by d.seq""").param("c", caseId).query(rs -> {
            uses.add(new EvidenceRequests.DocumentUse(rs.getObject(1, UUID.class), "ASSERTION_MEMO",
                    rs.getObject(2, UUID.class), "Defence " + rs.getInt(3) + ": " + rs.getString(4), List.of()));
        });
        jdbc.sql("""
                select j.copy_document_id, j.id, j.judgment_date from judgment j join proceeding p on p.id = j.proceeding_id
                where p.case_id = :c and j.copy_document_id is not null order by j.judgment_date""").param("c", caseId)
                .query(rs -> {
                    uses.add(new EvidenceRequests.DocumentUse(rs.getObject(1, UUID.class), "JUDGMENT_COPY",
                            rs.getObject(2, UUID.class), "Judgment of " + rs.getObject(3, LocalDate.class), List.of()));
                });
        return uses;
    }

    // ---- reading ----------------------------------------------------------------------------------------------

    private List<EvidenceRequests.ItemView> itemsOf(UUID caseId, boolean onlyGaps) {
        List<EvidenceRequests.ItemView> out = new ArrayList<>();
        for (EvidenceRequests.ItemView e : jdbc.sql(ITEM + " where e.case_id = :id order by e.created_at, e.id")
                .param("id", caseId).query(EvidenceService::item).list()) {
            String gap = gap(e);
            if (!onlyGaps || gap != null) {
                out.add(withDetails(e, gap));
            }
        }
        return out;
    }

    private EvidenceRequests.ItemView item(UUID id) {
        EvidenceRequests.ItemView e = jdbc.sql(ITEM + " where e.id = :id").param("id", id).query(EvidenceService::item)
                .optional().orElseThrow(() -> new NotFoundException("evidence"));
        return withDetails(e, gap(e));
    }

    private EvidenceRequests.ItemView withDetails(EvidenceRequests.ItemView e, String gap) {
        return new EvidenceRequests.ItemView(e.id(), e.caseId(), e.documentId(), e.documentTitle(), e.name(), e.docType(),
                e.issuedOn(), e.issuer(), e.receivedOn(), e.source(), e.role(), e.importance(), e.submittedToCourt(),
                e.submittedOn(), e.submittedIn(), e.opponentResponded(), e.opponentResponse(), e.courtPosition(), e.notes(),
                e.version(), links(e.id()), followups(e.id()), gap);
    }

    /** Evidence the case needs and does not have, or has and has not put before the court. */
    static String gap(EvidenceRequests.ItemView e) {
        if (e.role() == EvidenceRole.UNAVAILABLE || (e.role() == EvidenceRole.REQUIRED && e.documentId() == null)) {
            return NOT_OBTAINED;
        }
        boolean ours = e.role() == EvidenceRole.SUPPORTING || e.role() == EvidenceRole.SUPPLEMENTARY
                || e.role() == EvidenceRole.REQUIRED;
        return ours && e.documentId() != null && !e.submittedToCourt() ? NOT_SUBMITTED : null;
    }

    private List<EvidenceRequests.LinkView> links(UUID evidenceId) {
        return jdbc.sql("""
                select l.id, l.fact_id, l.defense_id, l.claim_id, f.seq as fact_seq, f.text as fact_text,
                       d.seq as defense_seq, d.title as defense_title, c.seq as claim_seq
                from evidence_link l left join case_fact f on f.id = l.fact_id left join defense d on d.id = l.defense_id
                     left join claim c on c.id = l.claim_id
                where l.evidence_id = :id order by l.created_at, l.id""").param("id", evidenceId).query((rs, n) -> {
            if (rs.getObject("fact_id") != null) {
                return new EvidenceRequests.LinkView(rs.getObject("id", UUID.class), "FACT", rs.getObject("fact_id", UUID.class),
                        "Fact " + rs.getInt("fact_seq") + ": " + CaseRows.clip(rs.getString("fact_text"), 80));
            }
            if (rs.getObject("defense_id") != null) {
                return new EvidenceRequests.LinkView(rs.getObject("id", UUID.class), "DEFENSE",
                        rs.getObject("defense_id", UUID.class), "Defence " + rs.getInt("defense_seq") + ": "
                                + CaseRows.clip(rs.getString("defense_title"), 80));
            }
            return new EvidenceRequests.LinkView(rs.getObject("id", UUID.class), "CLAIM", rs.getObject("claim_id", UUID.class),
                    "Claim " + rs.getInt("claim_seq"));
        }).list();
    }

    private List<EvidenceRequests.FollowupView> followups(UUID evidenceId) {
        return jdbc.sql("""
                select g.id, g.task_id, t.title, t.status, g.created_at from gap_followup g left join task t on t.id = g.task_id
                where g.evidence_id = :id order by g.created_at, g.id""").param("id", evidenceId)
                .query((rs, n) -> new EvidenceRequests.FollowupView(rs.getObject("id", UUID.class),
                        rs.getObject("task_id", UUID.class), rs.getString("title"), rs.getString("status"),
                        rs.getTimestamp("created_at").toInstant()))
                .list();
    }

    private static EvidenceRequests.ItemView item(ResultSet rs, int n) throws SQLException {
        String importance = rs.getString("importance");
        boolean responded = rs.getBoolean("opponent_responded");
        return new EvidenceRequests.ItemView(rs.getObject("id", UUID.class), rs.getObject("case_id", UUID.class),
                rs.getObject("document_id", UUID.class), rs.getString("document_title"), rs.getString("name"),
                rs.getString("doc_type"), rs.getObject("issued_on", LocalDate.class), rs.getString("issuer"),
                rs.getObject("received_on", LocalDate.class), rs.getString("source"),
                EvidenceRole.valueOf(rs.getString("role")), importance == null ? null : EvidenceImportance.valueOf(importance),
                rs.getBoolean("submitted_to_court"), rs.getObject("submitted_on", LocalDate.class),
                rs.getString("submitted_in"), rs.wasNull() ? null : responded, rs.getString("opponent_response"),
                rs.getString("court_position"), rs.getString("notes"), rs.getLong("version"), List.of(), List.of(), null);
    }
}
