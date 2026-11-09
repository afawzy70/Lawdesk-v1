package com.lawdesk.reports;

import java.util.HashMap;
import java.util.Map;

/**
 * Words of the dashboard indicators, the report families and the performance view in exports (LD-058), English or
 * Arabic, next to those of {@link ReportLabels}, which it falls back to. A key without a word is written as the key, never
 * dropped, so an export cannot lose a column.
 */
final class WpLabels {

    private static final Map<String, String> EN = new HashMap<>();
    private static final Map<String, String> AR = new HashMap<>();

    private static void add(String key, String en, String ar) {
        EN.put(key, en);
        AR.put(key, ar);
    }

    static {
        add("indicator.TOTAL_CASES", "Total cases", "إجمالي القضايا");
        add("indicator.CASES_NEW", "New cases", "القضايا الجديدة");
        add("indicator.CASES_ONGOING", "Circulating cases", "القضايا المتداولة");
        add("indicator.CASES_CONCLUDED", "Concluded cases", "القضايا المنتهية");
        add("indicator.RESERVED_FOR_JUDGMENT", "Cases reserved for judgment", "القضايا المحجوزة للحكم");
        add("indicator.JUDGMENTS_ISSUED", "Judgments issued", "الأحكام الصادرة");
        add("indicator.JUDGMENTS_IN_ENFORCEMENT", "Judgments being enforced", "الأحكام الجاري تنفيذها");
        add("indicator.UPCOMING_HEARINGS", "Upcoming hearings", "الجلسات القادمة");
        add("indicator.EXPERT_HEARINGS", "Expert sessions", "جلسات الخبراء");
        add("indicator.APPEAL_DEADLINES", "Appeal deadlines", "مواعيد الطعون");
        add("indicator.OVERDUE_TASKS", "Overdue tasks", "المهام المتأخرة");
        add("indicator.CASES_BY_UNIT", "Cases by sector, department and section", "القضايا حسب القطاع والإدارة والقسم");
        add("indicator.CASES_BY_MEMBER", "Cases by legal member", "القضايا حسب العضو القانوني");
        add("indicator.HIGH_RISK_CASES", "High-risk cases", "القضايا المرتفعة المخاطر");
        add("indicator.OVERDUE_INFO_REQUESTS", "Overdue requests to departments", "طلبات الإدارات المتأخرة");
        add("indicator.LINKED_CASES", "Linked cases", "القضايا المرتبطة");
        add("entity", "Record", "السجل");
        add("title", "Title", "العنوان");
        add("date", "Date", "التاريخ");
        add("status", "Status", "الحالة");
        add("detail", "Detail", "تفصيل");
        add("caseReferenceNo", "Case reference", "مرجع القضية");
        add("caseTitle", "Case title", "عنوان القضية");
        add("notSpecified", "Not specified", "غير محدد");
        add("count", "Count", "العدد");
        add("group", "Group", "المجموعة");
        add("cases", "Cases", "القضايا");
        add("claimedAmount", "Claimed amount (KWD)", "المبلغ المطالب به (د.ك)");
        add("financialImpactAmount", "Financial impact (KWD)", "الأثر المالي (د.ك)");
        add("awardedAmount", "Awarded amount (KWD)", "المبلغ المحكوم به (د.ك)");
        add("potentialLoss", "Potential loss (KWD)", "الخسارة المحتملة (د.ك)");
        add("claims", "Claims", "الطلبات");
        add("defenses", "Defences", "الدفوع");
        add("hearings", "Hearings", "الجلسات");
        add("links", "Links", "الارتباطات");
        add("courtDecisions", "Court decisions", "قرارات المحكمة");
        add("items", "Items", "العناصر");
        add("withoutDocument", "Without a document", "بلا مستند");
        add("reason", "Reason", "السبب");
        add("level", "Level", "المستوى");
        add("kind", "Kind", "النوع");
        add("result", "Result", "النتيجة");
        add("position", "Position", "الموقف");
        add("role", "Role", "الصفة");
        add("year", "Year", "السنة");
        add("topic", "Topic", "الموضوع");
        add("requests", "Requests", "الطلبات");
        add("replies", "Replies", "الردود");
        add("answered", "Answered", "المجاب عنها");
        add("incomplete", "Incomplete replies", "ردود غير مكتملة");
        add("casesAffected", "Cases affected", "قضايا متأثرة");
        add("averageReplyDays", "Average reply (days)", "متوسط مدة الرد (أيام)");
        add("member", "Member", "العضو");
        add("unit", "Unit", "الوحدة");
        add("primaryCases", "Cases (primary)", "القضايا (أساسي)");
        add("primaryOpen", "Open cases (primary)", "القضايا المفتوحة (أساسي)");
        add("alternateCases", "Cases (alternate)", "القضايا (بديل)");
        add("tasks", "Tasks due", "المهام المستحقة");
        add("tasksCompleted", "Tasks completed", "المهام المنجزة");
        add("tasksOpen", "Tasks open", "المهام المفتوحة");
        add("tasksOverdue", "Tasks overdue", "المهام المتأخرة");
        add("hearingsScheduled", "Hearings scheduled", "جلسات محددة");
        add("hearingsHeld", "Hearings held", "جلسات منعقدة");
        add("hearingsAwaiting", "Hearings awaiting result", "جلسات بانتظار النتيجة");
        add("memos", "Defence memos prepared", "مذكرات دفاع معدّة");
        add("directives", "Directives received", "تكليفات واردة");
        add("directivesDone", "Directives done", "تكليفات منفذة");
        add("directivesOverdue", "Directives overdue", "تكليفات متأخرة");
        add("family.CASES_BY_TYPE_COURT_STAGE", "Cases by type, court and stage", "القضايا حسب النوع والمحكمة ودرجة التقاضي");
        add("family.CASES_BY_ORG_UNIT", "Cases by sector, department and section", "القضايا حسب القطاع والإدارة والقسم");
        add("family.CASES_BY_MEMBER", "Cases by legal member", "القضايا حسب العضو القانوني");
        add("family.OPEN_CLOSED_CASES", "Open and closed cases", "القضايا المفتوحة والمغلقة");
        add("family.JUDGMENT_OUTCOMES", "Judgments and their outcomes", "الأحكام ونتائجها");
        add("family.CLAIM_RESULTS", "Result of each claim", "نتائج كل طلب من طلبات الدعوى");
        add("family.DEFENSE_POSITIONS", "Results of defences", "نتائج الدفوع وأوجه الدفاع");
        add("family.LINKED_CASES", "Linked cases", "القضايا المرتبطة");
        add("family.ADJOURNMENTS", "Hearings and adjournment reasons", "الجلسات والتأجيلات وأسبابها");
        add("family.EXPERT_SESSIONS", "Expert sessions", "جلسات الخبراء");
        add("family.DEADLINES_APPEALS", "Legal deadlines and appeals", "المواعيد القانونية والطعون");
        add("family.ENFORCEMENT", "Enforcement of judgments", "تنفيذ الأحكام");
        add("family.MISSING_EVIDENCE", "Missing evidence and documents", "الأدلة والمستندات الناقصة");
        add("family.INFO_REQUESTS", "Requests to departments and late replies", "طلبات الإدارات المعنية والردود المتأخرة");
        add("family.RISK_ASSESSMENT", "Legal risk assessment", "تقييم المخاطر القانونية");
        add("family.PERFORMANCE", "Performance of members and units", "أداء الأعضاء والإدارات");
        add("family.LIBRARY_PRECEDENTS", "Judgments and precedents by topic and court", "الأحكام والسوابق القضائية حسب الموضوع والمحكمة");
        add("group.level.CASE_TYPE", "Type", "النوع");
        add("group.level.COURT", "Court", "المحكمة");
        add("group.level.STAGE", "Stage", "درجة التقاضي");
        add("group.level.SECTOR", "Sector", "القطاع");
        add("group.level.DEPARTMENT", "Department", "الإدارة");
        add("group.level.SECTION", "Section", "القسم");
        add("group.level.STATUS_GROUP", "Open or closed", "مفتوحة أو مغلقة");
        add("group.level.STATUS", "Status", "الحالة");
        add("group.level.YEAR", "Year", "السنة");
        add("group.level.DIRECTION", "Direction", "الاتجاه");
        add("group.level.FINALITY", "Finality", "درجة النهائية");
        add("group.level.CLASSIFICATION", "Classification", "التصنيف");
        add("group.level.TOPIC", "Topic", "الموضوع");
        add("noProceeding", "No proceeding yet", "لا توجد دعوى بعد");
        add("currentPositionNote", "Each case is counted once, at its current stage and court (the highest stage, and the latest court at that stage).",
                "تُحسب كل قضية مرة واحدة عند درجتها ومحكمتها الحالية (أعلى درجة، وأحدث محكمة عند تلك الدرجة).");
        add("primaryNote", "Each case is counted once: under its primary involvement and its primary member. Cases with no value are listed as not specified.",
                "تُحسب كل قضية مرة واحدة: بحسب الجهة الأساسية والعضو الأساسي. القضايا بلا قيمة تظهر «غير محدد».");
        add("judgments", "Judgments", "الأحكام");
        add("outcomeNote", "Filters apply to the case. Each judgment is counted once; the outcome is from the client's point of view.",
                "تسري المرشحات على القضية. يُحسب كل حكم مرة واحدة، والنتيجة من وجهة نظر الموكل.");
        add("currentResultNote", "Each claim or defence is counted once, at the result given by its latest judgment, so amounts of the same claim at different levels are never added together.",
                "يُحتسب كل طلب أو دفع مرة واحدة بنتيجة آخر حكم فيه، فلا تُجمع مبالغ الطلب نفسه عبر درجات التقاضي.");
        add("noPosition", "No judgment has addressed it", "لم يتناوله حكم");
        add("reasonNotRecorded", "Reason not recorded", "السبب غير مسجل");
        add("distinctCasesNote", "The case column counts distinct cases within each row; rows can share a case, so it is not added up.",
                "عمود القضايا يعدّ القضايا المختلفة في كل صف؛ قد تشترك الصفوف في قضية، فلا يُجمع.");
        add("calcStatus", "Calculation", "الاحتساب");
        add("requiredAmount", "Amount required (KWD)", "المبلغ المطلوب تنفيذه (د.ك)");
        add("requiredAmountNote", "The amount required for enforcement is its own kind of amount and is not mixed with the others.",
                "المبلغ المطلوب تنفيذه نوع مستقل من المبالغ ولا يُخلط بغيره.");
        add("withoutLoss", "Without a recorded loss", "بلا خسارة مسجلة");
        add("riskNote", "Latest assessment of each case. A risk level is a decision aid, not a prediction of the judgment. The potential loss is its own amount.",
                "آخر تقييم لكل قضية. مستوى المخاطر أداة مساندة للقرار وليس توقعًا للحكم. الخسارة المحتملة مبلغ مستقل.");
        add("linkKind.SUBJECT_LINK", "Subject link", "ارتباط موضوعي");
        add("linkKind.JOINDER_REQUEST", "Joinder request", "طلب ضم");
        add("linkKind.JOINDER_DECISION", "Joinder decision", "قرار ضم");
        add("linkKind.REFERRAL", "Referral", "إحالة");
        add("linkStatus.REQUESTED", "Requested", "مطلوب");
        add("linkStatus.ACTIVE", "Active", "قائم");
        add("linkStatus.REJECTED", "Rejected", "مرفوض");
        add("linkStatus.ENDED", "Ended", "منتهٍ");
        add("linkNote", "A link joins two cases and is counted once.", "الارتباط يجمع قضيتين ويُحسب مرة واحدة.");
        for (String[] w : new String[][] {
                {"ACCEPTED", "Accepted", "مقبول"}, {"PARTIAL", "Partly accepted", "مقبول جزئيًا"}, {"REJECTED", "Rejected", "مرفوض"},
                {"INADMISSIBLE", "Inadmissible", "غير مقبول شكلًا"}, {"NO_JURISDICTION", "No jurisdiction", "عدم اختصاص"},
                {"REFERRED", "Referred", "محال"}, {"EXTINGUISHED", "Extinguished", "منقضٍ"},
                {"WAIVER_RECORDED", "Waiver recorded", "إثبات تنازل"}, {"ABANDONMENT_RECORDED", "Abandonment recorded", "إثبات ترك"},
                {"NOT_DECIDED", "Not decided", "لم يُفصل فيه"}, {"DEFERRED", "Deferred", "مؤجل"}, {"OTHER", "Other", "أخرى"}}) {
            add("claimResult." + w[0], w[1], w[2]);
        }
        for (String[] w : new String[][] {
                {"ACCEPTED", "Accepted", "مقبول"}, {"REJECTED", "Rejected", "مرفوض"}, {"PARTIAL", "Partly accepted", "مقبول جزئيًا"},
                {"NOT_ADDRESSED", "Not addressed", "لم تتناوله المحكمة"}, {"MOOT", "Moot", "منتفٍ"},
                {"DEFERRED", "Deferred", "مؤجل"}, {"OTHER", "Other", "أخرى"}}) {
            add("defensePosition." + w[0], w[1], w[2]);
        }
        for (String[] w : new String[][] {
                {"FORMAL", "Formal", "شكلي"}, {"PROCEDURAL", "Procedural", "إجرائي"}, {"SUBSTANTIVE", "Substantive", "موضوعي"},
                {"OTHER", "Other", "أخرى"}}) {
            add("defenseKind." + w[0], w[1], w[2]);
        }
        for (String[] w : new String[][] {
                {"OPEN", "Open", "مفتوحة"}, {"IN_PROGRESS", "In progress", "قيد التنفيذ"}, {"DONE", "Done", "منجزة"},
                {"CANCELLED", "Cancelled", "ملغاة"}, {"NEW", "New", "جديدة"}, {"WAITING_DOCUMENTS", "Waiting for documents", "بانتظار مستندات"},
                {"WAITING_OTHER", "Waiting for another body", "بانتظار جهة أخرى"}, {"COMPLETED", "Completed", "مكتملة"},
                {"LATE", "Late", "متأخرة"}}) {
            add("taskStatus." + w[0], w[1], w[2]);
        }
        for (String[] w : new String[][] {
                {"LOW", "Low", "منخفضة"}, {"NORMAL", "Normal", "عادية"}, {"HIGH", "High", "مرتفعة"}, {"URGENT", "Urgent", "عاجلة"}}) {
            add("priority." + w[0], w[1], w[2]);
        }
        for (String[] w : new String[][] {
                {"DRAFT", "Draft", "مسودة"}, {"PENDING_APPROVAL", "Pending approval", "بانتظار الاعتماد"}, {"SENT", "Sent", "مرسل"},
                {"AWAITING", "Awaiting reply", "بانتظار الرد"}, {"PARTIAL", "Partly answered", "رد جزئي"},
                {"INCOMPLETE", "Incomplete reply", "رد غير مكتمل"}, {"COMPLETE", "Complete", "مكتمل"},
                {"OVERDUE", "Overdue", "متأخر"}, {"CLOSED", "Closed", "مغلق"}}) {
            add("requestStatus." + w[0], w[1], w[2]);
        }
        for (String[] w : new String[][] {
                {"OPEN", "Open", "مفتوح"}, {"COMPLETED", "Completed", "منجز"}, {"MISSED", "Missed", "فائت"}}) {
            add("deadlineStatus." + w[0], w[1], w[2]);
        }
        for (String[] w : new String[][] {
                {"NOT_STARTED", "Not started", "لم يبدأ"}, {"IN_PROGRESS", "In progress", "جارٍ"},
                {"PARTIALLY_EXECUTED", "Partly executed", "منفذ جزئيًا"}, {"EXECUTED", "Executed", "منفذ"},
                {"OBSTRUCTED", "Obstructed", "معوّق"}, {"NOT_REQUIRED", "Not required", "غير مطلوب"}}) {
            add("enforcementStatus." + w[0], w[1], w[2]);
        }
        for (String[] w : new String[][] {
                {"JUDGMENT", "Judgment", "حكم"}, {"HEARING", "Hearing", "جلسة"}, {"PROCEEDING", "Proceeding", "دعوى"},
                {"TASK", "Task", "مهمة"}, {"TEXT", "Text", "نص"}}) {
            add("deadlineSource." + w[0], w[1], w[2]);
        }
        add("risk.VERY_HIGH", "Very high", "مرتفعة جدًا");
        add("risk.HIGH", "High", "مرتفعة");
        add("risk.MEDIUM", "Medium", "متوسطة");
        add("risk.LOW", "Low", "منخفضة");
        add("notAssessed", "Not assessed", "لم تقيّم");
        add("decided", "Not decided by any judgment", "لم يُفصل فيها بحكم");
        add("evidence.REQUIRED", "Required", "مطلوب");
        add("evidence.UNAVAILABLE", "Unavailable", "غير متاح");
        add("hearingKind.EXPERT", "Expert sessions", "جلسات الخبراء");
        add("referrals", "Expert referrals", "إحالات الخبراء");
        add("notAvailableYet", "Not available yet: the module that owns this data is not installed.",
                "غير متاح بعد: الوحدة المسؤولة عن هذه البيانات غير مثبتة.");
        add("noRanking", "Objective counts only. Members are listed by name, never ranked, and no outcome of a judgment is used.",
                "أعداد موضوعية فقط. يُعرض الأعضاء بالاسم دون ترتيب تفاضلي، ولا تُستخدم نتيجة الحكم.");
        add("total", "Total", "الإجمالي");
        add("period", "Period", "الفترة");
    }

    private final boolean arabic;
    private final ReportLabels base;

    private WpLabels(boolean arabic, ReportLabels base) {
        this.arabic = arabic;
        this.base = base;
    }

    static WpLabels of(String lang) {
        return new WpLabels("ar".equals(lang), ReportLabels.of(lang));
    }

    String lang() {
        return arabic ? "ar" : "en";
    }

    ReportLabels base() {
        return base;
    }

    String word(String key) {
        String own = (arabic ? AR : EN).get(key);
        if (own != null) {
            return own;
        }
        try {
            return base.word(key);
        } catch (IllegalArgumentException e) {
            return key;
        }
    }

    /**
     * A status ({@code detail} false) or a detail of an indicator row in this language: case status and type, judgment
     * outcome and stage, hearing status and purpose, task status and priority, request and deadline status. A unit name
     * or other free text passes through unchanged.
     */
    String value(IndicatorCode.Entity entity, boolean detail, String raw) {
        if (raw == null) {
            return null;
        }
        String key = switch (entity) {
            case CASE, JUDGMENT -> raw;
            case HEARING -> (detail ? "purpose." : "hearing.") + raw;
            case TASK -> (detail ? "priority." : "taskStatus.") + raw;
            case INFO_REQUEST -> detail ? null : "requestStatus." + raw;
            case DEADLINE -> (detail ? "deadlineSource." : "deadlineStatus.") + raw;
        };
        if (key == null) {
            return raw;
        }
        String own = (arabic ? AR : EN).get(key);
        if (own != null) {
            return own;
        }
        try {
            return base.word(key);
        } catch (IllegalArgumentException e) {
            return raw;
        }
    }

    /** A value that may be absent: "not specified" in the export language. */
    String orNotSpecified(String value) {
        return value == null || value.isBlank() ? word("notSpecified") : value;
    }
}
