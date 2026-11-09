"""WP6 journey on UAT (requirements sections 14, 15 and 32; scenarios S11, S14 and the task part of S15): a hearing and a
task in the judicial calendar, day/week/month views, Kuwait time, a conflict of dates and the alternate member, manual dates,
the seven task statuses with history, delay reason and reassignment, a task-only assignee, and the in-app notification
centre with its preferences, seen through the QA accounts. All data is synthetic (SYNTH-DOC) and no existing case is edited.
Run: python3 tools/journeys/wp6_journey.py [evidence.json]

Note for whoever runs it: the follow-up run it triggers looks at the whole system, so it may also raise notifications for
existing records (new cases of the last week, hearings ahead). That is the feature; it sends nothing outside the system."""

import datetime
import sys
import time
import zoneinfo

from uat import MARK, Journey, Session

run = time.strftime("%y%m%d%H%M%S")
KUWAIT = zoneinfo.ZoneInfo("Asia/Kuwait")
today = datetime.datetime.now(KUWAIT).date()
iso = lambda d: d.isoformat()
soon = today + datetime.timedelta(days=2)
journey = Journey("WP6 tasks, judicial calendar and notification centre (%s)" % run)
manager = Session("manager").login("manager")
staff = Session("staff").login("staff")
viewer = Session("viewer").login("viewer")
check = journey.check
status, release = manager.call("GET", "/api/v1/release")
journey.note("deployed release", release if status == 200 else status)

_, users = manager.call("GET", "/api/v1/users?size=100&q=qa.")
by_name = {u["username"]: u for u in users["items"]}
staff_id, manager_id, viewer_id = by_name["qa.staff"]["id"], by_name["qa.manager"]["id"], by_name["qa.viewer"]["id"]

_, courts = manager.call("GET", "/api/v1/courts?size=100")
court = next((c for c in courts["items"] if c["level"] == "FIRST_INSTANCE" and c["active"]), None)
if court is None:
    status, court = manager.call("POST", "/api/v1/courts", {
        "code": "SYN-" + run, "nameAr": "محكمة اختبارية (%s)" % MARK, "nameEn": "Synthetic court", "level": "FIRST_INSTANCE"})
    check("manager creates a synthetic court", status, 201)


def register(label):
    payload = {
        "automatedNo": "%s-%s" % (label, run), "title": "%s-%s قضية %s" % (MARK, run, label), "caseType": "ADMINISTRATIVE",
        "subject": "%s: قضية اختبارية %s" % (MARK, label), "openedOn": iso(today),
        "firstInstance": {"courtId": court["id"], "courtCaseNumber": "%s%s" % (label[-1], run[-5:]), "courtCaseYear": 2026,
                          "filingDate": iso(today)},
        "authorityPosition": "DEFENDANT",
        "parties": [{"newParty": {"kind": "PERSON", "name": "مدعٍ اختباري %s %s" % (label, run), "nationalId": None}, "side": "PLAINTIFF"}],
        "primaryMemberId": staff_id, "alternateMemberId": manager_id, "financialImpact": "NO",
    }
    status, created = manager.call("POST", "/api/v1/cases/intake", payload)
    check("intake registers synthetic case %s" % label, status, 201)
    return created


case_a, case_b = register("A6"), register("B6")
a, b = case_a["id"], case_b["id"]
journey.note("case A", case_a["referenceNo"])
journey.note("case B", case_b["referenceNo"])
_, proceedings_a = staff.call("GET", "/api/v1/cases/%s/proceedings" % a)
_, proceedings_b = staff.call("GET", "/api/v1/cases/%s/proceedings" % b)
proc_a, proc_b = proceedings_a["items"][0]["id"], proceedings_b["items"][0]["id"]


def events(session, path):
    status, view = session.call("GET", path)
    return status, view


def of_case(view, case_id, kind=None):
    return [e for e in view["events"] if e["caseId"] == case_id and (kind is None or e["type"] == kind)]


# --- S11: a hearing and a task both appear in the calendar --------------------------------------------------------------------------
status, hearing = staff.call("POST", "/api/v1/proceedings/%s/hearings" % proc_a, {
    "hearingDate": iso(soon), "hearingTime": "10:00", "purpose": "PLEADING", "kind": "COURT", "responsibleMemberId": staff_id,
    "chamber": "الدائرة الثالثة (%s)" % MARK})
check("staff schedules a hearing in two days at 10:00", status, 201)
status, task = staff.call("POST", "/api/v1/tasks", {
    "title": "إعداد مذكرة الرد (%s)" % MARK, "caseId": a, "assigneeId": staff_id, "startsOn": iso(today), "dueDate": iso(soon),
    "priority": "HIGH", "instructions": "إرفاق صورة الحكم وإيصال الإخطار"})
check("staff creates a task on the case with a start date and instructions", status, 201)
check("it starts NEW with its instructions", (task["status"], task["startsOn"], task["instructions"].startswith("إرفاق")), ("NEW", iso(today), True))

path = "/api/v1/calendar/events?from=%s&to=%s" % (iso(soon), iso(soon))
status, mine = events(staff, path + "&userId=me")
check("the personal calendar answers", status, 200)
check("the hearing and the task of the case are in it", sorted(e["type"] for e in of_case(mine, a)), ["HEARING", "TASK"])
check("days are Kuwait days", mine["zone"], "Asia/Kuwait")
hearing_entry = of_case(mine, a, "HEARING")[0]
expected_utc = datetime.datetime.combine(soon, datetime.time(10, 0), KUWAIT).astimezone(datetime.timezone.utc)
check("the hearing is shown at 10:00 and stored as the UTC instant", (hearing_entry["startTime"], hearing_entry["startsAt"]),
      ("10:00", expected_utc.strftime("%Y-%m-%dT%H:%M:%SZ")))
status, general = events(manager, path)
check("both are in the general calendar of the department", sorted(e["type"] for e in of_case(general, a)), ["HEARING", "TASK"])
check("a user with no access to the case sees none of it", of_case(events(viewer, path)[1], a), [])
status, day = events(staff, "/api/v1/calendar/day?date=%s&userId=me" % iso(soon))
check("the day view holds them", (status, day["view"], len(of_case(day, a))), (200, "DAY", 2))
status, week = events(staff, "/api/v1/calendar/week?date=%s&userId=me" % iso(soon))
check("the week view runs Sunday to Saturday", (status, datetime.date.fromisoformat(week["from"]).weekday(), len(of_case(week, a))), (200, 6, 2))
status, month = events(staff, "/api/v1/calendar/month?month=%s&userId=me" % soon.strftime("%Y-%m"))
check("the month view counts the day", any(d["date"] == iso(soon) and d["count"] >= 2 for d in month["days"]), True)
_, only_tasks = events(staff, path + "&userId=me&type=TASK")
check("the kind filter keeps the task only", sorted(e["type"] for e in of_case(only_tasks, a)), ["TASK"])
_, other_user = events(manager, path + "&userId=" + staff_id)
check("the calendar of another member is asked by user id", len(of_case(other_user, a)), 2)
check("the period is validated", staff.call("GET", "/api/v1/calendar/events?from=%s&to=%s" % (iso(soon), iso(today)))[0], 400)

# manual dates ----------------------------------------------------------------------------------------------------------------------------
status, appeal = staff.call("POST", "/api/v1/calendar/dates", {
    "kind": "APPEAL_DEADLINE", "title": "موعد الطعن حسب تقدير المحامي (%s)" % MARK, "caseId": a, "date": iso(soon)})
check("the lawyer enters an appeal date by hand", (status, appeal["allDay"], appeal["status"]), (201, True, "ACTIVE"))
status, far = staff.call("POST", "/api/v1/calendar/dates", {
    "kind": "APPEAL_DEADLINE", "title": "موعد بعيد (%s)" % MARK, "caseId": a, "date": iso(today + datetime.timedelta(days=40))})
check("a far date is entered too", status, 201)
status, meeting = staff.call("POST", "/api/v1/calendar/dates", {
    "kind": "MEETING", "title": "اجتماع القسم (%s)" % MARK, "date": iso(soon), "startTime": "13:00", "endTime": "14:00"})
check("a meeting of no case is entered", status, 201)
_, mine = events(staff, path + "&userId=me")
check("the manual dates are on the calendar", sorted(e["type"] for e in mine["events"] if e["title"].endswith(MARK + ")") or e["caseId"] == a),
      ["APPEAL_DEADLINE", "HEARING", "MEETING", "TASK"])
check("a user without the right to enter dates cannot add one to the case",
      viewer.call("POST", "/api/v1/calendar/dates", {"kind": "OTHER", "title": "x", "caseId": a, "date": iso(soon)})[0], 403)
check("a date needs its start before its end",
      staff.call("POST", "/api/v1/calendar/dates", {"kind": "OTHER", "title": "x", "date": iso(soon), "endTime": "10:00"})[0], 400)

# conflict of dates and the alternate member --------------------------------------------------------------------------------------------
status, second = staff.call("POST", "/api/v1/proceedings/%s/hearings" % proc_b, {
    "hearingDate": iso(soon), "hearingTime": "10:00", "purpose": "EVIDENCE", "kind": "COURT", "responsibleMemberId": staff_id})
check("a second hearing of the same member at the same time is scheduled", status, 201)
status, conflicts = events(staff, "/api/v1/calendar/conflicts?from=%s&to=%s&userId=me" % (iso(soon), iso(soon)))
mine_conflicts = [c for c in conflicts["conflicts"] if {hearing["id"], second["id"]} <= {e["refId"] for e in c["entries"]}]
check("the calendar detects the conflict", (status, len(mine_conflicts)), (200, 1))
check("it offers the alternate member of each case", {e["alternateMemberId"] for e in mine_conflicts[0]["entries"]}, {manager_id})
status, moved = staff.call("PUT", "/api/v1/hearings/" + second["id"], {
    "hearingDate": iso(soon), "hearingTime": "10:00", "purpose": "EVIDENCE", "kind": "COURT", "responsibleMemberId": manager_id,
    "version": second["version"]})
check("the alternate member is assigned to the second hearing", status, 200)
_, conflicts = events(staff, "/api/v1/calendar/conflicts?from=%s&to=%s&userId=me" % (iso(soon), iso(soon)))
check("the conflict is gone", [c for c in conflicts["conflicts"] if {hearing["id"], second["id"]} <= {e["refId"] for e in c["entries"]}], [])
# bring it back so that the notification of the conflict can be seen
staff.call("PUT", "/api/v1/hearings/" + second["id"], {
    "hearingDate": iso(soon), "hearingTime": "10:00", "purpose": "EVIDENCE", "kind": "COURT", "responsibleMemberId": staff_id,
    "version": moved["version"]})

# --- section 14: the seven statuses, history, delay reason, reassignment --------------------------------------------------------------
tid = task["id"]
version = task["version"]
for target in ("WAITING_DOCUMENTS", "WAITING_OTHER", "IN_PROGRESS"):
    status, task = staff.call("POST", "/api/v1/tasks/%s/status" % tid, {"status": target, "version": version})
    check("the task moves to %s" % target, (status, task["status"]), (200, target))
    version = task["version"]
status, _ = staff.call("POST", "/api/v1/tasks/%s/status" % tid, {"status": "LATE", "version": version})
check("late is set by the system, not chosen", status, 422)
status, task = staff.call("POST", "/api/v1/tasks/%s/delay-reason" % tid, {"reason": "السجل مغلق حتى الأحد (%s)" % MARK, "version": version})
check("the reason for delay is recorded", (status, task["delayReason"].endswith(")")), (200, True))
version = task["version"]

# a task-only assignee (S15): sees the case read-only and edits only their task
check("before the task the read-only user cannot see the case", viewer.call("GET", "/api/v1/cases/" + a)[0], 404)
status, side = staff.call("POST", "/api/v1/tasks", {"title": "مهمة جانبية (%s)" % MARK, "caseId": a, "assigneeId": viewer_id, "dueDate": iso(soon)})
check("the specialist gives a task of the case to the read-only user", status, 201)
check("they now see the case", viewer.call("GET", "/api/v1/cases/" + a)[0], 200)
_, current = staff.call("GET", "/api/v1/cases/" + a)
check("but cannot change it", viewer.call("PUT", "/api/v1/cases/" + a, {"title": "x", "caseType": "CIVIL", "openedOn": iso(today), "version": current["version"]})[0], 403)
check("nor see another case", viewer.call("GET", "/api/v1/cases/" + b)[0], 404)
check("nor edit the fields of their task", viewer.call("PUT", "/api/v1/tasks/" + side["id"], {"title": "y", "assigneeId": viewer_id, "version": side["version"]})[0], 403)
check("nor reassign it", viewer.call("POST", "/api/v1/tasks/%s/reassign" % side["id"], {"assigneeId": manager_id, "version": side["version"]})[0], 403)
_, in_calendar = events(viewer, path + "&userId=me")
check("their task is in their calendar", [e["type"] for e in of_case(in_calendar, a, "TASK")], ["TASK"])

# reassignment with history and a notice, respecting the user's own preference
status, _ = staff.call("PUT", "/api/v1/notifications/preferences/TASK_ASSIGNED", {"enabled": False})
check("the specialist switches the assignment notice off for themselves", status, 200)
status, handed = manager.call("POST", "/api/v1/tasks", {"title": "مهمة للمختص (%s)" % MARK, "caseId": a, "assigneeId": staff_id, "dueDate": iso(soon)})
check("the manager gives the specialist a task", status, 201)
_, mine_n = staff.call("GET", "/api/v1/notifications?type=TASK_ASSIGNED&size=100")
check("the specialist is not told: their preference is respected", [n for n in mine_n["items"] if n["recordRef"] == "TASK:" + handed["id"]], [])
staff.call("PUT", "/api/v1/notifications/preferences/TASK_ASSIGNED", {"enabled": True})
status, moved_task = staff.call("POST", "/api/v1/tasks/%s/reassign" % tid, {"assigneeId": manager_id, "reason": "تغيّر توزيع العمل (%s)" % MARK, "version": version})
check("the task is reassigned with a reason", (status, moved_task["assigneeId"]), (200, manager_id))
_, told = manager.call("GET", "/api/v1/notifications?type=TASK_ASSIGNED&size=100")
check("the new assignee is told in the system", any(n["recordRef"] == "TASK:" + tid for n in told["items"]), True)
status, history = staff.call("GET", "/api/v1/tasks/%s/history" % tid)
check("the history says who changed what", [(h["action"], h["field"]) for h in history],
      [("CREATED", None), ("STATUS", "status"), ("STATUS", "status"), ("STATUS", "status"), ("DELAY_REASON", "delay_reason"),
       ("REASSIGNED", "assignee")])
check("the reassignment keeps both people and the reason", (history[-1]["oldValue"], history[-1]["newValue"], history[-1]["reason"].endswith(")")),
      (staff_id, manager_id, True))
status, done = manager.call("POST", "/api/v1/tasks/%s/status" % tid, {"status": "DONE", "version": moved_task["version"]})
check("the old word DONE is still accepted and means COMPLETED", (status, done["status"]), (200, "COMPLETED"))
status, closed = staff.call("POST", "/api/v1/tasks/%s/status" % side["id"], {"status": "COMPLETED", "version": side["version"]})
check("the specialist completes the side task", status, 200)
check("the read-only user loses the case again", viewer.call("GET", "/api/v1/cases/" + a)[0], 404)

# --- S14 and section 32: notifications for dates that exist, once, in the system only -----------------------------------------------------
status, ran = manager.call("POST", "/api/v1/follow-up/run", {})
check("the follow-up run answers", (status, "lateTasks" in ran), (200, True))
manager.call("POST", "/api/v1/follow-up/run", {})
_, mine_n = staff.call("GET", "/api/v1/notifications?size=100")
def about(kind, ref_prefix):
    return [n for n in mine_n["items"] if n["type"] == kind and (n["recordRef"] or "").startswith(ref_prefix)]
check("the hearing in two days is announced once", len(about("HEARING_UPCOMING", "HEARING:" + hearing["id"])), 1)
check("the appeal date that exists is announced once", len(about("APPEAL_DEADLINE", "CALENDAR_DATE:" + appeal["id"])), 1)
check("the far appeal date is not announced yet", len(about("APPEAL_DEADLINE", "CALENDAR_DATE:" + far["id"])), 0)
check("nothing is announced for a deadline nobody entered",
      [n for n in mine_n["items"] if n["type"] == "APPEAL_DEADLINE" and n["caseId"] == b], [])
check("the conflict of dates is raised to the member", len([n for n in mine_n["items"] if n["type"] == "DATE_CONFLICT" and n["recordRef"] == "CALENDAR:" + iso(soon)]), 1)
check("the new case is announced to its specialist, not to the manager who registered it",
      (any(n["type"] == "CASE_NEW" and n["caseId"] == a for n in mine_n["items"]),
       any(n["type"] == "CASE_NEW" and n["caseId"] == a for n in manager.call("GET", "/api/v1/notifications?size=100")[1]["items"])), (True, False))
check("notifications of these cases are not shown to a user without access",
      [n for n in viewer.call("GET", "/api/v1/notifications?size=100")[1]["items"] if n["caseId"] in (a, b)], [])

# the notification centre ----------------------------------------------------------------------------------------------------------
_, kinds = staff.call("GET", "/api/v1/notifications/types")
check("the centre knows the eleven kinds of the requirements and the system notices", (len(kinds), sum(1 for k in kinds if k["core"])), (15, 11))
_, filtered = staff.call("GET", "/api/v1/notifications?type=HEARING_UPCOMING&size=100")
check("the list is filtered by kind", {n["type"] for n in filtered["items"]}, {"HEARING_UPCOMING"})
item = filtered["items"][0]
check("an item links to its record", (item["recordType"], bool(item["recordId"])), ("HEARING", True))
status, _ = staff.call("POST", "/api/v1/notifications/%s/read" % item["id"], {})
check("it is marked read for this user", status, 204)
_, manager_copy = manager.call("GET", "/api/v1/notifications?type=HEARING_UPCOMING&read=false&size=100")
check("another user's copy stays unread", any(n["recordRef"] == "HEARING:" + hearing["id"] for n in manager_copy["items"]), True)
status, _ = staff.call("POST", "/api/v1/notifications/%s/unread" % item["id"], {})
check("it can be marked unread again", status, 204)
_, prefs = staff.call("GET", "/api/v1/notifications/preferences")
check("every kind is on again", all(p["enabled"] for p in prefs), True)
check("a kind that does not exist cannot be configured", staff.call("PUT", "/api/v1/notifications/preferences/EMAIL_DIGEST", {"enabled": False})[0], 404)

journey.finish(sys.argv[1] if len(sys.argv) > 1 else "wp6_journey.json")
