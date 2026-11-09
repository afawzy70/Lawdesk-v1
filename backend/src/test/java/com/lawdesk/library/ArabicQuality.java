package com.lawdesk.library;

/** Test helper: how close a recognised text is to the text that was drawn, after the archive's own Arabic normalisation of letters. */
public final class ArabicQuality {

    private ArabicQuality() {
    }

    /** 1 minus the Levenshtein distance over the longer length, on texts reduced to letters and digits only. */
    public static double similarity(String expected, String actual) {
        String a = letters(expected);
        String b = letters(actual);
        if (a.isEmpty() && b.isEmpty()) {
            return 1.0;
        }
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return 1.0 - (double) previous[b.length()] / Math.max(a.length(), b.length());
    }

    private static String letters(String text) {
        return java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFKC).replaceAll("[^\\p{L}\\p{N}]", "")
                .replaceAll("[\\u064B-\\u065F\\u0640]", "");
    }
}
