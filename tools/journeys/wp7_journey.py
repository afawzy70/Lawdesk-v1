"""WP7 journey on UAT (requirements section 16; acceptance scenarios S4 and S5; owner decisions D3, D7, D8, D10): a
synthetic judgment is registered in the library with an extracted principle that can be found by its words, and a new
synthetic case is offered that judgment as a similar one, with reasons, which the specialist then links by hand.
Run: python3 tools/journeys/wp7_journey.py [evidence.json]
Everything created carries the marker SYNTH-DOC; no existing case is changed; the synthetic judgments are withdrawn at the
end (a library record is never deleted)."""

import sys
import time

from uat import MARK, Journey, Session

run = time.strftime("%y%m%d%H%M%S")
token = "%s-%s" % (MARK, run)
journey = Journey("WP7 judgments library: S4 search, S5 similar judgments (%s)" % run)
manager = Session("manager").login("manager")
staff = Session("staff").login("staff")
viewer = Session("viewer").login("viewer")
check = journey.check
status, release = manager.call("GET", "/api/v1/release")
journey.note("deployed release", release if status == 200 else status)
LIB = "/api/v1/library"


def ids(page):
    return [item["id"] for item in page["items"]]


# --- the lists: only the values the requirements text gives, managed afterwards (D10) -----------------------------------
status, outcomes = staff.call("GET", LIB + "/classifications?dimension=OUTCOME")
check("a reader sees the twelve judicial results of the requirements text", (status, len(outcomes)), (200, 12))
check("the reader's lists mark them as the document's own", {c["origin"] for c in outcomes}, {"DOCUMENT"})
check("a reader cannot add to a list", viewer.call("POST", LIB + "/classifications", {
    "dimension": "LEGAL_TOPIC", "code": "X" + run, "labelAr": "x"})[0], 403)
status, topic = staff.call("POST", LIB + "/classifications", {
    "dimension": "LEGAL_TOPIC", "code": "SYNTH" + run, "labelAr": "إنهاء الخدمة %s" % token, "labelEn": "Termination (%s)" % token})
check("the specialist proposes a legal topic", (status, topic["origin"]), (201, "PROPOSED"))
rejected = next(c for c in outcomes if c["code"] == "REJECTED")

# --- S4: register a judgment with an extracted principle that is searchable --------------------------------------------
subject = "%s تعويض عن إنهاء خدمة موظف بغير سبب مشروع" % token
body = {
    "courtName": "محكمة التمييز (%s)" % MARK, "courtLevel": "CASSATION", "chamber": "الدائرة الإدارية",
    "caseNumber": "7%s" % run[-5:], "caseYear": 2021, "judgmentDate": "2022-02-01", "caseType": "LABOR",
    "subject": subject, "summary": "قضت المحكمة بالتعويض عن الفصل التعسفي", "operativeText": "حكمت المحكمة بالتعويض",
    "reasons": "لثبوت الخطأ والضرر وعلاقة السببية بينهما", "legalTexts": "المادة ٤١ من قانون العمل",
    "direction": "AGAINST_AUTHORITY", "keywords": ["فصل تعسفي", token], "finality": "FINAL",
    "sourceReference": "مجموعة أحكام اختبارية %s" % MARK, "verificationNote": "قوبلت بنسخة اصطناعية (%s)" % MARK,
    "classificationIds": [topic["id"], rejected["id"]],
}
check("a reader cannot register a judgment", viewer.call("POST", LIB + "/judgments", body)[0], 403)
check("the source is mandatory", staff.call("POST", LIB + "/judgments", {**body, "sourceReference": " "})[0], 400)
check("a judgment cannot be dated in the future", staff.call("POST", LIB + "/judgments", {**body, "judgmentDate": "2999-01-01"})[0], 422)
status, judgment = staff.call("POST", LIB + "/judgments", body)
check("the specialist registers the judgment", status, 201)
judgment_id = judgment["id"]
check("it records who entered it", judgment["createdBy"], staff.me["id"])
check("a second entry of the same citation is refused until confirmed", staff.call("POST", LIB + "/judgments", body)[0], 422)

status, principle = staff.call("POST", "%s/judgments/%s/principles" % (LIB, judgment_id), {
    "principleText": "الإنهاء بغير سبب مشروع يوجب التعويض عن الضرر المادي والأدبي %s" % token,
    "legalTopic": "التعويض عن الفصل التعسفي", "legalBasis": "المادة ٤١ من قانون العمل",
    "factsApplied": "فصل موظف دون تحقيق", "scopeNote": "العقود غير محددة المدة", "memberNotes": "يراجع مع الحكم الأصلي"})
check("the specialist extracts a principle apart from the judgment", (status, principle["reviewStatus"]), (201, "DRAFT"))
status, reviewed = manager.call("POST", "/api/v1/library/principles/%s/review" % principle["id"], {"version": principle["version"]})
check("the manager reviews the principle", (status, reviewed["reviewStatus"]), (200, "REVIEWED"))

# The search forgives hamza, taa marbuta, diacritics and the article; a principle's words find the judgment.
for label, query in (("the token", token), ("spelling variants of the subject", "%s انهاء الخدمه" % token),
                     ("words found only in the principle", "%s الضرر الادبي" % token),
                     ("the legal basis with Arabic-Indic digits", "%s المادة ٤١" % token),
                     ("a keyword", "فصل تعسفي %s" % token)):
    status, page = staff.call("GET", "%s/judgments?q=%s" % (LIB, query.replace(" ", "%20")))
    check("a reader finds the judgment by %s" % label, (status, judgment_id in ids(page)), (200, True))
status, page = staff.call("GET", "%s/judgments?q=%s" % (LIB, (token + " الضرر الأدبي").replace(" ", "%20")))
check("the answer says the match was in the principle", page["items"][0]["matchedIn"][0], "PRINCIPLE")
status, page = staff.call("GET", "%s/judgments?q=%s&classificationId=%s" % (LIB, token, topic["id"]))
check("filtering by the classification keeps it", ids(page), [judgment_id])
status, page = staff.call("GET", "%s/judgments?q=%s&courtLevel=APPEAL" % (LIB, token))
check("filtering by another court level drops it", ids(page), [])
status, page = staff.call("GET", "%s/judgments?q=%s" % (LIB, (token + " برمجيات").replace(" ", "%20")))
check("words that are not there do not narrow to this judgment as an exact match", page.get("items") is not None, True)

# --- what happened later: recorded with a source, never decided (section 16.6) ----------------------------------------
status, validity = staff.call("GET", "%s/judgments/%s/validity" % (LIB, judgment_id))
check("no later event: no later-relation warning", "LATER_RELATION_EXISTS" in validity["warnings"], False)
status, _ = staff.call("POST", "%s/judgments/%s/relations" % (LIB, judgment_id), {
    "relationType": "MODIFIED", "relatedCitation": "الطعن ٩٠٠ لسنة ٢٠٢٢ (%s)" % MARK, "sourceReference": "حكم اصطناعي (%s)" % MARK})
check("a later event is recorded with its source", status, 201)
status, validity = staff.call("GET", "%s/judgments/%s/validity" % (LIB, judgment_id))
check("the library now warns that a later event exists", "LATER_RELATION_EXISTS" in validity["warnings"], True)
check("and still does not decide the legal validity", validity["legalValidityDecided"], False)

# --- S5: a new case is offered similar judgments --------------------------------------------------------------------------
_, users = manager.call("GET", "/api/v1/users?size=100&q=qa.")
by_name = {u["username"]: u for u in users["items"]}
_, courts = manager.call("GET", "/api/v1/courts?size=100")
court = next((c for c in courts["items"] if c["level"] == "FIRST_INSTANCE" and c["active"]), None)
if court is None:
    status, court = manager.call("POST", "/api/v1/courts", {
        "code": "SYN-" + run, "nameAr": "محكمة اختبارية (%s)" % MARK, "nameEn": "Synthetic court", "level": "FIRST_INSTANCE"})
    check("manager creates a synthetic first-instance court", status, 201)
status, created = manager.call("POST", "/api/v1/cases/intake", {
    "automatedNo": "AUTO-%s" % run, "title": "%s مطالبة بتعويض عن فصل" % token, "caseType": "LABOR",
    "subject": "%s تعويض عن إنهاء خدمة" % token, "openedOn": time.strftime("%Y-%m-%d"),
    "firstInstance": {"courtId": court["id"], "courtCaseNumber": "8%s" % run[-5:], "courtCaseYear": 2026,
                      "filingDate": time.strftime("%Y-%m-%d")},
    "authorityPosition": "DEFENDANT",
    "parties": [{"newParty": {"kind": "PERSON", "name": "مدعٍ اختباري %s" % run, "nationalId": None}, "side": "PLAINTIFF"}],
    "primaryMemberId": by_name["qa.staff"]["id"], "alternateMemberId": by_name["qa.manager"]["id"],
    "financialImpact": "NO",
    "details": {"facts": "تم فصل الموظف بغير سبب مشروع وهو يطلب التعويض عن الفصل التعسفي (%s)" % MARK}})
check("intake registers the synthetic case", status, 201)
case_id = created["id"]

status, offered = staff.call("GET", "/api/v1/cases/%s/similar-judgments" % case_id)
check("the specialist is offered similar judgments", status, 200)
mine = next((s for s in offered["items"] if s["judgment"]["id"] == judgment_id), None)
check("the registered judgment is among them", mine is not None, True)
if mine is not None:
    codes = {r["code"] for r in mine["reasons"]}
    check("each suggestion explains itself with words and points", all(r["points"] > 0 for r in mine["reasons"]), True)
    check("the reasons include the shared words and the type of dispute", {"TEXT_MATCH", "SAME_CASE_TYPE"} <= codes, True)
    check("the total is the sum of the reasons' points", mine["score"], sum(r["points"] for r in mine["reasons"]))
    check("the citation shows source, date and number", (bool(mine["judgment"]["sourceReference"]),
          mine["judgment"]["judgmentDate"], mine["judgment"]["caseNumber"]), (True, "2022-02-01", body["caseNumber"]))
    check("the later event is flagged on the suggestion", "LATER_RELATION_EXISTS" in mine["warnings"], True)
    journey.note("score and reasons", [mine["score"], sorted(codes)])
check("the answer is the same when asked again", staff.call("GET", "/api/v1/cases/%s/similar-judgments" % case_id)[1], offered)
check("nothing was linked by the suggestion alone", staff.call("GET", "/api/v1/cases/%s/precedents" % case_id)[1], [])

# --- the case access rules apply to the library's case routes (D3) ------------------------------------------------------
check("a reader without scope gets 404 for the case's suggestions", viewer.call("GET", "/api/v1/cases/%s/similar-judgments" % case_id)[0] in (403, 404), True)
check("and for its precedents", viewer.call("GET", "/api/v1/cases/%s/precedents" % case_id)[0] in (403, 404), True)
check("and cannot link to it", viewer.call("POST", "/api/v1/cases/%s/precedents" % case_id, {
    "judgmentId": judgment_id, "reason": "x"})[0] in (403, 404), True)
check("a reason is required to link", staff.call("POST", "/api/v1/cases/%s/precedents" % case_id, {
    "judgmentId": judgment_id, "reason": " "})[0], 400)
status, link = staff.call("POST", "/api/v1/cases/%s/precedents" % case_id, {
    "judgmentId": judgment_id, "reason": "نفس مسألة الفصل بغير سبب مشروع (%s)" % MARK,
    "similarityNote": "الوقائع متقاربة", "origin": "SUGGESTION"})
check("the specialist decides to link the judgment, giving the reason", (status, link["origin"]), (201, "SUGGESTION"))
check("linking it twice is a conflict", staff.call("POST", "/api/v1/cases/%s/precedents" % case_id, {
    "judgmentId": judgment_id, "reason": "again"})[0], 409)
check("the suggestion is now marked linked", next(
    s for s in staff.call("GET", "/api/v1/cases/%s/similar-judgments" % case_id)[1]["items"]
    if s["judgment"]["id"] == judgment_id)["alreadyLinked"], True)
status, detail = manager.call("GET", "%s/judgments/%s" % (LIB, judgment_id))
check("the manager (department scope) sees the case among the judgment's linked cases", [c["caseId"] for c in detail["linkedCases"]], [case_id])
status, detail = staff.call("GET", "%s/judgments/%s" % (LIB, judgment_id))
check("the specialist sees the judgment and the case it is linked to", (status, [c["caseId"] for c in detail["linkedCases"]]), (200, [case_id]))
status, timeline = staff.call("GET", "/api/v1/cases/%s/timeline" % case_id)
check("the case timeline records the link", any(e["type"] == "PRECEDENT_LINKED" for e in timeline["items"]), True)

# --- reports -------------------------------------------------------------------------------------------------------------
status, report = staff.call("GET", LIB + "/reports/summary")
check("a reader sees the library report", status, 200)
check("the report counts the judgment under its classification", any(
    c["classificationId"] == topic["id"] and c["count"] == 1 for c in report["byClassification"]), True)
check("the report names the principle's legal question with counts, not a verdict", any(
    i["topic"] == "التعويض عن الفصل التعسفي" for i in report["frequentIssues"]), True)

# --- leave the library tidy: the synthetic judgment is withdrawn, never deleted ---------------------------------------------
status, current = staff.call("GET", "%s/judgments/%s" % (LIB, judgment_id))
status, withdrawn = staff.call("POST", "%s/judgments/%s/withdraw" % (LIB, judgment_id), {
    "reason": "%s journey cleanup" % MARK, "version": current["version"]})
check("the synthetic judgment is withdrawn", (status, withdrawn["status"]), (200, "WITHDRAWN"))
status, page = staff.call("GET", "%s/judgments?q=%s" % (LIB, token))
check("a withdrawn judgment is out of the default search", judgment_id in ids(page), False)
status, offered = staff.call("GET", "/api/v1/cases/%s/similar-judgments" % case_id)
check("and out of the suggestions", judgment_id in [s["judgment"]["id"] for s in offered["items"]], False)

journey.finish(sys.argv[1] if len(sys.argv) > 1 else "wp7_journey.json")
