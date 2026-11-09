# Phase 8 performance runbook

**Source baseline:** `714d2f3b0c3ed7239b85c79966426d5fe27d2d1d`. This suite was first prepared against `aed1f7d68597cf1da5da9d8aa658d1b6036960bd`, then rebased onto GitHub `develop` at `1771bded862a49b9e3015eb76115a981944954c8`. The operator records the exact SHA deployed to the isolated stack; never infer it from a mutable branch name.

## Safety gate

Run on a verified dedicated worker **or** on the existing `lawdesk-ci` rootless Docker daemon on Contabo after the checks below pass. Do not use host Docker for the performance stack, add a daemon/VM, or touch the public UAT, Matbakhy, shared Caddy, deployment or secrets. Never connect an untrusted CI job to either Docker socket. `lawdesk-ci` already has a root-owned user-slice ceiling of 3 CPUs and 5 GiB, but verify the live systemd and cgroup values before relying on it. Its daemon is separate from the host Docker daemon; therefore it cannot inspect Matbakhy containers and must not claim to monitor their application health.

The Compose project is `lawdesk-perf`. It has one private `internal: true` network, one project-owned PostgreSQL volume, no external network, and only `127.0.0.1:18080` published for the web app. Backend, DB and k6 have no published ports. On the shared host the CPU caps are DB 0.75, backend 0.75, web 0.25 and k6 0.5 cores; memory caps are 512, 768, 96 and 384 MiB. Every service has a PID cap. The k6 library additionally rejects UAT/Matbakhy and targets other than `web:8080` or loopback `:18080`.

For shared Contabo, verify `DOCKER_HOST=unix:///run/user/$(id -u)/docker.sock`, `docker info` reports rootless and cgroup v2/systemd, `systemctl show user-$(id -u).slice` reports `CPUQuotaPerSecUSec=3s`, `MemoryMax=5368709120` or a tighter value, the runner has no active CI job, and the host has at least 8 GiB available RAM, load below 5.5 and disk use below 70%. Check the rootless container/image inventory and free disk before building. Do not use a remote Docker context. If any check cannot be proved, **do not launch**. The agent's current sandbox cannot open either Docker socket; this is an operator-only step until access is provided.

After checking the read-only inventory and checking out the reviewed PR commit as `lawdesk-ci`, the smallest direct operator command for the first real baseline is `bash tools/performance/isolated/operator_baseline.sh`. The script refuses any other user or CI job, verifies rootless/cgroup limits and clean exact app source, reuses exact-SHA rootless images or builds them in the existing daemon, provisions only synthetic credentials, runs 20-case seed and authenticated API baseline under the guard, and leaves private evidence in `/tmp/lawdesk-perf-evidence-<UTC>-<PID>`. If a gate fails, it stops only `lawdesk-perf`. Do not run it from GitHub Actions or as `devops` against host Docker.

## Prepare a fresh synthetic stack

Run from a clean commit of this PR branch as `lawdesk-ci` on the verified rootless daemon, or from a verified dedicated worker. Use a fresh secrets/evidence directory for every dataset. Keep secrets outside the repository and never use a UAT dump or UAT credentials. `LAWDESK_PERF_SHA` identifies the **application** source commit; `LAWDESK_PERF_SUITE_SHA` identifies the committed performance suite. Before a rootless build, compare the backend/frontend trees with the application commit; refuse any difference. CI rootless images are normally cleaned after jobs, so do not assume the host daemon's `lawdesk-local/*` images exist in rootless Docker.

```bash
set -euo pipefail
export LAWDESK_PERF_HOST_MODE=shared-verified
export LAWDESK_PERF_SHA=1771bded862a49b9e3015eb76115a981944954c8
export LAWDESK_PERF_SUITE_SHA="$(git rev-parse HEAD)"
export LAWDESK_PERF_UID="$(id -u)"
export LAWDESK_PERF_SECRETS_DIR=/tmp/lawdesk-perf-secrets-1
export LAWDESK_PERF_EVIDENCE_DIR=/tmp/lawdesk-perf-evidence-1
export LAWDESK_PERF_BACKEND_IMAGE="lawdesk-local/backend:$LAWDESK_PERF_SHA"
export LAWDESK_PERF_WEB_IMAGE="lawdesk-local/web:$LAWDESK_PERF_SHA"
export LAWDESK_PERF_RATE_SCALE=0.20
export DOCKER_HOST="unix:///run/user/$(id -u)/docker.sock"
perf_compose() { docker compose -f tools/performance/isolated/compose.yaml -f tools/performance/isolated/compose.shared.yaml "$@"; }
git diff --quiet "$LAWDESK_PERF_SHA" HEAD -- backend frontend
python3 tools/performance/isolated/accounts.py prepare "$LAWDESK_PERF_SECRETS_DIR"
install -d -m 0700 "$LAWDESK_PERF_EVIDENCE_DIR"
docker info --format '{{json .SecurityOptions}} {{.CgroupVersion}} {{.CgroupDriver}}'
perf_compose --profile load config > "$LAWDESK_PERF_EVIDENCE_DIR/compose-rendered.yaml"
perf_compose build backend web
perf_compose up --no-build -d db backend web
python3 tools/performance/isolated/accounts.py provision "$LAWDESK_PERF_SECRETS_DIR" http://127.0.0.1:18080
perf_compose exec -T db psql -U postgres -d lawdesk -v ON_ERROR_STOP=1 -c 'create extension if not exists pg_stat_statements'
python3 tools/performance/isolated/guarded_run.py seed
perf_compose exec -T db psql -U postgres -d lawdesk -v ON_ERROR_STOP=1 -c 'select pg_stat_statements_reset()'
python3 tools/performance/isolated/guarded_run.py api
perf_compose exec -T db psql -U postgres -d lawdesk -v ON_ERROR_STOP=1 < tools/performance/db/observe-phase8.sql > "$LAWDESK_PERF_EVIDENCE_DIR/api-db-after.txt"
```

The provisioner changes the forced bootstrap password, creates three distinct `perf.*` users through the API, verifies sign-in, and writes 0600 credentials, including the isolated admin account needed for seeding, in the private directory. `seed` writes only 20 synthetic cases with one VU; `api` is a low-rate, 15-iteration authenticated baseline. Record their summaries before expanding the dataset. Stop if either fails. The generated PDF fixture is a valid one-page synthetic PDF. Docker builds may need the managed proxy and CA passed to Maven/npm with TLS verification retained; follow the worker's runtime instructions. Do not disable TLS verification.

The full-size dataset needs a **fresh** private DB. After saving the API baseline evidence, run `perf_compose down -v` and use fresh secret/evidence paths. Repeat `accounts.py prepare`, `perf_compose up --no-build -d db backend web`, and `accounts.py provision` before running the SQL below. This removes only the `lawdesk-perf` project volume, never staging or Matbakhy. Keep the first baseline as a separate small-dataset result.

Create `pg_stat_statements` on this private database, then seed only this private database. The scripts are deterministic. `seed-phase5.sql` has a Phase 8 adjustment so its 100,000 hearing dates satisfy V406; `seed-phase8.sql` adds experts/referrals satisfying V407. The SQL seed's stored document bytes mimic PDF size and magic for download throughput; only the API upload fixture is a fully valid PDF.

```bash
perf_compose exec -T db psql -U postgres -d lawdesk -v ON_ERROR_STOP=1 -c 'create extension if not exists pg_stat_statements'
perf_compose exec -T -e PGOPTIONS='-c search_path=lawdesk,public' db psql -U postgres -d lawdesk -v ON_ERROR_STOP=1 < tools/performance/db/seed-develop.sql
perf_compose exec -T -e PGOPTIONS='-c search_path=lawdesk,public' db psql -U postgres -d lawdesk -v ON_ERROR_STOP=1 < tools/performance/db/seed-phase5.sql
perf_compose exec -T -e PGOPTIONS='-c search_path=lawdesk,public' db psql -U postgres -d lawdesk -v ON_ERROR_STOP=1 < tools/performance/db/seed-phase8.sql
perf_compose exec -T -e PGOPTIONS='-c search_path=lawdesk,public' db psql -U postgres -d lawdesk -Atc 'select count(*) from legal_case; select count(*) from proceeding; select count(*) from hearing; select count(*) from task; select count(*) from case_document; select count(*) from expert; select count(*) from expert_referral;' > "$LAWDESK_PERF_EVIDENCE_DIR/dataset-counts.txt"
```

Check that counts match the planned scale (10,000 cases, about 50,000 proceedings, up to 100,000 hearings, 30,000 tasks, 1,000 stored document payloads, 60 experts, 5,000 referrals), that all three personas sign in, and that authenticated `GET /api/v1/release` returns the SHA. Record image digests, Docker/Compose versions, host CPU/RAM, PostgreSQL version and dataset counts. If any check fails, stop; do not treat a partial dataset as a comparable baseline.

Take a private snapshot after provisioning and seeding. The scenarios create cases, tasks, hearings and documents, so **restore this same snapshot before each profile** to keep comparisons reproducible. The sole exception is `recovery`: run it immediately after `spike` on the same database to measure recovery from that burst. Stop backend/web before each restore, require `pg_restore` exit code 0, restart and verify `/healthz`, dataset counts and the authenticated release SHA before running k6. Keep the dump owner-only with the evidence; it contains synthetic account hashes.

```bash
umask 077
perf_compose exec -T db pg_dump -U postgres -d lawdesk -Fc > "$LAWDESK_PERF_EVIDENCE_DIR/seed.dump"
# Repeat before matrix, smoke, baseline, load, stress, spike and soak; skip before recovery.
perf_compose stop web backend
perf_compose exec -T db pg_restore -U postgres -d lawdesk --clean --if-exists < "$LAWDESK_PERF_EVIDENCE_DIR/seed.dump"
perf_compose exec -T db psql -U postgres -d lawdesk -v ON_ERROR_STOP=1 -c 'select pg_stat_statements_reset()'
perf_compose up --no-build -d backend web
```

## Run profiles in order

The existing `lawdesk-mix.js` uses distinct authenticated staff and manager personas. It covers session validation; cases, search/filter, parties, timeline, proceedings and judgments; hearing lists and scheduling; task lists, reminders, creation and completion; document metadata, valid PDF upload and authorized download; dashboard; five report classes; and all five reports' CSV/XLSX/PDF exports. `export-matrix.js` requests each of the 15 export combinations exactly once and records response bytes. k6 keeps per-VU cookies and CSRF state. Each load run has a ramp and a 30-second down-ramp. Rates below are **initial assumptions for calibration**, not observed production traffic.

| Profile | Planned visit rate on an isolated dedicated worker | Shared host with `RATE_SCALE=0.20` | Duration | Aim |
|---|---:|---:|---:|---|
| matrix | 1 VU, 15 exports | same | ~15 s | export format/size coverage |
| smoke | 1/s, read persona only | 0.2/s | ~1 min | contract and credential gate |
| baseline | 4.5/s | 0.9/s | ~12 min | steady baseline after warm-up |
| load | 9/s | 1.8/s | ~36 min | candidate normal load |
| stress | up to 27/s | 5.4/s | ~20 min | observe first degradation; stop there |
| spike | up to 45/s | 9/s | ~3 min | burst and recovery |
| recovery | 4.5/s | 0.9/s | ~6 min | confirm p95/errors and resource use return to baseline |
| soak | 4.5/s | 0.9/s | ~4 h | memory, connection and data growth |

Run one profile at a time. Keep the same evidence directory for `spike` and immediately following `recovery`; use a fresh directory or move prior outputs before rerunning the same profile. The guard checks rendered network/port isolation, actual rootless cgroups and exact SHA. It samples host load/free RAM/disk/web health, rootless neighbor state, Docker CPU/RAM and DB connection states every 10 seconds. On the shared host it stops k6 above load 6.5, below 6 GiB available RAM, above 75% disk use, on failed isolated web health, rootless neighbor restart/unhealthiness, missing k6 caps or HTTP 5xx above 5% for one minute. It does **not** inspect or send traffic to Matbakhy; the root-owned 3 CPU/5 GiB user-slice ceiling bounds impact on host services. k6 thresholds stop ordinary load when errors exceed 1% or tail latency doubles; stress/spike measure the threshold instead. Watch backend logs for Hikari timeouts; more than 10/minute on a normal load run is a stop condition. Check recovery only after health, error rate and p95 return to pre-spike levels. If the bounded stress profile never degrades, report the threshold as *not observed*; do not raise the cap merely to force degradation.

If k6 reaches its own CPU/memory cap or records `dropped_iterations`, treat the offered-rate limit as a generator bottleneck; do not claim the application reached a throughput ceiling. Inspect the k6 row in each `*-guard.jsonl` alongside backend and DB rows.

```bash
python3 tools/performance/isolated/guarded_run.py matrix
python3 tools/performance/isolated/guarded_run.py smoke
python3 tools/performance/isolated/guarded_run.py baseline
python3 tools/performance/isolated/guarded_run.py load
python3 tools/performance/isolated/guarded_run.py stress
python3 tools/performance/isolated/guarded_run.py spike
python3 tools/performance/isolated/guarded_run.py recovery
python3 tools/performance/isolated/guarded_run.py soak
```

Record `*-summary.json`, `*-metrics.jsonl`, `*-guard.jsonl`, and exact SHA for each run. The guard file has CPU/RAM and connection state snapshots. The matrix summary has exact export body bytes per report/format. Use `pg_stat_statements` (total and mean time, calls, rows, shared buffer hit/read) plus `EXPLAIN (ANALYZE, BUFFERS)` for the slowest **synthetic** queries. Capture slow-query logs from the private DB container (250 ms threshold). Do not copy SQL bind values or document content into a public PR. The application currently exposes health/info only, so Hikari active/idle/pending and JVM heap/GC need dedicated isolated instrumentation before claiming those measurements; PostgreSQL connections and Docker memory are proxies, not substitutes.

```bash
perf_compose exec -T db psql -U postgres -d lawdesk -v ON_ERROR_STOP=1 < tools/performance/db/observe-phase8.sql > "$LAWDESK_PERF_EVIDENCE_DIR/db-after.txt"
perf_compose logs --no-color db > "$LAWDESK_PERF_EVIDENCE_DIR/db-private.log"
```

For the frontend, run the bundled Playwright sampler on the same host as the isolated loopback site (or through an authenticated local SSH tunnel that forwards only port 18080). It signs in with the synthetic manager account, refuses any target other than `127.0.0.1:18080`, and captures cold/repeat navigation for dashboard, case list/details, hearings, tasks and reports at desktop/mobile widths. Record browser version, SHA and the absence of throttling. It measures lab TTFB/FCP/LCP/CLS and a synthetic click duration if an eligible link is visible. Synthetic click duration is **not** field INP; use a browser trace with representative interactions before claiming INP. The anonymous UAT login-shell sample in `PHASE8-UAT-BROWSER-SAMPLE.json` is not an authenticated frontend baseline.

```bash
export LAWDESK_PERF_PLAYWRIGHT_RUNTIME=/path/to/playwright-runtime
export LAWDESK_PERF_CREDENTIALS_FILE="$LAWDESK_PERF_SECRETS_DIR/perf_accounts.json"
node tools/performance/isolated_browser.mjs > "$LAWDESK_PERF_EVIDENCE_DIR/browser.json"
```

## Initial acceptance thresholds and handoff

The thresholds in `k6/lib/lawdesk.js` are assumptions pending traffic calibration: p95/p99 in milliseconds: auth 800/1500, case list 500/1000, case details/proceedings 300/600, hearings/tasks 400/800, documents 1500/3000, reports 1500/3000, exports 5000/10000; error rate below 1%. Candidate resource limits are backend CPU below 70% of one core, DB CPU below 50%, heap after GC below 60% of its max, no Hikari pending queue at p95, no timeouts. Sustained RPS is a measured output, not a claimed production target. `data_received` records transfer throughput and `response_body_bytes` records decoded body sizes per route; inspect both. Inspect per-route results as well as class aggregates so slow PDF exports cannot hide behind fast CSV exports. Keep functional/security gates unchanged.

After the isolated runs, report the first bottleneck with its profile, exact SHA, dataset, p50/p95/p99, achieved RPS, errors, response bytes, CPU/RAM, DB connections and query plan. Send only **confirmed** bottlenecks with this evidence to Claude for remediation, then independently rerun the same profile/data/SHA comparison. Do not mark Phase 8 PASS until load, stress, spike, recovery, soak, DB and frontend measurements are actually complete.
