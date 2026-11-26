# LD-DOC-R6-04 — Decision register, test and journey plan, milestones (M0)

## A. Conflicts between the document and the repository (resolution proposed; none implemented yet)

| # | Conflict | Proposed resolution |
|---|---|---|
| C1 | §3 ¶44 says the start date defaults to the judgment date; §4.2 ¶70 gives each setting its own default basis (judgment date or notification date) | The setting's basis supplies the initial start date; with no matching setting the judgment date is used (the §3 default); always editable by an authorised user; each party separately |
| C2 | The document has three outcomes; the repository has five (DISMISSED, OTHER) | Registration offers the three; existing DISMISSED/OTHER rows stay readable and get no automatic workflow (D-17) |
| C3 | The old `judgment.status` ISSUED/NOTIFIED/CHALLENGED/OTHER and boolean `final` | Left untouched; the new procedural status is a separate field; the UI labels the old one «حالة قيد قديمة» only after owner review |
| C4 | Existing rule model (D6: no seeded rule, approval workflow) vs the document's seeded 30/15/30 | Seed as DRAFT/inactive; activation only through the approval action (matches the document: "تُراجَع وتُعتمد قبل تفعيلها") |
| C5 | §4 ¶96 requires notifying "مدير النظام"; the system only has in-app notifications | In-app notification to every ADMIN user (D-15) |
| C6 | Complaint ¶192 "بعد عدم التظلم تُغلق الشكوى" vs ¶201 "لا تُغلق قبل انتهاء الميعاد أو قرار بعدم التظلم" | Consistent; closure is an explicit authorised action available after either event; no timer closes anything |
| C7 | «الحالة بعد الحفظ: مقدمة للنيابة» (¶181) is read as "after saving" | Initial status set on save |
| C8 | "direct manager" (§7.1, ¶209) has no data source | Owner decision D-14 (B.1); manager notifications stay off until approved |

## B. Owner / legal decision register

Classes: **S** explicitly specified in the source · **L** already supported by approved LawDesk rules (EXISTING) · **O** requires owner/legal approval.
Status: **BLOCKED** = activation prohibited until the appropriate **legal authority** (CITRA Legal Affairs) approves and the approval is recorded; **OWNER-PENDING** = owner design decision outstanding, construction may proceed behind a switch; **PROVISIONAL** = owner direction given for review, *not legal approval*; **OPEN-SPEC** = source specifies it, implemented as written when M1+ starts.

**Activation gate (PROPOSED, applies to every BLOCKED row).** A gated behaviour is implemented but cannot run until an approval record exists (legal authority identity, date, reference to the decision, version of the setting). Until then: settings stay DRAFT/inactive, the server refuses the gated action with a stable rule code, and the UI explains why. A gate is opened only by `APPEAL_SETTINGS_APPROVE`/`COMPLAINT_APPROVE` holders recorded by name, never by ADMIN role alone, never by seed data or migration.

| ID | Question | Class | Status | Position / what stays blocked |
|---|---|---|---|---|
| D-01 | Legal durations and court/case-type applicability | S+O | **BLOCKED** | Source gives 30/15/30 only as initial values "to be reviewed and approved before activation": seeded DRAFT/inactive; no judgment can use a setting without the approval record |
| D-02 | Calendar vs working days | L+O | **BLOCKED** | Mechanism EXISTS (`count_method`, no default per D6); each setting must state it and have it approved |
| D-03 | Weekends, public holidays, legal extensions | L+O | **BLOCKED** | Holiday table EXISTS (owner-approved entries); extension rules are not in the source; none applied |
| D-04 | Inclusive/exclusive boundary | L+O | **BLOCKED** | `counts_start_day` EXISTS; value per setting needs approval |
| D-05 | Judgment date vs notification date; opponent's unknown notification date | S+O | **BLOCKED** (opponent part) | Source: default judgment date, switchable. Implemented as written for CITRA's own deadline; the opponent's start rule is blocked: the opponent position stays UNRESOLVED and no opponent deadline is derived until approved |
| D-06 | Separate deadline per party | S | OPEN-SPEC | Implemented as written |
| D-07 | Finality conditions («شروط النهائية») | O | **BLOCKED** | The finality endpoint refuses until the approved condition set is recorded. The generic minimum (all deadlines elapsed, no recorded appeal, opponent position confirmed, CITRA decision where required) is a proposed floor, not the legal rule |
| D-08 | Highest stage, exceptional remedies (التماس إعادة نظر), judgment after remand | O | **BLOCKED** | Only levels with an approved setting create a next stage; nothing after the highest configured level; no remedy type is active |
| D-09 | Who prepares, who approves; technical administration vs legal authority; delegation | O | **OWNER-PENDING** | Proposed maker-checker (03 §2): researcher prepares, authorised legal manager approves, approver ≠ preparer, case-scoped, identity stored. ADMIN is technical and does not approve legal decisions by role. Final allocation of authority and delegation await the owner |
| D-10 | Grievance deadline basis (decision date or awareness date) and eligibility | O | **BLOCKED** | Fields exist in the setting; no grievance deadline is computed until the setting is approved |
| D-11 | Complaint closure details; path after a grievance is filed | S+O | **BLOCKED** (post-grievance) | Closure rule (PC-14) implemented as written; the status/referral path after a filed grievance is blocked |
| D-12 | Terminology and CITRA capacities | S+O | OPEN-SPEC (six terms) / OWNER-PENDING (others) | Fixed list: مستأنفة، طاعنة، مستأنف ضدها، مطعون ضدها، مجني عليها / شاكية; no other capacity without approval |
| D-13 | Manual duration when no setting matches: who may override | O | OWNER-PENDING | Proposed: `POSTJ_PREPARE` prepares with reason and legal reference, `POSTJ_APPROVE` approves; ADMIN users notified (ST-07) |
| D-14 | Source of the "direct manager" for alert recipients | O | **OWNER-PENDING; manager notifications stay OFF** | See B.1 |
| D-15 | Alert channels and delivery status | L | OPEN-SPEC | In-app only (decision D11); delivery status = created / delivered in-app / read |
| D-16 | Meaning of "conflicting" overlap (ST-04) | O | **PROVISIONAL** (owner direction, not legally approved) | Same judgment level + appeal type, overlapping effective dates, and the same case type or either is "all"; enforced by an exclusion constraint |
| D-17 | DISMISSED / OTHER outcomes | O | OWNER-PENDING | No workflow; rows stay readable |
| D-18 | Confidentiality of respondent identifiers (civil/commercial numbers) | L | OPEN-SPEC | Existing party-identifier confidentiality rules apply |
| D-19 | Retention of complaint data | O | **PROVISIONAL** | Never hard-deleted; soft removal with history; complaint, sessions, requests, documents and decisions are retained with their audit trail |
| D-20 | Who may see a complaint, including before it is linked to a case | O | OWNER-PENDING (design in B.2) | Explicit permission + organisational scope; ADMIN gets no complaint content by role |
| D-21 | Status label of an approved non-appeal while the opponent deadline runs | O | OWNER-PENDING | Source silent; held as «جاري إعداد الطعن» with the decision shown separately |
| D-22 | What moves a complaint to «قيد التحقيق»; may a decision skip it | O | OWNER-PENDING | Proposed: first session/request or explicit action; skipping only if approved |
| D-23 | "Recorded activity" for the inactivity alert | O | **PROVISIONAL** | Any session, request, document, decision or note counts; the lookback period is a setting |
| D-24 | Reopening a final judgment or closed complaint | O | **PROVISIONAL** | Forbidden by default; any correction path would be a separately approved, audited procedure — none is designed |

### B.1 D-14 — direct manager (PROPOSED)
EXISTING: no user→manager relation anywhere; `org_unit` (V600) has a tree (LEGAL / AUTHORITY, parent, level, validity) but **no head or manager field**, so a unit's head cannot be read from the data and is **not assumed** to be anyone's direct manager.
PROPOSED: an explicit mapping `user_direct_manager(user_id, manager_user_id, valid_from, valid_to, set_by, set_at, reason)`: at most one active row per user; a user cannot be their own manager; cycles refused; the manager must be an active user; history kept (rows are ended, never edited or deleted); changes audited and restricted to a dedicated authority (`USER_MANAGER_LINK_MANAGE`, proposal: ADMIN as technical custodian, because it is organisational master data and carries no legal approval). Integration with the existing structure: the mapping is independent of `org_unit`; an optional, *informational* link from a manager to an org unit may be shown, but the recipient is always the explicit mapping.
Recipient policy (PROPOSED, requires owner approval): setting `manager_recipient_policy` = `DISABLED` (default) | `DIRECT_MANAGER_ONLY` | `DIRECT_MANAGER_THEN_DELEGATE`. **While the policy is DISABLED, or the researcher has no active mapping, alerts go to the responsible researcher only; no manager, no LEGAL_MANAGER role holder and no unit head is notified by inference.** The previous draft's interim "every LEGAL_MANAGER" is withdrawn. A manager receives only alerts for records they are in scope to see (B.2 rules apply). Missing mapping is reported to ADMIN as a data-quality notice without case details.

### B.2 D-20 — complaint confidentiality (PROPOSED)
See 03 §2.2: explicit `COMPLAINT_VIEW` + scope (responsible researcher, explicit access list, organisational scope of the owning unit); no access by ADMIN role; hidden-record isolation across APIs, worklists, attachments, alerts, audit and search with tests T-S02.

## C. TDD plan

Layers: backend unit (calculators, state machines, rule codes) → integration with PostgreSQL 18.6 (`CaseAccessPolicy`, constraints, concurrency, idempotence) → API tests asserting persisted state → frontend component tests (RTL/LTR) → local journeys on the real jar (`tools/journeys/local_stack.py`) → browser acceptance (Arabic RTL, English, 375/768/1280 widths, real persistence) → migration/restore drills. RED first for each ID; the failing test is committed before the code in each PR.

| Scenario (from the brief and §8 of the document) | Test IDs |
|---|---|
| Favourable, no opponent appeal | T-A10 |
| Favourable, opponent appeals (stage opened, capacity respondent) | T-A11 |
| Against, CITRA appeals | T-A12 |
| Against, approved non-appeal (reason, approver, date mandatory; not final while opponent runs) | T-A13, T-A14 |
| Partial, both appeal; all six combinations | T-A15 ×6 |
| CITRA non-appeal while opponent appeals | T-A14 |
| Different start dates per party | T-A07 |
| Setting change leaves earlier deadlines untouched | T-B05 |
| Authorised recalculation with history; unauthorised refused | T-A08, T-A09 |
| Expired deadline without confirmation: alert, status unchanged | T-A16, T-C03 |
| Per-party alert independence, idempotent job across restart/retry | T-C05, T-C06 |
| Complaint with several respondents | T-D01 |
| Several sessions and requests | T-D02, T-D03 |
| Preservation, grievance, closure rules | T-D05–T-D07, T-D13 |
| Referral creates exactly one linked case; retry and concurrency | T-D09, T-D10 |
| Case ↔ complaint navigation both ways | T-D14 |
| Hidden record protection: same status and normalised body as unknown id on every related endpoint | T-S01 (matrix over all new endpoints) |
| Complaint isolation: ADMIN without scope sees nothing; attachments, worklists, counts, alerts, audit, search | T-S02 |
| Maker-checker: approver ≠ preparer, approver needs authority and case scope; ADMIN role alone refused | T-S03 |
| Manager policy DISABLED: no manager recipient; enabled: only in-scope alerts | T-C07 |
| Activation gates: gated action refused without approval record | T-G01 |
| Concurrent updates (409 on stale `version`), rollback on failure mid-referral | T-X02 |
| Existing-data upgrade and restore | T-M01–T-M03 |
| No literal period in code | T-X03 |
| Existing suites unchanged and green | T-X01 |

## D. Milestones

| M | Scope | Unblocked? | Branch (proposed) |
|---|---|---|---|
| M0 | This package: register, gap, architecture, migration plan, permissions, state matrices, decisions, test plan, R5 compatibility | yes — this PR | `feature/LD-DOC-r6-m0-requirements` |
| M1 | Judgment extension, per-party deadlines (manual duration path), appeals, procedural status + history | yes, except finality and settings lookup | `feature/LD-DOC-r6-m1-judgment` |
| M2 | Settings screen and versions, appeal decisions, recalculation, finality, transitions | partly: D-01, D-07, D-09 block activation, not construction | `feature/LD-DOC-r6-m2-decisions` |
| M3 | Alerts, history, worklists | manager recipients stay OFF until D-14 is approved (researcher-only alerts can be built) | `feature/LD-DOC-r6-m3-alerts` |
| M4 | Complaint submission and investigation | construction yes; complaint visibility per D-20 design is OWNER-PENDING and ships restrictive (researcher + explicit access list) | `feature/LD-DOC-r6-m4-complaints` |
| M5 | Decisions, grievances, referral, linked case | construction yes; D-10, D-11 block grievance calculation and post-grievance paths | `feature/LD-DOC-r6-m5-referral` |
| M6 | Integration, regression, security, browser QA, operational readiness | after M1–M5 | `feature/LD-DOC-r6-m6-integration` |

Every milestone: own branch and draft PR, tests and evidence in `docs/evidence/DOC-R6-Mx`, independent QA by Codex, no self-approval, no merge into develop, no UAT deployment without the owner's approval. Merging a milestone triggers the automatic UAT deploy, so the window is coordinated with Codex first.

## E. Evidence

M0 is documents only; no application code, migration or test was changed (the only added executable is `tools/docs/r6_m0_check.py`, which reads files). Verification performed: the source was read in full; the repository was inspected at `18822a8` (schema V302, V680, V681, V843; packages `proceedings`, `deadlines`, `notifications`, `org`; permissions; migrations across all remote branches).
