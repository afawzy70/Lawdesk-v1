# LD-DOC-R6-04 — Conflicts, decision register, test plan, milestones (M0, PROPOSED)

## A. Conflicts between the document and the repository (resolution proposed; none built)

| # | Conflict | Proposed resolution |
|---|---|---|
| C1 | §3 ¶44: start date defaults to the judgment date; §4.2 ¶70: each setting has a default basis (judgment date or notification date) | The setting's basis proposes the start date; with no matching setting the judgment date is a draft prefill of a *fact*; always editable by an authorised user; per party |
| C2 | The document has three outcomes; the repository has five (DISMISSED, OTHER) | The three source outcomes are the workflow; DISMISSED/OTHER rows stay readable with no workflow (D-17) |
| C3 | Old `judgment.judgment_status` (ISSUED/NOTIFIED/CHALLENGED/OTHER) and boolean `final` | Untouched; new procedural status is a separate table; `final` is kept in step only inside the finality transaction (05 §C.2) |
| C4 | Existing rule model (D6: no seeded rule, approval workflow) vs the document's seeded 30/15/30 | Seed as DRAFT/inactive; activation only with a versioned legal approval record (05 §D) |
| C5 | ¶96 requires notifying «مدير النظام»; only in-app notifications exist | In-app notification to every ADMIN user (D-15) |
| C6 | ¶192 «بعد عدم التظلم تُغلق الشكوى» vs ¶201 «لا تُغلق قبل انتهاء الميعاد أو قرار بعدم التظلم» | Consistent; closure is an explicit authorised action after either event and never while a grievance is pending (03 §3.3) |
| C7 | ¶181 «الحالة بعد الحفظ» | Read as "after saving" |
| C8 | "direct manager" (§7.1, ¶209) has no data source | D-14 (B.1); manager notifications stay off until approved |
| C9 | Existing `operative_text` (V621) vs the first register's "new field" | Existing field reused (PJ-02) |
| C10 | `judgment.appeal_deadline_id` points to one deadline; the document needs two per judgment | Authoritative model in 05 §C.2 |

## B. Owner / legal decision register (25)

Classes: **S** specified in the source (extract; author-compared with the original DOCX, independent check pending) · **L** supported by an EXISTING LawDesk mechanism (a mechanism is not a legal choice) · **O** requires owner/legal approval.
Status: **BLOCKED** — activation prohibited until the appropriate **legal authority** (CITRA Legal Affairs) approves and a versioned approval record exists (gate in 05 §D). **OWNER-PENDING** — an owner design decision is outstanding. **PROVISIONAL** — owner direction given for review, *not legally approved*; behaviour with legal effect stays inactive. **SPECIFIED** — the source states it; still only a proposed design until built and tested.
No legal approval record exists or has been supplied; none is assumed.

| ID | Question | Class | Status | Position / what stays blocked | Gate |
|---|---|---|---|---|---|
| D-01 | Legal durations and court/case-type applicability | S+O | **BLOCKED** | 30/15/30 are illustrative initial values "to be reviewed and approved before activation"; seeded DRAFT | G-SETTING, G-CALC |
| D-02 | Calendar vs working days | L+O | **BLOCKED** | Calculator supports both; no legal choice made; stated and approved per setting | G-CALC |
| D-03 | Weekends, public holidays, legal extensions | L+O | **BLOCKED** | Holiday table exists (owner-approved entries); extension rules are not in the source | G-CALC |
| D-04 | Inclusive/exclusive boundary and end-day roll | L+O | **BLOCKED** | `counts_start_day`/`roll_forward` exist; value per setting needs approval | G-CALC |
| D-05 | Judgment date vs notification date; the opponent's unknown notification date | S+O | **BLOCKED** (opponent rule) | Source: per-party start, default judgment date, switch to notification date (PROPOSED as a draft prefill for CITRA's own start). The opponent's start rule and notification evidence are blocked; the opponent position stays UNRESOLVED and no opponent due date is derived | G-OPPONENT |
| D-06 | Separate deadline per party | S | SPECIFIED | ¶13 states it; proposed design only until built | — |
| D-07 | Finality conditions («شروط النهائية») | O | **BLOCKED** | The finality endpoint refuses until an approved condition set exists; the generic floor (all deadlines resolved, no filing, opponent position confirmed, CITRA decision where required) is a proposal, not the legal rule | G-FINAL |
| D-08 | Highest stage, exceptional remedies (التماس إعادة نظر), judgment after remand | O | **BLOCKED** | Only levels with an approved setting offer a next stage; none after the highest; no remedy type active | G-STAGE |
| D-09 | Who prepares, who approves; technical administration vs legal authority; delegation; first custodian | O | **OWNER-PENDING** | Proposed maker-checker, non-inherited authorities, custodian-approved grants and delegations (05 §A); final allocation is the owner's. Until designated no sensitive grant exists and all approvals are refused | — |
| D-10 | Grievance deadline basis (decision date or awareness date) and eligibility | O | **BLOCKED** | No grievance deadline until the setting is approved | G-CALC |
| D-11 | Complaint closure details; dispositions and path after a filed grievance | S+O | **BLOCKED** (post-grievance) | Closure rule PC-14 specified; a filed grievance keeps the complaint open (05 §G); dispositions and next status blocked | G-GRIEVANCE |
| D-12 | Terminology, CITRA capacities | S+O | SPECIFIED (six terms) / OWNER-PENDING (others) | Fixed list: مستأنفة، طاعنة، مستأنف ضدها، مطعون ضدها، مجني عليها / شاكية; no other capacity without approval | — |
| D-13 | Manual duration when no setting matches: who may override | O | **BLOCKED** (legal) + OWNER-PENDING | Legally sensitive: refused without an approved override policy and a per-use checker approval; only a non-effective proposal is storable (05 §D.3) | G-OVERRIDE |
| D-14 | Source of the «direct manager» | O | **OWNER-PENDING; manager alerts OFF** | B.1 | — |
| D-15 | Alert channels and delivery status | L | SPECIFIED (in-app only, decision D11); owner confirms if more is needed | Delivery status = created / read | — |
| D-16 | "Conflicting" overlap of settings; which date matches a judgment | O | **PROVISIONAL**, legal approval required before activation | Rule in 05 §E (half-open ranges; ALL conflicts with specific); matching on the judgment registration date is proposed and needs approval | G-SETTING |
| D-17 | DISMISSED / OTHER outcomes | O | OWNER-PENDING | No workflow; rows stay readable | — |
| D-18 | Field-level protection, redaction and export of respondent identifiers in complaints | O | OWNER-PENDING | Baseline party-identifier handling does not prove complaint protection; PROPOSED: identifiers visible only to `complaint_visible` users, masked in lists and exports by default | — |
| D-19 | Retention of complaint data | O | **PROVISIONAL** | Never hard-deleted; soft removal with history; retention schedule and correction procedure to be approved | — |
| D-20 | Who may see a complaint; confidentiality inheritance after referral | O | OWNER-PENDING (design in 05 §B) | Explicit authority + scope; ADMIN none by role; RESTRICTED linked case; the capacity text may reveal a criminal context (accepted for owner approval) | — |
| D-21 | Label of an approved non-appeal while the opponent's period runs | O | OWNER-PENDING | PROPOSED: show status and CITRA decision as two separate fields and avoid a label implying a pending appeal after CITRA decided; wording is the owner's | — |
| D-22 | What moves a complaint to «قيد التحقيق»; may a decision skip it | O | OWNER-PENDING | PROPOSED: first session/request or explicit action; skipping only if approved | — |
| D-23 | "Recorded activity" for the inactivity alert | O | **PROVISIONAL** | Any session, request, document, decision or note counts; lookback is a setting | — |
| D-24 | Reopening a final judgment or closed complaint | O | **PROVISIONAL** | Forbidden by default; any audited correction path needs separate owner/legal approval | G-REOPEN |
| D-25 | Referral with incomplete mandatory case data; mapping of «مجني عليها / شاكية» to a case side | O | OWNER-PENDING (new in this revision) | PROPOSED: record the referral, create no case, keep `PENDING_DATA` and a task; the side mapping is the owner's | — |

### B.1 D-14 — direct manager (PROPOSED)
EXISTING: no user→manager relation; `org_unit` (V600) has a tree but **no head/manager field**, so a unit head is not assumed to be anyone's direct manager.
PROPOSED: `user_direct_manager(user_id, manager_user_id, valid_from, valid_to, set_by, set_at, reason)`: at most one active row per user; not self; no cycles; manager active; history kept (rows ended, never edited or deleted); changes audited and restricted to a dedicated authority. Independent of `org_unit`.
Recipient policy `manager_recipient_policy` = `DISABLED` (default) | `DIRECT_MANAGER_ONLY` | `DIRECT_MANAGER_THEN_DELEGATE`, needs owner approval. While DISABLED, or when the researcher has no active mapping: researcher only; no manager, no LEGAL_MANAGER role holder, no unit head by inference. A manager receives only alerts for records in the manager's scope (05 §B, §F). Missing mapping → data-quality notice to ADMIN users, no case details. Researcher-only delivery does **not** satisfy the source's manager recipient: AL-01 and PC-21 stay incomplete until the owner approves the policy.

## C. Test plan

Test ids are defined once, in the register (`LD-DOC-R6-01`: per row and "Cross-cutting tests"); `tools/docs/r6_m0_check.py` fails if a test id used anywhere is undefined. Layers: backend unit → integration on PostgreSQL 18.x (policy, constraints, concurrency, idempotence) → API tests asserting persisted state → frontend component tests (RTL/LTR) → local journeys on the real jar (`tools/journeys/local_stack.py`) → browser acceptance (Arabic RTL, English, 375/768/1280 widths, real persistence) → migration/restore drills (T-MIG-*). RED first: the failing test is committed before the code.
The document's eleven mandatory scenarios (§8 ¶152-163) map to named tests: favourable, no appeal T-PJ06-1; favourable, opponent appeals T-PJ06-2; against + appeal T-PJ07-1; against + non-appeal T-PJ07-2, T-PJ07-3; partial, both appeal T-PJ08-3; CITRA non-appeal while opponent appeals T-PJ08-4, T-PJ15-2; different start dates T-PJ05-2; setting change leaves history T-ST05-1; recalculation with reason T-PJ16-1, T-PJ16-2; expiry without confirmation T-PJ09-1, T-AL03-1; per-party alert independence T-AL05-1, T-AL05-2. Cross-cutting suites: T-SEC-1…10, T-CONF-1…10, T-DATA-1…11, T-GATE-1…7, T-ALERT-1…4, T-MIG-1…6, T-R5-1…2. Every denied action must leave **all** affected tables unchanged (digests).

## D. Milestones

| M | Scope | Not allowed in this milestone | Branch (proposed) |
|---|---|---|---|
| M0 | This package; independent QA rejected `865ee1c`; corrections in this revision | any code or migration | `feature/LD-DOC-r6-m0-requirements` |
| **M1** | **Schema, history and draft capture only:** `judgment_procedural`, `judgment_status_history`, `judgment_party_deadline` (draft: `deadline_id` NULL, no due dates), `judgment_appeal` + documents (facts), `proceeding.citra_capacity`, `legal_approval` and `legal_authority_*` tables with their triggers, the `Permission.adminInherited` change (05 §A); tests prove the gates are closed | due-date computation; any effective manual duration; finality; automatic next-stage creation; settings activation; alerts; seeding approvals | `feature/LD-DOC-r6-m1-judgment` |
| M2 | Settings screen and versions, overlap rule, legal approval workflow, calculation engine and evidence behind G-CALC, appeal decisions, recalculation, finality behind G-FINAL | activating any legal rule without an approval record | `feature/LD-DOC-r6-m2-decisions` |
| M3 | Alerts, history, worklists, reminder-run exclusion | manager recipients before D-14 approval | `feature/LD-DOC-r6-m3-alerts` |
| M4 | Complaint submission, investigation, access list, complaint visibility | referral, grievance deadlines | `feature/LD-DOC-r6-m4-complaints` |
| M5 | Decisions, grievances, referral adapter, linked case, closure guard | grievance deadline or post-grievance paths before D-10/D-11 | `feature/LD-DOC-r6-m5-referral` |
| M6 | Integration, regression, security, browser QA, operational readiness | — | `feature/LD-DOC-r6-m6-integration` |

**M1 readiness: NOT authorized.** It may start only after an independent M0 acceptance and the owner's explicit go-ahead; neither exists. Every milestone: own branch and draft PR, tests and evidence in `docs/evidence/DOC-R6-Mx`, independent QA by Codex, no self-approval, no merge into develop, no UAT deployment without owner approval; merging triggers the automatic UAT deploy, so the window is coordinated with Codex first.

## E. Evidence
M0 is documents only; no application code, migration or test was changed. Added executables read files only: `tools/docs/r6_m0_check.py`, `tools/docs/r6_register_build.py`, `tools/docs/verify_source.py`. Verification performed is in `LD-DOC-R6-00` and `docs/evidence/DOC-R6-M0/`.
