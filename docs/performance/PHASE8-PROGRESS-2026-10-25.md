# LawDesk Phase 8 — تقدم قياسات الأداء، 25 أكتوبر 2026

## الحالة

**Phase 8 لسه مش PASS.** عينة UAT عامة اتقاست فعلاً وبمعدل محدود، وتجهيزات الاختبار المعزول مكتوبة ومراجَعة تركيبياً. الحمل وstress/spike/soak وقياسات API الموثقة بالجلسة والـ DB والـ Web Vitals لسه **NOT_RUN**. لا حصل تشغيل حمل على UAT ولا اتصال بقاعدة بيانات Matbakhy أو Caddy المشترك.

الـ baseline SHA اللي المالك حدده: `714d2f3b0c3ed7239b85c79966426d5fe27d2d1d`. إعداد Phase 8 بدأ على `aed1f7d68597cf1da5da9d8aa658d1b6036960bd`، وبعد مراجعة GitHub اتنقل فرع الأدلة فوق `develop` الحالي `1771bded862a49b9e3015eb76115a981944954c8`. الـ UAT كان اتسجل على `aed1f7d` في QA سابقة، لكن **SHA المنشور وقت العينة الحالية مش متحقق**: `GET /api/v1/release` رد 401 من غير جلسة. بالتالي أرقام UAT دي ما تتقارنش بأرقام SHA تاني كأنها regression مؤكد.

## عينة UAT الفعلية

المصدر الخام: [PHASE8-UAT-PUBLIC-SAMPLE.json](PHASE8-UAT-PUBLIC-SAMPLE.json). التنفيذ الساعة `17:09:51 UTC`. السكربت عمل 28 GET متسلسل، بينهم ثانية على الأقل، منها warm-up واحد لكل مسار و8 قياسات لكل مسار. كل الـ 24 قراءة المقاسة رجعت 200؛ خطأ HTTP مرصود 0%. معدل الطلبات المعروض حد أعلى تقريبي 1 request/s بالتنظيم المتعمد، **مش قدرة تحمل السيرفر**. زمن تنفيذ الأداة حوالي 27.06 ثانية، يعني حوالي 1.03 request/s شاملاً warm-up، وحجم المحتوى الكلي للـ 28 استجابة 5.04 MiB. القياس يشمل الشبكة ونقل المحتوى من بيئة الاختبار؛ مش زمن المعالجة داخل السيرفر.

| مسار عام | n | p50 | p95 | p99 | حجم الاستجابة |
|---|---:|---:|---:|---:|---:|
| `/` | 8 | 20.95 ms | 31.84 ms | 35.78 ms | 473 B |
| JS asset | 8 | 24.23 ms | 58.89 ms | 69.38 ms | 535,915 B |
| CSS asset | 8 | 20.87 ms | 38.28 ms | 42.15 ms | 50,679 B |

الـ p99 هنا شديد التقلب عشان 8 عينات فقط. حجم الاستجابة هو body المقروء من Python client، من غير إعداد browser throttling أو cache. الصفحة والـ assets ما يقيسوش LCP/CLS/INP أو تفاعل React.

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
| Frontend load/responsiveness/Core Web Vitals | PUBLIC TRANSFER SAMPLE ONLY؛ browser BLOCKED | Lighthouse/Chrome trace على النسخة المعزولة بـ browser وإعدادات ثابتة |
| Baseline/load/ramp/stress/spike/recovery/soak | PREPARED / NOT_RUN | بيئة منفصلة مثبت عزلها وDocker شغال |

الأدلة القديمة في `PERFORMANCE-BASELINE.md` تخص `19ad538` وصفحات/جداول من PRs أقدم؛ وعينة loopback السابقة في `qa/evidence/phase56-acceptance-20261025/performance/performance-baseline.json` تخص `b849b1c` و20 قضية فقط. ما اعتبرتهمش قياس Phase 8 ولا دليل PASS.

## اللي اتجهز واتراجع

- [PHASE8-RUNBOOK.md](PHASE8-RUNBOOK.md): gate العزل، dataset، الأوامر، profiles الافتراضية، حدود الموارد، شروط التوقف وتجميع الأدلة.
- `tools/performance/isolated/compose.yaml`: PostgreSQL volume منفصل، شبكة `internal` واحدة، loopback web فقط، CPU/RAM/PID caps، k6 container مقيد.
- `tools/performance/isolated/accounts.py`: كلمات سر وحسابات `perf.*` صناعية ومنفصلة، من غير طباعتها.
- `tools/performance/isolated/guarded_run.py`: يتحقق من Compose وSHA قبل التشغيل ويراقب host وHTTP وDocker وDB كل 10 ثواني.
- `tools/performance/uat_public_browser.mjs`: أربع زيارات عامة، GET فقط، لقياس FCP/LCP/CLS/TTFB desktop/mobile. محاولة تشغيله اتوقفت **قبل أي request** لأن Chromium المحلي ناقص `libatk-1.0.so.0` ومكتبات تانية؛ [الدليل](PHASE8-UAT-BROWSER-ATTEMPT.json). Core Web Vitals لسه NOT_RUN.
- `tools/performance/k6/lawdesk-mix.js`: توسعت لتشمل فلاتر القضايا والمهام والتقارير الخمسة وصيغ التصدير الثلاثة ورفع/تنزيل PDF صناعي صالح؛ `export-matrix.js` بيطلب كل الـ 15 تركيبة تصدير وبيقيس حجمها؛ `lib/lawdesk.js` بيقصر target على web المعزول أو loopback.
- `tools/performance/db/seed-phase5.sql`: تاريخ الجلسة الصناعية اتظبط على تاريخ رفع الدعوى عشان ما يخالفش trigger V406؛ `seed-phase8.sql` بيضيف خبراء وندب صناعي متوافق مع V407.
- اتعمل `docker compose config --quiet` بالـ load profile، و`node --check` لملفات k6 المعدلة، و`py_compile` لسكربتات Python، و`file` أكد إن PDF fixture صفحة واحدة. كمان [PHASE8-K6-INSPECT.json](PHASE8-K6-INSPECT.json) بيثبت إن k6 v2.3.0 قرأ profiles السبعة وmatrix بنجاح ورفض UAT وMatbakhy وtarget خارجي وغياب تأكيد العزل. checksum الـ binary اتطابق مع baseline القديم. **inspect ما بيبعتش requests**، فده مش تنفيذ سيناريوهات.

## العائق الحالي

Docker socket المحلي رد `permission denied` حتى في فحص `docker info` read-only مع تحديد `/var/run/docker.sock`. مستخدم التنفيذ UID 1002 ومفيش صلاحية socket. مفيش k6 أو Java/PostgreSQL runtime قابل للتشغيل في البيئة الحالية، وبيانات اعتماد QA UAT مش متاحة في متغيرات الجلسة المجهزة. لذلك ما قدرتش أثبت عزل سيرفر Contabo ولا أنفذ حمل آمن. `gh auth status` كمان أبلغ إن token الحالي غير صالح؛ branch الأدلة جاهز محلياً لكن PR لسه ما اتفتحش.

## Backlog الأداء للتحقق، مش عيوب مؤكدة حالياً

1. إعادة قياس report inventory وjudgment queries على schema وdataset الحاليين؛ baseline القديم لمح إلى تجميع كبير وsort/join مكلف، لكن الكود اتغير بعدها.
2. قياس N+1 في hearing calendar بعد آخر merges؛ سجل baseline القديم أشار لكلفة حتى ~200 lookup في صفحة 100 جلسة.
3. قياس ضغط Hikari والـ JVM heap عند الصادرات PDF/XLSX والملفات 10 MiB. الحالة الحالية لا تعرض pending/GC metrics، وده نقص دليل لازم يتعالج في stack معزول فقط.
4. ضبط حجم السيناريوهات والحدود بعد بيانات استخدام حقيقية، من غير افتراض أرقام production.

مفيش bottleneck جديد مؤكد على SHA الحالي لسه، فمفيش remediation handoff لClaude يتقال إنه مؤكد. بعد تشغيل البيئة المعزولة، كل finding يتبعت له مع query plan، SHA، dataset، p50/p95/p99، RPS، أخطاء، CPU/RAM واتصالات DB، وبعد إصلاحه يتعاد قياسه باستقلالية. الاختبارات الوظيفية والأمنية هتفضل بنفس قوتها.
