# سجل تتبع المتطلبات — وثيقة LawDesk الشاملة (R3)

مولَّد آليًا من `lawdesk-comprehensive-source.txt` بواسطة `build_matrix.py` و`register_data.py` (لا يُعدَّل يدويًا). المصدر وبصمته في `SOURCE.md`.

## ثلاثة أعمدة منفصلة

- **IMPLEMENTED** (`YES` / `PARTIAL` / `NO` / `BLOCKED`): هل الوظيفة موجودة في الشيفرة المنشورة. `PARTIAL` يعني أن جزءًا مستقلًا منفذ والباقي مذكور في الملاحظة؛ `BLOCKED` لا جزء مستقلًا منفذًا منه.
- **VERIFIED_BY_DEVELOPER**: فارغ، أو الدليل الدقيق: صنف اختبار (أو `Class#method`) وSHA الذي أخضر عليه CI (`cac8d73`)، أو رحلة UAT واسم الخطوة وSHA النشر المسجل داخل ملف الدليل. لا يُملأ إلا لصف `YES`، وكل اسم يُفحص وجوده في المستودع عند التوليد. التحقق على مستوى الميزة: الاختبار/الخطوة يُنشئ السجل ويقرؤه أو يمارس القاعدة، ولا يُدّعى أن كل حقل فُحص منفردًا.
- **QA_ACCEPTED**: فارغ دائمًا. لا يملؤه إلا مراجعة Codex المستقلة. لا شيء مقبول بعد.

الرحلات المرجعية أُجريت على إصدارات UAT أقدم من `cac8d73` (الإصدار في كل ملف دليل)؛ إعادة تشغيلها على الإصدار الحالي مطلوبة قبل أن يعتمد Codex الأدلة. انظر `RETEST-PACKAGE-FOR-CODEX.md`.

## الملخص (من المصفوفة المولَّدة)

- أسطر المصدر غير الفارغة: 791، كلها مغطاة: نعم
- متطلبات ذرية (صفوف): 650
- توزيع IMPLEMENTED: YES: 565، PARTIAL: 40، NO: 0، BLOCKED: 45
- توزيع VERIFIED_BY_DEVELOPER: بدليل: 520، فارغ: 130
- توزيع QA_ACCEPTED: مقبول: 0، فارغ: 650
- BLOCKED حسب السبب: EXTERNAL_PROVIDER: 44، OWNER_DECISION: 1
- PARTIAL حسب سبب ما تبقى: DATA_NOT_PROVIDED: 2، EXTERNAL_PROVIDER: 21، INFRA: 3، LEGAL_INPUT: 4، NONE: 2، OWNER_DECISION: 3، PERFORMANCE_ENGINEERING: 1، SPECIALIST: 1، TEST_NOT_RUN: 1، UI_NOT_BUILT: 2
- صفوف NO: لا شيء

الصفان اللذان كانا «غير موجود» في الجولة الأولى: `LD-DOC-00-001` و`LD-DOC-00-002` (الترابط بين الوحدات). أُغلقا في PR #86 باختبار `ConnectedCaseApiTest` ورحلة `connected_journey.py`؛ لا يلزم كود جديد.

## الصفوف المجزأة (ليست ضمن الـ650)

| الصف | الجزء | IMPLEMENTED | الدليل / السبب |
|---|---|---|---|
| LD-DOC-10-011 | محرك الاحتساب والاعتماد (قواعد اصطناعية) | YES | T: DeadlineCalculatorTest (CI green cac8d73)<br>J: wp4 «with an approved rule the system proposes a date; the proposal becomes official only when approved» (UAT 82c3285) [DEADLINE_ENGINE] |
| LD-DOC-10-011 | قواعد المدد القانونية والعطلات الرسمية | BLOCKED (LEGAL_INPUT, D6) | لا قاعدة ولا عطلة مُدخلة؛ لا اختراع |
| LD-DOC-10-012 | محرك الاحتساب والاعتماد (قواعد اصطناعية) | YES | T: DeadlineCalculatorTest (CI green cac8d73)<br>J: wp4 «with an approved rule the system proposes a date; the proposal becomes official only when approved» (UAT 82c3285) [DEADLINE_ENGINE] |
| LD-DOC-10-012 | قواعد المدد القانونية والعطلات الرسمية | BLOCKED (LEGAL_INPUT, D6) | لا قاعدة ولا عطلة مُدخلة؛ لا اختراع |
| LD-DOC-22-003 | تلخيص استخراجي بمصادر | YES | T: AssistantTasksApiTest (CI green cac8d73)<br>J: wp10 «the summary quotes the case's records with sources» (UAT 64e5416) [ASSISTANT_SUMMARY] |
| LD-DOC-22-003 | تلخيص توليدي بنموذج حقيقي | BLOCKED (EXTERNAL_PROVIDER, D8) | لا مزود معتمد |
| LD-DOC-34-014 | Chromium وFirefox | YES | E: results-summary.json, SUMMARY.md (DOC-UX run on frontend sources equal to develop 4d83267) [UX_ENGINES] |
| LD-DOC-34-014 | WebKit | BLOCKED (INFRA) | لا صورة Docker ولا render node للمستخدم |

## ملحق: حزم الأدلة

كل رمز بين قوسين في عمود VERIFIED_BY_DEVELOPER يشير إلى حزمة هنا بخطواتها الكاملة.

- **ACCESS_SCOPE**
  - اختبار خلفي: `CaseVisibilityApiTest` (CI أخضر cac8d73)
  - اختبار خلفي: `RolePermissionsTakeEffectTest` (CI أخضر cac8d73)
  - اختبار خلفي: `SessionRevocationTest` (CI أخضر cac8d73)
  - رحلة `DOC-WP1/journey-wp1-access.json` (إصدار UAT 8d7c847): «manager places the staff account in the legal section»؛ «the case belongs to the specialist's unit»؛ «the specialist sees the case»
- **ARCH_AUDIT**
  - اختبار خلفي: `ArchiveDeletionApiTest` (CI أخضر cac8d73)
  - اختبار خلفي: `AuditTrailTest` (CI أخضر cac8d73)
  - رحلة `DOC-WP5/journey-wp5.json` (إصدار UAT 2bcd2dc): «every step is in the history»
- **ARCH_BASIC**
  - اختبار خلفي: `DocumentApiTest` (CI أخضر cac8d73)
  - اختبار خلفي: `ArchiveFormatsAndSettingsApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-WP5/journey-wp5.json` (إصدار UAT 2bcd2dc): «a PDF is archived as version 1»؛ «a Word document is archived»؛ «an image is archived»
- **ARCH_CLASS**
  - اختبار خلفي: `ArchiveTextSearchApiTest` (CI أخضر cac8d73)
  - اختبار خلفي: `DocumentApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-WP5/journey-wp5.json` (إصدار UAT 2bcd2dc): «a document is classified by type, date and stage»؛ «the search can be limited to a category»
- **ARCH_DELETE**
  - اختبار خلفي: `ArchiveDeletionApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-WP5/journey-wp5.json` (إصدار UAT 2bcd2dc): «a permanent deletion is refused without the archive right»؛ «a document is withdrawn from the case file with a reason»
- **ARCH_FORMATS**
  - اختبار خلفي: `FileFormatsTest` (CI أخضر cac8d73)
  - اختبار خلفي: `ArchiveFormatsAndSettingsApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-WP5/journey-wp5.json` (إصدار UAT 2bcd2dc): «a file that is not what it claims is refused»؛ «a script is refused»
- **ARCH_LINK**
  - اختبار خلفي: `DocumentLinkApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-WP5/journey-wp5.json` (إصدار UAT 2bcd2dc): «a document is linked to the judgment it concerns»؛ «a record of another case cannot be linked»
- **ARCH_SEARCH**
  - اختبار خلفي: `ArchiveTextSearchApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-WP5/journey-wp5.json` (إصدار UAT 2bcd2dc): «the specialist finds the PDF by its text»؛ «the snippet is the matching line only»
- **ARCH_VERSIONS**
  - اختبار خلفي: `ArchiveVersionsApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-WP5/journey-wp5.json` (إصدار UAT 2bcd2dc): «a new version keeps the earlier ones»؛ «both versions list who uploaded them and when»؛ «the old version is still downloadable»
- **AUDIT**
  - اختبار خلفي: `AuditTrailTest` (CI أخضر cac8d73)
  - اختبار خلفي: `AuditViewTest` (CI أخضر cac8d73)
  - اختبار خلفي: `AuditTrailTest#theDatabaseRefusesToChangeOrDeleteAuditEvents` (CI أخضر cac8d73)
  - رحلة `DOC-WP1/journey-wp1-access.json` (إصدار UAT 8d7c847): «the audit trail records the grant»
- **AUDIT_VALUES**
  - اختبار خلفي: `AuditTrailTest` (CI أخضر cac8d73)
  - اختبار خلفي: `AuditViewTest` (CI أخضر cac8d73)
  - اختبار خلفي: `ActivityHistoryTest` (CI أخضر cac8d73)
  - رحلة `DOC-WP2/journey-wp2-uat.json` (إصدار UAT 4220ccf): «its history keeps the creation and the deactivation»؛ «the change records the user and the date»
- **AUTH**
  - اختبار خلفي: `LoginFlowTest` (CI أخضر cac8d73)
  - اختبار خلفي: `EndpointAuthorizationTest` (CI أخضر cac8d73)
  - اختبار خلفي: `SessionRevocationTest` (CI أخضر cac8d73)
  - اختبار خلفي: `PermissionContractTest` (CI أخضر cac8d73)
  - رحلة `DOC-WP1/journey-wp1-access.json` (إصدار UAT 8d7c847): «viewer is refused /timeline»؛ «viewer is refused /documents»
- **CALENDAR**
  - اختبار خلفي: `CalendarApiTest` (CI أخضر cac8d73)
  - اختبار خلفي: `CalendarEngineTest` (CI أخضر cac8d73)
  - اختبار واجهة: `Calendar.test.tsx` (CI أخضر cac8d73)
  - رحلة `DOC-WP6/journey-uat.json` (إصدار UAT 87bf0e6): «the hearing and the task of the case are in it»؛ «the day view holds them»؛ «the week view runs Sunday to Saturday»؛ «the month view counts the day»
- **CALENDAR_CLOSED**
  - اختبار خلفي: `CalendarApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-GAPS/wp3_followup_journey-3937a50.json` (إصدار UAT 3937a50): «the hearings of the closed case remain»
- **CALENDAR_CONFLICT**
  - اختبار خلفي: `CalendarEngineTest` (CI أخضر cac8d73)
  - اختبار خلفي: `CalendarApiTest` (CI أخضر cac8d73)
  - اختبار واجهة: `Calendar.test.tsx` (CI أخضر cac8d73)
  - رحلة `DOC-WP6/journey-uat.json` (إصدار UAT 87bf0e6): «the calendar detects the conflict»؛ «it offers the alternate member of each case»؛ «the alternate member is assigned to the second hearing»
- **CALENDAR_KINDS**
  - اختبار خلفي: `CalendarApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-WP6/journey-uat.json` (إصدار UAT 87bf0e6): «the kind filter keeps the task only»؛ «the manual dates are on the calendar»؛ «the lawyer enters an appeal date by hand»
- **CASE_NUMBERS**
  - اختبار خلفي: `GovernmentIntakeApiTest` (CI أخضر cac8d73)
  - اختبار خلفي: `CaseDetailsApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-WP1/journey-wp1-access.json` (إصدار UAT 8d7c847): «reference (system, D1)»؛ «automated number (user, D1)»؛ «claimed amount keeps three decimals»
- **CASE_REG**
  - اختبار خلفي: `CaseApiTest` (CI أخضر cac8d73)
  - اختبار خلفي: `CaseDetailsApiTest` (CI أخضر cac8d73)
  - اختبار خلفي: `GovernmentIntakeApiTest` (CI أخضر cac8d73)
  - اختبار خلفي: `CaseSearchApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-WP1/journey-wp1-access.json` (إصدار UAT 8d7c847): «intake registers the synthetic case»؛ «the specialist finds it by its subject»
- **CASE_SEARCH**
  - اختبار خلفي: `CaseSearchApiTest` (CI أخضر cac8d73)
  - اختبار خلفي: `UnifiedSearchApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-WP1/journey-wp1-access.json` (إصدار UAT 8d7c847): «the specialist finds it by its subject»
  - رحلة `DOC-WP9/wp9_journey.json` (إصدار UAT f69bd76): «the case is found by its number too»
- **CASE_TYPE**
  - اختبار خلفي: `CaseTypeTest` (CI أخضر cac8d73)
  - اختبار خلفي: `CaseApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-WP1/journey-wp1d-stages.json` (إصدار UAT c898321): «a case whose nature is constitutional is registered»
- **CLAIMS**
  - اختبار خلفي: `ClaimApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-WP2/journey-wp2-uat.json` (إصدار UAT 4220ccf): «the case lists its three claims in order»؛ «the specialist amends claim 1»؛ «the previous text is kept verbatim»
- **CLAIM_RESULTS**
  - اختبار خلفي: `ClaimResultApiTest` (CI أخضر cac8d73)
  - اختبار خلفي: `JudgmentDetailsApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-WP2/journey-wp2-uat.json` (إصدار UAT 4220ccf): «each claim gets its own result»؛ «the awarded amount keeps three decimals as text»؛ «the first-instance results are unchanged»
- **CLAIM_STATS**
  - اختبار خلفي: `StatisticsApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-WP2/journey-wp2-uat.json` (إصدار UAT 4220ccf): «the statistics grew by the five results recorded»
- **CLOSING_CHECK**
  - اختبار خلفي: `GapClosureApiTest#closingACaseWithOpenItemsNeedsAnAcknowledgementAndSaysWhichItemsAreOpen` (CI أخضر cac8d73)
  - رحلة `DOC-GAPS/wp3_followup_journey-3937a50.json` (إصدار UAT 3937a50): «the case is closed»
- **CONNECTED**
  - اختبار خلفي: `ConnectedCaseApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-GAPS/connected_journey-3937a50.json` (إصدار UAT 3937a50): «a claim is recorded»؛ «a defence is recorded»؛ «a hearing is recorded»؛ «a judgment is recorded»؛ «the summary counts the hearing, the task and the deadline»؛ «the calendar shows the hearing, the task and the deadline»؛ «the dashboard follows»؛ «an outsider cannot open the summary»
- **DASHBOARD**
  - اختبار خلفي: `IndicatorApiTest` (CI أخضر cac8d73)
  - اختبار خلفي: `DashboardReportApiTest` (CI أخضر cac8d73)
  - اختبار واجهة: `DashboardPage.test.tsx` (CI أخضر cac8d73)
  - اختبار واجهة: `Wp8Reports.test.tsx` (CI أخضر cac8d73)
  - رحلة `DOC-GAPS/wp8_journey-3937a50.json` (إصدار UAT 3937a50): «after, manager: every indicator figure equals the total of»؛ «sixteen indicator codes are reported»
- **DB_INTEGRITY**
  - اختبار خلفي: `ProceedingCaseForeignKeyTest` (CI أخضر cac8d73)
  - اختبار خلفي: `HearingDatabaseIntegrityTest` (CI أخضر cac8d73)
  - اختبار خلفي: `ExpertDatabaseIntegrityTest` (CI أخضر cac8d73)
  - اختبار خلفي: `NoPublicSchemaReferenceTest` (CI أخضر cac8d73)
- **DEADLINES**
  - اختبار خلفي: `DeadlineApiTest` (CI أخضر cac8d73)
  - اختبار خلفي: `DeadlineReminderApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-WP4/journey-uat.json` (إصدار UAT 82c3285): «staff records a deadline from the judgment»؛ «a competent member approves it»؛ «the register lists the case's deadlines»؛ «staff completes the deadline with its proof»
- **DEFENSES**
  - اختبار خلفي: `DefenseApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-WP2/journey-wp2-uat.json` (إصدار UAT 4220ccf): «the specialist records a defence linked to a memo»؛ «a second defence gets the next number»
- **DEFENSE_LIBRARY**
  - اختبار خلفي: `DefenseApiTest` (CI أخضر cac8d73)
- **DEFENSE_POSITION**
  - اختبار خلفي: `DefenseApiTest` (CI أخضر cac8d73)
  - اختبار خلفي: `JudgmentDetailsApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-WP2/journey-wp2-uat.json` (إصدار UAT 4220ccf): «the court's position is recorded on each defence»؛ «the defence is re-asserted in the appeal»؛ «the defence keeps the court's position in each stage»
- **DEFENSE_PRECEDENT**
  - اختبار خلفي: `GapClosureApiTest#aDefenceKeepsItsSupportingPrecedentsAndTheirHistory` (CI أخضر cac8d73)
- **DEFENSE_STATS**
  - اختبار خلفي: `StatisticsApiTest#countsDefencePositionsAndTheMostUsedDefences` (CI أخضر cac8d73)
- **DIRECTIVES**
  - اختبار خلفي: `DirectiveApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-WP9/wp9_journey.json` (إصدار UAT f69bd76): «the manager issues a directive with a system number»؛ «the addressee finds it in the register»؛ «the addressee moves its status»
- **DUPLICATES**
  - اختبار خلفي: `PartyIdentityAndDuplicateApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-WP1/journey-wp1c-parties.json` (إصدار UAT 30fa134): «a case with the same automated number is warned about»؛ «the registrar confirms it is different and it is registered»
- **ENFORCEMENT**
  - اختبار خلفي: `EnforcementApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-WP5/journey-wp5.json` (إصدار UAT 2bcd2dc): «the specialist opens the enforcement file»؛ «an action is recorded with its number, date and result»؛ «the manager's list of late judgments shows it»
- **EVIDENCE**
  - اختبار خلفي: `EvidenceApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-WP2/journey-wp2-uat.json` (إصدار UAT 4220ccf): «a document becomes evidence with a role and an importance»؛ «the same document is linked to a fact and to a defence»؛ «the defence lists the document that supports it»
- **EVIDENCE_GAPS**
  - اختبار خلفي: `EvidenceApiTest` (CI أخضر cac8d73)
  - اختبار خلفي: `GapClosureApiTest#missingEvidenceCanBeFollowedByATaskOrAnInformationRequestOfTheSameCase` (CI أخضر cac8d73)
  - رحلة `DOC-WP2/journey-wp2-uat.json` (إصدار UAT 4220ccf): «a required document not yet obtained is a gap»؛ «a document obtained but not submitted to the court is a gap»؛ «submitting the document to the court is recorded»
- **FILE_PROTECTION**
  - اختبار خلفي: `ArchiveVisibilityApiTest` (CI أخضر cac8d73)
  - اختبار خلفي: `SecurityBaselineTest` (CI أخضر cac8d73)
  - رحلة `DOC-WP5/journey-wp5.json` (إصدار UAT 2bcd2dc): «a file that is not what it claims is refused»؛ «the viewer finds nothing of a case they cannot see»
- **HEARINGS**
  - اختبار خلفي: `HearingApiTest` (CI أخضر cac8d73)
  - اختبار خلفي: `HearingExtensionApiTest` (CI أخضر cac8d73)
  - اختبار خلفي: `HearingChainSerializationTest` (CI أخضر cac8d73)
  - رحلة `DOC-GAPS/wp3_followup_journey-3937a50.json` (إصدار UAT 3937a50): «staff schedules a hearing with the extended record»؛ «staff adjourns it to a next hearing»؛ «the previous hearing is adjourned, not replaced»
- **HEARING_NO_DATE**
  - اختبار خلفي: `HearingExtensionApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-GAPS/wp3_followup_journey-3937a50.json` (إصدار UAT 3937a50): «an adjournment with no next date is recorded»؛ «it creates no hearing and joins the follow-up list»؛ «a later next hearing ends the follow-up»
- **HEARING_TIMELINE**
  - اختبار خلفي: `HearingChainSerializationTest` (CI أخضر cac8d73)
  - اختبار خلفي: `CaseTimelineApiTest` (CI أخضر cac8d73)
  - اختبار واجهة: `CaseHearings.test.tsx` (CI أخضر cac8d73)
  - رحلة `DOC-GAPS/wp3_followup_journey-3937a50.json` (إصدار UAT 3937a50): «both hearings are in the file: the previous one is kept»؛ «the upcoming hearing is marked as future and links to its record»
- **HISTORY_KEPT**
  - اختبار خلفي: `AuditTrailTest` (CI أخضر cac8d73)
  - اختبار خلفي: `ActivityHistoryTest` (CI أخضر cac8d73)
  - اختبار خلفي: `ClaimApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-WP2/journey-wp2-uat.json` (إصدار UAT 4220ccf): «both versions are kept, newest first»
- **INFOREQ**
  - اختبار خلفي: `InfoRequestApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-GAPS/wp3_followup_journey-3937a50.json` (إصدار UAT 3937a50): «staff registers a request to the unit as a draft»؛ «the system generated the number»؛ «request moves to SENT»؛ «a partial reply is recorded»
- **INFOREQ_FOLLOW**
  - اختبار خلفي: `InfoRequestApiTest#remindersAndNewLettersAreRecordedInOrderWithTheirDate` (CI أخضر cac8d73)
  - اختبار خلفي: `InfoRequestApiTest#escalationNeedsItsPermissionAndATargetAndNotifiesTheTarget` (CI أخضر cac8d73)
  - رحلة `DOC-GAPS/wp3_followup_journey-3937a50.json` (إصدار UAT 3937a50): «a reminder is recorded»؛ «a holder of the permission escalates»؛ «the reason for the delay is recorded with a new date»
- **INFOREQ_LATE**
  - اختبار خلفي: `InfoRequestApiTest#theFollowUpRunMarksALateRequestOverdueNotifiesOnceAndNeverClosesIt` (CI أخضر cac8d73)
  - رحلة `DOC-GAPS/wp3_followup_journey-3937a50.json` (إصدار UAT 3937a50): «the follow-up run marks late requests»؛ «the late request is marked overdue and is not closed»؛ «running again never duplicates the alert»
- **INFOREQ_REPLY**
  - اختبار خلفي: `InfoRequestApiTest#keepsEveryReplyAndFollowsTheLatestOneUntilOneIsComplete` (CI أخضر cac8d73)
  - اختبار خلفي: `InfoRequestApiTest#aReplyDocumentIsArchivedInTheCaseFileAndLinkedToTheReply` (CI أخضر cac8d73)
  - رحلة `DOC-GAPS/wp3_followup_journey-3937a50.json` (إصدار UAT 3937a50): «every reply is kept and the state follows the latest»؛ «the document is archived in the case file»
- **INFOREQ_REPORT**
  - اختبار خلفي: `InfoRequestApiTest#theReportCountsRequestsRepliesDelaysAndIncompleteRepliesPerUnit` (CI أخضر cac8d73)
  - رحلة `DOC-GAPS/wp3_followup_journey-3937a50.json` (إصدار UAT 3937a50): «the responsiveness report counts requests per unit»
  - رحلة `DOC-GAPS/wp8_journey-3937a50.json` (إصدار UAT 3937a50): «the units view reports the responsiveness of departments»
- **JUDGMENTS**
  - اختبار خلفي: `JudgmentApiTest` (CI أخضر cac8d73)
  - اختبار خلفي: `JudgmentDetailsApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-WP2/journey-wp2-uat.json` (إصدار UAT 4220ccf): «the manager records the extended judgment»؛ «the appeal judgment is recorded (legacy request, no details)»
- **JUDGMENT_DEADLINE**
  - اختبار خلفي: `DeadlineApiTest` (CI أخضر cac8d73)
  - اختبار خلفي: `GapClosureApiTest#aJudgmentNamesItsOwnAppealDeadlineOnly` (CI أخضر cac8d73)
  - رحلة `DOC-GAPS/connected_journey-3937a50.json` (إصدار UAT 3937a50): «a deadline runs from the judgment»؛ «the judgment names it as its appeal deadline»
  - رحلة `DOC-WP4/journey-uat.json` (إصدار UAT 82c3285): «the judgment shows its own deadline»
- **JUDGMENT_FILE**
  - اختبار خلفي: `DocumentLinkApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-WP5/journey-wp5.json` (إصدار UAT 2bcd2dc): «a document is linked to the judgment it concerns»
- **LEGALDOCS**
  - اختبار خلفي: `LegalDocumentApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-WP5/journey-wp5.json` (إصدار UAT 2bcd2dc): «the specialist registers a memo as a draft»؛ «the manager approves»؛ «a member records that it was sent»؛ «every step is in the history»
- **LIBRARY**
  - اختبار خلفي: `LibraryJudgmentApiTest` (CI أخضر cac8d73)
  - اختبار خلفي: `LibraryPrincipleApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-WP7/wp7_journey.json` (إصدار UAT 60f5368): «the specialist registers the judgment»؛ «it records who entered it»؛ «the source is mandatory»
- **LIB_CLASS**
  - اختبار خلفي: `LibraryClassificationApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-WP7/wp7_journey.json` (إصدار UAT 60f5368): «the specialist proposes a legal topic»؛ «filtering by the classification keeps it»
- **LIB_COPY**
  - اختبار خلفي: `LibraryCopyApiTest` (CI أخضر cac8d73)
- **LIB_LINK**
  - اختبار خلفي: `CasePrecedentApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-WP7/wp7_journey.json` (إصدار UAT 60f5368): «the specialist decides to link the judgment, giving the reas»؛ «the specialist sees the judgment and the case it is linked t»
- **LIB_PRINCIPLE**
  - اختبار خلفي: `LibraryPrincipleApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-WP7/wp7_journey.json` (إصدار UAT 60f5368): «the specialist extracts a principle apart from the judgment»؛ «the manager reviews the principle»
- **LIB_REPORT**
  - اختبار خلفي: `LibraryReportApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-WP7/wp7_journey.json` (إصدار UAT 60f5368): «a reader sees the library report»؛ «the report counts the judgment under its classification»
- **LIB_SEARCH**
  - اختبار خلفي: `LibrarySearchQualityTest` (CI أخضر cac8d73)
  - اختبار خلفي: `LibraryJudgmentApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-WP7/wp7_journey.json` (إصدار UAT 60f5368): «a reader finds the judgment by the token»؛ «a reader finds the judgment by words found only in the princ»؛ «filtering by the classification keeps it»
- **LIB_SIMILAR**
  - اختبار خلفي: `SimilarJudgmentApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-WP7/wp7_journey.json` (إصدار UAT 60f5368): «the specialist is offered similar judgments»؛ «each suggestion explains itself with words and points»؛ «the citation shows source, date and number»
- **LIB_SOURCE**
  - اختبار خلفي: `LibraryJudgmentApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-WP7/wp7_journey.json` (إصدار UAT 60f5368): «the source is mandatory»؛ «the citation shows source, date and number»
- **LIB_STATUS**
  - اختبار خلفي: `LibraryRelationApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-WP7/wp7_journey.json` (إصدار UAT 60f5368): «a later event is recorded with its source»؛ «the library now warns that a later event exists»؛ «and still does not decide the legal validity»
- **LIFECYCLE**
  - اختبار خلفي: `CaseLifecycleApiTest` (CI أخضر cac8d73)
  - اختبار خلفي: `ConnectedCaseApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-GAPS/wp3_followup_journey-3937a50.json` (إصدار UAT 3937a50): «the case is closed»؛ «the hearings of the closed case remain»؛ «the summary of the closed case still shows its history»
- **LINKS**
  - اختبار خلفي: `CaseLinkApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-GAPS/wp3_followup_journey-3937a50.json` (إصدار UAT 3937a50): «a request to join starts as requested, not decided»؛ «the link shows in the file of case A»؛ «and in the file of case B»؛ «the court's decision is recorded on the link»
- **LINK_NO_CLOSE**
  - اختبار خلفي: `CaseLinkApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-GAPS/wp3_followup_journey-3937a50.json` (إصدار UAT 3937a50): «closing it did not close the linked case»
- **NODATALOSS**
  - اختبار خلفي: `MigrationSafetyTest` (CI أخضر cac8d73)
  - اختبار خلفي: `ArchiveMigrationTest` (CI أخضر cac8d73)
  - اختبار خلفي: `MigrationPhase5IntegrityTest` (CI أخضر cac8d73)
- **NOTIFY_CONFLICT**
  - اختبار خلفي: `CalendarApiTest` (CI أخضر cac8d73)
  - اختبار خلفي: `NotificationCentreApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-WP6/journey-uat.json` (إصدار UAT 87bf0e6): «the conflict of dates is raised to the member»
- **NOTIFY_DEADLINE**
  - اختبار خلفي: `DeadlineReminderApiTest` (CI أخضر cac8d73)
  - اختبار خلفي: `FollowUpDatesTest` (CI أخضر cac8d73)
  - رحلة `DOC-WP6/journey-uat.json` (إصدار UAT 87bf0e6): «the appeal date that exists is announced once»
  - رحلة `DOC-WP4/journey-uat.json` (إصدار UAT 82c3285): «the specialist is told in the system once per overdue approv»
- **NOTIFY_HEARING**
  - اختبار خلفي: `FollowUpDatesTest` (CI أخضر cac8d73)
  - اختبار خلفي: `DateRemindersTest` (CI أخضر cac8d73)
  - رحلة `DOC-WP6/journey-uat.json` (إصدار UAT 87bf0e6): «the hearing in two days is announced once»
- **NOTIFY_INFOREQ**
  - اختبار خلفي: `InfoRequestApiTest#theFollowUpRunMarksALateRequestOverdueNotifiesOnceAndNeverClosesIt` (CI أخضر cac8d73)
  - رحلة `DOC-GAPS/wp3_followup_journey-3937a50.json` (إصدار UAT 3937a50): «the specialist has one in-app alert about it»
- **NOTIFY_MISSING_DOC**
  - اختبار خلفي: `GapClosureApiTest#aMissingRequiredEvidenceItemRaisesOneDocumentMissingNotificationPerMember` (CI أخضر cac8d73)
- **NOTIFY_NEWCASE**
  - اختبار خلفي: `NotificationCentreApiTest` (CI أخضر cac8d73)
  - اختبار خلفي: `FollowUpDatesTest` (CI أخضر cac8d73)
  - رحلة `DOC-WP6/journey-uat.json` (إصدار UAT 87bf0e6): «the new case is announced to its specialist, not to the mana»
- **NOTIFY_TASK**
  - اختبار خلفي: `TaskReminderApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-WP6/journey-uat.json` (إصدار UAT 87bf0e6): «the follow-up run answers»
- **NO_LEAK**
  - اختبار خلفي: `CaseBoundEndpointsAccessTest#everyCaseBoundHandlerRefusesAUserWhoCannotSeeTheCase` (CI أخضر cac8d73)
  - اختبار خلفي: `AuthorizationBeforeValidationTest` (CI أخضر cac8d73)
  - رحلة `DOC-WP1/journey-wp1-access.json` (إصدار UAT 8d7c847): «viewer is refused /timeline»؛ «viewer is refused /parties»
- **OBSERVABILITY**
  - اختبار خلفي: `RequestCorrelationTest` (CI أخضر cac8d73)
  - اختبار خلفي: `AuditTrailTest` (CI أخضر cac8d73)
- **OCR**
  - اختبار خلفي: `TesseractOcrEngineTest` (CI أخضر cac8d73)
  - اختبار خلفي: `OcrTextSearchTest` (CI أخضر cac8d73)
  - اختبار خلفي: `OcrQualityTest` (CI أخضر cac8d73)
  - رحلة `DOC-OCR/uat-journey-1b502c1.json` (إصدار UAT 1b502c1): «ocr-clear-ar.png: OCR completed»؛ «unified search 'OCRJOURNEYONE' finds ocr-clear-ar.png through its text»
  - ملف دليل: `docs/evidence/DOC-OCR/UAT-RUN.md`
  - ملف دليل: `docs/evidence/DOC-OCR/corpus-results.tsv`
- **OCR_SEARCH**
  - اختبار خلفي: `OcrTextSearchTest` (CI أخضر cac8d73)
  - اختبار خلفي: `UnifiedSearchApiTest` (CI أخضر cac8d73)
  - ملف دليل: `docs/evidence/DOC-PERF/ocr-fix-after.md`
- **ORG**
  - اختبار خلفي: `OrgUnitApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-WP1/journey-wp1-access.json` (إصدار UAT 8d7c847): «manager creates the authority's sector/department/section»؛ «the sector name is frozen on the record»؛ «renaming the unit later leaves the case's record unchanged»
- **PARTIES**
  - اختبار خلفي: `PartyApiTest` (CI أخضر cac8d73)
  - اختبار خلفي: `CasePartyApiTest` (CI أخضر cac8d73)
  - اختبار خلفي: `PartyIdentityAndDuplicateApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-WP1/journey-wp1c-parties.json` (إصدار UAT 30fa134): «the case is registered with the party»؛ «counsel is recorded for the party on the case»
- **PARTY_CASES**
  - اختبار خلفي: `CasePartyApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-WP1/journey-wp1c-parties.json` (إصدار UAT 30fa134): «the party's cases include it for the specialist»؛ «but not for a reader who cannot see the case»
- **PARTY_ID**
  - اختبار خلفي: `PartyIdentityAndDuplicateApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-WP1/journey-wp1c-parties.json` (إصدار UAT 30fa134): «a reader without PARTY_ID_VIEW sees only the last three characters»؛ «and no contact details of a confidential party»
- **PERF_MEMBERS**
  - اختبار خلفي: `WorkloadReportApiTest` (CI أخضر cac8d73)
  - اختبار خلفي: `ExpertReportApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-GAPS/wp8_journey-3937a50.json` (إصدار UAT 3937a50): «the performance view is alphabetical, not ranked»؛ «it has no judgment-outcome column»؛ «the performance view says it ranks no one»
- **REPORTS**
  - اختبار خلفي: `FamilyApiTest` (CI أخضر cac8d73)
  - اختبار خلفي: `ReportExportApiTest` (CI أخضر cac8d73)
  - اختبار خلفي: `ExportFormatsApiTest` (CI أخضر cac8d73)
  - اختبار واجهة: `Wp8Reports.test.tsx` (CI أخضر cac8d73)
  - رحلة `DOC-GAPS/wp8_journey-3937a50.json` (إصدار UAT 3937a50): «seventeen report families»؛ «every family answers with rows as wide as its columns»؛ «CASES_BY_TYPE_COURT_STAGE exports to xlsx»؛ «PERFORMANCE exports to pdf»
- **REPORT_INVENTORY**
  - اختبار خلفي: `CaseInventoryReportApiTest` (CI أخضر cac8d73)
  - اختبار خلفي: `HearingReportApiTest` (CI أخضر cac8d73)
  - اختبار خلفي: `JudgmentReportApiTest` (CI أخضر cac8d73)
  - اختبار خلفي: `ExpertReportApiTest` (CI أخضر cac8d73)
  - اختبار واجهة: `CaseInventoryPage.test.tsx` (CI أخضر cac8d73)
  - رحلة `DOC-GAPS/wp8_journey-3937a50.json` (إصدار UAT 3937a50): «cases by CASE_TYPE add up to the same total as open/closed»؛ «cases by COURT add up to the same total as open/closed»
- **RISK**
  - اختبار خلفي: `RiskAssessmentApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-GAPS/wp3_followup_journey-3937a50.json` (إصدار UAT 3937a50): «the first assessment is version 1, with ten elements»؛ «a high risk without a plan is refused»
- **RISK_REPORT**
  - اختبار خلفي: `RiskAssessmentApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-GAPS/wp3_followup_journey-3937a50.json` (إصدار UAT 3937a50): «the high-risk report lists the case with its plan»
- **RISK_VERSIONS**
  - اختبار خلفي: `RiskAssessmentApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-GAPS/wp3_followup_journey-3937a50.json` (إصدار UAT 3937a50): «a new document leads to a new version»؛ «the history keeps both versions, newest first»؛ «the earlier version is unchanged»
- **ROLES**
  - اختبار خلفي: `DefaultRolePermissionsTest` (CI أخضر cac8d73)
  - اختبار خلفي: `RoleAdministrationTest` (CI أخضر cac8d73)
  - اختبار خلفي: `PermissionContractTest` (CI أخضر cac8d73)
  - اختبار واجهة: `RolesAudit.test.tsx` (CI أخضر cac8d73)
- **S1**
  - اختبار خلفي: `ClaimApiTest` (CI أخضر cac8d73)
  - اختبار خلفي: `ClaimResultApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-WP2/journey-wp2-uat.json` (إصدار UAT 4220ccf): «the case lists its three claims in order»؛ «each claim gets its own result»
- **S10**
  - اختبار خلفي: `CaseLinkApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-GAPS/wp3_followup_journey-3937a50.json` (إصدار UAT 3937a50): «the link shows in the file of case A»؛ «and in the file of case B»
- **S11**
  - اختبار خلفي: `CalendarApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-WP6/journey-uat.json` (إصدار UAT 87bf0e6): «the hearing and the task of the case are in it»
- **S12**
  - اختبار خلفي: `CaseLifecycleApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-GAPS/wp3_followup_journey-3937a50.json` (إصدار UAT 3937a50): «the case is closed»؛ «the hearings of the closed case remain»؛ «the summary of the closed case still shows its history»
- **S13**
  - اختبار خلفي: `ProceedingApiTest` (CI أخضر cac8d73)
  - اختبار خلفي: `ClaimResultApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-WP2/journey-wp2-uat.json` (إصدار UAT 4220ccf): «an appeal is recorded against the first-instance proceeding»؛ «the first-instance results are unchanged»؛ «the case shows five results across the two stages, in stage order»
- **S15**
  - اختبار خلفي: `CaseVisibilityApiTest` (CI أخضر cac8d73)
  - اختبار خلفي: `RoleAdministrationTest` (CI أخضر cac8d73)
  - اختبار خلفي: `PermissionContractTest` (CI أخضر cac8d73)
  - رحلة `DOC-WP1/journey-wp1-access.json` (إصدار UAT 8d7c847): «a read-only user without a scope does not see it»؛ «a grant is given with a reason»؛ «the grant is withdrawn»
- **S2**
  - اختبار خلفي: `ClaimApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-WP2/journey-wp2-uat.json` (إصدار UAT 4220ccf): «the specialist amends claim 1»؛ «both versions are kept, newest first»؛ «the previous text is kept verbatim»
- **S3**
  - اختبار خلفي: `DefenseApiTest` (CI أخضر cac8d73)
  - اختبار خلفي: `EvidenceApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-WP2/journey-wp2-uat.json` (إصدار UAT 4220ccf): «the specialist records a defence linked to a memo»؛ «the same document is linked to a fact and to a defence»؛ «the court's position is recorded on each defence»
- **S4**
  - اختبار خلفي: `LibraryPrincipleApiTest` (CI أخضر cac8d73)
  - اختبار خلفي: `LibrarySearchQualityTest` (CI أخضر cac8d73)
  - رحلة `DOC-WP7/wp7_journey.json` (إصدار UAT 60f5368): «the specialist registers the judgment»؛ «the specialist extracts a principle apart from the judgment»؛ «a reader finds the judgment by the token»
- **S5**
  - اختبار خلفي: `SimilarJudgmentApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-WP7/wp7_journey.json` (إصدار UAT 60f5368): «the specialist is offered similar judgments»؛ «each suggestion explains itself with words and points»
- **S6**
  - اختبار خلفي: `InfoRequestApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-GAPS/wp3_followup_journey-3937a50.json` (إصدار UAT 3937a50): «a partial reply is recorded»؛ «the late request is marked overdue and is not closed»؛ «the late request is in the overdue list»
- **S7**
  - اختبار خلفي: `RiskAssessmentApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-GAPS/wp3_followup_journey-3937a50.json` (إصدار UAT 3937a50): «the first assessment is version 1, with ten elements»؛ «a new document leads to a new version»
- **S8**
  - اختبار خلفي: `EvidenceApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-WP2/journey-wp2-uat.json` (إصدار UAT 4220ccf): «the same document is linked to a fact and to a defence»؛ «the case file shows the document as evidence with both links»
- **S9**
  - اختبار خلفي: `HearingExtensionApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-GAPS/wp3_followup_journey-3937a50.json` (إصدار UAT 3937a50): «staff adjourns it to a next hearing»؛ «the previous hearing is adjourned, not replaced»
- **SETTLEMENT**
  - اختبار خلفي: `SettlementApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-WP9/wp9_journey.json` (إصدار UAT f69bd76): «the specialist proposes a settlement with its own amount»؛ «the manager approves it»؛ «the settlement is executed»
- **STAGES**
  - اختبار خلفي: `ProceedingApiTest` (CI أخضر cac8d73)
  - اختبار خلفي: `AdditionalStagesApiTest` (CI أخضر cac8d73)
  - اختبار خلفي: `StageRulesTest` (CI أخضر cac8d73)
  - رحلة `DOC-WP1/journey-wp1d-stages.json` (إصدار UAT c898321): «a ENFORCEMENT proceeding follows the first instance»؛ «a CONSTITUTIONAL proceeding follows the first instance»؛ «the stage must match the court's level»
- **STAGE_CHAIN**
  - اختبار خلفي: `JudicialChronologyTest` (CI أخضر cac8d73)
  - اختبار خلفي: `ProceedingApiTest` (CI أخضر cac8d73)
  - اختبار واجهة: `CaseProceedings.test.tsx` (CI أخضر cac8d73)
  - رحلة `DOC-WP2/journey-wp2-uat.json` (إصدار UAT 4220ccf): «the case shows five results across the two stages, in stage order»
- **SUMMARY**
  - اختبار خلفي: `CaseFileApiTest` (CI أخضر cac8d73)
  - اختبار خلفي: `CaseTimelineApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-GAPS/wp3_followup_journey-3937a50.json` (إصدار UAT 3937a50): «the summary is assembled from the stored data»؛ «it names the hearings, the next one, the risk and the pending requests»
- **TASKS**
  - اختبار خلفي: `TaskApiTest` (CI أخضر cac8d73)
  - اختبار خلفي: `TaskExtensionApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-WP6/journey-uat.json` (إصدار UAT 87bf0e6): «staff creates a task on the case with a start date and instr»؛ «it starts NEW with its instructions»
- **TASK_REASSIGN**
  - اختبار خلفي: `TaskExtensionApiTest#reassigningMovesTheTaskAndTheSightOfTheCaseAndKeepsTheHistory` (CI أخضر cac8d73)
  - رحلة `DOC-WP6/journey-uat.json` (إصدار UAT 87bf0e6): «the task is reassigned with a reason»؛ «the reassignment keeps both people and the reason»
- **TASK_STATES**
  - اختبار خلفي: `TaskStatusAuditTest` (CI أخضر cac8d73)
  - اختبار خلفي: `TaskStatusMigrationApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-WP6/journey-uat.json` (إصدار UAT 87bf0e6): «the task moves to WAITING_DOCUMENTS»؛ «the task moves to WAITING_OTHER»؛ «late is set by the system, not chosen»؛ «the reason for delay is recorded»
- **TEMPLATES**
  - اختبار خلفي: `TemplateApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-WP9/wp9_journey.json` (إصدار UAT f69bd76): «the template kinds are those of the requirements text»؛ «the version is reviewed then approved»؛ «it fills the case number and title from the case file»؛ «it names the field the file does not hold and leaves its pla»
- **TIMELINE**
  - اختبار خلفي: `CaseTimelineApiTest` (CI أخضر cac8d73)
  - اختبار خلفي: `JudicialChronologyTest` (CI أخضر cac8d73)
  - اختبار واجهة: `timelineEvents.test.ts` (CI أخضر cac8d73)
  - رحلة `DOC-GAPS/wp3_followup_journey-3937a50.json` (إصدار UAT 3937a50): «the timeline joins hearings, requests, replies, risk and links»؛ «it filters by type»
- **TIMELINE_PAST**
  - اختبار خلفي: `CaseTimelineApiTest` (CI أخضر cac8d73)
  - رحلة `DOC-GAPS/wp3_followup_journey-3937a50.json` (إصدار UAT 3937a50): «the upcoming hearing is marked as future and links to its record»؛ «the summary of the closed case still shows its history»؛ «the closing is in the timeline»
- **UNIFIED_SEARCH**
  - اختبار خلفي: `UnifiedSearchApiTest` (CI أخضر cac8d73)
  - اختبار خلفي: `StoredSearchDocumentsTest` (CI أخضر cac8d73)
  - اختبار واجهة: `SearchPage.test.tsx` (CI أخضر cac8d73)
  - رحلة `DOC-WP9/wp9_journey.json` (إصدار UAT f69bd76): «the member finds the case, its party, the settlement and the»؛ «spelling variants find the same case»؛ «an account with no access finds nothing of it»
- **UX_RTL**
  - اختبار واجهة: `locale.test.ts` (CI أخضر cac8d73)
  - ملف دليل: `docs/evidence/DOC-UX/results-summary.json`
  - ملف دليل: `docs/evidence/DOC-UX/SUMMARY.md`

## LD-DOC-00 — مقدمة الوثيقة (الأسطر 1–5)

- **المرحلة**: — · **الأولوية**: — · **حزمة العمل**: WP0 · **سيناريوهات القبول المرتبطة**: —

| المعرّف | السطر | المجموعة | المتطلب (نص المصدر) | IMPLEMENTED | VERIFIED_BY_DEVELOPER | QA_ACCEPTED | سبب العائق | ملاحظة (ما تبقى / لماذا لم يُتحقق) |
|---|---|---|---|---|---|---|---|---|
| LD-DOC-00-001 | 4 | إلى: الفريق التقني المسؤول عن تطوير نظام LawDesk | يرجى اعتماد هذه الوثيقة باعتبارها المتطلبات الشاملة لتطوير النظام الحالي، بهدف تحويله إلى منصة متكاملة لإدارة القضايا والأعمال القانونية ومتابعة إجراءات التقاضي والتنفيذ، وتنظيم المواعيد والمهام، وإدارة الأحكام والدفوع والأدلة، وحفظ المستندات، وإعداد التقارير والإحصائيات. | YES | T: ConnectedCaseApiTest (CI green cac8d73)<br>J: conn «a claim is recorded; a defence is recorded» (UAT 3937a50) [CONNECTED] | | — | — |
| LD-DOC-00-002 | 5 | إلى: الفريق التقني المسؤول عن تطوير نظام LawDesk | المطلوب ليس مجرد إضافة شاشات مستقلة، وإنما إنشاء منظومة مترابطة تكون فيها بيانات القضية وطلباتها ودفوعها وجلساتها وأحكامها ومستنداتها ومواعيدها ومخاطرها ومهامها مرتبطة ببعضها، ويتم تحديثها بصورة متكاملة. | YES | T: ConnectedCaseApiTest (CI green cac8d73)<br>J: conn «a claim is recorded; a defence is recorded» (UAT 3937a50) [CONNECTED] | | — | — |

<sub>عناوين المجموعات في المصدر: وثيقة المتطلبات الوظيفية والفنية الشاملة لتطوير نظام LawDesk (س1)؛ نظام إدارة القضايا والأعمال القانونية والأرشفة الإلكترونية (س2)؛ إلى: الفريق التقني المسؤول عن تطوير نظام LawDesk (س3)</sub>

## LD-DOC-01 — أولاً: إدارة القضايا والبيانات الأساسية

- **المرحلة**: المرحلة الأولى · **الأولوية**: أعلى · **حزمة العمل**: WP1 · **سيناريوهات القبول المرتبطة**: S1, S5(13)
- **التحديث (2026-11-09)**: نُفِّذ بحزمة العمل؛ الدليل: DOC-WP1 (journey wp1-access).

| المعرّف | السطر | المجموعة | المتطلب (نص المصدر) | IMPLEMENTED | VERIFIED_BY_DEVELOPER | QA_ACCEPTED | سبب العائق | ملاحظة (ما تبقى / لماذا لم يُتحقق) |
|---|---|---|---|---|---|---|---|---|
| LD-DOC-01-001 | 8 | إنشاء سجل إلكتروني مستقل لكل قضية يتضمن | رقم القضية الداخلي الآلي. | YES | T: GovernmentIntakeApiTest, CaseDetailsApiTest (CI green cac8d73)<br>J: wp1a «reference (system, D1); automated number (user, D1)» (UAT 8d7c847) [CASE_NUMBERS] | | — | — |
| LD-DOC-01-002 | 9 | إنشاء سجل إلكتروني مستقل لكل قضية يتضمن | رقم القضية لدى المحكمة. | YES | T: GovernmentIntakeApiTest, CaseDetailsApiTest (CI green cac8d73)<br>J: wp1a «reference (system, D1); automated number (user, D1)» (UAT 8d7c847) [CASE_NUMBERS] | | — | — |
| LD-DOC-01-003 | 10 | إنشاء سجل إلكتروني مستقل لكل قضية يتضمن | اسم القضية وموضوعها. | YES | T: CaseApiTest, CaseDetailsApiTest, GovernmentIntakeApiTest, CaseSearchApiTest (CI green cac8d73)<br>J: wp1a «intake registers the synthetic case; the specialist finds it by its subject» (UAT 8d7c847) [CASE_REG] | | — | — |
| LD-DOC-01-004 | 11 | إنشاء سجل إلكتروني مستقل لكل قضية يتضمن | نوع القضية: إدارية، مدنية، تجارية، عمالية، جزائية، دستورية، تنفيذ، تظلم، استئناف، تمييز، أو غير ذلك. | YES | T: CaseTypeTest, CaseApiTest (CI green cac8d73)<br>J: wp1d «a case whose nature is constitutional is registered» (UAT c898321) [CASE_TYPE] | | — | — |
| LD-DOC-01-005 | 12 | إنشاء سجل إلكتروني مستقل لكل قضية يتضمن | المحكمة والدائرة المختصة. | YES | T: CaseApiTest, CaseDetailsApiTest, GovernmentIntakeApiTest, CaseSearchApiTest (CI green cac8d73)<br>J: wp1a «intake registers the synthetic case; the specialist finds it by its subject» (UAT 8d7c847) [CASE_REG] | | — | — |
| LD-DOC-01-006 | 13 | إنشاء سجل إلكتروني مستقل لكل قضية يتضمن | درجة التقاضي. | YES | T: CaseApiTest, CaseDetailsApiTest, GovernmentIntakeApiTest, CaseSearchApiTest (CI green cac8d73)<br>J: wp1a «intake registers the synthetic case; the specialist finds it by its subject» (UAT 8d7c847) [CASE_REG] | | — | — |
| LD-DOC-01-007 | 14 | إنشاء سجل إلكتروني مستقل لكل قضية يتضمن | تاريخ رفع الدعوى. | YES | T: CaseApiTest, CaseDetailsApiTest, GovernmentIntakeApiTest, CaseSearchApiTest (CI green cac8d73)<br>J: wp1a «intake registers the synthetic case; the specialist finds it by its subject» (UAT 8d7c847) [CASE_REG] | | — | — |
| LD-DOC-01-008 | 15 | إنشاء سجل إلكتروني مستقل لكل قضية يتضمن | تاريخ ورود القضية إلى الإدارة القانونية. | YES | T: CaseApiTest, CaseDetailsApiTest, GovernmentIntakeApiTest, CaseSearchApiTest (CI green cac8d73)<br>J: wp1a «intake registers the synthetic case; the specialist finds it by its subject» (UAT 8d7c847) [CASE_REG] | | — | — |
| LD-DOC-01-009 | 16 | إنشاء سجل إلكتروني مستقل لكل قضية يتضمن | الجهة التي أحالت القضية. | YES | T: CaseApiTest, CaseDetailsApiTest, GovernmentIntakeApiTest, CaseSearchApiTest (CI green cac8d73)<br>J: wp1a «intake registers the synthetic case; the specialist finds it by its subject» (UAT 8d7c847) [CASE_REG] | | — | — |
| LD-DOC-01-010 | 17 | إنشاء سجل إلكتروني مستقل لكل قضية يتضمن | صفة الجهة في الدعوى. | YES | T: CaseApiTest, CaseDetailsApiTest, GovernmentIntakeApiTest, CaseSearchApiTest (CI green cac8d73)<br>J: wp1a «intake registers the synthetic case; the specialist finds it by its subject» (UAT 8d7c847) [CASE_REG] | | — | — |
| LD-DOC-01-011 | 18 | إنشاء سجل إلكتروني مستقل لكل قضية يتضمن | موضوع الدعوى وملخص الوقائع. | YES | T: CaseApiTest, CaseDetailsApiTest, GovernmentIntakeApiTest, CaseSearchApiTest (CI green cac8d73)<br>J: wp1a «intake registers the synthetic case; the specialist finds it by its subject» (UAT 8d7c847) [CASE_REG] | | — | — |
| LD-DOC-01-012 | 19 | إنشاء سجل إلكتروني مستقل لكل قضية يتضمن | القيمة المالية المطالب بها، إن وجدت. | YES | T: CaseApiTest, CaseDetailsApiTest, GovernmentIntakeApiTest, CaseSearchApiTest (CI green cac8d73)<br>J: wp1a «intake registers the synthetic case; the specialist finds it by its subject» (UAT 8d7c847) [CASE_REG] | | — | — |
| LD-DOC-01-013 | 20 | إنشاء سجل إلكتروني مستقل لكل قضية يتضمن | درجة أهمية القضية. | YES | T: CaseApiTest, CaseDetailsApiTest, GovernmentIntakeApiTest, CaseSearchApiTest (CI green cac8d73)<br>J: wp1a «intake registers the synthetic case; the specialist finds it by its subject» (UAT 8d7c847) [CASE_REG] | | — | — |
| LD-DOC-01-014 | 21 | إنشاء سجل إلكتروني مستقل لكل قضية يتضمن | حالة القضية الحالية. | YES | T: CaseApiTest, CaseDetailsApiTest, GovernmentIntakeApiTest, CaseSearchApiTest (CI green cac8d73)<br>J: wp1a «intake registers the synthetic case; the specialist finds it by its subject» (UAT 8d7c847) [CASE_REG] | | — | — |
| LD-DOC-01-015 | 22 | إنشاء سجل إلكتروني مستقل لكل قضية يتضمن | العضو القانوني المسؤول والعضو البديل. | YES | T: CaseApiTest, CaseDetailsApiTest, GovernmentIntakeApiTest, CaseSearchApiTest (CI green cac8d73)<br>J: wp1a «intake registers the synthetic case; the specialist finds it by its subject» (UAT 8d7c847) [CASE_REG] | | — | — |
| LD-DOC-01-016 | 23 | إنشاء سجل إلكتروني مستقل لكل قضية يتضمن | الملاحظات الداخلية. | YES | T: CaseApiTest, CaseDetailsApiTest, GovernmentIntakeApiTest, CaseSearchApiTest (CI green cac8d73)<br>J: wp1a «intake registers the synthetic case; the specialist finds it by its subject» (UAT 8d7c847) [CASE_REG] | | — | — |
| LD-DOC-01-017 | 24 | إنشاء سجل إلكتروني مستقل لكل قضية يتضمن | المستندات الأساسية. | YES | T: DocumentApiTest, ArchiveFormatsAndSettingsApiTest (CI green cac8d73)<br>J: wp5 «a PDF is archived as version 1; a Word document is archived» (UAT 2bcd2dc) [ARCH_BASIC] | | — | — |
| LD-DOC-01-018 | 25 | إنشاء سجل إلكتروني مستقل لكل قضية يتضمن | ويجب توفير البحث باستخدام رقم القضية أو اسم الخصم أو موضوع الدعوى أو اسم الموظف أو الإدارة المعنية. | YES | T: CaseSearchApiTest, UnifiedSearchApiTest (CI green cac8d73)<br>J: wp1a «the specialist finds it by its subject» (UAT 8d7c847)<br>J: wp9 «the case is found by its number too» (UAT f69bd76) [CASE_SEARCH] | | — | — |

<sub>عناوين المجموعات في المصدر: إنشاء سجل إلكتروني مستقل لكل قضية يتضمن: (س7)</sub>

## LD-DOC-02 — ثانياً: أطراف القضية

- **المرحلة**: المرحلة الأولى · **الأولوية**: أعلى · **حزمة العمل**: WP1 · **سيناريوهات القبول المرتبطة**: S1
- **التحديث (2026-11-09)**: نُفِّذ بحزمة العمل؛ الدليل: DOC-WP1 (journey wp1c-parties).

| المعرّف | السطر | المجموعة | المتطلب (نص المصدر) | IMPLEMENTED | VERIFIED_BY_DEVELOPER | QA_ACCEPTED | سبب العائق | ملاحظة (ما تبقى / لماذا لم يُتحقق) |
|---|---|---|---|---|---|---|---|---|
| LD-DOC-02-001 | 28 | إنشاء قسم مستقل لتسجيل جميع أطراف القضية، ويشمل | اسم المدعي والمدعى عليه. | YES | T: PartyApiTest, CasePartyApiTest, PartyIdentityAndDuplicateApiTest (CI green cac8d73)<br>J: wp1c «the case is registered with the party; counsel is recorded for the party on the case» (UAT 30fa134) [PARTIES] | | — | — |
| LD-DOC-02-002 | 29 | إنشاء قسم مستقل لتسجيل جميع أطراف القضية، ويشمل | الصفة القانونية لكل طرف. | YES | T: PartyApiTest, CasePartyApiTest, PartyIdentityAndDuplicateApiTest (CI green cac8d73)<br>J: wp1c «the case is registered with the party; counsel is recorded for the party on the case» (UAT 30fa134) [PARTIES] | | — | — |
| LD-DOC-02-003 | 30 | إنشاء قسم مستقل لتسجيل جميع أطراف القضية، ويشمل | الرقم المدني أو رقم المنشأة عند الحاجة وفق ضوابط السرية. | YES | T: PartyIdentityAndDuplicateApiTest (CI green cac8d73)<br>J: wp1c «a reader without PARTY_ID_VIEW sees only the last three characters; and no contact details of a confidential party» (UAT 30fa134) [PARTY_ID] | | — | — |
| LD-DOC-02-004 | 31 | إنشاء قسم مستقل لتسجيل جميع أطراف القضية، ويشمل | المحامي أو الوكيل. | YES | T: PartyApiTest, CasePartyApiTest, PartyIdentityAndDuplicateApiTest (CI green cac8d73)<br>J: wp1c «the case is registered with the party; counsel is recorded for the party on the case» (UAT 30fa134) [PARTIES] | | — | — |
| LD-DOC-02-005 | 32 | إنشاء قسم مستقل لتسجيل جميع أطراف القضية، ويشمل | بيانات التواصل. | YES | T: PartyApiTest, CasePartyApiTest, PartyIdentityAndDuplicateApiTest (CI green cac8d73)<br>J: wp1c «the case is registered with the party; counsel is recorded for the party on the case» (UAT 30fa134) [PARTIES] | | — | — |
| LD-DOC-02-006 | 33 | إنشاء قسم مستقل لتسجيل جميع أطراف القضية، ويشمل | الملاحظات. | YES |  | | — | no test found for the free-text party note |
| LD-DOC-02-007 | 34 | إنشاء قسم مستقل لتسجيل جميع أطراف القضية، ويشمل | مع إمكانية ربط الطرف الواحد بأكثر من قضية واستعراض جميع القضايا المرتبطة به. | YES | T: CasePartyApiTest (CI green cac8d73)<br>J: wp1c «the party's cases include it for the specialist; but not for a reader who cannot see the case» (UAT 30fa134) [PARTY_CASES] | | — | — |

<sub>عناوين المجموعات في المصدر: إنشاء قسم مستقل لتسجيل جميع أطراف القضية، ويشمل: (س27)</sub>

## LD-DOC-03 — ثالثاً: منع تكرار تسجيل القضايا

- **المرحلة**: المرحلة الأولى · **الأولوية**: أعلى · **حزمة العمل**: WP1 · **سيناريوهات القبول المرتبطة**: S1
- **التحديث (2026-11-09)**: نُفِّذ بحزمة العمل؛ الدليل: DOC-WP1 (journey wp1c-parties).

| المعرّف | السطر | المجموعة | المتطلب (نص المصدر) | IMPLEMENTED | VERIFIED_BY_DEVELOPER | QA_ACCEPTED | سبب العائق | ملاحظة (ما تبقى / لماذا لم يُتحقق) |
|---|---|---|---|---|---|---|---|---|
| LD-DOC-03-001 | 36 | — | التحقق من عدم تسجيل القضية أكثر من مرة من خلال مقارنة رقم القضية والمحكمة ودرجة التقاضي والأطراف والموضوع، مع تنبيه المستخدم عند اكتشاف سجل مشابه. | YES | T: PartyIdentityAndDuplicateApiTest (CI green cac8d73)<br>J: wp1c «a case with the same automated number is warned about; the registrar confirms it is different and it is registered» (UAT 30fa134) [DUPLICATES] | | — | — |
| LD-DOC-03-002 | 37 | — | ولا يمنع ذلك تسجيل درجة تقاضٍ جديدة أو دعوى مستقلة مرتبطة بالقضية الأصلية. | YES | T: ProceedingApiTest, AdditionalStagesApiTest, StageRulesTest (CI green cac8d73)<br>J: wp1d «a ENFORCEMENT proceeding follows the first instance; a CONSTITUTIONAL proceeding follows the first instance» (UAT c898321) [STAGES] | | — | — |

## LD-DOC-04 — رابعاً: إدارة درجات التقاضي

- **المرحلة**: المرحلة الأولى · **الأولوية**: أعلى · **حزمة العمل**: WP1 · **سيناريوهات القبول المرتبطة**: S13
- **التحديث (2026-11-09)**: نُفِّذ بحزمة العمل؛ الدليل: DOC-WP1 (journey wp1d-stages).

| المعرّف | السطر | المجموعة | المتطلب (نص المصدر) | IMPLEMENTED | VERIFIED_BY_DEVELOPER | QA_ACCEPTED | سبب العائق | ملاحظة (ما تبقى / لماذا لم يُتحقق) |
|---|---|---|---|---|---|---|---|---|
| LD-DOC-04-001 | 40 | إنشاء سجل مستقل لكل درجة من درجات التقاضي، مع ربط جميع الدرجات بالقضية الأصلية، مثل | المحكمة الكلية. | YES | T: ProceedingApiTest, AdditionalStagesApiTest, StageRulesTest (CI green cac8d73)<br>J: wp1d «a ENFORCEMENT proceeding follows the first instance; a CONSTITUTIONAL proceeding follows the first instance» (UAT c898321) [STAGES] | | — | — |
| LD-DOC-04-002 | 41 | إنشاء سجل مستقل لكل درجة من درجات التقاضي، مع ربط جميع الدرجات بالقضية الأصلية، مثل | الاستئناف. | YES | T: ProceedingApiTest, AdditionalStagesApiTest, StageRulesTest (CI green cac8d73)<br>J: wp1d «a ENFORCEMENT proceeding follows the first instance; a CONSTITUTIONAL proceeding follows the first instance» (UAT c898321) [STAGES] | | — | — |
| LD-DOC-04-003 | 42 | إنشاء سجل مستقل لكل درجة من درجات التقاضي، مع ربط جميع الدرجات بالقضية الأصلية، مثل | التمييز. | YES | T: ProceedingApiTest, AdditionalStagesApiTest, StageRulesTest (CI green cac8d73)<br>J: wp1d «a ENFORCEMENT proceeding follows the first instance; a CONSTITUTIONAL proceeding follows the first instance» (UAT c898321) [STAGES] | | — | — |
| LD-DOC-04-004 | 43 | إنشاء سجل مستقل لكل درجة من درجات التقاضي، مع ربط جميع الدرجات بالقضية الأصلية، مثل | الدستورية، عند وجودها. | YES | T: ProceedingApiTest, AdditionalStagesApiTest, StageRulesTest (CI green cac8d73)<br>J: wp1d «a ENFORCEMENT proceeding follows the first instance; a CONSTITUTIONAL proceeding follows the first instance» (UAT c898321) [STAGES] | | — | — |
| LD-DOC-04-005 | 44 | إنشاء سجل مستقل لكل درجة من درجات التقاضي، مع ربط جميع الدرجات بالقضية الأصلية، مثل | التنفيذ. | YES | T: ProceedingApiTest, AdditionalStagesApiTest, StageRulesTest (CI green cac8d73)<br>J: wp1d «a ENFORCEMENT proceeding follows the first instance; a CONSTITUTIONAL proceeding follows the first instance» (UAT c898321) [STAGES] | | — | — |
| LD-DOC-04-006 | 45 | إنشاء سجل مستقل لكل درجة من درجات التقاضي، مع ربط جميع الدرجات بالقضية الأصلية، مثل | ويجب الاحتفاظ ببيانات كل درجة وجلساتها وأحكامها ومستنداتها، مع توفير شاشة تعرض التسلسل الكامل للقضية. | YES | T: JudicialChronologyTest, ProceedingApiTest (CI green cac8d73)<br>F: CaseProceedings.test.tsx (CI green cac8d73)<br>J: wp2 «the case shows five results across the two stages, in stage order» (UAT 4220ccf) [STAGE_CHAIN] | | — | — |

<sub>عناوين المجموعات في المصدر: إنشاء سجل مستقل لكل درجة من درجات التقاضي، مع ربط جميع الدرجات بالقضية الأصلية، مثل: (س39)</sub>

## LD-DOC-05 — خامساً: إدارة طلبات الدعوى ونتيجة الحكم في كل طلب

- **المرحلة**: المرحلة الأولى · **الأولوية**: أعلى · **حزمة العمل**: WP2 · **سيناريوهات القبول المرتبطة**: S1, S2, S13
- **التحديث (2026-11-09)**: نُفِّذ بحزمة العمل؛ الدليل: DOC-WP2 (journey wp2: S1, S2, S13).

| المعرّف | السطر | المجموعة | المتطلب (نص المصدر) | IMPLEMENTED | VERIFIED_BY_DEVELOPER | QA_ACCEPTED | سبب العائق | ملاحظة (ما تبقى / لماذا لم يُتحقق) |
|---|---|---|---|---|---|---|---|---|
| LD-DOC-05-001 | 47 | — | إنشاء وحدة مستقلة باسم «طلبات الدعوى»، تتيح إضافة عدد غير محدود من الطلبات، بحيث يكون لكل طلب رقم تسلسلي مستقل. | YES | T: ClaimApiTest (CI green cac8d73)<br>J: wp2 «the case lists its three claims in order; the specialist amends claim 1» (UAT 4220ccf) [CLAIMS] | | — | — |
| LD-DOC-05-002 | 49 | بيانات كل طلب | الرقم التسلسلي. | YES | T: ClaimApiTest (CI green cac8d73)<br>J: wp2 «the case lists its three claims in order; the specialist amends claim 1» (UAT 4220ccf) [CLAIMS] | | — | — |
| LD-DOC-05-003 | 50 | بيانات كل طلب | نص الطلب كاملاً. | YES | T: ClaimApiTest (CI green cac8d73)<br>J: wp2 «the case lists its three claims in order; the specialist amends claim 1» (UAT 4220ccf) [CLAIMS] | | — | — |
| LD-DOC-05-004 | 51 | بيانات كل طلب | نوع الطلب: أصلي، احتياطي، عارض، تابع، مستعجل، أو غير ذلك. | YES | T: ClaimApiTest (CI green cac8d73)<br>J: wp2 «the case lists its three claims in order; the specialist amends claim 1» (UAT 4220ccf) [CLAIMS] | | — | — |
| LD-DOC-05-005 | 52 | بيانات كل طلب | الطرف مقدم الطلب. | YES | T: ClaimApiTest (CI green cac8d73)<br>J: wp2 «the case lists its three claims in order; the specialist amends claim 1» (UAT 4220ccf) [CLAIMS] | | — | — |
| LD-DOC-05-006 | 53 | بيانات كل طلب | تاريخ تقديمه. | YES | T: ClaimApiTest (CI green cac8d73)<br>J: wp2 «the case lists its three claims in order; the specialist amends claim 1» (UAT 4220ccf) [CLAIMS] | | — | — |
| LD-DOC-05-007 | 54 | بيانات كل طلب | المرحلة التي قدم فيها. | YES | T: ClaimApiTest (CI green cac8d73)<br>J: wp2 «the case lists its three claims in order; the specialist amends claim 1» (UAT 4220ccf) [CLAIMS] | | — | — |
| LD-DOC-05-008 | 55 | بيانات كل طلب | المستند الذي تضمنه، مثل صحيفة الدعوى أو المذكرة. | YES | T: ClaimApiTest (CI green cac8d73)<br>J: wp2 «the case lists its three claims in order; the specialist amends claim 1» (UAT 4220ccf) [CLAIMS] | | — | — |
| LD-DOC-05-009 | 56 | بيانات كل طلب | حالة الطلب: قائم، معدل، متنازل عنه، متروك، أو غير ذلك. | YES | T: ClaimApiTest (CI green cac8d73)<br>J: wp2 «the case lists its three claims in order; the specialist amends claim 1» (UAT 4220ccf) [CLAIMS] | | — | — |
| LD-DOC-05-010 | 57 | بيانات كل طلب | الملاحظات. | YES | T: ClaimApiTest (CI green cac8d73)<br>J: wp2 «the case lists its three claims in order; the specialist amends claim 1» (UAT 4220ccf) [CLAIMS] | | — | — |
| LD-DOC-05-011 | 58 | بيانات كل طلب | يجب السماح بإضافة طلبات جديدة أو تعديل الطلبات السابقة أثناء سير الدعوى، مع الاحتفاظ بالنصوص السابقة وتاريخ التعديل والمستخدم الذي أجراه. | YES | T: ClaimApiTest (CI green cac8d73)<br>J: wp2 «the case lists its three claims in order; the specialist amends claim 1» (UAT 4220ccf) [CLAIMS] | | — | — |
| LD-DOC-05-012 | 61 | عند إدخال حكم المحكمة، تظهر جميع الطلبات المسجلة، ويحدد المستخدم نتيجة المحكمة لكل طلب بصورة مستقلة، وتشمل | قبول الطلب. | YES | T: ClaimResultApiTest, JudgmentDetailsApiTest (CI green cac8d73)<br>J: wp2 «each claim gets its own result; the awarded amount keeps three decimals as text» (UAT 4220ccf) [CLAIM_RESULTS] | | — | — |
| LD-DOC-05-013 | 62 | عند إدخال حكم المحكمة، تظهر جميع الطلبات المسجلة، ويحدد المستخدم نتيجة المحكمة لكل طلب بصورة مستقلة، وتشمل | قبول جزئي. | YES | T: ClaimResultApiTest, JudgmentDetailsApiTest (CI green cac8d73)<br>J: wp2 «each claim gets its own result; the awarded amount keeps three decimals as text» (UAT 4220ccf) [CLAIM_RESULTS] | | — | — |
| LD-DOC-05-014 | 63 | عند إدخال حكم المحكمة، تظهر جميع الطلبات المسجلة، ويحدد المستخدم نتيجة المحكمة لكل طلب بصورة مستقلة، وتشمل | رفض الطلب. | YES | T: ClaimResultApiTest, JudgmentDetailsApiTest (CI green cac8d73)<br>J: wp2 «each claim gets its own result; the awarded amount keeps three decimals as text» (UAT 4220ccf) [CLAIM_RESULTS] | | — | — |
| LD-DOC-05-015 | 64 | عند إدخال حكم المحكمة، تظهر جميع الطلبات المسجلة، ويحدد المستخدم نتيجة المحكمة لكل طلب بصورة مستقلة، وتشمل | عدم قبول الطلب. | YES | T: ClaimResultApiTest, JudgmentDetailsApiTest (CI green cac8d73)<br>J: wp2 «each claim gets its own result; the awarded amount keeps three decimals as text» (UAT 4220ccf) [CLAIM_RESULTS] | | — | — |
| LD-DOC-05-016 | 65 | عند إدخال حكم المحكمة، تظهر جميع الطلبات المسجلة، ويحدد المستخدم نتيجة المحكمة لكل طلب بصورة مستقلة، وتشمل | عدم الاختصاص. | YES | T: ClaimResultApiTest, JudgmentDetailsApiTest (CI green cac8d73)<br>J: wp2 «each claim gets its own result; the awarded amount keeps three decimals as text» (UAT 4220ccf) [CLAIM_RESULTS] | | — | — |
| LD-DOC-05-017 | 66 | عند إدخال حكم المحكمة، تظهر جميع الطلبات المسجلة، ويحدد المستخدم نتيجة المحكمة لكل طلب بصورة مستقلة، وتشمل | الإحالة. | YES | T: ClaimResultApiTest, JudgmentDetailsApiTest (CI green cac8d73)<br>J: wp2 «each claim gets its own result; the awarded amount keeps three decimals as text» (UAT 4220ccf) [CLAIM_RESULTS] | | — | — |
| LD-DOC-05-018 | 67 | عند إدخال حكم المحكمة، تظهر جميع الطلبات المسجلة، ويحدد المستخدم نتيجة المحكمة لكل طلب بصورة مستقلة، وتشمل | انقضاء الخصومة. | YES | T: ClaimResultApiTest, JudgmentDetailsApiTest (CI green cac8d73)<br>J: wp2 «each claim gets its own result; the awarded amount keeps three decimals as text» (UAT 4220ccf) [CLAIM_RESULTS] | | — | — |
| LD-DOC-05-019 | 68 | عند إدخال حكم المحكمة، تظهر جميع الطلبات المسجلة، ويحدد المستخدم نتيجة المحكمة لكل طلب بصورة مستقلة، وتشمل | إثبات التنازل. | YES | T: ClaimResultApiTest, JudgmentDetailsApiTest (CI green cac8d73)<br>J: wp2 «each claim gets its own result; the awarded amount keeps three decimals as text» (UAT 4220ccf) [CLAIM_RESULTS] | | — | — |
| LD-DOC-05-020 | 69 | عند إدخال حكم المحكمة، تظهر جميع الطلبات المسجلة، ويحدد المستخدم نتيجة المحكمة لكل طلب بصورة مستقلة، وتشمل | إثبات الترك. | YES | T: ClaimResultApiTest, JudgmentDetailsApiTest (CI green cac8d73)<br>J: wp2 «each claim gets its own result; the awarded amount keeps three decimals as text» (UAT 4220ccf) [CLAIM_RESULTS] | | — | — |
| LD-DOC-05-021 | 70 | عند إدخال حكم المحكمة، تظهر جميع الطلبات المسجلة، ويحدد المستخدم نتيجة المحكمة لكل طلب بصورة مستقلة، وتشمل | عدم الفصل في الطلب. | YES | T: ClaimResultApiTest, JudgmentDetailsApiTest (CI green cac8d73)<br>J: wp2 «each claim gets its own result; the awarded amount keeps three decimals as text» (UAT 4220ccf) [CLAIM_RESULTS] | | — | — |
| LD-DOC-05-022 | 71 | عند إدخال حكم المحكمة، تظهر جميع الطلبات المسجلة، ويحدد المستخدم نتيجة المحكمة لكل طلب بصورة مستقلة، وتشمل | تأجيل الفصل فيه. | YES | T: ClaimResultApiTest, JudgmentDetailsApiTest (CI green cac8d73)<br>J: wp2 «each claim gets its own result; the awarded amount keeps three decimals as text» (UAT 4220ccf) [CLAIM_RESULTS] | | — | — |
| LD-DOC-05-023 | 72 | عند إدخال حكم المحكمة، تظهر جميع الطلبات المسجلة، ويحدد المستخدم نتيجة المحكمة لكل طلب بصورة مستقلة، وتشمل | نتيجة أخرى. | YES | T: ClaimResultApiTest, JudgmentDetailsApiTest (CI green cac8d73)<br>J: wp2 «each claim gets its own result; the awarded amount keeps three decimals as text» (UAT 4220ccf) [CLAIM_RESULTS] | | — | — |
| LD-DOC-05-024 | 73 | عند إدخال حكم المحكمة، تظهر جميع الطلبات المسجلة، ويحدد المستخدم نتيجة المحكمة لكل طلب بصورة مستقلة، وتشمل | مع تسجيل منطوق الحكم وأسبابه والمبلغ المحكوم به والملاحظات لكل طلب. | YES | T: ClaimResultApiTest, JudgmentDetailsApiTest (CI green cac8d73)<br>J: wp2 «each claim gets its own result; the awarded amount keeps three decimals as text» (UAT 4220ccf) [CLAIM_RESULTS] | | — | — |
| LD-DOC-05-025 | 75 | الربط بين الطلبات ودرجات التقاضي | يجب الاحتفاظ بنتيجة كل طلب في كل درجة تقاضٍ، بحيث يمكن معرفة ما طلبه الخصم وما قضت به المحكمة الكلية والاستئناف والتمييز، دون استبدال النتائج السابقة. | YES | T: ClaimResultApiTest, JudgmentDetailsApiTest (CI green cac8d73)<br>J: wp2 «each claim gets its own result; the awarded amount keeps three decimals as text» (UAT 4220ccf) [CLAIM_RESULTS] | | — | — |
| LD-DOC-05-026 | 76 | الربط بين الطلبات ودرجات التقاضي | وتوفير تقارير إحصائية لنتائج الطلبات. | YES | T: StatisticsApiTest (CI green cac8d73)<br>J: wp2 «the statistics grew by the five results recorded» (UAT 4220ccf) [CLAIM_STATS] | | — | — |

<sub>عناوين المجموعات في المصدر: بيانات كل طلب (س48)؛ تسجيل نتيجة الحكم لكل طلب (س59)؛ عند إدخال حكم المحكمة، تظهر جميع الطلبات المسجلة، ويحدد المستخدم نتيجة المحكمة لكل طلب بصورة مستقلة، وتشمل: (س60)؛ الربط بين الطلبات ودرجات التقاضي (س74)</sub>

## LD-DOC-06 — سادساً: تحديد القطاع والإدارة والقسم المعني بالقضية

- **المرحلة**: المرحلة الأولى · **الأولوية**: أعلى · **حزمة العمل**: WP1 · **سيناريوهات القبول المرتبطة**: S15 (نطاق)
- **التحديث (2026-11-09)**: نُفِّذ بحزمة العمل؛ الدليل: DOC-WP1 (journey wp1-access).

| المعرّف | السطر | المجموعة | المتطلب (نص المصدر) | IMPLEMENTED | VERIFIED_BY_DEVELOPER | QA_ACCEPTED | سبب العائق | ملاحظة (ما تبقى / لماذا لم يُتحقق) |
|---|---|---|---|---|---|---|---|---|
| LD-DOC-06-001 | 79 | إضافة قسم باسم «الجهة الإدارية المعنية بالقضية»، وفق هيكل مترابط | القطاع ← الإدارة ← القسم. | YES | T: OrgUnitApiTest (CI green cac8d73)<br>J: wp1a «manager creates the authority's sector/department/section; the sector name is frozen on the record» (UAT 8d7c847) [ORG] | | — | — |
| LD-DOC-06-002 | 81 | مثال | القطاع: قطاع الخدمات المساندة. | YES | T: OrgUnitApiTest (CI green cac8d73)<br>J: wp1a «manager creates the authority's sector/department/section; the sector name is frozen on the record» (UAT 8d7c847) [ORG] | | — | — |
| LD-DOC-06-003 | 82 | مثال | الإدارة: إدارة الشؤون الإدارية. | YES | T: OrgUnitApiTest (CI green cac8d73)<br>J: wp1a «manager creates the authority's sector/department/section; the sector name is frozen on the record» (UAT 8d7c847) [ORG] | | — | — |
| LD-DOC-06-004 | 83 | مثال | القسم: قسم شؤون الموظفين. | YES | T: OrgUnitApiTest (CI green cac8d73)<br>J: wp1a «manager creates the authority's sector/department/section; the sector name is frozen on the record» (UAT 8d7c847) [ORG] | | — | — |
| LD-DOC-06-005 | 85 | ويشمل | القطاع المعني. | YES | T: OrgUnitApiTest (CI green cac8d73)<br>J: wp1a «manager creates the authority's sector/department/section; the sector name is frozen on the record» (UAT 8d7c847) [ORG] | | — | — |
| LD-DOC-06-006 | 86 | ويشمل | الإدارة المعنية. | YES | T: OrgUnitApiTest (CI green cac8d73)<br>J: wp1a «manager creates the authority's sector/department/section; the sector name is frozen on the record» (UAT 8d7c847) [ORG] | | — | — |
| LD-DOC-06-007 | 87 | ويشمل | القسم المعني. | YES | T: OrgUnitApiTest (CI green cac8d73)<br>J: wp1a «manager creates the authority's sector/department/section; the sector name is frozen on the record» (UAT 8d7c847) [ORG] | | — | — |
| LD-DOC-06-008 | 88 | ويشمل | الموظف المختص. | YES | T: OrgUnitApiTest (CI green cac8d73)<br>J: wp1a «manager creates the authority's sector/department/section; the sector name is frozen on the record» (UAT 8d7c847) [ORG] | | — | — |
| LD-DOC-06-009 | 89 | ويشمل | بيانات التواصل. | YES |  | | — | the involvement fields (contact, referral date, requested action, several authorities) are not asserted by any test or journey step found; the journey records only the primary involved unit |
| LD-DOC-06-010 | 90 | ويشمل | تاريخ الإحالة. | YES |  | | — | the involvement fields (contact, referral date, requested action, several authorities) are not asserted by any test or journey step found; the journey records only the primary involved unit |
| LD-DOC-06-011 | 91 | ويشمل | المطلوب من الجهة الإدارية. | YES |  | | — | the involvement fields (contact, referral date, requested action, several authorities) are not asserted by any test or journey step found; the journey records only the primary involved unit |
| LD-DOC-06-012 | 92 | ويشمل | الملاحظات. | YES |  | | — | the involvement fields (contact, referral date, requested action, several authorities) are not asserted by any test or journey step found; the journey records only the primary involved unit |
| LD-DOC-06-013 | 93 | ويشمل | مع إمكانية إضافة أكثر من جهة معنية بالقضية، وتحديد الجهة الرئيسية والجهات المساندة. | YES |  | | — | the involvement fields (contact, referral date, requested action, several authorities) are not asserted by any test or journey step found; the journey records only the primary involved unit |
| LD-DOC-06-014 | 94 | ويشمل | ويجب أن تكون القوائم قابلة للتحديث من الإعدادات، مع الاحتفاظ بالهيكل التنظيمي التاريخي وقت تسجيل القضية. | YES | T: OrgUnitApiTest (CI green cac8d73)<br>J: wp1a «manager creates the authority's sector/department/section; the sector name is frozen on the record» (UAT 8d7c847) [ORG] | | — | — |

<sub>عناوين المجموعات في المصدر: إضافة قسم باسم «الجهة الإدارية المعنية بالقضية»، وفق هيكل مترابط: (س78)؛ مثال: (س80)؛ ويشمل: (س84)</sub>

## LD-DOC-07 — سابعاً: سجل الجلسات والإجراءات القضائية

- **المرحلة**: المرحلة الأولى · **الأولوية**: أعلى · **حزمة العمل**: WP3 · **سيناريوهات القبول المرتبطة**: S9
- **التحديث (2026-11-09)**: نُفِّذ بحزمة العمل؛ الدليل: DOC-WP3 (journey: S9).

| المعرّف | السطر | المجموعة | المتطلب (نص المصدر) | IMPLEMENTED | VERIFIED_BY_DEVELOPER | QA_ACCEPTED | سبب العائق | ملاحظة (ما تبقى / لماذا لم يُتحقق) |
|---|---|---|---|---|---|---|---|---|
| LD-DOC-07-001 | 96 | — | إنشاء سجل تاريخي كامل للجلسات في كل قضية وفي كل درجة تقاضٍ، وعدم استبدال الجلسة السابقة عند التأجيل. | YES | T: HearingApiTest, HearingExtensionApiTest, HearingChainSerializationTest (CI green cac8d73)<br>J: wp3 «staff schedules a hearing with the extended record; staff adjourns it to a next hearing» (UAT 3937a50) [HEARINGS] | | — | — |
| LD-DOC-07-002 | 98 | بيانات الجلسة | تاريخ الجلسة ووقتها. | YES | T: HearingApiTest, HearingExtensionApiTest, HearingChainSerializationTest (CI green cac8d73)<br>J: wp3 «staff schedules a hearing with the extended record; staff adjourns it to a next hearing» (UAT 3937a50) [HEARINGS] | | — | — |
| LD-DOC-07-003 | 99 | بيانات الجلسة | المحكمة والدائرة ودرجة التقاضي. | YES | T: HearingApiTest, HearingExtensionApiTest, HearingChainSerializationTest (CI green cac8d73)<br>J: wp3 «staff schedules a hearing with the extended record; staff adjourns it to a next hearing» (UAT 3937a50) [HEARINGS] | | — | — |
| LD-DOC-07-004 | 100 | بيانات الجلسة | نوع الجلسة: محكمة، خبراء، تحقيق، أو إجراء آخر. | YES | T: HearingApiTest, HearingExtensionApiTest, HearingChainSerializationTest (CI green cac8d73)<br>J: wp3 «staff schedules a hearing with the extended record; staff adjourns it to a next hearing» (UAT 3937a50) [HEARINGS] | | — | — |
| LD-DOC-07-005 | 101 | بيانات الجلسة | اسم القاضي أو الخبير عند توافره. | YES |  | | — | no test found for the judge/expert name field |
| LD-DOC-07-006 | 102 | بيانات الجلسة | العضو القانوني المسؤول. | YES | T: HearingApiTest, HearingExtensionApiTest, HearingChainSerializationTest (CI green cac8d73)<br>J: wp3 «staff schedules a hearing with the extended record; staff adjourns it to a next hearing» (UAT 3937a50) [HEARINGS] | | — | — |
| LD-DOC-07-007 | 103 | بيانات الجلسة | الحاضرون. | YES | T: HearingApiTest, HearingExtensionApiTest, HearingChainSerializationTest (CI green cac8d73)<br>J: wp3 «staff schedules a hearing with the extended record; staff adjourns it to a next hearing» (UAT 3937a50) [HEARINGS] | | — | — |
| LD-DOC-07-008 | 104 | بيانات الجلسة | ما تم في الجلسة. | YES | T: HearingApiTest, HearingExtensionApiTest, HearingChainSerializationTest (CI green cac8d73)<br>J: wp3 «staff schedules a hearing with the extended record; staff adjourns it to a next hearing» (UAT 3937a50) [HEARINGS] | | — | — |
| LD-DOC-07-009 | 105 | بيانات الجلسة | طلبات الخصوم. | YES | T: HearingApiTest, HearingExtensionApiTest, HearingChainSerializationTest (CI green cac8d73)<br>J: wp3 «staff schedules a hearing with the extended record; staff adjourns it to a next hearing» (UAT 3937a50) [HEARINGS] | | — | — |
| LD-DOC-07-010 | 106 | بيانات الجلسة | قرار المحكمة أو القاضي أو الخبير. | YES | T: HearingApiTest, HearingExtensionApiTest, HearingChainSerializationTest (CI green cac8d73)<br>J: wp3 «staff schedules a hearing with the extended record; staff adjourns it to a next hearing» (UAT 3937a50) [HEARINGS] | | — | — |
| LD-DOC-07-011 | 107 | بيانات الجلسة | الإجراء المطلوب. | YES | T: HearingApiTest, HearingExtensionApiTest, HearingChainSerializationTest (CI green cac8d73)<br>J: wp3 «staff schedules a hearing with the extended record; staff adjourns it to a next hearing» (UAT 3937a50) [HEARINGS] | | — | — |
| LD-DOC-07-012 | 108 | بيانات الجلسة | هل تم التأجيل؟ | YES | T: HearingApiTest, HearingExtensionApiTest, HearingChainSerializationTest (CI green cac8d73)<br>J: wp3 «staff schedules a hearing with the extended record; staff adjourns it to a next hearing» (UAT 3937a50) [HEARINGS] | | — | — |
| LD-DOC-07-013 | 109 | بيانات الجلسة | سبب التأجيل. | YES | T: HearingApiTest, HearingExtensionApiTest, HearingChainSerializationTest (CI green cac8d73)<br>J: wp3 «staff schedules a hearing with the extended record; staff adjourns it to a next hearing» (UAT 3937a50) [HEARINGS] | | — | — |
| LD-DOC-07-014 | 110 | بيانات الجلسة | تاريخ الجلسة التالية. | YES | T: HearingApiTest, HearingExtensionApiTest, HearingChainSerializationTest (CI green cac8d73)<br>J: wp3 «staff schedules a hearing with the extended record; staff adjourns it to a next hearing» (UAT 3937a50) [HEARINGS] | | — | — |
| LD-DOC-07-015 | 111 | بيانات الجلسة | الملاحظات والمرفقات. | YES | T: HearingApiTest, HearingExtensionApiTest, HearingChainSerializationTest (CI green cac8d73)<br>J: wp3 «staff schedules a hearing with the extended record; staff adjourns it to a next hearing» (UAT 3937a50) [HEARINGS] | | — | — |
| LD-DOC-07-016 | 112 | بيانات الجلسة | عند التأجيل، يتم الاحتفاظ بسجل الجلسة السابقة وإنشاء سجل مستقل للموعد التالي. | YES | T: HearingApiTest, HearingExtensionApiTest, HearingChainSerializationTest (CI green cac8d73)<br>J: wp3 «staff schedules a hearing with the extended record; staff adjourns it to a next hearing» (UAT 3937a50) [HEARINGS] | | — | — |
| LD-DOC-07-017 | 113 | بيانات الجلسة | إذا لم تحدد المحكمة موعداً تالياً، يتم تسجيل ذلك وسبب عدم تحديده وإدراج القضية ضمن المتابعة. | YES | T: HearingExtensionApiTest (CI green cac8d73)<br>J: wp3 «an adjournment with no next date is recorded; it creates no hearing and joins the follow-up list» (UAT 3937a50) [HEARING_NO_DATE] | | — | — |
| LD-DOC-07-018 | 114 | بيانات الجلسة | ويجب عرض جميع الجلسات السابقة واللاحقة في سجل زمني موحد، مع بيان عدد الجلسات والتأجيلات وأسبابها وقرارات المحكمة والخبراء. | YES | T: HearingChainSerializationTest, CaseTimelineApiTest (CI green cac8d73)<br>F: CaseHearings.test.tsx (CI green cac8d73)<br>J: wp3 «both hearings are in the file: the previous one is kept; the upcoming hearing is marked as future and links to its record» (UAT 3937a50) [HEARING_TIMELINE] | | — | — |

<sub>عناوين المجموعات في المصدر: بيانات الجلسة (س97)</sub>

## LD-DOC-08 — ثامناً: القضايا المرتبطة ببعضها

- **المرحلة**: المرحلة الأولى · **الأولوية**: أعلى · **حزمة العمل**: WP3 · **سيناريوهات القبول المرتبطة**: S10
- **التحديث (2026-11-09)**: نُفِّذ بحزمة العمل؛ الدليل: DOC-WP3 (journey: S10, S12).

| المعرّف | السطر | المجموعة | المتطلب (نص المصدر) | IMPLEMENTED | VERIFIED_BY_DEVELOPER | QA_ACCEPTED | سبب العائق | ملاحظة (ما تبقى / لماذا لم يُتحقق) |
|---|---|---|---|---|---|---|---|---|
| LD-DOC-08-001 | 116 | — | إنشاء وحدة باسم «القضايا المرتبطة»، تتيح ربط القضية بقضية أخرى أو أكثر، بناءً على طلب أحد الخصوم أو الجهة أو قرار المحكمة. | YES | T: CaseLinkApiTest (CI green cac8d73)<br>J: wp3 «a request to join starts as requested, not decided; the link shows in the file of case A» (UAT 3937a50) [LINKS] | | — | — |
| LD-DOC-08-002 | 118 | وتشمل بيانات الارتباط | رقم القضية الأخرى. | YES | T: CaseLinkApiTest (CI green cac8d73)<br>J: wp3 «a request to join starts as requested, not decided; the link shows in the file of case A» (UAT 3937a50) [LINKS] | | — | — |
| LD-DOC-08-003 | 119 | وتشمل بيانات الارتباط | المحكمة ودرجة التقاضي. | YES | T: CaseLinkApiTest (CI green cac8d73)<br>J: wp3 «a request to join starts as requested, not decided; the link shows in the file of case A» (UAT 3937a50) [LINKS] | | — | — |
| LD-DOC-08-004 | 120 | وتشمل بيانات الارتباط | موضوع القضية. | YES | T: CaseLinkApiTest (CI green cac8d73)<br>J: wp3 «a request to join starts as requested, not decided; the link shows in the file of case A» (UAT 3937a50) [LINKS] | | — | — |
| LD-DOC-08-005 | 121 | وتشمل بيانات الارتباط | نوع الارتباط وسببه. | YES | T: CaseLinkApiTest (CI green cac8d73)<br>J: wp3 «a request to join starts as requested, not decided; the link shows in the file of case A» (UAT 3937a50) [LINKS] | | — | — |
| LD-DOC-08-006 | 122 | وتشمل بيانات الارتباط | مقدم طلب الارتباط. | YES | T: CaseLinkApiTest (CI green cac8d73)<br>J: wp3 «a request to join starts as requested, not decided; the link shows in the file of case A» (UAT 3937a50) [LINKS] | | — | — |
| LD-DOC-08-007 | 123 | وتشمل بيانات الارتباط | تاريخ الطلب. | YES | T: CaseLinkApiTest (CI green cac8d73)<br>J: wp3 «a request to join starts as requested, not decided; the link shows in the file of case A» (UAT 3937a50) [LINKS] | | — | — |
| LD-DOC-08-008 | 124 | وتشمل بيانات الارتباط | هل صدر قرار قضائي؟ | YES | T: CaseLinkApiTest (CI green cac8d73)<br>J: wp3 «a request to join starts as requested, not decided; the link shows in the file of case A» (UAT 3937a50) [LINKS] | | — | — |
| LD-DOC-08-009 | 125 | وتشمل بيانات الارتباط | تاريخ القرار ومنطوقه. | YES |  | | — | decision text/copy on a link: only the decision itself is asserted; no step for its copy |
| LD-DOC-08-010 | 126 | وتشمل بيانات الارتباط | نسخة القرار. | YES |  | | — | decision text/copy on a link: only the decision itself is asserted; no step for its copy |
| LD-DOC-08-011 | 127 | وتشمل بيانات الارتباط | حالة الارتباط. | YES | T: CaseLinkApiTest (CI green cac8d73)<br>J: wp3 «a request to join starts as requested, not decided; the link shows in the file of case A» (UAT 3937a50) [LINKS] | | — | — |
| LD-DOC-08-012 | 128 | وتشمل بيانات الارتباط | ويجب أن يظهر الارتباط تلقائياً في ملف كلتا القضيتين، مع التمييز بين الارتباط الموضوعي ومجرد طلب الضم وقرار الضم أو الإحالة الصادر من المحكمة. | YES | T: CaseLinkApiTest (CI green cac8d73)<br>J: wp3 «a request to join starts as requested, not decided; the link shows in the file of case A» (UAT 3937a50) [LINKS] | | — | — |
| LD-DOC-08-013 | 129 | وتشمل بيانات الارتباط | ولا يؤدي إغلاق إحدى القضيتين إلى إغلاق الأخرى تلقائياً. | YES | T: CaseLinkApiTest (CI green cac8d73)<br>J: wp3 «closing it did not close the linked case» (UAT 3937a50) [LINK_NO_CLOSE] | | — | — |

<sub>عناوين المجموعات في المصدر: وتشمل بيانات الارتباط: (س117)</sub>

## LD-DOC-09 — تاسعاً: الأحكام القضائية

- **المرحلة**: المرحلة الأولى · **الأولوية**: أعلى · **حزمة العمل**: WP2 · **سيناريوهات القبول المرتبطة**: S1, S13
- **التحديث (2026-11-09)**: نُفِّذ بحزمة العمل؛ الدليل: DOC-WP2 (journey wp2: S1, S13).

| المعرّف | السطر | المجموعة | المتطلب (نص المصدر) | IMPLEMENTED | VERIFIED_BY_DEVELOPER | QA_ACCEPTED | سبب العائق | ملاحظة (ما تبقى / لماذا لم يُتحقق) |
|---|---|---|---|---|---|---|---|---|
| LD-DOC-09-001 | 132 | إنشاء وحدة مستقلة للأحكام تتضمن | تاريخ الحكم. | YES | T: JudgmentApiTest, JudgmentDetailsApiTest (CI green cac8d73)<br>J: wp2 «the manager records the extended judgment; the appeal judgment is recorded (legacy request, no details)» (UAT 4220ccf) [JUDGMENTS] | | — | — |
| LD-DOC-09-002 | 133 | إنشاء وحدة مستقلة للأحكام تتضمن | المحكمة والدائرة. | YES | T: JudgmentApiTest, JudgmentDetailsApiTest (CI green cac8d73)<br>J: wp2 «the manager records the extended judgment; the appeal judgment is recorded (legacy request, no details)» (UAT 4220ccf) [JUDGMENTS] | | — | — |
| LD-DOC-09-003 | 134 | إنشاء وحدة مستقلة للأحكام تتضمن | درجة التقاضي. | YES | T: JudgmentApiTest, JudgmentDetailsApiTest (CI green cac8d73)<br>J: wp2 «the manager records the extended judgment; the appeal judgment is recorded (legacy request, no details)» (UAT 4220ccf) [JUDGMENTS] | | — | — |
| LD-DOC-09-004 | 135 | إنشاء وحدة مستقلة للأحكام تتضمن | نوع الحكم. | YES | T: JudgmentApiTest, JudgmentDetailsApiTest (CI green cac8d73)<br>J: wp2 «the manager records the extended judgment; the appeal judgment is recorded (legacy request, no details)» (UAT 4220ccf) [JUDGMENTS] | | — | — |
| LD-DOC-09-005 | 136 | إنشاء وحدة مستقلة للأحكام تتضمن | منطوق الحكم كاملاً. | YES | T: JudgmentApiTest, JudgmentDetailsApiTest (CI green cac8d73)<br>J: wp2 «the manager records the extended judgment; the appeal judgment is recorded (legacy request, no details)» (UAT 4220ccf) [JUDGMENTS] | | — | — |
| LD-DOC-09-006 | 137 | إنشاء وحدة مستقلة للأحكام تتضمن | أسباب الحكم. | YES | T: JudgmentApiTest, JudgmentDetailsApiTest (CI green cac8d73)<br>J: wp2 «the manager records the extended judgment; the appeal judgment is recorded (legacy request, no details)» (UAT 4220ccf) [JUDGMENTS] | | — | — |
| LD-DOC-09-007 | 138 | إنشاء وحدة مستقلة للأحكام تتضمن | النتيجة. | YES | T: JudgmentApiTest, JudgmentDetailsApiTest (CI green cac8d73)<br>J: wp2 «the manager records the extended judgment; the appeal judgment is recorded (legacy request, no details)» (UAT 4220ccf) [JUDGMENTS] | | — | — |
| LD-DOC-09-008 | 139 | إنشاء وحدة مستقلة للأحكام تتضمن | نتيجة كل طلب من طلبات الدعوى. | YES | T: ClaimResultApiTest, JudgmentDetailsApiTest (CI green cac8d73)<br>J: wp2 «each claim gets its own result; the awarded amount keeps three decimals as text» (UAT 4220ccf) [CLAIM_RESULTS] | | — | — |
| LD-DOC-09-009 | 140 | إنشاء وحدة مستقلة للأحكام تتضمن | المبالغ المحكوم بها. | YES | T: JudgmentApiTest, JudgmentDetailsApiTest (CI green cac8d73)<br>J: wp2 «the manager records the extended judgment; the appeal judgment is recorded (legacy request, no details)» (UAT 4220ccf) [JUDGMENTS] | | — | — |
| LD-DOC-09-010 | 141 | إنشاء وحدة مستقلة للأحكام تتضمن | المصروفات وأتعاب المحاماة. | YES | T: JudgmentApiTest, JudgmentDetailsApiTest (CI green cac8d73)<br>J: wp2 «the manager records the extended judgment; the appeal judgment is recorded (legacy request, no details)» (UAT 4220ccf) [JUDGMENTS] | | — | — |
| LD-DOC-09-011 | 142 | إنشاء وحدة مستقلة للأحكام تتضمن | صفة الحكم. | YES | T: JudgmentApiTest, JudgmentDetailsApiTest (CI green cac8d73)<br>J: wp2 «the manager records the extended judgment; the appeal judgment is recorded (legacy request, no details)» (UAT 4220ccf) [JUDGMENTS] | | — | — |
| LD-DOC-09-012 | 143 | إنشاء وحدة مستقلة للأحكام تتضمن | قابليته للطعن. | YES | T: JudgmentApiTest, JudgmentDetailsApiTest (CI green cac8d73)<br>J: wp2 «the manager records the extended judgment; the appeal judgment is recorded (legacy request, no details)» (UAT 4220ccf) [JUDGMENTS] | | — | — |
| LD-DOC-09-013 | 144 | إنشاء وحدة مستقلة للأحكام تتضمن | تاريخ الإعلان. | YES | T: JudgmentApiTest, JudgmentDetailsApiTest (CI green cac8d73)<br>J: wp2 «the manager records the extended judgment; the appeal judgment is recorded (legacy request, no details)» (UAT 4220ccf) [JUDGMENTS] | | — | — |
| LD-DOC-09-014 | 145 | إنشاء وحدة مستقلة للأحكام تتضمن | ميعاد الطعن. | YES | T: DeadlineApiTest, GapClosureApiTest#aJudgmentNamesItsOwnAppealDeadlineOnly (CI green cac8d73)<br>J: conn «a deadline runs from the judgment; the judgment names it as its appeal deadline» (UAT 3937a50)<br>J: wp4 «the judgment shows its own deadline» (UAT 82c3285) [JUDGMENT_DEADLINE] | | — | — |
| LD-DOC-09-015 | 146 | إنشاء وحدة مستقلة للأحكام تتضمن | حالة الحكم. | YES | T: JudgmentApiTest, JudgmentDetailsApiTest (CI green cac8d73)<br>J: wp2 «the manager records the extended judgment; the appeal judgment is recorded (legacy request, no details)» (UAT 4220ccf) [JUDGMENTS] | | — | — |
| LD-DOC-09-016 | 147 | إنشاء وحدة مستقلة للأحكام تتضمن | صورة الحكم. | YES | T: DocumentLinkApiTest (CI green cac8d73)<br>J: wp5 «a document is linked to the judgment it concerns» (UAT 2bcd2dc) [JUDGMENT_FILE] | | — | — |
| LD-DOC-09-017 | 148 | إنشاء وحدة مستقلة للأحكام تتضمن | ويجب الاحتفاظ بجميع الأحكام السابقة عند انتقال القضية إلى درجة أعلى. | YES | T: JudgmentApiTest, JudgmentDetailsApiTest (CI green cac8d73)<br>J: wp2 «the manager records the extended judgment; the appeal judgment is recorded (legacy request, no details)» (UAT 4220ccf) [JUDGMENTS] | | — | — |

<sub>عناوين المجموعات في المصدر: إنشاء وحدة مستقلة للأحكام تتضمن: (س131)</sub>

## LD-DOC-10 — عاشراً: متابعة مواعيد الطعون والإجراءات القانونية

- **المرحلة**: المرحلة الأولى · **الأولوية**: أعلى · **حزمة العمل**: WP4 · **سيناريوهات القبول المرتبطة**: S14
- **التحديث (2026-11-09)**: نُفِّذ بحزمة العمل؛ الدليل: DOC-WP4 (journey wp4: S14 للمواعيد اليدوية والقواعد المعتمدة).

| المعرّف | السطر | المجموعة | المتطلب (نص المصدر) | IMPLEMENTED | VERIFIED_BY_DEVELOPER | QA_ACCEPTED | سبب العائق | ملاحظة (ما تبقى / لماذا لم يُتحقق) |
|---|---|---|---|---|---|---|---|---|
| LD-DOC-10-001 | 150 | — | إنشاء وحدة لمتابعة المواعيد القانونية المرتبطة بالأحكام والإجراءات، ومنها الاستئناف والتمييز والتظلمات ومواعيد تقديم المذكرات والتنفيذ. | YES | T: DeadlineApiTest, DeadlineReminderApiTest (CI green cac8d73)<br>J: wp4 «staff records a deadline from the judgment; a competent member approves it» (UAT 82c3285) [DEADLINES] | | — | — |
| LD-DOC-10-002 | 152 | ويجب أن تتضمن | مصدر الميعاد. | YES | T: DeadlineApiTest, DeadlineReminderApiTest (CI green cac8d73)<br>J: wp4 «staff records a deadline from the judgment; a competent member approves it» (UAT 82c3285) [DEADLINES] | | — | — |
| LD-DOC-10-003 | 153 | ويجب أن تتضمن | تاريخ بدء الميعاد. | YES | T: DeadlineApiTest, DeadlineReminderApiTest (CI green cac8d73)<br>J: wp4 «staff records a deadline from the judgment; a competent member approves it» (UAT 82c3285) [DEADLINES] | | — | — |
| LD-DOC-10-004 | 154 | ويجب أن تتضمن | المدة القانونية. | YES | T: DeadlineApiTest, DeadlineReminderApiTest (CI green cac8d73)<br>J: wp4 «staff records a deadline from the judgment; a competent member approves it» (UAT 82c3285) [DEADLINES] | | — | — |
| LD-DOC-10-005 | 155 | ويجب أن تتضمن | آخر موعد لاتخاذ الإجراء. | YES | T: DeadlineApiTest, DeadlineReminderApiTest (CI green cac8d73)<br>J: wp4 «staff records a deadline from the judgment; a competent member approves it» (UAT 82c3285) [DEADLINES] | | — | — |
| LD-DOC-10-006 | 156 | ويجب أن تتضمن | الإجراء المطلوب. | YES | T: DeadlineApiTest, DeadlineReminderApiTest (CI green cac8d73)<br>J: wp4 «staff records a deadline from the judgment; a competent member approves it» (UAT 82c3285) [DEADLINES] | | — | — |
| LD-DOC-10-007 | 157 | ويجب أن تتضمن | العضو المسؤول. | YES | T: DeadlineApiTest, DeadlineReminderApiTest (CI green cac8d73)<br>J: wp4 «staff records a deadline from the judgment; a competent member approves it» (UAT 82c3285) [DEADLINES] | | — | — |
| LD-DOC-10-008 | 158 | ويجب أن تتضمن | حالة الإجراء. | YES | T: DeadlineApiTest, DeadlineReminderApiTest (CI green cac8d73)<br>J: wp4 «staff records a deadline from the judgment; a competent member approves it» (UAT 82c3285) [DEADLINES] | | — | — |
| LD-DOC-10-009 | 159 | ويجب أن تتضمن | تاريخ إتمامه. | YES | T: DeadlineApiTest, DeadlineReminderApiTest (CI green cac8d73)<br>J: wp4 «staff records a deadline from the judgment; a competent member approves it» (UAT 82c3285) [DEADLINES] | | — | — |
| LD-DOC-10-010 | 160 | ويجب أن تتضمن | المستند المثبت. | YES | T: DeadlineApiTest, DeadlineReminderApiTest (CI green cac8d73)<br>J: wp4 «staff records a deadline from the judgment; a competent member approves it» (UAT 82c3285) [DEADLINES] | | — | — |
| LD-DOC-10-011 | 161 | ويجب أن تتضمن | مع إمكانية ضبط المدد من الإعدادات ومراعاة القواعد القانونية والعطلات الرسمية بحسب كل ميعاد، وإرسال تنبيهات قبل انتهائه. | PARTIAL |  | | LEGAL_INPUT | DONE and verified: manual approved deadlines, rule/holiday draft+approval flow, and the calculator on SYNTHETIC rules (5 working days, weekend, holiday skip). NOT done: no legal period rule or holiday exists in the system (none invented). Whether reminder lead times must differ per deadline type is an open owner question; the text asks only for alerts before expiry. Dependency: D6: specialists enter rules and official holidays with their sources; owner says who enters and who approves. |
| LD-DOC-10-012 | 162 | ويجب أن تتضمن | ويجب أن يكون الاحتساب الآلي قابلاً للمراجعة والاعتماد من العضو القانوني المختص، مع تسجيل اسم المعتمد وتاريخ الاعتماد. | PARTIAL |  | | LEGAL_INPUT | DONE and verified: calculated dates are proposals only, official only after approval by a competent member, with the arithmetic kept. NOT possible yet: review of a real legal calculation. Dependency: D6: no approved legal rule exists to calculate against. |

<sub>عناوين المجموعات في المصدر: ويجب أن تتضمن: (س151)</sub>

## LD-DOC-11 — الحادي عشر: تنفيذ الأحكام القضائية

- **المرحلة**: المرحلة الثانية · **الأولوية**: عالية · **حزمة العمل**: WP5 · **سيناريوهات القبول المرتبطة**: —
- **التحديث (2026-11-09)**: نُفِّذ بحزمة العمل؛ الدليل: DOC-WP5 (journey wp5).

| المعرّف | السطر | المجموعة | المتطلب (نص المصدر) | IMPLEMENTED | VERIFIED_BY_DEVELOPER | QA_ACCEPTED | سبب العائق | ملاحظة (ما تبقى / لماذا لم يُتحقق) |
|---|---|---|---|---|---|---|---|---|
| LD-DOC-11-001 | 164 | — | إنشاء وحدة لمتابعة تنفيذ الأحكام، تتضمن رقم الحكم وتاريخه ومنطوقه والجهة المكلفة بالتنفيذ والمبالغ المطلوبة والإجراءات والمراسلات والعقبات وتاريخ تمام التنفيذ وحالة التنفيذ. | YES | T: EnforcementApiTest (CI green cac8d73)<br>J: wp5 «the specialist opens the enforcement file; an action is recorded with its number, date and result» (UAT 2bcd2dc) [ENFORCEMENT] | | — | — |
| LD-DOC-11-002 | 165 | — | ويجب إظهار الأحكام التي لم يتم تنفيذها أو تأخر تنفيذها. | YES | T: EnforcementApiTest (CI green cac8d73)<br>J: wp5 «the specialist opens the enforcement file; an action is recorded with its number, date and result» (UAT 2bcd2dc) [ENFORCEMENT] | | — | — |

## LD-DOC-12 — الثاني عشر: المذكرات والمراسلات القانونية

- **المرحلة**: المرحلة الثانية · **الأولوية**: عالية · **حزمة العمل**: WP5 · **سيناريوهات القبول المرتبطة**: —
- **التحديث (2026-11-09)**: نُفِّذ بحزمة العمل؛ الدليل: DOC-WP5 (journey wp5).

| المعرّف | السطر | المجموعة | المتطلب (نص المصدر) | IMPLEMENTED | VERIFIED_BY_DEVELOPER | QA_ACCEPTED | سبب العائق | ملاحظة (ما تبقى / لماذا لم يُتحقق) |
|---|---|---|---|---|---|---|---|---|
| LD-DOC-12-001 | 168 | إدارة جميع المذكرات والمراسلات المرتبطة بالقضية، بما في ذلك | صحف الدعاوى. | YES | T: LegalDocumentApiTest (CI green cac8d73)<br>J: wp5 «the specialist registers a memo as a draft; the manager approves» (UAT 2bcd2dc) [LEGALDOCS] | | — | — |
| LD-DOC-12-002 | 169 | إدارة جميع المذكرات والمراسلات المرتبطة بالقضية، بما في ذلك | مذكرات الدفاع. | YES | T: LegalDocumentApiTest (CI green cac8d73)<br>J: wp5 «the specialist registers a memo as a draft; the manager approves» (UAT 2bcd2dc) [LEGALDOCS] | | — | — |
| LD-DOC-12-003 | 170 | إدارة جميع المذكرات والمراسلات المرتبطة بالقضية، بما في ذلك | مذكرات الخصوم. | YES | T: LegalDocumentApiTest (CI green cac8d73)<br>J: wp5 «the specialist registers a memo as a draft; the manager approves» (UAT 2bcd2dc) [LEGALDOCS] | | — | — |
| LD-DOC-12-004 | 171 | إدارة جميع المذكرات والمراسلات المرتبطة بالقضية، بما في ذلك | صحف الاستئناف والتمييز. | YES | T: LegalDocumentApiTest (CI green cac8d73)<br>J: wp5 «the specialist registers a memo as a draft; the manager approves» (UAT 2bcd2dc) [LEGALDOCS] | | — | — |
| LD-DOC-12-005 | 172 | إدارة جميع المذكرات والمراسلات المرتبطة بالقضية، بما في ذلك | الكتب المتبادلة مع الإدارات. | YES | T: LegalDocumentApiTest (CI green cac8d73)<br>J: wp5 «the specialist registers a memo as a draft; the manager approves» (UAT 2bcd2dc) [LEGALDOCS] | | — | — |
| LD-DOC-12-006 | 173 | إدارة جميع المذكرات والمراسلات المرتبطة بالقضية، بما في ذلك | المراسلات مع المحاكم والجهات الحكومية. | YES | T: LegalDocumentApiTest (CI green cac8d73)<br>J: wp5 «the specialist registers a memo as a draft; the manager approves» (UAT 2bcd2dc) [LEGALDOCS] | | — | — |
| LD-DOC-12-007 | 174 | إدارة جميع المذكرات والمراسلات المرتبطة بالقضية، بما في ذلك | تقارير الخبراء. | YES | T: LegalDocumentApiTest (CI green cac8d73)<br>J: wp5 «the specialist registers a memo as a draft; the manager approves» (UAT 2bcd2dc) [LEGALDOCS] | | — | — |
| LD-DOC-12-008 | 175 | إدارة جميع المذكرات والمراسلات المرتبطة بالقضية، بما في ذلك | الأحكام ومحاضر الجلسات. | YES | T: LegalDocumentApiTest (CI green cac8d73)<br>J: wp5 «the specialist registers a memo as a draft; the manager approves» (UAT 2bcd2dc) [LEGALDOCS] | | — | — |
| LD-DOC-12-009 | 176 | إدارة جميع المذكرات والمراسلات المرتبطة بالقضية، بما في ذلك | ويجب أن يتضمن كل مستند اسمه ونوعه وتاريخه والجهة الصادرة عنه والجهة الموجه إليها والعضو المسؤول ووصفه ونسخته الإلكترونية. | YES |  | | — | document metadata set (issuer/addressee) is not asserted separately |
| LD-DOC-12-010 | 177 | إدارة جميع المذكرات والمراسلات المرتبطة بالقضية، بما في ذلك | كما يجب توفير دورة اعتماد للمستندات: مسودة، قيد المراجعة، معتمد، مرسل، محفوظ. | YES | T: LegalDocumentApiTest (CI green cac8d73)<br>J: wp5 «the specialist registers a memo as a draft; the manager approves» (UAT 2bcd2dc) [LEGALDOCS] | | — | — |

<sub>عناوين المجموعات في المصدر: إدارة جميع المذكرات والمراسلات المرتبطة بالقضية، بما في ذلك: (س167)</sub>

## LD-DOC-13 — الثالث عشر: الأرشيف الإلكتروني

- **المرحلة**: المرحلة الثانية · **الأولوية**: عالية · **حزمة العمل**: WP5 · **سيناريوهات القبول المرتبطة**: S8
- **التحديث (2026-11-09)**: نُفِّذ بحزمة العمل؛ الدليل: DOC-WP5 (journey wp5؛ OCR غير مُتحقَّق).

| المعرّف | السطر | المجموعة | المتطلب (نص المصدر) | IMPLEMENTED | VERIFIED_BY_DEVELOPER | QA_ACCEPTED | سبب العائق | ملاحظة (ما تبقى / لماذا لم يُتحقق) |
|---|---|---|---|---|---|---|---|---|
| LD-DOC-13-001 | 180 | إنشاء أرشيف إلكتروني مركزي مرتبط بملف القضية، يدعم | ملفات PDF وWord والصور والصيغ المعتمدة. | YES | T: FileFormatsTest, ArchiveFormatsAndSettingsApiTest (CI green cac8d73)<br>J: wp5 «a file that is not what it claims is refused; a script is refused» (UAT 2bcd2dc) [ARCH_FORMATS] | | — | — |
| LD-DOC-13-002 | 181 | إنشاء أرشيف إلكتروني مركزي مرتبط بملف القضية، يدعم | تصنيف المستندات حسب النوع والتاريخ والمرحلة القضائية. | YES | T: ArchiveTextSearchApiTest, DocumentApiTest (CI green cac8d73)<br>J: wp5 «a document is classified by type, date and stage; the search can be limited to a category» (UAT 2bcd2dc) [ARCH_CLASS] | | — | — |
| LD-DOC-13-003 | 182 | إنشاء أرشيف إلكتروني مركزي مرتبط بملف القضية، يدعم | البحث داخل المستندات. | YES | T: ArchiveTextSearchApiTest (CI green cac8d73)<br>J: wp5 «the specialist finds the PDF by its text; the snippet is the matching line only» (UAT 2bcd2dc) [ARCH_SEARCH] | | — | — |
| LD-DOC-13-004 | 183 | إنشاء أرشيف إلكتروني مركزي مرتبط بملف القضية، يدعم | OCR لاستخراج النصوص من الصور والمستندات الممسوحة ضوئياً. | YES | T: TesseractOcrEngineTest, OcrTextSearchTest, OcrQualityTest (CI green cac8d73)<br>J: ocr «ocr-clear-ar.png: OCR completed; unified search 'OCRJOURNEYONE' finds ocr-clear-ar.png through its text» (UAT 1b502c1)<br>E: UAT-RUN.md, corpus-results.tsv (OCR journey on UAT release 1b502c1 (synthetic corpus only)) [OCR] | | — | — |
| LD-DOC-13-005 | 184 | إنشاء أرشيف إلكتروني مركزي مرتبط بملف القضية، يدعم | حفظ النسخ السابقة. | YES | T: ArchiveVersionsApiTest (CI green cac8d73)<br>J: wp5 «a new version keeps the earlier ones; both versions list who uploaded them and when» (UAT 2bcd2dc) [ARCH_VERSIONS] | | — | — |
| LD-DOC-13-006 | 185 | إنشاء أرشيف إلكتروني مركزي مرتبط بملف القضية، يدعم | تسجيل اسم رافع المستند وتاريخ رفعه. | YES | T: ArchiveVersionsApiTest (CI green cac8d73)<br>J: wp5 «a new version keeps the earlier ones; both versions list who uploaded them and when» (UAT 2bcd2dc) [ARCH_VERSIONS] | | — | — |
| LD-DOC-13-007 | 186 | إنشاء أرشيف إلكتروني مركزي مرتبط بملف القضية، يدعم | منع الحذف النهائي إلا وفق صلاحيات وإجراءات معتمدة. | YES | T: ArchiveDeletionApiTest (CI green cac8d73)<br>J: wp5 «a permanent deletion is refused without the archive right; a document is withdrawn from the case file with a reason» (UAT 2bcd2dc) [ARCH_DELETE] | | — | — |
| LD-DOC-13-008 | 187 | إنشاء أرشيف إلكتروني مركزي مرتبط بملف القضية، يدعم | سجل لجميع عمليات الإضافة والتعديل والحذف. | YES | T: ArchiveDeletionApiTest, AuditTrailTest (CI green cac8d73)<br>J: wp5 «every step is in the history» (UAT 2bcd2dc) [ARCH_AUDIT] | | — | — |
| LD-DOC-13-009 | 188 | إنشاء أرشيف إلكتروني مركزي مرتبط بملف القضية، يدعم | ربط المستند بالطلب أو الدفع أو الجلسة أو الحكم أو المهمة ذات الصلة. | YES | T: DocumentLinkApiTest (CI green cac8d73)<br>J: wp5 «a document is linked to the judgment it concerns; a record of another case cannot be linked» (UAT 2bcd2dc) [ARCH_LINK] | | — | — |

<sub>عناوين المجموعات في المصدر: إنشاء أرشيف إلكتروني مركزي مرتبط بملف القضية، يدعم: (س179)</sub>

## LD-DOC-14 — الرابع عشر: توزيع القضايا والمهام

- **المرحلة**: المرحلة الثانية · **الأولوية**: عالية · **حزمة العمل**: WP6 · **سيناريوهات القبول المرتبطة**: S11, S15
- **التحديث (2026-11-09)**: نُفِّذ بحزمة العمل؛ الدليل: DOC-WP6 (journey wp6: S15 مهمة منفردة).

| المعرّف | السطر | المجموعة | المتطلب (نص المصدر) | IMPLEMENTED | VERIFIED_BY_DEVELOPER | QA_ACCEPTED | سبب العائق | ملاحظة (ما تبقى / لماذا لم يُتحقق) |
|---|---|---|---|---|---|---|---|---|
| LD-DOC-14-001 | 191 | تطوير وحدة توزيع القضايا والمهام بحيث يمكن | إسناد القضية إلى عضو قانوني. | YES | T: TaskApiTest, TaskExtensionApiTest (CI green cac8d73)<br>J: wp6 «staff creates a task on the case with a start date and instr; it starts NEW with its instructions» (UAT 87bf0e6) [TASKS] | | — | — |
| LD-DOC-14-002 | 192 | تطوير وحدة توزيع القضايا والمهام بحيث يمكن | تحديد عضو بديل. | YES | T: TaskApiTest, TaskExtensionApiTest (CI green cac8d73)<br>J: wp6 «staff creates a task on the case with a start date and instr; it starts NEW with its instructions» (UAT 87bf0e6) [TASKS] | | — | — |
| LD-DOC-14-003 | 193 | تطوير وحدة توزيع القضايا والمهام بحيث يمكن | إسناد مهمة مستقلة لعضو آخر. | YES | T: TaskApiTest, TaskExtensionApiTest (CI green cac8d73)<br>J: wp6 «staff creates a task on the case with a start date and instr; it starts NEW with its instructions» (UAT 87bf0e6) [TASKS] | | — | — |
| LD-DOC-14-004 | 194 | تطوير وحدة توزيع القضايا والمهام بحيث يمكن | تحديد تاريخ البداية وموعد الإنجاز. | YES | T: TaskApiTest, TaskExtensionApiTest (CI green cac8d73)<br>J: wp6 «staff creates a task on the case with a start date and instr; it starts NEW with its instructions» (UAT 87bf0e6) [TASKS] | | — | — |
| LD-DOC-14-005 | 195 | تطوير وحدة توزيع القضايا والمهام بحيث يمكن | تحديد الأولوية والتعليمات. | YES | T: TaskApiTest, TaskExtensionApiTest (CI green cac8d73)<br>J: wp6 «staff creates a task on the case with a start date and instr; it starts NEW with its instructions» (UAT 87bf0e6) [TASKS] | | — | — |
| LD-DOC-14-006 | 196 | تطوير وحدة توزيع القضايا والمهام بحيث يمكن | متابعة الإنجاز. | YES | T: TaskApiTest, TaskExtensionApiTest (CI green cac8d73)<br>J: wp6 «staff creates a task on the case with a start date and instr; it starts NEW with its instructions» (UAT 87bf0e6) [TASKS] | | — | — |
| LD-DOC-14-007 | 197 | تطوير وحدة توزيع القضايا والمهام بحيث يمكن | إعادة إسناد المهمة. | YES | T: TaskExtensionApiTest#reassigningMovesTheTaskAndTheSightOfTheCaseAndKeepsTheHistory (CI green cac8d73)<br>J: wp6 «the task is reassigned with a reason; the reassignment keeps both people and the reason» (UAT 87bf0e6) [TASK_REASSIGN] | | — | — |
| LD-DOC-14-008 | 198 | تطوير وحدة توزيع القضايا والمهام بحيث يمكن | تسجيل أسباب التأخير. | YES | T: TaskApiTest, TaskExtensionApiTest (CI green cac8d73)<br>J: wp6 «staff creates a task on the case with a start date and instr; it starts NEW with its instructions» (UAT 87bf0e6) [TASKS] | | — | — |
| LD-DOC-14-009 | 199 | تطوير وحدة توزيع القضايا والمهام بحيث يمكن | وتكون حالات المهمة: جديدة، قيد التنفيذ، بانتظار مستندات، بانتظار جهة أخرى، مكتملة، متأخرة، ملغاة. | YES | T: TaskStatusAuditTest, TaskStatusMigrationApiTest (CI green cac8d73)<br>J: wp6 «the task moves to WAITING_DOCUMENTS; the task moves to WAITING_OTHER» (UAT 87bf0e6) [TASK_STATES] | | — | — |

<sub>عناوين المجموعات في المصدر: تطوير وحدة توزيع القضايا والمهام بحيث يمكن: (س190)</sub>

## LD-DOC-15 — الخامس عشر: التقويم القضائي الإلكتروني

- **المرحلة**: المرحلة الثانية · **الأولوية**: عالية · **حزمة العمل**: WP6 · **سيناريوهات القبول المرتبطة**: S11
- **التحديث (2026-11-09)**: نُفِّذ بحزمة العمل؛ الدليل: DOC-WP6 (journey wp6: S11).

| المعرّف | السطر | المجموعة | المتطلب (نص المصدر) | IMPLEMENTED | VERIFIED_BY_DEVELOPER | QA_ACCEPTED | سبب العائق | ملاحظة (ما تبقى / لماذا لم يُتحقق) |
|---|---|---|---|---|---|---|---|---|
| LD-DOC-15-001 | 201 | — | إنشاء تقويم قضائي في الصفحة الشخصية لكل عضو قانوني وفي وحدة المهام، بالإضافة إلى تقويم عام للإدارة القانونية. | YES | T: CalendarApiTest, CalendarEngineTest (CI green cac8d73)<br>F: Calendar.test.tsx (CI green cac8d73)<br>J: wp6 «the hearing and the task of the case are in it; the day view holds them» (UAT 87bf0e6) [CALENDAR] | | — | — |
| LD-DOC-15-002 | 203 | ويعرض | جلسات المحاكم. | YES | T: CalendarApiTest (CI green cac8d73)<br>J: wp6 «the kind filter keeps the task only; the manual dates are on the calendar» (UAT 87bf0e6) [CALENDAR_KINDS] | | — | — |
| LD-DOC-15-003 | 204 | ويعرض | جلسات الاستئناف والتمييز. | YES | T: CalendarApiTest (CI green cac8d73)<br>J: wp6 «the kind filter keeps the task only; the manual dates are on the calendar» (UAT 87bf0e6) [CALENDAR_KINDS] | | — | — |
| LD-DOC-15-004 | 205 | ويعرض | جلسات الخبراء. | YES | T: CalendarApiTest (CI green cac8d73)<br>J: wp6 «the kind filter keeps the task only; the manual dates are on the calendar» (UAT 87bf0e6) [CALENDAR_KINDS] | | — | — |
| LD-DOC-15-005 | 206 | ويعرض | مواعيد التحقيقات. | YES | T: CalendarApiTest (CI green cac8d73)<br>J: wp6 «the kind filter keeps the task only; the manual dates are on the calendar» (UAT 87bf0e6) [CALENDAR_KINDS] | | — | — |
| LD-DOC-15-006 | 207 | ويعرض | مواعيد تقديم المذكرات. | YES | T: CalendarApiTest (CI green cac8d73)<br>J: wp6 «the kind filter keeps the task only; the manual dates are on the calendar» (UAT 87bf0e6) [CALENDAR_KINDS] | | — | — |
| LD-DOC-15-007 | 208 | ويعرض | مواعيد الطعون والتظلمات. | YES | T: CalendarApiTest (CI green cac8d73)<br>J: wp6 «the kind filter keeps the task only; the manual dates are on the calendar» (UAT 87bf0e6) [CALENDAR_KINDS] | | — | — |
| LD-DOC-15-008 | 209 | ويعرض | مواعيد تنفيذ الأحكام. | YES | T: CalendarApiTest (CI green cac8d73)<br>J: wp6 «the kind filter keeps the task only; the manual dates are on the calendar» (UAT 87bf0e6) [CALENDAR_KINDS] | | — | — |
| LD-DOC-15-009 | 210 | ويعرض | المهام والاجتماعات. | YES | T: CalendarApiTest (CI green cac8d73)<br>J: wp6 «the kind filter keeps the task only; the manual dates are on the calendar» (UAT 87bf0e6) [CALENDAR_KINDS] | | — | — |
| LD-DOC-15-010 | 211 | ويعرض | ويدعم العرض الشهري والأسبوعي واليومي والقائمة الزمنية. | YES | T: CalendarApiTest, CalendarEngineTest (CI green cac8d73)<br>F: Calendar.test.tsx (CI green cac8d73)<br>J: wp6 «the hearing and the task of the case are in it; the day view holds them» (UAT 87bf0e6) [CALENDAR] | | — | — |
| LD-DOC-15-011 | 212 | ويعرض | عند إضافة جلسة أو مهمة، يجب إدراجها تلقائياً في تقويم العضو والتقويم العام. | YES | T: CalendarApiTest, CalendarEngineTest (CI green cac8d73)<br>F: Calendar.test.tsx (CI green cac8d73)<br>J: wp6 «the hearing and the task of the case are in it; the day view holds them» (UAT 87bf0e6) [CALENDAR] | | — | — |
| LD-DOC-15-012 | 213 | ويعرض | ويجب أن تظل الجلسات السابقة ظاهرة حتى بعد إغلاق القضية، مع التحقق من أي موعد مستقبلي لقضية مغلقة قبل إلغائه أو تجاهله. | YES | T: CalendarApiTest (CI green cac8d73)<br>J: wp3 «the hearings of the closed case remain» (UAT 3937a50) [CALENDAR_CLOSED] | | — | — |
| LD-DOC-15-013 | 214 | ويعرض | كما يجب اكتشاف تعارض المواعيد والتنبيه إليه، وإتاحة تعيين عضو بديل. | YES | T: CalendarEngineTest, CalendarApiTest (CI green cac8d73)<br>F: Calendar.test.tsx (CI green cac8d73)<br>J: wp6 «the calendar detects the conflict; it offers the alternate member of each case» (UAT 87bf0e6) [CALENDAR_CONFLICT] | | — | — |

<sub>عناوين المجموعات في المصدر: ويعرض: (س202)</sub>

## LD-DOC-16 — السادس عشر: مكتبة الأحكام والسوابق القضائية

- **المرحلة**: المرحلة الأولى · **الأولوية**: الأعلى · **حزمة العمل**: WP7 · **سيناريوهات القبول المرتبطة**: S4, S5
- **التحديث (2026-11-09)**: نُفِّذ بحزمة العمل؛ الدليل: DOC-WP7 (journey wp7: S4, S5).

| المعرّف | السطر | المجموعة | المتطلب (نص المصدر) | IMPLEMENTED | VERIFIED_BY_DEVELOPER | QA_ACCEPTED | سبب العائق | ملاحظة (ما تبقى / لماذا لم يُتحقق) |
|---|---|---|---|---|---|---|---|---|
| LD-DOC-16-001 | 217 | — | إنشاء مكتبة قانونية مركزية مستقلة باسم «مكتبة الأحكام والسوابق القضائية»، تكون مرجعاً لجميع أعضاء الإدارة القانونية، ومرتبطة مباشرة بملفات القضايا. | YES | T: LibraryJudgmentApiTest, LibraryPrincipleApiTest (CI green cac8d73)<br>J: wp7 «the specialist registers the judgment; it records who entered it» (UAT 60f5368) [LIBRARY] | | — | — |
| LD-DOC-16-002 | 219 | تسجيل الأحكام في المكتبة | يجب أن تتيح المكتبة حفظ الأحكام القضائية السابقة والحالية، سواء كانت صادرة في قضايا الجهة أو أحكاماً قضائية أخرى يتم إدخالها للاستفادة منها. | YES | T: LibraryJudgmentApiTest, LibraryPrincipleApiTest (CI green cac8d73)<br>J: wp7 «the specialist registers the judgment; it records who entered it» (UAT 60f5368) [LIBRARY] | | — | — |
| LD-DOC-16-003 | 221 | وتشمل بيانات كل حكم | رقم القضية. | YES | T: LibraryJudgmentApiTest, LibraryPrincipleApiTest (CI green cac8d73)<br>J: wp7 «the specialist registers the judgment; it records who entered it» (UAT 60f5368) [LIBRARY] | | — | — |
| LD-DOC-16-004 | 222 | وتشمل بيانات كل حكم | المحكمة. | YES | T: LibraryJudgmentApiTest, LibraryPrincipleApiTest (CI green cac8d73)<br>J: wp7 «the specialist registers the judgment; it records who entered it» (UAT 60f5368) [LIBRARY] | | — | — |
| LD-DOC-16-005 | 223 | وتشمل بيانات كل حكم | الدائرة. | YES | T: LibraryJudgmentApiTest, LibraryPrincipleApiTest (CI green cac8d73)<br>J: wp7 «the specialist registers the judgment; it records who entered it» (UAT 60f5368) [LIBRARY] | | — | — |
| LD-DOC-16-006 | 224 | وتشمل بيانات كل حكم | درجة التقاضي. | YES | T: LibraryJudgmentApiTest, LibraryPrincipleApiTest (CI green cac8d73)<br>J: wp7 «the specialist registers the judgment; it records who entered it» (UAT 60f5368) [LIBRARY] | | — | — |
| LD-DOC-16-007 | 225 | وتشمل بيانات كل حكم | تاريخ الحكم. | YES | T: LibraryJudgmentApiTest, LibraryPrincipleApiTest (CI green cac8d73)<br>J: wp7 «the specialist registers the judgment; it records who entered it» (UAT 60f5368) [LIBRARY] | | — | — |
| LD-DOC-16-008 | 226 | وتشمل بيانات كل حكم | نوع الدعوى. | YES | T: LibraryJudgmentApiTest, LibraryPrincipleApiTest (CI green cac8d73)<br>J: wp7 «the specialist registers the judgment; it records who entered it» (UAT 60f5368) [LIBRARY] | | — | — |
| LD-DOC-16-009 | 227 | وتشمل بيانات كل حكم | موضوع النزاع. | YES | T: LibraryJudgmentApiTest, LibraryPrincipleApiTest (CI green cac8d73)<br>J: wp7 «the specialist registers the judgment; it records who entered it» (UAT 60f5368) [LIBRARY] | | — | — |
| LD-DOC-16-010 | 228 | وتشمل بيانات كل حكم | أسماء أو صفات الخصوم بحسب ضوابط السرية. | YES | T: LibraryJudgmentApiTest, LibraryPrincipleApiTest (CI green cac8d73)<br>J: wp7 «the specialist registers the judgment; it records who entered it» (UAT 60f5368) [LIBRARY] | | — | — |
| LD-DOC-16-011 | 229 | وتشمل بيانات كل حكم | منطوق الحكم. | YES | T: LibraryJudgmentApiTest, LibraryPrincipleApiTest (CI green cac8d73)<br>J: wp7 «the specialist registers the judgment; it records who entered it» (UAT 60f5368) [LIBRARY] | | — | — |
| LD-DOC-16-012 | 230 | وتشمل بيانات كل حكم | الأسباب والمبادئ القانونية التي استند إليها. | YES | T: LibraryJudgmentApiTest, LibraryPrincipleApiTest (CI green cac8d73)<br>J: wp7 «the specialist registers the judgment; it records who entered it» (UAT 60f5368) [LIBRARY] | | — | — |
| LD-DOC-16-013 | 231 | وتشمل بيانات كل حكم | النصوص القانونية ذات الصلة. | YES | T: LibraryJudgmentApiTest, LibraryPrincipleApiTest (CI green cac8d73)<br>J: wp7 «the specialist registers the judgment; it records who entered it» (UAT 60f5368) [LIBRARY] | | — | — |
| LD-DOC-16-014 | 232 | وتشمل بيانات كل حكم | الطلبات التي تناولها الحكم. | YES | T: LibraryJudgmentApiTest, LibraryPrincipleApiTest (CI green cac8d73)<br>J: wp7 «the specialist registers the judgment; it records who entered it» (UAT 60f5368) [LIBRARY] | | — | — |
| LD-DOC-16-015 | 233 | وتشمل بيانات كل حكم | نتيجة كل طلب. | YES | T: LibraryJudgmentApiTest, LibraryPrincipleApiTest (CI green cac8d73)<br>J: wp7 «the specialist registers the judgment; it records who entered it» (UAT 60f5368) [LIBRARY] | | — | — |
| LD-DOC-16-016 | 234 | وتشمل بيانات كل حكم | اتجاه الحكم: لصالح الجهة أو ضدها أو نتيجة أخرى. | YES | T: LibraryJudgmentApiTest, LibraryPrincipleApiTest (CI green cac8d73)<br>J: wp7 «the specialist registers the judgment; it records who entered it» (UAT 60f5368) [LIBRARY] | | — | — |
| LD-DOC-16-017 | 235 | وتشمل بيانات كل حكم | الكلمات المفتاحية. | YES | T: LibraryJudgmentApiTest, LibraryPrincipleApiTest (CI green cac8d73)<br>J: wp7 «the specialist registers the judgment; it records who entered it» (UAT 60f5368) [LIBRARY] | | — | — |
| LD-DOC-16-018 | 236 | وتشمل بيانات كل حكم | تصنيف الحكم. | YES | T: LibraryJudgmentApiTest, LibraryPrincipleApiTest (CI green cac8d73)<br>J: wp7 «the specialist registers the judgment; it records who entered it» (UAT 60f5368) [LIBRARY] | | — | — |
| LD-DOC-16-019 | 237 | وتشمل بيانات كل حكم | نسخة الحكم الإلكترونية. | YES | T: LibraryCopyApiTest (CI green cac8d73) [LIB_COPY] | | — | — |
| LD-DOC-16-020 | 238 | وتشمل بيانات كل حكم | مصدر الحكم وبيانات التحقق منه. | YES | T: LibraryJudgmentApiTest (CI green cac8d73)<br>J: wp7 «the source is mandatory; the citation shows source, date and number» (UAT 60f5368) [LIB_SOURCE] | | — | — |
| LD-DOC-16-021 | 239 | وتشمل بيانات كل حكم | العضو الذي قام بإدخاله وتاريخ الإدخال. | YES | T: LibraryJudgmentApiTest, LibraryPrincipleApiTest (CI green cac8d73)<br>J: wp7 «the specialist registers the judgment; it records who entered it» (UAT 60f5368) [LIBRARY] | | — | — |
| LD-DOC-16-022 | 241 | استخراج المبادئ القضائية | يجب إتاحة تسجيل واستخلاص المبدأ القانوني من كل حكم بصورة مستقلة، بحيث يمكن حفظ أكثر من مبدأ للحكم الواحد. | YES | T: LibraryPrincipleApiTest (CI green cac8d73)<br>J: wp7 «the specialist extracts a principle apart from the judgment; the manager reviews the principle» (UAT 60f5368) [LIB_PRINCIPLE] | | — | — |
| LD-DOC-16-023 | 243 | ويشمل كل مبدأ | نص المبدأ القانوني. | YES | T: LibraryPrincipleApiTest (CI green cac8d73)<br>J: wp7 «the specialist extracts a principle apart from the judgment; the manager reviews the principle» (UAT 60f5368) [LIB_PRINCIPLE] | | — | — |
| LD-DOC-16-024 | 244 | ويشمل كل مبدأ | الموضوع القانوني الذي يتعلق به. | YES | T: LibraryPrincipleApiTest (CI green cac8d73)<br>J: wp7 «the specialist extracts a principle apart from the judgment; the manager reviews the principle» (UAT 60f5368) [LIB_PRINCIPLE] | | — | — |
| LD-DOC-16-025 | 245 | ويشمل كل مبدأ | المحكمة التي قررته. | YES | T: LibraryPrincipleApiTest (CI green cac8d73)<br>J: wp7 «the specialist extracts a principle apart from the judgment; the manager reviews the principle» (UAT 60f5368) [LIB_PRINCIPLE] | | — | — |
| LD-DOC-16-026 | 246 | ويشمل كل مبدأ | تاريخ الحكم ورقم القضية. | YES | T: LibraryPrincipleApiTest (CI green cac8d73)<br>J: wp7 «the specialist extracts a principle apart from the judgment; the manager reviews the principle» (UAT 60f5368) [LIB_PRINCIPLE] | | — | — |
| LD-DOC-16-027 | 247 | ويشمل كل مبدأ | النصوص القانونية المرتبطة. | YES | T: LibraryPrincipleApiTest (CI green cac8d73)<br>J: wp7 «the specialist extracts a principle apart from the judgment; the manager reviews the principle» (UAT 60f5368) [LIB_PRINCIPLE] | | — | — |
| LD-DOC-16-028 | 248 | ويشمل كل مبدأ | الوقائع التي طبق عليها المبدأ. | YES | T: LibraryPrincipleApiTest (CI green cac8d73)<br>J: wp7 «the specialist extracts a principle apart from the judgment; the manager reviews the principle» (UAT 60f5368) [LIB_PRINCIPLE] | | — | — |
| LD-DOC-16-029 | 249 | ويشمل كل مبدأ | نطاق تطبيق المبدأ. | YES | T: LibraryPrincipleApiTest (CI green cac8d73)<br>J: wp7 «the specialist extracts a principle apart from the judgment; the manager reviews the principle» (UAT 60f5368) [LIB_PRINCIPLE] | | — | — |
| LD-DOC-16-030 | 250 | ويشمل كل مبدأ | ملاحظات العضو القانوني. | YES | T: LibraryPrincipleApiTest (CI green cac8d73)<br>J: wp7 «the specialist extracts a principle apart from the judgment; the manager reviews the principle» (UAT 60f5368) [LIB_PRINCIPLE] | | — | — |
| LD-DOC-16-031 | 251 | ويشمل كل مبدأ | ويجب التمييز بين نص الحكم الأصلي وبين المبدأ المستخلص منه، وعدم اعتبار الصياغة المختصرة للمبدأ بديلاً عن الرجوع إلى الحكم. | YES | T: LibraryPrincipleApiTest (CI green cac8d73)<br>J: wp7 «the specialist extracts a principle apart from the judgment; the manager reviews the principle» (UAT 60f5368) [LIB_PRINCIPLE] | | — | — |
| LD-DOC-16-032 | 254 | تصنيف الأحكام حسب | المحكمة والدائرة. | YES | T: LibraryClassificationApiTest (CI green cac8d73)<br>J: wp7 «the specialist proposes a legal topic; filtering by the classification keeps it» (UAT 60f5368) [LIB_CLASS] | | — | — |
| LD-DOC-16-033 | 255 | تصنيف الأحكام حسب | نوع الدعوى. | YES | T: LibraryClassificationApiTest (CI green cac8d73)<br>J: wp7 «the specialist proposes a legal topic; filtering by the classification keeps it» (UAT 60f5368) [LIB_CLASS] | | — | — |
| LD-DOC-16-034 | 256 | تصنيف الأحكام حسب | الموضوع القانوني. | YES | T: LibraryClassificationApiTest (CI green cac8d73)<br>J: wp7 «the specialist proposes a legal topic; filtering by the classification keeps it» (UAT 60f5368) [LIB_CLASS] | | — | — |
| LD-DOC-16-035 | 257 | تصنيف الأحكام حسب | النص القانوني. | YES | T: LibraryClassificationApiTest (CI green cac8d73)<br>J: wp7 «the specialist proposes a legal topic; filtering by the classification keeps it» (UAT 60f5368) [LIB_CLASS] | | — | — |
| LD-DOC-16-036 | 258 | تصنيف الأحكام حسب | نوع الطلب. | YES | T: LibraryClassificationApiTest (CI green cac8d73)<br>J: wp7 «the specialist proposes a legal topic; filtering by the classification keeps it» (UAT 60f5368) [LIB_CLASS] | | — | — |
| LD-DOC-16-037 | 259 | تصنيف الأحكام حسب | نوع الدفع. | YES | T: LibraryClassificationApiTest (CI green cac8d73)<br>J: wp7 «the specialist proposes a legal topic; filtering by the classification keeps it» (UAT 60f5368) [LIB_CLASS] | | — | — |
| LD-DOC-16-038 | 260 | تصنيف الأحكام حسب | النتيجة القضائية. | YES | T: LibraryClassificationApiTest (CI green cac8d73)<br>J: wp7 «the specialist proposes a legal topic; filtering by the classification keeps it» (UAT 60f5368) [LIB_CLASS] | | — | — |
| LD-DOC-16-039 | 261 | تصنيف الأحكام حسب | السنة. | YES | T: LibraryClassificationApiTest (CI green cac8d73)<br>J: wp7 «the specialist proposes a legal topic; filtering by the classification keeps it» (UAT 60f5368) [LIB_CLASS] | | — | — |
| LD-DOC-16-040 | 262 | تصنيف الأحكام حسب | الجهة أو القطاع المعني. | YES | T: LibraryClassificationApiTest (CI green cac8d73)<br>J: wp7 «the specialist proposes a legal topic; filtering by the classification keeps it» (UAT 60f5368) [LIB_CLASS] | | — | — |
| LD-DOC-16-041 | 263 | تصنيف الأحكام حسب | مع توفير بحث متقدم في نص الحكم وأسبابه ومبادئه والكلمات المفتاحية. | YES | T: LibrarySearchQualityTest, LibraryJudgmentApiTest (CI green cac8d73)<br>J: wp7 «a reader finds the judgment by the token; a reader finds the judgment by words found only in the princ» (UAT 60f5368) [LIB_SEARCH] | | — | — |
| LD-DOC-16-042 | 265 | ربط الأحكام بالقضايا | يجب إتاحة ربط الحكم بأكثر من قضية، وربط القضية بأكثر من حكم سابق، مع بيان سبب الاستفادة من الحكم ومدى التشابه بين الوقائع والمسائل القانونية. | YES | T: CasePrecedentApiTest (CI green cac8d73)<br>J: wp7 «the specialist decides to link the judgment, giving the reas; the specialist sees the judgment and the case it is linked t» (UAT 60f5368) [LIB_LINK] | | — | — |
| LD-DOC-16-043 | 267 | اقتراح السوابق المشابهة | عند تسجيل قضية جديدة أو إعداد مذكرة دفاع، يقترح النظام أحكاماً وسوابق مشابهة بناءً على موضوع الدعوى والوقائع والطلبات والدفوع والنصوص القانونية. | PARTIAL |  | | UI_NOT_BUILT | API suggests similar judgments from claims and defences (GapClosureApiTest.similarJudgmentsAreFoundFromADefenceAloneAndFromTheClaimsAndDefencesOfTheCase). The screen for choosing a defence while drafting a memo is not built. Dependency: none (engineering). |
| LD-DOC-16-044 | 268 | اقتراح السوابق المشابهة | ويجب أن يعرض النظام أوجه التشابه والاختلاف، مع إظهار مصدر كل حكم وتاريخه ورقمه ونسخته المحفوظة. | YES | T: SimilarJudgmentApiTest (CI green cac8d73)<br>J: wp7 «the specialist is offered similar judgments; each suggestion explains itself with words and points» (UAT 60f5368) [LIB_SIMILAR] | | — | — |
| LD-DOC-16-045 | 271 | يجب أن تتضمن المكتبة حقولاً توضح ما إذا كان الحكم | نهائياً أو غير نهائي. | YES | T: LibraryRelationApiTest (CI green cac8d73)<br>J: wp7 «a later event is recorded with its source; the library now warns that a later event exists» (UAT 60f5368) [LIB_STATUS] | | — | — |
| LD-DOC-16-046 | 272 | يجب أن تتضمن المكتبة حقولاً توضح ما إذا كان الحكم | تم الطعن عليه. | YES | T: LibraryRelationApiTest (CI green cac8d73)<br>J: wp7 «a later event is recorded with its source; the library now warns that a later event exists» (UAT 60f5368) [LIB_STATUS] | | — | — |
| LD-DOC-16-047 | 273 | يجب أن تتضمن المكتبة حقولاً توضح ما إذا كان الحكم | تم تأييده أو تعديله أو إلغاؤه. | YES | T: LibraryRelationApiTest (CI green cac8d73)<br>J: wp7 «a later event is recorded with its source; the library now warns that a later event exists» (UAT 60f5368) [LIB_STATUS] | | — | — |
| LD-DOC-16-048 | 274 | يجب أن تتضمن المكتبة حقولاً توضح ما إذا كان الحكم | صدر بشأنه حكم لاحق. | YES | T: LibraryRelationApiTest (CI green cac8d73)<br>J: wp7 «a later event is recorded with its source; the library now warns that a later event exists» (UAT 60f5368) [LIB_STATUS] | | — | — |
| LD-DOC-16-049 | 275 | يجب أن تتضمن المكتبة حقولاً توضح ما إذا كان الحكم | تم العدول عن المبدأ أو تغييره وفق مصدر موثوق، إن ثبت ذلك. | YES | T: LibraryRelationApiTest (CI green cac8d73)<br>J: wp7 «a later event is recorded with its source; the library now warns that a later event exists» (UAT 60f5368) [LIB_STATUS] | | — | — |
| LD-DOC-16-050 | 276 | يجب أن تتضمن المكتبة حقولاً توضح ما إذا كان الحكم | ولا يجوز وصف حكم بأنه مبدأ مستقر أو اتجاه قضائي ثابت لمجرد وجود حكم واحد، بل يجب إظهار عدد الأحكام ومصادرها وتواريخها، وترك تقييم مدى استقرار الاتجاه للعضو القانوني. | YES | T: LibraryRelationApiTest (CI green cac8d73)<br>J: wp7 «a later event is recorded with its source; the library now warns that a later event exists» (UAT 60f5368) [LIB_STATUS] | | — | — |
| LD-DOC-16-051 | 278 | التقارير | إتاحة تقارير عن الأحكام حسب المحكمة والموضوع والنتيجة والنص القانوني والسنوات، مع بيان أكثر المسائل القانونية تكراراً. | YES | T: LibraryReportApiTest (CI green cac8d73)<br>J: wp7 «a reader sees the library report; the report counts the judgment under its classification» (UAT 60f5368) [LIB_REPORT] | | — | — |

<sub>عناوين المجموعات في المصدر: المرحلة الأولى – أولوية مرتفعة جداً (س216)؛ 1. تسجيل الأحكام في المكتبة (س218)؛ وتشمل بيانات كل حكم: (س220)؛ 2. استخراج المبادئ القضائية (س240)؛ ويشمل كل مبدأ: (س242)؛ 3. التصنيف والبحث (س252)؛ تصنيف الأحكام حسب: (س253)؛ 4. ربط الأحكام بالقضايا (س264)؛ 5. اقتراح السوابق المشابهة (س266)؛ 6. التحقق من حجية الحكم وحداثته (س269)؛ يجب أن تتضمن المكتبة حقولاً توضح ما إذا كان الحكم: (س270)؛ 7. التقارير (س277)</sub>

## LD-DOC-17 — السابع عشر: إدارة الدفوع وأوجه الدفاع

- **المرحلة**: المرحلة الأولى · **الأولوية**: الأعلى · **حزمة العمل**: WP2 · **سيناريوهات القبول المرتبطة**: S3, S8
- **التحديث (2026-11-09)**: نُفِّذ بحزمة العمل؛ الدليل: DOC-WP2 (journey wp2: S3).

| المعرّف | السطر | المجموعة | المتطلب (نص المصدر) | IMPLEMENTED | VERIFIED_BY_DEVELOPER | QA_ACCEPTED | سبب العائق | ملاحظة (ما تبقى / لماذا لم يُتحقق) |
|---|---|---|---|---|---|---|---|---|
| LD-DOC-17-001 | 281 | — | إنشاء وحدة مستقلة داخل كل قضية باسم «الدفوع وأوجه الدفاع»، بحيث يتم تسجيل جميع الدفوع القانونية والموضوعية التي تتمسك بها الجهة في كل مرحلة من مراحل التقاضي. | YES | T: DefenseApiTest (CI green cac8d73)<br>J: wp2 «the specialist records a defence linked to a memo; a second defence gets the next number» (UAT 4220ccf) [DEFENSES] | | — | — |
| LD-DOC-17-002 | 284 | يجب أن يتضمن كل دفع | رقم الدفع. | YES | T: DefenseApiTest (CI green cac8d73)<br>J: wp2 «the specialist records a defence linked to a memo; a second defence gets the next number» (UAT 4220ccf) [DEFENSES] | | — | — |
| LD-DOC-17-003 | 285 | يجب أن يتضمن كل دفع | عنوان الدفع. | YES | T: DefenseApiTest (CI green cac8d73)<br>J: wp2 «the specialist records a defence linked to a memo; a second defence gets the next number» (UAT 4220ccf) [DEFENSES] | | — | — |
| LD-DOC-17-004 | 286 | يجب أن يتضمن كل دفع | نوع الدفع: شكلي، إجرائي، موضوعي، أو غير ذلك. | YES | T: DefenseApiTest (CI green cac8d73)<br>J: wp2 «the specialist records a defence linked to a memo; a second defence gets the next number» (UAT 4220ccf) [DEFENSES] | | — | — |
| LD-DOC-17-005 | 287 | يجب أن يتضمن كل دفع | شرح الدفع وأسانيده القانونية. | YES | T: DefenseApiTest (CI green cac8d73)<br>J: wp2 «the specialist records a defence linked to a memo; a second defence gets the next number» (UAT 4220ccf) [DEFENSES] | | — | — |
| LD-DOC-17-006 | 288 | يجب أن يتضمن كل دفع | الوقائع المرتبطة به. | YES | T: DefenseApiTest (CI green cac8d73)<br>J: wp2 «the specialist records a defence linked to a memo; a second defence gets the next number» (UAT 4220ccf) [DEFENSES] | | — | — |
| LD-DOC-17-007 | 289 | يجب أن يتضمن كل دفع | النصوص القانونية المستند إليها. | YES | T: DefenseApiTest (CI green cac8d73)<br>J: wp2 «the specialist records a defence linked to a memo; a second defence gets the next number» (UAT 4220ccf) [DEFENSES] | | — | — |
| LD-DOC-17-008 | 290 | يجب أن يتضمن كل دفع | الأحكام والسوابق القضائية المؤيدة. | YES | T: GapClosureApiTest#aDefenceKeepsItsSupportingPrecedentsAndTheirHistory (CI green cac8d73) [DEFENSE_PRECEDENT] | | — | — |
| LD-DOC-17-009 | 291 | يجب أن يتضمن كل دفع | المستندات الداعمة. | YES | T: DefenseApiTest (CI green cac8d73)<br>J: wp2 «the specialist records a defence linked to a memo; a second defence gets the next number» (UAT 4220ccf) [DEFENSES] | | — | — |
| LD-DOC-17-010 | 292 | يجب أن يتضمن كل دفع | المذكرة التي ورد فيها. | YES | T: DefenseApiTest (CI green cac8d73)<br>J: wp2 «the specialist records a defence linked to a memo; a second defence gets the next number» (UAT 4220ccf) [DEFENSES] | | — | — |
| LD-DOC-17-011 | 293 | يجب أن يتضمن كل دفع | تاريخ إبدائه. | YES | T: DefenseApiTest (CI green cac8d73)<br>J: wp2 «the specialist records a defence linked to a memo; a second defence gets the next number» (UAT 4220ccf) [DEFENSES] | | — | — |
| LD-DOC-17-012 | 294 | يجب أن يتضمن كل دفع | درجة التقاضي التي أثير فيها. | YES | T: DefenseApiTest (CI green cac8d73)<br>J: wp2 «the specialist records a defence linked to a memo; a second defence gets the next number» (UAT 4220ccf) [DEFENSES] | | — | — |
| LD-DOC-17-013 | 295 | يجب أن يتضمن كل دفع | العضو القانوني الذي أعده. | YES | T: DefenseApiTest (CI green cac8d73)<br>J: wp2 «the specialist records a defence linked to a memo; a second defence gets the next number» (UAT 4220ccf) [DEFENSES] | | — | — |
| LD-DOC-17-014 | 296 | يجب أن يتضمن كل دفع | ملاحظات إضافية. | YES | T: DefenseApiTest (CI green cac8d73)<br>J: wp2 «the specialist records a defence linked to a memo; a second defence gets the next number» (UAT 4220ccf) [DEFENSES] | | — | — |
| LD-DOC-17-015 | 299 | عند تسجيل الحكم، يجب أن تظهر الدفوع التي سبق إثارتها، ويتيح النظام تسجيل موقف المحكمة من كل دفع بصورة مستقلة | قبول الدفع. | YES | T: DefenseApiTest, JudgmentDetailsApiTest (CI green cac8d73)<br>J: wp2 «the court's position is recorded on each defence; the defence is re-asserted in the appeal» (UAT 4220ccf) [DEFENSE_POSITION] | | — | — |
| LD-DOC-17-016 | 300 | عند تسجيل الحكم، يجب أن تظهر الدفوع التي سبق إثارتها، ويتيح النظام تسجيل موقف المحكمة من كل دفع بصورة مستقلة | رفض الدفع. | YES | T: DefenseApiTest, JudgmentDetailsApiTest (CI green cac8d73)<br>J: wp2 «the court's position is recorded on each defence; the defence is re-asserted in the appeal» (UAT 4220ccf) [DEFENSE_POSITION] | | — | — |
| LD-DOC-17-017 | 301 | عند تسجيل الحكم، يجب أن تظهر الدفوع التي سبق إثارتها، ويتيح النظام تسجيل موقف المحكمة من كل دفع بصورة مستقلة | قبول الدفع جزئياً. | YES | T: DefenseApiTest, JudgmentDetailsApiTest (CI green cac8d73)<br>J: wp2 «the court's position is recorded on each defence; the defence is re-asserted in the appeal» (UAT 4220ccf) [DEFENSE_POSITION] | | — | — |
| LD-DOC-17-018 | 302 | عند تسجيل الحكم، يجب أن تظهر الدفوع التي سبق إثارتها، ويتيح النظام تسجيل موقف المحكمة من كل دفع بصورة مستقلة | عدم التعرض للدفع. | YES | T: DefenseApiTest, JudgmentDetailsApiTest (CI green cac8d73)<br>J: wp2 «the court's position is recorded on each defence; the defence is re-asserted in the appeal» (UAT 4220ccf) [DEFENSE_POSITION] | | — | — |
| LD-DOC-17-019 | 303 | عند تسجيل الحكم، يجب أن تظهر الدفوع التي سبق إثارتها، ويتيح النظام تسجيل موقف المحكمة من كل دفع بصورة مستقلة | عدم الحاجة إلى الفصل فيه. | YES | T: DefenseApiTest, JudgmentDetailsApiTest (CI green cac8d73)<br>J: wp2 «the court's position is recorded on each defence; the defence is re-asserted in the appeal» (UAT 4220ccf) [DEFENSE_POSITION] | | — | — |
| LD-DOC-17-020 | 304 | عند تسجيل الحكم، يجب أن تظهر الدفوع التي سبق إثارتها، ويتيح النظام تسجيل موقف المحكمة من كل دفع بصورة مستقلة | تأجيل الفصل فيه. | YES | T: DefenseApiTest, JudgmentDetailsApiTest (CI green cac8d73)<br>J: wp2 «the court's position is recorded on each defence; the defence is re-asserted in the appeal» (UAT 4220ccf) [DEFENSE_POSITION] | | — | — |
| LD-DOC-17-021 | 305 | عند تسجيل الحكم، يجب أن تظهر الدفوع التي سبق إثارتها، ويتيح النظام تسجيل موقف المحكمة من كل دفع بصورة مستقلة | نتيجة أخرى. | YES | T: DefenseApiTest, JudgmentDetailsApiTest (CI green cac8d73)<br>J: wp2 «the court's position is recorded on each defence; the defence is re-asserted in the appeal» (UAT 4220ccf) [DEFENSE_POSITION] | | — | — |
| LD-DOC-17-022 | 306 | عند تسجيل الحكم، يجب أن تظهر الدفوع التي سبق إثارتها، ويتيح النظام تسجيل موقف المحكمة من كل دفع بصورة مستقلة | مع تسجيل أسباب المحكمة ومنطوق الحكم المتعلق بالدفع. | YES | T: DefenseApiTest, JudgmentDetailsApiTest (CI green cac8d73)<br>J: wp2 «the court's position is recorded on each defence; the defence is re-asserted in the appeal» (UAT 4220ccf) [DEFENSE_POSITION] | | — | — |
| LD-DOC-17-023 | 309 | يجب الاحتفاظ بتاريخ كل دفع وموقف المحكمة منه في كل درجة تقاضٍ، مع بيان | الدفوع التي أثيرت أمام المحكمة الكلية. | YES | T: DefenseApiTest, JudgmentDetailsApiTest (CI green cac8d73)<br>J: wp2 «the court's position is recorded on each defence; the defence is re-asserted in the appeal» (UAT 4220ccf) [DEFENSE_POSITION] | | — | — |
| LD-DOC-17-024 | 310 | يجب الاحتفاظ بتاريخ كل دفع وموقف المحكمة منه في كل درجة تقاضٍ، مع بيان | الدفوع التي تمسك بها العضو أمام الاستئناف. | YES | T: DefenseApiTest, JudgmentDetailsApiTest (CI green cac8d73)<br>J: wp2 «the court's position is recorded on each defence; the defence is re-asserted in the appeal» (UAT 4220ccf) [DEFENSE_POSITION] | | — | — |
| LD-DOC-17-025 | 311 | يجب الاحتفاظ بتاريخ كل دفع وموقف المحكمة منه في كل درجة تقاضٍ، مع بيان | الدفوع التي أعيد التمسك بها أمام التمييز. | YES | T: DefenseApiTest, JudgmentDetailsApiTest (CI green cac8d73)<br>J: wp2 «the court's position is recorded on each defence; the defence is re-asserted in the appeal» (UAT 4220ccf) [DEFENSE_POSITION] | | — | — |
| LD-DOC-17-026 | 312 | يجب الاحتفاظ بتاريخ كل دفع وموقف المحكمة منه في كل درجة تقاضٍ، مع بيان | الدفوع التي لم يتم التمسك بها في مرحلة لاحقة. | YES | T: DefenseApiTest, JudgmentDetailsApiTest (CI green cac8d73)<br>J: wp2 «the court's position is recorded on each defence; the defence is re-asserted in the appeal» (UAT 4220ccf) [DEFENSE_POSITION] | | — | — |
| LD-DOC-17-027 | 313 | يجب الاحتفاظ بتاريخ كل دفع وموقف المحكمة منه في كل درجة تقاضٍ، مع بيان | الدفوع التي استجابت لها المحكمة والدفوع التي رفضتها. | YES | T: DefenseApiTest, JudgmentDetailsApiTest (CI green cac8d73)<br>J: wp2 «the court's position is recorded on each defence; the defence is re-asserted in the appeal» (UAT 4220ccf) [DEFENSE_POSITION] | | — | — |
| LD-DOC-17-028 | 315 | مكتبة الدفوع القانونية | إتاحة حفظ الدفوع المتكررة في مكتبة داخلية قابلة للبحث، بحيث يستطيع العضو الاستفادة من صياغات سابقة وأسانيد قانونية وأحكام مؤيدة، مع إمكانية تعديلها بحسب ظروف كل قضية. | YES | T: DefenseApiTest (CI green cac8d73) [DEFENSE_LIBRARY] | | — | — |
| LD-DOC-17-029 | 316 | مكتبة الدفوع القانونية | ويجب ألا يتم نسخ أي دفع آلياً إلى قضية جديدة دون مراجعته وملاءمته لوقائعها. | YES | T: DefenseApiTest (CI green cac8d73) [DEFENSE_LIBRARY] | | — | — |
| LD-DOC-17-030 | 318 | التقارير | توفير تقارير تبين أكثر الدفوع استخداماً، والدفوع التي استجابت لها المحاكم، والدفوع التي لم تستجب لها، حسب نوع القضية ودرجة التقاضي. | YES | T: StatisticsApiTest#countsDefencePositionsAndTheMostUsedDefences (CI green cac8d73) [DEFENSE_STATS] | | — | — |

<sub>عناوين المجموعات في المصدر: المرحلة الأولى – أولوية مرتفعة جداً (س280)؛ 1. تسجيل الدفوع (س282)؛ يجب أن يتضمن كل دفع: (س283)؛ 2. متابعة موقف المحكمة من كل دفع (س297)؛ عند تسجيل الحكم، يجب أن تظهر الدفوع التي سبق إثارتها، ويتيح النظام تسجيل موقف المحكمة من كل دفع بصورة مستقلة: (س298)؛ 3. متابعة الدفوع بين درجات التقاضي (س307)؛ يجب الاحتفاظ بتاريخ كل دفع وموقف المحكمة منه في كل درجة تقاضٍ، مع بيان: (س308)؛ 4. مكتبة الدفوع القانونية (س314)؛ 5. التقارير (س317)</sub>

## LD-DOC-18 — الثامن عشر: متابعة طلبات الإدارات المعنية بالقضايا

- **المرحلة**: المرحلة الأولى · **الأولوية**: الأعلى · **حزمة العمل**: WP3 · **سيناريوهات القبول المرتبطة**: S6
- **التحديث (2026-11-09)**: نُفِّذ بحزمة العمل؛ الدليل: DOC-WP3 (journey: S6).

| المعرّف | السطر | المجموعة | المتطلب (نص المصدر) | IMPLEMENTED | VERIFIED_BY_DEVELOPER | QA_ACCEPTED | سبب العائق | ملاحظة (ما تبقى / لماذا لم يُتحقق) |
|---|---|---|---|---|---|---|---|---|
| LD-DOC-18-001 | 321 | — | إنشاء وحدة مستقلة باسم «طلبات الإفادة والمستندات من الإدارات المعنية»، تتيح للإدارة القانونية متابعة جميع الطلبات الموجهة إلى قطاعات وإدارات وأقسام الجهة بشأن القضايا. | YES | T: InfoRequestApiTest (CI green cac8d73)<br>J: wp3 «staff registers a request to the unit as a draft; the system generated the number» (UAT 3937a50) [INFOREQ] | | — | — |
| LD-DOC-18-002 | 324 | يتضمن كل طلب | رقم الطلب الداخلي. | YES | T: InfoRequestApiTest (CI green cac8d73)<br>J: wp3 «staff registers a request to the unit as a draft; the system generated the number» (UAT 3937a50) [INFOREQ] | | — | — |
| LD-DOC-18-003 | 325 | يتضمن كل طلب | القضية المرتبط بها. | YES | T: InfoRequestApiTest (CI green cac8d73)<br>J: wp3 «staff registers a request to the unit as a draft; the system generated the number» (UAT 3937a50) [INFOREQ] | | — | — |
| LD-DOC-18-004 | 326 | يتضمن كل طلب | القطاع والإدارة والقسم المطلوب منه الرد. | YES | T: InfoRequestApiTest (CI green cac8d73)<br>J: wp3 «staff registers a request to the unit as a draft; the system generated the number» (UAT 3937a50) [INFOREQ] | | — | — |
| LD-DOC-18-005 | 327 | يتضمن كل طلب | موضوع الطلب. | YES | T: InfoRequestApiTest (CI green cac8d73)<br>J: wp3 «staff registers a request to the unit as a draft; the system generated the number» (UAT 3937a50) [INFOREQ] | | — | — |
| LD-DOC-18-006 | 328 | يتضمن كل طلب | تفاصيل الإفادة المطلوبة. | YES | T: InfoRequestApiTest (CI green cac8d73)<br>J: wp3 «staff registers a request to the unit as a draft; the system generated the number» (UAT 3937a50) [INFOREQ] | | — | — |
| LD-DOC-18-007 | 329 | يتضمن كل طلب | المستندات المطلوبة. | YES | T: InfoRequestApiTest (CI green cac8d73)<br>J: wp3 «staff registers a request to the unit as a draft; the system generated the number» (UAT 3937a50) [INFOREQ] | | — | — |
| LD-DOC-18-008 | 330 | يتضمن كل طلب | تاريخ إعداد الطلب. | YES | T: InfoRequestApiTest (CI green cac8d73)<br>J: wp3 «staff registers a request to the unit as a draft; the system generated the number» (UAT 3937a50) [INFOREQ] | | — | — |
| LD-DOC-18-009 | 331 | يتضمن كل طلب | تاريخ إرسال الكتاب. | YES | T: InfoRequestApiTest (CI green cac8d73)<br>J: wp3 «staff registers a request to the unit as a draft; the system generated the number» (UAT 3937a50) [INFOREQ] | | — | — |
| LD-DOC-18-010 | 332 | يتضمن كل طلب | رقم الكتاب وتاريخه. | YES | T: InfoRequestApiTest (CI green cac8d73)<br>J: wp3 «staff registers a request to the unit as a draft; the system generated the number» (UAT 3937a50) [INFOREQ] | | — | — |
| LD-DOC-18-011 | 333 | يتضمن كل طلب | الموعد المحدد للرد. | YES | T: InfoRequestApiTest (CI green cac8d73)<br>J: wp3 «staff registers a request to the unit as a draft; the system generated the number» (UAT 3937a50) [INFOREQ] | | — | — |
| LD-DOC-18-012 | 334 | يتضمن كل طلب | العضو القانوني المسؤول عن المتابعة. | YES | T: InfoRequestApiTest (CI green cac8d73)<br>J: wp3 «staff registers a request to the unit as a draft; the system generated the number» (UAT 3937a50) [INFOREQ] | | — | — |
| LD-DOC-18-013 | 335 | يتضمن كل طلب | الموظف أو المسؤول المخاطب. | YES | T: InfoRequestApiTest (CI green cac8d73)<br>J: wp3 «staff registers a request to the unit as a draft; the system generated the number» (UAT 3937a50) [INFOREQ] | | — | — |
| LD-DOC-18-014 | 336 | يتضمن كل طلب | الملاحظات. | YES | T: InfoRequestApiTest (CI green cac8d73)<br>J: wp3 «staff registers a request to the unit as a draft; the system generated the number» (UAT 3937a50) [INFOREQ] | | — | — |
| LD-DOC-18-015 | 339 | يجب أن تكون حالات الطلب | مسودة. | YES | T: InfoRequestApiTest (CI green cac8d73)<br>J: wp3 «staff registers a request to the unit as a draft; the system generated the number» (UAT 3937a50) [INFOREQ] | | — | — |
| LD-DOC-18-016 | 340 | يجب أن تكون حالات الطلب | قيد الاعتماد. | YES | T: InfoRequestApiTest (CI green cac8d73)<br>J: wp3 «staff registers a request to the unit as a draft; the system generated the number» (UAT 3937a50) [INFOREQ] | | — | — |
| LD-DOC-18-017 | 341 | يجب أن تكون حالات الطلب | مرسل. | YES | T: InfoRequestApiTest (CI green cac8d73)<br>J: wp3 «staff registers a request to the unit as a draft; the system generated the number» (UAT 3937a50) [INFOREQ] | | — | — |
| LD-DOC-18-018 | 342 | يجب أن تكون حالات الطلب | بانتظار الرد. | YES | T: InfoRequestApiTest (CI green cac8d73)<br>J: wp3 «staff registers a request to the unit as a draft; the system generated the number» (UAT 3937a50) [INFOREQ] | | — | — |
| LD-DOC-18-019 | 343 | يجب أن تكون حالات الطلب | ورد رد جزئي. | YES | T: InfoRequestApiTest (CI green cac8d73)<br>J: wp3 «staff registers a request to the unit as a draft; the system generated the number» (UAT 3937a50) [INFOREQ] | | — | — |
| LD-DOC-18-020 | 344 | يجب أن تكون حالات الطلب | ورد رد غير مكتمل. | YES | T: InfoRequestApiTest (CI green cac8d73)<br>J: wp3 «staff registers a request to the unit as a draft; the system generated the number» (UAT 3937a50) [INFOREQ] | | — | — |
| LD-DOC-18-021 | 345 | يجب أن تكون حالات الطلب | ورد رد مكتمل. | YES | T: InfoRequestApiTest (CI green cac8d73)<br>J: wp3 «staff registers a request to the unit as a draft; the system generated the number» (UAT 3937a50) [INFOREQ] | | — | — |
| LD-DOC-18-022 | 346 | يجب أن تكون حالات الطلب | متأخر. | YES | T: InfoRequestApiTest (CI green cac8d73)<br>J: wp3 «staff registers a request to the unit as a draft; the system generated the number» (UAT 3937a50) [INFOREQ] | | — | — |
| LD-DOC-18-023 | 347 | يجب أن تكون حالات الطلب | مغلق. | YES | T: InfoRequestApiTest (CI green cac8d73)<br>J: wp3 «staff registers a request to the unit as a draft; the system generated the number» (UAT 3937a50) [INFOREQ] | | — | — |
| LD-DOC-18-024 | 348 | يجب أن تكون حالات الطلب | ويجب إتاحة تسجيل أكثر من رد أو مستند على الطلب الواحد، مع الاحتفاظ بتاريخ جميع المراسلات. | YES | T: InfoRequestApiTest#keepsEveryReplyAndFollowsTheLatestOneUntilOneIsComplete, InfoRequestApiTest#aReplyDocumentIsArchivedInTheCaseFileAndLinkedToTheReply (CI green cac8d73)<br>J: wp3 «every reply is kept and the state follows the latest; the document is archived in the case file» (UAT 3937a50) [INFOREQ_REPLY] | | — | — |
| LD-DOC-18-025 | 350 | التنبيهات والتصعيد | تنبيه العضو قبل انتهاء موعد الرد. | YES | T: InfoRequestApiTest#theFollowUpRunMarksALateRequestOverdueNotifiesOnceAndNeverClosesIt (CI green cac8d73)<br>J: wp3 «the follow-up run marks late requests; the late request is marked overdue and is not closed» (UAT 3937a50) [INFOREQ_LATE] | | — | — |
| LD-DOC-18-026 | 351 | التنبيهات والتصعيد | إظهار الطلبات المتأخرة. | YES | T: InfoRequestApiTest#theFollowUpRunMarksALateRequestOverdueNotifiesOnceAndNeverClosesIt (CI green cac8d73)<br>J: wp3 «the follow-up run marks late requests; the late request is marked overdue and is not closed» (UAT 3937a50) [INFOREQ_LATE] | | — | — |
| LD-DOC-18-027 | 352 | التنبيهات والتصعيد | إرسال تنبيه للمسؤول المباشر عند التأخر. | YES |  | | — | no 'direct supervisor' model exists; escalation to a named user is tested, notification of a supervisor on delay is not |
| LD-DOC-18-028 | 353 | التنبيهات والتصعيد | إتاحة إعادة مخاطبة الإدارة. | YES | T: InfoRequestApiTest#remindersAndNewLettersAreRecordedInOrderWithTheirDate, InfoRequestApiTest#escalationNeedsItsPermissionAndATargetAndNotifiesTheTarget (CI green cac8d73)<br>J: wp3 «a reminder is recorded; a holder of the permission escalates» (UAT 3937a50) [INFOREQ_FOLLOW] | | — | — |
| LD-DOC-18-029 | 354 | التنبيهات والتصعيد | تسجيل تاريخ كل متابعة. | YES | T: InfoRequestApiTest#remindersAndNewLettersAreRecordedInOrderWithTheirDate, InfoRequestApiTest#escalationNeedsItsPermissionAndATargetAndNotifiesTheTarget (CI green cac8d73)<br>J: wp3 «a reminder is recorded; a holder of the permission escalates» (UAT 3937a50) [INFOREQ_FOLLOW] | | — | — |
| LD-DOC-18-030 | 355 | التنبيهات والتصعيد | بيان سبب التأخير عند وروده. | YES | T: InfoRequestApiTest#remindersAndNewLettersAreRecordedInOrderWithTheirDate, InfoRequestApiTest#escalationNeedsItsPermissionAndATargetAndNotifiesTheTarget (CI green cac8d73)<br>J: wp3 «a reminder is recorded; a holder of the permission escalates» (UAT 3937a50) [INFOREQ_FOLLOW] | | — | — |
| LD-DOC-18-031 | 356 | التنبيهات والتصعيد | إمكانية تصعيد الطلب وفق الصلاحيات المعتمدة. | YES | T: InfoRequestApiTest#remindersAndNewLettersAreRecordedInOrderWithTheirDate, InfoRequestApiTest#escalationNeedsItsPermissionAndATargetAndNotifiesTheTarget (CI green cac8d73)<br>J: wp3 «a reminder is recorded; a holder of the permission escalates» (UAT 3937a50) [INFOREQ_FOLLOW] | | — | — |
| LD-DOC-18-032 | 358 | ربط الردود بالقضية | عند ورود الإفادة أو المستند، يجب حفظه تلقائياً في ملف القضية وأرشفتها وربطها بالواقعة أو الطلب أو الدفع القانوني الذي يتعلق به. | YES | T: InfoRequestApiTest#keepsEveryReplyAndFollowsTheLatestOneUntilOneIsComplete, InfoRequestApiTest#aReplyDocumentIsArchivedInTheCaseFileAndLinkedToTheReply (CI green cac8d73)<br>J: wp3 «every reply is kept and the state follows the latest; the document is archived in the case file» (UAT 3937a50) [INFOREQ_REPLY] | | — | — |
| LD-DOC-18-033 | 361 | توفير تقارير توضح | عدد الطلبات الموجهة لكل إدارة. | YES | T: InfoRequestApiTest#theReportCountsRequestsRepliesDelaysAndIncompleteRepliesPerUnit (CI green cac8d73)<br>J: wp3 «the responsiveness report counts requests per unit» (UAT 3937a50)<br>J: wp8 «the units view reports the responsiveness of departments» (UAT 3937a50) [INFOREQ_REPORT] | | — | — |
| LD-DOC-18-034 | 362 | توفير تقارير توضح | عدد الردود الواردة. | YES | T: InfoRequestApiTest#theReportCountsRequestsRepliesDelaysAndIncompleteRepliesPerUnit (CI green cac8d73)<br>J: wp3 «the responsiveness report counts requests per unit» (UAT 3937a50)<br>J: wp8 «the units view reports the responsiveness of departments» (UAT 3937a50) [INFOREQ_REPORT] | | — | — |
| LD-DOC-18-035 | 363 | توفير تقارير توضح | متوسط مدة الرد. | YES | T: InfoRequestApiTest#theReportCountsRequestsRepliesDelaysAndIncompleteRepliesPerUnit (CI green cac8d73)<br>J: wp3 «the responsiveness report counts requests per unit» (UAT 3937a50)<br>J: wp8 «the units view reports the responsiveness of departments» (UAT 3937a50) [INFOREQ_REPORT] | | — | — |
| LD-DOC-18-036 | 364 | توفير تقارير توضح | عدد الطلبات المتأخرة. | YES | T: InfoRequestApiTest#theReportCountsRequestsRepliesDelaysAndIncompleteRepliesPerUnit (CI green cac8d73)<br>J: wp3 «the responsiveness report counts requests per unit» (UAT 3937a50)<br>J: wp8 «the units view reports the responsiveness of departments» (UAT 3937a50) [INFOREQ_REPORT] | | — | — |
| LD-DOC-18-037 | 365 | توفير تقارير توضح | عدد الردود غير المكتملة. | YES | T: InfoRequestApiTest#theReportCountsRequestsRepliesDelaysAndIncompleteRepliesPerUnit (CI green cac8d73)<br>J: wp3 «the responsiveness report counts requests per unit» (UAT 3937a50)<br>J: wp8 «the units view reports the responsiveness of departments» (UAT 3937a50) [INFOREQ_REPORT] | | — | — |
| LD-DOC-18-038 | 366 | توفير تقارير توضح | القضايا التي تأثرت بسبب تأخر الإفادات. | YES |  | | — | 'cases affected by late replies' is not asserted by the report test |
| LD-DOC-18-039 | 367 | توفير تقارير توضح | والهدف من هذه الوحدة هو تمكين الإدارة القانونية من متابعة احتياجات القضايا وتحديد أسباب التأخير بصورة موثقة. | YES | T: InfoRequestApiTest (CI green cac8d73)<br>J: wp3 «staff registers a request to the unit as a draft; the system generated the number» (UAT 3937a50) [INFOREQ] | | — | — |

<sub>عناوين المجموعات في المصدر: المرحلة الأولى – أولوية مرتفعة جداً (س320)؛ 1. تسجيل الطلب الداخلي (س322)؛ يتضمن كل طلب: (س323)؛ 2. متابعة الردود (س337)؛ يجب أن تكون حالات الطلب: (س338)؛ 3. التنبيهات والتصعيد (س349)؛ 4. ربط الردود بالقضية (س357)؛ 5. تقارير تجاوب الإدارات (س359)؛ توفير تقارير توضح: (س360)</sub>

## LD-DOC-19 — التاسع عشر: تقييم المخاطر القانونية للقضايا

- **المرحلة**: المرحلة الأولى · **الأولوية**: عالية · **حزمة العمل**: WP3 · **سيناريوهات القبول المرتبطة**: S7
- **التحديث (2026-11-09)**: نُفِّذ بحزمة العمل؛ الدليل: DOC-WP3 (journey: S7).

| المعرّف | السطر | المجموعة | المتطلب (نص المصدر) | IMPLEMENTED | VERIFIED_BY_DEVELOPER | QA_ACCEPTED | سبب العائق | ملاحظة (ما تبقى / لماذا لم يُتحقق) |
|---|---|---|---|---|---|---|---|---|
| LD-DOC-19-001 | 370 | — | إنشاء وحدة باسم «تقييم المخاطر القانونية»، تتيح تحديد مستوى المخاطر المرتبطة بكل قضية ومتابعتها خلال مراحل التقاضي. | YES | T: RiskAssessmentApiTest (CI green cac8d73)<br>J: wp3 «the first assessment is version 1, with ten elements; a high risk without a plan is refused» (UAT 3937a50) [RISK] | | — | — |
| LD-DOC-19-002 | 373 | يجب أن يتضمن التقييم العناصر التالية | قوة الموقف القانوني للجهة. | YES | T: RiskAssessmentApiTest (CI green cac8d73)<br>J: wp3 «the first assessment is version 1, with ten elements; a high risk without a plan is refused» (UAT 3937a50) [RISK] | | — | — |
| LD-DOC-19-003 | 374 | يجب أن يتضمن التقييم العناصر التالية | قوة المستندات والأدلة. | YES | T: RiskAssessmentApiTest (CI green cac8d73)<br>J: wp3 «the first assessment is version 1, with ten elements; a high risk without a plan is refused» (UAT 3937a50) [RISK] | | — | — |
| LD-DOC-19-004 | 375 | يجب أن يتضمن التقييم العناصر التالية | مدى سلامة الإجراءات الإدارية محل النزاع. | YES | T: RiskAssessmentApiTest (CI green cac8d73)<br>J: wp3 «the first assessment is version 1, with ten elements; a high risk without a plan is refused» (UAT 3937a50) [RISK] | | — | — |
| LD-DOC-19-005 | 376 | يجب أن يتضمن التقييم العناصر التالية | وجود عيوب إجرائية محتملة. | YES | T: RiskAssessmentApiTest (CI green cac8d73)<br>J: wp3 «the first assessment is version 1, with ten elements; a high risk without a plan is refused» (UAT 3937a50) [RISK] | | — | — |
| LD-DOC-19-006 | 377 | يجب أن يتضمن التقييم العناصر التالية | وجود سوابق قضائية مشابهة. | YES | T: RiskAssessmentApiTest (CI green cac8d73)<br>J: wp3 «the first assessment is version 1, with ten elements; a high risk without a plan is refused» (UAT 3937a50) [RISK] | | — | — |
| LD-DOC-19-007 | 378 | يجب أن يتضمن التقييم العناصر التالية | مدى استقرار الاتجاه القضائي في المسألة. | YES | T: RiskAssessmentApiTest (CI green cac8d73)<br>J: wp3 «the first assessment is version 1, with ten elements; a high risk without a plan is refused» (UAT 3937a50) [RISK] | | — | — |
| LD-DOC-19-008 | 379 | يجب أن يتضمن التقييم العناصر التالية | القيمة المالية المحتملة للخسارة. | YES | T: RiskAssessmentApiTest (CI green cac8d73)<br>J: wp3 «the first assessment is version 1, with ten elements; a high risk without a plan is refused» (UAT 3937a50) [RISK] | | — | — |
| LD-DOC-19-009 | 380 | يجب أن يتضمن التقييم العناصر التالية | الآثار الإدارية أو التنظيمية المحتملة. | YES | T: RiskAssessmentApiTest (CI green cac8d73)<br>J: wp3 «the first assessment is version 1, with ten elements; a high risk without a plan is refused» (UAT 3937a50) [RISK] | | — | — |
| LD-DOC-19-010 | 381 | يجب أن يتضمن التقييم العناصر التالية | احتمالية امتداد أثر الحكم إلى قضايا أخرى. | YES | T: RiskAssessmentApiTest (CI green cac8d73)<br>J: wp3 «the first assessment is version 1, with ten elements; a high risk without a plan is refused» (UAT 3937a50) [RISK] | | — | — |
| LD-DOC-19-011 | 382 | يجب أن يتضمن التقييم العناصر التالية | مدى توفر المعلومات والإفادات من الإدارات المعنية. | YES | T: RiskAssessmentApiTest (CI green cac8d73)<br>J: wp3 «the first assessment is version 1, with ten elements; a high risk without a plan is refused» (UAT 3937a50) [RISK] | | — | — |
| LD-DOC-19-012 | 385 | يتيح النظام تصنيف المخاطر إلى | منخفضة. | YES | T: RiskAssessmentApiTest (CI green cac8d73)<br>J: wp3 «the first assessment is version 1, with ten elements; a high risk without a plan is refused» (UAT 3937a50) [RISK] | | — | — |
| LD-DOC-19-013 | 386 | يتيح النظام تصنيف المخاطر إلى | متوسطة. | YES | T: RiskAssessmentApiTest (CI green cac8d73)<br>J: wp3 «the first assessment is version 1, with ten elements; a high risk without a plan is refused» (UAT 3937a50) [RISK] | | — | — |
| LD-DOC-19-014 | 387 | يتيح النظام تصنيف المخاطر إلى | مرتفعة. | YES | T: RiskAssessmentApiTest (CI green cac8d73)<br>J: wp3 «the first assessment is version 1, with ten elements; a high risk without a plan is refused» (UAT 3937a50) [RISK] | | — | — |
| LD-DOC-19-015 | 388 | يتيح النظام تصنيف المخاطر إلى | مرتفعة جداً. | YES | T: RiskAssessmentApiTest (CI green cac8d73)<br>J: wp3 «the first assessment is version 1, with ten elements; a high risk without a plan is refused» (UAT 3937a50) [RISK] | | — | — |
| LD-DOC-19-016 | 389 | يتيح النظام تصنيف المخاطر إلى | مع بيان أسباب التصنيف وعدم الاكتفاء بالدرجة النهائية. | YES | T: RiskAssessmentApiTest (CI green cac8d73)<br>J: wp3 «the first assessment is version 1, with ten elements; a high risk without a plan is refused» (UAT 3937a50) [RISK] | | — | — |
| LD-DOC-19-017 | 392 | لكل قضية ذات مخاطر مرتفعة، يجب إتاحة تسجيل | سبب الخطر. | YES | T: RiskAssessmentApiTest (CI green cac8d73)<br>J: wp3 «the first assessment is version 1, with ten elements; a high risk without a plan is refused» (UAT 3937a50) [RISK] | | — | — |
| LD-DOC-19-018 | 393 | لكل قضية ذات مخاطر مرتفعة، يجب إتاحة تسجيل | الإجراء المقترح لمعالجته. | YES | T: RiskAssessmentApiTest (CI green cac8d73)<br>J: wp3 «the first assessment is version 1, with ten elements; a high risk without a plan is refused» (UAT 3937a50) [RISK] | | — | — |
| LD-DOC-19-019 | 394 | لكل قضية ذات مخاطر مرتفعة، يجب إتاحة تسجيل | المستندات الواجب توفيرها. | YES | T: RiskAssessmentApiTest (CI green cac8d73)<br>J: wp3 «the first assessment is version 1, with ten elements; a high risk without a plan is refused» (UAT 3937a50) [RISK] | | — | — |
| LD-DOC-19-020 | 395 | لكل قضية ذات مخاطر مرتفعة، يجب إتاحة تسجيل | الدفوع المطلوب إعدادها. | YES | T: RiskAssessmentApiTest (CI green cac8d73)<br>J: wp3 «the first assessment is version 1, with ten elements; a high risk without a plan is refused» (UAT 3937a50) [RISK] | | — | — |
| LD-DOC-19-021 | 396 | لكل قضية ذات مخاطر مرتفعة، يجب إتاحة تسجيل | الإدارة المطلوب مخاطبتها. | YES | T: RiskAssessmentApiTest (CI green cac8d73)<br>J: wp3 «the first assessment is version 1, with ten elements; a high risk without a plan is refused» (UAT 3937a50) [RISK] | | — | — |
| LD-DOC-19-022 | 397 | لكل قضية ذات مخاطر مرتفعة، يجب إتاحة تسجيل | المسؤول عن الإجراء. | YES | T: RiskAssessmentApiTest (CI green cac8d73)<br>J: wp3 «the first assessment is version 1, with ten elements; a high risk without a plan is refused» (UAT 3937a50) [RISK] | | — | — |
| LD-DOC-19-023 | 398 | لكل قضية ذات مخاطر مرتفعة، يجب إتاحة تسجيل | الموعد المستهدف. | YES | T: RiskAssessmentApiTest (CI green cac8d73)<br>J: wp3 «the first assessment is version 1, with ten elements; a high risk without a plan is refused» (UAT 3937a50) [RISK] | | — | — |
| LD-DOC-19-024 | 399 | لكل قضية ذات مخاطر مرتفعة، يجب إتاحة تسجيل | حالة المعالجة. | YES | T: RiskAssessmentApiTest (CI green cac8d73)<br>J: wp3 «the first assessment is version 1, with ten elements; a high risk without a plan is refused» (UAT 3937a50) [RISK] | | — | — |
| LD-DOC-19-025 | 401 | تحديث التقييم | يجب أن يكون تقييم المخاطر قابلاً للتحديث عند ورود مستندات جديدة أو صدور حكم أو قرار أو تغير موقف القضية. | YES | T: RiskAssessmentApiTest (CI green cac8d73)<br>J: wp3 «a new document leads to a new version; the history keeps both versions, newest first» (UAT 3937a50) [RISK_VERSIONS] | | — | — |
| LD-DOC-19-026 | 402 | تحديث التقييم | ويحتفظ النظام بالتقييمات السابقة وتاريخ كل تقييم والعضو الذي أجراه. | YES | T: RiskAssessmentApiTest (CI green cac8d73)<br>J: wp3 «a new document leads to a new version; the history keeps both versions, newest first» (UAT 3937a50) [RISK_VERSIONS] | | — | — |
| LD-DOC-19-027 | 404 | التقارير | توفير تقرير بالقضايا ذات المخاطر المرتفعة، مع بيان قيمتها المالية المحتملة وأسباب المخاطر والإجراءات المتخذة لمعالجتها. | YES | T: RiskAssessmentApiTest (CI green cac8d73)<br>J: wp3 «the high-risk report lists the case with its plan» (UAT 3937a50) [RISK_REPORT] | | — | — |
| LD-DOC-19-028 | 405 | التقارير | ويجب أن يكون التقييم أداة مساعدة لاتخاذ القرار، ولا يعد توقعاً قضائياً مؤكداً أو بديلاً عن الرأي القانوني. | YES | T: RiskAssessmentApiTest (CI green cac8d73)<br>J: wp3 «the first assessment is version 1, with ten elements; a high risk without a plan is refused» (UAT 3937a50) [RISK] | | — | — |

<sub>عناوين المجموعات في المصدر: المرحلة الأولى – أولوية مرتفعة (س369)؛ 1. عناصر تقييم المخاطر (س371)؛ يجب أن يتضمن التقييم العناصر التالية: (س372)؛ 2. تصنيف مستوى المخاطر (س383)؛ يتيح النظام تصنيف المخاطر إلى: (س384)؛ 3. خطة معالجة المخاطر (س390)؛ لكل قضية ذات مخاطر مرتفعة، يجب إتاحة تسجيل: (س391)؛ 4. تحديث التقييم (س400)؛ 5. التقارير (س403)</sub>

## LD-DOC-20 — العشرون: إدارة الأدلة والمستندات المؤيدة للدفاع

- **المرحلة**: المرحلة الأولى · **الأولوية**: عالية · **حزمة العمل**: WP2 · **سيناريوهات القبول المرتبطة**: S8
- **التحديث (2026-11-09)**: نُفِّذ بحزمة العمل؛ الدليل: DOC-WP2 (journey wp2: S8).

| المعرّف | السطر | المجموعة | المتطلب (نص المصدر) | IMPLEMENTED | VERIFIED_BY_DEVELOPER | QA_ACCEPTED | سبب العائق | ملاحظة (ما تبقى / لماذا لم يُتحقق) |
|---|---|---|---|---|---|---|---|---|
| LD-DOC-20-001 | 408 | — | إنشاء وحدة مستقلة باسم «الأدلة والمستندات المؤيدة للدفاع»، بحيث لا يقتصر النظام على حفظ المستندات، وإنما يتيح تحديد دور كل مستند في بناء الموقف القانوني للجهة. | YES | T: EvidenceApiTest (CI green cac8d73)<br>J: wp2 «a document becomes evidence with a role and an importance; the same document is linked to a fact and to a defence» (UAT 4220ccf) [EVIDENCE] | | — | — |
| LD-DOC-20-002 | 411 | تصنيف المستندات إلى | مستندات مؤيدة لموقف الجهة. | YES | T: EvidenceApiTest (CI green cac8d73)<br>J: wp2 «a document becomes evidence with a role and an importance; the same document is linked to a fact and to a defence» (UAT 4220ccf) [EVIDENCE] | | — | — |
| LD-DOC-20-003 | 412 | تصنيف المستندات إلى | مستندات مقدمة من الخصم. | YES | T: EvidenceApiTest (CI green cac8d73)<br>J: wp2 «a document becomes evidence with a role and an importance; the same document is linked to a fact and to a defence» (UAT 4220ccf) [EVIDENCE] | | — | — |
| LD-DOC-20-004 | 413 | تصنيف المستندات إلى | مستندات محل نزاع. | YES | T: EvidenceApiTest (CI green cac8d73)<br>J: wp2 «a document becomes evidence with a role and an importance; the same document is linked to a fact and to a defence» (UAT 4220ccf) [EVIDENCE] | | — | — |
| LD-DOC-20-005 | 414 | تصنيف المستندات إلى | مستندات مطلوب الحصول عليها. | YES | T: EvidenceApiTest (CI green cac8d73)<br>J: wp2 «a document becomes evidence with a role and an importance; the same document is linked to a fact and to a defence» (UAT 4220ccf) [EVIDENCE] | | — | — |
| LD-DOC-20-006 | 415 | تصنيف المستندات إلى | مستندات غير متوافرة. | YES | T: EvidenceApiTest (CI green cac8d73)<br>J: wp2 «a document becomes evidence with a role and an importance; the same document is linked to a fact and to a defence» (UAT 4220ccf) [EVIDENCE] | | — | — |
| LD-DOC-20-007 | 416 | تصنيف المستندات إلى | مستندات إضافية أو مكملة. | YES | T: EvidenceApiTest (CI green cac8d73)<br>J: wp2 «a document becomes evidence with a role and an importance; the same document is linked to a fact and to a defence» (UAT 4220ccf) [EVIDENCE] | | — | — |
| LD-DOC-20-008 | 418 | بيانات كل مستند | اسم المستند. | YES | T: EvidenceApiTest (CI green cac8d73)<br>J: wp2 «a document becomes evidence with a role and an importance; the same document is linked to a fact and to a defence» (UAT 4220ccf) [EVIDENCE] | | — | — |
| LD-DOC-20-009 | 419 | بيانات كل مستند | نوع المستند. | YES | T: EvidenceApiTest (CI green cac8d73)<br>J: wp2 «a document becomes evidence with a role and an importance; the same document is linked to a fact and to a defence» (UAT 4220ccf) [EVIDENCE] | | — | — |
| LD-DOC-20-010 | 420 | بيانات كل مستند | تاريخ صدوره. | YES | T: EvidenceApiTest (CI green cac8d73)<br>J: wp2 «a document becomes evidence with a role and an importance; the same document is linked to a fact and to a defence» (UAT 4220ccf) [EVIDENCE] | | — | — |
| LD-DOC-20-011 | 421 | بيانات كل مستند | الجهة الصادرة عنه. | YES | T: EvidenceApiTest (CI green cac8d73)<br>J: wp2 «a document becomes evidence with a role and an importance; the same document is linked to a fact and to a defence» (UAT 4220ccf) [EVIDENCE] | | — | — |
| LD-DOC-20-012 | 422 | بيانات كل مستند | تاريخ وروده. | YES | T: EvidenceApiTest (CI green cac8d73)<br>J: wp2 «a document becomes evidence with a role and an importance; the same document is linked to a fact and to a defence» (UAT 4220ccf) [EVIDENCE] | | — | — |
| LD-DOC-20-013 | 423 | بيانات كل مستند | مصدر المستند. | YES | T: EvidenceApiTest (CI green cac8d73)<br>J: wp2 «a document becomes evidence with a role and an importance; the same document is linked to a fact and to a defence» (UAT 4220ccf) [EVIDENCE] | | — | — |
| LD-DOC-20-014 | 424 | بيانات كل مستند | الواقعة التي يثبتها. | YES | T: EvidenceApiTest (CI green cac8d73)<br>J: wp2 «a document becomes evidence with a role and an importance; the same document is linked to a fact and to a defence» (UAT 4220ccf) [EVIDENCE] | | — | — |
| LD-DOC-20-015 | 425 | بيانات كل مستند | الطلب أو الدفع المرتبط به. | YES | T: EvidenceApiTest (CI green cac8d73)<br>J: wp2 «a document becomes evidence with a role and an importance; the same document is linked to a fact and to a defence» (UAT 4220ccf) [EVIDENCE] | | — | — |
| LD-DOC-20-016 | 426 | بيانات كل مستند | مدى أهميته في الدفاع. | YES | T: EvidenceApiTest (CI green cac8d73)<br>J: wp2 «a document becomes evidence with a role and an importance; the same document is linked to a fact and to a defence» (UAT 4220ccf) [EVIDENCE] | | — | — |
| LD-DOC-20-017 | 427 | بيانات كل مستند | هل تم تقديمه للمحكمة؟ | YES | T: EvidenceApiTest (CI green cac8d73)<br>J: wp2 «a document becomes evidence with a role and an importance; the same document is linked to a fact and to a defence» (UAT 4220ccf) [EVIDENCE] | | — | — |
| LD-DOC-20-018 | 428 | بيانات كل مستند | في أي مذكرة أو جلسة تم تقديمه؟ | YES | T: EvidenceApiTest (CI green cac8d73)<br>J: wp2 «a document becomes evidence with a role and an importance; the same document is linked to a fact and to a defence» (UAT 4220ccf) [EVIDENCE] | | — | — |
| LD-DOC-20-019 | 429 | بيانات كل مستند | هل تم التعقيب عليه من الخصم؟ | YES | T: EvidenceApiTest (CI green cac8d73)<br>J: wp2 «a document becomes evidence with a role and an importance; the same document is linked to a fact and to a defence» (UAT 4220ccf) [EVIDENCE] | | — | — |
| LD-DOC-20-020 | 430 | بيانات كل مستند | الملاحظات. | YES | T: EvidenceApiTest (CI green cac8d73)<br>J: wp2 «a document becomes evidence with a role and an importance; the same document is linked to a fact and to a defence» (UAT 4220ccf) [EVIDENCE] | | — | — |
| LD-DOC-20-021 | 431 | بيانات كل مستند | النسخة الإلكترونية. | YES | T: EvidenceApiTest (CI green cac8d73)<br>J: wp2 «a document becomes evidence with a role and an importance; the same document is linked to a fact and to a defence» (UAT 4220ccf) [EVIDENCE] | | — | — |
| LD-DOC-20-022 | 433 | ربط الأدلة بالوقائع والدفوع | يجب أن يسمح النظام بربط المستند الواحد بأكثر من واقعة أو دفع، وربط كل دفع بالمستندات التي تؤيده. | YES | T: EvidenceApiTest (CI green cac8d73)<br>J: wp2 «a document becomes evidence with a role and an importance; the same document is linked to a fact and to a defence» (UAT 4220ccf) [EVIDENCE] | | — | — |
| LD-DOC-20-023 | 434 | ربط الأدلة بالوقائع والدفوع | كما يجب أن يتيح إعداد قائمة بالمستندات المؤيدة لكل دفع، والمستندات التي لا تزال مطلوبة من الإدارات المعنية. | YES | T: EvidenceApiTest, GapClosureApiTest#missingEvidenceCanBeFollowedByATaskOrAnInformationRequestOfTheSameCase (CI green cac8d73)<br>J: wp2 «a required document not yet obtained is a gap; a document obtained but not submitted to the court is a gap» (UAT 4220ccf) [EVIDENCE_GAPS] | | — | — |
| LD-DOC-20-024 | 436 | متابعة تقديم المستندات للمحكمة | تسجيل تاريخ تقديم المستند ورقم المذكرة أو الجلسة التي قدم فيها، مع إمكانية تسجيل موقف المحكمة أو الخصم منه. | YES | T: EvidenceApiTest (CI green cac8d73)<br>J: wp2 «a document becomes evidence with a role and an importance; the same document is linked to a fact and to a defence» (UAT 4220ccf) [EVIDENCE] | | — | — |
| LD-DOC-20-025 | 438 | كشف النواقص | يجب أن يستطيع النظام إظهار المستندات المطلوبة التي لم يتم الحصول عليها بعد، أو التي لم يتم تقديمها للمحكمة، مع ربطها بمهام المتابعة وطلبات الإدارات المعنية. | YES | T: EvidenceApiTest, GapClosureApiTest#missingEvidenceCanBeFollowedByATaskOrAnInformationRequestOfTheSameCase (CI green cac8d73)<br>J: wp2 «a required document not yet obtained is a gap; a document obtained but not submitted to the court is a gap» (UAT 4220ccf) [EVIDENCE_GAPS] | | — | — |

<sub>عناوين المجموعات في المصدر: المرحلة الأولى – أولوية مرتفعة (س407)؛ 1. تصنيف المستندات (س409)؛ تصنيف المستندات إلى: (س410)؛ 2. بيانات كل مستند (س417)؛ 3. ربط الأدلة بالوقائع والدفوع (س432)؛ 4. متابعة تقديم المستندات للمحكمة (س435)؛ 5. كشف النواقص (س437)</sub>

## LD-DOC-21 — الحادي والعشرون: الملخص التنفيذي والتسلسل الزمني للقضية (Timeline)

- **المرحلة**: المرحلة الأولى · **الأولوية**: عالية · **حزمة العمل**: WP3 · **سيناريوهات القبول المرتبطة**: S1, S12
- **التحديث (2026-11-09)**: نُفِّذ بحزمة العمل؛ الدليل: DOC-WP3 (journey: S12).

| المعرّف | السطر | المجموعة | المتطلب (نص المصدر) | IMPLEMENTED | VERIFIED_BY_DEVELOPER | QA_ACCEPTED | سبب العائق | ملاحظة (ما تبقى / لماذا لم يُتحقق) |
|---|---|---|---|---|---|---|---|---|
| LD-DOC-21-001 | 441 | — | إنشاء ملخص تنفيذي تلقائي لكل قضية يظهر عند فتح ملفها، مع سجل زمني شامل لجميع أحداثها. | YES | T: CaseFileApiTest, CaseTimelineApiTest (CI green cac8d73)<br>J: wp3 «the summary is assembled from the stored data; it names the hearings, the next one, the risk and the pending requests» (UAT 3937a50) [SUMMARY] | | — | — |
| LD-DOC-21-002 | 444 | يجب أن يعرض الملخص | رقم القضية وموضوعها. | YES | T: CaseFileApiTest, CaseTimelineApiTest (CI green cac8d73)<br>J: wp3 «the summary is assembled from the stored data; it names the hearings, the next one, the risk and the pending requests» (UAT 3937a50) [SUMMARY] | | — | — |
| LD-DOC-21-003 | 445 | يجب أن يعرض الملخص | أطراف الدعوى. | YES | T: CaseFileApiTest, CaseTimelineApiTest (CI green cac8d73)<br>J: wp3 «the summary is assembled from the stored data; it names the hearings, the next one, the risk and the pending requests» (UAT 3937a50) [SUMMARY] | | — | — |
| LD-DOC-21-004 | 446 | يجب أن يعرض الملخص | الجهة الإدارية المعنية. | YES | T: CaseFileApiTest, CaseTimelineApiTest (CI green cac8d73)<br>J: wp3 «the summary is assembled from the stored data; it names the hearings, the next one, the risk and the pending requests» (UAT 3937a50) [SUMMARY] | | — | — |
| LD-DOC-21-005 | 447 | يجب أن يعرض الملخص | العضو القانوني المسؤول. | YES | T: CaseFileApiTest, CaseTimelineApiTest (CI green cac8d73)<br>J: wp3 «the summary is assembled from the stored data; it names the hearings, the next one, the risk and the pending requests» (UAT 3937a50) [SUMMARY] | | — | — |
| LD-DOC-21-006 | 448 | يجب أن يعرض الملخص | الطلبات المقدمة. | YES | T: CaseFileApiTest, CaseTimelineApiTest (CI green cac8d73)<br>J: wp3 «the summary is assembled from the stored data; it names the hearings, the next one, the risk and the pending requests» (UAT 3937a50) [SUMMARY] | | — | — |
| LD-DOC-21-007 | 449 | يجب أن يعرض الملخص | موقف الجهة من الدعوى. | YES | T: CaseFileApiTest, CaseTimelineApiTest (CI green cac8d73)<br>J: wp3 «the summary is assembled from the stored data; it names the hearings, the next one, the risk and the pending requests» (UAT 3937a50) [SUMMARY] | | — | — |
| LD-DOC-21-008 | 450 | يجب أن يعرض الملخص | أهم الدفوع وأوجه الدفاع. | YES | T: CaseFileApiTest, CaseTimelineApiTest (CI green cac8d73)<br>J: wp3 «the summary is assembled from the stored data; it names the hearings, the next one, the risk and the pending requests» (UAT 3937a50) [SUMMARY] | | — | — |
| LD-DOC-21-009 | 451 | يجب أن يعرض الملخص | الأدلة والمستندات الأساسية. | YES | T: CaseFileApiTest, CaseTimelineApiTest (CI green cac8d73)<br>J: wp3 «the summary is assembled from the stored data; it names the hearings, the next one, the risk and the pending requests» (UAT 3937a50) [SUMMARY] | | — | — |
| LD-DOC-21-010 | 452 | يجب أن يعرض الملخص | آخر إجراء تم. | YES | T: CaseFileApiTest, CaseTimelineApiTest (CI green cac8d73)<br>J: wp3 «the summary is assembled from the stored data; it names the hearings, the next one, the risk and the pending requests» (UAT 3937a50) [SUMMARY] | | — | — |
| LD-DOC-21-011 | 453 | يجب أن يعرض الملخص | آخر جلسة وقرار المحكمة. | YES | T: CaseFileApiTest, CaseTimelineApiTest (CI green cac8d73)<br>J: wp3 «the summary is assembled from the stored data; it names the hearings, the next one, the risk and the pending requests» (UAT 3937a50) [SUMMARY] | | — | — |
| LD-DOC-21-012 | 454 | يجب أن يعرض الملخص | موعد الجلسة القادمة. | YES | T: CaseFileApiTest, CaseTimelineApiTest (CI green cac8d73)<br>J: wp3 «the summary is assembled from the stored data; it names the hearings, the next one, the risk and the pending requests» (UAT 3937a50) [SUMMARY] | | — | — |
| LD-DOC-21-013 | 455 | يجب أن يعرض الملخص | آخر حكم صدر. | YES | T: CaseFileApiTest, CaseTimelineApiTest (CI green cac8d73)<br>J: wp3 «the summary is assembled from the stored data; it names the hearings, the next one, the risk and the pending requests» (UAT 3937a50) [SUMMARY] | | — | — |
| LD-DOC-21-014 | 456 | يجب أن يعرض الملخص | المواعيد القانونية القائمة. | YES | T: CaseFileApiTest, CaseTimelineApiTest (CI green cac8d73)<br>J: wp3 «the summary is assembled from the stored data; it names the hearings, the next one, the risk and the pending requests» (UAT 3937a50) [SUMMARY] | | — | — |
| LD-DOC-21-015 | 457 | يجب أن يعرض الملخص | حالة تنفيذ الحكم. | YES | T: CaseFileApiTest, CaseTimelineApiTest (CI green cac8d73)<br>J: wp3 «the summary is assembled from the stored data; it names the hearings, the next one, the risk and the pending requests» (UAT 3937a50) [SUMMARY] | | — | — |
| LD-DOC-21-016 | 458 | يجب أن يعرض الملخص | مستوى المخاطر القانونية. | YES | T: CaseFileApiTest, CaseTimelineApiTest (CI green cac8d73)<br>J: wp3 «the summary is assembled from the stored data; it names the hearings, the next one, the risk and the pending requests» (UAT 3937a50) [SUMMARY] | | — | — |
| LD-DOC-21-017 | 459 | يجب أن يعرض الملخص | المستندات أو الإفادات الناقصة. | YES | T: CaseFileApiTest, CaseTimelineApiTest (CI green cac8d73)<br>J: wp3 «the summary is assembled from the stored data; it names the hearings, the next one, the risk and the pending requests» (UAT 3937a50) [SUMMARY] | | — | — |
| LD-DOC-21-018 | 460 | يجب أن يعرض الملخص | المهام والإجراءات المطلوبة حالياً. | YES | T: CaseFileApiTest, CaseTimelineApiTest (CI green cac8d73)<br>J: wp3 «the summary is assembled from the stored data; it names the hearings, the next one, the risk and the pending requests» (UAT 3937a50) [SUMMARY] | | — | — |
| LD-DOC-21-019 | 461 | يجب أن يعرض الملخص | ويجب تحديث الملخص تلقائياً كلما أضيفت بيانات أو إجراءات جديدة. | YES | T: CaseFileApiTest, CaseTimelineApiTest (CI green cac8d73)<br>J: wp3 «the summary is assembled from the stored data; it names the hearings, the next one, the risk and the pending requests» (UAT 3937a50) [SUMMARY] | | — | — |
| LD-DOC-21-020 | 464 | إنشاء خط زمني مرتب حسب التاريخ، يعرض جميع أحداث القضية، ومنها | تاريخ ورود القضية. | YES | T: CaseTimelineApiTest, JudicialChronologyTest (CI green cac8d73)<br>F: timelineEvents.test.ts (CI green cac8d73)<br>J: wp3 «the timeline joins hearings, requests, replies, risk and links; it filters by type» (UAT 3937a50) [TIMELINE] | | — | — |
| LD-DOC-21-021 | 465 | إنشاء خط زمني مرتب حسب التاريخ، يعرض جميع أحداث القضية، ومنها | تاريخ تسجيلها. | YES | T: CaseTimelineApiTest, JudicialChronologyTest (CI green cac8d73)<br>F: timelineEvents.test.ts (CI green cac8d73)<br>J: wp3 «the timeline joins hearings, requests, replies, risk and links; it filters by type» (UAT 3937a50) [TIMELINE] | | — | — |
| LD-DOC-21-022 | 466 | إنشاء خط زمني مرتب حسب التاريخ، يعرض جميع أحداث القضية، ومنها | تاريخ إحالتها إلى العضو القانوني. | YES | T: CaseTimelineApiTest, JudicialChronologyTest (CI green cac8d73)<br>F: timelineEvents.test.ts (CI green cac8d73)<br>J: wp3 «the timeline joins hearings, requests, replies, risk and links; it filters by type» (UAT 3937a50) [TIMELINE] | | — | — |
| LD-DOC-21-023 | 467 | إنشاء خط زمني مرتب حسب التاريخ، يعرض جميع أحداث القضية، ومنها | تاريخ تحديد الإدارات المعنية. | YES | T: CaseTimelineApiTest, JudicialChronologyTest (CI green cac8d73)<br>F: timelineEvents.test.ts (CI green cac8d73)<br>J: wp3 «the timeline joins hearings, requests, replies, risk and links; it filters by type» (UAT 3937a50) [TIMELINE] | | — | — |
| LD-DOC-21-024 | 468 | إنشاء خط زمني مرتب حسب التاريخ، يعرض جميع أحداث القضية، ومنها | تاريخ طلب الإفادات والمستندات. | YES | T: CaseTimelineApiTest, JudicialChronologyTest (CI green cac8d73)<br>F: timelineEvents.test.ts (CI green cac8d73)<br>J: wp3 «the timeline joins hearings, requests, replies, risk and links; it filters by type» (UAT 3937a50) [TIMELINE] | | — | — |
| LD-DOC-21-025 | 469 | إنشاء خط زمني مرتب حسب التاريخ، يعرض جميع أحداث القضية، ومنها | تواريخ ورود الردود. | YES | T: CaseTimelineApiTest, JudicialChronologyTest (CI green cac8d73)<br>F: timelineEvents.test.ts (CI green cac8d73)<br>J: wp3 «the timeline joins hearings, requests, replies, risk and links; it filters by type» (UAT 3937a50) [TIMELINE] | | — | — |
| LD-DOC-21-026 | 470 | إنشاء خط زمني مرتب حسب التاريخ، يعرض جميع أحداث القضية، ومنها | تاريخ تقديم صحيفة الدعوى. | YES | T: CaseTimelineApiTest, JudicialChronologyTest (CI green cac8d73)<br>F: timelineEvents.test.ts (CI green cac8d73)<br>J: wp3 «the timeline joins hearings, requests, replies, risk and links; it filters by type» (UAT 3937a50) [TIMELINE] | | — | — |
| LD-DOC-21-027 | 471 | إنشاء خط زمني مرتب حسب التاريخ، يعرض جميع أحداث القضية، ومنها | تواريخ المذكرات. | YES | T: CaseTimelineApiTest, JudicialChronologyTest (CI green cac8d73)<br>F: timelineEvents.test.ts (CI green cac8d73)<br>J: wp3 «the timeline joins hearings, requests, replies, risk and links; it filters by type» (UAT 3937a50) [TIMELINE] | | — | — |
| LD-DOC-21-028 | 472 | إنشاء خط زمني مرتب حسب التاريخ، يعرض جميع أحداث القضية، ومنها | تواريخ الجلسات. | YES | T: CaseTimelineApiTest, JudicialChronologyTest (CI green cac8d73)<br>F: timelineEvents.test.ts (CI green cac8d73)<br>J: wp3 «the timeline joins hearings, requests, replies, risk and links; it filters by type» (UAT 3937a50) [TIMELINE] | | — | — |
| LD-DOC-21-029 | 473 | إنشاء خط زمني مرتب حسب التاريخ، يعرض جميع أحداث القضية، ومنها | قرارات المحكمة والخبراء. | YES | T: CaseTimelineApiTest, JudicialChronologyTest (CI green cac8d73)<br>F: timelineEvents.test.ts (CI green cac8d73)<br>J: wp3 «the timeline joins hearings, requests, replies, risk and links; it filters by type» (UAT 3937a50) [TIMELINE] | | — | — |
| LD-DOC-21-030 | 474 | إنشاء خط زمني مرتب حسب التاريخ، يعرض جميع أحداث القضية، ومنها | الأحكام الصادرة. | YES | T: CaseTimelineApiTest, JudicialChronologyTest (CI green cac8d73)<br>F: timelineEvents.test.ts (CI green cac8d73)<br>J: wp3 «the timeline joins hearings, requests, replies, risk and links; it filters by type» (UAT 3937a50) [TIMELINE] | | — | — |
| LD-DOC-21-031 | 475 | إنشاء خط زمني مرتب حسب التاريخ، يعرض جميع أحداث القضية، ومنها | الطعون. | YES | T: CaseTimelineApiTest, JudicialChronologyTest (CI green cac8d73)<br>F: timelineEvents.test.ts (CI green cac8d73)<br>J: wp3 «the timeline joins hearings, requests, replies, risk and links; it filters by type» (UAT 3937a50) [TIMELINE] | | — | — |
| LD-DOC-21-032 | 476 | إنشاء خط زمني مرتب حسب التاريخ، يعرض جميع أحداث القضية، ومنها | إجراءات التنفيذ. | YES | T: CaseTimelineApiTest, JudicialChronologyTest (CI green cac8d73)<br>F: timelineEvents.test.ts (CI green cac8d73)<br>J: wp3 «the timeline joins hearings, requests, replies, risk and links; it filters by type» (UAT 3937a50) [TIMELINE] | | — | — |
| LD-DOC-21-033 | 477 | إنشاء خط زمني مرتب حسب التاريخ، يعرض جميع أحداث القضية، ومنها | تاريخ الإغلاق. | YES | T: CaseTimelineApiTest, JudicialChronologyTest (CI green cac8d73)<br>F: timelineEvents.test.ts (CI green cac8d73)<br>J: wp3 «the timeline joins hearings, requests, replies, risk and links; it filters by type» (UAT 3937a50) [TIMELINE] | | — | — |
| LD-DOC-21-034 | 479 | خصائص التسلسل الزمني | عرض الأحداث حسب التسلسل التاريخي. | YES | T: CaseTimelineApiTest (CI green cac8d73)<br>J: wp3 «the upcoming hearing is marked as future and links to its record; the summary of the closed case still shows its history» (UAT 3937a50) [TIMELINE_PAST] | | — | — |
| LD-DOC-21-035 | 480 | خصائص التسلسل الزمني | إمكانية التصفية حسب نوع الحدث. | YES | T: CaseTimelineApiTest (CI green cac8d73)<br>J: wp3 «the upcoming hearing is marked as future and links to its record; the summary of the closed case still shows its history» (UAT 3937a50) [TIMELINE_PAST] | | — | — |
| LD-DOC-21-036 | 481 | خصائص التسلسل الزمني | الانتقال من الحدث مباشرة إلى المستند أو الجلسة أو الطلب المرتبط به. | YES | T: CaseTimelineApiTest (CI green cac8d73)<br>J: wp3 «the upcoming hearing is marked as future and links to its record; the summary of the closed case still shows its history» (UAT 3937a50) [TIMELINE_PAST] | | — | — |
| LD-DOC-21-037 | 482 | خصائص التسلسل الزمني | إظهار المستخدم الذي سجل الحدث. | YES | T: CaseTimelineApiTest (CI green cac8d73)<br>J: wp3 «the upcoming hearing is marked as future and links to its record; the summary of the closed case still shows its history» (UAT 3937a50) [TIMELINE_PAST] | | — | — |
| LD-DOC-21-038 | 483 | خصائص التسلسل الزمني | الاحتفاظ بالتعديلات التاريخية. | YES | T: CaseTimelineApiTest (CI green cac8d73)<br>J: wp3 «the upcoming hearing is marked as future and links to its record; the summary of the closed case still shows its history» (UAT 3937a50) [TIMELINE_PAST] | | — | — |
| LD-DOC-21-039 | 484 | خصائص التسلسل الزمني | التمييز بين الأحداث المكتملة والإجراءات المستقبلية. | YES | T: CaseTimelineApiTest (CI green cac8d73)<br>J: wp3 «the upcoming hearing is marked as future and links to its record; the summary of the closed case still shows its history» (UAT 3937a50) [TIMELINE_PAST] | | — | — |
| LD-DOC-21-040 | 485 | خصائص التسلسل الزمني | إظهار المواعيد التي لم يتم استكمال الإجراء المطلوب بشأنها. | YES | T: CaseTimelineApiTest (CI green cac8d73)<br>J: wp3 «the upcoming hearing is marked as future and links to its record; the summary of the closed case still shows its history» (UAT 3937a50) [TIMELINE_PAST] | | — | — |
| LD-DOC-21-041 | 486 | خصائص التسلسل الزمني | ويجب أن يكون هذا السجل مرجعاً سريعاً لفهم تاريخ القضية دون الحاجة إلى فتح جميع التبويبات بصورة منفصلة. | YES | T: CaseFileApiTest, CaseTimelineApiTest (CI green cac8d73)<br>J: wp3 «the summary is assembled from the stored data; it names the hearings, the next one, the risk and the pending requests» (UAT 3937a50) [SUMMARY] | | — | — |

<sub>عناوين المجموعات في المصدر: المرحلة الأولى – أولوية مرتفعة (س440)؛ 1. الملخص التنفيذي (س442)؛ يجب أن يعرض الملخص: (س443)؛ 2. التسلسل الزمني (Timeline) (س462)؛ إنشاء خط زمني مرتب حسب التاريخ، يعرض جميع أحداث القضية، ومنها: (س463)؛ 3. خصائص التسلسل الزمني (س478)</sub>

## LD-DOC-22 — الثاني والعشرون: المساعد القانوني الذكي داخل LawDesk

- **المرحلة**: المرحلة الثالثة · **الأولوية**: ثالثة · **حزمة العمل**: WP10 · **سيناريوهات القبول المرتبطة**: S16
- **التحديث (2026-11-09)**: نُفِّذ بحزمة العمل؛ الدليل: DOC-WP10 (journey wp10: S16 بمزود اختبار غير إنتاجي).

| المعرّف | السطر | المجموعة | المتطلب (نص المصدر) | IMPLEMENTED | VERIFIED_BY_DEVELOPER | QA_ACCEPTED | سبب العائق | ملاحظة (ما تبقى / لماذا لم يُتحقق) |
|---|---|---|---|---|---|---|---|---|
| LD-DOC-22-001 | 489 | — | إضافة مساعد قانوني ذكي داخل النظام، يكون مرتبطاً بملفات القضايا والأرشيف الإلكتروني ومكتبة الأحكام والسوابق القضائية ومكتبة الدفوع، وذلك لمساعدة الأعضاء القانونيين في تحليل الملفات وإعداد الأعمال القانونية. | BLOCKED |  | | EXTERNAL_PROVIDER | Not operational by design: no provider approved, nothing leaves the system. The retrieval, citation, refusal and logging parts exist and are tested with a deterministic provider (see PARTIAL rows of section 22). Dependency: D8: owner approves an AI provider and what may be sent to it (LD-DOC-09). |
| LD-DOC-22-002 | 492 | يتيح المساعد | قراءة المستندات المسجلة في القضية. | BLOCKED |  | | EXTERNAL_PROVIDER | Not operational by design: no provider approved, nothing leaves the system. The retrieval, citation, refusal and logging parts exist and are tested with a deterministic provider (see PARTIAL rows of section 22). Dependency: D8: owner approves an AI provider and what may be sent to it (LD-DOC-09). |
| LD-DOC-22-003 | 493 | يتيح المساعد | إعداد ملخص شامل للوقائع. | PARTIAL |  | | EXTERNAL_PROVIDER | DONE and verified with a deterministic non-production provider: extractive summary quoting the case records with their sources. NOT done: abstractive summary of facts by a real model. Dependency: D8: approve an AI provider and what may be sent to it. |
| LD-DOC-22-004 | 494 | يتيح المساعد | إعداد تسلسل زمني للإجراءات. | BLOCKED |  | | EXTERNAL_PROVIDER | Not operational by design: no provider approved, nothing leaves the system. The retrieval, citation, refusal and logging parts exist and are tested with a deterministic provider (see PARTIAL rows of section 22). Dependency: D8: owner approves an AI provider and what may be sent to it (LD-DOC-09). |
| LD-DOC-22-005 | 495 | يتيح المساعد | استخراج طلبات الخصوم. | BLOCKED |  | | EXTERNAL_PROVIDER | Not operational by design: no provider approved, nothing leaves the system. The retrieval, citation, refusal and logging parts exist and are tested with a deterministic provider (see PARTIAL rows of section 22). Dependency: D8: owner approves an AI provider and what may be sent to it (LD-DOC-09). |
| LD-DOC-22-006 | 496 | يتيح المساعد | استخراج الدفوع وأوجه الدفاع. | BLOCKED |  | | EXTERNAL_PROVIDER | Not operational by design: no provider approved, nothing leaves the system. The retrieval, citation, refusal and logging parts exist and are tested with a deterministic provider (see PARTIAL rows of section 22). Dependency: D8: owner approves an AI provider and what may be sent to it (LD-DOC-09). |
| LD-DOC-22-007 | 497 | يتيح المساعد | تلخيص الأحكام الصادرة. | BLOCKED |  | | EXTERNAL_PROVIDER | Not operational by design: no provider approved, nothing leaves the system. The retrieval, citation, refusal and logging parts exist and are tested with a deterministic provider (see PARTIAL rows of section 22). Dependency: D8: owner approves an AI provider and what may be sent to it (LD-DOC-09). |
| LD-DOC-22-008 | 498 | يتيح المساعد | بيان موقف كل طلب وكل دفع. | BLOCKED |  | | EXTERNAL_PROVIDER | Not operational by design: no provider approved, nothing leaves the system. The retrieval, citation, refusal and logging parts exist and are tested with a deterministic provider (see PARTIAL rows of section 22). Dependency: D8: owner approves an AI provider and what may be sent to it (LD-DOC-09). |
| LD-DOC-22-009 | 499 | يتيح المساعد | تحديد آخر إجراء تم والإجراء المطلوب لاحقاً. | BLOCKED |  | | EXTERNAL_PROVIDER | Not operational by design: no provider approved, nothing leaves the system. The retrieval, citation, refusal and logging parts exist and are tested with a deterministic provider (see PARTIAL rows of section 22). Dependency: D8: owner approves an AI provider and what may be sent to it (LD-DOC-09). |
| LD-DOC-22-010 | 502 | يتيح المساعد | استخراج بيانات القضية من صحيفة الدعوى. | BLOCKED |  | | EXTERNAL_PROVIDER | Not operational by design: no provider approved, nothing leaves the system. The retrieval, citation, refusal and logging parts exist and are tested with a deterministic provider (see PARTIAL rows of section 22). Dependency: D8: owner approves an AI provider and what may be sent to it (LD-DOC-09). |
| LD-DOC-22-011 | 503 | يتيح المساعد | استخراج أسماء الخصوم وأرقام القضايا. | BLOCKED |  | | EXTERNAL_PROVIDER | Not operational by design: no provider approved, nothing leaves the system. The retrieval, citation, refusal and logging parts exist and are tested with a deterministic provider (see PARTIAL rows of section 22). Dependency: D8: owner approves an AI provider and what may be sent to it (LD-DOC-09). |
| LD-DOC-22-012 | 504 | يتيح المساعد | استخراج الطلبات من الصحف والمذكرات. | BLOCKED |  | | EXTERNAL_PROVIDER | Not operational by design: no provider approved, nothing leaves the system. The retrieval, citation, refusal and logging parts exist and are tested with a deterministic provider (see PARTIAL rows of section 22). Dependency: D8: owner approves an AI provider and what may be sent to it (LD-DOC-09). |
| LD-DOC-22-013 | 505 | يتيح المساعد | استخراج تواريخ الجلسات وقرارات المحكمة من المحاضر. | BLOCKED |  | | EXTERNAL_PROVIDER | Not operational by design: no provider approved, nothing leaves the system. The retrieval, citation, refusal and logging parts exist and are tested with a deterministic provider (see PARTIAL rows of section 22). Dependency: D8: owner approves an AI provider and what may be sent to it (LD-DOC-09). |
| LD-DOC-22-014 | 506 | يتيح المساعد | استخراج منطوق الأحكام وأسبابها. | BLOCKED |  | | EXTERNAL_PROVIDER | Not operational by design: no provider approved, nothing leaves the system. The retrieval, citation, refusal and logging parts exist and are tested with a deterministic provider (see PARTIAL rows of section 22). Dependency: D8: owner approves an AI provider and what may be sent to it (LD-DOC-09). |
| LD-DOC-22-015 | 507 | يتيح المساعد | تصنيف المستندات تلقائياً. | BLOCKED |  | | EXTERNAL_PROVIDER | Not operational by design: no provider approved, nothing leaves the system. The retrieval, citation, refusal and logging parts exist and are tested with a deterministic provider (see PARTIAL rows of section 22). Dependency: D8: owner approves an AI provider and what may be sent to it (LD-DOC-09). |
| LD-DOC-22-016 | 508 | يتيح المساعد | تحديد المستندات التي قد تكون ناقصة. | BLOCKED |  | | EXTERNAL_PROVIDER | Not operational by design: no provider approved, nothing leaves the system. The retrieval, citation, refusal and logging parts exist and are tested with a deterministic provider (see PARTIAL rows of section 22). Dependency: D8: owner approves an AI provider and what may be sent to it (LD-DOC-09). |
| LD-DOC-22-017 | 511 | يجب أن يكون المساعد مرتبطاً بمكتبة الأحكام والسوابق، بحيث يستطيع | البحث عن أحكام مشابهة. | PARTIAL |  | | EXTERNAL_PROVIDER | Independent part DONE and verified in the library: similar-judgment suggestions with their reasons and a citation (source, date, number) - SimilarJudgmentApiTest, journey wp7. The assistant on top of it is not operational. Dependency: D8 for the assistant wording; the library function itself needs no provider. |
| LD-DOC-22-018 | 512 | يجب أن يكون المساعد مرتبطاً بمكتبة الأحكام والسوابق، بحيث يستطيع | اقتراح المبادئ القضائية ذات الصلة. | PARTIAL |  | | EXTERNAL_PROVIDER | Independent part DONE and verified in the library: similar-judgment suggestions with their reasons and a citation (source, date, number) - SimilarJudgmentApiTest, journey wp7. The assistant on top of it is not operational. Dependency: D8 for the assistant wording; the library function itself needs no provider. |
| LD-DOC-22-019 | 513 | يجب أن يكون المساعد مرتبطاً بمكتبة الأحكام والسوابق، بحيث يستطيع | تحديد الأحكام المؤيدة لموقف الجهة. | BLOCKED |  | | EXTERNAL_PROVIDER | Not operational by design: no provider approved, nothing leaves the system. The retrieval, citation, refusal and logging parts exist and are tested with a deterministic provider (see PARTIAL rows of section 22). Dependency: D8: owner approves an AI provider and what may be sent to it (LD-DOC-09). |
| LD-DOC-22-020 | 514 | يجب أن يكون المساعد مرتبطاً بمكتبة الأحكام والسوابق، بحيث يستطيع | إظهار الأحكام التي قد تشكل اتجاهاً مخالفاً. | BLOCKED |  | | EXTERNAL_PROVIDER | Not operational by design: no provider approved, nothing leaves the system. The retrieval, citation, refusal and logging parts exist and are tested with a deterministic provider (see PARTIAL rows of section 22). Dependency: D8: owner approves an AI provider and what may be sent to it (LD-DOC-09). |
| LD-DOC-22-021 | 515 | يجب أن يكون المساعد مرتبطاً بمكتبة الأحكام والسوابق، بحيث يستطيع | مقارنة الوقائع والطلبات والدفوع بين القضايا. | BLOCKED |  | | EXTERNAL_PROVIDER | Not operational by design: no provider approved, nothing leaves the system. The retrieval, citation, refusal and logging parts exist and are tested with a deterministic provider (see PARTIAL rows of section 22). Dependency: D8: owner approves an AI provider and what may be sent to it (LD-DOC-09). |
| LD-DOC-22-022 | 516 | يجب أن يكون المساعد مرتبطاً بمكتبة الأحكام والسوابق، بحيث يستطيع | عرض مصدر الحكم ورقمه وتاريخه والمحكمة التي أصدرته. | PARTIAL |  | | EXTERNAL_PROVIDER | Independent part DONE and verified in the library: similar-judgment suggestions with their reasons and a citation (source, date, number) - SimilarJudgmentApiTest, journey wp7. The assistant on top of it is not operational. Dependency: D8 for the assistant wording; the library function itself needs no provider. |
| LD-DOC-22-023 | 517 | يجب أن يكون المساعد مرتبطاً بمكتبة الأحكام والسوابق، بحيث يستطيع | ويجب ألا ينشئ المساعد أحكاماً أو مبادئ قضائية غير موجودة في المصادر، وألا يقدم استشهاداً قضائياً دون إظهار مرجعه. | PARTIAL |  | | EXTERNAL_PROVIDER | The rule is enforced by code and tested with a deterministic non-production provider (AssistantBoundariesTest, AssistantAskApiTest, journey wp10: refusal without sources, 404 for a case the account cannot see, drafts stay DRAFT, every query logged). Not verifiable on a real model. Dependency: D8: the real model's behaviour cannot be tested before a provider is approved. |
| LD-DOC-22-024 | 520 | يتيح المساعد إعداد مسودة أولية للمذكرات، بناءً على | بيانات القضية. | BLOCKED |  | | EXTERNAL_PROVIDER | Not operational by design: no provider approved, nothing leaves the system. The retrieval, citation, refusal and logging parts exist and are tested with a deterministic provider (see PARTIAL rows of section 22). Dependency: D8: owner approves an AI provider and what may be sent to it (LD-DOC-09). |
| LD-DOC-22-025 | 521 | يتيح المساعد إعداد مسودة أولية للمذكرات، بناءً على | الوقائع المسجلة. | BLOCKED |  | | EXTERNAL_PROVIDER | Not operational by design: no provider approved, nothing leaves the system. The retrieval, citation, refusal and logging parts exist and are tested with a deterministic provider (see PARTIAL rows of section 22). Dependency: D8: owner approves an AI provider and what may be sent to it (LD-DOC-09). |
| LD-DOC-22-026 | 522 | يتيح المساعد إعداد مسودة أولية للمذكرات، بناءً على | طلبات الخصوم. | BLOCKED |  | | EXTERNAL_PROVIDER | Not operational by design: no provider approved, nothing leaves the system. The retrieval, citation, refusal and logging parts exist and are tested with a deterministic provider (see PARTIAL rows of section 22). Dependency: D8: owner approves an AI provider and what may be sent to it (LD-DOC-09). |
| LD-DOC-22-027 | 523 | يتيح المساعد إعداد مسودة أولية للمذكرات، بناءً على | موقف الجهة. | BLOCKED |  | | EXTERNAL_PROVIDER | Not operational by design: no provider approved, nothing leaves the system. The retrieval, citation, refusal and logging parts exist and are tested with a deterministic provider (see PARTIAL rows of section 22). Dependency: D8: owner approves an AI provider and what may be sent to it (LD-DOC-09). |
| LD-DOC-22-028 | 524 | يتيح المساعد إعداد مسودة أولية للمذكرات، بناءً على | الدفوع وأوجه الدفاع. | BLOCKED |  | | EXTERNAL_PROVIDER | Not operational by design: no provider approved, nothing leaves the system. The retrieval, citation, refusal and logging parts exist and are tested with a deterministic provider (see PARTIAL rows of section 22). Dependency: D8: owner approves an AI provider and what may be sent to it (LD-DOC-09). |
| LD-DOC-22-029 | 525 | يتيح المساعد إعداد مسودة أولية للمذكرات، بناءً على | الأدلة والمستندات. | BLOCKED |  | | EXTERNAL_PROVIDER | Not operational by design: no provider approved, nothing leaves the system. The retrieval, citation, refusal and logging parts exist and are tested with a deterministic provider (see PARTIAL rows of section 22). Dependency: D8: owner approves an AI provider and what may be sent to it (LD-DOC-09). |
| LD-DOC-22-030 | 526 | يتيح المساعد إعداد مسودة أولية للمذكرات، بناءً على | النصوص القانونية المسجلة أو المصادر القانونية المعتمدة. | BLOCKED |  | | EXTERNAL_PROVIDER | Not operational by design: no provider approved, nothing leaves the system. The retrieval, citation, refusal and logging parts exist and are tested with a deterministic provider (see PARTIAL rows of section 22). Dependency: D8: owner approves an AI provider and what may be sent to it (LD-DOC-09). |
| LD-DOC-22-031 | 527 | يتيح المساعد إعداد مسودة أولية للمذكرات، بناءً على | الأحكام والسوابق القضائية ذات الصلة. | BLOCKED |  | | EXTERNAL_PROVIDER | Not operational by design: no provider approved, nothing leaves the system. The retrieval, citation, refusal and logging parts exist and are tested with a deterministic provider (see PARTIAL rows of section 22). Dependency: D8: owner approves an AI provider and what may be sent to it (LD-DOC-09). |
| LD-DOC-22-032 | 528 | يتيح المساعد إعداد مسودة أولية للمذكرات، بناءً على | كما يمكنه اقتراح ترتيب الدفوع، واستخراج نقاط القوة والضعف، والتنبيه إلى وجود تناقض أو نقص في المستندات. | BLOCKED |  | | EXTERNAL_PROVIDER | Not operational by design: no provider approved, nothing leaves the system. The retrieval, citation, refusal and logging parts exist and are tested with a deterministic provider (see PARTIAL rows of section 22). Dependency: D8: owner approves an AI provider and what may be sent to it (LD-DOC-09). |
| LD-DOC-22-033 | 531 | إتاحة وظائف للمقارنة بين | صحيفة الدعوى والمذكرات المقدمة. | BLOCKED |  | | EXTERNAL_PROVIDER | Not operational by design: no provider approved, nothing leaves the system. The retrieval, citation, refusal and logging parts exist and are tested with a deterministic provider (see PARTIAL rows of section 22). Dependency: D8: owner approves an AI provider and what may be sent to it (LD-DOC-09). |
| LD-DOC-22-034 | 532 | إتاحة وظائف للمقارنة بين | طلبات الخصوم ومنطوق الحكم. | BLOCKED |  | | EXTERNAL_PROVIDER | Not operational by design: no provider approved, nothing leaves the system. The retrieval, citation, refusal and logging parts exist and are tested with a deterministic provider (see PARTIAL rows of section 22). Dependency: D8: owner approves an AI provider and what may be sent to it (LD-DOC-09). |
| LD-DOC-22-035 | 533 | إتاحة وظائف للمقارنة بين | حكم المحكمة الكلية وحكم الاستئناف. | BLOCKED |  | | EXTERNAL_PROVIDER | Not operational by design: no provider approved, nothing leaves the system. The retrieval, citation, refusal and logging parts exist and are tested with a deterministic provider (see PARTIAL rows of section 22). Dependency: D8: owner approves an AI provider and what may be sent to it (LD-DOC-09). |
| LD-DOC-22-036 | 534 | إتاحة وظائف للمقارنة بين | المذكرات السابقة والمذكرات الجديدة. | BLOCKED |  | | EXTERNAL_PROVIDER | Not operational by design: no provider approved, nothing leaves the system. The retrieval, citation, refusal and logging parts exist and are tested with a deterministic provider (see PARTIAL rows of section 22). Dependency: D8: owner approves an AI provider and what may be sent to it (LD-DOC-09). |
| LD-DOC-22-037 | 535 | إتاحة وظائف للمقارنة بين | الوقائع الواردة في المستندات المختلفة. | BLOCKED |  | | EXTERNAL_PROVIDER | Not operational by design: no provider approved, nothing leaves the system. The retrieval, citation, refusal and logging parts exist and are tested with a deterministic provider (see PARTIAL rows of section 22). Dependency: D8: owner approves an AI provider and what may be sent to it (LD-DOC-09). |
| LD-DOC-22-038 | 536 | إتاحة وظائف للمقارنة بين | مع إظهار مواضع الاختلاف والتناقض ومصدر كل معلومة. | BLOCKED |  | | EXTERNAL_PROVIDER | Not operational by design: no provider approved, nothing leaves the system. The retrieval, citation, refusal and logging parts exist and are tested with a deterministic provider (see PARTIAL rows of section 22). Dependency: D8: owner approves an AI provider and what may be sent to it (LD-DOC-09). |
| LD-DOC-22-039 | 539 | يستطيع العضو طرح أسئلة مثل | ما آخر إجراء تم في القضية؟ | BLOCKED |  | | EXTERNAL_PROVIDER | Not operational by design: no provider approved, nothing leaves the system. The retrieval, citation, refusal and logging parts exist and are tested with a deterministic provider (see PARTIAL rows of section 22). Dependency: D8: owner approves an AI provider and what may be sent to it (LD-DOC-09). |
| LD-DOC-22-040 | 540 | يستطيع العضو طرح أسئلة مثل | ما الطلبات التي قضت المحكمة برفضها؟ | BLOCKED |  | | EXTERNAL_PROVIDER | Not operational by design: no provider approved, nothing leaves the system. The retrieval, citation, refusal and logging parts exist and are tested with a deterministic provider (see PARTIAL rows of section 22). Dependency: D8: owner approves an AI provider and what may be sent to it (LD-DOC-09). |
| LD-DOC-22-041 | 541 | يستطيع العضو طرح أسئلة مثل | ما الدفوع التي لم تفصل فيها المحكمة؟ | BLOCKED |  | | EXTERNAL_PROVIDER | Not operational by design: no provider approved, nothing leaves the system. The retrieval, citation, refusal and logging parts exist and are tested with a deterministic provider (see PARTIAL rows of section 22). Dependency: D8: owner approves an AI provider and what may be sent to it (LD-DOC-09). |
| LD-DOC-22-042 | 542 | يستطيع العضو طرح أسئلة مثل | ما المستندات الناقصة؟ | BLOCKED |  | | EXTERNAL_PROVIDER | Not operational by design: no provider approved, nothing leaves the system. The retrieval, citation, refusal and logging parts exist and are tested with a deterministic provider (see PARTIAL rows of section 22). Dependency: D8: owner approves an AI provider and what may be sent to it (LD-DOC-09). |
| LD-DOC-22-043 | 543 | يستطيع العضو طرح أسئلة مثل | ما مواعيد الطعون القائمة؟ | BLOCKED |  | | EXTERNAL_PROVIDER | Not operational by design: no provider approved, nothing leaves the system. The retrieval, citation, refusal and logging parts exist and are tested with a deterministic provider (see PARTIAL rows of section 22). Dependency: D8: owner approves an AI provider and what may be sent to it (LD-DOC-09). |
| LD-DOC-22-044 | 544 | يستطيع العضو طرح أسئلة مثل | ما الأحكام السابقة المشابهة؟ | BLOCKED |  | | EXTERNAL_PROVIDER | Not operational by design: no provider approved, nothing leaves the system. The retrieval, citation, refusal and logging parts exist and are tested with a deterministic provider (see PARTIAL rows of section 22). Dependency: D8: owner approves an AI provider and what may be sent to it (LD-DOC-09). |
| LD-DOC-22-045 | 545 | يستطيع العضو طرح أسئلة مثل | ما الإجراءات التي يجب اتخاذها بعد الجلسة الأخيرة؟ | BLOCKED |  | | EXTERNAL_PROVIDER | Not operational by design: no provider approved, nothing leaves the system. The retrieval, citation, refusal and logging parts exist and are tested with a deterministic provider (see PARTIAL rows of section 22). Dependency: D8: owner approves an AI provider and what may be sent to it (LD-DOC-09). |
| LD-DOC-22-046 | 546 | يستطيع العضو طرح أسئلة مثل | ويجب أن تكون الإجابات مستندة إلى البيانات والمستندات الموجودة في النظام، مع إظهار مصدر كل معلومة. | PARTIAL |  | | EXTERNAL_PROVIDER | The rule is enforced by code and tested with a deterministic non-production provider (AssistantBoundariesTest, AssistantAskApiTest, journey wp10: refusal without sources, 404 for a case the account cannot see, drafts stay DRAFT, every query logged). Not verifiable on a real model. Dependency: D8: the real model's behaviour cannot be tested before a provider is approved. |
| LD-DOC-22-047 | 548 | ضوابط استخدام المساعد الذكي | لا يجوز للمساعد اعتماد مذكرة أو رأي قانوني بصورة نهائية. | PARTIAL |  | | EXTERNAL_PROVIDER | The rule is enforced by code and tested with a deterministic non-production provider (AssistantBoundariesTest, AssistantAskApiTest, journey wp10: refusal without sources, 404 for a case the account cannot see, drafts stay DRAFT, every query logged). Not verifiable on a real model. Dependency: D8: the real model's behaviour cannot be tested before a provider is approved. |
| LD-DOC-22-048 | 549 | ضوابط استخدام المساعد الذكي | جميع المسودات والمقترحات تخضع لمراجعة العضو القانوني المختص. | PARTIAL |  | | EXTERNAL_PROVIDER | The rule is enforced by code and tested with a deterministic non-production provider (AssistantBoundariesTest, AssistantAskApiTest, journey wp10: refusal without sources, 404 for a case the account cannot see, drafts stay DRAFT, every query logged). Not verifiable on a real model. Dependency: D8: the real model's behaviour cannot be tested before a provider is approved. |
| LD-DOC-22-049 | 550 | ضوابط استخدام المساعد الذكي | يجب إظهار المستند ومصدر المعلومة ورقم الصفحة متى كان ذلك متاحاً. | PARTIAL |  | | EXTERNAL_PROVIDER | The rule is enforced by code and tested with a deterministic non-production provider (AssistantBoundariesTest, AssistantAskApiTest, journey wp10: refusal without sources, 404 for a case the account cannot see, drafts stay DRAFT, every query logged). Not verifiable on a real model. Dependency: D8: the real model's behaviour cannot be tested before a provider is approved. |
| LD-DOC-22-050 | 551 | ضوابط استخدام المساعد الذكي | يجب التمييز بين المعلومة الثابتة والاقتراح أو التحليل الذي أنتجه المساعد. | PARTIAL |  | | EXTERNAL_PROVIDER | The rule is enforced by code and tested with a deterministic non-production provider (AssistantBoundariesTest, AssistantAskApiTest, journey wp10: refusal without sources, 404 for a case the account cannot see, drafts stay DRAFT, every query logged). Not verifiable on a real model. Dependency: D8: the real model's behaviour cannot be tested before a provider is approved. |
| LD-DOC-22-051 | 552 | ضوابط استخدام المساعد الذكي | لا يجوز اختلاق نصوص قانونية أو أحكام قضائية أو أرقام قضايا. | PARTIAL |  | | EXTERNAL_PROVIDER | The rule is enforced by code and tested with a deterministic non-production provider (AssistantBoundariesTest, AssistantAskApiTest, journey wp10: refusal without sources, 404 for a case the account cannot see, drafts stay DRAFT, every query logged). Not verifiable on a real model. Dependency: D8: the real model's behaviour cannot be tested before a provider is approved. |
| LD-DOC-22-052 | 553 | ضوابط استخدام المساعد الذكي | عند عدم وجود مصدر كافٍ، يجب التصريح بعدم توافر المعلومات بدلاً من تقديم إجابة غير موثقة. | PARTIAL |  | | EXTERNAL_PROVIDER | The rule is enforced by code and tested with a deterministic non-production provider (AssistantBoundariesTest, AssistantAskApiTest, journey wp10: refusal without sources, 404 for a case the account cannot see, drafts stay DRAFT, every query logged). Not verifiable on a real model. Dependency: D8: the real model's behaviour cannot be tested before a provider is approved. |
| LD-DOC-22-053 | 554 | ضوابط استخدام المساعد الذكي | يجب احترام صلاحيات الوصول، فلا يستطيع المساعد الاطلاع على قضية أو مستند لا يملك المستخدم صلاحية الوصول إليه. | PARTIAL |  | | EXTERNAL_PROVIDER | The rule is enforced by code and tested with a deterministic non-production provider (AssistantBoundariesTest, AssistantAskApiTest, journey wp10: refusal without sources, 404 for a case the account cannot see, drafts stay DRAFT, every query logged). Not verifiable on a real model. Dependency: D8: the real model's behaviour cannot be tested before a provider is approved. |
| LD-DOC-22-054 | 555 | ضوابط استخدام المساعد الذكي | يجب تسجيل الاستفسارات والمخرجات والإجراءات المهمة وفق سياسة التدقيق والاحتفاظ المعتمدة. | PARTIAL |  | | EXTERNAL_PROVIDER | The rule is enforced by code and tested with a deterministic non-production provider (AssistantBoundariesTest, AssistantAskApiTest, journey wp10: refusal without sources, 404 for a case the account cannot see, drafts stay DRAFT, every query logged). Not verifiable on a real model. Dependency: D8: the real model's behaviour cannot be tested before a provider is approved. |
| LD-DOC-22-055 | 556 | ضوابط استخدام المساعد الذكي | يجب حماية سرية بيانات القضايا وعدم استخدامها في تدريب نماذج خارجية إلا وفق موافقات وضوابط صريحة ومعتمدة. | PARTIAL |  | | EXTERNAL_PROVIDER | The rule is enforced by code and tested with a deterministic non-production provider (AssistantBoundariesTest, AssistantAskApiTest, journey wp10: refusal without sources, 404 for a case the account cannot see, drafts stay DRAFT, every query logged). Not verifiable on a real model. Dependency: D8: the real model's behaviour cannot be tested before a provider is approved. |

<sub>عناوين المجموعات في المصدر: المرحلة الثالثة – تطوير متقدم (س488)؛ 1. تلخيص ملف القضية (س490)؛ يتيح المساعد: (س491)؛ 2. تحليل المستندات واستخراج البيانات (س500)؛ يتيح المساعد: (س501)؛ 3. البحث عن الأحكام والسوابق القضائية (س509)؛ يجب أن يكون المساعد مرتبطاً بمكتبة الأحكام والسوابق، بحيث يستطيع: (س510)؛ 4. المساعدة في إعداد المذكرات القانونية (س518)؛ يتيح المساعد إعداد مسودة أولية للمذكرات، بناءً على: (س519)؛ 5. المقارنة والتحليل (س529)؛ إتاحة وظائف للمقارنة بين: (س530)؛ 6. الإجابة عن الاستفسارات القانونية المرتبطة بالقضية (س537)؛ يستطيع العضو طرح أسئلة مثل: (س538)؛ 7. ضوابط استخدام المساعد الذكي (س547)</sub>

## LD-DOC-23 — الثالث والعشرون: إدارة التسويات والتنازلات والصلح

- **المرحلة**: المرحلة الثانية · **الأولوية**: متوسطة · **حزمة العمل**: WP9 · **سيناريوهات القبول المرتبطة**: —
- **التحديث (2026-11-09)**: نُفِّذ بحزمة العمل؛ الدليل: DOC-WP9 (journey wp9).

| المعرّف | السطر | المجموعة | المتطلب (نص المصدر) | IMPLEMENTED | VERIFIED_BY_DEVELOPER | QA_ACCEPTED | سبب العائق | ملاحظة (ما تبقى / لماذا لم يُتحقق) |
|---|---|---|---|---|---|---|---|---|
| LD-DOC-23-001 | 559 | إنشاء وحدة خاصة بالتسويات المقترحة أو المنفذة، تتضمن | الطرف مقدم عرض التسوية. | YES | T: SettlementApiTest (CI green cac8d73)<br>J: wp9 «the specialist proposes a settlement with its own amount; the manager approves it» (UAT f69bd76) [SETTLEMENT] | | — | — |
| LD-DOC-23-002 | 560 | إنشاء وحدة خاصة بالتسويات المقترحة أو المنفذة، تتضمن | قيمة التسوية. | YES | T: SettlementApiTest (CI green cac8d73)<br>J: wp9 «the specialist proposes a settlement with its own amount; the manager approves it» (UAT f69bd76) [SETTLEMENT] | | — | — |
| LD-DOC-23-003 | 561 | إنشاء وحدة خاصة بالتسويات المقترحة أو المنفذة، تتضمن | المبررات القانونية. | YES | T: SettlementApiTest (CI green cac8d73)<br>J: wp9 «the specialist proposes a settlement with its own amount; the manager approves it» (UAT f69bd76) [SETTLEMENT] | | — | — |
| LD-DOC-23-004 | 562 | إنشاء وحدة خاصة بالتسويات المقترحة أو المنفذة، تتضمن | الأثر المالي. | YES | T: SettlementApiTest (CI green cac8d73)<br>J: wp9 «the specialist proposes a settlement with its own amount; the manager approves it» (UAT f69bd76) [SETTLEMENT] | | — | — |
| LD-DOC-23-005 | 563 | إنشاء وحدة خاصة بالتسويات المقترحة أو المنفذة، تتضمن | الرأي القانوني. | YES | T: SettlementApiTest (CI green cac8d73)<br>J: wp9 «the specialist proposes a settlement with its own amount; the manager approves it» (UAT f69bd76) [SETTLEMENT] | | — | — |
| LD-DOC-23-006 | 564 | إنشاء وحدة خاصة بالتسويات المقترحة أو المنفذة، تتضمن | جهة الموافقة. | YES | T: SettlementApiTest (CI green cac8d73)<br>J: wp9 «the specialist proposes a settlement with its own amount; the manager approves it» (UAT f69bd76) [SETTLEMENT] | | — | — |
| LD-DOC-23-007 | 565 | إنشاء وحدة خاصة بالتسويات المقترحة أو المنفذة، تتضمن | قرار الموافقة أو الرفض. | YES | T: SettlementApiTest (CI green cac8d73)<br>J: wp9 «the specialist proposes a settlement with its own amount; the manager approves it» (UAT f69bd76) [SETTLEMENT] | | — | — |
| LD-DOC-23-008 | 566 | إنشاء وحدة خاصة بالتسويات المقترحة أو المنفذة، تتضمن | تاريخ إتمام التسوية. | YES | T: SettlementApiTest (CI green cac8d73)<br>J: wp9 «the specialist proposes a settlement with its own amount; the manager approves it» (UAT f69bd76) [SETTLEMENT] | | — | — |
| LD-DOC-23-009 | 567 | إنشاء وحدة خاصة بالتسويات المقترحة أو المنفذة، تتضمن | المستندات المؤيدة. | YES | T: SettlementApiTest (CI green cac8d73)<br>J: wp9 «the specialist proposes a settlement with its own amount; the manager approves it» (UAT f69bd76) [SETTLEMENT] | | — | — |
| LD-DOC-23-010 | 568 | إنشاء وحدة خاصة بالتسويات المقترحة أو المنفذة، تتضمن | ولا تعتبر القضية منتهية بمجرد تسجيل عرض التسوية، وإنما بعد استكمال الموافقات والإجراءات القانونية. | YES | T: GapClosureApiTest#closingACaseWithOpenItemsNeedsAnAcknowledgementAndSaysWhichItemsAreOpen (CI green cac8d73)<br>J: wp3 «the case is closed» (UAT 3937a50) [CLOSING_CHECK] | | — | — |

<sub>عناوين المجموعات في المصدر: إنشاء وحدة خاصة بالتسويات المقترحة أو المنفذة، تتضمن: (س558)</sub>

## LD-DOC-24 — الرابع والعشرون: قياس الأداء والمتابعة الإدارية

- **المرحلة**: المرحلة الثانية · **الأولوية**: متوسطة · **حزمة العمل**: WP8 · **سيناريوهات القبول المرتبطة**: —
- **التحديث (2026-11-09)**: نُفِّذ بحزمة العمل؛ الدليل: DOC-WP8 (اختبارات API؛ الرحلة على UAT جزئية).

| المعرّف | السطر | المجموعة | المتطلب (نص المصدر) | IMPLEMENTED | VERIFIED_BY_DEVELOPER | QA_ACCEPTED | سبب العائق | ملاحظة (ما تبقى / لماذا لم يُتحقق) |
|---|---|---|---|---|---|---|---|---|
| LD-DOC-24-001 | 570 | — | إنشاء لوحة أداء للأعضاء القانونيين تتضمن عدد القضايا والمهام والجلسات والمذكرات والإجراءات المنجزة والمتأخرة. | YES | T: WorkloadReportApiTest, ExpertReportApiTest (CI green cac8d73)<br>J: wp8 «the performance view is alphabetical, not ranked; it has no judgment-outcome column» (UAT 3937a50) [PERF_MEMBERS] | | — | — |
| LD-DOC-24-002 | 571 | — | كما يجب توفير مؤشرات لقياس تجاوب الإدارات المعنية مع طلبات الإفادة والمستندات، ومتوسط مدة الرد وعدد الطلبات المتأخرة وغير المكتملة. | YES | T: InfoRequestApiTest#theReportCountsRequestsRepliesDelaysAndIncompleteRepliesPerUnit (CI green cac8d73)<br>J: wp3 «the responsiveness report counts requests per unit» (UAT 3937a50)<br>J: wp8 «the units view reports the responsiveness of departments» (UAT 3937a50) [INFOREQ_REPORT] | | — | — |
| LD-DOC-24-003 | 572 | — | ويجب أن تكون المؤشرات موضوعية، وألا يعتمد تقييم العضو على نتيجة الحكم وحدها، لأن نتيجة الدعوى تتأثر بعوامل متعددة. | YES | T: WorkloadReportApiTest, ExpertReportApiTest (CI green cac8d73)<br>J: wp8 «the performance view is alphabetical, not ranked; it has no judgment-outcome column» (UAT 3937a50) [PERF_MEMBERS] | | — | — |

## LD-DOC-25 — الخامس والعشرون: سجل القرارات والتكليفات الإدارية

- **المرحلة**: المرحلة الثانية · **الأولوية**: متوسطة · **حزمة العمل**: WP9 · **سيناريوهات القبول المرتبطة**: —
- **التحديث (2026-11-09)**: نُفِّذ بحزمة العمل؛ الدليل: DOC-WP9 (journey wp9).

| المعرّف | السطر | المجموعة | المتطلب (نص المصدر) | IMPLEMENTED | VERIFIED_BY_DEVELOPER | QA_ACCEPTED | سبب العائق | ملاحظة (ما تبقى / لماذا لم يُتحقق) |
|---|---|---|---|---|---|---|---|---|
| LD-DOC-25-001 | 574 | — | إنشاء وحدة لتسجيل التكليفات والقرارات المتعلقة بالقضايا، مثل تكليف عضو بمتابعة قضية أو إعداد مذكرة أو حضور جلسة أو مراجعة حكم أو مخاطبة إدارة أو اتخاذ إجراءات التنفيذ. | YES | T: DirectiveApiTest (CI green cac8d73)<br>J: wp9 «the manager issues a directive with a system number; the addressee finds it in the register» (UAT f69bd76) [DIRECTIVES] | | — | — |
| LD-DOC-25-002 | 575 | — | مع تسجيل مصدر التكليف وتاريخه والمسؤول عنه والموعد المحدد وحالة التنفيذ. | YES | T: DirectiveApiTest (CI green cac8d73)<br>J: wp9 «the manager issues a directive with a system number; the addressee finds it in the register» (UAT f69bd76) [DIRECTIVES] | | — | — |

## LD-DOC-26 — السادس والعشرون: لوحة التحكم الرئيسية

- **المرحلة**: المرحلة الثانية · **الأولوية**: عالية · **حزمة العمل**: WP8 · **سيناريوهات القبول المرتبطة**: —
- **التحديث (2026-11-09)**: نُفِّذ بحزمة العمل؛ الدليل: DOC-WP8 (UAT: اللوحة والمؤشرات للمدير بعد إصلاح 33b2982).

| المعرّف | السطر | المجموعة | المتطلب (نص المصدر) | IMPLEMENTED | VERIFIED_BY_DEVELOPER | QA_ACCEPTED | سبب العائق | ملاحظة (ما تبقى / لماذا لم يُتحقق) |
|---|---|---|---|---|---|---|---|---|
| LD-DOC-26-001 | 578 | تطوير لوحة تحكم تعرض | إجمالي القضايا. | YES | T: IndicatorApiTest, DashboardReportApiTest (CI green cac8d73)<br>F: DashboardPage.test.tsx, Wp8Reports.test.tsx (CI green cac8d73)<br>J: wp8 «after, manager: every indicator figure equals the total of; sixteen indicator codes are reported» (UAT 3937a50) [DASHBOARD] | | — | — |
| LD-DOC-26-002 | 579 | تطوير لوحة تحكم تعرض | القضايا الجديدة والمتداولة والمنتهية. | YES | T: IndicatorApiTest, DashboardReportApiTest (CI green cac8d73)<br>F: DashboardPage.test.tsx, Wp8Reports.test.tsx (CI green cac8d73)<br>J: wp8 «after, manager: every indicator figure equals the total of; sixteen indicator codes are reported» (UAT 3937a50) [DASHBOARD] | | — | — |
| LD-DOC-26-003 | 580 | تطوير لوحة تحكم تعرض | القضايا المحجوزة للحكم. | YES | T: IndicatorApiTest, DashboardReportApiTest (CI green cac8d73)<br>F: DashboardPage.test.tsx, Wp8Reports.test.tsx (CI green cac8d73)<br>J: wp8 «after, manager: every indicator figure equals the total of; sixteen indicator codes are reported» (UAT 3937a50) [DASHBOARD] | | — | — |
| LD-DOC-26-004 | 581 | تطوير لوحة تحكم تعرض | الأحكام الصادرة. | YES | T: IndicatorApiTest, DashboardReportApiTest (CI green cac8d73)<br>F: DashboardPage.test.tsx, Wp8Reports.test.tsx (CI green cac8d73)<br>J: wp8 «after, manager: every indicator figure equals the total of; sixteen indicator codes are reported» (UAT 3937a50) [DASHBOARD] | | — | — |
| LD-DOC-26-005 | 582 | تطوير لوحة تحكم تعرض | الأحكام الجاري تنفيذها. | YES | T: IndicatorApiTest, DashboardReportApiTest (CI green cac8d73)<br>F: DashboardPage.test.tsx, Wp8Reports.test.tsx (CI green cac8d73)<br>J: wp8 «after, manager: every indicator figure equals the total of; sixteen indicator codes are reported» (UAT 3937a50) [DASHBOARD] | | — | — |
| LD-DOC-26-006 | 583 | تطوير لوحة تحكم تعرض | الجلسات القادمة. | YES | T: IndicatorApiTest, DashboardReportApiTest (CI green cac8d73)<br>F: DashboardPage.test.tsx, Wp8Reports.test.tsx (CI green cac8d73)<br>J: wp8 «after, manager: every indicator figure equals the total of; sixteen indicator codes are reported» (UAT 3937a50) [DASHBOARD] | | — | — |
| LD-DOC-26-007 | 584 | تطوير لوحة تحكم تعرض | جلسات الخبراء. | YES | T: IndicatorApiTest, DashboardReportApiTest (CI green cac8d73)<br>F: DashboardPage.test.tsx, Wp8Reports.test.tsx (CI green cac8d73)<br>J: wp8 «after, manager: every indicator figure equals the total of; sixteen indicator codes are reported» (UAT 3937a50) [DASHBOARD] | | — | — |
| LD-DOC-26-008 | 585 | تطوير لوحة تحكم تعرض | مواعيد الطعون. | YES | T: IndicatorApiTest, DashboardReportApiTest (CI green cac8d73)<br>F: DashboardPage.test.tsx, Wp8Reports.test.tsx (CI green cac8d73)<br>J: wp8 «after, manager: every indicator figure equals the total of; sixteen indicator codes are reported» (UAT 3937a50) [DASHBOARD] | | — | — |
| LD-DOC-26-009 | 586 | تطوير لوحة تحكم تعرض | المهام المتأخرة. | YES | T: IndicatorApiTest, DashboardReportApiTest (CI green cac8d73)<br>F: DashboardPage.test.tsx, Wp8Reports.test.tsx (CI green cac8d73)<br>J: wp8 «after, manager: every indicator figure equals the total of; sixteen indicator codes are reported» (UAT 3937a50) [DASHBOARD] | | — | — |
| LD-DOC-26-010 | 587 | تطوير لوحة تحكم تعرض | القضايا حسب القطاع والإدارة والقسم. | PARTIAL |  | | DATA_NOT_PROVIDED | Mechanism done and verified (cases by unit move with the cases). The real sector/department/section data and the unit of each real case are not provided. Dependency: Owner supplies the organisation structure and assigns existing cases to units. |
| LD-DOC-26-011 | 588 | تطوير لوحة تحكم تعرض | القضايا حسب العضو القانوني. | YES | T: IndicatorApiTest, DashboardReportApiTest (CI green cac8d73)<br>F: DashboardPage.test.tsx, Wp8Reports.test.tsx (CI green cac8d73)<br>J: wp8 «after, manager: every indicator figure equals the total of; sixteen indicator codes are reported» (UAT 3937a50) [DASHBOARD] | | — | — |
| LD-DOC-26-012 | 589 | تطوير لوحة تحكم تعرض | القضايا المرتفعة المخاطر. | YES | T: IndicatorApiTest, DashboardReportApiTest (CI green cac8d73)<br>F: DashboardPage.test.tsx, Wp8Reports.test.tsx (CI green cac8d73)<br>J: wp8 «after, manager: every indicator figure equals the total of; sixteen indicator codes are reported» (UAT 3937a50) [DASHBOARD] | | — | — |
| LD-DOC-26-013 | 590 | تطوير لوحة تحكم تعرض | طلبات الإدارات المتأخرة. | YES | T: IndicatorApiTest, DashboardReportApiTest (CI green cac8d73)<br>F: DashboardPage.test.tsx, Wp8Reports.test.tsx (CI green cac8d73)<br>J: wp8 «after, manager: every indicator figure equals the total of; sixteen indicator codes are reported» (UAT 3937a50) [DASHBOARD] | | — | — |
| LD-DOC-26-014 | 591 | تطوير لوحة تحكم تعرض | القضايا المرتبطة. | YES | T: IndicatorApiTest, DashboardReportApiTest (CI green cac8d73)<br>F: DashboardPage.test.tsx, Wp8Reports.test.tsx (CI green cac8d73)<br>J: wp8 «after, manager: every indicator figure equals the total of; sixteen indicator codes are reported» (UAT 3937a50) [DASHBOARD] | | — | — |
| LD-DOC-26-015 | 592 | تطوير لوحة تحكم تعرض | ويجب أن تكون المؤشرات قابلة للنقر للانتقال إلى تفاصيل البيانات. | YES | T: IndicatorApiTest, DashboardReportApiTest (CI green cac8d73)<br>F: DashboardPage.test.tsx, Wp8Reports.test.tsx (CI green cac8d73)<br>J: wp8 «after, manager: every indicator figure equals the total of; sixteen indicator codes are reported» (UAT 3937a50) [DASHBOARD] | | — | — |

<sub>عناوين المجموعات في المصدر: تطوير لوحة تحكم تعرض: (س577)</sub>

## LD-DOC-27 — السابع والعشرون: التقارير والإحصائيات

- **المرحلة**: المرحلة الثانية · **الأولوية**: عالية · **حزمة العمل**: WP8 · **سيناريوهات القبول المرتبطة**: —
- **التحديث (2026-11-09)**: نُفِّذ بحزمة العمل؛ الدليل: DOC-WP8 (اختبارات API والتصدير).

| المعرّف | السطر | المجموعة | المتطلب (نص المصدر) | IMPLEMENTED | VERIFIED_BY_DEVELOPER | QA_ACCEPTED | سبب العائق | ملاحظة (ما تبقى / لماذا لم يُتحقق) |
|---|---|---|---|---|---|---|---|---|
| LD-DOC-27-001 | 595 | توفير تقارير قابلة للتصفية والتصدير إلى Excel وPDF، وتشمل | القضايا حسب النوع والمحكمة ودرجة التقاضي. | YES | T: CaseInventoryReportApiTest, HearingReportApiTest, JudgmentReportApiTest, ExpertReportApiTest (CI green cac8d73)<br>F: CaseInventoryPage.test.tsx (CI green cac8d73)<br>J: wp8 «cases by CASE_TYPE add up to the same total as open/closed; cases by COURT add up to the same total as open/closed» (UAT 3937a50) [REPORT_INVENTORY] | | — | — |
| LD-DOC-27-002 | 596 | توفير تقارير قابلة للتصفية والتصدير إلى Excel وPDF، وتشمل | القضايا حسب القطاع والإدارة والقسم. | YES | T: CaseInventoryReportApiTest, HearingReportApiTest, JudgmentReportApiTest, ExpertReportApiTest (CI green cac8d73)<br>F: CaseInventoryPage.test.tsx (CI green cac8d73)<br>J: wp8 «cases by CASE_TYPE add up to the same total as open/closed; cases by COURT add up to the same total as open/closed» (UAT 3937a50) [REPORT_INVENTORY] | | — | — |
| LD-DOC-27-003 | 597 | توفير تقارير قابلة للتصفية والتصدير إلى Excel وPDF، وتشمل | القضايا حسب العضو القانوني. | YES | T: CaseInventoryReportApiTest, HearingReportApiTest, JudgmentReportApiTest, ExpertReportApiTest (CI green cac8d73)<br>F: CaseInventoryPage.test.tsx (CI green cac8d73)<br>J: wp8 «cases by CASE_TYPE add up to the same total as open/closed; cases by COURT add up to the same total as open/closed» (UAT 3937a50) [REPORT_INVENTORY] | | — | — |
| LD-DOC-27-004 | 598 | توفير تقارير قابلة للتصفية والتصدير إلى Excel وPDF، وتشمل | القضايا المفتوحة والمغلقة. | YES | T: CaseInventoryReportApiTest, HearingReportApiTest, JudgmentReportApiTest, ExpertReportApiTest (CI green cac8d73)<br>F: CaseInventoryPage.test.tsx (CI green cac8d73)<br>J: wp8 «cases by CASE_TYPE add up to the same total as open/closed; cases by COURT add up to the same total as open/closed» (UAT 3937a50) [REPORT_INVENTORY] | | — | — |
| LD-DOC-27-005 | 599 | توفير تقارير قابلة للتصفية والتصدير إلى Excel وPDF، وتشمل | الأحكام ونتائجها. | YES | T: FamilyApiTest, ReportExportApiTest, ExportFormatsApiTest (CI green cac8d73)<br>F: Wp8Reports.test.tsx (CI green cac8d73)<br>J: wp8 «seventeen report families; every family answers with rows as wide as its columns» (UAT 3937a50) [REPORTS] | | — | — |
| LD-DOC-27-006 | 600 | توفير تقارير قابلة للتصفية والتصدير إلى Excel وPDF، وتشمل | نتائج كل طلب من طلبات الدعوى. | YES | T: FamilyApiTest, ReportExportApiTest, ExportFormatsApiTest (CI green cac8d73)<br>F: Wp8Reports.test.tsx (CI green cac8d73)<br>J: wp8 «seventeen report families; every family answers with rows as wide as its columns» (UAT 3937a50) [REPORTS] | | — | — |
| LD-DOC-27-007 | 601 | توفير تقارير قابلة للتصفية والتصدير إلى Excel وPDF، وتشمل | نتائج الدفوع وأوجه الدفاع. | YES | T: FamilyApiTest, ReportExportApiTest, ExportFormatsApiTest (CI green cac8d73)<br>F: Wp8Reports.test.tsx (CI green cac8d73)<br>J: wp8 «seventeen report families; every family answers with rows as wide as its columns» (UAT 3937a50) [REPORTS] | | — | — |
| LD-DOC-27-008 | 602 | توفير تقارير قابلة للتصفية والتصدير إلى Excel وPDF، وتشمل | القضايا المرتبطة. | YES | T: FamilyApiTest, ReportExportApiTest, ExportFormatsApiTest (CI green cac8d73)<br>F: Wp8Reports.test.tsx (CI green cac8d73)<br>J: wp8 «seventeen report families; every family answers with rows as wide as its columns» (UAT 3937a50) [REPORTS] | | — | — |
| LD-DOC-27-009 | 603 | توفير تقارير قابلة للتصفية والتصدير إلى Excel وPDF، وتشمل | الجلسات والتأجيلات وأسبابها. | YES | T: FamilyApiTest, ReportExportApiTest, ExportFormatsApiTest (CI green cac8d73)<br>F: Wp8Reports.test.tsx (CI green cac8d73)<br>J: wp8 «seventeen report families; every family answers with rows as wide as its columns» (UAT 3937a50) [REPORTS] | | — | — |
| LD-DOC-27-010 | 604 | توفير تقارير قابلة للتصفية والتصدير إلى Excel وPDF، وتشمل | جلسات الخبراء. | YES | T: FamilyApiTest, ReportExportApiTest, ExportFormatsApiTest (CI green cac8d73)<br>F: Wp8Reports.test.tsx (CI green cac8d73)<br>J: wp8 «seventeen report families; every family answers with rows as wide as its columns» (UAT 3937a50) [REPORTS] | | — | — |
| LD-DOC-27-011 | 605 | توفير تقارير قابلة للتصفية والتصدير إلى Excel وPDF، وتشمل | المواعيد القانونية والطعون. | YES | T: FamilyApiTest, ReportExportApiTest, ExportFormatsApiTest (CI green cac8d73)<br>F: Wp8Reports.test.tsx (CI green cac8d73)<br>J: wp8 «seventeen report families; every family answers with rows as wide as its columns» (UAT 3937a50) [REPORTS] | | — | — |
| LD-DOC-27-012 | 606 | توفير تقارير قابلة للتصفية والتصدير إلى Excel وPDF، وتشمل | تنفيذ الأحكام. | YES | T: FamilyApiTest, ReportExportApiTest, ExportFormatsApiTest (CI green cac8d73)<br>F: Wp8Reports.test.tsx (CI green cac8d73)<br>J: wp8 «seventeen report families; every family answers with rows as wide as its columns» (UAT 3937a50) [REPORTS] | | — | — |
| LD-DOC-27-013 | 607 | توفير تقارير قابلة للتصفية والتصدير إلى Excel وPDF، وتشمل | الأدلة والمستندات الناقصة. | YES | T: FamilyApiTest, ReportExportApiTest, ExportFormatsApiTest (CI green cac8d73)<br>F: Wp8Reports.test.tsx (CI green cac8d73)<br>J: wp8 «seventeen report families; every family answers with rows as wide as its columns» (UAT 3937a50) [REPORTS] | | — | — |
| LD-DOC-27-014 | 608 | توفير تقارير قابلة للتصفية والتصدير إلى Excel وPDF، وتشمل | طلبات الإدارات المعنية والردود المتأخرة. | YES | T: FamilyApiTest, ReportExportApiTest, ExportFormatsApiTest (CI green cac8d73)<br>F: Wp8Reports.test.tsx (CI green cac8d73)<br>J: wp8 «seventeen report families; every family answers with rows as wide as its columns» (UAT 3937a50) [REPORTS] | | — | — |
| LD-DOC-27-015 | 609 | توفير تقارير قابلة للتصفية والتصدير إلى Excel وPDF، وتشمل | تقييم المخاطر القانونية. | YES | T: FamilyApiTest, ReportExportApiTest, ExportFormatsApiTest (CI green cac8d73)<br>F: Wp8Reports.test.tsx (CI green cac8d73)<br>J: wp8 «seventeen report families; every family answers with rows as wide as its columns» (UAT 3937a50) [REPORTS] | | — | — |
| LD-DOC-27-016 | 610 | توفير تقارير قابلة للتصفية والتصدير إلى Excel وPDF، وتشمل | أداء الأعضاء والإدارات. | YES | T: FamilyApiTest, ReportExportApiTest, ExportFormatsApiTest (CI green cac8d73)<br>F: Wp8Reports.test.tsx (CI green cac8d73)<br>J: wp8 «seventeen report families; every family answers with rows as wide as its columns» (UAT 3937a50) [REPORTS] | | — | — |
| LD-DOC-27-017 | 611 | توفير تقارير قابلة للتصفية والتصدير إلى Excel وPDF، وتشمل | الأحكام والسوابق القضائية حسب الموضوع والمحكمة. | YES | T: FamilyApiTest, ReportExportApiTest, ExportFormatsApiTest (CI green cac8d73)<br>F: Wp8Reports.test.tsx (CI green cac8d73)<br>J: wp8 «seventeen report families; every family answers with rows as wide as its columns» (UAT 3937a50) [REPORTS] | | — | — |

<sub>عناوين المجموعات في المصدر: توفير تقارير قابلة للتصفية والتصدير إلى Excel وPDF، وتشمل: (س594)</sub>

## LD-DOC-28 — الثامن والعشرون: البحث الذكي والمتقدم

- **المرحلة**: المرحلة الثانية · **الأولوية**: عالية · **حزمة العمل**: WP9 · **سيناريوهات القبول المرتبطة**: —
- **التحديث (2026-11-09)**: نُفِّذ بحزمة العمل؛ الدليل: DOC-WP9 (journey wp9).

| المعرّف | السطر | المجموعة | المتطلب (نص المصدر) | IMPLEMENTED | VERIFIED_BY_DEVELOPER | QA_ACCEPTED | سبب العائق | ملاحظة (ما تبقى / لماذا لم يُتحقق) |
|---|---|---|---|---|---|---|---|---|
| LD-DOC-28-001 | 614 | إنشاء محرك بحث موحد يشمل | أرقام القضايا. | YES | T: UnifiedSearchApiTest, StoredSearchDocumentsTest (CI green cac8d73)<br>F: SearchPage.test.tsx (CI green cac8d73)<br>J: wp9 «the member finds the case, its party, the settlement and the; spelling variants find the same case» (UAT f69bd76) [UNIFIED_SEARCH] | | — | — |
| LD-DOC-28-002 | 615 | إنشاء محرك بحث موحد يشمل | أسماء الخصوم. | YES | T: UnifiedSearchApiTest, StoredSearchDocumentsTest (CI green cac8d73)<br>F: SearchPage.test.tsx (CI green cac8d73)<br>J: wp9 «the member finds the case, its party, the settlement and the; spelling variants find the same case» (UAT f69bd76) [UNIFIED_SEARCH] | | — | — |
| LD-DOC-28-003 | 616 | إنشاء محرك بحث موحد يشمل | موضوع الدعوى. | YES | T: UnifiedSearchApiTest, StoredSearchDocumentsTest (CI green cac8d73)<br>F: SearchPage.test.tsx (CI green cac8d73)<br>J: wp9 «the member finds the case, its party, the settlement and the; spelling variants find the same case» (UAT f69bd76) [UNIFIED_SEARCH] | | — | — |
| LD-DOC-28-004 | 617 | إنشاء محرك بحث موحد يشمل | نصوص الطلبات. | YES | T: UnifiedSearchApiTest, StoredSearchDocumentsTest (CI green cac8d73)<br>F: SearchPage.test.tsx (CI green cac8d73)<br>J: wp9 «the member finds the case, its party, the settlement and the; spelling variants find the same case» (UAT f69bd76) [UNIFIED_SEARCH] | | — | — |
| LD-DOC-28-005 | 618 | إنشاء محرك بحث موحد يشمل | الدفوع القانونية. | YES | T: UnifiedSearchApiTest, StoredSearchDocumentsTest (CI green cac8d73)<br>F: SearchPage.test.tsx (CI green cac8d73)<br>J: wp9 «the member finds the case, its party, the settlement and the; spelling variants find the same case» (UAT f69bd76) [UNIFIED_SEARCH] | | — | — |
| LD-DOC-28-006 | 619 | إنشاء محرك بحث موحد يشمل | منطوق الأحكام. | YES | T: UnifiedSearchApiTest, StoredSearchDocumentsTest (CI green cac8d73)<br>F: SearchPage.test.tsx (CI green cac8d73)<br>J: wp9 «the member finds the case, its party, the settlement and the; spelling variants find the same case» (UAT f69bd76) [UNIFIED_SEARCH] | | — | — |
| LD-DOC-28-007 | 620 | إنشاء محرك بحث موحد يشمل | المبادئ القضائية. | YES | T: UnifiedSearchApiTest, StoredSearchDocumentsTest (CI green cac8d73)<br>F: SearchPage.test.tsx (CI green cac8d73)<br>J: wp9 «the member finds the case, its party, the settlement and the; spelling variants find the same case» (UAT f69bd76) [UNIFIED_SEARCH] | | — | — |
| LD-DOC-28-008 | 621 | إنشاء محرك بحث موحد يشمل | أسماء الإدارات والأقسام. | YES | T: UnifiedSearchApiTest, StoredSearchDocumentsTest (CI green cac8d73)<br>F: SearchPage.test.tsx (CI green cac8d73)<br>J: wp9 «the member finds the case, its party, the settlement and the; spelling variants find the same case» (UAT f69bd76) [UNIFIED_SEARCH] | | — | — |
| LD-DOC-28-009 | 622 | إنشاء محرك بحث موحد يشمل | الأعضاء القانونيين. | YES | T: UnifiedSearchApiTest, StoredSearchDocumentsTest (CI green cac8d73)<br>F: SearchPage.test.tsx (CI green cac8d73)<br>J: wp9 «the member finds the case, its party, the settlement and the; spelling variants find the same case» (UAT f69bd76) [UNIFIED_SEARCH] | | — | — |
| LD-DOC-28-010 | 623 | إنشاء محرك بحث موحد يشمل | المذكرات والمستندات. | YES | T: OcrTextSearchTest, UnifiedSearchApiTest (CI green cac8d73)<br>E: ocr-fix-after.md (DOC-PERF ocr-fix run at code version 1740c0d) [OCR_SEARCH] | | — | — |
| LD-DOC-28-011 | 624 | إنشاء محرك بحث موحد يشمل | محاضر الجلسات. | YES | T: UnifiedSearchApiTest, StoredSearchDocumentsTest (CI green cac8d73)<br>F: SearchPage.test.tsx (CI green cac8d73)<br>J: wp9 «the member finds the case, its party, the settlement and the; spelling variants find the same case» (UAT f69bd76) [UNIFIED_SEARCH] | | — | — |
| LD-DOC-28-012 | 625 | إنشاء محرك بحث موحد يشمل | القضايا المرتبطة. | YES | T: UnifiedSearchApiTest, StoredSearchDocumentsTest (CI green cac8d73)<br>F: SearchPage.test.tsx (CI green cac8d73)<br>J: wp9 «the member finds the case, its party, the settlement and the; spelling variants find the same case» (UAT f69bd76) [UNIFIED_SEARCH] | | — | — |
| LD-DOC-28-013 | 626 | إنشاء محرك بحث موحد يشمل | مع دعم البحث داخل المستندات بعد تطبيق OCR، وتوفير فلاتر متقدمة. | YES | T: OcrTextSearchTest, UnifiedSearchApiTest (CI green cac8d73)<br>E: ocr-fix-after.md (DOC-PERF ocr-fix run at code version 1740c0d) [OCR_SEARCH] | | — | — |

<sub>عناوين المجموعات في المصدر: إنشاء محرك بحث موحد يشمل: (س613)</sub>

## LD-DOC-29 — التاسع والعشرون: الأرشفة والنماذج القانونية الجاهزة

- **المرحلة**: المرحلة الثانية · **الأولوية**: متوسطة · **حزمة العمل**: WP9 · **سيناريوهات القبول المرتبطة**: —
- **التحديث (2026-11-09)**: نُفِّذ بحزمة العمل؛ الدليل: DOC-WP9 (journey wp9).

| المعرّف | السطر | المجموعة | المتطلب (نص المصدر) | IMPLEMENTED | VERIFIED_BY_DEVELOPER | QA_ACCEPTED | سبب العائق | ملاحظة (ما تبقى / لماذا لم يُتحقق) |
|---|---|---|---|---|---|---|---|---|
| LD-DOC-29-001 | 629 | إنشاء مكتبة نماذج معتمدة تشمل | مذكرات الدفاع. | YES | T: TemplateApiTest (CI green cac8d73)<br>J: wp9 «the template kinds are those of the requirements text; the version is reviewed then approved» (UAT f69bd76) [TEMPLATES] | | — | — |
| LD-DOC-29-002 | 630 | إنشاء مكتبة نماذج معتمدة تشمل | صحف الدعاوى والاستئناف والتمييز. | YES | T: TemplateApiTest (CI green cac8d73)<br>J: wp9 «the template kinds are those of the requirements text; the version is reviewed then approved» (UAT f69bd76) [TEMPLATES] | | — | — |
| LD-DOC-29-003 | 631 | إنشاء مكتبة نماذج معتمدة تشمل | طلبات إيداع المستندات. | YES | T: TemplateApiTest (CI green cac8d73)<br>J: wp9 «the template kinds are those of the requirements text; the version is reviewed then approved» (UAT f69bd76) [TEMPLATES] | | — | — |
| LD-DOC-29-004 | 632 | إنشاء مكتبة نماذج معتمدة تشمل | طلبات التأجيل. | YES | T: TemplateApiTest (CI green cac8d73)<br>J: wp9 «the template kinds are those of the requirements text; the version is reviewed then approved» (UAT f69bd76) [TEMPLATES] | | — | — |
| LD-DOC-29-005 | 633 | إنشاء مكتبة نماذج معتمدة تشمل | مذكرات التعقيب. | YES | T: TemplateApiTest (CI green cac8d73)<br>J: wp9 «the template kinds are those of the requirements text; the version is reviewed then approved» (UAT f69bd76) [TEMPLATES] | | — | — |
| LD-DOC-29-006 | 634 | إنشاء مكتبة نماذج معتمدة تشمل | كتب طلب الإفادة. | YES | T: TemplateApiTest (CI green cac8d73)<br>J: wp9 «the template kinds are those of the requirements text; the version is reviewed then approved» (UAT f69bd76) [TEMPLATES] | | — | — |
| LD-DOC-29-007 | 635 | إنشاء مكتبة نماذج معتمدة تشمل | كتب متابعة تنفيذ الأحكام. | YES | T: TemplateApiTest (CI green cac8d73)<br>J: wp9 «the template kinds are those of the requirements text; the version is reviewed then approved» (UAT f69bd76) [TEMPLATES] | | — | — |
| LD-DOC-29-008 | 636 | إنشاء مكتبة نماذج معتمدة تشمل | ملخصات القضايا. | YES | T: TemplateApiTest (CI green cac8d73)<br>J: wp9 «the template kinds are those of the requirements text; the version is reviewed then approved» (UAT f69bd76) [TEMPLATES] | | — | — |
| LD-DOC-29-009 | 637 | إنشاء مكتبة نماذج معتمدة تشمل | تقارير الموقف القانوني. | YES | T: TemplateApiTest (CI green cac8d73)<br>J: wp9 «the template kinds are those of the requirements text; the version is reviewed then approved» (UAT f69bd76) [TEMPLATES] | | — | — |
| LD-DOC-29-010 | 638 | إنشاء مكتبة نماذج معتمدة تشمل | مع إمكانية تعبئة البيانات الأساسية من ملف القضية تلقائياً، على أن تتم مراجعة المستند واعتماده من العضو المختص. | YES | T: TemplateApiTest (CI green cac8d73)<br>J: wp9 «the template kinds are those of the requirements text; the version is reviewed then approved» (UAT f69bd76) [TEMPLATES] | | — | — |

<sub>عناوين المجموعات في المصدر: إنشاء مكتبة نماذج معتمدة تشمل: (س628)</sub>

## LD-DOC-30 — الثلاثون: الصلاحيات ومستويات المستخدمين

- **المرحلة**: المرحلة الأولى · **الأولوية**: أعلى · **حزمة العمل**: WP1 · **سيناريوهات القبول المرتبطة**: S15
- **التحديث (2026-11-09)**: نُفِّذ بحزمة العمل؛ الدليل: DOC-WP1 (journey wp1-access: S15).

| المعرّف | السطر | المجموعة | المتطلب (نص المصدر) | IMPLEMENTED | VERIFIED_BY_DEVELOPER | QA_ACCEPTED | سبب العائق | ملاحظة (ما تبقى / لماذا لم يُتحقق) |
|---|---|---|---|---|---|---|---|---|
| LD-DOC-30-001 | 641 | إنشاء نظام صلاحيات مرن للمستخدمين، مثل | مدير النظام. | YES | T: DefaultRolePermissionsTest, RoleAdministrationTest, PermissionContractTest (CI green cac8d73)<br>F: RolesAudit.test.tsx (CI green cac8d73) [ROLES] | | — | — |
| LD-DOC-30-002 | 642 | إنشاء نظام صلاحيات مرن للمستخدمين، مثل | مدير الإدارة القانونية. | YES | T: DefaultRolePermissionsTest, RoleAdministrationTest, PermissionContractTest (CI green cac8d73)<br>F: RolesAudit.test.tsx (CI green cac8d73) [ROLES] | | — | — |
| LD-DOC-30-003 | 643 | إنشاء نظام صلاحيات مرن للمستخدمين، مثل | رئيس القسم. | YES | T: DefaultRolePermissionsTest, RoleAdministrationTest, PermissionContractTest (CI green cac8d73)<br>F: RolesAudit.test.tsx (CI green cac8d73) [ROLES] | | — | — |
| LD-DOC-30-004 | 644 | إنشاء نظام صلاحيات مرن للمستخدمين، مثل | العضو القانوني. | YES | T: DefaultRolePermissionsTest, RoleAdministrationTest, PermissionContractTest (CI green cac8d73)<br>F: RolesAudit.test.tsx (CI green cac8d73) [ROLES] | | — | — |
| LD-DOC-30-005 | 645 | إنشاء نظام صلاحيات مرن للمستخدمين، مثل | مدخل البيانات. | YES | T: DefaultRolePermissionsTest, RoleAdministrationTest, PermissionContractTest (CI green cac8d73)<br>F: RolesAudit.test.tsx (CI green cac8d73) [ROLES] | | — | — |
| LD-DOC-30-006 | 646 | إنشاء نظام صلاحيات مرن للمستخدمين، مثل | مسؤول الأرشفة. | YES | T: DefaultRolePermissionsTest, RoleAdministrationTest, PermissionContractTest (CI green cac8d73)<br>F: RolesAudit.test.tsx (CI green cac8d73) [ROLES] | | — | — |
| LD-DOC-30-007 | 647 | إنشاء نظام صلاحيات مرن للمستخدمين، مثل | مستخدم للاطلاع فقط. | YES | T: DefaultRolePermissionsTest, RoleAdministrationTest, PermissionContractTest (CI green cac8d73)<br>F: RolesAudit.test.tsx (CI green cac8d73) [ROLES] | | — | — |
| LD-DOC-30-008 | 648 | إنشاء نظام صلاحيات مرن للمستخدمين، مثل | وتحديد صلاحيات إنشاء القضايا وتعديلها والاطلاع على المستندات وإضافة الجلسات والأحكام والدفوع والطلبات واعتماد المذكرات وتوزيع المهام واستخراج التقارير. | YES | T: CaseVisibilityApiTest, RolePermissionsTakeEffectTest, SessionRevocationTest (CI green cac8d73)<br>J: wp1a «manager places the staff account in the legal section; the case belongs to the specialist's unit» (UAT 8d7c847) [ACCESS_SCOPE] | | — | — |
| LD-DOC-30-009 | 649 | إنشاء نظام صلاحيات مرن للمستخدمين، مثل | ويجب أن تكون الصلاحيات قابلة للتخصيص بحسب القطاع والإدارة والقسم وطبيعة العمل. | YES | T: CaseVisibilityApiTest, RolePermissionsTakeEffectTest, SessionRevocationTest (CI green cac8d73)<br>J: wp1a «manager places the staff account in the legal section; the case belongs to the specialist's unit» (UAT 8d7c847) [ACCESS_SCOPE] | | — | — |

<sub>عناوين المجموعات في المصدر: إنشاء نظام صلاحيات مرن للمستخدمين، مثل: (س640)</sub>

## LD-DOC-31 — الحادي والثلاثون: سجل التدقيق والعمليات

- **المرحلة**: المرحلة الأولى · **الأولوية**: أعلى · **حزمة العمل**: WP1 · **سيناريوهات القبول المرتبطة**: S15
- **التحديث (2026-11-09)**: نُفِّذ بحزمة العمل؛ الدليل: DOC-WP1 + أحداث التدقيق في كل حزمة.

| المعرّف | السطر | المجموعة | المتطلب (نص المصدر) | IMPLEMENTED | VERIFIED_BY_DEVELOPER | QA_ACCEPTED | سبب العائق | ملاحظة (ما تبقى / لماذا لم يُتحقق) |
|---|---|---|---|---|---|---|---|---|
| LD-DOC-31-001 | 651 | — | تسجيل جميع العمليات التي تتم على النظام، بما في ذلك إنشاء القضايا وتعديلها وإضافة الطلبات والدفوع والجلسات والأحكام والمستندات وتغيير المسؤولين والمواعيد. | YES | T: AuditTrailTest, AuditViewTest, AuditTrailTest#theDatabaseRefusesToChangeOrDeleteAuditEvents (CI green cac8d73)<br>J: wp1a «the audit trail records the grant» (UAT 8d7c847) [AUDIT] | | — | — |
| LD-DOC-31-002 | 653 | ويجب أن يتضمن السجل | اسم المستخدم. | YES | T: AuditTrailTest, AuditViewTest, AuditTrailTest#theDatabaseRefusesToChangeOrDeleteAuditEvents (CI green cac8d73)<br>J: wp1a «the audit trail records the grant» (UAT 8d7c847) [AUDIT] | | — | — |
| LD-DOC-31-003 | 654 | ويجب أن يتضمن السجل | نوع العملية. | YES | T: AuditTrailTest, AuditViewTest, AuditTrailTest#theDatabaseRefusesToChangeOrDeleteAuditEvents (CI green cac8d73)<br>J: wp1a «the audit trail records the grant» (UAT 8d7c847) [AUDIT] | | — | — |
| LD-DOC-31-004 | 655 | ويجب أن يتضمن السجل | تاريخ ووقت العملية. | YES | T: AuditTrailTest, AuditViewTest, AuditTrailTest#theDatabaseRefusesToChangeOrDeleteAuditEvents (CI green cac8d73)<br>J: wp1a «the audit trail records the grant» (UAT 8d7c847) [AUDIT] | | — | — |
| LD-DOC-31-005 | 656 | ويجب أن يتضمن السجل | البيانات السابقة. | YES | T: AuditTrailTest, AuditViewTest, ActivityHistoryTest (CI green cac8d73)<br>J: wp2 «its history keeps the creation and the deactivation; the change records the user and the date» (UAT 4220ccf) [AUDIT_VALUES] | | — | — |
| LD-DOC-31-006 | 657 | ويجب أن يتضمن السجل | البيانات الجديدة. | YES | T: AuditTrailTest, AuditViewTest, ActivityHistoryTest (CI green cac8d73)<br>J: wp2 «its history keeps the creation and the deactivation; the change records the user and the date» (UAT 4220ccf) [AUDIT_VALUES] | | — | — |
| LD-DOC-31-007 | 658 | ويجب أن يتضمن السجل | سبب التعديل عند الحاجة. | YES | T: AuditTrailTest, AuditViewTest, ActivityHistoryTest (CI green cac8d73)<br>J: wp2 «its history keeps the creation and the deactivation; the change records the user and the date» (UAT 4220ccf) [AUDIT_VALUES] | | — | — |
| LD-DOC-31-008 | 659 | ويجب أن يتضمن السجل | ولا يكون السجل قابلاً للتعديل أو الحذف من المستخدمين العاديين. | YES | T: AuditTrailTest, AuditViewTest, AuditTrailTest#theDatabaseRefusesToChangeOrDeleteAuditEvents (CI green cac8d73)<br>J: wp1a «the audit trail records the grant» (UAT 8d7c847) [AUDIT] | | — | — |

<sub>عناوين المجموعات في المصدر: ويجب أن يتضمن السجل: (س652)</sub>

## LD-DOC-32 — الثاني والثلاثون: الإشعارات والتنبيهات

- **المرحلة**: المرحلة الثانية · **الأولوية**: عالية · **حزمة العمل**: WP6 · **سيناريوهات القبول المرتبطة**: S14
- **التحديث (2026-11-09)**: نُفِّذ بحزمة العمل؛ الدليل: DOC-WP6 (journey wp6) + DOC-WP3.

| المعرّف | السطر | المجموعة | المتطلب (نص المصدر) | IMPLEMENTED | VERIFIED_BY_DEVELOPER | QA_ACCEPTED | سبب العائق | ملاحظة (ما تبقى / لماذا لم يُتحقق) |
|---|---|---|---|---|---|---|---|---|
| LD-DOC-32-001 | 662 | إنشاء مركز موحد للإشعارات، يشمل | القضايا الجديدة. | YES | T: NotificationCentreApiTest, FollowUpDatesTest (CI green cac8d73)<br>J: wp6 «the new case is announced to its specialist, not to the mana» (UAT 87bf0e6) [NOTIFY_NEWCASE] | | — | — |
| LD-DOC-32-002 | 663 | إنشاء مركز موحد للإشعارات، يشمل | الجلسات القادمة. | YES | T: FollowUpDatesTest, DateRemindersTest (CI green cac8d73)<br>J: wp6 «the hearing in two days is announced once» (UAT 87bf0e6) [NOTIFY_HEARING] | | — | — |
| LD-DOC-32-003 | 664 | إنشاء مركز موحد للإشعارات، يشمل | جلسات الخبراء. | YES |  | | — | the centre and its eleven kinds are tested, but no test or journey step triggers this specific kind |
| LD-DOC-32-004 | 665 | إنشاء مركز موحد للإشعارات، يشمل | مواعيد الطعون. | YES | T: DeadlineReminderApiTest, FollowUpDatesTest (CI green cac8d73)<br>J: wp6 «the appeal date that exists is announced once» (UAT 87bf0e6)<br>J: wp4 «the specialist is told in the system once per overdue approv» (UAT 82c3285) [NOTIFY_DEADLINE] | | — | — |
| LD-DOC-32-005 | 666 | إنشاء مركز موحد للإشعارات، يشمل | المهام المتأخرة. | YES | T: TaskReminderApiTest (CI green cac8d73)<br>J: wp6 «the follow-up run answers» (UAT 87bf0e6) [NOTIFY_TASK] | | — | — |
| LD-DOC-32-006 | 667 | إنشاء مركز موحد للإشعارات، يشمل | الجلسات التي لم تسجل نتائجها. | YES |  | | — | the centre and its eleven kinds are tested, but no test or journey step triggers this specific kind |
| LD-DOC-32-007 | 668 | إنشاء مركز موحد للإشعارات، يشمل | طلبات الإدارات التي تأخر الرد عليها. | YES | T: InfoRequestApiTest#theFollowUpRunMarksALateRequestOverdueNotifiesOnceAndNeverClosesIt (CI green cac8d73)<br>J: wp3 «the specialist has one in-app alert about it» (UAT 3937a50) [NOTIFY_INFOREQ] | | — | — |
| LD-DOC-32-008 | 669 | إنشاء مركز موحد للإشعارات، يشمل | الأحكام الجديدة. | YES |  | | — | the centre and its eleven kinds are tested, but no test or journey step triggers this specific kind |
| LD-DOC-32-009 | 670 | إنشاء مركز موحد للإشعارات، يشمل | المستندات الناقصة. | YES | T: GapClosureApiTest#aMissingRequiredEvidenceItemRaisesOneDocumentMissingNotificationPerMember (CI green cac8d73) [NOTIFY_MISSING_DOC] | | — | — |
| LD-DOC-32-010 | 671 | إنشاء مركز موحد للإشعارات، يشمل | تعارض المواعيد. | YES | T: CalendarApiTest, NotificationCentreApiTest (CI green cac8d73)<br>J: wp6 «the conflict of dates is raised to the member» (UAT 87bf0e6) [NOTIFY_CONFLICT] | | — | — |
| LD-DOC-32-011 | 672 | إنشاء مركز موحد للإشعارات، يشمل | القضايا المرتفعة المخاطر. | YES |  | | — | the centre and its eleven kinds are tested, but no test or journey step triggers this specific kind |

<sub>عناوين المجموعات في المصدر: إنشاء مركز موحد للإشعارات، يشمل: (س661)</sub>

## LD-DOC-33 — الثالث والثلاثون: دورة حياة القضية

- **المرحلة**: المرحلة الأولى · **الأولوية**: عالية · **حزمة العمل**: WP1 · **سيناريوهات القبول المرتبطة**: S12
- **التحديث (2026-11-09)**: نُفِّذ بحزمة العمل؛ الدليل: DOC-WP3 (journey: S12).

| المعرّف | السطر | المجموعة | المتطلب (نص المصدر) | IMPLEMENTED | VERIFIED_BY_DEVELOPER | QA_ACCEPTED | سبب العائق | ملاحظة (ما تبقى / لماذا لم يُتحقق) |
|---|---|---|---|---|---|---|---|---|
| LD-DOC-33-001 | 675 | تكون دورة حياة القضية على النحو التالي | تسجيل القضية. | YES | T: CaseLifecycleApiTest, ConnectedCaseApiTest (CI green cac8d73)<br>J: wp3 «the case is closed; the hearings of the closed case remain» (UAT 3937a50) [LIFECYCLE] | | — | — |
| LD-DOC-33-002 | 676 | تكون دورة حياة القضية على النحو التالي | تحديد الجهات الإدارية المعنية. | YES | T: CaseLifecycleApiTest, ConnectedCaseApiTest (CI green cac8d73)<br>J: wp3 «the case is closed; the hearings of the closed case remain» (UAT 3937a50) [LIFECYCLE] | | — | — |
| LD-DOC-33-003 | 677 | تكون دورة حياة القضية على النحو التالي | تعيين العضو القانوني. | YES | T: CaseLifecycleApiTest, ConnectedCaseApiTest (CI green cac8d73)<br>J: wp3 «the case is closed; the hearings of the closed case remain» (UAT 3937a50) [LIFECYCLE] | | — | — |
| LD-DOC-33-004 | 678 | تكون دورة حياة القضية على النحو التالي | تسجيل الأطراف والطلبات. | YES | T: CaseLifecycleApiTest, ConnectedCaseApiTest (CI green cac8d73)<br>J: wp3 «the case is closed; the hearings of the closed case remain» (UAT 3937a50) [LIFECYCLE] | | — | — |
| LD-DOC-33-005 | 679 | تكون دورة حياة القضية على النحو التالي | تسجيل الدفوع وأوجه الدفاع. | YES | T: CaseLifecycleApiTest, ConnectedCaseApiTest (CI green cac8d73)<br>J: wp3 «the case is closed; the hearings of the closed case remain» (UAT 3937a50) [LIFECYCLE] | | — | — |
| LD-DOC-33-006 | 680 | تكون دورة حياة القضية على النحو التالي | تسجيل الأدلة والمستندات. | YES | T: CaseLifecycleApiTest, ConnectedCaseApiTest (CI green cac8d73)<br>J: wp3 «the case is closed; the hearings of the closed case remain» (UAT 3937a50) [LIFECYCLE] | | — | — |
| LD-DOC-33-007 | 681 | تكون دورة حياة القضية على النحو التالي | طلب الإفادات من الإدارات المعنية. | YES | T: CaseLifecycleApiTest, ConnectedCaseApiTest (CI green cac8d73)<br>J: wp3 «the case is closed; the hearings of the closed case remain» (UAT 3937a50) [LIFECYCLE] | | — | — |
| LD-DOC-33-008 | 682 | تكون دورة حياة القضية على النحو التالي | إعداد المذكرات. | YES | T: CaseLifecycleApiTest, ConnectedCaseApiTest (CI green cac8d73)<br>J: wp3 «the case is closed; the hearings of the closed case remain» (UAT 3937a50) [LIFECYCLE] | | — | — |
| LD-DOC-33-009 | 683 | تكون دورة حياة القضية على النحو التالي | تسجيل الجلسات والإجراءات. | YES | T: CaseLifecycleApiTest, ConnectedCaseApiTest (CI green cac8d73)<br>J: wp3 «the case is closed; the hearings of the closed case remain» (UAT 3937a50) [LIFECYCLE] | | — | — |
| LD-DOC-33-010 | 684 | تكون دورة حياة القضية على النحو التالي | متابعة المواعيد القانونية. | YES | T: CaseLifecycleApiTest, ConnectedCaseApiTest (CI green cac8d73)<br>J: wp3 «the case is closed; the hearings of the closed case remain» (UAT 3937a50) [LIFECYCLE] | | — | — |
| LD-DOC-33-011 | 685 | تكون دورة حياة القضية على النحو التالي | تسجيل الأحكام ونتائج الطلبات والدفوع. | YES | T: CaseLifecycleApiTest, ConnectedCaseApiTest (CI green cac8d73)<br>J: wp3 «the case is closed; the hearings of the closed case remain» (UAT 3937a50) [LIFECYCLE] | | — | — |
| LD-DOC-33-012 | 686 | تكون دورة حياة القضية على النحو التالي | الانتقال إلى درجة تقاضٍ أخرى عند الحاجة. | YES | T: CaseLifecycleApiTest, ConnectedCaseApiTest (CI green cac8d73)<br>J: wp3 «the case is closed; the hearings of the closed case remain» (UAT 3937a50) [LIFECYCLE] | | — | — |
| LD-DOC-33-013 | 687 | تكون دورة حياة القضية على النحو التالي | متابعة الطعون. | YES | T: CaseLifecycleApiTest, ConnectedCaseApiTest (CI green cac8d73)<br>J: wp3 «the case is closed; the hearings of the closed case remain» (UAT 3937a50) [LIFECYCLE] | | — | — |
| LD-DOC-33-014 | 688 | تكون دورة حياة القضية على النحو التالي | تنفيذ الحكم. | YES | T: CaseLifecycleApiTest, ConnectedCaseApiTest (CI green cac8d73)<br>J: wp3 «the case is closed; the hearings of the closed case remain» (UAT 3937a50) [LIFECYCLE] | | — | — |
| LD-DOC-33-015 | 689 | تكون دورة حياة القضية على النحو التالي | إغلاق القضية مع الاحتفاظ بكامل سجلها. | YES | T: CaseLifecycleApiTest, ConnectedCaseApiTest (CI green cac8d73)<br>J: wp3 «the case is closed; the hearings of the closed case remain» (UAT 3937a50) [LIFECYCLE] | | — | — |
| LD-DOC-33-016 | 690 | تكون دورة حياة القضية على النحو التالي | ولا يؤدي إغلاق القضية إلى حذف بياناتها أو جلساتها أو مستنداتها أو تاريخها. | YES | T: CaseLifecycleApiTest, ConnectedCaseApiTest (CI green cac8d73)<br>J: wp3 «the case is closed; the hearings of the closed case remain» (UAT 3937a50) [LIFECYCLE] | | — | — |

<sub>عناوين المجموعات في المصدر: تكون دورة حياة القضية على النحو التالي: (س674)</sub>

## LD-DOC-34 — الرابع والثلاثون: المتطلبات الفنية والأمنية

- **المرحلة**: أساس مشترك · **الأولوية**: أعلى · **حزمة العمل**: WP0 · **سيناريوهات القبول المرتبطة**: —
- **التحديث (2026-11-09)**: نُفِّذ بحزمة العمل؛ الدليل: DOC-WP10 (عقود تكامل معطلة).

| المعرّف | السطر | المجموعة | المتطلب (نص المصدر) | IMPLEMENTED | VERIFIED_BY_DEVELOPER | QA_ACCEPTED | سبب العائق | ملاحظة (ما تبقى / لماذا لم يُتحقق) |
|---|---|---|---|---|---|---|---|---|
| LD-DOC-34-001 | 692 | — | المحافظة على النظام الحالي والبيانات المسجلة. | YES | T: MigrationSafetyTest, ArchiveMigrationTest, MigrationPhase5IntegrityTest (CI green cac8d73) [NODATALOSS] | | — | — |
| LD-DOC-34-002 | 693 | — | مراجعة قاعدة البيانات قبل التعديلات الجوهرية. | YES |  | | — | process step (review before material changes): no automated check |
| LD-DOC-34-003 | 694 | — | إنشاء نسخ احتياطية قبل تحديث النظام أو ترحيل البيانات. | PARTIAL |  | | INFRA | DONE: backup before deploy exists in the deploy path; a full dump/restore drill passed 15 of 15 checks on an isolated PostgreSQL 18.6 (docs/evidence/DOC-RESTORE run3) after fixing a real defect (V800). NOT done: restore of UAT's real volume. Dependency: A host-level restore drill on UAT's own volume (shared host, outside the agent's rights). |
| LD-DOC-34-004 | 695 | — | تصميم قاعدة البيانات بما يسمح بالتوسع. | YES |  | | — | scale harness exists (DOC-PERF) but its targets are missed, so scalability is not claimed verified |
| LD-DOC-34-005 | 696 | — | ربط جميع الوحدات من خلال قاعدة بيانات موحدة. | YES | T: ProceedingCaseForeignKeyTest, HearingDatabaseIntegrityTest, ExpertDatabaseIntegrityTest, NoPublicSchemaReferenceTest (CI green cac8d73) [DB_INTEGRITY] | | — | — |
| LD-DOC-34-006 | 697 | — | ضمان سرعة البحث والاستعلام. | PARTIAL |  | | PERFORMANCE_ENGINEERING | Search and list were fixed (unified search 21 s to about 0.1-0.5 s p95 at 50,000 cases). Still over 0.5 s (p95, manager, 50k cases): dashboard 1.9 s, indicator figures 1.6 s, case inventory 0.9 s, and about a dozen other requests at 0.5-0.8 s. The 0.5 s target comes only from LD-DOC-03 D7 (proposed), not from the requirements text. Dependency: none external; a stored snapshot of dashboard figures needs an owner decision on its invalidation design (DOC-PERF section 4). |
| LD-DOC-34-007 | 698 | — | تشفير البيانات الحساسة. | BLOCKED |  | | OWNER_DECISION | DONE: passwords are hashed (LoginFlowTest, SecurityBaselineTest). NOT done: encryption of sensitive fields such as civil numbers; nothing can be implemented safely before keys are decided. Dependency: D7: approve key management and the field/disk encryption design. |
| LD-DOC-34-008 | 699 | — | حماية المستندات والملفات. | YES | T: ArchiveVisibilityApiTest, SecurityBaselineTest (CI green cac8d73)<br>J: wp5 «a file that is not what it claims is refused; the viewer finds nothing of a case they cannot see» (UAT 2bcd2dc) [FILE_PROTECTION] | | — | — |
| LD-DOC-34-009 | 700 | — | توفير آلية نسخ احتياطي واستعادة. | PARTIAL |  | | INFRA | DONE: backup mechanism (LD-041) and a passing restore drill on an isolated cluster including document versions (DOC-RESTORE). NOT done: backup encryption and a drill on UAT's real archive volume. Dependency: Restore drill of UAT archive data on the host. |
| LD-DOC-34-010 | 701 | — | إدارة الدخول والصلاحيات. | YES | T: LoginFlowTest, EndpointAuthorizationTest, SessionRevocationTest, PermissionContractTest (CI green cac8d73)<br>J: wp1a «viewer is refused /timeline; viewer is refused /documents» (UAT 8d7c847) [AUTH] | | — | — |
| LD-DOC-34-011 | 702 | — | منع الوصول غير المصرح به. | YES | T: CaseBoundEndpointsAccessTest#everyCaseBoundHandlerRefusesAUserWhoCannotSeeTheCase, AuthorizationBeforeValidationTest (CI green cac8d73)<br>J: wp1a «viewer is refused /timeline; viewer is refused /parties» (UAT 8d7c847) [NO_LEAK] | | — | — |
| LD-DOC-34-012 | 703 | — | تسجيل الأخطاء والعمليات الفنية. | YES | T: RequestCorrelationTest, AuditTrailTest (CI green cac8d73) [OBSERVABILITY] | | — | — |
| LD-DOC-34-013 | 704 | — | إجراء اختبارات أمنية قبل التشغيل. | PARTIAL |  | | SPECIALIST | DONE: automated security checks in CI (gitleaks 8.30.1 on every branch, SecurityBaselineTest, authorization-before-validation and case-bound endpoint tests). NOT done: independent security test. Dependency: An external penetration test / DAST (for example ZAP) before go-live. |
| LD-DOC-34-014 | 705 | — | ضمان التوافق مع المتصفحات الحديثة. | PARTIAL |  | | INFRA | DONE and verified: Chromium and Firefox, 39 screens x ar/en x 390/1366 px (312 smoke rows, 0 findings; DOC-UX at 4d83267). NOT run: WebKit/Safari. Dependency: WebKit needs a Docker image or render node the agent user cannot run on the host. |
| LD-DOC-34-015 | 706 | — | دعم العربية واتجاه العرض من اليمين إلى اليسار، مع دعم الإنجليزية عند الحاجة. | YES | F: locale.test.ts (CI green cac8d73)<br>E: results-summary.json, SUMMARY.md (DOC-UX run on frontend sources equal to develop 4d83267, Chromium+Firefox, ar/en) [UX_RTL] | | — | — |
| LD-DOC-34-016 | 707 | — | توفير واجهة متوافقة مع الحاسب والأجهزة اللوحية والهواتف. | PARTIAL |  | | TEST_NOT_RUN | DONE and verified: phone (390 px) and desktop (1366 px) on 39 screens, axe 0 violations on Chromium. NOT run: tablet width, and axe on Firefox/WebKit. Dependency: none (a tablet width was not in the matrix). |
| LD-DOC-34-017 | 708 | — | تصميم واجهات برمجية تسمح بالتكامل مستقبلاً مع أنظمة المراسلات والموارد البشرية والأرشفة والبريد الإلكتروني والأنظمة الحكومية ذات الصلة، وفق الموافقات اللازمة. | PARTIAL |  | | OWNER_DECISION | DONE and verified: integration contracts documented and five adapters present but disabled, no endpoint, no credentials (journey wp10, first two steps). NOT done: any real integration. Dependency: D8/section 34: owner names the systems to integrate and supplies credentials. |

## LD-DOC-35 — الخامس والثلاثون: اختبارات القبول

- **المرحلة**: أساس مشترك · **الأولوية**: أعلى · **حزمة العمل**: WP0 · **سيناريوهات القبول المرتبطة**: S1–S16
- **التحديث (2026-11-09)**: نُفِّذ بحزمة العمل؛ الدليل: tools/journeys/* + docs/evidence/DOC-WP*.

| المعرّف | السطر | المجموعة | المتطلب (نص المصدر) | IMPLEMENTED | VERIFIED_BY_DEVELOPER | QA_ACCEPTED | سبب العائق | ملاحظة (ما تبقى / لماذا لم يُتحقق) |
|---|---|---|---|---|---|---|---|---|
| LD-DOC-35-001 | 711 | يجب اختبار النظام عملياً قبل اعتماد التطوير، ومن ذلك | تسجيل قضية تحتوي على ثلاثة طلبات، ثم تسجيل حكم بنتائج مختلفة لكل طلب. | YES | T: ClaimApiTest, ClaimResultApiTest (CI green cac8d73)<br>J: wp2 «the case lists its three claims in order; each claim gets its own result» (UAT 4220ccf) [S1] | | — | — |
| LD-DOC-35-002 | 712 | يجب اختبار النظام عملياً قبل اعتماد التطوير، ومن ذلك | تعديل طلب أثناء سير الدعوى والتحقق من حفظ النص السابق. | YES | T: ClaimApiTest (CI green cac8d73)<br>J: wp2 «the specialist amends claim 1; both versions are kept, newest first» (UAT 4220ccf) [S2] | | — | — |
| LD-DOC-35-003 | 713 | يجب اختبار النظام عملياً قبل اعتماد التطوير، ومن ذلك | تسجيل دفع قانوني وربطه بمستند ومذكرة وحكم. | YES | T: DefenseApiTest, EvidenceApiTest (CI green cac8d73)<br>J: wp2 «the specialist records a defence linked to a memo; the same document is linked to a fact and to a defence» (UAT 4220ccf) [S3] | | — | — |
| LD-DOC-35-004 | 714 | يجب اختبار النظام عملياً قبل اعتماد التطوير، ومن ذلك | إضافة حكم إلى المكتبة واستخراج مبدأ قضائي منه والبحث عنه. | YES | T: LibraryPrincipleApiTest, LibrarySearchQualityTest (CI green cac8d73)<br>J: wp7 «the specialist registers the judgment; the specialist extracts a principle apart from the judgment» (UAT 60f5368) [S4] | | — | — |
| LD-DOC-35-005 | 715 | يجب اختبار النظام عملياً قبل اعتماد التطوير، ومن ذلك | إنشاء قضية جديدة والتحقق من اقتراح أحكام سابقة مشابهة. | YES | T: SimilarJudgmentApiTest (CI green cac8d73)<br>J: wp7 «the specialist is offered similar judgments; each suggestion explains itself with words and points» (UAT 60f5368) [S5] | | — | — |
| LD-DOC-35-006 | 716 | يجب اختبار النظام عملياً قبل اعتماد التطوير، ومن ذلك | إرسال طلب إفادة إلى إدارة وتسجيل الرد ومتابعة التأخير. | YES | T: InfoRequestApiTest (CI green cac8d73)<br>J: wp3 «a partial reply is recorded; the late request is marked overdue and is not closed» (UAT 3937a50) [S6] | | — | — |
| LD-DOC-35-007 | 717 | يجب اختبار النظام عملياً قبل اعتماد التطوير، ومن ذلك | تسجيل تقييم مخاطر وتحديثه بعد ورود مستند جديد. | YES | T: RiskAssessmentApiTest (CI green cac8d73)<br>J: wp3 «the first assessment is version 1, with ten elements; a new document leads to a new version» (UAT 3937a50) [S7] | | — | — |
| LD-DOC-35-008 | 718 | يجب اختبار النظام عملياً قبل اعتماد التطوير، ومن ذلك | ربط مستند بواقعة ودفع قانوني والتحقق من ظهوره في ملف القضية. | YES | T: EvidenceApiTest (CI green cac8d73)<br>J: wp2 «the same document is linked to a fact and to a defence; the case file shows the document as evidence with both links» (UAT 4220ccf) [S8] | | — | — |
| LD-DOC-35-009 | 719 | يجب اختبار النظام عملياً قبل اعتماد التطوير، ومن ذلك | تسجيل جلسة وتأجيلها والتحقق من بقاء الجلسة السابقة. | YES | T: HearingExtensionApiTest (CI green cac8d73)<br>J: wp3 «staff adjourns it to a next hearing; the previous hearing is adjourned, not replaced» (UAT 3937a50) [S9] | | — | — |
| LD-DOC-35-010 | 720 | يجب اختبار النظام عملياً قبل اعتماد التطوير، ومن ذلك | ربط قضيتين والتحقق من ظهور الارتباط في الملفين. | YES | T: CaseLinkApiTest (CI green cac8d73)<br>J: wp3 «the link shows in the file of case A; and in the file of case B» (UAT 3937a50) [S10] | | — | — |
| LD-DOC-35-011 | 721 | يجب اختبار النظام عملياً قبل اعتماد التطوير، ومن ذلك | إضافة جلسة ومهمة والتحقق من ظهورهما في التقويم. | YES | T: CalendarApiTest (CI green cac8d73)<br>J: wp6 «the hearing and the task of the case are in it» (UAT 87bf0e6) [S11] | | — | — |
| LD-DOC-35-012 | 722 | يجب اختبار النظام عملياً قبل اعتماد التطوير، ومن ذلك | إغلاق قضية والتحقق من بقاء الجلسات السابقة والبيانات التاريخية. | YES | T: CaseLifecycleApiTest (CI green cac8d73)<br>J: wp3 «the case is closed; the hearings of the closed case remain» (UAT 3937a50) [S12] | | — | — |
| LD-DOC-35-013 | 723 | يجب اختبار النظام عملياً قبل اعتماد التطوير، ومن ذلك | إنشاء درجات تقاضٍ متعددة والتحقق من استقلال بيانات كل درجة. | YES | T: ProceedingApiTest, ClaimResultApiTest (CI green cac8d73)<br>J: wp2 «an appeal is recorded against the first-instance proceeding; the first-instance results are unchanged» (UAT 4220ccf) [S13] | | — | — |
| LD-DOC-35-014 | 724 | يجب اختبار النظام عملياً قبل اعتماد التطوير، ومن ذلك | اختبار احتساب المواعيد والتنبيهات. | PARTIAL |  | | LEGAL_INPUT | Journey wp4 ran green on UAT with SYNTHETIC rules and manual approved deadlines; reminders ran. Scenario with real legal rules cannot be run. Dependency: D6: no approved legal deadline rules or holidays. |
| LD-DOC-35-015 | 725 | يجب اختبار النظام عملياً قبل اعتماد التطوير، ومن ذلك | اختبار صلاحيات المستخدمين. | YES | T: CaseVisibilityApiTest, RoleAdministrationTest, PermissionContractTest (CI green cac8d73)<br>J: wp1a «a read-only user without a scope does not see it; a grant is given with a reason» (UAT 8d7c847) [S15] | | — | — |
| LD-DOC-35-016 | 726 | يجب اختبار النظام عملياً قبل اعتماد التطوير، ومن ذلك | اختبار المساعد القانوني والتحقق من إظهار مصادر إجاباته وعدم اختلاق أحكام أو نصوص. | PARTIAL |  | | EXTERNAL_PROVIDER | Journey wp10 ran with a non-production deterministic provider (sources shown, refusal, no invented citation). Operational assistant is disabled. Dependency: D8. |

<sub>عناوين المجموعات في المصدر: يجب اختبار النظام عملياً قبل اعتماد التطوير، ومن ذلك: (س710)</sub>

## LD-DOC-36 — السادس والثلاثون: مراحل التنفيذ والتسليم

- **المرحلة**: أساس مشترك · **الأولوية**: أعلى · **حزمة العمل**: WP0 · **سيناريوهات القبول المرتبطة**: —
- **التحديث (2026-11-09)**: نُفِّذ بحزمة العمل؛ الدليل: docs/reports/PHASE-*-REPORT-ar.md.

| المعرّف | السطر | المجموعة | المتطلب (نص المصدر) | IMPLEMENTED | VERIFIED_BY_DEVELOPER | QA_ACCEPTED | سبب العائق | ملاحظة (ما تبقى / لماذا لم يُتحقق) |
|---|---|---|---|---|---|---|---|---|
| LD-DOC-36-001 | 730 | تشمل | إدارة القضايا والبيانات الأساسية. | YES |  | | — | Umbrella, rolled up from 18 detailed rows (0 not YES). Not tested on its own. no test or journey step found that exercises this row |
| LD-DOC-36-002 | 731 | تشمل | أطراف القضايا. | YES |  | | — | Umbrella, rolled up from 7 detailed rows (0 not YES). Not tested on its own. no test or journey step found that exercises this row |
| LD-DOC-36-003 | 732 | تشمل | درجات التقاضي. | YES |  | | — | Umbrella, rolled up from 6 detailed rows (0 not YES). Not tested on its own. no test or journey step found that exercises this row |
| LD-DOC-36-004 | 733 | تشمل | طلبات الدعوى ونتيجة الحكم في كل طلب. | YES |  | | — | Umbrella, rolled up from 26 detailed rows (0 not YES). Not tested on its own. no test or journey step found that exercises this row |
| LD-DOC-36-005 | 734 | تشمل | تحديد القطاع والإدارة والقسم المعني. | YES |  | | — | Umbrella, rolled up from 14 detailed rows (0 not YES). Not tested on its own. no test or journey step found that exercises this row |
| LD-DOC-36-006 | 735 | تشمل | سجل الجلسات والإجراءات. | YES |  | | — | Umbrella, rolled up from 18 detailed rows (0 not YES). Not tested on its own. no test or journey step found that exercises this row |
| LD-DOC-36-007 | 736 | تشمل | القضايا المرتبطة. | YES |  | | — | Umbrella, rolled up from 13 detailed rows (0 not YES). Not tested on its own. no test or journey step found that exercises this row |
| LD-DOC-36-008 | 737 | تشمل | الأحكام ومواعيد الطعون. | PARTIAL |  | | LEGAL_INPUT | Umbrella, rolled up from 29 detailed rows (2 not YES). Not tested on its own. Dependency: see the rolled-up rows. |
| LD-DOC-36-009 | 738 | تشمل | إدارة الدفوع وأوجه الدفاع. | YES |  | | — | Umbrella, rolled up from 30 detailed rows (0 not YES). Not tested on its own. no test or journey step found that exercises this row |
| LD-DOC-36-010 | 739 | تشمل | مكتبة الأحكام والسوابق القضائية. | PARTIAL |  | | UI_NOT_BUILT | Umbrella, rolled up from 51 detailed rows (1 not YES). Not tested on its own. Dependency: see the rolled-up rows. |
| LD-DOC-36-011 | 740 | تشمل | متابعة طلبات الإدارات المعنية. | YES |  | | — | Umbrella, rolled up from 39 detailed rows (0 not YES). Not tested on its own. no test or journey step found that exercises this row |
| LD-DOC-36-012 | 741 | تشمل | تقييم المخاطر القانونية. | YES |  | | — | Umbrella, rolled up from 28 detailed rows (0 not YES). Not tested on its own. no test or journey step found that exercises this row |
| LD-DOC-36-013 | 742 | تشمل | الأدلة والمستندات المؤيدة للدفاع. | YES |  | | — | Umbrella, rolled up from 25 detailed rows (0 not YES). Not tested on its own. no test or journey step found that exercises this row |
| LD-DOC-36-014 | 743 | تشمل | الملخص التنفيذي والتسلسل الزمني (Timeline). | YES |  | | — | Umbrella, rolled up from 41 detailed rows (0 not YES). Not tested on its own. no test or journey step found that exercises this row |
| LD-DOC-36-015 | 746 | تشمل | تنفيذ الأحكام. | YES |  | | — | Umbrella, rolled up from 2 detailed rows (0 not YES). Not tested on its own. no test or journey step found that exercises this row |
| LD-DOC-36-016 | 747 | تشمل | الأرشيف الإلكتروني المتقدم. | YES |  | | — | Umbrella, rolled up from 9 detailed rows (0 not YES). Not tested on its own. no test or journey step found that exercises this row |
| LD-DOC-36-017 | 748 | تشمل | المذكرات والمراسلات. | YES |  | | — | Umbrella, rolled up from 10 detailed rows (0 not YES). Not tested on its own. no test or journey step found that exercises this row |
| LD-DOC-36-018 | 749 | تشمل | توزيع القضايا والمهام. | YES |  | | — | Umbrella, rolled up from 9 detailed rows (0 not YES). Not tested on its own. no test or journey step found that exercises this row |
| LD-DOC-36-019 | 750 | تشمل | التقويم القضائي الفردي والعام. | YES |  | | — | Umbrella, rolled up from 13 detailed rows (0 not YES). Not tested on its own. no test or journey step found that exercises this row |
| LD-DOC-36-020 | 751 | تشمل | التنبيهات. | YES |  | | — | Umbrella, rolled up from 11 detailed rows (0 not YES). Not tested on its own. no test or journey step found that exercises this row |
| LD-DOC-36-021 | 752 | تشمل | لوحة التحكم. | PARTIAL |  | | DATA_NOT_PROVIDED | Umbrella, rolled up from 15 detailed rows (1 not YES). Not tested on its own. Dependency: see the rolled-up rows. |
| LD-DOC-36-022 | 753 | تشمل | التقارير والإحصائيات. | YES |  | | — | Umbrella, rolled up from 17 detailed rows (0 not YES). Not tested on its own. no test or journey step found that exercises this row |
| LD-DOC-36-023 | 754 | تشمل | قياس أداء الأعضاء والإدارات. | YES |  | | — | Umbrella, rolled up from 3 detailed rows (0 not YES). Not tested on its own. no test or journey step found that exercises this row |
| LD-DOC-36-024 | 755 | تشمل | إدارة التسويات والصلح. | YES |  | | — | Umbrella, rolled up from 10 detailed rows (0 not YES). Not tested on its own. no test or journey step found that exercises this row |
| LD-DOC-36-025 | 756 | تشمل | النماذج القانونية الجاهزة. | YES |  | | — | Umbrella, rolled up from 10 detailed rows (0 not YES). Not tested on its own. no test or journey step found that exercises this row |
| LD-DOC-36-026 | 757 | تشمل | تطوير البحث المتقدم. | YES |  | | — | Umbrella, rolled up from 13 detailed rows (0 not YES). Not tested on its own. no test or journey step found that exercises this row |
| LD-DOC-36-027 | 760 | تشمل | المساعد القانوني الذكي داخل النظام. | PARTIAL |  | | EXTERNAL_PROVIDER | Umbrella, rolled up from 12 detailed rows (12 not YES). Not tested on its own. Dependency: see the rolled-up rows. |
| LD-DOC-36-028 | 761 | تشمل | تلخيص ملفات القضايا آلياً. | PARTIAL |  | | EXTERNAL_PROVIDER | Umbrella, rolled up from 8 detailed rows (8 not YES). Not tested on its own. Dependency: see the rolled-up rows. |
| LD-DOC-36-029 | 762 | تشمل | استخراج البيانات والطلبات والدفوع من المستندات. | BLOCKED |  | | EXTERNAL_PROVIDER | Umbrella, rolled up from 7 detailed rows (7 not YES). Not tested on its own. Dependency: see the rolled-up rows. |
| LD-DOC-36-030 | 763 | تشمل | البحث الذكي عن الأحكام والسوابق القضائية. | PARTIAL |  | | EXTERNAL_PROVIDER | Umbrella, rolled up from 6 detailed rows (6 not YES). Not tested on its own. Dependency: see the rolled-up rows. |
| LD-DOC-36-031 | 764 | تشمل | اقتراح القضايا والدفوع المشابهة. | PARTIAL |  | | EXTERNAL_PROVIDER | Umbrella, rolled up from 5 detailed rows (5 not YES). Not tested on its own. Dependency: see the rolled-up rows. |
| LD-DOC-36-032 | 765 | تشمل | إعداد مسودات أولية للمذكرات. | BLOCKED |  | | EXTERNAL_PROVIDER | Umbrella, rolled up from 9 detailed rows (9 not YES). Not tested on its own. Dependency: see the rolled-up rows. |
| LD-DOC-36-033 | 766 | تشمل | مقارنة الأحكام والمذكرات والمستندات. | BLOCKED |  | | EXTERNAL_PROVIDER | Umbrella, rolled up from 6 detailed rows (6 not YES). Not tested on its own. Dependency: see the rolled-up rows. |
| LD-DOC-36-034 | 767 | تشمل | كشف النواقص والتناقضات. | BLOCKED |  | | EXTERNAL_PROVIDER | Umbrella, rolled up from 2 detailed rows (2 not YES). Not tested on its own. Dependency: see the rolled-up rows. |
| LD-DOC-36-035 | 768 | تشمل | التكامل مع الأنظمة الأخرى من خلال واجهات برمجية. | PARTIAL |  | | OWNER_DECISION | Umbrella, rolled up from 1 detailed rows (1 not YES). Not tested on its own. Dependency: see the rolled-up rows. |

<sub>عناوين المجموعات في المصدر: المرحلة الأولى: التطوير الأساسي – الأولوية الأعلى (س728)؛ تشمل: (س729)؛ المرحلة الثانية: المتابعة والأداء والتقارير (س744)؛ تشمل: (س745)؛ المرحلة الثالثة: الذكاء الاصطناعي والتطوير المتقدم (س758)؛ تشمل: (س759)</sub>

## LD-DOC-37 — السابع والثلاثون: تعليمات نهائية للمبرمج

- **المرحلة**: أساس مشترك · **الأولوية**: أعلى · **حزمة العمل**: WP0 · **سيناريوهات القبول المرتبطة**: —
- **التحديث (2026-11-09)**: نُفِّذ بحزمة العمل؛ الدليل: docs/reports + docs/handbook.

| المعرّف | السطر | المجموعة | المتطلب (نص المصدر) | IMPLEMENTED | VERIFIED_BY_DEVELOPER | QA_ACCEPTED | سبب العائق | ملاحظة (ما تبقى / لماذا لم يُتحقق) |
|---|---|---|---|---|---|---|---|---|
| LD-DOC-37-001 | 770 | — | المطلوب تطوير النظام الحالي وليس إنشاء نظام منفصل عنه. | YES |  | | — | no test or journey step found that exercises this row |
| LD-DOC-37-002 | 771 | — | عدم فقد أي بيانات موجودة. | YES | T: MigrationSafetyTest, ArchiveMigrationTest, MigrationPhase5IntegrityTest (CI green cac8d73) [NODATALOSS] | | — | — |
| LD-DOC-37-003 | 772 | — | يجب أن تكون جميع الوحدات مترابطة. | YES | T: ConnectedCaseApiTest (CI green cac8d73)<br>J: conn «a claim is recorded; a defence is recorded» (UAT 3937a50) [CONNECTED] | | — | — |
| LD-DOC-37-004 | 773 | — | الاحتفاظ بالسجل التاريخي لجميع الإجراءات والتعديلات. | YES | T: AuditTrailTest, ActivityHistoryTest, ClaimApiTest (CI green cac8d73)<br>J: wp2 «both versions are kept, newest first» (UAT 4220ccf) [HISTORY_KEPT] | | — | — |
| LD-DOC-37-005 | 774 | — | عدم استبدال الجلسات السابقة عند التأجيل. | YES | T: HearingExtensionApiTest (CI green cac8d73)<br>J: wp3 «staff adjourns it to a next hearing; the previous hearing is adjourned, not replaced» (UAT 3937a50) [S9] | | — | — |
| LD-DOC-37-006 | 775 | — | ربط الطلبات بنتائج الأحكام بصورة مستقلة. | YES | T: ClaimResultApiTest, JudgmentDetailsApiTest (CI green cac8d73)<br>J: wp2 «each claim gets its own result; the awarded amount keeps three decimals as text» (UAT 4220ccf) [CLAIM_RESULTS] | | — | — |
| LD-DOC-37-007 | 776 | — | ربط الدفوع بموقف المحكمة في كل درجة تقاضٍ. | YES | T: DefenseApiTest, JudgmentDetailsApiTest (CI green cac8d73)<br>J: wp2 «the court's position is recorded on each defence; the defence is re-asserted in the appeal» (UAT 4220ccf) [DEFENSE_POSITION] | | — | — |
| LD-DOC-37-008 | 777 | — | ربط المستندات بالوقائع والطلبات والدفوع ذات الصلة. | YES | T: EvidenceApiTest (CI green cac8d73)<br>J: wp2 «a document becomes evidence with a role and an importance; the same document is linked to a fact and to a defence» (UAT 4220ccf) [EVIDENCE] | | — | — |
| LD-DOC-37-009 | 778 | — | ربط طلبات الإدارات بالقضايا ومتابعة الردود. | YES | T: InfoRequestApiTest (CI green cac8d73)<br>J: wp3 «staff registers a request to the unit as a draft; the system generated the number» (UAT 3937a50) [INFOREQ] | | — | — |
| LD-DOC-37-010 | 779 | — | ربط الجلسات والمهام بالتقويم تلقائياً. | YES | T: CalendarApiTest, CalendarEngineTest (CI green cac8d73)<br>F: Calendar.test.tsx (CI green cac8d73)<br>J: wp6 «the hearing and the task of the case are in it; the day view holds them» (UAT 87bf0e6) [CALENDAR] | | — | — |
| LD-DOC-37-011 | 780 | — | بقاء الجلسات السابقة ظاهرة بعد إغلاق القضية. | YES | T: CalendarApiTest (CI green cac8d73)<br>J: wp3 «the hearings of the closed case remain» (UAT 3937a50) [CALENDAR_CLOSED] | | — | — |
| LD-DOC-37-012 | 781 | — | ظهور ارتباط القضايا في ملفاتها جميعاً. | YES | T: CaseLinkApiTest (CI green cac8d73)<br>J: wp3 «a request to join starts as requested, not decided; the link shows in the file of case A» (UAT 3937a50) [LINKS] | | — | — |
| LD-DOC-37-013 | 782 | — | إمكانية تحديث الهيكل التنظيمي من الإعدادات. | YES | T: OrgUnitApiTest (CI green cac8d73)<br>J: wp1a «manager creates the authority's sector/department/section; the sector name is frozen on the record» (UAT 8d7c847) [ORG] | | — | — |
| LD-DOC-37-014 | 783 | — | توثيق مصادر الأحكام والمبادئ القضائية. | YES | T: LibraryJudgmentApiTest (CI green cac8d73)<br>J: wp7 «the source is mandatory; the citation shows source, date and number» (UAT 60f5368) [LIB_SOURCE] | | — | — |
| LD-DOC-37-015 | 784 | — | عدم اعتماد مخرجات الذكاء الاصطناعي دون مراجعة بشرية. | PARTIAL |  | | EXTERNAL_PROVIDER | Drafts stay DRAFT and are never final in the test provider path; no operational assistant. Dependency: D8. |
| LD-DOC-37-016 | 785 | — | تقديم مخطط قاعدة البيانات والعلاقات بين الوحدات قبل البرمجة. | YES |  | | — | Deliverable (docs/design/LD-DOC-03-data-model.md); not a testable behaviour. no test or journey step found that exercises this row |
| LD-DOC-37-017 | 786 | — | تقديم تصميمات الشاشات المقترحة للاعتماد قبل التنفيذ. | PARTIAL |  | | OWNER_DECISION | Designs were delivered as documents and prototypes; approval is the owner's. Dependency: Owner visual approval of the design package (docs/design/PHASE-7-OWNER-VISUAL-REVIEW.md). |
| LD-DOC-37-018 | 787 | — | تقديم نسخة تجريبية للاختبار قبل التشغيل النهائي. | YES |  | | — | UAT is published; not a testable behaviour. no test or journey step found that exercises this row |
| LD-DOC-37-019 | 788 | — | تسليم التوثيق الفني ودليل الاستخدام. | YES |  | | — | Handbook delivered (PR #83); not a testable behaviour. no test or journey step found that exercises this row |
| LD-DOC-37-020 | 789 | — | عدم اعتبار المشروع مكتملاً بمجرد إضافة الشاشات؛ بل بعد تشغيل الوظائف واجتياز اختبارات القبول. | PARTIAL |  | | NONE | The project is not complete: open rows remain and nothing is QA_ACCEPTED. Dependency: All BLOCKED/PARTIAL rows above, QA acceptance. |
| LD-DOC-37-021 | 791 | النتيجة المستهدفة | تحويل LawDesk إلى منصة متكاملة لإدارة العمل القانوني، لا تقتصر على تسجيل القضايا والجلسات، وإنما تتيح متابعة الطلبات والدفوع والأدلة والمستندات والأحكام والسوابق القضائية، وإدارة المخاطر ومتابعة الإدارات المعنية، مع توفير ملخص تنفيذي وتسلسل زمني لكل قضية، ومساعد قانوني ذكي يدعم الأعضاء في التحليل والبحث وإعداد الأعمال القانونية، مع بقاء الاعتماد النهائي والمراجعة القانونية بيد المختص. | PARTIAL |  | | NONE | The project is not complete: open rows remain and nothing is QA_ACCEPTED. Dependency: All BLOCKED/PARTIAL rows above, QA acceptance. |

<sub>عناوين المجموعات في المصدر: النتيجة المستهدفة: (س790)</sub>
