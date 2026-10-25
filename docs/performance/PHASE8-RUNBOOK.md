# Phase 8 performance runbook

**Source baseline:** `714d2f3b0c3ed7239b85c79966426d5fe27d2d1d`. This suite was first prepared against `aed1f7d68597cf1da5da9d8aa658d1b6036960bd`, then rebased onto GitHub `develop` at `1771bded862a49b9e3015eb76115a981944954c8`. The operator records the exact SHA deployed to the isolated stack; never infer it from a mutable branch name.

## Safety gate

Run this only on a **verified dedicated worker** with a working local Docker daemon. The public UAT, Contabo shared host, Matbakhy host, their databases and shared Caddy are forbidden targets. No new VM or paid service is authorized by this runbook. If only the shared host is available, stop at `docker compose config` and retain the scripts for later. `LAWDESK_PERF_DEDICATED_HOST=verified` is an operator attestation after checking host identity; it does not itself prove isolation.

The Compose project is `lawdesk-perf`. It has one private `internal: true` network, one project-owned PostgreSQL volume, no external network, and only `127.0.0.1:18080` published for the web app. Backend, DB and k6 have no published ports. CPU limits: DB 1, backend 1, web 0.5, k6 1 core. Memory limits: 512, 768, 96 and 512 MiB respectively. Every service has a PID cap. The k6 library additionally rejects UAT/Matbakhy and targets other than `web:8080` or loopback `:18080`.

Before launch, verify the Docker context points to this worker's local socket, inspect the rendered Compose file, and confirm no LawDesk or Matbakhy service is present on the worker. Do not use a remote Docker context. Check sufficient host headroom (at least 3 GiB available RAM, load below 6 and disk below 85%) and that no CI build is running. If Docker cannot be inspected, **do not launch**. This repository's current execution environment has no access to its Docker socket, so no isolated run was attempted here.

## Prepare a fresh synthetic stack

Run from a clean commit of this branch on the verified worker. Use a fresh secrets/evidence directory for every dataset. Keep secrets outside the repository and never use a UAT dump or UAT credentials.

```bash
export LAWDESK_PERF_SHA="$(git rev-parse HEAD)"
export LAWDESK_PERF_UID="$(id -u)"
export LAWDESK_PERF_SECRETS_DIR=/tmp/lawdesk-perf-secrets-1
export LAWDESK_PERF_EVIDENCE_DIR=/tmp/lawdesk-perf-evidence-1
python3 tools/performance/isolated/accounts.py prepare "$LAWDESK_PERF_SECRETS_DIR"
install -d -m 0700 "$LAWDESK_PERF_EVIDENCE_DIR"
docker --host=unix:///var/run/docker.sock info
docker compose -f tools/performance/isolated/compose.yaml --profile load config > "$LAWDESK_PERF_EVIDENCE_DIR/compose-rendered.yaml"
docker compose -f tools/performance/isolated/compose.yaml up --build -d db backend web
python3 tools/performance/isolated/accounts.py provision "$LAWDESK_PERF_SECRETS_DIR" http://127.0.0.1:18080
```

The provisioner changes the forced bootstrap password, creates three distinct `perf.*` users through the API, verifies sign-in, and writes 0600 credentials in the private directory. Stop if it fails. The generated PDF fixture is a valid one-page synthetic PDF. Docker builds may need the managed proxy and CA passed to Maven/npm with TLS verification retained; follow the worker's runtime instructions. Do not disable TLS verification.

Create `pg_stat_statements` on this private database, then seed only this private database. The scripts are deterministic. `seed-phase5.sql` has a Phase 8 adjustment so its 100,000 hearing dates satisfy V406; `seed-phase8.sql` adds experts/referrals satisfying V407. The SQL seed's stored document bytes mimic PDF size and magic for download throughput; only the API upload fixture is a fully valid PDF.

```bash
docker compose -f tools/performance/isolated/compose.yaml exec -T db psql -U postgres -d lawdesk -v ON_ERROR_STOP=1 -c 'create extension if not exists pg_stat_statements'
docker compose -f tools/performance/isolated/compose.yaml exec -T db psql -U postgres -d lawdesk -v ON_ERROR_STOP=1 < tools/performance/db/seed-develop.sql
docker compose -f tools/performance/isolated/compose.yaml exec -T db psql -U postgres -d lawdesk -v ON_ERROR_STOP=1 < tools/performance/db/seed-phase5.sql
docker compose -f tools/performance/isolated/compose.yaml exec -T db psql -U postgres -d lawdesk -v ON_ERROR_STOP=1 < tools/performance/db/seed-phase8.sql
docker compose -f tools/performance/isolated/compose.yaml exec -T db psql -U postgres -d lawdesk -Atc 'select count(*) from legal_case; select count(*) from proceeding; select count(*) from hearing; select count(*) from task; select count(*) from case_document; select count(*) from expert; select count(*) from expert_referral;' > "$LAWDESK_PERF_EVIDENCE_DIR/dataset-counts.txt"
```

Check that counts match the planned scale (10,000 cases, about 50,000 proceedings, up to 100,000 hearings, 30,000 tasks, 1,000 stored document payloads, 60 experts, 5,000 referrals), that all three personas sign in, and that authenticated `GET /api/v1/release` returns the SHA. Record image digests, Docker/Compose versions, host CPU/RAM, PostgreSQL version and dataset counts. If any check fails, stop; do not treat a partial dataset as a comparable baseline.

## Run profiles in order

The existing `lawdesk-mix.js` uses distinct authenticated staff and manager personas. It covers session validation; cases, search/filter, parties, timeline, proceedings and judgments; hearing lists and scheduling; task lists, reminders, creation and completion; document metadata, valid PDF upload and authorized download; dashboard; five report classes; and all five reports' CSV/XLSX/PDF exports. `export-matrix.js` requests each of the 15 export combinations exactly once and records response bytes. k6 keeps per-VU cookies and CSRF state. Each load run has a ramp and a 30-second down-ramp. Rates below are **initial assumptions for calibration**, not observed production traffic.

| Profile | Offered visit rate at plateau | Duration | Aim |
|---|---:|---:|---|
| matrix | 1 VU, 15 exports | ~15 s | export format/size coverage |
| smoke | 1/s, read persona only | ~1 min | contract and credential gate |
| baseline | 4.5/s | ~12 min | steady baseline after warm-up |
| load | 9/s | ~36 min | candidate normal load |
| stress | up to 27/s | ~20 min | observe first degradation; stop there |
| spike | up to 45/s | ~3 min | burst and recovery |
| recovery | 4.5/s | ~6 min | confirm p95/errors and resource use return to baseline |
| soak | 4.5/s | ~4 h | memory, connection and data growth |

Run one profile at a time. Set a **new evidence directory per profile** or move its outputs before rerunning. The guard checks rendered network/port isolation and exact SHA, samples host load/free RAM/disk/web health plus Docker CPU/RAM and DB connection states every 10 seconds, and stops k6 on a host limit, failed health or 5xx above 5% over one minute. k6 thresholds stop ordinary load when errors exceed 1% or tail latency doubles; stress/spike measure the threshold instead. Watch backend logs for Hikari timeouts; more than 10/minute on a normal load run is a stop condition. On a shared host, Matbakhy health would also be a stop condition, but this runbook forbids launching there because its isolation cannot be proven. Check recovery only after health, error rate and p95 return to pre-spike levels.

```bash
export LAWDESK_PERF_DEDICATED_HOST=verified
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
docker compose -f tools/performance/isolated/compose.yaml exec -T db psql -U postgres -d lawdesk -v ON_ERROR_STOP=1 < tools/performance/db/observe-phase8.sql > "$LAWDESK_PERF_EVIDENCE_DIR/db-after.txt"
docker compose -f tools/performance/isolated/compose.yaml logs --no-color db > "$LAWDESK_PERF_EVIDENCE_DIR/db-private.log"
```

For the frontend, use a browser on the isolated loopback site with the synthetic account. Capture cold and warm navigation for dashboard, case list/details, hearings, tasks and reports at desktop/mobile widths. Use Lighthouse and `web-vitals` or Chrome trace for LCP, CLS, INP and TTFB, with network/CPU throttling settings, browser version and SHA recorded. The anonymous UAT transfer sample in `PHASE8-UAT-PUBLIC-SAMPLE.json` is **not** a Core Web Vitals measurement.

## Initial acceptance thresholds and handoff

The thresholds in `k6/lib/lawdesk.js` are assumptions pending traffic calibration: p95/p99 in milliseconds: auth 800/1500, case list 500/1000, case details/proceedings 300/600, hearings/tasks 400/800, documents 1500/3000, reports 1500/3000, exports 5000/10000; error rate below 1%. Candidate resource limits are backend CPU below 70% of one core, DB CPU below 50%, heap after GC below 60% of its max, no Hikari pending queue at p95, no timeouts. Sustained RPS is a measured output, not a claimed production target. `data_received` records transfer throughput and `response_body_bytes` records decoded body sizes per route; inspect both. Inspect per-route results as well as class aggregates so slow PDF exports cannot hide behind fast CSV exports. Keep functional/security gates unchanged.

After the isolated runs, report the first bottleneck with its profile, exact SHA, dataset, p50/p95/p99, achieved RPS, errors, response bytes, CPU/RAM, DB connections and query plan. Send only **confirmed** bottlenecks with this evidence to Claude for remediation, then independently rerun the same profile/data/SHA comparison. Do not mark Phase 8 PASS until load, stress, spike, recovery, soak, DB and frontend measurements are actually complete.
