# مراجعة QA المستقلة المستمرة — Phases 5 و 6 — 24 أكتوبر 2026

اتراجع كل رأس حالي واتنفذت الاختبارات من أرشيفات مستقلة؛ ما حصلش دمج أو نشر أو تعديل في فروع كلود أو Matbakhy. **QAD-032 و QAD-034 اتصلحوا عبر API واتحققوا محليًا**، لكن **QAD-033 سلامة قاعدة البيانات و QAD-035 توافق Excel لسه مفتوحين**. Phase7 مؤجل. ده قرار للنطاق المختبر و Dev/UAT ، مش قبول كامل للمرحلتين أو public release أو production readiness.

UAT الحالي اتأكد من `/api/v1/release` بالحسابات الأربعة قبل وبعد الجولة: **`19ad5386fa586e2469ecad36a677632273712f44`**. تشغيل [CI/deploy40118354959](https://github.com/afawzy70/Lawdesk-v1/actions/runs/40118354959) مطابق للـ SHA ؛ نجاح CI منفصل تمامًا عن نتائج QA أدناه.

| PR | exact tested/current HEAD | PASS | FAIL | BLOCKED | NOT_RUN داخل التشغيل | توصية الـ PR |
|---|---|---:|---:|---:|---:|---|
| #33 | `6e27f5c427380fddb1914872f53ea437eb642fc9` | 85 | 7 | 0 | 0 | **REQUEST_CHANGES** |
| #37 | `42fb56433d421a3d2040d78858d47bb81ff6d3b6` | 45 | 3 | 0 | 0 | **REQUEST_CHANGES** |
| #40 | `ded7e3c159b7cc60feffb076f09cff69dadcb583` | 32 | 0 | 0 | 0 | **BLOCKED — dependency** |
| #43 | `0db2befa642badfe262bc1edaeee287eb1b9fa43` | 51 | 0 | 0 | 0 | **BLOCKED — dependency** |
| #44 | `e4c8e470f2ad23b4a99714531c35dcf171585817` | 60 | 3 | 0 | 0 | **REQUEST_CHANGES** |
| #32 | `31044e7b674a3f7c836a8ce5491c97d359fa524a` | 12 | 6 | 0 | 0 | **REQUEST_CHANGES** |
| #34 | `b8aa9b5f3ca60505bf227b040363876d0e470b1a` | 50 | 0 | 0 | 0 | **APPROVE — scoped** |
| #36 | `875d9381dc1b6ebabb3f69759631f11abc51a696` | 77 | 0 | 0 | 0 | **APPROVE — scoped** |
| #39 | `19b2e66087ab549b2d303f54bcc2fb9c7bdc26f1` | 97 | 0 | 0 | 0 | **APPROVE — scoped** |
| #41 | `5762bee77347e5fba3fe91316ce41faccdc1d11d` | 36 | 3 | 1 | 0 | **REQUEST_CHANGES** |

الأعداد حالات اختبار منفذة، مش عدد assertions أو stories فريدة أو كل 351 حالة baseline. الجداول تضم API/SQL و browser لنفس الرأس، وتكرار fixtures بين المرشحين ظاهر في ملفات التشغيل. الصفر في NOT_RUN داخل تشغيل محدود لا يعني اكتمال كل نطاق القصة. [execution-summary.json](../evidence/continuous-phase56-20261024/execution-summary.json) يحصر كل ملف وعدده و SHA ؛ الأدلة القديمة والـ diagnostics منفصلة في `history/` ولم تتعدل.

- **#33:** QAD-032 عبر API اتصلح؛ QAD-033 سلامة DB ما زال مفتوحًا، و QAD-029 سياسة pagination.
- **#37:** QAD-029 و QAD-031 اختلافات معايير P3 ؛ ودمج الـ stack يعتمد على حسم QAD-033 في 33.
- **#40:** اختبارات المستندات 32PASS ؛ القرار على الـ PR المكدّس ينتظر سلامة اعتماده 33 ، وليس عيبًا مثبتًا في رفع الملفات.
- **#43:** QAD-034 اتصلح و 51PASS للخبير؛ الـ PR المكدّس ينتظر اعتماد سلامة 33. لا طلب لتكرار الإصلاح.
- **#44:** المذكرات 34PASS ، لكن اختبارات chronology على نفس الرأس فيها 3FAIL تخص QAD-033.
- **#32:** QAD-030: إصلاح الـ banner موجود في develop ، لكن رأس PR32 نفسه لسه يعرض الادعاء القديم. لا إعادة تنفيذ للإصلاح.
- **#34:** LD-031 المختبر؛ لا قبول للتقارير المؤجلة أو كل Phase6.
- **#36:** جزء الأحكام من LD-032 فقط؛ تقرير الجلسات ما زال BLOCKED.
- **#39:** CSV لـ LD-034 فقط؛ لا يشمل Excel/PDF أو تقارير غير منفذة.
- **#41:** QAD-035 metadata Excel ؛ QAD-036 توضيح fidelity ؛ PDF داخل حاوية النشر BLOCKED.

ترتيب الاعتماد: Phase5 هو 33→37→40→43→44 ؛ Phase6 هو 32→34→36→39→41. الرؤوس المحدثة دمجت develop وإصلاح QAD-032 ؛ البيانات والاختبارات القديمة التي كشفت غياب الإصلاح في stack سابق محفوظة، ولا تُنسب للرؤوس الحالية. الإصلاح المشترك للـ banner من 35 ، multipart من 38 ، وأسماء timeline من 45 مدموجين. PR32 ما زال OPEN رغم إن أساس dashboard دخل develop ضمن 35 ؛ عيب banner قائم على رأس 32 فقط في الاختبارات الجديدة. PR46 اندمج أثناء الجولة بوثائق سياسة التسليم؛ اتقرت السياسة، وكود backend/frontend لم يتغير.

| التنفيذ على UAT19ad5386 الكامل | PASS | FAIL | BLOCKED | NOT_RUN |
|---|---:|---:|---:|---:|
| تعريف release قبل التنفيذ، مجهول +4 أدوار |5|0|0|0|
|35 رحلة signed-in ، تشمل عربي/إنجليزي وموبايل |35|0|0|0|
| الأدوار، سلامة القضاء، reset/change/revoke والجلسات المتزامنة |41|0|0|0|
| نماذج مستخدمين/أدوار/قضايا/أطراف/دعاوى/أحكام/نقض/reset فعلية |16|0|0|0|
| dashboard: مطابقة بيانات حقيقية وصلاحيات و en/ar320/375/1366 و axe |27|0|0|0|
| تعريف release بعد التنفيذ |5|0|0|0|
| **إجمالي UAT الحالي** |**129**|**0**|**0**|**0**|

QAD-027 و QAD-028 ما زالوا FIX_VERIFIED_UAT: layout بدون page overflow ، reset يبطل الجلسات القديمة، تغيير الباسورد يبطل الجلسات الأخرى، والتفعيل مرة أخرى لا يُحيي جلسة مبطلة. الحسابات الأساسية لم تتغير؛ الـ IDs الصناعية و cleanup للمستخدمين المؤقتين محفوظين في manifests. قراءة dashboard طابقت totals/status/type/stage/judgment outcome/finality مع APIs السجلات الحقيقية، ولم تنشر محتويات السجلات. اختبارات الحسابات محليًا قارنت fixtures فعلية في PostgreSQL أيضًا.

نسخة QA المجمّعة **`f86acb963bccada608336779e7cc64ed56bb1934`**: **466PASS /13FAIL /1BLOCKED /0NOT_RUN داخل التشغيل**. دي git object مؤقتة من رأسي 44 و 41 ، مش رأس PR أو نسخة منشورة. الدمج الآلي لكود المجال لم يحتج تعديلًا؛ التعارض الوحيد اتجمع فيه ready flags الموجودة في navigation. ملفات provenance تحفظ الآباء والشجرة. اتنفذ عليها 80 اختبار Phase2–4 ، 6 سلامة case/concurrency ، chronology/hearings/tasks/documents/experts/notes ، CSV و Excel/PDF و browser. الـ 13FAIL:3 قيود DB ، 4pagination للجلسات، 3 للمهام، 3 في صيغ التصدير. لا عيب أصل QAD-032 أو QAD-034 أعيد إنتاجه على النسخة المجمّعة الحالية.

النتائج المؤكدة وإعادة الإنتاج:

- **QAD-032 ، FIX_VERIFIED_LOCAL_API_NOT_DEPLOYED:** قيد 2 يناير وجلسة 3 يناير؛ تعديل القيد 4 يناير يرجع 422 ويحافظ على الصفوف والتاريخ. نفس اليوم مسموح. ست سباقات إنشاء جلسة/تعديل قيد وثلاثة reschedule/edit حافظوا على التسلسل. الجلسة الملغاة تفضل ضمن حماية التاريخ. اتكرر على 33 و 44 والنسخة المجمعة. [الدليل](../evidence/continuous-phase56-20261024/current/pr33-hearing-chronology/results.json).
- **QAD-033 ، P2/S2:** في DB مؤقتة، بعد إنشاء A/P/H و B ، نفّذ داخل BEGIN/ROLLBACK كلًا من `UPDATE proceeding SET filing_date='2026-01-04' WHERE id=P`، `UPDATE hearing SET hearing_date='2026-01-01' WHERE id=H`، و`UPDATE proceeding SET case_id=B WHERE id=P`. الثلاثة تنجح وتسمح بتاريخ أو case/proceeding غير متسق. تغيير case_id على hearing نفسه يترفض صحيحًا. دي فجوة في trusted/direct SQL ، وليست API authorization bypass. اختباراتنا رجعت ROLLBACK ولم تغيّر UAT. [التفاصيل](../defects/qad-033.json)، [تعليق كلود](https://github.com/afawzy70/Lawdesk-v1/pull/33#issuecomment-6387613718).
- **QAD-034 ، P2/S2 ، FIX_VERIFIED_LOCAL_API_NOT_DEPLOYED:** العيب القديم سمح بتعديل تاريخ قيد الأب بعد إحالة الخبير. الرأس 43 الجديد يرفضه، ومعاه 6 سباقات إنشاء/تعديل وحفظ referral/history للملغى.51PASS API/SQL/browser. لا نطلب تكرار الإصلاح. [التفاصيل](../defects/qad-034.json).
- **QAD-035 ، P2/S2:** تصدير 6 قضايا XLSX ينتج 7rows في XML ، لكن`dimension=A1`. openpyxl3.1.5 العادي يقرأ 7 ، والـ streaming يقرأ 1 فقط، عربي وإنجليزي. الصفوف موجودة فعليًا؛ لا ادعاء بأن Excel GUI اتختبر أو بأن DB فقدت بيانات. [التفاصيل والملفات](../defects/qad-035.json)، [تعليق كلود](https://github.com/afawzy70/Lawdesk-v1/pull/41#issuecomment-6387414133).
- **QAD-036 ، P3/S3 ، ACCEPTANCE_CLARIFICATION_OPEN:** عنوان محايد`=1+1` يظهر في PDF العربي`1+1=`، و`-1+1` يظهر`1+1-`. الخوارزمية تختار RTL للنص بدون حرف اتجاهه قوي؛ يلزم حسم fidelity للتعبيرات المحايدة. وصل الحروف العربية وترتيب الأعمدة RTL نجحوا؛ ده مش formula execution أو ثغرة أمنية مثبتة. [الصورة](../evidence/continuous-phase56-20261024/current/pr41-formats-final/artifacts/ar-page1.png)، [التفاصيل](../defects/qad-036.json).
- **QAD-029 و QAD-031 ، P3/S3:** pagination غير الصالح ما زال 200/default بدل oracle400 ، و OPEN→DONE للمهام ما زال 200 رغم التسلسل المقترح. يفضلوا مفتوحين لحسم المعيار، بدون تعطيل تلقائي لتطوير أو Dev/UAT صناعي غير متعلق.
- **QAD-030 ، P3/S3:**6FAIL في ست لغات/مقاسات على 32 تخص نفس banner القديم؛ مش ست عيوب layout. الإصلاح موجود بالفعل واتحقق في UAT والرؤوس المتكاملة؛ لا إعادة تنفيذ مطلوبة.

نجح في المستندات: صلاحيات، filename traversal sanitization ، byte-identical download ، MIME/magic/empty/حد 10MiB ، سباق quota عند 9 ملفات ينتج 201/422 وعدد 10 ، حذف cascade ، stale version ، و rollback لل metadata/bytes/event عند fault injection. نجح في المذكرات: author-only حتى ضد admin غير صاحبها، 4000/4001 ، stale/concurrent edit200/409 ، حذف حقيقي، rollback عند فشل timeline ، عدم تسريب body في النشاط، و XSS markup يعرض كنص. نجح في الخبراء: قيود التاريخ/الخبير غير النشط، session/report/cancel terminal states ، سباق report/cancel ، rollback ، FK و case consistency على صف الإحالة، والنموذج الحقيقي واللغتين.

نجح في التقارير: summary على كل matching rows ، pagination مستقرة، فلاتر literal `%_` والفترات الشاملة و final/outcome/stage/court ، رفض القيم/sort غير الصالحة، وعدم الكتابة بسبب القراءة. CSV حمى البادئات الست`= + - @ TAB CR` وحافظ على الاقتباس والعربي، ورفض التصدير بلا REPORT_EXPORT. حدود 10000/10001 اتنفذت محليًا لـ CSV/XLSX/PDF ؛ PDF10000 رجع 560 صفحة وكل 9934 مرجع cap مع 66fixture أساسي. XLSX10000 له 10001row بقراءة عادية، مع استمرار عيب streaming. فشل audit المصطنع منع تسليم CSV/XLSX ونجاح audit كاذب. تنزيلات CSV/XLSX/PDF من browser فعلية، مش mocks.

**حدود التغطية:** [19 مواصفة متبقية](../independent/phase56-remaining-specifications.json): **15BLOCKED و 4NOT_RUN**، خارج أعداد التشغيل أعلاه. تشمل 3 مؤشرات dashboard المنتظرة، تقرير الجلسات وتقريري الخبراء/workload ، و UAT للـ 9 ميزات غير المنشورة. لم يُختبر Excel/LibreOffice GUI ، سباق report snapshot مع writer بشكل حتمي، Firefox/WebKit/قارئ شاشة يدوي، أو performance baseline المقاس. **PDF داخل deployment container BLOCKED** أيضًا في نتيجة 41 ؛ Docker socket يرفض الوصول و sudo غير التفاعلي يحتاج كلمة سر، ولم نغيّر صلاحيات أو daemon. لا نجاح jar أو CI يتحول إلى containerPASS. فتح ملف القضية `openedOn` وتاريخ قيد الدعوى`filingDate` معنيان مختلفان في المعايير الحالية؛ لم نفترض invariant جديدًا بينهما. Baseline **QAD-001…006 تفضل OPEN** في [السجل السابق](baseline-open-findings.json).

**البيئة والدليل:** Java21.0.12.1+1 ، PostgreSQL18.6 فعلي مؤقت، Node22.23.3 ، Playwright1.64.1/Chromium1248 ، axe4.14.0 ، openpyxl3.1.5 ، PyMuPDF1.26.7. البناء بـ`-DskipTests` وتجهيز npm ليسا PASS منتج. اتراجعت 97 ملف دليل TDD للمطور وعلامات RED/GREEN/regression و CI ؛ لم نشغّلها كاختبارات QA ولم نشهد ترتيبها الزمني بنفسنا. metadata يحفظ المصدر وال checksum ، ولا يثبت refactor مستقلًا بمجرد نجاح CI. [طريقة إعادة التنفيذ](../independent/PHASE56-EXECUTION.md).

حُفظت نتائج 1e5f340 القديمة منفصلة. أول probe قارن runtime مختلفًا بـ SHA توقعه من CI ، وآخر probe اكتشف انتقال UAT إلى 19ad538 ؛ FAIL المطابقة محفوظة كتشخيص drift وليست عيوبًا وظيفية مصطنعة. probe آخر انقطع لغياب CSRF ، فسُجل BLOCKED وأُصلح harness ليحفظ النتائج الجزئية بأمان. إعادة UAT الكاملة على 19ad538 نجحت مع attestation قبل وبعد. إنذار PDF cap الأول كان خطأ parser بسبب التفاف أرقام المرجع؛ الدليل محفوظ، و full rerun بالقارئ المصحح أثبت وجود كل السجلات. لا FAIL تاريخي اتمسح أو BLOCKED اتغير إلى PASS بدون تنفيذ جديد.

بعد نشر commit QA `307e97881a21a8fc8422694923234479f502007a`، [CI40119990135](https://github.com/afawzy70/Lawdesk-v1/actions/runs/40119990135) ظهر failure، لكن GitHub annotations تؤكد إن الست jobs **لم تبدأ أصلًا** بسبب تعثر دفعات الحساب أو الحاجة لرفع حد الإنفاق؛ الرسالة لا تحدد أيهما. ده **BLOCKED تشغيلي / NOT_RUN للـjobs**، وليس failed assertion من المنتج. إعدادات billing لم تتغير، ويحتاج صاحب الحساب يراجع Billing & plans. نتائج التنفيذ المستقل أعلاه ثابتة؛ scan محلي لكامل commit QA طلع صفر نتائج أيضًا. تفاصيل العائق وروابط handoff الجديدة في metadata.
