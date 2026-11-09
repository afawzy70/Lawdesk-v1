# Repeat current-head verification

This extends the previous execution guides; historical reports remain immutable.
Use canonical git-blob snapshots and a new exact-SHA evidence directory. Do not
copy PASS from another candidate or run any script on a live host.

Current changes to QA tools follow the new implementation contracts:

- `phase1_review.py` measures the FIRST outage probe immediately, no warm-up/retry.
  Three503 responses must each take<5s (5.1s client transport budget), plus30
  healthy200 probes, liveness200 and readiness recovery. Historical40s observation
  remains in its old SHA directory; it is not the current SLA test.
- `cd_review.py` uses Docker's formatted revision string, seven expected GitHub CI
  gate names, REF=refs/heads/develop, and real Caddy adapt behind a fake Docker
  transport. All SSH/Docker/curl/gh operations remain local doubles.
- The same16 CD regression oracles remain; three positive controls were added for
  all-green manual CI, correct cached image identity and normal proxy attachment.
  These prevent false closure by a fixture rejecting everything. The final suite
  is19 cases. The diagnostic old exact-six-job assertion is retained, not counted.
- `develop_guard_review.py` executes10 synthetic API/comment/history fixtures
  against the exact new guard module. It never posts a real QA verdict or performs
  a GitHub mutation. This verifies procedural detection, not server protection.

Existing commands:

```sh
python3 qa/independent/run_phase1_postgres.py --candidate <snapshot> --sha <sha> \
  --java <jdk21>/bin/java --jar <snapshot>/backend/target/lawdesk-backend-0.0.1-SNAPSHOT.jar \
  --output <new-evidence>/http --pg-bin <postgres18>/bin
LAWDESK_QA_CADDY=/tmp/lawdesk-qa-caddy/caddy \
  python3 qa/independent/cd_review.py --candidate <pr7-snapshot> --sha <pr7-sha> --output <new-evidence>/cd-complete
python3 qa/independent/develop_guard_review.py --candidate <pr7-snapshot> --sha <pr7-sha> --output <new-evidence>/guard
```

The official Caddy2.10.2 binary used by the fake transport is checksum-verified
as documented previously. It only adapts synthetic files; no daemon/listener
starts and no actual Matbakhy Caddyfile is read or changed.

Run the existing nine independent Java SQL/upgrade tests, React component tests
for the foundation shell, native Compose/CI audit, deployment-edge tests and
Vite probes. For the bilingual preview use its existing64+6 browser tests instead
of rendering context-dependent preview components with the foundation adapter.
After synchronizing PR5, its actual jar and backend SQL/HTTP are now executed too.

Preserve the developer Maven/frontend/tool/deploy logs separately. One full PR7
frontend run included the11 QA overlay tests:61 total=50 developer+11 QA. The
standalone QA rerun counts those11 only once. Raw evidence is retained.

Do not enable staging, call SSH, bootstrap, attach/detach, rollback-drill, create
secrets or alter Docker privileges. Record real-container, actual SSH/restore/
rollback and manual cross-browser scopes as BLOCKED/NOT_RUN until independently
executed in an authorized isolated environment.
