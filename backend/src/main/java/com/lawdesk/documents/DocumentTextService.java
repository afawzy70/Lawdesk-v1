package com.lawdesk.documents;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.lawdesk.api.FieldErrorException;
import com.lawdesk.cases.CaseAccessPolicy;

/**
 * The text of stored versions and the search over it (requirement 13; owner decision D7). A version's text is read from the
 * file when it carries any, otherwise by the local engine ({@link OcrEngine}); when no engine exists the status says so and the
 * version is simply not searchable by content. The text is data: it is stored, normalised for search and shown as plain
 * text, never interpreted. Every read goes through the case access rule.
 */
@Service
class DocumentTextService {

    static final String EXTRACTED = "EXTRACTED";
    static final String OCR_DONE = "OCR_DONE";
    static final String PENDING = "PENDING";
    static final String OCR_UNAVAILABLE = "OCR_UNAVAILABLE";
    static final String NO_TEXT = "NO_TEXT";
    static final String UNSUPPORTED = "UNSUPPORTED";
    static final String FAILED = "FAILED";

    private static final int MAX_TERMS = 8;
    private static final int SNIPPET_MAX = 240;
    private static final int MAX_ATTEMPTS = 3;

    /** The text state of one version. {@code text} is present only when the caller asked for it. */
    record TextView(UUID versionId, UUID documentId, String status, String method, String engine, String detail,
            Integer pageCount, boolean truncated, String text, Integer wordsRead, Integer wordsUncertain,
            List<AlternateView> alternates) {
    }

    /** One word a second reading of the page found and the main reading lacks (never part of {@code text}); returned with the text only. */
    record AlternateView(String word, int page, int line, String pass, double confidence) {
    }

    /** Why a hit was found by an alternate reading: the query word, the alternate word, and where and how sure that reading was. */
    record AlternateMatch(String term, String word, int page, int line, String pass, double confidence) {
    }

    /** One search result: the document, the case it belongs to and a short piece of its text around the match. */
    record Hit(UUID documentId, UUID caseId, String caseReferenceNo, String caseTitle, String title, String fileName,
            int versionNo, UUID categoryId, String status, String snippet, String source, List<AlternateMatch> alternateMatches,
            Integer wordsUncertain) {
        /** True when the hit holds only because of words another reading of the page found: it may be a misreading. */
        public boolean foundInUncertainAlternateReading() {
            return "ALTERNATE".equals(source);
        }
    }

    private final JdbcClient jdbc;
    private final TextExtractor extractor;
    private final OcrEngine engine;
    private final ArchiveSettings settings;
    private final CaseAccessPolicy access;
    private final DocumentStore store;
    private final TransactionTemplate transaction;

    DocumentTextService(JdbcClient jdbc, TextExtractor extractor, OcrEngine engine, ArchiveSettings settings,
            CaseAccessPolicy access, DocumentStore store, TransactionTemplate transaction) {
        this.transaction = transaction;
        this.jdbc = jdbc;
        this.extractor = extractor;
        this.engine = engine;
        this.settings = settings;
        this.access = access;
        this.store = store;
    }

    // ---- writing the text ------------------------------------------------------------------------------------

    /** Reads what a newly stored version carries and records the state; recognition itself waits for the worker. */
    @Transactional
    void index(UUID versionId, UUID documentId, String mediaType, byte[] content) {
        TextExtractor.Result result = extractor.extract(mediaType, content, settings.limits().maxTextChars());
        // A reprocess of a version whose text was read by recognition keeps that text (and its alternates) until the new reading
        // replaces them: a failed or interrupted reprocess must not lose the earlier primary text.
        if (result.outcome() == TextExtractor.Outcome.NEEDS_OCR && engine.status().available()
                && jdbc.sql("select count(*) from document_text where version_id = :v and text is not null and method = 'OCR'")
                        .param("v", versionId).query(Integer.class).single() > 0) {
            jdbc.sql("""
                    update document_text set status = 'PENDING', detail = 'waiting for recognition', attempts = 0,
                           claimed_at = null where version_id = :v""").param("v", versionId).update();
            return;
        }
        jdbc.sql("delete from document_text where version_id = :v").param("v", versionId).update();
        switch (result.outcome()) {
            case TEXT -> save(versionId, documentId, EXTRACTED, result.method(), null, null, result.pages(),
                    result.truncated(), result.text());
            case NEEDS_OCR -> {
                OcrEngine.Status status = engine.status();
                if (status.available()) {
                    save(versionId, documentId, PENDING, "OCR", status.engine(), "waiting for recognition", result.pages(),
                            false, null);
                } else {
                    save(versionId, documentId, OCR_UNAVAILABLE, "OCR", status.engine(), unavailable(status), result.pages(),
                            false, null);
                }
            }
            case NO_TEXT -> save(versionId, documentId, NO_TEXT, null, null, "the file holds no text", result.pages(), false, null);
            case UNSUPPORTED -> save(versionId, documentId, UNSUPPORTED, null, null,
                    "the text of this format is not read", null, false, null);
            default -> save(versionId, documentId, FAILED, null, null, "the file could not be read", null, false, null);
        }
    }

    private void save(UUID versionId, UUID documentId, String status, String method, String engineName, String detail,
            Integer pages, boolean truncated, String text) {
        save(versionId, documentId, status, method, engineName, detail, pages, truncated, text, null, null);
    }

    private void save(UUID versionId, UUID documentId, String status, String method, String engineName, String detail,
            Integer pages, boolean truncated, String text, Integer wordsRead, Integer wordsUncertain) {
        jdbc.sql("""
                insert into document_text (version_id, document_id, status, method, engine, languages, detail, page_count,
                                           truncated, text, words_read, words_uncertain, processed_at)
                values (:v, :d, :status, :method, :engine, :languages, :detail, :pages, :truncated, :text, :read, :unsure,
                        case when :status in ('EXTRACTED', 'OCR_DONE') then now() end)
                on conflict (version_id) do update set status = excluded.status, method = excluded.method,
                    engine = excluded.engine, languages = excluded.languages, detail = excluded.detail,
                    page_count = excluded.page_count, truncated = excluded.truncated, text = excluded.text,
                    words_read = excluded.words_read, words_uncertain = excluded.words_uncertain,
                    claimed_at = null, processed_at = excluded.processed_at""").param("v", versionId).param("d", documentId)
                .param("status", status).param("method", method).param("engine", engineName)
                .param("languages", "OCR".equals(method) ? engine.status().languages() : null).param("detail", detail)
                .param("pages", pages).param("truncated", truncated).param("text", text)
                .param("read", wordsRead).param("unsure", wordsUncertain).update();
    }

    private static String unavailable(OcrEngine.Status status) {
        return status.detail() == null ? "no recognition engine on this server" : status.detail();
    }

    /** A claim older than this is taken to belong to a worker that died, and the version is claimed again. */
    private static final String CLAIM_EXPIRY = "30 minutes";

    /**
     * Runs recognition for versions waiting for it; returns how many it handled. Called by the worker and by tests. The versions
     * are claimed in one short transaction; the engine then runs with NO transaction and no connection held (it can take minutes
     * on a page), and each result is written in its own short transaction, so recognition never makes a request wait for a connection.
     */
    int processPending(int max) {
        List<UUID> waiting = transaction.execute(status -> jdbc.sql("""
                update document_text set claimed_at = now() where version_id in (
                    select version_id from document_text
                    where status = 'PENDING' and (claimed_at is null or claimed_at < now() - interval '""" + CLAIM_EXPIRY + """
                    ')
                    order by created_at limit :max for update skip locked)
                returning version_id""").param("max", max).query(UUID.class).list());
        for (UUID versionId : waiting) {
            recognise(versionId);
        }
        return waiting.size();
    }

    private void recognise(UUID versionId) {
        Optional<UUID> document = jdbc.sql("select document_id from document_text where version_id = :v").param("v", versionId)
                .query(UUID.class).optional();
        Optional<byte[]> content = store.content(versionId);
        Optional<String> mediaType = jdbc.sql("select media_type from document_version where id = :v").param("v", versionId)
                .query(String.class).optional();
        if (document.isEmpty() || content.isEmpty() || mediaType.isEmpty()) {
            return;
        }
        OcrEngine.Status status = engine.status();
        if (!status.available()) {
            transaction.executeWithoutResult(t -> save(versionId, document.get(), OCR_UNAVAILABLE, "OCR", status.engine(),
                    unavailable(status), null, false, null));
            return;
        }
        ArchiveSettings.Limits limits = settings.limits();
        try {
            List<byte[]> pages;
            int total;
            if ("application/pdf".equals(mediaType.get())) {
                TextExtractor.PageImages images = extractor.render(content.get(), limits.maxOcrPages());
                pages = images.images();
                total = images.totalPages();
            } else {
                pages = List.of(content.get());
                total = 1;
            }
            StringBuilder text = new StringBuilder();
            List<Object[]> alternates = new ArrayList<>();
            int read = 0;
            int unsure = 0;
            int pageNo = 0;
            for (byte[] page : pages) {
                pageNo++;
                OcrEngine.Reading reading = engine.read(page);
                text.append(reading.text()).append("\n\n");
                for (OcrEngine.Alternate alternate : reading.alternates()) {
                    alternates.add(new Object[] {pageNo, alternate});
                }
                read += reading.words();
                unsure += reading.uncertainWords();
            }
            String clean = TextExtractor.clean(text.toString());
            boolean cut = total > pages.size() || clean.length() > limits.maxTextChars();
            String detail = total > pages.size() ? "only the first " + pages.size() + " of " + total + " pages were read" : null;
            boolean empty = clean.chars().noneMatch(Character::isLetterOrDigit);
            int wordsRead = read;
            int wordsUnsure = unsure;
            transaction.executeWithoutResult(t -> {
                save(versionId, document.get(), empty ? NO_TEXT : OCR_DONE, "OCR", status.engine(),
                        empty ? "recognition found no text" : detail, total, cut,
                        empty ? null : clean.substring(0, Math.min(clean.length(), limits.maxTextChars())),
                        empty ? null : wordsRead, empty ? null : wordsUnsure);
                // A re-reading replaces the alternates of the earlier one: never duplicated, never left over.
                jdbc.sql("delete from document_text_alternate where version_id = :v").param("v", versionId).update();
                if (!empty) {
                    for (Object[] row : alternates) {
                        OcrEngine.Alternate a = (OcrEngine.Alternate) row[1];
                        jdbc.sql("""
                                insert into document_text_alternate (version_id, document_id, page, line_no, word, pass, confidence)
                                values (:v, :d, :page, :line, :word, :pass, :conf)""").param("v", versionId)
                                .param("d", document.get()).param("page", row[0]).param("line", a.line())
                                .param("word", a.word().length() > 200 ? a.word().substring(0, 200) : a.word())
                                .param("pass", a.pass()).param("conf", Math.round(a.confidence() * 100) / 100.0).update();
                    }
                }
            });
        } catch (OcrEngine.OcrException | IOException | RuntimeException e) {
            transaction.executeWithoutResult(t -> {
                int attempts = jdbc.sql("update document_text set attempts = attempts + 1, claimed_at = null where version_id = :v returning attempts")
                        .param("v", versionId).query(Integer.class).single();
                // Only the state changes: a text read earlier (a reprocess that failed) stays as it was.
                jdbc.sql("update document_text set status = :s, detail = :d where version_id = :v").param("v", versionId)
                        .param("s", attempts >= MAX_ATTEMPTS ? FAILED : PENDING)
                        .param("d", attempts >= MAX_ATTEMPTS ? "recognition failed" : "recognition will be retried").update();
            });
        }
    }

    /** Re-reads a stored version (for example after an engine was installed); the caller has checked the rights. */
    @Transactional
    void reindex(UUID versionId) {
        DocumentVersion version = jdbc.sql("""
                select id, document_id, version_no, file_name, media_type, size_bytes, sha256, change_note, uploaded_by,
                       uploaded_at from document_version where id = :v""").param("v", versionId)
                .query((rs, n) -> new DocumentVersion(rs.getObject("id", UUID.class), rs.getObject("document_id", UUID.class),
                        rs.getInt("version_no"), rs.getString("file_name"), rs.getString("media_type"),
                        rs.getInt("size_bytes"), rs.getString("sha256"), rs.getString("change_note"),
                        rs.getObject("uploaded_by", UUID.class), rs.getTimestamp("uploaded_at").toInstant()))
                .optional().orElseThrow();
        byte[] content = store.content(versionId).orElseThrow();
        index(versionId, version.documentId(), version.mediaType(), content);
    }

    // ---- reading ---------------------------------------------------------------------------------------------

    /** The state of a version's text; the caller has already shown the user may see the document's case. */
    @Transactional(readOnly = true)
    Optional<TextView> view(UUID versionId, boolean withText) {
        return jdbc.sql("""
                select version_id, document_id, status, method, engine, detail, page_count, truncated, text,
                       words_read, words_uncertain
                from document_text where version_id = :v""").param("v", versionId)
                .query((rs, n) -> new TextView(rs.getObject("version_id", UUID.class), rs.getObject("document_id", UUID.class),
                        rs.getString("status"), rs.getString("method"), rs.getString("engine"), rs.getString("detail"),
                        (Integer) rs.getObject("page_count"), rs.getBoolean("truncated"),
                        withText ? rs.getString("text") : null,
                        (Integer) rs.getObject("words_read"), (Integer) rs.getObject("words_uncertain"), List.of()))
                .optional().map(v -> withText ? withAlternates(v) : v);
    }

    private TextView withAlternates(TextView v) {
        List<AlternateView> alternates = jdbc.sql("""
                select word, page, line_no, pass, confidence from document_text_alternate where version_id = :v
                order by page, line_no, id""").param("v", v.versionId())
                .query((rs, n) -> new AlternateView(rs.getString("word"), rs.getInt("page"), rs.getInt("line_no"),
                        rs.getString("pass"), rs.getDouble("confidence"))).list();
        return new TextView(v.versionId(), v.documentId(), v.status(), v.method(), v.engine(), v.detail(), v.pageCount(),
                v.truncated(), v.text(), v.wordsRead(), v.wordsUncertain(), alternates);
    }

    /** The status of every version of a document, by version id. */
    @Transactional(readOnly = true)
    Map<UUID, TextView> statuses(UUID documentId) {
        Map<UUID, TextView> found = new HashMap<>();
        for (DocumentVersion version : store.versions(documentId)) {
            view(version.id(), false).ifPresent(v -> found.put(version.id(), v));
        }
        return found;
    }

    OcrEngine.Status engineStatus() {
        return engine.status();
    }

    /**
     * Documents whose current text matches every word of {@code query} (normalised the Arabic way: diacritics, alef and yeh
     * forms, taa marbuta and the definite article do not matter), restricted to the cases the user may see. A document of a
     * case the user cannot see never appears, nor does any piece of its text.
     */
    @Transactional(readOnly = true)
    Page<Hit> search(String query, UUID caseId, UUID categoryId, Pageable pageable) {
        List<String> terms = terms(query);
        if (terms.isEmpty() || terms.stream().noneMatch(t -> t.length() >= 2)) {
            throw new FieldErrorException("q", "Size");
        }
        List<List<String>> groups = groups(query);
        CaseAccessPolicy.Predicate visible = access.visibleCaseIds("d.case_id");
        Map<String, Object> params = new HashMap<>(visible.params());
        StringBuilder where = new StringBuilder("""
                 from case_document d
                 join legal_case c on c.id = d.case_id
                 join document_version v on v.document_id = d.id and v.version_no = d.current_version_no
                 join document_text t on t.version_id = v.id and t.status in ('EXTRACTED', 'OCR_DONE')
                 where d.withdrawn_at is null and""").append(' ').append(visible.sql());
        StringBuilder every = new StringBuilder();
        for (int i = 0; i < terms.size(); i++) {
            params.put("t" + i, terms.get(i));
            every.append(i == 0 ? "" : " and ").append("position(:t").append(i).append(" in library_normalize(s)) > 0");
        }
        // Each word of the query (a reference such as LD-2026/045 is one word with three parts) is found in the main reading, or in
        // ONE alternate word: alternates are rows, never a text, so nothing is matched across two alternates or an alternate and its neighbour.
        StringBuilder primaryAll = new StringBuilder("true");
        for (int g = 0; g < groups.size(); g++) {
            StringBuilder inPrimary = new StringBuilder("true");
            StringBuilder inAlternate = new StringBuilder("true");
            for (int k = 0; k < groups.get(g).size(); k++) {
                params.put("g" + g + "_" + k, groups.get(g).get(k));
                inPrimary.append(" and position(:g").append(g).append('_').append(k).append(" in t.search_text) > 0");
                inAlternate.append(" and position(:g").append(g).append('_').append(k).append(" in a.norm) > 0");
            }
            where.append(" and ((").append(inPrimary).append(") or exists (select 1 from document_text_alternate a where a.version_id = t.version_id and ")
                    .append(inAlternate).append("))");
            primaryAll.append(" and (").append(inPrimary).append(')');
        }
        if (caseId != null) {
            params.put("caseId", caseId);
            where.append(" and d.case_id = :caseId");
        }
        if (categoryId != null) {
            params.put("categoryId", categoryId);
            where.append(" and d.category_id = :categoryId");
        }
        Long total = jdbc.sql("select count(*) " + where).params(params).query(Long.class).single();
        params.put("offset", pageable.getOffset());
        params.put("limit", pageable.getPageSize());
        String pieces = "regexp_split_to_table(t.text, '[\\r\\n.!?؟]+')";
        String sql = "select d.id, d.case_id, c.reference_no, c.title as case_title, d.title, d.file_name, v.version_no,"
                + " d.category_id, t.status, t.words_uncertain, (" + primaryAll + ") as primary_all, v.id as version_id, coalesce((select s from " + pieces + " s where " + every + " limit 1),"
                + " (select s from " + pieces + " s where position(:t0 in library_normalize(s)) > 0 limit 1)) as snippet "
                + where + " order by d.document_date desc nulls last, d.created_at desc, d.id offset :offset limit :limit";
        List<Hit> hits = jdbc.sql(sql).params(params).query((rs, n) -> new Hit(rs.getObject("id", UUID.class),
                rs.getObject("case_id", UUID.class), rs.getString("reference_no"), rs.getString("case_title"),
                rs.getString("title"), rs.getString("file_name"), rs.getInt("version_no"),
                rs.getObject("category_id", UUID.class), rs.getString("status"), clip(rs.getString("snippet")),
                rs.getBoolean("primary_all") ? "PRIMARY" : "ALTERNATE",
                rs.getBoolean("primary_all") ? List.<AlternateMatch>of() : alternateMatches(rs.getObject("version_id", UUID.class), groups),
                (Integer) rs.getObject("words_uncertain"))).list();
        return new PageImpl<>(hits, PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()), total);
    }

    /** The words of the query as the search sees them: one group of normalised parts per word typed ("LD-2026/045" is ld, 2026, 045). */
    private List<List<String>> groups(String query) {
        List<List<String>> groups = new ArrayList<>();
        if (query == null) {
            return groups;
        }
        for (String raw : query.strip().split("\\s+")) {
            String normal = jdbc.sql("select library_normalize(:q)")
                    .param("q", java.text.Normalizer.normalize(raw, java.text.Normalizer.Form.NFKC)).query(String.class).single();
            List<String> parts = new ArrayList<>(new LinkedHashSet<>(List.of(normal.split(" "))));
            parts.removeIf(String::isBlank);
            if (!parts.isEmpty() && !groups.contains(parts)) {
                groups.add(parts);
            }
        }
        return groups.size() > MAX_TERMS ? groups.subList(0, MAX_TERMS) : groups;
    }

    /** For a hit that does not hold on the main reading alone: the alternate words that carry the words the main reading lacks. */
    private List<AlternateMatch> alternateMatches(UUID versionId, List<List<String>> groups) {
        List<AlternateMatch> found = new ArrayList<>();
        for (List<String> group : groups) {
            boolean inPrimary = Boolean.TRUE.equals(jdbc.sql(
                    "select bool_and(position(p in t.search_text) > 0) from document_text t, unnest(cast(:parts as text[])) p"
                            + " where t.version_id = :v").param("parts", group.toArray(new String[0])).param("v", versionId)
                    .query(Boolean.class).optional().orElse(false));
            if (inPrimary) {
                continue;
            }
            jdbc.sql("""
                    select a.word, a.page, a.line_no, a.pass, a.confidence from document_text_alternate a
                    where a.version_id = :v and not exists (select 1 from unnest(cast(:parts as text[])) p
                                                            where position(p in a.norm) = 0)
                    order by a.confidence desc, a.id limit 3""").param("v", versionId).param("parts", group.toArray(new String[0]))
                    .query((rs, n) -> new AlternateMatch(String.join(" ", group), rs.getString("word"), rs.getInt("page"),
                            rs.getInt("line_no"), rs.getString("pass"), rs.getDouble("confidence"))).list().forEach(found::add);
        }
        return found;
    }

    private List<String> terms(String query) {
        if (query == null) {
            return List.of();
        }
        // NFKC first, as for stored text: a phrase copied from a PDF may carry Arabic presentation forms.
        String normal = jdbc.sql("select library_normalize(:q)")
                .param("q", java.text.Normalizer.normalize(query, java.text.Normalizer.Form.NFKC)).query(String.class).single();
        Set<String> words = new LinkedHashSet<>(List.of(normal.split(" ")));
        words.removeIf(String::isBlank);
        List<String> terms = new ArrayList<>(words);
        return terms.size() > MAX_TERMS ? terms.subList(0, MAX_TERMS) : terms;
    }

    private static String clip(String piece) {
        if (piece == null) {
            return null;
        }
        String flat = piece.strip().replaceAll("\\s+", " ");
        return flat.length() <= SNIPPET_MAX ? flat : flat.substring(0, SNIPPET_MAX - 1) + "…";
    }
}
