#!/usr/bin/env python3
"""Compare only the marked throwaway source and recovered old-dump databases."""

import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
from types import SimpleNamespace

ROOT = Path(__file__).resolve().parents[2]
DRILL = ROOT / "tools/restore-drill/restore_drill.py"


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--workdir", required=True)
    p.add_argument("--port", required=True, type=int)
    p.add_argument("--tools-dir", required=True)
    p.add_argument("--recovered-db", default="lawdesk_drill_old_recovered")
    p.add_argument("--dump-name", default="pre-v800-simulated.dump")
    p.add_argument("--failed-restore-stderr", default="pre-v800-restore.stderr")
    p.add_argument("--recovery-stderr", default="pre-v800-recovery.stderr")
    p.add_argument("--out", required=True)
    args = p.parse_args()
    work = Path(args.workdir).resolve()
    if not (work / "LAWDESK_DRILL_THROWAWAY").is_file() or not (work / "pgdata/LAWDESK_DRILL_THROWAWAY").is_file():
        raise SystemExit("refusing database without the restore-drill throwaway markers")
    spec = importlib.util.spec_from_file_location("qa_restore_drill", DRILL)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    base = Path(args.tools_dir).resolve()
    drill = module.Drill(SimpleNamespace(
        workdir=str(work), port=args.port, app_port=55492,
        server_bin=str(base / "server/bin"), server_lib=str(base / "server/lib"),
        client_bin=str(base / "client/usr/lib/postgresql/18/bin"),
        client_lib=str(base / "client/usr/lib/x86_64-linux-gnu"),
        download_sample=0,
    ))
    drill.assert_own_cluster("lawdesk_drill_src")
    old = drill.fingerprint("lawdesk_drill_src")
    recovered_db = module.check_database_name(args.recovered_db)
    recovered = drill.fingerprint(recovered_db)
    keys = ("tables", "files", "files_without_bytes", "version_history", "foreign_keys", "links",
            "access_rows", "sequences", "flyway", "visibility")
    diffs = module.compare("old-backup-recovery", {k: old[k] for k in keys}, {k: recovered[k] for k in keys})
    old_dump = work / args.dump_name
    error = (work / args.failed_restore_stderr).read_text(errors="replace")
    setting = drill.rows(recovered_db, """
        select p.proconfig from pg_proc p join pg_namespace n on n.oid = p.pronamespace
        where n.nspname = 'lawdesk' and p.proname = 'library_trigrams'
    """)[0]["proconfig"]
    result = {
        "applicationSourceSha": "352277571426bd4d135ea4acb0e12489aa09fc3c",
        "oldDumpOrigin": "simulated from the isolated scale-1 source by resetting only library_trigrams search_path",
        "oldDumpSha256": hashlib.file_digest(old_dump.open("rb"), "sha256").hexdigest(),
        "oldCustomRestore": "FAILED_AS_EXPECTED" if "function library_normalize(text) does not exist" in error else "UNEXPECTED_ERROR",
        "patchedPlainSqlRestore": "PASS" if not (work / args.recovery_stderr).read_text().strip() else "HAS_STDERR",
        "recoveredFunctionConfig": setting,
        "fingerprintFieldsCompared": list(keys),
        "fingerprintDifferences": diffs,
        "sourceCounts": {k: old["tables"][k]["rows"] for k in ("legal_case", "case_document", "document_version", "library_judgment")},
        "recoveredCounts": {k: recovered["tables"][k]["rows"] for k in ("legal_case", "case_document", "document_version", "library_judgment")},
        "limitation": "The dump is a controlled simulation of the pre-V800 function state, not a historical backup file; real old backups still need a guarded operator procedure."
    }
    Path(args.out).write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps({"oldRestore": result["oldCustomRestore"], "patchedRestore": result["patchedPlainSqlRestore"],
                      "differences": len(diffs)}))
    if result["oldCustomRestore"] != "FAILED_AS_EXPECTED" or result["patchedPlainSqlRestore"] != "PASS" or diffs:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
