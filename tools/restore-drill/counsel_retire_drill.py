#!/usr/bin/env python3
"""LD-DOC r5: what migration V880 does to existing «المحامون والوكلاء» rows, and how to get them back.

V880 only RENAMES case_party_counsel to case_party_counsel_retired (the irreversible drop needs explicit owner approval and is not part of
this tree). This drill proves, on SYNTHETIC data in its own throwaway PostgreSQL cluster (same guards as restore_drill.py: only its own
cluster, loopback, database names lawdesk_drill_*; it never touches UAT, Production or any /srv volume):
  1. the application migrates a database up to V879, SYNTHETIC data and counsel rows (active and removed) are loaded;
  2. the table is counted and a pg_dump -Fc backup is taken and VERIFIED by restoring it into a side database (row-for-row equal);
  3. the application boots on the source and Flyway applies V880 (and anything after it): rows kept, table renamed, nothing else changed;
  4. a second boot applies nothing (start-up is repeatable);
  5. recovery path A: renaming the table back restores the old name with every row; recovery path B: the verified backup restores them
     into a new database; both counts equal the count taken before V880.
Exit codes as restore_drill.py: 0 all checks passed, 1 a check failed, 2 refused, 3 a tool or step failed.
"""

import argparse
import hashlib
import json
import os
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import restore_drill as rd  # noqa: E402
import restore_old_backup as old  # noqa: E402

SCHEMA = rd.SCHEMA
APP_ROLE = rd.APP_ROLE

INSERT_COUNSEL = """
insert into case_party_counsel (case_id, party_id, kind, name, phone, created_by, removed_at, removed_by)
select cp.case_id, cp.party_id, case when n % 2 = 0 then 'LAWYER' else 'AGENT' end, 'SYNTH-COUNSEL ' || n, '+965 5000 ' || lpad(n::text, 4, '0'),
       (select created_by from case_party limit 1),
       case when n % 5 = 0 then now() end, case when n % 5 = 0 then (select created_by from case_party limit 1) end
from (select case_id, party_id, row_number() over () as n from case_party) cp
"""


def execute(d, target):
    clock, args, report = d.clock, d.args, d.report
    report.update(target_version=target, client_version=d.tool("pg_dump", "--version").stdout.decode().strip())

    def check(name, ok, detail=""):
        report["checks"].append({"check": name, "ok": bool(ok), "detail": detail})
        print("%s %s %s" % ("PASS" if ok else "FAIL", name, detail), flush=True)

    def counsel(db, table):
        row = d.rows(db, "select count(*) as total, count(*) filter (where removed_at is null) as active, "
                         "coalesce(md5(string_agg(id::text || name || kind, ',' order by id)), '') as digest from %s" % table)[0]
        return row

    def untouched(db):
        return d.rows(db, "select (select count(*) from legal_case) as cases, (select count(*) from party) as parties, "
                          "(select count(*) from case_party) as case_parties, (select count(*) from audit_event) as audit")[0]

    clock.run("1 start the throwaway cluster", d.start_cluster)
    clock.run("2 create the database and the application role", lambda: (d.assert_own_cluster(d.src_db), d.apply_role_script(d.src_db)))
    clock.run("3 the application migrates the empty database only up to V%d" % target, lambda: old.migrate_to(d, d.src_db, "migrate-old", target))
    state = d.rows(d.src_db, "select max(version::int) as latest from %s.flyway_schema_history where version is not null" % SCHEMA)[0]
    check("the source is below V880 and still has case_party_counsel", state["latest"] < 880 and
          d.scalar(d.src_db, "select to_regclass('%s.case_party_counsel') is not null" % SCHEMA) == "t", "latest=%s" % state["latest"])
    hash_value = rd.persona_hash(d.persona_password)
    clock.run("4 load SYNTHETIC data (seed.sql)", lambda: d.psql(d.src_db, file=HERE / "seed.sql", user=APP_ROLE,
                                                                  variables=(("scale", args.scale), ("pw_hash", hash_value)),
                                                                  options="-c search_path=%s" % SCHEMA))
    clock.run("4b load SYNTHETIC counsel rows (some marked removed)", lambda: d.psql(d.src_db, INSERT_COUNSEL, user="postgres"))
    before = counsel(d.src_db, "case_party_counsel")
    other_before = untouched(d.src_db)
    report["counsel_before"] = before
    report["other_rows_before"] = other_before
    check("synthetic counsel rows exist (active and removed)", before["total"] > 0 and 0 < before["active"] < before["total"], json.dumps(before))

    dump = d.work / "before_v880.dump"

    def make_dump():
        with open(dump, "wb") as out:
            os.chmod(dump, 0o600)
            d.tool("pg_dump", "-h", str(d.socket), "-p", str(d.port), "-U", "postgres", "-d", d.src_db, "-Fc", stdout=out)
        return {"bytes": dump.stat().st_size, "sha256": hashlib.sha256(dump.read_bytes()).hexdigest()}
    report["backup"] = clock.run("5 backup: pg_dump -Fc of the source before V880", make_dump)
    toc = d.tool("pg_restore", "--list", str(dump)).stdout.decode()
    check("the backup lists the counsel table and its data", "case_party_counsel" in toc and "TABLE DATA" in toc, "")

    def verify_backup():
        d.create_database(d.dst_db)
        result = d.tool("pg_restore", "--exit-on-error", "--no-owner", "-h", str(d.socket), "-p", str(d.port), "-U", "postgres",
                        "-d", d.dst_db, str(dump), check=False)
        return {"rc": result.returncode, "stderr": result.stderr.decode(errors="replace")[:400]}
    verified = clock.run("6 verify the backup: restore it into a side database (--exit-on-error)", verify_backup)
    restored = counsel(d.dst_db, "case_party_counsel")
    check("the restored backup holds exactly the counsel rows counted before V880", verified["rc"] == 0 and restored == before,
          "rc=%s restored=%s" % (verified["rc"], json.dumps(restored)))

    process = clock.run("7 boot the application on the source: Flyway applies V880", lambda: d.start_app(d.src_db, "after"))
    d.stop_app(process)
    summary = d.migration_summary("after")
    report["boot_after"] = summary
    latest = d.rows(d.src_db, "select max(version::int) as latest, bool_and(success) as ok from %s.flyway_schema_history where version is not null" % SCHEMA)[0]
    check("Flyway applied V880 and every later migration successfully", latest["latest"] >= 880 and latest["ok"], json.dumps(latest))
    after = counsel(d.src_db, "case_party_counsel_retired")
    check("V880 kept every row: the renamed table equals the table counted before", after == before, "after=%s" % json.dumps(after))
    check("the old table name no longer exists", d.scalar(d.src_db, "select to_regclass('%s.case_party_counsel') is null" % SCHEMA) == "t", "")
    check("nothing else changed (cases, parties, links, audit)", untouched(d.src_db) == other_before,
          "before=%s after=%s" % (json.dumps(other_before), json.dumps(untouched(d.src_db))))
    notice = (d.work / "app-after.log").read_text(errors="replace")
    check("the migration reported the row count in the log", "LD-DOC-r5: retiring case_party_counsel" in notice,
          [l for l in notice.splitlines() if "LD-DOC-r5" in l][:1])

    process = clock.run("8 second boot: start-up is repeatable", lambda: d.start_app(d.src_db, "again"))
    d.stop_app(process)
    again = d.migration_summary("again")
    report["boot_again"] = again
    check("the second start-up applies no migration", again["migrations_applied"] == 0, json.dumps(again))

    clock.run("9 recovery A: rename the table back", lambda: d.psql(d.src_db, "alter table case_party_counsel_retired rename to case_party_counsel"))
    check("recovery A: the old name holds every row again", counsel(d.src_db, "case_party_counsel") == before, "")
    d.psql(d.src_db, "alter table case_party_counsel rename to case_party_counsel_retired")
    check("recovery B: the verified backup still holds them (side database equals the count before)", restored == before, "")
    return 0 if all(c["ok"] for c in report["checks"]) else 1


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    for name in ("--workdir", "--server-bin", "--client-bin", "--jar"):
        parser.add_argument(name, required=True)
    parser.add_argument("--server-lib", default="")
    parser.add_argument("--client-lib", default="")
    parser.add_argument("--java", default=os.path.join(os.environ.get("JAVA_HOME", ""), "bin", "java") if os.environ.get("JAVA_HOME") else "java")
    parser.add_argument("--port", type=int, default=0)
    parser.add_argument("--app-port", type=int, default=0)
    parser.add_argument("--scale", type=int, default=1)
    parser.add_argument("--locale", default="C.UTF-8")
    parser.add_argument("--app-timeout", type=int, default=300)
    parser.add_argument("--download-sample", type=int, default=0)
    parser.add_argument("--target", type=int, default=879)
    parser.add_argument("--evidence", default="")
    parser.add_argument("--keep", action="store_true")
    args = parser.parse_args(argv)
    if args.target >= 880:
        print("REFUSED: --target must be below 880", file=sys.stderr)
        return 2
    try:
        rd.check_environment(os.environ)
        rd.check_loopback("127.0.0.1")
        drill = rd.Drill(args)
    except rd.Refused as error:
        print("REFUSED: %s" % error, file=sys.stderr)
        return 2
    try:
        code = execute(drill, args.target)
        rd.finish(drill, code == 0)
        return code
    except rd.Refused as error:
        print("REFUSED: %s" % error, file=sys.stderr)
        return 2
    except (rd.StepFailed, OSError, subprocess.SubprocessError) as error:
        print("DRILL STEP FAILED: %s" % error, file=sys.stderr)
        rd.finish(drill, False)
        return 3
    finally:
        for process in drill.processes:
            drill.stop_app(process)
        if not args.keep:
            drill.stop_cluster()


if __name__ == "__main__":
    sys.exit(main())
