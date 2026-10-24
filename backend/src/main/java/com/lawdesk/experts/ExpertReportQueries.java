package com.lawdesk.experts;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The read-only expert port for Phase 6 reports (architecture/CONTRACTS.md §8.3): referrals by expert and status, and
 * overdue reports. It has no security of its own: the reports module checks {@code REPORT_VIEW} first. "Overdue" (an
 * open referral whose report was due before today) is defined here, so reports never redefine it.
 */
@Component
@Transactional(readOnly = true)
public class ExpertReportQueries {

    /** The most rows one call returns (the export limit of LD-034, ASSUMPTION). */
    public static final int MAX_ROWS = 10_000;

    /**
     * Empty {@code statuses} means every status; a {@code null} expert or case means every one; {@code overdue} keeps
     * the overdue referrals only.
     */
    public record ReferralFilter(Set<ReferralStatus> statuses, UUID expertId, UUID caseId, boolean overdue) {

        public ReferralFilter {
            statuses = statuses == null ? Set.of() : Set.copyOf(statuses);
        }

        public static ReferralFilter all() {
            return new ReferralFilter(Set.of(), null, null, false);
        }

        public ReferralFilter withStatuses(Set<ReferralStatus> only) {
            return new ReferralFilter(only, expertId, caseId, overdue);
        }

        public ReferralFilter withExpert(UUID expert) {
            return new ReferralFilter(statuses, expert, caseId, overdue);
        }

        public ReferralFilter withCase(UUID onlyCase) {
            return new ReferralFilter(statuses, expertId, onlyCase, overdue);
        }

        public ReferralFilter overdueOnly() {
            return new ReferralFilter(statuses, expertId, caseId, true);
        }
    }

    /** One referral as reports see it; case, proceeding and expert details come from their own ports or the API. */
    public record ReferralRow(UUID id, UUID caseId, UUID proceedingId, UUID expertId, LocalDate referralDate,
            LocalDate reportDueDate, ReferralStatus status, LocalDate reportSubmittedDate, boolean reportOverdue) {
    }

    /** The open referrals of one expert and how many of them are overdue; experts without referrals count zero. */
    public record ExpertWorkload(UUID expertId, long open, long overdue) {
    }

    private static final String OVERDUE = "status = 'OPEN' and report_due_date < :today";

    private final NamedParameterJdbcTemplate jdbc;

    ExpertReportQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** "Today" for every expert rule: the calendar date in Egypt. */
    public LocalDate today() {
        return Rules.today();
    }

    /** The matching referrals by referral date, then the order of recording. */
    public List<ReferralRow> find(ReferralFilter filter, int offset, int limit) {
        if (offset < 0 || limit < 1 || limit > MAX_ROWS) {
            throw new IllegalArgumentException("offset must be >= 0 and limit 1.." + MAX_ROWS);
        }
        return jdbc.query("""
                select id, case_id, proceeding_id, expert_id, referral_date, report_due_date, status,
                       report_submitted_date, coalesce(""" + OVERDUE + ", false) as overdue from expert_referral"
                + where(filter) + " order by referral_date, created_at, id offset :offset limit :limit",
                params(filter).addValue("offset", offset).addValue("limit", limit), (rs, n) -> row(rs));
    }

    public long count(ReferralFilter filter) {
        Long count = jdbc.queryForObject("select count(*) from expert_referral" + where(filter), params(filter),
                Long.class);
        return count == null ? 0 : count;
    }

    /** Every status, with zero for the ones no referral has. */
    public Map<ReferralStatus, Long> countByStatus(ReferralFilter filter) {
        Map<ReferralStatus, Long> counts = new EnumMap<>(ReferralStatus.class);
        for (ReferralStatus status : ReferralStatus.values()) {
            counts.put(status, 0L);
        }
        jdbc.query("select status, count(*) as n from expert_referral" + where(filter) + " group by status",
                params(filter), rs -> {
                    counts.put(ReferralStatus.valueOf(rs.getString("status")), rs.getLong("n"));
                });
        return counts;
    }

    public long overdueReportCount() {
        return count(ReferralFilter.all().overdueOnly());
    }

    /** Every expert of the registry with its open and overdue referrals, by expert id. */
    public List<ExpertWorkload> workload() {
        return jdbc.query("""
                select e.id, count(r.id) filter (where r.status = 'OPEN') as open,
                       count(r.id) filter (where r.status = 'OPEN' and r.report_due_date < :today) as overdue
                from expert e left join expert_referral r on r.expert_id = e.id
                group by e.id order by e.id""", new MapSqlParameterSource("today", today()),
                (rs, n) -> new ExpertWorkload(rs.getObject("id", UUID.class), rs.getLong("open"),
                        rs.getLong("overdue")));
    }

    private static String where(ReferralFilter filter) {
        StringBuilder sql = new StringBuilder(" where true");
        if (!filter.statuses().isEmpty()) {
            sql.append(" and status in (:statuses)");
        }
        if (filter.expertId() != null) {
            sql.append(" and expert_id = :expertId");
        }
        if (filter.caseId() != null) {
            sql.append(" and case_id = :caseId");
        }
        if (filter.overdue()) {
            sql.append(" and ").append(OVERDUE);
        }
        return sql.toString();
    }

    private MapSqlParameterSource params(ReferralFilter filter) {
        return new MapSqlParameterSource().addValue("statuses", filter.statuses().stream().map(Enum::name).toList())
                .addValue("expertId", filter.expertId()).addValue("caseId", filter.caseId())
                .addValue("today", today());
    }

    private static ReferralRow row(ResultSet rs) throws SQLException {
        return new ReferralRow(rs.getObject("id", UUID.class), rs.getObject("case_id", UUID.class),
                rs.getObject("proceeding_id", UUID.class), rs.getObject("expert_id", UUID.class),
                rs.getObject("referral_date", LocalDate.class), rs.getObject("report_due_date", LocalDate.class),
                ReferralStatus.valueOf(rs.getString("status")), rs.getObject("report_submitted_date", LocalDate.class),
                rs.getBoolean("overdue"));
    }
}
