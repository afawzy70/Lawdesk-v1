# إعادة تحقق QAD-007..015 ومراجعة staging CD

الجولة دي مكملة لكل الأدلة السابقة. مفيش تقرير قديم أو FAIL تاريخي اتشال، ومفيش تعديل لكود التطبيق أو مساحة Claude أو أي مورد Matbakhy. التوصيات مرتبطة بالـSHAs الآتية فقط:

| PR | SHA المختبر | QA مستقل PASS / FAIL | التوصية |
|---|---|---:|---|
| #2 | `048ff350211f5620b69269c356e50fe8871b37fd` | 84 / 0 | **BLOCKED** لقبول/دمج المرحلة بالكامل |
| #5 | `a58dddcc395dc59424efdb84340f67559bbe76e2` | 70 / 0 | **BLOCKED** للاعتماد على Phase1 قديم وغير مقبول بالكامل |
| #7 | `32724ae88983ac72a43d49d7f3a8db3e253a989e` | 94 / 6 | **REQUEST_CHANGES** |

الأرقام تخص assertions منفذة عند كل SHA، مش عدد قصص مكتملة. كل family اتجمعت مرة واحدة؛ محاولات الإعادة ودليل Caddy الداعم مش اختبارات إضافية. اختبارات أدوات QA الثمانية PASS، منفصلة عن المنتج.

## العيوب القديمة

**QAD-007..015 كلها PASS في إعادة التحقق المنطبقة**، مع قصر كل حكم على SHA الاختبار:

- QAD-007: رفض أسماء Git غير الصالحة ونجاح حدود طول commit ومصفوفة PR flow.
- QAD-008: رفض tab/newline/CRLF/U+2003 في PostgreSQL الحقيقي. اتضاف اختباران جديدان لترقية V1→V2: البيانات الصحيحة بتفضل موجودة، والبيانات القديمة غير الصالحة بتفشل الترقية بشكل atomic من غير حذف الصف أو تثبيت V2. V1 ما اتعدلش.
- QAD-009/010/011: هيدرز firewall400، allowlist الدقيقة للـinfo، و401 للـanonymous POST اتأكدوا عبر HTTP حقيقي.
- QAD-012/013: نفس failure fixtures للـsmoke ولإعادة نشر الإصدار الحالي نجحت من غير تخفيف expected.
- QAD-014/015: كل حالات Escape الستة نجحت، وaxe ما رجعش الملاحظتين القديمتين. ده إغلاق للملاحظة الآلية فقط؛ Safari/WebKit وقارئ الشاشة اليدوي NOT_RUN.

التتبّع: `qa/defects/qad-007-through-015-retest.json`. الإصلاحات الخلفية متحققة عند PR2، وإصلاحات الـpreview عند PR5. فرع PR5 لسه فيه dependency أقدم: migration V2 غير موجودة فيه. نجاح preview70 مش إثبات إن الفرع كله ورث إصلاحات PR2.

## نتائج المطوّر — منفصلة

| PR | backend | frontend | tools | deploy |
|---|---:|---:|---:|---:|
| #2 | 92 PASS | 50 PASS | 34 PASS | 40 PASS |
| #5 | لم يتكرر backend عند هذا SHA | 141 PASS | 32 PASS | 34 PASS |
| #7 | 92 PASS | 50 PASS | 50 PASS | 94 PASS |

frontend lint/typecheck/coverage/build نجحوا في الثلاث نسخ. دي إعادة تشغيل لاختبارات المطوّر الأصلية، مش داخلة في QA المستقل. فحوص CI الستة نجحت لكل HEAD وقت الفحص؛ `Deploy staging` في PR7 **SKIPPED، وبالتالي NOT_RUN**، ومش PASS.

## حدود تمنع توصية merge

- PR2: runtime للـreal-container stack وactual artifact/schema rollback لسه BLOCKED بسبب Docker socket. API فحص حماية develop رجّع403 ورسالة اشتراط Pro/public؛ B4 ما اتقفلش. معايير القبول لسه PROPOSED/B5. الفجوات الأساسية QAD-001..006 OPEN.
- readiness أثناء DB outage رجعت503 بعد **30.24 ثانية** في PR2 بميزانية رصد40ثانية، ثم تعافت بعد4.841ثانية. محاولة العميل الأصلية ذات5ثوانٍ ما حصلتش على رد واتحفظت كـFAIL تشخيصي. النجاح الحالي يثبت معنى الحالة، **مش** الالتزام بمهلة5ثوانٍ أوSLA أداء غير معتمد. الموضوع ده ظاهر كحد تشغيلي، مش نتيجة مخفية أوPASS أداء.
- PR5: الـpreview نفسه70/70 ناجح، لكن لازم تحديث dependency إلى Phase1 المقبول وإعادة التحقق عند SHA الناتج. real nginx/container verification BLOCKED؛ مراجعة المتصفحات التانية والترجمة/قارئ الشاشة مش كاملة. قصص Phase7 ما اتقبلتش من preview.
- PR7: الستة عيوب الجديدة موضحة في التقرير المنفصل، وبعضها High. actual SSH enforcement وreal pg_dump/pg_restore وlive rollback/drill مش منفذين. shared proxy scripts بتعدل Matbakhy عند تشغيلها؛ QA لم يشغلها.

السجل الكامل لحالات المنتج عند PR2: **0 PASS / 0 FAIL / 351 BLOCKED**. ده ما بيمسحش الأربع حالات الفاشلة عند الـSHA القديم؛ العيوب المحددة اتصلحت، لكن السيناريوهات الكاملة وعقود قبولها ما اتعمدتش تلقائيًا. الفرق بين اختبار جزئي ناجح وقبول حالة كاملة محفوظ.

## الأدلة والبيئة

Linux Ubuntu24.04، Java21، Spring Boot4.1.2، Maven wrapper3.10.0، PostgreSQL18.6 real binaries، Node22، Playwright1.64.1، Chromium build1248، axe4.14.0. نسخة الـjar عند PR7 اتبنت من SHA7 نفسه؛ مفيش نقل PASS من jar أوSHA تاني. كل snapshot مطابق لبايتات Git مع QA-only test overlays.

الأدلة في `qa/evidence/pr-2-048ff350211f/` و`pr-5-a58dddcc395d/` و`pr-7-32724ae88983/`، والتجميع في `execution-summary.json` لكل واحد. الـcontext فيه hashes لأدوات QA والـUTC والـSHA. الخطوات الجديدة في `qa/independent/STAGING-CD-REVIEW.md` بجانب أوامر الجولة السابقة.

التوصيات منشورة كتقييمات QA، مش GitHub approval رسمي؛ الاتصال بنفس حساب صاحب PR يمنع review رسمي. مفيش merge أوdeploy أوتغيير صلاحيات Docker أوSSH أوproxy. أي HEAD يتغير يحتاج إعادة الاختبارات ذات الصلة قبل توصية جديدة.
