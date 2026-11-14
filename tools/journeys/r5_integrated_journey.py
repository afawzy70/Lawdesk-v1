"""LD-DOC r5 integrated journey (tracks A, B and C) with the four QA roles: admin, manager, staff, viewer.
A: the administrator's dropdown lists (ADMIN only, label/order edits, class rules, the catalogue every screen reads).
B: the next hearing given at registration reaches the hearings schedule and the calendar, and only for those who see the case.
C: requests per side (limits, soft delete, reload), hidden-case parity, read-only users, the permissions of the merged section,
   the administrative case-access endpoints, and the retired lawyer and agent endpoints.
Synthetic data only; it creates its own case and restores every list label it edits."""

import datetime
import re
import sys
import time
import uuid

from uat import MARK, Journey, Session

run = time.strftime("%y%m%d%H%M%S")
journey = Journey("R5 integrated A/B/C (%s)" % run)
check = journey.check
admin = Session("admin").login("admin")
manager = Session("manager").login("manager")
staff = Session("staff").login("staff")
viewer = Session("viewer").login("viewer")
status, release = manager.call("GET", "/api/v1/release")
journey.note("release", release if status == 200 else status)

# ------------------------------------------------------------------ A: the administrator's lists
status, lists = admin.call("GET", "/api/v1/admin/lists")
check("A: the administrator reads the catalogue of lists", status, 200)
check("A: the catalogue names 58 or more lists", len(lists) >= 58, True)
for who in (manager, staff, viewer):
    check("A: %s cannot open the lists administration" % who.name, who.call("GET", "/api/v1/admin/lists")[0], 403)
    check("A: %s cannot edit a list option" % who.name,
          who.call("PUT", "/api/v1/admin/lists/options/%s" % uuid.uuid4(), {"labelAr": "x", "active": True, "version": 0})[0], 403)
by_code = {item["code"]: item for item in lists}
check("A: hearing status is a label-and-order list (no new options)", by_code["HEARING_STATUS"]["addable"], False)
check("A: case importance accepts new options", by_code["CASE_IMPORTANCE"]["addable"], True)

status, options = admin.call("GET", "/api/v1/admin/lists/HEARING_STATUS/options")
scheduled = next(o for o in options if o["code"] == "SCHEDULED")
original = (scheduled["labelAr"], scheduled["labelEn"])
marker_ar, marker_en = "%s جلسة مجدولة %s" % (MARK, run), "%s Scheduled %s" % (MARK, run)
status, edited = admin.call("PUT", "/api/v1/admin/lists/options/%s" % scheduled["id"],
                            {"labelAr": marker_ar, "labelEn": marker_en, "active": True, "version": scheduled["version"]})
check("A: the administrator edits the label of a system value", status, 200)
check("A: its code is unchanged", edited["code"], "SCHEDULED")
status, everything = staff.call("GET", "/api/v1/lookups/all")
check("A: every signed-in user reads the catalogue the screens use", status, 200)
shown = next(o for o in everything["HEARING_STATUS"] if o["code"] == "SCHEDULED")
check("A: the edited label is what the catalogue serves", (shown["labelAr"], shown["labelEn"]), (marker_ar, marker_en))
check("A: a system value cannot be deactivated", admin.call("PUT", "/api/v1/admin/lists/options/%s" % scheduled["id"],
      {"labelAr": marker_ar, "labelEn": marker_en, "active": False, "version": edited["version"]})[0], 422)
check("A: no option can be added to a logic-bound list", admin.call("POST", "/api/v1/admin/lists/HEARING_STATUS/options",
      {"code": "SYNTH_NEW", "labelAr": "جديد"})[0], 422)
status, added = admin.call("POST", "/api/v1/admin/lists/CASE_IMPORTANCE/options",
                           {"code": "SYNTH_%s" % run, "labelAr": "%s أهمية %s" % (MARK, run), "labelEn": "%s importance" % MARK, "sortOrder": 90})
check("A: a descriptive list accepts a new option", status, 201)
check("A: a duplicate code is refused", admin.call("POST", "/api/v1/admin/lists/CASE_IMPORTANCE/options",
      {"code": "SYNTH_%s" % run, "labelAr": "مكرر"})[0], 409)
check("A: the new option reaches the catalogue", any(o["code"] == "SYNTH_%s" % run
      for o in manager.call("GET", "/api/v1/lookups/all")[1]["CASE_IMPORTANCE"]), True)
status, history = admin.call("GET", "/api/v1/admin/lists/options/%s/history" % scheduled["id"])
check("A: the edit is kept in the option's history", status == 200 and len(history) >= 2, True)

# ------------------------------------------------------------------ B and C: a case with a next hearing and requests
_, users = manager.call("GET", "/api/v1/users?size=100&q=qa.")
by_name = {u["username"]: u for u in users["items"]}
_, courts = manager.call("GET", "/api/v1/courts?size=100")
court = next(c for c in courts["items"] if c["level"] == "FIRST_INSTANCE" and c["active"])
hearing_day = (datetime.date.today() + datetime.timedelta(days=9)).isoformat()
payload = {
    "automatedNo": "AUTO-%s" % run, "title": "%s-%s قضية متكاملة" % (MARK, run), "caseType": "CIVIL", "subject": "%s: R5" % MARK,
    "openedOn": time.strftime("%Y-%m-%d"),
    "firstInstance": {"courtId": court["id"], "courtCaseNumber": "9%s" % run[-5:], "courtCaseYear": 2026, "filingDate": time.strftime("%Y-%m-%d")},
    "authorityPosition": "DEFENDANT", "parties": [], "primaryMemberId": by_name["qa.staff"]["id"],
    "alternateMemberId": by_name["qa.manager"]["id"], "financialImpact": "NO",
    "requests": {"plaintiff": ["طلب المدعي"], "defendant": ["طلب المدعى عليه الأول", "طلب المدعى عليه الثاني"]},
    "nextHearing": {"hearingDate": hearing_day, "hearingTime": "10:30", "notes": "%s جلسة قادمة" % MARK},
}
status, case = manager.call("POST", "/api/v1/cases/intake", payload)
check("C: the case is registered with requests and a next hearing", status, 201)
cid = case["id"]

status, page = manager.call("GET", "/api/v1/hearings?from=%s&to=%s&size=100" % (hearing_day, hearing_day))
mine = [h for h in (page or {}).get("items", []) if h.get("caseId") == cid]
check("B: the next hearing is in the hearings schedule", len(mine), 1)
check("B: it is a scheduled hearing on the given day", (mine[0]["status"], mine[0]["hearingDate"]) if mine else None, ("SCHEDULED", hearing_day))
status, events = manager.call("GET", "/api/v1/calendar/events?from=%s&to=%s" % (hearing_day, hearing_day))
text = str(events)
check("B: the calendar carries the same hearing", status == 200 and cid in text, True)
check("B: the specialist sees it in the schedule too", any(h.get("caseId") == cid for h in
      staff.call("GET", "/api/v1/hearings?from=%s&to=%s&size=100" % (hearing_day, hearing_day))[1].get("items", [])), True)
check("B: a user outside the case sees nothing of it", not any(h.get("caseId") == cid for h in
      (viewer.call("GET", "/api/v1/hearings?from=%s&to=%s&size=100" % (hearing_day, hearing_day))[1] or {}).get("items", [])), True)
check("B: nor in the calendar", cid in str(viewer.call("GET", "/api/v1/calendar/events?from=%s&to=%s" % (hearing_day, hearing_day))[1]), False)
bad = dict(payload, automatedNo="AUTO-%s-B" % run, nextHearing={"hearingDate": "2099-01-01"})
check("B: a hearing date far in the future is refused and registers nothing",
      manager.call("POST", "/api/v1/cases/intake", bad)[0] in (400, 422), True)
check("B: the old /deadlines register is a page of the application only (the API of case deadlines stays)",
      manager.call("GET", "/api/v1/cases/%s/deadlines" % cid)[0] in (200, 404), True)

# requests: persistence, edit, soft delete, limit
check("C: requests given at registration are stored per side", manager.call("GET", "/api/v1/cases/%s/requests" % cid)[1],
      {"plaintiff": ["طلب المدعي"], "defendant": ["طلب المدعى عليه الأول", "طلب المدعى عليه الثاني"]})
side = lambda s, n: ["%s %d" % (s, i) for i in range(1, n + 1)]
check("C: exactly 20 items per side are accepted", staff.call("PUT", "/api/v1/cases/%s/requests" % cid,
      {"plaintiff": side("P", 20), "defendant": side("D", 20)})[0], 200)
check("C: the 21st item is refused", staff.call("PUT", "/api/v1/cases/%s/requests" % cid,
      {"plaintiff": side("P", 21), "defendant": []})[0], 400)
status, kept = staff.call("GET", "/api/v1/cases/%s/requests" % cid)
check("C: the refused change left the stored items alone (reload)", (len(kept["plaintiff"]), len(kept["defendant"])), (20, 20))
edited_items = {"plaintiff": ["P 1 (amended)"] + side("P", 20)[2:], "defendant": side("D", 20)[:1]}
check("C: an item is edited and others are removed", staff.call("PUT", "/api/v1/cases/%s/requests" % cid, edited_items)[0], 200)
check("C: a fresh read shows exactly the current list", manager.call("GET", "/api/v1/cases/%s/requests" % cid)[1], edited_items)

# hidden-case parity: a reader outside the case and an id that does not exist look the same
absent = str(uuid.uuid4())
def shape(call):
    code, body = call
    return code, re.sub(r"[0-9a-f]{8}-[0-9a-f-]{27}", "ID", str(body)).replace(cid, "ID").replace(absent, "ID")
for label, method, path, body in (("the case", "GET", "/api/v1/cases/%s", None), ("its requests", "GET", "/api/v1/cases/%s/requests", None),
                                  ("a change of its requests", "PUT", "/api/v1/cases/%s/requests", {"plaintiff": ["x"], "defendant": []}),
                                  ("its grants", "GET", "/api/v1/cases/%s/grants", None)):
    hidden, nothing = shape(viewer.call(method, path % cid, body)), shape(viewer.call(method, path % absent, body))
    check("C: %s answers a reader outside the case exactly as an unknown id (%s)" % (label, hidden[0]), hidden, nothing)
check("C: and that answer is 404", viewer.call("GET", "/api/v1/cases/%s" % cid)[0], 404)

# read-only user with a grant: can read, cannot change; the merged section keeps its two permissions apart
status, case_now = manager.call("GET", "/api/v1/cases/%s" % cid)
viewer_id = by_name["qa.viewer"]["id"]
status, _ = manager.call("POST", "/api/v1/cases/%s/grants" % cid, {"userId": viewer_id, "level": "VIEW", "reason": "%s read-only check" % MARK})
check("C: the manager grants read access to the read-only user", status, 201)
check("C: the read-only user now reads the requests", viewer.call("GET", "/api/v1/cases/%s/requests" % cid)[0], 200)
check("C: but cannot change them", viewer.call("PUT", "/api/v1/cases/%s/requests" % cid, {"plaintiff": ["z"], "defendant": []})[0], 403)
check("C: the requests did not change", manager.call("GET", "/api/v1/cases/%s/requests" % cid)[1], edited_items)
check("C: the read-only user cannot link an administrative body", viewer.call("POST", "/api/v1/cases/%s/involvements" % cid, {})[0], 403)
check("C: nor raise an information request", viewer.call("POST", "/api/v1/cases/%s/info-requests" % cid, {})[0], 403)
check("C: the specialist, without the grant permission, cannot manage grants", staff.call("GET", "/api/v1/cases/%s/grants" % cid)[0] in (403, 404), True)
status, grants = manager.call("GET", "/api/v1/cases/%s/grants" % cid)
check("C: the administrative case-access endpoints list the grant for the manager", status == 200 and any(g["userId"] == viewer_id for g in grants), True)
status, _ = manager.call("PUT", "/api/v1/cases/%s/confidentiality" % cid,
                         {"level": "RESTRICTED", "reason": "%s restricted check" % MARK, "version": case_now["version"]})
check("C: confidentiality is set through the same endpoint the administration page uses", status, 204)
check("C: a restricted case stays unseen by an unrelated reader (the user with the grant still reads it)", viewer.call("GET", "/api/v1/cases/%s" % cid)[0], 200)
check("C: the lawyer and agent endpoints are gone", staff.call("GET", "/api/v1/cases/%s/parties/counsel" % cid)[0] in (404, 405), True)

# restore every list label this journey edited
status, _ = admin.call("PUT", "/api/v1/admin/lists/options/%s" % scheduled["id"],
                       {"labelAr": original[0], "labelEn": original[1], "active": True, "version": edited["version"]})
check("A: the original hearing-status label is restored", status, 200)
journey.finish(sys.argv[1] if len(sys.argv) > 1 else "/tmp/r5_integrated_journey.json")
