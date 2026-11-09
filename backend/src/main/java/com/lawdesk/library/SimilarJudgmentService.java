package com.lawdesk.library;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.cases.CaseAccessPolicy;
import com.lawdesk.library.LibraryViews.Basis;
import com.lawdesk.library.LibraryViews.Difference;
import com.lawdesk.library.LibraryViews.JudgmentRef;
import com.lawdesk.library.LibraryViews.Reason;
import com.lawdesk.library.LibraryViews.Suggestion;
import com.lawdesk.library.LibraryViews.Suggestions;

/**
 * Judgments that look like a case (requirements section 16.5, acceptance scenario S5). It is not generative and uses no
 * provider: a fixed, documented scoring over what the case and the library already record, and every point is explained
 * by a reason that names the shared words, keywords, classification or court level. The same case and library always give
 * the same list in the same order. Whether a suggestion is useful is for the member, who links it by hand.
 *
 * <p>Scores: same type of case 20, same court level 10, each shared classification 15 (at most three), each shared
 * keyword 10 (at most four), each shared word 5 (at most eight), each word shared with a principle 5 (at most four),
 * subject similarity up to 50. A type or a level alone never makes a suggestion: some shared content is required.
 * The defences of the case (work package 2) are not among the sources yet; {@code basis.defensesIncluded} says so.
 */
@Service
class SimilarJudgmentService {

    static final int SAME_CASE_TYPE = 20;
    static final int SAME_COURT_LEVEL = 10;
    static final int PER_CLASSIFICATION = 15;
    static final int PER_KEYWORD = 10;
    static final int PER_TERM = 5;
    static final int PER_PRINCIPLE_TERM = 5;
    static final double SIMILARITY_FLOOR = 0.15;
    static final int MAX_PROFILE_TERMS = 40;
    static final String NOTE = "Suggestions are found by a fixed comparison of words, classifications and court levels,"
            + " not by judgment of meaning. Read the judgment itself before relying on it.";

    private final JdbcClient jdbc;
    private final CaseAccessPolicy access;

    SimilarJudgmentService(JdbcClient jdbc, CaseAccessPolicy access) {
        this.jdbc = jdbc;
        this.access = access;
    }

    private record Candidate(JudgmentRef ref, String courtLevel, String caseType, String docText, List<String> keywords,
            List<String> classLabels, String principleText, double similarity, List<String> relationTypes) {
    }

    @Transactional(readOnly = true)
    Suggestions forCase(UUID caseId, int limit) {
        access.requireView(caseId);
        Map<String, Object> profile = jdbc.sql("select case_type, library_normalize(title) as t, library_normalize(subject)"
                        + " as s, library_normalize(facts) as f, library_normalize(title || ' ' || coalesce(subject, '')"
                        + " || ' ' || coalesce(facts, '')) as whole from legal_case where id = :id")
                .param("id", caseId).query((rs, n) -> Map.<String, Object>of("caseType", rs.getString("case_type"),
                        "t", rs.getString("t"), "s", rs.getString("s"), "f", rs.getString("f"),
                        "whole", rs.getString("whole"))).single();
        String whole = (String) profile.get("whole");
        String caseType = (String) profile.get("caseType");
        List<String> terms = LibraryText.terms(profile.get("t") + " " + profile.get("s") + " " + profile.get("f"),
                MAX_PROFILE_TERMS);
        List<String> levels = jdbc.sql("select distinct c.level from proceeding p join court c on c.id = p.court_id"
                + " where p.case_id = :id order by 1").param("id", caseId).query(String.class).list();
        Set<UUID> linked = new HashSet<>(jdbc.sql("select library_judgment_id from case_precedent_link where case_id = :id"
                + " and removed_at is null").param("id", caseId).query(UUID.class).list());
        Basis basis = new Basis(terms, caseType, levels, false);
        if (whole.isEmpty()) {
            return new Suggestions(caseId, basis, List.of(), NOTE);
        }
        String padded = " " + whole + " ";
        List<Candidate> candidates = jdbc.sql("select " + CasePrecedentService.REF_COLUMNS + ", j.keywords,"
                        + " library_normalize(j.subject || ' ' || coalesce(j.keywords, '') || ' ' || coalesce(j.summary, '')"
                        + " || ' ' || coalesce(j.legal_texts, '')) as doc_text,"
                        + " (select coalesce(array_agg(library_normalize(k)), cast('{}' as text[])) from"
                        + " unnest(string_to_array(coalesce(j.keywords, ''), E'\\n')) k where k <> '') as kw,"
                        + " (select coalesce(array_agg(library_normalize(c.label_ar)), cast('{}' as text[]))"
                        + " from library_judgment_class jc join library_classification c on c.id = jc.classification_id"
                        + " where jc.judgment_id = j.id) as class_labels,"
                        + " (select library_normalize(string_agg(p.principle_text || ' ' || coalesce(p.legal_topic, '')"
                        + " || ' ' || coalesce(p.legal_basis, ''), ' ')) from library_principle p where p.judgment_id ="
                        + " j.id) as principle_text, library_similarity(j.trigrams, library_trigrams(:whole)) as sim,"
                        + " array(select distinct r.relation_type from library_judgment_relation r where r.judgment_id ="
                        + " j.id and r.removed_at is null order by 1) as relation_types"
                        + " from library_judgment j where j.status = 'ACTIVE' and (j.search_vector @@ to_tsquery('simple',"
                        + " cast(:orq as text)) or exists (select 1 from library_principle p where p.search_vector @@"
                        + " to_tsquery('simple', cast(:orq as text)) and p.judgment_id = j.id)"
                        + " or library_similarity(j.trigrams, library_trigrams(:whole)) >= :floor"
                        + " or exists (select 1 from library_judgment_class jc join library_classification c on c.id ="
                        + " jc.classification_id where jc.judgment_id = j.id and position(' ' || library_normalize(c.label_ar)"
                        + " || ' ' in :padded) > 0))")
                .param("whole", whole).param("orq", LibraryText.anyOf(terms)).param("floor", SIMILARITY_FLOOR)
                .param("padded", padded).query((rs, n) -> {
                    List<String> keywords = new ArrayList<>();
                    for (Object k : (Object[]) rs.getArray("kw").getArray()) {
                        keywords.add((String) k);
                    }
                    List<String> labels = new ArrayList<>();
                    for (Object k : (Object[]) rs.getArray("class_labels").getArray()) {
                        labels.add((String) k);
                    }
                    List<String> relationTypes = new ArrayList<>();
                    for (Object k : (Object[]) rs.getArray("relation_types").getArray()) {
                        relationTypes.add((String) k);
                    }
                    return new Candidate(CasePrecedentService.ref(rs), rs.getString("court_level"),
                            rs.getString("case_type"), rs.getString("doc_text"), keywords, labels,
                            rs.getString("principle_text"), rs.getDouble("sim"), relationTypes);
                }).list();

        List<Suggestion> items = new ArrayList<>();
        for (Candidate c : candidates) {
            Suggestion s = score(c, terms, padded, caseType, levels, linked.contains(c.ref().id()));
            if (s != null) {
                items.add(s);
            }
        }
        items.sort(Comparator.comparingInt(Suggestion::score).reversed()
                .thenComparing((Suggestion s) -> s.judgment().judgmentDate(),
                        Comparator.nullsLast(Comparator.<LocalDate>reverseOrder()))
                .thenComparing(s -> s.judgment().id()));
        return new Suggestions(caseId, basis, items.stream().limit(limit).toList(), NOTE);
    }

    private Suggestion score(Candidate c, List<String> terms, String padded, String caseType, List<String> levels,
            boolean alreadyLinked) {
        List<Reason> reasons = new ArrayList<>();
        List<Difference> differences = new ArrayList<>();
        int content = 0;

        List<String> classes = c.classLabels().stream().filter(l -> !l.isEmpty() && padded.contains(" " + l + " "))
                .limit(3).toList();
        if (!classes.isEmpty()) {
            reasons.add(new Reason("SHARED_CLASSIFICATION", PER_CLASSIFICATION * classes.size(), classes));
            content += PER_CLASSIFICATION * classes.size();
        }
        List<String> keywords = c.keywords().stream().filter(k -> !k.isEmpty() && padded.contains(" " + k + " "))
                .limit(4).toList();
        if (!keywords.isEmpty()) {
            reasons.add(new Reason("KEYWORD_MATCH", PER_KEYWORD * keywords.size(), keywords));
            content += PER_KEYWORD * keywords.size();
        }
        Set<String> docWords = new HashSet<>(LibraryText.words(c.docText()));
        List<String> shared = terms.stream().filter(docWords::contains).limit(8).toList();
        if (!shared.isEmpty()) {
            reasons.add(new Reason("TEXT_MATCH", PER_TERM * shared.size(), shared));
            content += PER_TERM * shared.size();
        }
        Set<String> principleWords = new HashSet<>(LibraryText.words(c.principleText()));
        List<String> inPrinciples = terms.stream().filter(principleWords::contains).limit(4).toList();
        if (!inPrinciples.isEmpty()) {
            reasons.add(new Reason("PRINCIPLE_MATCH", PER_PRINCIPLE_TERM * inPrinciples.size(), inPrinciples));
            content += PER_PRINCIPLE_TERM * inPrinciples.size();
        }
        if (c.similarity() >= SIMILARITY_FLOOR) {
            int points = (int) Math.round(c.similarity() * 50);
            reasons.add(new Reason("SUBJECT_SIMILARITY", points, List.of(String.format("%.2f", c.similarity()))));
            content += points;
        }
        if (content == 0) {
            return null;
        }
        int total = content;
        if (caseType != null && c.caseType() != null) {
            if (caseType.equals(c.caseType())) {
                reasons.add(new Reason("SAME_CASE_TYPE", SAME_CASE_TYPE, List.of(caseType)));
                total += SAME_CASE_TYPE;
            } else {
                differences.add(new Difference("DIFFERENT_CASE_TYPE", c.caseType()));
            }
        }
        if (!levels.isEmpty() && c.courtLevel() != null) {
            if (levels.contains(c.courtLevel())) {
                reasons.add(new Reason("SAME_COURT_LEVEL", SAME_COURT_LEVEL, List.of(c.courtLevel())));
                total += SAME_COURT_LEVEL;
            } else {
                differences.add(new Difference("DIFFERENT_COURT_LEVEL", c.courtLevel()));
            }
        }
        List<String> warnings = new ArrayList<>();
        if (!c.relationTypes().isEmpty()) {
            warnings.add("LATER_RELATION_EXISTS");
        }
        if ("NOT_FINAL".equals(c.ref().finality())) {
            warnings.add("NOT_FINAL");
        } else if ("NOT_SPECIFIED".equals(c.ref().finality())) {
            warnings.add("FINALITY_NOT_SPECIFIED");
        }
        return new Suggestion(c.ref(), total, reasons, differences, c.relationTypes(), warnings, alreadyLinked);
    }
}
