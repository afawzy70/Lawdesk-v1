#!/usr/bin/env python3
"""One bounded UAT reproduction using a newly created, marked synthetic case."""

import json
import sys
from datetime import datetime, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "tools/journeys"))
from uat import MARK, Session  # noqa: E402


def require(status, expected, label):
    if status != expected:
        raise RuntimeError(f"{label}: HTTP {status}, expected {expected}")


def main():
    root = Path(__file__).resolve().parent
    marker = datetime.now(timezone.utc).strftime("%Y%m%d%H%M%S%f")
    manager = Session("manager").login("manager")
    staff = Session("staff").login("staff")
    status, release = manager.call("GET", "/api/v1/release")
    require(status, 200, "release before")
    before = release["release"]
    status, party = manager.call("POST", "/api/v1/parties", {
        "kind": "PERSON", "name": f"{MARK} unlink counsel {marker}",
        "confidential": False,
    })
    require(status, 201, "create marked party")
    _, users = manager.call("GET", "/api/v1/users?size=100&q=qa.")
    by_name = {user["username"]: user for user in users["items"]}
    _, courts = manager.call("GET", "/api/v1/courts?size=100")
    court = next(c for c in courts["items"] if c["level"] == "FIRST_INSTANCE" and c["active"])
    today = datetime.now(timezone.utc).date().isoformat()
    status, case = manager.call("POST", "/api/v1/cases/intake", {
        "automatedNo": f"QA-UNLINK-{marker}", "title": f"{MARK} unlink counsel {marker}",
        "caseType": "CIVIL", "subject": f"{MARK} unlink reproduction", "openedOn": today,
        "firstInstance": {"courtId": court["id"], "courtCaseNumber": marker[-9:],
                          "courtCaseYear": 2026, "filingDate": today},
        "authorityPosition": "DEFENDANT", "parties": [{"partyId": party["id"], "side": "PLAINTIFF"}],
        "primaryMemberId": by_name["qa.staff"]["id"],
        "alternateMemberId": by_name["qa.manager"]["id"], "financialImpact": "NO",
    })
    require(status, 201, "create marked case")
    status, counsel = staff.call("POST", f"/api/v1/cases/{case['id']}/parties/{party['id']}/counsel", {
        "kind": "LAWYER", "name": f"{MARK} counsel {marker}"})
    require(status, 201, "add counsel")
    status, _ = staff.call("DELETE", f"/api/v1/cases/{case['id']}/parties/{party['id']}")
    unlink_status = status
    status, counsel_list = staff.call("GET", f"/api/v1/cases/{case['id']}/parties/counsel")
    require(status, 200, "counsel list after")
    status, release = manager.call("GET", "/api/v1/release")
    require(status, 200, "release after")
    output = {
        "observedAtUtc": datetime.now(timezone.utc).isoformat(),
        "applicationShaBefore": before, "applicationShaAfter": release["release"],
        "role": "qa.staff", "browser": "API", "syntheticMarker": marker,
        "partyCreated": True, "caseCreated": True, "counselCreated": bool(counsel.get("id")),
        "unlinkHttpStatus": unlink_status, "expectedUnlinkHttpStatuses": [204, 409],
        "counselStillLinked": any(item.get("id") == counsel["id"] for item in counsel_list),
    }
    if before != release["release"]:
        output["state"] = "INVALIDATED_RELEASE_CHANGED"
    elif unlink_status in (204, 409):
        output["state"] = "PASS"
    else:
        output["state"] = "FAIL"
    path = root / f"party-unlink-counsel-{before[:7]}-result.json"
    path.write_text(json.dumps(output, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(output, ensure_ascii=False))


if __name__ == "__main__":
    main()
