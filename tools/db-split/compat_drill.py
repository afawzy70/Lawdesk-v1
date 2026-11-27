#!/usr/bin/env python3
"""M1-pre compatibility and recovery drill for the separated database ownership. Never run against a shared database: it creates its
own PostgreSQL clusters (loopback only, scram authentication, throw-away passwords) and removes them.

It runs the REAL backend jars (the currently deployed release and this one) and the REAL files of deploy/postgres through psql,
pg_dump and pg_restore, with synthetic data created through the HTTP API:

  1  today's installation: app-role.sql, the previous jar migrates and seeds as lawdesk_app
  2  isolated restored copy: dump -> second cluster -> roles/ownership/migrate/audit; every table's count and digest unchanged
  3  in-place transfer while the previous jar is serving requests; failures and latency measured
  4  previous jar on the split schema (Flyway on, as the old compose runs it): login, permissions, writes, background jobs
  5  this jar as the backend runs it (Flyway off)
  6  rollback to the old role script, then the next deployment again
  7  dump of the split database -> empty cluster -> restore, digest, start
  8  the documented limit: an old release needing its own DDL fails on the split schema, changes nothing; emergency revert

usage: compat_drill.py --pg-bin DIR --client-bin DIR --previous-jar JAR --current-jar JAR [--java JAVA] [--report FILE]
Exit 0 only if every check passes.
"""
import argparse
import datetime
import hashlib
import json
import os
import re
import shutil
import socket
import subprocess
import sys
import tempfile
import threading
import time
import urllib.error
import urllib.request
import uuid
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
SQL = REPO / "deploy" / "postgres"
PDF = (b"%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj 2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj "
       b"3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 10 10]>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF\n")
ADMIN_PASSWORD = "Drill-Admin-" + uuid.uuid4().hex[:12]
NEW_PASSWORD = "Drill-Changed-" + uuid.uuid4().hex[:12]
USER_PASSWORD = "Drill-User-" + uuid.uuid4().hex[:12]


class Failed(Exception):
    pass


def free_port():
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


class Report:
    def __init__(self):
        self.checks, self.facts = [], {}

    def check(self, name, ok, detail=""):
        self.checks.append({"check": name, "ok": bool(ok), "detail": str(detail)[:600]})
        print(("PASS " if ok else "FAIL ") + name + ("" if ok else "  -> " + str(detail)[:600]), flush=True)

    def require(self, name, ok, detail=""):
        self.check(name, ok, detail)
        if not ok:
            raise Failed(name)


class Cluster:
    def __init__(self, args, work, name):
        self.args, self.work, self.port = args, work / name, free_port()
        self.work.mkdir()
        self.passwords = {k: uuid.uuid4().hex for k in ("db_admin_password", "db_app_password", "db_owner_password")}
        for key, value in self.passwords.items():
            (self.work / key).write_text(value + "\n")
        subprocess.run([Path(args.pg_bin) / "initdb", "-D", self.work / "data", "-U", "postgres", "-E", "UTF8", "--locale=C",
                        "--auth=scram-sha-256", "--pwfile", self.work / "db_admin_password"], check=True, stdout=subprocess.DEVNULL)
        subprocess.run([Path(args.pg_bin) / "pg_ctl", "-D", self.work / "data", "-w", "-l", self.work / "server.log", "-o",
                        "-p %d -h 127.0.0.1 -c unix_socket_directories=''" % self.port, "start"], check=True, stdout=subprocess.DEVNULL)

    def stop(self):
        subprocess.run([Path(self.args.pg_bin) / "pg_ctl", "-D", self.work / "data", "-w", "-m", "immediate", "stop"],
                       check=False, stdout=subprocess.DEVNULL)

    def tool(self, name, *argv, stdin=None, check=True):
        env = dict(os.environ, PGPASSWORD=self.passwords["db_admin_password"])
        result = subprocess.run([str(Path(self.args.client_bin) / name), *argv], input=stdin, capture_output=True, env=env, check=False)
        if check and result.returncode != 0:
            raise Failed("%s %s: %s" % (name, " ".join(map(str, argv))[:200], result.stderr.decode(errors="replace")[-1500:]))
        return result

    def conn(self, database="lawdesk"):
        return ["-h", "127.0.0.1", "-p", str(self.port), "-U", "postgres", "-d", database]

    def psql(self, sql_text, database="lawdesk", check=True):
        return self.tool("psql", *self.conn(database), "-v", "ON_ERROR_STOP=1", "-qAt", stdin=sql_text.encode(), check=check)

    def query(self, sql_text, database="lawdesk"):
        return self.psql(sql_text, database).stdout.decode().strip()

    def script(self, name):
        """A deploy/postgres file as deploy.sh pipes it; the secret paths of the db container point at this cluster's files."""
        text = (SQL / name).read_text()
        for key in ("db_app_password", "db_owner_password"):
            text = text.replace("/run/secrets/" + key, str(self.work / key))
        return text

    def provision(self):
        """deploy.sh: database, roles, ownership, passwords in one session."""
        self.psql("".join(self.script(n) for n in ("database.sql", "roles.sql", "ownership.sql", "passwords.sql")), database="postgres")

    def legacy_roles(self):
        self.psql(self.script("app-role.sql"), database="postgres")

    def audit(self, check=True):
        return self.psql(self.script("audit.sql"), check=check)

    def digest(self):
        tables = self.query("select tablename from pg_tables where schemaname = 'lawdesk'"
                            " and tablename not in ('flyway_schema_history', 'app_release') order by 1").split()
        return {t: self.query("select count(*) || ':' || md5(coalesce(string_agg(x::text, ',' order by x::text), '')) from lawdesk.%s x" % t)
                for t in tables}


class Backend:
    """One backend process, configured through the same environment variables the compose file uses."""

    def __init__(self, args, cluster, jar, tag, extra=None):
        self.port, self.tag = free_port(), tag
        self.log = cluster.work / (tag + ".log")
        admin_file = cluster.work / "admin_bootstrap_password"
        admin_file.write_text(ADMIN_PASSWORD + "\n")
        env = dict(os.environ, SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:%d/lawdesk" % cluster.port,
                   SPRING_DATASOURCE_USERNAME="lawdesk_app", SPRING_DATASOURCE_PASSWORD=cluster.passwords["db_app_password"],
                   SERVER_PORT=str(self.port), SERVER_ADDRESS="127.0.0.1", LAWDESK_BOOTSTRAP_ADMIN_USERNAME="admin",
                   LAWDESK_BOOTSTRAP_ADMIN_EMAIL="admin@lawdesk.invalid", LAWDESK_BOOTSTRAP_ADMIN_PASSWORD_FILE=str(admin_file),
                   LAWDESK_RELEASE=tag, **(extra or {}))
        self.handle = self.log.open("w")
        self.process = subprocess.Popen([args.java, "-Xmx512m", "-jar", jar], env=env, stdout=self.handle, stderr=subprocess.STDOUT)

    def wait(self, timeout=240):
        deadline = time.time() + timeout
        while time.time() < deadline:
            if self.process.poll() is not None:
                return False
            try:
                with urllib.request.urlopen("http://127.0.0.1:%d/actuator/health/readiness" % self.port, timeout=3) as r:
                    if b'"UP"' in r.read():
                        return True
            except (urllib.error.URLError, OSError):
                pass
            time.sleep(1)
        return False

    def stop(self):
        if self.process.poll() is None:
            self.process.terminate()
            try:
                self.process.wait(40)
            except subprocess.TimeoutExpired:
                self.process.kill()
        self.handle.close()

    def permission_errors(self):
        text = self.log.read_text(errors="replace")
        return [line[:300] for line in text.splitlines()
                if re.search(r"(?i)permission denied|must be owner|SQLState: 42501|insufficient_privilege", line)]


class Client:
    def __init__(self, backend):
        self.base = "http://127.0.0.1:%d" % backend.port
        self.cookies = {}

    def call(self, method, path, body=None, raw=None, content_type="application/json"):
        data = raw if raw is not None else (json.dumps(body).encode() if body is not None else None)
        request = urllib.request.Request(self.base + path, data=data, method=method)
        if data is not None:
            request.add_header("Content-Type", content_type)
        if self.cookies:
            request.add_header("Cookie", "; ".join("%s=%s" % kv for kv in self.cookies.items()))
        if "XSRF-TOKEN" in self.cookies:
            request.add_header("X-XSRF-TOKEN", self.cookies["XSRF-TOKEN"])
        try:
            response = urllib.request.urlopen(request, timeout=60)
        except urllib.error.HTTPError as e:
            response = e
        for header in response.headers.get_all("Set-Cookie") or []:
            name, _, value = header.split(";", 1)[0].partition("=")
            if value:
                self.cookies[name] = value
            else:
                self.cookies.pop(name, None)
        payload = response.read()
        try:
            return response.status, json.loads(payload) if payload else None
        except ValueError:
            return response.status, payload

    def login(self, username, password):
        self.call("GET", "/api/v1/auth/csrf")
        status, me = self.call("POST", "/api/v1/auth/login", {"login": username, "password": password})
        self.call("GET", "/api/v1/auth/csrf")
        return status, me


def signed_in(backend, report, username, password, new_password):
    """Signs in, completing a forced password change on the first use of an account."""
    client = Client(backend)
    status, me = client.login(username, password)
    if status != 200:
        client = Client(backend)
        status, me = client.login(username, new_password)
        report.require("%s: %s signs in" % (backend.tag, username), status == 200, (status, me))
        return client
    if me.get("mustChangePassword"):
        status, body = client.call("POST", "/api/v1/me/password", {"currentPassword": password, "newPassword": new_password})
        report.require("%s: %s changes the temporary password" % (backend.tag, username), status < 300, (status, body))
        client = Client(backend)
        status, me = client.login(username, new_password)
    report.require("%s: %s signs in" % (backend.tag, username), status == 200, (status, me))
    return client


def workflow(backend, report, n):
    """The existing product, through its API: cases, proceedings, judgments, deadlines, documents, tasks, search, reports, jobs,
    permissions. `n` makes every created value unique, and the case list must keep every case of earlier phases."""
    tag = backend.tag
    admin = signed_in(backend, report, "admin", ADMIN_PASSWORD, NEW_PASSWORD)
    failures, calls = [], []

    def ok(method, path, body=None, expect=(200, 201, 204), **kw):
        calls.append(path)
        status, payload = admin.call(method, path, body, **kw)
        if status not in expect:
            failures.append("%s %s -> %s %s" % (method, path, status, str(payload)[:200]))
        return payload if isinstance(payload, (dict, list)) else {}

    me = ok("GET", "/api/v1/me")
    before = ok("GET", "/api/v1/cases?size=100").get("totalItems", -1)
    court = ok("POST", "/api/v1/courts", {"code": "DRILL%d" % n, "nameAr": "محكمة %d" % n, "nameEn": "Drill court %d" % n,
                                          "level": "FIRST_INSTANCE"})
    case = ok("POST", "/api/v1/cases", {"title": "قضية تمرين %d" % n, "caseType": "CIVIL", "openedOn": "2026-01-10"})
    ok("POST", "/api/v1/cases", {"title": "external ref %d" % n, "caseType": "CIVIL", "openedOn": "2026-01-10", "referenceNo": "EXT-%d" % n})
    cid = case.get("id")
    ok("PUT", "/api/v1/cases/%s" % cid, {"title": "قضية معدلة %d" % n, "caseType": "CIVIL", "openedOn": "2026-01-10",
                                           "version": case.get("version", 0)})
    proceeding = ok("POST", "/api/v1/cases/%s/proceedings" % cid, {"stage": "FIRST_INSTANCE", "courtId": court.get("id"),
                    "courtCaseNumber": str(100 + n), "courtCaseYear": 2026, "filingDate": "2026-01-12"})
    judgment = ok("POST", "/api/v1/proceedings/%s/judgment" % proceeding.get("id"),
                  {"judgmentDate": "2026-02-01", "outcome": "AGAINST", "summary": "drill"})
    deadline = ok("POST", "/api/v1/cases/%s/deadlines" % cid, {"sourceType": "JUDGMENT", "sourceId": judgment.get("id"),
                  "dueOn": "2026-03-01", "action": "appeal"})
    ok("PUT", "/api/v1/judgments/%s/appeal-deadline" % judgment.get("id"), {"deadlineId": deadline.get("id")})
    ok("POST", "/api/v1/tasks", {"title": "drill task %d" % n, "caseId": cid, "assigneeId": me.get("id"), "dueDate": (datetime.date.today() + datetime.timedelta(days=30)).isoformat()})
    boundary = "drill" + uuid.uuid4().hex
    multipart = (("--%s\r\nContent-Disposition: form-data; name=\"file\"; filename=\"d%d.pdf\"\r\nContent-Type: application/pdf\r\n\r\n"
                  % (boundary, n)).encode() + PDF + ("\r\n--%s--\r\n" % boundary).encode())
    document = ok("POST", "/api/v1/cases/%s/documents" % cid, raw=multipart, content_type="multipart/form-data; boundary=" + boundary)
    if document.get("id"):
        ok("POST", "/api/v1/documents/%s/withdraw" % document["id"], {"version": document.get("version", 0), "reason": "drill"})
        ok("POST", "/api/v1/documents/%s/permanent-deletion" % document["id"], {"reason": "drill purge"})
    for path in ("/api/v1/cases/%s" % cid, "/api/v1/cases/%s/judgments" % cid, "/api/v1/deadlines?size=5", "/api/v1/search?q=drill",
                 "/api/v1/reports/dashboard", "/api/v1/reports/indicators", "/api/v1/reports/cases/export?format=csv",
                 "/api/v1/tasks?overdue=true", "/api/v1/users", "/api/v1/roles"):
        ok("GET", path)
    # background jobs, run now (their schedules also run in the process)
    ok("POST", "/api/v1/deadlines/reminders/run")
    ok("POST", "/api/v1/follow-up/run")
    after = ok("GET", "/api/v1/cases?size=100").get("totalItems", -1)

    # permissions: a user without administration rights is still refused, and sees what the role allows
    username = "drill.staff%d" % n
    ok("POST", "/api/v1/users", {"username": username, "email": "%s@lawdesk.invalid" % username, "displayName": "Drill staff",
                                  "locale": "ar", "roles": ["LEGAL_STAFF"], "temporaryPassword": USER_PASSWORD})
    staff = signed_in(backend, report, username, USER_PASSWORD, NEW_PASSWORD)
    status_users, _ = staff.call("GET", "/api/v1/users")
    status_cases, _ = staff.call("GET", "/api/v1/cases")
    if (status_users, status_cases) != (403, 200):
        failures.append("permissions: staff GET users=%s cases=%s" % (status_users, status_cases))
    time.sleep(2)
    report.check("%s: %d API calls of the existing product succeed" % (tag, len(calls)), not failures, "; ".join(failures))
    report.check("%s: every earlier case is still listed (%s -> %s)" % (tag, before, after), after == before + 2, (before, after))
    report.check("%s: no permission error in the backend log" % tag, not backend.permission_errors(), backend.permission_errors()[:5])


def migrate_job(args, cluster, jar, report, label, extra=None):
    env = dict(os.environ, SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:%d/lawdesk" % cluster.port,
               SPRING_DATASOURCE_USERNAME="lawdesk_owner", SPRING_DATASOURCE_PASSWORD=cluster.passwords["db_owner_password"], **(extra or {}))
    result = subprocess.run([args.java, "-Xmx384m", "-jar", jar, "migrate"], env=env, capture_output=True, text=True, timeout=300)
    report.require(label + ": migration job (owner) succeeds and exits", result.returncode == 0, result.stdout[-1500:] + result.stderr[-500:])


def upgrade(args, cluster, report, label, extra=None):
    """What deploy.sh does for a split-aware release, in its order."""
    cluster.provision()
    migrate_job(args, cluster, args.current_jar, report, label, extra)
    cluster.psql(cluster.script("ownership.sql"))
    result = cluster.audit(check=False)
    report.require(label + ": privilege audit passes", result.returncode == 0, result.stderr.decode(errors="replace"))


def run(args, work, report, clusters, backends):
    def start(cluster, jar, tag, extra=None):
        backend = Backend(args, cluster, jar, tag, extra)
        backends.append(backend)
        up = backend.wait()
        report.require(tag + ": backend becomes ready", up, backend.log.read_text(errors="replace")[-1500:])
        return backend

    off = {"SPRING_FLYWAY_ENABLED": "false"}

    # 1 today's installation
    live = Cluster(args, work, "live")
    clusters.append(live)
    live.legacy_roles()
    previous = start(live, args.previous_jar, "p1-previous-legacy")
    workflow(previous, report, 1)
    previous.stop()
    report.facts["flyway_before"] = live.query("select count(*) || ' migrations, last ' || max(version::int) from lawdesk.flyway_schema_history")
    seeded = live.digest()
    report.facts["rows_seeded"] = sum(int(v.split(":")[0]) for v in seeded.values())
    dump = work / "before.dump"
    live.tool("pg_dump", *live.conn(), "-Fc", "-f", str(dump))

    # 2 isolated restored copy
    copy = Cluster(args, work, "copy")
    clusters.append(copy)
    copy.psql(copy.script("roles.sql"), database="postgres")
    copy.psql("create database lawdesk", database="postgres")
    started = time.time()
    copy.tool("pg_restore", *copy.conn(), "--exit-on-error", str(dump))
    report.require("restored copy: identical to the source before any change", copy.digest() == seeded, diff(seeded, copy.digest()))
    upgrade(args, copy, report, "restored copy")
    report.facts["restored_copy_seconds"] = round(time.time() - started, 1)
    report.require("restored copy: %d tables, row counts and content digests unchanged by transfer and migration" % len(seeded),
                   copy.digest() == seeded, diff(seeded, copy.digest()))

    # 3 in-place transfer under load, previous jar serving
    previous = start(live, args.previous_jar, "p3-previous-under-load")
    client = signed_in(previous, report, "admin", ADMIN_PASSWORD, NEW_PASSWORD)
    samples, stop = [], threading.Event()

    def load():
        while not stop.is_set():
            began = time.time()
            try:
                status, _ = client.call("GET", "/api/v1/cases?size=20")
            except OSError as e:
                status = str(e)
            samples.append((status, time.time() - began))
            time.sleep(0.02)

    thread = threading.Thread(target=load)
    thread.start()
    time.sleep(2)
    began = time.time()
    try:
        live.provision()
        report.facts["transfer_seconds"] = round(time.time() - began, 2)
        migrate_job(args, live, args.current_jar, report, "in place, previous release serving")
        live.psql(live.script("ownership.sql"))
        live.audit()
        time.sleep(2)
    finally:
        stop.set()
        thread.join(90)
    bad = [s for s, _ in samples if s != 200]
    report.facts["requests_during_transfer"] = {"total": len(samples), "not_200": len(bad),
                                                "max_seconds": round(max(d for _, d in samples), 3)}
    report.check("transfer while serving: %d requests, none failed" % len(samples), samples and not bad, bad[:5])
    workflow(previous, report, 2)   # the same process, connections opened before the transfer included
    previous.stop()

    # 4 previous release freshly started on the split schema, as a rollback would start it (Flyway on, as lawdesk_app)
    previous = start(live, args.previous_jar, "p4-previous-on-split")
    workflow(previous, report, 3)
    previous.stop()

    # 5 this release as the backend runs it
    current = start(live, args.current_jar, "p5-current-flyway-off", off)
    workflow(current, report, 4)
    current.stop()

    # 6 rollback with the old release's own role script, then forward again
    live.legacy_roles()
    report.facts["temporary_regranted_by_old_script"] = live.query("select has_database_privilege('lawdesk_app', 'lawdesk', 'TEMPORARY')")
    previous = start(live, args.previous_jar, "p6-previous-after-old-role-script")
    workflow(previous, report, 5)
    previous.stop()
    upgrade(args, live, report, "next deployment after a rollback")
    report.check("next deployment: TEMPORARY withdrawn again",
                 live.query("select has_database_privilege('lawdesk_app', 'lawdesk', 'TEMPORARY')") == "f")

    # 7 backup of the split database restored into an empty cluster
    split_digest = live.digest()
    dump2 = work / "split.dump"
    live.tool("pg_dump", *live.conn(), "-Fc", "-f", str(dump2))
    fresh = Cluster(args, work, "fresh")
    clusters.append(fresh)
    fresh.psql(fresh.script("roles.sql"), database="postgres")
    fresh.psql("create database lawdesk", database="postgres")
    fresh.tool("pg_restore", *fresh.conn(), "--exit-on-error", str(dump2))
    fresh.psql(fresh.script("ownership.sql"))
    fresh.psql(fresh.script("passwords.sql"))
    result = fresh.audit(check=False)
    report.check("restore of the split database: privilege audit passes", result.returncode == 0, result.stderr.decode(errors="replace"))
    report.require("restore of the split database: %d tables identical" % len(split_digest), fresh.digest() == split_digest,
                   diff(split_digest, fresh.digest()))
    current = start(fresh, args.current_jar, "p7-current-on-restored", off)
    workflow(current, report, 6)
    current.stop()

    # 8 documented limit, and the emergency way back
    extra = work / "extra-migration"
    extra.mkdir()
    (extra / "V846__drill_needs_ddl.sql").write_text("create table drill_needs_ddl (k int);\n")
    needs_ddl = {"SPRING_FLYWAY_LOCATIONS": "classpath:db/migration,filesystem:%s" % extra}
    state = fresh.digest(), fresh.query("select count(*) from lawdesk.flyway_schema_history")
    old = Backend(args, fresh, args.previous_jar, "p8-previous-needing-ddl", needs_ddl)
    backends.append(old)
    came_up = old.wait(150)
    old.stop()
    report.check("limit: an old release with a pending DDL migration does NOT start on the split schema",
                 not came_up and bool(old.permission_errors()), old.log.read_text(errors="replace")[-800:])
    report.check("limit: its failure changed no data and no history",
                 (fresh.digest(), fresh.query("select count(*) from lawdesk.flyway_schema_history")) == state)
    fresh.psql(fresh.script("revert-ownership.sql"))
    old = start(fresh, args.previous_jar, "p8-previous-after-revert", needs_ddl)
    report.check("emergency revert: that release can then run its DDL",
                 fresh.query("select to_regclass('lawdesk.drill_needs_ddl') is not null") == "t")
    old.stop()
    # a newer release always carries the older one's migrations: here, the drill's extra one
    upgrade(args, fresh, report, "re-split after the emergency revert", needs_ddl)
    report.check("re-split: the object the old release created now belongs to the owner",
                 fresh.query("select tableowner from pg_tables where schemaname = 'lawdesk' and tablename = 'drill_needs_ddl'") == "lawdesk_owner")


def diff(a, b):
    return {k: (a.get(k), b.get(k)) for k in set(a) | set(b) if a.get(k) != b.get(k)}


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    for name in ("--pg-bin", "--client-bin", "--previous-jar", "--current-jar"):
        parser.add_argument(name, required=True)
    parser.add_argument("--java", default="java")
    parser.add_argument("--report")
    parser.add_argument("--keep-logs", help="directory to copy the backend and server logs into")
    args = parser.parse_args()
    report, clusters, backends = Report(), [], []
    work = Path(tempfile.mkdtemp(prefix="lawdesk-split-drill-"))
    for name in ("previous_jar", "current_jar"):
        report.facts[name] = hashlib.sha256(Path(getattr(args, name)).read_bytes()).hexdigest()
    try:
        run(args, work, report, clusters, backends)
    except Failed as e:
        report.check("drill stopped", False, e)
    finally:
        for backend in backends:
            backend.stop()
        for cluster in clusters:
            cluster.stop()
        if args.keep_logs:
            Path(args.keep_logs).mkdir(parents=True, exist_ok=True)
            for log in work.rglob("*.log"):
                shutil.copy(log, Path(args.keep_logs) / (log.parent.name + "-" + log.name))
        shutil.rmtree(work, ignore_errors=True)
    passed = sum(c["ok"] for c in report.checks)
    summary = {"passed": passed, "failed": len(report.checks) - passed, "facts": report.facts, "checks": report.checks}
    if args.report:
        Path(args.report).write_text(json.dumps(summary, indent=2, ensure_ascii=False) + "\n")
    print("\nfacts:", json.dumps(report.facts, indent=1))
    print("RESULT passed=%d failed=%d" % (passed, summary["failed"]))
    return 0 if summary["failed"] == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
