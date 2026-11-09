package com.lawdesk.settlements;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
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
import com.lawdesk.documents.DocumentArchive;
import com.lawdesk.lists.ManagedLists;
import com.lawdesk.security.ActorRef;
import com.lawdesk.security.CurrentActor;
import com.lawdesk.security.Permission;
import com.lawdesk.settlements.SettlementRequests.DocumentRef;
import com.lawdesk.settlements.SettlementRequests.HistoryEntry;
import com.lawdesk.settlements.SettlementRequests.Settlement;
import com.lawdesk.settlements.SettlementRequests.Status;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Settlements, waivers and conciliations (LD-057, requirements section 23). The settlement amount is its own (D5). A state
 * change is recorded with who and when; approving or rejecting needs {@code SETTLEMENT_APPROVE}, everything else
 * {@code SETTLEMENT_MANAGE}, and every write needs the right to change the case (D3). Recording, approving or executing a
 * settlement never changes the status of the case or of a linked case: closing stays a manual decision (workflow 9).
 */
@Service
class SettlementService {

    static final String KIND_UNKNOWN = "SETTLEMENT_KIND_UNKNOWN";
    static final String PARTY_NOT_IN_CASE = "SETTLEMENT_PARTY_NOT_IN_CASE";
    static final String TRANSITION = "SETTLEMENT_TRANSITION";
    static final String NOT_EDITABLE = "SETTLEMENT_NOT_EDITABLE";
    static final String DOCUMENT_NOT_IN_CASE = "SETTLEMENT_DOCUMENT_NOT_IN_CASE";
    static final String CASE_ARCHIVED = "CASE_ARCHIVED";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** Where each state may go: flexible (workflow section 33), but never out of a finished or withdrawn settlement. */
    private static final Map<Status, Set<Status>> NEXT = new EnumMap<>(Status.class);

    static {
        NEXT.put(Status.PROPOSED, Set.of(Status.UNDER_REVIEW, Status.APPROVED, Status.REJECTED, Status.WITHDRAWN));
        NEXT.put(Status.UNDER_REVIEW, Set.of(Status.PROPOSED, Status.APPROVED, Status.REJECTED, Status.WITHDRAWN));
        NEXT.put(Status.APPROVED, Set.of(Status.EXECUTED, Status.WITHDRAWN));
        NEXT.put(Status.REJECTED, Set.of(Status.PROPOSED));
        NEXT.put(Status.EXECUTED, Set.of());
        NEXT.put(Status.WITHDRAWN, Set.of());
    }

    private static final String SELECT = """
            select s.id, s.case_id, s.kind, s.status, s.offering_party_id, p.name as offering_party_name,
                   s.offering_party_text, s.settlement_amount, s.legal_grounds, s.financial_impact_note, s.legal_opinion,
                   s.approving_authority, s.decision_reason, s.decided_by, s.decided_at, s.completed_on, s.version,
                   s.created_at, s.created_by, s.updated_at, s.updated_by
            from settlement s left join party p on p.id = s.offering_party_id""";

    private final JdbcClient jdbc;
    private final CaseLookup cases;
    private final CaseAccessPolicy access;
    private final CurrentActor currentActor;
    private final AuditLog audit;
    private final DocumentArchive documents;
    private final ManagedLists lists;

    SettlementService(JdbcClient jdbc, CaseLookup cases, CaseAccessPolicy access, CurrentActor currentActor,
            AuditLog audit, DocumentArchive documents, ManagedLists lists) {
        this.jdbc = jdbc;
        this.cases = cases;
        this.access = access;
        this.currentActor = currentActor;
        this.audit = audit;
        this.documents = documents;
        this.lists = lists;
    }

    // ---- reads ----------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    List<Settlement> forCase(UUID caseId) {
        access.requireView(caseId);
        return jdbc.sql(SELECT + " where s.case_id = :id order by s.created_at, s.id").param("id", caseId)
                .query((rs, n) -> view(rs)).list();
    }

    @Transactional(readOnly = true)
    Settlement get(UUID id) {
        Settlement found = load(id);
        access.requireView(found.caseId());
        return found;
    }

    @Transactional(readOnly = true)
    List<HistoryEntry> history(UUID id) {
        Settlement found = load(id);
        access.requireView(found.caseId());
        return jdbc.sql("""
                        select h.id, h.action, h.from_status, h.to_status, h.changes::text as changes, h.note, h.actor_id,
                               u.display_name as actor_name, h.occurred_at
                        from settlement_history h left join app_user u on u.id = h.actor_id
                        where h.settlement_id = :id order by h.occurred_at, h.id""")
                .param("id", id).query((rs, n) -> new HistoryEntry(rs.getObject("id", UUID.class), rs.getString("action"),
                        rs.getString("from_status"), rs.getString("to_status"), parse(rs.getString("changes")),
                        rs.getString("note"), rs.getObject("actor_id", UUID.class), rs.getString("actor_name"),
                        rs.getTimestamp("occurred_at").toInstant()))
                .list();
    }

    private Settlement load(UUID id) {
        return jdbc.sql(SELECT + " where s.id = :id").param("id", id).query((rs, n) -> view(rs)).optional()
                .orElseThrow(() -> new NotFoundException("settlement"));
    }

    private Settlement view(ResultSet rs) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        List<DocumentRef> docs = jdbc.sql("""
                        select d.id, d.title, d.file_name from settlement_document sd
                        join case_document d on d.id = sd.document_id where sd.settlement_id = :id
                        order by sd.linked_at, d.id""")
                .param("id", id).query((r, n) -> new DocumentRef(r.getObject("id", UUID.class), r.getString("title"),
                        r.getString("file_name")))
                .list();
        return new Settlement(id, rs.getObject("case_id", UUID.class), rs.getString("kind"), rs.getString("status"),
                rs.getObject("offering_party_id", UUID.class), rs.getString("offering_party_name"),
                rs.getString("offering_party_text"), rs.getBigDecimal("settlement_amount"), rs.getString("legal_grounds"),
                rs.getString("financial_impact_note"), rs.getString("legal_opinion"), rs.getString("approving_authority"),
                rs.getString("decision_reason"), rs.getObject("decided_by", UUID.class),
                rs.getTimestamp("decided_at") == null ? null : rs.getTimestamp("decided_at").toInstant(),
                rs.getObject("completed_on", LocalDate.class), docs, rs.getLong("version"),
                rs.getTimestamp("created_at").toInstant(), rs.getObject("created_by", UUID.class),
                rs.getTimestamp("updated_at").toInstant(), rs.getObject("updated_by", UUID.class));
    }

    private static JsonNode parse(String json) {
        return json == null ? null : JSON.readTree(json);
    }

    // ---- writes ---------------------------------------------------------------------------------------------------

    @Transactional
    Settlement create(UUID caseId, SettlementRequests.Create request) {
        ActorRef actor = currentActor.require();
        access.requireChange(caseId);
        requireWritable(caseId);
        lists.requireActive(ManagedLists.SETTLEMENT_KIND, request.kind(), KIND_UNKNOWN);
        requirePartyOfCase(caseId, request.offeringPartyId());
        UUID id = jdbc.sql("""
                        insert into settlement (case_id, kind, offering_party_id, offering_party_text, settlement_amount,
                            legal_grounds, financial_impact_note, legal_opinion, approving_authority, created_by, updated_by)
                        values (:caseId, :kind, :party, :partyText, :amount, :grounds, :impact, :opinion, :authority, :actor,
                            :actor)
                        returning id""")
                .param("caseId", caseId).param("kind", request.kind()).param("party", request.offeringPartyId())
                .param("partyText", request.offeringPartyText()).param("amount", request.settlementAmount())
                .param("grounds", request.legalGrounds()).param("impact", request.financialImpactNote())
                .param("opinion", request.legalOpinion()).param("authority", request.approvingAuthority())
                .param("actor", actor.id()).query(UUID.class).single();
        history(id, "CREATED", null, Status.PROPOSED.name(), null, null, actor);
        cases.recordEvent(caseId, "SETTLEMENT_RECORDED", "Settlement recorded (" + request.kind() + ")");
        audit.recordWithChange("SETTLEMENT_CREATE", actor.id(), actor.username(), "SETTLEMENT", id.toString(),
                request.kind());
        return load(id);
    }

    @Transactional
    Settlement update(UUID id, SettlementRequests.Update request) {
        ActorRef actor = currentActor.require();
        Settlement current = load(id);
        access.requireChange(current.caseId());
        requireWritable(current.caseId());
        Status status = Status.valueOf(current.status());
        if (status != Status.PROPOSED && status != Status.UNDER_REVIEW) {
            throw new BusinessRuleException(NOT_EDITABLE,
                    "the terms of a settlement are fixed once it is decided, executed or withdrawn");
        }
        if (!Objects.equals(current.kind(), request.kind())) {
            lists.requireActive(ManagedLists.SETTLEMENT_KIND, request.kind(), KIND_UNKNOWN);
        }
        requirePartyOfCase(current.caseId(), request.offeringPartyId());
        Map<String, Object> changes = new LinkedHashMap<>();
        diff(changes, "kind", current.kind(), request.kind());
        diff(changes, "offeringPartyId", current.offeringPartyId(), request.offeringPartyId());
        diff(changes, "offeringPartyText", current.offeringPartyText(), request.offeringPartyText());
        diff(changes, "settlementAmount", amount(current.settlementAmount()), amount(request.settlementAmount()));
        diff(changes, "legalGrounds", current.legalGrounds(), request.legalGrounds());
        diff(changes, "financialImpactNote", current.financialImpactNote(), request.financialImpactNote());
        diff(changes, "legalOpinion", current.legalOpinion(), request.legalOpinion());
        diff(changes, "approvingAuthority", current.approvingAuthority(), request.approvingAuthority());
        int updated = jdbc.sql("""
                        update settlement set kind = :kind, offering_party_id = :party, offering_party_text = :partyText,
                            settlement_amount = :amount, legal_grounds = :grounds, financial_impact_note = :impact,
                            legal_opinion = :opinion, approving_authority = :authority, version = version + 1,
                            updated_at = now(), updated_by = :actor
                        where id = :id and version = :version""")
                .param("kind", request.kind()).param("party", request.offeringPartyId())
                .param("partyText", request.offeringPartyText()).param("amount", request.settlementAmount())
                .param("grounds", request.legalGrounds()).param("impact", request.financialImpactNote())
                .param("opinion", request.legalOpinion()).param("authority", request.approvingAuthority())
                .param("actor", actor.id()).param("id", id).param("version", request.version()).update();
        if (updated != 1) {
            throw new ConflictException("the settlement was changed by someone else");
        }
        history(id, "UPDATED", null, null, changes, null, actor);
        cases.recordEvent(current.caseId(), "SETTLEMENT_UPDATED", "Settlement terms updated");
        audit.recordWithChange("SETTLEMENT_UPDATE", actor.id(), actor.username(), "SETTLEMENT", id.toString(), "terms");
        return load(id);
    }

    @Transactional
    Settlement changeStatus(UUID id, SettlementRequests.StatusChange request) {
        ActorRef actor = currentActor.require();
        Settlement current = load(id);
        access.requireView(current.caseId());
        Status from = Status.valueOf(current.status());
        Status to = request.status();
        boolean decision = to == Status.APPROVED || to == Status.REJECTED;
        Permission needed = decision ? Permission.SETTLEMENT_APPROVE : Permission.SETTLEMENT_MANAGE;
        if (!currentActor.has(needed)) {
            throw new AccessDeniedException("missing " + needed);
        }
        access.requireChange(current.caseId());
        requireWritable(current.caseId());
        if (!NEXT.get(from).contains(to)) {
            throw new BusinessRuleException(TRANSITION, "a settlement cannot move from " + from + " to " + to);
        }
        if (to == Status.REJECTED && request.reason() == null) {
            throw new FieldErrorException("reason", "NotBlank");
        }
        if (to == Status.EXECUTED && request.completedOn() == null) {
            throw new FieldErrorException("completedOn", "NotNull");
        }
        int updated = jdbc.sql("""
                        update settlement set status = cast(:to as text),
                            decided_by = case when :decision then cast(:actor as uuid)
                                              when cast(:to as text) in ('APPROVED', 'EXECUTED') then decided_by end,
                            decided_at = case when :decision then now()
                                              when cast(:to as text) in ('APPROVED', 'EXECUTED') then decided_at end,
                            decision_reason = case when :decision then cast(:reason as text)
                                                   when cast(:to as text) in ('APPROVED', 'EXECUTED') then decision_reason end,
                            completed_on = case when cast(:to as text) = 'EXECUTED' then cast(:completedOn as date) end,
                            version = version + 1, updated_at = now(), updated_by = cast(:actor as uuid)
                        where id = :id and version = :version""")
                .param("to", to.name()).param("decision", decision).param("actor", actor.id())
                .param("reason", request.reason()).param("completedOn", request.completedOn()).param("id", id)
                .param("version", request.version()).update();
        if (updated != 1) {
            throw new ConflictException("the settlement was changed by someone else");
        }
        history(id, "STATUS_CHANGED", from.name(), to.name(), null, request.reason(), actor);
        cases.recordEvent(current.caseId(), "SETTLEMENT_STATUS_CHANGED", "Settlement: " + from + " -> " + to);
        audit.recordWithChange("SETTLEMENT_STATUS", actor.id(), actor.username(), "SETTLEMENT", id.toString(),
                from + "->" + to);
        return load(id);
    }

    @Transactional
    Settlement linkDocument(UUID id, UUID documentId) {
        ActorRef actor = currentActor.require();
        Settlement current = load(id);
        access.requireChange(current.caseId());
        requireWritable(current.caseId());
        requireOpen(current);
        if (!documents.belongsToCase(documentId, current.caseId())) {
            throw new BusinessRuleException(DOCUMENT_NOT_IN_CASE, "the document is not in this case file");
        }
        int added = jdbc.sql("""
                        insert into settlement_document (settlement_id, document_id, linked_by) values (:id, :doc, :actor)
                        on conflict do nothing""")
                .param("id", id).param("doc", documentId).param("actor", actor.id()).update();
        if (added == 1) {
            history(id, "DOCUMENT_LINKED", null, null, Map.of("document", Map.of("to", documentId.toString())), null,
                    actor);
            cases.recordEvent(current.caseId(), "SETTLEMENT_DOCUMENT_LINKED", "A supporting document was linked to a settlement");
            audit.recordWithChange("SETTLEMENT_DOCUMENT", actor.id(), actor.username(), "SETTLEMENT", id.toString(), "link");
        }
        return load(id);
    }

    @Transactional
    Settlement unlinkDocument(UUID id, UUID documentId) {
        ActorRef actor = currentActor.require();
        Settlement current = load(id);
        access.requireChange(current.caseId());
        requireWritable(current.caseId());
        requireOpen(current);
        int removed = jdbc.sql("delete from settlement_document where settlement_id = :id and document_id = :doc")
                .param("id", id).param("doc", documentId).update();
        if (removed == 1) {
            history(id, "DOCUMENT_UNLINKED", null, null, Map.of("document", Map.of("from", documentId.toString())), null,
                    actor);
            cases.recordEvent(current.caseId(), "SETTLEMENT_DOCUMENT_UNLINKED",
                    "A supporting document was unlinked from a settlement");
            audit.recordWithChange("SETTLEMENT_DOCUMENT", actor.id(), actor.username(), "SETTLEMENT", id.toString(),
                    "unlink");
        }
        return load(id);
    }

    // ---- rules and helpers --------------------------------------------------------------------------------------

    private void requireOpen(Settlement settlement) {
        if (Status.WITHDRAWN.name().equals(settlement.status())) {
            throw new BusinessRuleException(NOT_EDITABLE, "a withdrawn settlement is closed");
        }
    }

    private void requireWritable(UUID caseId) {
        CaseSummary summary = cases.find(caseId).orElseThrow(() -> new NotFoundException("case"));
        if ("ARCHIVED".equals(summary.status())) {
            throw new BusinessRuleException(CASE_ARCHIVED, "an archived case is read-only");
        }
    }

    private void requirePartyOfCase(UUID caseId, UUID partyId) {
        if (partyId == null) {
            return;
        }
        boolean onCase = jdbc.sql("select exists (select 1 from case_party where case_id = :c and party_id = :p)")
                .param("c", caseId).param("p", partyId).query(Boolean.class).single();
        if (!onCase) {
            throw new BusinessRuleException(PARTY_NOT_IN_CASE, "the offering party is not a party of this case");
        }
    }

    private void history(UUID id, String action, String from, String to, Map<String, Object> changes, String note,
            ActorRef actor) {
        jdbc.sql("""
                        insert into settlement_history (settlement_id, action, from_status, to_status, changes, note, actor_id)
                        values (:id, :action, :from, :to, cast(:changes as jsonb), :note, :actor)""")
                .param("id", id).param("action", action).param("from", from).param("to", to)
                .param("changes", changes == null ? null : JSON.writeValueAsString(changes)).param("note", note)
                .param("actor", actor.id()).update();
    }

    private static String amount(BigDecimal value) {
        return value == null ? null : value.setScale(3, java.math.RoundingMode.UNNECESSARY).toPlainString();
    }

    private static void diff(Map<String, Object> changes, String field, Object from, Object to) {
        if (!Objects.equals(from, to)) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("from", from == null ? null : from.toString());
            entry.put("to", to == null ? null : to.toString());
            changes.put(field, entry);
        }
    }

    /** All changes are listed in an unmodifiable view for tests of the transition table. */
    static Map<Status, Set<Status>> transitions() {
        return Map.copyOf(NEXT);
    }

    static List<Status> statuses() {
        return new ArrayList<>(List.of(Status.values()));
    }
}
