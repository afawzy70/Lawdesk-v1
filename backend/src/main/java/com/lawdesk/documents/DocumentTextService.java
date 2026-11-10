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
            Integer pageCount, boolean truncated, String text, String alternateReadings, Integer wordsRead,
            Integer wordsUncertain) {
    }

    /** One search result: the document, the case it belongs to and a short piece of its text around the match. */
    record Hit(UUID documentId, UUID caseId, String caseReferenceNo, String caseTitle, String title, String fileName,
            int versionNo, UUID categoryId, String status, String snippet) {
    }

    private final JdbcClient jdbc;
    private final TextExtractor extractor;
    private final OcrEngine engine;
    private final ArchiveSettings settings;
    private final CaseAccessPolicy access;
    private final DocumentStore store;

    DocumentTextService(JdbcClient jdbc, TextExtractor extractor, OcrEngine engine, ArchiveSettings settings,
            CaseAccessPolicy access, DocumentStore store) {
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
        jdbc.sql("delete from document_text where version_id = :v").param("v", versionId).update();
        TextExtractor.Result result = extractor.extract(mediaType, content, settings.limits().maxTextChars());
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
        save(versionId, documentId, status, method, engineName, detail, pages, truncated, text, null, null, null);
    }

    private void save(UUID versionId, UUID documentId, String status, String method, String engineName, String detail,
            Integer pages, boolean truncated, String text, String altText, Integer wordsRead, Integer wordsUncertain) {
        jdbc.sql("""
                insert into document_text (version_id, document_id, status, method, engine, languages, detail, page_count,
                                           truncated, text, alt_text, words_read, words_uncertain, processed_at)
                values (:v, :d, :status, :method, :engine, :languages, :detail, :pages, :truncated, :text, :alt, :read, :unsure,
                        case when :status in ('EXTRACTED', 'OCR_DONE') then now() end)
                on conflict (version_id) do update set status = excluded.status, method = excluded.method,
                    engine = excluded.engine, languages = excluded.languages, detail = excluded.detail,
                    page_count = excluded.page_count, truncated = excluded.truncated, text = excluded.text,
                    alt_text = excluded.alt_text, words_read = excluded.words_read, words_uncertain = excluded.words_uncertain,
                    processed_at = excluded.processed_at""").param("v", versionId).param("d", documentId)
                .param("status", status).param("method", method).param("engine", engineName)
                .param("languages", "OCR".equals(method) ? engine.status().languages() : null).param("detail", detail)
                .param("pages", pages).param("truncated", truncated).param("text", text).param("alt", altText)
                .param("read", wordsRead).param("unsure", wordsUncertain).update();
    }

    private static String unavailable(OcrEngine.Status status) {
        return status.detail() == null ? "no recognition engine on this server" : status.detail();
    }

    /** Runs recognition for versions waiting for it; returns how many it handled. Called by the worker and by tests. */
    @Transactional
    int processPending(int max) {
        List<UUID> waiting = jdbc.sql("""
                select version_id from document_text where status = 'PENDING' order by created_at limit :max
                for update skip locked""").param("max", max).query(UUID.class).list();
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
            save(versionId, document.get(), OCR_UNAVAILABLE, "OCR", status.engine(), unavailable(status), null, false, null);
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
            StringBuilder alternates = new StringBuilder();
            int read = 0;
            int unsure = 0;
            for (byte[] page : pages) {
                OcrEngine.Reading reading = engine.read(page);
                text.append(reading.text()).append("\n\n");
                alternates.append(reading.alternates()).append('\n');
                read += reading.words();
                unsure += reading.uncertainWords();
            }
            String clean = TextExtractor.clean(text.toString());
            boolean cut = total > pages.size() || clean.length() > limits.maxTextChars();
            String detail = total > pages.size() ? "only the first " + pages.size() + " of " + total + " pages were read" : null;
            boolean empty = clean.chars().noneMatch(Character::isLetterOrDigit);
            String alt = TextExtractor.clean(alternates.toString());
            save(versionId, document.get(), empty ? NO_TEXT : OCR_DONE, "OCR", status.engine(),
                    empty ? "recognition found no text" : detail, total, cut,
                    empty ? null : clean.substring(0, Math.min(clean.length(), limits.maxTextChars())),
                    empty || alt.isBlank() ? null : alt.substring(0, Math.min(alt.length(), limits.maxTextChars())),
                    empty ? null : read, empty ? null : unsure);
        } catch (OcrEngine.OcrException | IOException | RuntimeException e) {
            int attempts = jdbc.sql("update document_text set attempts = attempts + 1 where version_id = :v returning attempts")
                    .param("v", versionId).query(Integer.class).single();
            save(versionId, document.get(), attempts >= MAX_ATTEMPTS ? FAILED : PENDING, "OCR", status.engine(),
                    attempts >= MAX_ATTEMPTS ? "recognition failed" : "recognition will be retried", null, false, null);
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
                select version_id, document_id, status, method, engine, detail, page_count, truncated, text, alt_text,
                       words_read, words_uncertain
                from document_text where version_id = :v""").param("v", versionId)
                .query((rs, n) -> new TextView(rs.getObject("version_id", UUID.class), rs.getObject("document_id", UUID.class),
                        rs.getString("status"), rs.getString("method"), rs.getString("engine"), rs.getString("detail"),
                        (Integer) rs.getObject("page_count"), rs.getBoolean("truncated"),
                        withText ? rs.getString("text") : null, withText ? rs.getString("alt_text") : null,
                        (Integer) rs.getObject("words_read"), (Integer) rs.getObject("words_uncertain"))).optional();
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
            where.append(" and position(:t").append(i).append(" in t.search_text) > 0");
            every.append(i == 0 ? "" : " and ").append("position(:t").append(i).append(" in library_normalize(s)) > 0");
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
                + " d.category_id, t.status, coalesce((select s from " + pieces + " s where " + every + " limit 1),"
                + " (select s from " + pieces + " s where position(:t0 in library_normalize(s)) > 0 limit 1)) as snippet "
                + where + " order by d.document_date desc nulls last, d.created_at desc, d.id offset :offset limit :limit";
        List<Hit> hits = jdbc.sql(sql).params(params).query((rs, n) -> new Hit(rs.getObject("id", UUID.class),
                rs.getObject("case_id", UUID.class), rs.getString("reference_no"), rs.getString("case_title"),
                rs.getString("title"), rs.getString("file_name"), rs.getInt("version_no"),
                rs.getObject("category_id", UUID.class), rs.getString("status"), clip(rs.getString("snippet")))).list();
        return new PageImpl<>(hits, PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()), total);
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
