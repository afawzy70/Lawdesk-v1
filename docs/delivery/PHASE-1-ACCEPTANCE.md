# Phase 1 — refined acceptance criteria (PROPOSED, awaiting product/QA approval)

`product/stories.json` holds Phase 1 stories with templated criteria (`DRAFT_NEEDS_REFINEMENT`), and the matching
`qa/test-cases.json` entries are generic scenarios. This file proposes concrete, testable criteria and maps each
QA case to an executable check. The baseline files are not edited here; product/QA owns them and can adopt this
mapping in a separate pull request. Status values: `AUTOMATED` (test exists and was run by the developer),
`BLOCKED` (cannot be executed yet, reason given), `N/A` (scenario does not apply; reviewer to confirm).
Developer runs are not independent QA: every case stays `NOT_RUN` in `qa/test-cases.json` until QA executes it.

## LD-001 Repository conventions and branch policy

Acceptance criteria:

1. The repository has a documented layout and contribution rules (`CONTRIBUTING.md`), editor/line-ending
   settings, ignore rules that exclude secrets and build output, code owners and a pull request template that
   asks for story IDs, QA case IDs, TDD evidence and test results.
2. Branch names are `main`, `develop` or `<prefix>/<slug>` with prefix in feature, fix, docs, qa, chore, release, hotfix.
   Anything else is rejected with a message naming the allowed prefixes and an example.
3. Commit subjects follow Conventional Commits (`type(scope): summary`, ≤ 72 chars); git/GitHub merge and revert
   subjects are accepted. Violations are rejected with an actionable message.
4. Pull requests from work branches may target only `develop`; only `release/*` and `hotfix/*` may target `main`.
5. The checks run locally through opt-in git hooks and in CI on every push and pull request (CI wiring in LD-005).
6. Direct pushes to `main`/`develop` and self-approval are prevented by GitHub branch protection — **blocked**
   (B4: unavailable for this private repository on the current GitHub plan).

| QA case | Scenario | Mapping | Status |
|---------|----------|---------|--------|
| QA-0001 | authorized happy path | Valid branch/commit/PR accepted — `BranchNameTest.test_accepts_*`, `CommitMessageTest.test_accepts_*`, `PullRequestFlowTest.test_work_branches_target_develop`, layout test | AUTOMATED |
| QA-0002 | required-field validation | Empty branch name, empty/comment-only commit message rejected | AUTOMATED |
| QA-0003 | invalid input boundary | 73+ char subject, malformed/traversal branch names, unknown prefix | AUTOMATED |
| QA-0004 | unauthenticated access | Direct push to protected branch must be refused by GitHub | BLOCKED (B4) |
| QA-0005 | insufficient permission | `feature/*` → `main` rejected; unknown base rejected | AUTOMATED |
| QA-0006 | record integrity and rollback | Checks are read-only; layout check on empty dir lists all missing files and creates nothing | AUTOMATED |
| QA-0007 | concurrent or repeat request | Checks are pure functions; repeated CLI invocations give the same exit code | AUTOMATED (CLI tests) |
| QA-0008 | API-to-UI state and error handling | No API/UI in this story; CLI exit codes 0/1/2 and stderr messages are the interface | AUTOMATED (CLI tests) |

## LD-002 … LD-006

Refined in each story's kickoff commit before its RED step.

## LD-003 React application shell

Acceptance criteria:

1. `frontend/` is a Vite + React + TypeScript (strict) project with exact-pinned dependencies, a committed
   `package-lock.json`, Node 22 declared in `.nvmrc` and `engines`, and npm scripts `dev`, `build`, `test`,
   `test:coverage`, `lint` and `typecheck`, all of which pass. `node_modules/`, `dist/` and `coverage/` are ignored.
2. Every page renders inside a shell with a banner showing "LawDesk", a navigation landmark labelled "Primary",
   a single `<main>` landmark and a footer showing `LawDesk v<version>` taken from `package.json` at build time.
3. `/` renders the home page; any unknown path renders "Page not found" inside the shell with an explanation and a
   "Go to the home page" link that navigates home.
4. The header shows the API status in a `role="status"` / `aria-live="polite"` region: "Checking API…" while
   `GET /actuator/health` is in flight, "API online" only for a 2xx JSON `{"status":"UP"}` answer, and
   "API unavailable" for any non-2xx status, redirect, network error, timeout (request aborted after 5 s),
   malformed JSON or other payload. Response bodies, error messages and stack traces are never rendered.
5. The status request is aborted when the indicator unmounts; under React StrictMode double-mount the first
   request is aborted and a late answer from it cannot overwrite the live result. No timers are left behind.
6. `<html lang dir>` is set from the locale setting: default `en`/`ltr`; `ar` (and tags such as `ar-EG`) yields
   `dir="rtl"`; unsupported or empty values fall back to `en`. No translation catalogs in Phase 1.
7. A top-level error boundary replaces a crashed tree with a generic "Something went wrong" alert, a
   "Try again" button and a home link, without showing the error message.
8. The application writes nothing to `localStorage`/`sessionStorage`; ESLint forbids `localStorage` in source.
9. The Vite dev server proxies `/api` and `/actuator` to `http://localhost:8080` (overridable with
   `LAWDESK_API_ORIGIN`); verified manually: with no backend running the proxied health call returns 502.

| QA case | Scenario | Mapping | Status |
|---------|----------|---------|--------|
| QA-0017 | authorized happy path | Shell landmarks, footer version, home route, "API online" for `{"status":"UP"}` — `App.test.tsx` (landmarks, version, home page), `health.test.ts` "reports online…", `ApiStatus.test.tsx` "shows API online…" | AUTOMATED |
| QA-0018 | required-field validation | No forms or input fields in the shell. Nearest equivalent (a required `status` field in the health payload) is covered: missing/non-string/null status → "API unavailable" (`health.test.ts`) | N/A (no user input; reviewer to confirm) |
| QA-0019 | invalid input boundary | Unknown routes → NotFound with home link (`App.test.tsx`); unsupported/empty/malicious locale tags fall back to `en` (`locale.test.ts`); malformed JSON and unexpected payloads → unavailable (`health.test.ts`) | AUTOMATED |
| QA-0020 | unauthenticated access | Authentication is introduced in Phase 2; the shell has no protected routes yet. 401 from the health endpoint shows "API unavailable" (`health.test.ts`) | BLOCKED (no auth until Phase 2) |
| QA-0021 | insufficient permission | Roles/permissions are introduced in Phase 2. 403 from the health endpoint shows "API unavailable" (`health.test.ts`) | BLOCKED (no authorization until Phase 2) |
| QA-0022 | record integrity and rollback | The shell creates or edits no records and stores nothing in browser storage (`App.test.tsx` "does not write to localStorage or sessionStorage"); error boundary recovers without data loss (`ErrorBoundary.test.tsx`) | N/A (no records in this story; reviewer to confirm) |
| QA-0023 | concurrent or repeat request behavior | StrictMode double-mount: first request aborted, stale late answer ignored, no React warnings; unmount aborts in-flight request; timeout/abort timers cleared (`ApiStatus.test.tsx`, `health.test.ts`) | AUTOMATED |
| QA-0024 | API-to-UI state and error handling | Non-2xx (301/401/403/404/500/503), network error, 5 s timeout, malformed JSON, DOWN/unknown status → "API unavailable" with no response body, error text or stack shown; error boundary hides error details (`health.test.ts`, `ApiStatus.test.tsx`, `ErrorBoundary.test.tsx`) | AUTOMATED |

Evidence: `docs/evidence/LD-003/` (`01-red.log` missing modules, `02-red-stubs.log` failing assertions against
signature stubs, `03-green.log`, `04-refactor.log` with lint, typecheck, tests, coverage, build and repo tooling).
