#!/usr/bin/env python3
"""Read-only independent probe of the provider-free case brief on synthetic UAT data."""

import json
import os
import re
import sys
import urllib.parse
import uuid
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT / "tools/journeys"))
from uat import Session  # noqa: E402


def main():
    source = json.loads((HERE / "runs-aea33fa/wp2.json").read_text())
    run = re.search(r"\((\d+)\)$", source["journey"]).group(1)
    marker = "SYNTH-DOC-" + run
    staff = Session("staff").login("staff")
    viewer = Session("viewer").login("viewer")
    release_before = staff.call("GET", "/api/v1/release")[1]["release"]
    status, cases = staff.call("GET", "/api/v1/cases?" + urllib.parse.urlencode({"q": marker, "size": 10}))
    assert status == 200 and cases["items"], "synthetic case not found"
    case = next((c for c in cases["items"] if marker in c["title"]), cases["items"][0])
    path = f"/api/v1/cases/{case['id']}/brief"
    member_status, brief = staff.call("GET", path)
    outsider_status, _ = viewer.call("GET", path)
    outsider_bad_status, _ = viewer.call("GET", path + "?similarLimit=0")
    outsider_random_status, _ = viewer.call("GET", f"/api/v1/cases/{uuid.uuid4()}/brief?similarLimit=0")
    member_bad_status, _ = staff.call("GET", path + "?similarLimit=0")
    sections = ("timeline", "requests", "defenses", "facts", "evidence", "legalTexts", "linkedJudgments")
    stats = {}
    if member_status == 200:
        for name in sections:
            part = brief.get(name, {})
            items = part.get("items") or []
            stats[name] = {"status": part.get("status"), "items": len(items),
                           "withSourceId": sum(bool((x.get("source") or {}).get("id")) for x in items)}
    release_after = staff.call("GET", "/api/v1/release")[1]["release"]
    result = {
        "observedAtUtc": datetime.now(timezone.utc).isoformat(),
        "applicationShaBefore": release_before,
        "applicationShaAfter": release_after,
        "sourceCase": "wp2 synthetic case (run identifier omitted)",
        "staffBriefStatus": member_status,
        "origin": brief.get("origin") if member_status == 200 else None,
        "modelGenerated": brief.get("modelGenerated") if member_status == 200 else None,
        "hasNotice": bool(brief.get("notice")) if member_status == 200 else False,
        "sections": stats,
        "viewerKnownCaseStatus": outsider_status,
        "viewerHasCaseViewPermission": "CASE_VIEW" in viewer.me.get("permissions", []),
        "viewerInvalidKnownCaseStatus": outsider_bad_status,
        "viewerInvalidRandomCaseStatus": outsider_random_status,
        "staffInvalidCaseParameterStatus": member_bad_status,
    }
    expected_sha = os.environ.get("QA_EXPECTED_SHA")
    result["narrowPass"] = (release_before == release_after and (not expected_sha or release_before == expected_sha)
                            and member_status == 200 and result["origin"] == "RECORDS"
                            and result["modelGenerated"] is False and result["hasNotice"]
                            and stats.get("requests", {}).get("items", 0) >= 3
                            and result["viewerHasCaseViewPermission"]
                            and all(s == 404 for s in (outsider_status, outsider_bad_status, outsider_random_status))
                            and member_bad_status == 400)
    (HERE / f"brief-{release_before[:7]}-result.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps({"narrowPass": result["narrowPass"], "statuses": [member_status, outsider_status,
                       outsider_bad_status, outsider_random_status, member_bad_status],
                       "releaseChanged": release_before != release_after}))
    return 0 if result["narrowPass"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
