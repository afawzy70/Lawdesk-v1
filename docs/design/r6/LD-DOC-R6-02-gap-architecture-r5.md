# LD-DOC-R6-02 — Gap analysis, architecture proposal, R5 compatibility (M0)

Baseline: `origin/develop` = `18822a83bff8e2a38b83db4370674a9f69919d81` (the SHA served by UAT; develop has not moved since). R6 branches from it. PR #114 (R5) is **not** in this base and is not touched.

## 1. EXISTING on develop (verified by reading the tree; nothing here is R6)

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

## 3. Domain model — PROPOSED, NOT IMPLEMENTED (tables new unless stated; all `uuid` ids, `version` for optimistic locking, `created_*/updated_*`)

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

## 4. Compatibility with R5 (matrix built from the actual R5 diff, `develop...feature/LD-DOC-r5-screens`, 195 files)

State of R5: PR #114 is an unaccepted Draft and, per the owner, independently **QA-rejected**. GitHub shows no review or comment on #114 (the verdict is owner-stated; recorded here as given). Nothing from R5 is assumed merged; R6 branches from develop `18822a8` and must work with or without R5.

| Area | R5 change (in #114, unmerged) | R6 touch (PROPOSED) | Conflict | Mitigation / order independence |
|---|---|---|---|---|
| Migrations | V860, V861 (lists), V880 (rename counsel table), V881 (case requests) | M1+ new numbers | Number clash only if R6 picks one R5 uses | Plan range V900+; script rescan before each migration (03 §1.2). Flyway `out-of-order` makes either merge order apply cleanly |
| `Permission.java` | +`LIST_ADMIN` (2 lines) | + R6 authorities | Textual merge conflict in the same enum/constants block | Add R6 entries in their own block at the end; resolve mechanically on rebase |
| `App.tsx`, `AdminTabs.tsx` | +6 / +2 lines (routes/tabs for lists, case access) | New routes (settings, complaints, post-judgment) | Textual conflict, same files | New route files; one-line registrations placed in separate hunks |
| Deadlines UI (`features/deadlines/*`) | **Deletes the `/deadlines` register screen**; rules/holidays move to `/admin`; messages `deadlines.*` edited | Settings screen needs rules/holidays/settings | If R6 edited `DeadlineSettingsPage`/`RulesPanel` it would conflict with R5's move/deletion | R6 does **not** edit any file under `features/deadlines`; its settings screen is a new component under `/admin`. Backend `DeadlineSettingsController` is unchanged by R5 (not in the diff) |
| Proceedings UI (`ProceedingForms.tsx`) | edited | M1 adds capacity/appeal UI | Possible textual conflict | R6 adds new components; if the form must change, the edit is isolated in its own commit |
| Case view and registration (`CaseDetails*`, `CaseCreatePage`, `IntakeRequest`, `IntakeService`, `CaseService`, `CaseDetails.java`) | Removes status/lawyers sections, merges bodies, adds requests and next hearing, new fields in `IntakeRequest` | M5 referral calls case intake; case view gains complaint link and post-judgment panel | Same classes and DTO | M5 uses the case-intake **service interface** and adds only a new optional field; no R6 code reads R5-only fields. Referral integration test runs on both bases (develop and develop+R5) before M5 is submitted |
| Case access (`features/access/*`) | Moves grants/confidentiality to `/admin/case-access` | Complaint access list reuses the grant concept | UI only | R6 backend reuses `CaseAccessPolicy` unchanged; no UI file shared |
| Catalogue (`catalog/*`, lists) | ~58 lists admin-managed | New code lists (appeal type, complaint kinds) | R6 lists would be catalogue-managed only if R5 lands | R6 registers its lists through the develop mechanism (`lookup_value`) and does not depend on the R5 registry; reconcile on rebase |
| Judgments/deadlines/notifications backend | **Not in the R5 diff** | Core of R6 | None | — |
| Tests | R5 changes `CaseVisibilityApiTest`, `CaseTestData` | R6 adds own tests | Fixture edits | R6 does not edit shared fixtures; uses its own builders |

Rule: R6 never rebases onto R5 until the owner decides R5 and Codex accepts it; the post-R5 rebase is a separate reviewed step. If R5 is rejected and reworked, R6 is unaffected. R6 does not read or write `case_party_counsel`.

## 5. Risks

1. Direct-manager data does not exist (D-14).
2. Legal parameters are unapproved; seeded values stay inactive (D-01..D-04).
3. `judgment` one-per-proceeding fits the document (judgment plus its appeals), but a re-judgment after remand would need a second judgment per stage — not requested; recorded under D-08.
4. Rebase friction with R5 (§4).
5. Scheduled jobs: must be idempotent across restarts and multiple instances (unique alert key plus advisory lock).
