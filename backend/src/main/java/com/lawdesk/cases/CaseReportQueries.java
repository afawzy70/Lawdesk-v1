package com.lawdesk.cases;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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

    /**
     * Which cases to read; the conditions combine. Empty sets and null values do not filter, except {@code onlyCases}:
     * when it is not null, only the cases it names match (an empty set matches nothing).
     *
     * @param text a text the reference or the title contains, ignoring case; {@code %} and {@code _} are literal
     */
    public record CaseFilter(String text, Set<CaseStatus> statuses, Set<CaseType> types, LocalDate openedFrom,
            LocalDate openedTo, Set<UUID> onlyCases) {

        public static final CaseFilter ALL = new CaseFilter(null, Set.of(), Set.of(), null, null, null);

        public CaseFilter {
            text = text == null || text.isBlank() ? null : text.strip();
            statuses = statuses == null ? Set.of() : Set.copyOf(statuses);
            types = types == null ? Set.of() : Set.copyOf(types);
            onlyCases = onlyCases == null ? null : Set.copyOf(onlyCases);
        }
    }

    /** The orders reports may ask for; status and type follow their declaration (lifecycle) order. */
    public enum SortField {
        REFERENCE_NO("lower(reference_no)"),
        TITLE("lower(title)"),
        CASE_TYPE(declarationOrder("case_type", CaseType.values())),
        STATUS(declarationOrder("status", CaseStatus.values())),
        OPENED_ON("opened_on");

        private final String sql;

        SortField(String sql) {
            this.sql = sql;
        }
    }

    /** One case as reports list it. */
    public record CaseRow(UUID id, String referenceNo, String title, CaseType caseType, CaseStatus status,
            LocalDate openedOn) {
    }

    private final JdbcClient jdbc;

    CaseReportQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Every stored case, counted by status and by type. */
    public CaseCounts counts() {
        return counts(CaseFilter.ALL);
    }

    /** The matching cases, counted by status and by type. */
    public CaseCounts counts(CaseFilter filter) {
        Map<CaseStatus, Long> byStatus = zeros(CaseStatus.class);
        Map<CaseType, Long> byType = zeros(CaseType.class);
        long[] total = {0};
        Where where = where(filter);
        jdbc.sql("select status, case_type, count(*) as n from legal_case" + where.sql + " group by status, case_type")
                .params(where.params).query(rs -> {
                    long n = rs.getLong("n");
                    byStatus.merge(CaseStatus.valueOf(rs.getString("status")), n, Long::sum);
                    byType.merge(CaseType.valueOf(rs.getString("case_type")), n, Long::sum);
                    total[0] += n;
                });
        return new CaseCounts(total[0], Collections.unmodifiableMap(byStatus), Collections.unmodifiableMap(byType));
    }

    /**
     * A page of the matching cases in the given order; ties are broken by reference number, then id, so paging never
     * repeats or skips a case.
     */
    public List<CaseRow> list(CaseFilter filter, SortField sort, boolean descending, long offset, int limit) {
        Where where = where(filter);
        Map<String, Object> params = new HashMap<>(where.params);
        params.put("limit", limit);
        params.put("offset", offset);
        return jdbc.sql("select id, reference_no, title, case_type, status, opened_on from legal_case" + where.sql
                        + " order by " + sort.sql + (descending ? " desc" : " asc") + ", lower(reference_no), id"
                        + " limit :limit offset :offset")
                .params(params)
                .query((rs, n) -> new CaseRow(rs.getObject("id", UUID.class), rs.getString("reference_no"),
                        rs.getString("title"), CaseType.valueOf(rs.getString("case_type")),
                        CaseStatus.valueOf(rs.getString("status")), rs.getObject("opened_on", LocalDate.class)))
                .list();
    }

    /** The ids of every matching case. */
    public List<UUID> ids(CaseFilter filter) {
        Where where = where(filter);
        return jdbc.sql("select id from legal_case" + where.sql).params(where.params).query(UUID.class).list();
    }

    /** The cases with these ids that exist, by id. */
    public Map<UUID, CaseSummary> summaries(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, CaseSummary> found = new LinkedHashMap<>();
        jdbc.sql("select id, reference_no, title, status from legal_case where id = any(cast(:ids as uuid[]))")
                .param("ids", uuidArray(ids)).query(rs -> {
                    UUID id = rs.getObject("id", UUID.class);
                    found.put(id, new CaseSummary(id, rs.getString("reference_no"), rs.getString("title"),
                            rs.getString("status")));
                });
        return found;
    }

    /** The number of cases opened between the two dates, both included. */
    public long openedBetween(LocalDate from, LocalDate to) {
        return jdbc.sql("select count(*) from legal_case where opened_on between :from and :to")
                .param("from", from).param("to", to).query(Long.class).single();
    }

    private record Where(String sql, Map<String, Object> params) {
    }

    private static Where where(CaseFilter filter) {
        StringBuilder sql = new StringBuilder(" where true");
        Map<String, Object> params = new HashMap<>();
        if (filter.text() != null) {
            sql.append(" and (reference_no ilike :text escape '\\' or title ilike :text escape '\\')");
            params.put("text", "%" + Text.escapeLike(filter.text()) + "%");
        }
        if (!filter.statuses().isEmpty()) {
            sql.append(" and status in (:statuses)");
            params.put("statuses", filter.statuses().stream().map(Enum::name).toList());
        }
        if (!filter.types().isEmpty()) {
            sql.append(" and case_type in (:types)");
            params.put("types", filter.types().stream().map(Enum::name).toList());
        }
        if (filter.openedFrom() != null) {
            sql.append(" and opened_on >= :openedFrom");
            params.put("openedFrom", filter.openedFrom());
        }
        if (filter.openedTo() != null) {
            sql.append(" and opened_on <= :openedTo");
            params.put("openedTo", filter.openedTo());
        }
        if (filter.onlyCases() != null) {
            sql.append(" and id = any(cast(:onlyCases as uuid[]))");
            params.put("onlyCases", uuidArray(filter.onlyCases()));
        }
        return new Where(sql.toString(), params);
    }

    /** A PostgreSQL array literal of ids; UUIDs hold only hex digits and hyphens, so nothing needs quoting. */
    private static String uuidArray(Collection<UUID> ids) {
        return ids.stream().map(UUID::toString).collect(Collectors.joining(",", "{", "}"));
    }

    private static String declarationOrder(String column, Enum<?>[] values) {
        return Arrays.stream(values).map(value -> "when '" + value.name() + "' then " + value.ordinal())
                .collect(Collectors.joining(" ", "case " + column + " ", " end"));
    }

    private static <E extends Enum<E>> Map<E, Long> zeros(Class<E> type) {
        Map<E, Long> counts = new EnumMap<>(type);
        for (E value : type.getEnumConstants()) {
            counts.put(value, 0L);
        }
        return counts;
    }
}
