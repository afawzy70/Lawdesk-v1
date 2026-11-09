package com.lawdesk.search;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.api.FieldErrorException;
import com.lawdesk.cases.CaseAccessPolicy;
import com.lawdesk.library.LibraryText;
import com.lawdesk.search.SearchRequests.Group;
import com.lawdesk.search.SearchRequests.Item;
import com.lawdesk.search.SearchSpecs.Part;
import com.lawdesk.search.SearchSpecs.Spec;
import com.lawdesk.security.CurrentActor;
import com.lawdesk.security.Permission;

/**
 * The unified search (LD-057, requirements section 28). PostgreSQL full text over the Arabic normal form of WP7
 * ({@code library_normalize}); every word of the query must match as the beginning of a word of the record. Results are
 * grouped by type and ranked within a group by where the words occur (title and numbers above facts and notes).
 *
 * <p>Search is a way to read, so it follows the same rules as reading (owner decision D3): each case-bound record is
 * restricted with {@code case_visible} before it is matched, a total counts only what the user may see, and a snippet is cut
 * from a record the user may see. A user finds the other types (hearings, judgments, documents, notes, tasks, library,
 * settlements, directives) only with {@code SEARCH_ALL} and the view permission of that type.
 */
@Service
class SearchService {

    private final JdbcClient jdbc;
    private final CaseAccessPolicy access;
    private final CurrentActor currentActor;

    SearchService(JdbcClient jdbc, CaseAccessPolicy access, CurrentActor currentActor) {
        this.jdbc = jdbc;
        this.access = access;
        this.currentActor = currentActor;
    }

    @Transactional(readOnly = true)
    SearchRequests.Result search(SearchRequests.Query query) {
        if (query.q() == null) {
            throw new FieldErrorException("q", "NotBlank");
        }
        for (String type : query.types()) {
            if (!SearchSpecs.ORDER.contains(type)) {
                throw new FieldErrorException("types", "Pattern");
            }
        }
        Map<String, Spec> specs = SearchSpecs.all(access, currentActor.has(Permission.PARTY_ID_VIEW));
        boolean everyType = currentActor.has(Permission.SEARCH_ALL);
        List<String> searched = new ArrayList<>();
        for (String type : SearchSpecs.ORDER) {
            Spec spec = specs.get(type);
            boolean allowed = currentActor.has(spec.permission()) && (everyType || "CASE".equals(type) || "PARTY".equals(type));
            boolean wanted = query.types().isEmpty() || query.types().contains(type);
            boolean applicable = spec.caseIdSql() != null || query.caseId() == null;
            if (allowed && wanted && applicable) {
                searched.add(type);
            }
        }
        String normalised = jdbc.sql("select library_normalize(:q)").param("q", query.q()).query(String.class).single();
        String tsQuery = LibraryText.allOfPrefixes(normalised);
        if (tsQuery == null) {
            return new SearchRequests.Result(query.q(), 0, searched, List.of());
        }
        Map<String, Object> params = new HashMap<>(access.viewable("c").params());
        params.put("tsq", tsQuery);
        params.put("q", query.q());
        params.put("canId", currentActor.has(Permission.PARTY_ID_VIEW));
        params.put("limit", query.sizeOrDefault());
        params.put("offset", (long) query.pageOrDefault() * query.sizeOrDefault());
        List<Group> groups = new ArrayList<>();
        long total = 0;
        for (String type : searched) {
            Group group = run(specs.get(type), query, params);
            if (group.total() > 0) {
                groups.add(group);
                total += group.total();
            }
        }
        return new SearchRequests.Result(query.q(), total, searched, groups);
    }

    private Group run(Spec spec, SearchRequests.Query query, Map<String, Object> shared) {
        Map<String, Object> params = new HashMap<>(shared);
        StringBuilder caseFilters = new StringBuilder();
        if (query.caseType() != null) {
            caseFilters.append(" and ").append(spec.caseTypeSql());
            params.put("caseType", query.caseType());
        }
        if (query.caseId() != null) {
            caseFilters.append(" and ").append(spec.caseIdSql());
            params.put("caseId", query.caseId());
        }
        boolean lateral = spec.from().contains("%CASE_FILTERS%");
        String from = spec.from().replace("%CASE_FILTERS%", lateral ? caseFilters.toString() : "");
        StringBuilder where = new StringBuilder(spec.condition());
        if (!lateral) {
            where.append(caseFilters);
        }
        if (query.from() != null) {
            where.append(" and ").append(spec.date()).append(" >= :dateFrom");
            params.put("dateFrom", query.from());
        }
        if (query.to() != null) {
            where.append(" and ").append(spec.date()).append(" <= :dateTo");
            params.put("dateTo", query.to());
        }
        if (spec.stored() != null) {
            return runStored(spec, query, params, from, where.toString());
        }
        List<Part> parts = spec.parts();
        StringBuilder columns = new StringBuilder();
        StringBuilder vector = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            columns.append(", ").append(parts.get(i).sql()).append(" as p").append(i);
            vector.append(i == 0 ? "" : " || ").append("setweight(to_tsvector('simple', library_normalize(b.p").append(i)
                    .append(")), '").append(parts.get(i).weight()).append("')");
        }
        String base = "select " + spec.id() + " as id, " + spec.caseId() + " as case_id, " + spec.caseRef() + " as case_ref, "
                + spec.title() + " as title, " + spec.detail() + " as detail, " + spec.date() + " as rec_date, " + spec.extra()
                + " as extra" + columns + " from " + from + " where " + where;
        String scored = "with base as materialized (" + base + "), scored as materialized (select b.*, " + vector
                + " as vec from base b), q as (select to_tsquery('simple', cast(:tsq as text)) as tq) ";
        StringBuilder flags = new StringBuilder();
        StringBuilder snippet = new StringBuilder("case");
        for (int i = 0; i < parts.size(); i++) {
            flags.append(", to_tsvector('simple', library_normalize(s.p").append(i).append(")) @@ q.tq as m").append(i);
            snippet.append(" when r.m").append(i).append(" then r.p").append(i);
        }
        snippet.append(" else r.p0 end");
        // One pass: the page and the total of all matches (count(*) over () is computed before offset and limit). Scoring
        // every visible record is the expensive part, so it is done once, not once for the count and again for the page.
        String page = ", page as (select s.*, ts_rank_cd(s.vec, q.tq) as rank, count(*) over () as total_n" + flags
                + " from scored s cross join q where s.vec @@ q.tq order by rank desc, s.rec_date desc nulls last, s.id"
                + " offset :offset limit :limit) ";
        String sql = scored.substring(0, scored.length() - 1) + page + "select r.id, r.case_id, r.case_ref, r.title, r.detail,"
                + " r.rec_date, r.extra, r.rank, r.total_n, search_snippet(" + snippet + ", :q) as snippet"
                + matchedColumns(parts.size()) + " from page r order by r.rank desc, r.rec_date desc nulls last, r.id";
        long[] counted = {-1};
        List<Item> items = jdbc.sql(sql).params(params).query((rs, n) -> {
            counted[0] = rs.getLong("total_n");
            return item(rs, spec, rs.getDouble("rank"));
        }).list();
        long total = counted[0];
        if (items.isEmpty()) {
            // Nothing on this page: either nothing matches, or the page is past the end and the total is still needed.
            total = query.pageOrDefault() == 0 ? 0 : jdbc.sql(scored + "select count(*) from scored s cross join q where s.vec @@ q.tq")
                    .params(params).query(Long.class).single();
        }
        if (total == 0) {
            return new Group(spec.type(), 0, query.pageOrDefault(), query.sizeOrDefault(), List.of());
        }
        return new Group(spec.type(), total, query.pageOrDefault(), query.sizeOrDefault(), items);
    }

    /**
     * The stored-vector path (V801): candidates come from a GIN index, are restricted by the access rule and ranked, and only the
     * page's rows are read again for their matched-in flags and snippet. Two statements per type, whatever the register's size.
     */
    private Group runStored(Spec spec, SearchRequests.Query query, Map<String, Object> params, String from, String where) {
        SearchSpecs.Stored stored = spec.stored();
        String with = "with q as (select to_tsquery('simple', cast(:tsq as text)) as tq)"
                + (stored.with() == null ? "" : ", " + stored.with()) + " ";
        String matching = " from " + from + stored.from() + " cross join q where " + where + " and " + spec.id() + " in ("
                + stored.matchIds() + ") and " + stored.vector() + " @@ q.tq";
        String pageSql = with + "select s.id, s.rank, s.total_n from (select " + spec.id() + " as id, " + spec.date()
                + " as rec_date, ts_rank_cd(" + stored.vector() + ", q.tq) as rank, count(*) over () as total_n" + matching
                + ") s order by s.rank desc, s.rec_date desc nulls last, s.id offset :offset limit :limit";
        List<UUID> ids = new ArrayList<>();
        Map<UUID, Double> ranks = new HashMap<>();
        long[] counted = {-1};
        jdbc.sql(pageSql).params(params).query(rs -> {
            UUID id = rs.getObject("id", UUID.class);
            ids.add(id);
            ranks.put(id, rs.getDouble("rank"));
            counted[0] = rs.getLong("total_n");
        });
        long total = counted[0];
        if (ids.isEmpty()) {
            // Nothing on this page: either nothing matches, or the page is past the end and the total is still needed.
            total = query.pageOrDefault() == 0 ? 0
                    : jdbc.sql(with + "select count(*)" + matching).params(params).query(Long.class).single();
            return new Group(spec.type(), total, query.pageOrDefault(), query.sizeOrDefault(), List.of());
        }
        List<Part> parts = spec.parts();
        StringBuilder columns = new StringBuilder();
        StringBuilder flags = new StringBuilder();
        StringBuilder snippet = new StringBuilder("case");
        for (int i = 0; i < parts.size(); i++) {
            columns.append(", ").append(parts.get(i).sql()).append(" as p").append(i);
            flags.append(", to_tsvector('simple', library_normalize(b.p").append(i).append(")) @@ q.tq as m").append(i);
            snippet.append(" when r.m").append(i).append(" then r.p").append(i);
        }
        snippet.append(" else r.p0 end");
        Map<String, Object> rowParams = new HashMap<>(params);
        rowParams.put("ids", ids.stream().map(UUID::toString).collect(java.util.stream.Collectors.joining(",", "{", "}")));
        String rowsSql = "with q as (select to_tsquery('simple', cast(:tsq as text)) as tq), base as (select " + spec.id()
                + " as id, " + spec.caseId() + " as case_id, " + spec.caseRef() + " as case_ref, " + spec.title() + " as title, "
                + spec.detail() + " as detail, " + spec.date() + " as rec_date, " + spec.extra() + " as extra" + columns
                + " from " + from + " where " + spec.id() + " in (select unnest(cast(:ids as uuid[]))) and " + where
                + "), page as (select b.*" + flags + " from base b cross join q) select r.id, r.case_id, r.case_ref, r.title,"
                + " r.detail, r.rec_date, r.extra, search_snippet(" + snippet + ", :q) as snippet" + matchedColumns(parts.size())
                + " from page r";
        Map<UUID, Item> byId = new HashMap<>();
        jdbc.sql(rowsSql).params(rowParams).query((rs, n) -> {
            Item item = item(rs, spec, ranks.get(rs.getObject("id", UUID.class)));
            byId.put(item.id(), item);
            return item;
        }).list();
        // The statement returned the page's rows in any order; the order of the first statement (rank, date, id) is the answer.
        List<Item> items = ids.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
        return new Group(spec.type(), total, query.pageOrDefault(), query.sizeOrDefault(), items);
    }

    private static String matchedColumns(int count) {
        StringBuilder columns = new StringBuilder();
        for (int i = 0; i < count; i++) {
            columns.append(", r.m").append(i);
        }
        return columns.toString();
    }

    private static Item item(ResultSet rs, Spec spec, double rank) throws SQLException {
        List<String> matched = new ArrayList<>();
        for (int i = 0; i < spec.parts().size(); i++) {
            if (rs.getBoolean("m" + i)) {
                matched.add(spec.parts().get(i).key());
            }
        }
        int extra = rs.getInt("extra");
        Integer caseCount = rs.wasNull() ? null : extra;
        return new Item(rs.getObject("id", UUID.class), spec.type(), rs.getObject("case_id", UUID.class),
                rs.getString("case_ref"), rs.getString("title"), rs.getString("detail"),
                rs.getObject("rec_date", LocalDate.class), rs.getString("snippet"), matched,
                Math.round(rank * 1000.0) / 1000.0, caseCount);
    }
}
