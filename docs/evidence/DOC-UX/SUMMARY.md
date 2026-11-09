# DOC-UX: cross-engine smoke, layout and accessibility evidence

Status: VERIFIED_BY_DEVELOPER for the matrix below (frontend sources equal to develop at `4d83267`, PR #88, built with
`vite build` and served by `vite preview` against the Spring Boot jar on a throwaway embedded PostgreSQL; synthetic
SYNTH-DOC data; never UAT). Not QA_ACCEPTED. Raw per-row results: `results-summary.json`; tooling and how to rerun:
`frontend/e2e-wp/README.md`.

## Matrix (smoke.mjs): 312 rows = 39 screens x 2 engines x 2 locales x 2 widths

| Engine | Locale | Width | Screens | Page/console errors | Horizontal overflow | Wrong `dir` | axe violations |
|--------|--------|-------|---------|---------------------|---------------------|-------------|----------------|
| Chromium | ar (RTL) | 390 | 39 | 0 | 0 | 0 | 0 |
| Chromium | ar (RTL) | 1366 | 39 | 0 | 0 | 0 | 0 |
| Chromium | en | 390 | 39 | 0 | 0 | 0 | 0 |
| Chromium | en | 1366 | 39 | 0 | 0 | 0 | 0 |
| Firefox | ar (RTL) | 390 | 39 | 0 | 0 | 0 | not run (axe runs on Chromium) |
| Firefox | ar (RTL) | 1366 | 39 | 0 | 0 | 0 | not run |
| Firefox | en | 390 | 39 | 0 | 0 | 0 | not run |
| Firefox | en | 1366 | 39 | 0 | 0 | 0 | not run |

axe-core 4.14.0, tags wcag2a, wcag2aa, wcag21a, wcag21aa, wcag22aa, best-practice: 156 Chromium rows, 0 violations. axe checks
the DOM, which does not differ per engine; `UX_AXE_ALL=1` runs it on Firefox too.

Screens: dashboard, case register, intake, case page (all sections), case edit, library (list, new, reports, classifications),
search, directives, templates, deadlines, deadline settings, tasks, calendar, hearings, documents, experts, notifications,
archive, enforcement, reports (inventory, judgments, hearings, workload, experts, families, performance, claim results),
admin (users, roles, org, access, lookups, audit), courts, assistant status, profile.

## Interaction journeys (journeys.mjs): 80 steps (10 steps x 2 engines x 2 locales x 2 widths), 0 failed
Sign-in, wrong password refused, skip link focuses main, case register filter and open case, case page sections,
intake empty submit shows field errors and stays, search, assistant status, notifications/deadlines/calendar without
overflow, calendar month grid is rows of seven cells.

## Violations found, fixed, remaining
Found by the first Chromium Arabic pass (78 rows, 6 with findings) and fixed in PR #88:
1. Calendar month grid: aria-required-parent (30 nodes) and aria-required-children (critical). Fixed: week rows.
2. Case page: landmark-unique (the parties scroll region had the same name as its section). Fixed: distinct name.
3. Audit table: scrollable-region-focusable at 390px. Fixed: Users, Roles and Audit tables are named focusable regions.
4. Claim-results report: 3px horizontal overflow at 390px (grid-item margins). Fixed in pleadings.css.
5. Header links and table links under 24px target size (WCAG 2.2 2.5.8). Fixed.

New findings from the Firefox and English runs: none (0 rows with findings out of 312).

Remaining, not fixed, with reasons:
- Small-target heuristic still lists 64 `input` rows (unnamed inputs, not axe violations), inline links inside prose and
  ref links inside the dashboard lists. Inline links are exempt from WCAG 2.5.8; the inputs were not inspected further.
- The header search label is reported as "clipped" at 390px: it is the visually hidden label (1x1, absolute), so a false positive.
- axe was not run on Firefox/WebKit (DOM-level checks do not depend on the engine).
- WebKit: not run (below).
- Screens only seen as empty or near-empty states: tasks, hearings, documents, experts, archive, enforcement,
  deadlines, notifications (only synthetic cases were seeded; deeper journeys are untested there).

## WebKit: could not run on this host
The browser downloads (Playwright 1.64 build 2388, 1.55 build 2203) and about 60 missing system libraries can be unpacked
without root (`apt-get download` + `dpkg -x`), but two obstacles remain:
1. There is no `/dev/dri` render node, so headless WebKit (WPE) aborts with "Could not create WPE EGL display".
2. The build links `libjxl.so.0.8`, which Ubuntu 24.04 does not package (only 0.7); a 0.7-as-0.8 symlink gets past loading.
Solution: run the WebKit step in the official Playwright image (`mcr.microsoft.com/playwright`, ships the dependencies and
software GL) or on a host with root, a render node and `libjxl0.8`.

## Firefox on this host (no root)
Playwright reports missing GTK3 libraries. Workaround used: `npx playwright install firefox` from `frontend/e2e-wp` (the
engine revision must match that package's Playwright), unpack `libgtk-3-0t64`, pango, cairo, gdk-pixbuf, xcursor, etc. with
`apt-get download` + `dpkg -x` into a directory, and run with `PLAYWRIGHT_SKIP_VALIDATE_HOST_REQUIREMENTS=1` and that directory
on `LD_LIBRARY_PATH`.

## Screenshots
`chromium-{phone,desktop}-*.png` and `firefox-phone-*.png` for dashboard, cases, case, calendar, report-claims,
admin-access and intake (Arabic RTL; phone is 390px, desktop 1366px). Synthetic data only.
