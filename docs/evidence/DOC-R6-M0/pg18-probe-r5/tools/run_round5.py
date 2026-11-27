#!/usr/bin/env python3
"""Round-5 probe runner (documentation evidence, not a migration, never run against UAT).

Builds a DISPOSABLE PostgreSQL 18 cluster (trust auth, loopback only, new data directory, removed afterwards), applies the
repository's migrations as lawdesk_app, then a probe model, then one of:

  tests  --model r4|r5   regression suites (r4 = the reviewed Round-4 model: the attack tests are expected to FAIL)
  chain  --model r5      numbered probe files in order, each as its role, output on stdout
  qa     --model r4|r5   chain, then the independent QA Round-4 attack suite, UNMODIFIED (--qa-scripts DIR)
  locks  --model r5      chain, then the Round-4 Java lock scenarios (--jdbc-jar JAR --port 54329; JDK on PATH)

usage: PGBIN=<dir with initdb, pg_ctl> python3 run_round5.py <mode> --model r4|r5 [--qa-scripts DIR] [--port N]
"""
import argparse
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

import psycopg2

TOOLS = Path(__file__).resolve().parent
R5 = TOOLS.parent
R4 = R5.parent / "pg18-probe"
REPO = TOOLS.parents[4]
MIGRATIONS = REPO / "backend" / "src" / "main" / "resources" / "db" / "migration"


def r(name, user):
    return (user, (R5 if name[:3] == "r5-" else R4) / name[3 if name[:3] == "r5-" else 0:])


MODEL = {
    "r4": [r("05-synthetic-fixture.sql.txt", "lawdesk_app"), r("10-ownership-split.sql.txt", "postgres"),
           r("20-protected-objects-model.sql.txt", "lawdesk_owner"), r("25-link-fixture.sql.txt", "lawdesk_owner"),
           r("26a-role-permission-guard-setup.sql.txt", "lawdesk_owner"), r("40-referral-setup.sql.txt", "lawdesk_owner"),
           r("80-two-party-operator.sql.txt", "postgres"), r("80b-two-party-objects.sql.txt", "lawdesk_owner")],
    "r5": [r("05-synthetic-fixture.sql.txt", "lawdesk_app"), r("10-ownership-split.sql.txt", "postgres"),
           r("r5-12-operator-logins.sql.txt", "postgres"), r("r5-20-protected-model.sql.txt", "lawdesk_owner"),
           r("r5-22-identities-fixture.sql.txt", "lawdesk_owner"), r("r5-25-complaint-model.sql.txt", "lawdesk_owner"),
           r("26a-role-permission-guard-setup.sql.txt", "lawdesk_owner"), r("r5-40-referral-fixture.sql.txt", "lawdesk_owner")],
}

CHAIN = {
    "r4": [r("05-synthetic-fixture.sql.txt", "lawdesk_app"), r("10-ownership-split.sql.txt", "postgres"),
           r("20-protected-objects-model.sql.txt", "lawdesk_owner"), r("25-link-fixture.sql.txt", "lawdesk_owner"),
           r("26a-role-permission-guard-setup.sql.txt", "lawdesk_owner"), r("26b-role-permission-guard-runtime.sql.txt", "lawdesk_app"),
           r("30-runtime-role-attacks.sql.txt", "lawdesk_app"), r("31-legal-role.sql.txt", "lawdesk_legal"),
           r("32-operator-role.sql.txt", "lawdesk_operator"), r("33-legal-role-after-tier-b.sql.txt", "lawdesk_legal"),
           r("34-runtime-after.sql.txt", "lawdesk_app"), r("40-referral-setup.sql.txt", "lawdesk_owner"),
           r("41-referral-contradiction.sql.txt", "lawdesk_owner"), r("42-referral-sequence.sql.txt", "lawdesk_legal"),
           r("43-operator-policy-on.sql.txt", "lawdesk_operator"), r("44-referral-sequence-policy-on.sql.txt", "lawdesk_legal"),
           r("45-invariant-at-commit.sql.txt", "lawdesk_owner"), r("50-integrity-setup.sql.txt", "lawdesk_owner"),
           r("51-integrity-tests.sql.txt", "lawdesk_owner"), r("52-integrity-runtime.sql.txt", "lawdesk_app"),
           r("53-legacy-unlinked-runtime.sql.txt", "lawdesk_app")],
    "r5": MODEL["r5"][:6] + [
        r("26a-role-permission-guard-setup.sql.txt", "lawdesk_owner"), r("26b-role-permission-guard-runtime.sql.txt", "lawdesk_app"),
        r("30-runtime-role-attacks.sql.txt", "lawdesk_app"), r("31-legal-role.sql.txt", "lawdesk_legal"),
        r("r5-32-two-operator-facts.sql.txt", "op_alice+op_bob"), r("33-legal-role-after-tier-b.sql.txt", "lawdesk_legal"),
        r("34-runtime-after.sql.txt", "lawdesk_app"), r("r5-40-referral-fixture.sql.txt", "lawdesk_owner"),
        r("r5-42-referral-sequence.sql.txt", "lawdesk_legal"), r("45-invariant-at-commit.sql.txt", "lawdesk_owner"),
        r("50-integrity-setup.sql.txt", "lawdesk_owner"), r("51-integrity-tests.sql.txt", "lawdesk_owner"),
        r("52-integrity-runtime.sql.txt", "lawdesk_app"), r("53-legacy-unlinked-runtime.sql.txt", "lawdesk_app"),
        r("70-definer-hygiene-setup.sql.txt", "lawdesk_owner"), r("71-definer-hygiene-run.sql.txt", "lawdesk_app"),
        r("90-digest.sql.txt", "lawdesk_owner")],
}


def connect(port, user, db="lawdesk", **kw):
    c = psycopg2.connect(host="127.0.0.1", port=port, user=user, dbname=db, **kw)
    c.autocommit = True
    return c


def statements(path):
    return [s.strip() for s in re.split(r"(?m)^;;\s*$", path.read_text()) if s.strip()]


def execute_file(port, user, path, out):
    out.write("##### %s as %s\n" % (path.name, user))
    # "op_alice+op_bob": a file may switch the session with a first comment line "-- @login <name>" per statement.
    sessions = {}
    default = user.split("+")[0]
    for text in statements(path):
        m = re.match(r"-- @login (\w+)", text)
        login = m.group(1) if m else default
        label = text.splitlines()[0]
        try:
            if login not in sessions:
                sessions[login] = connect(port, login)
            cur = sessions[login].cursor()
            cur.execute(text)
            if cur.description:
                out.write("OK   %s\n     -> %s\n" % (label, " || ".join(" | ".join(str(x) for x in row) for row in cur.fetchall())))
            else:
                out.write("OK   %s\n" % label)
        except psycopg2.Error as e:
            out.write("DENY %s\n     -> %s %s\n" % (label, e.pgcode, str(e).splitlines()[0]))
            if login in sessions and not sessions[login].closed:
                sessions[login].cursor().execute("rollback")
    for c in sessions.values():
        c.close()
    out.flush()


def bootstrap(port):
    """The CURRENT deployment model, as deploy/postgres/app-role.sql creates it."""
    c = connect(port, "postgres", "postgres").cursor()
    c.execute("create database lawdesk")
    c.execute("create role lawdesk_app login nosuperuser nocreatedb nocreaterole noreplication nobypassrls")
    c = connect(port, "postgres").cursor()
    for s in ("revoke all on database lawdesk from public", "grant connect, temporary on database lawdesk to lawdesk_app",
              "revoke all on schema public from public", "create schema lawdesk authorization lawdesk_app",
              "alter role lawdesk_app set search_path = lawdesk"):
        c.execute(s)
    files = sorted(MIGRATIONS.glob("V*.sql"), key=lambda f: [int(x) for x in re.match(r"V([\d.]+)__", f.name).group(1).split(".")])
    c = psycopg2.connect(host="127.0.0.1", port=port, user="lawdesk_app", dbname="lawdesk", options="-c search_path=lawdesk")
    for f in files:
        c.cursor().execute(f.read_text())
        c.commit()
    c.close()
    print("applied %d migrations as lawdesk_app" % len(files), flush=True)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("mode", choices=["tests", "chain", "qa", "locks"])
    ap.add_argument("--model", choices=["r4", "r5"], required=True)
    ap.add_argument("--qa-scripts")
    ap.add_argument("--port", type=int, default=55611)
    ap.add_argument("--pattern", default="test_*.py")
    ap.add_argument("--jdbc-jar", help="pgjdbc jar, for the locks mode")
    a = ap.parse_args()
    pgbin = Path(os.environ["PGBIN"])
    work = Path(tempfile.mkdtemp(prefix="r6-m0-pg-"))
    data = work / "data"
    subprocess.run([pgbin / "initdb", "-D", data, "-U", "postgres", "-A", "trust", "-E", "UTF8", "--locale=C"],
                   check=True, stdout=subprocess.DEVNULL)
    subprocess.run([pgbin / "pg_ctl", "-D", data, "-w", "-l", work / "server.log", "-o",
                    "-p %d -h 127.0.0.1 -c unix_socket_directories=''" % a.port, "start"], check=True, stdout=subprocess.DEVNULL)
    code = 1
    try:
        bootstrap(a.port)
        c = connect(a.port, "postgres").cursor()
        c.execute("show server_version")
        print("server_version", c.fetchone()[0], "| model", a.model, "| mode", a.mode, flush=True)
        if a.mode == "tests":
            log = work / "setup.out"
            with log.open("w") as out:
                for user, path in MODEL[a.model]:
                    execute_file(a.port, user, path, out)
            denied = [ln for ln in log.read_text().splitlines() if ln.startswith("DENY")]
            print("model setup refusals (the two intended ones: owner attaching a sensitive role code, R4's old-design probe):")
            for ln in denied:
                print("  ", ln)
            env = dict(os.environ, PGP=str(a.port), PYTHONDONTWRITEBYTECODE="1")
            code = subprocess.run([sys.executable, "-m", "unittest", "discover", "-v", "-s", str(TOOLS), "-p", a.pattern],
                                  env=env, cwd=TOOLS).returncode
        else:
            for user, path in CHAIN[a.model]:
                execute_file(a.port, user, path, sys.stdout)
            if a.mode == "qa":
                scripts = Path(a.qa_scripts).resolve()
                probe = work / "probe"
                probe.mkdir()
                # The QA suite reads 80 and 80b from the probe directory it is given.
                src = R4 if a.model == "r4" else R5
                for name in ("80-two-party-operator.sql.txt", "80b-two-party-objects.sql.txt"):
                    shutil.copy(src / name, probe / name)
                env = dict(os.environ, PGP=str(a.port), PY=sys.executable, E=str(probe), PYTHONDONTWRITEBYTECODE="1")
                print("\n########## independent QA Round-4 attack suite (unmodified) ##########", flush=True)
                subprocess.run(["bash", "attack_suite.sh"], cwd=scripts, env=env, check=False)
            if a.mode == "locks":
                # the author's Round-4 concurrency tool (pg18-probe/tools/Lock.java); it connects to port 54329
                out = work / "classes"
                subprocess.run(["javac", "-cp", a.jdbc_jar, "-d", out, R4 / "tools" / "Lock.java"], check=True)
                print("\n########## Lock.java scenarios ##########", flush=True)
                for scenario in ("inverted", "unified", "phantom", "closure"):
                    subprocess.run(["java", "-cp", "%s:%s" % (out, a.jdbc_jar), "Lock", scenario], check=True)
            code = 0
    finally:
        subprocess.run([pgbin / "pg_ctl", "-D", data, "-w", "-m", "immediate", "stop"], check=False, stdout=subprocess.DEVNULL)
        shutil.rmtree(work, ignore_errors=True)
    return code


if __name__ == "__main__":
    sys.exit(main())
