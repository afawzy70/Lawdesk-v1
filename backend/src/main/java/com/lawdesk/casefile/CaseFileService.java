package com.lawdesk.casefile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.api.FieldErrorException;
import com.lawdesk.cases.CaseAccessPolicy;
import com.lawdesk.cases.CaseLookup;
import com.lawdesk.cases.MemberRef;
import com.lawdesk.security.CurrentActor;
import com.lawdesk.security.Permission;

/**
 * The executive summary (requirements section 21.1) and the unified timeline (21.2, 21.3) of one case, read from the stored
 * records of every module the user may open. Both need only that the user can see the case; a section or entry of an area
 * the user holds no permission for is left out rather than refused. Nothing is generated and nothing is stored.
 */
@Service
class CaseFileService {

    private static final ZoneId ZONE = ZoneId.of("Africa/Cairo");
    private static final Set<String> KINDS = Set.of("EVENT", "HEARING", "INFO_REQUEST", "INFO_REQUEST_DUE", "INFO_REPLY",
            "RISK", "JUDGMENT", "LINK", "DEADLINE");
    private static final List<String> PENDING_MODULES = List.of("CLAIMS", "DEFENSES", "EVIDENCE", "ENFORCEMENT");
    private static final String WAITING = "('SENT', 'AWAITING', 'PARTIAL', 'INCOMPLETE', 'OVERDUE')";

    private final JdbcClient jdbc;
    private final CaseAccessPolicy access;
    private final CaseLookup cases;
    private final CurrentActor currentActor;

    CaseFileService(JdbcClient jdbc, CaseAccessPolicy access, CaseLookup cases, CurrentActor currentActor) {
        this.jdbc = jdbc;
        this.access = access;
        this.cases = cases;
        this.currentActor = currentActor;
    }

    private static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    // ---- the summary -------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    CaseFileViews.Summary summary(UUID caseId) {
        access.requireView(caseId);
        LocalDate today = today();
        record Head(String referenceNo, String automatedNo, String title, String subject, String caseType, String status,
                String position, BigDecimal claimed, MemberRef primary, MemberRef alternate) {
        }
        Head head = jdbc.sql("""
                        select c.reference_no, c.automated_no, c.title, c.subject, c.case_type, c.status, c.authority_position,
                               c.claimed_amount, c.primary_member_id, p.display_name as primary_name, c.alternate_member_id,
                               a.display_name as alternate_name
                        from legal_case c left join app_user p on p.id = c.primary_member_id
                        left join app_user a on a.id = c.alternate_member_id where c.id = :id""")
                .param("id", caseId)
                .query((rs, n) -> new Head(rs.getString("reference_no"), rs.getString("automated_no"), rs.getString("title"),
                        rs.getString("subject"), rs.getString("case_type"), rs.getString("status"),
                        rs.getString("authority_position"), rs.getBigDecimal("claimed_amount"),
                        member(rs.getObject("primary_member_id", UUID.class), rs.getString("primary_name")),
                        member(rs.getObject("alternate_member_id", UUID.class), rs.getString("alternate_name"))))
                .single();
        List<CaseFileViews.Party> parties = jdbc.sql("""
                        select p.name, cp.role from case_party cp join party p on p.id = cp.party_id
                        where cp.case_id = :id order by cp.role, p.name""").param("id", caseId)
                .query((rs, n) -> new CaseFileViews.Party(rs.getString("name"), rs.getString("role"))).list();
        List<CaseFileViews.Unit> units = jdbc.sql("""
                        select concat_ws(' / ', sector_name_snapshot, department_name_snapshot, section_name_snapshot) as name,
                               is_primary from case_org_involvement where case_id = :id and removed_at is null
                        order by is_primary desc, created_at""").param("id", caseId)
                .query((rs, n) -> new CaseFileViews.Unit(rs.getString("name"), rs.getBoolean("is_primary"))).list();
        CaseFileViews.Procedure last = jdbc.sql("select type, summary, occurred_at from case_event where case_id = :id order by seq desc limit 1")
                .param("id", caseId)
                .query((rs, n) -> new CaseFileViews.Procedure(rs.getString("type"), rs.getString("summary"),
                        rs.getObject("occurred_at", OffsetDateTime.class).toInstant()))
                .optional().orElse(null);
        return new CaseFileViews.Summary(caseId, head.referenceNo(), head.automatedNo(), head.title(), head.subject(),
                head.caseType(), head.status(), head.position(), head.claimed(), head.primary(), head.alternate(), parties,
                units, last,
                currentActor.has(Permission.HEARING_VIEW) ? hearings(caseId, today) : null,
                currentActor.has(Permission.JUDGMENT_VIEW) ? lastJudgment(caseId) : null,
                currentActor.has(Permission.RISK_VIEW) ? risk(caseId) : null,
                currentActor.has(Permission.INFO_REQUEST_VIEW) ? infoRequests(caseId, today) : null,
                currentActor.has(Permission.TASK_VIEW) ? tasks(caseId, today) : null, links(caseId), PENDING_MODULES,
                currentActor.has(Permission.DEADLINE_VIEW) ? deadlines(caseId, today) : null);
    }

    private static MemberRef member(UUID id, String name) {
        return id == null ? null : new MemberRef(id, name);
    }

    private CaseFileViews.Hearings hearings(UUID caseId, LocalDate today) {
        Map<String, Long> counts = new HashMap<>();
        long[] followUp = new long[1];
        long[] awaiting = new long[1];
        jdbc.sql("""
                        select status, count(*) as n, count(*) filter (where follow_up_flag) as follow_up,
                               count(*) filter (where status = 'SCHEDULED' and hearing_date < :today) as awaiting
                        from hearing where case_id = :id group by status""").param("id", caseId).param("today", today)
                .query((rs, n) -> {
                    counts.put(rs.getString("status"), rs.getLong("n"));
                    followUp[0] += rs.getLong("follow_up");
                    awaiting[0] += rs.getLong("awaiting");
                    return 0;
                }).list();
        String select = """
                select id, hearing_date, hearing_time, status, purpose, kind, decision, adjournment_reason, no_next_date
                from hearing where case_id = :id and """;
        CaseFileViews.HearingBrief last = jdbc.sql(select + " status in ('HELD', 'ADJOURNED') order by hearing_date desc, hearing_time desc nulls last, id limit 1")
                .param("id", caseId).query((rs, n) -> brief(rs)).optional().orElse(null);
        CaseFileViews.HearingBrief next = jdbc.sql(select + " status = 'SCHEDULED' and hearing_date >= :today order by hearing_date, hearing_time nulls first, id limit 1")
                .param("id", caseId).param("today", today).query((rs, n) -> brief(rs)).optional().orElse(null);
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        return new CaseFileViews.Hearings(total, counts.getOrDefault("HELD", 0L), counts.getOrDefault("ADJOURNED", 0L),
                counts.getOrDefault("CANCELLED", 0L), counts.getOrDefault("SCHEDULED", 0L), awaiting[0], followUp[0], last,
                next);
    }

    private static CaseFileViews.HearingBrief brief(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new CaseFileViews.HearingBrief(rs.getObject("id", UUID.class), rs.getObject("hearing_date", LocalDate.class),
                rs.getObject("hearing_time", java.time.LocalTime.class), rs.getString("status"), rs.getString("purpose"),
                rs.getString("kind"), rs.getString("decision"), rs.getString("adjournment_reason"),
                rs.getBoolean("no_next_date"));
    }

    private CaseFileViews.JudgmentBrief lastJudgment(UUID caseId) {
        return jdbc.sql("""
                        select j.id, j.proceeding_id, j.judgment_date, j.outcome, j.summary, j.final
                        from judgment j join proceeding p on p.id = j.proceeding_id where p.case_id = :id
                        order by j.judgment_date desc, j.created_at desc limit 1""").param("id", caseId)
                .query((rs, n) -> new CaseFileViews.JudgmentBrief(rs.getObject("id", UUID.class),
                        rs.getObject("proceeding_id", UUID.class), rs.getObject("judgment_date", LocalDate.class),
                        rs.getString("outcome"), rs.getString("summary"), rs.getBoolean("final")))
                .optional().orElse(null);
    }

    private CaseFileViews.RiskBrief risk(UUID caseId) {
        return jdbc.sql("""
                        select a.id, a.version_no, a.level, a.potential_loss, a.assessed_at,
                               (select count(*) from risk_remediation r where r.assessment_id = a.id
                                  and r.status in ('OPEN', 'IN_PROGRESS')) as open_count
                        from risk_assessment a where a.case_id = :id order by a.version_no desc limit 1""").param("id", caseId)
                .query((rs, n) -> new CaseFileViews.RiskBrief(rs.getObject("id", UUID.class), rs.getInt("version_no"),
                        rs.getString("level"), rs.getBigDecimal("potential_loss"),
                        rs.getObject("assessed_at", OffsetDateTime.class).toInstant(), rs.getLong("open_count")))
                .optional().orElse(null);
    }

    private CaseFileViews.InfoRequests infoRequests(UUID caseId, LocalDate today) {
        List<CaseFileViews.PendingRequest> pending = jdbc.sql("""
                        select id, request_no, subject, unit_name_snapshot, status, due_on,
                               (due_on is not null and due_on < :today) as overdue
                        from info_request where case_id = :id and status in %s
                        order by due_on nulls last, created_at limit 5""".formatted(WAITING))
                .param("id", caseId).param("today", today)
                .query((rs, n) -> new CaseFileViews.PendingRequest(rs.getObject("id", UUID.class), rs.getString("request_no"),
                        rs.getString("subject"), rs.getString("unit_name_snapshot"), rs.getString("status"),
                        rs.getObject("due_on", LocalDate.class), rs.getBoolean("overdue")))
                .list();
        long[] counts = jdbc.sql("""
                        select count(*) filter (where status in %s) as open,
                               count(*) filter (where status in %s and due_on is not null and due_on < :today) as overdue,
                               (select count(*) from info_reply p where p.case_id = :id and p.completeness = 'INCOMPLETE') as incomplete
                        from info_request where case_id = :id""".formatted(WAITING, WAITING))
                .param("id", caseId).param("today", today)
                .query((rs, n) -> new long[] {rs.getLong("open"), rs.getLong("overdue"), rs.getLong("incomplete")}).single();
        return new CaseFileViews.InfoRequests(counts[0], counts[1], counts[2], pending);
    }

    private CaseFileViews.Tasks tasks(UUID caseId, LocalDate today) {
        List<CaseFileViews.TaskBrief> items = jdbc.sql("""
                        select id, title, due_date, status, (due_date is not null and due_date < :today) as overdue
                        from task where case_id = :id and status in ('OPEN', 'IN_PROGRESS')
                        order by due_date nulls last, created_at limit 5""").param("id", caseId).param("today", today)
                .query((rs, n) -> new CaseFileViews.TaskBrief(rs.getObject("id", UUID.class), rs.getString("title"),
                        rs.getObject("due_date", LocalDate.class), rs.getString("status"), rs.getBoolean("overdue")))
                .list();
        long[] counts = jdbc.sql("""
                        select count(*) as open, count(*) filter (where due_date is not null and due_date < :today) as overdue
                        from task where case_id = :id and status in ('OPEN', 'IN_PROGRESS')""")
                .param("id", caseId).param("today", today)
                .query((rs, n) -> new long[] {rs.getLong("open"), rs.getLong("overdue")}).single();
        return new CaseFileViews.Tasks(counts[0], counts[1], items);
    }

    /** Open deadlines of the case: how many, how many are late, how many wait for approval, and the next few. */
    private CaseFileViews.Deadlines deadlines(UUID caseId, LocalDate today) {
        List<CaseFileViews.DeadlineBrief> next = jdbc.sql("""
                        select id, action, due_on, calc_status, (due_on is not null and due_on < :today) as overdue
                        from deadline where case_id = :id and status = 'OPEN'
                        order by due_on nulls last, created_at limit 5""").param("id", caseId).param("today", today)
                .query((rs, n) -> new CaseFileViews.DeadlineBrief(rs.getObject("id", UUID.class), rs.getString("action"),
                        rs.getObject("due_on", LocalDate.class), rs.getString("calc_status"), rs.getBoolean("overdue")))
                .list();
        long[] counts = jdbc.sql("""
                        select count(*) as open, count(*) filter (where due_on is not null and due_on < :today) as overdue,
                               count(*) filter (where calc_status <> 'APPROVED') as unapproved
                        from deadline where case_id = :id and status = 'OPEN'""")
                .param("id", caseId).param("today", today)
                .query((rs, n) -> new long[] {rs.getLong("open"), rs.getLong("overdue"), rs.getLong("unapproved")}).single();
        return new CaseFileViews.Deadlines(counts[0], counts[1], counts[2], next);
    }

    private CaseFileViews.Links links(UUID caseId) {
        List<UUID> others = jdbc.sql("""
                        select case when case_id = :id then other_case_id else case_id end from case_link
                        where case_id = :id or other_case_id = :id""").param("id", caseId).query(UUID.class).list();
        long visible = others.stream().filter(other -> cases.find(other).isPresent()).count();
        return new CaseFileViews.Links(visible, others.size() - visible);
    }

    // ---- the unified timeline ----------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    CaseFileViews.Timeline timeline(UUID caseId, List<String> types, boolean ascending, Pageable pageable) {
        access.requireView(caseId);
        for (String type : types) {
            if (!KINDS.contains(type)) {
                throw new FieldErrorException("types", "Invalid");
            }
        }
        CaseAccessPolicy.Predicate linkVisible = access.visibleCaseIds(
                "(case when l.case_id = :caseId then l.other_case_id else l.case_id end)");
        List<String> branches = new ArrayList<>();
        branches.add("""
                select occurred_at as at, 'EVENT' as kind, null::text as status, summary, 'CASE_EVENT' as ref_type, id as ref_id,
                       false as is_future, false as needs_action, created_by as actor_id
                from case_event where case_id = :caseId""");
        branches.add("""
                select occurred_at, 'LINK', l.status, case when %s then l.reason else '' end, 'CASE_LINK',
                       case when %s then l.id else null end, false, false, l.created_by
                from (select created_at as occurred_at, * from case_link) l
                where l.case_id = :caseId or l.other_case_id = :caseId""".formatted(linkVisible.sql(), linkVisible.sql()));
        if (currentActor.has(Permission.HEARING_VIEW)) {
            branches.add("""
                    select (h.hearing_date + coalesce(h.hearing_time, time '00:00')) at time zone 'Africa/Cairo', 'HEARING',
                           h.status, coalesce(h.decision, h.purpose), 'HEARING', h.id,
                           h.status = 'SCHEDULED' and h.hearing_date >= :today,
                           h.status = 'SCHEDULED' and h.hearing_date < :today, h.created_by
                    from hearing h where h.case_id = :caseId""");
        }
        if (currentActor.has(Permission.INFO_REQUEST_VIEW)) {
            branches.add("""
                    select coalesce(r.sent_on, r.drafted_on, (r.created_at at time zone 'Africa/Cairo')::date)::timestamp
                           at time zone 'Africa/Cairo', 'INFO_REQUEST', r.status, r.subject, 'INFO_REQUEST', r.id, false,
                           false, r.created_by
                    from info_request r where r.case_id = :caseId""");
            branches.add("""
                    select r.due_on::timestamp at time zone 'Africa/Cairo', 'INFO_REQUEST_DUE', r.status, r.subject,
                           'INFO_REQUEST', r.id, r.due_on >= :today, r.due_on < :today, r.created_by
                    from info_request r where r.case_id = :caseId and r.due_on is not null and r.status in %s""".formatted(WAITING));
            branches.add("""
                    select p.received_on::timestamp at time zone 'Africa/Cairo', 'INFO_REPLY', p.completeness,
                           coalesce(p.summary, p.completeness), 'INFO_REQUEST', p.request_id, false, false, p.created_by
                    from info_reply p where p.case_id = :caseId""");
        }
        if (currentActor.has(Permission.RISK_VIEW)) {
            branches.add("""
                    select a.assessed_at, 'RISK', a.level, a.level_reasons, 'RISK_ASSESSMENT', a.id, false, false, a.assessed_by
                    from risk_assessment a where a.case_id = :caseId""");
        }
        if (currentActor.has(Permission.JUDGMENT_VIEW)) {
            branches.add("""
                    select j.judgment_date::timestamp at time zone 'Africa/Cairo', 'JUDGMENT', j.outcome,
                           coalesce(j.summary, 'Judgment: ' || j.outcome), 'JUDGMENT', j.id, false, false, j.created_by
                    from judgment j join proceeding p on p.id = j.proceeding_id where p.case_id = :caseId""");
        }
        if (currentActor.has(Permission.DEADLINE_VIEW)) {
            // The due date is the moment of the entry; its state is the work state while open, the work state otherwise.
            branches.add("""
                    select d.due_on::timestamp at time zone 'Africa/Cairo', 'DEADLINE',
                           case when d.status = 'OPEN' then d.calc_status else d.status end, d.action, 'DEADLINE', d.id,
                           d.status = 'OPEN' and d.due_on >= :today, d.status = 'OPEN' and d.due_on < :today, d.created_by
                    from deadline d where d.case_id = :caseId and d.due_on is not null""");
        }
        String union = String.join("\nunion all\n", branches);
        Map<String, Object> params = new HashMap<>(linkVisible.params());
        params.put("caseId", caseId);
        params.put("today", today());
        String filter = types.isEmpty() ? "" : " where t.kind in (:types)";
        if (!types.isEmpty()) {
            params.put("types", types);
        }
        long total = jdbc.sql("select count(*) from (" + union + ") as t(at, kind, status, summary, ref_type, ref_id, is_future, needs_action, actor_id)"
                + filter).params(params).query(Long.class).single();
        String order = ascending ? "t.at, t.kind, t.ref_id" : "t.at desc, t.kind, t.ref_id";
        List<CaseFileViews.Entry> items = jdbc.sql("""
                        select t.at, t.kind, t.status, t.summary, t.ref_type, t.ref_id, t.is_future, t.needs_action,
                               t.actor_id, u.display_name
                        from (""" + union + ") as t(at, kind, status, summary, ref_type, ref_id, is_future, needs_action, actor_id)"
                        + " left join app_user u on u.id = t.actor_id" + filter + " order by " + order
                        + " limit :limit offset :offset")
                .params(params).param("limit", pageable.getPageSize()).param("offset", pageable.getOffset())
                .query((rs, n) -> new CaseFileViews.Entry(rs.getObject("at", OffsetDateTime.class).toInstant(),
                        rs.getString("kind"), rs.getString("status"), rs.getString("summary"), rs.getString("ref_type"),
                        rs.getObject("ref_id", UUID.class), rs.getBoolean("is_future"), rs.getBoolean("needs_action"),
                        rs.getObject("actor_id", UUID.class), rs.getString("display_name")))
                .list();
        int size = pageable.getPageSize();
        return new CaseFileViews.Timeline(items, pageable.getPageNumber(), size, total, (int) ((total + size - 1) / size),
                currentActor.has(Permission.DEADLINE_VIEW));
    }
}
