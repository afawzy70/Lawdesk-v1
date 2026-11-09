# LD-DOC-08 — دليل التنفيذ لحزم العمل (للمطورين والوكلاء)

مستخلص من تنفيذ WP1 (LD-050). التزم به في كل حزمة؛ ما هو ليس هنا يُقرَّر بالتشابه مع شيفرة الحزم المجاورة.

## البيئة والتشغيل
- JDK 21: `export JAVA_HOME=/home/aiagent/tools/jdk-21.0.12.1+1 PATH=$JAVA_HOME/bin:$PATH`. اختبارات الخلفية: `cd backend && ./mvnw -o test -Dtest='ClassA,ClassB' > log 2>&1` ثم استخرج `Tests run`/`[ERROR]   ` من السجل (الإخراج ضخم، لا تطبعه). لا تشغّل المجموعة كاملة محليًا (بطيئة)؛ تشغّلها CI (~5 دقائق).
- الواجهة: `cd frontend && npx tsc -p tsconfig.app.json --noEmit && npx eslint src && npx vitest run --coverage` (بوابة فروع 90%، والاختبارات تدخل في فحص الأنواع؛ لا `!` ولا فهارس غير محمية). الاختبارات تتأخر تحت الحمل: `asyncUtilTimeout` 4 ثوانٍ.
- لا تستخدم `pkill`. الخلفية الطويلة: `run_in_background`.

## الاتفاقيات
- ترحيلات Flyway إضافية فقط، اسمها `V6xx__LD-05n_<what>.sql` (نطاق `tools/contracts.py`: V600–V999 للقصص LD-050..059). WP1: LD-050 (V600–V602 مستخدمة). WP2: LD-051 من V620، WP7: LD-052 من V640، WP3: LD-053 من V660، WP4: LD-054 من V680، WP5: LD-055 من V700، WP6: LD-056 من V720، WP9: LD-057 من V740، WP8: LD-058، WP10: LD-059 من V780.
- كل عمود جديد nullable أو بقيمة محايدة؛ «غير محدد» قيمة؛ لا تعبئة مخترعة. المبالغ `numeric(15,3)` بالدينار، تُعاد نصًا (`@JsonFormat(shape=STRING)`).
- Jackson 3: الحقول البدائية `boolean` الغائبة من JSON تُرفض 400؛ استعمل `Boolean` وعالج null في الباني المضغوط.
- كتل النص `"""` في Java تحذف المسافة قبل `"""` الختامية: اكتب `"..." + " " + ...` صراحة.
- الصلاحيات: أضف الاسم في `Permission.java` (enum + Codes) و`frontend/src/api/permissions.ts` وتسمياته في `admin.en.ts`/`admin.ar.ts`، وازرعها للأدوار بترحيل `on conflict do nothing`. ADMIN يحمل كل الصلاحيات تلقائيًا لكن لا نطاق اطلاع له.

## الاطلاع (قرار D3) — إلزامي لكل جدول يخص قضية
- القاعدة الوحيدة في دوال قاعدة البيانات `case_visible` و`case_changeable` (V600) عبر `CaseAccessPolicy`: `access.requireView(caseId)` (404 لغير المطلع)، `access.requireChange(caseId)` (403 لمن يطلع ولا يعدل)، `access.visibleCaseIds("col")` لتقييد أي استعلام قوائم/تقارير/تصدير/بحث، `access.scope()` مع JPQL `function('case_visible', ...)`.
- كل واجهة نقطة نهاية تقرأ سجلًا فرعيًا ترجع 404 إن لم يُرَ حالته؛ كل كتابة تمر بـ`CaseLookup.recordEvent` (يفرض التعديل) أو `access.requireChange`. المهمة المسندة تعطي الاطلاع فقط (`recordTaskEvent`).
- منافذ القراءة (`*ReportQueries`) تتطلب مستخدمًا مسجلًا؛ اختبارات المنافذ المباشرة تستعمل `TestActors.signIn(...)` قبل كل نداء.
- أي نقطة جديدة تُضاف إلى `CaseVisibilityApiTest`-style: اختبار يثبت 404 لغير المطلع وعدم ظهورها في قوائم/تقارير لمن لا يراها.

## الاختبارات
- `TestActors.as(...)` يضيف نطاق `ALL` و`CASE_EDIT_IN_SCOPE` (للاختبارات القديمة)؛ لاختبار العضوية والاطلاع استعمل `TestActors.scoped(id, name, List.of(...scopes), perms...)`.
- التنظيف: أضف جداولك إلى `OperationsTestData.deleteAll` و`CaseTestData.deleteAll` (الأبناء قبل الآباء) وإلا تفشل الاختبارات اللاحقة بقيد المفتاح الأجنبي.
- `GovernmentIntakeTestSupport.register` يؤكد التنبيه من التكرار تلقائيًا؛ `registerStrict` يبقيه.
- اختبارات الواجهة: `fakeApi` (test/api.ts) و`scriptedFetch` (features/cases/testSupport.tsx؛ يعترض قوائم الهيكل والتكرار). واجهات جديدة تُستدعى من صفحة القضية تُضاف إلى اعتراضه أو تُمرَّر مسارها في كل اختبار يعرض الصفحة.

## الفرع والـPR والنشر
- فرع `feature/LD-DOC-<wp>-<slug>` من أحدث `origin/develop`؛ عنوان الإيداع ≤ 72 حرفًا، وخاتمة `Co-Authored-By`/`Claude-Session` كما في الإيداعات السابقة؛ لا تدمج قبل نجاح كل فحوص PR؛ الدمج `gh pr merge N --merge` مصرّح به عند النجاح.
- بعد الدمج: انتظر دورة develop (النشر على UAT)، تحقق من `GET /api/v1/release` (بجلسة QA) أنه SHA الدمج، ثم شغّل رحلة `tools/journeys/<wp>_journey.py` (قالب: `wp1_access_journey.py`) وخزّن `docs/evidence/DOC-<WP>/` ولقطات المتصفح (`/tmp/lawdesk-qa-browser-runtime`, `LD_LIBRARY_PATH=/tmp/lawdesk-qa-browser-libs/usr/lib/x86_64-linux-gnu`).
- حسابات QA: `/srv/ai-workspaces/secrets/lawdesk-qa/{manager,staff,viewer}.json` (لا تُطبع ولا تُغيَّر؛ `admin` معطل بانتظار المالك). كل بيانات الرحلات اصطناعية بعلامة `SYNTH-DOC`، ولا تعديل لقضايا قائمة.

## ممنوعات المالك (تذكير)
لا اختراع مدد قانونية أو عطلات أو سياسات احتفاظ أو مزود ذكاء اصطناعي؛ لا دمج تلقائي لسجلات متشابهة؛ لا بيانات تاريخية مولّدة؛ لا إشعارات خارجية؛ لا تعديل لـ Matbakhy أو main أو الإنتاج أو CI/CD أو بيانات الدخول.
