# LD-DOC-R6-02 — Gap analysis, architecture proposal, R5 compatibility (M0)

Baseline: `origin/develop` = `18822a83bff8e2a38b83db4370674a9f69919d81` (the SHA served by UAT; develop has not moved since). R6 branches from it. PR #114 (R5) is **not** in this base and is not touched.

## 1. What develop already has (verified by reading the tree)

| Area | Found | Consequence for R6 |
|---|---|---|
| Judgment | `judgment` (V302): one per `proceeding` (unique), `outcome` IN_FAVOUR/AGAINST/PARTIAL/DISMISSED/OTHER, `summary`, boolean `final` (informational), `appeal_deadline_id` (V843, trigger checks the deadline runs from this judgment); `JudgmentStatus` ISSUED/NOTIFIED/CHALLENGED/OTHER | Keep; extend. The old status is a different concept from the document's procedural status and must not be reused (R6-03) |
| Stage | `proceeding` has stage, number, court, chamber; `case_position` caches the current stage | Next stage = a new `proceeding` row; capacity column is missing |
| Deadlines | `deadline` (V681: source JUDGMENT, starts_on, period_days, due_on, rule_id, owner, status, calc_status, approval, change_reason), `deadline_rule` (V680: period, CALENDAR/WORKING, counts_start_day, roll_forward, closed weekdays, legal source, effective_from, DRAFT/APPROVED/RETIRED), `official_holiday`, `deadline_reminder_setting` (one row of lead days), `DeadlineCalculator`, `DeadlineReminderRun`; permissions `DEADLINE_VIEW/MANAGE/APPROVE` | Reuse the calculator, holiday data and approval pattern. The rule table has **no** judgment level, appeal type, case-type scope or start basis; per-party deadlines do not exist |
| Notifications | `notification`, preferences, types incl. `APPEAL_DEADLINE`, `JUDGMENT_NEW`; in-app only (decision D11) | Extend the type list; no e-mail/SMS channel exists |
| Org / manager | `org_unit` tree (LEGAL, AUTHORITY); roles incl. ADMIN, LEGAL_MANAGER, LEGAL_STAFF | **No user → direct-manager relation anywhere in the schema** (searched all migrations). Blocks AL-01 and PC-21 recipient rule |
| Case links | `case_link` (V663) with unique pair+kind | Reused for complaint→case only if the link semantics fit; a dedicated bidirectional FK is cleaner (§3) |
| Lists | `lookup_value` catalogue (R3; R5 extends it, unmerged) | New code lists go through the catalogue mechanism that exists in develop, not hard-coded enums |
| Audit | `audit_event`, status/lookup history tables | Reused |
| Frontend | `features/proceedings`, `deadlines`, `cases`, `admin`, `calendar`, `casefile` | R6 adds routes/components; see §4 for the shared-file collisions with R5 |

## 2. Gaps (summary of the register)

Missing entirely: per-party deadlines, appeals as separate records with parties, appeal-decision table, procedural status and its history, finality confirmation, appeal-period settings with versions/overlap rule, recalculation history, post-judgment alert history, the whole complaint domain, the direct-manager relation.
Partial: judgment outcome mapping, capacity of CITRA per stage, reminder offsets (global today), counting method (present in rules, not tied to settings).
Conflicts between the document and the repository are listed in `LD-DOC-R6-04` §A.

## 3. Domain model proposal (tables new unless stated; all `uuid` ids, `version` for optimistic locking, `created_*/updated_*`)

```
judgment (existing) 1──* judgment_party_deadline            (party CITRA|OPPONENT)
judgment 1──1 judgment_procedural (status, citra_decision, finality fields)   -- or columns on judgment; decided in M1 review
judgment 1──* judgment_status_history
judgment_party_deadline 1──* deadline_recalculation          (before/after, reason, user)
judgment 1──* judgment_appeal (party, type, number, filed_on, court, chamber, citra_capacity, next_proceeding_id)
judgment_appeal 1──* judgment_appeal_document
judgment_party_deadline 1──0..1 appeal_decision              (decision, reason, approver, decided_on)
deadline_setting (kind APPEAL|GRIEVANCE, level, appeal_type, case_type|ALL, days, start_basis, legal_ref, status, effective_from/to, version_no, supersedes_id)
deadline_setting_history
post_judgment_alert / complaint_alert  -> one table `case_alert` (kind, subject_type, subject_id, offset_key unique with recipient) — idempotency by unique key
prosecution_complaint 1──* complaint_respondent
prosecution_complaint 1──* complaint_session, complaint_request, complaint_document(link)
prosecution_complaint 1──0..1 complaint_decision (SAVED|REFERRED) 1──* complaint_grievance_decision
prosecution_complaint 0..1──1 legal_case (linked_case_id, unique)  and legal_case.origin_complaint_id (unique)
complaint_status_history
```

Principles:
* Each party deadline stores a **snapshot**: days applied, start basis, count method, setting id and version, legal reference. A later setting edit never recalculates; recalculation is a separate audited action with a mandatory reason (PJ-16, ST-05).
* Judgment finality is a distinct explicit record (who, when, which conditions were evaluated); it is never computed from dates (PJ-09).
* An unknown opponent position is a state (`UNRESOLVED`), not "no appeal" (the document: §3 ¶43).
* Referral creates the case through the existing case-intake service in the same transaction, guarded by a unique constraint on `legal_case.origin_complaint_id` and an idempotency key on the request, so a retry returns the same case.
* Authorization: every endpoint goes through the existing `CaseAccessPolicy` (hidden → 404 with a body identical to an unknown id); a complaint not yet linked to a case has its own visibility rule (D-20).
* Counting reuses `DeadlineCalculator` with the method chosen by the approved setting (calendar/working, holidays) — no period literal in code (T-X03).

## 4. Compatibility with R5 (documented before any code)

* R6 branches from **develop**, not from `feature/LD-DOC-r5-screens`. R5's open state: #114 is an unaccepted draft; I found no recorded QA verdict in the PR or in #104 (the owner reports an independent REJECT; I treat that as given and do not touch #114 or its branches).
* Migration numbers: develop ends at V845; R2/R3 use V801-V803 and V850; R5 uses V860, V861, V880, V881. **R6 reserves V900–V949** (V900-V909 M1, V910-V919 M2, V920-V929 M3, V930-V939 M4, V940-V949 M5), re-checked against every remote branch before each merge request. Because R6 numbers are above every number used on any branch, they apply after all of them in either merge order.
* Shared UI/code touch points with R5 that will conflict at merge time: the case view page and tabs (R5 removes the lawyers section and the «الحالة» field, merges administrative bodies), the admin navigation and `/deadlines` (R5 deletes the register screen; R6 needs deadline *rules/holidays/settings* reachable from `/admin`), `IntakeRequest`/`CaseCreatePage` (R5 adds requests and next hearing; R6 referral reuses intake), the catalogue lists (R5 makes ~58 lists admin-editable). Strategy: R6 adds new files and routes; edits to shared files stay minimal and are listed per milestone in the PR so the later rebase is mechanical. R6 does **not** depend on any R5 behaviour; the post-R5 rebase is a separate, reviewed step after the owner decides R5.
* One real dependency to flag: if R5 is merged first, the `/deadlines` register disappears while R6 relies on the deadline rules/holidays administration. Rules and holidays are API-level in develop (`DeadlineSettingsController`); R6 puts its settings screen under `/admin` so it survives either order.
* `case_party_counsel` (R5 V880) is irrelevant to R6; R6 does not read or write it.

## 5. Risks

1. Direct-manager data does not exist (D-14).
2. Legal parameters are unapproved; seeded values stay inactive (D-01..D-04).
3. `judgment` one-per-proceeding fits the document (judgment plus its appeals), but a re-judgment after remand would need a second judgment per stage — not requested; recorded under D-08.
4. Rebase friction with R5 (§4).
5. Scheduled jobs: must be idempotent across restarts and multiple instances (unique alert key plus advisory lock).
