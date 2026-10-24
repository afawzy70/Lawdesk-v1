# LawDesk v1 security baseline (LD-040)

**Date:** 2026-10-24. **Analysed:** `develop` at `19ad5386fa586e2469ecad36a677632273712f44` (merge of PR #46; its
backend and frontend are byte-identical to `1e5f340`, where the work started). Open Phase 5/6 pull requests were
looked at as a preview only and are labelled as such, with their head SHAs.

**Mandate:** `docs/governance/LAWDESK-DELIVERY-STRATEGY.md` §3 (security testing starts now, DAST only on isolated
targets) and §7 (delivery states). This is a baseline, not an assessment: the comprehensive assessment and full DAST
belong to Phase 8 (§5).

> **Automated scan results are not proof of security.** Zero findings from a scanner means only that its rules and
> databases matched nothing on the day it ran. Authorization logic, business rules, session handling and data
> integrity need the manual review in §4 and the access-control tests the workstreams own, and Phase 8 adds
> independent validation and full DAST.

**Delivery state of this document:** IMPLEMENTED on branch `docs/LD-040-security-performance-baseline`. Nothing here is
QA ACCEPTED.

## 1. Tools and exact versions

| Purpose | Tool | Version | Integrity |
|---------|------|---------|-----------|
| SCA | OSV-Scanner | v2.6.0 (osv-scalibr 0.5.2, commit `e840a6e8`) | `osv-scanner_linux_amd64` sha256 `ca69b3d3cd08f889a49dc0a383122f71cc528b83803671df5fd874d97485b108`, checked with `sha256sum -c` against the release's `osv-scanner_SHA256SUMS` |
| SCA | npm audit | npm 10.9.9, node v22.23.3 | registry advisory API |
| Dependency tree | Maven wrapper `dependency:tree` | as pinned in `backend/.mvn/wrapper`; JDK 21.0.12 | — |
| SAST | Semgrep OSS | 1.180.0, in a venv under `~/tools` (`pip install semgrep==1.180.0`) | wheel `semgrep-1.180.0-cp310…py314-none-manylinux_2_34_x86_64.whl` sha256 `16db2e2706ab814a975eeec25debfad49abba846b7c513d88e041447152f8447` (PyPI) |
| Secrets | gitleaks | 8.30.1 (the CI pin) | local binary sha256 `88f91962aa2f93ac6ab281d553b9e125f5197bbbce38f9f2437f7299c32e5509`; CI checks the tarball `551f6fc8…70eb` |
| Authz matrix | `tools/security/endpoint_matrix.py` | this PR | stdlib only; `tools/tests/test_endpoint_matrix.py` |

Evidence: `docs/evidence/SECURITY-BASELINE/` (01 RED, 02 GREEN, 03 matrix, 04 SCA, 05 SAST, 06 secrets, 07 open-PR
matrices, 08 docs scan).

## 2. Software composition analysis

**Method.**

- **Backend.** `./mvnw -B -q dependency:tree` resolved 152 distinct artifacts: 99 compile or runtime, 53 test-scope.
  OSV-Scanner's `-L backend/pom.xml` sees only the 14 declared dependencies, so the tree was turned into a CycloneDX
  SBOM (purls) and scanned with `--all-packages`.
- **Frontend.** `frontend/package-lock.json` (267 packages) was scanned with OSV-Scanner, then `npm audit --omit=dev`
  and a full `npm audit` were run.
- **Positive control.** `log4j-core 2.14.1` and `lodash 4.17.20` returned 7 and 5 advisories, which shows the
  scanner and database were answering.

**Result: 0 known vulnerabilities in runtime, dev or test dependencies.**

| Scope | Packages | Findings (crit/high/med/low) | npm audit |
|-------|---------:|------------------------------|-----------|
| Backend compile + runtime (Spring Boot 4.1.2, Spring 7.0.10, Spring Security 7.1.2, Tomcat 11.0.27, Jackson 3.1.7, Hibernate 7.4.13, pgJDBC 42.7.14, Flyway 12.4.0, Logback 1.5.38, SnakeYAML 2.6) | 99 | 0/0/0/0 | — |
| Backend test only (embedded-postgres 2.2.2, PostgreSQL binaries 18.6.0, Spring test starters) | 53 | 0/0/0/0 | — |
| Frontend runtime (react 19.3.1, react-dom 19.3.1, react-router 8.5.0 + transitive) | 10 | 0/0/0/0 | 0 (`--omit=dev`) |
| Frontend dev/build (vite 8.3.5, vitest 5.0.5, eslint 10.13.0, typescript 6.0.3, jsdom 30.1.3, …) | 260 (+27 optional) | 0/0/0/0 | 0 (full) |
| *Preview, PR #41 `5762bee`:* fastexcel 0.20.2, pdfbox/fontbox/pdfbox-io 3.0.8, icu4j 78.3 | 5 | 0/0/0/0 | — |

**Triage.** There is no finding to triage. **Proposed upgrades: none required today.**

**Blind spots.**

- Container base images are not covered: `postgres:18.6-alpine3.24`, the backend JRE image and the nginx web image
  (`backend/Dockerfile`, `frontend/Dockerfile`). They need an image scanner such as Trivy or Grype, which would run in
  CI or on a separate host (Docker is not available to this agent).
- The PostgreSQL native binaries are not covered.
- Maven plugins and the `mvnw` wrapper are not covered.

These are listed as NOT_RUN in §7.

**Ongoing.** Re-run on every dependency-changing PR. PR #41 adds the export libraries: re-scan its merged tree with
transitive resolution.

## 3. Secret scanning

**CI setup** (`.github/workflows/ci.yml`, job `secrets`):

- gitleaks 8.30.1 is downloaded with a pinned sha256 (`551f6fc8…70eb`).
- The checkout uses `fetch-depth: 0`, so every branch of the repository is scanned.
- It runs `gitleaks git --redact --exit-code 1 .` on every PR and every push to `develop` and `main`.

**`.gitleaksignore` (3 fingerprints, each with a recorded reason):**

| Fingerprint | Reason (from the file) | Assessment |
|-------------|------------------------|------------|
| `15998539…:docs/evidence/LD-002/01-red.log:generic-api-key:59` | Spring Boot's generated dev password, printed by an ephemeral test JVM in a RED run. The default user was removed and the value is redacted at HEAD. | ACCEPTED: not a credential of any running system |
| `db49a4a1…:qa/evidence/followup-bdd9d424-20261024/sha256.json:generic-api-key:7` and `:10` | SHA-256 digests of evidence files whose names contain "secret" | ACCEPTED: verified digests, not secrets |

**Local run.**

- `git fetch origin --prune` brought all 49 remote branches into the repository.
- `gitleaks detect --redact --log-opts=--all` then scanned 135 commits (28.3 MB): **no leaks found**.
- The agent sandbox refuses a separate `git clone --mirror`, so the fetched refs stand in for the mirror. Refs
  `refs/pull/*` of PRs whose branches were deleted are not covered.
- This PR's `docs/` and `tools/` were scanned with `gitleaks dir` before committing (evidence 08).

## 4. Authentication and authorization review

### 4.1 Endpoint → permission matrix (generated from code)

`python3 tools/security/endpoint_matrix.py [--root R] [--format markdown|json] [--check]` reads:

- every controller, and every `@RequestMapping`/`@GetMapping`/… with its `@PreAuthorize` (method level, otherwise
  class level, with constants resolved);
- the `permitAll` matchers and the logout URL of the filter chain;
- the default roles from the `role_permission` seeds of all migrations.

`--check` exits 1 when a controller endpoint has no rule. The repository test
`RepositoryTest.test_every_endpoint_on_this_commit_has_an_explicit_rule` keeps that true in CI from now on.

**Result on develop (`03-endpoint-matrix-develop.md`):** 52 endpoints.

- **43 PERMISSION**, each with one explicit `hasAuthority(...)`, or `hasAnyAuthority`/`or` for `/users/directory` and
  `/roles`.
- **3 AUTHENTICATED**: `GET /api/v1/me`, `POST /api/v1/me/password` and `GET /api/v1/release`, all
  `isAuthenticated()`.
- **5 PUBLIC**:
  - `GET /api/v1/auth/csrf` and `POST /api/v1/auth/login`;
  - `GET /actuator/health`, `/actuator/health/**` and `/actuator/info`. Of these, nginx forwards only health, liveness
    and readiness; `info` publishes only the name and version (QAD-010).
- **1 FILTER**: `POST /api/v1/auth/logout`, the LogoutFilter, which needs the CSRF token.
- The fallback is `anyRequest().authenticated()`.

**No endpoint lacks an explicit rule (NO_RULE = 0). No endpoint is NEEDS_REVIEW.**

On the open PR heads (`07-endpoint-matrix-open-prs.md`), the result is again **0 NO_RULE**:

| Head | Endpoints |
|------|----------:|
| #40 `ded7e3c` | 70 |
| #41 `5762bee` | 56 |
| #43 `0db2bef` | 81 |
| #44 `e4c8e47` | 85 |

**Comparison with CONTRACTS §3 and §8.2 and the default roles.** The tool reports which default role can call each
endpoint (columns ADMIN / LEGAL_MANAGER / LEGAL_STAFF):

- **Consistent:**
  - case deletion has no endpoint;
  - `CASE_STATUS_CHANGE`, court, judgment and special-decision management are manager/admin only;
  - user and role management is admin only;
  - `USER_VIEW` and `AUDIT_VIEW` are manager/admin;
  - `REPORT_VIEW` (dashboard; on #41 also the case and judgment reports) and `REPORT_EXPORT` are manager/admin;
  - `/users/directory` uses the `TASK_VIEW`/`TASK_MANAGE`/`REPORT_VIEW` rule of §8.3;
  - on #44, reading notes needs `CASE_VIEW` (§8.2).
- **Inconsistency for owner review:** LEGAL_MANAGER has `USER_VIEW` but not `ROLE_VIEW`. Managers can therefore list
  users (`GET /users`) but get 403 on `GET /roles`, so the user screen cannot name roles for them. This matches the
  seeds and §3, so it is a product question and not a defect (S-09).

### 4.2 Read endpoints that leak across permissions

Permissions are global: the product has no per-case ownership or assignment model. Under the default roles no leak
occurs, because every holder of `CASE_VIEW` also holds `PROCEEDING_VIEW` and `JUDGMENT_VIEW`. With custom roles
(LD-009 lets administrators create them), these paths cross permission boundaries:

| Id | Path | What crosses | Severity |
|----|------|--------------|----------|
| S-05 | `GET /api/v1/cases/{id}/timeline` (`CASE_VIEW`) | The timeline carries judgment date and outcome (`JudgmentService.java:61`), special-decision type, effect and date (`SpecialDecisionService.java:66`), and proceeding number and court (`ProceedingService.java:82`). A role with `CASE_VIEW` but no `JUDGMENT_VIEW` or `PROCEEDING_VIEW` reads them. | Low |
| S-06 | `GET /api/v1/cases/{caseId}/proceedings`, `/judgments`, `/special-decisions` (`PROCEEDING_VIEW` / `JUDGMENT_VIEW` only) | Case-scoped reads without `CASE_VIEW`: a role holding only `PROCEEDING_VIEW` can enumerate the judicial records of any case id. The same pattern appears on the open PRs: `/cases/{caseId}/hearings`, `/documents`, `/expert-referrals`. | Low |
| S-07 | `/api/v1/reports/*` on #34/#36/#41 (`REPORT_VIEW`/`REPORT_EXPORT`) | Reports list case titles, references, judgments and courts to holders of `REPORT_VIEW` without `CASE_VIEW` or `JUDGMENT_VIEW`. Intended by §8.2 (reports are their own permission), but worth stating to the owner. | Info |
| — | `GET /api/v1/documents` on #40/#44 (`DOCUMENT_VIEW`) | A global document list across all cases. Fine under global permissions; becomes a leak if case-level access is ever introduced. | Info |

### 4.3 IDOR

Every id in a path (`{id}`, `{caseId}`, `{proceedingId}`, `{partyId}`) is looked up and answers 404 when it is
missing. The services check that the parent exists, for example `JudicialRecords.requireCase` and
`CaseService.timeline`. No endpoint checks the id against a per-user case access list, because the product defines
none (no story asks for it, as of 2026-10-24).

- So there is **no IDOR relative to the current model**: anyone holding the permission may read any case.
- **ACCEPTED_RISK proposal (S-08):** state explicitly in the backlog that LawDesk v1 has firm-wide visibility. If
  case confidentiality ("Chinese walls") is ever required, it becomes a cross-cutting change: a case-access port that
  every case-scoped query uses.
- **Mitigation already present:** ids are random UUIDs (`gen_random_uuid()`), not sequential.

### 4.4 Sessions, CSRF and sign-in

| Control | Where | Status |
|---------|-------|--------|
| Session cookie HttpOnly, Secure, SameSite=Lax, 30 min idle | `application.yaml:33-40` | OK |
| Session state | Servlet `HttpSession` (in memory in Tomcat). The user is **reloaded from the database on every request** (`ReloadingSecurityContextRepository`), so disablement, role changes and credential-version revocation apply at once. This is not Spring Session JDBC: sessions do not survive a restart, and one backend instance is assumed. | OK for v1; note for scaling |
| Session fixation | `AuthController.java:74` changes the session id when a session exists; otherwise a new one is created at save | OK |
| CSRF | `csrf.spa()` with the `XSRF-TOKEN` cookie echoed in `X-XSRF-TOKEN` (`SecurityConfig.java:75`). Login and logout are covered too. | OK |
| CSRF token at sign-in | Sign-in is a custom controller, so Spring's `CsrfAuthenticationStrategy` is not invoked. Whether the token is rotated at sign-in depends on the SPA handler, and the qa client comments that it rotates. Verify that `XSRF-TOKEN` changes across sign-in (S-02). | NEEDS_REVIEW |
| Cookie scope on the shared host | UAT runs under `*.167-86-90-174.sslip.io`, a parent domain shared with other apps on the host. Unless that name is a public suffix, a sibling host can set a parent-domain `XSRF-TOKEN` or `JSESSIONID` cookie (cookie tossing), which enables CSRF-token fixation and login CSRF. Recommend `__Host-` cookie names (Path=/, Secure, no Domain) at least for production, and a dedicated domain (S-02). | OPEN (Medium) |
| Brute force | 5 failures lock the account for 15 min (`UserAccounts.java:24-26`); bcrypt cost 12; unknown users go through Spring's dummy-hash path. There is no per-IP or global throttle, so anyone who knows a username can keep that user locked out (S-03). | OPEN (Medium) |
| Password change | Needs the current password. Wrong attempts are audited (`MeController.java:69`) but not counted, so a hijacked session can guess the current password at bcrypt speed (S-04). | OPEN (Low) |
| Audit of failed logins | Stores the attacker-supplied login string, `actor_username` up to 64 characters (`AuthController.java:97`). Bound and parameterised, so not an injection; noise or spoofing only (S-10). | Low |
| Security headers | CSP `default-src 'none'` on the API, the nginx CSP on the SPA, `X-Frame-Options DENY`, `nosniff`, `Referrer-Policy no-referrer`, no-store cache; the firewall-rejection path writes the same headers (QAD-009). HSTS is set by the shared proxy. | OK |
| Errors | `include-stacktrace: never`; RFC 9457 problems without internals; 401 for anonymous and 403 for missing permissions; authorization runs before binding (QAD-026) | OK |
| SQL | All repositories bind parameters. Dynamic `ORDER BY` uses whitelisted columns (`CaseRepository.orderBy`, the report `SortField` enums). The uuid[] literals in the #34/#36 report ports are built from UUID objects only. | OK |

### 4.5 Uploads and exports (open PRs, preview)

**Uploads.** PR #40 `ded7e3c` (`documents/DocumentController.java`):

- Upload needs `DOCUMENT_MANAGE` and multipart content.
- Limits: `max-file-size 10MB` and `max-request-size 11MB`, with lazy resolution.
- Per §8.3, the declared type must be PDF and the content must start with `%PDF-`, with at most 10 per case under a
  quota lock.
- Downloads answer `attachment` with an RFC 5987 filename, `nosniff` and no caching.

Matches CONTRACTS §8.3. Two notes:

- **S-11 (Low):** the upload is buffered in the read-only container's `/tmp` tmpfs and then in heap (`bytea`). This
  costs memory inside the 768 MiB limit; see the performance baseline (P-08).
- Content is not malware-scanned (ACCEPTED_RISK proposal; PDFs are never rendered server-side).

**Exports.** PRs #39/#41 `5762bee` (`reports/ReportExports.java`, `Csv.java`, `Xlsx.java`):

- `REPORT_EXPORT`, audited without data.
- CSV is UTF-8 with a BOM, and formula-leading cells get a `'` prefix.
- At most 10 000 rows (`ReportExports.java:27`), with libraries pinned under permissive licences.

Matches §8.3. Exports are `GET` requests, which is CSRF-safe because they cause no state change other than the audit
row.

### 4.6 CI and infrastructure observations

| Id | Finding | Severity |
|----|---------|----------|
| S-01 | `deploy-staging` gets `secrets: inherit` (`ci.yml:311`) but uses only `LAWDESK_STAGING_SSH_KEY` and `LAWDESK_STAGING_KNOWN_HOSTS` (`deploy-staging.yml:93-94`). Pass those two explicitly (least privilege). The called workflow is in the same repository, so exploiting this needs write access anyway. | Low |
| S-12 | `deploy/compose.yaml` sets `mem_limit` (db 512m, backend 768m, web 64m) but no `cpus` or `pids_limit`. A runaway LawDesk process, or a mistaken load test, could take CPU from Matbakhy on the shared host. See the performance baseline §6. | Medium (availability) |

## 5. SAST

**Semgrep OSS 1.180.0, two runs:**

1. The four required rulesets, `p/java`, `p/typescript`, `p/react` and `p/secrets`: 173 rules on 323 tracked files,
   **0 findings**.
2. With `p/security-audit` and `p/owasp-top-ten` added: 464 rules, **6 findings**.

| # | Rule | File:line | Semgrep severity | Triage | Reasoning |
|---|------|-----------|------------------|--------|-----------|
| 1 | `secrets-inherit` | `.github/workflows/ci.yml:311` | ERROR | TRUE_POSITIVE (Low) → S-01 | All repository secrets go to a reusable workflow that needs two |
| 2 | `npm-missing-minimum-release-age` | `frontend/.npmrc:1` | MEDIUM | NEEDS_REVIEW (Low) | `min-release-age` exists only in npm ≥ 11.10; the project uses npm 10.9.9 with exact pins (`save-exact`) and a lockfile, which already blocks silent version drift. Propose adopting it with an npm 11 upgrade. |
| 3 | `missing-internal` | `frontend/nginx.conf:24` | WARNING | FALSE_POSITIVE | `/api/` is the public API proxy to a fixed upstream (`$backend` is a constant); `internal` would break the SPA. No user-controlled URL reaches `proxy_pass`, so there is no SSRF. |
| 4 | `request-host-used` | `frontend/nginx.conf:25` | WARNING | FALSE_POSITIVE | The `Host` header is forwarded to the backend, but the shared proxy routes only the LawDesk host name to this container, so an arbitrary Host never arrives. The backend uses it only for `Location` headers. |
| 5 | `missing-internal` | `frontend/nginx.conf:33` | WARNING | FALSE_POSITIVE | Same as 3, for the public health probe |
| 6 | `request-host-used` | `frontend/nginx.conf:34` | WARNING | FALSE_POSITIVE | Same as 4 |

**By severity:** 1 ERROR (TP, Low), 1 MEDIUM (NEEDS_REVIEW), 4 WARNING (all FALSE_POSITIVE). Java and TypeScript
application code: 0 findings.

**What the tool cannot cover:**

- authorization logic: whether a rule is the *right* permission (that is §4);
- business-rule and data-integrity flaws, race conditions and locking (QAD-024 class);
- cross-request flows: session fixation, CSRF semantics, cookie scope;
- Spring configuration semantics, such as the filter-chain order or `permitAll` breadth;
- dataflow across modules: the OSS engine is intra-file for most rules;
- test code: test directories are skipped by `.semgrepignore`;
- dependencies (that is §2), and container images and infrastructure.

SpotBugs with FindSecBugs was not run, because one SAST tool was required. It would add bytecode-level checks and is a
candidate for Phase 8.

## 6. DAST: scoped plan (NOT_RUN)

**Status: NOT_RUN.** Reason: there is no isolated LawDesk target. This agent has no Docker, and strategy §3 and the
safety limits forbid DAST against shared UAT. The plan:

- **Target.** An isolated LawDesk stack on a separate host or VM, or on this host only in a resource-bounded throwaway
  compose project:
  - it has its own network and no `lawdesk-edge` attachment;
  - it is never behind the shared Caddy;
  - it has `cpus`/`mem_limit` caps;
  - it is never on Matbakhy networks.
- **Data.** Synthetic only: `tools/performance/db/seed-*.sql` on its throwaway database, or `k6/seed-via-api.js`.
  The accounts are synthetic: admin, manager, staff, and a viewer on `QA_READ_ONLY`.
- **Tool.** OWASP ZAP (pinned release, checksum verified), run as:
  1. `zap-baseline` (passive) against the SPA;
  2. `zap-api-scan` against the API with a route list generated by
     `tools/security/endpoint_matrix.py --format json`. There is no OpenAPI document yet; springdoc would need
     owner approval.
  3. Authenticated contexts for each role, with the CSRF header script (`X-XSRF-TOKEN` from the cookie), so that the
     scan also checks 403s across roles.
- **Rate limits.** At most 10 requests per second, 2 threads, and a 2-hour maximum.
- **Exclusions:**
  - `POST /api/v1/auth/logout` (it ends the scan session);
  - `PUT /api/v1/users/{id}/enabled` and `/roles` against the scanning admin itself;
  - `password-reset`;
  - active-scan payloads on `DELETE` routes. These run only after a database snapshot, so the target can be reset.
- **Abort criteria:**
  - target 5xx rate above 5 % for 1 minute;
  - host load above 4 on the scan host;
  - any request seen outside the target host.
- **Outputs.** HTML and JSON reports in `docs/evidence/`, with every alert triaged as in §5, and the same caveat that
  a clean run is not proof.

## 7. Findings, and everything NOT_RUN

### 7.1 Findings

| Id | Finding | Severity | Status |
|----|---------|----------|--------|
| S-02 | Cookie scope on the shared parent domain; no `__Host-` prefix; CSRF token rotation at sign-in unverified (`application.yaml:36-40`, `SecurityConfig.java:75`, `AuthController.java:74`) | Medium | OPEN |
| S-03 | Account lockout without per-IP throttling allows targeted lockout of known users (`UserAccounts.java:24-26`) | Medium | OPEN |
| S-12 | No CPU or pids limits on LawDesk containers on the shared host (`deploy/compose.yaml:37,70,92`) | Medium (availability) | OPEN |
| S-05 | Timeline summaries expose judgment, decision and proceeding details to `CASE_VIEW`-only custom roles (`JudgmentService.java:61`, `SpecialDecisionService.java:66`, `ProceedingService.java:82`) | Low | OPEN |
| S-06 | Case-scoped child reads do not require `CASE_VIEW` (`ProceedingController.java:33`, `JudgmentController.java:33`, `SpecialDecisionController.java:33`; same pattern on the #33/#40/#43 heads) | Low | OPEN |
| S-04 | Wrong current password in a password change is not counted (`MeController.java:67-71`) | Low | OPEN |
| S-01 | `secrets: inherit` to deploy-staging (`ci.yml:311`) | Low | OPEN |
| S-11 | Upload buffering in tmpfs and heap (PR #40) | Low | OPEN (with P-08) |
| SG-2 | npm `min-release-age` (`frontend/.npmrc:1`) | Low | ACCEPTED_RISK proposal until npm 11 |
| S-10 | Free-text login stored in failed-login audit rows (`AuthController.java:97`) | Low | ACCEPTED_RISK proposal |
| S-08 | Firm-wide visibility: no per-case access model | Info | ACCEPTED_RISK proposal (owner) |
| S-07 | Reports expose case and judgment data to `REPORT_VIEW` holders | Info | ACCEPTED_RISK proposal (as designed, §8.2) |
| S-09 | LEGAL_MANAGER has `USER_VIEW` without `ROLE_VIEW` | Info | owner question |
| SG-3…6 | nginx `missing-internal` / `request-host-used` | — | FALSE_POSITIVE |

**Recommended first fixes:**

1. **S-02:** use `__Host-` cookie names and verify CSRF rotation. This change belongs to the security package (A), in
   a small separate PR (§8.1).
2. **S-03:** add a per-IP or global sign-in throttle.
3. **S-12:** add `cpus`/`pids_limit` caps in `deploy/compose.yaml`, with owner approval.

### 7.2 NOT_RUN

| Item | Reason |
|------|--------|
| DAST (ZAP baseline and API scan) | No isolated target. DAST on shared UAT is forbidden (strategy §3); this agent has no Docker. Plan in §6. |
| Container image scanning (Trivy or Grype) | Needs the built images and Docker, or a registry pull with an image scanner; not available to this agent. Proposed as a CI job, which needs owner approval because this PR does not edit CI. |
| SpotBugs + FindSecBugs | One SAST tool was required, and Semgrep was chosen. |
| Mirror-clone secret scan | The sandbox refuses a clone outside the worktree; the fetched refs of all 49 branches were scanned instead (§3). |
| Manual verification of CSRF rotation on UAT | It would need writes and sessions beyond the read-only budget; listed as S-02. |
