package com.lawdesk.documents;

import java.util.ArrayList;
import java.util.List;

/**
 * The hold-out corpus (task DOC-R3-OCR, owner requirement 3): new synthetic pages generated AFTER the pass set was frozen
 * (docs/evidence/DOC-R3-OCR/FREEZE.md), not used to choose passes, thresholds or terms. Tables, 7-8 pt type, noise, dates in several
 * formats (Gregorian, Hijri-style, Arabic-Indic digits), mixed Arabic/Latin references, and one multi-page PDF. SYNTH-DOC text, DejaVu Sans.
 */
final class OcrHoldoutCorpus {

    static final String DIR = "backend/src/test/resources/ocr-holdout";

    record Page(OcrCorpus.Case drawing, String file, List<String> terms) {
    }

    static final List<String> LEGAL = List.of("قررت الدائرة التجارية رفض الدفع بعدم الاختصاص المكاني",
            "وألزمت الشركة المدعى عليها بأداء المبلغ المستحق للمؤسسة المدعية", "مع تحمل المصروفات ومقابل أتعاب المحاماة");
    static final List<String> LEGAL_TERMS = List.of("الدائرة", "التجارية", "الاختصاص", "المكاني", "المؤسسة", "المدعية", "أتعاب", "المحاماة");

    static final List<String> DATES = List.of("صدر الحكم بتاريخ 15/03/2026 الموافق 1447/09/25 هـ",
            "تاريخ الإعلان ٢٠/٠٤/٢٠٢٦ وتاريخ الاستحقاق 2026-11-09", "موعد الجلسة التالية ٢٠٢٦-١٢-٠١ الساعة 09:30");
    static final List<String> DATES_TERMS = List.of("15/03/2026", "1447/09/25", "٢٠/٠٤/٢٠٢٦", "2026-11-09", "٢٠٢٦-١٢-٠١", "09:30", "الإعلان",
            "الاستحقاق");

    static final List<String> REFS = List.of("مرجع الملف CASE-2026/778 والطعن رقم ٤٥٦٧/٢٠٢٥", "الفاتورة INV-00921/B بمبلغ 3,450.750 KWD",
            "كتاب وزارة العدل م/2025/3041 المؤرخ في 2025-12-30", "Order No. ORD-88/2026 — شركة البناء الحديث");
    static final List<String> REFS_TERMS = List.of("CASE-2026/778", "٤٥٦٧/٢٠٢٥", "INV-00921/B", "3,450.750", "م/2025/3041", "ORD-88/2026",
            "الحديث", "المؤرخ");

    static final List<String> TABLE = List.of("البند\tالمبلغ\tالتاريخ", "أتعاب الخبير\t1,200.000\t2026-02-10",
            "رسوم التقاضي\t350.500\t2026-02-12", "مصاريف الإعلان\t75.250\t2026-02-15");
    static final List<String> TABLE_TERMS = List.of("أتعاب", "الخبير", "رسوم", "التقاضي", "مصاريف", "الإعلان", "1,200.000", "350.500",
            "75.250", "2026-02-10");

    private OcrHoldoutCorpus() {
    }

    static List<Page> pages() {
        List<Page> pages = new ArrayList<>();
        pages.add(page("h-ar-300dpi-7pt.png", 300, 7, 0, 0, 1, 0, LEGAL, LEGAL_TERMS, false));
        pages.add(page("h-ar-200dpi-8pt.png", 200, 8, 0, 0, 1, 0, LEGAL, LEGAL_TERMS, false));
        pages.add(page("h-ar-150dpi-8pt-noise.jpg", 150, 8, 0, 18, 0.7, 0.8, LEGAL, LEGAL_TERMS, false));
        pages.add(page("h-dates-300dpi-10pt.png", 300, 10, 0, 0, 1, 0, DATES, DATES_TERMS, false));
        pages.add(page("h-dates-150dpi-8pt.png", 150, 8, 0, 0, 1, 0, DATES, DATES_TERMS, false));
        pages.add(page("h-dates-200dpi-10pt-skew2-noise.jpg", 200, 10, 2, 16, 0.7, 0.8, DATES, DATES_TERMS, false));
        pages.add(page("h-refs-300dpi-10pt.png", 300, 10, 0, 0, 1, 0, REFS, REFS_TERMS, false));
        pages.add(page("h-refs-200dpi-8pt-noise.jpg", 200, 8, 0, 18, 0.7, 0.8, REFS, REFS_TERMS, false));
        pages.add(page("h-refs-150dpi-12pt-skew3.png", 150, 12, 3, 0, 1, 0, REFS, REFS_TERMS, false));
        pages.add(page("h-table-300dpi-10pt.png", 300, 10, 0, 0, 1, 0, TABLE, TABLE_TERMS, true));
        pages.add(page("h-table-150dpi-8pt.png", 150, 8, 0, 0, 1, 0, TABLE, TABLE_TERMS, true));
        pages.add(page("h-table-200dpi-7pt-noise.jpg", 200, 7, 0, 16, 0.7, 0.8, TABLE, TABLE_TERMS, true));
        return pages;
    }

    /** The pages of the multi-page PDF (h-multipage.pdf), with the terms of each page. */
    static List<Page> pdfPages() {
        return List.of(page("pdf-1", 300, 10, 0, 0, 1, 0, LEGAL, LEGAL_TERMS, false),
                page("pdf-2", 200, 12, 0, 0, 1, 0, REFS, REFS_TERMS, false),
                page("pdf-3", 200, 10, 0, 12, 0.8, 0.8, TABLE, TABLE_TERMS, true),
                page("pdf-4", 150, 9, 0, 14, 0.7, 0.8, DATES, DATES_TERMS, false));
    }

    private static Page page(String file, int dpi, double points, double angle, double noise, double contrast, double blur,
            List<String> lines, List<String> terms, boolean table) {
        String name = file.contains(".") ? file.substring(0, file.lastIndexOf('.')) : file;
        return new Page(new OcrCorpus.Case(name, "holdout", dpi, lines, angle, noise, contrast, blur, false, points, table), file, terms);
    }
}
