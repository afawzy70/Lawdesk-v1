package com.lawdesk.reports;

import java.util.Map;

import com.lawdesk.cases.CaseReportQueries.StatusGroup;
import com.lawdesk.cases.CaseStatus;
import com.lawdesk.cases.CaseType;
import com.lawdesk.proceedings.Outcome;
import com.lawdesk.proceedings.ProceedingReportQueries.CourtRef;
import com.lawdesk.proceedings.Stage;

/**
 * Column names and values of exported reports in English or Arabic (LD-034). The wording is the screens' wording
 * (frontend {@code src/i18n/messages}), so an export reads like the report it came from.
 */
enum ReportLabels {
    EN(Map.ofEntries(
            Map.entry("referenceNo", "Reference"), Map.entry("title", "Title"), Map.entry("caseType", "Type"),
            Map.entry("status", "Status"), Map.entry("statusGroup", "Open or closed"),
            Map.entry("openedOn", "Opened on"), Map.entry("currentStage", "Current stage"),
            Map.entry("currentCourt", "Current court"), Map.entry("proceedingCount", "Proceedings"),
            Map.entry("judgmentDate", "Judgment date"), Map.entry("caseReferenceNo", "Case reference"),
            Map.entry("caseTitle", "Case title"), Map.entry("stage", "Stage"), Map.entry("court", "Court"),
            Map.entry("number", "Number / year"), Map.entry("outcome", "Outcome"), Map.entry("final", "Final"),
            Map.entry("yes", "Yes"), Map.entry("no", "No"),
            Map.entry("report.CASE_INVENTORY", "Case inventory"), Map.entry("report.JUDGMENTS", "Judgments"),
            Map.entry("OPEN", "Open"), Map.entry("ACTIVE", "Active"), Map.entry("SUSPENDED", "Suspended"),
            Map.entry("CLOSED", "Closed"), Map.entry("ARCHIVED", "Archived"),
            Map.entry("group.OPEN", "Open"), Map.entry("group.CLOSED", "Closed"),
            Map.entry("CIVIL", "Civil"), Map.entry("CRIMINAL", "Criminal"), Map.entry("COMMERCIAL", "Commercial"),
            Map.entry("LABOR", "Labour"), Map.entry("FAMILY", "Family (personal status)"),
            Map.entry("ADMINISTRATIVE", "Administrative"),
            Map.entry("FIRST_INSTANCE", "First instance"), Map.entry("APPEAL", "Appeal"),
            Map.entry("CASSATION", "Cassation"),
            Map.entry("IN_FAVOUR", "In favour of the client"), Map.entry("AGAINST", "Against the client"),
            Map.entry("PARTIAL", "Partly in favour"), Map.entry("DISMISSED", "Dismissed"),
            Map.entry("OTHER", "Other"))),
    AR(Map.ofEntries(
            Map.entry("referenceNo", "المرجع"), Map.entry("title", "العنوان"), Map.entry("caseType", "النوع"),
            Map.entry("status", "الحالة"), Map.entry("statusGroup", "مفتوحة أو مغلقة"),
            Map.entry("openedOn", "تاريخ الفتح"), Map.entry("currentStage", "درجة التقاضي الحالية"),
            Map.entry("currentCourt", "المحكمة الحالية"), Map.entry("proceedingCount", "الدعاوى"),
            Map.entry("judgmentDate", "تاريخ الحكم"), Map.entry("caseReferenceNo", "مرجع القضية"),
            Map.entry("caseTitle", "عنوان القضية"), Map.entry("stage", "درجة التقاضي"), Map.entry("court", "المحكمة"),
            Map.entry("number", "الرقم / السنة"), Map.entry("outcome", "النتيجة"), Map.entry("final", "نهائي"),
            Map.entry("yes", "نعم"), Map.entry("no", "لا"),
            Map.entry("report.CASE_INVENTORY", "جرد القضايا"), Map.entry("report.JUDGMENTS", "الأحكام"),
            Map.entry("OPEN", "مفتوحة"), Map.entry("ACTIVE", "متداولة"), Map.entry("SUSPENDED", "موقوفة"),
            Map.entry("CLOSED", "منتهية"), Map.entry("ARCHIVED", "مؤرشفة"),
            Map.entry("group.OPEN", "مفتوحة"), Map.entry("group.CLOSED", "مغلقة"),
            Map.entry("CIVIL", "مدني"), Map.entry("CRIMINAL", "جنائي"), Map.entry("COMMERCIAL", "تجاري"),
            Map.entry("LABOR", "عمالي"), Map.entry("FAMILY", "أحوال شخصية"), Map.entry("ADMINISTRATIVE", "إداري"),
            Map.entry("FIRST_INSTANCE", "أول درجة"), Map.entry("APPEAL", "الاستئناف"), Map.entry("CASSATION", "النقض"),
            Map.entry("IN_FAVOUR", "لصالح الموكل"), Map.entry("AGAINST", "ضد الموكل"),
            Map.entry("PARTIAL", "لصالح الموكل جزئيًا"), Map.entry("DISMISSED", "رفض الدعوى"),
            Map.entry("OTHER", "أخرى")));

    private final Map<String, String> words;

    ReportLabels(Map<String, String> words) {
        this.words = words;
    }

    /** {@code ar} gives Arabic; anything else, including no value, English. */
    static ReportLabels of(String lang) {
        return "ar".equals(lang) ? AR : EN;
    }

    boolean rightToLeft() {
        return this == AR;
    }

    String word(String key) {
        String word = words.get(key);
        if (word == null) {
            throw new IllegalArgumentException("no label " + key);
        }
        return word;
    }

    String status(CaseStatus status) {
        return word(status.name());
    }

    String type(CaseType type) {
        return word(type.name());
    }

    String group(StatusGroup group) {
        return word("group." + group.name());
    }

    String stage(Stage stage) {
        return stage == null ? null : word(stage.name());
    }

    String outcome(Outcome outcome) {
        return word(outcome.name());
    }

    String yesNo(boolean value) {
        return word(value ? "yes" : "no");
    }

    String court(CourtRef court) {
        return court == null ? null : this == AR ? court.nameAr() : court.nameEn();
    }
}
