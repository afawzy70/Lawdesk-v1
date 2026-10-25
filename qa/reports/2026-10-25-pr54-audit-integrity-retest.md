# إعادة اختبار PR54 ومراجعة نزاهة الـ audit — 25 أكتوبر 2026

**القرار العام: REQUEST_CHANGES.** QAD-037 و 038 و 039 و 040 اتثبت إصلاحهم مستقلًا على
`2d07a222189f82ddeacf74fdcd28165980cea85d`. مراجعة الحدود ومسارات التدقيق الإضافية كشفت **3 عيوب جديدة**:
QAD-041 — S3 ، و QAD-042/QAD-043 — S2. مفيش إعلان QA ACCEPTED شامل من الجولة المركزة دي.

النتيجة المستقلة بعد تسوية التكرار وخطأ oracle موضّح تحت: **299 PASS / 19 FAIL / 0 BLOCKED / 0 NOT_RUN**.
التغطية الأقدم اللي لسه متعذرة أو غير منفذة محفوظة منفصلة: **2 BLOCKED / 4 NOT_RUN**، ومش محسوبة نجاح.
الـ 19 FAIL تكرارات مستقلة لثلاثة عيوب، مش 19 عيب. اختبارات Claude و CI ليها **صفر credit** في الأعداد.

## المصدر والبيئة

GitHub develop والـ backend الفعلي اتأكدوا أول وآخر التنفيذ بالحسابات الأربعة من `/api/v1/release`:
**`2d07a222189f82ddeacf74fdcd28165980cea85d`**. PR54 HEAD كان
`b245f124cb53dc30e2fd50f38c9ac3f5087ab933`؛ اتدمج عند النسخة المختبرة، والـ Git tree متطابق.

- UAT: Chromium/Playwright ، HTTPS ، حسابات QA المعتمدة من الدليل المحمي في الذاكرة فقط؛ بيانات synthetic و owned prefixes.
- محلي: canonical archive مستقل، Java21.0.12.1+1 و PostgreSQL18.6 مؤقت، HTTP فعلي للـ Spring Boot jar.
  Fault injection ، SQL races وحدود آلاف الصفوف كلها محليًا؛ **مفيش حقن أخطاء أو bulk data على UAT**.
- تحليل الملفات: openpyxl3.1.5 و PyMuPDF1.26.7 ؛ فحص بصري مستقل للصور المرسومة من PDFs المولدة من UAT.
- لا merge/deploy من QA ، ولا تعديل Matbakhy أو shared proxy أو workspace كلود أو Modern Chambers Phase7.

[هوية المصدر](../evidence/pr54-retest-20261025/source-identity.json)،
[release البداية](../evidence/pr54-retest-20261025/release-start/results.json)،
[release النهاية](../evidence/pr54-retest-20261025/release-end/results.json).

## الأعداد والتغطية

| النطاق | PASS | FAIL | BLOCKED | NOT_RUN |
|---|---:|---:|---:|---:|
| UAT release بداية ونهاية؛ أربع أدوار و anonymous401 | 10 | 0 | 0 | 0 |
| UAT browser/API للمراحل 2–4 | 35 | 0 | 0 | 0 |
| UAT أربع أدوار، password reset ، revocation ، concurrent sessions | 41 | 0 | 0 | 0 |
| UAT Phase5/6 ، strict pagination ، uploads ، task audit ، تقارير وتنزيلات؛ بدون تكرار نفس harness | 47 | 4 | 0 | 0 |
| UAT dashboard مقابل source records ، عربي/إنجليزي و mobile/axe | 27 | 0 | 0 | 0 |
| تحليل ملفات CSV/XLSX/PDF الفعلية | 10 | 0 | 0 | 0 |
| مراجعة بصرية فعلية لـ PDF عربي/إنجليزي ونص مختلط | 8 | 0 | 0 | 0 |
| محلي: strict pagination و QAD-040 ، HTTP/SQL | 76 | 4 | 0 | 0 |
| محلي: QAD-038 و locks/COMMIT/deadlock | 14 | 0 | 0 | 0 |
| محلي: QAD-039 ، 9999/10000/10001 في CSV/XLSX/PDF | 20 | 0 | 0 | 0 |
| محلي: معاملات المستخدمين والأدوار وكلمات المرور وتدقيق الأحداث | 11 | 11 | 0 | 0 |
| **الإجمالي** | **299** | **19** | **0** | **0** |

UAT وملفاته: **178 PASS / 4 FAIL**؛ محلي: **121 PASS / 15 FAIL**.
دي checks مسجلة، ممكن check واحد يحتوي طلبات متعددة؛ مش عدد القصص المقبولة.
كل test record فيه preconditions و data و steps و expected و priority و story IDs.

الخام **311 PASS / 20 FAIL**. تسوية نفس سكريبت Phase5/6 استبعدت تكرار logins/fixtures/release ، واستخدمت
إعادة اختبار oracle مصحح لتقرير خبير QA: الخبير كان له referrals جديدة في قضايا guard إضافية، لكن المقارنة القديمة
كانت بتقرأ القضية الأصلية فقط. المقارنة المصححة بتجمع كل referrals لنفس الخبير في الـ QA namespace بالكامل ونجحت.
الملف الخام الفاشل محفوظ؛ ده **خطأ oracle ، مش عيب منتج**. مفيش إخفاء لفشل QAD-041/042/043.

[كل التشغيلات](../evidence/pr54-retest-20261025/raw-executions.json)،
[حساب النتيجة](../evidence/pr54-retest-20261025/execution-summary.json)،
[UAT reconciled](../evidence/pr54-retest-20261025/phase56-uat-reconciled.json)،
[سبب تصحيح oracle](../evidence/pr54-retest-20261025/harness-triage.json).

## العيوب المطلوب إعادة اختبارها

| العيب | الحالة المستقلة الحالية | الدليل |
|---|---|---|
| QAD-037 | **FIX_VERIFIED_UAT** | PDF صالح أقل وفوق 1MiB و 10MiB بالضبط يرجع 201 ؛ تنزيل bytes/hash/case مطابق؛ 10MiB+1 يرجع 400 ؛ quota race201/422 ؛ read-only/write denial ؛ actual browser upload/download. |
| QAD-038 | **FIX_VERIFIED_INDEPENDENT_POSTGRESQL_AND_API** | إعادة الإنتاج الأصلية، parent/successor في الترتيبين، expert/session races ، انتظار Lock ثم 23514 ، deadlock40P01 و rollback كتابة الضحية؛ 14PASS. UAT parent guards422 والتاريخ ثابت. |
| QAD-039 | **FIX_VERIFIED_INDEPENDENT_LOCAL** | 9999 و 10000 صف ومجموعة أسماء مطابقة في الصيغ الثلاث؛ 10001→422 EXPORT_TOO_LARGE ، بدون 500 أو exception leakage أو success audit. Bulk محلي بنفس SHA ؛ UAT exports الفعلية صغيرة. |
| QAD-040 | **FIX_VERIFIED_INDEPENDENT_LOCAL_AND_UAT** | deferredCOMMIT failure: المهمة/history ثابتين و success audit0→0 ؛ retry→حدث واحد؛ audit insert failure يرجّع business write ؛ سباق طلبين 200/409 وحدث واحد. المسار الطبيعي وواجهة audit بالعربي/الإنجليزي ناجحين على UAT. |

[الرفع والـ guards وتدقيق المهمة على UAT](../evidence/pr54-retest-20261025/focused-uat/results.json)،
[PostgreSQL races](../evidence/pr54-retest-20261025/integrity/results.json)،
[حدود التصدير](../evidence/pr54-retest-20261025/expert-limits/results.json)،
[QAD-040 وسياسة القوائم](../evidence/pr54-retest-20261025/policy/results.json).

QAD-040 مقفول **في نطاق المهمة**. عيوب التدقيق في إدارة المستخدمين والأدوار وتغيير كلمة المرور مسارات مختلفة؛
متسجلة بأرقام جديدة، من غير إعادة فتح أو طلب تكرار إصلاح المهمة.

## Strict pagination و QAD-041 — S3

اختبرت **18 route بتقبل pagination**، بعد مراجعة controllers بشكل مستقل: المستخدمين، الأطراف، audit ، المحاكم،
الخبراء، الجلسات، المستندات العامة، المهام، القضايا، جلسات/ملاحظات/timeline/دعاوى/أحكام/قرارات القضية،
وتقارير القضايا والأحكام والجلسات. القوائم غير المرقمة، زي roles و directory وتقارير experts/workload ،
و exports مش اتوصفت كقوائم paged بالخطأ. [جرد applicability](../evidence/pr54-retest-20261025/pagination-applicability.json).

على كل الـ 18 محليًا وعلى UAT: omitted→page0/size20 ، size100→200 ؛ الفاضي و whitespace-only و negative/zero
والقيم غير الرقمية و size101→400. duplicate parameters وفيها قيمة غير صالحة اترفضت 400. محليًا اتأكدت كمان
من overflow ونطاق size الكبير، ومن 401/403 قبل validation. **توضيح المالك السابق اتحسم؛ مفيش سؤال قبول معلق للقيم الفاضية.**

لكن الطلب التالي ك admin مصرح له رجع 500 ، محليًا وعلى UAT:

```text
GET /api/v1/courts?page=2147483647&size=100
GET /api/v1/hearings?page=2147483647&size=100
GET /api/v1/tasks?page=2147483647&size=100
GET /api/v1/cases/{ownedCaseId}/hearings?page=2147483647&size=100
```

القيمتين منفردتين داخل الحدود المعلنة، لكن page×size يتعدى حد JPA. الخطأ الداخلي المسموح بنشره من البيئة المحلية:
`InvalidDataAccessApiUsageException: Page offset exceeds Integer.MAX_VALUE (2147483647)`.
المتوقع تحت العقد الحالي 200 وصفحة فاضية، أو 400 بتحقق واضح وعقد يوثق حد offset ؛ **مش 500**.
باقي 14 قائمة رجعوا 200 وصفحة فاضية عند نفس الحد. لا حاجة لطلب ثقيل على sharedDB: JPA بيرفض قبل تنفيذ query الصفحة.

**8 FAIL** لنفس QAD-041: أربع محلية وأربع UAT. [التفاصيل](../defects/qad-041.json)،
[طلبات UAT](../evidence/pr54-retest-20261025/paging-pdf-uat/results.json).
كل ملف`paging-N.json` يحفظ statuses لكل قيمة، بما فيها اللي نجحت قبل الحد الفاشل.

## مراجعة transaction audit — QAD-042 و QAD-043

المعيار اللي اتطبق: **SUCCESS يصف عملية اتعمل لها commit فعلًا**؛ وفي المقابل failure/denial/security attempts
لازم تفضل مسجلة لما الطلب يفشل. مجرد تحويل كل audit إلى نفس transaction مش إصلاح صحيح للأحداث الأمنية.

**QAD-042 — S2: نجاح وهمي لإدارة المستخدمين والأدوار بعد rollback.**

داخل PostgreSQL مؤقت، على مستخدم/دور QA مملوك:

1. احفظ snapshot كامل للصف والـ memberships ، وعدّ أحداث SUCCESS للفعل.
2. ركّب `AFTER INSERT/UPDATE ... DEFERRABLE INITIALLY DEFERRED` trigger محدود للصف المختبر، بيرفع exception عند COMMIT.
3. نفّذ العملية الصحيحة بحساب admin عبر API.
4. الرد 500 ، business snapshot ثابت، لكن عدد SUCCESS زاد 1.

اتكرر مع USER_CREATE/UPDATE/ENABLE/DISABLE/ROLES_CHANGE و PASSWORD_RESET ، ومع ROLE_CREATE/UPDATE/
PERMISSIONS_CHANGE. تغيير permission سجل ROLE_UPDATE و ROLE_PERMISSIONS_CHANGE رغم rollback.
اختبار إضافي لفشل`role_permission` قبل INSERT ، من غير انتظار COMMIT ، رجّع role/permissions لكن ساب ROLE_UPDATE/SUCCESS.
**10 checks FAIL** لعيب atomicity واحد. السبب: UserAdministration/RoleAdministration بيستخدموا
`AuditLog.record` بـ REQUIRES_NEW جوه transaction العملية. مش نفس مسارالمهمة المصحح.

[QAD-042 وإعادة الإنتاج الدقيقة](../defects/qad-042.json).

**QAD-043 — S2: كلمة المرور تتغير من غير audit والعميل يشوف 500.**

1. سجّل دخول مستخدم QA صناعي في البيئة المؤقتة.
2. امنع INSERT في audit_event فقط لما action=PASSWORD_CHANGE و outcome=SUCCESS باستخدام trigger اختباري.
3. نفّذ POST `/api/v1/me/password` بكلمة حالية صحيحة وجديدة صالحة؛ القيم تفضل في الذاكرة.
4. الرد 500 ؛ صف المستخدم اتغير، كلمة المرور الجديدة بتسجّل دخول من client جديد، وعدد SUCCESS لم يزد.

السبب: MeController بينادي UserAccounts.changePassword ، والـ JDBC update بيتعمل له commit قبل كتابة audit المستقلة.
المطلوب عملية credential change مع audit ذري/مضمون durable ؛ مش تغيير غيرمسجل بيرجعفشل.
**1 check FAIL**. [QAD-043](../defects/qad-043.json).

الـ positive controls مستقلة وناجحة:

- العمليات العادية للمستخدم والدور وتغيير كلمة المرور لهاحدث SUCCESS واحد لكل فعل مطلوب.
- فشل DBCOMMIT لتغيير كلمة المرور نفسه لايترك SUCCESS ولايغيّر الحالة.
- كلمة حالية غلط→400 مع PASSWORD_CHANGE/FAILURE محفوظ؛ محاولة login مجهولة→401 مع LOGIN/FAILURE محفوظ؛
  رفضصلاحية→403 مع ACCESS_DENIED/DENIED محفوظ. دي أحداث أمنية صحيحة، مش audits وهمية لازم تتشال.
- reset/revocation/concurrent sessions على UAT نجحوا في 41check مستقلة.

[دليل الـ audit:11PASS/11FAIL](../evidence/pr54-retest-20261025/audit/results.json).
مفيش password/hash/cookie/CSRF في النتائج؛ snapshots الداخلية متسجل منها boolean المساواة فقط.
حقن الأخطاء كله **محلي**؛ النتائج لا تدعي حصول الأعطال دي فعلًا على سيرفر UAT أو استغلالها anonymously.

## QAD-036 — مراجعة بصرية للـ PDF الفعلي

أنشأت 3 عناوين synthetic ، واتأكدت من النص المخزن، وبعدها صدّرت من UAT بالعربي والإنجليزي:

- `قضية Alpha رقم 123 لسنة 2026`
- `ملف AB-123 ضد شركة Beta (2025)`
- `Alpha 123 - قضية تجريبية 456`

راجعت بصريًا الصفحتين في كل لغة بعد render بدقة 2x: الحروف العربية متصلة، والكلمات اللاتينية والأرقام
و AB-123 والأقواس وترتيب استمرار النص عند التفاف السطر سليم. عمود reference على يمين الجدول العربي وعلى
شمال الإنجليزي، والترويسة بتتكرر في الصفحة التانية. `=1+1` محفوظ زي الأصل. مفيش صفوف ناقصة أو مقصوصة
في العينات؛ المراجع الطويلة بتلتف جوه الخلايا. ده **فحص بصري مستقل**، مش نجاح مستنتج من استخراج النص أو اختبارات المطور.

[العينة العربية](../evidence/pr54-retest-20261025/uat-artifacts/mixed-ar-1.png)،
[الإنجليزية](../evidence/pr54-retest-20261025/uat-artifacts/mixed-en-1.png)،
[8 نتائج بصرية ومواصفاتها](../evidence/pr54-retest-20261025/pdf-visual/results.json)،
[تحليل CSV/XLSX/PDF](../evidence/pr54-retest-20261025/uat-artifacts/results.json).
**QAD-036 يفضل FIX_VERIFIED** للنطاق والعينات المختبرة؛ مش ضمان لكل نص Unicode ممكن.

## Regression وحالة القبول

المراحل 2–4: browser workflows حقيقية للمستخدمين والأدوار والحسابات وكلمات المرور والقضايا والأطراف والبحث
والحالات، والمحاكم والدعاوى والأحكام والاستئناف والنقض والتاريخ. 35 PASS ، بالإضافة لأربع أدوار والجلسات: 41 PASS.
Phase 5: رفع وتنزيل PDF وحدود وصلاحيات وسباق quota ، مباشرة OPEN→DONE وتدقيقها، و guards للجلسات والخبراء
ومنع تعديل filing date المخالف. Phase 6: dashboard من السجلات: 27 PASS ؛ مطابقة تقارير الجلسات وعبء العمل
والخبراء؛ تنزيل فعلي للصيغ الثلاث، وحدود تصدير الخبراء محليًا. الاختبارات المكتملة على SHA السابق محفوظة
بـ SHA الأصلي، مش مضافة كـ PASS جديد.
[مراجعة أثر التغيير والأدلة السابقة](../evidence/pr54-retest-20261025/carried-forward-evidence.json).

| النطاق | حكم الجولة الحالية |
|---|---|
| Phase 2 | REQUEST_CHANGES — QAD-042 و QAD-043 في تدقيق الإدارة وتغيير كلمات المرور. |
| Phase 3 | الـ regression المتاح ناجح؛ تعليق empty pagination القديم اتحل. مفيش عيب قضايا مؤكد جديد. |
| Phase 4 | REQUEST_CHANGES — QAD-041 في قائمة المحاكم؛ النزاهة القضائية المختبرة ناجحة. |
| Phase 5 | REQUEST_CHANGES — QAD-041 في قوائم الجلسات والمهام؛ QAD-037/038/040 اتثبت إصلاحهم. |
| Phase 6 | الـ regression والصيغ والحسابات المختبرة ناجحة؛ تعليق empty pagination القديم اتحل. |

الجدول ما يعلنش QA ACCEPTED جديدًا للمراحل 3/6 من retest مركز، ولا يمنح قبولًا كاملًا للمنتج وفيه عيوب audit مؤكدة.
مش بنضيف طلب owner جديد للـ pagination ؛ السياسة الصارمة معروفة واتاختبرت.

## الحالات القديمة والتغطية المتبقية

[السجل الموحد QAD-001…043](../evidence/pr54-retest-20261025/all-qad-reconciliation.json) بيحفظ كل SHAs والإثباتات:
النطاق الأصلي لـ QAD-029 اتثبت إصلاحه بالسياسة الصارمة؛ QAD-036/037/038/039/040 متحدثين بالدليل الجديد؛
QAD-041/042/043 مفتوحين. الـ baseline findings القديمة و business assumptions مااتقفلتش ضمنيًا،
ومفيش BLOCKED قديم اتحول PASS من غير تنفيذ.

[التغطية المتبقية](../evidence/pr54-retest-20261025/remaining-scope.json):

- **2 BLOCKED:** تدقيق SQL مباشر لكل بيانات UAT التاريخية؛ وتجربة مستقلة لاستعادة الصورة وقاعدة البيانات في containers معزولة.
- **4 NOT_RUN:** full automated Java/TS SAST ؛ هوية وفحص advisories للـ embedded jarmode-tools المتبقي؛
  Excel/LibreOffice GUI ؛ وفحص cross-browser شامل مع assistive technology يدوي.

دي فحوص أعمق أو تشغيلية محفوظة، مش موانع تلقائية لنشر Dev/UAT العادي. مفيش stress/load/soak على Contabo ؛
التحقق الموسع في Phase 8. دليل CI/deploy job 121181008508 بيبين بناء ونشر الصورة بالـ SHA الحالي؛
ده دليل مطور فقط. QA نفسه أكد release وولّد PDF من مسار UAT الفعلي.
[دليل النشر](../evidence/pr54-retest-20261025/deployment-provenance.json).
مفيش ادعاء Docker inspect/rollback مستقل. dependencies مااتغيرتش؛ نتائج SCA والأداء القديمة مش مسجلة كقياس جديد.

## تسليم العيوب لـ Claude وإعادة التنفيذ

العيوب الثلاثة اتبلغت فور التأكد على PR54 ؛ الأدلة الموحدة هنا في PR6. المطلوب من Claude:

1. QAD-041: معالجة حد offset الناتج من page×size بعقد واضح، مع إعادة اختبار كل القوائم وعدم إرجاع 500.
2. QAD-042: جعل SUCCESS للتغييرات الإدارية atomic مع business commit ، مع الحفاظ على FAILURE/DENIED الأمنية المستقلة.
3. QAD-043: ضمان atomicity/durability لكلمة المرور والـ success audit ، وإعادة اختبار reset/session revocation.

QA يعيد الفشل نفسه والـ regression المتأثر على HEAD الجديد، وبعد نشره يثبت release.
**مفيش طلب تكرار إصلاحات QAD-037…040.**

السكريبتات: `final_owner_policy.py --strict-paging`، `audit_transaction_review.py`، `final_integrity.py`،
`final_expert_exports.py`؛ كلهم بياخدوا `--candidate <canonical-archive> --sha <full-sha> --output <fresh-dir>`،
مع Python dependencies الخاصة بالـ QA. Browser: `phase5-signedin.mjs <sha> <fresh-dir>` مع `LAWDESK_QA_ONLY`
لاختيار IDs و`LAWDESK_QA_RETEST_FROM` ل manifest بيانات QA المملوكة فقط. النتائج والطلبات والصور والملفات
محفوظة ومرتبطة بالـ SHA ؛ app logs و credential files مش منشورين. Secret scans و checksums محفوظين مع الدليل.
