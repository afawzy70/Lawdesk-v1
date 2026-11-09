package com.lawdesk.risk;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.api.BusinessRuleException;
import com.lawdesk.api.ConflictException;
import com.lawdesk.api.FieldErrorException;
import com.lawdesk.api.NotFoundException;
import com.lawdesk.cases.CaseAccessPolicy;
import com.lawdesk.cases.CaseLookup;
import com.lawdesk.cases.CaseSummary;
import com.lawdesk.notifications.NotificationPublisher;
import com.lawdesk.security.ActorRef;
import com.lawdesk.security.CurrentActor;

/**
 * Legal risk assessment (LD-053, requirements section 19, docs/design/LD-DOC-05 section 6). A version is written once and
 * never edited; a high or very high level needs a remediation plan and raises an in-app alert. Reads follow the case
 * visibility of D3, writes need the right to change the case.
 */
@Service
class RiskService {

    static final String REMEDIATION_REQUIRED = "REMEDIATION_REQUIRED";
    static final String CASE_ARCHIVED = "CASE_ARCHIVED";

    private final JdbcClient jdbc;
    private final CaseLookup cases;
    private final CaseAccessPolicy access;
    private final CurrentActor currentActor;
    private final NotificationPublisher notifications;

    RiskService(JdbcClient jdbc, CaseLookup cases, CaseAccessPolicy access, CurrentActor currentActor,
            NotificationPublisher notifications) {
        this.jdbc = jdbc;
        this.cases = cases;
        this.access = access;
        this.currentActor = currentActor;
        this.notifications = notifications;
    }

    // ---- reads -------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    List<RiskRequests.Assessment> forCase(UUID caseId) {
        access.requireView(caseId);
        return assessments("a.case_id = :caseId", Map.of("caseId", caseId), "order by a.version_no desc");
    }

    @Transactional(readOnly = true)
    RiskRequests.Assessment get(UUID id) {
        UUID caseId = caseOf(id);
        access.requireView(caseId);
        return assessments("a.id = :id", Map.of("id", id), "").get(0);
    }

    @Transactional(readOnly = true)
    RiskRequests.HighRiskReport high(Pageable pageable) {
        CaseAccessPolicy.Predicate visible = access.visibleCaseIds("a.case_id");
        String where = """
                a.level in ('HIGH', 'VERY_HIGH') and a.version_no = (select max(m.version_no) from risk_assessment m
                where m.case_id = a.case_id) and """ + " " + visible.sql();
        long total = jdbc.sql("select count(*) from risk_assessment a where " + where).params(visible.params())
                .query(Long.class).single();
        List<RiskRequests.HighRisk> items = jdbc.sql("""
                        select a.case_id, c.reference_no, c.title, a.id, a.version_no, a.level, a.level_reasons,
                               a.potential_loss, a.assessed_at,
                               (select count(*) from risk_remediation r where r.assessment_id = a.id
                                  and r.status in ('OPEN', 'IN_PROGRESS')) as open_count,
                               (select count(*) from risk_remediation r where r.assessment_id = a.id) as total_count
                        from risk_assessment a join legal_case c on c.id = a.case_id where """ + " " + where
                        + " order by a.potential_loss desc nulls last, a.assessed_at desc, a.id limit :limit offset :offset")
                .params(visible.params()).param("limit", pageable.getPageSize()).param("offset", pageable.getOffset())
                .query((rs, n) -> new RiskRequests.HighRisk(rs.getObject("case_id", UUID.class),
                        rs.getString("reference_no"), rs.getString("title"), rs.getObject("id", UUID.class),
                        rs.getInt("version_no"), rs.getString("level"), rs.getString("level_reasons"),
                        rs.getBigDecimal("potential_loss"),
                        rs.getObject("assessed_at", OffsetDateTime.class).toInstant(), rs.getLong("open_count"),
                        rs.getLong("total_count")))
                .list();
        int size = pageable.getPageSize();
        return new RiskRequests.HighRiskReport(RiskRequests.NOTICE, items, pageable.getPageNumber(), size, total,
                (int) ((total + size - 1) / size));
    }

    // ---- writes ------------------------------------------------------------------------------------------------

    @Transactional
    RiskRequests.Assessment assess(UUID caseId, RiskRequests.Assess request) {
        ActorRef actor = currentActor.require();
        writableCase(caseId);
        Map<RiskRequests.ElementCode, RiskRequests.ElementInput> byCode = new EnumMap<>(RiskRequests.ElementCode.class);
        for (RiskRequests.ElementInput element : request.elements()) {
            if (byCode.put(element.code(), element) != null) {
                throw new FieldErrorException("elements", "Unique");
            }
        }
        if (request.level().high() && request.remediation().isEmpty()) {
            throw new BusinessRuleException(REMEDIATION_REQUIRED, "a high risk needs a remediation plan");
        }
        for (RiskRequests.RemediationInput plan : request.remediation()) {
            requireUser("remediation.ownerId", plan.ownerId());
        }
        // Serialises the numbering of versions of one case.
        jdbc.sql("select id from legal_case where id = :id for update").param("id", caseId).query(UUID.class).single();
        int versionNo = jdbc.sql("select coalesce(max(version_no), 0) + 1 from risk_assessment where case_id = :id")
                .param("id", caseId).query(Integer.class).single();
        UUID id;
        try {
            id = jdbc.sql("""
                            insert into risk_assessment (case_id, version_no, level, level_reasons, potential_loss,
                                trigger_kind, trigger_note, trigger_ref_id, assessed_by)
                            values (:caseId, :version, :level, :reasons, :loss, :trigger, :note, :ref, :actor)
                            returning id""")
                    .param("caseId", caseId).param("version", versionNo).param("level", request.level().name())
                    .param("reasons", request.levelReasons()).param("loss", request.potentialLoss())
                    .param("trigger", request.trigger().name()).param("note", request.triggerNote())
                    .param("ref", request.triggerRefId()).param("actor", actor.id()).query(UUID.class).single();
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("another assessment was recorded at the same time");
        }
        for (RiskRequests.ElementCode code : RiskRequests.ElementCode.values()) {
            RiskRequests.ElementInput element = byCode.get(code);
            jdbc.sql("insert into risk_assessment_element (assessment_id, code, rating, reason) values (:id, :code, :rating, :reason)")
                    .param("id", id).param("code", code.name())
                    .param("rating", element == null ? RiskRequests.Rating.NOT_ASSESSED.name() : element.rating().name())
                    .param("reason", element == null ? null : element.reason()).update();
        }
        for (RiskRequests.RemediationInput plan : request.remediation()) {
            insertRemediation(id, caseId, plan, actor.id());
        }
        cases.recordEvent(caseId, "RISK_ASSESSED", "Risk assessment version %d: %s".formatted(versionNo, request.level().name()));
        if (request.level().high()) {
            alert(caseId, id, actor.id(), request.level());
        }
        return assessments("a.id = :id", Map.of("id", id), "").get(0);
    }

    @Transactional
    RiskRequests.Remediation addRemediation(UUID assessmentId, RiskRequests.RemediationInput input) {
        ActorRef actor = currentActor.require();
        UUID caseId = caseOf(assessmentId);
        access.requireView(caseId);
        writableCase(caseId);
        requireUser("ownerId", input.ownerId());
        UUID id = insertRemediation(assessmentId, caseId, input, actor.id());
        cases.recordEvent(caseId, "RISK_REMEDIATION_ADDED", "Remediation step added: " + input.action());
        return remediation(id);
    }

    @Transactional
    RiskRequests.Remediation updateRemediation(UUID id, RiskRequests.RemediationUpdate request) {
        ActorRef actor = currentActor.require();
        UUID caseId = jdbc.sql("select case_id from risk_remediation where id = :id").param("id", id).query(UUID.class)
                .optional().orElseThrow(() -> new NotFoundException("remediation"));
        access.requireView(caseId);
        writableCase(caseId);
        requireUser("ownerId", request.ownerId());
        int updated = jdbc.sql("""
                        update risk_remediation set cause = :cause, action = :action, documents = :documents,
                            defenses = :defenses, unit_to_address = :unit, owner_id = :owner, target_date = :target,
                            status = :status, version = version + 1, updated_at = now(), updated_by = :actor
                        where id = :id and version = :version""")
                .param("id", id).param("version", request.version()).param("cause", request.cause())
                .param("action", request.action()).param("documents", request.documents())
                .param("defenses", request.defenses()).param("unit", request.unitToAddress())
                .param("owner", request.ownerId()).param("target", request.targetDate())
                .param("status", request.status().name()).param("actor", actor.id()).update();
        if (updated != 1) {
            throw new ConflictException("the remediation step was changed by someone else");
        }
        cases.recordEvent(caseId, "RISK_REMEDIATION_UPDATED", "Remediation step is now " + request.status().name());
        return remediation(id);
    }

    // ---- helpers -----------------------------------------------------------------------------------------------

    private UUID insertRemediation(UUID assessmentId, UUID caseId, RiskRequests.RemediationInput plan, UUID actor) {
        return jdbc.sql("""
                        insert into risk_remediation (assessment_id, case_id, cause, action, documents, defenses,
                            unit_to_address, owner_id, target_date, created_by, updated_by)
                        values (:assessment, :caseId, :cause, :action, :documents, :defenses, :unit, :owner, :target, :actor, :actor)
                        returning id""")
                .param("assessment", assessmentId).param("caseId", caseId).param("cause", plan.cause())
                .param("action", plan.action()).param("documents", plan.documents()).param("defenses", plan.defenses())
                .param("unit", plan.unitToAddress()).param("owner", plan.ownerId()).param("target", plan.targetDate())
                .param("actor", actor).query(UUID.class).single();
    }

    /** The alert of a high risk: the case's specialist and alternate, and whoever assessed it. */
    private void alert(UUID caseId, UUID assessmentId, UUID assessor, RiskRequests.Level level) {
        record Members(UUID primary, UUID alternate, String reference) {
        }
        Members members = jdbc.sql("select primary_member_id, alternate_member_id, reference_no from legal_case where id = :id")
                .param("id", caseId)
                .query((rs, n) -> new Members(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3)))
                .single();
        Set<UUID> users = new LinkedHashSet<>();
        users.add(members.primary());
        users.add(members.alternate());
        users.add(assessor);
        users.remove(null);
        for (UUID user : users) {
            notifications.publish(user, NotificationPublisher.RISK_HIGH, caseId, "RISK_ASSESSMENT:" + assessmentId,
                    Map.of("caseReferenceNo", members.reference(), "level", level.name()));
        }
    }

    private UUID caseOf(UUID assessmentId) {
        return jdbc.sql("select case_id from risk_assessment where id = :id").param("id", assessmentId).query(UUID.class)
                .optional().orElseThrow(() -> new NotFoundException("risk assessment"));
    }

    private void writableCase(UUID caseId) {
        CaseSummary legalCase = cases.find(caseId).orElseThrow(() -> new NotFoundException("case"));
        access.requireChange(caseId);
        if ("ARCHIVED".equals(legalCase.status())) {
            throw new BusinessRuleException(CASE_ARCHIVED, "an archived case is read-only");
        }
    }

    private void requireUser(String field, UUID userId) {
        if (userId != null && !jdbc.sql("select exists (select 1 from app_user where id = :id and enabled)")
                .param("id", userId).query(Boolean.class).single()) {
            throw new FieldErrorException(field, "Invalid");
        }
    }

    private RiskRequests.Remediation remediation(UUID id) {
        return remediations("r.id = :id", Map.of("id", id)).get(0);
    }

    private List<RiskRequests.Assessment> assessments(String where, Map<String, Object> params, String order) {
        List<RiskRequests.Assessment> heads = jdbc.sql("""
                        select a.id, a.case_id, a.version_no, a.level, a.level_reasons, a.potential_loss, a.trigger_kind,
                               a.trigger_note, a.trigger_ref_id, a.assessed_by, u.display_name, a.assessed_at
                        from risk_assessment a left join app_user u on u.id = a.assessed_by where """ + " " + where
                        + " " + order)
                .params(params)
                .query((rs, n) -> new RiskRequests.Assessment(rs.getObject("id", UUID.class),
                        rs.getObject("case_id", UUID.class), rs.getInt("version_no"), rs.getString("level"),
                        rs.getString("level_reasons"), rs.getBigDecimal("potential_loss"), rs.getString("trigger_kind"),
                        rs.getString("trigger_note"), rs.getObject("trigger_ref_id", UUID.class),
                        rs.getObject("assessed_by", UUID.class), rs.getString("display_name"),
                        rs.getObject("assessed_at", OffsetDateTime.class).toInstant(), List.of(), List.of()))
                .list();
        if (heads.isEmpty()) {
            return heads;
        }
        List<UUID> ids = heads.stream().map(RiskRequests.Assessment::id).toList();
        Map<UUID, List<RiskRequests.Element>> elements = new HashMap<>();
        jdbc.sql("select assessment_id, code, rating, reason from risk_assessment_element where assessment_id in (:ids)")
                .param("ids", ids)
                .query((rs, n) -> Map.entry(rs.getObject("assessment_id", UUID.class),
                        new RiskRequests.Element(rs.getString("code"), rs.getString("rating"), rs.getString("reason"))))
                .list().forEach(e -> elements.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(e.getValue()));
        Map<UUID, List<RiskRequests.Remediation>> plans = new HashMap<>();
        remediations("r.assessment_id in (:ids)", Map.of("ids", ids))
                .forEach(r -> plans.computeIfAbsent(r.assessmentId(), k -> new ArrayList<>()).add(r));
        return heads.stream().map(a -> new RiskRequests.Assessment(a.id(), a.caseId(), a.versionNo(), a.level(),
                a.levelReasons(), a.potentialLoss(), a.trigger(), a.triggerNote(), a.triggerRefId(), a.assessedBy(),
                a.assessedByName(), a.assessedAt(), sorted(elements.getOrDefault(a.id(), List.of())),
                plans.getOrDefault(a.id(), List.of()))).toList();
    }

    private static List<RiskRequests.Element> sorted(List<RiskRequests.Element> elements) {
        return elements.stream().sorted(java.util.Comparator.comparingInt(
                e -> RiskRequests.ElementCode.valueOf(e.code()).ordinal())).toList();
    }

    private List<RiskRequests.Remediation> remediations(String where, Map<String, Object> params) {
        return jdbc.sql("""
                        select r.id, r.assessment_id, r.case_id, r.cause, r.action, r.documents, r.defenses,
                               r.unit_to_address, r.owner_id, o.display_name, r.target_date, r.status, r.version,
                               r.created_at, r.updated_at
                        from risk_remediation r left join app_user o on o.id = r.owner_id where """ + " " + where
                        + " order by r.created_at, r.id")
                .params(params)
                .query((rs, n) -> new RiskRequests.Remediation(rs.getObject("id", UUID.class),
                        rs.getObject("assessment_id", UUID.class), rs.getObject("case_id", UUID.class),
                        rs.getString("cause"), rs.getString("action"), rs.getString("documents"), rs.getString("defenses"),
                        rs.getString("unit_to_address"), rs.getObject("owner_id", UUID.class), rs.getString("display_name"),
                        rs.getObject("target_date", java.time.LocalDate.class), rs.getString("status"), rs.getLong("version"),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                        rs.getObject("updated_at", OffsetDateTime.class).toInstant()))
                .list();
    }
}
