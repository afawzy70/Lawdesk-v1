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
            LocalDate openedTo, Set<UUID> onlyCases, List<CaseAccessPolicy.Predicate> extra) {

        public static final CaseFilter ALL = new CaseFilter(null, Set.of(), Set.of(), null, null, null);

        public CaseFilter {
            text = text == null || text.isBlank() ? null : text.strip();
            statuses = statuses == null ? Set.of() : Set.copyOf(statuses);
            types = types == null ? Set.of() : Set.copyOf(types);
            onlyCases = onlyCases == null ? null : Set.copyOf(onlyCases);
            extra = extra == null ? List.of() : List.copyOf(extra);
        }

        public CaseFilter(String text, Set<CaseStatus> statuses, Set<CaseType> types, LocalDate openedFrom,
                LocalDate openedTo, Set<UUID> onlyCases) {
            this(text, statuses, types, openedFrom, openedTo, onlyCases, List.of());
        }

        /**
         * The same filter narrowed by a condition over {@code legal_case} (columns qualified {@code legal_case.}) that
         * the module owning the rule published, for example "has a high risk". Parameter names must be unique.
         */
        public CaseFilter and(CaseAccessPolicy.Predicate condition) {
            List<CaseAccessPolicy.Predicate> all = new java.util.ArrayList<>(extra);
            all.add(condition);
            return new CaseFilter(text, statuses, types, openedFrom, openedTo, onlyCases, all);
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

    /**
     * The ids of a query as an uncorrelated subquery. A filter written {@code id = any(cast(? as uuid[]))} re-evaluates
     * the cast for every row of a sequential scan (about 105 s for 10 000 ids, QAD-039 audit); the subquery reads the
     * array once and joins it.
     */
    private static final String IDS = "(select unnest(cast(:ids as uuid[])))";

    private final CaseAccessPolicy access;

    CaseReportQueries(JdbcClient jdbc, CaseAccessPolicy access) {
        this.jdbc = jdbc;
        this.access = access;
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
        CaseAccessPolicy.Predicate visible = access.viewable("legal_case");
        jdbc.sql("select id, reference_no, title, status from legal_case where id in " + IDS + " and " + visible.sql())
                .params(visible.params()).param("ids", uuidArray(ids)).query(rs -> {
                    UUID id = rs.getObject("id", UUID.class);
                    found.put(id, new CaseSummary(id, rs.getString("reference_no"), rs.getString("title"),
                            rs.getString("status")));
                });
        return found;
    }

    /** The number of cases opened between the two dates, both included. */
    public long openedBetween(LocalDate from, LocalDate to) {
        CaseAccessPolicy.Predicate visible = access.viewable("legal_case");
        return jdbc.sql("select count(*) from legal_case where opened_on between :from and :to and " + visible.sql())
                .params(visible.params()).param("from", from).param("to", to).query(Long.class).single();
    }


    // ---- conditions the dashboard indicators and the report families share (WP8, LD-058) -----------------------------

    /**
     * The cases the signed-in user may see that match the filter, as one condition over {@code legal_case} (columns
     * qualified {@code legal_case.}): what another module's report joins to when it counts rows of visible, matching
     * cases only. The visibility rule is {@link CaseAccessPolicy}'s and cannot be left out.
     */
    public CaseAccessPolicy.Predicate scope(CaseFilter filter) {
        Where where = where(filter, "legal_case.");
        return new CaseAccessPolicy.Predicate(where.sql.substring(" where ".length()), where.params);
    }

    private static final java.util.concurrent.atomic.AtomicInteger NAMES = new java.util.concurrent.atomic.AtomicInteger();

    private static String unique(String base) {
        return base + NAMES.incrementAndGet();
    }

    /** Cases whose primary member is this user. */
    public static CaseAccessPolicy.Predicate primaryMember(UUID member) {
        String name = unique("cm");
        return new CaseAccessPolicy.Predicate("legal_case.primary_member_id = :" + name, Map.of(name, member));
    }

    /** Cases with no primary member recorded ("not specified" is a value, never a guess). */
    public static CaseAccessPolicy.Predicate noPrimaryMember() {
        return new CaseAccessPolicy.Predicate("legal_case.primary_member_id is null", Map.of());
    }

    /** The level of the authority's structure a case is grouped by. */
    public enum UnitLevel {
        SECTOR("sector_name_snapshot"),
        DEPARTMENT("department_name_snapshot"),
        SECTION("section_name_snapshot");

        private final String column;

        UnitLevel(String column) {
            this.column = column;
        }
    }

    /**
     * Cases whose primary involvement names this sector, department or section (the names copied when the unit was
     * recorded); a null {@code name} selects the cases with no name at that level. A case has at most one primary
     * involvement, so it falls in exactly one group per level.
     */
    public static CaseAccessPolicy.Predicate authorityUnit(UnitLevel level, String name) {
        String column = "i." + level.column;
        if (name == null) {
            return new CaseAccessPolicy.Predicate("not exists (select 1 from case_org_involvement i where i.case_id = legal_case.id"
                    + " and i.is_primary and i.removed_at is null and " + column + " is not null and length(btrim(" + column
                    + ")) > 0)", Map.of());
        }
        String param = unique("cu");
        return new CaseAccessPolicy.Predicate("exists (select 1 from case_org_involvement i where i.case_id = legal_case.id"
                + " and i.is_primary and i.removed_at is null and btrim(" + column + ") = :" + param + ")",
                Map.of(param, name));
    }

    /** What cases are grouped by. */
    public enum Dimension {
        CASE_TYPE,
        STATUS,
        MEMBER,
        SECTOR,
        DEPARTMENT,
        SECTION
    }

    /**
     * One group of cases. {@code key} is null for the cases that have no value; the two amounts are separate sums of the
     * case's own claimed amount and financial-impact amount, each case counted once (decision D5: kinds never mix).
     */
    public record Group(String key, String label, long cases, java.math.BigDecimal claimedAmount,
            java.math.BigDecimal financialImpactAmount) {
    }

    /** The matching visible cases grouped by one dimension; every case falls in exactly one group. */
    public List<Group> groups(CaseFilter filter, Dimension dimension) {
        Where where = where(filter, "legal_case.");
        String key;
        String join = "";
        switch (dimension) {
            case CASE_TYPE -> key = "legal_case.case_type";
            case STATUS -> key = "legal_case.status";
            case MEMBER -> {
                key = "cast(legal_case.primary_member_id as text)";
                join = " left join app_user u on u.id = legal_case.primary_member_id";
            }
            default -> {
                UnitLevel level = UnitLevel.valueOf(dimension.name());
                key = "nullif(btrim(pi." + level.column + "), '')";
                join = " left join case_org_involvement pi on pi.case_id = legal_case.id and pi.is_primary"
                        + " and pi.removed_at is null";
            }
        }
        String label = dimension == Dimension.MEMBER ? "max(u.display_name)" : "max(" + key + ")";
        String sql = "select " + key + " as k, " + label + " as label, count(*) as n,"
                + " coalesce(sum(legal_case.claimed_amount), 0) as claimed,"
                + " coalesce(sum(legal_case.financial_amount), 0) as impact from legal_case" + join
                + where.sql + " group by " + key + " order by label nulls last, k nulls last";
        return jdbc.sql(sql).params(where.params).query((rs, i) -> new Group(rs.getString("k"), rs.getString("label"),
                rs.getLong("n"), rs.getBigDecimal("claimed").setScale(3), rs.getBigDecimal("impact").setScale(3))).list();
    }


    /**
     * The matching visible cases grouped by the primary involvement's sector, department and section at once: the same groups
     * {@link #groups} gives for each of the three dimensions, from one pass over the cases instead of three.
     */
    public Map<UnitLevel, List<Group>> unitGroups(CaseFilter filter) {
        Where where = where(filter, "legal_case.");
        String sql = "select grouping(x.sector, x.department, x.section) as g, x.sector, x.department, x.section, count(*) as n,"
                + " coalesce(sum(x.claimed), 0) as claimed, coalesce(sum(x.impact), 0) as impact from ("
                + "select nullif(btrim(pi.sector_name_snapshot), '') as sector, nullif(btrim(pi.department_name_snapshot), '') as department,"
                + " nullif(btrim(pi.section_name_snapshot), '') as section, legal_case.claimed_amount as claimed,"
                + " legal_case.financial_amount as impact from legal_case left join case_org_involvement pi on"
                + " pi.case_id = legal_case.id and pi.is_primary and pi.removed_at is null" + where.sql + ") x"
                + " group by grouping sets ((x.sector), (x.department), (x.section))"
                + " order by g, coalesce(x.sector, x.department, x.section) nulls last";
        Map<UnitLevel, List<Group>> out = new java.util.EnumMap<>(UnitLevel.class);
        for (UnitLevel level : UnitLevel.values()) {
            out.put(level, new java.util.ArrayList<>());
        }
        jdbc.sql(sql).params(where.params).query(rs -> {
            // grouping() bit set = the column is NOT grouped: 3 = sector, 5 = department, 6 = section.
            int g = rs.getInt("g");
            UnitLevel level = g == 3 ? UnitLevel.SECTOR : g == 5 ? UnitLevel.DEPARTMENT : UnitLevel.SECTION;
            String key = rs.getString(level == UnitLevel.SECTOR ? "sector" : level == UnitLevel.DEPARTMENT ? "department" : "section");
            out.get(level).add(new Group(key, key, rs.getLong("n"), rs.getBigDecimal("claimed").setScale(3),
                    rs.getBigDecimal("impact").setScale(3)));
        });
        return out;
    }

    /** A member's cases in the filter: as primary member (of which open, by {@link StatusGroup}) and as alternate. */
    public record MemberCases(UUID memberId, long primary, long primaryOpen, long alternate) {
    }

    /** The matching visible cases per member; objective counts of assignment, nothing about their outcome. */
    public List<MemberCases> memberCases(CaseFilter filter) {
        Where where = where(filter, "legal_case.");
        Map<String, Object> params = new HashMap<>(where.params);
        params.put("openStatuses", StatusGroup.OPEN.statuses().stream().map(Enum::name).toList());
        String selected = "select %s as member, %s as p, %s as o, %s as a from legal_case" + where.sql + " and %s is not null";
        String sql = "select member, sum(p) as primary_n, sum(o) as open_n, sum(a) as alt_n from ("
                + selected.formatted("legal_case.primary_member_id", "1", "(case when legal_case.status in (:openStatuses) then 1 else 0 end)",
                        "0", "legal_case.primary_member_id")
                + " union all "
                + selected.formatted("legal_case.alternate_member_id", "0", "0", "1", "legal_case.alternate_member_id")
                + ") x group by member";
        return jdbc.sql(sql).params(params).query((rs, i) -> new MemberCases(rs.getObject("member", UUID.class),
                rs.getLong("primary_n"), rs.getLong("open_n"), rs.getLong("alt_n"))).list();
    }

    private record Where(String sql, Map<String, Object> params) {
    }

    private Where where(CaseFilter filter) {
        return where(filter, "");
    }

    /** The conditions of the filter; {@code prefix} (such as {@code "legal_case."}) qualifies every case column. */
    private Where where(CaseFilter filter, String prefix) {
        CaseAccessPolicy.Predicate visible = access.viewable("legal_case");
        StringBuilder sql = new StringBuilder(" where " + visible.sql());
        Map<String, Object> params = new HashMap<>(visible.params());
        if (filter.text() != null) {
            // Only a selective text uses the trigram index; a text most cases contain is just as well read in one pass.
            if (Text.fewTrigramCandidates(jdbc, filter.text())) {
                sql.append(Text.trigramPrefilter(filter.text(), prefix + "id", "textRaw"));
                params.put("textRaw", filter.text());
            }
            sql.append(" and (" + prefix + "reference_no ilike :text escape '\\' or " + prefix
                    + "title ilike :text escape '\\')");
            params.put("text", "%" + Text.escapeLike(filter.text()) + "%");
        }
        if (!filter.statuses().isEmpty()) {
            sql.append(" and " + prefix + "status in (:statuses)");
            params.put("statuses", filter.statuses().stream().map(Enum::name).toList());
        }
        if (!filter.types().isEmpty()) {
            sql.append(" and " + prefix + "case_type in (:types)");
            params.put("types", filter.types().stream().map(Enum::name).toList());
        }
        if (filter.openedFrom() != null) {
            sql.append(" and " + prefix + "opened_on >= :openedFrom");
            params.put("openedFrom", filter.openedFrom());
        }
        if (filter.openedTo() != null) {
            sql.append(" and " + prefix + "opened_on <= :openedTo");
            params.put("openedTo", filter.openedTo());
        }
        if (filter.onlyCases() != null) {
            sql.append(" and " + prefix + "id in (select unnest(cast(:onlyCases as uuid[])))");
            params.put("onlyCases", uuidArray(filter.onlyCases()));
        }
        for (CaseAccessPolicy.Predicate condition : filter.extra()) {
            sql.append(" and (").append(condition.sql()).append(')');
            params.putAll(condition.params());
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
