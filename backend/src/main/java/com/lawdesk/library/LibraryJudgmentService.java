package com.lawdesk.library;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.api.BusinessRuleException;
import com.lawdesk.api.ConflictException;
import com.lawdesk.api.NotFoundException;
import com.lawdesk.audit.AuditLog;
import com.lawdesk.cases.CaseAccessPolicy;
import com.lawdesk.library.LibraryViews.Classification;
import com.lawdesk.library.LibraryViews.Judgment;
import com.lawdesk.library.LibraryViews.LinkedCase;
import com.lawdesk.library.LibraryViews.ListItem;
import com.lawdesk.library.LibraryViews.Relations;
import com.lawdesk.library.LibraryViews.Validity;
import com.lawdesk.security.ActorRef;
import com.lawdesk.security.CurrentActor;
import com.lawdesk.security.Permission;

/**
 * Judgments of the library (LD-052, requirements section 16): registering, reading, replacing, withdrawing, and the
 * Arabic search over them. The library is department-wide reference data; the only case data it ever shows is the list of
 * cases a judgment is linked to, restricted to the cases the signed-in user may see.
 */
@Service
class LibraryJudgmentService {

    static final String DATE_IN_FUTURE = "LIBRARY_DATE_IN_FUTURE";
    static final String CLASSIFICATION_UNKNOWN = "LIBRARY_CLASSIFICATION_UNKNOWN";
    static final String DUPLICATE_SUSPECTED = "LIBRARY_DUPLICATE_SUSPECTED";
    static final String ALREADY_WITHDRAWN = "LIBRARY_ALREADY_WITHDRAWN";
    static final String NOT_WITHDRAWN = "LIBRARY_NOT_WITHDRAWN";

    /** A trigram similarity from which a misspelled search still finds a judgment. */
    static final double FUZZY_THRESHOLD = 0.2;

    private static final ZoneId KUWAIT = ZoneId.of("Asia/Kuwait");
    private static final String COLUMNS = "id, court_name, court_level, chamber, case_number, case_year, judgment_date,"
            + " case_type, subject, parties_description, summary, operative_text, reasons, legal_texts, requests_addressed,"
            + " request_results, direction, keywords, finality, confidentiality, source_reference, verification_note,"
            + " copy_reference, status, withdrawn_reason, withdrawn_at, version, created_at, created_by, updated_at,"
            + " updated_by";

    private final JdbcClient jdbc;
    private final CurrentActor currentActor;
    private final AuditLog audit;
    private final CaseAccessPolicy access;
    private final LibraryPrincipleService principles;
    private final LibraryRelationService relations;

    LibraryJudgmentService(JdbcClient jdbc, CurrentActor currentActor, AuditLog audit, CaseAccessPolicy access,
            LibraryPrincipleService principles, LibraryRelationService relations) {
        this.jdbc = jdbc;
        this.currentActor = currentActor;
        this.audit = audit;
        this.access = access;
        this.principles = principles;
        this.relations = relations;
    }

    // ---- reads ----------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    Judgment get(UUID id) {
        return load(id);
    }

    private Judgment load(UUID id) {
        Map<String, Object> row = jdbc.sql("select " + COLUMNS + " from library_judgment where id = :id").param("id", id)
                .query((rs, n) -> raw(rs)).optional().orElseThrow(() -> new NotFoundException("judgment"));
        return build(id, row);
    }

    private Judgment build(UUID id, Map<String, Object> r) {
        boolean masked = "RESTRICTED".equals(r.get("confidentiality")) && !currentActor.has(Permission.LIBRARY_MANAGE);
        Relations rel = relations.of(id);
        return new Judgment(id, (String) r.get("court_name"), (String) r.get("court_level"), (String) r.get("chamber"),
                (String) r.get("case_number"), (Integer) r.get("case_year"), (LocalDate) r.get("judgment_date"),
                (String) r.get("case_type"), (String) r.get("subject"),
                masked ? null : (String) r.get("parties_description"), masked, (String) r.get("summary"),
                (String) r.get("operative_text"), (String) r.get("reasons"), (String) r.get("legal_texts"),
                (String) r.get("requests_addressed"), (String) r.get("request_results"), (String) r.get("direction"),
                LibraryText.keywordsFromColumn((String) r.get("keywords")), (String) r.get("finality"),
                (String) r.get("confidentiality"), (String) r.get("source_reference"),
                (String) r.get("verification_note"), (String) r.get("copy_reference"), (String) r.get("status"),
                (String) r.get("withdrawn_reason"), (Instant) r.get("withdrawn_at"), classificationsOf(id),
                principles.of(id), rel, relations.validity(id, (String) r.get("finality"), (String) r.get("status"), rel),
                linkedCases(id), (Long) r.get("version"), (Instant) r.get("created_at"), (UUID) r.get("created_by"),
                (Instant) r.get("updated_at"), (UUID) r.get("updated_by"));
    }

    private static Map<String, Object> raw(ResultSet rs) throws SQLException {
        Map<String, Object> m = new HashMap<>();
        for (String c : COLUMNS.split(", ")) {
            String column = c.strip();
            Object value = switch (column) {
                case "id", "created_by", "updated_by" -> rs.getObject(column, UUID.class);
                case "case_year" -> rs.getObject(column) == null ? null : rs.getInt(column);
                case "judgment_date" -> rs.getObject(column, LocalDate.class);
                case "version" -> rs.getLong(column);
                case "withdrawn_at", "created_at", "updated_at" ->
                    rs.getTimestamp(column) == null ? null : rs.getTimestamp(column).toInstant();
                default -> rs.getString(column);
            };
            m.put(column, value);
        }
        return m;
    }

    List<Classification> classificationsOf(UUID judgmentId) {
        return jdbc.sql("select c.id, c.dimension, c.code, c.label_ar, c.label_en, c.origin, c.active, c.sort_order,"
                        + " c.version, c.updated_at from library_judgment_class jc join library_classification c"
                        + " on c.id = jc.classification_id where jc.judgment_id = :id order by c.dimension, c.sort_order,"
                        + " c.code")
                .param("id", judgmentId).query(ClassificationService::row).list();
    }

    /** The cases this judgment is used in, restricted to those the signed-in user may see. */
    private List<LinkedCase> linkedCases(UUID judgmentId) {
        CaseAccessPolicy.Predicate visible = access.visibleCaseIds("l.case_id");
        Map<String, Object> params = new HashMap<>(visible.params());
        params.put("id", judgmentId);
        return jdbc.sql("select l.id, l.case_id, c.reference_no, c.title, l.reason, l.created_at from case_precedent_link l"
                        + " join legal_case c on c.id = l.case_id where l.library_judgment_id = :id and l.removed_at is null"
                        + " and " + visible.sql() + " order by l.created_at, l.id")
                .params(params).query((rs, n) -> new LinkedCase(rs.getObject("id", UUID.class),
                        rs.getObject("case_id", UUID.class), rs.getString("reference_no"), rs.getString("title"),
                        rs.getString("reason"), rs.getTimestamp("created_at").toInstant())).list();
    }

    // ---- search -----------------------------------------------------------------------------------------------------

    record Filters(String q, String courtName, Integer year, String caseType, String courtLevel, String direction,
            String finality, List<UUID> classificationIds, boolean includeWithdrawn) {
    }

    /**
     * The list and the search. With a query: every word must match as a prefix of a word of the judgment's subject,
     * keywords, summary, legal texts, operative part, reasons or of one of its principles, or the text must be similar
     * enough in word trigrams to forgive a misspelling. Ranking: where the words occur (subject and keywords weigh most,
     * reasons least), plus the principles, plus half the trigram similarity. Without a query: newest judgment first.
     */
    @Transactional(readOnly = true)
    Page<ListItem> search(Filters f, Pageable pageable) {
        String normalised = f.q() == null ? "" : jdbc.sql("select library_normalize(:q)").param("q", f.q())
                .query(String.class).single();
        String tsQuery = LibraryText.allOfPrefixes(normalised);
        boolean searching = !normalised.isEmpty();
        Map<String, Object> params = new HashMap<>();
        params.put("tsq", tsQuery);
        params.put("qn", normalised);
        params.put("threshold", FUZZY_THRESHOLD);
        StringBuilder where = new StringBuilder(" where true");
        if (!f.includeWithdrawn()) {
            where.append(" and j.status = 'ACTIVE'");
        }
        if (f.courtName() != null) {
            where.append(" and library_normalize(j.court_name) like '%' || library_normalize(:court) || '%'");
            params.put("court", f.courtName());
        }
        if (f.year() != null) {
            where.append(" and extract(year from j.judgment_date) = :year");
            params.put("year", f.year());
        }
        for (String[] filter : new String[][] {{"case_type", "caseType"}, {"court_level", "courtLevel"},
            {"direction", "direction"}, {"finality", "finality"}}) {
            Object value = switch (filter[1]) {
                case "caseType" -> f.caseType();
                case "courtLevel" -> f.courtLevel();
                case "direction" -> f.direction();
                default -> f.finality();
            };
            if (value != null) {
                where.append(" and j.").append(filter[0]).append(" = :").append(filter[1]);
                params.put(filter[1], value);
            }
        }
        int i = 0;
        for (UUID classification : f.classificationIds()) {
            String name = "cls" + i++;
            where.append(" and exists (select 1 from library_judgment_class jc where jc.judgment_id = j.id"
                    + " and jc.classification_id = :").append(name).append(')');
            params.put(name, classification);
        }
        String principleMatch = "exists (select 1 from library_principle p where p.judgment_id = j.id"
                + " and p.search_vector @@ q.tq)";
        String from = " from library_judgment j cross join (select to_tsquery('simple', cast(:tsq as text)) as tq,"
                + " library_trigrams(:qn) as qt) q";
        // Words come first: every word of the query must match. Only when nothing matches that way are judgments with
        // a similar spelling offered (a forgiving fallback, so a typing slip is not an empty answer); it never widens
        // an answer that already has exact matches.
        String base = where.toString();
        String strict = base + (searching ? " and (j.search_vector @@ q.tq or " + principleMatch + ")" : "");
        long total = jdbc.sql("select count(*)" + from + strict).params(params).query(Long.class).single();
        boolean fuzzy = searching && total == 0;
        if (fuzzy) {
            strict = base + " and library_similarity(j.trigrams, q.qt) >= :threshold";
            total = jdbc.sql("select count(*)" + from + strict).params(params).query(Long.class).single();
        }
        params.put("fuzzy", fuzzy);
        String score = searching
                ? "(coalesce(ts_rank_cd(j.search_vector, q.tq), 0) + 0.8 * coalesce((select max(ts_rank_cd(p.search_vector,"
                        + " q.tq)) from library_principle p where p.judgment_id = j.id and p.search_vector @@ q.tq), 0)"
                        + " + 0.5 * library_similarity(j.trigrams, q.qt))"
                : "0";
        String matched = searching
                ? ", to_tsvector('simple', library_normalize(j.subject)) @@ q.tq as m_subject,"
                        + " to_tsvector('simple', library_normalize(j.keywords)) @@ q.tq as m_keywords,"
                        + " to_tsvector('simple', library_normalize(j.summary)) @@ q.tq as m_summary,"
                        + " to_tsvector('simple', library_normalize(j.legal_texts)) @@ q.tq as m_legal,"
                        + " " + principleMatch + " as m_principle,"
                        + " to_tsvector('simple', library_normalize(j.operative_text)) @@ q.tq as m_operative,"
                        + " to_tsvector('simple', library_normalize(j.reasons)) @@ q.tq as m_reasons,"
                        + " (:fuzzy and library_similarity(j.trigrams, q.qt) >= :threshold) as m_similar"
                : ", false as m_subject, false as m_keywords, false as m_summary, false as m_legal, false as m_principle,"
                        + " false as m_operative, false as m_reasons, false as m_similar";
        params.put("offset", pageable.getOffset());
        params.put("limit", pageable.getPageSize());
        List<ListItem> items = jdbc.sql("select j.id, j.court_name, j.court_level, j.chamber, j.case_number, j.case_year,"
                        + " j.judgment_date, j.case_type, j.subject, j.direction, j.finality, j.confidentiality, j.status,"
                        + " (select count(*) from library_principle p where p.judgment_id = j.id) as principle_count,"
                        + " array(select distinct r.relation_type from library_judgment_relation r where r.judgment_id ="
                        + " j.id and r.removed_at is null order by 1) as relation_types, " + score + " as score" + matched
                        + from + strict + " order by score desc, j.judgment_date desc nulls last, j.id offset :offset limit :limit")
                .params(params).query((rs, n) -> item(rs)).list();
        return new PageImpl<>(items, PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()), total);
    }

    private ListItem item(ResultSet rs) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        List<String> matchedIn = new ArrayList<>();
        // A word match is reported where it occurred; the similar-spelling match only when no word matched.
        for (String[] field : new String[][] {{"m_subject", "SUBJECT"}, {"m_keywords", "KEYWORDS"},
            {"m_summary", "SUMMARY"}, {"m_legal", "LEGAL_TEXTS"}, {"m_principle", "PRINCIPLE"},
            {"m_operative", "OPERATIVE"}, {"m_reasons", "REASONS"}}) {
            if (rs.getBoolean(field[0])) {
                matchedIn.add(field[1]);
            }
        }
        if (matchedIn.isEmpty() && rs.getBoolean("m_similar")) {
            matchedIn.add("SIMILAR_TEXT");
        }
        Object[] types = (Object[]) rs.getArray("relation_types").getArray();
        List<String> relationTypes = new ArrayList<>();
        for (Object type : types) {
            relationTypes.add((String) type);
        }
        return new ListItem(id, rs.getString("court_name"), rs.getString("court_level"), rs.getString("chamber"),
                rs.getString("case_number"), rs.getObject("case_year") == null ? null : rs.getInt("case_year"),
                rs.getObject("judgment_date", LocalDate.class), rs.getString("case_type"), rs.getString("subject"),
                rs.getString("direction"), rs.getString("finality"), rs.getString("confidentiality"),
                rs.getString("status"), classificationsOf(id), rs.getInt("principle_count"), relationTypes,
                Math.round(rs.getDouble("score") * 1000.0) / 1000.0, matchedIn);
    }

    // ---- writes -----------------------------------------------------------------------------------------------------

    @Transactional
    Judgment create(LibraryRequests.JudgmentWrite request) {
        ActorRef actor = currentActor.require();
        checkDate(request.judgmentDate());
        List<UUID> classes = checkedClassifications(request.classificationIds());
        if (!Boolean.TRUE.equals(request.allowDuplicate()) && duplicateExists(request, null)) {
            throw new BusinessRuleException(DUPLICATE_SUSPECTED,
                    "a judgment with this court, number, year and date is already in the library");
        }
        UUID id = jdbc.sql("insert into library_judgment (court_name, court_level, chamber, case_number, case_year,"
                        + " judgment_date, case_type, subject, parties_description, summary, operative_text, reasons,"
                        + " legal_texts, requests_addressed, request_results, direction, keywords, finality,"
                        + " confidentiality, source_reference, verification_note, copy_reference, created_by, updated_by)"
                        + " values (:courtName, :courtLevel, :chamber, :caseNumber, :caseYear, :judgmentDate, :caseType,"
                        + " :subject, :parties, :summary, :operative, :reasons, :legalTexts, :requests, :results,"
                        + " :direction, :keywords, :finality, :confidentiality, :source, :verification, :copy, :actor,"
                        + " :actor) returning id")
                .params(values(request)).param("actor", actor.id()).query(UUID.class).single();
        replaceClassifications(id, classes);
        audit.recordWithChange("LIBRARY_JUDGMENT_CREATE", actor.id(), actor.username(), "LIBRARY_JUDGMENT",
                id.toString(), "registered");
        return load(id);
    }

    @Transactional
    Judgment update(UUID id, LibraryRequests.JudgmentWrite request) {
        ActorRef actor = currentActor.require();
        if (request.version() == null) {
            throw new com.lawdesk.api.FieldErrorException("version", "NotNull");
        }
        checkDate(request.judgmentDate());
        List<UUID> classes = checkedClassifications(request.classificationIds());
        lockedVersion(id, request.version());
        Map<String, Object> params = values(request);
        params.put("id", id);
        params.put("actor", actor.id());
        params.put("version", request.version());
        int changed = jdbc.sql("update library_judgment set court_name = :courtName, court_level = :courtLevel,"
                        + " chamber = :chamber, case_number = :caseNumber, case_year = :caseYear,"
                        + " judgment_date = :judgmentDate, case_type = :caseType, subject = :subject,"
                        + " parties_description = :parties, summary = :summary, operative_text = :operative,"
                        + " reasons = :reasons, legal_texts = :legalTexts, requests_addressed = :requests,"
                        + " request_results = :results, direction = :direction, keywords = :keywords,"
                        + " finality = :finality, confidentiality = :confidentiality, source_reference = :source,"
                        + " verification_note = :verification, copy_reference = :copy, version = version + 1,"
                        + " updated_at = now(), updated_by = :actor where id = :id and version = :version")
                .params(params).update();
        if (changed == 0) {
            throw new ConflictException("judgment " + id + " has changed concurrently");
        }
        replaceClassifications(id, classes);
        audit.recordWithChange("LIBRARY_JUDGMENT_UPDATE", actor.id(), actor.username(), "LIBRARY_JUDGMENT",
                id.toString(), "edit");
        return load(id);
    }

    @Transactional
    Judgment withdraw(UUID id, String reason, long version) {
        ActorRef actor = currentActor.require();
        String status = lockedVersion(id, version);
        if ("WITHDRAWN".equals(status)) {
            throw new BusinessRuleException(ALREADY_WITHDRAWN, "the judgment is already withdrawn");
        }
        jdbc.sql("update library_judgment set status = 'WITHDRAWN', withdrawn_reason = :reason, withdrawn_at = now(),"
                        + " withdrawn_by = :actor, version = version + 1, updated_at = now(), updated_by = :actor"
                        + " where id = :id")
                .param("id", id).param("reason", reason.strip()).param("actor", actor.id()).update();
        audit.recordWithChange("LIBRARY_JUDGMENT_WITHDRAW", actor.id(), actor.username(), "LIBRARY_JUDGMENT",
                id.toString(), "withdrawn");
        return load(id);
    }

    @Transactional
    Judgment reinstate(UUID id, long version) {
        ActorRef actor = currentActor.require();
        String status = lockedVersion(id, version);
        if (!"WITHDRAWN".equals(status)) {
            throw new BusinessRuleException(NOT_WITHDRAWN, "the judgment is not withdrawn");
        }
        jdbc.sql("update library_judgment set status = 'ACTIVE', withdrawn_reason = null, withdrawn_at = null,"
                        + " withdrawn_by = null, version = version + 1, updated_at = now(), updated_by = :actor"
                        + " where id = :id")
                .param("id", id).param("actor", actor.id()).update();
        audit.recordWithChange("LIBRARY_JUDGMENT_REINSTATE", actor.id(), actor.username(), "LIBRARY_JUDGMENT",
                id.toString(), "reinstated");
        return load(id);
    }

    /** Row-locks the judgment, checks the version the caller read and returns its status. */
    private String lockedVersion(UUID id, long version) {
        Map<String, Object> row = jdbc.sql("select version, status from library_judgment where id = :id for update")
                .param("id", id).query((rs, n) -> Map.<String, Object>of("version", rs.getLong("version"), "status",
                        rs.getString("status"))).optional().orElseThrow(() -> new NotFoundException("judgment"));
        if ((Long) row.get("version") != version) {
            throw new ConflictException("judgment " + id + " has changed since version " + version);
        }
        return (String) row.get("status");
    }

    private static void checkDate(LocalDate date) {
        if (date != null && date.isAfter(LocalDate.now(KUWAIT))) {
            throw new BusinessRuleException(DATE_IN_FUTURE, "a judgment cannot be dated in the future");
        }
    }

    private List<UUID> checkedClassifications(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Set<UUID> distinct = new LinkedHashSet<>(ids);
        int found = jdbc.sql("select count(*) from library_classification where active and id in (:ids)")
                .param("ids", distinct).query(Integer.class).single();
        if (found != distinct.size()) {
            throw new BusinessRuleException(CLASSIFICATION_UNKNOWN, "a classification is unknown or inactive");
        }
        return new ArrayList<>(distinct);
    }

    private void replaceClassifications(UUID judgmentId, List<UUID> ids) {
        jdbc.sql("delete from library_judgment_class where judgment_id = :id").param("id", judgmentId).update();
        for (UUID classification : ids) {
            jdbc.sql("insert into library_judgment_class (judgment_id, classification_id) values (:j, :c)")
                    .param("j", judgmentId).param("c", classification).update();
        }
    }

    private boolean duplicateExists(LibraryRequests.JudgmentWrite request, UUID except) {
        String number = LibraryText.trimToNull(request.caseNumber());
        if (number == null || request.caseYear() == null) {
            return false;
        }
        return jdbc.sql("select exists (select 1 from library_judgment where library_normalize(court_name) ="
                        + " library_normalize(:court) and case_number = :number and case_year = :year"
                        + " and judgment_date is not distinct from :date and (cast(:except as uuid) is null or id <> :except))")
                .param("court", request.courtName()).param("number", number).param("year", request.caseYear())
                .param("date", request.judgmentDate()).param("except", except).query(Boolean.class).single();
    }

    private static Map<String, Object> values(LibraryRequests.JudgmentWrite r) {
        Map<String, Object> m = new HashMap<>();
        m.put("courtName", r.courtName().strip());
        m.put("courtLevel", r.courtLevel());
        m.put("chamber", LibraryText.trimToNull(r.chamber()));
        m.put("caseNumber", LibraryText.trimToNull(r.caseNumber()));
        m.put("caseYear", r.caseYear());
        m.put("judgmentDate", r.judgmentDate());
        m.put("caseType", r.caseType());
        m.put("subject", r.subject().strip());
        m.put("parties", LibraryText.trimToNull(r.partiesDescription()));
        m.put("summary", LibraryText.trimToNull(r.summary()));
        m.put("operative", LibraryText.trimToNull(r.operativeText()));
        m.put("reasons", LibraryText.trimToNull(r.reasons()));
        m.put("legalTexts", LibraryText.trimToNull(r.legalTexts()));
        m.put("requests", LibraryText.trimToNull(r.requestsAddressed()));
        m.put("results", LibraryText.trimToNull(r.requestResults()));
        m.put("direction", r.direction());
        m.put("keywords", LibraryText.keywordsToColumn(r.keywords()));
        m.put("finality", r.finality() == null ? "NOT_SPECIFIED" : r.finality());
        m.put("confidentiality", r.confidentiality() == null ? "NORMAL" : r.confidentiality());
        m.put("source", r.sourceReference().strip());
        m.put("verification", r.verificationNote().strip());
        m.put("copy", LibraryText.trimToNull(r.copyReference()));
        return m;
    }

    /** True when the judgment exists (used by the other library services). */
    boolean exists(UUID id) {
        return jdbc.sql("select exists (select 1 from library_judgment where id = :id)").param("id", id)
                .query(Boolean.class).single();
    }
}
