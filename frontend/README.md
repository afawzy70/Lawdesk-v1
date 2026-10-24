# LawDesk frontend

React + TypeScript (strict) application shell built with Vite. Requires Node 22 (`.nvmrc`) and npm 10.
Dependencies are pinned to exact versions; install with `npm ci`.

## Scripts

| Command | Purpose |
|---------|---------|
| `npm run dev` | Dev server on http://localhost:5173 |
| `npm run build` | Type-check and build to `dist/` |
| `npm test` | Run the Vitest suite once (jsdom + Testing Library) |
| `npm run test:coverage` | Tests with V8 coverage (fails below 90 %) |
| `npm run lint` | ESLint (typescript-eslint strict type-checked, react-hooks), zero warnings allowed |
| `npm run typecheck` | `tsc --noEmit` for app and tooling configs |

## Dev proxy

`npm run dev` proxies `/api` and `/actuator` to the backend at `http://localhost:8080`
(override with `LAWDESK_API_ORIGIN=http://host:port npm run dev`). The header status indicator calls
`GET /actuator/health` and shows "API online" only for a 2xx `{"status":"UP"}` answer within 5 seconds;
anything else shows "API unavailable" without exposing response or error details.

## Conventions

- No tokens or session data in `localStorage`/`sessionStorage` (enforced by ESLint and tested).
- `<html lang dir>` is driven by `src/i18n/locale.ts` (`en`/`ltr`, `ar`/`rtl`).

## Early visual preview

The dashboard still shows the owner-requested sample data (see `docs/delivery/PREVIEW.md`); sign-in, cases and courts
are working screens on UAT, and the other areas show a "coming later" page.

- English/Arabic toggle in the header; the language is kept only in the `?lang=en|ar` URL parameter (no browser
  storage). Messages live in typed catalogs `src/i18n/messages/{en,ar}.ts` and are read with `useI18n()`; add every
  key to both catalogs (type-checked and tested).
- `/` is the dashboard preview; the other navigation entries show a "Coming in a later phase" page.
- All mock data lives in `src/preview/mockData.ts` and must stay obviously synthetic. Regions that show it use
  `SampleRegion`, which labels them "Sample data"; every page title ends with "(Preview)".
- Styles are plain CSS with logical properties (`margin-inline-start`, `border-inline-end`, …) so RTL mirrors
  automatically.
