# مراجعة فجوات QA المستقلة — 2026-10-23

القرار الحالي: **BLOCKED** لقبول المنتج. دي مراجعة للـ baseline وتصميم الاختبارات، مش نتيجة اختبار لتطبيق شغال.

الـ commit اللي اتراجع: `8ff061cf59c1bdd2e2ef41c2cc6fbc53acc0af4d`، وهو نفس `origin/develop` وقت الفحص.
اتقرت المستندات الخمسة المطلوبة كاملة بالبرمجة، مع فحص النصوص والقوائم. بصماتها في `qa/evidence/baseline-context.json`.
فيه 42 قصة، 126 معيار قبول، و351 سيناريو: 336 = 8 لكل قصة، و15 اختبار أمني مشترك.
كل القصص حالتها `DRAFT_NEEDS_REFINEMENT`، والـ README بيسمي الـ baseline draft. موافقة خطة التنفيذ مش معناها إن تفاصيل كل معيار قبول اتحسمت.

مافيش backend أو frontend أو migrations أو build manifests في الـ SHA ده. مافيش PRs تنفيذ مفتوحة لـ develop وقت الاستعلام.
مش ممكن التحقق من Spring Security أو PostgreSQL أو React أو المتصفح في الحالة دي.

## نتائج التنفيذ والأدلة

- اختبارات أدوات QA: 6 PASS، 0 FAIL. النتيجة دي تخص validator وHTTP assertion safeguards فقط.
- المنتج: 0 PASS، 0 FAIL، **351 BLOCKED**، 0 NOT_RUN في سجل التقييم الحالي.
- ملفات React/Playwright: NOT_RUN؛ مفيش toolchain أو تطبيق أو fixtures معتمدة. الحالات المرتبطة بيها ضمن الـ351 BLOCKED، مش عدد حالات إضافي.
- RED: اختبارين فشلوا لأن `catalog.py` مش موجود. GREEN: نفس الاختبارين نجحوا بعد إضافة validator، وبعدها 4 اختبارات حماية للأداة نجحوا. ده دليل TDD لأداة QA فقط، مش دليل TDD لـ Claude.
- أوامر الإعادة: `python3 -m unittest discover -s qa/independent/tests -v` ثم `python3 qa/independent/catalog.py` ثم `python3 qa/independent/run.py --output /tmp/lawdesk-results.json`.
- الأمر الأخير لازم يرجع exit 2 طول ما الحالات محجوبة. `qa/evidence/product-results.json` بيسجل SHA الفعلي ووجود تغييرات محلية. ملفات الأدلة اتولدت فوق baseline مع تغييرات QA غير committed؛ مش بننسب تشغيل QA لكود تطبيق مش موجود.
- البيئة: Linux وPython موثقين في context؛ Java مش على PATH. Node وDocker متاحين كأوامر، لكن مفيش خدمات اتشغلت أو قواعد بيانات اتعدلت.

## العيوب والفجوات المثبتة في المستندات

| ID | الأولوية | النتيجة وتأثيرها |
|---|---|---|
| QAD-001 | High | الـ42 قصة بتستخدم نفس 3 معايير عامة مع تبديل العنوان؛ مفيش نتائج قابلة للحسم أو عقود حقول/أخطاء. قبول التنفيذ محجوب. |
| QAD-002 | High | الـ351 سيناريو الأصلية مفيهاش priority أو test_data؛ steps وexpected نصوص عامة. كتابة 351 سيناريو مش تغطية تنفيذية. |
| QAD-003 | High | مصفوفة endpoint/permission/record-scope وقرارات session/CSRF غير محددة؛ اختبارات 401/403 وIDOR/revocation محتاجة عقد. |
| QAD-004 | High | قواعد التسلسل القضائي والمواعيد والحالات والـ timezone مش محددة؛ مينفعش QA يخترع قاعدة قانونية من عنوان قصة. |
| QAD-005 | Medium | 15 سيناريو story_id بتاعهم CROSS-CUTTING بدل story حقيقي؛ الاختبار reset موجود من غير قصة reset صريحة، وفيه سيناريوهات API/UI عامة لقصص بنية تحتية. |
| QAD-006 | High | خطة staging on merge after QA محتاجة ترتيب عملي: تشغيل QA قبل الدمج في بيئة معزولة، وبعده تحقق staging لنفس artifact. مفيش تعريف artifact أو عقود evidence/thresholds حاليًا. |

دي عيوب baseline وتصميم، مش ثغرات مؤكدة في تطبيق. تفاصيل إعادة إنتاج كل نتيجة في `qa/defects/baseline-findings.json`.

## مراجعة كل القصص وحدودها

كل صف تحته 8 حالات محفوظة بالـ IDs الأصلية في `qa/specifications/refined-cases.json`.
عمود القرارات محتاج حسم في story contract، والعمود الأخير يوضح اختبار مخاطر ناقص في baseline الأصلي.
كل تصميم حالته PROPOSED_REQUIRES_CONTRACT؛ خطواته مش قابلة للتنفيذ النهائي قبل ربط المتغيرات بعقد معتمد.

| القصة | قرارات/معايير قبول ناقصة | حدود وتزامن لازم يتغطوا |
|---|---|---|
| LD-001 | Branch protection, reviewer count, required check names and QA branch exception | Two PRs with identical head versus stale head approval |
| LD-002 | Liveness/readiness routes, public exposure, status codes, startup timeout | Repeated health requests during dependency outage and recovery |
| LD-003 | Route inventory, authentication redirects, error copy and browser matrix | Double navigation and late response after route change |
| LD-004 | Migration engine/version, schema constraints and supported upgrade paths | Run migrations twice and start two migrators concurrently |
| LD-005 | Required CI job names, coverage thresholds, artifact retention and QA ordering | Two rapid pushes must deploy only verified SHA |
| LD-006 | Hostname, deployment owner, readiness and rollback procedure | Restart LawDesk in dedicated disposable environment while routing stays isolated |
| LD-007 | Login/logout routes, payload, cookie policy, expiry and error statuses | Repeated login/logout and session expiry at exact threshold |
| LD-008 | Hash algorithm/work factor, length policy, reset ownership and lockout limits | Password at min-1/min/max/max+1; concurrent failed attempts |
| LD-009 | Role/permission matrix, grant revocation latency and last-admin safeguard | Concurrent grant and revoke; stale admin session |
| LD-010 | Endpoint permission matrix and 401/403/404 policy | Alternate methods, encoded IDs, nested resources and batch endpoints |
| LD-011 | Auditable events, retention, clock, tamper controls and audit-read permission | Retry same operation; audit sink failure; forged newline in actor label |
| LD-012 | Required fields, lengths, unique key scope, duplicate policy and ownership model | Duplicate reference, Unicode normalization and concurrent creates |
| LD-013 | Mutable fields, optimistic locking, validation and audit rules | Stale version, omitted field versus null, max-length+1 |
| LD-014 | Timeline ordering/tie breaker, pagination and redaction | Empty timeline, deleted related reference and simultaneous events |
| LD-015 | Search normalization, filter semantics, sort defaults and page size limits | Page 0/-1/max+1; empty query; wildcard and large result set |
| LD-016 | Party roles, cardinality, uniqueness, deletion and privacy rules | Duplicate association, self-link and concurrent removal |
| LD-017 | Exact states, transition matrix, reopen permission and side effects | Concurrent close/reopen; repeat terminal transition |
| LD-018 | Court hierarchy, uniqueness, localization and reference edit permissions | Duplicate normalized name and inactive reference on new proceeding |
| LD-019 | Docket uniqueness, required dates, court compatibility and multiplicity | Duplicate docket and invalid date ordering |
| LD-020 | Appeal eligibility, filing window, calendar rules and multiple appeals | At deadline minus one/exact/plus one; concurrent appeals |
| LD-021 | Cassation prerequisites, court rules, deadlines and outcomes | Missing appeal, duplicate cassation and reversed decision |
| LD-022 | Outcome enum, finality, amendment authority and date restrictions | Judgment before hearing; repeated submission and simultaneous amendment |
| LD-023 | Special decision types, precedence, effective dates and permission | Start/end equality, overlapping suspensions and timezone boundary |
| LD-024 | Court timezone, conflict policy, status and reschedule history | DST gap/fold, past date, concurrent slot booking |
| LD-025 | Expert availability, referral lifecycle, report attachment and due date rules | Inactive expert, duplicate session and concurrent assignment |
| LD-026 | Per-file byte limit, PDF validation/scanning, duplicate and delete semantics | Two concurrent uploads when nine files exist must never produce eleven |
| LD-027 | Task state machine, assignee scope, deletion and notification policy | Two assignees race; overdue equality; repeat completion |
| LD-028 | Timezone, reminder offsets, retry/idempotency, delivery channel and holidays | DST, leap day, due exactly now and retry after worker crash |
| LD-029 | Note visibility, edit/delete windows, retention and rich-text policy | Empty/whitespace/max+1 note and concurrent edits |
| LD-030 | Metric definitions, refresh policy, timezone and zero-state behavior | Empty data, stale cache after permission change and large counts |
| LD-031 | Inventory columns, status/date semantics, ordering and row limits | Zero rows, large data and filter boundary equality |
| LD-032 | Inclusive/exclusive range, selected date field and amendment counting | Midnight/DST; start after end; same-day range |
| LD-033 | Workload formula, inactive experts, unassigned items and date rules | Zero denominator, duplicate session and reassignment during report |
| LD-034 | Supported formats, schema, encoding, size limits and async expiry | Empty export, max rows+1, concurrent exports and revoked download |
| LD-035 | Locale persistence/fallback, date calendar and Arabic digit normalization | Missing translation and switching while request is in flight |
| LD-036 | Supported breakpoints, browsers, table overflow and bidi conventions | 200%/400% zoom; long unbroken text and direction switch |
| LD-037 | Accessibility conformance target, contrast and assisted-tech matrix | Focus trap, dynamic errors, reduced motion and duplicate accessible names |
| LD-038 | Cross-story fixtures, supported legal paths and recovery checkpoints | Failure mid-journey, retry submission, permission revoked in open browser |
| LD-039 | Coverage definition, evidence freshness and acceptable unresolved severity | Missing/orphan IDs, stale SHA, unknown status and empty suite |
| LD-040 | Threat model, security severity rubric and complete route inventory | Alternate HTTP methods, stale sessions, batch access and error leakage |
| LD-041 | RPO/RTO, backup keys, backward schema compatibility and authorized operators | Corrupt backup, interrupted restore, wrong key and failed migration |
| LD-042 | Release owner, signoff process, artifact provenance and support/runbook scope | Stale candidate, missing artifact and last-minute critical defect |

## مخاطر عابرة للقصص

- السرية: تحديد نطاق المستخدم للقضايا والتقارير والتصدير والمرفقات والروابط المباشرة؛ إخفاء زر مش تفويض.
- سلامة البيانات: FK/unique/check وعمليات atomic، stale updates، upload races، retries بعد timeout، ومخلفات التخزين بعد رفض PDF.
- الملفات: حد عشرة مرفقات مؤكد من العنوان؛ حجم الملف، التوقيع الفعلي، المحتوى النشط والفحص محتاجين قرار. لازم 9→10 و10→11 ورفع متزامن عند 9.
- التقارير: تعريف المقاييس، الفلاتر الشاملة، pagination المستقرة، Unicode، CSV formula injection، وانتهاء روابط التحميل.
- التشغيل: فشل migration، upgrade ببيانات قديمة، backup restore في DB معزولة، RPO/RTO، وrollback متوافق مع schema.
- تجربة المستخدم: RTL/LTR، keyboard/focus، أخطاء مرتبطة بالحقل، قارئ شاشة، وتغيير اللغة أثناء الطلب. مستوى accessibility والمصفوفة مدخلات غير محسومة.
- الحدود: لكل حد معتمد L نحتاج L-1/L/L+1؛ لكل موعد T نحتاج T-epsilon/T/T+epsilon مع clock متحكم فيه. من غير L/T معتمدين الاختبار يفضل BLOCKED.
- الأدلة: اختبارات أمن فعلية تبدأ من مرحلة 2 مش تستنى مرحلة 8؛ مرحلة 8 إعادة تحقق شاملة. ترتيب exit gates لازم يطابق ده.

## اللي اتسلّم واللي لسه محجوب

اتحافظ على الملفات الأصلية من غير تغيير، واتضافت مواصفات مقترحة لكل الـ351 حالة، وتتبع للـ15 حالة المشتركة، وأولوية وبيانات وخطوات ونتائج ودليل مطلوب لكل حالة.
اتضاف runner فعلي لـ API/SQL بعقود صريحة، واختبارات لأداة QA نفسها، ومسودات اختبارات React/Playwright.
الـ runner مش بديل لاختبارات transactional/concurrency ولا بيعتبر probe جزئي نجاحًا لحالة كاملة.

مفيش اعتماد لأي تنفيذ، ولا merge أو deploy، ولا تغيير في workspace بتاع Claude أو Matbakhy أو الـ proxy المشترك.
مراجعة PRs مستمرة في كل تشغيل لاحق، لكن مفيش مراقبة خلفية اتعملت. قالب المراجعة موجود جنب التقرير.
أي PR جديد لازم يتراجع عند head SHA محدد مع RED/GREEN/REFACTOR وأدلة مستقلة قبل التوصية.
