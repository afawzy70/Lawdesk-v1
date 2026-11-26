# LD-DOC-R6-01 — Requirements register and traceability (M0)

Source: `البرنامج.docx` (uploaded 2026-11-26; 209 non-empty paragraphs). Its two parts are *«تطوير منطق ما بعد صدور الحكم»* and *«إضافة جديدة مع القضايا»* (prosecution complaints).
Page numbers: the file carries no page markers in its text layer, so each row cites **section and paragraph** (`§` = the document's own heading; `¶` = paragraph number in the extracted text, kept in the M0 evidence extract). The reader maps them to the printed pages.

Status key: **Exists** (already in develop `18822a8`) · **Partial** · **Missing**. Decision column points to `LD-DOC-R6-04` (D-xx).
Terminology is kept exactly as in the source («الهيئة» = CITRA). Nothing below changes the legal wording.

## Module A — post-judgment workflow

| ID | Source (Arabic, faithful) | § / ¶ | Existing | Gap → proposal | Depends | Acceptance (summary) | Tests | Decision |
|---|---|---|---|---|---|---|---|---|
| PJ-01 | قاعدة عامة بعد أي حكم في أي درجة: أول درجة ← استئناف، استئناف ← تمييز | §1 ¶4-6 | Partial: `judgment` (one per proceeding), `proceeding.stage` | Next-stage rule table configurable by level; no stage after the highest configured level | R5-free | Judgment at stage S offers only the configured appeal type | T-A01 | D-08 |
| PJ-02 | تسجيل منطوق الحكم وتاريخه ونتيجته (لصالح / ضد / لصالح جزئيًا) | §1 ¶7-8 | Partial: `judgment.summary`, `judgment_date`, `outcome` has IN_FAVOUR/AGAINST/PARTIAL/DISMISSED/OTHER | Operative text field distinct from `summary`; map DISMISSED/OTHER | — | Three source outcomes selectable; existing rows unchanged | T-A02 | D-17 |
| PJ-03 | انتهاء الميعاد = بدء الميعاد + المدة، المدة من «إعدادات مواعيد الطعن» حسب الدرجة ونوع الدعوى | §1 ¶9-11 | Partial: `deadline`, `DeadlineCalculator`, `deadline_rule` (no level/case-type/basis) | New versioned setting (Module B) feeds a per-party deadline | ST-02 | Due date equals start + configured days using the approved counting method | T-A03 | D-01,02,04 |
| PJ-04 | تحديد الحالة الإجرائية للحكم وفق نتيجته | §1 ¶12 | Missing (`judgment.status` ISSUED/NOTIFIED/CHALLENGED/OTHER is a different thing) | New `judgment_procedural_status`; do not reuse case or old status | PJ-17 | Status set on registration by outcome | T-A04 | — |
| PJ-05 | احتساب مستقل لكل طرف؛ تاريخ بدء مختلف بحسب الإعلان | §1 ¶13 | Missing | `judgment_party_deadline` rows for CITRA and OPPONENT | PJ-03 | Two deadlines, different starts, independent | T-A05, T-A07 | D-05 |
| PJ-06 | لصالح الهيئة: «في انتظار انقضاء ميعاد الطعن»؛ عند الانتهاء يطلب النظام تأكيد الباحث لموقف الخصم؛ لا نهائي تلقائيًا؛ بعد تأكيد عدم الطعن والتحقق من شروط النهائية ← «حكم نهائي» ثم تنفيذ أو إغلاق؛ عند طعن الخصم تُفتح الدرجة التالية وصفة الهيئة «مستأنف ضدها» (استئناف) / «مطعون ضدها» (تمييز) | §2أ ¶16-21 | Missing (enforcement module exists) | Workflow service + opponent-position confirmation + next-stage creation + enforcement/closure routing | PJ-05, PJ-19 | Scenarios 1 and 2 of §8 | T-A10, T-A11 | D-07, D-12 |
| PJ-07 | ضد الهيئة: «جاري إعداد الطعن»؛ الطعن قبل الانتهاء يفتح الدرجة التالية بصفة «مستأنفة» / «طاعنة»؛ عدم الطعن يلزم سببًا مكتوبًا + صاحب الموافقة + تاريخ القرار في جدول «قرارات الطعن»؛ لا نهائية تلقائية ما دام ميعاد الخصم قائمًا أو له طعن؛ بعد انتهاء كل المواعيد وعدم وجود طعن وبعد التأكيد ← «حكم نهائي (قبول الحكم)» | §2ب ¶23-29 | Missing | `appeal_decision` table; finality service | PJ-05 | Scenarios 3, 4, 6 of §8 | T-A12-T-A14 | D-07, D-09, D-21 |
| PJ-08 | جزئيًا: يُعامل كالصادر ضد الهيئة؛ طعن مستقل لكل طرف على الحكم ذاته؛ أحدهما لا يمنع الآخر؛ ست حالات | §2ج ¶31-41 | Missing | `judgment_appeal` supports both parties; six combinations covered in one parametrised suite | PJ-19 | Each of the six combinations reaches the right state | T-A15 (×6) | — |
| PJ-09 | لا نهائي تلقائيًا بانتهاء الميعاد؛ تأكيد يدوي من الباحث | §3 ¶43 | Partial: `judgment.final` boolean is informational | Finality only through an explicit endpoint | PJ-06 | An expired deadline leaves the status unchanged | T-A16 | D-07 |
| PJ-10 | «تاريخ بدء ميعاد الطعن» قابل للتعديل، افتراضيًا تاريخ الحكم، ويمكن تغييره إلى تاريخ الإعلان | §3 ¶44 | Partial | Start date + `start_basis` (JUDGMENT/NOTIFICATION); default from setting | ST-02 | Default applied, authorised edit audited | T-A06 | D-05 |
| PJ-11 | لا مدد ثابتة في الكود | §3 ¶45 | Compliant by design (D6) | Static check: no literal period in new code | — | Architecture test fails on literals | T-X03 | — |
| PJ-12 | جدول «قرارات الطعن»: القرار، السبب، صاحب الموافقة، تاريخ القرار | §3 ¶46-50 | Missing | `appeal_decision` | — | Four fields mandatory for NOT_APPEAL | T-A12 | D-09 |
| PJ-13 | كل تغيير في الحالة الإجرائية يُسجَّل: المستخدم، التاريخ والوقت، السابقة، الجديدة | §3 ¶51-55 | Partial: `audit_event` | Status-history table + audit events | PJ-04 | Row per change | T-A17 | — |
| PJ-14 | الفصل بين قرار الهيئة (طعن / عدم طعن / لم يُحسم) والوضع القضائي (ميعاد قائم / طعن مقيد / نهائي) | §3 ¶56-58 | Missing | Three separate columns/records | PJ-05 | Changing one never rewrites another | T-A18 | — |
| PJ-15 | قبول الهيئة للحكم ليس دليل نهائية | §3 ¶59 | Missing | Finality precondition code | PJ-09 | Non-appeal with running opponent deadline stays non-final | T-A14 | — |
| PJ-16 | إعادة الاحتساب: سبب إلزامي، حفظ البيانات السابقة والجديدة، المستخدم والوقت | §3 ¶60 | Partial: `deadline.change_reason` | Recalculation history table | PJ-05 | Reason required; before/after kept | T-A08, T-A09 | — |
| PJ-17 | الحالات: في انتظار انقضاء ميعاد الطعن، جاري إعداد الطعن، منظورة استئناف، منظورة تمييز، حكم نهائي، حكم نهائي (قبول الحكم)؛ موقف كل طرف مستقل | §5 ¶99-106 | Missing | Status codes + state matrix (R6-03) | — | Matrix enforced server-side | T-A19 | D-21 |
| PJ-18 | بيانات كل درجة: الدرجة، رقم الدعوى/الطعن، المحكمة، الدائرة، صفة الهيئة، المنطوق، التاريخ، النتيجة، بدء الميعاد، المدة المطبقة (منقولة وقت التسجيل)، الانتهاء، الطرف الطاعن | §6 ¶108-120 | Partial: `proceeding` holds stage/number/court/chamber | Add capacity (`citra_capacity`) and the applied-duration snapshot | — | Snapshot unchanged after setting edit | T-A09 | D-12 |
| PJ-19 | أكثر من طعن على الحكم الواحد، لكل طعن: الطرف الطاعن، النوع، الرقم، تاريخ القيد، المحكمة والدائرة، صفة الهيئة، المستندات؛ مرتبط بالحكم الأصلي دون فقد بيانات | §6 ¶121-129 | Missing | `judgment_appeal` + `judgment_appeal_document` | PJ-08 | Independent rows, history kept | T-A15 | — |
| PJ-20 | التطوير على الوظائف الحالية دون إلغاء أي منها؛ ترابط بيانات الحكم والطعن والمواعيد؛ اختبار الحالات الإحدى عشرة؛ لا اعتماد قبل صحة الانتقالات ودقة الاحتساب وسلامة البيانات | §8 ¶149-165 | — | Regression gate (R6-04 §Tests) | all | Whole suite + journeys | T-X01.. | — |

## Module B — «إعدادات مواعيد الطعن»

| ID | Source | § / ¶ | Existing | Gap → proposal | Acceptance | Tests | Decision |
|---|---|---|---|---|---|---|---|
| ST-01 | تظهر لمدير النظام أو مدير الإدارة القانونية فقط | §4.1 ¶63 | ADMIN and LEGAL_MANAGER roles exist | Permission `APPEAL_SETTINGS_MANAGE` to those two roles | Others 403 on every endpoint | T-B01 | D-09 |
| ST-02 | إضافة وتعديل وإيقاف؛ حقول: درجة الحكم، نوع الطعن، نوع الدعوى (أو الكل)، المدة، بداية الاحتساب الافتراضية، السند القانوني، الحالة، تاريخ السريان | §4.2 ¶65-73 | Partial: `deadline_rule` lacks level, appeal type, case type, basis | New `deadline_setting` (kind APPEAL) | CRUD + deactivate | T-B02 | D-01 |
| ST-03 | قيم مبدئية: أول درجة/استئناف/عادية/30؛ أول درجة/استئناف/مستعجلة/15؛ استئناف/تمييز/الكل/30 — تُراجَع وتُعتمد قبل التفعيل | §4.3 ¶75-91 | Missing | Seed as **DRAFT, inactive** (never auto-active) | Fresh database has three inactive rows; no judgment can use them until approved | T-B03 | D-01 |
| ST-04 | لا سجلان مفعلان لنفس الدرجة ونوع الدعوى في الفترة نفسها إن تعارضا | §4.4 ¶93 | Missing | Exclusion constraint on (level, case type scope, effective range) | Overlap refused 409 | T-B04 | D-16 |
| ST-05 | التعديل يسري على الأحكام الجديدة فقط | §4.4 ¶94 | — | Snapshot columns on the party deadline (days, basis, setting id, version) | Old deadlines untouched | T-B05 | — |
| ST-06 | تدقيق كل تعديل: المستخدم، الوقت، القيمة قبل وبعد | §4.4 ¶95 | `audit_event` | Before/after payload | Present on every write | T-B06 | — |
| ST-07 | لا إعداد مطابق: تنبيه + مدة يدوية + السند والسبب + إشعار مدير النظام | §4.4 ¶96 | Missing | Manual-entry path; notification to ADMIN users | Override recorded and admins notified | T-B07 | D-13 |
| ST-08 | الاحتفاظ بالإعدادات السابقة وعدم حذفها | §4.4 ¶97 | Pattern exists (`lookup_value_history`) | Versioned rows, no delete | No delete endpoint | T-B08 | — |

## Module C — post-judgment alerts

| ID | Source | § / ¶ | Existing | Proposal | Tests | Decision |
|---|---|---|---|---|---|---|
| AL-01 | قبل الانتهاء بـ10 و5 ويومين إلى الباحث ومديره المباشر | §7.1 ¶132-136 | `notification`, `DeadlineReminderRun` (lead days, no default). **No "direct manager" relation exists** | Direct-manager link is a prerequisite | T-C01 | D-14 |
| AL-02 | أيام التنبيه قابلة للتعديل من لوحة الإدارة | ¶137 | `deadline_reminder_setting` (one row, all deadlines) | Separate offsets for APPEAL and GRIEVANCE | T-C02 | — |
| AL-03 | تنبيه عند الانتهاء دون تأكيد، دون تغيير الحالة | §7.2 ¶139-140 | Missing | Alert type EXPIRED_UNCONFIRMED | T-C03 | — |
| AL-04 | تنبيه عاجل: ضد الهيئة، يومان أو أقل، لا طعن ولا قرار معتمد | §7.3 ¶142 | Missing | Alert type URGENT | T-C04 | — |
| AL-05 | إيقاف تنبيهات الطرف الذي سُجل طعنه أو حُسم بعدم الطعن، واستمرار الآخر | §7.4 ¶144-145 | Missing | Alerts keyed by (party deadline, type, offset) | T-C05 | — |
| AL-06 | سجل التنبيهات: النوع، الوقت، المستلمون، القضية والحكم | §7.5 ¶147 | Partial | `post_judgment_alert` unique key makes the job idempotent | T-C06 | D-15 |

## Module D — prosecution complaints

| ID | Source | § / ¶ | Existing | Proposal | Tests | Decision |
|---|---|---|---|---|---|---|
| PC-01 | النطاق: من تقديم الشكوى إلى قرار النيابة (حفظ أو إحالة) | ¶168 | Missing | New aggregate `prosecution_complaint` linked to cases | — | — |
| PC-02 | تقديم الشكوى: رقم داخلي تلقائي، رقم الشكوى والسنة، النيابة المختصة، تاريخ التقديم، مشكو في حقه (أكثر من واحد؛ اسم/جهة/رقم مدني أو تجاري)، ملخص الواقعة وتاريخها، التكييف القانوني، الباحث المسؤول، النسخة والمرفقات | §ثانيًا-1 ¶171-180 | Missing | `complaint`, `complaint_respondent`, document links | T-D01 | D-18 |
| PC-03 | الحالة «مقدمة للنيابة» | ¶181 | Missing | Initial status | T-D01 | — |
| PC-04 | جلسات التحقيق: التاريخ، الحاضرون من الهيئة، ما تم، الموعد التالي، المطلوب قبله | §2 ¶183 | Missing | `complaint_session` | T-D02 | — |
| PC-05 | طلبات النيابة (مستندات/تقارير فنية/شهود)، تاريخ الطلب وتاريخ الرد | ¶184 | Missing | `complaint_request` | T-D03 | — |
| PC-06 | إرفاق المستندات والمذكرات؛ الحالة «قيد التحقيق بالنيابة» | ¶185-186 | Documents module exists | Link table, case-level visibility rules | T-D04 | D-20 |
| PC-07 | الحفظ: سبب، تاريخ القرار، تاريخ علم الهيئة، نسخة القرار؛ الحالة «محفوظة» | §3أ ¶189-190 | Missing | `complaint_decision` | T-D05 | — |
| PC-08 | حساب آخر ميعاد للتظلم من «إعدادات المواعيد» | ¶191, ¶203 | Missing | `deadline_setting` kind GRIEVANCE, same fields as appeal settings | T-D06 | D-10 |
| PC-09 | تظلم / عدم تظلم؛ عدم التظلم يلزم سببًا وصاحب الموافقة، وبعدها تُغلق الشكوى | ¶192 | Missing | `complaint_grievance_decision` | T-D07 | D-09, D-11 |
| PC-10 | الإحالة: تاريخ القرار، المحكمة المختصة، رقم القضية، التهمة/المواد؛ الحالة «محالة للمحكمة» | §3ب ¶194-195 | Missing | `complaint_referral` | T-D08 | — |
| PC-11 | فتح قضية مرتبطة تلقائيًا، نقل الأطراف والوقائع والمستندات، صفة الهيئة «مجني عليها / شاكية» | ¶196 | Case intake + capacity exist | Idempotent referral service calling existing case domain; unique link | T-D09, T-D10 | D-12 |
| PC-12 | بعدها تكمل القضية الدورة العادية بما فيها منطق ما بعد الحكم | ¶197 | Depends on Module A | Linked case uses Module A unchanged | T-D11 | — |
| PC-13 | الحالات: مقدمة للنيابة، قيد التحقيق بالنيابة، محفوظة، محالة للمحكمة، مغلقة | §3 ¶199 | Missing | Status codes + matrix | T-D12 | D-22 |
| PC-14 | المحفوظة لا تُغلق قبل انتهاء ميعاد التظلم أو تسجيل قرار بعدم التظلم | §4-1 ¶201 | Missing | Closure precondition, explicit action, never by timer | T-D13 | D-11 |
| PC-15 | ربط ثنائي الاتجاه: من الشكوى تُفتح القضية ومن القضية تُفتح الشكوى | ¶202 | Missing | FK both ways + UI both ways | T-D14 | — |
| PC-16 | ميعاد التظلم ليس في الكود | ¶203 | — | See PC-08 | T-X03 | — |
| PC-17 | تدقيق كل تغيير حالة: من، متى، من أي حالة إلى أي حالة | ¶204 | `audit_event` | Status-history table | T-D12 | — |
| PC-18 | تنبيه قبل جلسات التحقيق (أيام قابلة للضبط) | §5 ¶206 | Missing | Alert type | T-E01 | — |
| PC-19 | طلبات النيابة بلا رد خلال مدة قابلة للضبط | ¶207 | Missing | Alert type | T-E02 | — |
| PC-20 | شكوى قيد التحقيق بلا إجراء مسجل مدة قابلة للضبط | ¶208 | Missing | Alert type; "recorded activity" definition in D-23 | T-E03 | D-23 |
| PC-21 | قبل انتهاء ميعاد التظلم بـ5 أيام ثم يومين إلى الباحث ومديره المباشر | ¶209 | Missing | As AL-01 | T-E04 | D-14 |

## Evidence and delivery status

Every row above is **Not started** (M0 delivers analysis only). The column is updated per milestone in `docs/evidence/DOC-R6-*`.
