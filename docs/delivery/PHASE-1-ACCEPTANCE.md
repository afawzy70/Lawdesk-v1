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

## LD-002 Backend bootstrap and health endpoints

Acceptance criteria:

1. `backend/` is a Java 21 / Spring Boot 4.1.2 Maven project built with the committed Maven wrapper; `./mvnw verify`
   compiles, runs all tests and produces an executable jar with build metadata.
2. `GET /actuator/health`, `/actuator/health/liveness` and `/actuator/health/readiness` are public and return
   `200 {"status":"UP"}` with no component details; `GET /actuator/info` returns only build name/version metadata.
3. Every other endpoint is deny-by-default: unauthenticated requests get `401 application/problem+json` (UTF-8,
   RFC 9457 shape) with no stack traces or class names; writes without authentication are rejected; actuator
   endpoints other than health/info (env, beans, configprops, heapdump, loggers, mappings, threaddump) are unreachable.
4. Responses carry `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`,
   `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'` and `Cache-Control: no-store`, including on
   rejected requests.
5. Every response carries `X-Request-Id`: a caller value matching `[A-Za-z0-9._-]{1,64}` is echoed, anything else is
   replaced by a fresh UUID; concurrent requests get distinct ids.
6. Logs are structured JSON (ECS) on stdout. Each request produces one `request completed` line with request id,
   method, path, status and duration; query strings and headers (e.g. `Authorization`) are never logged.
7. Graceful shutdown is enabled with a 20 s phase timeout; error responses never include stack trace, exception or message.

| QA case | Scenario | Mapping | Status |
|---------|----------|---------|--------|
| QA-0009 | authorized happy path | `HealthEndpointTest.healthIsPublicAndUp`, `livenessAndReadiness…`, `infoReports…` | AUTOMATED |
| QA-0010 | required-field validation | Unknown health group → 404 without internals (`unknownHealthGroupIsNotFoundWithoutInternals`); no input-bearing endpoints exist yet | AUTOMATED (partial — reviewer to confirm scope) |
| QA-0011 | invalid input boundary | Malformed/oversized `X-Request-Id` replaced (`replacesMalformedOrOversizedClientRequestId`) | AUTOMATED |
| QA-0012 | unauthenticated access | `unauthenticatedApiRequestIsRejectedWithProblemDetail`, `writeWithoutAuthenticationIsRejected` | AUTOMATED |
| QA-0013 | insufficient permission | Sensitive actuator endpoints unreachable (7 parameterized cases). Role-based checks need Phase 2 users | AUTOMATED (partial); role cases BLOCKED until LD-009/LD-010 |
| QA-0014 | record integrity and rollback | No persistence in this story; rejected writes cannot reach a handler. DB integrity is LD-004 | N/A (reviewer to confirm) |
| QA-0015 | concurrent or repeat request | `concurrentRequestsGetDistinctIds` (20 parallel requests) | AUTOMATED |
| QA-0016 | API-to-UI state and error handling | API side: problem JSON contract + health contract. UI side covered by LD-003 status indicator | AUTOMATED (API side) |

Cross-cutting cases touched (not closed): QA-0348 (no request data in logs — `completedRequestIsLoggedAsStructuredJson…`),
QA-0349 (security headers — `responsesCarryBaselineSecurityHeaders`). Secure cookies are Phase 2.

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

## LD-004 PostgreSQL migrations and test isolation

Acceptance criteria:

1. PostgreSQL 18 (18.6) is the database. Schema changes happen only through versioned Flyway migrations in
   `backend/src/main/resources/db/migration`; Hibernate runs with `ddl-auto=validate` and open-in-view is off.
2. The baseline migration `V1__app_release.sql` creates `app_release`; the application records its build version
   there at every start, giving deployment and rollback evidence.
3. Validation is enforced twice: bean validation rejects blank or >64-character versions before SQL is issued, and
   database constraints reject the same values when the application is bypassed. Rejected writes persist nothing.
4. A failed transaction leaves no partial rows; a failing migration leaves no partial schema and no success entry.
5. After a rollback to a previous release, that release still validates against a schema that already contains the
   newer release's migrations (Flyway ignores future migrations).
6. Readiness includes database health; liveness does not.
7. Tests use real PostgreSQL, never H2: each Spring test context gets a freshly created database with migrations
   applied from scratch, on an embedded PostgreSQL 18.6 server by default or on an external server given by
   `LAWDESK_TEST_DB_URL`/`_USER`/`_PASSWORD` (CI runs the official image this way from LD-005). Connection
   settings come only from environment variables; no credentials are committed.

| QA case | Scenario | Mapping | Status |
|---------|----------|---------|--------|
| QA-0025 | authorized happy path | `baselineMigrationIsApplied`, `persistsAndReadsBackARelease`, `recordsTheRunningReleaseAtStartup`; QA edge fixture: `migratingAnUpToDateDatabaseAgainChangesNothing`, `concurrentMigratorsApplyEachMigrationExactlyOnce` | AUTOMATED |
| QA-0026 | required-field validation | `rejectsMissingVersionWithoutPersisting` (blank, null) | AUTOMATED |
| QA-0027 | invalid input boundary | `acceptsVersionAtTheLengthLimitAndRejectsOneCharacterMore`, `databaseEnforcesTheSameRules…` | AUTOMATED |
| QA-0028 | unauthenticated access | No data endpoints exist; `/api/**` is deny-by-default (LD-002). DB credentials only via environment | N/A (reviewer to confirm) |
| QA-0029 | insufficient permission | Least-privilege DB role (app user owns only its schema, no superuser) is configured in LD-006 compose | BLOCKED until LD-006 |
| QA-0030 | record integrity and rollback | `failedTransactionLeavesNoPartialData`, `failingMigrationIsRolledBackCompletely`, `previousReleaseStillValidates…` | AUTOMATED |
| QA-0031 | concurrent or repeat request | `concurrentInsertsGetDistinctIdentifiers` (10 parallel inserts) | AUTOMATED |
| QA-0032 | API-to-UI state and error handling | `readinessDependsOnTheDatabaseButLivenessDoesNot`; UI shows "API unavailable" when readiness fails (LD-003) | AUTOMATED (partial) |

## LD-005 CI quality gates

Acceptance criteria:

1. `.github/workflows/ci.yml` runs on every pull request into `develop`/`main`, on pushes to them and on manual
   dispatch, with five gates: **Repository policy**, **Backend (Java 21, embedded PostgreSQL 18.6)**,
   **Backend against postgres:18.6 image**, **Frontend (Node 22)**, **Secret scan (gitleaks 8.30.1)**.
2. Policy: tooling tests, required files, PR source/target flow, every PR commit message, actionlint.
3. Backend: `./mvnw verify` on Temurin 21; the same suite also runs against the official `postgres:18.6-alpine` image.
4. Frontend: `npm ci`, lint (zero warnings), typecheck, tests with coverage thresholds (90 %), build, and
   `npm audit --omit=dev --audit-level=high`.
5. Secrets: gitleaks over the full git history; allowlist entries in `.gitleaksignore` need a written reason.
6. Supply chain and least privilege: read-only `GITHUB_TOKEN`, every action pinned to a commit SHA, downloaded tools
   checksum-verified, no `pull_request_target`, job timeouts ≤ 30 minutes, superseded runs cancelled.
7. A broken change fails CI (demonstrated by a throwaway negative-check PR).
8. Merging is blocked when a gate fails — **blocked (B4)**: GitHub cannot enforce required checks on this private
   repository's plan, so enforcement is procedural until the owner changes the plan or visibility.

| QA case | Scenario | Mapping | Status |
|---------|----------|---------|--------|
| QA-0033 | authorized happy path | CI run 39972644083 on PR #2: 5/5 gates green (31 tooling, 38 backend ×2, 50 frontend tests) | AUTOMATED |
| QA-0034 | required-field validation | `test_ci_workflow.py` fails if any required gate/command is removed | AUTOMATED |
| QA-0035 | invalid input boundary | Negative check run 39973069588: bad commit subject, failing backend and frontend tests → 4 gates fail | AUTOMATED |
| QA-0036 | unauthenticated access | Read-only token, no `pull_request_target`, no secrets in workflow (`SecurityPostureTest`) | AUTOMATED |
| QA-0037 | insufficient permission | Required status checks / no self-merge not enforceable by GitHub on current plan | BLOCKED (B4) |
| QA-0038 | record integrity and rollback | Secret scan over full history caught a real finding (LD-002 fallback user) that was fixed | AUTOMATED |
| QA-0039 | concurrent or repeat request | `concurrency` cancels superseded runs; "only the verified SHA deploys" belongs to LD-006 | AUTOMATED (CI part) |
| QA-0040 | API-to-UI state and error handling | Failure surfaces as job annotations; test-report upload is best-effort because the artifact quota is exhausted (B6) | AUTOMATED (partial) |

## LD-006 Contabo staging and HTTPS reverse proxy

Acceptance criteria:

1. `deploy/compose.yaml` defines an isolated project `lawdesk-staging` (db, backend, web) with no published host ports;
   db and backend sit on an internal-only network; only web joins the external `lawdesk-edge` network (alias
   `lawdesk-web`). No fixed container names, absolute bind mounts or shared volumes with other projects.
2. Containers drop all capabilities, set `no-new-privileges`, have health checks, memory limits, bounded JSON logs and
   restart policies; backend and web have read-only root file systems; images are digest-pinned (db, bases) or
   selected by an explicit release SHA (backend, web). Rendering without a release tag fails.
3. Passwords are files under git-ignored `deploy/secrets/`; the backend connects as `lawdesk_app` (no superuser,
   no CREATEROLE/CREATEDB), which owns only the `lawdesk` schema.
4. `deploy.sh <sha>` accepts only a full lowercase SHA, takes a lock, pulls, starts with health-check wait, runs the
   smoke test and records the release; on failure it redeploys the previous release (exit 1) and never removes
   volumes or networks. `rollback.sh` redeploys the previous release.
5. Smoke test from inside the web container: `/healthz`, `/`, readiness UP, `/api/v1/cases` → 401, `/actuator/env` → 404.
6. Baseline observability: ECS JSON logs with request ids, readiness tied to the database (503 during an outage,
   recovering afterwards, liveness unaffected), `app_release` row and log line per start, deploy history file.
7. HTTPS on a dedicated hostname through the shared proxy — **prepared, not applied** (`deploy/proxy/lawdesk.caddy`,
   B1). Deployment on Contabo — **blocked** (B2: no deploy mechanism, `aiagent` has no Docker access).

| QA case | Scenario | Mapping | Status |
|---------|----------|---------|--------|
| QA-0041 | authorized happy path | CI stack job (run 39975575391): images built, stack healthy, smoke passed | AUTOMATED in CI; on Contabo BLOCKED (B1, B2) |
| QA-0042 | required-field validation | `deploy.sh` without SHA/registry → exit 2, no docker calls; compose without `LAWDESK_TAG` fails | AUTOMATED |
| QA-0043 | invalid input boundary | Short/uppercase/41-char/path-like SHAs rejected | AUTOMATED |
| QA-0044 | unauthenticated access | No host ports; db/backend internal-only; only health and `/api` forwarded; `/actuator/env` → 404 through web | AUTOMATED (CI); proxy IP allow-list BLOCKED (B1) |
| QA-0045 | insufficient permission | `lawdesk_app` not superuser/createrole/createdb (CI SQL check); containers without capabilities | AUTOMATED |
| QA-0046 | record integrity and rollback | Failed start or smoke → automatic rollback; rollback script; no volume removal; DB outage/recovery in CI | AUTOMATED (scripts with fake docker; stack in CI). Real rollback on Contabo BLOCKED (B2) |
| QA-0047 | concurrent or repeat request | Deploy lock (exit 3); redeploying the current release is idempotent | AUTOMATED |
| QA-0048 | API-to-UI state and error handling | Smoke covers SPA, readiness and 401 through nginx; UI shows API status | AUTOMATED (CI) |

Cross-cutting: QA-0351 (TLS and staging domain isolation) stays BLOCKED until B1 is approved and applied.
