package com.lawdesk.hearings;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import com.lawdesk.cases.CaseAccessPolicy;
import org.springframework.transaction.annotation.Transactional;

/**
 * The read-only hearing port for Phase 6 reports (architecture/CONTRACTS.md §8.3). It has no security of its own: the
 * reports module checks {@code REPORT_VIEW} before it calls it. The rules "upcoming" and "awaiting a result" are defined
 * here, so reports never redefine them (docs/delivery/PHASE-5-ACCEPTANCE.md, LD-024 criterion 8).
 */
@Component
@Transactional(readOnly = true)
public class HearingReportQueries {

    /** The most rows one call returns (the export limit of LD-034, ASSUMPTION). */
    public static final int MAX_ROWS = 10_000;

    /**
     * Hearings dated {@code from}…{@code to} (both required and inclusive); an empty {@code statuses} means every status,
     * a {@code null} case or proceeding means every one.
     */
    public record HearingFilter(LocalDate from, LocalDate to, Set<HearingStatus> statuses, UUID caseId,
            UUID proceedingId, Set<HearingKind> kinds) {

        public HearingFilter {
            Objects.requireNonNull(from, "from");
            Objects.requireNonNull(to, "to");
            statuses = statuses == null || statuses.isEmpty() ? Set.of() : Set.copyOf(statuses);
            kinds = kinds == null || kinds.isEmpty() ? Set.of() : Set.copyOf(kinds);
        }

        public HearingFilter(LocalDate from, LocalDate to, Set<HearingStatus> statuses, UUID caseId,
                UUID proceedingId) {
            this(from, to, statuses, caseId, proceedingId, Set.of());
        }

        /** Only hearings of these kinds (court, expert, investigation, other); empty means every kind. */
        public HearingFilter withKinds(Set<HearingKind> only) {
            return new HearingFilter(from, to, statuses, caseId, proceedingId, only);
        }

        public static HearingFilter between(LocalDate from, LocalDate to) {
            return new HearingFilter(from, to, Set.of(), null, null);
        }

        public HearingFilter withStatuses(Set<HearingStatus> only) {
            return new HearingFilter(from, to, only, caseId, proceedingId, kinds);
        }

        public HearingFilter withCase(UUID onlyCase) {
            return new HearingFilter(from, to, statuses, onlyCase, proceedingId, kinds);
        }

        public HearingFilter withProceeding(UUID onlyProceeding) {
            return new HearingFilter(from, to, statuses, caseId, onlyProceeding, kinds);
        }
    }

    /** One hearing as reports see it; case and proceeding details come from their own ports. */
    public record HearingRow(UUID id, UUID caseId, UUID proceedingId, LocalDate hearingDate, LocalTime hearingTime,
            HearingPurpose purpose, HearingStatus status, boolean awaitingResult, UUID adjournedFromId) {
    }

    private static final String ORDER = " order by hearing_date, hearing_time nulls first, created_at, id";

    private final NamedParameterJdbcTemplate jdbc;
    private final CaseAccessPolicy access;

    HearingReportQueries(NamedParameterJdbcTemplate jdbc, CaseAccessPolicy access) {
        this.jdbc = jdbc;
        this.access = access;
    }

    /** "Today" for every hearing rule: the calendar date in Kuwait (Asia/Kuwait). */
    public LocalDate today() {
        return LegalDay.today();
    }

    /** The matching hearings in calendar order (date, time with untimed first, order of recording). */
    public List<HearingRow> find(HearingFilter filter, int offset, int limit) {
        if (offset < 0 || limit < 1 || limit > MAX_ROWS) {
            throw new IllegalArgumentException("offset must be >= 0 and limit 1.." + MAX_ROWS);
        }
        MapSqlParameterSource params = params(filter).addValue("offset", offset).addValue("limit", limit)
                .addValue("today", today());
        return jdbc.query("""
                select id, case_id, proceeding_id, hearing_date, hearing_time, purpose, status, adjourned_from_id,
                       status = 'SCHEDULED' and hearing_date < :today as awaiting
                from hearing""" + where(filter) + ORDER + " offset :offset limit :limit", params,
                (rs, n) -> row(rs));
    }

    public long count(HearingFilter filter) {
        Long count = jdbc.queryForObject("select count(*) from hearing" + where(filter), params(filter), Long.class);
        return count == null ? 0 : count;
    }

    /** Every status, with zero for the ones no hearing has. */
    public Map<HearingStatus, Long> countByStatus(HearingFilter filter) {
        Map<HearingStatus, Long> counts = new EnumMap<>(HearingStatus.class);
        for (HearingStatus status : HearingStatus.values()) {
            counts.put(status, 0L);
        }
        jdbc.query("select status, count(*) as n from hearing" + where(filter) + " group by status", params(filter),
                rs -> {
                    counts.put(HearingStatus.valueOf(rs.getString("status")), rs.getLong("n"));
                });
        return counts;
    }

    /** Upcoming hearings: scheduled and dated from today to today + {@code days}, inclusive. */
    public long upcomingCount(int days) {
        if (days < 0) {
            throw new IllegalArgumentException("days must be >= 0");
        }
        LocalDate today = today();
        return count(new HearingFilter(today, today.plusDays(days), EnumSet.of(HearingStatus.SCHEDULED), null, null));
    }

    /** Hearings awaiting a result: still scheduled although their day has passed. */
    public long awaitingResultCount() {
        CaseAccessPolicy.Predicate visible = access.visibleCaseIds("case_id");
        Long count = jdbc.queryForObject("select count(*) from hearing where status = 'SCHEDULED'"
                + " and hearing_date < :today and " + visible.sql(),
                new MapSqlParameterSource(visible.params()).addValue("today", today()), Long.class);
        return count == null ? 0 : count;
    }


    // ---- WP8 (LD-058): conditions and groupings the dashboard indicators and report families share -------------------

    /**
     * Cases reserved for judgment (القضايا المحجوزة للحكم), as a condition over {@code legal_case}: a proceeding with no
     * judgment recorded yet that has a hearing for the pronouncement of judgment which was not cancelled. ASSUMPTION:
     * the system has no separate "reserved" status, so the pronouncement hearing is the record of the reservation.
     */
    public static CaseAccessPolicy.Predicate reservedForJudgment() {
        return new CaseAccessPolicy.Predicate("legal_case.id in (select rp.case_id from proceeding rp"
                + " join hearing rh on rh.proceeding_id = rp.id where rh.purpose = 'JUDGMENT' and rh.status <> 'CANCELLED'"
                + " and not exists (select 1 from judgment rj where rj.proceeding_id = rp.id))", Map.of());
    }

    /** One reason the court gave for adjourning, as recorded; hearings with none recorded are grouped with a null reason. */
    public record AdjournmentReason(String reason, long hearings, long cases) {
    }

    /**
     * Adjourned hearings of the cases in {@code caseScope} (a condition over {@code legal_case}, see
     * {@code CaseReportQueries#scope}) grouped by the recorded adjournment reason, ignoring case and surrounding spaces.
     */
    public List<AdjournmentReason> adjournmentReasons(CaseAccessPolicy.Predicate caseScope) {
        return jdbc.query("select min(btrim(h.adjournment_reason)) as reason, count(*) as hearings,"
                + " count(distinct h.case_id) as cases from hearing h join legal_case on legal_case.id = h.case_id"
                + " where h.status = 'ADJOURNED' and " + caseScope.sql()
                + " group by lower(nullif(btrim(h.adjournment_reason), '')) order by hearings desc, reason nulls last",
                new MapSqlParameterSource(caseScope.params()),
                (rs, n) -> new AdjournmentReason(rs.getString("reason"), rs.getLong("hearings"), rs.getLong("cases")));
    }

    /** The hearings of the cases in {@code caseScope} of one kind, counted per status (every status, zeros included). */
    public Map<HearingStatus, Long> countByStatus(CaseAccessPolicy.Predicate caseScope, HearingKind kind) {
        Map<HearingStatus, Long> counts = new EnumMap<>(HearingStatus.class);
        for (HearingStatus status : HearingStatus.values()) {
            counts.put(status, 0L);
        }
        jdbc.query("select h.status, count(*) as n from hearing h join legal_case on legal_case.id = h.case_id where h.kind = :kind"
                + " and " + caseScope.sql() + " group by h.status",
                new MapSqlParameterSource(caseScope.params()).addValue("kind", kind.name()),
                rs -> {
                    counts.put(HearingStatus.valueOf(rs.getString("status")), rs.getLong("n"));
                });
        return counts;
    }

    /** What a member is recorded as responsible for in a period: hearings by state, objective counts only. */
    public record MemberHearings(UUID memberId, long scheduled, long held, long awaitingResult) {
    }

    /** Hearings dated in the period, per responsible member, over the cases in {@code caseScope}. */
    public List<MemberHearings> byResponsibleMember(CaseAccessPolicy.Predicate caseScope, LocalDate from, LocalDate to) {
        return jdbc.query("select h.responsible_member_id as member, count(*) filter (where h.status = 'SCHEDULED') as scheduled,"
                + " count(*) filter (where h.status = 'HELD') as held,"
                + " count(*) filter (where h.status = 'SCHEDULED' and h.hearing_date < :today) as awaiting"
                + " from hearing h join legal_case on legal_case.id = h.case_id where h.responsible_member_id is not null"
                + " and h.hearing_date between :from and :to and " + caseScope.sql() + " group by h.responsible_member_id",
                new MapSqlParameterSource(caseScope.params()).addValue("from", from).addValue("to", to)
                        .addValue("today", today()),
                (rs, n) -> new MemberHearings(rs.getObject("member", UUID.class), rs.getLong("scheduled"),
                        rs.getLong("held"), rs.getLong("awaiting")));
    }

    private String where(HearingFilter filter) {
        StringBuilder sql = new StringBuilder(" where hearing_date between :from and :to and "
                + access.visibleCaseIds("case_id").sql());
        if (!filter.statuses().isEmpty()) {
            sql.append(" and status in (:statuses)");
        }
        if (filter.caseId() != null) {
            sql.append(" and case_id = :caseId");
        }
        if (filter.proceedingId() != null) {
            sql.append(" and proceeding_id = :proceedingId");
        }
        if (!filter.kinds().isEmpty()) {
            sql.append(" and kind in (:kinds)");
        }
        return sql.toString();
    }

    private MapSqlParameterSource params(HearingFilter filter) {
        return new MapSqlParameterSource(access.visibleCaseIds("case_id").params()).addValue("from", filter.from()).addValue("to", filter.to())
                .addValue("statuses", filter.statuses().stream().map(Enum::name).toList())
                .addValue("caseId", filter.caseId()).addValue("proceedingId", filter.proceedingId())
                .addValue("kinds", filter.kinds().stream().map(Enum::name).toList());
    }

    private static HearingRow row(ResultSet rs) throws SQLException {
        return new HearingRow(rs.getObject("id", UUID.class), rs.getObject("case_id", UUID.class),
                rs.getObject("proceeding_id", UUID.class), rs.getObject("hearing_date", LocalDate.class),
                rs.getObject("hearing_time", LocalTime.class), HearingPurpose.valueOf(rs.getString("purpose")),
                HearingStatus.valueOf(rs.getString("status")), rs.getBoolean("awaiting"),
                rs.getObject("adjourned_from_id", UUID.class));
    }
}
