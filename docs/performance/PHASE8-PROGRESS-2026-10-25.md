# LawDesk Phase 8 — تقدم قياسات الأداء، 25 أكتوبر 2026

## الحالة

**Phase 8 لسه مش PASS.** عينة UAT عامة اتقاست فعلاً وبمعدل محدود، واتعملت عينة browser لصفحة الدخول العامة، وتجهيزات الاختبار المعزول مكتوبة ومراجَعة تركيبياً. الحمل وstress/spike/soak وقياسات API الموثقة بالجلسة والـ DB وWeb Vitals للصفحات بعد الدخول لسه **NOT_RUN**. لا حصل تشغيل حمل على UAT ولا اتصال بقاعدة بيانات Matbakhy أو Caddy المشترك.

الـ baseline SHA اللي المالك حدده: `714d2f3b0c3ed7239b85c79966426d5fe27d2d1d`. إعداد Phase 8 بدأ على `aed1f7d68597cf1da5da9d8aa658d1b6036960bd`، وبعد مراجعة GitHub اتنقل فرع الأدلة فوق `develop` الحالي `1771bded862a49b9e3015eb76115a981944954c8`. الـ UAT كان اتسجل على `aed1f7d` في QA سابقة، لكن **SHA المنشور وقت العينة الحالية مش متحقق**: `GET /api/v1/release` رد 401 من غير جلسة. بالتالي أرقام UAT دي ما تتقارنش بأرقام SHA تاني كأنها regression مؤكد.

## عينة UAT الفعلية

المصدر الخام: [PHASE8-UAT-PUBLIC-SAMPLE.json](PHASE8-UAT-PUBLIC-SAMPLE.json). التنفيذ الساعة `17:09:51 UTC`. السكربت عمل 28 GET متسلسل، بينهم ثانية على الأقل، منها warm-up واحد لكل مسار و8 قياسات لكل مسار. كل الـ 24 قراءة المقاسة رجعت 200؛ خطأ HTTP مرصود 0%. معدل الطلبات المعروض حد أعلى تقريبي 1 request/s بالتنظيم المتعمد، **مش قدرة تحمل السيرفر**. زمن تنفيذ الأداة حوالي 27.06 ثانية، يعني حوالي 1.03 request/s شاملاً warm-up، وحجم المحتوى الكلي للـ 28 استجابة 5.04 MiB. القياس يشمل الشبكة ونقل المحتوى من بيئة الاختبار؛ مش زمن المعالجة داخل السيرفر.

| مسار عام | n | p50 | p95 | p99 | حجم الاستجابة |
|---|---:|---:|---:|---:|---:|
| `/` | 8 | 20.95 ms | 31.84 ms | 35.78 ms | 473 B |
| JS asset | 8 | 24.23 ms | 58.89 ms | 69.38 ms | 535,915 B |
| CSS asset | 8 | 20.87 ms | 38.28 ms | 42.15 ms | 50,679 B |

الـ p99 هنا شديد التقلب عشان 8 عينات فقط. حجم الاستجابة هو body المقروء من Python client، من غير إعداد browser throttling أو cache. الصفحة والـ assets في الجدول ما يقيسوش LCP/CLS/INP أو تفاعل React.

عينة المتصفح [PHASE8-UAT-BROWSER-SAMPLE.json](PHASE8-UAT-BROWSER-SAMPLE.json) اتنفذت الساعة `17:37:29 UTC` بـ Chromium 156، أربع زيارات GET فقط للصفحة العامة من غير credentials أو CPU/network throttling. أول زيارة desktop: TTFB `370 ms`، FCP `1196 ms`، LCP `1528 ms`، CLS `0.0117`، و2 long tasks مجموعهم `365 ms`. أول زيارة mobile 375px: TTFB `54 ms`، FCP `264 ms`، LCP `412 ms`، CLS `0`. دي عينات مفردة، وINP ما اتقاسش. كان فيه محاولة أقدم فشلت قبل أي request بسبب مكتبات Chromium الناقصة؛ [دليلها](PHASE8-UAT-BROWSER-ATTEMPT.json). المكتبات اتوفرت محلياً في `/tmp` فقط واتنفذت العينة بعدها.

## تغطية البنود المطلوبة

| البند | الوضع الحالي | المطلوب للتأكيد |
|---|---|---|
| Login وsession validation | PREPARED / NOT_RUN | حسابات `perf.*` معزولة، warm-up وk6 |
| Cases list/search/filter/details، parties وproceedings | PREPARED / NOT_RUN | تشغيل k6 على dataset ثابت |
| Hearings وtasks، scheduling وmanagement | PREPARED / NOT_RUN | تشغيل k6 ومراجعة نسب نجاح كل route |
| Document metadata، PDF upload/download مصرح به | PREPARED / NOT_RUN | تشغيل k6 وقياس أحجام ونقل الملف |
| Dashboard؛ case/judgment/hearing/expert/workload reports | PREPARED / NOT_RUN | تشغيل k6، فحص latency per route |
| CSV/XLSX/PDF exports | PREPARED / NOT_RUN | تغطية كل تقرير وكل format، وقياس حجم الاستجابة |
| PostgreSQL plans/slow queries | HISTORICAL ONLY | إعادة `EXPLAIN ANALYZE` و`pg_stat_statements` على SHA الحالي والبيانات الصناعية |
| CPU/RAM/DB connections/Hikari | PREPARED / NOT_RUN | Docker/PG snapshots؛ Hikari pending يحتاج instrumentation معزول |
| Frontend load/responsiveness/Core Web Vitals | عينة public login shell LCP/CLS/TTFB؛ INP والصفحات الموثقة NOT_RUN | Lighthouse/Chrome trace على النسخة المعزولة بـ browser وإعدادات ثابتة |
| Baseline/load/ramp/stress/spike/recovery/soak | PREPARED / NOT_RUN | بيئة منفصلة مثبت عزلها وDocker شغال |

الأدلة القديمة في `PERFORMANCE-BASELINE.md` تخص `19ad538` وصفحات/جداول من PRs أقدم؛ وعينة loopback السابقة في `qa/evidence/phase56-acceptance-20261025/performance/performance-baseline.json` تخص `b849b1c` و20 قضية فقط. ما اعتبرتهمش قياس Phase 8 ولا دليل PASS.

## اللي اتجهز واتراجع

- [PHASE8-RUNBOOK.md](PHASE8-RUNBOOK.md): gate العزل، dataset، الأوامر، profiles الافتراضية، حدود الموارد، شروط التوقف وتجميع الأدلة.
- `tools/performance/isolated/compose.yaml` مع `compose.shared.yaml`: PostgreSQL volume منفصل، شبكة `internal` واحدة، loopback web فقط، CPU/RAM/PID caps؛ shared override بيقلل السقف وبيستخدم Docker الـ rootless الموجود بدل host Docker.
- `tools/performance/isolated/accounts.py`: كلمات سر وحسابات `perf.*` صناعية ومنفصلة، من غير طباعتها.
- `tools/performance/isolated/guarded_run.py`: gate للـ rootless daemon وcgroup وSHA وأماكن الـ mounts والشبكة وحدود التشغيل؛ بيراقب host وHTTP وDocker وDB كل 10 ثواني وبيوقف k6 عند خرق الحدود. الـ rootless daemon ما يقدرش يشوف حاويات Matbakhy على host Docker؛ المراقبة هنا على موارد المضيف وحاويات rootless المجاورة، من غير إرسال طلبات لـ Matbakhy.
- `tools/performance/k6/api-baseline.js`: baseline موثّق محدود بـ VU واحد و15 دورة على 20 قضية صناعية؛ بيقيس login/session والقضايا والجلسات والمهام والتقارير والصادرات بعد seed محدود. ده **PREPARED / NOT_RUN** لحد ما Stack معزول يشتغل فعلاً.
- `tools/performance/uat_public_browser.mjs`: أربع زيارات عامة، GET فقط، لقياس FCP/LCP/CLS/TTFB desktop/mobile. محاولة أولى اتوقفت **قبل أي request** بسبب مكتبات Chromium ناقصة؛ بعدها اتنزّلت 11 مكتبة Ubuntu إلى `/tmp` واتنفذت عينة public فعلية. الصفحات الموثقة وINP لسه NOT_RUN.
- `tools/performance/isolated_browser.mjs`: قياس browser بعد الدخول على stack معزول فقط لصفحات dashboard/cases/details/hearings/tasks/reports. السكربت متراجع syntax لكن **NOT_RUN**؛ التفاعل الصناعي فيه مش بديل INP الميداني.
- `tools/performance/k6/lawdesk-mix.js`: توسعت لتشمل فلاتر القضايا والمهام والتقارير الخمسة وصيغ التصدير الثلاثة ورفع/تنزيل PDF صناعي صالح؛ `export-matrix.js` بيطلب كل الـ 15 تركيبة تصدير وبيقيس حجمها؛ `lib/lawdesk.js` بيقصر target على web المعزول أو loopback.
- `tools/performance/db/seed-phase5.sql`: تاريخ الجلسة الصناعية اتظبط على تاريخ رفع الدعوى عشان ما يخالفش trigger V406؛ `seed-phase8.sql` بيضيف خبراء وندب صناعي متوافق مع V407.
- اتعمل `docker compose config --quiet` بالـ load profile، و`node --check` لملفات k6 المعدلة، و`py_compile` لسكربتات Python، و`file` أكد إن PDF fixture صفحة واحدة. كمان [PHASE8-K6-INSPECT.json](PHASE8-K6-INSPECT.json) بيثبت إن k6 v2.3.0 قرأ profiles السبعة وmatrix بنجاح ورفض UAT وMatbakhy وtarget خارجي وغياب تأكيد العزل. checksum الـ binary اتطابق مع baseline القديم. **inspect ما بيبعتش requests**، فده مش تنفيذ سيناريوهات.

## العائق الحالي

Docker socket المحلي رد `permission denied` حتى في فحص `docker info` read-only مع تحديد `/var/run/docker.sock`. مستخدم التنفيذ UID 1002 ومفيش صلاحية socket. توثيق CI بيبين إن الـ rootless daemon تحت `lawdesk-ci` وسوكته الخاصة `/run/user/<uid>/docker.sock`، منفصل عن Docker بتاع `devops`/الـ host. لا عندي صلاحية `lawdesk-ci` ولا SSH key له هنا. k6 اتوفر لفحص `inspect` فقط؛ Java/PostgreSQL runtime والتشغيل المعزول مش متاحين في الجلسة. طلبت فحص read-only من المشغّل عشان أقدر أثبت العزل وحدود cgroup قبل الـ baseline. لذلك **authenticated API/DB وload ما اتنفذوش لسه**. [Draft PR #60](https://github.com/afawzy70/Lawdesk-v1/pull/60) مفتوحة على `develop`؛ فحوص GitHub Actions الحالية ظهرت `SKIPPED`، ومش دليل نجاح.

## Backlog الأداء للتحقق، مش عيوب مؤكدة حالياً

1. إعادة قياس report inventory وjudgment queries على schema وdataset الحاليين؛ baseline القديم لمح إلى تجميع كبير وsort/join مكلف، لكن الكود اتغير بعدها.
2. قياس N+1 في hearing calendar بعد آخر merges؛ سجل baseline القديم أشار لكلفة حتى ~200 lookup في صفحة 100 جلسة.
3. قياس ضغط Hikari والـ JVM heap عند الصادرات PDF/XLSX والملفات 10 MiB. الحالة الحالية لا تعرض pending/GC metrics، وده نقص دليل لازم يتعالج في stack معزول فقط.
4. ضبط حجم السيناريوهات والحدود بعد بيانات استخدام حقيقية، من غير افتراض أرقام production.

مفيش bottleneck جديد مؤكد على SHA الحالي لسه، فمفيش remediation handoff لClaude يتقال إنه مؤكد. بعد تشغيل البيئة المعزولة، كل finding يتبعت له مع query plan، SHA، dataset، p50/p95/p99، RPS، أخطاء، CPU/RAM واتصالات DB، وبعد إصلاحه يتعاد قياسه باستقلالية. الاختبارات الوظيفية والأمنية هتفضل بنفس قوتها.
