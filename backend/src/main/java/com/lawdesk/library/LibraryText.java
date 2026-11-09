package com.lawdesk.library;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Small text helpers of the library. The Arabic normal form itself lives in the database ({@code library_normalize},
 * V640) so that what is stored, indexed and searched for cannot disagree; this class only splits already normalised text
 * into words and builds query strings from them.
 */
final class LibraryText {

    /** A word of a normalised text: Latin letters, digits and the Arabic letters (hamza dropped by the normaliser). */
    private static final Pattern WORD = Pattern.compile("[a-z0-9ء-ي]+");

    /**
     * Grammar words that say nothing about a subject (normalised spelling). Deliberately short: it only keeps the
     * suggestions from matching on "in", "on", "that"; no legal term is ever treated as a stop word.
     */
    private static final Set<String> STOP_WORDS = Set.of("في", "من", "علي", "الي", "عن", "ان", "او", "ثم", "قد", "لا",
            "ما", "هذا", "هذه", "ذلك", "التي", "الذي", "الذين", "كان", "كانت", "بين", "عند", "بعد", "قبل", "مع", "حتي",
            "كل", "لم", "لن", "هو", "هي", "تم", "كما", "اذا", "اذ", "لكن", "بل", "بان", "وان", "ولا", "وقد", "فقد");

    private LibraryText() {
    }

    static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** The audit trail keeps a reason of at most 64 characters. */
    static String shortReason(String reason) {
        return reason.length() <= 64 ? reason : reason.substring(0, 64);
    }

    /** Refuses a query-string value outside the allowed codes, like every other list in the API (400, field and code). */
    static void requireCode(String field, String value, String allowedPattern) {
        if (value != null && !value.matches(allowedPattern)) {
            throw new com.lawdesk.api.FieldErrorException(field, "Pattern");
        }
    }

    /** The distinct words of a normalised text, in order of appearance. */
    static List<String> words(String normalised) {
        Set<String> found = new LinkedHashSet<>();
        if (normalised != null) {
            var matcher = WORD.matcher(normalised);
            while (matcher.find()) {
                found.add(matcher.group());
            }
        }
        return new ArrayList<>(found);
    }

    /** The words that carry meaning: three letters or more and not a grammar word, at most {@code max} of them. */
    static List<String> terms(String normalised, int max) {
        return words(normalised).stream().filter(w -> w.length() >= 3 && !STOP_WORDS.contains(w)).limit(max).toList();
    }

    /** A {@code to_tsquery('simple', ...)} string in which every word must match, each as a prefix; null when empty. */
    static String allOfPrefixes(String normalised) {
        List<String> words = words(normalised);
        return words.isEmpty() ? null : String.join(" & ", words.stream().map(w -> w + ":*").toList());
    }

    /** A {@code to_tsquery('simple', ...)} string in which any word may match, exact words only; null when empty. */
    static String anyOf(List<String> terms) {
        return terms.isEmpty() ? null : String.join(" | ", terms);
    }

    static String keywordsToColumn(List<String> keywords) {
        if (keywords == null) {
            return null;
        }
        List<String> cleaned = keywords.stream().filter(k -> k != null).map(k -> k.strip().replaceAll("\\s+", " "))
                .filter(k -> !k.isEmpty()).distinct().toList();
        return cleaned.isEmpty() ? null : String.join("\n", cleaned);
    }

    static List<String> keywordsFromColumn(String column) {
        return column == null || column.isBlank() ? List.of() : List.of(column.split("\n"));
    }
}
