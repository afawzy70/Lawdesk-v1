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
| QA-0025 | authorized happy path | `baselineMigrationIsApplied`, `persistsAndReadsBackARelease`, `recordsTheRunningReleaseAtStartup` | AUTOMATED |
| QA-0026 | required-field validation | `rejectsMissingVersionWithoutPersisting` (blank, null) | AUTOMATED |
| QA-0027 | invalid input boundary | `acceptsVersionAtTheLengthLimitAndRejectsOneCharacterMore`, `databaseEnforcesTheSameRules…` | AUTOMATED |
| QA-0028 | unauthenticated access | No data endpoints exist; `/api/**` is deny-by-default (LD-002). DB credentials only via environment | N/A (reviewer to confirm) |
| QA-0029 | insufficient permission | Least-privilege DB role (app user owns only its schema, no superuser) is configured in LD-006 compose | BLOCKED until LD-006 |
| QA-0030 | record integrity and rollback | `failedTransactionLeavesNoPartialData`, `failingMigrationIsRolledBackCompletely`, `previousReleaseStillValidates…` | AUTOMATED |
| QA-0031 | concurrent or repeat request | `concurrentInsertsGetDistinctIdentifiers` (10 parallel inserts) | AUTOMATED |
| QA-0032 | API-to-UI state and error handling | `readinessDependsOnTheDatabaseButLivenessDoesNot`; UI shows "API unavailable" when readiness fails (LD-003) | AUTOMATED (partial) |

## LD-005 … LD-006

Refined in each story's kickoff commit before its RED step.
