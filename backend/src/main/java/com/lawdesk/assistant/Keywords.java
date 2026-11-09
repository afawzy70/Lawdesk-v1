package com.lawdesk.assistant;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The search words of a question: its words, without the little words that every question has, at most {@link #MAX}. The words
 * are only handed to the existing search, which does the Arabic normalisation (hamza, taa marbuta, diacritics, the article).
 */
final class Keywords {

    static final int MAX = 6;

    private static final Set<String> STOP = Set.of(
            "في", "من", "على", "الى", "إلى", "عن", "ما", "ماذا", "هل", "هي", "هو", "التي", "الذي", "الذين", "هذا", "هذه", "ذلك", "تلك",
            "كان", "كانت", "يكون", "اي", "أي", "متى", "كيف", "اين", "أين", "لماذا", "كم", "الا", "إلا", "ان", "أن", "إن", "هنا", "قضية", "دعوى", "بعد", "قبل", "عند", "مع", "ثم", "او", "أو", "لا", "لم",
            "the", "and", "for", "are", "was", "were", "what", "which", "who", "whom", "when", "where", "how", "why", "with", "from",
            "this", "that", "these", "those", "has", "have", "had", "does", "did", "not", "about", "into", "case", "any", "all", "can");

    private Keywords() {
    }

    static List<String> of(String question) {
        if (question == null) {
            return List.of();
        }
        String text = Normalizer.normalize(question, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        Set<String> words = new LinkedHashSet<>();
        for (String token : text.split("[^\\p{L}\\p{N}]+")) {
            String bare = token.startsWith("ال") && token.length() > 4 ? token.substring(2) : token;
            boolean numeric = token.chars().allMatch(Character::isDigit);
            boolean tooShort = numeric ? token.length() < 2 : token.length() < 3;
            if (token.isEmpty() || tooShort || STOP.contains(token) || STOP.contains(bare)) {
                continue;
            }
            words.add(token);
        }
        List<String> list = new ArrayList<>(words);
        return list.size() > MAX ? list.subList(0, MAX) : list;
    }
}
