# LD-DOC-13 — إدارة القوائم المنسدلة (صفحة الأدمن) / Administrator dropdown lists

Stream A of the r5 screens round. Page: **/admin/lists** (tab «القوائم المنسدلة»), right `LIST_ADMIN`, held by `ADMIN` only
(seeded by V860; no other role). The inventory below was taken from the **screens** (every `<select>`, `ChoiceField`,
`PagedSelect` and picker in `frontend/src`), not from the database constraints. It is regenerated from
`tools/lists/registry.py` by `python3 tools/lists/gen.py` (which also writes V861, `ListCatalog.java` and
`frontend/src/catalog/registry.ts`).

## Design

* One catalogue (`ListCatalog`). Options live in `lookup_value` (extended with `sort_order` and `system`), in
  `managed_list_value` (settlement, directive, template kinds: existing service and permissions) or in the court register.
* Every list was seeded with its **current codes** and labels copied from the screens' own messages (V860, V861), so no stored
  code changes and no relation breaks.
* **Class a** — the administrator adds, edits and deactivates options. The fixed CHECK (and Java enum where there was one)
  is replaced by a format check plus validation against the active catalogue options in the service. A record keeps an option
  deactivated later; only a *new choice* must be an active option.
* **Class b** — label and order only (no new option: 422 `LIST_LABEL_ONLY`; the code never changes and the value is never
  deactivated: 422 `SYSTEM_VALUE_FIXED`). The reason names the logic that depends on the codes. Permissions, workflow and
  reports keep working because their codes are untouched.
* Nothing is deleted. Every change is audited and keeps a history row; a stale version is 409; a duplicate code is 409.
* **Screens:** the whole app asks `GET /api/v1/lookups/all` once after sign-in. The translation function consults the catalogue
  first for every key `prefix + CODE` of a catalogued list, so a label edited by the administrator is what every screen,
  table, filter and report shows, in the active language. Each dropdown also takes its options **in the catalogue order**, and
  a deactivated option is hidden from new input (kept only when it is the record's current value). If the catalogue is
  unavailable, screens keep the built-in labels and order.
* The three original lists stay manageable by `LOOKUP_MANAGE` on the old tab; every other lookup list needs `LIST_ADMIN` on
  the old endpoints too.

## Inventory (list → screens → source → class → reason)

| List | Screens | Backing / source | Class | Reason (class b) |
|---|---|---|---|---|
| CASE_TYPE (أنواع القضايا) | case registration/edit, case list filter, case inventory/family/claim-statistics/library/search filters, access scopes | legal_case.case_type (CHECK + Java enum) | b | access scopes of type CASE_TYPE, report grouping and library matching key on the code |
| CASE_STATUS (حالات القضية) | case list filter, case inventory filter, case status form, dashboard | legal_case.status (CHECK + Java enum) | b | the closing/archiving workflow, archived read-only rules and report groups depend on the codes |
| CASE_IMPORTANCE (أهمية القضية) | case registration/edit, case details | legal_case.importance (CHECK replaced by catalogue validation, V860) | a | — |
| AUTHORITY_POSITION (جانب الدعوى (مدعي/مدعى عليه)) | case registration (authority position), case parties (side), case details | legal_case.authority_position, case_party.side (CHECK) | b | which side each party is on drives party roles, the swap rule and the brief |
| PARTY_SIDE_GROUP (عنوان مجموعة الأطراف (المدعون/المدعى عليهم)) | case registration parties section | case_party.side (CHECK) shown as a plural heading | b | the side code decides which group a party joins |
| PARTY_ROLE (دور الطرف) | case parties (add/edit party) | case_party.role (CHECK + Java enum PartyRole) | b | CLIENT/OPPONENT decide the authority side and duplicate/conflict checks |
| PARTY_KIND (نوع الطرف (شخص/جهة)) | case registration parties, case parties | party.kind (CHECK + Java enum PartyKind) | b | an organisation cannot hold a national id; identity and duplicate rules differ by kind |
| LIBRARY_CONFIDENTIALITY (سرية الحكم في المكتبة) | library judgment form | library_judgment.confidentiality (CHECK) | b | RESTRICTED hides parties from readers of the library |
| CASE_CONFIDENTIALITY (سرية القضية) | case access section | legal_case.confidentiality (CHECK) | b | RESTRICTED hides the case from everyone outside members and grants (access policy) |
| GRANT_LEVEL (مستوى المنحة) | case access section (explicit grants) | case_grant.level (CHECK) | b | VIEW/CONTRIBUTE are what the access policy checks for read and write |
| COURT_LEVEL (درجات المحاكم) | courts list filter and form | court.level (CHECK) | b | the level decides which stage a court may be used for |
| STAGE (مرحلة التقاضي) | proceedings forms, case inventory/judgment reports, claim statistics, documents | proceeding.stage (CHECK) | b | the appeal chain (an appeal needs a first-instance judgment) and stage reports key on the code |
| JUDGMENT_OUTCOME (نتيجة الحكم) | proceedings judgment form, judgment report filter | judgment.outcome (CHECK) | b | judgment statistics, outcome reports and appeal-deadline logic group by the code |
| DECISION_TYPE (نوع القرار الخاص) | proceedings special-decision form | special_decision.decision_type (CHECK + Java enum) | b | ENFORCEMENT_SUSPENSION requires a linked judgment (rule in SpecialDecisionService) |
| DECISION_EFFECT (أثر القرار الخاص) | proceedings special-decision form | special_decision.effect (CHECK + Java enum) | b | the effect is recorded as a decision result and read by the timeline and reports |
| HEARING_KIND (نوع الجلسة) | hearing form | hearing.kind (CHECK + Java enum) | b | the kind decides the calendar entry type (court, expert session, investigation) |
| HEARING_PURPOSE (غرض الجلسة) | hearing form, hearing reschedule, hearings page and report | hearing.purpose (CHECK + Java enum) | b | purpose drives the proposed dependent dates and the hearing report |
| HEARING_STATUS (حالة الجلسة) | hearings filter and result form, hearings report | hearing.status (CHECK + state machine) | b | the result/adjournment state machine and "awaiting result" alerts depend on it |
| TASK_STATUS (حالة المهمة) | tasks filter and form, workload report | task.status (CHECK + state machine) | b | lateness, reminders and completion rules transition on these codes |
| TASK_PRIORITY (أولوية المهمة) | task form, tasks list | task.priority (CHECK + Java enum TaskPriority) | b | task ordering and reminder escalation compare priorities |
| CALENDAR_ENTRY_TYPE (نوع مدخل التقويم) | calendar filter, manual date form | calendar projection kinds (Java constants) | b | the calendar projects hearings, tasks and deadlines under these types and colours them |
| NOTIFICATION_TYPE (نوع الإشعار) | notifications filter and preferences | notification.type (Java constants) | b | notification generation and per-user preferences are keyed by the type |
| DEADLINE_STATUS (حالة الموعد القانوني) | badges on case deadlines (the filter dropdown was removed with the /deadlines register) | deadline.status (CHECK) | b | missed-deadline detection and reminders transition on these codes |
| DEADLINE_CALC_STATUS (حالة احتساب الموعد) | badges on case deadlines (the filter dropdown was removed with the /deadlines register) | deadline.calc_status (CHECK) | b | PROPOSED/APPROVED is the approval workflow of calculated dates |
| DEADLINE_COUNT_METHOD (طريقة العدّ) | deadline rule form | deadline_rule.count_method (CHECK) | b | the calculation engine branches on calendar vs working days |
| DEADLINE_SOURCE_TYPE (مصدر الموعد) | deadline form | deadline.source_type (CHECK) | b | the source type decides which record the date is computed from |
| RISK_LEVEL (مستوى المخاطر) | risk assessment form | risk_assessment.level (CHECK) | b | HIGH/VERY_HIGH raise notifications and appear in reports |
| RISK_RATING (تقدير عنصر المخاطر) | risk assessment form | risk_assessment_element.rating (CHECK) | b | the overall level is computed from the element ratings |
| RISK_TRIGGER (سبب إعادة التقييم) | risk assessment form | risk_assessment.trigger_kind (CHECK) | b | automatic triggers (new document, judgment, decision) create assessments by code |
| REMEDIATION_STATUS (حالة خطوة المعالجة) | risk remediation steps | risk_remediation.status (CHECK) | b | open steps keep the risk alert active |
| LINK_KIND (نوع ربط القضايا) | case links section | case_link.kind (CHECK) | b | joinder request/decision is a workflow between two cases |
| LINK_STATUS (حالة ربط القضايا) | case links section | case_link.status (CHECK) | b | ACTIVE links change which cases see each other |
| INFO_COMPLETENESS (اكتمال رد الإدارة) | information request reply form | info_reply.completeness (CHECK) | b | the request status (partial/complete) is derived from it |
| INFO_FOLLOW_UP_KIND (نوع متابعة طلب الإفادة) | information request card | info_follow_up.kind (CHECK) | b | escalation is a distinct workflow step with its own notification |
| CLAIM_KIND (نوع الطلب) | case claims form | claim.kind (CHECK replaced by catalogue validation, V861; Java enum -> String) | a | — |
| CLAIM_STATUS (حالة الطلب) | case claims form, claim statistics | claim.status (CHECK + Java enum) | b | claim statistics and the "standing claims" results screen filter on it |
| CLAIM_RESULT (نتيجة الطلب في الحكم) | judgment results form, judgments filter | claim_result.result (CHECK) | b | awarded-amount statistics and success rates are computed per result code |
| JUDGMENT_STATUS (حالة الحكم) | judgment details form | judgment.judgment_status (CHECK) | b | NOTIFIED/CHALLENGED interact with the appeal deadline |
| DEFENSE_KIND (نوع الدفع) | case defences form, defence wording library | defense.kind, defense_template.kind (CHECK + Java enum) | b | defence statistics are grouped by kind and the wording library is filtered by it |
| DEFENSE_POSITION (موقف المحكمة من الدفع) | judgment results form, case defences | defense_result.position (CHECK) | b | defence success statistics are computed per position code |
| ASSERTION_STATUS (حالة التمسك بالدفع) | case defences (per stage) | defense_assertion.status (CHECK) | b | "re-asserted" is checked when a defence is raised at a later stage |
| EVIDENCE_ROLE (دور الدليل) | case evidence form | evidence_item.role (CHECK + Java enum) | b | UNAVAILABLE/REQUIRED raise the missing-document alert and SUPPORTING/OPPONENT split "ours" from "theirs" |
| EVIDENCE_IMPORTANCE (أهمية الدليل) | case evidence form, defence evidence links | evidence_item.importance (CHECK replaced by catalogue validation, V861; Java enum -> String) | a | — |
| LIBRARY_COURT_LEVEL (درجة المحكمة في المكتبة) | library judgment form, library search | library_judgment.court_level (CHECK) | b | similar-judgment matching compares court levels |
| LIBRARY_DIRECTION (اتجاه الحكم) | library judgment form, library search | library_judgment.direction (CHECK) | b | for/against the authority feeds library statistics and similar-judgment ranking |
| LIBRARY_FINALITY (نهائية الحكم) | library judgment form, library search | library_judgment.finality (CHECK) | b | FINAL judgments only are offered as binding precedents |
| LIBRARY_RELATION_TYPE (نوع العلاقة بين الأحكام) | library relations section | library_judgment_relation.relation_type (CHECK) | b | overturned/modified relations change the validity warning of a judgment |
| CLASSIFICATION_DIMENSION (بُعد التصنيف) | library classifications page | library_classification.dimension (CHECK) | b | classification trees and similar-judgment matching are built per dimension |
| DOCUMENT_LINK_TYPE (نوع ربط المستند) | document links | document_link target types (Java constants) | b | each type resolves to a different record table |
| ENFORCEMENT_STATUS (حالة التنفيذ) | judgment enforcement form | enforcement_file.status (CHECK) | b | the enforcement tracking and money rules transition on these codes |
| ENFORCEMENT_ACTION_KIND (نوع إجراء التنفيذ) | judgment enforcement action form | enforcement_action.kind (CHECK) | b | OBSTACLE actions mark the file as obstructed |
| LEGAL_DOCUMENT_STATUS (حالة المستند القانوني) | legal document status moves | legal_document.status (CHECK + approval workflow) | b | approval/sending is a workflow with its own permission |
| SETTLEMENT_STATUS (حالة التسوية) | settlement card | settlement.status (CHECK + approval workflow) | b | proposal/approval/execution is an approval workflow (SETTLEMENT_APPROVE) |
| DIRECTIVE_STATUS (حالة التكليف) | directives filter and card | directive.status (CHECK) | b | open directives drive follow-up and reports |
| TEMPLATE_VERSION_STATUS (حالة نسخة القالب) | templates page, defence wording library | form_template_version.status, defense_template.status (CHECK) | b | only APPROVED versions can be used to draft documents |
| ORG_LEVEL (مستوى الوحدة التنظيمية) | organisation units page | org_unit.level (CHECK) | b | the tree is sector > department > section; scopes and reports roll up by level |
| ACCESS_SCOPE_TYPE (نوع نطاق الصلاحية) | access scopes page | access_scope.scope_type (CHECK) | b | CaseAccessPolicy evaluates each scope type by code |
| ORG_TREE (شجرة الهيكل) | organisation units page | org_unit.tree (CHECK) | b | LEGAL is the department's own tree and AUTHORITY the authority's; access and case forms read them separately |
| JUDGMENT_TYPE, JUDGMENT_NATURE, DOCUMENT_CATEGORY | judgment results form, document category pickers (archive, legal documents, search) | lookup_value (existing) | a | — |
| SETTLEMENT_KIND, DIRECTIVE_KIND, TEMPLATE_KIND | settlement/directive/template forms and filters | managed_list_value (existing) | a | — |
| COURTS | case registration, proceedings, reports court pickers; managed on /courts | court (existing register) | a | — |

58 catalogue lists in `lookup_value` (class a: 3, class b: 55), plus the existing managed lists and courts.

## Not included, with the reason

| Dropdown | Why it is not a catalogue list |
|---|---|
| Counsel kind (lawyer/agent) in case counsel section | the section, its data and API are deleted by the case-view stream (owner decision), so the list has no screen |
| Library classifications picker (library search, judgment form) | its options are records of the library_classification table, which has its own management page (/library classifications), not a fixed list |
| Roles picker (access scopes, user forms) | options are rows of the roles table, managed on the Roles page |
| Users / members / units / parties / proceedings / judgments / documents / templates pickers | options are records (people, organisation units, case records), not dropdown lists |
| Audit page filters (action, outcome) | values are system-generated event codes written by the code, not choices a user makes |
| Report grouping and sort pickers (wp8 groupBy, status group OPEN/CLOSED, inventory/judgment sorts) | they select a computation or a column of one report, not data values |
| Search type (case, party, hearing, …) and assistant task pickers | they select a system module/feature, not a list of values |
| Yes/No, any/true/false, unread/read, asc/desc, due-filter, active/inactive filters, financial impact YES/NO | fixed boolean/state toggles of a query; there is nothing to add or relabel as data |
| Language switch | two supported locales, handled by the locale layer |

## Other limits

* Courts are managed on their own screen (`/courts`, `COURT_MANAGE`), linked from the page, not inline.
* A new option in a class-a list is selectable wherever that list is a dropdown (case importance, claim kind, evidence
  importance, judgment type/nature, document category, settlement/directive/template kinds, courts).
* Label edits apply to the codes that exist in the catalogue; an option added to a class-a list has one label per language
  (`labelAr`, `labelEn`).
* Class assignment is conservative. Class a is only the lists whose codes no logic reads (case importance, claim kind,
  evidence importance and the three already-managed lookup lists). The class-b reasons name the logic found while taking
  the inventory; the ones for enum codes read in the services (evidence role, decision type, party kind, defence
  kind) were checked in the code, the others (e.g. notification type, calendar entry type, risk lists, party role) follow the
  module design and were not each traced line by line. Moving a list to class a means the same conversion done for
  claim kind and evidence importance (enum to string, CHECK to catalogue validation) and is a separate change.
* Only the registered message prefixes relabel. A label family that merely displays a code through another key (timeline
  event kinds, notification texts, calendar status) is not part of a list and keeps its built-in wording.
