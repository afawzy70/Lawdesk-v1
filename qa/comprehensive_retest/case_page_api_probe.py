#!/usr/bin/env python3
"""Sequential read-only controls for the browser case page's concurrent API failures."""

import json
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT.parents[1] / "tools/journeys"))
from uat import Session  # noqa: E402


def main():
    manager = Session("manager").login("manager")
    before = manager.call("GET", "/api/v1/release")[1]["release"]
    status, cases = manager.call("GET", "/api/v1/cases?q=SYNTH-DOC&size=1")
    assert status == 200 and cases["items"]
    case_id = cases["items"][0]["id"]
    paths = [
        f"/api/v1/cases/{case_id}", f"/api/v1/cases/{case_id}/summary",
        f"/api/v1/cases/{case_id}/documents", f"/api/v1/cases/{case_id}/parties",
        f"/api/v1/cases/{case_id}/hearings", f"/api/v1/cases/{case_id}/document-usage",
        "/api/v1/org-units", "/api/v1/users/directory", "/api/v1/archive/settings",
    ]
    statuses = {}
    for path in paths:
        status, _ = manager.call("GET", path)
        statuses[path.replace(case_id, "{caseId}")] = status
    after = manager.call("GET", "/api/v1/release")[1]["release"]
    result = {"observedAtUtc": datetime.now(timezone.utc).isoformat(),
              "applicationShaBefore": before, "applicationShaAfter": after,
              "role": "qa.manager", "browser": "API sequential", "syntheticCase": True,
              "statuses": statuses}
    (ROOT / f"case-page-sequential-{before[:7]}-result.json").write_text(
        json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps({"releaseChanged": before != after, "statuses": statuses}))


if __name__ == "__main__":
    main()
