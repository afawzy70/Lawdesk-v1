"""WP1c journey on UAT (requirements sections 2 and 3, owner decision D4): identifiers shown in part to those who may not
see them, the cases of a party, the request items of a case and the duplicate warning. (The lawyer and agent endpoints were
removed by owner decision in LD-DOC-r5.) Synthetic data only."""

import sys
import time

from uat import MARK, Journey, Session

run = time.strftime("%y%m%d%H%M%S")
journey = Journey("WP1c parties, requests and duplicates (%s)" % run)
manager = Session("manager").login("manager")
staff = Session("staff").login("staff")
viewer = Session("viewer").login("viewer")
check = journey.check
status, release = manager.call("GET", "/api/v1/release")
journey.note("deployed release", release if status == 200 else status)

civil = "29%s" % run[-9:]
status, party = manager.call("POST", "/api/v1/parties", {
    "kind": "PERSON", "name": "%s طرف اختباري %s" % (MARK, run), "nationalId": civil, "phone": "22000000",
    "email": "synth@example.invalid", "confidential": True, "notes": "بيانات اصطناعية"})
check("manager creates a confidential party with a civil number", status, 201)
pid = party["id"]
check("the manager sees the full civil number", manager.call("GET", "/api/v1/parties/" + pid)[1]["nationalId"], civil)
masked = viewer.call("GET", "/api/v1/parties/" + pid)[1]
check("a reader without PARTY_ID_VIEW sees only the last three characters",
      masked["nationalId"], "*" * (len(civil) - 3) + civil[-3:])
check("and no contact details of a confidential party", masked.get("phone"), None)
check("an identifier is not a search key for that reader",
      viewer.call("GET", "/api/v1/parties?q=" + civil)[1]["totalItems"], 0)
check("but it is for the manager", manager.call("GET", "/api/v1/parties?q=" + civil)[1]["totalItems"], 1)

_, users = manager.call("GET", "/api/v1/users?size=100&q=qa.")
by_name = {u["username"]: u for u in users["items"]}
_, courts = manager.call("GET", "/api/v1/courts?size=100")
court = next(c for c in courts["items"] if c["level"] == "FIRST_INSTANCE" and c["active"])
payload = {
    "automatedNo": "AUTO-%s" % run, "title": "%s-%s قضية طرف" % (MARK, run), "caseType": "CIVIL",
    "subject": "%s: اختبار" % MARK, "openedOn": time.strftime("%Y-%m-%d"),
    "firstInstance": {"courtId": court["id"], "courtCaseNumber": "7%s" % run[-5:], "courtCaseYear": 2026,
                      "filingDate": time.strftime("%Y-%m-%d")},
    "authorityPosition": "DEFENDANT", "parties": [{"partyId": pid, "side": "PLAINTIFF"}],
    "primaryMemberId": by_name["qa.staff"]["id"], "alternateMemberId": by_name["qa.manager"]["id"],
    "financialImpact": "NO",
}
status, case = manager.call("POST", "/api/v1/cases/intake", payload)
check("the case is registered with the party", status, 201)
cid = case["id"]
check("the party's cases include it for the specialist", any(
    c["caseId"] == cid for c in staff.call("GET", "/api/v1/parties/%s/cases" % pid)[1]), True)
# Since #105 a party that sits only on cases the reader cannot see does not exist for that reader (404, like an unknown id),
# instead of answering an empty list that would still confirm the party exists.
check("but not for a reader who cannot see the case: the party is not found", viewer.call("GET", "/api/v1/parties/%s/cases" % pid)[0], 404)

# LD-DOC-r5: the lawyer and agent endpoints are gone (owner decision); there is nothing to list or record.
check("the lawyer and agent list is gone", staff.call("GET", "/api/v1/cases/%s/parties/counsel" % cid)[0] in (404, 405), True)
status, _ = staff.call("POST", "/api/v1/cases/%s/parties/%s/counsel" % (cid, pid), {"kind": "LAWYER", "name": "x"})
check("recording a lawyer is no longer possible", status in (404, 405), True)

# --- request items of the case, per side -----------------------------------------------------------------------------------
status, body = staff.call("PUT", "/api/v1/cases/%s/requests" % cid,
                          {"plaintiff": ["طلب المدعي %s" % run], "defendant": ["طلب المدعى عليه %s" % run]})
check("the specialist records the request items of both sides", status, 200)
check("they are read back per side", staff.call("GET", "/api/v1/cases/%s/requests" % cid)[1],
      {"plaintiff": ["طلب المدعي %s" % run], "defendant": ["طلب المدعى عليه %s" % run]})
check("a reader without access gets not found", viewer.call("GET", "/api/v1/cases/%s/requests" % cid)[0], 404)
check("a blank item is refused", staff.call("PUT", "/api/v1/cases/%s/requests" % cid, {"plaintiff": [" "], "defendant": []})[0], 400)

# --- duplicate warning ----------------------------------------------------------------------------------------------------
again = dict(payload, firstInstance=dict(payload["firstInstance"], courtCaseNumber="8%s" % run[-5:]),
             parties=[])
status, body = manager.call("POST", "/api/v1/cases/intake", again)
check("a case with the same automated number is warned about", (status, body.get("rule")), (422, "DUPLICATE_SUSPECTED"))
_, hits = manager.call("GET", "/api/v1/cases/duplicate-check?automatedNo=AUTO-%s" % run)
check("the check names the visible case", [h["caseId"] for h in hits["visible"]], [cid])
# (A reader without access learns only a count: covered by PartyIdentityAndDuplicateApiTest; no QA account holds CASE_CREATE
# without access to the case.)
status, second = manager.call("POST", "/api/v1/cases/intake", dict(again, confirmDistinct=True, distinctNote="مرحلة جديدة"))
check("the registrar confirms it is different and it is registered", status, 201)
journey.note("second case", second["referenceNo"])

journey.finish(sys.argv[1] if len(sys.argv) > 1 else "wp1c_parties_journey.json")
