# QA-RT-011: reading the UAT logs and database state (read-only, for host user `devops`)

The agent user `aiagent` cannot use Docker on the host, so the UAT evidence for QA-RT-011 (HTTP 500 on case-page
requests) has to be collected by `devops`. Every command below only reads. Nothing here changes a file, a permission,
a container, a setting or a secret, and no command prints a secret.

## What is verified from the repository, and what is not

Verified in `deploy/compose.yaml` and `deploy/scripts/deploy.sh`:

- Compose project name `lawdesk-staging`; services `db` (postgres 18, database `lawdesk`, admin role `postgres`),
  `backend` (Spring Boot, app role `lawdesk_app`, `mem_limit: 768m`, `-XX:MaxRAMPercentage=75` in `backend/Dockerfile`)
  and `web` (nginx serving the SPA, `mem_limit: 64m`). Logging driver `json-file`, 10 MB x 5 files per container, so
  an old window may already be rotated away: read soon after a reproduction.
- The database is reached only inside the `internal` network. `deploy.sh` runs the DB admin path as
  `docker compose -p lawdesk-staging ... exec -T db psql -U postgres -d postgres`; the same path is used below.
- The app has one instance, so the pool of 10 connections (HikariCP default; `connection-timeout` 3000 ms in
  `backend/src/main/resources/application.yaml`) is the only client of `lawdesk_app`. `deploy/` sets no
  `max_connections`, so PostgreSQL's default of 100 applies (check it below).

Not verifiable without Docker access (state this in the report if the values differ):

- The names of the running containers (use the label filters below, not guessed names).
- The reverse proxy in front of `web`: it is the host's Caddy (shared with another project, see
  `deploy/proxy/*.sh` and `docs/operations/STAGING-RUNBOOK.md`); its container name and log format are not in this
  repository.
- Actual `pg_settings` values and the log lines of 2026-11-10 05:12-05:17 UTC.

## 1. Find the containers (labels, no guessing)

```bash
for s in backend db web; do
  printf '%s: ' "$s"
  docker ps -a --filter label=com.docker.compose.project=lawdesk-staging \
    --filter label=com.docker.compose.service=$s --format '{{.Names}} {{.Status}} {{.Image}}'
done
# The Caddy proxy that joined the lawdesk-edge network:
docker network inspect lawdesk-edge --format '{{range .Containers}}{{.Name}} {{end}}'
```

Set the names once (replace the placeholders with the output above):

```bash
BACKEND=<backend container name>; DB=<db container name>; PROXY=<caddy container name>
```

## 2. Backend log for a UTC window

Logs are ECS JSON, one object per line. `--since/--until` accept RFC 3339 UTC timestamps.

```bash
docker logs --since 2026-11-10T05:12:00Z --until 2026-11-10T05:17:00Z "$BACKEND" 2>&1 > /tmp/backend-window.log
wc -l /tmp/backend-window.log

# All ERROR lines: logger, message and the first exception line, newest last
jq -r 'select(.log.level=="ERROR") | [."@timestamp", .log.logger, .message, (.error.message // "")[0:160]] | @tsv' \
  /tmp/backend-window.log | head -200

# Count by exception class and by message (is it "Connection is not available, request timed out"?)
jq -r 'select(.log.level=="ERROR") | (.error.type // "-") + " | " + ((.error.message // .message) | .[0:100])' \
  /tmp/backend-window.log | sort | uniq -c | sort -rn | head -20

# Hikari lines that report the pool state: total / active / idle / waiting
grep -o 'HikariPool-1[^"]*' /tmp/backend-window.log | sort | uniq -c | sort -rn | head -20

# One request by id (the x-request-id of a failed response seen in the browser or in curl -D)
RID=<x-request-id value>
jq -c --arg r "$RID" 'select(.requestId==$r or (.mdc.requestId? // "")==$r)
   | {t:."@timestamp", level:.log.level, logger:.log.logger, msg:.message, status:."http.response.status_code",
      ms:."event.duration_ms", err:(.error.message // null)}' /tmp/backend-window.log
```

The field that holds the request id depends on the ECS formatter (`requestId` or under `mdc`); if the `jq` filter
returns nothing, run `grep "$RID" /tmp/backend-window.log | head`. The completed-request line
(`request completed`) carries `url.path`, `http.response.status_code` and `event.duration_ms`.

Requests per second and status mix in the window (is the burst real, and how many 500 / 503?):

```bash
jq -r 'select(.message=="request completed") | (."@timestamp"[0:19]) + " " + (."http.response.status_code"|tostring)' \
  /tmp/backend-window.log | sort | uniq -c | sort -k2,2 -k3,3 | head -100
jq -r 'select(.message=="request completed" and ."http.response.status_code">=500) | ."url.path"' \
  /tmp/backend-window.log | sed -E 's#[0-9a-f]{8}-[0-9a-f-]{27}#{id}#g' | sort | uniq -c | sort -rn | head -30
```

Releases from this fix log, at ERROR and with the request id, either `database connection unavailable; answering 503`
(pool timeout, answered 503 + `Retry-After: 1`), `request failed outside MVC; the container will answer 500 through
/error` (the default Spring Boot error JSON shape) or `unexpected error` (the ProblemDetail shape). Older releases only
log the Tomcat `Servlet.service() ... threw exception` line without the request id.

## 3. Proxy (Caddy) log for the same window

Caddy writes one JSON line per request when access logging is on (not verifiable from this repository):

```bash
docker logs --since 2026-11-10T05:12:00Z --until 2026-11-10T05:17:00Z "$PROXY" 2>&1 > /tmp/proxy-window.log
# Status mix and protocol (HTTP/1.1 vs HTTP/2 vs HTTP/3) of lawdesk requests:
jq -r 'select(.request.uri? and (.request.host|tostring|test("lawdesk"))) | [.status, .request.proto] | @tsv' \
  /tmp/proxy-window.log | sort | uniq -c
# Upstream errors (502/504, dial or timeout) as opposed to 500 coming from the app:
grep -Ei 'dial tcp|upstream|timeout|502|504' /tmp/proxy-window.log | head -20
# A 500 that the proxy relays carries the app's x-request-id in the response headers:
grep "$RID" /tmp/proxy-window.log | head -3
```

If the proxy log has no `lawdesk` hosts, replace the `test("lawdesk")` filter with the real host name from
`docs/operations/STAGING-RUNBOOK.md`.

## 4. Database state (run during a reproduction, and `pg_stat_database` after it)

Use the existing admin path (the same one `deploy.sh` uses); `-c` statements below are all `select`/`show`:

```bash
P="docker exec -i $DB psql -U postgres -d lawdesk -X -A -F' | ' -c"
$P "show max_connections"
$P "select name, setting from pg_settings where name in ('max_connections','idle_in_transaction_session_timeout','statement_timeout','lock_timeout','shared_buffers','work_mem','log_min_duration_statement')"

# Connections by state and wait event (repeat every second during a reproduction: for i in $(seq 30); do ...; sleep 1; done)
$P "select usename, state, coalesce(wait_event_type||':'||wait_event,'-') wait, count(*), max(now()-xact_start) longest_xact, max(now()-state_change) longest_in_state
    from pg_stat_activity where datname='lawdesk' and backend_type='client backend' group by 1,2,3 order by 4 desc"

# Oldest transactions / idle in transaction (a connection held while the app waits for something else)
$P "select pid, usename, state, now()-xact_start as xact_age, left(query,120) as last_query
    from pg_stat_activity where datname='lawdesk' and state like 'idle in transaction%' order by xact_start limit 20"

# Ungranted locks and who blocks whom
$P "select count(*) filter (where not granted) as waiting, count(*) as total from pg_locks"
$P "select a.pid, pg_blocking_pids(a.pid) as blocked_by, left(a.query,100) from pg_stat_activity a where cardinality(pg_blocking_pids(a.pid))>0"

# Database-wide counters since the last reset (rollbacks, deadlocks, connections used, temp files)
$P "select numbackends, xact_commit, xact_rollback, deadlocks, conflicts, temp_files, blks_read, blks_hit from pg_stat_database where datname='lawdesk'"
$P "select count(*) as backends_total, (select setting::int from pg_settings where name='max_connections') as max_connections from pg_stat_activity"
```

Do not call `pg_stat_reset`, `pg_terminate_backend`, `pg_cancel_backend` or `alter system`; this note never needs them.

## 5. Container resources at the time (memory limit 768 MiB for the backend)

```bash
docker stats --no-stream "$BACKEND" "$DB" "$PROXY"
docker inspect "$BACKEND" --format '{{.State.OOMKilled}} restarts={{.RestartCount}} started={{.State.StartedAt}}'
docker logs --since 2026-11-10T05:12:00Z --until 2026-11-10T05:17:00Z "$BACKEND" 2>&1 | grep -ci 'OutOfMemory'
```

## What the answers mean

- Many `Connection is not available, request timed out after 3000ms (total=10, active=10, idle=0, waiting=N)` lines
  with `idle in transaction` or `idle` backends in step 4 point at pool starvation (the cause found in the isolated
  reproduction, see `docs/evidence/DOC-R3-RT011/README.md`).
- 500s without such lines, with `ERROR` stack traces of another type, are a different cause: report the class and the
  first `com.lawdesk` frame.
- 502/504 or dial errors in the proxy log, and no matching `request completed` line in the backend, would be a proxy
  or upstream problem, not the application.
