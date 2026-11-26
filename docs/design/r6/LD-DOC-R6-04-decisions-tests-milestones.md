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
| C8 | "direct manager" (§7.1, ¶209) has no data source | Blocking decision D-14 |

## B. Owner / legal decision register

Classes: **S** explicitly specified in the source · **L** already supported by approved LawDesk rules · **O** requires owner/legal approval (activation blocked until answered).

| ID | Question | Class | Position / blocked behaviour |
|---|---|---|---|
| D-01 | Legal durations and applicability by court/case type | S+O | Source gives 30/15/30 as initial values to be reviewed; seeded inactive; no judgment may use a setting until legal approval is recorded |
| D-02 | Calendar vs working days | L+O | Mechanism exists (`count_method`); no default (D6); the setting must state it; blocked until chosen per setting |
| D-03 | Weekends, public holidays, extensions | L+O | Holiday table exists (owner-approved entries); legal extension rules are not in the source |
| D-04 | Inclusive/exclusive boundary | L+O | `counts_start_day` exists; value per setting needs approval |
| D-05 | Judgment date vs notification date; opponent's unknown notification date | S (default and switch) +O (opponent default, evidence of notification) | Judgment date default and editable start are implemented; what start the opponent's deadline gets when the notification date is unknown needs approval; until then the opponent position stays UNRESOLVED |
| D-06 | Separate opponent deadlines | S | Implemented |
| D-07 | Finality verification conditions («شروط النهائية») | O | The source names them without listing; implemented generically: all deadlines elapsed, no recorded appeal, opponent position confirmed, CITRA decision recorded where required; any extra condition blocks the finality endpoint until approved |
| D-08 | Highest stage, exceptional remedies (التماس إعادة نظر), judgment after remand | O | Cassation is the highest configured level; extra remedy types are configuration only and inactive |
| D-09 | Who approves a non-appeal, finality, recalculation, non-grievance; delegation | O | Settings screen roles are S (admin, legal manager); other approvals proposed as LEGAL_MANAGER; delegation not implemented |
| D-10 | Grievance deadline basis (from the decision date or the date CITRA became aware) and eligibility | O | Fields exist in the setting; no default; grievance deadline cannot be computed until the setting is approved |
| D-11 | Complaint closure details; what happens after a grievance is filed | S (closure rule) +O (post-grievance) | Closure rule implemented; post-grievance status/referral path blocked |
| D-12 | Terminology and CITRA capacities | S (six terms) +O (others) | The six terms are a fixed list; other capacities need approval |
| D-13 | Manual duration: who may override | O | Proposed POSTJ_MANAGE with mandatory reason + legal reference, admins notified |
| D-14 | Source of "direct manager" | O | Needs a user→manager link (or org-unit head). Until answered, recipients are the researcher plus every LEGAL_MANAGER (explicitly labelled interim) — owner must confirm or reject |
| D-15 | Alert channels | L | In-app only (decision D11); delivery status = in-app delivered/read |
| D-16 | What "conflicting" overlap means for ST-04 | O | Proposed: same level + appeal type + overlapping dates + (same case type or either is ALL) |
| D-17 | Treatment of DISMISSED/OTHER outcomes | O | No workflow |
| D-18 | Fields for respondents (civil/commercial ids) confidentiality | L | Existing confidentiality rules for party identifiers apply |
| D-19 | Retention of complaint data | O | Never deleted; soft removal only |
| D-20 | Who may see an unlinked complaint | O | Researcher, LEGAL_MANAGER, ADMIN |
| D-21 | Status label of an approved non-appeal while the opponent deadline runs | O | Source silent; held as «جاري إعداد الطعن» + decision shown separately until answered |
| D-22 | What moves a complaint to «قيد التحقيق»; may a decision skip it | O | Proposed: first recorded session/request or explicit action; skipping allowed only if approved |
| D-23 | Definition of "recorded activity" for the inactivity alert | O | Proposed: any session, request, document, decision or note |
| D-24 | Reopening a final judgment or closed complaint | O | Not in the source; forbidden |

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
| M3 | Alerts, history, worklists | recipients blocked by D-14 (interim rule needs owner confirmation) | `feature/LD-DOC-r6-m3-alerts` |
| M4 | Complaint submission and investigation | yes, D-20 interim | `feature/LD-DOC-r6-m4-complaints` |
| M5 | Decisions, grievances, referral, linked case | construction yes; D-10, D-11 block grievance calculation and post-grievance paths | `feature/LD-DOC-r6-m5-referral` |
| M6 | Integration, regression, security, browser QA, operational readiness | after M1–M5 | `feature/LD-DOC-r6-m6-integration` |

Every milestone: own branch and draft PR, tests and evidence in `docs/evidence/DOC-R6-Mx`, independent QA by Codex, no self-approval, no merge into develop, no UAT deployment without the owner's approval. Merging a milestone triggers the automatic UAT deploy, so the window is coordinated with Codex first.

## E. Evidence

M0 is documents only; no code, migration or test was changed. Verification performed: the source was read in full; the repository was inspected at `18822a8` (schema V302, V680, V681, V843; packages `proceedings`, `deadlines`, `notifications`, `org`; permissions; migrations across all remote branches).
