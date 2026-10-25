package com.lawdesk.reports;

import java.util.ArrayList;
import java.util.List;

import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.lang.UCharacterDirection;
import com.ibm.icu.text.ArabicShaping;
import com.ibm.icu.text.ArabicShapingException;
import com.ibm.icu.text.Bidi;

/**
 * Text layout for PDF exports (docs/delivery/PHASE-6-ACCEPTANCE.md, LD-034 criterion 6). A PDF draws glyphs left to
 * right exactly as given, with no shaping or bidirectional layout, so each line is prepared here: Arabic letters take
 * their contextual forms (and lam-alef its ligature) with ICU's {@link ArabicShaping}, then the line is reordered
 * into visual order with the Unicode bidirectional algorithm ({@link Bidi}), mirroring brackets.
 */
final class PdfText {

    /** The width of a piece of text in points. */
    interface Measure {
        float width(String text);
    }

    private PdfText() {
    }

    /**
     * One line of logical text in drawing order (left to right). Its direction is that of its first strong letter,
     * as for {@code dir="auto"}: an Arabic title reads right to left in an English export and an English one left to
     * right in an Arabic export.
     *
     * <p>QAD-036 (ASSUMPTION in docs/delivery/PHASE-6-ACCEPTANCE.md): a line without any strong right-to-left
     * character keeps its logical order, left to right, in either export language. The Unicode algorithm would
     * otherwise lay a neutral expression out right to left and turn {@code =1+1} into {@code 1+1=}. The table
     * mirroring and the alignment of Arabic exports do not depend on this.
     *
     * @param rightToLeft the language of the export; it no longer decides the direction of a line by itself
     */
    static String visual(String logical, boolean rightToLeft) {
        if (logical == null || logical.isEmpty()) {
            return "";
        }
        String shaped;
        try {
            shaped = new ArabicShaping(ArabicShaping.LETTERS_SHAPE | ArabicShaping.TEXT_DIRECTION_LOGICAL
                    | ArabicShaping.LENGTH_GROW_SHRINK).shape(logical);
        } catch (ArabicShapingException e) {
            throw new IllegalStateException("Arabic shaping failed", e);
        }
        int baseLevel = !hasStrongRightToLeft(logical) ? Bidi.LTR
                : rightToLeft ? Bidi.LEVEL_DEFAULT_RTL : Bidi.LEVEL_DEFAULT_LTR;
        Bidi bidi = new Bidi(shaped, (byte) baseLevel);
        return bidi.writeReordered(Bidi.DO_MIRRORING | Bidi.REMOVE_BIDI_CONTROLS);
    }

    /** Whether the text holds a letter that is strongly right to left (Arabic, Hebrew, …); digits and signs do not. */
    static boolean hasStrongRightToLeft(String text) {
        return text.codePoints().anyMatch(codePoint -> {
            int direction = UCharacter.getDirection(codePoint);
            return direction == UCharacterDirection.RIGHT_TO_LEFT
                    || direction == UCharacterDirection.RIGHT_TO_LEFT_ARABIC;
        });
    }

    /** Text kept together left to right inside a line of either direction, such as a date after Arabic words. */
    static String isolated(String text) {
        return "\u2066" + text + "\u2069";
    }

    /**
     * Splits logical text into lines no wider than {@code width}: at line breaks, then between words, and inside a word
     * only when it alone is too wide (long references). Widths are measured on the text as it will be drawn.
     */
    static List<String> wrap(String text, float width, Measure measure) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : (text == null ? "" : text).split("\\R", -1)) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (measure.width(candidate) <= width) {
                    line.setLength(0);
                    line.append(candidate);
                    continue;
                }
                if (!line.isEmpty()) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                String rest = word;
                while (measure.width(rest) > width && rest.length() > 1) {
                    int fit = 1;
                    while (fit < rest.length() && measure.width(rest.substring(0, fit + 1)) <= width) {
                        fit++;
                    }
                    lines.add(rest.substring(0, fit));
                    rest = rest.substring(fit);
                }
                line.append(rest);
            }
            lines.add(line.toString());
        }
        return lines;
    }
}
