# Early visual preview (bilingual shell and mock dashboard)

| | |
|---|---|
| Requested by | Product owner, 2026-10-23 |
| Branch | `feature/preview-shell` (based on `feature/phase-1-foundation`) |
| Code | `frontend/src/` |
| Evidence | `docs/evidence/PREVIEW/` |

## Owner request

> As soon as the React foundation is ready, provide a responsive Arabic/English application shell with navigation
> and a dashboard preview using clearly labeled mock data. Use the approved architecture and design patterns. The
> preview must not be represented as a functional legal case management system.

## What this is — and is not

- It **is** an early, read-only visual preview built on the LD-003 React shell, so the owner can see the layout,
  navigation and Arabic right-to-left rendering early.
- It is **not** one of the Phase 1 stories and adds no acceptance criteria to them.
- It does **not** satisfy LD-035/LD-036 (the Phase 7 internationalisation and RTL stories). Those stories still need
  their own refinement, complete catalogs, translation review, RTL QA and acceptance.
- It is **not** a working case management system: there is no backend data, no authentication, no forms and no
  create/edit/save actions. Every figure and record was invented and lived in one module,
  `frontend/src/preview/mockData.ts`. **Retired with LD-030:** the dashboard now shows real figures, and the module,
  the sample regions and their badges are gone.

## How the preview is labelled

- A persistent, non-dismissible notice (`role="note"`, named "Preview notice") on every page:
  "UAT test environment — enter test data only, never real client data. Areas marked “coming later” are not built
  yet." / "بيئة اختبار UAT — أدخل بيانات تجريبية فقط، ولا تُدخل بيانات موكلين حقيقية. الأقسام المعلَّمة «لاحقًا»
  لم تُبنَ بعد."
  The earlier wordings were retired as their claims stopped being true: "This is not a working case management
  system" once cases, courts and sign-in worked, and "The dashboard shows sample figures" once LD-030 replaced them.
- While mock data existed, every mock-data region showed a visible "Sample data" / "بيانات نموذجية" badge (retired
  with LD-030 together with the data).
- The document title ends with "(Preview)" / "(معاينة)", e.g. "Dashboard — LawDesk (Preview)".
- Mock data is obviously synthetic: cases are "Sample Case 001…005", courts are "Sample Court of First Instance /
  Appeal / Cassation"; no parties, lawyers, judges, firms, real case numbers or personal data.

## Screens

| Route | Screen |
|-------|--------|
| `/` | Dashboard preview: four KPI tiles (open cases, hearings this week, pending judgments, expert reports due), upcoming-hearings table, cases by stage (first instance / appeal / cassation) as text plus decorative HTML/CSS bars |
| `/cases`, `/hearings`, `/judgments`, `/experts`, `/documents`, `/reports`, `/admin` | "Coming in a later phase" placeholder stating the feature is not available in this preview |
| any other path | LD-003 "Page not found" page (now localised) |

Shell (all routes): skip-to-content link; header with the preview notice, product name, API status indicator (LD-003)
and language toggle; primary navigation as a side bar on wide screens (on the right in Arabic) that collapses behind
a "Menu" button (`aria-expanded`/`aria-controls`, Escape closes and returns focus) below 768 px; footer with version.

## Language (English / Arabic)

- Tiny in-house i18n, no new dependency: typed catalogs `src/i18n/messages/en.ts` and `ar.ts` (the Arabic catalog is
  typed against the English keys, and a test checks key and placeholder parity), a React context and `useI18n()`.
- The language toggle in the header switches `<html lang dir>` (`ar` → `rtl`) and every label.
- **Where the choice is kept:** in the URL query parameter `?lang=en|ar` only, and in-app links carry it. This keeps
  the existing rule that the app writes nothing to `localStorage`/`sessionStorage` (ESLint rule and test unchanged),
  survives reloads, and makes a language-specific link shareable. Unsupported values fall back to English.
- Dates and numbers use `Intl` with `en-GB` and `ar-EG` (Arabic-Indic digits), tested with a fixed instant and
  time zone. Dates on the dashboard render in the browser's time zone.

## How to run

```bash
cd frontend
npm ci
npm run dev                       # http://localhost:5173  (Arabic: http://localhost:5173/?lang=ar)
npm run build && npm run preview  # production build served on http://localhost:4173
```

No backend is needed; the API status indicator then shows "API unavailable".

## Test evidence

Strict RED → GREEN → REFACTOR, logs in `docs/evidence/PREVIEW/`:

- `01-red.log` — new tests written first; suite fails (50 failed, 47 passed) on missing modules and behaviour.
- `02-green.log` — implementation; full suite passes (134 tests). Before this logged run, one test was corrected:
  the "Arabic catalog is in Arabic script" check now exempts the language-toggle label, which intentionally names
  the other language in its own script ("English"). No production code changed for it.
- `03-refactor.log` — after refactoring: `npm run lint`, `npm run typecheck`, `npm run test:coverage` (existing 90 %
  thresholds), `npm run build`, and `python3 -I -m unittest discover -s tools/tests`. The refactor simplified
  placeholder interpolation and added two tests (Escape while the menu is collapsed, i.e. in the desktop side
  navigation, does not move focus; partial placeholder values): 136 frontend tests passed, 0 failed, 0 skipped; coverage 100 % statements and
  lines, 97.56 % branches, 98.14 % functions; 31 repository-tooling tests passed. Production build: about 277 kB
  (JS 270.7 kB, 86.3 kB gzipped).

Covered behaviour: catalog key/placeholder parity and Arabic script; formatting (en/ar, fixed time zone);
mock-data synthetic markers and consistency; preview notice and "(Preview)" title on every route in both languages;
no forms, inputs or create/edit/save/login buttons on any route; sample-data labelling of each region (visible and
accessible description); locale toggle switching `lang`/`dir`, labels and title, and `?lang=` persisting through
navigation; menu collapse/expand `aria-expanded` with keyboard, Escape and link selection; skip link; placeholders in
both languages; LD-003 behaviours (API status, not-found page, error boundary, no browser storage).

## Known limitations

- Responsive layout and visual RTL mirroring are implemented in CSS but verified only by review; jsdom does not
  apply media queries or layout, so tests cover the ARIA state and `dir`, not pixels. No visual-regression tests.
- Arabic copy is Modern Standard Arabic written by the implementer; it has not had a professional/legal
  translation review (that belongs to LD-035/LD-036).
- The top-level error boundary message stays English (it sits outside the router and language context).
- "Hearings this week" and all dates are fixed sample values, not relative to today.
- Language is not remembered across visits without `?lang=` (by design: no browser storage).
- The brand name "LawDesk" is shown in Latin script in both languages.

## QA findings addressed

Independent QA review of `ad0def0` (PR #5) reported two findings; both are fixed test-first.

| Finding | Fix | Regression tests |
|---|---|---|
| QAD-014: Escape right after opening the mobile menu (focus still on the toggle) left it open | While the menu is open, a document-level Escape handler closes it from anywhere and returns focus to the toggle (`layout/AppLayout.tsx`) | `App.test.tsx` › "closes with Escape pressed right after opening…" (en, ar) and "closes with Escape from elsewhere on the page…" |
| QAD-015: axe `scrollable-region-focusable` on `.table-scroll` at 375 px | The scroll wrapper is a named region (`role="region"`, `aria-labelledby` the localized table caption) with `tabIndex={0}`, so keyboard users can focus and scroll it; the global `:focus-visible` outline applies | `DashboardPage.test.tsx` › "hearings table scroll region (QAD-015)" (en, ar) |

Evidence: `docs/evidence/PREVIEW/07-red-qa-findings.log` (5 failing), `08-green-qa-findings.log` (141/141),
`09-refactor-full-checks.log` (lint, typecheck, coverage, build). A WebKit/screen-reader check remains NOT_RUN.
