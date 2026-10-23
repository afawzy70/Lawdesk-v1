# مراجعة QA المستقلة لـ PR #2 — Phase 1

التوصية: **REQUEST_CHANGES** للنسخة المختبرة، مع بقاء قبول المرحلة والإصدار **BLOCKED**.

PR: https://github.com/afawzy70/Lawdesk-v1/pull/2

اتحدث develop المحلي إلى `91551a3` بعد دمج PR #3. المراجعة اتعملت على فرع QA منفصل ونسخ `/tmp` مستقلة؛ مفيش تغيير في مساحة Claude أو فرع التنفيذ أو Matbakhy أو الـ proxy المشترك.

الـ SHA النهائي المثبت للمراجعة: `ffd3c8a297db0172d78ac5840a51a67d7fffcfbb`.
اتراجعت كمان نسخ `15998539` و`e1efac19` و`663677df` أثناء تقدم التنفيذ. كل نسخة ليها evidence directory منفصل؛ مفيش دمج أرقام من SHAs مختلفة. أي head أحدث من SHA المثبت **غير متحقق منه** لحد إعادة التنفيذ.

## النتائج عند SHA المثبت

| النطاق | PASS | FAIL | BLOCKED | المعنى |
|---|---:|---:|---:|---|
| أدوات وسياسة وCI الخاصة بالمطوّر | 31 | 0 | 0 | إعادة تشغيل مستقلة لاختبارات المطوّر، مش حالات QA جديدة |
| backend الخاص بالمطوّر | 40 | 0 | 0 | verify فعلي مع PostgreSQL18.6 |
| frontend الخاص بالمطوّر | 50 | 0 | 0 | lint/typecheck/coverage/build كمان نجحوا |
| assertions مستقلة لسياسة المستودع وHTTP | 33 | 7 | 0 | جزء من الفشل اختلاف عن عقد مقترح غير معتمد |
| اختبارات SQL مستقلة | 3 | 4 | 0 | سبع assertions على PostgreSQL حقيقي |
| React component assertions مستقلة | 11 | 0 | 0 | real components، network controlled، jsdom |
| سجل حالات المنتج الأصلية | 0 | 2 | 349 | QA-0003 وQA-0026 فشلوا؛ مفيش ترقية PASS من partial assertions |

إجمالي assertions المستقلة: **47 PASS / 11 FAIL** من 58؛ الأرقام دي مش عدد قصص أو حالات قبول كاملة. الأربع إخفاقات الخاصة بـSQL والتلاتة الخاصة بالفروع عيوب مثبتة؛ باقي الأربع نتائج توضح اختلاف/تناقض في عقد القبول المقترح. مفيش Critical/High جديد اتأكد في التنفيذ من الاختبارات دي.

البيئة: Linux، Java21.0.12.1، Maven3.10.0 من wrapper، Spring Boot4.1.2، PostgreSQL18.6 real binaries، Node22.23.3، npm10.9.9، Vitest5.0.5/jsdom30.1.3. الاعتمادات من lock/BOM؛ cache QA منفصل، أو dependencies frontend المعزولة المعاد استخدامها بنفس lockfile. hash الـ jar ووقت الاختبار موجودين في independent-results.json.

## العيوب وإعادة الإنتاج

| ID | الخطورة | النتيجة | المطلوب |
|---|---|---|---|
| QAD-007 | Medium | policy بيقبل `feature/a.lock` و`feature/a.` واسم آخره newline رغم رفض Git ليهم | رفض كل أسماء Git غير الصالحة وإضافة regression |
| QAD-008 | Medium | SQL بيقبل version كله tab/newline/Unicode whitespace؛ الأربع اختبارات بتنتظر exception ومش بيحصل | توحيد قواعد blank بين bean validation وقيد DB من غير فقد بيانات |
| QAD-009 | Medium | GET حرفي إلى `//actuator/health` بيرجع400 من غير هيدرز الأمان والكاش المطلوبة في AC4 | تغطية مسار firewall أو حسم الاستثناء صراحة في عقد القبول |
| QAD-010 | Low | `/actuator/info` بيعرض artifact/group/time بالإضافة للاسم والإصدار، خلاف proposed AC2 | allowlist دقيقة مع اختبار exact keys أو تعديل معتمد للعقد |
| QAD-011 | Medium | anonymous POST بيرجع403 للـCSRF؛ proposed AC3 بيقول401 بينما اختبار المطوّر بيقبل الاتنين | حسم precedence وكود الاستجابة ثم اختبار دقيق؛ مفيش bypass مثبت |

تفاصيل البيانات والخطوات والـ expected/actual والـ assertion IDs موجودة في `qa/defects/pr-2-findings.json`؛ الاستجابات الفعلية في evidence JSON. الكوكيز والـ generated-password اتنقحوا قبل حفظ الأدلة.

خطوات التنفيذ القابلة للإعادة، ومسارات QA overlays، وأوامر Maven/HTTP/React في `qa/independent/PHASE-1-EXECUTION.md`.

## قبول المرحلة الأولى وTDD

اتراجع proposed PHASE-1-ACCEPTANCE مع المعمارية والتنفيذ، واتضافت `qa/specifications/phase-1-cases.json`: 48 حالة بأرقامها الأصلية، منهم refinement عملي لـLD-001/002/003/004/005. LD-006 لسه تخطيط مش تنفيذ. كل حالة فيها preconditions/data/steps/expected/priority/traceability، والقرارات غير المعتمدة ظاهرة كـ blockers.

عدد القصص الأصلية **42**؛ عدد قصص المرحلة الأولى **6**؛ عدد حالاتها **48**. عدد الـ assertions المستقلة منفصل تمامًا.

الـ RED/GREEN/REFACTOR logs اتراجعت للقصص الموجودة. فيها أوامر وتوقيت وفشل/نجاح فعلي مسجل، والـ GREEN الحالي اتعاد تشغيله. لكن الـ RED trees مش مثبتة بـ commit/patch hashes مستقلة في كل الحالات، فترتيب TDD التاريخي مش قابل لإعادة التشغيل بالكامل من git وحده. دي حدود الدليل، مش اتهام بتنفيذ feature قبل test.

معيار SQL whitespace والـ branch validity اتكتبوا كاختبارات QA سلبية، واتسابوا RED علشان يتصلحوا في مساحة التنفيذ. كود المنتج ما اتعدلش. اختبارات redaction لأداة QA نفسها اتسجل لها RED ثم GREEN.

فحوص CI الخمسة ظهرت ناجحة على SHA سابق أثناء المراجعة، واتوثقت حالة الـhead والـchecks في discovery evidence. نجاح CI للمطوّر مش إغلاق لعيوب الاختبارات المستقلة ولا إثبات إن branch protection شغال. B4 وB5 وموانع النشر لسه لازم تتعالج قبل exit gate.

## الفجوات الباقية

الفجوات الستة الأصلية **QAD-001..QAD-006 كلها OPEN**؛ السجل المختصر `qa/reports/baseline-open-findings.json` بيربطها بالسجل الأصلي من غير حذفه. عقود المرحلة الأولى وCI تقدم جزئي، لكن قصص باقي المراحل لسه عامة.

لسه BLOCKED: branch protection/منع self approval، اعتماد scope ومعايير القبول، roles/IDOR/session fixtures لمرحلة2، LD-006 والتشغيل والـrollback الفعلي، browser/Playwright، اختبار UI مع backend حقيقي بدل controlled transport، ومراجعة كل السيناريوهات الكاملة. مفيش ادعاء إن jsdom بديل browser أو إن membership في health group يثبت سلوك outage فعلي.

لا approval ولا merge ولا deployment اتعملوا. النسخة الجديدة تتراجع وتتختبر عند SHA جديد بعد إصلاح العيوب؛ مفيش اعتماد تلقائي من نتائج SHA قديم.

سجل `product-case-ledger.json` بيسجل QA-0003 وQA-0026 كـFAIL من دليل سلبي مثبت، وباقي الـ349 BLOCKED. عدم اكتمال العقود مش سبب لإخفاء عيب مثبت كنجاح أو عدم تنفيذ.

آخر head اتلاحظ وقت إغلاق المراجعة: `c0da6910884a7e85ee17e80a78416aac51de216c`. أحدث من الـSHA المختبر؛ النسخة الأحدث BLOCKED لحد إعادة الاختبارات، ومفيش approval ليها.
