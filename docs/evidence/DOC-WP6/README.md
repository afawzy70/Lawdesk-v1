# DOC-WP6 — extended tasks, judicial calendar, notification centre (LD-056)

Story LD-056, migrations V720–V721. Requirement sections 14, 15 and 32 (requirement IDs `LD-DOC-14-001…009`,
`LD-DOC-15-001…013`, `LD-DOC-32-001…011`). Scenarios S11, S14 and the task part of S15. Decisions D3 (access), D6 (no
calculated deadlines), D11 (in-app only).

## What was built

| Area | Backend | Screens |
|---|---|---|
| 14 Tasks | `task` gets `starts_on`, `instructions`, `delay_reason` (+ who/when) and the seven statuses of the source text: NEW, IN_PROGRESS, WAITING_DOCUMENTS, WAITING_OTHER, COMPLETED, LATE, CANCELLED. `task_history` (V720) records who changed what: creation, field changes, status moves, reassignment (with reason), delay reason, and the follow-up run's LATE/restore lines. `POST /tasks/{id}/reassign`, `POST /tasks/{id}/delay-reason`, `GET /tasks/{id}/history`. LATE is set only by the follow-up run (an open task past its due date; it remembers its earlier status in `status_before_late` and returns to it when the due date moves). | Task form with start date and instructions; "Details" panel per task (instructions, delay reason form, reassignment, history) on the task list and on the case page; the seven statuses and their moves |
| 15 Calendar | Derived, nothing duplicated: `CalendarSource` interface; sources for hearings (court, expert, investigation, other; stage kept so appeal/cassation sessions are identifiable), tasks (due date), expert sessions, information-request reply dates, the legal deadlines of WP4 (`DeadlineCalendarSource`, type DEADLINE: only deadlines entered by hand or approved, never a bare proposal; the deadlines keep their own reminders, so the date notifications do not repeat them), and manual dates (`calendar_date`, V721: meeting, investigation, memorandum, appeal/grievance, enforcement, other). `GET /calendar/events|day|week|month|conflicts`, `POST/PUT /calendar/dates`, `POST /calendar/dates/{id}/status`. Filters: user (`me` or id), case, kind, period (≤ 101 days). Every source applies `CaseAccessPolicy` in SQL and the permission of its module (HEARING_VIEW, TASK_VIEW, EXPERT_VIEW, INFO_REQUEST_VIEW). Instants UTC, days and times Asia/Kuwait. Past entries stay after a case closes; future entries of a closed/archived case carry `closedCaseFuture`. Conflicts: timed, active entries of one member that overlap; the case's alternate member is offered. | `/calendar` (month grid, week, day, list; my / general calendar; kind filter; conflicts panel; "new date" form), "Dates of the case" on the case page, navigation item |
| 32 Notification centre | `NotificationTypes`: the eleven kinds of section 32 in order (CASE_NEW, HEARING_UPCOMING, EXPERT_SESSION_UPCOMING, APPEAL_DEADLINE, TASK_OVERDUE, HEARING_NO_RESULT, INFO_REQUEST_OVERDUE, JUDGMENT_NEW, DOCUMENT_MISSING, DATE_CONFLICT, RISK_HIGH) plus system notices (TASK_ASSIGNED, TASK_DUE_SOON, INFO_REQUEST_ESCALATED, HEARING_NO_NEXT_DATE). Per-user read/unread (`/read`, `/unread`, `/read-all`), filters (kind, case, read state), `recordType`/`recordId` link, per-user preferences (`notification_preference`; absent = on; in-app kinds only), `dedupe_key` (a `token` makes a moved date or a new due date news again). | `/notifications`: kind and state filters, mark read/unread, link to the record, "choose the notifications I receive" |
| Follow-up job | `FollowUpRun` (hourly job and `POST /follow-up/run`) now also: marks open tasks past due LATE and raises TASK_OVERDUE (assignee + giver) and TASK_DUE_SOON; reads the calendar as the system and raises HEARING_UPCOMING, EXPERT_SESSION_UPCOMING and APPEAL_DEADLINE for entries within 3 days, DATE_CONFLICT (next 30 days), HEARING_NO_RESULT (scheduled hearing whose day passed), CASE_NEW and JUDGMENT_NEW (last 7 days, not to whoever registered it), DOCUMENT_MISSING (request whose reply is incomplete). Idempotent; nothing leaves the system. | — |

Permissions added: `CALENDAR_VIEW` (seeded to every role holding `CASE_VIEW`) and `CALENDAR_MANAGE` (roles holding `TASK_MANAGE` or `HEARING_MANAGE`), V721.

## Migration of the existing tasks (V720)

`task_status_map` documents and reverses it: OPEN→NEW, DONE→COMPLETED, IN_PROGRESS and CANCELLED unchanged; WAITING_DOCUMENTS and WAITING_OTHER fall back to IN_PROGRESS, LATE to its `status_before_late` (rollback recipe in the migration header). The old words are still accepted: as API input (`status` in bodies and filters, mapped by `TaskStatus.parse`) and by direct writers (a `before insert/update` trigger stores OPEN/DONE as NEW/COMPLETED), so older clients, scripts and parallel branches keep working. Responses now carry the new names. `case_visible` (V600) was re-created: a task opens its case to the assignee while it is not COMPLETED or CANCELLED.

## Tests (strict TDD: the red runs are in `red-*.txt`)

Backend (MockMvc on embedded PostgreSQL), package `com.lawdesk.wp6`: `TaskExtensionApiTest` (14), `TaskStatusMigrationTest` (3: rows survive with only the two renamed statuses, the map is complete and reversible, legacy writers), `CalendarApiTest` (20, two for the WP4 deadline source), `NotificationCentreApiTest` (8), `FollowUpDatesTest` (14); plain unit tests `CalendarEngineTest` (5, a plugged-in source) and `DateRemindersTest` (3, S14 plug-in point). Existing task/report tests were updated for the renamed statuses; a test that deleted a user now removes the assignment notice first. Each endpoint test has outsider (404), viewer (403) and permission (403) cases.
`red-task-extension.txt` — 14 of 14 failed before the code; `red-calendar.txt` — 18 of 18; `red-notification-centre.txt` — 8 of 8; `red-deadline-source.txt` — both deadline tests failed before the source existed. The follow-up, migration and unit tests were written before their code in the same way but their red run was not kept.
Frontend (vitest, branch gate 90 %, 91.6 % overall): `features/calendar/*.test.*` (calendar, API helpers, case dates), `features/followup/NotificationCentre.test.tsx`, `features/operations/tasks/TaskDetails.test.tsx`; existing task, report, navigation and notification tests were updated.

## Journey

`python3 tools/journeys/wp6_journey.py docs/evidence/DOC-WP6/journey-uat.json` (QA accounts, synthetic data `SYNTH-DOC`, no existing case edited). Run by the integration owner after the merge and deployment. The follow-up run it triggers is system-wide and may also raise notifications for existing records.

## Assumptions to confirm

- The seven statuses are those of the source text (section 14); the brief's list (POSTPONED, REJECTED) is not in the document and was not built.
- Reminder lead time (3 days, like a task "due soon"), "new" window (7 days) and the conflict horizon (30 days) are not in the requirements: configurable (`lawdesk.notifications.upcoming-days`, `lawdesk.notifications.recent-days`).
- A conflict is two timed entries of one member that overlap; an entry without an end occupies only its start; day-long entries (tasks, dates without hours) never conflict. The weekly view runs Sunday to Saturday.
- WP4 deadlines are on the calendar through the same interface (tested). Manual dates remain for what no record holds (memorandum, appeal and enforcement dates the lawyer sets; WP5 enforcement can plug in its own `CalendarSource`); only APPEAL_DEADLINE manual entries (and hearing-like ones) raise date notifications.
- DOCUMENT_MISSING is raised from an information request whose reply is incomplete; evidence gaps (WP2/WP5) are not a source yet.
- Task timekeeping keeps the existing Africa/Cairo "today" for due/overdue; the calendar and the date notifications use Asia/Kuwait. They differ by at most one hour a day.

## What remains

- Enforcement dates (WP5) as a calendar source; the notification kinds already exist.
- Hearings are not assigned to an alternate from the conflict panel by one click (the panel names the alternate; the hearing form changes the responsible member).
- Browser matrix, axe and screenshots were not captured here (run with the journey on UAT).
- `frontend/node_modules` was a committed (dangling) symlink on develop until WP2 removed it; worktrees need their own `npm ci` or a local link.
