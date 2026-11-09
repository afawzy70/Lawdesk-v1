package com.lawdesk.assistant;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Fixed, documented rules that point at sentences of a document (requirements 22.2). A rule only SELECTS a sentence that is
 * already in the text, by a date, a case-number shape or a cue word; it never rewrites, completes or classifies it, and what it
 * does not find is simply not reported.
 */
final class TextRules {

    static final int PER_SECTION = 6;

    private static final Map<String, Pattern> SECTIONS = new LinkedHashMap<>();

    static {
        SECTIONS.put("DATES", Pattern.compile("\\b\\d{4}\\s*[/.-]\\s*\\d{1,2}\\s*[/.-]\\s*\\d{1,2}\\b|\\b\\d{1,2}\\s*[/.-]\\s*\\d{1,2}\\s*[/.-]\\s*\\d{4}\\b"));
        SECTIONS.put("CASE_NUMBERS", Pattern.compile("\\b\\d{1,6}\\s*/\\s*\\d{4}\\b|قضية\\s+رقم|رقم\\s+القضية|case\\s+(no|number)", Pattern.CASE_INSENSITIVE));
        SECTIONS.put("PARTIES", Pattern.compile("المدعي|المدعى\\s+عليه|المستأنف|المطعون|plaintiff|defendant|claimant|respondent|appellant", Pattern.CASE_INSENSITIVE));
        SECTIONS.put("REQUESTS", Pattern.compile("يطلب|يلتمس|نطلب|تطلب|الطلبات|طلب|prays|requests?|seeks?|claims?", Pattern.CASE_INSENSITIVE));
        SECTIONS.put("DEFENSES", Pattern.compile("الدفع|الدفوع|دفع|ندفع|يدفع|defen[cs]e|plea", Pattern.CASE_INSENSITIVE));
        SECTIONS.put("OPERATIVE", Pattern.compile("حكمت|قضت|قررت|منطوق|فلهذه\\s+الأسباب|فلهذه\\s+الاسباب|judgment|ordered|rules\\s+that", Pattern.CASE_INSENSITIVE));
    }

    private TextRules() {
    }

    /** The sentences of the text, in order (a sentence ends at a line break, a full stop, an exclamation or a question mark). */
    static List<String> sentences(String text) {
        List<String> list = new ArrayList<>();
        if (text == null) {
            return list;
        }
        for (String piece : text.split("[\\r\\n.!?؟;]+")) {
            String flat = piece.strip().replaceAll("\\s+", " ");
            if (flat.length() >= 8) {
                list.add(flat);
            }
        }
        return list;
    }

    /** Section code to the sentences it points at (at most {@link #PER_SECTION} each), in the fixed section order. */
    static Map<String, List<String>> select(String text) {
        Map<String, List<String>> found = new LinkedHashMap<>();
        List<String> sentences = sentences(text);
        for (var rule : SECTIONS.entrySet()) {
            List<String> hits = new ArrayList<>();
            for (String sentence : sentences) {
                if (hits.size() < PER_SECTION && rule.getValue().matcher(digits(sentence)).find()) {
                    hits.add(sentence);
                }
            }
            found.put(rule.getKey(), hits);
        }
        return found;
    }

    /** Arabic-Indic and extended digits to ASCII, so that a date written ٢٠٢٦/٠٣/٠١ is found. */
    static String digits(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            int d = Character.digit(c, 10);
            out.append(d >= 0 && c > 127 ? (char) ('0' + d) : c);
        }
        return out.toString();
    }

    /** The comparison form of a sentence: no diacritics or tatweel, one alef and yeh form, ASCII digits, lower case. */
    static String comparable(String sentence) {
        String s = Normalizer.normalize(digits(sentence), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        s = s.replaceAll("[\\u064B-\\u065F\\u0670\\u0640]", "").replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
                .replace('ى', 'ي').replace('ة', 'ه');
        return s.replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
    }
}
