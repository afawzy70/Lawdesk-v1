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
            UUID proceedingId) {

        public HearingFilter {
            Objects.requireNonNull(from, "from");
            Objects.requireNonNull(to, "to");
            statuses = statuses == null || statuses.isEmpty() ? Set.of() : Set.copyOf(statuses);
        }

        public static HearingFilter between(LocalDate from, LocalDate to) {
            return new HearingFilter(from, to, Set.of(), null, null);
        }

        public HearingFilter withStatuses(Set<HearingStatus> only) {
            return new HearingFilter(from, to, only, caseId, proceedingId);
        }

        public HearingFilter withCase(UUID onlyCase) {
            return new HearingFilter(from, to, statuses, onlyCase, proceedingId);
        }

        public HearingFilter withProceeding(UUID onlyProceeding) {
            return new HearingFilter(from, to, statuses, caseId, onlyProceeding);
        }
    }

    /** One hearing as reports see it; case and proceeding details come from their own ports. */
    public record HearingRow(UUID id, UUID caseId, UUID proceedingId, LocalDate hearingDate, LocalTime hearingTime,
            HearingPurpose purpose, HearingStatus status, boolean awaitingResult, UUID adjournedFromId) {
    }

    private static final String ORDER = " order by hearing_date, hearing_time nulls first, created_at, id";

    private final NamedParameterJdbcTemplate jdbc;

    HearingReportQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** "Today" for every hearing rule: the calendar date in Egypt. */
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
        Long count = jdbc.queryForObject("select count(*) from hearing where status = 'SCHEDULED' and hearing_date < :today",
                new MapSqlParameterSource("today", today()), Long.class);
        return count == null ? 0 : count;
    }

    private static String where(HearingFilter filter) {
        StringBuilder sql = new StringBuilder(" where hearing_date between :from and :to");
        if (!filter.statuses().isEmpty()) {
            sql.append(" and status in (:statuses)");
        }
        if (filter.caseId() != null) {
            sql.append(" and case_id = :caseId");
        }
        if (filter.proceedingId() != null) {
            sql.append(" and proceeding_id = :proceedingId");
        }
        return sql.toString();
    }

    private static MapSqlParameterSource params(HearingFilter filter) {
        return new MapSqlParameterSource().addValue("from", filter.from()).addValue("to", filter.to())
                .addValue("statuses", filter.statuses().stream().map(Enum::name).toList())
                .addValue("caseId", filter.caseId()).addValue("proceedingId", filter.proceedingId());
    }

    private static HearingRow row(ResultSet rs) throws SQLException {
        return new HearingRow(rs.getObject("id", UUID.class), rs.getObject("case_id", UUID.class),
                rs.getObject("proceeding_id", UUID.class), rs.getObject("hearing_date", LocalDate.class),
                rs.getObject("hearing_time", LocalTime.class), HearingPurpose.valueOf(rs.getString("purpose")),
                HearingStatus.valueOf(rs.getString("status")), rs.getBoolean("awaiting"),
                rs.getObject("adjourned_from_id", UUID.class));
    }
}
