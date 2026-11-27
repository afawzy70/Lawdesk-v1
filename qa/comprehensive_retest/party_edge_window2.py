#!/usr/bin/env python3
"""Two synthetic UAT party edges. Save statuses only; never expose identifiers or credentials."""
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
    marker = datetime.now(timezone.utc).strftime("%Y%m%d%H%M%S%f")
    manager = Session("manager").login("manager")
    staff = Session("staff").login("staff")
    viewer = Session("viewer").login("viewer")
    status, release = manager.call("GET", "/api/v1/release")
    require(status, 200, "release before")
    before = release["release"]
    result = {
        "observedAtUtc": datetime.now(timezone.utc).isoformat(),
        "applicationShaBefore": before,
        "syntheticMarker": marker,
        "viewerHasCaseView": "CASE_VIEW" in viewer.me.get("permissions", []),
        "viewerHasPartyManage": "PARTY_MANAGE" in viewer.me.get("permissions", []),
        "steps": {},
    }
    name = f"{MARK} unattached confidential party {marker}"
    status, party = manager.call("POST", "/api/v1/parties", {
        "kind": "PERSON", "name": name, "confidential": True,
        "phone": "22000000", "notes": "synthetic QA access edge",
    })
    require(status, 201, "create unattached party")
    party_id = party["id"]
    status, own = manager.call("GET", f"/api/v1/parties?q={marker}")
    require(status, 200, "manager party search")
    status, outsider = viewer.call("GET", f"/api/v1/parties?q={marker}")
    require(status, 200, "viewer party search")
    get_status, outsider_get = viewer.call("GET", f"/api/v1/parties/{party_id}")
    cases_status, outsider_cases = viewer.call("GET", f"/api/v1/parties/{party_id}/cases")
    result["steps"]["unattached"] = {
        "managerSearchTotal": own.get("totalItems"),
        "viewerSearchTotal": outsider.get("totalItems"),
        "viewerGetStatus": get_status,
        "viewerSeesMarkedName": get_status == 200 and outsider_get.get("name") == name,
        "viewerSeesContact": get_status == 200 and bool(outsider_get.get("phone")),
        "viewerPartyCasesStatus": cases_status,
        "viewerPartyCasesCount": len(outsider_cases) if isinstance(outsider_cases, list) else None,
    }

    _, users = manager.call("GET", "/api/v1/users?size=100&q=qa.")
    by_name = {x["username"]: x for x in users["items"]}
    _, courts = manager.call("GET", "/api/v1/courts?size=100")
    court = next(x for x in courts["items"] if x["level"] == "FIRST_INSTANCE" and x["active"])
    today = datetime.now(timezone.utc).date().isoformat()
    status, legal_case = manager.call("POST", "/api/v1/cases/intake", {
        "automatedNo": f"QA-PEDGE-{marker}", "title": f"{MARK} party edge {marker}",
        "caseType": "CIVIL", "subject": f"{MARK} party scope", "openedOn": today,
        "firstInstance": {"courtId": court["id"], "courtCaseNumber": marker[-9:],
                          "courtCaseYear": 2026, "filingDate": today},
        "authorityPosition": "DEFENDANT", "parties": [{"partyId": party_id, "side": "PLAINTIFF"}],
        "primaryMemberId": by_name["qa.staff"]["id"],
        "alternateMemberId": by_name["qa.manager"]["id"], "financialImpact": "NO",
    })
    require(status, 201, "create synthetic case")
    case_id = legal_case["id"]
    status, after_link = viewer.call("GET", f"/api/v1/parties?q={marker}")
    require(status, 200, "viewer search after link")
    after_get_status, _ = viewer.call("GET", f"/api/v1/parties/{party_id}")
    after_cases_status, _ = viewer.call("GET", f"/api/v1/parties/{party_id}/cases")
    result["steps"]["afterHiddenCaseLink"] = {
        "viewerSearchTotal": after_link.get("totalItems"),
        "viewerGetStatus": after_get_status,
        "viewerPartyCasesStatus": after_cases_status,
    }

    status, party2 = manager.call("POST", "/api/v1/parties", {
        "kind": "PERSON", "name": f"{MARK} counsel removal party {marker}", "confidential": False,
    })
    require(status, 201, "create second party")
    party2_id = party2["id"]
    status, _ = staff.call("POST", f"/api/v1/cases/{case_id}/parties", {
        "partyId": party2_id, "role": "OPPONENT", "side": "PLAINTIFF"})
    require(status, 201, "link second party")
    status, counsel = staff.call("POST", f"/api/v1/cases/{case_id}/parties/{party2_id}/counsel", {
        "kind": "LAWYER", "name": f"{MARK} synthetic lawyer {marker}"})
    require(status, 201, "add counsel")
    status, _ = staff.call("DELETE", f"/api/v1/cases/{case_id}/parties/counsel/{counsel['id']}")
    require(status, 204, "mark counsel removed")
    status, current_counsel = staff.call("GET", f"/api/v1/cases/{case_id}/parties/counsel")
    require(status, 200, "active counsel list")
    unlink_status, unlink_body = staff.call("DELETE", f"/api/v1/cases/{case_id}/parties/{party2_id}")
    status, current_parties = staff.call("GET", f"/api/v1/cases/{case_id}/parties")
    require(status, 200, "parties after attempted unlink")
    result["steps"]["removedCounselUnlink"] = {
        "counselSoftDeleteStatus": 204,
        "activeCounselCountForParty": sum(x.get("partyId") == party2_id for x in current_counsel),
        "unlinkStatus": unlink_status,
        "unlinkRule": unlink_body.get("rule") if isinstance(unlink_body, dict) else None,
        "partyStillLinked": any(x.get("partyId") == party2_id for x in current_parties),
    }
    status, release = manager.call("GET", "/api/v1/release")
    require(status, 200, "release after")
    result["applicationShaAfter"] = release["release"]
    result["state"] = "OBSERVED" if before == release["release"] else "INVALIDATED_RELEASE_CHANGED"
    output = Path(__file__).with_name("party-edge-window2.json")
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps({"state": result["state"], "steps": result["steps"]}, ensure_ascii=False))


if __name__ == "__main__":
    main()
