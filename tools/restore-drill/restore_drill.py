#!/usr/bin/env python3
"""LD-DOC restore drill: proves that a pg_dump -Fc backup of the LawDesk database restores into an empty database and gives
the same data, the same files and the same access answers. It never touches UAT, Production or any shared database.

What it does, each step timed (see docs/handbook/06-operations-backup-restore-ar.md for the measured procedure):
  1. starts its OWN PostgreSQL cluster under --workdir (initdb with a marker file, unix socket in the workdir, TCP on 127.0.0.1
     only) and builds the least-privilege role and schema exactly like deploy/postgres/app-role.sql (that file is reused, with
     only the database name and the password line substituted);
  2. lets the real application migrate an empty database (java -jar, role lawdesk_app, search_path=lawdesk);
  3. loads SYNTHETIC data (seed.sql): documents with versions and binary files, links, OCR text, enforcement files, memos,
     org units, grants, scopes, tasks, audit rows;
  4. boots the application on it and records what four personas see through the API (cases, documents, downloaded file hashes);
  5. fingerprints the database (rows per table and a content hash per table, SHA-256 of every stored file, foreign keys,
     document links, version history, grants, scopes, sequences, the structure) and answers the CaseAccessPolicy question
     for the personas in SQL;
  6. dumps with the documented command (pg_dump -U postgres -d <db> -Fc);
  7. restores into a second EMPTY database with pg_restore, re-applies the role script, analyses;
  8. fingerprints again and compares everything; boots the application on the restored database and repeats the API reads.

Refuses to run unless the cluster is its own: the data directory must hold the marker file the tool wrote itself, database
names must start with lawdesk_drill_, the host is loopback only, port 5432 is refused, and SPRING_DATASOURCE_* / PG* variables
in the environment are rejected. Passwords exist only in memory and in 0600 files inside --workdir; none is printed.

Exit codes: 0 every check passed, 1 a check failed, 2 refused (unsafe configuration), 3 a tool or step failed.
"""

import argparse
import datetime
import hashlib
import json
import os
import re
import secrets
import shutil
import signal
import subprocess
import sys
import time
import urllib.error
import urllib.request
import http.cookiejar
from pathlib import Path

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[1]
MARKER = "LAWDESK_DRILL_THROWAWAY"
DB_PREFIX = "lawdesk_drill_"
DB_NAME = re.compile(r"^lawdesk_drill_[a-z0-9_]{1,32}$")
FORBIDDEN_ENV = ("SPRING_DATASOURCE_URL", "SPRING_DATASOURCE_USERNAME", "SPRING_DATASOURCE_PASSWORD", "PGHOST", "PGPORT",
                 "PGSERVICE", "PGDATABASE", "PGUSER", "PGPASSWORD", "PGPASSFILE", "PGSERVICEFILE", "DATABASE_URL")
APP_ROLE = "lawdesk_app"
SCHEMA = "lawdesk"
PERSONAS = (("manager", "20000000-0000-4000-8000-000000000001"), ("head", "20000000-0000-4000-8000-000000000002"),
            ("staff", "20000000-0000-4000-8000-000000000003"), ("archivist", "20000000-0000-4000-8000-000000000004"))


class Refused(Exception):
    """An unsafe configuration: nothing was started or changed."""


class StepFailed(Exception):
    pass


# --- guards -----------------------------------------------------------------------------------------------------------------
def check_database_name(name):
    if not DB_NAME.match(name):
        raise Refused("database name %r is not a drill database (must match %s)" % (name, DB_NAME.pattern))
    return name


def check_environment(environ):
    found = sorted(k for k in FORBIDDEN_ENV if environ.get(k))
    if found:
        raise Refused("refusing to run with %s set: the drill builds its own connections and must not inherit others"
                      % ", ".join(found))


def free_port():
    """A port nothing listens on right now (the host is shared: fixed ports collide with other work)."""
    import socket
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", 0))
        return probe.getsockname()[1]


def check_port(port):
    if port == 0:
        port = free_port()
    if not 1024 <= port <= 65535 or port in (5432, 5433, 6432):
        raise Refused("port %s is refused: use a high, unused port for the throwaway cluster" % port)
    return port


def check_workdir(workdir):
    """A new or empty directory, or one this tool created earlier (it holds the marker); never an existing data directory."""
    workdir = Path(workdir).resolve()
    if workdir in (Path("/"), Path.home(), REPO) or len(workdir.parts) < 3:
        raise Refused("workdir %s is too broad" % workdir)
    if workdir.exists():
        entries = [p for p in workdir.iterdir()]
        if entries and not (workdir / MARKER).exists():
            raise Refused("workdir %s is not empty and was not created by the drill" % workdir)
        if (workdir / "pgdata" / "PG_VERSION").exists() and not (workdir / "pgdata" / MARKER).exists():
            raise Refused("%s/pgdata is a PostgreSQL data directory the drill did not create" % workdir)
    return workdir


def check_loopback(host):
    if host not in ("127.0.0.1", "localhost", "::1"):
        raise Refused("host %r is not loopback" % host)


# --- helpers ----------------------------------------------------------------------------------------------------------------
class Clock:
    def __init__(self):
        self.steps = []
        self.t0 = time.monotonic()

    def run(self, name, fn, **detail):
        started = time.monotonic()
        print("[%6.1fs] START %s" % (started - self.t0, name), flush=True)
        entry = {"step": name, "ok": True, **detail}
        try:
            result = fn()
        except BaseException as error:
            entry.update(ok=False, error=str(error)[:600], seconds=round(time.monotonic() - started, 2))
            self.steps.append(entry)
            print("[%6.1fs] FAIL  %s: %s" % (time.monotonic() - self.t0, name, str(error)[:300]), flush=True)
            raise
        entry["seconds"] = round(time.monotonic() - started, 2)
        if isinstance(result, dict):
            entry.update(result)
        self.steps.append(entry)
        print("[%6.1fs] done  %s (%.2fs)" % (time.monotonic() - self.t0, name, entry["seconds"]), flush=True)
        return result


class Drill:
    def __init__(self, args):
        self.args = args
        self.work = check_workdir(args.workdir)
        self.port = check_port(args.port)
        self.app_port = check_port(args.app_port)
        self.pgdata = self.work / "pgdata"
        self.socket = self.work / "socket"
        self.server_bin = Path(args.server_bin)
        self.client_bin = Path(args.client_bin)
        self.env = dict(os.environ)
        libs = [p for p in (args.server_lib, args.client_lib) if p]
        if libs:
            self.env["LD_LIBRARY_PATH"] = ":".join(libs + [self.env.get("LD_LIBRARY_PATH", "")]).strip(":")
        self.env["PGOPTIONS"] = ""
        self.clock = Clock()
        self.app_password = secrets.token_urlsafe(24)
        self.persona_password = secrets.token_urlsafe(24)
        self.src_db = check_database_name(DB_PREFIX + "src")
        self.dst_db = check_database_name(DB_PREFIX + "restored")
        self.par_db = check_database_name(DB_PREFIX + "restored_j")
        self.fail_db = check_database_name(DB_PREFIX + "negative")
        self.processes = []
        self.report = {"checks": [], "measurements": {}}

    # ---- process execution -----------------------------------------------------------------------------------------------
    def tool(self, name, *argv, stdin=None, stdout=None, check=True, env=None, cwd=None):
        binary = (self.server_bin if name in ("initdb", "pg_ctl", "postgres") else self.client_bin) / name
        result = subprocess.run([str(binary), *argv], env=env or self.env, stdin=stdin, stdout=stdout or subprocess.PIPE,
                                stderr=subprocess.PIPE, cwd=cwd)
        if check and result.returncode != 0:
            text = result.stderr.decode(errors="replace").strip()
            if len(text) > 900:
                text = text[:600] + " [...] " + text[-250:]
            raise StepFailed("%s exited %d: %s" % (name, result.returncode, text))
        return result

    def psql(self, db, sql=None, file=None, variables=(), user="postgres", check=True, options=None):
        argv = ["-X", "-q", "-A", "-t", "-v", "ON_ERROR_STOP=1", "-h", str(self.socket), "-p", str(self.port), "-U", user,
                "-d", db]
        for key, value in variables:
            argv += ["-v", "%s=%s" % (key, value)]
        argv += ["-c", sql] if sql is not None else ["-f", str(file)]
        env = dict(self.env)
        env["PGOPTIONS"] = options or "-c search_path=%s" % SCHEMA
        result = self.tool("psql", *argv, env=env, check=check)
        return result.stdout.decode().strip() if result.returncode == 0 else result

    def rows(self, db, sql, user="postgres", options=None):
        out = self.psql(db, "select coalesce(json_agg(r), '[]'::json) from (%s) r" % sql, user=user, options=options)
        return json.loads(out)

    def scalar(self, db, sql, user="postgres", options=None):
        return self.psql(db, sql, user=user, options=options)

    # ---- cluster ---------------------------------------------------------------------------------------------------------
    def start_cluster(self):
        self.work.mkdir(parents=True, exist_ok=True)
        (self.work / MARKER).write_text("created by tools/restore-drill/restore_drill.py; safe to delete\n")
        if not self.pgdata.exists():
            pwfile = self.work / "superuser.pw"
            pwfile.write_text(secrets.token_urlsafe(24))
            pwfile.chmod(0o600)
            self.tool("initdb", "-D", str(self.pgdata), "-U", "postgres", "-E", "UTF8", "--locale", self.args.locale,
                      "--auth-local=trust", "--auth-host=scram-sha-256", "--pwfile", str(pwfile))
            (self.pgdata / MARKER).write_text("throwaway cluster of the restore drill\n")
        self.socket.mkdir(exist_ok=True)
        options = "-p %d -c listen_addresses=127.0.0.1 -c unix_socket_directories=%s -c fsync=off" % (self.port, self.socket)
        self.tool("pg_ctl", "-D", str(self.pgdata), "-o", options, "-l", str(self.work / "postgres.log"), "-w", "start")
        # The guard that matters: whatever answers on the socket must be the cluster this tool created.
        actual = self.scalar("postgres", "show data_directory")
        if Path(actual).resolve() != self.pgdata.resolve() or not (Path(actual) / MARKER).exists():
            raise Refused("the server on the socket is not the drill's own cluster (%s)" % actual)
        self.report["server_version"] = self.scalar("postgres", "show server_version")
        self.report["server_encoding_collation"] = self.scalar(
            "postgres", "select datcollate || ' / ' || pg_encoding_to_char(encoding) from pg_database where datname = 'postgres'")

    def stop_cluster(self):
        if (self.pgdata / "postmaster.pid").exists():
            self.tool("pg_ctl", "-D", str(self.pgdata), "-m", "fast", "-w", "stop", check=False)

    def assert_own_cluster(self, db):
        """Called before every statement that creates or drops a database."""
        check_database_name(db)
        actual = self.scalar("postgres", "show data_directory")
        if Path(actual).resolve() != self.pgdata.resolve():
            raise Refused("not the drill's cluster")

    def create_database(self, db):
        self.assert_own_cluster(db)
        self.psql("postgres", "drop database if exists %s with (force)" % db)
        self.psql("postgres", "create database %s" % db)

    def role_script(self, db):
        """deploy/postgres/app-role.sql with only the password line and the database name replaced (same statements)."""
        text = (REPO / "deploy" / "postgres" / "app-role.sql").read_text()
        password_line = r"\set app_password `cat /run/secrets/db_app_password`"
        if password_line not in text:
            raise StepFailed("deploy/postgres/app-role.sql changed: update the drill's substitution")
        text = text.replace(password_line, "\\set app_password '%s'" % self.app_password)
        for old, new in (("'CREATE DATABASE lawdesk'", "'CREATE DATABASE %s'" % db), ("datname = 'lawdesk'", "datname = '%s'" % db),
                         ("\\connect lawdesk\n", "\\connect %s\n" % db), ("ON DATABASE lawdesk ", "ON DATABASE %s " % db)):
            if old not in text:
                raise StepFailed("deploy/postgres/app-role.sql changed: %r not found" % old)
            text = text.replace(old, new)
        path = self.work / ("app-role-%s.sql" % db)
        path.write_text(text)
        path.chmod(0o600)
        return path

    def apply_role_script(self, db):
        self.assert_own_cluster(db)
        self.psql("postgres", file=self.role_script(db))

    # ---- the application -------------------------------------------------------------------------------------------------
    def jdbc(self, db):
        return "jdbc:postgresql://127.0.0.1:%d/%s" % (self.port, db)

    def start_app(self, db, label):
        java = self.args.java
        log = open(self.work / ("app-%s.log" % label), "wb")
        command = [java, "-Xmx512m", "-jar", self.args.jar, "--server.port=%d" % self.app_port,
                   "--spring.datasource.url=" + self.jdbc(db), "--spring.datasource.username=" + APP_ROLE,
                   "--lawdesk.followup.schedule-enabled=false", "--lawdesk.ocr.schedule-enabled=false",
                   "--server.servlet.session.cookie.secure=false"]
        env = dict(self.env)
        env["SPRING_DATASOURCE_PASSWORD"] = self.app_password
        process = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, env=env, start_new_session=True)
        self.processes.append(process)
        deadline = time.monotonic() + self.args.app_timeout
        while time.monotonic() < deadline:
            if process.poll() is not None:
                raise StepFailed("the application exited with %s; see %s" % (process.returncode, log.name))
            try:
                with urllib.request.urlopen("http://127.0.0.1:%d/actuator/health" % self.app_port, timeout=3) as response:
                    if response.status == 200 and json.load(response).get("status") == "UP":
                        return process
            except (urllib.error.URLError, OSError, ValueError):
                pass
            time.sleep(1)
        raise StepFailed("the application did not become healthy in %ds" % self.args.app_timeout)

    def stop_app(self, process):
        if process.poll() is None:
            os.killpg(process.pid, signal.SIGTERM)
            try:
                process.wait(timeout=30)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, signal.SIGKILL)
                process.wait()

    def migration_summary(self, label):
        text = (self.work / ("app-%s.log" % label)).read_text(errors="replace")
        applied = re.search(r"Successfully applied (\d+) migrations", text)
        uptodate = "is up to date" in text or "No migration necessary" in text
        return {"migrations_applied": int(applied.group(1)) if applied else 0, "schema_up_to_date_message": uptodate}

    # ---- API reads --------------------------------------------------------------------------------------------------------
    class Api:
        def __init__(self, port):
            self.base = "http://127.0.0.1:%d" % port
            self.jar = http.cookiejar.CookieJar()
            self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar))
            self.opener.open(self.base + "/api/v1/auth/csrf", timeout=20).read()

        def xsrf(self):
            return next((c.value for c in self.jar if c.name == "XSRF-TOKEN"), "")

        def call(self, method, path, body=None, raw=False):
            headers = {"Accept": "application/json"}
            data = None
            if body is not None:
                data = json.dumps(body).encode()
                headers["Content-Type"] = "application/json"
            if method != "GET":
                headers["X-XSRF-TOKEN"] = self.xsrf()
            request = urllib.request.Request(self.base + path, data=data, method=method, headers=headers)
            try:
                with self.opener.open(request, timeout=60) as response:
                    payload = response.read()
                    return response.status, payload if raw else (json.loads(payload) if payload else None)
            except urllib.error.HTTPError as error:
                return error.code, None

    def api_fingerprint(self):
        """What each persona sees through the real endpoints, and the SHA-256 of the files downloaded through them."""
        out = {}
        anonymous = self.Api(self.app_port)
        out["anonymous_cases_status"] = anonymous.call("GET", "/api/v1/cases")[0]
        out["release_status"] = anonymous.call("GET", "/api/v1/release")[0]
        for name, _ in PERSONAS:
            api = self.Api(self.app_port)
            status, me = api.call("POST", "/api/v1/auth/login", {"login": "synth." + name, "password": self.persona_password})
            if status != 200:
                out[name] = {"login_status": status}
                continue
            entry = {"login_status": 200}
            cases, page = [], 0
            while True:
                status, body = api.call("GET", "/api/v1/cases?page=%d&size=100" % page)
                if status != 200:
                    entry["cases_status"] = status
                    break
                cases += [c["id"] for c in body["items"]]
                if page + 1 >= body["totalPages"]:
                    break
                page += 1
            entry["cases"] = len(cases)
            entry["cases_digest"] = hashlib.sha256(",".join(sorted(cases)).encode()).hexdigest()
            documents, page = [], 0
            while True:
                status, body = api.call("GET", "/api/v1/documents?page=%d&size=100" % page)
                if status != 200:
                    entry["documents_status"] = status
                    break
                documents += [d["id"] for d in body["items"]]
                if page + 1 >= body["totalPages"]:
                    break
                page += 1
            entry["documents"] = len(documents)
            entry["documents_digest"] = hashlib.sha256(",".join(sorted(documents)).encode()).hexdigest()
            hashes = {}
            for doc in sorted(documents)[: self.args.download_sample]:
                status, payload = api.call("GET", "/api/v1/documents/%s/content" % doc, raw=True)
                hashes[doc] = hashlib.sha256(payload).hexdigest() if status == 200 else status
                status, versions = api.call("GET", "/api/v1/documents/%s/versions" % doc)
                if status == 200:
                    for version in versions if isinstance(versions, list) else versions.get("items", []):
                        no = version.get("versionNo", version.get("version_no"))
                        s2, p2 = api.call("GET", "/api/v1/documents/%s/versions/%s/content" % (doc, no), raw=True)
                        hashes["%s#%s" % (doc, no)] = hashlib.sha256(p2).hexdigest() if s2 == 200 else s2
            entry["downloaded_files"] = len(hashes)
            entry["download_digest"] = hashlib.sha256(json.dumps(hashes, sort_keys=True).encode()).hexdigest()
            status, tasks = api.call("GET", "/api/v1/tasks?size=100")
            entry["tasks_status"] = status
            if status == 200:
                entry["tasks_first_page_statuses"] = sorted({t.get("status") for t in tasks["items"]})
            out[name] = entry
        return out

    # ---- fingerprints ----------------------------------------------------------------------------------------------------
    def fingerprint(self, db):
        q = lambda sql: self.rows(db, sql)  # noqa: E731
        fp = {}
        tables = [r["relname"] for r in q("select c.relname from pg_class c join pg_namespace n on n.oid = c.relnamespace "
                                            "where n.nspname = '%s' and c.relkind in ('r','p') order by 1" % SCHEMA)]
        fp["tables"] = {}
        for table in tables:
            fp["tables"][table] = self.rows(
                db, "select count(*) as rows, coalesce(md5(string_agg(h, ',' order by h)), '') as digest from "
                    "(select md5(t::text) as h from %s.%s t) x" % (SCHEMA, table))[0]
        # Files: the SHA-256 column against the bytes, for every stored file and version, and the file list as a whole.
        fp["files"] = q("""
            select 'version' as kind, count(*) as files, sum(length(c.content)) as bytes,
                   count(*) filter (where encode(sha256(c.content), 'hex') <> v.sha256) as hash_mismatch,
                   count(*) filter (where length(c.content) <> v.size_bytes) as size_mismatch,
                   md5(string_agg(v.document_id || ':' || v.version_no || ':' || encode(sha256(c.content), 'hex'), ',' order by v.document_id, v.version_no)) as digest
              from document_version v join document_version_content c on c.version_id = v.id
            union all
            select 'head', count(*), sum(length(c.content)), count(*) filter (where encode(sha256(c.content), 'hex') <> d.sha256),
                   count(*) filter (where length(c.content) <> d.size_bytes),
                   md5(string_agg(d.id || ':' || encode(sha256(c.content), 'hex'), ',' order by d.id))
              from case_document d join case_document_content c on c.document_id = d.id""")
        fp["files_without_bytes"] = self.scalar(db, "select count(*) from document_version v where not exists (select 1 from "
                                                    "document_version_content c where c.version_id = v.id)")
        fp["version_history"] = q("""
            select count(*) as documents,
                   count(*) filter (where d.current_version_no <> m.max_no) as current_not_latest,
                   md5(string_agg(d.id || ':' || d.current_version_no || ':' || m.versions || ':' || m.digest, ',' order by d.id)) as digest
              from %s.case_document d join (select document_id, max(version_no) as max_no, count(*) as versions,
                     string_agg(version_no || '/' || sha256 || '/' || uploaded_at, ';' order by version_no) as digest
                     from %s.document_version group by document_id) m on m.document_id = d.id""" % (SCHEMA, SCHEMA))
        # Foreign keys: validated, and no orphan in any child table.
        fks = q("""select con.conname, cn.nspname as child_schema, cc.relname as child, pn.nspname as parent_schema, pc.relname as parent,
                          con.convalidated,
                          (select json_agg(a.attname order by k.ord) from unnest(con.conkey) with ordinality k(attnum, ord)
                             join pg_attribute a on a.attrelid = con.conrelid and a.attnum = k.attnum) as child_cols,
                          (select json_agg(a.attname order by k.ord) from unnest(con.confkey) with ordinality k(attnum, ord)
                             join pg_attribute a on a.attrelid = con.confrelid and a.attnum = k.attnum) as parent_cols
                     from pg_constraint con join pg_class cc on cc.oid = con.conrelid join pg_namespace cn on cn.oid = cc.relnamespace
                     join pg_class pc on pc.oid = con.confrelid join pg_namespace pn on pn.oid = pc.relnamespace
                    where con.contype = 'f' and cn.nspname = '%s' order by 1, 3""" % SCHEMA)
        orphans = {}
        for fk in fks:
            on = " and ".join('p."%s" = c."%s"' % (p, c) for c, p in zip(fk["child_cols"], fk["parent_cols"]))
            notnull = " and ".join('c."%s" is not null' % c for c in fk["child_cols"])
            count = int(self.scalar(db, 'select count(*) from %s."%s" c where %s and not exists (select 1 from %s."%s" p where %s)'
                                    % (SCHEMA, fk["child"], notnull, SCHEMA, fk["parent"], on)))
            if count:
                orphans[fk["conname"] + "@" + fk["child"]] = count
        fp["foreign_keys"] = {"count": len(fks), "not_validated": [f["conname"] for f in fks if not f["convalidated"]],
                              "orphans": orphans}
        # Links between documents and the records they support.
        fp["links"] = q("""
            select (select count(*) from document_link) as links,
                   (select count(*) from document_link where claim_id is not null) as to_claims,
                   (select count(*) from document_link where defense_id is not null) as to_defenses,
                   (select count(*) from document_link where hearing_id is not null) as to_hearings,
                   (select count(*) from document_link where judgment_id is not null) as to_judgments,
                   (select count(*) from document_link where task_id is not null) as to_tasks,
                   (select count(*) from claim where source_document_id is not null) as claims_with_source,
                   (select count(*) from defense where memo_document_id is not null) as defenses_with_memo,
                   (select count(*) from enforcement_action where document_id is not null) as enforcement_actions_with_document,
                   (select count(*) from legal_document where document_id is not null) as memos_with_file,
                   (select count(*) from document_text) as texts, (select count(*) from document_text where text like '%%SYNTH-DOC%%') as texts_with_marker,
                   (select count(*) from document_link l join case_document d on d.id = l.document_id
                      left join claim cl on cl.id = l.claim_id left join defense df on df.id = l.defense_id
                      left join hearing h on h.id = l.hearing_id left join task t on t.id = l.task_id
                      left join judgment j on j.id = l.judgment_id left join proceeding jp on jp.id = j.proceeding_id
                     where coalesce(cl.case_id, df.case_id, h.case_id, t.case_id, jp.case_id) is distinct from d.case_id) as links_across_cases,
                   (select md5(string_agg(l.document_id || ':' || coalesce(l.claim_id::text, '') || coalesce(l.defense_id::text, '')
                      || coalesce(l.hearing_id::text, '') || coalesce(l.judgment_id::text, '') || coalesce(l.task_id::text, ''), ',' order by l.id)) from document_link l) as digest""")[0]
        fp["links_note"] = "document_link target ids and the cross-case count (must be 0)"
        # Grants, scopes, units, roles.
        fp["access_rows"] = {t: self.rows(db, "select count(*) as rows, md5(string_agg(md5(x::text), ',' order by md5(x::text))) as digest from %s.%s x"
                                          % (SCHEMA, t))[0]
                             for t in ("case_grant", "access_scope", "org_unit", "user_role", "role_permission", "app_role")}
        fp["sequences"] = q("select schemaname || '.' || sequencename as name, last_value from pg_sequences where schemaname = '%s' order by 1" % SCHEMA)
        # The structure: functions (case_visible and friends), triggers, indexes and constraints, as the database prints them.
        # Index and constraint texts are compared after removing the casts and brackets of ARRAY comparisons: pg_dump prints
        # "= ANY ((ARRAY['A'::varchar])::text[])" and PostgreSQL re-parses it as "= ANY (ARRAY[('A'::varchar)::text])". Same expression,
        # different text (observed in the drill: 265 indexes, 1234 constraints, identical names and counts); the raw digests are kept
        # in structure_raw for the record and are not compared.
        norm = "regexp_replace(%s, '::(character varying|text)(\\[\\])?|[() ]', '', 'g')"
        fp["structure"] = q("""
            select 'functions' as kind, count(*) as n, md5(string_agg(pg_get_functiondef(p.oid), '' order by p.proname, p.oid)) as digest
              from pg_proc p join pg_namespace n on n.oid = p.pronamespace where n.nspname = '%(s)s' and p.prokind = 'f'
            union all select 'triggers', count(*), md5(string_agg(pg_get_triggerdef(t.oid), '' order by t.tgname, t.tgrelid::text))
              from pg_trigger t join pg_class c on c.oid = t.tgrelid join pg_namespace n on n.oid = c.relnamespace where n.nspname = '%(s)s' and not t.tgisinternal
            union all select 'indexes', count(*), md5(string_agg(%(ni)s, '' order by indexname)) from pg_indexes where schemaname = '%(s)s'
            union all select 'constraints', count(*), md5(string_agg(c.conname || %(nc)s, '' order by n.nspname, c.conname, %(nc)s))
              from pg_constraint c join pg_namespace n on n.oid = c.connamespace where n.nspname = '%(s)s'""" % {
            "s": SCHEMA, "ni": norm % "indexdef", "nc": norm % "pg_get_constraintdef(c.oid)"})
        fp["structure_raw"] = q("""
            select 'indexes' as kind, md5(string_agg(indexdef, '' order by indexname)) as digest from pg_indexes where schemaname = '%(s)s'
            union all select 'constraints', md5(string_agg(pg_get_constraintdef(c.oid), '' order by n.nspname, c.conname, c.oid::text))
              from pg_constraint c join pg_namespace n on n.oid = c.connamespace where n.nspname = '%(s)s'""" % {"s": SCHEMA})
        fp["ownership"] = q("select c.relkind, pg_get_userbyid(c.relowner) as owner, count(*) as n from pg_class c join pg_namespace n "
                            "on n.oid = c.relnamespace where n.nspname = '%s' group by 1, 2 order by 1, 2" % SCHEMA)
        fp["flyway"] = q("select count(*) as applied, count(*) filter (where not success) as failed, max(version::int) as latest from %s.flyway_schema_history where version is not null" % SCHEMA)[0]
        fp["visibility"] = self.visibility(db)
        return fp

    def persona_scope(self, db, user_id):
        """Mirrors UserAccounts.scopes (tokens from the user's and the roles' access_scope rows) as CaseAccessPolicy.scope() reads them."""
        row = self.rows(db, """
            select coalesce(bool_or(s.scope_type = 'ALL'), false) as all_scope,
                   coalesce(string_agg(distinct case s.scope_type when 'ORG_UNIT' then s.scope_ref when 'OWN_UNIT' then u.org_unit_id::text end, ',')
                            filter (where s.scope_type in ('ORG_UNIT','OWN_UNIT') and (s.scope_type = 'ORG_UNIT' or u.org_unit_id is not null)), '') as units,
                   coalesce(string_agg(distinct s.scope_ref, ',') filter (where s.scope_type = 'CASE_TYPE'), '') as types,
                   exists (select 1 from %(s)s.user_role ur join %(s)s.role_permission rp on rp.role_id = ur.role_id
                            where ur.user_id = u.id and rp.permission = 'CASE_EDIT_IN_SCOPE') as edit_in_scope
              from %(s)s.app_user u
              left join %(s)s.access_scope s on (s.subject_type = 'USER' and s.subject_ref = u.id::text)
                   or (s.subject_type = 'ROLE' and s.subject_ref in (select r.code from %(s)s.user_role ur join %(s)s.app_role r on r.id = ur.role_id where ur.user_id = u.id))
             where u.id = '%(id)s' group by u.id""" % {"s": SCHEMA, "id": user_id})
        if not row:
            raise StepFailed("persona %s missing" % user_id)
        scope = row[0]
        units = sorted(set(filter(None, scope["units"].split(","))))
        return {"all": scope["all_scope"], "units": ",".join(units), "types": ",".join(sorted(set(filter(None, scope["types"].split(","))))),
                "edit": scope["edit_in_scope"]}

    def visibility(self, db):
        """The CaseAccessPolicy questions, written as the policy writes them (viewable / changeable / visibleCaseIds), asked per persona."""
        out = {}
        for name, user_id in PERSONAS:
            scope = self.persona_scope(db, user_id)
            bind = {"me": "'%s'::uuid" % user_id, "all": "true" if scope["all"] else "false",
                    "units": "'%s'" % scope["units"], "types": "'%s'" % scope["types"], "edit": "true" if scope["edit"] else "false"}
            fast = ("(c.primary_member_id = {me} or c.alternate_member_id = {me} or c.created_by = {me} or ({all} and c.confidentiality <> 'RESTRICTED')"
                    " or ({types} <> '' and c.confidentiality <> 'RESTRICTED' and c.case_type = any (string_to_array({types}, ',')))"
                    " or (({units} <> '' or c.id in (select cg.case_id from {s}.case_grant cg where cg.user_id = {me} and cg.revoked_at is null)"
                    " or c.id in (select ct.case_id from {s}.task ct where ct.assignee_id = {me} and ct.case_id is not null))"
                    " and {s}.case_visible(c.id, {me}, {all}, {units}, {types})))").format(s=SCHEMA, **bind)
            slow = "{s}.case_visible(c.id, {me}, {all}, {units}, {types})".format(s=SCHEMA, **bind)
            changeable = "{s}.case_changeable(c.id, {me}, {edit}, {all}, {units}, {types})".format(s=SCHEMA, **bind)
            row = self.rows(db, """
                select (select count(*) from {s}.legal_case c where {fast}) as viewable,
                       (select md5(coalesce(string_agg(c.id::text, ',' order by c.id), '')) from {s}.legal_case c where {fast}) as viewable_digest,
                       (select count(*) from {s}.legal_case c where {slow}) as viewable_by_function,
                       (select count(*) from {s}.legal_case c where {fast} and {changeable}) as changeable,
                       (select md5(coalesce(string_agg(c.id::text, ',' order by c.id), '')) from {s}.legal_case c where {fast} and {changeable}) as changeable_digest,
                       (select count(*) from {s}.case_document d where d.case_id in (select c.id from {s}.legal_case c where {fast})) as documents,
                       (select count(*) from {s}.document_version v join {s}.case_document d on d.id = v.document_id where d.case_id in (select c.id from {s}.legal_case c where {fast})) as versions,
                       (select count(*) from {s}.task t where t.case_id in (select c.id from {s}.legal_case c where {fast})) as tasks,
                       (select count(*) from {s}.hearing h where h.case_id in (select c.id from {s}.legal_case c where {fast})) as hearings,
                       (select count(*) from {s}.enforcement_file e where e.case_id in (select c.id from {s}.legal_case c where {fast})) as enforcement_files,
                       (select count(*) from {s}.legal_document m where m.case_id in (select c.id from {s}.legal_case c where {fast})) as memos""".format(
                s=SCHEMA, fast=fast, slow=slow, changeable=changeable))[0]
            row["scope"] = scope
            out[name] = row
        return out


def compare(label, before, after, path=""):
    """A list of differences between two JSON-like values (empty means identical)."""
    diffs = []
    if isinstance(before, dict) and isinstance(after, dict):
        for key in sorted(set(before) | set(after)):
            if key not in before or key not in after:
                diffs.append("%s%s: only in %s" % (path, key, "after" if key in after else "before"))
            else:
                diffs += compare(label, before[key], after[key], "%s%s." % (path, key))
    elif isinstance(before, list) and isinstance(after, list) and len(before) == len(after) and any(isinstance(x, (dict, list)) for x in before):
        for i, (x, y) in enumerate(zip(before, after)):
            diffs += compare(label, x, y, "%s%s." % (path, i))
    elif before != after:
        diffs.append("%s: before=%s after=%s" % (path.rstrip("."), json.dumps(before)[:120], json.dumps(after)[:120]))
    return diffs


def run_command(argv, input_text=None):
    return subprocess.run(argv, input=input_text, capture_output=True, text=True)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--workdir", required=True, help="new or drill-owned directory for the throwaway cluster and artefacts")
    parser.add_argument("--server-bin", required=True, help="directory with initdb, pg_ctl and postgres (PostgreSQL 18)")
    parser.add_argument("--client-bin", required=True, help="directory with psql, pg_dump and pg_restore (18)")
    parser.add_argument("--server-lib", default="", help="library directory the server binaries need (e.g. <zonky>/lib)")
    parser.add_argument("--client-lib", default="", help="library directory the client binaries need (libpq)")
    parser.add_argument("--jar", required=True, help="backend/target/lawdesk-backend-*.jar built from the commit under test")
    parser.add_argument("--java", default=os.path.join(os.environ.get("JAVA_HOME", ""), "bin", "java") if os.environ.get("JAVA_HOME") else "java")
    parser.add_argument("--port", type=int, default=0, help="PostgreSQL port (loopback); 0 picks a free one")
    parser.add_argument("--app-port", type=int, default=0, help="application port (loopback); 0 picks a free one")
    parser.add_argument("--scale", type=int, default=1, help="1 = 300 cases, 600 documents, 1200 versions (about 70 MB of files)")
    parser.add_argument("--locale", default="C.UTF-8")
    parser.add_argument("--app-timeout", type=int, default=240)
    parser.add_argument("--download-sample", type=int, default=25, help="documents per persona whose files are downloaded and hashed through the API")
    parser.add_argument("--skip-api", action="store_true", help="skip the application boots and API reads (database-only drill)")
    parser.add_argument("--evidence", default="", help="write drill-result.json here")
    parser.add_argument("--keep", action="store_true", help="leave the cluster running and the workdir in place")
    args = parser.parse_args(argv)

    try:
        check_environment(os.environ)
        check_loopback("127.0.0.1")
        drill = Drill(args)
    except Refused as error:
        print("REFUSED: %s" % error, file=sys.stderr)
        return 2
    try:
        return execute(drill)
    except Refused as error:
        print("REFUSED: %s" % error, file=sys.stderr)
        return 2
    except (StepFailed, OSError, subprocess.SubprocessError) as error:
        print("DRILL STEP FAILED: %s" % error, file=sys.stderr)
        finish(drill, False)
        return 3
    finally:
        for process in drill.processes:
            drill.stop_app(process)
        if not args.keep:
            drill.stop_cluster()


def finish(drill, passed):
    report = drill.report
    report.update(passed=passed, steps=drill.clock.steps, finished=datetime.datetime.now(datetime.timezone.utc).isoformat(timespec="seconds"),
                  total_seconds=round(time.monotonic() - drill.clock.t0, 1))
    if drill.args.evidence:
        Path(drill.args.evidence).mkdir(parents=True, exist_ok=True)
        (Path(drill.args.evidence) / "drill-result.json").write_text(json.dumps(report, indent=2, ensure_ascii=False, default=str))


def execute(d):
    clock, args, report = d.clock, d.args, d.report
    report.update(started=datetime.datetime.now(datetime.timezone.utc).isoformat(timespec="seconds"), scale=args.scale,
                  client_version=d.tool("pg_dump", "--version").stdout.decode().strip(),
                  restore_version=d.tool("pg_restore", "--version").stdout.decode().strip())

    def check(name, ok, detail=""):
        report["checks"].append({"check": name, "ok": bool(ok), "detail": detail})
        print("%s %s %s" % ("PASS" if ok else "FAIL", name, detail), flush=True)

    clock.run("1 start the throwaway cluster", d.start_cluster)
    clock.run("2 create the database and the application role (deploy/postgres/app-role.sql)",
              lambda: (d.assert_own_cluster(d.src_db), d.apply_role_script(d.src_db)))
    if not args.skip_api:
        process = clock.run("3 the application migrates the empty database (first boot)", lambda: d.start_app(d.src_db, "migrate"))
        report["migration_boot"] = d.migration_summary("migrate")
        d.stop_app(process)
    else:
        raise StepFailed("--skip-api needs another way to migrate; not implemented: run without it")

    hash_value = persona_hash(d.persona_password)
    seed_started = time.monotonic()
    clock.run("4 load SYNTHETIC data as the application role", lambda: d.psql(
        d.src_db, file=HERE / "seed.sql", user=APP_ROLE, variables=(("scale", args.scale), ("pw_hash", hash_value)),
        options="-c search_path=%s" % SCHEMA))
    report["measurements"]["seed_seconds"] = round(time.monotonic() - seed_started, 1)
    report["measurements"]["source_database_bytes"] = int(d.scalar("postgres", "select pg_database_size('%s')" % d.src_db))

    process = clock.run("5 boot on the source and read through the API (4 personas)", lambda: d.start_app(d.src_db, "source"))
    api_before = clock.run("5b API fingerprint of the source", d.api_fingerprint)
    d.stop_app(process)
    report["api_before"] = api_before

    before = clock.run("6 fingerprint the source database", lambda: d.fingerprint(d.src_db))
    report["fingerprint_before"] = summarise(before)
    check("source: stored files match their SHA-256 column",
          all(f["hash_mismatch"] == 0 and f["size_mismatch"] == 0 for f in before["files"]) and before["files_without_bytes"] == "0",
          json.dumps([{k: f[k] for k in ("kind", "files", "bytes")} for f in before["files"]]))

    dump = d.work / "lawdesk_drill.dump"
    # The documented backup command of deploy/scripts/deploy.sh, run against the drill cluster.
    def make_dump():
        with open(dump, "wb") as out:
            os.chmod(dump, 0o600)
            d.tool("pg_dump", "-h", str(d.socket), "-p", str(d.port), "-U", "postgres", "-d", d.src_db, "-Fc", stdout=out)
        return {"bytes": dump.stat().st_size}
    dump_info = clock.run("7 pg_dump -U postgres -d <db> -Fc", make_dump)
    report["measurements"]["dump_bytes"] = dump_info["bytes"]
    report["measurements"]["dump_sha256"] = hashlib.sha256(dump.read_bytes()).hexdigest()
    listing = d.tool("pg_restore", "--list", str(dump)).stdout.decode()
    report["measurements"]["dump_toc_entries"] = len([l for l in listing.splitlines() if l and not l.startswith(";")])

    # Negative experiment: the order the existing runbook text suggests (role script first) fails on the schema.
    def negative():
        d.create_database(d.fail_db)
        d.apply_role_script(d.fail_db)
        result = d.tool("pg_restore", "-h", str(d.socket), "-p", str(d.port), "-U", "postgres", "-d", d.fail_db, "--exit-on-error", str(dump), check=False)
        return {"rc": result.returncode, "stderr": result.stderr.decode(errors="replace")[-400:],
                "tables_after": int(d.scalar(d.fail_db, "select count(*) from pg_tables where schemaname = '%s'" % SCHEMA))}
    negative_result = clock.run("7b EXPERIMENT: restore into a database the role script already prepared (expected to fail)", negative)
    report["negative_experiment"] = negative_result
    check("experiment: role-script-first order is refused by pg_restore (documented as the wrong order)", negative_result["rc"] != 0)
    d.psql("postgres", "drop database %s with (force)" % d.fail_db)

    def restore(db, jobs):
        d.assert_own_cluster(db)
        d.psql("postgres", "create database %s" % db)
        argv = ["-h", str(d.socket), "-p", str(d.port), "-U", "postgres", "-d", db, "--exit-on-error"]
        if jobs > 1:
            argv += ["-j", str(jobs)]
        result = d.tool("pg_restore", *argv, str(dump))
        return {"pg_restore_stderr": result.stderr.decode(errors="replace").strip()[:2000]}
    clock.run("8 create an empty database and pg_restore --exit-on-error (single job)", lambda: restore(d.dst_db, 1))
    check("pg_restore printed nothing on stderr (no warnings, no ignored errors)", not clock.steps[-1].get("pg_restore_stderr"),
          clock.steps[-1].get("pg_restore_stderr", "")[:200])
    clock.run("9 re-apply deploy/postgres/app-role.sql on the restored database", lambda: d.apply_role_script(d.dst_db))
    clock.run("10 ANALYZE (a restore carries no planner statistics)", lambda: d.psql(d.dst_db, "analyze"))
    after = clock.run("11 fingerprint the restored database", lambda: d.fingerprint(d.dst_db))
    report["fingerprint_after"] = summarise(after)

    differences = compare("db", strip_noise(before), strip_noise(after))
    report["differences"] = differences
    check("restored database is identical to the source (rows, content hashes, files, FKs, links, versions, grants, scopes, sequences, structure, visibility)",
          not differences, "; ".join(differences[:5]))
    check("restored: every stored file matches its SHA-256 column and size",
          all(f["hash_mismatch"] == 0 and f["size_mismatch"] == 0 for f in after["files"]) and after["files_without_bytes"] == "0")
    check("restored: no orphan rows and every foreign key validated", not after["foreign_keys"]["orphans"] and not after["foreign_keys"]["not_validated"],
          "%d foreign keys" % after["foreign_keys"]["count"])
    check("restored: no document link across cases", after["links"]["links_across_cases"] == 0)
    check("restored: the visibility answers of four personas are identical", before["visibility"] == after["visibility"],
          json.dumps({k: v["viewable"] for k, v in after["visibility"].items()}))
    check("restored: the fast CaseAccessPolicy predicate equals case_visible() for every persona",
          all(v["viewable"] == v["viewable_by_function"] for v in after["visibility"].values()))
    privileges = d.rows(d.dst_db, "select rolconfig::text as cfg from pg_roles where rolname = '%s'" % APP_ROLE)[0]["cfg"]
    check("restored: the application role keeps search_path=lawdesk after the role script", "search_path=lawdesk" in privileges, privileges)

    # Parallel restore, measured on its own database (same dump).
    clock.run("12 EXPERIMENT: pg_restore -j 4 into another empty database", lambda: restore(d.par_db, 4))
    parallel = clock.run("12b fingerprint of the parallel restore", lambda: d.fingerprint(d.par_db))
    # The role script is not applied here, so ignore nothing: structure and data must still equal the source.
    parallel_diffs = compare("db", strip_noise(before), strip_noise(parallel))
    check("experiment: the -j 4 restore is identical as well", not parallel_diffs, "; ".join(parallel_diffs[:3]))

    # The application on the restored database.
    process = clock.run("13 boot the application on the restored database", lambda: d.start_app(d.dst_db, "restored"))
    report["restored_boot"] = d.migration_summary("restored")
    check("restored: Flyway applied nothing new and the schema validated", report["restored_boot"]["migrations_applied"] == 0)
    api_after = clock.run("13b API fingerprint of the restored database", d.api_fingerprint)
    d.stop_app(process)
    report["api_after"] = api_after
    api_diffs = compare("api", api_before, api_after)
    check("restored: the API answers (cases, documents, downloaded file hashes) are identical for the four personas", not api_diffs, "; ".join(api_diffs[:3]))
    check("restored: smoke reads worked", all(api_after[n].get("login_status") == 200 and api_after[n].get("cases", 0) > 0 for n, _ in PERSONAS)
          and api_after["anonymous_cases_status"] == 401)
    check("restored: files downloaded through the API were hashed", all(api_after[n].get("downloaded_files", 0) > 0 for n, _ in PERSONAS if api_after[n].get("documents")))

    passed = all(c["ok"] for c in report["checks"])
    finish(d, passed)
    print("\nDRILL %s in %.1fs" % ("PASSED" if passed else "FAILED", time.monotonic() - clock.t0))
    return 0 if passed else 1


def persona_hash(password):
    try:
        import bcrypt
    except ImportError:
        raise StepFailed("the python 'bcrypt' module is needed to create the personas' password hashes (pip install bcrypt)")
    return "{bcrypt}" + bcrypt.hashpw(password.encode(), bcrypt.gensalt(4)).decode()


def summarise(fp):
    """The compact, printable form kept in the evidence file."""
    return {"tables": len(fp["tables"]), "rows_total": sum(t["rows"] for t in fp["tables"].values()),
            "non_empty_tables": sum(1 for t in fp["tables"].values() if t["rows"]),
            "rows_per_table": {k: v["rows"] for k, v in fp["tables"].items() if v["rows"]},
            "files": [{k: f[k] for k in ("kind", "files", "bytes", "hash_mismatch", "size_mismatch")} for f in fp["files"]],
            "foreign_keys": fp["foreign_keys"], "links": fp["links"], "version_history": fp["version_history"],
            "sequences": fp["sequences"], "structure": fp["structure"], "ownership": fp["ownership"], "flyway": fp["flyway"],
            "visibility": fp["visibility"]}


def strip_noise(fp):
    """The only exclusion: the raw index/constraint text digests, which differ cosmetically (see fingerprint())."""
    return {k: v for k, v in fp.items() if k != "structure_raw"}


if __name__ == "__main__":
    sys.exit(main())
