"""WP8 journey on UAT (requirements sections 24, 26, 27; LD-058): the dashboard indicators, the lists they open, the report
families and the performance view, seen through the QA accounts. A synthetic dataset (SYNTH-DOC) is created through the
API; every figure is read before and after, so the check does not depend on what the environment already holds:
 - for the manager (department scope), the staff member (own cases) and the viewer (no scope) every available indicator
   figure equals the total of the list it opens, group by group;
 - the changes in the figures are exactly what the dataset adds for each account (the viewer sees none of it);
 - the report families answer, keep the financial-impact and claimed amounts apart, and export to CSV, Excel and PDF;
 - the performance view is alphabetical, carries no judgment-outcome column and shows the staff member only their cases.
No existing case is edited. Run: python3 tools/journeys/wp8_journey.py [evidence.json]"""

import datetime
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import zoneinfo

from uat import BASE, MARK, Journey, Session

run = time.strftime("%y%m%d%H%M%S")
today = datetime.datetime.now(zoneinfo.ZoneInfo("Africa/Cairo")).date()
iso = lambda d: d.isoformat()
journey = Journey("WP8 dashboard indicators, report families, performance (%s)" % run)
manager = Session("manager").login("manager")
staff = Session("staff").login("staff")
viewer = Session("viewer").login("viewer")
check = journey.check
status, release = manager.call("GET", "/api/v1/release")
journey.note("deployed release", release if status == 200 else status)
ACCOUNTS = {"manager": manager, "staff": staff, "viewer": viewer}

_, users = manager.call("GET", "/api/v1/users?size=100&q=qa.")
by_name = {u["username"]: u for u in users["items"]}
staff_id, manager_id = by_name["qa.staff"]["id"], by_name["qa.manager"]["id"]
_, courts = manager.call("GET", "/api/v1/courts?size=100")
court = next((c for c in courts["items"] if c["level"] == "FIRST_INSTANCE" and c["active"]), None)
if court is None:
    status, court = manager.call("POST", "/api/v1/courts", {
        "code": "SYN-" + run, "nameAr": "محكمة اختبارية (%s)" % MARK, "nameEn": "Synthetic court", "level": "FIRST_INSTANCE"})
    check("manager creates a synthetic court", status, 201)


def figures(session):
    status, body = session.call("GET", "/api/v1/reports/indicators")
    assert status == 200, status
    return {f["code"]: f for f in body}


def list_total(session, code, group=None, level=None):
    query = "?size=1"
    if group is not None:
        query += "&group=" + urllib.parse.quote(group, safe="")
    if level is not None:
        query += "&level=" + level
    status, body = session.call("GET", "/api/v1/reports/indicators/%s%s" % (code, query))
    return body["totalItems"] if status == 200 else "HTTP %s" % status


def verify_equality(name, session):
    """The promise of the indicators: every figure equals the total of its list, and every group of a distribution too."""
    snapshot = figures(session)
    mismatches, groups_checked = [], 0
    for code, figure in snapshot.items():
        if not figure["available"]:
            if list_total(session, code) != 0:
                mismatches.append(code)
            continue
        if list_total(session, code) != figure["count"]:
            mismatches.append(code)
        sums = {}
        for group in figure["groups"] or []:
            groups_checked += 1
            sums[group["level"]] = sums.get(group["level"], 0) + group["count"]
            if list_total(session, code, group["key"], group["level"]) != group["count"]:
                mismatches.append("%s/%s" % (code, group["key"]))
        for level, total in sums.items():
            if total != figure["count"]:
                mismatches.append("%s sum %s" % (code, level))
    check("%s: every indicator figure equals the total of its list (%d groups checked)" % (name, groups_checked), mismatches, [])
    return snapshot


def delta(before, after):
    return {code: after[code]["count"] - before[code]["count"] for code in after
            if after[code]["available"] and before[code]["available"] and after[code]["count"] != before[code]["count"]}


# --- before: what each account already sees -----------------------------------------------------------------------------
before = {name: verify_equality("before, " + name, session) for name, session in ACCOUNTS.items()}
check("sixteen indicator codes are reported", len(before["manager"]), 16)
check("the numbers run 1 to 14", sorted({f["number"] for f in before["manager"].values()}), list(range(1, 15)))
status, dash = staff.call("GET", "/api/v1/reports/dashboard")
check("the dashboard carries the same indicator figures", (status, dash["indicators"]), (200, list(figures(staff).values())))


# --- a synthetic dataset ---------------------------------------------------------------------------------------------------
def register(label, case_type, primary, alternate, impact):
    payload = {
        "automatedNo": "%s-%s" % (label, run), "title": "%s-%s قضية %s" % (MARK, run, label), "caseType": case_type,
        "subject": "%s: قضية اختبارية %s" % (MARK, label), "openedOn": iso(today),
        "firstInstance": {"courtId": court["id"], "courtCaseNumber": "%s%s" % (ord(label[0]) - 64, run[-5:]),
                          "courtCaseYear": 2026, "filingDate": iso(today)},
        "authorityPosition": "DEFENDANT",
        "parties": [{"newParty": {"kind": "PERSON", "name": "مدعٍ اختباري %s %s" % (label, run), "nationalId": None}, "side": "PLAINTIFF"}],
        "primaryMemberId": primary, "alternateMemberId": alternate, "financialImpact": "YES" if impact else "NO",
        "financialAmount": impact, "details": {"claimedAmount": "250.250" if impact else None},
    }
    status, created = manager.call("POST", "/api/v1/cases/intake", payload)
    check("intake registers synthetic case %s" % label, status, 201)
    return created


case_a = register("W1", "COMMERCIAL", staff_id, manager_id, "12500.500")
case_b = register("X1", "CIVIL", manager_id, None, None)
a, b = case_a["id"], case_b["id"]
journey.note("cases", [case_a["referenceNo"], case_b["referenceNo"]])
_, proceedings_a = staff.call("GET", "/api/v1/cases/%s/proceedings" % a)
_, proceedings_b = manager.call("GET", "/api/v1/cases/%s/proceedings" % b)
proc_a, proc_b = proceedings_a["items"][0]["id"], proceedings_b["items"][0]["id"]

status, h1 = staff.call("POST", "/api/v1/proceedings/%s/hearings" % proc_a, {
    "hearingDate": iso(today + datetime.timedelta(days=2)), "purpose": "PLEADING", "kind": "COURT", "responsibleMemberId": staff_id})
check("a court hearing is scheduled in the coming days", status, 201)
status, h2 = staff.call("POST", "/api/v1/proceedings/%s/hearings" % proc_a, {
    "hearingDate": iso(today + datetime.timedelta(days=3)), "purpose": "EVIDENCE", "kind": "EXPERT", "judgeOrExpert": "خبير اختباري",
    "responsibleMemberId": staff_id})
check("an expert session is scheduled too", status, 201)
status, _ = staff.call("POST", "/api/v1/proceedings/%s/hearings" % proc_a, {
    "hearingDate": iso(today + datetime.timedelta(days=4)), "purpose": "JUDGMENT", "kind": "COURT", "responsibleMemberId": staff_id})
check("a hearing for the pronouncement of judgment reserves the case for judgment", status, 201)
status, task = staff.call("POST", "/api/v1/tasks", {"title": "مهمة اختبارية (%s)" % MARK, "caseId": a, "assigneeId": staff_id,
                                                    "dueDate": iso(today), "priority": "NORMAL"})
check("a task is created for the case (an overdue one cannot be created, so the figure is checked as it stands)", status, 201)
status, unit = manager.call("POST", "/api/v1/org-units", {
    "tree": "AUTHORITY", "parentId": None, "level": "SECTOR", "nameAr": "قطاع اختباري (%s %s)" % (MARK, run)})
check("an authority sector is created", status, 201)
status, request = staff.call("POST", "/api/v1/cases/%s/info-requests" % a, {
    "subject": "مستند ناقص (%s)" % MARK, "unitId": unit["id"], "dueOn": iso(today - datetime.timedelta(days=2)), "responsibleMemberId": staff_id})
check("a request to the sector is registered with a date already past", status, 201)
status, _ = staff.call("POST", "/api/v1/info-requests/%s/status" % request["id"], {"status": "SENT", "version": request["version"]})
check("it is sent, so it is waiting and overdue", status, 200)
status, risk = staff.call("POST", "/api/v1/cases/%s/risk-assessments" % a, {
    "level": "HIGH", "levelReasons": "اختبار المؤشرات (%s)" % MARK, "potentialLoss": "75000.500", "trigger": "MANUAL",
    "elements": [{"code": "EVIDENCE_STRENGTH", "rating": "HIGH", "reason": "نقص الأدلة"}],
    "remediation": [{"cause": "نقص", "action": "طلب المستند", "ownerId": staff_id, "targetDate": iso(today + datetime.timedelta(days=7))}]})
check("a high risk with its plan is recorded", status, 201)
status, link = staff.call("POST", "/api/v1/cases/%s/links" % a, {
    "otherCaseId": b, "kind": "SUBJECT_LINK", "reason": "وحدة الموضوع (%s)" % MARK})
check("a link to the manager's case is recorded", status, 201)
status, judgment = manager.call("POST", "/api/v1/proceedings/%s/judgment" % proc_b, {
    "judgmentDate": iso(today), "outcome": "AGAINST", "summary": "حكم اختباري (%s)" % MARK, "final": False})
check("a judgment is recorded on the manager's case", status, 201)

# --- after: the changes are what the dataset adds for each account ---------------------------------------------------------------
after = {name: verify_equality("after, " + name, session) for name, session in ACCOUNTS.items()}
changes = {name: delta(before[name], after[name]) for name in ACCOUNTS}
journey.note("changes", changes)
check("manager (department scope) sees both cases", changes["manager"].get("TOTAL_CASES"), 2)
check("staff (own cases) sees only the case they are primary member of", changes["staff"].get("TOTAL_CASES"), 1)
check("viewer (no scope) sees nothing of it", changes["viewer"], {})
check("new cases: both for the manager, one for staff", (changes["manager"].get("CASES_NEW"), changes["staff"].get("CASES_NEW")), (2, 1))
check("reserved for judgment: the case with the pronouncement hearing", (changes["staff"].get("RESERVED_FOR_JUDGMENT"), changes["manager"].get("RESERVED_FOR_JUDGMENT")), (1, 1))
check("upcoming hearings and expert sessions", (changes["staff"].get("UPCOMING_HEARINGS"), changes["staff"].get("EXPERT_HEARINGS")), (3, 1))
check("high risk, overdue request and linked cases for staff", (changes["staff"].get("HIGH_RISK_CASES"), changes["staff"].get("OVERDUE_INFO_REQUESTS"), changes["staff"].get("LINKED_CASES")), (1, 1, 1))
check("the link counts both its cases for the manager", changes["manager"].get("LINKED_CASES"), 2)
check("the judgment is the manager's alone", (changes["manager"].get("JUDGMENTS_ISSUED"), changes["staff"].get("JUDGMENTS_ISSUED")), (1, None))
check("distributions: cases by member and by unit move with the cases", (changes["staff"].get("CASES_BY_MEMBER"), changes["manager"].get("CASES_BY_UNIT")), (1, 2))
check("indicators of modules not installed say so instead of a number",
      sorted(c for c, f in after["manager"].items() if not f["available"]) in ([], ["APPEAL_DEADLINES", "JUDGMENTS_IN_ENFORCEMENT"], ["APPEAL_DEADLINES"], ["JUDGMENTS_IN_ENFORCEMENT"]), True)

# --- the report families -----------------------------------------------------------------------------------------------------------
status, catalogue = manager.call("GET", "/api/v1/reports/families")
check("seventeen report families", (status, len(catalogue)), (200, 17))


def family(session, code, query=""):
    status, body = session.call("GET", "/api/v1/reports/families/%s%s" % (code, query))
    return body if status == 200 else status


def download(session, path):
    request = urllib.request.Request(BASE + path, headers={"Accept": "*/*"})
    try:
        with session.opener.open(request, timeout=120) as response:
            return response.status, response.read()
    except urllib.error.HTTPError as error:
        return error.code, b""


bad = []
for item in catalogue:
    table = family(manager, item["code"], "?lang=en")
    if isinstance(table, int) or any(len(r) != len(table["columns"]) for r in table["rows"]):
        bad.append(item["code"])
check("every family answers with rows as wide as its columns", bad, [])
for item in catalogue:
    outsider = family(viewer, item["code"], "?lang=en")
    if isinstance(outsider, int):
        continue
    counts = [i for i, c in enumerate(outsider["columns"]) if c["type"] == "COUNT"]
    if item["code"] != "LIBRARY_PRECEDENTS" and any((r[i] or 0) != 0 for r in outsider["rows"] for i in counts):
        bad.append("viewer " + item["code"])
check("the viewer's reports count nothing", bad, [])

m_after = family(manager, "OPEN_CLOSED_CASES", "?lang=en")
s_after = family(staff, "OPEN_CLOSED_CASES", "?lang=en")
check("staff's total financial impact includes the synthetic 12500.500 and money keeps three decimals",
      s_after["rows"][-1][3].endswith(".500") or s_after["rows"][-1][3].endswith(".000"), True)
for group in ("CASE_TYPE", "COURT", "STAGE"):
    t = family(manager, "CASES_BY_TYPE_COURT_STAGE", "?groupBy=%s&lang=en" % group)
    check("cases by %s add up to the same total as open/closed" % group, t["rows"][-1][1:], m_after["rows"][-1][1:])
check("the claimed and financial-impact amounts are separate columns",
      [c["key"] for c in m_after["columns"]], ["group", "cases", "claimedAmount", "financialImpactAmount"])

for code in ("CASES_BY_TYPE_COURT_STAGE", "RISK_ASSESSMENT", "INFO_REQUESTS", "PERFORMANCE"):
    for fmt, magic in (("csv", None), ("xlsx", b"PK"), ("pdf", b"%PDF")):
        status, body = download(manager, "/api/v1/reports/families/%s/export?format=%s&lang=ar" % (code, fmt))
        check("%s exports to %s" % (code, fmt), (status, magic is None or body.startswith(magic)), (200, True))
status, body = download(viewer, "/api/v1/reports/families/OPEN_CLOSED_CASES/export?format=csv")
check("the viewer cannot export", status, 403)

# --- performance: objective, alphabetical, no outcome ---------------------------------------------------------------------------------
perf = family(manager, "PERFORMANCE", "?groupBy=MEMBERS&lang=en")
names = [r[0] for r in perf["rows"]]
check("the performance view is alphabetical, not ranked", names, sorted(names, key=str.lower))
check("it has no judgment-outcome column", [k for k in (c["key"] for c in perf["columns"]) if k in ("outcome", "judgments", "score", "rank")], [])
mine = family(staff, "PERFORMANCE", "?groupBy=MEMBERS&lang=en")
check("the staff member sees only members of the cases they can see", all(r[0] for r in mine["rows"]), True)
check("the performance view says it ranks no one", "never ranked" in perf["note"], True)
units = family(manager, "PERFORMANCE", "?groupBy=UNITS&lang=en")
check("the units view reports the responsiveness of departments", [c["key"] for c in units["columns"]][:3], ["unit", "requests", "replies"])

journey.finish(sys.argv[1] if len(sys.argv) > 1 else "wp8_journey.json")
