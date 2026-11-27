package com.lawdesk.documents;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The three things the OCR measurement reports, never merged into one number:
 * (a) accuracy of the PRIMARY text (the main reading: character and word error rate against what was drawn, and the terms found in it);
 * (b) recovery through alternate readings (terms the primary text lacks and an alternate word supplies, alternates that were never drawn);
 * (c) search: terms found by a search that reads primary text and alternates, terms found by parts only (as the database matches),
 *     and false positives, queries for words that are NOT on the page (near misses of its own terms and a fixed list of absent words).
 */
final class OcrMeasure {

    /** Words that are on none of the corpus pages; a search for any of them must find nothing. */
    static final List<String> ABSENT = List.of("زئبق", "الكونغو", "KRAKATOA-7731", "QZX-1999/001", "محكمةالفضاء", "9999999");

    record Row(String name, int dpi, double points, double skew, double noise, double charError, double wordError,
            int termsPrimary, int terms, int termsAlternateOnly, int termsFound, int termsByParts, int alternates, int junkAlternates,
            int falsePositives, int negatives, int passes, int uncertain, int wordsRead, long ms, List<String> missed,
            List<String> falseHits) {

        static String header() {
            return "page\tdpi\tpt\tskew\tnoise\ta_char_error\ta_word_error\ta_terms_in_primary\tb_terms_only_via_alternate\tb_alternates"
                    + "\tb_alternates_not_drawn\tc_terms_found\tc_terms_by_parts\tc_false_positives\tpasses\tuncertain_words\twords_read\tms\tmissed\tfalse_hits\n";
        }

        String line() {
            return String.format(java.util.Locale.ROOT, "%s\t%d\t%.0f\t%.1f\t%.0f\t%.3f\t%.3f\t%d/%d\t%d\t%d\t%d\t%d/%d\t%d/%d\t%d/%d\t%d\t%d\t%d\t%d\t%s\t%s%n",
                    name, dpi, points, skew, noise, charError, wordError, termsPrimary, terms, termsAlternateOnly, alternates,
                    junkAlternates, termsFound, terms, termsByParts, terms, falsePositives, negatives, passes, uncertain,
                    wordsRead, ms, String.join(" | ", missed), String.join(" | ", falseHits));
        }
    }

    private OcrMeasure() {
    }

    static Row measure(String name, OcrCorpus.Case drawing, List<String> terms, byte[] picture, TesseractOcrEngine engine)
            throws OcrEngine.OcrException {
        long t0 = System.nanoTime();
        OcrEngine.Reading reading = engine.read(picture);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        String primary = reading.text();
        String truth = drawing.truth();
        Set<String> drawn = new HashSet<>(OcrExactCorpus.searchWords(truth));

        int inPrimary = 0;
        int viaAlternate = 0;
        int found = 0;
        int byParts = 0;
        List<String> missed = new ArrayList<>();
        for (String term : terms) {
            boolean p = OcrExactCorpus.contains(primary, term);
            boolean alt = containsInAlternate(reading, term);
            boolean any = p || alt;
            // the parts of the term as separate words of the primary text (a date read in reversed order), or inside one alternate
            boolean parts = alt || OcrExactCorpus.searchWords(term).stream().allMatch(OcrExactCorpus.searchWords(primary)::contains);
            inPrimary += p ? 1 : 0;
            viaAlternate += !p && any ? 1 : 0;
            found += any ? 1 : 0;
            byParts += parts ? 1 : 0;
            if (!any) {
                missed.add(term);
            }
        }
        int junk = 0;
        for (OcrEngine.Alternate a : reading.alternates()) {
            if (!OcrExactCorpus.searchWords(a.word()).stream().allMatch(drawn::contains)) {
                junk++;
            }
        }
        // False results: near misses of the page's own terms and the fixed absent words must not be found.
        List<String> negatives = new ArrayList<>(ABSENT);
        for (String term : terms) {
            String near = nearMiss(term, drawn);
            if (near != null) {
                negatives.add(near);
            }
        }
        List<String> falseHits = new ArrayList<>();
        for (String negative : negatives) {
            if (OcrExactCorpus.contains(primary, negative) || containsInAlternate(reading, negative)) {
                falseHits.add(negative);
            }
        }
        return new Row(name, drawing.dpi(), drawing.points(), drawing.angleDegrees(), drawing.noiseSigma(),
                1 - OcrCorpus.characterAccuracy(truth, primary), 1 - OcrCorpus.wordAccuracy(truth, primary), inPrimary, terms.size(),
                viaAlternate, found, byParts, reading.alternates().size(), junk, falseHits.size(), negatives.size(), reading.passes(),
                reading.uncertainWords(), reading.words(), ms, missed, falseHits);
    }

    /** True when ONE alternate word holds the term (the rule of the archive search: never across two alternates). */
    static boolean containsInAlternate(OcrEngine.Reading reading, String term) {
        return reading.alternates().stream().anyMatch(a -> OcrExactCorpus.contains(a.word(), term));
    }

    /** The term with one digit or one letter changed, or null when the change is a word that is drawn on the page. */
    static String nearMiss(String term, Set<String> drawn) {
        char[] chars = term.toCharArray();
        for (int i = chars.length - 1; i >= 0; i--) {
            if (Character.isDigit(chars[i])) {
                chars[i] = (char) ('0' + (Character.digit(chars[i], 10) + 3) % 10);
                break;
            }
            if (i == 1 && Character.isLetter(chars[i])) {
                chars[i] = chars[i] == 'ظ' ? 'ض' : 'ظ';
                break;
            }
        }
        String near = new String(chars);
        return near.equals(term) || OcrExactCorpus.searchWords(near).stream().allMatch(drawn::contains) ? null : near;
    }
}
