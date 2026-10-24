package com.lawdesk.cases;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * The read-only port reports use to count and list cases (architecture/CONTRACTS.md §8.3; Workstream E, Phase 6).
 * It never writes. Rules such as which statuses count as open are decided here, in the cases module, so that reports
 * never redefine them.
 */
@Component
public class CaseReportQueries {

    /**
     * How reports group case statuses (docs/delivery/PHASE-6-ACCEPTANCE.md, common rule 3): a case is closed when its
     * details are read-only, that is closed or archived (LD-013, LD-017); every other status, suspended included, is
     * open.
     */
    public enum StatusGroup {
        OPEN,
        CLOSED;

        public static StatusGroup of(CaseStatus status) {
            return status.detailsReadOnly() ? CLOSED : OPEN;
        }

        /** The statuses of this group, in lifecycle order. */
        public List<CaseStatus> statuses() {
            return Arrays.stream(CaseStatus.values()).filter(status -> of(status) == this).toList();
        }
    }

    /** Cases counted by status and by type; every status and type is present, zeros included. */
    public record CaseCounts(long total, Map<CaseStatus, Long> byStatus, Map<CaseType, Long> byType) {

        public long count(StatusGroup group) {
            return group.statuses().stream().mapToLong(byStatus::get).sum();
        }
    }

    private final JdbcClient jdbc;

    CaseReportQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Every stored case, counted by status and by type. */
    public CaseCounts counts() {
        Map<CaseStatus, Long> byStatus = zeros(CaseStatus.class);
        Map<CaseType, Long> byType = zeros(CaseType.class);
        long[] total = {0};
        jdbc.sql("select status, case_type, count(*) as n from legal_case group by status, case_type")
                .query(rs -> {
                    long n = rs.getLong("n");
                    byStatus.merge(CaseStatus.valueOf(rs.getString("status")), n, Long::sum);
                    byType.merge(CaseType.valueOf(rs.getString("case_type")), n, Long::sum);
                    total[0] += n;
                });
        return new CaseCounts(total[0], Collections.unmodifiableMap(byStatus), Collections.unmodifiableMap(byType));
    }

    /** The number of cases opened between the two dates, both included. */
    public long openedBetween(LocalDate from, LocalDate to) {
        return jdbc.sql("select count(*) from legal_case where opened_on between :from and :to")
                .param("from", from).param("to", to).query(Long.class).single();
    }

    private static <E extends Enum<E>> Map<E, Long> zeros(Class<E> type) {
        Map<E, Long> counts = new EnumMap<>(type);
        for (E value : type.getEnumConstants()) {
            counts.put(value, 0L);
        }
        return counts;
    }
}
