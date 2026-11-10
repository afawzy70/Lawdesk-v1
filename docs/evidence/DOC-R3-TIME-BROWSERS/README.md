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
- **The live boundary window itself (21:00:00Z-23:59:59Z) was NOT observed.** `date -u` at the time was 03:28Z (start of the session) to 06:20Z (end of the browser runs), always outside the window, so the
  window run was not executed. It can only be observed by running the same script between 21:00Z and 24:00Z (when it should report
  `dashboard_month`/export day equal to the Kuwait day, i.e. one day ahead of the UTC day). No server clock was changed.
- The synthetic case remains on UAT (no delete call exists for cases); it is marked SYNTH-DOC.

Commands: `cd backend && ./mvnw -o test -Dtest='KuwaitBoundaryInstantsTest,KuwaitBoundaryDeadlinesTest,KuwaitBoundaryReportsTest'` (about 2 minutes
on the loaded host) and `cd frontend && TZ=America/Los_Angeles npx vitest run src/features/operations/kuwaitBoundary.test.ts`.

# Part 2: tablet widths, Firefox axe, WebKit

Tooling: `frontend/e2e-wp/smoke.mjs` (Playwright 1.64.1, axe-core 4.14.0; versions verified from `frontend/e2e-wp/package-lock.json`: `playwright` 1.64.1 with its own nested
`playwright-core` 1.64.1). Local throwaway stack only (embedded PostgreSQL, the jar of this commit, `vite preview`, 24 synthetic cases); never UAT.
Changes to the tool: widths `phone` 390, **`tablet` 768x1024**, **`tablet-l` 1024x768**, `desktop` 1366 (`UX_WIDTHS` selects); **axe runs on every engine** (was Chromium
only; `UX_AXE_ALL=0` restores that); new finding classes **clipped actions** (a link, button or field cut off by an ancestor with `overflow: hidden`/`clip`) and
**words broken mid-word** (a cell, header, link or button word that renders on two lines; checked from 768px up; UUIDs and hyphenated words inside a sentence are exempt).

## Final matrix: 936 rows = 3 engines x 2 locales x 4 widths x 39 screens, 0 findings (`results-final-<engine>.json`)

| Engine (version) | Locale | Widths | Screens per width | Page/console errors | Horizontal overflow | Elements outside viewport | Clipped actions | Words broken mid-word | Wrong `dir` | axe violations (all rows ran axe) |
|---|---|---|---|---|---|---|---|---|---|---|
| Chromium headless shell (Playwright 1.64.1, build 1248) | ar (RTL) and en (LTR) | 390, 768, 1024, 1366 | 39 | 0 | 0 | 0 | 0 | 0 | 0 | 0 of 312 rows |
| Firefox 157.0 | ar and en | 390, 768, 1024, 1366 | 39 | 0 | 0 | 0 | 0 | 0 | 0 | 0 of 312 rows |
| **WebKit** (Playwright WPE build 2370, version 27.2) | ar and en | 390, 768, 1024, 1366 | 39 | 0 | 0 | 0 | 0 | 0 | 0 | 0 of 312 rows |

axe tags: wcag2a, wcag2aa, wcag21a, wcag21aa, wcag22aa, best-practice. The per-width and per-locale breakdown is derivable from the JSON (every row carries engine, locale,
width, screen, and each measure). The first pass (390 and 1366 plus the old measure set, run before the fixes) also had 0 findings on all three engines: 312 rows each;
the new tablet rows and the new broken-word check are what found the defects below.

## Defects found by the new runs and fixed (frontend, with tests)

First pass of the new broken-word check on Chromium at 768 and 1024 (`results-before-fix-chromium-tablet.json`): 91 of 156 rows with findings.
The old measures had reported 0 findings for the same pages: nothing overflowed, the words were simply cut into pieces. Found first by looking at the WebKit screenshot of
the case register at 768px (`before-webkit-tablet-cases-ar.png`, `before-webkit-tablet-l-cases-ar.png`; after: `after-*.png`).

1. **Case register at 768px and 1024px**: the reference "LD-2026-00024" wrapped at its hyphens over three lines, the type column and the header "نوع القضية" broke mid-word.
   Fix: `.case-register` (class on the table) keeps references, types and headers unbroken from 768px up (the title column gives way, the named scroll region scrolls); vitest
   asserts the class and that the update time is shown in Kuwait time.
2. **English side rail, at every width of 768px and up (also desktop)**: "Responsiveness" and "Administration" were cut into pieces because the rail (9rem at <= 1120px, 10rem above) was
   narrower than the longest English word. Fix: 10.75rem and 11rem for the non-Arabic UI.
3. **Tables from 768px up** (courts, users, roles, lookups, audit, reports and others): `overflow-wrap: anywhere` let a column shrink to one letter ("Status", "Administrator",
   "SYN-UX", "JURISDICTION"). Fix: whole words inside `.table-scroll` tables from 768px, no break at the hyphen of references/codes (`bdi[dir=ltr]`, `td[dir=ltr]`). Phones keep their card/scroll behaviour.
4. **Managed lists (lookups) table had no scroll region**: it widened the page when its words stayed whole (found as an overflow when fix 3 was first applied to all tables).
   Fix: the table sits in a named, focusable `.table-scroll` region like every other wide table.
Re-runs (Chromium; the 91 above were 156 tablet rows, the later runs added the desktop width, 234 rows): after fix 1-3: 23 rows with findings; after fix 4 and the checker refinements (UUIDs and hyphenated words inside sentences are exempt): 10 rows, all "SYNTH-DOC" inside a sentence, which the refined
check no longer counts; the final 936-row matrix above ran with the final code and the final check. The refinements of the check are deliberate and visible in `smoke.mjs`; they were added after the 10 rows were seen.
Full vitest after the fixes: 968 tests passed; `eslint . --max-warnings 0` and `tsc` clean.

Screens reached only as empty or near-empty states in this local data (tasks, hearings, documents, experts, enforcement, notifications: only synthetic cases exist) are listed as before in
`docs/evidence/DOC-UX/SUMMARY.md`; the same limits apply. Not checked: real mouse/touch interaction at tablet width beyond the page load, print layouts, browser zoom,
Safari itself (the WebKit run is Playwright's Linux WPE build, a different embedder from macOS/iOS Safari, so it is evidence of WebKit engine layout, not of Safari chrome or iOS behaviour).

## WebKit: run rootless on this host without touching UAT, its OS or permissions

This host has no docker access for `aiagent`, no podman and no root. Playwright's `install-deps` (needs root) was not used. What worked, exactly (all inside the session scratchpad):

1. `PLAYWRIGHT_BROWSERS_PATH=<dir> npx playwright install webkit` from `frontend/e2e-wp` (a download; 1.64.1 needs WebKit revision 2370; Firefox 1555 and the Chromium headless shell 1248 were reused from an earlier install).
2. `apt-get download <package>` (no root; only fetches) for about 110 library packages and `dpkg -x` into a private prefix; `LD_LIBRARY_PATH` points at it. List and script: `frontend/e2e-wp/tools/rootless-browser-libs.sh`.
3. The two earlier blockers, resolved: (a) `libjxl.so.0.8` is not packaged by Ubuntu 24.04 (it has 0.7; Ubuntu skipped 0.8): `libjxl0.8_0.8.2-1_amd64.deb` from `snapshot.debian.org/archive/debian/20231011T205917Z/` unpacked into the prefix (it loads against the Ubuntu libhwy/brotli/lcms2).
   (b) no `/dev/dri` render node: Mesa's software EGL platform: `EGL_PLATFORM=surfaceless`, `LIBGL_ALWAYS_SOFTWARE=1`, `LIBGL_DRIVERS_PATH`, `__EGL_VENDOR_LIBRARY_FILENAMES` set to the prefix's `50_mesa.json` (needs `mesa-libgallium`, `libegl-mesa0`, `libgl1-mesa-dri`, `libllvm20`, `libpciaccess0` and a few libxcb libraries).
4. Two more obstacles met on the way: `libbacktrace.so.0` is not packaged by Ubuntu 24.04 at all: a **stub** library (three functions returning "nothing found") was compiled with gcc; WebKit only uses it to symbolise crashes, so a crash in this run would have produced no backtrace. And the browser's `MiniBrowser` wrapper script sets `LD_LIBRARY_PATH` itself, so the prefix was appended in that wrapper (a file inside the downloaded browser directory, not the host).
5. Result: `browser.version()` 27.2, then the full smoke matrix (312 rows) and the screenshots (`after-webkit-*.png`).
Firefox needed the GTK 3 set in a second prefix (same mechanism; `libgtk-3-0t64`, pango, cairo, gdk-pixbuf and the xcb libraries) and ran Firefox 157.0.
These are test-host workarounds in the scratchpad. The honest caveats: stubbed libbacktrace, a Debian-snapshot libjxl (0.8.2) instead of the one the build was linked for, and software GL; none of them is a production browser.

Not executed: WebKit in the official Playwright container, because `aiagent` cannot use `/var/run/docker.sock`. A docker-capable operator (devops is in the docker group) can run the same matrix there; the image
tag matches the repo's Playwright (1.64.1, verified above; tag `v1.64.1-noble` exists on `mcr.microsoft.com`, checked against the registry's tag list):

```bash
# from a checkout of this branch, with the throwaway stack from frontend/e2e-wp/README.md already running on the host (backend :8080, vite preview :4173)
docker run --rm --network host --ipc=host -v "$PWD":/work -w /work/frontend/e2e-wp \
  -e UX_URL=http://127.0.0.1:4173 -e UX_CREDS_FILE=/work/ux-creds.json -e UX_CASES_FILE=/work/ux-cases.json -e UX_OUT=/work/ux-out-webkit \
  -e UX_ENGINES=chromium,firefox,webkit -e UX_LOCALES=ar,en \
  mcr.microsoft.com/playwright:v1.64.1-noble \
  bash -c 'npm ci && node smoke.mjs'
```
(`ux-creds.json` and `ux-cases.json` are the files written by `tools/seed.mjs` with `UX_CREDS_FILE`/`UX_CASES_FILE`; the browsers are baked into that image, no `playwright install` is needed.)
