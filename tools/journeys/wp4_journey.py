"""WP4 journey on UAT (requirements section 10; owner decision D6; scenario S14): legal deadlines. A manual deadline is not
official until a competent member approves it; a computed date exists only from an approved rule, is a proposal until it is
approved, and shows its arithmetic; reminders are in-app and wait for configured lead times; nothing is ever completed or
closed by the system. All data is synthetic (SYNTH-DOC) and no existing case is edited.

The journey enters ONE synthetic rule and ONE synthetic holiday, both dated far in the past (2000/2001) so no real deadline can
use them, and retires both before it ends. It never sets the reminder lead times (that is the owner's choice); it only reports
them. Run: python3 tools/journeys/wp4_journey.py [evidence.json]"""

import datetime
import json
import sys
import time
import urllib.error
import urllib.request
import uuid
import zoneinfo

from uat import BASE, MARK, Journey, Session

run = time.strftime("%y%m%d%H%M%S")
today = datetime.datetime.now(zoneinfo.ZoneInfo("Asia/Kuwait")).date()
iso = lambda d: d.isoformat()
journey = Journey("WP4 legal deadlines, rules, holidays and reminders (%s)" % run)
manager = Session("manager").login("manager")
staff = Session("staff").login("staff")
viewer = Session("viewer").login("viewer")
check = journey.check
status, release = manager.call("GET", "/api/v1/release")
journey.note("deployed release", release if status == 200 else status)

_, users = manager.call("GET", "/api/v1/users?size=100&q=qa.")
by_name = {u["username"]: u for u in users["items"]}
staff_id, manager_id = by_name["qa.staff"]["id"], by_name["qa.manager"]["id"]

_, courts = manager.call("GET", "/api/v1/courts?size=100")
court = next((c for c in courts["items"] if c["level"] == "FIRST_INSTANCE" and c["active"]), None)
if court is None:
    status, court = manager.call("POST", "/api/v1/courts", {
        "code": "SYN-" + run, "nameAr": "محكمة اختبارية (%s)" % MARK, "nameEn": "Synthetic court", "level": "FIRST_INSTANCE"})
    check("manager creates a synthetic court", status, 201)

payload = {
    "automatedNo": "D1-%s" % run, "title": "%s-%s قضية مواعيد" % (MARK, run), "caseType": "ADMINISTRATIVE",
    "subject": "%s: قضية اختبارية للمواعيد القانونية" % MARK, "openedOn": iso(today),
    "firstInstance": {"courtId": court["id"], "courtCaseNumber": "4%s" % run[-5:], "courtCaseYear": 2026, "filingDate": iso(today)},
    "authorityPosition": "DEFENDANT",
    "parties": [{"newParty": {"kind": "PERSON", "name": "مدعٍ اختباري مواعيد %s" % run, "nationalId": None}, "side": "PLAINTIFF"}],
    "primaryMemberId": staff_id, "alternateMemberId": manager_id, "financialImpact": "NO",
}
status, case = manager.call("POST", "/api/v1/cases/intake", payload)
check("intake registers the synthetic case", status, 201)
case_id = case["id"]
journey.note("case", case["referenceNo"])
deadlines = "/api/v1/cases/%s/deadlines" % case_id
_, proceedings = staff.call("GET", "/api/v1/cases/%s/proceedings" % case_id)
proceeding = proceedings["items"][0]["id"]


def multipart(session, path, fields, file_name, content):
    boundary = "----synth" + uuid.uuid4().hex
    parts = [('--%s\r\nContent-Disposition: form-data; name="%s"\r\n\r\n%s\r\n' % (boundary, k, v)).encode() for k, v in fields.items()]
    parts.append(('--%s\r\nContent-Disposition: form-data; name="file"; filename="%s"\r\nContent-Type: application/pdf\r\n\r\n'
                  % (boundary, file_name)).encode() + content + b"\r\n")
    parts.append(("--%s--\r\n" % boundary).encode())
    request = urllib.request.Request(BASE + path, data=b"".join(parts), method="POST", headers={
        "Content-Type": "multipart/form-data; boundary=" + boundary, "Accept": "application/json",
        "X-XSRF-TOKEN": session._xsrf()})
    try:
        with session.opener.open(request, timeout=60) as response:
            return response.status, json.loads(response.read())
    except urllib.error.HTTPError as error:
        return error.code, None


# --- the settings start from what the owner's specialists entered: nothing is assumed ----------------------------------------
_, reminders = staff.call("GET", "/api/v1/deadline-settings/reminders")
journey.note("reminder lead times configured by the owner", reminders)
check("the lead times are an explicit choice: unconfigured means no leadDays at all",
      reminders["configured"] or reminders["leadDays"] is None, True)

# --- (a) a manual deadline is not official until a competent member approves it ----------------------------------------------
status, judgment = manager.call("POST", "/api/v1/proceedings/%s/judgment" % proceeding, {
    "judgmentDate": iso(today), "outcome": "PARTIAL", "summary": "حكم اختباري (%s)" % MARK, "final": False})
check("a synthetic judgment is recorded as a source", status, 201)
due = iso(today + datetime.timedelta(days=30))
status, manual = staff.call("POST", deadlines, {
    "sourceType": "JUDGMENT", "sourceId": judgment["id"], "startsOn": iso(today), "dueOn": due,
    "action": "تقديم الاستئناف (%s)" % MARK, "ownerId": staff_id, "notes": "ميعاد يدوي"})
check("staff records a deadline from the judgment", status, 201)
check("it is manual and not official", (manual["calcStatus"], manual["official"], manual["status"]), ("MANUAL", False, "OPEN"))
check("the source is the judgment", (manual["sourceType"], manual["sourceId"]), ("JUDGMENT", judgment["id"]))
check("a read-only user cannot record a deadline", viewer.call("POST", deadlines, {
    "sourceType": "TEXT", "sourceText": "x", "dueOn": due, "action": "x"})[0], 403)
check("a read-only user cannot approve it", viewer.call("POST", "/api/v1/deadlines/%s/approve" % manual["id"], {"version": 0})[0], 403)
status, approved = manager.call("POST", "/api/v1/deadlines/%s/approve" % manual["id"], {"version": manual["version"]})
check("a competent member approves it", status, 200)
check("it is official, with the approver and the time kept",
      (approved["calcStatus"], approved["official"], approved["approvedById"], approved["approvedAt"] is not None),
      ("APPROVED", True, manager_id, True))
check("approving twice is refused", manager.call("POST", "/api/v1/deadlines/%s/approve" % manual["id"], {"version": approved["version"]})[1]["rule"],
      "DEADLINE_ALREADY_APPROVED")
status, undated = staff.call("POST", deadlines, {"sourceType": "TEXT", "sourceText": "كتاب بتاريخ اختباري", "periodDays": 60,
                                                   "action": "الرد على الكتاب (%s)" % MARK})
check("a period without a due date is a valid record", (status, undated["dueOn"]), (201, None))
check("but it cannot be approved without a date", manager.call("POST", "/api/v1/deadlines/%s/approve" % undated["id"], {"version": 0})[1]["rule"],
      "DEADLINE_NO_DUE_DATE")

# --- the per-judgment view, the register and the visibility rules -------------------------------------------------------------
_, per_judgment = staff.call("GET", "/api/v1/judgments/%s/deadlines" % judgment["id"])
check("the judgment shows its own deadline", [d["id"] for d in per_judgment["items"]], [manual["id"]])
_, register = staff.call("GET", "/api/v1/deadlines?caseId=%s&size=100" % case_id)
check("the register lists the case's deadlines", register["totalItems"], 2)
_, official = staff.call("GET", "/api/v1/deadlines?caseId=%s&calcStatus=APPROVED" % case_id)
check("the register filters by approval", [d["id"] for d in official["items"]], [manual["id"]])
_, window = staff.call("GET", "/api/v1/deadlines?caseId=%s&from=%s&to=%s" % (case_id, iso(today + datetime.timedelta(days=29)), iso(today + datetime.timedelta(days=31))))
check("the register filters by due window", [d["id"] for d in window["items"]], [manual["id"]])
check("a user who cannot see the case sees none of its deadlines", (viewer.call("GET", "/api/v1/deadlines?caseId=%s" % case_id)[1] or {}).get("totalItems", 0), 0)
check("nor can they open one", viewer.call("GET", "/api/v1/deadlines/" + manual["id"])[0] in (403, 404), True)
check("nor the judgment's view", viewer.call("GET", "/api/v1/judgments/%s/deadlines" % judgment["id"])[0] in (403, 404), True)

# --- (b)+(c) rules and holidays are entered and approved by people; a computed date is only a proposal -------------------------
code = "SYNTH-DOC-%s" % run
rule_body = {"code": code, "label": "قاعدة اختبارية (%s)" % MARK, "periodDays": 5, "countMethod": "WORKING", "countsStartDay": False,
             "rollForward": False, "closedWeekdays": [6, 7], "legalSource": "مصدر اختباري فقط (%s)" % MARK, "effectiveFrom": "2000-01-01"}
check("a read-only user cannot enter a rule", viewer.call("POST", "/api/v1/deadline-rules", rule_body)[0], 403)
status, draft = staff.call("POST", "/api/v1/deadline-rules", rule_body)
check("staff enters a rule as a draft", (status, draft["status"]), (201, "DRAFT"))
start = "2001-03-05"  # a Monday, long before any real deadline
propose = {"sourceType": "TEXT", "sourceText": "تاريخ اختباري قديم", "ruleId": draft["id"], "startsOn": start,
           "action": "إجراء اختباري (%s)" % MARK, "ownerId": staff_id}
status, refused = staff.call("POST", deadlines + "/propose", propose)
check("a draft rule is not an approved rule: the system answers 422 NO_APPROVED_RULE", (status, refused["rule"]), (422, "NO_APPROVED_RULE"))
status, holiday = staff.call("POST", "/api/v1/official-holidays", {"holidayOn": "2001-03-07", "label": "عطلة اختبارية (%s)" % MARK,
                                                                      "legalSource": "مصدر اختباري فقط (%s)" % MARK})
check("staff enters a holiday as a draft", (status, holiday["status"]), (201, "DRAFT"))
check("a holiday is approved by a competent member only",
      viewer.call("POST", "/api/v1/official-holidays/%s/approve" % holiday["id"], {"version": 0})[0], 403)
status, holiday = manager.call("POST", "/api/v1/official-holidays/%s/approve" % holiday["id"], {"version": holiday["version"]})
check("the holiday is approved, with the approver kept", (status, holiday["status"], holiday["approvedByName"] is not None), (200, "APPROVED", True))
status, rule = manager.call("POST", "/api/v1/deadline-rules/%s/approve" % draft["id"], {"version": draft["version"]})
check("a competent member approves the rule", (status, rule["status"]), (200, "APPROVED"))
status, edit = staff.call("PUT", "/api/v1/deadline-rules/" + rule["id"], {**rule_body, "version": rule["version"]})
check("an approved rule is frozen", (status, edit["rule"]), (422, "RULE_NOT_DRAFT"))

status, proposed = staff.call("POST", deadlines + "/propose", propose)
check("with an approved rule the system proposes a date", status, 201)
check("it is a proposal, not an official deadline", (proposed["calcStatus"], proposed["official"], proposed["approvedById"]), ("PROPOSED", False, None))
check("5 working days from Monday 2001-03-05, Saturday/Sunday closed and the holiday skipped, end on 2001-03-13",
      proposed["dueOn"], "2001-03-13")
calc = proposed["calculation"]
check("it shows the rule and its legal source", (calc["ruleCode"], calc["legalSource"]), (code, "مصدر اختباري فقط (%s)" % MARK))
check("it shows the holiday applied and the closed weekdays skipped", [(s["date"], s["kind"]) for s in calc["skipped"]],
      [("2001-03-07", "HOLIDAY"), ("2001-03-10", "WEEKDAY"), ("2001-03-11", "WEEKDAY")])
check("a proposal cannot be completed before it is approved",
      staff.call("POST", "/api/v1/deadlines/%s/status" % proposed["id"], {"status": "COMPLETED", "version": proposed["version"]})[1]["rule"],
      "DEADLINE_PROPOSAL_PENDING")
status, early = staff.call("POST", deadlines + "/propose", {**propose, "startsOn": "1999-12-31"})
check("a rule is not used before its effective date", (status, early["rule"]), (422, "RULE_NOT_EFFECTIVE"))
status, official = manager.call("POST", "/api/v1/deadlines/%s/approve" % proposed["id"], {"version": proposed["version"]})
check("the proposal becomes official only when approved", (status, official["official"], official["approvedById"]), (200, True, manager_id))
check("and keeps the arithmetic it was approved with", official["calculation"]["dueOn"], "2001-03-13")

# --- completion is a user's act; an overdue deadline stays open; reminders are in-app ---------------------------------------------
status, doc = multipart(staff, "/api/v1/cases/%s/documents" % case_id, {"title": "إثبات اختباري (%s)" % MARK}, "proof-%s.pdf" % run,
                        b"%PDF-1.4\n%" + MARK.encode() + b"\n")
check("a proof document is archived in the case file", status, 201)
status, done = staff.call("POST", "/api/v1/deadlines/%s/status" % official["id"], {
    "status": "COMPLETED", "completedOn": iso(today), "proofDocumentId": doc["id"], "version": official["version"]})
check("staff completes the deadline with its proof", (status, done["status"], done["proofDocumentId"]), (200, "COMPLETED", doc["id"]))
status, reopened = staff.call("POST", "/api/v1/deadlines/%s/status" % official["id"], {"status": "OPEN", "version": done["version"]})
check("a mistaken completion can be reopened by a user", (status, reopened["status"], reopened["completedOn"]), (200, "OPEN", None))

late_due = iso(today - datetime.timedelta(days=2))
status, late = staff.call("POST", deadlines, {"sourceType": "TEXT", "sourceText": "ميعاد متأخر اختباري", "dueOn": late_due,
                                                "action": "إجراء متأخر (%s)" % MARK, "ownerId": staff_id})
check("an already-late deadline can be recorded", status, 201)
status, late = manager.call("POST", "/api/v1/deadlines/%s/approve" % late["id"], {"version": late["version"]})
check("and approved", status, 200)
_, listed = staff.call("GET", "/api/v1/deadlines?caseId=%s&overdue=true" % case_id)
check("it is overdue in the register (as is the reopened 2001 deadline: nothing closes by itself)",
      sorted(d["id"] for d in listed["items"]), sorted([late["id"], official["id"]]))
status, outcome = manager.call("POST", "/api/v1/deadlines/reminders/run", {})
check("the reminder run answers", status, 200)
if not reminders["configured"]:
    check("no reminder is created before a due date while the lead times are not configured", outcome["approaching"], 0)
manager.call("POST", "/api/v1/deadlines/reminders/run", {})
_, mine = staff.call("GET", "/api/v1/notifications?size=100")
overdue_notes = [n for n in mine["items"] if n["type"] == "DEADLINE_OVERDUE" and n["caseId"] == case_id]
check("the specialist is told in the system once per overdue approved deadline (a repeated run adds none)", len(overdue_notes), 2)
check("a user who cannot see the case receives nothing about it", [n for n in viewer.call("GET", "/api/v1/notifications?size=100")[1].get("items", [])
                                                                    if n["caseId"] == case_id], [])
check("the run did not close the overdue deadline", staff.call("GET", "/api/v1/deadlines/" + late["id"])[1]["status"], "OPEN")

# --- the unified timeline and the summary -----------------------------------------------------------------------------------------
_, timeline = staff.call("GET", "/api/v1/cases/%s/unified-timeline?types=DEADLINE&size=100" % case_id)
check("the timeline lists the case's dated deadlines (the one with only a period has no date)", len(timeline["items"]), 3)
check("and says deadlines are available", timeline["deadlinesAvailable"], True)
check("the overdue one needs action", [e["needsAction"] for e in timeline["items"] if e["refId"] == late["id"]], [True])
check("the future approved one is marked as upcoming", [e["isFuture"] for e in timeline["items"] if e["refId"] == manual["id"]], [True])
_, summary = staff.call("GET", "/api/v1/cases/%s/summary" % case_id)
check("the summary counts the open deadlines", (summary["deadlines"]["open"], summary["deadlines"]["overdue"]), (4, 2))
check("a user who cannot see the case cannot read its timeline", viewer.call("GET", "/api/v1/cases/%s/unified-timeline" % case_id)[0], 404)

# --- clean-up: the synthetic rule and holiday are retired so no one can use them ----------------------------------------------------
status, retired_rule = manager.call("POST", "/api/v1/deadline-rules/%s/retire" % rule["id"], {"version": rule["version"]})
check("the synthetic rule is retired", (status, retired_rule["status"]), (200, "RETIRED"))
status, retired_holiday = manager.call("POST", "/api/v1/official-holidays/%s/retire" % holiday["id"], {"version": holiday["version"]})
check("the synthetic holiday is retired", (status, retired_holiday["status"]), (200, "RETIRED"))
status, after = staff.call("POST", deadlines + "/propose", propose)
check("a retired rule proposes nothing", (status, after["rule"]), (422, "NO_APPROVED_RULE"))

journey.finish(sys.argv[1] if len(sys.argv) > 1 else "wp4_journey.json")
sys.exit(1 if journey.failed else 0)
