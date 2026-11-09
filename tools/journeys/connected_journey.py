"""Connectedness journey on UAT (LD-DOC-00-001/002: the preamble asks for an interconnected system, not isolated screens).
One synthetic case receives a claim, a defence, a hearing, a judgment, a deadline named as the judgment's appeal deadline, a
task, a note and a risk assessment; the journey then reads the same case back through its summary, its unified timeline, the
calendar, the unified search, the case inventory and the dashboard, adds a second task and checks that the change shows in
the summary, the calendar, the search and the dashboard, and that an account with no access to the case sees none of it.
Run (by the integration owner, not by the developer): python3 tools/journeys/connected_journey.py [evidence.json]
Everything created carries the marker SYNTH-DOC; no existing case is changed."""

import datetime
import json
import time

from uat import MARK, Journey, Session

run = time.strftime("%y%m%d%H%M%S")
token = "%s-%s" % (MARK, run)
word = "connected%s" % run
import zoneinfo
today = datetime.datetime.now(zoneinfo.ZoneInfo("Asia/Kuwait")).date()
# Dates the server checks as "not in the future" use its own clock (UTC), which is behind Kuwait between 00:00 and 03:00 Kuwait
# time: a case cannot be dated with the Kuwait day then (application defect reported separately); journeys use the earlier day.
server_today = min(today, datetime.datetime.now(datetime.timezone.utc).date())
iso = lambda d: d.isoformat()
journey = Journey("Connected case: one case's data is linked everywhere (%s)" % run)
manager = Session("manager").login("manager")
staff = Session("staff").login("staff")
viewer = Session("viewer").login("viewer")
check = journey.check
status, release = manager.call("GET", "/api/v1/release")
journey.note("deployed release", release if status == 200 else status)

_, users = manager.call("GET", "/api/v1/users?size=100&q=qa.")
by_name = {u["username"]: u for u in users["items"]}
staff_id = by_name["qa.staff"]["id"]
_, courts = manager.call("GET", "/api/v1/courts?size=100")
court = next((c for c in courts["items"] if c["level"] == "FIRST_INSTANCE" and c["active"]), None)
if court is None:
    status, court = manager.call("POST", "/api/v1/courts", {
        "code": "SYN-" + run, "nameAr": "محكمة اختبارية (%s)" % MARK, "nameEn": "Synthetic court", "level": "FIRST_INSTANCE"})
    check("manager creates a synthetic first-instance court", status, 201)

status, case = manager.call("POST", "/api/v1/cases/intake", {
    "automatedNo": "AUTO-%s" % run, "title": "%s %s dispute" % (token, word), "caseType": "COMMERCIAL",
    "subject": "%s subject" % word, "openedOn": iso(server_today),
    "firstInstance": {"courtId": court["id"], "courtCaseNumber": "8%s" % run[-5:], "courtCaseYear": 2026, "filingDate": iso(server_today)},
    "authorityPosition": "DEFENDANT",
    "parties": [{"newParty": {"kind": "ORGANIZATION", "name": "%s party" % token, "nationalId": None}, "side": "PLAINTIFF"}],
    "primaryMemberId": staff_id, "alternateMemberId": by_name["qa.manager"]["id"], "financialImpact": "NO",
    "details": {"facts": "%s facts" % word}})
check("intake registers the synthetic case", status, 201)
case_id = case["id"]
_, proceedings = staff.call("GET", "/api/v1/cases/%s/proceedings" % case_id)
proceeding = proceedings["items"][0]["id"]

# --- the data of one case, entered where it belongs --------------------------------------------------------------------------
status, claim = staff.call("POST", "/api/v1/cases/%s/claims" % case_id, {"text": "%s claim" % word, "kind": "PRIMARY"})
check("a claim is recorded", status, 201)
status, defense = staff.call("POST", "/api/v1/cases/%s/defenses" % case_id, {"title": "%s defence" % word, "kind": "SUBSTANTIVE", "argument": token})
check("a defence is recorded", status, 201)
status, hearing = staff.call("POST", "/api/v1/proceedings/%s/hearings" % proceeding, {
    "hearingDate": iso(today + datetime.timedelta(days=5)), "purpose": "PLEADING", "notes": "%s hearing" % word})
check("a hearing is recorded", status, 201)
status, judgment = manager.call("POST", "/api/v1/proceedings/%s/judgment" % proceeding, {
    "judgmentDate": iso(server_today), "outcome": "PARTIAL", "summary": "%s judgment" % word, "final": False})
check("a judgment is recorded", status, 201)
status, result = manager.call("PUT", "/api/v1/judgments/%s/claim-results" % judgment["id"], {
    "results": [{"claimId": claim["id"], "result": "PARTIAL"}]})
journey.note("claim result status", status)
status, deadline = staff.call("POST", "/api/v1/cases/%s/deadlines" % case_id, {
    "sourceType": "JUDGMENT", "sourceId": judgment["id"], "dueOn": iso(today + datetime.timedelta(days=9)),
    "action": "%s appeal" % word, "ownerId": staff_id})
check("a deadline runs from the judgment", status, 201)
status, named = manager.call("PUT", "/api/v1/judgments/%s/appeal-deadline" % judgment["id"], {"deadlineId": deadline["id"]})
check("the judgment names it as its appeal deadline", (status, (named or {}).get("appealDeadline", {}).get("id")), (200, deadline["id"]))
status, task = staff.call("POST", "/api/v1/tasks", {
    "caseId": case_id, "title": "%s first task" % word, "assigneeId": staff_id, "dueDate": iso(today + datetime.timedelta(days=2)), "priority": "NORMAL"})
check("a task is assigned", status, 201)
status, _ = staff.call("POST", "/api/v1/cases/%s/notes" % case_id, {"body": "%s note" % word})
check("a note is added", status, 201)
status, _ = staff.call("POST", "/api/v1/cases/%s/risk-assessments" % case_id, {
    "level": "MEDIUM", "levelReasons": "%s risk" % word, "trigger": "MANUAL", "elements": []})
check("a risk assessment is recorded", status, 201)

# --- the same case read back through every other place -----------------------------------------------------------------------
window = "from=%s&to=%s&caseId=%s" % (iso(today), iso(today + datetime.timedelta(days=30)), case_id)
status, summary = staff.call("GET", "/api/v1/cases/%s/summary" % case_id)
check("the summary counts the hearing, the task and the deadline", (status, summary["hearings"]["total"], summary["tasks"]["open"], summary["deadlines"]["open"]), (200, 1, 1, 1))
_, timeline = staff.call("GET", "/api/v1/cases/%s/unified-timeline" % case_id)
kinds = {item["kind"] for item in timeline["items"]}
check("the timeline carries the hearing, the judgment and the deadline", {"HEARING", "JUDGMENT", "DEADLINE"} <= kinds, True)
_, calendar = staff.call("GET", "/api/v1/calendar/events?" + window)
types = {entry["type"] for entry in calendar["events"]}
check("the calendar shows the hearing, the task and the deadline", {"HEARING", "TASK", "DEADLINE"} <= types, True)
status, found = staff.call("GET", "/api/v1/search?q=" + word)
check("the unified search finds the case and its records by the same word", (status, found["total"] >= 5), (200, True))
_, inventory = manager.call("GET", "/api/v1/reports/cases?size=100&q=" + word)
check("the case inventory lists the case", token in json.dumps(inventory, ensure_ascii=False), True)
status, dashboard_before = manager.call("GET", "/api/v1/reports/dashboard")
check("the dashboard answers", status, 200)

# --- a change in one place shows in the others -------------------------------------------------------------------------------
status, second = staff.call("POST", "/api/v1/tasks", {
    "caseId": case_id, "title": "%s second task" % word, "assigneeId": staff_id, "dueDate": iso(today + datetime.timedelta(days=3)), "priority": "NORMAL"})
check("a second task is added", status, 201)
_, summary = staff.call("GET", "/api/v1/cases/%s/summary" % case_id)
check("the summary follows", summary["tasks"]["open"], 2)
_, calendar = staff.call("GET", "/api/v1/calendar/events?" + window)
check("the calendar follows", second["id"] in json.dumps(calendar), True)
_, found = staff.call("GET", "/api/v1/search?q=%s&types=TASK" % word)
check("the search follows", "second task" in json.dumps(found, ensure_ascii=False), True)
_, dashboard_after = manager.call("GET", "/api/v1/reports/dashboard")
check("the dashboard follows", dashboard_after != dashboard_before, True)

# --- and an account with no access sees none of it ---------------------------------------------------------------------------
check("an outsider cannot open the summary", viewer.call("GET", "/api/v1/cases/%s/summary" % case_id)[0] in (403, 404), True)
_, hidden = viewer.call("GET", "/api/v1/search?q=" + word)
check("an outsider finds nothing by search", (hidden or {}).get("total", 0), 0)

journey.finish(__import__("sys").argv[1] if len(__import__("sys").argv) > 1 else "docs/evidence/DOC-GAPS/connected_journey.json")
