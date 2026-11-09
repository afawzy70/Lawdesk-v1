# التحقق المستقل من HEADs الحالية — LawDesk Phase1 / preview / CD

اتأكدت أولًا من GitHub: PR2 وPR5 مطابقين لرسالة المالك. PR7 اتقدم من `f492e0c8d0eba22e421ea5ebfc33ebd09fd2d53a` إلى `3b6f18cfb9d2f47a6be4dc79803254d748c69a15` قبل الفحص؛ اتراجع الفرق واتختبر الأحدث. دليل ancestry والمزامنة في `qa/evidence/latest-verification-dependencies.json`.

| PR | SHA المختبر | QA مستقل PASS / FAIL | توصية merge |
|---|---|---:|---|
| #2 | `13297c0a27738fb69f6ad90bf1135678e09b8880` | **85 / 0** | **BLOCKED — تحقق تشغيل باقي** |
| #5 | `26fbc19fb3e7a5911d6689631a3f46e6e6b3daac` | **144 / 0** | **BLOCKED — اعتماد Phase1 وتحقق تشغيل باقي** |
| #7 | `3b6f18cfb9d2f47a6be4dc79803254d748c69a15` | **114 / 0** | **BLOCKED — تحقق تشغيل/SSH/restore باقي** |

**مفيش عيب مؤكد باقي في QAD-007..021 ضمن الفحوص المنفذة، ومفيش طلب لإعادة أي إصلاح خلصه Claude.** التوصيات BLOCKED تخص gates غير منفذة، مش REQUEST_CHANGES للكود اللي نجح. أرقام الجدول لا تشمل اختبارات المطوّر ولا retries ولا حالات BLOCKED/NOT_RUN، ومش عدد قصص مقبولة بالكامل.

## الإصلاحات المتحقق منها

كل **QAD-007..021 PASS** عند SHA الاختبار المنطبق. السجل `qa/defects/qad-007-through-021-verification.json` بيربط كل ID بالدليل. الإخفاقات والأدلة القديمة محفوظة عند SHAs القديمة.

- QAD-007..013: نفس رفض أسماء الفروع، SQL whitespace، headers/info/anonymous writes، smoke failure وsame-release rollback نجحوا. SQL المستقل9/9 يشمل ترقية V1→V2 الصحيحة ورفض الترقية بشكل atomic فوق بيانات قديمة غير صالحة؛ مفيش حذف صامت للبيانات.
- QAD-014/015: نفس70 فحص Chromium/axe/security نجحوا. المزامنة اتثبتت من Git: HEAD الحالي لـPR2 ancestor للـPR5؛ السبب القديم اللي كان بيطلب sync اتقفل.
- QAD-016: manual dispatch بيرفض فشل بوابة exact-SHA، والـpositive control بكل السبع بوابات بيمر.
- QAD-017: cached revision الغلط بيتبني أويرفض، والـcorrect cached revision control بيقدر يكمل للـdeploy fixture.
- QAD-018: المفتاح الصالح الموجود بلاقيود بيترفض قبل bootstrap بدل تركه غير مقيّد. المفتاح خاص بالـfixture واتمسح؛ مفيش ملف SSH حقيقي اتعدل.
- QAD-019: اختبار persist failure وصل لفشل النسخ بعدreload ثمسجّلreload تاني للاستعادة، والـactive والـpersisted رجعوا للأصل.
- QAD-020: markers غير متوازنة اترفضت قبلreload؛ الـnominal control بيمر مع الحفاظ علىhosts. نقلDocker وهمي، لكن Caddy2.10.2 الحقيقي بيعملadapt للملفات الصناعية بدلvalidator بيقبل أي حاجة.
- QAD-021: up وsmoke أثناءrollback بيستخدمواmanifest الإصدار المستهدف.

CD regression النهائي **19/19 PASS**: نفس16 حالة القديمة وثلاث positive controls بتأكد إننا مش بنعتبر أي رفض مبكر إصلاحًا. النقل اتحدث لردود image revision وGitHub checks وnative Caddy adapt. assertion البوابات بقت تشترط الستة الأصليين ومعاهم develop guard. المحاولة التشخيصية القديمة محفوظة ومستبعدة من التجميع؛ اختلاف أداة QA مش عيب منتج.

develop guard اتراجع عند أحدث SHA، و**10/10** fixtures مستقلة نجحوا: force/direct pushes، غياب verdict، quotes/code fences، تعديل بعدmerge، FAIL أحدث، parent غير متحقق، وRECOVER صريح. ده حارس إجرائي بعدpush؛ مش server-side prevention أوإثبات تمييز هوية QA عن المطوّر في الحساب المشترك.

## مهلة readiness الجديدة

الاختبار المستقل اتشدّد للشرط الجديد: أول probe بعد توقف قاعدة QA مباشرة، **من غير warm-up أوretry**، وبعدين probeين إضافيين. كل رد لازم503 وأقل من5 ثوانٍ. فحص30 قراءة سليمة لازم كلهم200، وliveness تفضل200، والتعافي يحصل بعدrestart لنفس قاعدة البيانات والمنفذ.

| PR | أول probe | ثاني probe | ثالث probe |
|---|---:|---:|---:|
| #2 | 0.084s | 3.092s | 3.099s |
| #5 | 0.127s | 3.098s | 3.116s |
| #7 | 0.102s | 3.099s | 3.097s |

الكل PASS، ومعاهم healthy30 وliveness/recovery. المحاولة القديمة ذات30 ثانية محفوظة كتاريخ؛ مش مستخدمة لتمرير مهلة5 ثوانٍ. اختبارات المطوّر الجديدة للانقطاع والـhealthy false-negatives اتراجعت واتعادت، لكنها منفصلة عن عدد QA المستقل.

## فصل أنواع الأدلة

كل SHA ليه build وjar خاص به. SQL/HTTP/Vite اتنفذوا عليه فعلًا؛ مفيش نقل PASS بين الفروع. PR5 بيشمل backend regression بعد المزامنة، فوق70 browser checks.

| اختبارات المطوّر المعاد تشغيلها | PR2 | PR5 | PR7 |
|---|---:|---:|---:|
| backend | 94 PASS | 94 PASS | 94 PASS |
| frontend الأصلي | 50 PASS | 141 PASS | 50 PASS |
| tools | 35 PASS | 35 PASS | 84 PASS |
| deploy | 40 PASS | 40 PASS | 111 PASS |

lint/typecheck/coverage/build نجحوا. تشغيل frontend7 الكامل كان فيه QA overlay:61 اختبارًا =50 للمطوّر+11 لـQA. الـ11 اتعادوا targeted واتحسبوا مرة واحدة بس. أدوات QA الثمانية PASS ومش من أرقام المنتج.

بوابات CI الستة الأصلية SUCCESS عند كل HEAD وقت الفحص. في PR7، Develop guard وDeploy staging كانوا SKIPPED في سياق PR، واتسجّلوا **NOT_RUN**. نجاح fixtures للحارس مش بيحوّل job متخطية إلى تنفيذ فعلي.

## الباقي BLOCKED / NOT_RUN

- Docker socket رجع permission denied في فحص جديد للقراءة فقط؛ **real-container execution BLOCKED**. مفيش تغيير daemon أوصلاحيات أواستخدام Docker تطبيق آخر.
- installed forced-command/root-owned gate، actual Docker privileges، real pg_dump/pg_restore وlive image/schema rollback **NOT_RUN/BLOCKED**. transport tests نجحت، لكن مفيش runtime PASS بديل.
- تشغيل shared Matbakhy proxy attach/detach/restore **NOT_RUN** طبقًا لتعليمات عدم تعديل البنية التحتية. مفيش ادعاء إن الـproxy الحقيقي اتجرب أوإن النشر ممكن يتفعّل من الأدلة دي.
- Safari/WebKit وقارئ الشاشة اليدوي **NOT_RUN**؛ Chromium/axe بس اللي اتقاسوا.
- QA-0004 وQA-0037 لسه BLOCKED في register؛ الحارس الإجرائي مش بيمنعpush قبل حدوثه. B5 بقى APPROVED provisional baseline واتاخد في الاعتبار؛ مش مطلوب إعادة موافقة قديمة.

الحالات المحجوبة وغير المنفذة وN/A ما اتحسبتش PASS. نجاح إصلاح محدد مش قبول تلقائي لباقي قصص المنتج. الفجوات الأساسية والأدلة التاريخية محفوظة. المطلوب المتبقي تحقق تشغيل معزول، مش إعادة تنفيذ الإصلاحات.

البيئة: Ubuntu24.04، Java21، Spring Boot4.1.2، Maven wrapper3.10.0، PostgreSQL18.6، Node22، Playwright1.64.1، Chromium1248، axe4.14.0. الأدلة والـUTC وsource hashes في `qa/evidence/pr-2-13297c0a2773/` و`pr-5-26fbc19fb3e7/` و`pr-7-3b6f18cfb9d2/`.

مفيش merge أوdeploy أوتغيير Matbakhy/SSH/Docker permissions، ومفيش طلب من Claude بتكرار شغل منتهي. خطوات الإعادة في `qa/independent/CURRENT-VERIFICATION.md`.
