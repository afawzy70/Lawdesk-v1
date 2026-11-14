package com.lawdesk.pleadings;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Every dropdown list the administrator manages (LD-DOC-r5, docs/design/LD-DOC-13-admin-lists.md). A list is stored in
 * {@code lookup_value} (LOOKUP), in {@code managed_list_value} (MANAGED) or is the court register (COURT). {@code addable}
 * says whether a new option may be added: a list whose values drive system logic (a Java enum, a status machine, a scope)
 * takes label and order edits only, because a code the code does not know cannot work.
 */
final class ListCatalog {

    enum Source { LOOKUP, MANAGED, COURT }

    record Def(String code, Source source, boolean addable, String titleAr, String titleEn, String noteAr, String noteEn) {
    }

    private static final String ENUM_AR = "قيم يعتمد عليها منطق النظام: يُعدَّل الاسم والترتيب فقط";
    private static final String ENUM_EN = "System logic depends on these values: only the label and the order are editable";

    static final List<Def> ALL = List.of(
            new Def("CASE_IMPORTANCE", Source.LOOKUP, true, "أهمية القضية", "Case importance", "", ""),
            new Def("JUDGMENT_TYPE", Source.LOOKUP, true, "أنواع الأحكام", "Judgment types", "", ""),
            new Def("JUDGMENT_NATURE", Source.LOOKUP, true, "طبيعة الحكم", "Judgment nature", "", ""),
            new Def("DOCUMENT_CATEGORY", Source.LOOKUP, true, "فئات المستندات", "Document categories", "", ""),
            new Def("SETTLEMENT_KIND", Source.MANAGED, true, "أنواع التسويات", "Settlement kinds", "", ""),
            new Def("DIRECTIVE_KIND", Source.MANAGED, true, "أنواع التكليفات", "Directive kinds", "", ""),
            new Def("TEMPLATE_KIND", Source.MANAGED, true, "أنواع القوالب", "Template kinds", "", ""),
            new Def("COURTS", Source.COURT, true, "المحاكم", "Courts",
                    "درجة المحكمة قائمة ثابتة", "A court's level is a fixed list"),
            new Def("CASE_TYPE", Source.LOOKUP, false, "أنواع القضايا", "Case types", ENUM_AR, ENUM_EN),
            new Def("CASE_STATUS", Source.LOOKUP, false, "حالات القضية", "Case statuses", ENUM_AR, ENUM_EN),
            new Def("AUTHORITY_POSITION", Source.LOOKUP, false, "موقف الهيئة (مدعي/مدعى عليه)",
                    "Authority position (plaintiff/defendant)", ENUM_AR, ENUM_EN),
            new Def("COURT_LEVEL", Source.LOOKUP, false, "درجات المحاكم", "Court levels", ENUM_AR, ENUM_EN),
            new Def("HEARING_PURPOSE", Source.LOOKUP, false, "غرض الجلسة", "Hearing purpose", ENUM_AR, ENUM_EN),
            new Def("HEARING_STATUS", Source.LOOKUP, false, "حالة الجلسة", "Hearing status", ENUM_AR, ENUM_EN));

    /** The lists that existed before the administrator catalogue: LOOKUP_MANAGE still manages them. */
    static final Set<String> ORIGINAL = Set.of("JUDGMENT_TYPE", "JUDGMENT_NATURE", "DOCUMENT_CATEGORY");

    private static final Set<String> LOOKUP_CODES = ALL.stream().filter((d) -> d.source() == Source.LOOKUP)
            .map(Def::code).collect(Collectors.toUnmodifiableSet());

    private ListCatalog() {
    }

    static boolean isLookupList(String code) {
        return LOOKUP_CODES.contains(code);
    }

    static Optional<Def> find(String code) {
        return ALL.stream().filter((d) -> d.code().equals(code)).findFirst();
    }
}
