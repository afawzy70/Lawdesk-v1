"""WP1d journey on UAT (owner decision D2): enforcement/constitutional/grievance stages beside the ladder."""
import sys
import time

from uat import MARK, Journey, Session

run = time.strftime("%y%m%d%H%M%S")
journey = Journey("WP1d additional stages (%s)" % run)
manager = Session("manager").login("manager")
check = journey.check
journey.note("deployed release", manager.call("GET", "/api/v1/release")[1])
_, users = manager.call("GET", "/api/v1/users?size=100&q=qa.")
by_name = {u["username"]: u for u in users["items"]}
_, courts = manager.call("GET", "/api/v1/courts?size=100")
first = next(c for c in courts["items"] if c["level"] == "FIRST_INSTANCE" and c["active"])
today = time.strftime("%Y-%m-%d")
status, case = manager.call("POST", "/api/v1/cases/intake", {
    "automatedNo": "AUTO-%s" % run, "title": "%s-%s قضية مسارات" % (MARK, run), "caseType": "CONSTITUTIONAL",
    "openedOn": today, "firstInstance": {"courtId": first["id"], "courtCaseNumber": "5%s" % run[-5:],
                                         "courtCaseYear": 2026, "filingDate": today},
    "authorityPosition": "DEFENDANT", "parties": [], "primaryMemberId": by_name["qa.manager"]["id"],
    "financialImpact": "NO"})
check("a case whose nature is constitutional is registered (nature is separate from the stage)", status, 201)
_, procs = manager.call("GET", "/api/v1/cases/%s/proceedings" % case["id"])
parent = procs["items"][0]["id"]
for level in ("ENFORCEMENT", "GRIEVANCE", "CONSTITUTIONAL"):
    status, court = manager.call("POST", "/api/v1/courts", {
        "code": "%s-%s" % (level[:3], run[-6:]), "nameAr": "جهة اختبارية %s (%s)" % (level, MARK),
        "nameEn": "Synthetic %s" % level, "level": level})
    check("a %s-level court or body can be recorded" % level, status, 201)
    status, proc = manager.call("POST", "/api/v1/cases/%s/proceedings" % case["id"], {
        "stage": level, "courtId": court["id"], "courtCaseNumber": "9%s" % run[-5:], "courtCaseYear": 2026,
        "filingDate": today, "parentProceedingId": parent})
    check("a %s proceeding follows the first instance" % level, (status, proc.get("stage")), (201, level))
status, body = manager.call("POST", "/api/v1/cases/%s/proceedings" % case["id"], {
    "stage": "ENFORCEMENT", "courtId": first["id"], "courtCaseNumber": "1", "courtCaseYear": 2026,
    "filingDate": today, "parentProceedingId": parent})
check("the stage must match the court's level", (status, body.get("rule")), (422, "COURT_LEVEL_MISMATCH"))
journey.finish(sys.argv[1] if len(sys.argv) > 1 else "wp1d_stages_journey.json")
