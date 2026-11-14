#!/usr/bin/env python3
"""LD-DOC r5: runs the journeys against a LOCAL stack: the real application jar on its own throw-away PostgreSQL cluster (same guards
as tools/restore-drill/restore_drill.py: only its own cluster, loopback, database names lawdesk_drill_*). Nothing touches UAT.

Steps: cluster + application role; the application migrates the empty database (every Flyway migration of the tree) and starts;
a local ADMIN user is inserted; the REAL deploy/qa/qa_accounts.py `provision | receive` creates qa.admin/manager/staff/viewer with
their roles and verifies each role (the same procedure the operator runs on UAT); then the given journeys run with LAWDESK_BASE
and LAWDESK_QA_DIR pointing at the local stack. Every journey writes its JSON next to --out. Exit 0 when all journeys passed.
"""

import argparse
import os
import secrets
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
REPO = HERE.parent.parent
sys.path.insert(0, str(REPO / "tools" / "restore-drill"))
import restore_drill as rd  # noqa: E402


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    for name in ("--workdir", "--server-bin", "--client-bin", "--jar", "--out"):
        parser.add_argument(name, required=True)
    parser.add_argument("--server-lib", default="")
    parser.add_argument("--client-lib", default="")
    parser.add_argument("--java", default=os.path.join(os.environ.get("JAVA_HOME", ""), "bin", "java") if os.environ.get("JAVA_HOME") else "java")
    parser.add_argument("--port", type=int, default=0)
    parser.add_argument("--app-port", type=int, default=0)
    parser.add_argument("--locale", default="C.UTF-8")
    parser.add_argument("--app-timeout", type=int, default=300)
    parser.add_argument("--scale", type=int, default=1)
    parser.add_argument("--download-sample", type=int, default=0)
    parser.add_argument("--evidence", default="")
    parser.add_argument("--keep", action="store_true")
    parser.add_argument("journeys", nargs="+", help="journey file names under tools/journeys, for example r5_integrated_journey.py")
    args = parser.parse_args(argv)
    rd.check_environment(os.environ)
    rd.check_loopback("127.0.0.1")
    d = rd.Drill(args)
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    results = {}
    try:
        d.start_cluster()
        d.assert_own_cluster(d.src_db)
        d.create_database(d.src_db)
        d.apply_role_script(d.src_db)
        app = d.start_app(d.src_db, "journeys")
        base = "http://127.0.0.1:%d" % d.app_port
        admin_password = secrets.token_urlsafe(18)
        d.psql(d.src_db, "insert into app_user (username, email, display_name, password_hash, created_by, updated_by) "
                         "values ('localadmin', 'localadmin@example.invalid', 'SYNTH local admin', '%s', gen_random_uuid(), gen_random_uuid())"
               % rd.persona_hash(admin_password), user=rd.APP_ROLE)
        d.psql(d.src_db, "insert into user_role (user_id, role_id) select u.id, r.id from app_user u, app_role r "
                         "where u.username = 'localadmin' and r.code = 'ADMIN'", user=rd.APP_ROLE)
        qa_dir = d.work / "qa-credentials"
        qa_dir.mkdir(mode=0o700, exist_ok=True)
        pw_file = d.work / "admin.pw"
        pw_file.write_text(admin_password)
        pw_file.chmod(0o600)
        tool = REPO / "deploy" / "qa" / "qa_accounts.py"
        provision = subprocess.Popen([sys.executable, "-I", str(tool), "provision", "--base-url", base, "--admin-user", "localadmin",
                                      "--admin-password-file", str(pw_file)], stdout=subprocess.PIPE)
        receive = subprocess.run([sys.executable, "-I", str(tool), "receive", "--dir", str(qa_dir)], stdin=provision.stdout,
                                 stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        provision.stdout.close()
        provision.wait()
        print("provision exit=%s receive exit=%s" % (provision.returncode, receive.returncode), flush=True)
        if provision.returncode != 0 or receive.returncode != 0:
            print(receive.stderr.decode(errors="replace")[-500:], file=sys.stderr)
            return 3
        verify = subprocess.run([sys.executable, "-I", str(tool), "verify", "--dir", str(qa_dir), "--base-url", base],
                                stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        (out / "qa-accounts-verify.txt").write_bytes(verify.stdout)
        print(verify.stdout.decode(errors="replace"), flush=True)
        env = dict(os.environ, LAWDESK_BASE=base, LAWDESK_QA_DIR=str(qa_dir))
        for name in args.journeys:
            target = out / (Path(name).stem + ".json")
            log = out / (Path(name).stem + ".log")
            with open(log, "wb") as handle:
                result = subprocess.run([sys.executable, "-I", str(HERE / name), str(target)], env=env, stdout=handle,
                                        stderr=subprocess.STDOUT, timeout=1500)
            text = log.read_text(errors="replace")
            results[name] = {"exit": result.returncode, "pass": text.count("\nPASS ") + (1 if text.startswith("PASS ") else 0),
                             "fail": text.count("\nFAIL ") + (1 if text.startswith("FAIL ") else 0)}
            print("%s exit=%d pass=%d fail=%d" % (name, result.returncode, results[name]["pass"], results[name]["fail"]), flush=True)
        d.stop_app(app)
    finally:
        for process in d.processes:
            d.stop_app(process)
        if not args.keep:
            d.stop_cluster()
    (out / "SUMMARY.txt").write_text("\n".join("%s exit=%d pass=%d fail=%d" % (k, v["exit"], v["pass"], v["fail"]) for k, v in results.items()) + "\n")
    return 0 if results and all(v["exit"] == 0 for v in results.values()) else 1


if __name__ == "__main__":
    sys.exit(main())
