# Reproduce continuation QA

Keep every previous evidence directory. The authoritative current runs are:

- PR2 `c0da6910884a7e85ee17e80a78416aac51de216c`: `qa/evidence/pr-2-c0da6910884a/execution-summary.json`.
- PR5 `ad0def0aeba514619f060fe1f150d1833a9f3fd9`: `qa/evidence/pr-5-ad0def0aeba5/execution-summary.json`.

Use canonical git-blob-verified snapshots under `/tmp/lawdesk-qa-pr<number>-<sha12>`.
Never alter Claude's worktree, shared Docker permissions/daemon, Matbakhy or proxy.
All release/deployment shell tests below use recorded fake transport, not a host deployment.

Run the existing Phase1 SQL and React tests as in PHASE-1-EXECUTION.md. SQL diagnostics
now report actual stored length/UTF8 hex but preserve the same seven test assertions.
The current `run_phase1_postgres.py` additionally probes ECS logs and stops/restarts
only its own temporary PostgreSQL using the same loopback ephemeral port:

```sh
python3 qa/independent/run_phase1_postgres.py --candidate <snapshot> --sha <full-sha> \
  --java <jdk21>/bin/java --jar <snapshot>/backend/target/lawdesk-backend-0.0.1-SNAPSHOT.jar \
  --output <new-evidence-directory> --pg-bin <postgres18>/bin
python3 qa/independent/deployment_edges.py --candidate <snapshot> --sha <full-sha> --output <evidence>
python3 qa/independent/compose_ci_review.py --candidate <snapshot> --sha <full-sha> --output <evidence>
```

`compose_ci_review.py` requires PyYAML (the candidate tools requirement) and the native
Compose CLI; config rendering needs no Docker daemon. It performs12 configuration
assertions, never starts containers. `deployment_edges.py` performs4 new failure
injection assertions; it executes the real smoke inner shell, preserving shell
semantics. Its valid-response control proves the fixture works. Neither runner
claims real-container rollback or production authentication coverage.

For developer deployment tests, a git archive lacks `.git` and makes the test's
`git check-ignore` assertion error. Initialize only the disposable QA snapshot with
`git init -q`, then rerun the same suite. The initial environment failure is retained
in `developer-deploy-tests.log`; corrected rerun34 tests passes separately. This is
not a product defect and is excluded from independent assertion counts.

For browsers, use a separate toolchain:

```sh
npm ci --prefix qa/independent/browser-runtime --cache /tmp/lawdesk-qa-npm-cache
```

Or install this exact lockfile under a disposable `/tmp` runtime. Install matching
Chromium using that runtime's Playwright CLI if not already cached. This run used
Playwright1.64.1, Chromium build1248, axe4.14.0. Missing Ubuntu libraries were downloaded
as debs and extracted only to `/tmp/lawdesk-qa-browser-libs`; no system packages or
services were changed. Set `LD_LIBRARY_PATH` to that extracted `usr/lib/x86_64-linux-gnu`
if necessary. Never borrow or modify another project's toolchain.

Build the PR5 frontend with its exact lockfile, then start only the QA fixture server:

```sh
python3 qa/independent/preview_server.py --candidate <pr5-snapshot> --port 18765
```

In another session, with explicit runtime and browser executable paths:

```sh
LAWDESK_QA_BROWSER_RUNTIME=<runtime> LAWDESK_QA_CHROMIUM=<chromium-executable> \
  node qa/independent/browser/preview-review.mjs <pr5-sha> http://127.0.0.1:18765 <browser-evidence>
LAWDESK_QA_BROWSER_RUNTIME=<runtime> LAWDESK_QA_CHROMIUM=<chromium-executable> \
  node qa/independent/browser/preview-security.mjs <pr5-sha> http://127.0.0.1:18765 <browser-evidence>
```

The first script has64 assertions across6 viewport widths and both locales; the
second has6 security assertions. Screenshots cover375/1366px. Fixture health503/401
is synthetic and the candidate nginx CSP is mirrored. This proves browser behavior
under that policy, not nginx networking, authentication, real API or deployment.
Safari/WebKit, Firefox and manual screen-reader testing are NOT_RUN.

The main script writes partial results after each assertion. Only final reports are
counted. Package metadata export was fixed after an unscored initial browser attempt;
its context is retained. The invalid `extended/` PostgreSQL restart fixture is
preserved with `verification_usable=false`; use only `extended-corrected/` in totals.
The failed recovery caused by the QA fixture must not be logged as a product defect.

Vite AC9 was tested separately against a QA-owned bound non-listening loopback port
with `LAWDESK_API_ORIGIN` explicitly overridden. Start real candidate Vite on an
unused QA port; request root (200) and `/actuator/health` (502). Never point this
experiment at a default port that may belong to another application. Stop Vite after
recording results. Actual responses are in `independent-vite-proxy.json`.

A changed PR HEAD requires a fresh snapshot and evidence directory, plus rerunning
applicable existing tests. Do not copy old PASS results into a new SHA or duplicate
scenario IDs. Keep developer reruns, independent assertions, blocked runtime cases,
NOT_RUN manual/browser scopes and the351 baseline-case ledger separate.
