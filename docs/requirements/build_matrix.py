#!/usr/bin/env python3
"""Builds lawdesk-comprehensive-requirements-matrix.md from lawdesk-comprehensive-source.txt.

Every non-empty source line is accounted for exactly once: it becomes a requirement row (bullets and sentences), a
group label (sub-headings and lead-ins that end in a colon), or a section heading. Run it from this directory:
    python3 -I build_matrix.py [--check]
--check fails when the committed matrix is stale or a source line is not covered (coverage both ways, LD-DOC-00-001).
"""
import json
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import register_data as RD  # noqa: E402
SOURCE = HERE / "lawdesk-comprehensive-source.txt"
OUT = HERE / "lawdesk-comprehensive-requirements-matrix.md"

ORDINALS = ['أولاً', 'ثانياً', 'ثالثاً', 'رابعاً', 'خامساً', 'سادساً', 'سابعاً', 'ثامناً', 'تاسعاً', 'عاشراً',
            'الحادي عشر', 'الثاني عشر', 'الثالث عشر', 'الرابع عشر', 'الخامس عشر', 'السادس عشر', 'السابع عشر',
            'الثامن عشر', 'التاسع عشر', 'العشرون', 'الحادي والعشرون', 'الثاني والعشرون', 'الثالث والعشرون',
            'الرابع والعشرون', 'الخامس والعشرون', 'السادس والعشرون', 'السابع والعشرون', 'الثامن والعشرون',
            'التاسع والعشرون', 'الثلاثون', 'الحادي والثلاثون', 'الثاني والثلاثون', 'الثالث والثلاثون',
            'الرابع والثلاثون', 'الخامس والثلاثون', 'السادس والثلاثون', 'السابع والثلاثون']

# Status vocabulary (see the legend written into the matrix).
V, P, PA, M, B = "VERIFIED", "PRESENT", "PARTIAL", "MISSING", "BLOCKED"

# per section: phase, priority, work package, existing, gap, changes (data/api/ui/permissions), acceptance, default status
SEC = {
 0: ("—", "—", "WP0", "توجيهات عامة", "تُطبَّق على كل الأقسام", "—", "—", M),
 1: ("1", "أعلى", "WP1", "legal_case (مرجع داخلي، عنوان، نوع من 6 قيم، موضوع، تاريخ الفتح، حالة 5 قيم)، الرقم الآلي وموقف الهيئة والعضو المختص والبديل والأثر المالي (LD-045)، رقم المحكمة وتاريخ الرفع داخل درجة التقاضي",
    "دائرة المحكمة، تاريخ الورود، جهة الإحالة، الوقائع منفصلة عن الموضوع، قيمة المطالبة (منفصلة عن الأثر المالي)، الأهمية، توسيع نوع القضية، بحث بالموظف والإدارة",
    "بيانات: أعمدة وجداول بحث. API: توسيع intake/cases وفلاتر البحث. واجهة: شاشة التسجيل والملف. صلاحيات: نطاق الرؤية (القسم 30)", "S1, S5(13)", PA),
 2: ("1", "أعلى", "WP1", "party وcase_party (نوع شخص/منشأة، اسم، رقم، هاتف، بريد، عنوان، جانب)", "المحامي/الوكيل، الملاحظات، تسمية الرقم المدني/المنشأة وسريته، شاشة قضايا الطرف مع احترام الصلاحيات", "بيانات: counsel، ملاحظات، سرية. API: قضايا الطرف. واجهة: بطاقة الطرف", "S1", PA),
 3: ("1", "أعلى", "WP1", "فهرس فريد لمرجع القضية فقط", "مقارنة الرقم والمحكمة والدرجة والأطراف والموضوع وتنبيه (مؤكد مقابل مشابه) دون منع", "خدمة تشابه + endpoint فحص قبل الحفظ + لوحة تنبيه", "S1", M),
 4: ("1", "أعلى", "WP1", "proceeding (درجات: أول درجة/استئناف/تمييز) مع محكمة ورقم وسنة وتاريخ رفع وأب", "الدستورية والتنفيذ كمسارين، شاشة التسلسل الكامل، ربط المستندات بالدرجة", "توسيع stage ونموذج المسار (قرار D2)، عرض التسلسل", "S13", PA),
 5: ("1", "أعلى", "WP2", "—", "كامل الوحدة: الطلبات، نسخ التعديل، نتيجة كل طلب لكل درجة، الإحصاءات", "جداول claim, claim_revision, claim_result. API+واجهة+تقارير", "S1, S2, S13", M),
 6: ("1", "أعلى", "WP1", "—", "هيكل قطاع/إدارة/قسم قابل للإدارة، ارتباط عدة جهات رئيسية/مساندة، حفظ لقطة تاريخية", "org_unit, case_org_involvement + شاشة إعدادات", "S15 (نطاق)", M),
 7: ("1", "أعلى", "WP3", "hearing (تاريخ، غرض، حالة، قرار، سلسلة تأجيل)، جلسات الخبراء", "الوقت، القاضي/الخبير، الحاضرون، طلبات الخصوم، الإجراء المطلوب، سبب التأجيل، حالة عدم تحديد موعد، عدّادات، نوع الجلسة (محكمة/خبراء/تحقيق/أخرى)", "توسيع hearing وسجل زمني موحد ومتابعة 'بلا موعد'", "S9", PA),
 8: ("1", "أعلى", "WP3", "—", "كامل الوحدة مع تمييز الارتباط الموضوعي/طلب الضم/قرار الضم/الإحالة وظهوره في الملفين", "case_link (مؤشران متقابلان) وسجل قرار", "S10", M),
 9: ("1", "أعلى", "WP2", "judgment (تاريخ، نتيجة من 5 قيم، ملخص، نهائي) لكل درجة", "منطوق وأسباب كاملان، النوع، نتائج الطلبات والدفوع، المبالغ والمصروفات والأتعاب، صفة الحكم، قابلية الطعن، الإعلان، الميعاد، الصورة", "توسيع judgment + ربط claim_result وdefense_position", "S1, S13", PA),
 10: ("1", "أعلى", "WP4", "—", "كامل الوحدة؛ الاحتساب الآلي لا يُعتمد دون قواعد معتمدة (لا مدد مفترضة)", "deadline, deadline_rule, official_holiday (فارغة حتى اعتماد المالك)", "S14", M),
 11: ("2", "عالية", "WP5", "special_decision (إيقاف تنفيذ، إجراء عاجل)", "وحدة تنفيذ الأحكام كاملة وكشف المتأخر", "enforcement_file, enforcement_action", "—", M),
 12: ("2", "عالية", "WP5", "case_document (PDF فقط)", "كل أنواع المذكرات والمراسلات ودورة الاعتماد، والتمييز بين تسجيل الإرسال والإرسال الفعلي", "legal_document + workflow", "—", M),
 13: ("2", "عالية", "WP5", "case_document PDF ≤10 لكل قضية، محتوى في قاعدة البيانات", "Word وصور، إصدارات، تصنيف، OCR، بحث نصي، ربط بالسجلات، منع الحذف النهائي", "تخزين ملفات (قرار D7)، document_version, document_link", "S8", PA),
 14: ("2", "عالية", "WP6", "task (عنوان، مسند إليه، استحقاق، أولوية، 4 حالات) والعضو المختص/البديل", "7 حالات، تاريخ البداية، تعليمات، سبب التأخر، إعادة إسناد بسجل", "توسيع task + task_history", "S11, S15", PA),
 15: ("2", "عالية", "WP6", "جلسات ومهام بتواريخ وتقارير حسب LD-028", "تقويم شخصي وعام، عروض يومي/أسبوعي/شهري/قائمة، كشف التعارض وتعيين بديل", "calendar_feed موحد وخدمة تعارض", "S11", PA),
 16: ("1", "الأعلى", "WP7", "—", "المكتبة كاملة: أحكام، مبادئ، تصنيف، بحث، ربط، اقتراح غير توليدي، حجية وحداثة، تقارير", "library_judgment, library_principle, case_precedent_link + بحث", "S4, S5", M),
 17: ("1", "الأعلى", "WP2", "—", "الدفوع، موقف المحكمة لكل دفع بكل درجة، مكتبة الدفوع، تقارير", "defense, defense_position, defense_template", "S3, S8", M),
 18: ("1", "الأعلى", "WP3", "—", "طلبات الإفادة، الردود المتعددة، التنبيه والتصعيد، التقارير", "info_request, info_reply, info_follow_up", "S6", M),
 19: ("1", "عالية", "WP3", "—", "تقييم المخاطر بعناصره العشرة وإصداراته وخطة المعالجة وتقريره", "risk_assessment(+version), risk_remediation", "S7", M),
 20: ("1", "عالية", "WP2", "—", "الأدلة بأدوارها وربطها بالوقائع والدفوع والطلبات وكشف النواقص", "evidence_item, case_fact, evidence_link", "S8", M),
 21: ("1", "عالية", "WP3", "case_event (خط زمني للأحداث الحالية)", "الملخص التنفيذي بالعناصر الـ17، تصفية الخط، روابط مباشرة، تمييز المستقبل/غير المنفذ", "عرض مُجمّع من الوحدات + توسيع case_event", "S1, S12", PA),
 22: ("3", "ثالثة", "WP10", "—", "كامل المساعد؛ التحقق التشغيلي متعطل حتى اعتماد مزود مصرّح (قرار D8)", "ports + retrieval + سجل استفسارات", "S16", B),
 23: ("2", "متوسطة", "WP9", "—", "كامل الوحدة؛ التسجيل لا يغلق القضية", "settlement + approvals", "—", M),
 24: ("2", "متوسطة", "WP8", "تقارير عبء الخبراء (LD-033)", "لوحة أداء الأعضاء والإدارات دون الاعتماد على نتيجة الحكم", "مؤشرات مجمّعة بالصلاحيات", "—", PA),
 25: ("2", "متوسطة", "WP9", "—", "سجل التكليفات والقرارات الإدارية وربطها بالعمل الناتج", "directive + link", "—", M),
 26: ("2", "عالية", "WP8", "لوحة LD-030 بمؤشرات حالية", "المؤشرات الـ14 وكلها قابلة للنقر إلى قوائم مفلترة متطابقة", "تعريف مؤشرات مشترك مع القوائم", "—", PA),
 27: ("2", "عالية", "WP8", "تقارير: جرد القضايا، الأحكام، الجلسات، الخبراء؛ تصدير CSV/Excel/PDF", "الفئات الـ17 كلها مع صلاحيات التصدير ومنع تضاعف المبالغ", "خدمات تقرير مشتركة", "—", PA),
 28: ("2", "عالية", "WP9", "فلاتر قائمة القضايا", "بحث موحد بكل المجالات، نص المستندات بعد OCR، فلاتر متقدمة، تطبيق الصلاحيات على النتائج", "فهرس بحث (قرار D7)", "—", M),
 29: ("2", "متوسطة", "WP9", "—", "9 أنواع نماذج، نسخ معتمدة، تعبئة آلية، اعتماد المختص", "form_template(+version)", "—", M),
 30: ("1", "أعلى", "WP1", "3 أدوار افتراضية و28 صلاحية عامة؛ تقييد تعديل القضية بالعضو (LD-045)", "7 أدوار، نطاق بالقطاع/الإدارة/القسم/طبيعة العمل، سياسة وصول مركزية للملفات والبحث والتقارير والمساعد", "access_scope, case_grant, CaseAccessPolicy (قرار D3)", "S15", PA),
 31: ("1", "أعلى", "WP1", "audit_event (LD-011) وتدقيق LD-045", "تغطية كل الأحداث الجديدة وقيم سابقة/جديدة وحماية الحساس", "توسيع الأحداث؛ لا تعديل للسجل", "S15", PA),
 32: ("2", "عالية", "WP6", "تذكيرات المهام والمواعيد (LD-028)", "مركز موحد بالأنواع الـ11، منع التكرار، حفظ الربط والصلاحيات", "notification + dedupe_key", "S14", PA),
 33: ("1", "عالية", "WP1", "حالات القضية الخمس (LD-017)", "تطابق الدورة الكاملة دون ترتيب جامد؛ الإغلاق لا يحذف التاريخ", "قواعد انتقال مرنة + تحقق عند الإغلاق", "S12", PA),
 34: ("0", "أعلى", "WP0", "Spring Security، نسخ احتياطي واستعادة (LD-041)، عربي/RTL، متجاوب", "تشفير الحساس، حماية الملفات، واجهات التكامل المستقبلية، اختبارات أمنية جديدة", "عقود تكامل (بلا اتصال فعلي)", "—", PA),
 35: ("0", "أعلى", "WP0", "حزم اختبار LD-039", "السيناريوهات الـ16 كما وردت + اختبارات الفشل/الصلاحيات/الترحيل", "journey harness", "S1–S16", M),
 36: ("0", "أعلى", "WP0", "—", "الالتزام بالمراحل الثلاث وتنفيذ كل البنود", "—", "—", M),
 37: ("0", "أعلى", "WP0", "—", "كل التعليمات النهائية", "—", "—", M),
}

# keyword -> (status, note); first match inside a section wins, otherwise the section default applies
OV = {
 1: [("رقم القضية الداخلي", P, "مرجع LD-YYYY-NNNNN؛ تسميته بين قرار D1"), ("رقم القضية لدى المحكمة", V, "رقم لكل درجة محفوظ (LD-045 على UAT)"),
     ("اسم القضية", P, "عنوان القضية"), ("نوع القضية", PA, "6 قيم من 10+؛ التصنيف قرار D2"), ("المحكمة والدائرة", PA, "المحكمة موجودة والدائرة غير موجودة"),
     ("درجة التقاضي", PA, "3 درجات؛ ينقص الدستورية والتنفيذ"), ("تاريخ رفع الدعوى", P, "داخل الدرجة"), ("تاريخ ورود", M, ""), ("الجهة التي أحالت", M, ""),
     ("صفة الجهة", V, "موقف الهيئة (LD-045 على UAT)"), ("موضوع الدعوى", PA, "حقل موضوع واحد؛ الوقائع منفصلة مطلوبة"), ("القيمة المالية", PA, "الأثر المالي موجود (LD-045)؛ قيمة المطالبة منفصلة ناقصة"),
     ("درجة أهمية", M, ""), ("حالة القضية", P, "5 حالات"), ("العضو القانوني", V, "مختص وبديل (LD-045 على UAT)"), ("الملاحظات الداخلية", P, "case_note"),
     ("المستندات الأساسية", PA, "PDF بحد 10"), ("البحث", PA, "بحث نصي على القائمة؛ الخصم/الموظف/الإدارة ناقصة"), ("رقم القضية الآلي", V, "")],
 2: [("اسم المدعي", P, "اسم الطرف وجانبه (LD-045)"), ("الصفة القانونية", PA, "role + side؛ الصفة الفرعية ناقصة"), ("الرقم المدني", PA, "الحقل موجود بتسمية 'الرقم الآلي للطرف' (LD-045) ويرجع للتسمية بالوثيقة"),
     ("بيانات التواصل", P, "هاتف/بريد/عنوان"), ("ربط الطرف", P, "case_party متعدد القضايا؛ العرض يُتحقق منه")],
 4: [("المحكمة الكلية", P, "FIRST_INSTANCE"), ("الاستئناف", P, "APPEAL"), ("التمييز", P, "CASSATION")],
 7: [("تاريخ الجلسة", PA, "التاريخ موجود والوقت ناقص"), ("هل تم التأجيل", P, "حالة ADJOURNED"), ("عند التأجيل", P, "adjourned_from_id يحفظ السابقة"),
     ("قرار المحكمة", P, "decision"), ("نوع الجلسة", PA, "غرض الجلسة موجود بقيم مختلفة"), ("الملاحظات", P, "notes")],
 9: [("تاريخ الحكم", P, ""), ("درجة التقاضي", P, "عبر الدرجة"), ("النتيجة", PA, "5 قيم؛ قيم الطلبات في القسم 5"), ("حالة الحكم", PA, "final فقط"),
     ("ويجب الاحتفاظ بجميع الأحكام", P, "حكم لكل درجة")],
 14: [("إسناد القضية", V, "LD-045"), ("عضو بديل", V, "LD-045"), ("إسناد مهمة", P, "task.assignee"), ("تاريخ الإنجاز", P, "due_date"), ("الأولوية", P, ""), ("وتكون حالات المهمة", PA, "4 حالات من 7")],
 30: [("مدير النظام", P, "ADMIN"), ("مدير الإدارة القانونية", P, "LEGAL_MANAGER"), ("العضو القانوني", P, "LEGAL_STAFF")],
 31: [("اسم المستخدم", P, ""), ("نوع العملية", P, ""), ("تاريخ ووقت", P, "")],
 33: [("تسجيل القضية", P, ""), ("تعيين العضو", V, "LD-045"), ("إغلاق القضية", PA, "الحالة موجودة؛ التحقق عند الإغلاق ناقص")],
 34: [("دعم العربية", P, "RTL وتجاوب مُختبران"), ("نسخ احتياطية", P, "LD-041"), ("استعادة", P, "LD-041")],
}

# Delivered work packages (2026-11-09): section -> (default status, evidence, remaining gaps as (keyword, status, note)).
# PRESENT here means: the code, the migration and passing backend/frontend suites exist on the deployed SHA, and the
# section's acceptance journey ran green on UAT (docs/evidence/DOC-WP*/); individual rows are not yet mapped one by one
# to a scenario step, so none is claimed VERIFIED. The final reconciliation pass does that row by row.
DELIVERED = {
 1: (P, "DOC-WP1 (journey wp1-access)", []),
 2: (P, "DOC-WP1 (journey wp1c-parties)", []),
 3: (P, "DOC-WP1 (journey wp1c-parties)", []),
 4: (P, "DOC-WP1 (journey wp1d-stages)", []),
 5: (P, "DOC-WP2 (journey wp2: S1, S2, S13)", []),
 6: (P, "DOC-WP1 (journey wp1-access)", []),
 7: (P, "DOC-WP3 (journey: S9)", []),
 8: (P, "DOC-WP3 (journey: S10, S12)", [("ويمكن ربط", PA, "حالات الارتباط المقترحة تحتاج اعتماد المالك")]),
 9: (P, "DOC-WP2 (journey wp2: S1, S13)", [("الميعاد", PA, "ميعاد الطعن ينتظر WP4"), ("الصورة", PA, "تخزين الصورة ينتظر WP5")]),
 16: (P, "DOC-WP7 (journey wp7: S4, S5)", [("صورة", PA, "تخزين نسخة الحكم ينتظر WP5"), ("الدفوع", PA, "مقارنة الدفوع والطلبات تنتظر ربط WP2")]),
 17: (P, "DOC-WP2 (journey wp2: S3)", [("السوابق", PA, "ربط الدفع بالسوابق (defense_precedent) لم يُنفَّذ بعد")]),
 18: (P, "DOC-WP3 (journey: S6)", [("المشرف", PA, "لا نموذج 'مشرف مباشر': التصعيد يدوي إلى مستخدم مسمى")]),
 19: (P, "DOC-WP3 (journey: S7)", []),
 20: (P, "DOC-WP2 (journey wp2: S8)", [("طلب إفادة", PA, "ربط النواقص بطلب إفادة محجوز ولم يُوصَل")]),
 21: (P, "DOC-WP3 (journey: S12)", []),
 10: (P, "DOC-WP4 (journey wp4: S14 للمواعيد اليدوية والقواعد المعتمدة)", [("الاحتساب", PA, "الاحتساب الآلي معطل حتى يدخل المختصون قاعدة ويعتمدوها (قرار D6)"), ("العطلات", PA, "جدول العطلات فارغ حتى إدخال المختصين")]),
 11: (P, "DOC-WP5 (journey wp5)", []),
 12: (P, "DOC-WP5 (journey wp5)", [("الإرسال", PA, "تسجيل الإرسال بيد المستخدم؛ لا تكامل إرسال فعلي")]),
 13: (P, "DOC-WP5 (journey wp5؛ OCR غير مُتحقَّق)", [("OCR", B, "لا Tesseract بالعربية على الخادم؛ الجودة غير مقيسة"), ("الصور", PA, "نص الصور ينتظر OCR")]),
 14: (P, "DOC-WP6 (journey wp6: S15 مهمة منفردة)", [("حالات", PA, "سبع حالات من نص الوثيقة؛ POSTPONED وREJECTED غير واردة فلم تُضف")]),
 15: (P, "DOC-WP6 (journey wp6: S11)", [("تعارض", PA, "الكشف يسمي العضو البديل دون إعادة إسناد بنقرة")]),
 32: (P, "DOC-WP6 (journey wp6) + DOC-WP3", [("البريد", B, "لا بريد ولا SMS (قرار D11)")]),
 24: (P, "DOC-WP8 (اختبارات API؛ الرحلة على UAT جزئية)", [("نتيجة الحكم", PA, "لوحة الأداء موضوعية بلا ترتيب؛ لا مقياس يعتمد نتيجة الحكم وحدها")]),
 26: (P, "DOC-WP8 (UAT: اللوحة والمؤشرات للمدير بعد إصلاح 33b2982)", [("القطاع", PA, "التوزيع حسب القطاع/الإدارة/القسم يعتمد الجهات المسجلة")]),
 27: (P, "DOC-WP8 (اختبارات API والتصدير)", [("الأداء", PA, "هدف 500 مللي ثانية عند 50 ألف قضية غير محقق لعدد من الاستعلامات (انظر DOC-WP8)")]),
 30: (P, "DOC-WP1 (journey wp1-access: S15)", [("واجهة", PA, "شاشة الأدوار لم تُختبر على UAT: حساب qa.admin معطل بانتظار المالك")]),
 31: (P, "DOC-WP1 + أحداث التدقيق في كل حزمة", []),
 33: (P, "DOC-WP3 (journey: S12)", []),
 35: (PA, "tools/journeys/* + docs/evidence/DOC-WP*", [("اختبارات", PA, "S1–S15 بالرحلات على UAT (S14 جزئي)، وS16 بمزود اختبار؛ لا مصفوفة متصفحات كاملة ولا Firefox/WebKit")]),
 36: (PA, "docs/reports/PHASE-*-REPORT-ar.md", [("المراحل", PA, "نُفِّذت الحزم وفق المراحل الثلاث؛ المطابقة النهائية صفًا صفًا لم تكتمل")]),
 37: (PA, "docs/reports + docs/handbook", [("التعليمات", PA, "التقارير والوثائق النهائية مكتوبة؛ لا يُعلن اكتمال المشروع مع وجود متعطل (المساعد التشغيلي وOCR) وغير مكتمل (أداء 50 ألف قضية، S14 الكامل)")]),
 22: (B, "DOC-WP10 (journey wp10: S16 بمزود اختبار غير إنتاجي)", [("المساعد", B, "التحقق التشغيلي متعطل: لا مزود معتمد (قرار D8)؛ المكونات والاسترجاع والاستشهاد والرفض والسجل منفذة ومُختبرة بمزود اختبار حتمي"), ("ملخص", PA, "تلخيص استخراجي فقط؛ لا إعادة صياغة دون مزود"), ("OCR", B, "ينتظر OCR")]),
 34: (PA, "DOC-WP10 (عقود تكامل معطلة)", [("تكامل", PA, "خمس محوّلات معطلة دون اتصال أو بيانات دخول")]),
 23: (P, "DOC-WP9 (journey wp9)", [("القضية", PA, "تحذير عند إغلاق قضية بتسوية غير معتمدة غير منفذ")]),
 25: (P, "DOC-WP9 (journey wp9)", []),
 28: (P, "DOC-WP9 (journey wp9)", [("OCR", B, "نص المستندات بعد OCR متعطل: لا Tesseract بالعربية على الخادم"), ("المستندات", PA, "البحث في المستندات بالعنوان/الاسم حتى WP5")]),
 29: (P, "DOC-WP9 (journey wp9)", []),
}

PHASE_NAME = {"0": "أساس مشترك", "1": "المرحلة الأولى", "2": "المرحلة الثانية", "3": "المرحلة الثالثة", "—": "—"}
STATUS_AR = {V: "موجود ومتحقق منه", P: "موجود في الشيفرة (يُتحقق منه بـWP0)", PA: "موجود جزئيًا", M: "غير موجود", B: "متعطل باعتماد خارجي"}


def parse(lines):
    heads = {}
    for i, line in enumerate(lines):
        for n, o in enumerate(ORDINALS, 1):
            if line.startswith(o + ":"):
                heads.setdefault(n, i)
    starts = sorted((idx, n) for n, idx in heads.items())
    secs = {0: (0, starts[0][0])}
    for k, (idx, n) in enumerate(starts):
        end = starts[k + 1][0] if k + 1 < len(starts) else len(lines)
        secs[n] = (idx, end)
    return heads, secs


def classify(lines, lo, hi, head_idx):
    """Yield (kind, lineno, text, group); kind is head | group | req."""
    group = ""
    for i in range(lo, hi):
        t = lines[i].strip()
        if not t:
            continue
        no = i + 1
        if i == head_idx:
            yield "head", no, t, ""
            continue
        if head_idx == -1 and i < 2:
            yield "group", no, t, ""
            continue
        bullet = t.startswith("- ")
        body = t[2:].strip() if bullet else t
        if t.startswith("<badge"):
            yield "group", no, re.sub(r"</?badge[^>]*>", "", t), ""
            continue
        if not bullet and (body.endswith(":") or (len(body) < 60 and not body.endswith(".") and not body.endswith("،"))):
            group = re.sub(r"^\d+\.\s*", "", body.rstrip(":")) if len(body) < 60 or body.endswith(":") else group
            yield "group", no, body, group
            continue
        yield "req", no, body, group


def status_for(sec, text):
    if sec in DELIVERED:
        status, evidence, gaps = DELIVERED[sec]
        for kw, st, note in gaps:
            if kw in text:
                return st, note, evidence
        return status, "", evidence
    for kw, st, note in OV.get(sec, []):
        if kw in text:
            return st, note, "—"
    return SEC[sec][7], "", "—"


ROOT = HERE.parent.parent
EXPECTED_ROWS = 650
COLUMNS = ["المعرّف", "السطر", "المجموعة", "المتطلب (نص المصدر)", "IMPLEMENTED", "VERIFIED_BY_DEVELOPER", "QA_ACCEPTED", "سبب العائق", "ملاحظة (ما تبقى / لماذا لم يُتحقق)"]
IMPL_VALUES = ("YES", "PARTIAL", "NO", "BLOCKED")
SUMMARY = HERE / "TESTED-STATUS-SUMMARY.md"
YES_NOW = {"LD-DOC-13-001", "LD-DOC-13-004", "LD-DOC-28-010", "LD-DOC-28-013", "LD-DOC-16-043"}  # delivered after the round-2 gap list (OCR, #89, #95)


def gap_status():
    out = {}
    for line in (HERE / "gap-closure.md").read_text(encoding="utf8").split("\n"):
        c = [x.strip() for x in line.strip().strip("|").split("|")]
        if len(c) >= 6 and c[0].startswith("LD-DOC-"):
            out[c[0]] = c[2]
    return out


_cache = {}


def _tests():
    if "t" not in _cache:
        _cache["t"] = {p.stem: p for p in (ROOT / "backend/src/test").rglob("*.java")}
        _cache["f"] = {p.name: p for p in (ROOT / "frontend/src").rglob("*.test.*")}
    return _cache["t"], _cache["f"]


def _journey(alias):
    if alias not in _cache:
        d = json.loads((ROOT / "docs/evidence" / RD.J[alias]).read_text(encoding="utf8"))
        rel = next((s["value"]["release"] for s in d["steps"] if s["step"].startswith("deployed release")), "?")
        _cache[alias] = ([s["step"] for s in d["steps"]], rel[:7])
    return _cache[alias]


def check_bundle(key):
    """Fail loudly when a named test, method, frontend file, evidence file or journey step does not exist."""
    b = RD.EVIDENCE[key]
    tests, fronts = _tests()
    for t in b.get("t", []):
        cls, _, m = t.partition("#")
        if cls not in tests:
            raise SystemExit(f"evidence {key}: backend test class {cls} not found")
        if m and f"void {m}(" not in tests[cls].read_text(encoding="utf8"):
            raise SystemExit(f"evidence {key}: method {cls}#{m} not found")
    for f in b.get("f", []):
        if f not in fronts:
            raise SystemExit(f"evidence {key}: frontend test {f} not found")
    for x in b.get("x", []):
        if not (ROOT / x).exists():
            raise SystemExit(f"evidence {key}: file {x} not found")
    for alias, steps in b.get("j", []):
        have, _ = _journey(alias)
        for st in steps:
            if not any(h.startswith(st) for h in have):
                raise SystemExit(f"evidence {key}: journey {alias} has no step starting with {st!r}")


def evidence_text(key):
    b = RD.EVIDENCE[key]
    parts = []
    if b.get("t"):
        parts.append("T: " + ", ".join(b["t"]) + f" (CI green {RD.TEST_SHA})")
    if b.get("f"):
        parts.append("F: " + ", ".join(b["f"]) + f" (CI green {RD.TEST_SHA})")
    for alias, steps in b.get("j", []):
        parts.append(f"J: {alias} \u00ab{'; '.join(steps[:2])}\u00bb (UAT {_journey(alias)[1]})")
    if b.get("x"):
        parts.append("E: " + ", ".join(Path(x).name for x in b["x"]) + (f" ({b['at']})" if b.get("at") else ""))
    return "<br>".join(parts) + f" [{key}]"


def bundle_for(sec, seq):
    if sec == 0:
        return "CONNECTED", ""
    if sec == 35:
        return (RD.V35[seq], "") if seq in RD.V35 else (None, "")
    if sec == 37:
        return (RD.V37[seq], "") if seq in RD.V37 else (None, "")
    for seqs, key, why in RD.V.get(sec, []):
        if seqs is None or seq in seqs:
            return key, why
    return None, ""


def rid_parts(rid):
    _, _, s, q = rid.split("-")
    return int(s), int(q)


def decide(rows):
    """rows: list of dict(id, sec, seq, base). Returns per row dict with impl, cause, dep, note, key."""
    gaps = gap_status()
    res = {}
    for r in rows:
        rid, sec, seq, base = r["id"], r["sec"], r["seq"], r["base"]
        impl, cause, dep, note = None, "NONE", "", ""
        if rid in RD.OV:
            impl, cause, dep, note, _b = RD.OV[rid]
        elif rid in YES_NOW:
            impl = "YES"
        elif sec == 22:
            if seq in RD.A22_RULES:
                impl, cause, dep = "PARTIAL", "EXTERNAL_PROVIDER", "D8: the real model's behaviour cannot be tested before a provider is approved"
                note = "The rule is enforced by code and tested with a deterministic non-production provider (AssistantBoundariesTest, AssistantAskApiTest, journey wp10: refusal without sources, 404 for a case the account cannot see, drafts stay DRAFT, every query logged). Not verifiable on a real model."
            elif seq in RD.A22_BRIEF:
                impl, cause, dep = "PARTIAL", "EXTERNAL_PROVIDER", "D8 for the natural-language assistant wording; the records-only part needs no provider"
                note = ("Records-only part DONE and verified by the developer: GET /api/v1/cases/{id}/brief (CaseBriefApiTest, PR #102; docs/evidence/DOC-R3-BRIEF) answers from the system's own records, "
                        "marked not model-generated, each item with its source record, NOT_RECORDED where the records are silent, access policy before validation. "
                        "NOT done: asking in natural language, any model-generated wording, extraction from document text. This is not the AI assistant and does not satisfy the model-based requirement.")
            elif seq in RD.A22_LIBRARY:
                impl, cause, dep = "PARTIAL", "EXTERNAL_PROVIDER", "D8 for the assistant wording; the library function itself needs no provider"
                note = "Independent part DONE and verified in the library: similar-judgment suggestions with their reasons and a citation (source, date, number) - SimilarJudgmentApiTest, journey wp7. The assistant on top of it is not operational."
            else:
                impl, cause, dep = "BLOCKED", "EXTERNAL_PROVIDER", "D8: owner approves an AI provider and what may be sent to it (LD-DOC-09)"
                note = "Not operational by design: no provider approved, nothing leaves the system. The retrieval, citation, refusal and logging parts exist and are tested with a deterministic provider (see PARTIAL rows of section 22)."
        elif sec == 35 and seq == 14:
            impl, cause, dep = "PARTIAL", "LEGAL_INPUT", "D6: no approved legal deadline rules or holidays"
            note = "Journey wp4 ran green on UAT with SYNTHETIC rules and manual approved deadlines; reminders ran. Scenario with real legal rules cannot be run."
        elif sec == 35 and seq == 16:
            impl, cause, dep = "PARTIAL", "EXTERNAL_PROVIDER", "D8"
            note = "Journey wp10 ran with a non-production deterministic provider (sources shown, refusal, no invented citation). Operational assistant is disabled."
        elif sec == 35:
            impl = "YES"
        elif sec == 37 and seq in (15,):
            impl, cause, dep = "PARTIAL", "EXTERNAL_PROVIDER", "D8"
            note = "Drafts stay DRAFT and are never final in the test provider path; no operational assistant."
        elif sec == 37 and seq in (17,):
            impl, cause, dep = "PARTIAL", "OWNER_DECISION", "Owner visual approval of the design package (docs/design/PHASE-7-OWNER-VISUAL-REVIEW.md)"
            note = "Designs were delivered as documents and prototypes; approval is the owner's."
        elif sec == 37 and seq in (20, 21):
            impl, cause, dep = "PARTIAL", "NONE", "All BLOCKED/PARTIAL rows above, QA acceptance"
            note = "The project is not complete: open rows remain and nothing is QA_ACCEPTED."
        elif sec == 37 and seq in (1, 16, 18, 19) or (sec == 37 and seq in RD.V37):
            impl = "YES"
            if seq == 16:
                note = "Deliverable (docs/design/LD-DOC-03-data-model.md); not a testable behaviour."
            if seq == 18:
                note = "UAT is published; not a testable behaviour."
            if seq == 19:
                note = "Handbook delivered (PR #83); not a testable behaviour."
        elif sec == 36:
            impl = None  # second pass
        elif gaps.get(rid) in ("IMPLEMENTED", "SATISFIED_BY_EXISTING", "PRESENT"):
            impl = "YES"
        elif base in ("VERIFIED", "PRESENT"):
            impl = "YES"
        elif base == "MISSING":
            impl = "NO"
        else:
            raise SystemExit(f"row {rid} has no classification (base {base}, gap {gaps.get(rid)})")
        res[rid] = dict(impl=impl, cause=cause, dep=dep, note=note)
    # section 36 roll-up
    for r in rows:
        if r["sec"] != 36:
            continue
        mapped = []
        for sec, lo, hi in RD.ROLL36[r["seq"]]:
            mapped += [x["id"] for x in rows if x["sec"] == sec and lo <= x["seq"] <= hi]
        st = [res[m]["impl"] for m in mapped]
        impl = "YES" if all(s == "YES" for s in st) else ("BLOCKED" if all(s == "BLOCKED" for s in st) else "PARTIAL")
        n_not = sum(1 for s in st if s != "YES")
        causes = sorted({res[m]["cause"] for m in mapped if res[m]["impl"] != "YES" and res[m]["cause"] != "NONE"})
        res[r["id"]] = dict(impl=impl, cause=",".join(causes) or "NONE", dep="see the rolled-up rows",
                            note=f"Umbrella, rolled up from {len(mapped)} detailed rows ({n_not} not YES). Not tested on its own.")
    return res


def render(lines):
    heads, secs = parse(lines)
    seen = set()
    body = []
    rows = []
    sec_blocks = []
    for n in range(0, 38):
        lo, hi = secs[n]
        s = SEC[n]
        head_idx = heads.get(n, -1)
        title = lines[head_idx].strip() if n else "مقدمة الوثيقة (الأسطر 1–5)"
        seq = 0
        groups = []
        items = []
        for kind, no, text, group in classify(lines, lo, hi, head_idx):
            seen.add(no)
            if kind == "head":
                continue
            if kind == "group":
                groups.append((no, text))
                continue
            seq += 1
            st, _note, _ev = status_for(n, text)
            rows.append(dict(id=f"LD-DOC-{n:02d}-{seq:03d}", sec=n, seq=seq, base=st))
            items.append((f"LD-DOC-{n:02d}-{seq:03d}", no, group, text))
        sec_blocks.append((n, title, s, groups, items))
    dec = decide(rows)
    used = set()
    totals = {k: 0 for k in IMPL_VALUES}
    verified = 0
    causes = {}
    for n, title, s, groups, items in sec_blocks:
        body.append(f"\n## LD-DOC-{n:02d} \u2014 {title}\n")
        body.append(f"- **المرحلة**: {PHASE_NAME[s[0]]} · **الأولوية**: {s[1]} · **حزمة العمل**: {s[2]} · **سيناريوهات القبول المرتبطة**: {s[6]}")
        if n in DELIVERED:
            body.append(f"- **التحديث (2026-11-09)**: نُفِّذ بحزمة العمل؛ الدليل: {DELIVERED[n][1]}.")
        body.append("\n| " + " | ".join(COLUMNS) + " |")
        body.append("|" + "---|" * len(COLUMNS))
        for rid, no, group, text in items:
            sec, seq = rid_parts(rid)
            d = dec[rid]
            key, why = bundle_for(sec, seq)
            impl = d["impl"]
            ver = ""
            note = d["note"]
            if impl == "YES" and key:
                if key not in used:
                    check_bundle(key)
                    used.add(key)
                ver = evidence_text(key)
                verified += 1
            elif impl == "YES":
                note = (note + " " if note else "") + (why or "no test or journey step found that exercises this row")
            elif key and impl != "YES" and not d["note"]:
                note = why or ""
            if impl != "YES":
                causes[d["cause"]] = causes.get(d["cause"], 0) + (1 if impl in ("BLOCKED", "PARTIAL", "NO") else 0)
            totals[impl] += 1
            cause = d["cause"] if impl != "YES" else ""
            dep = d["dep"]
            note_full = (note + (f" Dependency: {dep}." if dep and impl != "YES" else "")).strip() or "\u2014"
            body.append(f"| {rid} | {no} | {group or '\u2014'} | {text.replace('|', '/')} | {impl} | {ver or ''} | | {cause or '\u2014'} | {note_full.replace('|', '/')} |")
        if groups:
            body.append("\n<sub>عناوين المجموعات في المصدر: " + "؛ ".join(f"{t} (س{no})" for no, t in groups) + "</sub>")
    nonblank = {i + 1 for i, l in enumerate(lines) if l.strip()}
    missing = sorted(nonblank - seen)
    nreq = sum(totals.values())
    blocked_causes = {}
    for r in rows:
        d = dec[r["id"]]
        if d["impl"] == "BLOCKED":
            blocked_causes[d["cause"]] = blocked_causes.get(d["cause"], 0) + 1
    remaining = {}
    for r in rows:
        d = dec[r["id"]]
        if d["impl"] == "PARTIAL":
            for c in d["cause"].split(","):
                remaining[c] = remaining.get(c, 0) + 1
    stats = dict(rows=nreq, impl=totals, verified=verified, unverified=nreq - verified, blocked_causes=blocked_causes,
                 partial_causes=remaining, no_rows=[r["id"] for r in rows if dec[r["id"]]["impl"] == "NO"], nonblank=len(nonblank), missing=missing)
    out = ["# سجل تتبع المتطلبات \u2014 وثيقة LawDesk الشاملة (R3)\n"]
    out.append("مولَّد آليًا من `lawdesk-comprehensive-source.txt` بواسطة `build_matrix.py` و`register_data.py` (لا يُعدَّل يدويًا). المصدر وبصمته في `SOURCE.md`.\n")
    out.append("## ثلاثة أعمدة منفصلة\n")
    out.append("- **IMPLEMENTED** (`YES` / `PARTIAL` / `NO` / `BLOCKED`): هل الوظيفة موجودة في الشيفرة المنشورة. `PARTIAL` يعني أن جزءًا مستقلًا منفذ والباقي مذكور في الملاحظة؛ `BLOCKED` لا جزء مستقلًا منفذًا منه.")
    out.append(f"- **VERIFIED_BY_DEVELOPER**: فارغ، أو الدليل الدقيق: صنف اختبار (أو `Class#method`) وSHA الذي أخضر عليه CI (`{RD.TEST_SHA}`)، أو رحلة UAT واسم الخطوة وSHA النشر المسجل داخل ملف الدليل. لا يُملأ إلا لصف `YES`، وكل اسم يُفحص وجوده في المستودع عند التوليد. التحقق على مستوى الميزة: الاختبار/الخطوة يُنشئ السجل ويقرؤه أو يمارس القاعدة، ولا يُدّعى أن كل حقل فُحص منفردًا.")
    out.append("- **QA_ACCEPTED**: فارغ دائمًا. لا يملؤه إلا مراجعة Codex المستقلة. لا شيء مقبول بعد.\n")
    out.append("رحلات UAT المرجعية الأربع عشرة أُعيد تشغيلها على الإصدار المنشور `18822a83bff8e2a38b83db4370674a9f69919d81` (الإصدار مسجّل داخل كل ملف دليل في `docs/evidence/DOC-RETEST/*-18822a8.json`)، وقد نجحت كلها. أدلة الاختبارات الآلية مرتبطة بـCI الأخضر على `18822a8`. هذا تحقق مطوّر لا قبول QA. انظر `RETEST-PACKAGE-FOR-CODEX.md`.\n")
    out.append("## الملخص (من المصفوفة المولَّدة)\n")
    out.append(f"- أسطر المصدر غير الفارغة: {len(nonblank)}، كلها مغطاة: {'نعم' if not missing else 'لا \u2014 ' + str(missing)}")
    out.append(f"- متطلبات ذرية (صفوف): {nreq}")
    out.append("- توزيع IMPLEMENTED: " + "، ".join(f"{k}: {totals[k]}" for k in IMPL_VALUES))
    out.append(f"- توزيع VERIFIED_BY_DEVELOPER: بدليل: {verified}، فارغ: {nreq - verified}")
    out.append(f"- توزيع QA_ACCEPTED: مقبول: 0، فارغ: {nreq}")
    out.append("- BLOCKED حسب السبب: " + "، ".join(f"{k}: {v}" for k, v in sorted(blocked_causes.items())))
    out.append("- PARTIAL حسب سبب ما تبقى: " + "، ".join(f"{k}: {v}" for k, v in sorted(remaining.items())))
    out.append("- صفوف NO: " + (", ".join(stats["no_rows"]) or "لا شيء"))
    out.append("\nالصفان اللذان كانا «غير موجود» في الجولة الأولى: `LD-DOC-00-001` و`LD-DOC-00-002` (الترابط بين الوحدات). أُغلقا في PR #86 باختبار `ConnectedCaseApiTest` ورحلة `connected_journey.py`؛ لا يلزم كود جديد.\n")
    out.append("## الصفوف المجزأة (ليست ضمن الـ650)\n")
    out.append("| الصف | الجزء | IMPLEMENTED | الدليل / السبب |")
    out.append("|---|---|---|---|")
    for k in ("DEADLINE_ENGINE", "ASSISTANT_SUMMARY", "UX_ENGINES"):
        check_bundle(k)
    for rid in ("LD-DOC-10-011", "LD-DOC-10-012"):
        out.append(f"| {rid} | محرك الاحتساب والاعتماد (قواعد اصطناعية) | YES | {evidence_text('DEADLINE_ENGINE')} |")
        out.append(f"| {rid} | قواعد المدد القانونية والعطلات الرسمية | BLOCKED (LEGAL_INPUT, D6) | لا قاعدة ولا عطلة مُدخلة؛ لا اختراع |")
    out.append("| LD-DOC-22-003 | تلخيص استخراجي بمصادر | YES | " + evidence_text("ASSISTANT_SUMMARY") + " |")
    out.append("| LD-DOC-22-003 | تلخيص توليدي بنموذج حقيقي | BLOCKED (EXTERNAL_PROVIDER, D8) | لا مزود معتمد |")
    out.append("| LD-DOC-34-014 | Chromium وFirefox | YES | " + evidence_text("UX_ENGINES") + " |")
    out.append("| LD-DOC-34-014 | WebKit (بناء Playwright WPE، ليس Safari) | YES | " + evidence_text("UX_ENGINES") + " |")
    out.append("| LD-DOC-34-014 | Safari / iOS الفعليان | NOT RUN | لم يُشغَّلا؛ أمر الصورة الرسمية لمشغّل يملك docker في `frontend/e2e-wp/README.md` ولم يُنفَّذ |")
    out.append("\n## ملحق: حزم الأدلة\n")
    out.append("كل رمز بين قوسين في عمود VERIFIED_BY_DEVELOPER يشير إلى حزمة هنا بخطواتها الكاملة.\n")
    for key in sorted(used):
        b = RD.EVIDENCE[key]
        out.append(f"- **{key}**")
        for t in b.get("t", []):
            out.append(f"  - اختبار خلفي: `{t}` (CI أخضر {RD.TEST_SHA})")
        for f in b.get("f", []):
            out.append(f"  - اختبار واجهة: `{f}` (CI أخضر {RD.TEST_SHA})")
        for alias, steps in b.get("j", []):
            out.append(f"  - رحلة `{RD.J[alias]}` (إصدار UAT {_journey(alias)[1]}): " + "؛ ".join(f"«{x}»" for x in steps))
        for x in b.get("x", []):
            out.append(f"  - ملف دليل: `{x}`")
    out.extend(body)
    return "\n".join(out) + "\n", missing, stats


def summary_text(stats):
    t = stats["impl"]
    n = stats["rows"]
    L = ["# TESTED-STATUS-SUMMARY (generated, do not edit)", "",
         "Source of every number: `lawdesk-comprehensive-requirements-matrix.md`, generated by `build_matrix.py`. "
         "Nothing here is QA_ACCEPTED, and the project is not complete.", "",
         f"Atomic requirement rows: **{n}**", "",
         "| Column | Value | Count |", "|---|---|---|"]
    for k in IMPL_VALUES:
        L.append(f"| IMPLEMENTED | {k} | {t[k]} |")
    L.append(f"| VERIFIED_BY_DEVELOPER | with named test/journey evidence | {stats['verified']} |")
    L.append(f"| VERIFIED_BY_DEVELOPER | empty | {stats['unverified']} |")
    L.append(f"| QA_ACCEPTED | accepted by Codex | 0 |")
    L.append(f"| QA_ACCEPTED | empty | {n} |")
    L += ["", "BLOCKED rows by real cause: " + ", ".join(f"{k} {v}" for k, v in sorted(stats["blocked_causes"].items())) + ".",
          "PARTIAL rows by cause of what remains: " + ", ".join(f"{k} {v}" for k, v in sorted(stats["partial_causes"].items())) + ".",
          "NO rows: " + (", ".join(stats["no_rows"]) or "none") + ".", "",
          "Former MISSING rows: LD-DOC-00-001 and LD-DOC-00-002 (connected system). Closed in PR #86 (ConnectedCaseApiTest, connected_journey.py); no new code needed.", "",
          "VERIFIED_BY_DEVELOPER is feature-level evidence (a named test class or journey step on a stated SHA), not a claim that every field was asserted. "
          "Journey evidence files were recorded on older UAT releases than the current develop head; rerun is part of the retest package.", ""]
    return "\n".join(L)


def main():
    lines = SOURCE.read_text(encoding="utf8").split("\n")
    text, missing, stats = render(lines)
    summ = summary_text(stats)
    if "--check" in sys.argv:
        bad = []
        if missing:
            bad.append(f"uncovered source lines {missing}")
        if stats["rows"] != EXPECTED_ROWS:
            bad.append(f"row count {stats['rows']} != {EXPECTED_ROWS}")
        if not OUT.exists() or OUT.read_text(encoding="utf8") != text:
            bad.append("matrix is stale")
        else:
            hdr = [l for l in text.split("\n") if l.startswith("| " + COLUMNS[0])]
            if any(h != "| " + " | ".join(COLUMNS) + " |" for h in hdr):
                bad.append("column header drift")
            for l in text.split("\n"):
                c = [x.strip() for x in l.strip().strip("|").split("|")]
                if l.startswith("| LD-DOC-") and len(c) >= 9:
                    if len(c) != len(COLUMNS) or c[4] not in IMPL_VALUES or c[6] != "" or (c[5] and c[4] != "YES"):
                        bad.append(f"row {c[0]}: invalid IMPLEMENTED/VERIFIED/QA_ACCEPTED combination")
        if not SUMMARY.exists() or SUMMARY.read_text(encoding="utf8") != summ:
            bad.append("TESTED-STATUS-SUMMARY.md is stale")
        if bad:
            print("; ".join(bad), file=sys.stderr)
            return 1
        return 0
    OUT.write_text(text, encoding="utf8")
    SUMMARY.write_text(summ, encoding="utf8")
    print("rows written; uncovered lines:", missing, stats["impl"], "verified", stats["verified"])
    return 0


if __name__ == "__main__":
    sys.exit(main())
