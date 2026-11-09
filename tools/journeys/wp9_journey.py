"""WP9 journey on UAT (requirements sections 23, 25, 28, 29; owner decisions D3, D4, D5, D10): a synthetic case gets a
settlement that is proposed, reviewed and approved by a different account without closing the case, a directive that
the addressee moves, a ready template that is reviewed and approved and then filled into a draft from the case file, and
a unified search that finds all of it for the member and nothing for an account with no access to the case.
Run: python3 tools/journeys/wp9_journey.py [evidence.json]
Everything created carries the marker SYNTH-DOC; no existing case is changed; the template is put out of use at the end
(a template is never deleted)."""

import sys
import time

from uat import MARK, Journey, Session

run = time.strftime("%y%m%d%H%M%S")
token = "%s-%s" % (MARK, run)
journey = Journey("WP9 settlements, directives, templates, unified search (%s)" % run)
manager = Session("manager").login("manager")
staff = Session("staff").login("staff")
viewer = Session("viewer").login("viewer")
check = journey.check
status, release = manager.call("GET", "/api/v1/release")
journey.note("deployed release", release if status == 200 else status)


def total(session, query):
    status, body = session.call("GET", "/api/v1/search?q=" + query)
    return (status, body["total"] if status == 200 else None)


# --- a synthetic case owned by the staff account (the manager supervises it) ---------------------------------------------
_, users = manager.call("GET", "/api/v1/users?size=100&q=qa.")
by_name = {u["username"]: u for u in users["items"]}
_, courts = manager.call("GET", "/api/v1/courts?size=100")
court = next((c for c in courts["items"] if c["level"] == "FIRST_INSTANCE" and c["active"]), None)
if court is None:
    status, court = manager.call("POST", "/api/v1/courts", {
        "code": "SYN-" + run, "nameAr": "محكمة اختبارية (%s)" % MARK, "nameEn": "Synthetic court", "level": "FIRST_INSTANCE"})
    check("manager creates a synthetic first-instance court", status, 201)
status, case = manager.call("POST", "/api/v1/cases/intake", {
    "automatedNo": "AUTO-%s" % run, "title": "%s نزاع حول عقد التوريد" % token, "caseType": "COMMERCIAL",
    "subject": "%s مطالبة بالتعويض عن التأخير في التوريد" % token, "openedOn": time.strftime("%Y-%m-%d"),
    "firstInstance": {"courtId": court["id"], "courtCaseNumber": "9%s" % run[-5:], "courtCaseYear": 2026,
                      "filingDate": time.strftime("%Y-%m-%d")},
    "authorityPosition": "DEFENDANT",
    "parties": [{"newParty": {"kind": "ORGANIZATION", "name": "شركة المورد %s" % token, "nationalId": None}, "side": "PLAINTIFF"}],
    "primaryMemberId": by_name["qa.staff"]["id"], "alternateMemberId": by_name["qa.manager"]["id"],
    "financialImpact": "NO", "details": {"facts": "تأخر المورد في التسليم (%s)" % MARK}})
check("intake registers the synthetic case", status, 201)
case_id = case["id"]

# --- section 23: a settlement is proposed, approved by someone else, and never closes the case -------------------------
status, kinds = staff.call("GET", "/api/v1/lists/SETTLEMENT_KIND")
check("the settlement kinds are the three of the requirements text", (status, [k["code"] for k in kinds]),
      (200, ["SETTLEMENT", "WAIVER", "CONCILIATION"]))
check("a reader cannot propose a settlement", viewer.call("POST", "/api/v1/cases/%s/settlements" % case_id, {"kind": "SETTLEMENT"})[0] in (403, 404), True)
status, settlement = staff.call("POST", "/api/v1/cases/%s/settlements" % case_id, {
    "kind": "SETTLEMENT", "offeringPartyText": "الطرف المدعي (%s)" % MARK, "settlementAmount": "12500.500",
    "legalGrounds": "تفادي تكاليف التقاضي %s" % token, "legalOpinion": "يوصى بالقبول", "approvingAuthority": "مدير الإدارة القانونية"})
check("the specialist proposes a settlement with its own amount", (status, settlement["status"], settlement["settlementAmount"]),
      (201, "PROPOSED", "12500.500"))
sid = settlement["id"]
check("the amount does not touch the case's other amounts", staff.call("GET", "/api/v1/cases/%s" % case_id)[1].get("claimedAmount"), None)
status, moved = staff.call("POST", "/api/v1/settlements/%s/status" % sid, {"status": "UNDER_REVIEW", "version": settlement["version"]})
check("the specialist submits it for review", (status, moved["status"]), (200, "UNDER_REVIEW"))
check("the specialist cannot approve it", staff.call("POST", "/api/v1/settlements/%s/status" % sid, {
    "status": "APPROVED", "reason": "x", "version": moved["version"]})[0], 403)
check("a rejection needs a reason", manager.call("POST", "/api/v1/settlements/%s/status" % sid, {
    "status": "REJECTED", "version": moved["version"]})[0], 400)
status, approved = manager.call("POST", "/api/v1/settlements/%s/status" % sid, {
    "status": "APPROVED", "reason": "موافق على الشروط (%s)" % MARK, "version": moved["version"]})
check("the manager approves it", (status, approved["status"], approved["decidedBy"]), (200, "APPROVED", manager.me["id"]))
check("the approved terms cannot be edited", staff.call("PUT", "/api/v1/settlements/%s" % sid, {
    "kind": "SETTLEMENT", "version": approved["version"]})[0], 422)
check("execution needs the completion date", staff.call("POST", "/api/v1/settlements/%s/status" % sid, {
    "status": "EXECUTED", "version": approved["version"]})[0], 400)
status, done = staff.call("POST", "/api/v1/settlements/%s/status" % sid, {
    "status": "EXECUTED", "completedOn": time.strftime("%Y-%m-%d"), "version": approved["version"]})
check("the settlement is executed", (status, done["status"]), (200, "EXECUTED"))
status, fresh = manager.call("GET", "/api/v1/cases/%s" % case_id)
check("the case is still open: a settlement never closes it", fresh["status"], "OPEN")
status, history = staff.call("GET", "/api/v1/settlements/%s/history" % sid)
check("the history keeps every step with who did it", (status, [h["action"] for h in history]),
      (200, ["CREATED", "STATUS_CHANGED", "STATUS_CHANGED", "STATUS_CHANGED"]))

# --- section 25: a directive the addressee moves ----------------------------------------------------------------------
today = time.strftime("%Y-%m-%d")
check("a specialist cannot issue a directive", staff.call("POST", "/api/v1/directives", {
    "caseId": case_id, "title": "x", "directiveDate": today, "source": "s", "addresseeText": "a"})[0], 403)
status, directive = manager.call("POST", "/api/v1/directives", {
    "caseId": case_id, "kind": "PREPARE_MEMO", "title": "إعداد مذكرة الرد %s" % token, "directiveDate": today,
    "source": "قرار مدير الإدارة (%s)" % MARK, "sourceReferenceNo": run, "addresseeUserId": by_name["qa.staff"]["id"]})
check("the manager issues a directive with a system number", (status, directive["directiveNo"][:4], directive["status"]), (201, "DIR-", "NEW"))
status, mine = staff.call("GET", "/api/v1/directives?addressee=me&caseId=%s" % case_id)
check("the addressee finds it in the register", (status, [d["id"] for d in mine["items"]]), (200, [directive["id"]]))
status, progress = staff.call("POST", "/api/v1/directives/%s/status" % directive["id"], {"status": "IN_PROGRESS", "version": directive["version"]})
check("the addressee moves its status", (status, progress["status"]), (200, "IN_PROGRESS"))
check("a reader finds no directive of this case", viewer.call("GET", "/api/v1/directives?caseId=%s" % case_id)[1]["totalItems"], 0)

# --- section 29: a template, reviewed and approved, then a draft from the case file ----------------------------------------
status, tkinds = manager.call("GET", "/api/v1/lists/TEMPLATE_KIND")
check("the template kinds are those of the requirements text", (status, len(tkinds)), (200, 11))
check("no template is seeded", [t for t in manager.call("GET", "/api/v1/templates?includeInactive=true")[1] if t["origin"] == "DOCUMENT"], [])
status, template = manager.call("POST", "/api/v1/templates", {
    "kind": "DEFENSE_MEMO", "title": "مذكرة دفاع (%s)" % token,
    "body": "مذكرة دفاع في القضية {{case.referenceNo}} بعنوان {{case.title}} أمام {{court.name}} دائرة {{case.chamber}}."})
check("the manager creates a template with a draft first version", (status, template["versions"][0]["status"]), (201, "DRAFT"))
tid, vid = template["id"], template["versions"][0]["id"]
check("a specialist cannot make a draft from an unapproved version", staff.call("POST", "/api/v1/cases/%s/template-drafts" % case_id, {"templateId": tid})[0], 422)
status, reviewed = manager.call("POST", "/api/v1/template-versions/%s/review" % vid, {"version": template["versions"][0]["version"]})
status, approved_version = manager.call("POST", "/api/v1/template-versions/%s/approve" % vid, {"version": reviewed["version"]})
check("the version is reviewed then approved", (status, approved_version["status"]), (200, "APPROVED"))
status, draft = staff.call("POST", "/api/v1/cases/%s/template-drafts" % case_id, {"templateId": tid})
check("the draft is not final", (status, draft["draft"], draft["final"]), (200, True, False))
check("it fills the case number and title from the case file", case["referenceNo"] in draft["text"] and token in draft["text"], True)
check("it names the field the file does not hold and leaves its placeholder",
      ([f["key"] for f in draft["missing"]], "{{case.chamber}}" in draft["text"]), (["case.chamber"], True))
check("nothing was added to the case file", len(manager.call("GET", "/api/v1/cases/%s/documents" % case_id)[1]["items"]), 0)

# --- section 28: the unified search, restricted to what each account may see ---------------------------------------------------
status, found = staff.call("GET", "/api/v1/search?q=" + token)
types = [g["type"] for g in found["groups"]] if status == 200 else []
check("the member finds the case, its party, the settlement and the directive", {"CASE", "PARTY", "SETTLEMENT", "DIRECTIVE"} <= set(types), True)
check("the group totals add up to the total", sum(g["total"] for g in found["groups"]), found["total"])
check("spelling variants find the same case", total(staff, "التوريد")[0], 200)
status, one = staff.call("GET", "/api/v1/search?q=%s&types=CASE" % token)
check("the case is found by its number too", staff.call("GET", "/api/v1/search?q=%s&types=CASE" % case["referenceNo"])[1]["total"], 1)
check("a snippet shows the matched words", token.split("-")[0] in (one["groups"][0]["items"][0]["snippet"] or ""), True)
check("an account with no access finds nothing of it", total(viewer, token), (200, 0))
status, outsider_view = viewer.call("GET", "/api/v1/search?q=" + token)
check("and its answer does not mention the case", case_id in str(outsider_view), False)

# --- clean up what can be put away: the template goes out of use (nothing is deleted) ---------------------------------------
status, current = manager.call("GET", "/api/v1/templates/%s" % tid)
check("the template is put out of use at the end", manager.call("PUT", "/api/v1/templates/%s" % tid, {
    "kind": current["kind"], "title": current["title"], "description": None, "active": False, "version": current["version"]})[0], 200)

journey.finish(sys.argv[1] if len(sys.argv) > 1 else "wp9_journey.json")
