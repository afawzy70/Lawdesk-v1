package com.lawdesk.documents;

import java.util.ArrayList;
import java.util.List;

/**
 * The corpus of the exact-term measurement (task DOC-R3-OCR-EXACT-TERMS): the three fixtures the UAT journey uploads
 * (tools/journeys/ocr_fixtures, the pages QA-RT-001 was found on) and extra synthetic pages with different point sizes,
 * resolutions, skew, noise, a table and mixed Arabic/Latin references. Every page is SYNTH-DOC (invented text, DejaVu Sans),
 * written to backend/src/test/resources/ocr-corpus by {@link OcrFixtureExport} and read back from there by the tests, so the
 * pictures the engine sees are the committed ones. {@code terms} are the words and references a user would search for; they are
 * never removed to make a run pass.
 */
final class OcrExactCorpus {

    /** A page: how it is drawn, the terms that must be found in its text, and where the picture is committed. */
    record Page(OcrCorpus.Case drawing, String file, List<String> terms, boolean journey) {
    }

    static final String JOURNEY_DIR = "tools/journeys/ocr_fixtures";
    static final String EXTRA_DIR = "backend/src/test/resources/ocr-corpus";

    private OcrExactCorpus() {
    }

    static List<Page> pages() {
        List<Page> pages = new ArrayList<>();
        pages.add(journey("ocr-clear-ar.png", 200, 0, 0, 1, 0, List.of("SYNTH-DOC مذكرة دفاع في الدعوى التجارية",
                "المادة 12 من القانون المدني الكويتي", "تأجيل الجلسة إلى يوم الأحد القادم", "OCRJOURNEYONE"),
                List.of("الكويتي", "المدني", "القانون", "الدعوى", "التجارية", "الجلسة", "12", "OCRJOURNEYONE")));
        pages.add(journey("ocr-scan-skew.jpg", 300, 3, 12, 0.6, 0.8, List.of("SYNTH-DOC صحيفة استئناف الحكم الصادر",
                "عن محكمة أول درجة الدائرة المدنية", "طلب إلغاء الحكم المستأنف وإعادة القضية", "OCRJOURNEYTWO"),
                List.of("استئناف", "الحكم", "محكمة", "الدائرة", "المستأنف", "القضية", "OCRJOURNEYTWO")));
        pages.add(journey("ocr-mixed.png", 300, 0, 0, 1, 0, List.of("SYNTH-DOC عقد الإيجار رقم LD-2026/045",
                "المبلغ المطلوب 1,250.500 KWD يستحق في 2026-03-15", "الشركة الوطنية للتوريد ABC Trading", "OCRJOURNEYTHREE"),
                List.of("LD-2026/045", "الإيجار", "1,250.500", "KWD", "2026-03-15", "الوطنية", "ABC Trading", "OCRJOURNEYTHREE")));

        List<String> body = List.of("مذكرة الدفاع المقدمة من الشركة الكويتية للتجارة العامة",
                "نظرت المحكمة الكلية في الدعوى المرفوعة ضد الوزارة المختصة",
                "وفقا للقانون المدني الكويتي المادة 45 الفقرة الثانية");
        List<String> bodyTerms = List.of("الكويتية", "المحكمة", "الوزارة", "المختصة", "الكويتي", "المدني", "45");
        pages.add(extra("x-ar-150dpi-12pt.png", 150, 12, 0, 0, 1, 0, body, bodyTerms));
        pages.add(extra("x-ar-200dpi-9pt.png", 200, 9, 0, 0, 1, 0, body, bodyTerms));
        pages.add(extra("x-ar-300dpi-16pt.png", 300, 16, 0, 0, 1, 0, body, bodyTerms));
        pages.add(extra("x-ar-300dpi-skew1.5.png", 300, 12, 1.5, 0, 1, 0, body, bodyTerms));
        pages.add(extra("x-ar-300dpi-noise.jpg", 300, 12, 0, 22, 0.7, 0.8, body, bodyTerms));

        List<String> refs = List.of("الملف رقم LD-2026/045 المقيد بتاريخ 2026-03-15",
                "مرجع الكتاب REF-2025/118 الصادر عن إدارة الفتوى",
                "القضية 77/2026 تجاري كلي والطعن 1203/2025 مدني",
                "Reference: Case 77/2026 — Contract LD-2026/045");
        List<String> refTerms = List.of("LD-2026/045", "REF-2025/118", "77/2026", "1203/2025", "الفتوى", "المقيد");
        pages.add(extra("x-ref-150dpi-12pt.png", 150, 12, 0, 0, 1, 0, refs, refTerms));
        pages.add(extra("x-ref-200dpi-12pt.png", 200, 12, 0, 0, 1, 0, refs, refTerms));
        pages.add(extra("x-ref-300dpi-9pt.png", 300, 9, 0, 0, 1, 0, refs, refTerms));

        // harder pages: low resolution, small type with noise and skew (no tuning of these: they are what they are)
        pages.add(extra("x-ref-100dpi-12pt.png", 100, 12, 0, 0, 1, 0, refs, refTerms));
        pages.add(extra("x-ref-72dpi-12pt.png", 72, 12, 0, 0, 1, 0, refs, refTerms));
        pages.add(extra("x-ar-150dpi-9pt-noise.jpg", 150, 9, 0, 20, 0.7, 0.8, body, bodyTerms));
        pages.add(extra("x-ref-200dpi-9pt-skew3-noise.jpg", 200, 9, 3, 18, 0.6, 0.8, refs, refTerms));

        List<String> rows =List.of("الاسم\tالصفة\tالمبلغ", "شركة الكويت للتجارة\tالمدعية\t1,250.500", "وزارة المالية\tالمدعى عليها\t980.000",
                "مكتب الخبير\tجهة الخبرة\t450.250");
        pages.add(new Page(new OcrCorpus.Case("x-table-200dpi", "table", 200, rows, 0, 0, 1, 0, false, 12, true), "x-table-200dpi.png",
                List.of("الصفة", "المدعية", "المالية", "المدعى", "الخبير", "الخبرة", "1,250.500", "980.000"), false));
        return pages;
    }

    private static Page journey(String file, int dpi, double angle, double noise, double contrast, double blur, List<String> lines,
            List<String> terms) {
        String name = file.substring(0, file.lastIndexOf('.'));
        return new Page(new OcrCorpus.Case(name, "fixture", dpi, lines, angle, noise, contrast, blur, false), file, terms, true);
    }

    private static Page extra(String file, int dpi, double points, double angle, double noise, double contrast, double blur,
            List<String> lines, List<String> terms) {
        String name = file.substring(0, file.lastIndexOf('.'));
        return new Page(new OcrCorpus.Case(name, "extra", dpi, lines, angle, noise, contrast, blur, false, points, false), file, terms,
                false);
    }

    // ---- exact-term search, as strict as the database search is lenient ------------------------------------------

    /** The normalised words of a text the way the archive search sees them, with the definite article stripped (V640). */
    static List<String> searchWords(String text) {
        List<String> out = new ArrayList<>();
        for (String w : OcrCorpus.tokens(text)) {
            out.add(w.matches("(وال|بال|كال|فال|لل|ال)[ء-ي]{3,}") ? w.replaceFirst("^(وال|بال|كال|فال|لل|ال)", "") : w);
        }
        return out;
    }

    /**
     * Whether the term is in the text as the same run of whole words: "LD-2026/045" is "ld 2026 045", so a text that reads
     * "02-5" or "ld 2026" does not contain it. Stricter than the database (which matches each part as a substring).
     */
    static boolean contains(String text, String term) {
        List<String> words = searchWords(text);
        List<String> wanted = searchWords(term);
        if (wanted.isEmpty()) {
            return true;
        }
        for (int i = 0; i + wanted.size() <= words.size(); i++) {
            if (words.subList(i, i + wanted.size()).equals(wanted)) {
                return true;
            }
        }
        return false;
    }
}
