package com.lawdesk.assistant;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.api.FieldErrorException;
import com.lawdesk.assistant.AssistantProvider.Field;
import com.lawdesk.assistant.AssistantProvider.Section;
import com.lawdesk.casefile.CaseFileReadPort;
import com.lawdesk.documents.DocumentReadPort;
import com.lawdesk.library.LibraryReadPort;
import com.lawdesk.pleadings.PleadingReadPort;
import com.lawdesk.search.SearchPort;

/**
 * Finds what an answer may rest on - and ONLY through the modules that already enforce the rules (owner decisions D3 and D8):
 * the unified search (WP9), the document text (WP5), the case file and the pleadings (WP1 to WP3), and the judgments library
 * (WP7). Every one of them restricts itself to the cases and records the signed-in user may see and reports a case they may not
 * see as missing, so the assistant cannot reach a case by asking about it. This class reads nothing from the database itself.
 *
 * <p>Retrieval is a plain lookup: the search words of a question are searched one by one; a passage is adequate when it holds at
 * least half of them. A question may also name a kind of record ("last hearing", "deadlines", "requests"), in which case those
 * records are quoted. If nothing is adequate the plan says so and the assistant refuses.
 */
@Component
class Retriever {

    static final int PER_WORD = 5;
    static final int MAX_PASSAGES = 8;
    static final int CHRONOLOGY = 15;
    static final int SIMILAR = 8;

    private static final List<String> CASE_TYPES = List.of("CASE", "PARTY", "CLAIM", "DEFENSE", "HEARING", "JUDGMENT", "DOCUMENT",
            "NOTE", "TASK", "SETTLEMENT", "DIRECTIVE");
    private static final List<String> ORDER = List.of("CASE", "CLAIM", "DEFENSE", "HEARING", "JUDGMENT", "DOCUMENT", "NOTE", "TASK",
            "SETTLEMENT", "DIRECTIVE", "PARTY", "LIBRARY");

    private final SearchPort search;
    private final DocumentReadPort documents;
    private final CaseFileReadPort caseFile;
    private final PleadingReadPort pleadings;
    private final LibraryReadPort library;

    Retriever(SearchPort search, DocumentReadPort documents, CaseFileReadPort caseFile, PleadingReadPort pleadings,
            LibraryReadPort library) {
        this.search = search;
        this.documents = documents;
        this.caseFile = caseFile;
        this.pleadings = pleadings;
        this.library = library;
    }

    /** One passage found by words, with the words that found it. */
    private static final class Candidate {
        final String type;
        final UUID id;
        final UUID caseId;
        final String caseReferenceNo;
        final String title;
        final LocalDate date;
        String excerpt;
        List<Field> fields = List.of();
        final Set<String> words = new LinkedHashSet<>();

        Candidate(String type, UUID id, UUID caseId, String caseReferenceNo, String title, LocalDate date, String excerpt) {
            this.type = type;
            this.id = id;
            this.caseId = caseId;
            this.caseReferenceNo = caseReferenceNo;
            this.title = title;
            this.date = date;
            this.excerpt = excerpt;
        }
    }

    // ---- 22.6: a question ---------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    Plan question(UUID caseId, String question) {
        List<String> words = Keywords.of(question);
        SourceBook book = new SourceBook();
        List<Integer> chosen = new ArrayList<>();
        List<CaseFileReadPort.Fact> facts = caseId == null ? List.of() : caseFile.facts(caseId);
        String caseRef = caseRef(facts);
        if (caseId != null) {
            chosen.addAll(intents(caseId, caseRef, question, facts, book));
        }
        if (words.isEmpty() && chosen.isEmpty()) {
            return Plan.refused(caseId, Plan.NO_KEYWORDS);
        }
        Map<String, Candidate> found = new LinkedHashMap<>();
        for (String word : words) {
            if (caseId != null) {
                for (var hit : search.find(word, caseId, CASE_TYPES, PER_WORD)) {
                    String excerpt = hit.snippet() != null ? hit.snippet() : hit.detail() != null ? hit.detail() : hit.title();
                    found.computeIfAbsent(hit.type() + "|" + hit.id(), k -> new Candidate(hit.type(), hit.id(), hit.caseId(),
                            hit.caseReferenceNo(), hit.title(), hit.date(), excerpt)).words.add(word);
                }
                for (var text : documents.findText(word, caseId, PER_WORD)) {
                    Candidate c = found.computeIfAbsent("DOCUMENT|" + text.documentId(), k -> new Candidate("DOCUMENT",
                            text.documentId(), text.caseId(), text.caseReferenceNo(), text.title(), null, text.snippet()));
                    if (text.snippet() != null) {
                        c.excerpt = text.snippet();
                    }
                    c.words.add(word);
                }
            }
            for (LibraryReadPort.Authority a : library.search(word, PER_WORD)) {
                Candidate c = found.computeIfAbsent("LIBRARY|" + a.id(), k -> libraryCandidate(a));
                c.words.add(word);
            }
        }
        int need = (words.size() + 1) / 2;
        List<Candidate> adequate = found.values().stream().filter(c -> c.words.size() >= need && !words.isEmpty())
                .sorted(Comparator.<Candidate>comparingInt(c -> -c.words.size())
                        .thenComparingInt(c -> ORDER.indexOf(c.type) < 0 ? ORDER.size() : ORDER.indexOf(c.type))
                        .thenComparing(c -> c.date, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(MAX_PASSAGES).toList();
        for (Candidate c : adequate) {
            chosen.add(book.add(c.type, c.id, c.caseId, c.caseReferenceNo, c.title, c.date, c.excerpt, c.fields));
        }
        List<Integer> unique = new ArrayList<>(new LinkedHashSet<>(chosen));
        return Plan.of(caseId, book, List.of(new Section("ANSWER_SOURCES", unique)));
    }

    private static Candidate libraryCandidate(LibraryReadPort.Authority a) {
        Candidate c = new Candidate("LIBRARY", a.id(), null, null, a.subject(), a.judgmentDate(), null);
        c.fields = authorityFields(a);
        return c;
    }

    /** Kinds of record a question names; each is quoted from the case file. A fixed list, not an interpretation. */
    private List<Integer> intents(UUID caseId, String caseRef, String question, List<CaseFileReadPort.Fact> facts, SourceBook book) {
        String q = question == null ? "" : question.toLowerCase(Locale.ROOT);
        List<Integer> numbers = new ArrayList<>();
        boolean last = has(q, "آخر", "اخر", "last", "latest", "most recent");
        boolean next = has(q, "القادم", "التالي", "المطلوب", "بعد الجلسة", "next", "upcoming", "required", "should be taken");
        boolean deadline = has(q, "موعد", "مواعيد", "ميعاد", "طعن", "deadline", "appeal");
        boolean hearing = has(q, "جلسة", "جلسات", "hearing");
        boolean judgment = has(q, "حكم", "أحكام", "احكام", "judgment", "ruling");
        boolean task = has(q, "مهمة", "مهام", "task");
        if (last) {
            numbers.addAll(quote(caseId, caseRef, facts, book, f -> f.type().equals("PROCEDURE") || f.type().equals("JUDGMENT") || isRole(f, "lastHearing")));
        }
        if (next) {
            numbers.addAll(quote(caseId, caseRef, facts, book, f -> isRole(f, "nextHearing") || f.type().equals("DEADLINE") || f.type().equals("TASK")));
        }
        if (deadline && !next) {
            numbers.addAll(quote(caseId, caseRef, facts, book, f -> f.type().equals("DEADLINE")));
        }
        if (hearing && !last && !next) {
            numbers.addAll(quote(caseId, caseRef, facts, book, f -> f.type().equals("HEARING")));
        }
        if (judgment && !last) {
            numbers.addAll(quote(caseId, caseRef, facts, book, f -> f.type().equals("JUDGMENT")));
        }
        if (task && !next) {
            numbers.addAll(quote(caseId, caseRef, facts, book, f -> f.type().equals("TASK")));
        }
        if (has(q, "طلبات", "الطلبات", "claims", "requests")) {
            pleadings.claims(caseId).forEach(a -> numbers.add(assertion(caseId, caseRef, a, book)));
        }
        if (has(q, "دفع", "دفوع", "defen")) {
            pleadings.defenses(caseId).forEach(a -> numbers.add(assertion(caseId, caseRef, a, book)));
        }
        return numbers;
    }

    private static boolean has(String text, String... stems) {
        for (String stem : stems) {
            if (text.contains(stem)) {
                return true;
            }
        }
        return false;
    }

    // ---- 22.1: the summary of a case ----------------------------------------------------------------------------

    @Transactional(readOnly = true)
    Plan summary(UUID caseId) {
        SourceBook book = new SourceBook();
        List<CaseFileReadPort.Fact> facts = caseFile.facts(caseId);
        String ref = caseRef(facts);
        List<Section> sections = new ArrayList<>();
        sections.add(new Section("CASE", quote(caseId, ref, facts, book, f -> f.type().equals("CASE"))));
        sections.add(new Section("LAST_ACTION", quote(caseId, ref, facts, book, f -> f.type().equals("PROCEDURE") || isRole(f, "lastHearing"))));
        sections.add(new Section("CHRONOLOGY", chronology(caseId, ref, book)));
        sections.add(new Section("REQUESTS", pleadings.claims(caseId).stream().map(a -> assertion(caseId, ref, a, book)).toList()));
        sections.add(new Section("DEFENSES", pleadings.defenses(caseId).stream().map(a -> assertion(caseId, ref, a, book)).toList()));
        sections.add(new Section("JUDGMENTS", quote(caseId, ref, facts, book, f -> f.type().equals("JUDGMENT"))));
        sections.add(new Section("NEXT_STEPS", quote(caseId, ref, facts, book, f -> isRole(f, "nextHearing") || f.type().equals("DEADLINE") || f.type().equals("TASK"))));
        return Plan.of(caseId, book, sections);
    }

    // ---- 22.4: a draft memorandum ------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    Plan memo(UUID caseId) {
        SourceBook book = new SourceBook();
        List<CaseFileReadPort.Fact> facts = caseFile.facts(caseId);
        String ref = caseRef(facts);
        List<Section> sections = new ArrayList<>();
        List<Integer> factual = new ArrayList<>(quote(caseId, ref, facts, book, f -> f.type().equals("CASE") || f.type().equals("PROCEDURE")
                || f.type().equals("HEARING") || f.type().equals("JUDGMENT")));
        factual.addAll(chronology(caseId, ref, book));
        sections.add(new Section("FACTS", factual));
        sections.add(new Section("REQUESTS", pleadings.claims(caseId).stream().map(a -> assertion(caseId, ref, a, book)).toList()));
        sections.add(new Section("DEFENSES", pleadings.defenses(caseId).stream().map(a -> assertion(caseId, ref, a, book)).toList()));
        sections.add(new Section("PRECEDENTS", authorities(similarOrEmpty(caseId), book)));
        return Plan.of(caseId, book, sections);
    }

    /** The memorandum draws on the library when the user may open it; without LIBRARY_VIEW that section is simply empty. */
    private List<LibraryReadPort.Authority> similarOrEmpty(UUID caseId) {
        return library.available() ? library.similarTo(caseId, SIMILAR) : List.of();
    }

    // ---- 22.3: precedents ---------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    Plan precedents(UUID caseId, String question) {
        SourceBook book = new SourceBook();
        if (caseId != null) {
            return Plan.of(caseId, book, List.of(new Section("AUTHORITIES", authorities(library.similarTo(caseId, SIMILAR), book))));
        }
        List<String> words = Keywords.of(question);
        if (words.isEmpty()) {
            return Plan.refused(null, Plan.NO_KEYWORDS);
        }
        Map<UUID, Integer> counts = new LinkedHashMap<>();
        Map<UUID, LibraryReadPort.Authority> byId = new LinkedHashMap<>();
        for (String word : words) {
            for (LibraryReadPort.Authority a : library.search(word, PER_WORD)) {
                counts.merge(a.id(), 1, Integer::sum);
                byId.putIfAbsent(a.id(), a);
            }
        }
        int need = (words.size() + 1) / 2;
        List<LibraryReadPort.Authority> adequate = byId.values().stream().filter(a -> counts.get(a.id()) >= need)
                .sorted(Comparator.<LibraryReadPort.Authority>comparingInt(a -> -counts.get(a.id()))).limit(MAX_PASSAGES).toList();
        return Plan.of(null, book, List.of(new Section("AUTHORITIES", authorities(adequate, book))));
    }

    // ---- 22.2: one document -------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    Plan analysis(UUID documentId) {
        DocumentReadPort.DocumentText doc = documents.text(documentId);
        if (!hasText(doc)) {
            return Plan.refused(doc.caseId(), Plan.DOCUMENT_TEXT_UNAVAILABLE);
        }
        SourceBook book = new SourceBook();
        List<Section> sections = new ArrayList<>();
        for (var rule : TextRules.select(doc.text()).entrySet()) {
            sections.add(new Section(rule.getKey(), rule.getValue().stream().map(sentence -> sentenceOf(doc, sentence, book)).toList()));
        }
        return Plan.of(doc.caseId(), book, sections);
    }

    // ---- 22.5: two documents ------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    Plan comparison(UUID documentId, UUID otherId) {
        DocumentReadPort.DocumentText a = documents.text(documentId);
        DocumentReadPort.DocumentText b = documents.text(otherId);
        if (!hasText(a) || !hasText(b)) {
            return Plan.refused(a.caseId(), Plan.DOCUMENT_TEXT_UNAVAILABLE);
        }
        SourceBook book = new SourceBook();
        List<String> left = TextRules.sentences(a.text());
        List<String> right = TextRules.sentences(b.text());
        Set<String> leftKeys = new HashSet<>(left.stream().map(TextRules::comparable).toList());
        Set<String> rightKeys = new HashSet<>(right.stream().map(TextRules::comparable).toList());
        List<Integer> onlyA = pick(left, s -> !rightKeys.contains(TextRules.comparable(s)), a, book);
        List<Integer> onlyB = pick(right, s -> !leftKeys.contains(TextRules.comparable(s)), b, book);
        List<Integer> common = pick(left, s -> rightKeys.contains(TextRules.comparable(s)), a, book);
        return Plan.of(a.caseId(), book, List.of(new Section("ONLY_IN_FIRST", onlyA), new Section("ONLY_IN_SECOND", onlyB),
                new Section("COMMON", common)));
    }

    private List<Integer> pick(List<String> sentences, Predicate<String> keep, DocumentReadPort.DocumentText doc, SourceBook book) {
        List<Integer> numbers = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String sentence : sentences) {
            if (numbers.size() < 15 && keep.test(sentence) && seen.add(TextRules.comparable(sentence))) {
                numbers.add(sentenceOf(doc, sentence, book));
            }
        }
        return numbers;
    }

    private static boolean hasText(DocumentReadPort.DocumentText doc) {
        return ("EXTRACTED".equals(doc.status()) || "OCR_DONE".equals(doc.status())) && doc.text() != null && !doc.text().isBlank();
    }

    private static int sentenceOf(DocumentReadPort.DocumentText doc, String sentence, SourceBook book) {
        List<Field> fields = new ArrayList<>();
        fields.add(new Field("version", Integer.toString(doc.versionNo())));
        if (doc.pageCount() != null) {
            fields.add(new Field("pageCount", doc.pageCount().toString()));
        }
        return book.add("DOCUMENT", doc.documentId(), doc.caseId(), doc.caseReferenceNo(), doc.title(), null, sentence, fields);
    }

    // ---- shared -------------------------------------------------------------------------------------------------

    private List<Integer> chronology(UUID caseId, String caseRef, SourceBook book) {
        List<Integer> numbers = new ArrayList<>();
        for (CaseFileReadPort.Fact f : caseFile.chronology(caseId, CHRONOLOGY)) {
            numbers.add(book.add(f.type(), f.id(), caseId, caseRef, f.title(), f.date(), null, fields(f.fields())));
        }
        return numbers;
    }

    private static List<Integer> quote(UUID caseId, String caseRef, List<CaseFileReadPort.Fact> facts, SourceBook book,
            Predicate<CaseFileReadPort.Fact> wanted) {
        List<Integer> numbers = new ArrayList<>();
        for (CaseFileReadPort.Fact f : facts) {
            if (wanted.test(f)) {
                numbers.add(book.add(f.type(), f.id(), caseId, caseRef, f.title(), f.date(),
                        null, fields(f.fields())));
            }
        }
        return numbers;
    }

    private static int assertion(UUID caseId, String caseRef, PleadingReadPort.Assertion a, SourceBook book) {
        List<Field> fields = new ArrayList<>();
        addField(fields, "status", a.status());
        addField(fields, "party", a.party());
        addField(fields, "legalBasis", a.basis());
        return book.add(a.type(), a.id(), caseId, caseRef, a.title(), a.date(), a.text(), fields);
    }

    private List<Integer> authorities(List<LibraryReadPort.Authority> list, SourceBook book) {
        List<Integer> numbers = new ArrayList<>();
        for (LibraryReadPort.Authority a : list) {
            numbers.add(book.add("LIBRARY", a.id(), null, null, a.subject(), a.judgmentDate(), null, authorityFields(a)));
        }
        return numbers;
    }

    private static List<Field> authorityFields(LibraryReadPort.Authority a) {
        List<Field> fields = new ArrayList<>();
        addField(fields, "court", a.courtName());
        addField(fields, "courtLevel", a.courtLevel());
        addField(fields, "caseNumber", a.caseNumber());
        addField(fields, "caseYear", a.caseYear() == null ? null : a.caseYear().toString());
        addField(fields, "judgmentDate", a.judgmentDate() == null ? null : a.judgmentDate().toString());
        addField(fields, "subject", a.subject());
        addField(fields, "summary", a.summary());
        addField(fields, "direction", a.direction());
        addField(fields, "finality", a.finality());
        addField(fields, "sourceReference", a.sourceReference());
        a.principles().forEach(p -> addField(fields, "principle", p));
        a.warnings().forEach(w -> addField(fields, "warning", w));
        a.reasons().forEach(r -> addField(fields, "reason", r));
        return fields;
    }

    private static List<Field> fields(List<CaseFileReadPort.Field> fields) {
        return fields.stream().map(f -> new Field(f.code(), f.value())).toList();
    }

    private static void addField(List<Field> fields, String code, String value) {
        if (value != null && !value.isBlank()) {
            fields.add(new Field(code, value));
        }
    }

    private static boolean isRole(CaseFileReadPort.Fact fact, String role) {
        return fact.type().equals("HEARING") && fact.fields().stream().anyMatch(f -> f.code().equals("role") && f.value().equals(role));
    }

    private static String caseRef(List<CaseFileReadPort.Fact> facts) {
        return facts.stream().filter(f -> f.type().equals("CASE")).flatMap(f -> f.fields().stream())
                .filter(f -> f.code().equals("referenceNo")).map(CaseFileReadPort.Field::value).findFirst().orElse(null);
    }

    /** A question needs at least a case or some words; the checks that belong to the request shape live here. */
    static void requireShape(AssistantTask task, UUID caseId, UUID documentId, UUID otherDocumentId, String question) {
        boolean blank = question == null || question.isBlank();
        switch (task) {
            case QUESTION -> {
                if (blank) {
                    throw new FieldErrorException("question", "NotBlank");
                }
            }
            case CASE_SUMMARY, MEMO_DRAFT -> {
                if (caseId == null) {
                    throw new FieldErrorException("caseId", "NotNull");
                }
            }
            case DOCUMENT_ANALYSIS -> {
                if (documentId == null) {
                    throw new FieldErrorException("documentId", "NotNull");
                }
            }
            case PRECEDENTS -> {
                if (caseId == null && blank) {
                    throw new FieldErrorException("question", "NotBlank");
                }
            }
            case COMPARISON -> {
                if (documentId == null) {
                    throw new FieldErrorException("documentId", "NotNull");
                }
                if (otherDocumentId == null) {
                    throw new FieldErrorException("otherDocumentId", "NotNull");
                }
            }
        }
    }
}
