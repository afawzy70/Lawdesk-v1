# قبول Phase 5 ومراجعة Phase 6 المستقلة — 25 أكتوبر 2026

**الحكم الحالي: REQUEST_CHANGES لقبول Phase 5، وREQUEST_CHANGES لمرشح Phase 6 التراكمي.**
السبب المؤكد: QAD-037 وQAD-038، ومعاهم QAD-039 في حد تصدير الخبراء. ده حكم قبول وظيفي؛ مش طلب تعطيل
التطوير غير المتأثر، ولا تحويل فحوص الإنتاج المؤجلة لشروط نشر Dev/UAT. QA ماعملش merge أو deploy أو تعديل
في workspace كلود أو Matbakhy أو الـproxy المشترك. Phase 7 لسه مؤجلة.

## النسخ اللي اتراجعت واتنفذت

| السياق | SHA كامل | الإثبات |
|---|---|---|
| Phase 5 المنشورة أول الجولة | `9bdbdd9533f17007c70dbfae2c0d61c45932df00` | `/api/v1/release` بالحسابات الأربعة؛ anonymous401 |
| PR #48 عند بدء المراجعة | `5c17d4b10b09c582f72b4b32b2f873c7392fc7c4` | GitHub HEAD + canonical archive؛ اختبارات مستقلة محلية |
| رأس PR #48 النهائي | `312bf2933bf31562ad16ec32158cf9627e32fe7d` | اتحدث واتدمج أثناء الجولة؛ ماوافقناش على الدمج |
| merge المنشور والمتختبر حاليًا | `b849b1cabcbebd4df058e4ab989d1e8c1a562899` | runtime attestation أربع أدوار، بداية/نهاية suites؛ دليل CD |

رأس PR #48 النهائي والـmerge ليهم **نفس Git tree بالضبط**؛ التنفيذ الأخير اتعمل على SHA الـmerge، مش بننسب
له تشغيل على SHA تاني. الرأس القديم كان ناقص V406/V407 وسياسة التسليم وتحديثات CI؛ الرأس النهائي شاملهم.
كل Phase 6 endpoints/screens موجودة: dashboard، القضايا، الأحكام، الجلسات، الخبراء، عبء العمل، والصيغ الثلاث.
وجود التنفيذ مش قبول تلقائي لحدوده أو مخرجاته.

الإثبات: [هوية المصدر](../evidence/phase56-acceptance-20261025/source-identity.json)،
[attestation الحالية](../evidence/phase56-acceptance-20261025/release-refresh/results.json)،
[دليل النشر](../evidence/phase56-acceptance-20261025/deployment-provenance.json).

## نتائج مستقلة على النسخة الحالية b849b1

الأرقام assertions مسجلة، مش عدد اختبارات كلود أو عدد قصص مقبولة. الجدول بيفصل الـUAT عن التنفيذ المحلي.
نتائج السكريبت المتداخل اتجمعت بأحدث نتيجة لكل test ID **داخل نفس SHA فقط**؛ المحاولات الخام محفوظة.

| النطاق | PASS | FAIL | BLOCKED | NOT_RUN |
|---|---:|---:|---:|---:|
| attestation الحالية | 5 | 0 | 0 | 0 |
| UAT مركّز Phase 5/6، بعد إزالة تكرار prerequisites | 45 | 2 | 0 | 0 |
| dashboard UAT ومطابقة source APIs والموبايل | 27 | 0 | 0 | 0 |
| cross-phase signed-in journeys للمراحل 2–4 | 35 | 0 | 0 | 0 |
| أدوار/reset/revocation/concurrent sessions | 41 | 0 | 0 | 0 |
| تحليل/render ملفات CSV/XLSX/PDF المولدة من UAT | 10 | 0 | 0 | 0 |
| محلي: صيغ التصدير وحدودها | 30 | 0 | 1 | 0 |
| محلي: تقارير العمليات ومطابقة fixtures/SQL | 47 | 0 | 0 | 0 |
| محلي: React حقيقي + API + PostgreSQL، reports/downloads/RTL/mobile/axe | 34 | 0 | 0 | 0 |
| محلي: تكامل قضائي للمراحل 2–4 | 6 | 0 | 0 | 0 |
| محلي: V406/V407 وسباق سلسلة التأجيل | 7 | 1 | 0 | 0 |
| محلي: QAD-033 الأصلي وrollback بعد خطأ DB | 6 | 0 | 0 | 0 |
| محلي: ترقية Flyway فعلية V405→407 ببيانات موجودة | 5 | 0 | 0 | 0 |
| محلي: 10,000/10,001 لكل تقرير عمليات جديد | 13 | 3 | 0 | 0 |
| محلي: baseline أداء خفيف | 4 | 0 | 0 | 0 |

إجمالي المجموعتين: **UAT 163 PASS / 2 FAIL**؛ **محلي 152 PASS / 4 FAIL / 1 BLOCKED**.
الفشلان في UAT إعادة إنتاج مختلفة لنفس QAD-037؛ الثلاثة في export limits نفس QAD-039 بثلاث صيغ.
BLOCKED المحلي هو محاولة تشغيل Docker محليًا، ومحفوظ زي ما اتنفذ؛ مش نتيجة فشل PDF من UAT.
في سجل التغطية الإضافي **2 BLOCKED و2 NOT_RUN** موضحين تحت؛ مش مضافين ضمن نجاح الاختبارات.

المراجع: [UAT بدون تكرار](../evidence/phase56-acceptance-20261025/current-uat-reconciled.json)،
[كل التشغيلات الخام](../evidence/phase56-acceptance-20261025/execution-summary.json)،
[نطاق التغطية المتبقي](../evidence/phase56-acceptance-20261025/remaining-scope.json).

## قبول Phase 5

على SHA الأول `9bdbdd9...`: **76 PASS / 1 FAIL** في 77 حالة UAT فريدة بعد triage للسكريبت،
و**232 PASS / 1 FAIL** محليًا في الجلسات، المهام، المستندات، الخبراء، الملاحظات، chronology، SQL COMMIT وrollback.
اختبارات الرجوع للمراحل 2–4 كانت **76 PASS** إضافية. نتائج SHA القديم محفوظة ومش متسجلة كتشغيل على SHA الجديد.

اتجربت scheduling/rescheduling/adjournment/held/cancelled، حفظ التاريخ، optimistic locking، وسباقات bounded
من طلبين؛ إنشاء/إسناد/إنهاء/reopen/cancel task وحساب reminders؛ رفع وتنزيل PDF ومطابقة bytes/hash، quota10
بسباق uploadين؛ registry/referral/session/report/cancel؛ note author-only ضد admin، XSS كنص، وتاريخ القضية
وعدم تسريب النص الخاص فيه؛ audit ACCESS_DENIED مترابط بـrequest IDs. فيه browser submissions فعلية للجلسات،
PDF، notes، tasks وإحالة الخبراء، مش unit tests فقط.

على SHA الجديد اتضاف تحقق read-only موجب: role/user صناعي مؤقت، خمس قراءات200 وخمس كتابات403، وبعده الحساب
اتعطّل. حساب qa.viewer الأصلي صلاحياته مقصودة للمراحل 2–4؛ مااعتبرناش غياب صلاحيات Phase 5 عنده عيب منتج.
الـPDF الصالح المولد من LawDesk اترفع واتنزّل بمتصفح حقيقي. واجهة القضية اتراجعت en/ar عند320/375/1366px؛
مفيش إخفاق overflow أو axe A/AA في الاختبارات المنفذة. ده مش بديل لتجربة screen reader أو إعادة تصميم Phase7.

## العيوب وحالتها

| ID | الحالة الحالية | الأثر / دليل التحقق |
|---|---|---|
| QAD-027 / QAD-028 | FIX_VERIFIED_UAT | mobile/signed-in regression، reset/session revocation/concurrent sessions على b849b1 |
| QAD-029 | قرار قبول لسه مفتوح | pagination غير الصالح بيرجع defaults200 في قوائم، و400 في قوائم أخرى؛ التنفيذ الحالي متوافق مع الافتراض المكتوب، مش دليل موافقة المالك عليه |
| QAD-030 | الإصلاح السابق محفوظ | dashboard الحالي من سجلات فعلية؛ مفيش sample figures |
| QAD-031 | قرار قبول لسه مفتوح | OPEN→DONE المباشر متوثق كافتراض للمراجعة؛ اختبرنا السلوك الحالي ولم نغلق قرار المالك |
| QAD-032 | FIX_VERIFIED_UAT_AND_LOCAL_API | fresh hearing-only fixture على b849b1: filing بعد الجلسة422، كامل parent/history unchanged |
| QAD-033 | FIX_VERIFIED_ORIGINAL_SCOPE | الثلاث مخالفات الأصلية بnon-owner SQL COMMIT مرفوضة، وrollback ناجح؛ QAD-038 فجوة إضافية مستقلة |
| QAD-034 | FIX_VERIFIED_UAT_AND_LOCAL_API | fresh expert-only fixture على b849b1؛ جلسات/إحالات وV407 محليًا |
| QAD-035 | FIX_VERIFIED_UAT | used-range XLSX صحيح؛ normal وstreaming readers بيشوفوا كل الصفوف |
| QAD-036 | FIX_VERIFIED_UAT | `=1+1` محفوظ في العربي والإنجليزي؛ PDF العربي اتعمله render وفحص بصري |
| **QAD-037 — S2** | **CONFIRMED_OPEN** | PDF صالح1,047,552bytes→201؛1,048,577bytes→413؛10MiB→413 رغم إنه مسموح |
| **QAD-038 — S2** | **CONFIRMED_OPEN** | SQL transactions تلتزم بتاريخ parentJan5/childJan4 رغمV406؛ موجود على9bdbdd9 وb849b1 |
| **QAD-039 — S3** | **CONFIRMED_OPEN** | experts10001→500 بدل422 EXPORT_TOO_LARGE فيCSV/XLSX/PDF؛ hearings/workload الحدود صحيحة |

إعادة الإنتاج بالتفصيل: [QAD-037](../defects/qad-037.json)، [QAD-038](../defects/qad-038.json)،
[QAD-039](../defects/qad-039.json). كلهم اتبعتوا لـClaude فور التأكد على PR #6 / #48.
QAD-001..006 وباقي baseline findings القديمة محفوظين؛ مفيش إغلاق ضمني لهم في الجولة دي.

QAD-038 مش ادعاء إن مستخدم API يقدر يتجاوز authorization: هو التزام قضائي قابل للكسر بواسطة SQL writer
عادي مصرح له بالكتابة. إعادة الإنتاج محلية فقط، بمستخدم غير owner وغير superuser، ومن غير تعطيل triggers.

## Phase 6 والتوليد داخل البيئة المنشورة

الحسابات اتراجعت على fixtures مستقلة ثم source API records على UAT: totals/statuses، انتظار الحكم/نتيجة الجلسة،
week boundary اليوم..+6، overdue/dueSoon، inactive experts وworkload. اتجربت pagination/order/filter validation،
صلاحيات REPORT_VIEW وREPORT_EXPORT منفصلين، CSV formula neutralization، XLSX cell types/RTL، PDF embedded
fonts واللغة واتجاه النص. حدود10,000 نجحت للتقارير الجديدة؛10,001 كشف QAD-039 فقط.

CI/CD job `121128265734` فيه build وتشغيل صورة `lawdesk-local/backend:b849b1...` وتأكيد current SHA؛
ده **دليل نشر من المطور**، مش نجاح QA. بعده QA بنفسه نفذ طلبات PDF/XLSX/CSV المفلترة على UAT، وأكد release،
وحلل ورسم الملفات الناتجة. بالتالي اختبار توليد PDF على المسار الفعلي للصورة المنشورة اتنفذ، رغم استمرار عدم
القدرة على Docker exec محليًا. مفيش ادعاء إن QA عمل docker inspect أو rollback على المضيف.

لقطة ملف عربي مولد فعليًا: [PDF render](../evidence/phase56-acceptance-20261025/uat-artifacts-verified/uat-ar-page1.png).
لقطات React وتقارير axe موجودة في
[current-operations/browser](../evidence/phase56-acceptance-20261025/current-operations/browser/results.json).

## أمان وأداء خفيف

- راجعت authorization للـreport controllers، حدود التصدير، CSV neutralization، صيغ SQL المقيدة/parameters،
  attachment/no-store/nosniff، وإدخال notes كـtext. الاختبارات الآمنة أثبتت رفض الكتابة بدون CSRF وعدم تنفيذ stored
  script؛ fault injection محلي أثبت فشل export مغلقًا لو audit فشل. مفيش حقن faults على UAT.
- npm audit: **269 dependency entries، صفر advisories** في lockfile المختبر؛ frontend lockfile متطابق بين الرأسين.
- OSV: **99 Maven coordinates** مستخرجة من JAR أو متحقق من هويتها بـSHA256 مقابل cache؛ صفر تطابقات معلنة.
  `spring-boot-jarmode-tools-4.1.2.jar` فضل خارج فحص الهوية المؤكدة. ده فحص advisories، مش إثبات غياب ثغرات.
- gitleaks مستقل على backend source وعلى أدلة QA قبل النشر؛ الأسرار المعتمدة اتقارنت بالأدلة في الذاكرة من غير
  طباعة قيمها. مفيش passwords/cookies/CSRF محفوظة في الأدلة.
- baseline محلي فقط:20cases/20proceedings/20hearings، client واحد،30قياس لكل endpoint،100ms spacing.
  p95: cases **157.609ms**، hearings **293.580ms**، dashboard **137.643ms**؛ error rate0 في90طلب.
  أعلى RSS مرصود503,795,712bytes. throughput المقاس شامل pacing، مش saturation. العينات قليلة والـhost غير
  محكوم بالكامل؛ مفيش SLO أو استنتاج قدرة production. [المقاييس والقيود](../evidence/phase56-acceptance-20261025/performance/performance-baseline.json).

مفيش load/stress/spike/soak على Contabo، ومفيش orchestration جديد.

## التغطية المتبقية وشروط القبول

BLOCKED: audit SQL مباشر لبيانات UAT التاريخية كلها، وindependent real-image/database rollback drill؛ الصلاحية/البيئة
المعزولة مش متاحة. نجاح CI للدريل متراجع كدليل مطور فقط. NOT_RUN: full automated Java/TS SAST، والـJAR الصغير
المتبقي في SCA. دي مش موانع تلقائية لنشر Dev/UAT بموجب سياسة المالك؛ اختبارات الإنتاج والتصميم Phase7 منفصلة.

المطلوب من Claude: إصلاح QAD-037 في إعداد LawDesk المعزول أولًا، وإصلاح serialisation لQAD-038، وترتيب
limit check لQAD-039؛ تسليم SHA جديد وإعادة النشر بإجراءات المالك القائمة. QA يعيد الحالات المتأثرة والرجوع
المناسب بعد التأكد من `/api/v1/release`. **مفيش طلب تكرار إصلاح QAD-035/036 أو إصلاحات اتثبت نجاحها.**
مش مطلوب تدخل روتيني من المالك دلوقتي؛ لو مطلوب إغلاق audit بيانات UAT بالكامل، يلزم diagnostic قراءة معتمد
يرجع counts فقط، ولو مطلوب دريل مستقل يلزم Docker معزول مصرح له. ده لا يبرر لمس Matbakhy.

## نزاهة الأدلة وطريقة إعادة التشغيل

المحاولات الخام محفوظة، بما فيها أخطاء QA harness: مسار قراءة hearings الخاطئ405، اختيار staff بدل manager
لإدارة الخبراء، مقارنة header متعدد القيم، تكرار reference fixture، اعتبار workload.OPEN شاملIN_PROGRESS،
ومقارنة مرجع PDF ملتف على أسطر كأنه سطر واحد. اتصحح السكريبت واتعاد الجزء المتأثر فقط، أو إعادة parser على
نفس الملف؛ مفيش واحد منهم متسجل كعيب منتج. الأولوية لـreconciled files للنتيجة، والخام للـaudit.

البيئة: Chromium/Playwright الحقيقي؛ Java21؛ PostgreSQL18.6 منفصل لكل suite؛ canonical archives في`/tmp`؛
openpyxl3.1.5/PyMuPDF1.26.7 للتحليل. UAT credentials تتقرأ من الدليل المعتمد في الذاكرة فقط.
السكريبتات في [qa/independent](../independent/). مثال محلي:

```sh
PYTHONPATH=/tmp/lawdesk-qa-python python3 qa/independent/phase5_database_acceptance.py \
  --candidate <canonical-archive> --sha <full-sha> --output <fresh-evidence-directory>
```

UAT: `phase5-signedin.mjs <full-sha> <fresh-output>`، مع browser executable/libraries المناسبة؛
`LAWDESK_QA_ONLY` لاختيار الحالات، و`LAWDESK_QA_RETEST_FROM` لmanifest سابق **لـowned fixtures فقط**.
السكريبت يثبت runtime SHA من جديد، وبيسجل SHA أصل fixtures منفصل. مفيش test يُحسب PASS لمجرد وجود التنفيذ أوCI.
