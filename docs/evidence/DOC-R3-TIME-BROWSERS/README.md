# DOC-R3-TIME-BROWSERS: Kuwait day boundary, tablet widths, cross-browser

Base: develop `3522775`. Branch `qa/LD-DOC-r3-time-browsers-restore` (the requested `test/` prefix is refused by the repo's
branch policy hook, which allows feature/, fix/, docs/, qa/, chore/, release/, hotfix/). Synthetic data only (SYNTH-DOC).
Status: VERIFIED_BY_DEVELOPER, not QA_ACCEPTED.

# Part 1: the Asia/Kuwait day at 00:00-03:00

Rule under test: instants are stored and sent in UTC; people read Kuwait time (UTC+3, no daylight saving); "today" is the Kuwait day,
which starts at 21:00:00Z of the previous UTC day. The window 21:00:00Z-23:59:59Z (00:00-02:59:59 in Kuwait) is where code that uses
the server's or browser's own zone shows yesterday.

Instants used everywhere: `2026-11-09T20:59:59Z` (Kuwait 23:59:59, 9 Nov), `21:00:00Z` (00:00:00, 10 Nov), `23:59:59Z` (02:59:59, 10 Nov),
`2026-11-10T00:00:00Z` (03:00:00), `02:59:59Z` (05:59:59).

## PINNED-CLOCK tests versus LIVE checks (never mixed)

| Kind | What fixes the time | Where | Proves | Does NOT prove |
|------|---------------------|-------|--------|----------------|
| PINNED | `Clock.fixed` handed to the validator (`ProductClockConfig.defaultValidator`) | `KuwaitBoundaryInstantsTest` (backend, 15 cases) | `@PastOrPresent`/`@FutureOrPresent` judge on the Kuwait day at all five instants; a UTC clock would refuse the Kuwait day exactly inside the window (contrast test); `CalendarTime` day/clock, fixed +03:00 offset | the live server's clock |
| PINNED | Mockito static mock of `LocalDate.now(ZoneId)` and `LocalDate.now(Clock)` for the test thread only | `KuwaitBoundaryDeadlinesTest`, `KuwaitBoundaryReportsTest` (backend, 5 + 5 cases, real Spring + PostgreSQL) | deadline overdue filter, deadline reminder run (overdue), date-only `dueOn` returned unshifted, dashboard `overdueTasks`, dashboard `month`, export file date (`workload-<kuwait day>.csv`) | the live server's clock; database `now()` (row creation times) is not pinned |
| PINNED | a `Date` passed in | `frontend/src/features/operations/kuwaitBoundary.test.ts` (vitest, 5 x 5 + 2) | `legalToday`, `todayIso`, overdue computation, `formatDateTime` in English and Arabic digits at all five instants | the live server |
| LIVE | the real server and its real clock, read-only except one synthetic case | `tools/journeys/kuwait_live_check.py` | what UAT answers now (below) | the boundary window, unless run inside it |

Production code has no Clock injection point for most "today" reads (they call `LocalDate.now(ZONE)` with the constant
Asia/Kuwait). No production seam was added. Pinning is test-only, as stated in each test's header. Dashboards, exports, hearing dates and
audit time: the date-only fields (hearing date, deadline due date) are `LocalDate` and travel as `yyyy-MM-dd` text, which the
deadline test shows unchanged. Audit time is an instant: it is stored in UTC and serialized with a `Z`; the frontend shows it with
`formatDateTime`, covered by the vitest file.

Not pinned in backend tests (stated, not hidden): hearings' "awaiting result" and the task/info-request reminder runs use the same
`LocalDate.now(Asia/Kuwait)` call as the code covered above but have no boundary test of their own; `TimeZoneConsistencyTest` (existing)
guarantees that no other zone literal exists in production code.

## Defects found by the frontend tests (fixed in this PR)

Run first against the unfixed code with the process zone `TZ=America/Los_Angeles`: 12 of the new cases failed.

1. `formatDateTime` defaulted to the browser's zone: audit times, notification times, document times and case "updated" times were
   shown in the viewer's zone, not Kuwait time. Fixed: the default is `Asia/Kuwait` (`LEGAL_TIME_ZONE` now lives in `i18n/format.ts`).
2. `todayIso()` (case opening date default, judgment-date check, directive date, deadline completion default) used the browser's local date.
   Fixed: Kuwait date.
3. Calendar page's initial day (`CalendarPage.todayIso`), the case dates panel's window (`CaseDates.addDays`) and the information-request
   `draftedOn` used the UTC date (wrong between 21:00Z and 24:00Z). Fixed: `legalToday()`.

Full vitest suite after the fix under `TZ=America/Los_Angeles`: 111 files, 967 tests, all passed.
The backend has no defect found: the three backend classes passed on first run (25 cases; the 20:59:59Z deadline case would have failed
if the pin did not work because the real date today is 10 Nov, so the pin is effective).

## LIVE read-only check against UAT, 2026-11-10 03:40Z

`python3 tools/journeys/kuwait_live_check.py <out.json>` (Session.login('manager'); credentials never printed; result in `live-check.json`).
Server clock from the HTTP `Date` header: 2026-11-10T03:40:36Z (client skew -0.3 s). Results:

- Dashboard `month` = `2026-11`; workload export file name `workload-2026-11-10.csv`: both equal the Kuwait day computed from the server clock.
- One synthetic case (title starts `SYNTH-DOC Kuwait live clock check`, the only write) came back with `openedOn` `2026-11-10` and
  `createdAt` serialized as `2026-11-10T03:40:37.887704Z`: UTC with a `Z`, within 120 s of the server clock; read as Kuwait wall clock it is 06:40:37.
- At 03:40Z the UTC date and the Kuwait date are the same (10 Nov), so this run **cannot tell the two apart**: it proves the storage/serialization
  (UTC `Z`) and that the server's Kuwait "today" agrees with the server clock at this hour, nothing more.
- **The live boundary window itself (21:00:00Z-23:59:59Z) was NOT observed.** `date -u` at the time was 03:28-04:45Z, outside the window, so the
  window run was not executed. It can only be observed by running the same script between 21:00Z and 24:00Z (when it should report
  `dashboard_month`/export day equal to the Kuwait day, i.e. one day ahead of the UTC day). No server clock was changed.
- The synthetic case remains on UAT (no delete call exists for cases); it is marked SYNTH-DOC.

Commands: `cd backend && ./mvnw -o test -Dtest='KuwaitBoundaryInstantsTest,KuwaitBoundaryDeadlinesTest,KuwaitBoundaryReportsTest'` (about 2 minutes
on the loaded host) and `cd frontend && TZ=America/Los_Angeles npx vitest run src/features/operations/kuwaitBoundary.test.ts`.

# Part 2: tablet widths, Firefox axe, WebKit

(Filled in with Part 2's commit.)
