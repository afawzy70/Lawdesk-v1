#!/usr/bin/env python3
"""Read-only UAT probe: can an outsider discover a party on one hidden synthetic case?"""

import json
import os
import re
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT.parents[1] / "tools/journeys"))
from uat import Session  # noqa: E402


def main():
    run = json.loads((ROOT / "runs-514c481/wp1c_parties.json").read_text())
    marker = re.search(r"\((\d+)\)", run["journey"]).group(1)
    manager = Session("manager").login("manager")
    viewer = Session("viewer").login("viewer")
    status, release = manager.call("GET", "/api/v1/release")
    assert status == 200
    before = release["release"]
    expected_sha = os.environ.get("QA_EXPECTED_SHA", "514c481c02673f29a39601a6b22694d5e0427029")
    assert before == expected_sha, before
    status, own = manager.call("GET", f"/api/v1/parties?q={marker}")
    assert status == 200 and own["totalItems"] == 1
    party_id = own["items"][0]["id"]
    status, outsider_list = viewer.call("GET", f"/api/v1/parties?q={marker}")
    assert status == 200
    by_id_status, outsider = viewer.call("GET", f"/api/v1/parties/{party_id}")
    cases_status, outsider_cases = viewer.call("GET", f"/api/v1/parties/{party_id}/cases")
    after_status, after = manager.call("GET", "/api/v1/release")
    assert after_status == 200
    result = {
        "observedAtUtc": datetime.now(timezone.utc).isoformat(),
        "applicationShaBefore": before, "applicationShaAfter": after["release"],
        "role": "qa.viewer", "browser": "API", "syntheticMarker": marker,
        "managerFindsOnlyOneMarkedParty": True,
        "outsiderSearchTotal": outsider_list["totalItems"],
        "outsiderGetStatus": by_id_status,
        "outsiderSeesName": bool(outsider and outsider.get("name")),
        "outsiderCaseListStatus": cases_status,
        "outsiderCaseCount": len(outsider_cases) if isinstance(outsider_cases, list) else None,
        "state": "INVALIDATED_RELEASE_CHANGED" if before != after["release"] else "OBSERVED",
    }
    (ROOT / f"party-directory-scope-{before[:7]}-result.json").write_text(
        json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(result, ensure_ascii=False))


if __name__ == "__main__":
    main()
