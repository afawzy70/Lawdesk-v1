"""WP1 journey on UAT (requirements sections 1, 6, 30, 31; owner decisions D1-D3): one synthetic case from registration to
restricted access, seen through four accounts. Run: python3 tools/journeys/wp1_access_journey.py [evidence.json]"""

import sys
import time
import uuid

from uat import MARK, Journey, Session

run = time.strftime("%y%m%d%H%M%S")
journey = Journey("WP1 access, case record and involved units (%s)" % run)
manager = Session("manager").login("manager")
staff = Session("staff").login("staff")
viewer = Session("viewer").login("viewer")
check = journey.check
status, release = manager.call("GET", "/api/v1/release")
journey.note("deployed release", release if status == 200 else status)


def ids(session, path):
    _, body = session.call("GET", path)
    return body


def count_cases(session):
    status, body = session.call("GET", "/api/v1/cases?size=100&q=" + MARK + "-" + run)
    return body["totalItems"] if status == 200 else status


# --- the structure: the legal department and the authority's own units, all marked as test data ----------------
status, legal_root = manager.call("POST", "/api/v1/org-units", {
    "tree": "LEGAL", "parentId": None, "level": "DEPARTMENT", "nameAr": "الإدارة القانونية (%s %s)" % (MARK, run)})
check("manager creates a legal department unit", status, 201)
status, legal_section = manager.call("POST", "/api/v1/org-units", {
    "tree": "LEGAL", "parentId": legal_root["id"], "level": "SECTION", "nameAr": "قسم القضايا (%s %s)" % (MARK, run)})
check("manager creates a legal section", status, 201)
status, sector = manager.call("POST", "/api/v1/org-units", {
    "tree": "AUTHORITY", "parentId": None, "level": "SECTOR", "nameAr": "قطاع الخدمات المساندة (%s %s)" % (MARK, run)})
status, department = manager.call("POST", "/api/v1/org-units", {
    "tree": "AUTHORITY", "parentId": sector["id"], "level": "DEPARTMENT", "nameAr": "إدارة الشؤون الإدارية (%s)" % run})
status, section = manager.call("POST", "/api/v1/org-units", {
    "tree": "AUTHORITY", "parentId": department["id"], "level": "SECTION", "nameAr": "قسم شؤون الموظفين (%s)" % run})
check("manager creates the authority's sector/department/section", status, 201)

_, users = manager.call("GET", "/api/v1/users?size=100&q=qa.")
by_name = {u["username"]: u for u in users["items"]}
status, _ = manager.call("PUT", "/api/v1/users/%s/org-unit" % by_name["qa.staff"]["id"], {"orgUnitId": legal_section["id"]})
check("manager places the staff account in the legal section", status, 204)

# --- a court to register against -----------------------------------------------------------------------------------
_, courts = manager.call("GET", "/api/v1/courts?size=100")
first_instance = next((c for c in courts["items"] if c["level"] == "FIRST_INSTANCE" and c["active"]), None)
if first_instance is None:
    status, first_instance = manager.call("POST", "/api/v1/courts", {
        "code": "SYN-" + run, "nameAr": "محكمة اختبارية (%s)" % MARK, "nameEn": "Synthetic court", "level": "FIRST_INSTANCE"})
    check("manager creates a synthetic first-instance court", status, 201)

# --- registration, with the record fields ----------------------------------------------------------------------------
payload = {
    "automatedNo": "AUTO-%s" % run,
    "title": "%s-%s قضية اختبارية" % (MARK, run),
    "caseType": "ADMINISTRATIVE",
    "subject": "%s: طعن اختباري على قرار إداري" % MARK,
    "openedOn": time.strftime("%Y-%m-%d"),
    "firstInstance": {"courtId": first_instance["id"], "courtCaseNumber": "9%s" % run[-5:], "courtCaseYear": 2026,
                      "filingDate": time.strftime("%Y-%m-%d")},
    "authorityPosition": "DEFENDANT",
    "parties": [{"newParty": {"kind": "PERSON", "name": "مدعٍ اختباري %s" % run, "nationalId": None}, "side": "PLAINTIFF"}],
    "primaryMemberId": by_name["qa.staff"]["id"],
    "alternateMemberId": by_name["qa.manager"]["id"],
    "financialImpact": "YES", "financialAmount": "1500.250",
    "details": {"chamber": "الدائرة الإدارية الثالثة", "receivedOn": time.strftime("%Y-%m-%d"),
                "referringUnitId": sector["id"], "facts": "ملخص وقائع اختباري", "claimedAmount": "12500.500",
                "importance": "HIGH", "internalNotes": "بيانات اصطناعية"},
}
status, created = manager.call("POST", "/api/v1/cases/intake", payload)
check("intake registers the synthetic case", status, 201)
case_id = created["id"]
journey.note("reference (system, D1)", created["referenceNo"])
journey.note("automated number (user, D1)", created["automatedNo"])
check("claimed amount keeps three decimals", created["details"]["claimedAmount"], "12500.500")
check("financial impact stays a separate amount", created["financialAmount"], "1500.250")
check("the case belongs to the specialist's unit", created["owningUnitId"], legal_section["id"])

# --- who sees it (D3) ----------------------------------------------------------------------------------------------------
check("the specialist sees the case", staff.call("GET", "/api/v1/cases/" + case_id)[0], 200)
check("the alternate member (also the manager) sees it", manager.call("GET", "/api/v1/cases/" + case_id)[0], 200)
check("a read-only user without a scope does not see it", viewer.call("GET", "/api/v1/cases/" + case_id)[0], 404)
check("nor in the viewer's list", count_cases(viewer), 0)
check("the specialist finds it by its subject", staff.call("GET", "/api/v1/cases?q=" + MARK + "-" + run)[1]["totalItems"], 1)
for path in ("/timeline", "/parties", "/proceedings", "/notes", "/judgments"):
    check("viewer is refused %s" % path, viewer.call("GET", "/api/v1/cases/%s%s" % (case_id, path))[0], 404)
for path in ("/hearings", "/documents"):
    # The read-only account holds no permission for these areas at all: refused by permission before the case is looked at.
    check("viewer is refused %s" % path, viewer.call("GET", "/api/v1/cases/%s%s" % (case_id, path))[0] in (403, 404), True)

# --- the units the case concerns, with frozen names ------------------------------------------------------------------
status, involvement = staff.call("POST", "/api/v1/cases/%s/involvements" % case_id, {
    "orgUnitId": section["id"], "primary": True, "contactName": "موظف اختباري", "requestedFromUnit": "صورة الملف الوظيفي"})
check("the specialist records the primary involved unit", status, 201)
check("the sector name is frozen on the record", involvement["sectorName"], "قطاع الخدمات المساندة (%s %s)" % (MARK, run))
manager.call("PUT", "/api/v1/org-units/%s" % section["id"], {
    "parentId": department["id"], "nameAr": "قسم أُعيدت تسميته (%s)" % run, "active": True, "version": section["version"]})
_, listed = staff.call("GET", "/api/v1/cases/%s/involvements" % case_id)
check("renaming the unit later leaves the case's record unchanged", listed[0]["sectionName"], "قسم شؤون الموظفين (%s)" % run)

# --- restricted access and a grant -------------------------------------------------------------------------------------
_, current = manager.call("GET", "/api/v1/cases/" + case_id)
status, _ = manager.call("PUT", "/api/v1/cases/%s/confidentiality" % case_id,
                         {"level": "RESTRICTED", "reason": "حساسية الملف (%s)" % MARK, "version": current["version"]})
check("the alternate member restricts the case", status, 204)
check("the specialist still sees it", staff.call("GET", "/api/v1/cases/" + case_id)[0], 200)
status, grant = manager.call("POST", "/api/v1/cases/%s/grants" % case_id, {
    "userId": by_name["qa.viewer"]["id"], "level": "VIEW", "reason": "رأي ثانٍ (%s)" % MARK})
check("a grant is given with a reason", status, 201)
check("the grantee now sees the restricted case", viewer.call("GET", "/api/v1/cases/" + case_id)[0], 200)
check("the grantee cannot change it", viewer.call("PUT", "/api/v1/cases/" + case_id, {
    "title": "x", "caseType": "CIVIL", "openedOn": time.strftime("%Y-%m-%d"), "version": 0})[0], 403)
check("nobody grants themself access", manager.call("POST", "/api/v1/cases/%s/grants" % case_id, {
    "userId": by_name["qa.manager"]["id"], "level": "VIEW", "reason": "me"})[0], 422)
status, _ = manager.call("DELETE", "/api/v1/cases/%s/grants/%s" % (case_id, grant["id"]))
check("the grant is withdrawn", status, 204)
check("the grantee no longer sees it", viewer.call("GET", "/api/v1/cases/" + case_id)[0], 404)

# --- audit shows who did what -------------------------------------------------------------------------------------------
_, audit = manager.call("GET", "/api/v1/audit?size=50&action=CASE_GRANT_CREATE")
check("the audit trail records the grant", any(e["targetId"] == case_id for e in audit["items"]), True)

journey.finish(sys.argv[1] if len(sys.argv) > 1 else "wp1_access_journey.json")
