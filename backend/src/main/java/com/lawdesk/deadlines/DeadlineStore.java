package com.lawdesk.deadlines;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.lawdesk.cases.CaseAccessPolicy;

import tools.jackson.databind.json.JsonMapper;

/** SQL of the deadline register (V681__LD-054_deadline.sql). */
@Repository
class DeadlineStore {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static final String SELECT = """
            select d.id, d.case_id, c.reference_no, c.title as case_title, d.source_type, d.source_id, d.source_text,
                   d.source_label, d.starts_on, d.period_days, d.due_on, d.rule_id, r.code as rule_code,
                   d.calculation::text as calculation, d.action, d.owner_id, o.display_name as owner_name, d.status,
                   d.completed_on, d.proof_document_id, doc.title as proof_title, d.notes, d.calc_status,
                   d.approved_by, a.display_name as approved_name, d.approved_at, d.change_reason, d.version,
                   d.created_at, d.updated_at
            from deadline d
            join legal_case c on c.id = d.case_id
            left join deadline_rule r on r.id = d.rule_id
            left join app_user o on o.id = d.owner_id
            left join app_user a on a.id = d.approved_by
            left join case_document doc on doc.id = d.proof_document_id""";

    private final JdbcClient jdbc;

    DeadlineStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** The row's case, state and version, without the joins. */
    record Head(UUID id, UUID caseId, String status, String calcStatus, LocalDate startsOn, Integer periodDays,
            LocalDate dueOn, UUID ruleId, String action, UUID ownerId, long version) {
    }

    Optional<Head> head(UUID id, boolean lock) {
        return jdbc.sql("select id, case_id, status, calc_status, starts_on, period_days, due_on, rule_id, action,"
                        + " owner_id, version from deadline where id = :id" + (lock ? " for update" : ""))
                .param("id", id)
                .query((rs, n) -> new Head(rs.getObject("id", UUID.class), rs.getObject("case_id", UUID.class),
                        rs.getString("status"), rs.getString("calc_status"), rs.getObject("starts_on", LocalDate.class),
                        (Integer) rs.getObject("period_days"), rs.getObject("due_on", LocalDate.class),
                        rs.getObject("rule_id", UUID.class), rs.getString("action"),
                        rs.getObject("owner_id", UUID.class), rs.getLong("version")))
                .optional();
    }

    Optional<DeadlineRequests.Response> find(UUID id, LocalDate today) {
        return jdbc.sql(SELECT + " where d.id = :id").param("id", id).query((rs, n) -> row(rs, today)).optional();
    }

    /** Filters of the register; null values do not filter. */
    record Filter(String status, String calcStatus, UUID ownerId, LocalDate from, LocalDate to, Boolean overdue,
            UUID caseId, String sourceType, UUID sourceId) {
    }

    private String where(Filter filter, CaseAccessPolicy.Predicate visible, LocalDate today, Map<String, Object> params) {
        params.putAll(visible.params());
        params.put("today", today);
        StringBuilder sql = new StringBuilder(" where " + visible.sql());
        if (filter.status() != null) {
            sql.append(" and d.status = :status");
            params.put("status", filter.status());
        }
        if (filter.calcStatus() != null) {
            sql.append(" and d.calc_status = :calcStatus");
            params.put("calcStatus", filter.calcStatus());
        }
        if (filter.ownerId() != null) {
            sql.append(" and d.owner_id = :ownerId");
            params.put("ownerId", filter.ownerId());
        }
        if (filter.from() != null) {
            sql.append(" and d.due_on >= :from");
            params.put("from", filter.from());
        }
        if (filter.to() != null) {
            sql.append(" and d.due_on <= :to");
            params.put("to", filter.to());
        }
        if (Boolean.TRUE.equals(filter.overdue())) {
            sql.append(" and d.status = 'OPEN' and d.due_on is not null and d.due_on < :today");
        } else if (Boolean.FALSE.equals(filter.overdue())) {
            sql.append(" and not (d.status = 'OPEN' and d.due_on is not null and d.due_on < :today)");
        }
        if (filter.caseId() != null) {
            sql.append(" and d.case_id = :caseId");
            params.put("caseId", filter.caseId());
        }
        if (filter.sourceType() != null) {
            sql.append(" and d.source_type = :sourceType");
            params.put("sourceType", filter.sourceType());
        }
        if (filter.sourceId() != null) {
            sql.append(" and d.source_id = :sourceId");
            params.put("sourceId", filter.sourceId());
        }
        return sql.toString();
    }

    List<DeadlineRequests.Response> list(Filter filter, CaseAccessPolicy.Predicate visible, LocalDate today, int limit,
            long offset) {
        Map<String, Object> params = new HashMap<>();
        String where = where(filter, visible, today, params);
        return jdbc.sql(SELECT + where + " order by d.due_on nulls last, d.created_at, d.id limit :limit offset :offset")
                .params(params).param("limit", limit).param("offset", offset)
                .query((rs, n) -> row(rs, today)).list();
    }

    long count(Filter filter, CaseAccessPolicy.Predicate visible, LocalDate today) {
        Map<String, Object> params = new HashMap<>();
        String where = where(filter, visible, today, params);
        return jdbc.sql("select count(*) from deadline d" + where).params(params).query(Long.class).single();
    }

    // ---- sources -------------------------------------------------------------------------------------------------

    /** The neutral label of the source when it belongs to the case, empty otherwise. */
    Optional<String> sourceLabel(String type, UUID id, UUID caseId) {
        String sql = switch (type) {
            case "JUDGMENT" -> "select j.judgment_date::text from judgment j join proceeding p on p.id = j.proceeding_id"
                    + " where j.id = :id and p.case_id = :caseId";
            case "HEARING" -> "select h.hearing_date::text from hearing h where h.id = :id and h.case_id = :caseId";
            case "PROCEEDING" -> "select p.court_case_number || '/' || p.court_case_year from proceeding p"
                    + " where p.id = :id and p.case_id = :caseId";
            case "TASK" -> "select t.title from task t where t.id = :id and t.case_id = :caseId";
            default -> throw new IllegalArgumentException(type);
        };
        return jdbc.sql(sql).param("id", id).param("caseId", caseId).query(String.class).optional();
    }

    Optional<UUID> caseOfJudgment(UUID judgmentId) {
        return jdbc.sql("select p.case_id from judgment j join proceeding p on p.id = j.proceeding_id where j.id = :id")
                .param("id", judgmentId).query(UUID.class).optional();
    }

    boolean userExists(UUID userId) {
        return jdbc.sql("select count(*) from app_user where id = :id").param("id", userId).query(Long.class).single() > 0;
    }

    // ---- writes --------------------------------------------------------------------------------------------------

    record NewDeadline(UUID caseId, String sourceType, UUID sourceId, String sourceText, String sourceLabel,
            LocalDate startsOn, Integer periodDays, LocalDate dueOn, UUID ruleId, DeadlineCalculation calculation,
            String action, UUID ownerId, String notes, String calcStatus) {
    }

    UUID insert(NewDeadline d, UUID actor) {
        return jdbc.sql("""
                        insert into deadline (case_id, source_type, source_id, source_text, source_label, starts_on,
                            period_days, due_on, rule_id, calculation, action, owner_id, notes, calc_status, created_by,
                            updated_by)
                        values (:caseId, :sourceType, :sourceId, :sourceText, :sourceLabel, :startsOn, :periodDays,
                            :dueOn, :ruleId, cast(:calculation as jsonb), :action, :ownerId, :notes, :calcStatus,
                            :actor, :actor)
                        returning id""")
                .param("caseId", d.caseId()).param("sourceType", d.sourceType()).param("sourceId", d.sourceId())
                .param("sourceText", d.sourceText()).param("sourceLabel", d.sourceLabel()).param("startsOn", d.startsOn())
                .param("periodDays", d.periodDays()).param("dueOn", d.dueOn()).param("ruleId", d.ruleId())
                .param("calculation", d.calculation() == null ? null : MAPPER.writeValueAsString(d.calculation()))
                .param("action", d.action()).param("ownerId", d.ownerId()).param("notes", d.notes())
                .param("calcStatus", d.calcStatus()).param("actor", actor).query(UUID.class).single();
    }

    /** The edited fields; the rule and the calculation are kept only when {@code keepCalculation}. */
    boolean update(UUID id, long version, LocalDate startsOn, Integer periodDays, LocalDate dueOn, String action,
            UUID ownerId, String notes, String calcStatus, boolean keepCalculation, boolean keepApproval,
            String changeReason, UUID actor) {
        return jdbc.sql("""
                        update deadline set starts_on = :startsOn, period_days = :periodDays, due_on = :dueOn,
                            action = :action, owner_id = :ownerId, notes = :notes, calc_status = :calcStatus,
                            rule_id = case when :keepCalculation then rule_id end,
                            calculation = case when :keepCalculation then calculation end,
                            approved_by = case when :keepApproval then approved_by end,
                            approved_at = case when :keepApproval then approved_at end,
                            change_reason = coalesce(:changeReason, change_reason),
                            version = version + 1, updated_at = now(), updated_by = :actor
                        where id = :id and version = :version""")
                .param("id", id).param("version", version).param("startsOn", startsOn).param("periodDays", periodDays)
                .param("dueOn", dueOn).param("action", action).param("ownerId", ownerId).param("notes", notes)
                .param("calcStatus", calcStatus).param("keepCalculation", keepCalculation)
                .param("keepApproval", keepApproval).param("changeReason", changeReason).param("actor", actor)
                .update() == 1;
    }

    boolean approve(UUID id, long version, UUID actor) {
        return jdbc.sql("""
                        update deadline set calc_status = 'APPROVED', approved_by = :actor, approved_at = now(),
                            version = version + 1, updated_at = now(), updated_by = :actor
                        where id = :id and version = :version""")
                .param("id", id).param("version", version).param("actor", actor).update() == 1;
    }

    boolean setStatus(UUID id, long version, String status, LocalDate completedOn, UUID proofDocumentId, UUID actor) {
        return jdbc.sql("""
                        update deadline set status = :status, completed_on = :completedOn,
                            proof_document_id = :proof, version = version + 1, updated_at = now(), updated_by = :actor
                        where id = :id and version = :version""")
                .param("id", id).param("version", version).param("status", status).param("completedOn", completedOn)
                .param("proof", proofDocumentId).param("actor", actor).update() == 1;
    }

    // ---- row -----------------------------------------------------------------------------------------------------

    private static DeadlineRequests.Response row(ResultSet rs, LocalDate today) throws SQLException {
        LocalDate dueOn = rs.getObject("due_on", LocalDate.class);
        String status = rs.getString("status");
        String calcStatus = rs.getString("calc_status");
        boolean open = status.equals("OPEN");
        String calculation = rs.getString("calculation");
        OffsetDateTime approvedAt = rs.getObject("approved_at", OffsetDateTime.class);
        return new DeadlineRequests.Response(rs.getObject("id", UUID.class), rs.getObject("case_id", UUID.class),
                rs.getString("reference_no"), rs.getString("case_title"), rs.getString("source_type"),
                rs.getObject("source_id", UUID.class), rs.getString("source_text"), rs.getString("source_label"),
                rs.getObject("starts_on", LocalDate.class), (Integer) rs.getObject("period_days"), dueOn,
                rs.getObject("rule_id", UUID.class), rs.getString("rule_code"),
                calculation == null ? null : MAPPER.readValue(calculation, DeadlineCalculation.class),
                rs.getString("action"), rs.getObject("owner_id", UUID.class), rs.getString("owner_name"), status,
                rs.getObject("completed_on", LocalDate.class), rs.getObject("proof_document_id", UUID.class),
                rs.getString("proof_title"), rs.getString("notes"), calcStatus, calcStatus.equals("APPROVED"),
                rs.getObject("approved_by", UUID.class), rs.getString("approved_name"),
                approvedAt == null ? null : approvedAt.toInstant(), rs.getString("change_reason"),
                open && dueOn != null && dueOn.isBefore(today),
                dueOn == null ? null : (int) ChronoUnit.DAYS.between(today, dueOn), rs.getLong("version"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getObject("updated_at", OffsetDateTime.class).toInstant());
    }
}
