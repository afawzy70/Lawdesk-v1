"""Writes docs/requirements/gap-closure.md from the requirements matrix (partial and missing rows) and the closure notes below.
Run: python3 docs/requirements/build_gap_closure.py. The matrix itself is never edited by this script."""
import pathlib
HERE=pathlib.Path(__file__).resolve().parent
M=str(HERE/'lawdesk-comprehensive-requirements-matrix.md')
import json
# The 104 rows of the round-2 gap closure (id, source line); frozen so this historical record does not depend on the matrix status column.
rows=[[i,n,'','','',''] for i,n in json.load(open(str(HERE/'gap_rows.json'),encoding='utf-8'))]
IMPL='IMPLEMENTED'; BLK='BLOCKED'; SAT='SATISFIED_BY_EXISTING'; UMB='UMBRELLA'
spec={
'LD-DOC-00-001':(IMPL,'الوثيقة تطلب منظومة مترابطة لا شاشات منفصلة؛ لم يكن هناك اختبار يثبت الترابط.','`wp9/ConnectedCaseApiTest` (قضية واحدة بطلب ودفع وجلسة وحكم ومستند وميعاد ومخاطر ومهمة وملاحظة تظهر في الملخص والخط الزمني والتقويم والبحث والتقارير واللوحة، وتغيير مهمة يظهر في الباقي، وغير المطلع لا يرى شيئًا) + رحلة `tools/journeys/connected_journey.py` (لم تُشغَّل على UAT).',''),
'LD-DOC-00-002':(IMPL,'كما أعلاه.','الاختبار والرحلة نفسهما؛ ربط الحكم بميعاد طعنه (`judgment.appeal_deadline_id`, V843)، وربط الدفع بالسوابق (V840)، وربط نقص الدليل بطلب الإفادة (V841) تُغلق روابط كانت مفقودة بين الوحدات.',''),
'LD-DOC-10-011':(BLK,'جدول العطلات وقواعد المدد فارغة؛ مهل التذكير عامة لا بحسب نوع الميعاد.','لا تغيير: الوثيقة لا تطلب مهلة تذكير لكل نوع ميعاد (السطر 161: «ضبط المدد من الإعدادات… وإرسال تنبيهات قبل انتهائه»)، والآلية موجودة (WP4).','التبعية: إدخال المختصين لقواعد المدد والعطلات الرسمية بمصادرها (القرار D6: لا اختراع). القرار المطلوب: من يدخل القواعد والعطلات ومن يعتمدها، وهل يلزم مهل تذكير مختلفة لكل نوع ميعاد.'),
'LD-DOC-10-012':(BLK,'الاحتساب الآلي معطل حتى تُدخل قاعدة وتُعتمد.','آلية الاقتراح والاعتماد وتسجيل اسم المعتمد وتاريخه موجودة ومختبرة (WP4)؛ ربط الميعاد بالحكم: V843.','التبعية نفسها (D6): لا قاعدة معتمدة بعد، فلا احتساب آلي حقيقي لاختباره.'),
'LD-DOC-13-001':(BLK,'نص الصور ينتظر OCR.','صيغ PDF وWord وPNG وJPEG وTIFF تُقبل وتُتحقق بتوقيعها وتُخزَّن (WP5).','التبعية الخارجية: لا Tesseract ببيانات العربية على خادم UAT. القرار المطلوب: تثبيت `tesseract-ocr-ara` على المضيف (تغيير في الخادم المشترك خارج صلاحيتي) أو اعتماد محرك بديل.'),
'LD-DOC-14-009':(SAT,'ملاحظة المصفوفة: POSTPONED وREJECTED غير واردة.','الحالات السبع المذكورة في الوثيقة منفذة كلها (جديدة، قيد التنفيذ، بانتظار مستندات، بانتظار جهة أخرى، مكتملة، متأخرة، ملغاة)؛ الحالتان الأخريان لم تطلبهما الوثيقة فلا فجوة.','لا شيء (يمكن لإعادة توليد المصفوفة اعتباره موجودًا في الشيفرة).'),
'LD-DOC-15-013':(IMPL,'الكشف يسمي العضو البديل دون إعادة إسناد بنقرة.','`POST /api/v1/hearings/{id}/reassign` (`HearingService.reassign`) وزر «إسناد إلى البديل» في لوحة التعارض بالتقويم؛ يسجّل حدثًا في الخط الزمني ويتطلب `HEARING_MANAGE` وتعديل القضية. اختبار الواجهة في `Calendar.test.tsx` (one-click reassignment).',''),
'LD-DOC-16-022':(IMPL,'تخزين نسخة الحكم ينتظر WP5.','`library_judgment_file` (V842) و`documents/LibraryCopyService`: رفع النسخة عبر قواعد الأرشيف (توقيع الملف، الصيغ المفعلة، الحد الأقصى)، استبدال مع حفظ التاريخ، تنزيل مدقَّق، إخفاء نسخة الحكم السري عن غير المديرين. اختبار: `library/LibraryCopyApiTest`. وقسم «النسخة المحفوظة» في صفحة الحكم (`LibraryCopySection`, `Linked.test.tsx`).',''),
'LD-DOC-16-043':(IMPL,'مقارنة الدفوع والطلبات تنتظر ربط WP2؛ ولا اقتراح لحظة إعداد المذكرة.','`SimilarJudgmentService` يضيف الطلبات القائمة والدفوع مصدرًا (بقدر ما يملك المستخدم CLAIM_VIEW/DEFENSE_VIEW) و`?defenseId=` لاقتراح أحكام مذكرة دفع بعينه؛ `basis.defensesIncluded/claimsIncluded`. اختبار: `pleadings/GapClosureApiTest.similarJudgments…`.','واجهة اختيار الدفع عند إعداد المذكرة غير منفذة (API فقط).'),
'LD-DOC-17-008':(IMPL,'ربط الدفع بالسوابق (defense_precedent) لم يُنفَّذ بعد.','`defense_precedent` (V840) + `GET/POST/DELETE /api/v1/defenses/{id}/precedents` وحقل `precedents` في تفصيل الدفع؛ حكم مكتبة غير مسحوب فقط، ومبدأ من الحكم نفسه، والإزالة علامة لا حذف. اختبار: `GapClosureApiTest.aDefenceKeepsItsSupportingPrecedents…`؛ وقسم «الأحكام والسوابق المؤيدة» في تفصيل الدفع (`DefensePrecedents`, `Linked.test.tsx`).',''),
'LD-DOC-22-003':(BLK,'تلخيص استخراجي فقط.','لا تغيير.','التبعية: مزود ذكاء اصطناعي معتمد (D8). القرار المطلوب: اعتماد مزود وما يُسمح بإرساله إليه (docs/design/LD-DOC-09).'),
'LD-DOC-23-010':(IMPL,'تحذير عند إغلاق قضية بتسوية غير معتمدة غير منفذ.','`CaseClosingCheck` و`GET /api/v1/cases/{id}/closing-check`؛ إغلاق القضية بتسوية مقترحة/قيد المراجعة/معتمدة غير منفذة (أو ميعاد مفتوح أو جلسة مجدولة أو مهمة مفتوحة أو طلب إفادة معلق أو تنفيذ جارٍ) يُرفض بالقاعدة `CLOSING_OPEN_ITEMS_NOT_ACKNOWLEDGED` حتى يُرسل `acknowledgeOpenItems=true` فيُسجَّل في الخط الزمني. لا حذف لأي شيء. اختبار: `GapClosureApiTest.closingACase…`؛ وفي الواجهة تُعرض البنود ويُطلب التأكيد (`CaseStatusClosing.test.tsx`).',''),
'LD-DOC-24-003':(SAT,'لوحة الأداء موضوعية بلا ترتيب.','المؤشرات موضوعية ولا يوجد مقياس يعتمد نتيجة الحكم وحدها ولا ترتيب للأعضاء؛ المتطلب سلبي (ألا يُعتمد التقييم على نتيجة الحكم وحدها) ومحقَّق بالتصميم.','لا شيء.'),
'LD-DOC-26-010':(BLK,'التوزيع حسب القطاع/الإدارة/القسم يعتمد الجهات المسجلة.','الآلية موجودة (ربط القضايا بوحدات الهيكل).','التبعية: إدخال الهيكل التنظيمي وربط القضايا بجهاتها (بيانات المالك). القرار المطلوب: اعتماد الهيكل وتسجيل الجهات على القضايا القائمة.'),
'LD-DOC-28-010':(BLK,'البحث الموحد في المستندات بالعنوان/الاسم؛ نص المستند في بحث الأرشيف المستقل `/documents/search`.','لا تغيير: دمج نص المستندات في البحث الموحد يضيف استعلامًا غير مفهرس على كل المستندات ويفاقم زمن البحث الموحد المعروف (~19 ث عند 50 ألف قضية).','التبعية: مستند بحث مخزَّن/مفهرس (بند الأداء المفتوح). القرار المطلوب: لا قرار مالك؛ عمل هندسي منفصل.'),
}
def umbrella(i):
    sec,n=i.split('-')[2],int(i.split('-')[3])
    if sec=='34':
        t={1:('PRESENT','لا حذف ولا تعديل مدمر؛ الترحيلات إضافية فقط (اختبارات Migration*).',''),
           2:('PRESENT','ترحيلات إضافية مع اختبار ترتيب وسلامة.',''),
           3:('BLOCKED','النسخ الاحتياطي قبل كل نشر يعمل بمسار النشر.','لا تدريب استعادة فعلي؛ التبعية: تشغيل drill لـ pg_dump/pg_restore على UAT (إجراء تشغيلي خارج صلاحيتي على المضيف المشترك).'),
           4:('PRESENT','فهارس وقيود وترحيلات إضافية.',''),
           5:('PRESENT','قاعدة بيانات واحدة وجداول مترابطة بمفاتيح أجنبية وفحوص تطابق القضية.',''),
           6:('BLOCKED','البحث الموحد ~19 ث عند 50 ألف قضية (الهدف 0.5 ث).','التبعية: مستند بحث مخزَّن؛ لا يخفَّض الهدف.'),
           7:('BLOCKED','كلمات المرور مجزأة؛ لا تشفير على مستوى الحقول للأرقام المدنية.','القرار المطلوب: اعتماد إدارة المفاتيح وتشفير الحقول/القرص (D7).'),
           8:('PRESENT','الملفات خلف صلاحيات وسياسة الاطلاع، `nosniff`، وتحقق التوقيع، وتدقيق التنزيل (ونسخ المكتبة V842).',''),
           9:('BLOCKED','النسخ الاحتياطي موجود (LD-041).','لا تدريب استعادة لبيانات الأرشيف على UAT.'),
           10:('PRESENT','Spring Security وأدوار وصلاحيات ونطاقات.',''),
           11:('IMPLEMENTED','اختبار يمر على كل نقطة نهاية مرتبطة بقضية بمستخدم غير مطلع ويتوقع 404/403: `security/CaseBoundEndpointsAccessTest`؛ وإخفاء تفاصيل التدقيق بنطاق القضية (`AuditController`).',''),
           12:('PRESENT','سجل الطلبات والتدقيق وربط requestId.',''),
           13:('BLOCKED','اختبارات الأمان الآلية في CI (gitleaks وغيرها).','التبعية: اختبار اختراق خارجي/ZAP قبل التشغيل — غير منفذ.'),
           14:('BLOCKED','Chromium فقط.','التبعية: Firefox وWebKit غير مثبتين على المضيف.'),
           15:('PRESENT','RTL والعربية والإنجليزية.',''),
           16:('BLOCKED','لقطات الجوال وaxe لـWP1 فقط.','التبعية: تمرير لقطات لبقية الشاشات (تشغيلي).'),
           17:('BLOCKED','خمس محوّلات معطلة بعقود موثقة.','القرار المطلوب: اعتماد جهات التكامل وبيانات دخولها (D8/§34).')}[n]
        return t
    if sec=='35':
        return ('UMBRELLA',f'السيناريو {n} له رحلة UAT وأدلة في docs/evidence/DOC-WP*.','يبقى «جزئيًا» حتى يربط مالك التكامل الصف بخطوة رحلة محددة ويعيد تشغيلها ويصدر Codex القبول (QA_ACCEPTED)' + ('؛ S14 جزئي لغياب القواعد المعتمدة' if n==14 else '؛ S16 بمزود اختبار فقط' if n==16 else ''))
    if sec=='36':
        return ('UMBRELLA','بند جامع يتبع صفوف الوحدة المعنية في المصفوفة.','تُعاد صياغته من حالة صفوف وحدته؛ ' + ('المساعد التشغيلي متعطل (D8).' if 27<=n<=34 else 'التكامل مع الأنظمة الخارجية معطل (محوّلات بلا اتصال).' if n==35 else ''))
    if sec=='37':
        extra={2:'عدم فقد البيانات: ترحيلات إضافية.',3:'الترابط: `ConnectedCaseApiTest`.',5:'تأجيل الجلسة يحفظ السابقة (S9).',11:'S12.',12:'S10.',13:'إعدادات الهيكل (WP1).',15:'المسودات غير نهائية والمساعد لا يغيّر سجلًا (WP10).'}.get(n,'')
        return ('UMBRELLA','بند جامع ' + extra,'يُغلق بعد مطابقة الصفوف التفصيلية وقبول Codex؛ ' + ('21: المساعد التشغيلي وOCR وأداء 50 ألف قضية.' if n in (20,21) else ''))
    return ('UMBRELLA','','')

EXTRA = """
## بنود من خارج الصفوف (نُفِّذت أو بقيت مفتوحة)

| البند | الحالة | الدليل / السبب |
|---|---|---|
| ربط نقص الدليل بطلب إفادة (`gap_followup.info_request_id`) | IMPLEMENTED | V841 + `GapClosureApiTest.missingEvidenceCanBeFollowed…` |
| تنبيه نقص الدليل مصدرًا للإشعارات | IMPLEMENTED | `DateReminders.evidenceGaps` (نوع DOCUMENT_MISSING) + `GapClosureApiTest.aMissingRequiredEvidenceItem…` |
| تاريخ التنفيذ مصدرًا للتقويم | IMPLEMENTED | `EnforcementCalendarSource` + `GapClosureApiTest.theEnforcementDueDate…` |
| إعادة إسناد الجلسة بنقرة عند التعارض | IMPLEMENTED | `POST /hearings/{id}/reassign` + زر في لوحة التعارض (`Calendar.test.tsx`) |
| شاشة صياغات الدفوع، شاشة حدود الأرشيف، قائمة الأحكام `/judgments`، تقرير الاستجابة | IMPLEMENTED | `features/gaps` + `Gaps.test.tsx`؛ `GET /api/v1/judgments` |
| ميعاد الطعن على الحكم | IMPLEMENTED | V843 + `PUT /judgments/{id}/appeal-deadline` |
| ربط الدفع بالسوابق وعرضه في بطاقة الدفع | IMPLEMENTED | V840 + `DefensePrecedents.tsx` |
| نسخة الحكم في المكتبة عبر الأرشيف وقسمها في صفحة الحكم | IMPLEMENTED | V842 + `LibraryCopySection.tsx` |
| تحذير الإغلاق بتسوية غير معتمدة/بنود مفتوحة وتأكيده في الواجهة | IMPLEMENTED | `CaseClosingCheck` + `CaseStatusClosing.test.tsx` |
| اقتراح أحكام من الدفع/الطلبات (مصدرًا ولحظة المذكرة) | IMPLEMENTED (API) | `?defenseId=`؛ **واجهة اختيار الدفع غير منفذة** |
| الاستحقاق والتأخر للمهام بتوقيت الكويت | IMPLEMENTED | توحيد المنطقة على `Asia/Kuwait` + `TimeZoneConsistencyTest` (افتراض جديد: LD-DOC-11 البند 15) |
| إخفاء حقول التدقيق بنطاق القضية | IMPLEMENTED | `AuditController` (`masked`) |
| اختبار يفشل إن تجاوزت نقطة نهاية مرتبطة بقضية سياسة الاطلاع | IMPLEMENTED | `security/CaseBoundEndpointsAccessTest` (كشف تسريبًا فعليًا: تعديل ملاحظة كان يردّ 409 قبل فحص الاطلاع، أُصلح) |
| مهلة التذكير لكل نوع ميعاد | NOT_REQUIRED | الوثيقة (السطر 161) لا تطلبها |
| `CASE_DELETE` مزروعة وغير مستعملة | BLOCKED | الوثيقة تمنع الحذف النهائي إلا بإجراء معتمد (السطر 186) ولا سياسة احتفاظ (D9). القرار المطلوب: هل يُسمح بحذف قضية مسجلة بالخطأ، بأي إجراء وصلاحية وتدقيق. إلى ذلك لا توجد نقطة حذف للقضايا |
| عناصر القسم 34 (تكامل) | BLOCKED جزئيًا | انظر صفوف 34 أعلاه |

## حدود اختبار الاطلاع الانعكاسي

`security/CaseBoundEndpointsAccessTest` يستدعي كل نقطة نهاية فيها متغير مسار مرتبطة بقضية بمستخدم يملك كل الصلاحيات دون عضوية أو نطاق، ويتوقع 404/403، وبمستخدم ضابط يجد السجل. **عشر نقاط نهاية لا يمكن ملاحظتها بهذا الاختبار** لأن التحقق من الجسم يردّ 400 قبل فحص الاطلاع (`BODY_NOT_BUILT`): `AccessController#confidentiality`, `AccessController#grant`, `CaseController#changeStatus`, `CaseController#update`, `CasePrecedentController#link`, `DocumentLinkController#add`, `InfoRequestController#replyWithDocument`, `PartyController#addCounsel`, `PartyController#link`, `ProceedingController#create`. تغطيها اختبارات الاطلاع الأقدم (مثل `CaseVisibilityApiTest`) ولم يثبتها هذا الاختبار.

الحالات هنا من المطوّر فقط: لا `VERIFIED` على UAT ولا `QA_ACCEPTED`.
"""
out=[]
out.append('# إغلاق الصفوف «موجود جزئيًا» و«غير موجود» — سجل الفجوات (الجولة الثانية)\n')
out.append('يسرد لكل صف من المصفوفة (104 صفًا: 102 جزئي + 2 غير موجود) ما كان ناقصًا، وما نُفِّذ (الاختبار/الفرع)، أو العائق الدقيق والقرار المطلوب. **لا يُعدَّل** ملف المصفوفة؛ تُعاد توليدته من هذا الملف.\n')
out.append('الحالات هنا: `IMPLEMENTED` (نُفِّذ في الشيفرة ومعه اختبار)، `SATISFIED_BY_EXISTING` (لا فجوة فعلية)، `BLOCKED` (يحتاج قرارًا أو تبعية خارجية)، `UMBRELLA` (بند جامع يتبع صفوفًا أخرى). **لا يُعلن أي صف `VERIFIED` ولا `QA_ACCEPTED`**: التحقق على UAT برحلة وSHA منشور من شأن مالك التكامل، والقبول من Codex وحده.\n')
out.append('> **ملاحظة R3**: هذا سجل تاريخي للجولة الثانية. الحالة الحالية لكل صف (IMPLEMENTED / VERIFIED_BY_DEVELOPER / QA_ACCEPTED) في المصفوفة نفسها؛ تغيرت أمور منذ كُتب (OCR، البحث، فحوص المتصفحات، تدريب الاستعادة).\n')
out.append('## الجدول\n')
out.append('| الصف | السطر | الحالة | ما كان ناقصًا | ما نُفِّذ / الدليل | العائق والقرار المطلوب |')
out.append('|---|---|---|---|---|---|')
for c in rows:
    i=c[0]
    if i in spec:
        st,gap,done,block=spec[i]
    else:
        st,done,block=umbrella(i); gap='بند جامع (قسم %s)'%i.split('-')[2]
    out.append('| %s | %s | %s | %s | %s | %s |'%(i,c[1],st,gap.replace('|','/'),done.replace('|','/'),block.replace('|','/') or '—'))
out.append(EXTRA)
open(str(HERE/'gap-closure.md'),'w',encoding='utf-8').write('\n'.join(out)+'\n')
print(len(rows))
