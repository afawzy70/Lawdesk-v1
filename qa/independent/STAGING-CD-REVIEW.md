# Reproduce staging CD QA safely

This extends CONTINUATION-EXECUTION.md. Do not run bootstrap/gate/attach/detach,
deploy or rollback directly on a host. The independent CD runner replaces **all**
Docker/curl/gh operations with fixture transports before invoking candidate shell
scripts. Git ancestry is exercised against a temporary real local origin, not GitHub.
The child HOME is isolated, and only synthetic temporary SSH keys are generated.
No actual SSH session, deployment, container or shared proxy operation is performed.

From the QA workspace with the candidate commit fetched:

```sh
python3 qa/independent/cd_review.py \
  --candidate /tmp/lawdesk-qa-pr7-32724ae88983 \
  --sha 32724ae88983ac72a43d49d7f3a8db3e253a989e \
  --output <new-evidence-directory>
```

Exit1 is intentional at this candidate:16 assertions yield10 PASS/6 FAIL. The
current report uses `valid-key-retest/`; the earlier run used a placeholder key and
is retained but not counted. The retest uses a real valid temporary Ed25519 key;
its private half is deleted with the fixture and is never persisted in evidence.

The runner covers command parsing, unmerged Git rejection, cached image revision,
manual/automatic workflow gates, key restriction fail-closed behavior, backup
failure/modes/retention, rollback manifest selection, and proxy partial-activation
and malformed-marker failures. Each issue is specified in
`qa/defects/staging-cd-findings.json`. No test expectation was relaxed to close a
product defect. Fixture dumps are synthetic: a real pg_dump/pg_restore is NOT_RUN.

Existing regression commands remain the same. Add the QA-owned
`IndependentMigrationUpgradeTest.java` beside the previous independent SQL test
in the disposable snapshot, then run:

```sh
./mvnw -o -B -Dmaven.repo.local=<qa-cache>/repository \
  -Dtest=IndependentDatabaseAcceptanceTest,IndependentMigrationUpgradeTest test
```

The seven previous SQL assertions are unchanged. Two new tests verify migration
V2 over valid V1 data and atomic refusal over old invalid data. Their random schemas
belong exclusively to the disposable test database and are removed in finally.

The native Compose/CI checker now resolves the timeout of local reusable workflows
and recognizes PR-only cancellation with queued deployments. This follows GitHub's
caller/callee model; it does not waive missing manual CI verification. The same12
assertions still run. Real deployment/timeouts/permissions remain unverified.

HTTP regression's outage observation uses40-second client timeout for dependency
probes because the original5-second observer did not receive a response. Expected
HTTP status remains503, with actual duration recorded (30.24s on PR2). The old result
is retained and no5-second or performance-SLA PASS is claimed. All other HTTP calls
retain5-second timeout. Do not hide this latency risk or count both runs.

Native Caddy supporting verification used official v2.10.2 release assets downloaded
into `/tmp/lawdesk-qa-caddy`. The checksum manifest is SHA512; the GitHub asset API
also supplies SHA256. Verify both before extracting/executing. Use `caddy adapt`
only against the synthetic original/candidate captured by PROXY-UNBALANCED-MARKER.
No server/listener is started; no real Caddyfile or Matbakhy resource is read.
The resulting host lists prove the candidate is syntactically valid while removing
api.qa.invalid. This supports one existing failing case, not two new test counts.

For PR5, rerun the existing64 browser checks plus6 browser security checks against
the newly built exact SHA. Do not transfer old screenshots or PASS records. The
current70 checks pass; WebKit/Safari/manual screen-reader remain NOT_RUN.

See each candidate's `execution-summary.json` for one-run-per-family counts. Keep
developer suite reruns, independent assertions, old-defect closure, full-case
acceptance and BLOCKED/NOT_RUN runtime scopes separate. Never merge, enable CD,
set GitHub deployment variables, change SSH keys/privileges or alter Matbakhy as
part of this review.
