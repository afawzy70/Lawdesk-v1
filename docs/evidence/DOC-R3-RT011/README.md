# DOC-R3-RT011: case page HTTP 500s under concurrent requests (QA-RT-011)

## Reproduction

Against UAT (QA's, not repeated here): `curl -K urls.txt -Z --parallel-max 100 --http2` with the `qa.manager` cookie
gave 58 of 84 responses 500; HTTP/1.1 parallel 11 of 84; sequential all 200.

In the repository (isolated, no UAT load), same Hikari settings as production (`application.yaml`: pool default 10,
`connection-timeout` 3000 ms; nothing raised), Postgres from the usual test support, Tomcat default threads:

```
cd backend
./mvnw test -Dtest='CasePageConcurrencyTest,CasePageConcurrencyHttp2Test'   # HTTP/1.1 and HTTP/2 (h2c)
./mvnw test -Dtest='PoolExhaustionTest,DatabaseUnavailableTest'              # overload -> 503 + Retry-After, recovery
```

`CasePageConcurrencyTest`: seeds one case (proceeding, judgment, hearing, claim, task, note, document, deadline, party),
signs in a `LEGAL_MANAGER` over HTTP, discovers every parameterless `GET /api/v1/cases/{id}/...` handler plus the lookups
(users/directory, org-units, deadline-rules, lists, assistant/outputs, courts, tasks, me) = 45 paths, doubles them (90),
fires them truly at once (latch, one thread per request, fresh connections), 5 rounds = 450 requests. It first makes a
sequential control and then requires each concurrent answer to have the same status and body as the control (complete
data, same permissions), and no 5xx and no broken response. A sampler thread records `HikariPoolMXBean` and, over a
separate non-pooled connection, `pg_stat_activity` states/wait events and ungranted `pg_locks` every 25 ms.

## Measured (see `measurements.txt`)

| | requests | failures (5xx or broken) | Hikari max active / awaiting | pg idle in tx (max) | ungranted locks | max latency |
|---|---|---|---|---|---|---|
| before, HTTP/1.1 | 450 | 215 | 10/10, 89 | 3 | 0 | n/a (timeouts at 3 s) |
| before, HTTP/2 (h2c) | 450 | 176 | 10/10, 64 | 6 | 0 | n/a |
| after, HTTP/1.1 | 450 | 0 | 10/10, 46 | 9 | 0 | 372 ms |
| after, HTTP/2 (h2c) | 450 | 0 | 10/10, 30 | 9 | 0 | 982 ms |

Both body shapes reproduced: ProblemDetail 500s (exception in a controller/service) and Spring Boot's default `/error`
JSON (exception in the security filter). Among the exceptions in the "before" runs, 428 of about 560 stack traces end in
`UserAccounts.one(...)` (the account reload of `ReloadingSecurityContextRepository`, a filter step before MVC), the rest
in service methods that were simply waiting for a connection; every root cause is
`SQLTransientConnectionException: Connection is not available, request timed out after 3000ms (total=10, active=10,
idle=0, waiting=N)`. No lock waits (`pg_locks` ungranted = 0), no blocked backends: not a database lock or slow SQL
problem. Protocol (HTTP/1.1 vs HTTP/2) did not change the cause, only how many requests arrive at once.

## Root cause (proved by the before/after, not assumed)

`UserAccounts.one()` ran `jdbc.query(sql, (rs, row) -> account(rs))`; the row mapper `account(rs)` ran three more queries
(roles, permissions, scopes) through the same `JdbcTemplate` without a transaction. The first query keeps its pooled
connection until the result set is consumed, so each request held one connection while asking the pool for a second.
That runs on every authenticated request (the security context is rebuilt from the database, LD-007.7). With 10
connections and 10 or more simultaneous requests, all 10 connections are held by requests waiting for another one
(hold-while-acquire deadlock); they only get out when Hikari times out after 3 s, which answers 500 for them and for every
request queued behind them. That is why the failing set was random, why sequential and low-concurrency clients saw
nothing, and why the `/error` shape appeared (the failure is thrown in the filter chain, before MVC).

## Fix

- `UserAccounts`: read the account row into a plain record first, then load roles, permissions and scopes; and
  `findById`/`findByLogin` are `@Transactional(readOnly = true)` so the four reads share one connection and one snapshot.
  No hold-while-acquire remains in this path. Pool size and the 3000 ms timeout are unchanged (a single instance, and
  PostgreSQL's default `max_connections` 100 would allow more, but the measurements do not call for it: the burst now
  queues for at most about 1 s with 10 connections).
- Honest overload: `RequestCorrelationFilter` (outermost filter) answers a pool timeout that escapes the filter chain
  as `503` + `Retry-After: 1` + problem JSON, and `ApiExceptionHandler` does the same for MVC; both log at ERROR with the
  cause and the request id. Any other failure that reaches the container is logged at ERROR with the request id before
  the default `/error` 500 is written, so both body shapes now have a logged cause with the id. Tests: `PoolExhaustionTest`
  (all connections held -> `/api/v1/me` is 503 with Retry-After and request id, then 200 once released),
  `DatabaseUnavailableTest` (MVC handler 503 vs 500).

## Limits (not proven until deployed and re-run on UAT)

- The isolated runs use embedded PostgreSQL on a shared host, not UAT's container limits (768 MiB backend, 512 MiB db);
  memory pressure and CPU limits were not reproduced. Max latency of about 1 s for a 90-request burst leaves a margin of
  about 3x to the 3 s timeout here; a slower host has less margin. If UAT still shows pool timeouts, the next step is the
  measured per-request connection hold time (most case reads are `@Transactional` and keep a connection across the
  access policy checks), not a bigger pool by default.
- Caddy/TLS in front was not run (no root, no caddy binary). HTTP/2 was isolated as cleartext h2c to Tomcat, which
  covers multiplexing against the application but not the proxy's behaviour. Proxy and UAT logs could not be read
  (Docker denied); `docs/operations/QA-RT011-LOG-READ.md` has the read-only commands for `devops`.
- The number of requests one page open generates in a real Chromium against a local full stack was NOT measured (the
  frontend dependencies are not installed here and no local full stack with data was started). From the code, the case
  details page mounts about ten panels at once (operations, library, proceedings, pleadings, access, follow-up, wp9,
  assistant, brief, summary), each with its own effect-driven fetches and no shared cache, which is consistent with the
  observed ~80 and means request fan-out remains a separate, unfixed amplifier.
- Reloading and navigating between case pages was not driven separately; the burst test repeats the full set five times
  on fresh connections.
