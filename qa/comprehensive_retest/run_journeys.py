#!/usr/bin/env python3
"""Run existing synthetic UAT journeys with release checks and redacted metadata."""

import json
import os
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT / "tools" / "journeys"))
from uat import Session  # noqa: E402

NAMES = ("wp1_access", "wp1c_parties", "wp1d_stages", "wp2", "wp3_followup", "wp4", "wp5", "wp6", "wp7", "wp8", "wp9", "wp10", "connected", "ocr")


def release():
    status, body = Session("manager").login("manager").call("GET", "/api/v1/release")
    return body.get("release") if status == 200 and isinstance(body, dict) else f"HTTP_{status}"


def run_one(name, out):
    before = release()
    evidence = out / f"{name}.json"
    env = dict(os.environ)
    env["PYTHONPATH"] = str(ROOT / "tools" / "journeys") + os.pathsep + str(HERE) + os.pathsep + env.get("PYTHONPATH", "")
    script = ROOT / "tools" / "journeys" / f"{name}_journey.py"
    if os.environ.get("QA_JOURNEY_PROFILE") == "03ff" and name == "wp1c_parties":
        script = HERE / "wp1c_parties_journey_03ff.py"
    command = [sys.executable, str(script), str(evidence)]
    try:
        proc = subprocess.run(command, cwd=ROOT, env=env, text=True, stdout=subprocess.PIPE,
                              stderr=subprocess.PIPE, timeout=420)
        exit_code = proc.returncode
        # Existing journeys print only synthetic fixture values and statuses, but logs
        # are kept out of the committed evidence until separately inspected.
        (out / f"{name}.log").write_text(proc.stdout + "\nSTDERR:\n" + proc.stderr)
    except subprocess.TimeoutExpired:
        exit_code = "TIMEOUT_420_SECONDS"
    after = release()
    data = json.loads(evidence.read_text()) if evidence.exists() else None
    steps = data.get("steps", []) if isinstance(data, dict) else []
    passed = sum(s.get("ok") is True for s in steps)
    failed = sum(s.get("ok") is False for s in steps)
    result = {
        "name": name,
        "observedAtUtc": datetime.now(timezone.utc).isoformat(),
        "applicationShaBefore": before,
        "applicationShaAfter": after,
        "qaSourceSha": os.environ.get("QA_SOURCE_SHA", "uncommitted"),
        "exitCode": exit_code,
        "assertionsPassed": passed,
        "assertionsFailed": failed,
        "state": "INVALIDATED_RELEASE_CHANGED" if before != after else
                 "FAIL" if exit_code != 0 or failed else "SCRIPT_PASS",
    }
    return result


def main():
    names = sys.argv[1:] or list(NAMES)
    invalid = set(names) - set(NAMES)
    if invalid:
        raise SystemExit(f"unknown journeys: {sorted(invalid)}")
    out = Path(os.environ.get("QA_RUN_DIR", str(HERE / "runs"))).resolve()
    if out.parent != HERE or not out.name.startswith("runs"):
        raise SystemExit("QA_RUN_DIR must name a runs directory directly under qa/comprehensive_retest")
    out.mkdir(exist_ok=True)
    summary_path = out / "run-summary.json"
    results = json.loads(summary_path.read_text()) if summary_path.exists() else []
    for name in names:
        result = run_one(name, out)
        results = [old for old in results if old["name"] != name] + [result]
        print(json.dumps(result, ensure_ascii=False), flush=True)
        summary_path.write_text(json.dumps(results, ensure_ascii=False, indent=2) + "\n")
    return 1 if any(r["state"] != "SCRIPT_PASS" for r in results if r["name"] in names) else 0


if __name__ == "__main__":
    raise SystemExit(main())
