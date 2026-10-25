# LawDesk v1 performance baseline (LD-040)

**Date:** 2026-10-24. **Analysed:** `develop` at `19ad5386fa586e2469ecad36a677632273712f44`, whose backend is
identical to `1e5f340`, plus the open Phase 5/6 PR heads named in §4.2 as a preview.

**Mandate:** `docs/governance/LAWDESK-DELIVERY-STRATEGY.md` §4:

- baselines, percentiles, throughput, error rate and resources;
- realistic, reproducible scenarios with test data only;
- expensive queries;
- heavy tests only on isolated, resource-bounded infrastructure, never disrupting Matbakhy.

**What this is.** A design for the measurements, the reproducible tooling, and a first expensive-query analysis on a
throwaway local database. **No load, stress, spike or soak test was run anywhere.** The only contact with UAT was a
read-only smoke sample within a hard 60-request budget (§5).

**Delivery state:** IMPLEMENTED on branch `docs/LD-040-security-performance-baseline`. The targets below are
ASSUMPTIONS for owner review.

## 1. Resource budgets on develop (read only)

| Resource | Setting | Source |
|----------|---------|--------|
| Backend container memory | `mem_limit: 768m`. The JVM uses `-XX:MaxRAMPercentage=75`, so the heap is about 576 MiB, with `ExitOnOutOfMemoryError`. | `deploy/compose.yaml:70`, `backend/Dockerfile:16` |
| Database container memory | `mem_limit: 512m`, `postgres:18.6-alpine3.24` with default settings (shared_buffers 128 MB, max_connections 100) | `deploy/compose.yaml:23,37` |
| Web (nginx) memory | `mem_limit: 64m` | `deploy/compose.yaml:92` |
| CPU | **no `cpus` limit on any LawDesk container**, and no `pids_limit` (see SECURITY-BASELINE S-12) | `deploy/compose.yaml` |
| Hikari pool | `maximum-pool-size` not set, so the default of **10**. `connection-timeout: 3000` ms, `validation-timeout: 2000` ms (readiness reports a DB outage within 5 s). | `backend/src/main/resources/application.yaml:10-15` |
| Tomcat | defaults: 200 worker threads, `max-connections` 8192 | Spring Boot defaults |
| Sessions | in-memory `HttpSession`, 30 min idle; **3 DB queries per authenticated request** to reload the user, roles and permissions (`ReloadingSecurityContextRepository`, `UserAccounts.java:100-110`), each under 0.2 ms on the synthetic data (§4.1) | `application.yaml:33-40` |

**Implication.** 200 request threads share 10 connections. Under load, requests queue on Hikari, and a request that
waits more than 3 s fails with a 500. That is the first saturation signal to watch, as `hikaricp_connections_pending`
and the timeout count.

## 2. Metrics and initial targets (ASSUMPTIONS for owner review)

**Measured per endpoint class**, server-side (Micrometer `http.server.requests` once exposed on an isolated target)
and client-side (k6 `http_req_duration{endpoint_class}`):

- p50, p95 and p99 latency;
- throughput (requests per second, and user iterations per second);
- error rate: non-2xx except expected 4xx, plus `http_req_failed`;
- backend CPU and heap (used and after GC), GC pause p99;
- Hikari active, idle and pending connections, and the timeout count;
- PostgreSQL CPU, active sessions and buffer hit ratio, plus the slowest statements (`pg_stat_statements` on the
  isolated target only).

**Targets.** At the "baseline" load in §3 (about 4.5 user iterations per second, about 25 requests per second), on
the volumes in §3, inside the current container limits:

| Endpoint class | Examples | p50 | p95 | p99 |
|----------------|----------|----:|----:|----:|
| auth | `POST /auth/login` (bcrypt 12 costs about 250 ms of CPU by design), `GET /me` | 300 | 800 | 1500 |
| case list/search | `GET /cases`, `?q=` | 150 | 500 | 1000 |
| case details | `GET /cases/{id}`, `/parties`, `/timeline` | 80 | 300 | 600 |
| proceedings | `/cases/{id}/proceedings`, `/judgments`, court list | 80 | 300 | 600 |
| hearings (Phase 5) | calendar, per case | 120 | 400 | 800 |
| documents upload/download (Phase 5) | up to 10 MiB PDF | 500 | 1500 | 3000 |
| reports (Phase 6) | dashboard, case and judgment reports | 400 | 1500 | 3000 |
| exports (Phase 6) | CSV/XLSX/PDF, at most 10 000 rows | 1500 | 5000 | 10000 |

**Other targets:**

- error rate below 1 % (abort at 5 %, §6);
- **backend CPU** below 70 % of one core and heap after GC below 60 % of max;
- **DB connections:** pending at 0 at p95, with no Hikari timeout;
- **database CPU** below 50 % of one core;
- **throughput target:** sustain the "load" profile (about 9 iterations per second, about 50 requests per second)
  with the latency targets met.

The same numbers are in `tools/performance/k6/lib/lawdesk.js` (`TARGETS`) as thresholds.

## 3. Reproducible scenarios and data

### 3.1 Volumes

| Entity | Volume |
|--------|-------:|
| cases | 10 000 |
| parties | 20 000 |
| case–party links | 30 000 |
| proceedings (30 000 first-instance, 15 000 appeals, 5 000 cassations) | 50 000 |
| judgments | about 30 000 |
| special decisions | about 5 000 |
| timeline events | 150 000 |
| audit events | 200 000 |
| users | 50 |
| courts | 60 |
| hearings (Phase 5) | 100 000 |
| tasks (Phase 5) | 30 000 |
| PDFs of 64 KiB, random content (Phase 5) | 1 000 |

### 3.2 User mix

`tools/performance/k6/lawdesk-mix.js`, k6 **v2.3.0**. The tarball `k6-v2.3.0-linux-amd64.tar.gz` has sha256
`39c3117b6af817592dcd0ce4242105c0a7af10948c2a425306f0be8f7a8a8ab1`, checked against `k6-v2.3.0-checksums.txt`. It is an
open model (`ramping-arrival-rate`) with three personas:

| Persona | Account role | One iteration ("visit") | Share at baseline |
|---------|--------------|-------------------------|------------------:|
| lawyer | LEGAL_STAFF | `me`; 40 % a search; then a page of cases and one case's details, parties, timeline, proceedings and judgments. With `FEATURES=hearings,documents`: hearings, calendar, documents, and a download 30 % of the time. Think time 1–3 s. | 67 % |
| staff | LEGAL_STAFF | create a case and a party, link them, list courts, create a proceeding; with features, schedule a hearing, and upload a 200 KiB PDF 20 % of the time | 22 % |
| manager | LEGAL_MANAGER | dashboard; with features, the case and judgment reports, and an export 10 % of the time | 11 % |

**Profiles (`-e PROFILE=`), iterations per second for lawyer/staff/manager:**

| Profile | Rates | Ramp, then hold |
|---------|-------|-----------------|
| smoke | 1/0/0 | 1 min |
| baseline | 3/1/0.5 | 2 min ramp, 10 min hold |
| load | 6/2/1 | 30 min hold |
| stress | 18/6/3 | 10 min ramp, 10 min hold |
| spike | 30/10/5 | 30 s ramp, 2 min hold |
| soak | baseline rates | 4 h |

Thresholds abort a run when the error rate passes 1 % or an endpoint class's p99 passes twice its target. For
stress and spike these are evaluated but do not abort.

**Parameters:**

- `BASE_URL`;
- `CREDENTIALS_FILE`, a JSON `{"accounts": {"lawyer", "staff", "manager", "admin": {username, password}}}` of
  **synthetic** accounts;
- `FEATURES`, which enables the Phase 5/6 endpoints once they are merged;
- `LAWDESK_K6_ISOLATED=yes`, the operator's statement that the target is isolated.

`lib/lawdesk.js` **refuses** `BASE_URL`s that name `167-86-90-174`, `sslip.io` or `matbakhy`. Evidence `05-k6-inspect.log`
shows every profile parsing (`k6 inspect`, with no request sent) and the UAT, Matbakhy and non-isolated refusals.
**The scripts were not executed against any target.**

### 3.3 Seeding plan (public API or isolated database only)

- **Isolated database (preferred for full volumes).** On a throwaway PostgreSQL 18:
  1. apply the Flyway migrations;
  2. run `tools/performance/db/seed-develop.sql` (about 29 s locally);
  3. once the Phase 5 migrations exist, run `seed-phase5.sql` (about 27 s).

  The data is deterministic (`setseed`), every row is marked `SYN`, the e-mail addresses are `@example.invalid`, and
  the password hashes are placeholders, so nobody can sign in with a seeded user. Real sign-in accounts for k6 are
  created through the API.
- **Public API (any isolated instance, for example one started from a release image).** `tools/performance/k6/seed-via-api.js`:
  - the admin account creates 60 SYN courts once;
  - the manager account creates N cases with parties, proceedings, appeals and judgments, plus hearings with
    `FEATURES=hearings`;
  - it runs on at most 4 VUs, because seeding is not a load test.
- **Never** seed UAT or any shared database.

## 4. Expensive-query analysis

**Set-up:**

- the throwaway local **PostgreSQL 18.6** from the zonky `embedded-postgres-binaries-linux-amd64:18.6.0` jar the
  backend tests use (from `~/.m2`);
- `initdb` into a scratch directory, listening on `127.0.0.1:55432`, `shared_buffers=128MB`, `max_connections=10`,
  no parallel workers, `jit=off`;
- driven by `tools/performance/db/QueryPlans.java`, run with `java -cp postgresql-42.7.14.jar …`; it accepts only
  `127.0.0.1` URLs;
- every query was run once to warm the cache and then under `EXPLAIN (ANALYZE, BUFFERS, TIMING)`;
- the instance was stopped and discarded afterwards. No backend test was changed.

**Caveat.** The shared host had a load average of 7–15 on 8 cores during the run. Timings are indicative and vary
between runs (a query measured at 55 ms in one run took 180 ms in the next); the **buffer counts and plan shapes are
the stable signal**. Evidence: `docs/evidence/PERFORMANCE-BASELINE/00`–`03`.

### 4.1 Develop

Volumes are as in §3.1; the database is 220 MB.

| Query (source) | Time | Shared buffers | Plan |
|----------------|-----:|---------------:|------|
| Case list, page 1 (`CaseRepository.search`, `order by created_at desc, id`) | 17 ms | 250 | **Seq Scan of 10 000 rows + top-N sort**: no index on `created_at` |
| Case list, page 500 (offset 9 980) | 26 ms | 250 | Seq Scan + full quicksort (1.7 MB) |
| Case list total (`count(*)`) | 3 ms | 41 | Index-only scan |
| **Case search `q=` (count + page)** (`CaseRepository.java:89-91`) | **59 + 60 ms** | 916 each | Seq Scan of `legal_case` with a correlated `EXISTS` and a **Seq Scan of `party`** (`ilike '%q%'` cannot use `party_name_idx`); runs twice per request |
| Case filter status + opened since + sort title | 5 ms | 250 | Seq Scan |
| Case details, timeline, parties, proceedings, judgments | 0.05–0.15 ms | 3–22 | Index scans: fine |
| **Party search `q=`** (`PartyRepository.java:76-80`) | **52 ms** | 413 | Seq Scan, `ilike` on name, national id and registration |
| Dashboard: cases by status and type | 9 ms | 250 | Seq Scan + HashAggregate (fine at 10 000 rows) |
| Dashboard: cases opened this month | 3 ms | 250 | Seq Scan: no index on `opened_on` |
| **Dashboard: proceedings by stage awaiting judgment** (`ProceedingReportQueries.byStage`) | **71–89 ms** | 1 655 | Hash join of the whole `proceeding` (50 000) × `judgment` (30 000) on every dashboard view |
| Dashboard: judgments of the period | 8 ms | 546 | Seq Scan of `judgment`: no index on `judgment_date` |
| Sign-in lookup and per-request permission reload | 0.1 ms | 2 | Tiny tables: fine |
| **Audit log total** (`AuditController.java:74`) | **52–73 ms** | 159 | Index-only scan of 200 000 rows; grows without bound, because the audit log is append-only |
| Audit log filtered by actor (`lower(actor_username)`) | 1 ms | 17 | Walks `audit_event_occurred`; fine while the actor is frequent, degrades for rare actors (no index on `lower(actor_username)`) |

**N+1 patterns on develop:**

- `ProceedingService.list` (`ProceedingService.java:58`) loads one court per distinct court. That is 1–5 extra
  queries per case page: acceptable.
- `JudgmentService.list` loads all of a case's proceedings and judgments and pages them in memory: fine at about 5
  per case.
- Every authenticated request costs 3 queries for the user reload, by design (LD-007.7).

### 4.2 Open Phase 5/6 PR heads (preview, not on develop)

**Sources:**

- schema V400–V403 from #40 `ded7e3c`;
- queries from #34 `b8aa9b5`, #36 `875d938`, #33 `6e27f5c`, #37 `42fb564` and #40 `ded7e3c`;
- 100 000 hearings (705 of them in the next 30 days), 30 000 tasks, 1 000 documents (65 MB of content).

| Query (source) | Time | Shared buffers | Plan |
|----------------|-----:|---------------:|------|
| **Case inventory: current-stage counts** (#34/#36 `CaseInventory.java:55` → `ProceedingReportQueries.currentStageCounts:111`) | **360–528 ms** | **45 215** | `cases.ids(filter)` loads **every matching case id (10 000) into the JVM**, then sends them back as a uuid[] literal (about 370 KB of SQL). The query is an index scan over all 50 000 proceedings, an incremental sort and `DISTINCT ON`. This happens **on every report page view**. |
| **Case inventory: stage filter** (`casesMatching:77`) | **197–307 ms** | 45 215 | `DISTINCT ON` over all proceedings, materialised in the JVM as a `Set<UUID>` and fed back as uuid[] |
| Case inventory: summary counts, page (sort `opened_on`) | 11 + 17 ms | 250 each | Seq Scans: no index on `opened_on` |
| Case inventory: positions of the 20 page rows | 6 ms | 166 | Fine |
| **Judgment report: summary and page** (`JudgmentReportQueries.java:129`) | **48 ms; 55–180 ms** | 1 657 | Seq Scan of `judgment` (no `judgment_date` index) hash-joined with a **Seq Scan of all 50 000 proceedings**, then a sort |
| Hearing calendar, 30 days: page + count | 0.2 + 1.6 ms | 45 / 623 | `hearing_calendar` index: fine |
| **Hearing calendar row lookups** (#33 `HearingService.java:252-258, 280`) | 0.06 ms each | — | **N+1**: for each distinct case and proceeding on a page, one `CaseLookup.find` and one `ProceedingLookup.find`, plus courts. A 100-row calendar page spread over 100 cases costs up to about 200 extra round trips (about 20–60 ms of latency plus pool pressure). |
| **Hearings awaiting a result** (#33 `HearingReportQueries.java:123`) | **45 ms** | 2 484 | Seq Scan of 100 000 hearings: no index for `status = 'SCHEDULED' and hearing_date < today` |
| Tasks of an assignee (open), overdue count | 0.3 / 0.2 ms | 37 / 97 | Partial index `task_assignee_open`: fine |
| Documents of a case, document download | 0.03 ms | 3 | Fine. EXPLAIN does not detoast the `bytea`; real download cost is the transfer of up to 10 MiB, measured only in a load test. |

### 4.3 Proposed fixes (not implemented)

Measured as WHAT-IF only on the same throwaway database (`whatif-indexes.sql`, `plans-whatif.sql`, evidence `03`).
Each fix belongs to its owning workstream and Flyway range (CONTRACTS §2, §8.3); pg_trgm needs an owner/DBA decision,
because the app role cannot `CREATE EXTENSION`.

| Id | Fix | Before → after (what-if) | Owner |
|----|-----|--------------------------|-------|
| **P-03** | Compute the inventory stage summary **in SQL**: `max(stage rank) group by case_id` joined to the filtered `legal_case`, instead of `ids()` + uuid[] round trips; do the same for `casesMatching` (a join or `EXISTS` instead of a Java set) | 360–528 ms / 45 215 buffers → **68 ms / 1 158 buffers**, and no 10 000-id transfer | E (reports, #34) |
| **P-05** | `create index on judgment (judgment_date)` | judgment report page 55–180 ms → **0.5 ms**; the summary stays at about 48 ms (bounded by the proceeding join; acceptable) | C/E, V5xx |
| **P-02** | Case search: rewrite the `OR … EXISTS` into a `UNION` of three branches and add pg_trgm GIN indexes on `legal_case.title`, `legal_case.reference_no` and `party.name`. Trigram indexes alone do not help the current OR form (still a Seq Scan, 105 ms). Party search needs trigram on all three columns or the same UNION. | case search count 59 ms → **1.5 ms** | B (cases), owner decision on pg_trgm |
| **P-06** | `create index on hearing (hearing_date) where status = 'SCHEDULED'` | 45 ms / 2 484 buffers → **1.3 ms / 13 buffers** | D (hearings, #33) |
| **P-07** | Batch the calendar lookups: `CaseLookup.findAll(ids)` and `ProceedingLookup.findAll(ids)`, one query each per page | up to about 200 queries → 2–3 per page | D (#33); needs a small port addition to B/C |
| **P-01** | `create index on legal_case (created_at desc, id)`, and on `(opened_on)` for the inventory default sort; prefer keyset pagination for deep pages | page 1: 17 ms → **0.1 ms**; deep offsets still walk N rows | B / E |
| **P-04** | Audit log: a capped count ("10 000+") or an estimate, plus keyset pagination; index `lower(actor_username)` | count 52–73 ms → **5 ms** (capped) | A (audit) |
| **P-09** | Dashboard `byStage`: acceptable at 50 000 rows (71–89 ms), but it scales linearly. Consider an `awaiting` partial index or a maintained counter when proceedings pass about 200 000. | — | C / E |
| **P-08** | Memory: an upload is buffered in `/tmp` tmpfs (memory-backed, inside the 768 MiB limit) and then as `byte[]` before the `bytea` insert. Exports of up to 10 000 rows build in `ByteArrayOutputStream` (`Csv.java:26`), and PDF rendering is CPU-heavy. Bound concurrency, for example a semaphore of 2 for exports and uploads, or stream them, and measure heap in the Phase 5/6 load test. | — | D (#40), E (#41) |

**Priority:** P-03, P-05 and P-07 before the reports and hearings PRs merge; then P-02 and P-06; then P-01, P-04
and P-08.

## 5. UAT latency smoke sample (read-only)

This is reported as a **smoke sample, not a load test**: `tools/performance/uat_smoke_sample.py`, evidence
`04-uat-smoke-sample.log`.

**How it ran:**

- the read-only account `qa.viewer`, whose credential file was read in-process by `deploy/qa/qa_accounts.py`'s
  `Session` and never printed;
- sequential, at most 1 request per second, GET only, apart from sign-in and sign-out;
- **60 requests to UAT in total**, the limit: 1 health check, 54 in run 1 and 5 in run 2.

**Results:**

- **Run 1 (54 requests): latency INVALID.** The timer also wrapped the 1-second pacing wait, so every figure is about
  1 s. The statuses are valid: 50 of 50 reads answered 200 (`/me`, `/cases?size=20`, `/courts`, `/cases/{id}`,
  `/cases/{id}/proceedings`). The script was fixed to time only the HTTP exchange.
- **Run 2 (5 requests, corrected):**
  - `GET /api/v1/cases?size=20` answered 200 in **66 ms**;
  - `GET /api/v1/me` answered 200 in **169 ms**;
  - the earlier `GET /actuator/health` answered 200 in 206 ms (curl, including TLS).

Two data points are anecdotes, not percentiles. **UAT percentiles: NOT_RUN.** A valid sample needs a new budget the
owner approves.

## 6. Plan for controlled load tests and the later heavy tests

### 6.1 Matbakhy protection (mandatory)

1. **Never against shared UAT.** Load, stress, spike, soak, fuzzing and DAST never target
   `lawdesk.167-86-90-174.sslip.io` or anything Matbakhy. The k6 library refuses those hosts.
2. **Isolation.**
   - Preferred: a separate VM or host.
   - On the Contabo host, only with explicit owner approval, a separate compose project that:
     - has no `lawdesk-edge` network and no shared proxy;
     - publishes on `127.0.0.1` only;
     - uses its own volumes;
     - has hard caps: backend `cpus: 1.0`, db `cpus: 1.0`, k6 `cpus: 1.0` (about 3 of the 8 cores in total),
       `mem_limit` as on UAT, and `pids_limit`;
     - runs under `nice`/`ionice` for k6.
3. **Synthetic data only** (§3.3), on a throwaway database. No production or UAT dump.
4. **Time.** Run outside Matbakhy's busy hours, announced in advance, with one test at a time and never in parallel
   with CI builds on the host.

### 6.2 Abort criteria

Watched every 10 s. Any one of these stops the run (Ctrl-C k6, then stop the stack):

- host load average above 6, or host free memory below 3 GiB;
- any Matbakhy health check failing, or its p95 latency doubling from its pre-test sample;
- the target's 5xx rate above 5 % for 1 minute, or a Hikari timeout storm (more than 10 per minute) during a load
  (not stress) run;
- the k6 thresholds' `abortOnFail` (error rate above 1 %, or p99 above twice the target);
- disk use above 85 %, or swap activity.

### 6.3 After Phases 5 and 6 (controlled load)

On the isolated stack at the §3.1 volumes, with `FEATURES=hearings,documents,reports,exports`, in this order:

1. smoke;
2. baseline for 12 min;
3. load for 36 min.

Covers cases, proceedings, hearings, attachments (upload and download up to 10 MiB) and reporting/exports. Record
everything in §2's metrics. Re-run `plans-*.sql` against the merged schema and confirm P-03, P-05 and P-07 first.
Exit criterion: the §2 targets are met at "load", or the gaps are listed as defects.

### 6.4 After Phase 7

On separate infrastructure where possible:

- frontend performance: Lighthouse/Web Vitals on the redesigned SPA;
- API regression against this baseline;
- **load** (36 min), **stress** (ramping to 3× load, to find the knee: Hikari saturation, heap, CPU), **spike**
  (5× in 30 s, then recovery time), and **soak** (4 h at baseline: heap growth, connection leaks, audit and table
  growth).

Each run is preceded and followed by the Matbakhy health sample in §6.1, and Phase 8 repeats them as release gates.

## 7. NOT_RUN

| Item | Reason |
|------|--------|
| Load, stress, spike and soak tests (all k6 profiles) | Forbidden against shared UAT. No isolated LawDesk target exists, and this agent has no Docker. The scripts were validated with `k6 inspect` only. |
| UAT latency percentiles | Run 1 timing defect; the remaining budget was 6 requests (§5) |
| Server-side metrics (Micrometer, Hikari, pg_stat_statements) under load | Needs the isolated target in §6 |
| API seeding (`seed-via-api.js`) | Needs an isolated instance; the database seeding path was used locally instead |
| Query plans on the merged Phase 5/6 code | Those PRs are not merged; previewed on their heads (§4.2) |
