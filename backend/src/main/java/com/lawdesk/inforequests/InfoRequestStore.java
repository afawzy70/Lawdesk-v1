package com.lawdesk.inforequests;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.lawdesk.cases.CaseAccessPolicy;

/** SQL of the information requests, replies and follow-ups (V661__LD-053_info_request.sql). */
@Repository
class InfoRequestStore {

    private static final String WAITING = "('SENT', 'AWAITING', 'PARTIAL', 'INCOMPLETE', 'OVERDUE')";

    private static final String SELECT = """
            select r.id, r.case_id, c.reference_no, c.title as case_title, r.request_no, r.unit_id, r.unit_name_snapshot,
                   r.subject, r.details, r.required_documents, r.drafted_on, r.sent_on, r.letter_no, r.letter_date,
                   r.due_on, r.responsible_member_id, m.display_name as responsible_name, r.addressee, r.notes,
                   r.status, r.delay_reason, r.close_reason, r.closed_on,
                   (r.due_on is not null and r.due_on < :today and r.status in %s) as overdue,
                   r.linked_fact_id, r.linked_defense_id,
                   (select count(*) from info_reply p where p.request_id = r.id) as reply_count,
                   r.version, r.created_at, r.updated_at
            from info_request r
            join legal_case c on c.id = r.case_id
            left join app_user m on m.id = r.responsible_member_id""".formatted(WAITING);

    private final JdbcClient jdbc;

    InfoRequestStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** The row's case, status and version, without the joins; empty when there is no such request. */
    record Head(UUID id, UUID caseId, String requestNo, InfoRequestStatus status, InfoRequestStatus statusBeforeOverdue,
            LocalDate dueOn, UUID responsibleMemberId, long version) {
    }

    Optional<Head> head(UUID id, boolean lock) {
        return jdbc.sql("select id, case_id, request_no, status, status_before_overdue, due_on, responsible_member_id,"
                        + " version from info_request where id = :id" + (lock ? " for update" : ""))
                .param("id", id)
                .query((rs, n) -> new Head(rs.getObject("id", UUID.class), rs.getObject("case_id", UUID.class),
                        rs.getString("request_no"), InfoRequestStatus.valueOf(rs.getString("status")),
                        rs.getString("status_before_overdue") == null ? null
                                : InfoRequestStatus.valueOf(rs.getString("status_before_overdue")),
                        rs.getObject("due_on", LocalDate.class), rs.getObject("responsible_member_id", UUID.class),
                        rs.getLong("version")))
                .optional();
    }

    Optional<InfoRequestRequests.Response> find(UUID id, LocalDate today) {
        return jdbc.sql(SELECT + " where r.id = :id").param("id", id).param("today", today)
                .query((rs, n) -> row(rs)).optional();
    }

    List<InfoRequestRequests.Response> forCase(UUID caseId, LocalDate today, int limit, long offset) {
        return jdbc.sql(SELECT + " where r.case_id = :caseId order by r.created_at, r.id limit :limit offset :offset")
                .param("caseId", caseId).param("today", today).param("limit", limit).param("offset", offset)
                .query((rs, n) -> row(rs)).list();
    }

    long countForCase(UUID caseId) {
        return jdbc.sql("select count(*) from info_request where case_id = :caseId").param("caseId", caseId)
                .query(Long.class).single();
    }

    /** Filters of the all-cases list; null values do not filter. */
    record Filter(String status, Boolean overdue, UUID unitId, UUID caseId) {
    }

    private String where(Filter filter, CaseAccessPolicy.Predicate visible, Map<String, Object> params) {
        params.putAll(visible.params());
        StringBuilder sql = new StringBuilder(" where " + visible.sql());
        if (filter.status() != null) {
            sql.append(" and r.status = :status");
            params.put("status", filter.status());
        }
        if (Boolean.TRUE.equals(filter.overdue())) {
            sql.append(" and r.due_on is not null and r.due_on < :today and r.status in ").append(WAITING);
        }
        if (filter.unitId() != null) {
            sql.append(" and r.unit_id = :unitId");
            params.put("unitId", filter.unitId());
        }
        if (filter.caseId() != null) {
            sql.append(" and r.case_id = :caseId");
            params.put("caseId", filter.caseId());
        }
        return sql.toString();
    }

    List<InfoRequestRequests.Response> list(Filter filter, CaseAccessPolicy.Predicate visible, LocalDate today,
            int limit, long offset) {
        Map<String, Object> params = new HashMap<>();
        String where = where(filter, visible, params);
        return jdbc.sql(SELECT + where + " order by r.due_on nulls last, r.created_at, r.id limit :limit offset :offset")
                .params(params).param("today", today).param("limit", limit).param("offset", offset)
                .query((rs, n) -> row(rs)).list();
    }

    long count(Filter filter, CaseAccessPolicy.Predicate visible, LocalDate today) {
        Map<String, Object> params = new HashMap<>();
        String where = where(filter, visible, params);
        return jdbc.sql("select count(*) from info_request r" + where).params(params).param("today", today)
                .query(Long.class).single();
    }

    UUID insert(UUID caseId, InfoRequestRequests.Create request, String unitName, UUID actor) {
        return jdbc.sql("""
                        insert into info_request (case_id, request_no, unit_id, unit_name_snapshot, subject, details,
                            required_documents, drafted_on, due_on, responsible_member_id, addressee, notes,
                            linked_fact_id, linked_defense_id, created_by, updated_by)
                        values (:caseId, 'IR-' || to_char(now(), 'YYYY') || '-' || lpad(nextval('info_request_no_seq')::text, 5, '0'),
                            :unitId, :unitName, :subject, :details, :requiredDocuments, :draftedOn, :dueOn, :responsible,
                            :addressee, :notes, :fact, :defense, :actor, :actor)
                        returning id""")
                .param("caseId", caseId).param("unitId", request.unitId()).param("unitName", unitName)
                .param("subject", request.subject()).param("details", request.details())
                .param("requiredDocuments", request.requiredDocuments()).param("draftedOn", request.draftedOn())
                .param("dueOn", request.dueOn()).param("responsible", request.responsibleMemberId())
                .param("addressee", request.addressee()).param("notes", request.notes())
                .param("fact", request.linkedFactId()).param("defense", request.linkedDefenseId())
                .param("actor", actor).query(UUID.class).single();
    }

    /** Applies the editable fields; returns false when the version is no longer current. */
    boolean update(UUID id, InfoRequestRequests.Update r, String unitName, InfoRequestStatus status,
            InfoRequestStatus statusBefore, UUID actor) {
        return jdbc.sql("""
                        update info_request set unit_id = :unitId, unit_name_snapshot = :unitName, subject = :subject,
                            details = :details, required_documents = :requiredDocuments, drafted_on = :draftedOn,
                            sent_on = :sentOn, letter_no = :letterNo, letter_date = :letterDate, due_on = :dueOn,
                            responsible_member_id = :responsible, addressee = :addressee, notes = :notes,
                            delay_reason = :delayReason, linked_fact_id = :fact, linked_defense_id = :defense,
                            status = :status, status_before_overdue = :before,
                            version = version + 1, updated_at = now(), updated_by = :actor
                        where id = :id and version = :version""")
                .param("id", id).param("version", r.version()).param("unitId", r.unitId()).param("unitName", unitName)
                .param("subject", r.subject()).param("details", r.details())
                .param("requiredDocuments", r.requiredDocuments()).param("draftedOn", r.draftedOn())
                .param("sentOn", r.sentOn()).param("letterNo", r.letterNo()).param("letterDate", r.letterDate())
                .param("dueOn", r.dueOn()).param("responsible", r.responsibleMemberId())
                .param("addressee", r.addressee()).param("notes", r.notes()).param("delayReason", r.delayReason())
                .param("fact", r.linkedFactId()).param("defense", r.linkedDefenseId())
                .param("status", status.name()).param("before", statusBefore == null ? null : statusBefore.name())
                .param("actor", actor).update() == 1;
    }

    boolean changeStatus(UUID id, long version, InfoRequestStatus status, LocalDate sentOn, String letterNo,
            LocalDate letterDate, String closeReason, LocalDate closedOn, UUID actor) {
        return jdbc.sql("""
                        update info_request set status = :status, status_before_overdue = null,
                            sent_on = coalesce(:sentOn, sent_on), letter_no = coalesce(:letterNo, letter_no),
                            letter_date = coalesce(:letterDate, letter_date), close_reason = :closeReason,
                            closed_on = :closedOn, version = version + 1, updated_at = now(), updated_by = :actor
                        where id = :id and version = :version""")
                .param("id", id).param("version", version).param("status", status.name()).param("sentOn", sentOn)
                .param("letterNo", letterNo).param("letterDate", letterDate).param("closeReason", closeReason)
                .param("closedOn", closedOn).param("actor", actor).update() == 1;
    }

    /** The status that follows from the replies (the latest reply, or complete as soon as one is); version bumps. */
    void followReplies(UUID id, InfoRequestStatus status, UUID actor) {
        jdbc.sql("""
                        update info_request set status = :status, status_before_overdue = null, version = version + 1,
                            updated_at = now(), updated_by = :actor where id = :id""")
                .param("id", id).param("status", status.name()).param("actor", actor).update();
    }

    UUID insertReply(UUID requestId, UUID caseId, InfoRequestRequests.ReplyInput reply, UUID documentId, UUID actor) {
        return jdbc.sql("""
                        insert into info_reply (request_id, case_id, received_on, completeness, summary, document_id,
                            linked_fact_id, linked_defense_id, created_by)
                        values (:request, :caseId, :receivedOn, :completeness, :summary, :document, :fact, :defense, :actor)
                        returning id""")
                .param("request", requestId).param("caseId", caseId).param("receivedOn", reply.receivedOn())
                .param("completeness", reply.completeness().name()).param("summary", reply.summary())
                .param("document", documentId).param("fact", reply.linkedFactId()).param("defense", reply.linkedDefenseId())
                .param("actor", actor).query(UUID.class).single();
    }

    /** The completeness of the latest reply and whether any reply is complete; empty without replies. */
    Optional<InfoRequestStatus> statusFromReplies(UUID requestId) {
        List<String> all = jdbc.sql("select completeness from info_reply where request_id = :id"
                        + " order by received_on desc, created_at desc, id").param("id", requestId)
                .query(String.class).list();
        if (all.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(InfoRequestStatus.valueOf(all.contains("COMPLETE") ? "COMPLETE" : all.get(0)));
    }

    UUID insertFollowUp(UUID requestId, UUID caseId, InfoRequestRequests.FollowUpInput input, LocalDate onDate,
            UUID actor) {
        return jdbc.sql("""
                        insert into info_follow_up (request_id, case_id, kind, on_date, note, escalated_to, created_by)
                        values (:request, :caseId, :kind, :onDate, :note, :target, :actor) returning id""")
                .param("request", requestId).param("caseId", caseId).param("kind", input.kind().name())
                .param("onDate", onDate).param("note", input.note()).param("target", input.escalatedTo())
                .param("actor", actor).query(UUID.class).single();
    }

    Map<UUID, List<InfoRequestRequests.Reply>> replies(Collection<UUID> requestIds) {
        Map<UUID, List<InfoRequestRequests.Reply>> out = new HashMap<>();
        if (requestIds.isEmpty()) {
            return out;
        }
        jdbc.sql("""
                        select p.id, p.request_id, p.received_on, p.completeness, p.summary, p.document_id, d.title,
                               p.linked_fact_id, p.linked_defense_id, p.created_at, p.created_by, u.display_name
                        from info_reply p left join case_document d on d.id = p.document_id
                        left join app_user u on u.id = p.created_by
                        where p.request_id in (:ids) order by p.received_on, p.created_at, p.id""")
                .param("ids", requestIds)
                .query((rs, n) -> new InfoRequestRequests.Reply(rs.getObject("id", UUID.class),
                        rs.getObject("request_id", UUID.class), rs.getObject("received_on", LocalDate.class),
                        rs.getString("completeness"), rs.getString("summary"), rs.getObject("document_id", UUID.class),
                        rs.getString("title"), rs.getObject("linked_fact_id", UUID.class),
                        rs.getObject("linked_defense_id", UUID.class),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                        rs.getObject("created_by", UUID.class), rs.getString("display_name")))
                .list().forEach(reply -> out.computeIfAbsent(reply.requestId(), k -> new ArrayList<>()).add(reply));
        return out;
    }

    Map<UUID, List<InfoRequestRequests.FollowUp>> followUps(Collection<UUID> requestIds) {
        Map<UUID, List<InfoRequestRequests.FollowUp>> out = new HashMap<>();
        if (requestIds.isEmpty()) {
            return out;
        }
        jdbc.sql("""
                        select f.id, f.request_id, f.kind, f.on_date, f.note, f.escalated_to, t.display_name as target_name,
                               f.created_at, f.created_by, u.display_name
                        from info_follow_up f left join app_user t on t.id = f.escalated_to
                        left join app_user u on u.id = f.created_by
                        where f.request_id in (:ids) order by f.on_date, f.created_at, f.id""")
                .param("ids", requestIds)
                .query((rs, n) -> new InfoRequestRequests.FollowUp(rs.getObject("id", UUID.class),
                        rs.getObject("request_id", UUID.class), rs.getString("kind"),
                        rs.getObject("on_date", LocalDate.class), rs.getString("note"),
                        rs.getObject("escalated_to", UUID.class), rs.getString("target_name"),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                        rs.getObject("created_by", UUID.class), rs.getString("display_name")))
                .list().forEach(f -> out.computeIfAbsent(f.requestId(), k -> new ArrayList<>()).add(f));
        return out;
    }

    /** The reply with this id, for the answer to a reply just stored. */
    Optional<InfoRequestRequests.Reply> reply(UUID requestId, UUID replyId) {
        return replies(List.of(requestId)).getOrDefault(requestId, List.of()).stream()
                .filter(r -> r.id().equals(replyId)).findFirst();
    }

    Optional<InfoRequestRequests.FollowUp> followUp(UUID requestId, UUID followUpId) {
        return followUps(List.of(requestId)).getOrDefault(requestId, List.of()).stream()
                .filter(f -> f.id().equals(followUpId)).findFirst();
    }

    /** The name of an authority unit with its parents ("sector / department / section"), if it exists in that tree. */
    Optional<String> authorityUnitPath(UUID unitId) {
        return jdbc.sql("""
                        with recursive chain (id, parent_id, name_ar, tree, depth) as (
                            select id, parent_id, name_ar, tree, 0 from org_unit where id = :id
                            union all
                            select o.id, o.parent_id, o.name_ar, o.tree, chain.depth + 1
                            from org_unit o join chain on o.id = chain.parent_id)
                        select (select tree from chain where depth = 0) as tree,
                               string_agg(name_ar, ' / ' order by depth desc) as path
                        from chain""")
                .param("id", unitId)
                .query((rs, n) -> "AUTHORITY".equals(rs.getString("tree")) ? Optional.of(rs.getString("path"))
                        : Optional.<String>empty())
                .single();
    }

    boolean userExists(UUID userId) {
        return jdbc.sql("select exists (select 1 from app_user where id = :id and enabled)").param("id", userId)
                .query(Boolean.class).single();
    }

    /** The report: one row per unit over the requests of the cases the signed-in user may see. */
    List<InfoRequestRequests.UnitReport> report(CaseAccessPolicy.Predicate visible, LocalDate today) {
        Map<String, Object> params = new HashMap<>(visible.params());
        return jdbc.sql("""
                        select r.unit_id, coalesce(max(r.unit_name_snapshot), '') as unit_name,
                               count(distinct r.id) as requests,
                               count(p.id) as replies,
                               count(distinct r.id) filter (where p.id is not null) as answered,
                               count(distinct r.id) filter (where r.due_on is not null and r.due_on < :today
                                                              and r.status in %s) as overdue,
                               count(p.id) filter (where p.completeness = 'INCOMPLETE') as incomplete,
                               count(distinct r.case_id) filter (where r.due_on is not null and r.due_on < :today
                                                                   and r.status in %s) as affected,
                               avg((select min(f.received_on) from info_reply f where f.request_id = r.id) - r.sent_on)
                                   filter (where r.sent_on is not null
                                           and exists (select 1 from info_reply f where f.request_id = r.id)) as avg_days
                        from info_request r left join info_reply p on p.request_id = r.id
                        where %s
                        group by r.unit_id order by unit_name, r.unit_id""".formatted(WAITING, WAITING, visible.sql()))
                .params(params).param("today", today)
                .query((rs, n) -> new InfoRequestRequests.UnitReport(rs.getObject("unit_id", UUID.class),
                        rs.getString("unit_name"), rs.getLong("requests"), rs.getLong("replies"), rs.getLong("answered"),
                        rs.getLong("overdue"), rs.getLong("incomplete"), rs.getLong("affected"),
                        rs.getObject("avg_days") == null ? null : rs.getDouble("avg_days")))
                .list();
    }

    private static InfoRequestRequests.Response row(ResultSet rs) throws SQLException {
        return new InfoRequestRequests.Response(rs.getObject("id", UUID.class), rs.getObject("case_id", UUID.class),
                rs.getString("reference_no"), rs.getString("case_title"), rs.getString("request_no"),
                rs.getObject("unit_id", UUID.class), rs.getString("unit_name_snapshot"), rs.getString("subject"),
                rs.getString("details"), rs.getString("required_documents"), rs.getObject("drafted_on", LocalDate.class),
                rs.getObject("sent_on", LocalDate.class), rs.getString("letter_no"),
                rs.getObject("letter_date", LocalDate.class), rs.getObject("due_on", LocalDate.class),
                rs.getObject("responsible_member_id", UUID.class), rs.getString("responsible_name"),
                rs.getString("addressee"), rs.getString("notes"), rs.getString("status"), rs.getString("delay_reason"),
                rs.getString("close_reason"), rs.getObject("closed_on", LocalDate.class), rs.getBoolean("overdue"),
                rs.getObject("linked_fact_id", UUID.class), rs.getObject("linked_defense_id", UUID.class),
                rs.getInt("reply_count"), List.of(), List.of(), rs.getLong("version"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getObject("updated_at", OffsetDateTime.class).toInstant());
    }
}
