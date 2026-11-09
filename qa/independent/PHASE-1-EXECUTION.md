# Phase 1 independent execution

Only use a disposable checkout/snapshot of the exact PR SHA. Never alter Claude's
branch/workspace, use a shared database, run deployment scripts or change proxy
configuration. Production files are compared to every git blob at the candidate
SHA before the HTTP runner executes. `git archive` can convert `.cmd` line endings;
restore canonical blob bytes for hash verification. Extra QA test files are
explicit overlays, not product modifications.

## Repository / real HTTP probes

`phase1_review.py` checks Git-invalid ref names against native Git, 71/72/73-char
commit subjects, PR branch flows, public health, minimal info, anonymous API and
actuator denial, exact proposed POST status, baseline headers including firewall
rejections, correlation IDs at 1/64/65 chars and 20 concurrent requests.

For a datasource-requiring candidate use `run_phase1_postgres.py`:

```sh
python3 qa/independent/run_phase1_postgres.py \
  --candidate /tmp/lawdesk-qa-pr2-<sha12> --sha <full-sha> \
  --java <jdk21>/bin/java --jar /tmp/lawdesk-qa-pr2-<sha12>/backend/target/lawdesk-backend-0.0.1-SNAPSHOT.jar \
  --output qa/evidence/pr-2-<sha12> --pg-bin <verified-embedded-postgres18>/bin
```

This starts an exclusively owned temporary PostgreSQL cluster with a test-only
role, trust authentication bound to 127.0.0.1, and an ephemeral port; it is not a
production authentication model. The cluster's `postgres` database is disposable,
not a shared service. The wrapper stops the cluster and removes it on exit.
The API jar is stopped by its owning runner. No running process is selected by
name or killed globally. The initial one-off build/JDK/cache paths are recorded
in logs. Do not silently substitute installed versions or arbitrary DB URLs.

Exit 1 means failed assertions. Each of the 40 narrower assertions is separate
from complete acceptance-case certification. Three proposed-contract comparisons
(info allowlist, POST status and universal headers) intentionally expose contract
mismatches and must be resolved with scope decisions, not waived as passed.
Responses are synthetic; session cookies are redacted before persistence.

## Independent database tests

1. In the clean candidate backend run the committed Maven wrapper `verify` with
   Java21, a QA-local Maven cache and real ephemeral PostgreSQL. Record output and
   exact SHA; never count the developer suite as independent coverage.
2. Copy `qa/independent/java/IndependentDatabaseAcceptanceTest.java` into only the
   disposable candidate's `backend/src/test/java/com/lawdesk/platform/`.
3. Run `./mvnw -o -B -Dmaven.repo.local=<qa-cache>/repository -Dtest=IndependentDatabaseAcceptanceTest test`.
4. Save sanitized log and parse Surefire testcase results. These seven independent
   assertions verify migration history/version, whitespace-only raw SQL values,
   64/65 length and transactional rollback. They use the candidate's test-server
   provisioner, but the SQL/test oracles are QA-owned. No product source changes.
5. Keep failures RED for Claude to fix. Do not change the expected results to make
   the candidate green. A later SHA requires rerun and its own evidence directory.

This does not certify upgrade/restore, least-privilege production roles, every
constraint or complete migration concurrency coverage. Developer tests provide
additional evidence, identified separately.

## Real React tests

1. In the clean candidate `frontend/`, install exact `package-lock.json` with
   `npm ci --cache <qa-cache>`, using the declared Node22 engine.
2. Run original lint/typecheck/test:coverage/build before adding overlays.
3. Copy `browser/phase1-react.test.tsx` into only the disposable candidate's
   `frontend/src/qa/phase1-react.test.tsx`.
4. Run `npm test -- src/qa/phase1-react.test.tsx`.

The 11 assertions render real components, controlling network responses only.
They cover 401/403/404/500/503 false-online prevention, malformed status payloads,
unmount abort and unknown-route/Arabic direction shell behavior. These are jsdom
component tests; no Playwright/browser pass is claimed. Initial dependency cache
may be reused only with unchanged lockfile and after verifying source blobs.

## Reporting

Keep the six original baseline findings OPEN until explicitly resolved. Product
case ledgers remain BLOCKED where contract, fixture, dependent UI/identity,
deployment enforcement or whole-case evidence is absent. Do not sum independent
assertion results with the 351 product-case ledger or developer test counts.
Always bind PR feedback to the tested SHA; a newer head is unverified until rerun.
