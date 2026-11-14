# LawDesk — تقرير UAT مستقل موحّد للمراحل2–7

**آخر نسخة اتأكدت واتعمل لها retest: `aed1f7d68597cf1da5da9d8aa658d1b6036960bd`، بعد PR #58.**

بدأت المهمة على النسخة المطلوبة `714d2f3b0c3ed7239b85c79966426d5fe27d2d1d`. أثناء تجهيز النشر، فحص release النهائي
الساعة16:50:17 UTC كشف انتقال UAT إلىaed1. احتفظت بفشل مقارنة الهوية الأربع، وراجعت فرق المصدر وأعدت اختبارات
التغيير فورًا؛ مفيش تحويل نتائج714d إلى نجاحات جديدة. التقرير ده فيه جولتين واضحتين، وكل واحدة لها SHA وأرقام مستقلة.

| النسخة والنطاق | PASS | FAIL | BLOCKED | NOT_RUN |
|---|---:|---:|---:|---:|
|714d — UAT المراحل2–7 والمصفوفة الكاملة + SQL/browser مستقل|583|18|0|0|
|aed1 — retest مستقل لتعديلPR58 والـregression المتأثرة|78|2|0|0|
|تغطية متبقية محفوظة، خارج النجاحات|0|0|2|6|

**QAD-044 اتحل جزئيًا؛ المانع الحالي فلتر المحاكم في تقارير القضايا والأحكام.** التصنيف **S3/P1**.
علىaed1، كل الحالات القضائية الثمانية اللي كانت FAIL نجحت، لكن فلتر تقرير الأحكام لسه ما بيوصلش لمحكمة بعد أول100
على UAT، بالعربي والإنجليزي. helper التقارير المشترك لم يتغير. **Phase6 وPhase7: REQUEST_CHANGES.**

| المرحلة علىaed1 | الحكم الوظيفي ونطاقه |
|---|---|
|Phase2|**QA ACCEPTED للنطاق الوظيفي**: منطقAuth/admin لم يتغير؛ regression714d محفوظ، وforms الإدارة والهوية اتفحصوا علىaed1. ملاحظةإتاحةaudit موضحة أدناه، وليست شهادةWCAG.|
|Phase3|**QA ACCEPTED للنطاق الوظيفي**: منطقالقضايا لم يتغير؛ forms القضية والأطراف والحالة وdossier اتعادوا علىaed1.|
|Phase4|**QA ACCEPTED للنطاق الوظيفي المتحقق** بعد إصلاح المانع السابق: المحكمة/الدعوى بعد100 وjudicial chain نجحوا. الـvariant المنفصل لأكبرمن100حكم/قرار ما زالNOT_RUN، ومش محسوب نجاح.|
|Phase5|**QA ACCEPTED للنطاق الوظيفي المتحقق**:101جلسة/مهمة/مذكرة/إحالة ظهروا؛ الخبيررقم101 والبحث عنه نجحوا، والدعوىرقم101 متاحة للجلسة والإحالة. الأدلة السابقة للرفع والتاريخ والصلاحيات محفوظة بمنطقbackendغيرمتغير.|
|Phase6|**REQUEST_CHANGES — QAD-044**: فلتر محكمة التقارير ما زال أول100.|
|Phase7|**REQUEST_CHANGES — QAD-044**: رحلة التقارير المتكاملة غير مكتملة؛4تنبيهاتaxe منالجولةالسابقة وتغطيةcross-browser لا تزال ظاهرة.|

دي أحكام للنطاق الوظيفي والعقود المتحققة، مع كل قيودالتغطية والافتراضات القديمة المعروضة؛ **القبول الكامل للمراحل2–7 غير صادر**.

**تفصيل retest علىaed1:**14مطابقةfrontend،5release،16formsUAT،14فحصdossier/mobile/keyboard/search ناجح مقابل2فشل report court،
6API/سباقات محلية،8اختيارات قضائية،4اختيارات جديدة معsearch/selection preservation/axe، و11fixture/browser للقوائم101.
إجمالي **78PASS/2FAIL**. النتيجة الخام للجولة الجديدة79PASS/4FAIL: سكريبتQA افترض إن إحالاتالخبير paginated،
لكنController بيرجعهم كاملين فيenvelopeواحد. كل101كانوا ظاهرين حتى فيالتشغيلالأول؛ بعدتصحيحالتوقع اتنفذت الحالتين
ونجحوا. الأصل محفوظ، وfixtureمتكرر واحد مستبعد. مفيش عيبمنتج اتخفى.

[نتائجaed1](../evidence/phase7-uat-714d-20261025/pr58-retest/execution-summary.json)،
[هويةنسخةaed1](../evidence/phase7-uat-714d-20261025/pr58-retest/source-identity.json)،
[تسويةharness](../evidence/phase7-uat-714d-20261025/pr58-retest/harness-triage.json)،
[أحكامالمراحل](../evidence/phase7-uat-714d-20261025/pr58-retest/phase-verdicts.json)،
[تسليم الجزءالمتبقي لـClaude علىPR58](https://github.com/afawzy70/Lawdesk-v1/pull/58#issuecomment-6400444288).

المطلوب منClaude دلوقتي محدود: استكمال paging/search لفلترالمحكمة في `/reports` و`/reports/judgments`، مع حفظالاختيار
واللغة ومعالجةالفشل، وبعدها retestعلىSHAالمنشور الجديد. **مفيش طلب لإعادة إصلاحاتالاختيارات القضائية اللي نجحت.**
مفيش owner action أو provisioning جديد مطلوب للجولة الحالية؛ مفيش merge/deploy أو تعديللبنيةMatbakhy اتعمل بواسطةQA.

## تفاصيل الجولة الأساسية على714d — محفوظة بتاريخها ونتائجها

**النسخة المختبرة: `714d2f3b0c3ed7239b85c79966426d5fe27d2d1d` — 2026-10-25.**

في الجولة الأساسية، الـbackend أكد714d بالحسابات الأربعة في البداية وفي فحص release-end؛ anonymous رجع401. عند النشر النهائي اتغير UAT إلىaed1، وده موثق منفصلًا في تحديث PR58 أعلاه. الـfrontend المنشور اتطابق
byte-for-byte مع **14 ملف** من بناء مستقل لنفس المصدر، بما فيهم HTML وJS وCSS والخطوط.
GitHub develop وقت مراجعة الجولة الأساسية كان714d؛ بعد كده PR58 اتدمج إلىaed1. نشر CI اكتمل 16:08:54 UTC؛ ده **دليل نشر فقط** ومفيش نتيجة مطور أو CI محسوبة نجاح QA.
[هوية المصدر](../evidence/phase7-uat-714d-20261025/source-identity.json)،
[مطابقة الواجهة](../evidence/phase7-uat-714d-20261025/frontend-identity/results.json)،
[دليل النشر](../evidence/phase7-uat-714d-20261025/deployment-provenance.json).

**النتيجة: 583 PASS / 18 FAIL في الاختبارات المنفذة.** التغطية المتبقية القديمة منفصلة: **2 BLOCKED / 6 NOT_RUN**.
الـ18 FAIL عبارة عن **14 إعادة إنتاج لـQAD-044** و**4 تنبيهات axe لإتاحة جدول audit**.
الأربعة الأخيرة مش دليل مؤكد إن لوحة المفاتيح معطلة: Tab والأسهم اشتغلوا فعلًا في Chromium؛ التنبيهات محفوظة من غير تحويلها PASS.

| المرحلة | الحكم عند SHA المحدد | السبب |
|---|---|---|
| Phase 2 — LD-007…011 | **QA ACCEPTED للنطاق الوظيفي** | دخول وخروج، forced password change/reset، إبطال الجلسات، المستخدمين والأدوار والصلاحيات وaudit نجحوا. أدلة atomic audit السابقة محفوظة لمنطق backend اللي ما اتغيرش؛ إتاحة العرض عبر المتصفحات تحت مراجعة Phase7. |
| Phase 3 — LD-012…017 | **QA ACCEPTED للنطاق الوظيفي** | إنشاء وتعديل وأطراف وحالات وبحث وتاريخ، مع forms حقيقية. الوصول للقضايا بعد أول100 نجح بالعربي والإنجليزي. |
| Phase 4 — LD-018…023 | **REQUEST_CHANGES** | QAD-044: المحكمة والدعوى بعد أول100 مش متاحين في الاختيارات/السلم القضائي. |
| Phase 5 — LD-024…029 | **REQUEST_CHANGES** | QAD-044: الدعوى بعد أول100 مش متاحة لتحديد جلسة أو إحالة لخبير. باقي الاختبارات المنفذة نجحت. |
| Phase 6 — LD-030…034 | **REQUEST_CHANGES** | اتأكد امتداد QAD-044 لفلاتر المحاكم في تقارير القضايا والأحكام؛ قبول المرحلة السابق اتراجع عند النسخة الحالية. الحسابات والتنزيلات المنفذة نجحت. |
| Phase 7 — LD-035…038 | **REQUEST_CHANGES** | الرحلات المتكاملة لسه متأثرة بـQAD-044، وفيه4 تنبيهات إتاحة محتاجة تسوية عبر المتصفحات. باقي فحوص اللغتين والمقاسات والشاشات المنفذة محفوظة. |

الأحكام الوظيفية مبنية على العقود المنشورة وقرارات المالك؛ مش اعتماد ضمني للافتراضات القانونية المفتوحة، ولا شهادة Production Ready أو قبول كامل لمعايير WCAG. مفيش merge أو deploy اتعمل بواسطة QA.

## التنفيذ المستقل

| النطاق | PASS | FAIL | BLOCKED | NOT_RUN |
|---|---:|---:|---:|---:|
| Backend release قبل/بعد + مطابقة ملفاتfrontend | 24 | 0 | 0 | 0 |
| UAT المراحل2–4، بما فيه إعادة حالة البحث المصححة | 35 | 0 | 0 | 0 |
| أربع أدوار، reset، إبطال الجلسات والتزامن | 41 | 0 | 0 | 0 |
| forms فعلية للمستخدم/الدور/القضية/الأطراف/التدرج القضائي | 16 | 0 | 0 | 0 |
| بحث حرفي `%` و`_` مع filters وreload، عربي/إنجليزي | 6 | 0 | 0 | 0 |
| Phase5/6: عمليات وصلاحيات وحدود وchronology وreports | 138 | 0 | 0 | 0 |
| Dashboard مقابل السجلات الفعلية وواجهة/axe | 27 | 0 | 0 | 0 |
| تحليل CSV/XLSX/PDF للقضايا الصناعية وformula/RTL | 10 | 0 | 0 | 0 |
| Phase7 matrix + إعادة الوصول للقضية بعد100، بدون تكرار حالة البحث | 206 | 6 | 0 | 0 |
| Login layouts وkeyboard audit وواجهات الأربع أدوار | 10 | 0 | 0 | 0 |
| تنزيلات التقارير الخمسة من المتصفح، لغتين×3formats، + أحكام بقضية QA | 36 | 0 | 0 | 0 |
| تحليل مستقل لمحتوى تنزيلات التقارير الخمسة | 20 | 0 | 0 | 0 |
| forms فلاتر التقارير والترتيب وreload/reset/pagination، لغتين | 6 | 0 | 0 | 0 |
| محلي: APIs الصفحات التالية وسباقات آخرADMIN | 6 | 0 | 0 | 0 |
| محلي: اختيارات المحاكم/الدعاوى بعد100 | 0 | 8 | 0 | 0 |
| محلي: fixture وAPI المحاكم لفلاتر التقارير | 2 | 0 | 0 | 0 |
| محلي: فلتر المحكمة بعد100 في تقريري القضايا والأحكام | 0 | 4 | 0 | 0 |
| **إجمالي منفذ بعد تسوية أعطال QA harness** | **583** | **18** | **0** | **0** |
| **تغطية متبقية محفوظة، مش نجاحات** | **0** | **0** | **2** | **6** |

الوحدة هنا check موثق؛ مش عدد requests أو قصص مقبولة. كل check له preconditions وdata وsteps وexpected وpriority وstory traceability.
[السجل الموحد](../evidence/phase7-uat-714d-20261025/execution-summary.json) هو مصدر الأرقام؛ فيه تصحيح metadata
للربط بين tasks=LD-027 وdocuments=LD-026 وإضافة قصص Phase7 للمصفوفة، مع بقاء captures الأصلية.

الخام **619 PASS / 32 FAIL / 0 BLOCKED / 0 NOT_RUN**. الفرق موثق في
[harness-triage.json](../evidence/phase7-uat-714d-20261025/harness-triage.json): زر Search بقى له مثيل في toolbar،
انتظار البحث كان بيقرأ DOM قديم قبل وصول الرد، locator عنوان dossier كان غلط، وانتظار response event لتنزيل attachment
مش مناسب لـPlaywright. الحالات اتصححت واتنفذت فعلًا؛ التشغيلات الأصلية محفوظة، والـPASS المكرر مش محسوب مرتين.
مفيش فشل منتج اتحذف. فحص release-final كشف تغير النشر:1PASS/4FAIL لهوية714d المتوقعة، منفصلين عن عيوب المنتج؛ فحصrelease-end الصحيح714d هو المحسوب في الجدول. قراءة الملفات قبل إضافة الأحكام المؤرخة وتصديرات الأحكام الفارغة المكررة محفوظين خارج الإجمالي المسوّى. الأربع لقطات viewport الإضافية مكررة للتفتيش البصري فقط، مش أربع نجاحات زيادة.

## QAD-044 — S3 / P1، ما زال مفتوح

المصدر المحلي المستقل: نفس SHA، Spring Boot/Java21.0.12.1+1، PostgreSQL18.6 مؤقت، وChromium فعلي.
Fixture102 محكمة نشطة و101 دعوى في قضية صناعية؛ مفيش bulk data أو SQL على UAT.

1. اقرأ `/api/v1/courts?active=true&level=FIRST_INSTANCE&size=100&page=1&sort=code,asc`؛ المحكمة المطلوبة موجودة.
2. افتح القضية → تسجيل دعوى: الاختيار فيه100 محكمة فقط، من غير وصول للمحكمة في الصفحة التالية.
3. اقرأ `/api/v1/cases/{caseId}/proceedings?page=1&size=100`؛ QA-P-101 موجودة.
4. سلم الدعاوى وتحديد الجلسة وإحالة الخبير بيعرضوا أول100 فقط. اتكرر بالعربي والإنجليزي: **8 FAIL**.
5. افتح `/reports` و`/reports/judgments`؛ `select[name=courtId]` بيعرض100 محكمة فقط. محليًا: **4 FAIL**.
6. على UAT كان فيه115 محكمة. API page1 رجع target QA الموجود في [الدليل](../evidence/phase7-uat-714d-20261025/matrix/report-court-en.json)، لكن فلتر الأحكام ما عرضهوش، عربي/إنجليزي: **2 FAIL**.

السبب: `features/proceedings/proceedingsApi.ts` و`features/reports/courts.ts` بيرجعوا page0/items بس.
المطلوب اختيار قابل للبحث أو pagination/تحميل صفحات محدود يحافظ على cap100 وoffset validation.
مفيش دليل فقد بيانات أو فساد PostgreSQL هنا. قائمة القضايا نفسها فيها pagination صحيح: `/cases?page=5` عند20/صفحة
وفرت القضية بعد أول100 وفتحت تفاصيلها بالعربي والإنجليزي.

[العيب الكامل](../defects/qad-044.json)، [8فشل قضائي](../evidence/phase7-uat-714d-20261025/qad044/browser/results.json)،
[4فشل تقارير محلي](../evidence/phase7-uat-714d-20261025/report-courts/browser/results.json)،
[التسليم المباشر لـClaude](https://github.com/afawzy70/Lawdesk-v1/pull/56#issuecomment-6400300827).

## Modern Chambers، الإتاحة، واللغتين

اتنفذت **25 شاشة/حالة × لغتين ×4مقاسات =200** فحص layout/axe:320 و375 و768 و1366.
الشاشات تشمل dashboard، القضايا list/new/details/edit، المحاكم، جلسات/tasks/documents/experts، التقارير الخمسة،
users/new/detail، roles/new/detail، audit، profile، المسار المخطط للأحكام وnot-found. login اتفحص منفصلًا.
ملف القضية populated ببيانات QA، وforms العمليات والقضية والمستخدم والتدرج القضائي اتنفذت فعلًا في suites المصاحبة.
الـ200 فحص نتيجتهم **196 PASS / 4 FAIL**؛ مفيش document horizontal overflow اتكشف.

- Menu بـEnter وEscape مع رجوع focus، skip-link، التنقل، toolbar search وlocale toggle نجحوا.
- empty search state كان من API حقيقي؛ error/recovery اتجربوا بإيقاف طلب المتصفح نفسه مؤقتًا، من غير mock200 أو تعطيل السيرفر.
- Arabic RTL/English LTR والخطوط المحلية والعناوين وforms وقراءة السجل الصناعي اتراجعوا بصريًا. لقطات privacy-mask بتخفي صفوف غير لازمة من الدليل فقط؛ assertions اتعملت على DOM الحقيقي قبل أي masking.
- عينات: [Dashboard عربي375](../evidence/phase7-uat-714d-20261025/viewport/P7-LAYOUT-dashboard-ar-375.png)،
  [ملف قضية عربي375](../evidence/phase7-uat-714d-20261025/viewport/P7-LAYOUT-dossier-ar-375.png)،
  [Dashboard إنجليزي1366](../evidence/phase7-uat-714d-20261025/viewport/P7-LAYOUT-dashboard-en-1366.png).

**تنبيهات audit:** axe رجّع `scrollable-region-focusable` عند320/375 في اللغتين. DOM مفيهوش tabindex أوrole/name صريح
للـscroll container. لكن اختبار Tab والأسهم الفعلي عند375 نجح باللغتين في Chromium: عرض349، محتوى640، focus وscroll
اتحققوا. دي نتيجة آلية FAIL محفوظة، مع positive control فعلي؛ مش عيب مؤكد يمنع keyboard على المتصفح ده.
يلزم explicit labelled keyboardable region حسب التصميم أو تسوية مدعومة بفحص المتصفحات المطلوبة.
Firefox/WebKit/قارئ الشاشة لسه NOT_RUN؛ مفيش ادعاء WCAG كامل.
[4alerts](../evidence/phase7-uat-714d-20261025/matrix/results.json)،
[الدليل المباشر](../evidence/phase7-uat-714d-20261025/access/audit-keyboard-ar.json).

## العمليات والتقارير والـregression

UAT الفعلي غطى scheduling/adjournment/cancellation/results، tasks/assignment/completion/reminders، رفع PDF وحفظه
وتنزيله وbyte integrity وحدود10MiB/أكثر منها وquota والتزامن، experts/referrals/sessions، notes/XSS/activity، والصلاحيات
للأربع أدوار. chronology/ownership/archived guards والتزامن اتراجعوا من API؛ مفيش حقن أعطال أو SQL على UAT.
[138check](../evidence/phase7-uat-714d-20261025/phase56/results.json)،
[41roles/session](../evidence/phase7-uat-714d-20261025/roles234/results.json)،
[forms](../evidence/phase7-uat-714d-20261025/forms234/results.json).

حسابات dashboard اتقارنت بتعداد السجلات من APIs المصدر، والتقارير اتراجعت مع fixtures صناعية وبياناتها الفعلية.
فورم فلاتر القضايا والأحكام والجلسات اتنفذ في اللغتين، مع تحقق predicates والترتيب وreload وreset والصفحة التالية لتقرير القضايا. نطاق تاريخ صريح2026 ضمن وجود حكم QA في التصدير؛ الفلاتر الافتراضية للشهر الحالي رجعت فاضي بشكل صحيح. CSV/XLSX row counts اتطابقت مع API لكل تقرير؛ قراءة XLSX العادية والـstreaming اتفقت، ومفيش formula cells، وRTL صحيح.
PDF اتولد من backend المنشور؛ parser أكد font embedding والـneutral identifiers المتاحة. QAD-035/036 اتغطوا كمان
بـ`=1+1` ومرجع القضية، وPDF عربي فعلي اتفحص بصريًا: الحروف متصلة، الأعمدة RTL، والنص المختلط والأرقام مقروءين.
[عينة PDF صناعية](../evidence/phase7-uat-714d-20261025/export-parsing/uat-ar-page1.png).
ملفات التقارير غير المفلترة مش مرفوعة؛ المنشور counts/hashes ونتائج القراءة فقط.

QAD-037 وحدود الرفع والتزامن، QAD-038 judicial guards، QAD-041 strict pagination لكل18route، وQAD-043 reset/revocation
رجعوا في الاختبارات الحالية. QAD-039 limit9999/10000/10001، وQAD-042/043 commit/audit failure وrollback والlocking،
عندهم أدلة مستقلة سابقة؛ backend/migrations والـdependency lock **ما اتغيروش** من1e749. الأدلة دي محمولة بنطاقها وSHA الأصلي،
ومش نجاحات جديدة أو استنتاج من CI. QAD-007…043 فضلوا في سجل الحل المستقل؛ QAD-001…006 baseline remainders لسه ظاهرة.
[تسوية QAD-001…044](../evidence/phase7-uat-714d-20261025/all-qad-reconciliation.json).

## المتبقي وشروط إكمال القبول

- **BLOCKED×2:** read-only SQL diagnostic لكل السجل التاريخي المنشور غير متاح؛ isolated image/DB restore-rollback drill اتعطل سابقًا بسبب Docker socket. نجاح CI مش بديل للاتنين.
- **NOT_RUN×6:** automated SAST كامل، advisory تصنيف embedded jarmode-tools، Excel/LibreOffice GUI، Firefox/WebKit/manual assistive technology، كل permission permutations، وUI datasets أكبر من100 للأحكام/القرارات القضائية.
- فحص الأمان الحالي شمل CSRF/session/role boundaries وstored-XSS/exports/uploads؛ مفيش production security certification. الـstress/load/soak الشامل في Phase8 حسب استراتيجية المالك، ومفيش اختبار مزعج للسيرفر المشترك اتعمل.

[تفاصيل التغطية المتبقية](../evidence/phase7-uat-714d-20261025/remaining-scope.json).
Claude محتاج يصلح QAD-044 في كل consumers المذكورين، وبعد نشر SHA جديد يتعمل retest مركز للـselectors والـregression المتأثرة.
مفيش احتياج provisioning جديد أو إعادة تنفيذ إصلاحات041–043. مفيش owner action روتيني مطلوب دلوقتي؛ قرارات الافتراضات القانونية
ومتطلبات اختبارات المرحلة8 تفضل في سجلاتها. **القبول الكامل للمراحل2–7 غير صادر عند النسخة الحالية.**
