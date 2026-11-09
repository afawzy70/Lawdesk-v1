"""WP3 journey on UAT (requirements sections 7, 8, 18, 19, 21 and the minimum of 32; scenarios S6, S7, S9, S10, S12):
hearings and adjournments, linked cases, an information request with replies, late-reply follow-up and escalation, risk
assessment versions, the unified timeline and the in-app notifications, seen through the QA accounts. All data is
synthetic (SYNTH-DOC) and no existing case is edited. Run: python3 tools/journeys/wp3_followup_journey.py [evidence.json]"""

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
# The application's calendar day is the one in Egypt (hearing and request rules), whatever the clock of this machine says.
today = datetime.datetime.now(zoneinfo.ZoneInfo("Asia/Kuwait")).date()
# Dates the server checks as "not in the future" use its own clock (UTC), which is behind Kuwait between 00:00 and 03:00 Kuwait
# time: a case cannot be dated with the Kuwait day then (application defect reported separately); journeys use the earlier day.
server_today = min(today, datetime.datetime.now(datetime.timezone.utc).date())
iso = lambda d: d.isoformat()
journey = Journey("WP3 hearings, linked cases, information requests, risk and summary (%s)" % run)
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

status, unit = manager.call("POST", "/api/v1/org-units", {
    "tree": "AUTHORITY", "parentId": None, "level": "SECTOR", "nameAr": "قطاع الشؤون الإدارية (%s %s)" % (MARK, run)})
check("manager creates the authority unit the requests go to", status, 201)


def register(label):
    payload = {
        "automatedNo": "%s-%s" % (label, run), "title": "%s-%s قضية %s" % (MARK, run, label), "caseType": "ADMINISTRATIVE",
        "subject": "%s: قضية اختبارية %s" % (MARK, label), "openedOn": iso(server_today),
        "firstInstance": {"courtId": court["id"], "courtCaseNumber": "%s%s" % (ord(label[0]) - 64, run[-5:]), "courtCaseYear": 2026,
                          "filingDate": iso(server_today)},
        "authorityPosition": "DEFENDANT",
        "parties": [{"newParty": {"kind": "PERSON", "name": "مدعٍ اختباري %s %s" % (label, run), "nationalId": None}, "side": "PLAINTIFF"}],
        "primaryMemberId": staff_id, "alternateMemberId": manager_id, "financialImpact": "NO",
    }
    status, created = manager.call("POST", "/api/v1/cases/intake", payload)
    check("intake registers synthetic case %s" % label, status, 201)
    return created


case_a, case_b = register("A1"), register("B1")
a, b = case_a["id"], case_b["id"]
journey.note("case A", case_a["referenceNo"])
journey.note("case B", case_b["referenceNo"])
_, proceedings_a = staff.call("GET", "/api/v1/cases/%s/proceedings" % a)
_, proceedings_b = staff.call("GET", "/api/v1/cases/%s/proceedings" % b)
proc_a, proc_b = proceedings_a["items"][0]["id"], proceedings_b["items"][0]["id"]


def multipart(session, path, fields, file_name, content):
    boundary = "----synth" + uuid.uuid4().hex
    parts = []
    for name, value in fields.items():
        parts.append(('--%s\r\nContent-Disposition: form-data; name="%s"\r\n\r\n%s\r\n' % (boundary, name, value)).encode())
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


# --- S9: an adjournment keeps the previous hearing and creates a separate next one ---------------------------------------
status, first = staff.call("POST", "/api/v1/proceedings/%s/hearings" % proc_a, {
    "hearingDate": iso(today), "hearingTime": "09:00", "purpose": "PLEADING", "kind": "COURT", "chamber": "الدائرة الثالثة (%s)" % MARK,
    "judgeOrExpert": "المستشار الاختباري", "responsibleMemberId": staff_id, "attendees": "ممثل الجهة والخصم",
    "requiredAction": "إعداد مذكرة الرد"})
check("staff schedules a hearing with the extended record", status, 201)
check("the kind and chamber are kept", (first["kind"], first["chamber"]), ("COURT", "الدائرة الثالثة (%s)" % MARK))
status, adjourned = staff.call("POST", "/api/v1/hearings/%s/result" % first["id"], {
    "status": "ADJOURNED", "decision": "تأجيل للرد", "adjournmentReason": "لإعلان الخصم بالمذكرة", "attendees": "حضر الطرفان",
    "proceedingsSummary": "سُمعت المرافعة", "opponentsRequests": "طلب أجلا للرد", "nextHearingDate": iso(today + datetime.timedelta(days=14)),
    "nextHearingTime": "10:30", "version": first["version"]})
check("staff adjourns it to a next hearing", status, 200)
check("the adjournment reason is stored", adjourned["adjournmentReason"], "لإعلان الخصم بالمذكرة")
next_id = adjourned["nextHearingId"]
_, hearings_a = staff.call("GET", "/api/v1/cases/%s/hearings" % a)
check("both hearings are in the file: the previous one is kept", hearings_a["totalItems"], 2)
status, kept = staff.call("GET", "/api/v1/hearings/" + first["id"])
check("the previous hearing is adjourned, not replaced", (kept["status"], kept["hearingDate"]), ("ADJOURNED", iso(today)))
status, following = staff.call("GET", "/api/v1/hearings/" + next_id)
check("the next hearing is a separate scheduled record chained to it",
      (following["status"], following["adjournedFromId"], following["chamber"]), ("SCHEDULED", first["id"], "الدائرة الثالثة (%s)" % MARK))

# no next date: the case goes to the follow-up list, no date is guessed
status, other = staff.call("POST", "/api/v1/proceedings/%s/hearings" % proc_b, {
    "hearingDate": iso(today), "purpose": "EVIDENCE", "kind": "EXPERT", "judgeOrExpert": "خبير اختباري", "responsibleMemberId": staff_id})
status, no_date = staff.call("POST", "/api/v1/hearings/%s/result" % other["id"], {
    "status": "ADJOURNED", "noNextDate": True, "noNextDateReason": "لم تحدد المحكمة موعدا", "version": other["version"]})
check("an adjournment with no next date is recorded", status, 200)
check("it creates no hearing and joins the follow-up list", (no_date["followUp"], no_date["nextHearingId"]), (True, None))
_, follow_up = staff.call("GET", "/api/v1/hearings/follow-up")
check("the follow-up list holds it", any(h["id"] == other["id"] for h in follow_up["items"]), True)
check("a read-only user without a scope sees no follow-up of it", any(h["id"] == other["id"] for h in viewer.call("GET", "/api/v1/hearings/follow-up")[1].get("items", [])), False)
_, mine = staff.call("GET", "/api/v1/notifications?size=100")
check("the specialist is alerted in the system", any(n["type"] == "HEARING_NO_NEXT_DATE" and n["caseId"] == b for n in mine["items"]), True)
status, scheduled = staff.call("POST", "/api/v1/hearings/%s/next-hearing" % other["id"], {
    "hearingDate": iso(today + datetime.timedelta(days=21)), "version": no_date["version"]})
check("a later next hearing ends the follow-up", status, 201)
_, follow_up = staff.call("GET", "/api/v1/hearings/follow-up")
check("the case leaves the follow-up list", any(h["id"] == other["id"] for h in follow_up["items"]), False)

# --- S10: linked cases ------------------------------------------------------------------------------------------------------
status, linked = staff.call("POST", "/api/v1/cases/%s/links" % a, {
    "otherCaseId": b, "kind": "JOINDER_REQUEST", "reason": "طلب الضم لوحدة الموضوع (%s)" % MARK, "requestedBy": "المدعي", "requestedOn": iso(today)})
check("staff links the two cases", status, 201)
check("a request to join starts as requested, not decided", (linked["kind"], linked["status"], linked["courtDecision"]), ("JOINDER_REQUEST", "REQUESTED", False))
_, in_a = staff.call("GET", "/api/v1/cases/%s/links" % a)
_, in_b = staff.call("GET", "/api/v1/cases/%s/links" % b)
check("the link shows in the file of case A", [x["otherCaseId"] for x in in_a["items"]], [b])
check("and in the file of case B", [x["otherCaseId"] for x in in_b["items"]], [a])
status, decided = staff.call("PUT", "/api/v1/case-links/" + linked["id"], {
    "reason": linked["reason"], "requestedBy": "المدعي", "requestedOn": iso(today), "status": "ACTIVE", "courtDecision": True,
    "decisionDate": iso(today), "operative": "ضم القضية الثانية للأولى (%s)" % MARK, "version": linked["version"]})
check("the court's decision is recorded on the link", (status, decided["courtDecision"]), (200, True))
check("a user with no access to the cases cannot read their links", viewer.call("GET", "/api/v1/cases/%s/links" % a)[0] in (403, 404), True)
status, _ = staff.call("POST", "/api/v1/cases/%s/links" % a, {"otherCaseId": a, "kind": "SUBJECT_LINK", "reason": "x"})
check("a case cannot be linked to itself", status, 422)

# --- S6: information request, replies, late follow-up and escalation ----------------------------------------------------------
status, request = staff.call("POST", "/api/v1/cases/%s/info-requests" % a, {
    "subject": "ملف القرار المطعون فيه (%s)" % MARK, "unitId": unit["id"], "details": "إفادة بتاريخ إخطار صاحب الشأن",
    "requiredDocuments": "القرار وإيصال الإخطار", "draftedOn": iso(today), "dueOn": iso(today + datetime.timedelta(days=10)),
    "responsibleMemberId": staff_id, "addressee": "مدير الإدارة"})
check("staff registers a request to the unit as a draft", status, 201)
check("the system generated the number", request["requestNo"].startswith("IR-"), True)
journey.note("request number", request["requestNo"])
rid = request["id"]
for target in ("PENDING_APPROVAL", "SENT"):
    status, request = staff.call("POST", "/api/v1/info-requests/%s/status" % rid, {
        "status": target, "letterNo": "L-%s" % run, "letterDate": iso(today), "version": request["version"]})
    check("request moves to %s" % target, (status, request["status"]), (200, target))
status, reply = staff.call("POST", "/api/v1/info-requests/%s/replies" % rid, {
    "receivedOn": iso(today), "completeness": "PARTIAL", "summary": "رد جزئي بالقرار دون الإيصال"})
check("a partial reply is recorded", status, 201)
status, reply2 = multipart(staff, "/api/v1/info-requests/%s/replies" % rid,
                           {"receivedOn": iso(today), "completeness": "INCOMPLETE", "summary": "إيصال غير مقروء"},
                           "reply-%s.pdf" % run, b"%PDF-1.4\n%" + MARK.encode() + b"\n")
check("a second reply with a document is recorded", status, 201)
check("the document is archived in the case file", reply2["documentId"] is not None, True)
_, docs = staff.call("GET", "/api/v1/cases/%s/documents" % a)
check("the archived reply is among the case documents", any(d["id"] == reply2["documentId"] for d in docs["items"]), True)
_, shown = staff.call("GET", "/api/v1/info-requests/" + rid)
check("every reply is kept and the state follows the latest", (shown["replyCount"], shown["status"]), (2, "INCOMPLETE"))

status, late = staff.call("POST", "/api/v1/cases/%s/info-requests" % a, {
    "subject": "مستند ناقص (%s)" % MARK, "unitId": unit["id"], "dueOn": iso(today - datetime.timedelta(days=2)),
    "responsibleMemberId": staff_id})
late_id = late["id"]
staff.call("POST", "/api/v1/info-requests/%s/status" % late_id, {"status": "SENT", "version": late["version"]})
_, late_now = staff.call("GET", "/api/v1/info-requests/" + late_id)
check("a request past its date is overdue as soon as it is read", late_now["overdue"], True)
status, ran = manager.call("POST", "/api/v1/follow-up/run", {})
check("the follow-up run marks late requests", (status, ran["overdueRequests"] >= 1), (200, True))
_, late_now = staff.call("GET", "/api/v1/info-requests/" + late_id)
check("the late request is marked overdue and is not closed", late_now["status"], "OVERDUE")
_, mine = staff.call("GET", "/api/v1/notifications?size=100")
alerts = [n for n in mine["items"] if n["type"] == "INFO_REQUEST_OVERDUE" and n["recordRef"] == "INFO_REQUEST:" + late_id]
check("the specialist has one in-app alert about it", len(alerts), 1)
status, ran = manager.call("POST", "/api/v1/follow-up/run", {})
_, mine = staff.call("GET", "/api/v1/notifications?size=100")
check("running again never duplicates the alert",
      len([n for n in mine["items"] if n["type"] == "INFO_REQUEST_OVERDUE" and n["recordRef"] == "INFO_REQUEST:" + late_id]), 1)
status, _ = staff.call("POST", "/api/v1/info-requests/%s/follow-ups" % late_id, {"kind": "REMINDER", "note": "اتصال هاتفي بالإدارة"})
check("a reminder is recorded", status, 201)
status, _ = staff.call("POST", "/api/v1/info-requests/%s/follow-ups" % late_id, {"kind": "RE_ADDRESS", "note": "كتاب ثانٍ"})
check("a new letter to the unit is recorded", status, 201)
status, _ = staff.call("POST", "/api/v1/info-requests/%s/follow-ups" % late_id, {"kind": "ESCALATION", "escalatedTo": manager_id})
check("escalation is refused without the escalation permission", status, 403)
status, _ = manager.call("POST", "/api/v1/info-requests/%s/follow-ups" % late_id, {
    "kind": "ESCALATION", "escalatedTo": staff_id, "note": "تأخر الرد أسبوعا (%s)" % MARK})
check("a holder of the permission escalates", status, 201)
_, shown = staff.call("GET", "/api/v1/info-requests/" + late_id)
check("the follow-up history is kept in order", [f["kind"] for f in shown["followUps"]], ["REMINDER", "RE_ADDRESS", "ESCALATION"])
_, listed = staff.call("GET", "/api/v1/info-requests?overdue=true&size=100")
check("the late request is in the overdue list", any(r["id"] == late_id for r in listed["items"]), True)
status, _ = staff.call("PUT", "/api/v1/info-requests/" + late_id, {
    "subject": shown["subject"], "unitId": shown["unitId"], "dueOn": iso(today + datetime.timedelta(days=5)),
    "delayReason": "طلبت الإدارة مهلة إضافية", "version": shown["version"]})
check("the reason for the delay is recorded with a new date", status, 200)
_, shown = staff.call("GET", "/api/v1/info-requests/" + late_id)
check("it is no longer overdue and returns to its earlier state", (shown["overdue"], shown["status"]), (False, "SENT"))
check("a user with no access to the case cannot see its requests", viewer.call("GET", "/api/v1/cases/%s/info-requests" % a)[0] in (403, 404), True)
_, report = staff.call("GET", "/api/v1/info-requests/report")
check("the responsiveness report counts requests per unit", any(u["unitId"] == unit["id"] and u["requests"] >= 2 for u in report["units"]), True)

# --- S7: risk assessment updated after a new document, history kept ------------------------------------------------------------
elements = [{"code": "EVIDENCE_STRENGTH", "rating": "HIGH", "reason": "إيصال الإخطار غير متوفر"},
            {"code": "POTENTIAL_FINANCIAL_LOSS", "rating": "MEDIUM", "reason": "تعويض مطالب به"}]
status, v1 = staff.call("POST", "/api/v1/cases/%s/risk-assessments" % a, {
    "level": "HIGH", "levelReasons": "غياب ما يثبت الإخطار", "potentialLoss": "75000.500", "trigger": "MANUAL", "elements": elements})
check("a high risk without a plan is refused", (status, (v1 or {}).get("rule")), (422, "REMEDIATION_REQUIRED"))
status, v1 = staff.call("POST", "/api/v1/cases/%s/risk-assessments" % a, {
    "level": "HIGH", "levelReasons": "غياب ما يثبت الإخطار", "potentialLoss": "75000.500", "trigger": "MANUAL", "elements": elements,
    "remediation": [{"cause": "لا دليل على الإخطار", "action": "طلب إيصال الإخطار من الإدارة", "unitToAddress": "الشؤون الإدارية",
                     "ownerId": staff_id, "targetDate": iso(today + datetime.timedelta(days=7))}]})
check("the first assessment is version 1, with ten elements and its own loss amount",
      (status, v1["versionNo"], len(v1["elements"]), v1["potentialLoss"]), (201, 1, 10, "75000.500"))
_, high = staff.call("GET", "/api/v1/risk-assessments/high?size=100")
check("the high-risk report lists the case with its plan", any(i["caseId"] == a and i["openRemediation"] == 1 for i in high["items"]), True)
status, v2 = staff.call("POST", "/api/v1/cases/%s/risk-assessments" % a, {
    "level": "LOW", "levelReasons": "ورد إيصال الإخطار", "potentialLoss": "0.000", "trigger": "NEW_DOCUMENT",
    "triggerNote": "رد الإدارة بالإيصال (%s)" % MARK, "triggerRefId": reply2["documentId"], "elements": [
        {"code": "EVIDENCE_STRENGTH", "rating": "LOW", "reason": "الإيصال مرفق"}]})
check("a new document leads to a new version", (status, v2["versionNo"], v2["trigger"]), (201, 2, "NEW_DOCUMENT"))
_, history = staff.call("GET", "/api/v1/cases/%s/risk-assessments" % a)
check("the history keeps both versions, newest first", [x["versionNo"] for x in history["items"]], [2, 1])
check("the earlier version is unchanged", (history["items"][1]["level"], history["items"][1]["potentialLoss"]), ("HIGH", "75000.500"))
_, high = staff.call("GET", "/api/v1/risk-assessments/high?size=100")
check("the lowered case leaves the high-risk report", any(i["caseId"] == a for i in high["items"]), False)
check("a user with no access cannot see the assessments", viewer.call("GET", "/api/v1/cases/%s/risk-assessments" % a)[0] in (403, 404), True)

# --- executive summary and unified timeline --------------------------------------------------------------------------------------
status, summary = staff.call("GET", "/api/v1/cases/%s/summary" % a)
check("the summary is assembled from the stored data", status, 200)
check("it names the hearings, the next one, the risk and the pending requests",
      (summary["hearings"]["total"], summary["hearings"]["adjourned"], summary["hearings"]["nextHearing"]["id"] == next_id,
       summary["risk"]["level"], summary["infoRequests"]["open"] >= 1), (2, 1, True, "LOW", True))
# The pending list names only modules that exist in the product (nothing is invented). It still names modules that are built
# since WP2/WP5 (a stale constant of the application: reported as a defect, not asserted away).
KNOWN_MODULES = {"CLAIMS", "DEFENSES", "EVIDENCE", "ENFORCEMENT", "DEADLINES"}
check("the modules named as pending are real modules, none is invented", set(summary["pendingModules"]) <= KNOWN_MODULES, True)
journey.note("summary.pendingModules (names modules that are now built)", summary["pendingModules"])
status, timeline = staff.call("GET", "/api/v1/cases/%s/unified-timeline?size=100" % a)
kinds = {e["kind"] for e in timeline["items"]}
check("the timeline joins hearings, requests, replies, risk and links", {"HEARING", "INFO_REQUEST", "INFO_REPLY", "RISK", "LINK", "EVENT"} <= kinds, True)
check("the upcoming hearing is marked as future and links to its record",
      any(e["refType"] == "HEARING" and e["refId"] == next_id and e["isFuture"] for e in timeline["items"]), True)
_, only = staff.call("GET", "/api/v1/cases/%s/unified-timeline?types=RISK" % a)
check("it filters by type", {e["kind"] for e in only["items"]}, {"RISK"})
check("a user with no access cannot see the summary or timeline",
      (viewer.call("GET", "/api/v1/cases/%s/summary" % a)[0] in (403, 404), viewer.call("GET", "/api/v1/cases/%s/unified-timeline" % a)[0] in (403, 404)), (True, True))

# --- S10/S12: closing one case does not close the other; hearings and history remain ------------------------------------------------
_, current = manager.call("GET", "/api/v1/cases/" + a)
status, closed = manager.call("POST", "/api/v1/cases/%s/status" % a, {"status": "CLOSED", "reason": "إغلاق اختباري (%s)" % MARK, "version": current["version"],
    "acknowledgeOpenItems": True})
check("the case is closed", (status, closed["status"]), (200, "CLOSED"))
_, case_b_now = manager.call("GET", "/api/v1/cases/" + b)
check("closing it did not close the linked case", case_b_now["status"], "OPEN")
_, in_b = staff.call("GET", "/api/v1/cases/%s/links" % b)
check("the link remains in the other case's file", [x["otherCaseStatus"] for x in in_b["items"]], ["CLOSED"])
_, hearings_a = staff.call("GET", "/api/v1/cases/%s/hearings" % a)
check("the hearings of the closed case remain", hearings_a["totalItems"], 2)
status, summary = staff.call("GET", "/api/v1/cases/%s/summary" % a)
check("the summary of the closed case still shows its history", (status, summary["status"], summary["hearings"]["total"]), (200, "CLOSED", 2))
_, timeline = staff.call("GET", "/api/v1/cases/%s/unified-timeline?types=EVENT&size=100" % a)
check("the closing is in the timeline", any("OPEN -> CLOSED" in e["summary"] or "CLOSED" in e["summary"] for e in timeline["items"]), True)

# --- notifications -----------------------------------------------------------------------------------------------------------------------
_, unread = staff.call("GET", "/api/v1/notifications/unread-count")
check("the specialist has unread notifications", unread["unread"] >= 2, True)
_, mine = staff.call("GET", "/api/v1/notifications?unreadOnly=true&size=100")
status, _ = staff.call("POST", "/api/v1/notifications/%s/read" % mine["items"][0]["id"], {})
check("one notification is marked read", status, 204)
check("another user cannot mark it", viewer.call("POST", "/api/v1/notifications/%s/read" % mine["items"][0]["id"], {})[0] in (403, 404), True)
_, theirs = viewer.call("GET", "/api/v1/notifications?size=100")
check("the read-only user sees none of these cases' notifications", [n for n in theirs.get("items", []) if n["caseId"] in (a, b)], [])
status, _ = staff.call("POST", "/api/v1/notifications/read-all", {})
check("all are marked read", status, 204)

journey.finish(sys.argv[1] if len(sys.argv) > 1 else "wp3_followup_journey.json")
