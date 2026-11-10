#!/usr/bin/env python3
"""LD-DOC r3: restore of an OLD backup, one taken before migration V800 (restore-safe trigram function).

V800 only changes the live database. A dump FILE taken earlier still carries the old definition of library_trigrams (no search_path
setting), and pg_restore (which empties the search path) cannot create library_judgment from it. This tool proves that with a
SYNTHETIC old-state backup and then proves the repair procedure of docs/operations/RESTORE-OLD-BACKUPS.md. It reuses the guards,
cluster handling, fingerprints and API reads of restore_drill.py (same refusals: only its own throwaway cluster, loopback, free
ports, database names lawdesk_drill_*; it never touches UAT, Production or any /srv volume).

Steps: own PostgreSQL 18 cluster; the real application migrates an empty database only up to --target (default 781, the last migration
before V800) and is stopped; SYNTHETIC data (seed.sql plus library judgments) is loaded; pg_dump -Fc; then
  (a) the dump is restored as it is: with --exit-on-error it stops, without it errors are ignored and library_judgment is missing;
  (b) EXPERIMENT: pg_restore options alone (database-level search_path, no repair) do not help;
  (c) the documented repair, a sed over `pg_restore -f -` that gives library_trigrams its own search_path, piped to psql, restores;
  (d) the role script is re-applied, the application boots on the restored database and Flyway applies V800 and later;
  (e) the same 15 checks of docs/evidence/DOC-RESTORE (adapted: the source is the reference and is migrated forward the same way).
Exit codes as restore_drill.py: 0 all checks passed, 1 a check failed, 2 refused, 3 a tool or step failed.
"""

import argparse
import datetime
import hashlib
import json
import os
import re
import subprocess
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import restore_drill as rd  # noqa: E402

SCHEMA = rd.SCHEMA
APP_ROLE = rd.APP_ROLE

# The documented repair: library_trigrams (V640) is a SQL function whose body calls library_normalize by its plain name. In the
# dump it is written without a search_path setting. Giving it one (what V800 does for the live database) stops PostgreSQL from
# inlining its body while CREATE TABLE library_judgment runs under pg_restore's empty search path.
SED_REPAIR = r"/^CREATE FUNCTION lawdesk\.library_trigrams(p_text text) RETURNS text\[\]$/a\    SET search_path TO 'lawdesk'"

LIBRARY_ROWS = """
insert into library_judgment (court_name, court_level, case_number, case_year, judgment_date, case_type, subject, keywords, summary,
                              source_reference, verification_note, created_by, updated_by)
select 'SYNTH-DOC court ' || g, 'APPEAL', 'SYN-' || g, 2025, date '2025-01-01' + g, 'CIVIL', 'SYNTH-DOC judgment subject ' || g,
       'SYNTH-DOC keyword ' || g, 'SYNTH-DOC summary ' || g, 'SYNTH-DOC source ' || g, 'SYNTH-DOC verified',
       '00000000-0000-4000-8000-0000000000d0', '00000000-0000-4000-8000-0000000000d0'
from generate_series(1, 40) g;
"""


def migrate_to(d, db, label, target):
    """Starts the real application with spring.flyway.target, waits for Flyway to finish, stops it. The application itself may refuse to
    finish booting on an old schema (Hibernate validation); the migrations are applied before that, which is all this step needs."""
    log_path = d.work / ("app-%s.log" % label)
    log = open(log_path, "wb")
    command = [d.args.java, "-Xmx512m", "-jar", d.args.jar, "--server.port=%d" % d.app_port, "--spring.datasource.url=" + d.jdbc(db),
               "--spring.datasource.username=" + APP_ROLE, "--spring.flyway.target=%d" % target,
               "--lawdesk.followup.schedule-enabled=false", "--lawdesk.ocr.schedule-enabled=false",
               "--server.servlet.session.cookie.secure=false"]
    env = dict(d.env)
    env["SPRING_DATASOURCE_PASSWORD"] = d.app_password
    process = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, env=env, start_new_session=True)
    d.processes.append(process)
    deadline = time.monotonic() + d.args.app_timeout
    outcome = None
    while time.monotonic() < deadline:
        text = log_path.read_text(errors="replace")
        if "Successfully applied" in text or "is up to date" in text:
            outcome = "flyway finished"
            break
        if process.poll() is not None:
            outcome = "application exited %s before Flyway reported" % process.returncode
            break
        time.sleep(1)
    time.sleep(2)
    d.stop_app(process)
    text = log_path.read_text(errors="replace")
    applied = re.search(r"Successfully applied (\d+) migrations?", text)
    return {"outcome": outcome, "migrations_applied": int(applied.group(1)) if applied else 0,
            "application_booted": "Started LawdeskApplication" in text or "Started " in text and " in " in text}


def execute(d, target):
    clock, args, report = d.clock, d.args, d.report
    report.update(started=datetime.datetime.now(datetime.timezone.utc).isoformat(timespec="seconds"), scale=args.scale,
                  target_version=target, client_version=d.tool("pg_dump", "--version").stdout.decode().strip(),
                  restore_version=d.tool("pg_restore", "--version").stdout.decode().strip())

    def check(name, ok, detail=""):
        report["checks"].append({"check": name, "ok": bool(ok), "detail": detail})
        print("%s %s %s" % ("PASS" if ok else "FAIL", name, detail), flush=True)

    clock.run("1 start the throwaway cluster", d.start_cluster)
    clock.run("2 create the database and the application role", lambda: (d.assert_own_cluster(d.src_db), d.apply_role_script(d.src_db)))
    migration = clock.run("3 the application migrates the empty database only up to V%d (the old state)" % target,
                          lambda: migrate_to(d, d.src_db, "migrate-old", target))
    state = d.rows(d.src_db, "select max(version::int) as latest, count(*) as applied from %s.flyway_schema_history where version is not null" % SCHEMA)[0]
    config = d.rows(d.src_db, "select proconfig::text as cfg from pg_proc p join pg_namespace n on n.oid = p.pronamespace "
                              "where n.nspname = '%s' and p.proname = 'library_trigrams'" % SCHEMA)[0]["cfg"]
    report["old_state"] = {"flyway": state, "library_trigrams_proconfig": config, "migration_step": migration}
    check("the source is in the pre-V800 state: no migration above V%d and library_trigrams has no search_path setting" % target,
          state["latest"] < 800 and config is None, "latest=%s applied=%s proconfig=%s" % (state["latest"], state["applied"], config))

    hash_value = rd.persona_hash(d.persona_password)
    clock.run("4 load SYNTHETIC data as the application role (seed.sql)", lambda: d.psql(
        d.src_db, file=HERE / "seed.sql", user=APP_ROLE, variables=(("scale", args.scale), ("pw_hash", hash_value)),
        options="-c search_path=%s" % SCHEMA))
    clock.run("4b load SYNTHETIC library judgments (the table the old dump cannot restore)", lambda: d.psql(d.src_db, LIBRARY_ROWS, user="postgres"))
    source_rows = int(d.scalar(d.src_db, "select count(*) from library_judgment"))
    report["library_judgment_rows_source"] = source_rows

    before = clock.run("5 fingerprint the OLD source database", lambda: d.fingerprint(d.src_db))
    report["fingerprint_before"] = rd.summarise(before)
    check("source: stored files match their SHA-256 column",
          all(f["hash_mismatch"] == 0 and f["size_mismatch"] == 0 for f in before["files"]) and before["files_without_bytes"] == "0",
          json.dumps([{k: f[k] for k in ("kind", "files", "bytes")} for f in before["files"]]))

    dump = d.work / "lawdesk_old_state.dump"

    def make_dump():
        with open(dump, "wb") as out:
            os.chmod(dump, 0o600)
            d.tool("pg_dump", "-h", str(d.socket), "-p", str(d.port), "-U", "postgres", "-d", d.src_db, "-Fc", stdout=out)
        return {"bytes": dump.stat().st_size}
    dump_info = clock.run("6 pg_dump -U postgres -d <db> -Fc of the OLD state", make_dump)
    report["measurements"] = {"dump_bytes": dump_info["bytes"], "dump_sha256": hashlib.sha256(dump.read_bytes()).hexdigest()}
    toc = d.tool("pg_restore", "--list", str(dump)).stdout.decode()
    trigram_entry = [l for l in toc.splitlines() if "library_trigrams" in l]
    report["measurements"]["dump_toc_entries"] = len([l for l in toc.splitlines() if l and not l.startswith(";")])
    report["library_trigrams_toc_entry"] = trigram_entry
    plain = d.work / "old_state_plain.sql"
    with open(plain, "wb") as out:
        d.tool("pg_restore", "-f", "-", str(dump), stdout=out)
    definition = re.search(r"CREATE FUNCTION lawdesk\.library_trigrams\(p_text text\).*?\$\$;", plain.read_text(errors="replace"), re.S)
    report["library_trigrams_in_dump"] = definition.group(0) if definition else None
    check("the old dump carries library_trigrams without a search_path setting",
          bool(definition) and "search_path" not in definition.group(0).lower(), "")

    pg_restore = ["-h", str(d.socket), "-p", str(d.port), "-U", "postgres"]

    # (a) restore as it is
    def as_is_strict():
        d.assert_own_cluster(d.fail_db)
        d.psql("postgres", "drop database if exists %s with (force)" % d.fail_db)
        d.psql("postgres", "create database %s" % d.fail_db)
        result = d.tool("pg_restore", *pg_restore, "-d", d.fail_db, "--exit-on-error", str(dump), check=False)
        tables = int(d.scalar(d.fail_db, "select count(*) from pg_tables where schemaname = '%s'" % SCHEMA))
        has = d.scalar(d.fail_db, "select to_regclass('%s.library_judgment') is not null" % SCHEMA)
        text = result.stderr.decode(errors="replace").strip()
        return {"rc": result.returncode, "stderr": text[:900], "names_missing_function": "function library_normalize(text) does not exist" in text,
                "tables_after": tables, "library_judgment_exists": has == "t"}
    strict = clock.run("7a pg_restore --exit-on-error of the old dump AS IT IS (expected to fail)", as_is_strict)
    report["as_is_exit_on_error"] = strict
    check("(a) as-is with --exit-on-error: stops with 'function library_normalize(text) does not exist'",
          strict["rc"] != 0 and strict["names_missing_function"] and not strict["library_judgment_exists"], strict["stderr"][:160])

    def as_is_lenient():
        d.psql("postgres", "drop database if exists %s with (force)" % d.fail_db)
        d.psql("postgres", "create database %s" % d.fail_db)
        result = d.tool("pg_restore", *pg_restore, "-d", d.fail_db, str(dump), check=False)
        text = result.stderr.decode(errors="replace")
        has = d.scalar(d.fail_db, "select to_regclass('%s.library_judgment') is not null" % SCHEMA)
        tables = int(d.scalar(d.fail_db, "select count(*) from pg_tables where schemaname = '%s'" % SCHEMA))
        ignored = re.search(r"errors ignored on restore: (\d+)", text)
        return {"rc": result.returncode, "errors_ignored": int(ignored.group(1)) if ignored else None, "tables_after": tables,
                "library_judgment_exists": has == "t", "stderr_head": text.strip()[:700]}
    lenient = clock.run("7b pg_restore WITHOUT --exit-on-error of the old dump as it is (degrades silently)", as_is_lenient)
    report["as_is_without_exit_on_error"] = lenient
    check("(a) as-is without --exit-on-error: exit 1, errors ignored, library_judgment missing",
          lenient["rc"] == 1 and (lenient["errors_ignored"] or 0) > 0 and not lenient["library_judgment_exists"],
          "errors ignored: %s, tables restored: %s" % (lenient["errors_ignored"], lenient["tables_after"]))
    d.psql("postgres", "drop database if exists %s with (force)" % d.fail_db)

    # (b) pg_restore options alone
    def options_only():
        d.psql("postgres", "create database %s" % d.fail_db)
        d.psql("postgres", "alter database %s set search_path = %s" % (d.fail_db, SCHEMA))
        result = d.tool("pg_restore", *pg_restore, "-d", d.fail_db, "--exit-on-error", str(dump), check=False)
        out = {"rc": result.returncode, "stderr": result.stderr.decode(errors="replace").strip()[-300:]}
        d.psql("postgres", "drop database %s with (force)" % d.fail_db)
        return out
    options = clock.run("7c EXPERIMENT: database-level search_path set before pg_restore (no repair of the dump)", options_only)
    report["experiment_database_search_path"] = options
    check("experiment: setting search_path on the database does not help (pg_restore overrides it per session)", options["rc"] != 0, options["stderr"][-160:])

    # (c) the documented repair: sed over the plain script
    repaired = d.work / "old_state_repaired.sql"

    def repair_and_restore():
        with open(plain, "rb") as src, open(repaired, "wb") as out:
            result = subprocess.run(["sed", SED_REPAIR], stdin=src, stdout=out, stderr=subprocess.PIPE)
        if result.returncode != 0:
            raise rd.StepFailed("sed failed: " + result.stderr.decode())
        changed = plain.read_bytes() != repaired.read_bytes()
        d.assert_own_cluster(d.dst_db)
        d.psql("postgres", "drop database if exists %s with (force)" % d.dst_db)
        d.psql("postgres", "create database %s" % d.dst_db)
        restored = d.tool("psql", "-X", "-q", "-v", "ON_ERROR_STOP=1", "-h", str(d.socket), "-p", str(d.port), "-U", "postgres",
                          "-d", d.dst_db, "-f", str(repaired), check=False)
        return {"sed_changed_the_script": changed, "rc": restored.returncode, "stderr": restored.stderr.decode(errors="replace").strip()[-500:]}
    repaired_run = clock.run("8 repair: pg_restore -f - | sed | psql ON_ERROR_STOP into an empty database", repair_and_restore)
    report["repair"] = repaired_run
    check("(b) the repaired script restores with exit code 0 and an empty stderr",
          repaired_run["sed_changed_the_script"] and repaired_run["rc"] == 0 and not repaired_run["stderr"], repaired_run["stderr"][:200])

    clock.run("9 re-apply deploy/postgres/app-role.sql on the restored database", lambda: d.apply_role_script(d.dst_db))
    clock.run("10 ANALYZE", lambda: d.psql(d.dst_db, "analyze"))
    after = clock.run("11 fingerprint the restored database (still pre-V800)", lambda: d.fingerprint(d.dst_db))
    report["fingerprint_after"] = rd.summarise(after)
    differences = rd.compare("db", rd.strip_noise(before), rd.strip_noise(after))
    report["differences_before_flyway"] = differences
    # The one expected difference: the repaired function definition carries the search_path setting. Anything else is a failure.
    unexpected = [x for x in differences if not x.startswith("structure.")]
    check("restored database is identical to the source (rows, content hashes, files, FKs, links, versions, grants, scopes, sequences, visibility); "
          "the only allowed difference is the repaired function text",
          not unexpected, "; ".join(differences[:4]))
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
    restored_rows = int(d.scalar(d.dst_db, "select count(*) from library_judgment"))
    check("restored: library_judgment is back with all %d synthetic rows (the table the old dump lost)" % source_rows, restored_rows == source_rows,
          "%d rows" % restored_rows)

    # Flyway applies V800 and later on the restored database; the source is migrated forward the same way as the reference.
    process = clock.run("12 boot the application on the restored database (Flyway applies V800 and later)", lambda: d.start_app(d.dst_db, "restored"))
    report["restored_boot"] = d.migration_summary("restored")
    api_after = clock.run("12b API fingerprint of the restored and migrated database", d.api_fingerprint)
    d.stop_app(process)
    process = clock.run("13 reference: boot the application on the OLD SOURCE database (the same forward migration)", lambda: d.start_app(d.src_db, "source"))
    report["source_boot"] = d.migration_summary("source")
    api_before = clock.run("13b API fingerprint of the migrated source", d.api_fingerprint)
    d.stop_app(process)
    check("restored: Flyway applied V800 and the later migrations, the schema validated and the application is healthy",
          report["restored_boot"]["migrations_applied"] > 0 and report["restored_boot"]["migrations_applied"] == report["source_boot"]["migrations_applied"],
          "restored applied %s, reference applied %s" % (report["restored_boot"]["migrations_applied"], report["source_boot"]["migrations_applied"]))
    final_cfg = d.rows(d.dst_db, "select proconfig::text as cfg from pg_proc p join pg_namespace n on n.oid = p.pronamespace "
                                 "where n.nspname = '%s' and p.proname = 'library_trigrams'" % SCHEMA)[0]["cfg"]
    check("after Flyway the restored library_trigrams carries the search_path setting (V800 applied on top of the repair)", final_cfg is not None and "search_path" in final_cfg, str(final_cfg))
    api_diffs = rd.compare("api", api_before, api_after)
    check("restored: the API answers (cases, documents, downloaded file hashes) are identical for the four personas", not api_diffs, "; ".join(api_diffs[:3]))
    check("restored: smoke reads worked", all(api_after[n].get("login_status") == 200 and api_after[n].get("cases", 0) > 0 for n, _ in rd.PERSONAS)
          and api_after["anonymous_cases_status"] == 401)
    check("restored: files downloaded through the API were hashed", all(api_after[n].get("downloaded_files", 0) > 0 for n, _ in rd.PERSONAS if api_after[n].get("documents")))
    final_src = d.fingerprint(d.src_db)
    final_dst = d.fingerprint(d.dst_db)
    # Rows written by every application boot differ in their timestamps between two boots and are compared by other means:
    # app_release (one row per boot), audit_event (boot/login rows) and flyway_schema_history (installed_on, execution_time).
    boot_noise = ("app_release", "audit_event", "flyway_schema_history")

    def without_boot_noise(fp):
        fp = rd.strip_noise(fp)
        fp["tables"] = {k: ({kk: vv for kk, vv in v.items() if kk != "digest"} if k in boot_noise else v) for k, v in fp["tables"].items()}
        return fp
    final_diffs = rd.compare("db", without_boot_noise(final_src), without_boot_noise(final_dst))
    history = "select version, description, checksum, success from %s.flyway_schema_history order by installed_rank"
    history_equal = d.rows(d.src_db, history % SCHEMA) == d.rows(d.dst_db, history % SCHEMA)
    report["differences_after_flyway"] = final_diffs
    report["flyway_history_equal_version_description_checksum"] = history_equal
    check("after Flyway the restored database equals the migrated reference in everything fingerprinted (rows and content hashes of every table, "
          "files, FKs, links, versions, grants, structure incl. functions; the digests of the three tables every boot rewrites are excluded, "
          "their row counts are compared and the Flyway history is compared by version, description and checksum)",
          not final_diffs and history_equal, "; ".join(final_diffs[:4]))
    report["api_after"] = api_after

    passed = all(c["ok"] for c in report["checks"])
    rd.finish(d, passed)
    print("\nOLD-BACKUP DRILL %s in %.1fs (%d checks)" % ("PASSED" if passed else "FAILED", time.monotonic() - clock.t0, len(report["checks"])))
    return 0 if passed else 1


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--workdir", required=True)
    parser.add_argument("--server-bin", required=True)
    parser.add_argument("--client-bin", required=True)
    parser.add_argument("--server-lib", default="")
    parser.add_argument("--client-lib", default="")
    parser.add_argument("--jar", required=True)
    parser.add_argument("--java", default=os.path.join(os.environ.get("JAVA_HOME", ""), "bin", "java") if os.environ.get("JAVA_HOME") else "java")
    parser.add_argument("--port", type=int, default=0)
    parser.add_argument("--app-port", type=int, default=0)
    parser.add_argument("--scale", type=int, default=1)
    parser.add_argument("--locale", default="C.UTF-8")
    parser.add_argument("--app-timeout", type=int, default=300)
    parser.add_argument("--download-sample", type=int, default=25)
    parser.add_argument("--target", type=int, default=781, help="last Flyway version applied to the old-state source (781 is the last migration below V800)")
    parser.add_argument("--evidence", default="")
    parser.add_argument("--keep", action="store_true")
    parser.set_defaults(skip_api=False)
    args = parser.parse_args(argv)
    if args.target >= 800:
        print("REFUSED: --target must be below 800 (the old state)", file=sys.stderr)
        return 2
    try:
        rd.check_environment(os.environ)
        rd.check_loopback("127.0.0.1")
        drill = rd.Drill(args)
    except rd.Refused as error:
        print("REFUSED: %s" % error, file=sys.stderr)
        return 2
    try:
        return execute(drill, args.target)
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
