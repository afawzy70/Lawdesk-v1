# DOC-WP4 — legal deadlines, their rules, holidays and reminders (LD-054)

Story LD-054, migrations V680–V682. Requirement section 10 (source lines 149-162; IDs `LD-DOC-10-001…011`), owner decision D6,
scenario S14. Plugs into the WP3 unified timeline/summary and notifications; replaces the empty "appeal deadline of a judgment"
field with a per-judgment view.

## What was built

| Requirement (section 10) | Backend | Screens |
|---|---|---|
| Source of the deadline | `deadline.source_type` JUDGMENT / HEARING / PROCEEDING / TASK / TEXT with `source_id` (checked to belong to the same case) or free `source_text`; a language-neutral `source_label` is kept | Source picker in the deadline form |
| Start date, period, last day to act | `starts_on`, `period_days`, `due_on` (a period alone is a valid record; the due date is then "not specified"; the system never computes a manual deadline's date) | Record / edit forms |
| Required action, responsible member, state, completion date, proof document | `action`, `owner_id`, `status` OPEN / COMPLETED / MISSED, `completed_on`, `proof_document_id` (must be in the case file) | Deadline card with complete / missed / reopen |
| Rules and holidays from settings | `deadline_rule` (code, label, period_days, CALENDAR/WORKING, whether the start day counts, roll-forward, closed weekdays, mandatory `legal_source`, `effective_from`, DRAFT/APPROVED/RETIRED), `official_holiday` (date, label, mandatory source, same states) | Settings page `/admin/deadlines` (tab "Deadline rules") |
| Reviewable and approved by the competent member, name and date kept | `calc_status` MANUAL / PROPOSED / APPROVED with `approved_by/at`; `POST /deadlines/{id}/approve` needs `DEADLINE_APPROVE` and the right to change the case; rule/holiday approval and retirement also need `DEADLINE_APPROVE`; every approval is also in the audit log | Approve buttons, "approved by X on date" |
| Automatic calculation | `POST /cases/{id}/deadlines/propose` computes **only** from an APPROVED rule effective at the start date and the APPROVED holidays; the result is PROPOSED and stores the whole arithmetic (rule, source, first counted day, every skipped weekday/holiday, roll-forward). With no approved rule it answers **422 `NO_APPROVED_RULE`**. A proposal cannot be completed until approved; editing its dates by hand makes it MANUAL again (reason required) | "How the date was computed" panel |
| Alerts before expiry | In-app notifications `DEADLINE_APPROACHING` / `DEADLINE_OVERDUE` (existing `notification` table, dedupe keys `DEADLINE:<id>[:<lead>]`), hourly job and `POST /deadlines/reminders/run` | `/notifications` texts |
| Register | `GET /deadlines` with filters status, calcStatus, ownerId, due window (from/to), overdue, caseId, sourceType; `GET /cases/{id}/deadlines`; `GET /judgments/{id}/deadlines` | Register `/deadlines`, case section, per-judgment list |
| Unified timeline / summary | Timeline kind `DEADLINE` (dated deadlines, past and future, `needsAction` when open and late), `deadlinesAvailable` true for holders of `DEADLINE_VIEW`; summary block `deadlines` (open / overdue / awaiting approval / next five); "DEADLINES" removed from `pendingModules` | Timeline filter, summary panel |

Permissions (V682 seeds them): `DEADLINE_VIEW/MANAGE/APPROVE`. ADMIN, LEGAL_MANAGER, SECTION_HEAD and LEGAL_STAFF hold all three
(LDOC-04 table: "approve a computed deadline" is for LEGAL_STAFF as a member of the case; the case-access policy enforces that
only a member or a holder of the in-scope edit right may approve); DATA_ENTRY and READ_ONLY hold `DEADLINE_VIEW`.
Every endpoint uses `CaseAccessPolicy` (404 for a case the user cannot see, 403 for one they only see); the register, the
per-judgment list, the timeline and the notifications are restricted to visible cases.

## What D6 forbids, and how it is kept

- `deadline_rule`, `official_holiday` and `deadline_reminder_setting` have **no row** after the migrations (tested: `theApplicationShipsNoRuleNoHolidayAndNoReminderSetting`). No period, count method, weekday closure or holiday is stated anywhere in code or SQL.
- A rule must state *every* counting choice (period, method, whether the start day counts, roll-forward, closed weekdays — which may be none) and a non-blank legal source; none has a default.
- Rules/holidays in the arithmetic tests are created inside the tests (`TEST-` codes, 2031 dates) and removed by the test clean-up; the UAT journey enters one synthetic rule and one synthetic holiday dated 2000/2001 and **retires both before it ends**.
- Nothing is ever completed, missed, closed or approved by the system. Overdue is derived (`status OPEN` and past due).

## Reminder lead times — NO DEFAULT (decision for the owner)

The document says reminders are sent "before expiry" but gives no lead times. They are therefore a setting in the database
(`deadline_reminder_setting`, edited at `/admin/deadlines` or `PUT /deadline-settings/reminders` by a holder of
`DEADLINE_APPROVE`), with **no row and no value until configured**. Until then **no approaching-deadline reminder is created**.
Once configured (for example 7, 3, 1 days), an open, approved deadline raises one in-app notification per reached lead time
for the responsible member and the case's specialist. The *overdue* notification needs no lead time and is raised once per
approved open deadline that is past its date. Unapproved (manual or proposed) dates are not official and raise nothing.
Nothing is sent outside the system (D11). The workflow note "per deadline type" is not modelled: one list of lead days applies to all.

## Tests

Backend (MockMvc on embedded PostgreSQL, 39 tests in `com.lawdesk.deadlines`):
- `DeadlineCalculatorTest` (10) — arithmetic with TEST-ONLY rules: calendar/working, start day counted or not, closed weekdays, holidays inside/outside the span, roll-forward, 3650-day period, rule that closes every day.
- `DeadlineApiTest` (21) — database ships empty; manual deadline is MANUAL/not official; approval keeps who/when and needs the permission (403 specialist, 403 viewer, 404 outsider, 409 stale, 422 twice); period-only record; field validation; every source type and a source of another case refused; edit after approval needs a reason and returns to MANUAL; completion with proof of the same case, future date refused, reopen; overdue stays open; rule draft/approve/freeze; rule must state source and counting; holiday draft/approve/retire; **`withNoApprovedRuleTheProposeEndpointAnswersAClear422`** (no rule, unknown rule, draft rule, retired rule); proposal with arithmetic, later holidays do not rewrite it, cannot be completed before approval; hand edit forgets the rule; effective date; register filters; per-judgment view; **visibility** (outsider 404 on every endpoint and 0 in lists, viewer 403 on writes, no-permission 403); archived case.
- `DeadlineReminderApiTest` (5) — no approaching reminder without configured lead times; lead-time validation and audit; approaching reminders once per reached lead time and recipient; overdue once, nothing closed, hidden from users who cannot see the case; run needs the permission.
- `DeadlineTimelineApiTest` (3) — timeline entries and `deadlinesAvailable`, summary block, permission/visibility.
- `CaseFileApiTest` updated (DEADLINES no longer pending).

Frontend (vitest, 90 % branch gate): `features/deadlines/*.test.tsx` (case section, register, settings, judgment list, api helpers) plus
updated timeline/summary/notification/case-follow-up/navigation tests.

TDD note: the calculator test and the API contract were written first; the code and tests of the API, reminders, timeline and
screens were then built slice by slice with the tests run after each slice. Red runs were **not** captured as files for this package.

## Journey

`python3 tools/journeys/wp4_journey.py docs/evidence/DOC-WP4/journey-uat.json` — QA accounts, synthetic `SYNTH-DOC` data, one new
case, no existing case edited. It covers S14: manual deadline from a judgment → approval by a competent member (and refusals for a
read-only user) → period-only record → per-judgment view, register filters and visibility → draft rule (422 NO_APPROVED_RULE) → holiday
→ approved rule → proposal with arithmetic (expected 2001-03-13) → approval → completion with proof and reopening → overdue
deadline, reminder run (in-app, once) → timeline and summary → retirement of the synthetic rule and holiday. It does not change the
reminder lead times. Run by the integration owner after the merge and deployment.

## What remains / assumptions to confirm

- Rules, sources, holidays and reminder lead times must be entered and approved by the owner's specialists; the system is useless for automatic calculation until then (by design).
- `judgment.appeal_deadline_id` (data model) was **not** added: the per-judgment view is `deadline.source_type = JUDGMENT`, which also allows several deadlines (appeal, cassation, grievance) per judgment. WP2 judgment details are unchanged.
- Proof document is optional at completion (the register shows "No proof document"); the workflow note "completion + proof" can be made mandatory if the owner wants.
- Reminders are global (one list of lead days), not per deadline type; the deadline "type" (appeal / cassation / grievance / memo / enforcement) is not a field — the required action text and the source describe it.
- A MISSED state exists only as a user's record; no automatic transition.
- Calendar events derived from deadlines (WP6) and the enforcement deadlines of WP5 are not part of this package.
- Browser matrix, axe and screenshots were not captured here (to run with the journey on UAT).
