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
- `<html lang dir>` is driven by `src/i18n/locale.ts` (`en`/`ltr` now; `ar`/`rtl` is registered for Phase 7).
