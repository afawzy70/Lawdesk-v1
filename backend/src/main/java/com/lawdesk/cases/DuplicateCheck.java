package com.lawdesk.cases;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Warns when a case may be registered twice (requirements section 3). It compares the court, the court's case number and
 * year, the judicial automated number and the title; it never blocks by itself and never merges anything: a registrar who
 * says the case is different (a new stage, an independent claim) goes on, and the decision is kept. Cases the asker may
 * not see are counted, not described, so the warning cannot be used to read them.
 */
@Component
class DuplicateCheck {

    static final String EXACT = "EXACT";
    static final String SIMILAR = "SIMILAR";

    /** What is known of the case being registered; any of it may be absent. */
    record Candidate(String automatedNo, UUID courtId, String courtCaseNumber, Integer courtCaseYear, String title) {
    }

    record Hit(UUID caseId, String referenceNo, String title, String kind, List<String> reasons) {
    }

    /** {@code visible} are described; {@code hidden} only counted. */
    record Result(List<Hit> visible, int hidden) {

        boolean isEmpty() {
            return visible.isEmpty() && hidden == 0;
        }
    }

    private final JdbcClient jdbc;
    private final CaseAccessPolicy access;

    DuplicateCheck(JdbcClient jdbc, CaseAccessPolicy access) {
        this.jdbc = jdbc;
        this.access = access;
    }

    /** A case that matches, whoever may see it. */
    record Match(UUID caseId, String kind, List<String> reasons) {
    }

    Result find(Candidate candidate) {
        return describe(matches(candidate));
    }

    List<Match> matches(Candidate candidate) {
        Map<UUID, Found> found = new LinkedHashMap<>();
        if (candidate.courtId() != null && candidate.courtCaseNumber() != null && candidate.courtCaseYear() != null) {
            jdbc.sql("""
                            select distinct case_id from proceeding
                            where court_id = :court and court_case_year = :year
                              and lower(court_case_number) = lower(btrim(:number))""")
                    .param("court", candidate.courtId()).param("year", candidate.courtCaseYear())
                    .param("number", candidate.courtCaseNumber()).query(UUID.class).list()
                    .forEach(id -> found.computeIfAbsent(id, Found::new).add(EXACT, "COURT_CASE_NUMBER"));
        }
        if (candidate.automatedNo() != null) {
            jdbc.sql("select id from legal_case where lower(btrim(automated_no)) = lower(btrim(:no))")
                    .param("no", candidate.automatedNo()).query(UUID.class).list()
                    .forEach(id -> found.computeIfAbsent(id, Found::new).add(SIMILAR, "AUTOMATED_NO"));
        }
        if (candidate.title() != null) {
            jdbc.sql("select id from legal_case where lower(btrim(title)) = lower(btrim(:title))")
                    .param("title", candidate.title()).query(UUID.class).list()
                    .forEach(id -> found.computeIfAbsent(id, Found::new).add(SIMILAR, "TITLE"));
        }
        return found.values().stream().map(f -> new Match(f.id, f.kind(), List.copyOf(f.reasons))).toList();
    }

    /** Describes the matches the asker may see and counts the others. */
    Result describe(List<Match> matches) {
        if (matches.isEmpty()) {
            return new Result(List.of(), 0);
        }
        CaseAccessPolicy.Predicate visible = access.viewable("c");
        Map<String, Object> params = new HashMap<>(visible.params());
        params.put("ids", matches.stream().map(Match::caseId).toArray(UUID[]::new));
        Map<UUID, String[]> seen = new LinkedHashMap<>();
        jdbc.sql("select c.id, c.reference_no, c.title from legal_case c where c.id = any (:ids) and " + visible.sql())
                .params(params).query(rs -> {
                    seen.put(rs.getObject("id", UUID.class),
                            new String[] {rs.getString("reference_no"), rs.getString("title")});
                });
        List<Hit> hits = new ArrayList<>();
        for (Match match : matches) {
            String[] details = seen.get(match.caseId());
            if (details != null) {
                hits.add(new Hit(match.caseId(), details[0], details[1], match.kind(), match.reasons()));
            }
        }
        return new Result(hits, matches.size() - hits.size());
    }

    private static final class Found {
        final UUID id;
        final List<String> reasons = new ArrayList<>();
        private boolean exact;

        Found(UUID id) {
            this.id = id;
        }

        void add(String kind, String reason) {
            exact = exact || EXACT.equals(kind);
            if (!reasons.contains(reason)) {
                reasons.add(reason);
            }
        }

        String kind() {
            return exact ? EXACT : SIMILAR;
        }
    }
}
