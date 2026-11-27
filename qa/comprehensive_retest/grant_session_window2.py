#!/usr/bin/env python3
"""Grant/revoke on the marked case from party_edge_window2; viewer keeps one live session."""
import json
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT.parents[1] / "tools/journeys"))
from uat import Session  # noqa: E402


def require(status, expected, label):
    if status != expected:
        raise RuntimeError(f"{label}: HTTP {status}, expected {expected}")


def main():
    prior = json.loads((ROOT / "party-edge-window2.json").read_text())
    marker = prior["syntheticMarker"]
    manager = Session("manager").login("manager")
    viewer = Session("viewer").login("viewer")
    staff = Session("staff").login("staff")
    status, rel = manager.call("GET", "/api/v1/release")
    require(status, 200, "release before")
    before = rel["release"]
    status, listing = manager.call("GET", f"/api/v1/cases?q=QA-PEDGE-{marker}&size=20")
    require(status, 200, "marked case lookup")
    legal_case = next(x for x in listing["items"] if marker in x.get("title", ""))
    case_id = legal_case["id"]
    status, details = manager.call("GET", f"/api/v1/cases/{case_id}")
    require(status, 200, "case details")
    status, _ = manager.call("PUT", f"/api/v1/cases/{case_id}/confidentiality", {
        "level": "RESTRICTED", "reason": "SYNTH-DOC QA live grant session", "version": details["version"]})
    require(status, 204, "restrict marked case")
    status, users = manager.call("GET", "/api/v1/users?size=100&q=qa.viewer")
    require(status, 200, "lookup viewer")
    viewer_id = next(x["id"] for x in users["items"] if x["username"] == "qa.viewer")
    before_viewer = viewer.call("GET", f"/api/v1/cases/{case_id}")[0]
    staff_status = staff.call("GET", f"/api/v1/cases/{case_id}")[0]
    status, grant = manager.call("POST", f"/api/v1/cases/{case_id}/grants", {
        "userId": viewer_id, "level": "VIEW", "reason": "SYNTH-DOC QA live session change"})
    require(status, 201, "grant viewer")
    after_grant = viewer.call("GET", f"/api/v1/cases/{case_id}")[0]
    party_after_grant = viewer.call("GET", f"/api/v1/cases/{case_id}/parties")[0]
    forbidden_change = viewer.call("PUT", f"/api/v1/cases/{case_id}", {
        "title": "SYNTH-DOC no update", "caseType": "CIVIL", "openedOn": datetime.now(timezone.utc).date().isoformat(),
        "version": 0})[0]
    status, _ = manager.call("DELETE", f"/api/v1/cases/{case_id}/grants/{grant['id']}")
    require(status, 204, "revoke viewer grant")
    after_revoke = viewer.call("GET", f"/api/v1/cases/{case_id}")[0]
    party_after_revoke = viewer.call("GET", f"/api/v1/cases/{case_id}/parties")[0]
    status, rel = manager.call("GET", "/api/v1/release")
    require(status, 200, "release after")
    result = {
        "observedAtUtc": datetime.now(timezone.utc).isoformat(),
        "applicationShaBefore": before, "applicationShaAfter": rel["release"],
        "marker": marker, "role": "qa.viewer live session, qa.staff, qa.manager", "browser": "API",
        "beforeViewerCase": before_viewer, "staffCase": staff_status,
        "afterGrantViewerCase": after_grant, "afterGrantViewerParties": party_after_grant,
        "viewerCannotChangeStatus": forbidden_change,
        "afterRevokeViewerCase": after_revoke, "afterRevokeViewerParties": party_after_revoke,
    }
    result["state"] = "INVALIDATED_RELEASE_CHANGED" if before != rel["release"] else (
        "PASS" if (before_viewer, staff_status, after_grant, party_after_grant,
                   forbidden_change, after_revoke, party_after_revoke) == (404, 200, 200, 200, 403, 404, 404)
        else "FAIL")
    (ROOT / "grant-session-window2.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps({k: v for k, v in result.items() if k not in ("marker", "observedAtUtc")}, ensure_ascii=False))
    if result["state"] != "PASS":
        raise SystemExit(1)


if __name__ == "__main__":
    main()
