# LD-DOC-R6-05 — Authoritative design: authorization, confidentiality, data model, legal gates, alerts (M0, PROPOSED)

Everything below is **PROPOSED**; nothing is built. "EXISTING" statements cite files read at develop `18822a8`. Test ids refer to the register (`LD-DOC-R6-01`).
These choices are made **in M0** so that M1 is not asked to decide them.

## A. Authorization: technical ADMIN must not inherit legal authority (QA-R6-M0-001, -014)

### A.1 EXISTING facts (verified)
* `LawdeskUser.of(account)` (security/LawdeskUser.java:36-38): if the account has role ADMIN, `granted` = **every** `Permission` enum value (`Arrays.stream(Permission.values())`) and the account's own `role_permission` rows are **ignored**; otherwise only known codes from the account's roles. The javadoc says this includes "codes added after the role was seeded".
* Therefore any value added to `Permission` is automatically held by every ADMIN. A case-level filter cannot remove an *authority* that the principal already holds; it only filters data. Case-level filtering alone does **not** solve the defect.
* `ReloadingSecurityContextRepository` rebuilds the principal from the database on **every request** (roles and permissions apply to the next request; disabled users are signed out; `credentialVersion` revokes sessions on credential change).
* `users/RoleAdministration`, `users/UserAdministration` and `access/AccessAdministration` exist and reference the ADMIN role; their exact powers (whether an ADMIN may attach any permission to a role or user) were not traced in M0 and **must be confirmed by the first M1 test (T-SEC-5) before the refusal in A.2(3) is relied on**.

### A.2 Design
1. **Classification on the enum, fail-closed.** `Permission` gains a constructor argument `adminInherited` (boolean). All existing values are `true` (preserving every legitimate admin capability, T-SEC-2). Sensitive R6 authorities are `false`: `POSTJ_APPROVE`, `APPEAL_SETTINGS_APPROVE`, `COMPLAINT_VIEW`, `COMPLAINT_CREATE`, `COMPLAINT_EDIT`, `COMPLAINT_PREPARE_DECISION`, `COMPLAINT_APPROVE`, `LEGAL_GATE_APPROVE`, `LEGAL_AUTHORITY_CUSTODIAN`. A unit test enumerates `Permission.values()` and fails if any value is not explicitly classified (T-SEC-3).
2. **`LawdeskUser.of` rewrite.** `granted = (ADMIN ? { p : p.adminInherited } : ∅) ∪ account.permissions()` — the explicit grants of **all** the account's roles are always added, so an ADMIN who also holds LEGAL_MANAGER receives the union and nothing is lost (T-SEC-4). Sensitive authorities reach ADMIN only through an explicit grant (A.3).
3. **Sensitive grants are not editable through ordinary role/user administration.** `RoleAdministration` and `UserAdministration` refuse to attach a non-inherited sensitive authority to a role or user. They are held in `legal_authority_grant` (A.3), read into the account at load (`UserAccounts`), and exposed as authorities.
4. **Spring Security.** Endpoints keep `@PreAuthorize("hasAuthority('…')")` using new `Permission.Expr` constants; the service layer additionally calls `AuthorityScope.require(actor, authority, target)` (A.4) so authority is re-read **inside the transaction** from the database (not only from the request principal).
5. **Existing role migration (M1/M2 migrations, additive):** insert `role_permission` rows giving the non-sensitive R6 permissions (`POSTJ_VIEW`, `POSTJ_PREPARE`, `APPEAL_SETTINGS_VIEW`, `APPEAL_SETTINGS_MANAGE`) to LEGAL_MANAGER, and `APPEAL_SETTINGS_MANAGE` to ADMIN via the inherited flag; LEGAL_STAFF gets VIEW/PREPARE. **No sensitive authority is inserted by any migration.** Until the owner designates a custodian (D-09), no sensitive grant exists anywhere and every gated and approval action is refused (fail-closed).

### A.3 Grants, revocation, delegation, audit
`legal_authority_grant(id, grantee_id, authority, scope_type ALL|UNIT|CASE_TYPE, scope_ref, valid_from, valid_to, requested_by, approved_by, approved_at, revoked_at, revoked_by, revoke_reason, version)`:
* A grant needs two distinct identities: requester and approver; the approver holds `LEGAL_AUTHORITY_CUSTODIAN` (designated by the owner; **the first designation is an owner decision, D-09**, and is recorded by an owner-approved operation, never by a migration or ADMIN role alone). Self-approval is refused (T-SEC-5). The DB enforces `requested_by <> approved_by <> grantee_id` for approval-type authorities.
* Revocation is a row update by an authorised custodian with a reason; it takes effect on the next request because the principal is rebuilt from the database each request (verified) and in-transaction re-read covers requests already in flight (T-SEC-6).
* `legal_authority_delegation(id, delegator_id, delegate_id, authority, scope_type, scope_ref, valid_from, valid_to, reason, approved_by, revoked_at…)`: the delegate acts only within the delegated scope and window; a delegation is void the moment the delegator's grant is revoked or expires (checked at use); no sub-delegation; a delegate cannot approve what they prepared (T-SEC-7).
* Every grant, revocation and delegation writes an `audit_event` with actor, before/after (T-SEC-8). The approval records of all workflows store `grant_id` (or `delegation_id`) that authorised them, so the authority *as it was at approval time* is reconstructible.
* Stale-session handling: no cached authority; a revoked authority between page load and submit yields 403 with no write (T-SEC-6, T-SEC-10); `credentialVersion` is untouched by authority changes (they take effect without forcing sign-out).

### A.4 Scope validation
`AuthorityScope.require(actor, authority, target)`: (1) an active grant or delegation of the authority exists for the actor now; (2) its scope covers the target (case type / owning unit / all, using the existing `case_in_scope` semantics; for a complaint its owning unit); (3) the actor passes the target's visibility predicate (`case_visible` or `complaint_visible`); (4) maker≠checker where the action is an approval. Any failure → 403/404 per the visibility rule, evaluated **before** validation, with no write.

### A.5 Tests with real ADMIN principals
T-SEC-1 (a principal built by `LawdeskUser.of` for an ADMIN account **and** a real HTTP login of an ADMIN test user: no sensitive authority, 403 on every approval/complaint endpoint), T-SEC-2…T-SEC-10, T-ST01-2. State-before/after digests on every denied write.

## B. Complaint confidentiality and the referral security model (QA-R6-M0-002, -009)

### B.1 EXISTING facts (verified)
`legal_case.confidentiality` NORMAL|RESTRICTED (V600:34-35); `case_in_scope` returns false for RESTRICTED, so a restricted case is visible only to its primary/alternate member, its creator, holders of an active `case_grant`, and users with an open task on it (`case_visible`, V600:154-175, V720). `LEGAL_MANAGER` has scope ALL over non-restricted cases (V600:79-80).

### B.2 Rules
1. A complaint is always confidential by itself: visible only through `complaint_visible(complaint, user)` = (`COMPLAINT_VIEW` authority **and** (responsible researcher OR explicit complaint access-list entry OR organisational scope of the owning unit)) — ADMIN has no access by role (A).
2. **Atomic inheritance.** Referral runs in one transaction: lock complaint → insert the linked case with `confidentiality = 'RESTRICTED'`, `primary_member_id` = the complaint's responsible researcher, `owning_unit_id` = the complaint's owning unit → insert the transfer copies → insert the link → status change + history. Any failure rolls back everything (T-CONF-6). Lowering the case to NORMAL is a separate approved, audited action (`COMPLAINT_APPROVE`), never automatic (T-CONF-10).
3. **Transfer manifest instead of wholesale copy.** The preparer lists what moves to the case (respondents as parties, incident facts text, selected documents); the checker approves the manifest. Complaint-only material (sessions, prosecution requests, investigation memoranda, internal notes, unselected respondents' identifiers, documents not selected) **never** moves. Everything transferred becomes ordinary case data under case rules.

### B.3 Surfaces (each has a named negative test)
| Surface | Rule | Test |
|---|---|---|
| Linked case DTO, timeline, events | no complaint id, number, prosecution reference, link or provenance label; the case-opening event text is neutral | T-CONF-7 |
| Parties | transferred parties carry no "from complaint" marker visible to case-only viewers; identity resolution uses the existing duplicate detection, never auto-merge | T-CONF-7 |
| Documents/attachments | **ownership**: one owner per document row (complaint or case). Transfer = a new `case_document` row (content copy under the case's own access path); the complaint document remains reachable only via `complaint_visible`; no shared access path, no cross-reference column readable by case viewers | T-CONF-2, T-CONF-7 |
| Search / OCR text | complaint documents are indexed under a `complaint_visible` predicate; case documents under `case_visible`; hits never mention the other side | T-CONF-2 |
| Reports, exports, dashboards | each aggregate applies its own predicate; "cases from referral" counts apply **both**; no "N hidden" totals | T-CONF-3, T-CONF-5 |
| Notifications / alerts | created only for users who pass the relevant predicate at creation; `params` carry the internal complaint reference only, never respondent names or incident text; presentation re-filters | T-CONF-4 |
| Reverse navigation | complaint→case shows the case identifier only if the viewer passes `case_visible`; case→complaint shows the complaint only if the viewer passes `complaint_visible`; otherwise the field is absent (not "restricted") | T-CONF-7, T-CONF-8 |
| User who sees the case but not the complaint | sees the case under restricted-case rules and the transferred data; nothing indicates a complaint (the capacity field «مجني عليها / شاكية» is case data and may reveal a criminal context — accepted, recorded in D-20) | T-CONF-7 |
| Task assignee on the restricted case | `case_visible` grants visibility through an open task; because only the manifest was transferred, nothing complaint-only is exposed | T-CONF-9 |
| Audit viewer | complaint audit rows only for users passing `complaint_visible` | T-CONF-5 |

### B.4 Referral adapter for the mandatory intake fields (QA-R6-M0-009)
Target: a dedicated `ReferralCaseFactory` that builds the existing intake command through the case-intake **service boundary** (so it compiles on develop and on develop+R5, T-R5-1). `IntakeRequest` requires: `automatedNo`, `title`, `caseType`, `openedOn`, `firstInstance{courtId, courtCaseNumber, courtCaseYear, filingDate}`, `authorityPosition`, `primaryMemberId`, `financialImpact` (+amount) (IntakeRequest.java:24-43,56-69). The source referral supplies: referral date, competent court, case number, charges (¶194).

| Intake field | Source of the value | If unknown |
|---|---|---|
| firstInstance.courtId | referral «المحكمة المختصة» | refused |
| firstInstance.courtCaseNumber | referral «رقم القضية» | refused |
| firstInstance.courtCaseYear, filingDate | **entered by the preparer** (not in ¶194) | refused |
| automatedNo, title, caseType, openedOn, financialImpact | **entered/confirmed by the preparer**; the form may *propose* (e.g. opened-on = referral date) but the stored value is the confirmed one, recorded in the approval | refused |
| authorityPosition | mapping of «مجني عليها / شاكية» to a case side — **owner decision D-25** | refused |
| primaryMemberId | the complaint's responsible researcher | refused |
| parties | respondents chosen in the manifest, resolved through the existing party registry (duplicate warning → `confirmDistinct` only by explicit user choice) | refused |

Incomplete data: the referral decision (PC-10) is recorded and the status becomes «محالة للمحكمة», the link is empty and the case state is `PENDING_DATA` with a task for the researcher; **no value is invented and no case is created** (T-PC11-3). Duplicate protection: unique `complaint_case_link(complaint_id)` and unique `(case_id)` + an idempotency key on the referral request; a retry or concurrent call returns the existing case (T-PC11-2). The link is **one** authoritative row `complaint_case_link(complaint_id unique, case_id unique)`; both directions are derived from it, so they cannot disagree (T-PC15-3) (no column is added to `legal_case`; the earlier plan for `legal_case.origin_complaint_id` is withdrawn).

## C. One authoritative data model for judgments, deadlines, appeals and stages (QA-R6-M0-004, -005, -015)

### C.1 EXISTING facts (verified)
`proceeding` (V301): stage, court, number/year, filing date, `parent_proceeding_id`, unique `(court_id, court_case_year, lower(court_case_number), stage)`; `judgment` (V302): `proceeding_id` unique (one judgment per proceeding), `outcome`, `final`; V621: `operative_text`, `judgment_status`; V843: `judgment.appeal_deadline_id` → `deadline` with a trigger requiring `source_type='JUDGMENT'` and `source_id = judgment.id`; `deadline` (V681): due date, status OPEN/COMPLETED/MISSED, calc_status MANUAL/PROPOSED/APPROVED, approval, `change_reason`; `case_position` (V803) kept by triggers from proceeding rows; `DeadlineCalendarSource` shows approved/manual deadlines; `DeadlineReminderRun` reminds open approved deadlines; `ProceedingService` takes `lockedProceeding(parent)` before creating a child (QAD-024).

### C.2 Decisions (made now)
| Question | Decision |
|---|---|
| Where does the due date live? | **Only in the existing `deadline` row** (single source of truth for due date, status, approval; calendar, reminders and reports keep reading it). |
| Party-specific attributes | `judgment_party_deadline(id, judgment_id, party CITRA\|OPPONENT, appeal_type, start_on, start_basis, deadline_id unique NULL, resolution OPEN\|APPEALED\|NOT_APPEAL_DECIDED\|NO_APPEAL_CONFIRMED\|VOID, legal_basis_status, version…)`. `deadline_id` is **NULL until a legally effective calculation exists** (M2, gate G-CALC); M1 rows are drafts without due dates. |
| Judgment procedural record | `judgment_procedural(judgment_id pk, procedural_status, citra_decision, opponent_position, judicial_status, finality fields, version)` — a **separate table** (the existing `judgment` rows and the old release's reads are untouched). Operative text = existing `judgment.operative_text`. |
| `judgment.appeal_deadline_id` | **Legacy pointer, not the source of truth.** Never rewritten for a judgment that already has a value. For a new judgment R6 sets it to the CITRA party deadline's `deadline` row (when it exists) so the previous release still shows a sensible value; a reconciliation test asserts equality (T-DATA-1). Legacy deadlines without an extension row are displayed as «سجل سابق» and may be *adopted* as CITRA or OPPONENT's deadline by an authorised user with a reason (audited); **no migration backfills or assigns them**. |
| Cardinality | per judgment, party and appeal type: at most one non-VOID party deadline (partial unique index); one `deadline` row ↔ at most one extension (unique `deadline_id`); trigger: the `deadline` must be `source_type='JUDGMENT'` with `source_id = judgment_id` (T-DATA-2, T-DATA-3). |
| Appeal filings | `judgment_appeal(id, judgment_id, party, appeal_type, appeal_number, registered_on, filed_on, court_id, chamber, citra_capacity, party_deadline_id NULL, proceeding_id NULL, status FILED\|WITHDRAWN\|VOID, …)`, unique `(judgment_id, party, appeal_type)` where status ≠ VOID. Filing ≠ proceeding: a filing *maps* to a child proceeding. |
| Filing → proceeding | Creating the child goes through the existing `ProceedingService` (ladder, court level, parent judged, filed-after-judgment checks). Two filings against the same judgment may map to **two child proceedings** (separate court numbers) or to the **same** child when the court joined them (the preparer picks the existing child; shared `proceeding_id`). The unique court-number index continues to prevent duplicate numbers. Cassation after appeal repeats the pattern one level up. Nothing is created automatically after the highest configured level (D-08). |
| `case_position` | unchanged: it is maintained by triggers from proceeding rows (highest stage); R6 never writes it (T-DATA-9). |
| Stage capacity | nullable `proceeding.citra_capacity` (catalogue code), additive. |
| Legacy `judgment.final` | set to true **inside the finality transaction** (and nowhere else) so older code and reports agree (T-DATA-10). |
| Completing a party deadline | filing an appeal sets the party's `resolution = APPEALED` and its `deadline` COMPLETED with the filing date; an approved non-appeal decision sets `NOT_APPEAL_DECIDED` and COMPLETED (the required action — deciding — is done), confirmed «no appeal» sets `NO_APPEAL_CONFIRMED` and COMPLETED; `resolution` is what the alert job and the finality check read. |
| Calendar | unchanged source (`deadline`); party shown by joining the extension (M3) (T-DATA-8). |
| Reminders | `DeadlineReminderRun` is changed in M3 to **exclude** deadlines that have an extension row; the R6 alert job owns them; legacy deadlines keep their reminders (T-DATA-7). |
| Reports | existing judgment/deadline reports read `deadline` and `judgment` and keep working; extra columns are additive (T-DATA-8). |
| Historical records | no backfill; old judgments get a procedural record only when someone opens the post-judgment panel and registers it (draft). |

### C.3 Concurrency and transaction order (QA-R6-M0-005)
Global lock order for every R6 write that touches a judgment: **(1)** parent `proceeding` row `for update` (the existing `lockedProceeding`) → **(2)** `judgment` → **(3)** `judgment_procedural` → **(4)** `judgment_party_deadline` rows ordered by party (CITRA, then OPPONENT) → **(5)** inserts of filings, child proceeding, history. Finality and appeal filing both start at (1), so they are serialized: filing after finality is refused (`JUDGMENT_FINAL`), finality after a filing is refused (`APPEAL_FILED`), and finality re-reads non-VOID filings and running deadlines **after** taking the locks. Two different parties filing concurrently both succeed in either order; the same party twice yields one row (unique index) and the replay returns it. Optimistic `version` still guards single-row edits (409) but is not the serialization mechanism. Tests: T-DATA-4, T-DATA-5, T-DATA-6 (two real connections with a latch on PostgreSQL 18; 50 mixed operations for deadlocks).

### C.4 Simultaneous CITRA and opponent appeals
Both filings exist as independent rows; each resolves its own party deadline; the judicial status becomes «منظورة …» on the first filing and stays so; no filing is overwritten; capacity is per filing (CITRA appellant on its own filing, respondent on the other's). Matrix 03 §3.1 states one status with two filings.

### C.5 Immutable calculation evidence (QA-R6-M0-015)
`deadline_calculation_evidence(id, deadline_id, seq, kind INITIAL\|RECALCULATION\|MANUAL_APPROVED, inputs jsonb, result_due_on, computed_by, computed_at, reason, approval_id, grant_id, sha256)`, **insert-only** (trigger refuses UPDATE/DELETE). `inputs` embeds everything needed to reproduce the result without reading mutable tables: start date and basis, setting id and version, applied days, count method, `counts_start_day`, `roll_forward`, closed weekdays, **the holiday list actually used** (date, label, source, row id, row status/version) and the legal reference. A later holiday or setting edit never alters stored evidence; a recalculation appends a new row (both complete versions remain) (T-DATA-11, T-PJ16-2, T-ST05-1).

## D. Legal activation gates (QA-R6-M0-006)

### D.1 Mechanism
`legal_approval(id, subject_type, subject_id, subject_version, scope, effective_from, effective_to, legal_reference, decision_document_ref, approver_id, grant_id, approved_at, status ACTIVE\|SUPERSEDED\|REVOKED)` — versioned, insert-only (revocation = a new row). `LegalGate.require(subject, scope, date)` is called in the **service layer** at every action that gives legal effect; there is no controller-only check. DB defence in depth: a trigger refuses `deadline_setting.status = 'ACTIVE'` unless an ACTIVE approval for that exact version exists, and refuses inserting an approval whose approver lacks an active grant or equals the setting's author (T-GATE-3, T-GATE-7).

### D.2 What is gated and what is not
**Fact capture is never blocked** (an appeal was filed; the opponent says it appealed; a date was served): it is recorded with `legal_basis_status = UNAPPROVED`. **Legal effect is gated**: computing or activating a due date (G-CALC: D-01..D-04), settings use (G-SETTING: D-01, D-16), opponent start rule (G-OPPONENT: D-05), finality (G-FINAL: D-07), manual override (G-OVERRIDE: D-13), grievance deadline (D-10 → G-CALC), manager recipients (G-ALERT: D-14, recipient policy approved), post-grievance path (G-GRIEVANCE: D-11), stage model beyond the ladder (G-STAGE: D-08), reopening (G-REOPEN: D-24).

### D.3 Bypass closure
| Path | Closed by | Test |
|---|---|---|
| Seed data | seeds are DRAFT; `legal_approval` has no seed rows; trigger blocks ACTIVE | T-GATE-2, T-ST03-1 |
| Manual duration | refused without an approved override policy; even then each use needs a checker approval; before that only a non-effective *proposed* value is stored, with no due date | T-GATE-4, T-ST07-2 |
| Fallback/default values | none exist; start-date default is a draft prefill of a fact, never a period; static scan | T-GATE-4, T-PJ11-1 |
| ADMIN permission | A (not inherited) | T-SEC-1 |
| Direct API / direct SQL | service-layer gate + DB trigger | T-GATE-1, T-GATE-3 |
| Background jobs | selection requires `calc_status = 'APPROVED'` **and** an ACTIVE approval covering the deadline's evidence; otherwise skip and write nothing | T-GATE-5 |
| Changed rule | new version needs a new approval; revocation closes the gate again | T-GATE-6 |

### D.4 "No persistent side effects" — exact negative test
For each gated action: capture row counts **and** content digests of every R6 table, `deadline`, `judgment`, `proceeding`, `case_position`, `notification`, `audit_event` and `legal_case`; call the action by (a) the normal API with an authorised user, (b) ADMIN, (c) a direct service call, (d) the scheduler; assert HTTP 4xx with `LEGAL_GATE_CLOSED` (or no-op for jobs) and **identical digests afterwards**. Refusals are written to the application log only, not to domain tables or `audit_event`. (T-GATE-1)

### D.5 M1 is schema, history and draft only
M1 contains: tables, constraints, history/append-only triggers, draft capture of procedural status and fact records (judgment details, party start dates and basis, filings recorded as facts), UI to view/enter them. M1 **excludes**: any due-date computation, effective manual duration, finality, automatic next-stage creation, settings activation, alerts. See 04 §D.

## E. Settings overlap, precedence and boundaries (QA-R6-M0-008)
Scope of a setting: `(judgment_level, appeal_type, case_type or ALL, effective_from, effective_to)`, ranges **half-open** `[effective_from, effective_to)`; `effective_to` is the first day it no longer applies (NULL = open-ended); `effective_to > effective_from`. Two ACTIVE settings **conflict** iff same level, same appeal type, their date ranges intersect, and (same case type **or** either is ALL). Enforcement: a trigger on activation takes `pg_advisory_xact_lock(hash(level, appeal_type))` and runs the intersection query (so `ALL` vs a specific type is caught — plain equality exclusion would miss it); a `btree_gist` exclusion on `(level, appeal_type, case_type, daterange)` is kept as a backstop for identical scopes. Because overlaps are refused, **no precedence rule is needed**: at most one active setting matches a given level, appeal type, case type and date. Which date matches: the date the judgment is **registered** in LawDesk (consistent with ¶94 "new judgments only"), stored in the evidence — a legal question recorded in D-16. Adjacent ranges ([a,b) then [b,c)) are allowed (T-ST04-2); concurrent activations: one wins, the other gets 409 (T-ST04-4). Remains inactive until D-16 and D-01 are approved.

## F. Alert idempotency, visibility and retry lifecycle (QA-R6-M0-013)
`alert_history(id, kind, subject_type, subject_id, subject_version, offset_days, recipient_id, notification_id, created_at, suppressed_reason)` with **unique** `(kind, subject_type, subject_id, subject_version, offset_days, recipient_id)`; the existing `notification.dedupe_key` (unique, V664) is set to the same tuple string. `subject_version` = the evidence `seq` of the deadline (or the complaint session/request version), so:
* retry, restart and two application instances: `INSERT … ON CONFLICT DO NOTHING` + `pg_try_advisory_xact_lock` per run → one alert (T-ALERT-1, T-AL06-2);
* recalculation → new evidence `seq` → fresh alerts for the new dates; the old ones are not retracted (T-ALERT-2);
* offset configuration change → only offsets not yet raised for that version are considered; removing an offset retracts nothing;
* resolution (appeal, approved non-appeal, confirmed no-appeal, non-grievance, grievance filed) → the job skips that party's subject (`suppressed_reason` recorded when it would otherwise have fired);
* a job failure creates no row; the next run retries (no half state);
* **recipient visibility** is checked at creation (predicate of the record) and again when notifications are listed; a recipient whose access/scope/authority is revoked gets no new alert and cannot read old ones (T-AL06-3, T-CONF-4);
* manager policy: DISABLED (default) → researcher only; enabling later does not back-fill past offsets (T-ALERT-3); missing mapping → researcher only plus a data-quality notice to ADMIN users without case details (T-ALERT-4);
* delivery status = in-app created/read (D-15); the `notification` payload carries references only, no case or respondent text.

## G. Grievance and closure guard (QA-R6-M0-007)
`complaint_grievance(id, complaint_id, filed_on, status PENDING\|DISPOSED, disposition, disposed_on, …)`. Closure of a «محفوظة» complaint is allowed only if **no grievance is PENDING** and (a non-grievance decision is approved **or** the grievance deadline has elapsed with no grievance filed). A filed grievance therefore keeps the complaint open until a recorded disposition; the allowed dispositions and what follows (including referral) are D-11 (BLOCKED), so until approved the complaint stays PENDING and cannot be closed (T-PC09-2, T-PC14-2).

## H. Compatibility with previously deployed releases and PostgreSQL 18 (QA-R6-M0-011)
EXISTING configuration (`application.yaml:17-24`): `ddl-auto: validate`, `out-of-order: true`, `ignore-migration-patterns: "*:future"`. These settings are **necessary but not sufficient evidence**: they show intent, not that an older release runs on an R6 schema. Required proof per milestone: T-MIG-1 … T-MIG-6 — fresh PostgreSQL 18 database; restored synthetic pre-milestone dump; **previous release jar** starting on the migrated schema, logging in, doing case/judgment/deadline CRUD and one reminder-job cycle with R6 tables populated; second start applies nothing; restore and forward-recovery rehearsals; and, only if R5 is later accepted, every installation order (T-MIG-5). `out-of-order` is never cited as proof that two branches' migrations coexist.
