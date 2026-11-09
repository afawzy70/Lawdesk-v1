# المراجعة النهائية المستقلة للمراحل 2–6 — 25 أكتوبر 2026

**القبول الكامل لسه غير مكتمل.** إصلاحات QAD-037 وQAD-038 وQAD-039 اتثبتت مستقلًا. ظهر **QAD-040 — S2**:
حدث نجاح تغيير حالة المهمة بيفضل محفوظ لو commit المهمة فشل. فيه كمان نقطة قبول ضيقة لسه معلّقة:
`page=` و`size=` والقيمة المسافات بيرجعوا defaults200؛ تعليمات المالك بتحدد400 للقيم غير الصالحة وdefaults
للمعاملات المحذوفة، بينما مستند PR53 بيضيف استثناء للقيمة الفاضية. طلب التوضيح اتبعت؛ مفيش موافقة مفترضة.
دي **مش نتيجة FAIL مؤكدة** ولا طلب من Claude يغيّر السلوك قبل حسم المعيار.

كل التنفيذ الحالي على **`0338cac0fca07eb0ce06eb8f2b45a54dd0386f7e`**. بعد إزالة تكرار prerequisites في نفس
سكريبت المتصفح: **547 PASS / 1 FAIL / 0 BLOCKED / 1 NOT_RUN**. فيه سجل تغطية منفصل:
**2 BLOCKED / 4 NOT_RUN** لفحوص تشغيلية/أعمق موضحة تحت، غير نتيجة معيار pagination المعلّق.
لا اختبار مطور أو CI محسوب ضمن الأرقام دي.

## قرار كل مرحلة

| المرحلة | القرار الحالي | المانع المتبقي فقط |
|---|---|---|
| 2 — LD-007…011 | **REQUEST_CHANGES** | QAD-040 يخل بصحة سجل التدقيق LD-011 عند تكامله مع المهام. الدخول، المستخدمين، الأدوار، reset/revocation والجلسات نجحوا. |
| 3 — LD-012…017 | **REQUEST_CHANGES — تعليق معيار قبول** | حسم استثناء pagination الفاضي فقط؛ اختبارات القضايا والتعديل والأطراف والبحث والحالة نجحت، ومفيش عيب تنفيذ مؤكد جديد في المرحلة. |
| 4 — LD-018…023 | **REQUEST_CHANGES — تعليق معيار قبول** | نفس معيار pagination لقوائم المحاكم والدعاوى والأحكام. اختبارات التسلسل القضائي وSQL/API/concurrency نجحت؛ مفيش عيب قضائي مؤكد متبقٍ في النطاق المختبر. |
| 5 — LD-024…029 | **REQUEST_CHANGES** | QAD-040 في atomicity لتدقيق المهمة، ومعيار pagination المعلّق. عيوب الرفع والchronology القديمة اتثبت إصلاحها. |
| 6 — LD-030…034 | **REQUEST_CHANGES — تعليق معيار قبول** | حسم استثناء pagination في التقارير المرقمة؛ الحسابات والفلاتر والحدود والصيغ وقراءة snapshot أثناء الكتابة نجحوا. مفيش عيب تقارير مؤكد متبقٍ. |

تعليق معيار القبول في 3/4/6 **مش طلب remediation تخميني أو تعطيل نشر Dev/UAT**. لو المالك أكد defaults للقيمة
الفاضية، الملاحظة الحالية قابلة للإغلاق على الدليل الموجود؛ لو المطلوب400، نفس الطلبات تمثل إعادة إنتاج دقيقة
لـClaude. لا نعتبر أي مرحلة مقبولة بالكامل بينما معيار داخل نطاق قبولها لسه معلق. باقي `ASSUMPTION` في مستندات
القبول محفوظة في سجل منفصل للمراجعة؛ نجاح التنفيذ طبقًا لها مش موافقة business ضمنية.

[قرار كل مرحلة وربط القصص بالاختبارات](../evidence/final-phase2-6-20261025/phase-verdicts.json).
ده قرار QA وظيفي، مش إذن merge/deploy أو public release/automatic CD، ومش PRODUCTION READY.
Phase7 شغالة بتوجيه المالك بشكل مستقل؛ QA ماعدلش فرعها أو تنفيذها.

## هوية التنفيذ والبيئة

- GitHub `develop` وruntime المنشور: `0338cac0fca07eb0ce06eb8f2b45a54dd0386f7e`، اتأكدوا أول الجولة وآخرها.
- PR53 HEAD: `91e04ec2097283bfca886537bce010d7d889b78f`؛ اتدمج عند SHA المختبر. الـGit tree متطابق؛
  التنفيذ منسوب للـmerge SHA فقط، مش تشغيل مستقل مزعوم على HEAD تاني.
- `/api/v1/release` بالحسابات الأربعة بيرجع SHA المتوقع؛ anonymous401 اختبار منع وصول فقط، مش إثبات نجاح رفع.
- UAT: Chromium/Playwright فعلي على HTTPS، credentials في الذاكرة من الدليل المعتمد، synthetic namespaces
  وowned fixture manifests فقط. مفيش SQL مباشر أو fault injection على UAT.
- محلي: canonical archive منفصل، Java21.0.12.1+1، PostgreSQL18.6 مؤقت، Node22/Playwright1.64.1،
  openpyxl3.1.5 وPyMuPDF1.26.7. SQL bulk/commit faults/deadlocks كلها في DB المحلية المؤقتة.
- QA لم يعمل merge/deploy، ولم يغيّر Matbakhy أو الـproxy المشترك أو workspace/feature branches كلود.

[هوية المصدر](../evidence/final-phase2-6-20261025/source-identity.json)،
[release البداية](../evidence/final-phase2-6-20261025/release-start/results.json)،
[release النهاية](../evidence/final-phase2-6-20261025/release-end/results.json).

## التنفيذ المستقل

| النطاق | PASS | FAIL | BLOCKED | NOT_RUN |
|---|---:|---:|---:|---:|
| UAT: release بداية ونهاية، أربع أدوار + anonymous | 10 | 0 | 0 | 0 |
| UAT: المراحل 2–4، browser/API workflows | 35 | 0 | 0 | 0 |
| UAT: أربع أدوار، reset/revocation، concurrent sessions، نزاهة قضائية | 41 | 0 | 0 | 0 |
| UAT: المراحل 5/6، الرفع والتقارير والتدقيق، بعد إزالة التكرار | 82 | 0 | 0 | 0 |
| UAT: dashboard مقابل source records، عربي/إنجليزي وmobile/axe | 27 | 0 | 0 | 0 |
| تحليل ملفات CSV/XLSX/PDF المولدة فعليًا من UAT | 10 | 0 | 0 | 0 |
| محلي: QAD-038 SQL/locks/COMMIT/deadlock | 14 | 0 | 0 | 0 |
| محلي: chronology API، filing changes وسباقات الجدولة/التعديل | 29 | 0 | 0 | 0 |
| محلي: expert registry/referrals/sessions/rollback | 37 | 0 | 0 | 0 |
| محلي: Flyway V407→V408 على بيانات موجودة | 5 | 0 | 0 | 0 |
| محلي: QAD-039، 9,999/10,000/10,001 بصيغ التصدير الثلاث | 20 | 0 | 0 | 0 |
| محلي: تقارير القضايا والأحكام والفلاتر والصلاحيات وCSV | 89 | 0 | 0 | 0 |
| محلي: تقارير العمليات، SQL oracle والصيغ والتدقيق | 47 | 0 | 0 | 0 |
| محلي: React + API + PostgreSQL، reports/downloads/RTL/mobile | 34 | 0 | 0 | 0 |
| محلي: workload 10,000/10,001؛ batching لتقرير 10,001 قضية | 10 | 0 | 0 | 0 |
| محلي: تقرير snapshot أثناء الكتابة، fixture + assertion مجمّع | 2 | 0 | 0 | 0 |
| محلي: سياسة pagination وtask audit | 55 | 1 | 0 | 1 |
| **الإجمالي بعد إزالة تكرار نفس browser harness** | **547** | **1** | **0** | **1** |

**UAT 205 PASS**؛ **محلي 342 PASS / 1 FAIL / 1 NOT_RUN**. الأرقام checks مسجلة، مش عدد القصص المقبولة؛
بعضها يجمع طلبات متعددة. كل check فيه prerequisites/data/steps/expected/priority/story IDs.
الإجمالي الخام **566 PASS / 1 FAIL / 1 NOT_RUN**، والفرق19 هو logins/fixtures/release وإعادة probe مكتمل
لنفس IDs في نفس harness وعلى نفس SHA. كل المحاولات الخام محفوظة، ومفيش طرح لFAIL عشان يختفي من التقرير.

[التشغيلات الخام](../evidence/final-phase2-6-20261025/execution-summary.json)،
[حساب الأعداد](../evidence/final-phase2-6-20261025/counting-summary.json)،
[UAT بدون تكرار](../evidence/final-phase2-6-20261025/phase56-uat-reconciled.json).

## QAD-037 — FIX_VERIFIED_UAT

PDF صالح أقل من1MiB عند1,047,552bytes، وفوقه عند1,048,577bytes، رجع201 بالحساب المصرح. تنزيل كل حجم
طابق bytes وSHA256 ومعرّف القضية. PDF صالح **10,485,760bytes بالضبط** رجع201 واتنزّل مطابق؛
10,485,761bytes اترفض400 من غير orphan metadata. الـpadding جوه PDF قبل `startxref`، مع حفظ xref offsets وEOF.
سباق uploadين لما القضية فيها9 ملفات رجع201/422، والمحفوظ بالضبط10. الملفات التجريبية اتشالت بعد اختبارات الحد.
اتجربت actual browser upload/download، viewer403، وrole صناعي read-only يقرأ خمس وحدات ويترفض في خمس كتابات؛
الحساب الصناعي اتعطل بعد التنفيذ. مفيش401 من anonymous متسجل كنجاح رفع.

[اختبارات الرفع](../evidence/final-phase2-6-20261025/qad037-uat/results.json)،
[تنزيل الحجمين ومطابقة المحتوى](../evidence/final-phase2-6-20261025/document-persistence/results.json).

## QAD-038 — FIX_VERIFIED_INDEPENDENT_POSTGRESQL_AND_API

إعادة الإنتاج الأصلية اتنفذت بمستخدم PostgreSQL غير owner وغير superuser. اختباران جديدان فعليان لكل ترتيب
parent-first/successor-first رصدوا `pg_stat_activity` في انتظار Lock، وبعد commit الكاتب الأول رفض التاني
بـSQLSTATE23514؛ عدد الأزواج المخالفة0 وتاريخ القضية ثابت. نفس التحقق اتعمل referral-first/session-first.
اختبار deadlock فعلي رجع40P01 لطرف وCOMMITTED للطرف التاني؛ الكتابة السابقة للضحية اتعمل لها rollback،
ومفيش حالة قضائية غير صالحة محفوظة. ده لا يدعي استحالة deadlock تحت أي workload؛ بيثبت rollback صحيح في السباق المختبر.

ترقية Flyway الحقيقية V407→V408 حافظت على snapshots للدعاوى والجلسات والإحالات وجلسات الخبراء وتاريخ القضايا.
API chronology29، expert37، وUAT child-only guards رفضوا filing date المخالف422 من غير تغيير parent/history.
تعديل `openedOn` للقضية و`filingDate` للدعوى معنيين مختلفين في العقد الحالي؛ QA مااخترعش invariant جديد بينهم.
التدقيق المباشر لكل بيانات UAT القديمة لسه BLOCKED ومش مستبدل بنجاح synthetic local DB.

[SQL والسباقات](../evidence/final-phase2-6-20261025/qad038-db/results.json)،
[الترقية](../evidence/final-phase2-6-20261025/v408-upgrade/results.json)،
[API chronology](../evidence/final-phase2-6-20261025/chronology-api/results.json)،
[experts](../evidence/final-phase2-6-20261025/expert-regression/results.json).

## QAD-039 — FIX_VERIFIED_INDEPENDENT_LOCAL

9,999 و10,000 خبير: CSV/XLSX/PDF رجعوا200؛ تحليل مستقل أثبت العدد الكامل ومجموعة الأسماء نفسها، من غير صفوف
مفقودة أو إضافية. PDF10,000 فيه334 صفحة واتقارن كل identifier، مش مجرد أول صفحة.
10,001: الصيغ الثلاث رجعت422 `EXPORT_TOO_LARGE`، من غير attachment أو exception leakage أو success export audit.
التقرير المعروض عادي قدر يعرض10,001، واسم كل خبير اتجاب صح عبر chunks.
كمان workload حد10,000/10,001 نجح، وsummary10,001 قضية طابق5,001 first-instance و5,000 بدون دعوى،
والفلاتر بالمرحلة والمحكمة صحيحة. الـbulk ده محلي فقط؛ UAT اتحقق بصيغ الملفات الفعلية الصغيرة على owned prefixes.

[حدود الخبراء](../evidence/final-phase2-6-20261025/qad039/results.json)،
[batching القضايا](../evidence/final-phase2-6-20261025/report-chunks/results.json)،
[snapshot مع writer](../evidence/final-phase2-6-20261025/report-snapshot/results.json).

## QAD-040 — S2، CONFIRMED_OPEN

على SHA الحالي، `TaskService.changeStatus` بينادي `AuditLog.record` قبل outer COMMIT؛ الأخير `REQUIRES_NEW`.
بالتالي نجاح التدقيق ممكن يتعمل لهcommit مستقل، وبعده المهمة تفشل. ده يخالف LD-027 criterion3a ويؤثر على LD-011.

إعادة الإنتاج، **محليًا في DB مؤقتة فقط**:

1. أنشئ مهمة QA فيOPEN/version0، واحفظ snapshot المهمة وcase_event؛ عدد أحداثTASK_STATUS_CHANGED لنفسtargetId يساوي0.
2. ركّب constraint trigger علىUPDATE task من نوعDEFERRABLE INITIALLY DEFERRED، دالته ترفعexception عندCOMMIT.
3. بحسابTASK_MANAGE نفّذPOST `/api/v1/tasks/{ownedTaskId}/status` بجسم `{"status":"DONE","version":0}`.
4. الرد500؛ المهمة والتاريخ رجعوا زي ما كانوا، لكن فيه **حدث SUCCESS/OPEN->DONE جديد** وعدد الأحداث0→1.
5. شيل trigger/function الخاصة بالاختبار. لا حقن أخطاء على UAT.

المتوقع: rollback لا يترك success event. إثبات العيب مش متوقف على screenshot؛ snapshots/counts وassertion
`TASK-COMMIT-ROLLBACK-AUDIT-ATOMICITY` محفوظين. المسار الطبيعي على UAT نجح، والحدث ظاهر ومترجم في admin audit
بالعربي والإنجليزي؛ ده لا ينفي عيب الفشل المتأخر.

[العيب وSQL وخطواته](../defects/qad-040.json)،
[الدليل الفاشل](../evidence/final-phase2-6-20261025/owner-policy/results.json)،
[لقطة التدقيق العربي](../evidence/final-phase2-6-20261025/phase6-uat/task-audit-ar.png).
اتبلّغ فور التأكد على [PR53](https://github.com/afawzy70/Lawdesk-v1/pull/53#issuecomment-6397554788)
و[PR6](https://github.com/afawzy70/Lawdesk-v1/pull/6#issuecomment-6397555571).

المطلوب من Claude: جعل success task audit atomic مع business transaction، مع الحفاظ على مستقلية أحداث الدخول
والرفض المطلوبة. إعادة اختبار deferredCOMMIT failure، success exactly-once، stale/refused writes، audit-write
failure وauthentication audit. مفيش طلب إعادة تنفيذ إصلاحات037–039 اللي اتثبتت.

## تسوية كل findings القديمة

[السجل الموحد QAD-001…040](../evidence/final-phase2-6-20261025/all-qad-reconciliation.json) يحفظ مصادر وSHAs التاريخ:

- QAD-001…006: **PARTIALLY_RESOLVED، الباقي ظاهر**. بقى فيه عقود مفصلة ومواصفات مستقلة وruntime/CI evidence؛
  القوالب الأصلية، مراجعة business assumptions والتغطية النهائية للإنتاج لسه مش مقفولة ضمنيًا.
- QAD-007…021: الإصلاحات المستقلة التاريخية محفوظة على SHAs الأصلية؛ مش محسوبة كتشغيل جديد0338.
- QAD-022…028: الإغلاق المستقل السابق محفوظ؛ UAT الحالي35+41 وموبايل en/ar يؤكد عدم رجوع العيوب الأصلية.
- QAD-029: original invalid nonempty cases اتثبتت400 على17 قائمة، omitted0/20 وsize101→100؛ auth precedes
  validation401/403. الاستثناء الفاضي معلّق، ومفيش PASS لمعيار لسه مش متفق عليه.
- QAD-030: dashboard الحالي فعلي ومش sample؛ failure القديم في PR32 المنفصل تاريخي.
- QAD-031: قرار المالك OPEN→DONE محسوم ومتحقق موجبًا مع audit؛ مشكلة rollback الإضافية QAD-040 منفصلة.
- QAD-032/033/034: الإصلاحات الأصلية محفوظة ومتعززة بالـDB/API/UAT الحالي؛ QAD-038 الإضافي اتقفل مستقلًا.
- QAD-035/036: ملفات UAT الجديدة اتفحصت بقراءة XLSX normal/streaming وPDF rendering؛ العربي و`=1+1` صحيحين.
- QAD-037/038/039: الحالات الجديدة ناجحة بالنطاق الدقيق الموضح فوق؛ QAD-040 هو العيب الوظيفي المؤكد المفتوح.

[مصير الحالات القديمة BLOCKED/NOT_RUN](../evidence/final-phase2-6-20261025/historic-blocked-disposition.json):
كل الوظائف اللي كانت منتظرة ports/deployment بقى لها تنفيذ مستقل جديد. الملفات القديمة مااتعدلتش لإظهار نجاح مزيف.
اختبار report/writer اللي كانNOT_RUN اتنفذ محليًا بعشر قراءات وكتابات متداخلة، وكل response احتفظ بpage/summary متسقين.

## أمان وأداء وفحوص متبقية

الـreview ركّز على auth قبلvalidation، CSRF، session revocation، scoped upload proxy، read-only permissions،
SQL parameterization، export limit قبل enrichment، منع formula injection، MIME/attachment/nosniff/no-store،
وحفظ atomicity. QAD-040 اكتشاف من المراجعة دي. إثبات سلامة أي scan مش إثبات غياب ثغرات.

dependency manifests مااتغيرتش من b849؛ نتيجة npm/OSV السابقة محفوظة ومش محسوبة كscan جديد.
الـbaseline السابق للأداء محلي:30قياس لكل endpoint، p95 cases157.609ms/hearings293.580ms/dashboard137.643ms،
error rate0؛ مش قياس قدرة إنتاج. اختبارات bulk الحالية محلية correctness، مش load test على Contabo.
[change-impact وhashes](../evidence/final-phase2-6-20261025/carry-forward-review.json).

CI/deployment job121159401480 نشر صورةbackend بالـSHA الحالي؛ ده **دليل مطور**. QA نفسه تحقق منrelease ونفّذ
توليدPDF على UAT وحلل الملفات. بالتالي مسارPDF المنشور اتختبر؛ مفيش ادعاء Docker exec/inspect مستقل.
[provenance](../evidence/final-phase2-6-20261025/deployment-provenance.json)،
[PDF العربي المنشور](../evidence/final-phase2-6-20261025/uat-artifacts/uat-ar-page1.png).

[التغطية المتبقية](../evidence/final-phase2-6-20261025/remaining-scope.json):

- **BLOCKED2:** SQL diagnostic مباشر لكل بياناتUAT التاريخية؛ independent image+database rollback drill فيcontainers معزولة.
- **NOT_RUN4:** full automated Java/TS SAST؛ هوية/advisories للـembedded jarmode-tools JAR المتبقي؛ Excel/LibreOffice GUI؛
  Firefox/WebKit وmanual assistive technology. قراءةopenpyxl والـXML والمتصفح الحالية أدلة مستقلة منفصلة.
- **NOT_RUN إضافي في نتيجة السياسة:** حكم قبول empty/whitespace pagination، رغم تسجيل السلوك الفعلي200.

ده مش تحويل production checks لشروط نشر Dev/UAT. Full stress/load/spike/soak والتقييم الأمني النهائي Phase8؛
مفيش اختبار مزعج على السيرفر المشترك أو orchestration جديد. الـbusiness assumptions اللي ماصدرش قرار صريح فيها
متسجلة [بمكانها في الملفات](../evidence/final-phase2-6-20261025/acceptance-assumptions.json)، مش مقفولة بقرار QA.

## إعادة التشغيل ونزاهة الدليل

كل النتائج الأصلية تحت `qa/evidence/final-phase2-6-20261025/`، والـscripts تحت`qa/independent/`.
مثال: `PYTHONPATH=<qa-deps> python3 qa/independent/final_integrity.py --candidate <canonical-archive> --sha <full-sha> --output <fresh-dir>`.
نفسCLI لـ`final_expert_exports.py` و`final_owner_policy.py` و`final_report_chunks.py` و`final_report_snapshot.py`.
Flyway: `phase5_migration_upgrade.py` مع `--baseline 407`.
UAT: `phase5-signedin.mjs <sha> <fresh-dir>`؛ `LAWDESK_QA_ONLY` لاختيارscope، و`LAWDESK_QA_RETEST_FROM`
لـowned-fixture manifest فقط. الحسابات المعتمدة لا تظهر في command arguments أو الأدلة.

الفشل الأول لترقيةDB كان syntax error في QA harness قبل تشغيل المنتج؛ اتصلح واتنفذت الترقية5PASS.
إضافات assertions للتنزيل اتنفذت كتجربة جديدة محفوظة. [harness notes](../evidence/final-phase2-6-20261025/harness-notes.json).
مفيش app logs أو credential files أو cookies/CSRF في الأدلة المنشورة؛ checksum manifest وsecret checks موثّقين معها.

**الخطوة المطلوبة:** Claude يصلحQAD-040 ويسلمSHA؛ QA يعيد السيناريو الفاشل والرجوع المتأثر على النسخة الجديدة
ثم يثبتUAT release بعد نشره بالإجراء القائم. المالك يحسم empty pagination400 أمdefaults. لا يلزم إعادة تنفيذ
نطاقات نجحت على مصدر غير متغير، ولا موافقة نشر أو merge من QA في الجولة دي.
