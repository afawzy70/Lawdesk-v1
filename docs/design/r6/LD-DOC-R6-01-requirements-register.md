# LD-DOC-R6-01 — Requirements register and traceability (M0)

Generated from `tools/docs/r6_register_data.py` by `tools/docs/r6_register_build.py`; edit the data file, not this page.

**Reading rule (M0 is documentation only).** *Existing baseline* = behaviour or schema read in the develop tree at `18822a8` (file cited). *Proposed R6* = design for review, **not built**. Nothing in R6 is built; every row is "Not started". Source: `البرنامج.docx`, 209 numbered paragraphs (`¶`) in `docs/evidence/DOC-R6-M0/source-extract.txt`; provenance and digest in `LD-DOC-R6-00`. Decisions `D-nn` are in `LD-DOC-R6-04`; legal gates `G-*` and the authoritative data model in `LD-DOC-R6-05`.

**Verification state of every row:** Not started. Baseline column: read in the develop tree (cited). Source text: author-compared with the original DOCX (sha256 in LD-DOC-R6-00); independent verification PENDING (Codex).

## Module A — post-judgment workflow

| ID | Source | Expected behaviour (source) | Existing baseline | Proposed R6 | Dependency / legal status |
|---|---|---|---|---|---|
| PJ-01 | §1 ¶4-6 | قاعدة عامة بعد أي حكم في أي درجة: أول درجة ← استئناف، استئناف ← تمييز | `ProceedingService.checkParent` enforces the proceeding ladder FIRST_INSTANCE→APPEAL→CASSATION (ProceedingService:140-170). No per-judgment appeal-type rule. | Appeal type offered for a judgment comes from the approved setting of its level (G-SETTING); nothing after the highest configured level. | D-08 BLOCKED; G-SETTING, G-STAGE; M2 |
| PJ-02 | §1 ¶7-8 | تسجيل منطوق الحكم وتاريخه ونتيجته (لصالح / ضد / لصالح جزئيًا) | `judgment.operative_text` EXISTS (V621:64, Judgment.java:51-52), `judgment_date`, `outcome` IN_FAVOUR/AGAINST/PARTIAL/DISMISSED/OTHER (V302), `summary`. | Reuse `operative_text`; **no new operative-text column**. The three source outcomes are IN_FAVOUR/AGAINST/PARTIAL; DISMISSED/OTHER stay readable and enter no workflow (D-17). | D-17; M1 |
| PJ-03 | §1 ¶9-11 | انتهاء الميعاد = بدء الميعاد + المدة، المدة من «إعدادات مواعيد الطعن» حسب الدرجة ونوع الدعوى | `DeadlineCalculator` (CALENDAR/WORKING, holidays, counts_start_day, roll_forward) and `deadline` (V680/V681). No level, appeal-type or case-type link. | Due date = start + days of the approved setting version, by the existing calculator, only through G-CALC; immutable calculation evidence (05 §C.5). | D-01, D-02, D-03, D-04 BLOCKED; G-CALC; M2 |
| PJ-04 | §1 ¶12 | تحديد الحالة الإجرائية للحكم وفق نتيجته | None. The old `judgment_status` (ISSUED/NOTIFIED/CHALLENGED/OTHER, V621:70-75) is a different concept. | `judgment_procedural.procedural_status` (separate table, 05 §C.2); the old field is untouched. | M1 |
| PJ-05 | §1 ¶13 | احتساب مستقل لكل طرف؛ تاريخ بدء مختلف بحسب الإعلان | One `deadline` per judgment can be named by `judgment.appeal_deadline_id` (V843); `deadline.owner_id` is the responsible member, not a party. No per-party deadline. | `judgment_party_deadline` rows for CITRA and OPPONENT (05 §C.3); legacy pointer kept. | D-05 BLOCKED (opponent), D-06; M1 (draft), M2 |
| PJ-06 | §2أ ¶16-21 | لصالح الهيئة: «في انتظار انقضاء ميعاد الطعن»؛ عند الانتهاء يطلب النظام تأكيد الباحث؛ لا نهائي تلقائيًا؛ بعد التأكيد والتحقق ← «حكم نهائي» ثم تنفيذ أو إغلاق؛ عند طعن الخصم تُفتح الدرجة التالية وصفة الهيئة «مستأنف ضدها» / «مطعون ضدها» | Enforcement module exists; `judgment.final` is informational (V302). No confirmation workflow. | Workflow service per the matrices in 03 §3; next stage through the existing `ProceedingService`; routing to enforcement/closure is offered, not forced. | D-07, D-12 BLOCKED; G-FINAL; M2 |
| PJ-07 | §2ب ¶23-29 | ضد الهيئة: «جاري إعداد الطعن»؛ الطعن قبل الانتهاء يفتح الدرجة التالية بصفة «مستأنفة» / «طاعنة»؛ عدم الطعن يلزم سببًا وصاحب الموافقة وتاريخ القرار في «قرارات الطعن»؛ لا نهائية تلقائية ما دام ميعاد الخصم قائمًا أو له طعن؛ بعد الانتهاء والتأكيد ← «حكم نهائي (قبول الحكم)» | None. | `appeal_decision` (maker-checker, 03 §2); finality service (05 §C.6). | D-07 BLOCKED, D-09, D-21; G-FINAL; M2 |
| PJ-08 | §2ج ¶31-41 | جزئيًا: يُعامل كالصادر ضد الهيئة؛ طعن مستقل لكل طرف على الحكم ذاته؛ أحدهما لا يمنع الآخر؛ ست حالات | None. | Two `judgment_appeal` filings per judgment, one per party (05 §C.4); the six combinations as one parametrised suite. | D-08; M1/M2 |
| PJ-09 | §3 ¶43 | يُمنع تحويل الحكم إلى «نهائي» تلقائيًا بانتهاء الميعاد؛ تأكيد يدوي من الباحث | `judgment.final` is a plain boolean that any editor with JUDGMENT_MANAGE can set (V302; informational). | Finality only through the explicit gated endpoint; the legacy boolean is kept in step inside the same transaction (05 §C.6). | D-07 BLOCKED; G-FINAL; M2 |
| PJ-10 | §3 ¶44 | «تاريخ بدء ميعاد الطعن» قابل للتعديل، افتراضيًا تاريخ الحكم، ويمكن تغييره إلى تاريخ الإعلان | `deadline.starts_on` exists; no basis field. | `start_basis` (JUDGMENT_DATE/NOTIFICATION_DATE) on the party row; default = judgment date as a draft prefill (a fact, not a period). | D-05; M1 |
| PJ-11 | §3 ¶45 | لا مدد ثابتة في الكود | Compliant by design (D6: no seeded rule, `V680`). | Static check plus data test. | M1+ |
| PJ-12 | §3 ¶46-50 | جدول «قرارات الطعن»: القرار، السبب، صاحب الموافقة، تاريخ القرار | None. | `appeal_decision` (05 §C.3). | D-09; M2 |
| PJ-13 | §3 ¶51-55 | كل تغيير في الحالة الإجرائية يُسجَّل: المستخدم، التاريخ والوقت، السابقة، الجديدة | `audit_event` exists. | `judgment_status_history` append-only plus an audit event. | M1 |
| PJ-14 | §3 ¶56-58 | الفصل بين قرار الهيئة (طعن / عدم طعن / لم يُحسم) والوضع القضائي (ميعاد قائم / طعن مقيد / نهائي) | None. | Three separate columns in `judgment_procedural`: citra_decision, opponent_position, judicial_status. | M1 |
| PJ-15 | §3 ¶59 | قبول الهيئة للحكم ليس دليل نهائية | None. | Finality precondition. | D-07; M2 |
| PJ-16 | §3 ¶60 | إعادة الاحتساب: سبب إلزامي، حفظ البيانات السابقة والجديدة، المستخدم والوقت | `deadline.change_reason` exists for one change; no history. | Recalculation = new append-only evidence record with reason, maker-checker (05 §C.5). | D-09; G-CALC; M2 |
| PJ-17 | §5 ¶99-106 | الحالات: في انتظار انقضاء ميعاد الطعن، جاري إعداد الطعن، منظورة استئناف، منظورة تمييز، حكم نهائي، حكم نهائي (قبول الحكم)؛ موقف كل طرف مستقل | None (case status and old judgment status are different). | Status codes and matrix in 03 §3.1, enforced server-side. | D-21; M1 (statuses), M2 |
| PJ-18 | §6 ¶108-120 | بيانات كل درجة: الدرجة، رقم الدعوى/الطعن، المحكمة، الدائرة، صفة الهيئة، المنطوق، التاريخ، النتيجة، بدء الميعاد، المدة المطبقة (منقولة وقت التسجيل)، الانتهاء، الطرف الطاعن | `proceeding` holds stage, number, year, court, filing date, parent (V301); chamber and judgment data elsewhere. CITRA capacity missing. | Nullable `proceeding.citra_capacity` (catalogue code) and the applied-duration snapshot on the evidence record. | D-12; M1 |
| PJ-19 | §6 ¶121-129 | أكثر من طعن على الحكم الواحد، لكل طعن: الطرف الطاعن، النوع، الرقم، تاريخ القيد، المحكمة والدائرة، صفة الهيئة، المستندات؛ مرتبط بالحكم الأصلي دون فقد بيانات | None. | `judgment_appeal` + `judgment_appeal_document`; mapping to child `proceeding` (05 §C.4). | M1 |
| PJ-20 | §8 ¶149-165 | التطوير على الوظائف الحالية دون إلغاء أي منها؛ ترابط الحكم والطعن والمواعيد؛ اختبار الحالات الإحدى عشرة؛ لا اعتماد قبل صحة الانتقالات ودقة الاحتساب وسلامة البيانات | Existing suites at 18822a8 (backend/frontend). | Regression and release gate, split into six checks. | all milestones |

| ID | Acceptance criteria | Test IDs |
|---|---|---|
| PJ-01 | 1 first-instance judgment offers only «استئناف»; 2 appeal judgment only «تمييز»; 3 cassation judgment offers no next stage; 4 a level without an approved setting offers none and shows a warning. | `T-PJ01-1` offer matrix by stage with approved settings<br>`T-PJ01-2` no offer after the highest level / without approved setting |
| PJ-02 | 1 a judgment registered with operative text, date and one of the three outcomes persists them; 2 existing rows are unchanged; 3 DISMISSED/OTHER rows get no procedural record. | `T-PJ02-1` register with each of the three outcomes persists operative_text/date/outcome<br>`T-PJ02-2` legacy DISMISSED/OTHER judgments unchanged, no procedural row<br>`T-PJ02-3` schema assertion: no second operative-text column |
| PJ-03 | 1 with an approved setting the due date equals the independently computed reference for each counting case; 2 without one no due date and no row is produced; 3 the duration is read from the setting row. | `T-PJ03-1` computation matrix vs reference table (calendar/working, holiday, boundary)<br>`T-PJ03-2` gate closed → no due date, no persistent side effect<br>`T-PJ03-3` duration read from settings, not from code (static + data test) |
| PJ-04 | 1 registering a judgment sets the status the transition matrix gives for its outcome; 2 the old status is never written. | `T-PJ04-1` status by outcome at registration<br>`T-PJ04-2` old judgment_status untouched |
| PJ-05 | 1 two rows with different start dates exist independently; 2 editing one leaves the other unchanged; 3 a legacy judgment keeps its `appeal_deadline_id` and shows it as a previous record. | `T-PJ05-1` two independent party rows<br>`T-PJ05-2` different start dates<br>`T-PJ05-3` legacy pointer unchanged and displayed |
| PJ-06 | 1 favourable registration → «في انتظار…»; 2 at expiry an alert and a confirmation request, status unchanged; 3 confirmed «no appeal» + approved finality → «حكم نهائي»; 4 opponent appeal → child proceeding with capacity «مستأنف ضدها» (appeal) or «مطعون ضدها» (cassation). | `T-PJ06-1` favourable, no opponent appeal, finality path<br>`T-PJ06-2` favourable, opponent appeals, capacity set<br>`T-PJ06-3` expiry leaves status; confirmation requested<br>`T-PJ06-4` routing offered, not forced |
| PJ-07 | 1 CITRA appeal → child proceeding with capacity «مستأنفة»/«طاعنة»; 2 non-appeal needs reason, approver (≠ preparer) and date, else refused with no change; 3 non-appeal while the opponent period runs or an opponent appeal exists leaves the judgment non-final; 4 all periods elapsed + no appeal + confirmation + approved finality → «حكم نهائي (قبول الحكم)». | `T-PJ07-1` CITRA appeal path and capacity<br>`T-PJ07-2` non-appeal mandatory fields and approver ≠ preparer<br>`T-PJ07-3` non-appeal not final while opponent runs/appeals<br>`T-PJ07-4` final (accept) after all conditions |
| PJ-08 | Each of the six combinations (CITRA only; opponent only; both; CITRA accepts + opponent appeals; CITRA appeals + opponent not; neither) reaches the state of the matrix; one filing never blocks the other. | `T-PJ08-1` CITRA appeals alone<br>`T-PJ08-2` opponent appeals alone<br>`T-PJ08-3` both appeal<br>`T-PJ08-4` CITRA accepts, opponent appeals<br>`T-PJ08-5` CITRA appeals, opponent does not<br>`T-PJ08-6` neither appeals<br>`T-PJ08-7` concurrent filings by both parties both succeed (race test) |
| PJ-09 | 1 a scheduler run after expiry changes no status; 2 the finality endpoint without confirmation/approval/gate is refused and changes nothing. | `T-PJ09-1` job run after expiry changes nothing<br>`T-PJ09-2` finality refused without conditions; table digests unchanged |
| PJ-10 | 1 default is the judgment date; 2 an authorised user changes it to the notification date with audit; 3 an unauthorised change is refused and nothing changes. | `T-PJ10-1` default start = judgment date<br>`T-PJ10-2` authorised change audited<br>`T-PJ10-3` unauthorised change refused |
| PJ-11 | No numeric appeal/grievance period in main code or migrations except inactive DRAFT seeds. | `T-PJ11-1` static scan finds no period literal<br>`T-PJ11-2` durations come from setting rows only |
| PJ-12 | 1 the four fields are stored; 2 a missing field is refused and no row is written. | `T-PJ12-1` four fields stored<br>`T-PJ12-2` missing field refused, no side effect |
| PJ-13 | 1 each change writes one history row with user, time, from, to; 2 history rows cannot be updated or deleted. | `T-PJ13-1` history row per change<br>`T-PJ13-2` history append-only (update/delete refused) |
| PJ-14 | Changing any one of the three leaves the other two untouched. | `T-PJ14-1` independence of the three positions |
| PJ-15 | A CITRA non-appeal decision never finalises while the opponent period runs or an opponent appeal exists. | `T-PJ15-1` non-appeal + running opponent period → not final<br>`T-PJ15-2` non-appeal + opponent appeal → not final |
| PJ-16 | 1 recalculation without a reason is refused; 2 both the previous and the new evidence remain; 3 an unauthorised user is refused with no change. | `T-PJ16-1` reason mandatory<br>`T-PJ16-2` before/after evidence kept<br>`T-PJ16-3` unauthorised recalculation refused, no side effect |
| PJ-17 | 1 every allowed transition works; 2 every transition outside the matrix is refused with a stable rule code and no side effect. | `T-PJ17-1` allowed transitions<br>`T-PJ17-2` forbidden transitions refused, no side effect |
| PJ-18 | 1 stage data complete; 2 the applied duration is a snapshot and does not change when the setting changes. | `T-PJ18-1` stage data and capacity stored<br>`T-PJ18-2` snapshot unchanged after setting change |
| PJ-19 | 1 several filings per judgment with independent data; 2 each maps to a child proceeding (own or shared) through the ladder service; 3 a voided filing stays in history. | `T-PJ19-1` independent filings<br>`T-PJ19-2` filing ↔ proceeding mapping own/shared<br>`T-PJ19-3` voided filing retained |
| PJ-20 | 1 the existing backend suite passes unchanged; 2 the existing frontend suite passes; 3 digests of every pre-existing table are identical before/after migration; 4 case registration, proceedings and archive journeys pass; 5 each of the eleven source scenarios maps to a named test (checked by script); 6 no milestone is accepted before transitions, calculation and data-safety evidence is recorded. | `T-PJ20-1` existing backend suite unchanged and green<br>`T-PJ20-2` existing frontend suite green<br>`T-PJ20-3` pre-existing table digests identical across migration<br>`T-PJ20-4` case/proceeding/archive journeys<br>`T-PJ20-5` script: each of the 11 §8 scenarios mapped to test ids<br>`T-PJ20-6` milestone evidence checklist complete |

## Module B — «إعدادات مواعيد الطعن»

| ID | Source | Expected behaviour (source) | Existing baseline | Proposed R6 | Dependency / legal status |
|---|---|---|---|---|---|
| ST-01 | §4.1 ¶63 | تظهر لمدير النظام أو مدير إدارة الشؤون القانونية فقط | Roles ADMIN, LEGAL_MANAGER exist (V100/V600). ADMIN receives every `Permission` value (LawdeskUser.java:36-38). | `APPEAL_SETTINGS_MANAGE` granted to those two roles; managing a setting is not legal approval (`APPEAL_SETTINGS_APPROVE`, 05 §A). | D-09; M2 |
| ST-02 | §4.2 ¶65-73 | إضافة وتعديل وإيقاف؛ حقول: درجة الحكم، نوع الطعن، نوع الدعوى (أو الكل)، المدة، بداية الاحتساب الافتراضية، السند القانوني، الحالة، تاريخ السريان | `deadline_rule` lacks level, appeal type, case type, basis (V680). | New versioned `deadline_setting` (kind APPEAL). | D-01; M2 |
| ST-03 | §4.3 ¶75-91 | قيم مبدئية: أول درجة/استئناف/عادية/30؛ أول درجة/استئناف/مستعجلة/15؛ استئناف/تمييز/الكل/30 — تُراجَع وتُعتمد قبل التفعيل | None (D6: no seeded rule). | Three DRAFT rows, no approval record; activation impossible without `legal_approval` (DB trigger, 05 §D). | D-01 BLOCKED; G-SETTING; M2 |
| ST-04 | §4.4 ¶93 | لا سجلان مفعلان لنفس الدرجة ونوع الدعوى في الفترة ذاتها متى كانا متعارضين | None. | Overlap rule of 05 §E (trigger + advisory lock + exclusion backstop, half-open ranges). | D-16 PROVISIONAL+legal; M2 |
| ST-05 | §4.4 ¶94 | أي تعديل في المدة يسري على الأحكام الجديدة فقط | — | Evidence snapshot per deadline (05 §C.5). | M2 |
| ST-06 | §4.4 ¶95 | تدقيق كل تعديل: المستخدم، الوقت، القيمة قبل وبعد | `audit_event` exists. | Before/after payload in audit and in setting history. | M2 |
| ST-07 | §4.4 ¶96 | لا إعداد مطابق: تنبيه + مدة يدوية + السند والسبب + إشعار مدير النظام | None. | Warning and a **non-effective proposal** only; effective manual duration needs an approved override policy and per-use maker-checker (05 §D). | D-13 BLOCKED; G-OVERRIDE; M2 |
| ST-08 | §4.4 ¶97 | الاحتفاظ بالإعدادات السابقة وعدم حذفها | Pattern: `lookup_value_history`. | Versioned rows, no delete endpoint, FK from evidence to the version used. | M2 |

| ID | Acceptance criteria | Test IDs |
|---|---|---|
| ST-01 | 1 ADMIN and LEGAL_MANAGER can manage; 2 other roles get 403 on every endpoint with no change; 3 ADMIN cannot approve a setting legally. | `T-ST01-1` manage allowed for the two roles only<br>`T-ST01-2` real ADMIN principal cannot approve a setting |
| ST-02 | 1 create/update/deactivate with all fields; 2 validation refuses missing legal reference or non-positive days; 3 every change creates a new version. | `T-ST02-1` create/update/deactivate<br>`T-ST02-2` validation<br>`T-ST02-3` version per change |
| ST-03 | 1 a fresh database has three DRAFT rows and no approval; 2 no judgment can use them; 3 direct SQL or API activation without approval is refused. | `T-ST03-1` seeds are DRAFT, unusable<br>`T-ST03-2` activation without approval refused at API and DB level |
| ST-04 | 1 ALL vs specific overlap refused; 2 adjacent half-open ranges allowed; 3 identical-scope overlap refused; 4 concurrent activations: exactly one succeeds. | `T-ST04-1` ALL vs specific refused<br>`T-ST04-2` adjacent ranges allowed<br>`T-ST04-3` same-scope overlap refused<br>`T-ST04-4` two concurrent activations → one wins |
| ST-05 | Editing or superseding a setting leaves every existing deadline and evidence row byte-identical. | `T-ST05-1` digests of existing deadlines unchanged after setting edit |
| ST-06 | Every write has user, time, before and after. | `T-ST06-1` audit before/after on each write |
| ST-07 | 1 no match → warning, a proposal stored with no due date; 2 without an approved override policy a manual duration is refused and nothing is persisted; 3 with the policy and checker approval it is recorded with reason and legal reference and ADMIN users are notified. | `T-ST07-1` no match: warning, no due date<br>`T-ST07-2` override refused without policy, no side effect<br>`T-ST07-3` approved override recorded and admins notified |
| ST-08 | 1 old versions remain; 2 no delete path; 3 a historical deadline resolves the exact setting version. | `T-ST08-1` versions retained<br>`T-ST08-2` no delete<br>`T-ST08-3` historical resolution |

## Module C — post-judgment alerts

| ID | Source | Expected behaviour (source) | Existing baseline | Proposed R6 | Dependency / legal status |
|---|---|---|---|---|---|
| AL-01 | §7.1 ¶132-136 | قبل الانتهاء بـ10 و5 ويومين إلى الباحث المسؤول ومديره المباشر | `notification` (V664, dedupe_key unique), `DeadlineReminderRun` with lead days and no default; no manager relation in any migration. | Offsets 10/5/2 as configuration; recipients = researcher; direct manager only under an approved recipient policy and an active mapping (04 B.1). | D-14 OWNER-PENDING; G-ALERT; M3 |
| AL-02 | §7 ¶137 | أيام التنبيه قابلة للتعديل من لوحة الإدارة | `deadline_reminder_setting` single global row. | Separate offset sets for APPEAL and GRIEVANCE. | M3 |
| AL-03 | §7.2 ¶139-140 | تنبيه عند انتهاء الميعاد دون تأكيد الباحث، دون تغيير الحالة | Overdue reminder exists for deadlines generally. | Alert type EXPIRED_UNCONFIRMED. | M3 |
| AL-04 | §7.3 ¶142 | تنبيه عاجل: ضد الهيئة، يومان أو أقل، لا طعن ولا قرار معتمد بعدم الطعن | None. | Alert type URGENT. | M3 |
| AL-05 | §7.4 ¶144-145 | إيقاف تنبيهات الطرف الذي سُجل طعنه أو حُسم بعدم الطعن، واستمرار الآخر | None. | Alerts keyed per party deadline; `resolution` suppresses (05 §F). | M3 |
| AL-06 | §7.5 ¶147 | سجل التنبيهات: النوع، الوقت، المستلمون، القضية والحكم | `notification` (V664). | `alert_history` with a unique natural key; lifecycle in 05 §F. | D-15; M3 |

| ID | Acceptance criteria | Test IDs |
|---|---|---|
| AL-01 | 1 researcher receives one alert per offset; 2 with policy DISABLED or no mapping no manager receives anything; 3 with policy enabled and mapping, the manager receives alerts only for records in the manager's scope. | `T-AL01-1` researcher alerts at 10/5/2<br>`T-AL01-2` policy disabled → no manager recipient<br>`T-AL01-3` enabled + mapping → in-scope manager only |
| AL-02 | 1 offsets change from the admin screen; 2 a change creates no duplicates and affects only offsets not yet raised. | `T-AL02-1` offsets editable<br>`T-AL02-2` offset change: no duplicates, future only |
| AL-03 | An expired deadline without confirmation raises one alert; the status is unchanged. | `T-AL03-1` expiry alert, status unchanged |
| AL-04 | 1 raised when outcome is AGAINST, ≤2 days remain and neither an appeal nor an approved non-appeal exists; 2 not raised otherwise. | `T-AL04-1` urgent conditions met<br>`T-AL04-2` not raised when appeal or approved non-appeal exists |
| AL-05 | 1 after a party's appeal no further alert for that party; 2 the other party's alerts continue; 3 an approved non-appeal decision suppresses that party only. | `T-AL05-1` suppression after appeal<br>`T-AL05-2` other party continues<br>`T-AL05-3` approved non-appeal suppresses own party only |
| AL-06 | 1 history shows kind, time, recipients, case, judgment, party deadline, delivery status; 2 retry, restart and two instances create no duplicate; 3 a recipient whose access was revoked receives no new alert and does not see old ones. | `T-AL06-1` history fields<br>`T-AL06-2` idempotent under retry/restart/two instances<br>`T-AL06-3` revoked recipient: none new, old hidden |

## Module D — prosecution complaints

| ID | Source | Expected behaviour (source) | Existing baseline | Proposed R6 | Dependency / legal status |
|---|---|---|---|---|---|
| PC-01 | ¶168 | النطاق: من تقديم الشكوى إلى قرار النيابة (حفظ أو إحالة) | None. | New aggregate `prosecution_complaint`, distinct from the case. | M4 |
| PC-02 | §ثانيًا-1 ¶171-180 | تقديم الشكوى: رقم داخلي تلقائي، رقم الشكوى والسنة، النيابة المختصة، تاريخ التقديم، مشكو في حقه (أكثر من واحد)، ملخص الواقعة وتاريخها، التكييف القانوني، الباحث المسؤول، النسخة والمرفقات | Documents module, party identifier handling exist; no complaint data. | `prosecution_complaint`, `complaint_respondent`, document links; internal reference generated by a sequence. | D-18 OWNER-PENDING, D-19; M4 |
| PC-03 | ¶181 | الحالة بعد الحفظ: «مقدمة للنيابة» | None. | Initial status set on save. | M4 |
| PC-04 | §2 ¶183 | جلسات التحقيق: التاريخ، الحاضرون من الهيئة، ما تم، الموعد التالي، المطلوب قبله | None. | `complaint_session`. | M4 |
| PC-05 | ¶184 | طلبات النيابة (مستندات / تقارير فنية / شهود)، تاريخ الطلب، تاريخ الرد | None. | `complaint_request`. | M4 |
| PC-06 | ¶185-186 | إرفاق المستندات والمذكرات؛ الحالة «قيد التحقيق بالنيابة» | `case_document` (V403) is case-bound. | Complaint-owned documents (05 §B.4); status change per D-22. | D-20, D-22; M4 |
| PC-07 | §3أ ¶189-190 | الحفظ: سبب الحفظ، تاريخ القرار، تاريخ علم الهيئة، نسخة القرار؛ الحالة «محفوظة» | None. | `complaint_decision` kind SAVED. | M5 |
| PC-08 | ¶191, ¶203 | يحسب النظام آخر ميعاد للتظلم ويقرأ المدة من «إعدادات المواعيد» | None. | `deadline_setting` kind GRIEVANCE with the appeal-setting fields; calculated only through G-CALC. | D-10 BLOCKED; G-CALC; M5 |
| PC-09 | ¶192 | تظلم / عدم تظلم؛ عدم التظلم يلزم سببًا وصاحب الموافقة، وبعدها تُغلق الشكوى | None. | `complaint_grievance` (status PENDING/DISPOSED) and non-grievance decision with maker-checker; closure by explicit action only. | D-09, D-11 BLOCKED (post-grievance); M5 |
| PC-10 | §3ب ¶194-195 | الإحالة: تاريخ قرار الإحالة، المحكمة المختصة، رقم القضية، التهمة/المواد؛ الحالة «محالة للمحكمة» | None. | `complaint_referral`. | M5 |
| PC-11 | ¶196 | فتح قضية مرتبطة تلقائيًا، نقل الأطراف والوقائع والمستندات، صفة الهيئة «مجني عليها / شاكية» | Intake requires automated no., title, case type, opened date, first-instance court/number/year/filing date, position, primary member, financial impact (IntakeRequest.java:24-43,56-69). | Referral adapter and confidentiality inheritance (05 §B): the case is created RESTRICTED in the same transaction; mandatory intake fields come from the user, never invented; incomplete data leaves the case PENDING_DATA. | D-12, D-20, D-25; M5 |
| PC-12 | ¶197 | بعدها تكمل القضية الدورة العادية بما فيها منطق ما بعد الحكم والطعن | Normal case cycle exists. | Linked case uses Module A unchanged. | D-12; M5 |
| PC-13 | §3 ¶199 | الحالات: مقدمة للنيابة، قيد التحقيق بالنيابة، محفوظة، محالة للمحكمة، مغلقة | None. | Status codes and matrix in 03 §3.3. | D-22; M4/M5 |
| PC-14 | §4-1 ¶201 | المحفوظة لا تُغلق قبل انتهاء ميعاد التظلم أو تسجيل قرار بعدم التظلم | None. | Closure preconditions with a grievance-pending guard (03 §3.3). | D-11 BLOCKED; M5 |
| PC-15 | ¶202 | الربط بين الشكوى والقضية في الاتجاهين | `case_link` (V663) links cases to cases. | One authoritative link row; both directions derived; reverse navigation filtered by the viewer's access (05 §B.3). | M5 |
| PC-16 | ¶203 | ميعاد التظلم لا يُكتب داخل الكود | Compliant by design (D6). | Static check. | M5 |
| PC-17 | ¶204 | تدقيق كل تغيير حالة: من، متى، من أي حالة إلى أي حالة | `audit_event` exists. | `complaint_status_history` append-only plus audit. | M4 |
| PC-18 | §5 ¶206 | تنبيه قبل جلسات التحقيق بعدد أيام قابل للضبط | None. | Alert type with configurable days. | M3/M4 |
| PC-19 | ¶207 | طلبات النيابة بلا رد خلال مدة قابلة للضبط | None. | Alert type. | M4 |
| PC-20 | ¶208 | شكوى قيد التحقيق بلا أي إجراء مسجل مدة قابلة للضبط | None. | Alert type; activity definition per D-23. | D-23 PROVISIONAL; M4 |
| PC-21 | ¶209 | قبل انتهاء ميعاد التظلم بـ5 أيام ثم يومين إلى الباحث ومديره المباشر | None. | As AL-01 for grievance deadlines. | D-14, D-10 BLOCKED; M5 |

| ID | Acceptance criteria | Test IDs |
|---|---|---|
| PC-01 | 1 a complaint exists and moves through its own statuses with no case; 2 its transitions never change a case status. | `T-PC01-1` complaint lives without a case<br>`T-PC01-2` complaint transitions leave case status untouched |
| PC-02 | 1 submission with several respondents persists all fields; 2 the internal reference is unique and automatic; 3 respondent identifiers follow the field-level protection decided in D-18. | `T-PC02-1` submit with several respondents<br>`T-PC02-2` internal reference unique under concurrency<br>`T-PC02-3` identifier protection per D-18 |
| PC-03 | A saved complaint has status «مقدمة للنيابة» and a status-history row. | `T-PC03-1` initial status and history row |
| PC-04 | Several sessions are stored and listed in order; none replaces another. | `T-PC04-1` several sessions kept |
| PC-05 | Requests with request and response dates; a pending one is listed as awaiting response. | `T-PC05-1` request and response dates, pending listing |
| PC-06 | 1 a document attaches to the complaint, never to a case by this action; 2 download/preview/OCR text obey complaint visibility. | `T-PC06-1` attach and read complaint document<br>`T-PC06-2` hidden complaint document: 404 identical to unknown id |
| PC-07 | A preservation decision stores reason, date, awareness date and document and sets «محفوظة» with history. | `T-PC07-1` preservation stored, status set |
| PC-08 | 1 with an approved grievance setting the deadline equals the reference computation; 2 without it no grievance deadline is produced. | `T-PC08-1` grievance deadline reference computation<br>`T-PC08-2` no approved setting → none, no side effect |
| PC-09 | 1 a non-grievance decision needs reason and approver (≠ preparer); 2 a filed grievance sets PENDING and blocks closure until disposed; 3 closure after a non-grievance decision is an explicit action. | `T-PC09-1` non-grievance mandatory fields and approver<br>`T-PC09-2` filed grievance PENDING blocks closure<br>`T-PC09-3` closure is explicit |
| PC-10 | Referral data stored, status «محالة للمحكمة», history row. | `T-PC10-1` referral stored and status set |
| PC-11 | 1 complete referral creates exactly one RESTRICTED linked case; 2 retry or concurrent referral returns the same case; 3 missing mandatory intake data creates no case and no invented value; 4 a user who sees the case but not the complaint sees no complaint trace. | `T-PC11-1` complete referral → one restricted case<br>`T-PC11-2` retry/concurrency → same single case<br>`T-PC11-3` incomplete data → no case, no invented values<br>`T-PC11-4` case-only viewer sees no complaint trace |
| PC-12 | A judgment registered on the linked case follows Module A with CITRA capacity «مجني عليها / شاكية». | `T-PC12-1` post-judgment flow on a linked case |
| PC-13 | Allowed transitions work; forbidden ones are refused with a stable rule code and no side effect. | `T-PC13-1` allowed transitions<br>`T-PC13-2` forbidden transitions refused, no side effect |
| PC-14 | 1 closure refused before the deadline unless a non-grievance decision exists; 2 refused while a grievance is PENDING; 3 never closed by a timer. | `T-PC14-1` closure preconditions<br>`T-PC14-2` grievance PENDING blocks closure<br>`T-PC14-3` scheduled job never closes |
| PC-15 | 1 navigation works both ways for a viewer entitled to both; 2 a viewer entitled to only one side gets no identifier of the other; 3 the two directions cannot disagree. | `T-PC15-1` both-way navigation<br>`T-PC15-2` one-side viewer gets no identifier<br>`T-PC15-3` reciprocity cannot be violated (constraint test) |
| PC-16 | No grievance period literal in code. | `T-PC16-1` static scan: no grievance period literal |
| PC-17 | Each transition writes one history row; rows are immutable. | `T-PC17-1` history row per transition, append-only |
| PC-18 | An alert before a recorded next session on the configured day, to recipients in complaint scope only. | `T-PC18-1` alert before session, in-scope recipients |
| PC-19 | A request without response after the configured days raises one alert. | `T-PC19-1` overdue prosecution request alert |
| PC-20 | A complaint with no qualifying activity for the configured period raises one alert. | `T-PC20-1` inactivity alert per D-23 definition |
| PC-21 | Alerts at 5 and 2 days to the researcher; manager only under the approved policy; none when the grievance is filed or a non-grievance decision exists. | `T-PC21-1` 5 and 2 day alerts<br>`T-PC21-2` suppressed after grievance or non-grievance<br>`T-PC21-3` manager policy honoured |

## Cross-cutting tests (referenced from 03, 04 and 05)

| Test ID | Proves |
|---|---|
| `T-SEC-1` | real ADMIN principal (LawdeskUser.of with ADMIN role) lacks every sensitive R6 authority |
| `T-SEC-2` | ADMIN keeps every pre-existing permission (set equal to the develop snapshot) |
| `T-SEC-3` | every Permission value must declare adminInherited; unclassified value fails the build |
| `T-SEC-4` | ADMIN who also holds LEGAL_MANAGER gets the union, and the union is the only way to hold a sensitive authority |
| `T-SEC-5` | sensitive grant needs two distinct identities (requester ≠ custodian approver); self-grant refused |
| `T-SEC-6` | revocation takes effect on the next request; revoked between page load and submit → 403 and no write |
| `T-SEC-7` | delegation: in scope and window works; out of scope, expired, revoked delegator, or sub-delegation refused |
| `T-SEC-8` | grant/revoke/delegation each write audit rows with before/after and actor |
| `T-SEC-9` | maker-checker: approver = preparer refused; approver without authority or case scope refused; every refusal leaves all tables unchanged |
| `T-SEC-10` | out-of-scope, revoked and stale principals: 403/404 per policy with no write |
| `T-CONF-1` | complaint read/list/search: ADMIN without scope sees nothing |
| `T-CONF-2` | complaint documents: download, preview, OCR text, search hit hidden with 404 identical to unknown id |
| `T-CONF-3` | worklists and dashboard counts exclude hidden complaints and never show 'N hidden' |
| `T-CONF-4` | alerts/notifications: none created for out-of-scope users; payload has no respondent name; presentation filter hides after revocation |
| `T-CONF-5` | audit viewer, exports and reports exclude hidden complaints |
| `T-CONF-6` | linked case created RESTRICTED atomically; failure rolls back case, link and copies |
| `T-CONF-7` | case-only viewer: no complaint id, number, link, event text, party provenance or document provenance in case DTO, timeline, search, report, dashboard, notification |
| `T-CONF-8` | complaint-only viewer: referral status visible, case identifier and details not |
| `T-CONF-9` | task assignee on the linked case sees only the transferred manifest, never complaint-only material |
| `T-CONF-10` | declassification of the linked case needs COMPLAINT_APPROVE and is audited; never automatic |
| `T-DATA-1` | legacy judgment with appeal_deadline_id and deadline row: unchanged, shown as legacy, adoptable as a party deadline with reason |
| `T-DATA-2` | extension row must point to a JUDGMENT deadline of the same judgment (trigger test) |
| `T-DATA-3` | party deadline ↔ deadline 1:1 and uniqueness per (judgment, party, appeal type) among non-void |
| `T-DATA-4` | race: appeal filing vs finality on two connections: exactly one order, no final judgment with a non-void filing |
| `T-DATA-5` | race: two parties file simultaneously: both succeed; same party twice: one row, replay idempotent |
| `T-DATA-6` | lock order proceeding → judgment → procedural → party deadlines → children; deadlock-free under 50 concurrent mixed operations |
| `T-DATA-7` | legacy DeadlineReminderRun excludes extension-backed deadlines; no duplicate reminders; legacy deadlines still reminded |
| `T-DATA-8` | calendar and judgment reports show party deadlines once, legacy deadlines unchanged |
| `T-DATA-9` | case_position reflects the highest child proceeding after filings; unchanged for cases without R6 data |
| `T-DATA-10` | legacy judgment.final stays equal to finality state after a finality transaction |
| `T-DATA-11` | evidence rows are append-only; later holiday edit does not change stored evidence; recomputation from stored inputs reproduces due date |
| `T-GATE-1` | each gated action refused with LEGAL_GATE_CLOSED when no approval record: all R6 tables, deadline, notification, audit_event and case_position digests identical before/after |
| `T-GATE-2` | seed data cannot activate: DRAFT settings unusable |
| `T-GATE-3` | direct API call and direct SQL cannot set a setting ACTIVE without an approval record (DB trigger) |
| `T-GATE-4` | manual duration, fallback and default values never produce an effective due date |
| `T-GATE-5` | background jobs skip unapproved deadlines and write nothing |
| `T-GATE-6` | approval record is versioned: a changed setting version needs a new approval; revoked approval closes the gate again |
| `T-GATE-7` | approval requires authoritative identity with a legal grant, approved scope, effective dates; approver ≠ setting author |
| `T-ALERT-1` | unique natural key: retry, restart and two instances create one alert |
| `T-ALERT-2` | recalculation (new evidence version) creates fresh alerts; offset change adds only future offsets |
| `T-ALERT-3` | manager policy flip does not back-fill past offsets |
| `T-ALERT-4` | missing mapping: researcher only, data-quality notice to ADMIN without case details |
| `T-MIG-1` | fresh PostgreSQL 18 database migrates |
| `T-MIG-2` | restored synthetic pre-milestone dump migrates; every pre-existing table digest unchanged except documented additive columns |
| `T-MIG-3` | previous release jar starts on the migrated database, logs in, runs case/judgment/deadline CRUD smoke and one reminder-job cycle with R6 tables populated |
| `T-MIG-4` | second start applies no migration |
| `T-MIG-5` | installation orders: develop+R6; develop→R5→R6; develop→R6→R5; each starts and passes the smoke (only if R5 is later accepted) |
| `T-MIG-6` | restore drill and forward-recovery rehearsal (failed step leaves no partial R6 data) |
| `T-R5-1` | referral adapter compiles and passes on develop and on develop+R5 intake signatures |
| `T-R5-2` | legacy /deadlines link behaviour on both bases |
