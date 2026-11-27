# LawDesk — إعادة الاختبار المستقلة بعد PR #57

النسخة المختبرة: **`1e74945961a77fcf5a67fc26f3a51d0715660628`**. التاريخ: 2026-10-25.
GitHub `develop` وقت بداية الجولة و `/api/v1/release` اتطابقوا مع النسخة دي؛ أربع حسابات QA أكدوها قبل وبعد UAT.
PR #57 اتدمج من HEAD `e91736967f04c96e62be03d98f5121cf1f775eae`.

عند تجهيز النشر، PR #55 اتدمج الساعة 15:54:40 UTC: develop بقى
`40b5bc84b3722348b50dd9c1210da6b962a7b549`. القراءة المستقلة الساعة 15:57:24 UTC لسه أثبتت UAT على
`1e74945961a77fcf5a67fc26f3a51d0715660628`. الأحكام هنا تخص SHA المختبر فقط؛ مفيش قبول ضمني لـPhase 7
أو تحويل نتائج الجولة للنسخة الأحدث. [قراءة وقت النشر](../evidence/pr57-final-retest-20261025/publication-release.json).

**QAD-041 و QAD-042 و QAD-043 اتثبت إصلاحهم مستقلًا.** مفيش طلب من Claude يعيد تنفيذ إصلاحاتهم.
لكن تسوية اختبار قديم كان NOT_RUN كشفت **QAD-044 — S3**: سجلات المحاكم والدعاوى بعد أول 100 بتختفي من
بعض الشاشات والاختيارات. عشان كده **القبول الكامل للمراحل 2–6 لسه غير مكتمل**؛ المرحلتين 4 و5 محتاجين إصلاح.

| المرحلة | حكم QA الوظيفي عند النسخة المحددة | أساس الحكم والمانع المتبقي |
|---|---|---|
| Phase 2 — LD-007…011 | **QA ACCEPTED** | دخول وخروج، المستخدمين والأدوار والصلاحيات، reset/revocation، وفشل/نجاح audit والمعاملات والتزامن؛ QAD-042/043 مقفولين بالدليل. |
| Phase 3 — LD-012…017 | **QA ACCEPTED** | إنشاء وتعديل وبحث وأطراف وحالات وتاريخ القضايا؛ UAT حقيقي، حدود حقول ورفض تغييرات atomic، pagination مستقرة، ودليل lifecycle السابق. |
| Phase 4 — LD-018…023 | **REQUEST_CHANGES** | QAD-044: المحكمة والدعوى رقم 101 مش متاحين في الواجهة، رغم وجودهم في API. فحوص النزاهة القضائية السابقة محفوظة ونجحت الـ regression المتاحة. |
| Phase 5 — LD-024…029 | **REQUEST_CHANGES** | QAD-044: مش ممكن اختيار الدعوى رقم 101 لجدولة جلسة أو إحالة لخبير. إصلاحات الرفع والتاريخ وaudit المهمة السابقة لسه متحققة. |
| Phase 6 — LD-030…034 | **QA ACCEPTED** | الحسابات والتقارير وCSV/XLSX/PDF والصلاحيات وحدود التصدير؛ أدلة جديدة على UAT مع الأدلة المستقلة السابقة للمنطق اللي ما اتغيرش. |

القبول ده **للنطاق الوظيفي الحالي حسب العقود المنشورة وسياسة Dev/UAT**، مع القرارات الصريحة للمالك:
OPEN → DONE مسموح مع audit، والـ pagination غير الصالح 400. مش شهادة Production Ready، ولا اعتماد ضمني
للافتراضات القانونية/التجارية القديمة. QAD-001…006 لسه ظاهرة، و351 template ما اتحولوش PASS بالجملة.
مراجعة Phase 7 وتصميم Modern Chambers خارج الحكم ده؛ PR #55 و#56 ما اتلمسوش.

**الأرقام المستقلة الحالية**

| التنفيذ | PASS | FAIL | BLOCKED | NOT_RUN |
|---|---:|---:|---:|---:|
| Runtime release قبل وبعد UAT — 4 أدوار + anonymous | 10 | 0 | 0 | 0 |
| UAT: المستخدمين والقضايا والتدرج القضائي، browser/API | 35 | 0 | 0 | 0 |
| UAT: أربع أدوار وreset والجلسات والسباقات | 41 | 0 | 0 | 0 |
| UAT: بحث حرفي مع filters وreload بالعربي والإنجليزي وrelease | 6 | 0 | 0 | 0 |
| UAT: 18 قائمة، audit المهمة، guards، تقارير وتنزيلات | 41 | 0 | 0 | 0 |
| UAT: dashboard مقابل السجلات، عربي/إنجليزي وmobile/axe | 27 | 0 | 0 | 0 |
| تحليل مستقل لملفات CSV/XLSX/PDF المنزّلة فعلًا | 10 | 0 | 0 | 0 |
| محلي: pagination وحدود offset وaudit المهام | 80 | 0 | 0 | 0 |
| محلي: audit الإدارة وكلمة المرور وفشل المعاملات | 48 | 0 | 0 | 0 |
| محلي: API الصفحات الثانية وسباق آخر مسؤول | 6 | 0 | 0 | 0 |
| محلي: واجهات المحاكم/الدعاوى بعد 100 سجل، عربي/إنجليزي | 0 | 8 | 0 | 0 |
| محلي: حدود القضايا والأطراف والتاريخ والبحث | 18 | 0 | 0 | 0 |
| محلي: الحالات القضائية التفصيلية القديمة | 10 | 0 | 0 | 0 |
| **الإجمالي المنفذ بعد تسوية مشاكل سكريبت QA** | **332** | **8** | **0** | **0** |
| **تغطية متبقية منفصلة، مش نجاحات** | **0** | **0** | **2** | **6** |

UAT وملفاته: **170 PASS / 0 FAIL**. محلي: **162 PASS / 8 FAIL**. الثمانية FAIL لنفس العيب QAD-044.
دي checks مسجلة؛ check واحد ممكن يحتوي أكتر من request أو assertion، ومش عدد قصص مقبولة.
كل النتائج مع مواصفاتها في [execution-summary.json](../evidence/pr57-final-retest-20261025/execution-summary.json).

الخام **334 PASS / 8 FAIL / 3 BLOCKED / 0 NOT_RUN**. التشغيل الأول لاختبار الحدود اتعطل بسبب انتظار
`option` كعنصر مرئي بدل وجوده في DOM، وبسبب helper للتوازي كان ناقص في سكريبت QA. أصل النتائج محفوظ؛
تم تصحيح السكريبت وتنفيذ الحالات فعلًا. اتشال تكرار اثنين من prerequisites من الإجمالي المنفذ، ومفيش
فشل منتج اتخفى ولا BLOCKED اتحول نجاح بالتخمين.
[التشغيلات الخام](../evidence/pr57-final-retest-20261025/raw-executions.json)،
[تسوية الـ harness](../evidence/pr57-final-retest-20261025/harness-triage.json).

**QAD-041 — حدود pagination**

اختبرت كل الـ18 route المرقمة محليًا وعلى UAT: users، parties، audit، courts، experts، hearings، documents، tasks،
cases، وقوائم hearings/notes/timeline/proceedings/judgments/special-decisions للقضية، وتقارير cases/judgments/hearings.
القوائم غير المرقمة زي roles وexpert/workload reports والـ exports مش محسوبة بالخطأ.
[جرد applicability](../evidence/pr57-final-retest-20261025/pagination-applicability.json).

- الفاضي وwhitespace-only وnegative/zero وغير الرقمي وsize101 رجعوا 400؛ القيم المحذوفة استخدمت page0/size20.
- `page=2147483647&size=1` → 200 وصفحة فاضية؛ `page=2147483647&size=100` → 400 `page/Max`.
- `page=21474836&size=100` → 200؛ القيمة التالية للـpage → 400.
- محليًا اتغطى كمان size2 عند 1073741823/1073741824، وinteger parsing overflow، والحد مع size الافتراضي.
- duplicate parameters اللي فيها offset زائد اترفضت؛ anonymous 401 وmissing permission 403 قبل كشف validation.

الأربع routes اللي كانت 500 أصلًا ما رجعتش 500. العقد الجديد موثق حد حاصل ضرب page×size بدل تركه يفشل داخل JPA.
[80 نتيجة HTTP/SQL](../evidence/pr57-final-retest-20261025/policy/results.json)،
[طلبات UAT والنتائج](../evidence/pr57-final-retest-20261025/phase56-uat/results.json).

**QAD-042 — SUCCESS لازم يطابق commit فعلي**

في PostgreSQL 18.6 مؤقت، اتعاد نفس فشل COMMIT باستخدام constraint triggers مؤجلة على الصفوف الصناعية فقط.
شمل USER_CREATE/UPDATE/ENABLE/DISABLE/ROLES_CHANGE وPASSWORD_RESET وROLE_CREATE/UPDATE/PERMISSIONS_CHANGE.
النتيجة: الرد 500 عند الفشل، business snapshot ثابت، وSUCCESS delta = 0. فشل كتابة role_permission بعد
تعديل الدور رجّع كل التغييرات من غير نجاح وهمي.

اتمنع إدخال SUCCESS نفسه لكل عملية، واتأكدت من rollback الكامل. بعد إزالة fault، نفس الطلب نجح وسجل حدث واحد
لكل فعل مطلوب؛ تغيير permissions له ROLE_UPDATE وROLE_PERMISSIONS_CHANGE، مش duplication. سباق تعديل نفس
version للمستخدم والدور رجع 200/409، وسجل حدث العملية اللي كسبت فقط؛ retry بالـversion الجديدة سجل العملية
الجديدة مرة واحدة. bootstrap الطبيعي سجل مستخدم واحد وحدثه المطابق. فشل bootstrap أثناء startup نفسه مش
مستنتج من positive control؛ الـ fault injection هنا لمسارات الإدارة الفعلية أثناء التشغيل.

ثلاثة سباقات إضافية لسحب/تعطيل آخر اثنين ADMIN من operator مستقل رجعوا 200/422 `LAST_ADMIN`، فضل ADMIN
واحد، وسجلوا SUCCESS واحد. [48 اختبار audit](../evidence/pr57-final-retest-20261025/audit/results.json)،
[سباقات آخر مسؤول](../evidence/pr57-final-retest-20261025/edges-verified/results.json).

**QAD-043 — كلمة المرور والجلسات والأحداث الأمنية**

فشل business COMMIT أو إدخال PASSWORD_CHANGE/SUCCESS رجّع التغيير كله: الحالة ثابتة، كلمة المرور الجديدة
مرفوضة، القديمة والجلسة السابقة صالحين. retry الناجح رجع 204 مع SUCCESS واحد، أبقى جلسة التغيير، ورفض الجلسة
الأخرى 401 وسجل SESSION_REVOKED/DENIED. سباق تغييرين متعارضين انتهى بكلمة واحدة صالحة وSUCCESS واحد.

PASSWORD_CHANGE/FAILURE عند كلمة حالية غلط، وLOGIN/FAILURE للمستخدم المجهول، وACCESS_DENIED/DENIED
فضلوا محفوظين بعد الطلب الفاشل. دول أحداث أمنية صحيحة؛ ما اتلغوش مع rollback لعمليات النجاح.
الاختبارات الفعلية على UAT غطت reset وforced change وconcurrent sessions وdisable/re-enable، وإبطال الطلبات
القديمة حتى بعد تغيير جديد. [دليل الأدوار والجلسات](../evidence/pr57-final-retest-20261025/roles234/results.json).
مفيش حقن أعطال أو SQL مباشر على UAT، ومفيش كلمة مرور أو hash أو cookie أو CSRF منشورين في الأدلة.

**QAD-044 — S3 / P1: اختفاء السجلات بعد أول 100**

دي كانت فجوة NOT_RUN محفوظة من المراجعة السابقة؛ اتنفذت دلوقتي على نسخة محلية من نفس SHA:

1. أنشئ 102 محكمة FIRST_INSTANCE نشطة، منها QA-EDGE-001…101، و101 دعوى بأرقام QA-P-001…101 في قضية QA واحدة.
2. `GET /api/v1/courts?active=true&level=FIRST_INSTANCE&size=100&page=1&sort=code,asc` بيرجع QA-EDGE-101.
3. افتح القضية → تسجيل دعوى: القائمة فيها 100 محكمة فقط، من غير المحكمة المطلوبة أو وسيلة للوصول للصفحة التالية.
4. `GET /api/v1/cases/{caseId}/proceedings?page=1&size=100` بيرجع QA-P-101.
5. شاشة الدعاوى بتعرض 100؛ تحديد جلسة وإحالة لخبير بيتيحوا نفس أول 100 فقط. اتكرر بالعربي والإنجليزي.

السبب في `proceedingsApi.ts`: `size=100` ثم إرجاع `.items` فقط وتجاهل page metadata.
مش فساد أو فقد بيانات في PostgreSQL؛ العيب بيمنع الوصول الوظيفي للسجلات من الواجهة.
المطلوب paging أو تحميل صفحات محدود أو searchable selector، مع بقاء size cap100 وoffset validation.
امتداد نفس النمط للأحكام والقرارات القضائية ظاهر في المصدر؛ dataset أكبر من100 لكل واحد منهم لسه NOT_RUN.

[العيب وخطواته](../defects/qad-044.json)،
[8 نتائج browser FAIL](../evidence/pr57-final-retest-20261025/edges-verified/browser/results.json)،
[مثال عربي](../evidence/pr57-final-retest-20261025/edges-verified/browser/hearing-ar.png)،
[تسليم Claude على PR57](https://github.com/afawzy70/Lawdesk-v1/pull/57#issuecomment-6399633381).
ده إصلاح وظيفة، مش بدء إعادة التصميم أو تعديل فرع Phase 7.

**تسوية كل QAD والأدلة السابقة**

[السجل الموحد QAD-001…044](../evidence/pr57-final-retest-20261025/all-qad-reconciliation.json) بيحتفظ بآخر دليل
وSHA لكل عيب. QAD-007…043 عندهم دليل حل مستقل في نطاقهم، بما فيه قرار المالك المتحقق لـQAD-031، مع المحافظة على تاريخ الفشل الأصلي.
QAD-041…043 اتحدثوا بالدليل الجديد؛ QAD-044 مفتوح. مفيش طلب إعادة إصلاح QAD-037…040.

| baseline finding | الباقي الظاهر |
|---|---|
| QAD-001 | العقود التفصيلية موجودة؛ بعض عناوين PROPOSED والافتراضات التجارية لسه مش اعتماد مالك ضمني. |
| QAD-002 | suites مستقلة قابلة للتكرار موجودة؛ 351 template الأصلية محفوظة ومش blanket PASS. |
| QAD-003 | الضوابط الوظيفية متحققة؛ التحقق الأمني الشامل للإنتاج لسه Phase 8. |
| QAD-004 | افتراضات مواعيد الطعون والنهائية وسياسة التذكير ظاهرة؛ QA لا يعتمد قرارًا قانونيًا بالنيابة عن المالك. |
| QAD-005 | traceability للقصص الحالية موجودة؛ مش كل applicability للـ42 قصة داخل نطاق المراحل 2–6. |
| QAD-006 | النشر وruntime موثقين؛ independent image/database restore drill لسه BLOCKED. |

المنطق الخاص بالمستندات، V408، reports/export renderers والـfrontend ما اتغيرش عن PR54. لذلك احتفظت بدليل
QAD-037 للـPDF حتى10MiB وquota race، وQAD-038 لسباقات SQL والـlocks/deadlock/rollback، وQAD-039 لحدود
9999/10000/10001 في الصيغ الثلاث، من غير ما أضيفهم PASS جديد. مراجعة PDF العربية/النص المختلط البصرية
السابقة لـQAD-036 محفوظة؛ الجولة دي نزلت ملفات جديدة وحللتها مستقلًا، ومش بتسمي extraction مراجعة بصرية جديدة.
[مقارنة source blobs والأدلة المحمولة](../evidence/pr57-final-retest-20261025/carried-forward-evidence.json).

الـ regression الجديد شمل 18 check لسد حدود قديمة في Phase 3: title200/201، subject4000/4001، no-op،
رفض تعديل CLOSED/ARCHIVED، 102 timeline events مع timestamps متساوية، بحث `%` و`_` حرفيًا، sort ties،
party duplicate/kind mismatch، منع unlink في archived case، وreopen reason500/501.
[الدليل](../evidence/pr57-final-retest-20261025/core/results.json).
الـbrowser نفّذ كمان بحث `%` و`_` بالعربي والإنجليزي مع OPEN/CIVIL وreload للـURL: 6PASS شاملة release قبل وبعد.
[الدليل](../evidence/pr57-final-retest-20261025/search/results.json).
اختبار lifecycle السابق 31PASS محفوظ عند SHA الأصلي، بما فيه كل25 انتقال وsession rotation/audit filters؛
ما اتضافش للعداد الجديد.

**الفحوص المتبقية وحدود القبول**

[remaining-scope.json](../evidence/pr57-final-retest-20261025/remaining-scope.json) بيحفظ **2 BLOCKED / 6 NOT_RUN**:

- BLOCKED: فحص مباشر لكل بيانات UAT التاريخية عبر SQL؛ وdrill مستقل لاستعادة image+database في containers معزولة.
- NOT_RUN: full automated Java/TS SAST؛ تحديد وفحص embedded jarmode-tools المتبقي؛ Excel/LibreOffice GUI؛
  cross-browser/manual assistive technology الشامل؛ كل permission permutations؛ وواجهات judgments/decisions بعد100.

الفحص الشامل للصلاحيات زيادة على الـfour-role/custom-role matrix المنفذة، ومش ادعاء إنها exhaustive.
**الخمس حالات القضائية القديمة اتنفذت فعلًا في 10 checks PASS**: تعطيل محكمة مع حفظ تاريخها ورفض قيد جديد،
failure ثم retry لتسجيل proceeding وتاريخه، wrong-stage/no-judgment/other-case للنقض، سباق إنشاء حكمين
201/409 مع حكم وحدث واحد، ورفض قرار بمرجع أجنبي أو بتاريخ مبكر ثم قبول suspension من غير تغيير القضية/الحكم.
[الدليل](../evidence/pr57-final-retest-20261025/judicial/results.json).
آخر حالة وظيفية فوق100 تدخل في إعادة اختبار QAD-044؛ باقي فحوص التشغيل/الإنتاج لا تمنع Dev/UAT العادي تلقائيًا.
الخطط القديمة ومواصفاتها محفوظة؛ [تسوية الخطط](../evidence/pr57-final-retest-20261025/prior-plans-reconciliation.json)
بتفرق بين التنفيذ الكامل والجزئي والتصميم اللي لسه ما اتنفذش، من غير تغيير ملفات الدليل القديمة.

مراجعة المصدر الأمنية ركزت على transaction boundaries، إلغاء الجلسات، التفريق بين SUCCESS والأحداث الأمنية،
وحساب offset باستخدام long. نتائج SCA والأداء القديمة محفوظة بـSHA الأصلي، مش قياس جديد ولا ضمان أمان كامل.
مفيش stress/load/soak على Contabo. دليل CI/deployment job `121205600821` ناجح، لكنه **صفر PASS مستقل**؛
إثبات QA للنشر هو release endpoint والـbrowser/API الفعليين.
[المصدر والبيئة](../evidence/pr57-final-retest-20261025/source-identity.json)،
[دليل النشر](../evidence/pr57-final-retest-20261025/deployment-provenance.json).

التنفيذ التالي المطلوب من Claude هو QAD-044 فقط، مع إبلاغ QA بالـHEAD وSHA المنشور لإعادة اختبار النطاق المتغير.
QA باقي له النطاق المنفصل BLOCKED/NOT_RUN وإعادة التحقق من QAD-044 بعد إصلاح Claude؛ مش مطلوب تكرار الشغل الناجح.
مفيش إجراء مطلوب من المالك لتكرار provisioning أو مشاركة credentials، ومفيش merge/deploy نفذه QA.
السكربتات المحلية تستخدم `--candidate <archive> --sha <fullSHA> --output <newDirectory>`:
`final_owner_policy.py --strict-paging --bounded-offset`، `audit_transaction_review.py --extended`،
`final_acceptance_edges.py`، `final_core_boundaries.py`، و`final_judicial_variants.py`؛ إعدادات browser والـQA manifest محفوظة في الملفات.
الأدلة وchecksums وsecret scans مرفقة هنا في PR #6؛ logs الخام وcredentials خارج المستودع.
