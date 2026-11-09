# التوثيق الفني — نظام LawDesk

الأساس: فرع `develop` عند الالتزام `2ff4c95`. المرجع الأقدم للعقود المشتركة: `architecture/CONTRACTS.md`. الخط الأساسي الأمني: `docs/security/SECURITY-BASELINE.md`.

## 1. البنية العامة

| الطبقة | التقنية (من `pom.xml` و`package.json` والملفات الحاضنة) |
|--------|-----------------------------------------------------------|
| الواجهة الخلفية | Java 21، Spring Boot `4.1.2`، Spring Security، Spring Data JPA، Flyway، PDFBox (استخراج نص PDF)، ICU4J، FastExcel (تصدير Excel) |
| قاعدة البيانات | PostgreSQL `18.6` (الصورة الرسمية)، دور التطبيق `lawdesk_app` بحقوق دنيا |
| الواجهة الأمامية | React `19.3.1`، TypeScript `6.0.3`، Vite `8.3.5`، React Router `8.5.0`، Vitest `5.0.5`؛ عربي RTL أولًا مع تطابق مفاتيح الإنجليزية |
| الحزمة | ثلاث حاويات بمشروع Compose باسم `lawdesk-staging`: `db` و`backend` و`web` (nginx يقدّم الواجهة ويمرر `/api/*` والمسارات الصحية) |
| الهوية | جلسة متصفح (`JSESSIONID` HttpOnly وSameSite=Lax) ورمز CSRF (`XSRF-TOKEN` ← `X-XSRF-TOKEN`)؛ لا مزود هوية خارجي ولا رموز في `localStorage` |

لا منافذ منشورة على المضيف. `db` و`backend` على شبكة داخلية فقط؛ `web` وحده ينضم إلى الشبكة الخارجية `lawdesk-edge`. الحاويات بلا صلاحيات (`cap_drop: ALL`) وبنظام ملفات للقراءة فقط (الخلفية والويب).

## 2. اصطلاحات الواجهة البرمجية (`architecture/CONTRACTS.md` §4)

- مسارات `/api/v1/...` بصيغة الجمع، JSON بـcamelCase، المعرّفات UUID، التواريخ ISO-8601.
- الأخطاء `application/problem+json`: 400 تحقق (`errors[{field,code}]`)، 401 بلا جلسة، 403 صلاحية ناقصة أو قضية مرئية غير قابلة للتعديل، 404 غير موجود **أو غير مرئي**، 409 نسخة قديمة (`version`) أو تعارض، 422 قاعدة عمل (`rule: "<CODE>"`).
- الصفحات: `page ≥ 0` و`1 ≤ size ≤ 100` (الافتراضي 20)؛ أي قيمة خارج ذلك 400 لا تُقصّ. القوائم الصغيرة داخل القضية مصفوفات كاملة.
- كل كتابة على كيان قابل للتعديل ترسل `version` (قفل متفائل).
- المبالغ نص بثلاث منازل في JSON (`@JsonFormat(STRING)`) و`numeric(15,3)` في القاعدة.
- الأوقات `timestamptz` بتوقيت UTC؛ العرض بتوقيت `Asia/Kuwait`، باستثناء حساب «اليوم» للمهام (انظر §8).

## 3. خريطة الوحدات (حزم `com.lawdesk.*`)

| الوحدة (الحزمة) | الجداول الرئيسية | المتحكمات (أمثلة المسارات) | ترحيلات |
|------------------|-------------------|------------------------------|----------|
| `security`, `users`, `audit` | `app_user`, `app_role`, `role_permission`, `user_role`, `audit_event` | `/auth/login`, `/me`, `/users`, `/roles`, `/audit` | V100–V102، V190 |
| `access`, `org`, `cases.CaseAccessPolicy` | `org_unit`, `access_scope`, `case_grant` | `/access-scopes`, `/cases/{id}/grants`, `/cases/{id}/confidentiality`, `/org-units` | V600 |
| `cases` | `legal_case`, `case_reference_counter`, `case_event`, `party`, `case_party`, `case_party_counsel`, `case_org_involvement`, `case_duplicate_hint` | `/cases`, `/cases/intake`, `/parties`, `/cases/{id}/involvements` | V200–V202، V601–V602 |
| `courts`, `proceedings` | `court`, `proceeding`, `judgment`, `special_decision` | `/courts`, `/cases/{id}/proceedings`, `/judgments/{id}` | V300–V304، V603 |
| `pleadings` | `claim`, `claim_revision`, `claim_result`, `defense*`, `case_fact`, `evidence_*`, `gap_followup`, `lookup_value*` | `/cases/{id}/claims`, `/defenses`, `/evidence`, `/judgments/{id}/claim-results`, `/lookups` | V620–V623 |
| `hearings`, `experts` | `hearing`, `expert`, `expert_referral`, `expert_session` | `/hearings`, `/experts`, `/expert-referrals` | V400–V408، V660 |
| `inforequests`, `risk`, `caselinks`, `casefile`, `notifications`, `followup` | `info_request/reply/follow_up`, `risk_assessment*`, `case_link`, `notification` | `/info-requests`, `/risk-assessments`, `/cases/{id}/links`, `/cases/{id}/summary`, `/notifications`, `/follow-up/run` | V661–V665 |
| `deadlines` | `deadline`, `deadline_rule`, `official_holiday`, `deadline_reminder_setting` | `/deadlines`, `/deadline-rules`, `/official-holidays`, `/deadline-settings/reminders` | V680–V682 |
| `documents`, `legaldocs`, `enforcement` | `case_document*`, `document_version*`, `document_link`, `document_text`, `document_deletion_log`, `archive_*`, `legal_document*`, `enforcement_*` | `/documents`, `/archive/settings`, `/legal-documents`, `/enforcement` | V403، V700–V705 |
| `tasks`, `calendar`, `notes` | `task`, `task_history`, `task_status_map`, `calendar_date`, `notification_preference`, `case_note` | `/tasks`, `/calendar/*`, `/cases/{id}/notes` | V402، V405، V720–V721 |
| `library` | `library_judgment`, `library_principle`, `library_judgment_relation`, `library_classification`, `case_precedent_link` | `/library/*`, `/cases/{id}/precedents`, `/similar-judgments` | V640 |
| `settlements`, `directives`, `templates`, `lists`, `search` | `settlement*`, `directive`, `form_template*`, `managed_list_value` | `/settlements`, `/directives`, `/templates`, `/lists/{list}`, `/search` | V740–V745 |
| `reports` | لا جداول (يقرأ عبر منافذ الوحدات) | `/reports/dashboard`, `/indicators`, `/families`, `/cases`، وتصدير | V760 (تكلفة/فهرس فقط) |
| `assistant`, `integrations` | `ai_provider_config`, `assistant_session`, `assistant_output`, `assistant_query_log` | `/assistant/*`, `/integrations` | V780–V781 |
| `platform`, `api` | `app_release` | `/release`, الصحة | V1–V2 |

الواجهة الأمامية: `frontend/src/features/<area>` لكل منطقة (`routes.tsx` لكل واحدة تركّب مساراتها في `App.tsx`)، والرسائل في `src/i18n/messages/<area>.{ar,en}.ts`، والتنقل في `src/layout/navigation.ts` (العنصر `planned` يبقى «قريبًا»: اليوم `/judgments` فقط). `src/api/permissions.ts` يعكس `Permission.java`، ويفرض `tools/tests/test_contracts.py` تساويهما.

## 4. سياسة الوصول: `case_visible` و`case_changeable`

**قاعدة واحدة تُكتب مرة واحدة** في قاعدة البيانات (V600، وأُعيد تعريف `case_visible` في V720 لتعتمد المهام غير المكتملة، وخُفضت تكلفتاها في V760):

| الدالة | معناها |
|--------|---------|
| `case_in_scope(confidentiality, case_type, owning_unit, all, units, types)` | `false` للقضية `RESTRICTED`؛ وإلا `true` إن كان النطاق شاملًا، أو نوع القضية ضمن أنواع النطاق، أو وحدتها المالكة ضمن شجرة وحدات النطاق (قضية بلا وحدة مالكة يغطيها نطاق وحدة عليا) |
| `case_visible(case, me, all, units, types)` | العضو المختص أو البديل أو المنشئ؛ أو `case_in_scope`؛ أو منح فعّال (غير مسحوب ولا منتهٍ)؛ أو مهمة للمستخدم في القضية حالتها ليست `COMPLETED` أو `CANCELLED` |
| `case_changeable(case, me, edit_in_scope, all, units, types)` | العضو المختص أو البديل، أو المنشئ **ما دام لا عضو مختص**، أو منح `CONTRIBUTE` فعّال، أو (`CASE_EDIT_IN_SCOPE` **و** `case_in_scope`). العرض والمهمة والمنشئ لا تكفي للتعديل |

نطاق المستخدم (`CaseAccessPolicy.scope()`) يُبنى من رموز تحملها صلاحياته: `SCOPE:ALL` و`SCOPE:UNIT:<id>` و`SCOPE:TYPE:<type>` (مصدرها صفوف `access_scope` للدور وللمستخدم؛ و`OWN_UNIT` تتحول إلى وحدة المستخدم). **دور ADMIN بلا صف نطاق افتراضيًا ويرفض النظام إنشاء نطاق له** (`ACCESS_SCOPE_FOR_ADMIN_ROLE`)؛ ولا يمنح المرء نفسه منحًا ولا نطاقًا (`ACCESS_SELF_GRANT`).

**كيف تُطبَّق في الشيفرة** (الصنف `com.lawdesk.cases.CaseAccessPolicy`، ويُحقن حيث يلزم):

| الحاجة | الاستدعاء | الأثر |
|--------|-----------|-------|
| قراءة سجل تابع لقضية | `access.requireView(caseId)` | 404 `NotFoundException` إن لم تكن مرئية |
| كتابة | `access.requireChange(caseId)` | 404 إن لم تكن مرئية، 403 إن كانت مرئية وغير قابلة للتعديل |
| توزيع/اعتماد يتطلب صلاحية وحق تعديل | `access.requireDistribute(caseId, Permission.X)` | يرفض إن غابت الصلاحية أو حق التعديل |
| قائمة أو عدّاد أو تقرير على جدول القضايا | `access.viewable("alias")` ← `Predicate(sql, params)` تُضاف بـAND | قيد SQL سريع يختار ما تختاره `case_visible` نفسه |
| جدول تابع (جلسات، مستندات، مهام…) | `access.visibleCaseIds("t.case_id")` | شبه ربط مجزأ: القاعدة تُقيَّم مرة لكل قضية لا لكل صف |
| تعديل | `access.changeable("alias")` | قيد `case_changeable` |
| JPA | `function('case_visible', ...)` بمعاملات `access.scope()` | |

قواعد: لا تُعِد كتابة القاعدة في أي وحدة؛ كل endpoint يخص قضية يحدد قضية السجل ثم يستدعي السياسة (والصلاحية الوظيفية بـ`@PreAuthorize(Permission.Codes.X)`). الاستثناءات الموثقة: اعتماد المذكرة يكفيه الاطلاع (`MEMO_APPROVE`)، وتحريك المكلَّف لحالة تكليفه أو مهمته لا يتطلب حق تعديل القضية. `CaseVisibilityPredicateEquivalenceTest` يثبت أن القيد السريع يطابق الدالة في 32 تركيبة مستخدم/نطاق؛ و`EndpointAuthorizationTest` يمر على كل معالجات `/api/` ويفشل إن لم يعلن أحدها صلاحيته (العامّ فقط `csrf` و`login`)، ويولّد `tools/security/endpoint_matrix.py` مصفوفة المسارات والصلاحيات. **لم يُتحقق هنا من وجود اختبار يفشل إن وُجد endpoint لا يستعمل `CaseAccessPolicy`** (ورد في تصميم LD-DOC-04 كمتطلب)؛ المغطى اختبارات الوصول لكل وحدة (غريب 404، مطّلع بلا تعديل 403).

## 5. نطاقات الترحيل (Flyway)

- مسار `backend/src/main/resources/db/migration`، الصيغة `V<n>__LD-0xx_<وصف>.sql`، **للأمام فقط** ولا تُعدَّل بعد الدمج؛ التصحيح بترحيل جديد.
- `out-of-order: true` و`ignore-migration-patterns: "*:future"`: إصدار أقدم يبدأ على مخطط أحدث (أساس الرجوع).
- النطاقات (يفرضها `tools/contracts.py`): `V1–V99` المنصة؛ `V100–199` الأمن والمستخدمون؛ `V200–299` القضايا؛ `V300–399` المحاكم والإجراءات؛ `V400–499` العمليات (جلسات، خبراء، مهام، مستندات، ملاحظات)؛ `V500–599` اللوحة والتقارير؛ **`V600–999` برنامج المتطلبات الشاملة** (LD-050…LD-059).
- ما في النطاق الأخير: V600–603 النواة والوصول؛ V620–623 الطلبات والأحكام والدفوع والأدلة؛ V640 المكتبة؛ V660–665 الجلسات والإفادة والمخاطر والارتباط والتنبيهات؛ V680–682 المواعيد؛ V700–705 الأرشيف والتنفيذ؛ V720–721 المهام والتقويم؛ V740–745 التسويات والتكليفات والنماذج والبحث؛ V760 تكلفة الدوال؛ V780–781 المساعد.
- إن أعدت تعريف `case_visible` أو `case_in_scope` بـ`create or replace` فأعد ذكر `cost` فيه (V760) وإلا عاد المخطِّط للتكلفة الافتراضية 100.
- ترحيلات منح الصلاحيات تستعمل `on conflict do nothing`: **لا تسحب ما عدّله المسؤول**، ولا تمنح الأدوار المخصصة شيئًا.
- لا ترحيل مدمِّر (drop/rename) في هذا البرنامج. ترحيل المهام V720 أعاد تسمية حالتين فقط (`OPEN←NEW`, `DONE←COMPLETED`) عبر `task_status_map` القابل للرجوع، وتقبل الواجهة الأسماء القديمة مدخلاً.

## 6. الاختبار والتكامل المستمر

- **الخلفية:** `cd backend && ./mvnw -B -ntp verify`؛ اختبارات MockMvc على PostgreSQL مدمجة (`io.zonky.test:embedded-postgres`) وتعاد على الصورة الرسمية `postgres:18.6` في مهمة منفصلة. الحزم تحت `backend/src/test/java/com/lawdesk/<module>`.
- **الأمامية:** `cd frontend && npm ci && npm run lint && npm run typecheck && npm run test:coverage && npm run build` — بوابة تغطية 90% للتعليمات والفروع والدوال والأسطر (`vite.config.ts`)، وفحص `npm audit --omit=dev --audit-level=high`.
- **الأدوات والنشر:** `python -I -m unittest discover -s tools/tests -v` و`-s deploy/tests -v`.
- **مهام `.github/workflows/ci.yml`** (أسماء الفحوص): Repository policy، Backend (Java 21, embedded PostgreSQL 18.6)، Backend against postgres:18.6 image، Frontend (Node 22)، Staging stack (images + compose smoke)، Secret scan (gitleaks 8.30.1)، Develop guard (QA-cleared merge). تُشغَّل على `ubuntu-24.04` افتراضيًا؛ والمتغير `LAWDESK_CI_RUNNER=self-hosted` ينقلها إلى المشغّل `contabo` (انظر `docs/operations/CI-RUNNER.md`). الطلبات المسودة تتخطى المهام. نشر UAT (`deploy-staging.yml`) يعمل دائمًا على مشغّل مستضاف.
- **قواعد المستودع:** فروع `feature|fix|docs|qa|chore/...`، رسائل Conventional Commits بعنوان ≤ 72 حرفًا، لا دفع مباشر إلى `develop`/`main`؛ كل ذلك في `CONTRIBUTING.md` و`tools/repo_policy.py`.
- **الرحلات:** `tools/journeys/wp*_journey.py` (WP1…WP10) تعمل على UAT بحسابات QA وبيانات اصطناعية موسومة `SYNTH-DOC`، ولا تعدّل قضية قائمة.
- **القياس:** `Wp8PerformanceHarness` (اختياري بـ`-Dlawdesk.perf=true`) على قاعدة مؤقتة ببيانات اصطناعية (50,000 قضية و584,999 سجلًا تابعًا).

## 7. النشر (ملخص؛ التفصيل في `06-operations-backup-restore-ar.md`)

دمج إلى `develop` ← اجتياز كل بوابات CI ← مهمة `deploy-staging` ← الأمر المقيَّد `lawdesk-deploy-gate deploy <sha>` على المضيف ← بناء الصورتين محليًا ← `deploy.sh` (قفل، نسخة `pg_dump`، `compose up --wait`، `smoke.sh`) ← تأكيد `GET /api/v1/release` للتحقق من SHA المنشور. فشل الصحة أو الفحص الدخاني يعيد الإصدار السابق تلقائيًا.

## 8. محاذير تشغيلية معروفة

1. **دور `lawdesk_app` ومسار البحث.** التطبيق يتصل بدور `lawdesk_app` الذي `search_path` الخاص به `lawdesk` **وبلا أي حق على المخطط `public`** (`deploy/postgres/app-role.sql`). أي استعلام يحوي `public.` أو `to_regclass('public.…')` يفشل بـ`permission denied` على UAT بينما ينجح في CI لأنه يتصل بحساب مميّز. وقع هذا فعلًا: مؤشران في اللوحة أجابا 500 بعد نشر WP8 حتى الإصلاح #80 (بحث بالاسم المجرّد)، وصار كل مؤشر يُحسب في savepoint مستقل (`unavailableReason: INDICATOR_FAILED`). القاعدة: لا تذكر `public.` أبدًا، واختبر بدور مقيَّد (`IndicatorPendingModulesTest`).
2. **لا `pg_trgm`.** الدور لا يملك `CREATE` على القاعدة فلا يُنشئ امتدادات؛ لذا بُني التطبيع والتشابه الثلاثي في دوال SQL (`library_normalize`, `library_trigrams`, `library_similarity`, `search_snippet`). فلتر النص في قائمة القضايا لا يستعمل فهرسًا (603 مللي ثانية عند 50 ألف قضية).
3. **`ANALYZE` بعد التحميل الكبير.** على جدول حُمّل بالجملة ولم يُحلَّل بعد قد يختار المخطِّط حلقة متداخلة تقارن كل صف بكل صف (اختبار 10,000 قضية استغرق 44–54 ثانية بدل ثوانٍ). Autovacuum يحلل خلال نحو دقيقة على نظام يعمل؛ بعد **أي استيراد كبير** شغّل `ANALYZE` يدويًا قبل الاعتماد على أزمنة التقارير. مساعدات الاختبار تحلل بعد التحميل (`analyzeAfterBulkLoad`).
4. **أخطاء 504 في تنزيل أدوات CI.** فحوص السياسة (actionlint وshellcheck) والمسح السري (gitleaks) تنزّل أدواتها من GitHub، وأخفقت مرارًا بـ504. هذا عطل بنية تحتية لا خلل في الشيفرة: أعد تشغيل الفحص الأحمر (`gh run rerun --failed`) ولا تدمج حتى تخضر كل الفحوص.
5. **gitleaks يمسح التاريخ كله ولكل الفروع.** نصوص مفاتيح الترجمة وأسماء ملفات تحوي كلمات مثل `secret` أو `password` بجوار سلسلة طويلة قد تُحتسب `generic-api-key`؛ أعد صياغة النص بدل إضافة استثناء إلى `.gitleaksignore` (لكل استثناء سبب مكتوب).
6. **أزمنة المهام والتقويم.** «اليوم» لاستحقاق المهام (`Africa/Cairo`) يختلف بساعة على الأكثر عن التقويم والتنبيهات (`Asia/Kuwait`).
7. **الوصول والمسارات الجديدة.** بعد إضافة جدول تابع للقضية استعمل `visibleCaseIds` وأضف اختبار غريب/مطّلع/بلا صلاحية؛ وإلا تسرّب السجلات عبر القوائم والبحث والتقارير.
8. **التخزين.** الملفات `bytea` داخل PostgreSQL (لا وحدة مشفرة متاحة للحاوية، قرار D7)، فالنسخ الاحتياطي يغطيها لكنه غير مشفّر. حدود الرفع: 10 ميغابايت (`spring.servlet.multipart`).
9. **OCR.** صورة الواجهة الخلفية تثبّت `tesseract-ocr` 5.5 مع `ara` و`eng` (Alpine، `backend/Dockerfile`) وتثبت وظيفة CI `stack` ذلك بـ`tesseract --list-langs`. المحول `TesseractOcrEngine` يجهّز الصورة (`OcrPreprocessor`: تدوير تصحيحي حتى 10 درجات، عتبة Otsu، هامش أبيض) ويقرأ بـ`--psm 3` ثم بوضع النص المتناثر للصفحات القصيرة (أقل من 8 أسطر) ويضيف الأسطر الناقصة فقط. الأرقام المقيسة والحدود في `docs/evidence/DOC-OCR/README.md`. اختبارات الدقة (`OcrQualityTest`، `OcrRealEngineApiTest`) تُتخطى حيث لا يوجد المحرك (مشغّل CI المستضاف)؛ التخطي لا يعني النجاح.
10. **الوظائف المجدولة.** تشغيل ساعي واحد للمتابعة (`FollowUpRun`) وللتذكير بالمواعيد؛ وعامل خلفي يقرأ المسوحات. كل شيء متكرر آمن (مفاتيح `dedupe_key`). لا مهمة حذف مجدولة (القرار D9).
11. **الحدود.** قيم `page`/`size` الخاطئة تعطي 400 ولا تُقص؛ والتصدير الأكبر من 10,000 صف يُرفض (أما جرد مراحل القضايا فيُحسب بالأجزاء فوق هذا الحد).
12. **اختلافات اسمية بين التصميم والتنفيذ.** عمودا الرقم المدني ورقم المنشأة في القاعدة هما `party.national_id` و`party.registration_no` (التصميم ذكر `civil_no`/`establishment_no`)؛ وجدول `calendar_event` لم يُنشأ (التقويم مشتق من مصادر)؛ وجدول `document_category` لم يُنشأ (تستعمل `lookup_value` بالقائمة `DOCUMENT_CATEGORY`).

## 9. إضافة وظيفة جديدة (دليل مختصر)

1. حدّد نطاق ترحيل ورقم قصة؛ اكتب الاختبار الأحمر أولًا واحفظ سجله في `docs/evidence/<story>/`.
2. أضف الصلاحية في `Permission.java` (مع `Codes`) و`permissions.ts` وتسميتَي العربية والإنجليزية، ومنحًا افتراضيًا بترحيل `on conflict do nothing`.
3. اجعل كل endpoint يحمل `@PreAuthorize(Permission.Codes.X)` ويستدعي `CaseAccessPolicy` للسجلات التابعة لقضية.
4. أي مبلغ جديد عمود `numeric(15,3)` مستقل، لا يُجمع ولا يُشتق (انظر `03-data-model-ar.md`).
5. أي جدول يحمل قيمًا من الوثيقة يُزرع **فقط** بما ورد فيها، وما سواه `PROPOSED` يحتاج اعتماد المالك. لا بيانات مخترعة.
