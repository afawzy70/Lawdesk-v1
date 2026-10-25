# متابعة QA المستقلة — UAT والمرحلتين الخامسة والسادسة

إصلاح **QAD-027 و QAD-028 اتأكد على UAT**. النسخة المنشورة فعليًا من `/api/v1/release` هي `bdd9d4249fa246aca6cc1f1aef4cd7b6c5a9481e`، ومتطابقة مع SHA تشغيل [40102779349](https://github.com/afawzy70/Lawdesk-v1/actions/runs/40102779349)، بما فيه job النشر الناجح. تم الفحص قبل وبعد الاختبارات، من الحسابات الأربعة، والطلب المجهول مرفوض 401. Endpoint يعرض حقل release فقط. ده تعريف النسخة اللي بيعلنها process التطبيق، مش ادعاء بقياس cryptographic لصورة Docker.

**النتيجة المهمة الجديدة: QAD-032 ، P2/S2** في PR33: تعديل تاريخ قيد الدعوى يسمح بحفظ جلسة أقدم من القيد. التوصية **REQUEST_CHANGES** لقبول سلامة بيانات الجلسات. تفاصيل إعادة الإنتاج تحت، واتبلغ كلود على PR33. مفيش merge أو deploy أوتعديل Matbakhy أو workspace كلود من QA.

## النطاق والبيئة

- UAT: `https://lawdesk.167-86-90-174.sslip.io`، TLS verified ، Chromium build1248/Playwright1.64.1 و axe4.14 ، حسابات qa.admin/manager/staff/viewer المعتمدة. الأسرار تُقرأ داخليًا من الملفات المعتمدة، بدون حفظ كلمات سر أو cookies أو CSRF في الأدلة.
- الفروع: archives مستقلة من exact SHA ، Spring Boot jar حقيقي، JDK21.0.12.1+1 ، PostgreSQL18.6 مؤقت على loopback ، Vite/Node22.23.3 مع API حقيقي. build باستخدام `-DskipTests`؛ نجاح البناء مش اختبار منتج ولا تنفيذ مستقل لاختبارات المطوّر.
- بيانات UAT صناعية بأسماء QA فريدة، IDs محفوظة في manifest كل تشغيل. المستخدمون المؤقتون اتعطّلوا؛ الحسابات الأربعة الأساسية وكلمات سرهم لم تتغير. بيانات القضايا الصناعية محفوظة كأدلة، ولم تُعدّل سجلات غير مملوكة للاختبار.
- fault injection و SQL المباشر حصلوا في قواعد مؤقتة فقط، واتقفلت بعد الاختبار. لا Docker/SSH/deploy في الجولة دي. اختبارات المرشح المحلي لا تُنقل إلى UAT ولا إلى SHA جديد.

## التنفيذ المستقل

كل الأرقام هنا حالات نفذها QA بنفسه، وليست أعداد developer tests أوكل 351 حالة baseline. الحالة الواحدة ممكن تضم أكتر من assertion ؛ بعض fixture checks تتكرر بين المرشحين، فالأرقام ليست عدد acceptance criteria فريدة.

| مجموعة UAT ، كلها على bdd9d424 الكامل أعلاه | PASS | FAIL | BLOCKED | NOT_RUN داخل التشغيل |
|---|---:|---:|---:|---:|
| تعريف release قبل التنفيذ: مجهول +4 أدوار | 5 | 0 | 0 | 0 |
| الـ 35 رحلة signed-in ، تشمل layout عربي/إنجليزي 375/1366 | 35 | 0 | 0 | 0 |
| مصفوفة الأدوار، سلامة القضاء، الجلسات والتزامن | 41 | 0 | 0 | 0 |
| نماذج فعلية: أدوار/مستخدمين/قضايا/أطراف/دعاوى/أحكام/نقض/reset | 16 | 0 | 0 | 0 |
| تعريف release بعد التنفيذ | 5 | 0 | 0 | 0 |
| **الإجمالي المنشور UAT** | **102** | **0** | **0** | **0** |

QAD-027: case details والمحتوى العربي/الإنجليزي لم يعد يسبب document overflow عند 375 ؛ screenshots و axe محفوظين. QAD-028: reset يُبطل الجلسات السابقة فورًا 401 ، وبعد إكمال تغيير كلمة السر بجلسة جديدة لا تعود الجلسة القديمة. تغيير الباسورد العادي يحافظ على الجلسة الحالية ويُبطل الجلسات الأخرى؛ تعطيل ثم إعادة تفعيل الحساب لا يعيد الجلسة القديمة. ثلاث سباقات reset/change اجتازت التحقق. اختبار browser form أكد نفس النتيجة. توقع 403 القديم اتغير إلى 401 وفق contract الإصلاح؛ الأدلة التاريخية لم تتعدل.

| PR | exact tested SHA | API/SQL PASS/FAIL | Browser PASS/FAIL | مراجعة مرئية PASS/FAIL | إجمالي PASS/FAIL |
|---|---|---:|---:|---:|---:|
| #32 ، LD-030 | `63bc2d8320ab50b343a0dd89fb60d0b09c9e9991` | 11/0 | 7/0 | 0/1 | **18/1** |
| #33 ، LD-024 | `fb60c26884fc6ca25143526bc6edcf857716bf22` | 52/6 | 9/0 | — | **61/6** |
| #34 ، LD-031 | `12a1a1f38e54217d9988d6e002c1037df213e84c` | 36/0 | 14/0 | 0/1 | **50/1** |
| #35 ، تصحيح تنبيه UAT | `0dcbe01c291b1d602f69de81b6859a7464ace81e` | 11/0 | 7/0 | — | **18/0** |
| #36 ، LD-032 | `7ee71046c73cfee6b799f4893976877a1a440b76` | 70/0 | 7/0 | 0/1 | **77/1** |
| #37 ، LD-027/028 | `e40dfcb7596f4526ec7c59d3e61d04f3c3e46340` | 38/3 | 7/0 | — | **45/3** |
| #39 ، CSV لـ LD-034 | `937d5681f0a0b6b3f6130b7d4b92f3ae3cdc26bd` | 87/0 | 8/0 | 0/1 | **95/1** |
| #38 ، تجهيز transport لـ LD-026 | `e73e38e5c9aa1e70a6c1ac8984de460da9711621` | — | 6/0 | — | **6/0** |

الـ 6 اختبارات PR38 هي browser HTTP transport مع echo server محلي صناعي: multipart boundary/bytes ، CSRF ، JSON و GET regression ، 401 session event ، وعدم تسريب HTML error. **مش** قبول رفع/تخزين/تنزيل PDF في المنتج.

## النتائج والعيوب

- **QAD-027 و QAD-028: FIX_VERIFIED_UAT** على bdd9d424. لا نطلب إعادة تنفيذ الإصلاحات من كلود. ملفات العيوب تحفظ إعادة الإنتاج القديمة وتضيف تحقق النسخة الجديدة.
- **QAD-029 ، P3/S3:** PR33 يعيد 200/default للطلبات `/api/v1/hearings?page=-1` و`page=bad` و`size=-1` و`size=bad`. PR37 يعيد نفس السلوك لـ`/api/v1/tasks?page=-1` و`size=bad`. توقع QA هو 400 اتساقًا مع APIs القضايا والتقارير؛ معايير Phase5 لا تحسم invalid pagination صراحةً. يلزم توحيد التحقق أوقرار معيار صريح ثم إعادة الاختبار. الست FAIL لستة طلبات تخص عيبًا واحدًا؛ مش تجاوز صلاحيات.
- **QAD-030 ، P3/S3:** الـ banner في PR32/34/36/39 يقول إن dashboard يعرض sample figures رغم إنه يحسب بيانات قاعدة البيانات. **الإصلاح موجود بالفعل في PR35 واتحقق مستقلًا** بالعربي والإنجليزي. لا نطلب تنفيذه مرة تانية؛ يلزم دمجه عبر مسار كلود وإعادة فحص الـ HEAD المتكامل. الرؤوس القديمة تفضل FAIL في هذا البند.
- **QAD-031 ، P3/S3:** في PR37 ، أنشئ task OPEN/version0 ثم POST `/api/v1/tasks/{id}/status` بجسم `{"status":"DONE","version":0}`. الفعلي 200 وحفظ DONE/completedAt. معيار LD-027 يرسم OPEN→IN_PROGRESS→DONE ويمنع باقي الانتقالات 422 ، لكن TaskStatus يسمح بالإكمال المباشر. يُحسم قصد المنتج بإصلاح التنفيذ أوتعديل معيار مُعتمد، ثم retest. لا يوجد دليل على bypass صلاحيات أوفقد بيانات هنا.
- **QAD-032 ، P2/S2 ، PR33:** أنشئ FIRST_INSTANCE بتاريخ قيد 2026-01-02 ، ثم جلسة 2026-01-03/purposePLEADING. عدّل الدعوى عبر PUT `/api/v1/proceedings/{id}` بنفس courtId/number/year2026 ، مع filingDate2026-01-04/version0. الفعلي 200/version1 ؛ SQL join يؤكد حفظ القيد 4 يناير والجلسة 3 يناير. المتوقع لحفظ التسلسل رفض 422 بدون تغيير. **LD-024.7 يذكر تأجيل حماية تعديل الأب**: ده خلل تكامل معلوم تأكد بالتنفيذ، وليس ادعاء فشل معيار إنشاء الجلسة الضيق. يلزم حماية تاريخ القيد مع قفل/transaction مشتركة مع إنشاء وتعديل الجلسات واختبار السباق. الدليل `pr33-chronology/results.json` يتضمن الحالة المحفوظة.

نجح في PR33: تطابق case/proceeding ، رفض FK و orphan على INSERT و UPDATE ، عدم حذف proceeding مستخدم، منع duplicate day ، reschedule/cancel/held/adjourn ، successor على نفس الدعوى، رفض stale version ، السباقات 200/409 و 201/409 ، rollback كامل عند فشل timeline أوتصادم اليوم، CSRF والصلاحيات، إخفاء notes/decision من timeline ، ونماذج schedule/adjourn الفعلية. نجاح الحاجات دي لا يغلق QAD-032.

نجح في التقارير: zero/pending بوضوح، الأعداد حسب fixture ، summary على كل matching rows مش الصفحة، صفحات مستقرة، literal `%_`، الفلاتر المتعارضة والفارغة والتاريخ الشامل، رفض sort/enum/UUID/paging غير صالح، وعدم تغيير قاعدة البيانات بمجرد القراءة. PR36 تحقق من final/outcome/stage/court/period. التزامن بين كتابة جديدة و repeatable-read التقرير لم يُنفذ كاختبار مستقل.

نجح في PR37: اختيار مستخدم enabled ، رفض unknown/disabled ، قواعد due dates ، lifecycle والتوقيت، reassignment ، optimistic concurrency ، FK للقضية، rollback عند فشل timeline ، منع ARCHIVED ، وتذكيرات صاحب الجلسة فقط وحدود أمس/اليوم/+3/+4. لا FK ل assignee مطابق لل contract المعتمد؛ لا نسجله تلقائيًا كعيب.

في PR39 نجح 87 API/SQL و 8 browser checks: رفض تصدير REPORT_VIEW بدون REPORT_EXPORT ، تنسيق CSV و BOM والنص العربي والاقتباس وتحييد بداية المعادلة، تطبيق الفلاتر وتجاهل page/size عند التصدير، attachment/nosniff/no-store ، وسجل audit واحد لكل تصدير ناجح. نجح حد 10000 صف ورفض 10001 بـ 422 من غير success audit. تنزيل CSV فعليًا من المتصفح حافظ على final/date filters. سجل 10000 صف اتعمل في قاعدة مؤقتة فقط. لا ادعاء باختبار Excel/PDF أوفتح الملف في spreadsheet حقيقي، أو fault injection لفشل audit التصدير. QAD-030 موروث في نفس الرأس.

## التوصيات وحدود Dev/UAT

| PR | التوصية على الـ SHA المحدد | الشرط |
|---|---|---|
| #33 | **REQUEST_CHANGES** | QAD-032 لحماية التسلسل، و QAD-029 لحسم pagination ؛ إعادة الاختبار على HEAD جديد |
| #37 | **REQUEST_CHANGES** | حسم QAD-031 و QAD-029 ؛ لا منع تلقائي لنشر Dev/UAT صناعي بسبب عيوب P3 |
| #32 ، #34 ، #36 ، #39 | **REQUEST_CHANGES — integration dependency** | إدخال إصلاح PR35 الموجود والتحقق من HEAD المتكامل، بدون طلب تكرار تنفيذه |
| #35 | **APPROVE للنطاق المختبر** | تصحيح التنبيه، 18PASS ؛ ليس قبول كل Phase6 أوترخيص public release |
| #38 | **APPROVE لتغيير transport فقط** | 6PASS ؛ مسار PDF الكامل BLOCKED/NOT_RUN حسب توفر التنفيذ |

عيوب P3 لا تمنع دورة Dev/UAT العادية حسب قرار المالك. QAD-032 يمنع قبول سلامة بيانات الجلسات أوالاستخدام علىسجلات قانونية حقيقية؛ بيئة خاصة لاختبار الإصلاح ببيانات صناعية مسموحة. مفيش هنا تصريح public release أوتفعيل automatic CD أو QA merge/deploy.

## اللي لسه متنفذش

[remaining-scope.json](../evidence/followup-bdd9d424-20261024/remaining-scope.json) يحافظ على **8 BLOCKED** لاختبارات المرشحين على UAT لأنهم مش النسخة المنشورة، و**13 NOT_RUN**: الست فجوات السابقة (متصفحات/أجهزة حقيقية، screen reader ومراجعة ترجمة شاملة، كل permission permutations ، last-admin concurrency ، fault injection لـ Phase2–4 على SHA الحالي، وقوائم قضائية>100)، وخمس قصص غير مكتملة التحقق LD025/026/029/033/034 ، ونماذج المهام التفصيلية وتقديم فلاتر تقرير الأحكام من الواجهة. نجاح API/layout لا يغلق الحالات دي. axe ليس تدقيق accessibility كامل.

QAD-001…006 تفضل OPEN كماهي في [baseline-open-findings.json](baseline-open-findings.json) و[baseline-findings.json](../defects/baseline-findings.json). المقترحات المرحلية لا تعني اعتمادكل 42 قصة أو 351 حالة. الأدلة السابقة لـ QAD007…026 محفوظة ولا نعيد تسميتها PASS على SHA الحالي من غير إعادة تنفيذ. لا إعلان قبول شامل للمراحل 2–6 ؛ الرحلات signed-in الأساسية اتنفذت، لكن التغطية المتبقية والعيوب المذكورة واضحة.

## منشأ الأدلة وإعادة التشغيل

[فهرس الأدلة](../evidence/followup-bdd9d424-20261024/index.json) يحتويالأعدادوالـ SHAs. كل results.json فيه preconditions/data/steps/expected/actual/priority/story traceability ؛ مصادر QA توضحأجسامالطلباتوال fixtures القابلة لإعادة الإنشاء. IDs مؤقتة تتغير معكل تشغيل. screenshots لا تشملحقول باسورد.

الأوامر المستخدمة، بعد تجهيز أدواتالـ QA الموجودة:

```bash
LAWDESK_QA_CREDENTIAL_FILE=/srv/ai-workspaces/secrets/lawdesk-qa/admin.json \
  node qa/independent/browser/phase234-signedin.mjs \
  https://lawdesk.167-86-90-174.sslip.io bdd9d4249fa246aca6cc1f1aef4cd7b6c5a9481e <fresh-output-dir>
# بنفس target/SHA: phase234-forms.mjs ؛ وراجع واجهة phase234-roles-integrity.mjs للأدوار الأربعة.
node qa/independent/browser/release-attestation.mjs bdd9d4249fa246aca6cc1f1aef4cd7b6c5a9481e <fresh-output-dir>
PYTHONPATH=/tmp/lawdesk-qa-python python3 qa/independent/phase56_review.py \
  --candidate <canonical-snapshot> --sha <full-sha> --kind hearings --browser --output <fresh-output-dir>
# kinds: hearing-chronology ، dashboard ، inventory ، judgments ، tasks ، exports
```

المتصفح يحتاج LAWDESK_QA_CHROMIUM و LD_LIBRARY_PATH الموضحين في environment.json. الـ candidate يتبني بـ JDK21 و maven cache الموجود، بدون developer tests. لا تعيد fault injection على UAT.

تشغيل جلسات أول اتوقف بسبب إغلاق browser contexts ، سبب الإغلاق غير محسوم؛ محفوظ كـ diagnostic37PASS/4FAIL/1BLOCKED ، مش عيوب منتج مؤكدة ولا ضمن إجمالي التشغيل النهائي 41PASS. المستخدم المؤقت المتبقي اتعطّل بعد فحص ملكيته، quarantine.json محفوظ. تشغيل tasks أول اتوقف بعدما الانتقال غير المتوقع غيّر fixture ؛ محفوظ 13PASS/2FAIL/1BLOCKED ، ثم فصلنا fixture وأعدناالتنفيذبالكامل. لم نغيّر FAIL/BLOCKED القديمة إلى PASS.

Developer RED/GREEN logs اتراجعت من exact candidate `docs/evidence/PHASE-5/` و`PHASE-6/`، و CI الحالي ناجح على الرؤوس المسجلة. ده **developer evidence فقط**؛ لم تُشغّلاختباراتالمطوّر أوتُضمأعدادها. Logs تبين RED failures ثم GREEN ، لكنها وحدها لا تثبت تاريخ كل REFACTOR أو تستبدل التحقق المستقل. CI job النجاح ونشر UAT موثقين في deployment.json و ci-runs.json.
