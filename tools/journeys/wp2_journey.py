"""WP2 journey on UAT (requirements sections 5, 9, 17, 20; scenarios S1, S2, S3, S8, S13): one synthetic case with three
claims, a first-instance judgment and an appeal judgment giving different results, amended claims, defences linked to
documents, facts and evidence, gap detection and the access rules, seen through three accounts.
Run: python3 tools/journeys/wp2_journey.py [evidence.json]. Creates only records marked SYNTH-DOC and never edits an
existing case; the only shared record it touches is one managed-list value, which it deactivates again."""

import io
import json
import sys
import time
import urllib.error
import urllib.request
import uuid

from uat import BASE, MARK, Journey, Session

run = time.strftime("%y%m%d%H%M%S")
today = time.strftime("%Y-%m-%d")
journey = Journey("WP2 claims, judgment results, defences and evidence (%s)" % run)
manager = Session("manager").login("manager")
staff = Session("staff").login("staff")
viewer = Session("viewer").login("viewer")
check = journey.check
status, release = manager.call("GET", "/api/v1/release")
journey.note("deployed release", release if status == 200 else status)

PDF = b"%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF\n"


def upload(session, case_id, title):
    """Attaches a minimal synthetic PDF through the real multipart endpoint; returns the document id."""
    boundary = "----synth" + uuid.uuid4().hex
    body = io.BytesIO()
    for name, filename, content, ctype in (("title", None, title.encode(), None), ("file", "synth.pdf", PDF, "application/pdf")):
        body.write(("--%s\r\n" % boundary).encode())
        disposition = 'form-data; name="%s"' % name + ('; filename="%s"' % filename if filename else "")
        body.write(("Content-Disposition: %s\r\n" % disposition).encode())
        if ctype:
            body.write(("Content-Type: %s\r\n" % ctype).encode())
        body.write(b"\r\n" + content + b"\r\n")
    body.write(("--%s--\r\n" % boundary).encode())
    request = urllib.request.Request(
        BASE + "/api/v1/cases/%s/documents" % case_id, data=body.getvalue(), method="POST",
        headers={"Content-Type": "multipart/form-data; boundary=" + boundary, "X-XSRF-TOKEN": session._xsrf(),
                 "Accept": "application/json"})
    try:
        with session.opener.open(request, timeout=60) as response:
            return json.loads(response.read())["id"]
    except urllib.error.HTTPError as error:
        raise SystemExit("document upload answered %d" % error.code)


def results_of(session, judgment_id):
    status, body = session.call("GET", "/api/v1/judgments/%s/claim-results" % judgment_id)
    return {row["claimSeq"]: row for row in body} if status == 200 else status


# --- the baseline of the statistics, so the journey can prove its own contribution -------------------------------------
_, before = manager.call("GET", "/api/v1/reports/claim-results")
_, viewer_before = viewer.call("GET", "/api/v1/reports/claim-results")

# --- a synthetic case: staff is the specialist, the manager the alternate ---------------------------------------------
_, users = manager.call("GET", "/api/v1/users?size=100&q=qa.")
by_name = {u["username"]: u for u in users["items"]}
_, courts = manager.call("GET", "/api/v1/courts?size=100")
first_court = next((c for c in courts["items"] if c["level"] == "FIRST_INSTANCE" and c["active"]), None)
if first_court is None:
    _, first_court = manager.call("POST", "/api/v1/courts", {
        "code": "SYN1-" + run, "nameAr": "محكمة اختبارية (%s)" % MARK, "nameEn": "Synthetic court", "level": "FIRST_INSTANCE"})
appeal_court = next((c for c in courts["items"] if c["level"] == "APPEAL" and c["active"]), None)
if appeal_court is None:
    _, appeal_court = manager.call("POST", "/api/v1/courts", {
        "code": "SYN2-" + run, "nameAr": "محكمة استئناف اختبارية (%s)" % MARK, "nameEn": "Synthetic appeal court", "level": "APPEAL"})
status, created = manager.call("POST", "/api/v1/cases/intake", {
    "automatedNo": "AUTO-%s" % run, "title": "%s-%s قضية طلبات ودفوع" % (MARK, run), "caseType": "ADMINISTRATIVE",
    "subject": "%s: طعن اختباري على قرار إداري" % MARK, "openedOn": today,
    "firstInstance": {"courtId": first_court["id"], "courtCaseNumber": "8%s" % run[-5:], "courtCaseYear": 2026, "filingDate": today},
    "authorityPosition": "DEFENDANT",
    "parties": [{"newParty": {"kind": "PERSON", "name": "مدعٍ اختباري %s" % run, "nationalId": None}, "side": "PLAINTIFF"}],
    "primaryMemberId": by_name["qa.staff"]["id"], "alternateMemberId": by_name["qa.manager"]["id"],
    "financialImpact": "NO"})
check("intake registers the synthetic case", status, 201)
case_id = created["id"]
_, stages = staff.call("GET", "/api/v1/cases/%s/proceedings" % case_id)
first_stage = stages["items"][0]["id"]
_, parties = staff.call("GET", "/api/v1/cases/%s/parties" % case_id)
plaintiff = next(p["partyId"] for p in parties if not p.get("authority"))

# --- S1: three claims, the specialist adds them --------------------------------------------------------------------------
claims = {}
statement = upload(staff, case_id, "%s صحيفة الدعوى" % MARK)
for seq, (text, kind) in enumerate([("تعويض عن الضررين المادي والأدبي (%s)" % MARK, "PRIMARY"),
                                    ("رد الرسوم المسددة (%s)" % MARK, "ALTERNATIVE"),
                                    ("المصروفات (%s)" % MARK, "ANCILLARY")], start=1):
    status, body = staff.call("POST", "/api/v1/cases/%s/claims" % case_id, {
        "text": text, "kind": kind, "submittedByPartyId": plaintiff, "submittedOn": today, "stageId": first_stage,
        "sourceDocumentId": statement if seq == 1 else None})
    check("the specialist adds claim %d" % seq, (status, body["seq"] if status == 201 else None), (201, seq))
    claims[seq] = body["id"]
_, listed = staff.call("GET", "/api/v1/cases/%s/claims" % case_id)
check("the case lists its three claims in order", [c["seq"] for c in listed], [1, 2, 3])

# --- S2: an amendment keeps the previous text, the date and the user ------------------------------------------------------
original = listed[0]["text"]
status, amended = staff.call("PUT", "/api/v1/claims/%s" % claims[1], {
    "text": "تعويض عن الضرر المادي (%s)" % MARK, "kind": "PRIMARY", "status": "STANDING", "reason": "حذف الضرر الأدبي (%s)" % MARK,
    # An amendment replaces the claim, so it carries over what does not change.
    "submittedByPartyId": plaintiff, "submittedOn": today, "stageId": first_stage, "sourceDocumentId": statement,
    "version": listed[0]["version"]})
check("the specialist amends claim 1", (status, amended["status"], amended["revision"]), (200, "AMENDED", 2))
_, revisions = staff.call("GET", "/api/v1/claims/%s/revisions" % claims[1])
check("both versions are kept, newest first", [r["rev"] for r in revisions], [2, 1])
check("the previous text is kept verbatim", revisions[1]["text"], original)
check("the change records the user and the date", (revisions[0]["changedBy"], bool(revisions[0]["changedAt"])),
      (by_name["qa.staff"]["id"], True))
check("a stale amendment is refused", staff.call("PUT", "/api/v1/claims/%s" % claims[1], {
    "text": "x", "kind": "PRIMARY", "status": "STANDING", "version": listed[0]["version"]})[0], 409)

# --- defences, facts, evidence (S3, S8) -----------------------------------------------------------------------------------
memo = upload(staff, case_id, "%s مذكرة الدفاع" % MARK)
receipt = upload(staff, case_id, "%s كشف الاستلام" % MARK)
status, defense = staff.call("POST", "/api/v1/cases/%s/defenses" % case_id, {
    "title": "عدم قبول الدعوى لرفعها بعد الميعاد (%s)" % MARK, "kind": "FORMAL", "argument": "الدفع بالميعاد",
    "memoDocumentId": memo, "raisedOn": today, "raisedStageId": first_stage, "preparedBy": by_name["qa.staff"]["id"]})
check("the specialist records a defence linked to a memo", (status, defense["memoDocumentTitle"]), (201, "%s مذكرة الدفاع" % MARK))
status, defense2 = staff.call("POST", "/api/v1/cases/%s/defenses" % case_id, {"title": "سلامة الإجراءات (%s)" % MARK, "kind": "SUBSTANTIVE"})
check("a second defence gets the next number", (status, defense2["seq"]), (201, 2))
status, fact = staff.call("POST", "/api/v1/cases/%s/facts" % case_id, {"text": "تاريخ إعلان القرار (%s)" % MARK, "factDate": today})
check("the specialist records a fact", status, 201)
status, evidence = staff.call("POST", "/api/v1/cases/%s/evidence" % case_id, {
    "name": "كشف الاستلام (%s)" % MARK, "role": "SUPPORTING", "importance": "DECISIVE", "documentId": receipt})
check("a document becomes evidence with a role and an importance", (status, evidence["role"]), (201, "SUPPORTING"))
link_fact = staff.call("POST", "/api/v1/evidence/%s/links" % evidence["id"], {"factId": fact["id"]})[0]
link_defense = staff.call("POST", "/api/v1/evidence/%s/links" % evidence["id"], {"defenseId": defense["id"]})[0]
check("the same document is linked to a fact and to a defence", (link_fact, link_defense), (201, 201))
check("linking twice is refused", staff.call("POST", "/api/v1/evidence/%s/links" % evidence["id"], {"defenseId": defense["id"]})[0], 409)
_, detail = staff.call("GET", "/api/v1/defenses/%s" % defense["id"])
check("the defence lists the document that supports it", [e["name"] for e in detail["evidence"]], ["كشف الاستلام (%s)" % MARK])
_, usage = staff.call("GET", "/api/v1/cases/%s/document-usage" % case_id)
used = {(u["documentId"], u["type"]): u for u in usage}
check("the case file shows the document as evidence with both links", len(used[(receipt, "EVIDENCE")]["links"]), 2)
check("the case file shows the memo and the claim's source", ((memo, "DEFENSE_MEMO") in used, (statement, "CLAIM_SOURCE") in used), (True, True))

# gaps: evidence needed and missing, followed up by a task
status, missing = staff.call("POST", "/api/v1/cases/%s/evidence" % case_id, {
    "name": "سجل حضور (%s)" % MARK, "role": "REQUIRED", "importance": "HIGH"})
_, gaps = staff.call("GET", "/api/v1/cases/%s/evidence/gaps" % case_id)
gap_kinds = {g["name"]: g["gap"] for g in gaps}
check("a required document not yet obtained is a gap", gap_kinds.get("سجل حضور (%s)" % MARK), "NOT_OBTAINED")
check("a document obtained but not submitted to the court is a gap", gap_kinds.get("كشف الاستلام (%s)" % MARK), "NOT_SUBMITTED")
status, task = staff.call("POST", "/api/v1/tasks", {
    "title": "طلب سجل الحضور (%s)" % MARK, "caseId": case_id, "assigneeId": by_name["qa.staff"]["id"], "priority": "NORMAL"})
check("a follow-up task is created for the case", status, 201)
status, followup = staff.call("POST", "/api/v1/evidence/%s/followups" % missing["id"], {"taskId": task["id"]})
check("the gap is linked to its follow-up task", (status, followup["taskId"]), (201, task["id"]))
status, _ = staff.call("PUT", "/api/v1/evidence/%s" % evidence["id"], {
    "name": "كشف الاستلام (%s)" % MARK, "role": "SUPPORTING", "importance": "DECISIVE", "documentId": receipt,
    "submittedToCourt": True, "submittedOn": today, "submittedIn": "مذكرة اليوم (%s)" % MARK, "version": evidence["version"]})
check("submitting the document to the court is recorded", status, 200)
_, gaps = staff.call("GET", "/api/v1/cases/%s/evidence/gaps" % case_id)
check("a submitted document is no longer a gap", "كشف الاستلام (%s)" % MARK in [g["name"] for g in gaps], False)

# --- managed lists (D10): the seeded values are proposals --------------------------------------------------------------
_, types = manager.call("GET", "/api/v1/lookups/JUDGMENT_TYPE")
check("every seeded judgment type is a proposal awaiting approval", all(t["origin"] == "PROPOSED" and t["needsApproval"] for t in types), True)
status, proposed = manager.call("POST", "/api/v1/lookups/JUDGMENT_TYPE", {"code": "SYNTH_DOC_%s" % run, "labelAr": "نوع اختباري (%s)" % MARK, "labelEn": "Synthetic type"})
check("the manager proposes a new value", (status, proposed["needsApproval"]), (201, True))

# --- S1: the first-instance judgment gives each claim its own result -------------------------------------------------------
status, judgment1 = manager.call("POST", "/api/v1/proceedings/%s/judgment" % first_stage, {
    "judgmentDate": today, "outcome": "PARTIAL", "summary": "حكم جزئي (%s)" % MARK, "final": False,
    "details": {"chamber": "الدائرة الإدارية الثالثة", "judgmentTypeId": types[0]["id"], "operativeText": "منطوق اختباري (%s)" % MARK,
                "reasons": "أسباب اختبارية", "costsAmount": "120.000", "lawyerFeesAmount": "300.000", "appealable": True, "notifiedOn": today,
                "status": "NOTIFIED"}})
check("the manager records the extended judgment", (status, judgment1["details"]["costsAmount"]), (201, "120.000"))
check("the judgment type carries its label from the managed list", judgment1["details"]["judgmentTypeLabelAr"], types[0]["labelAr"])
_, before_results = manager.call("GET", "/api/v1/judgments/%s/claim-results" % judgment1["id"])
check("before recording, every claim is listed without a result", [r["result"] for r in before_results], [None, None, None])
status, recorded = manager.call("PUT", "/api/v1/judgments/%s/claim-results" % judgment1["id"], {"results": [
    {"claimId": claims[1], "result": "REJECTED", "reasons": "أسباب الرفض", "operative": "ترفض"},
    {"claimId": claims[2], "result": "ACCEPTED", "awardedAmount": "75.125"},
    {"claimId": claims[3], "result": "PARTIAL", "awardedAmount": "10"}]})
check("each claim gets its own result", [r["result"] for r in recorded], ["REJECTED", "ACCEPTED", "PARTIAL"])
check("the awarded amount keeps three decimals as text", [r["awardedAmount"] for r in recorded], [None, "75.125", "10.000"])
status, positions = manager.call("PUT", "/api/v1/judgments/%s/defense-positions" % judgment1["id"], {"positions": [
    {"defenseId": defense["id"], "position": "REJECTED", "reasons": "أسباب المحكمة"},
    {"defenseId": defense2["id"], "position": "ACCEPTED"}]})
check("the court's position is recorded on each defence", [p["position"] for p in positions], ["REJECTED", "ACCEPTED"])

# --- S13: an appeal never replaces the first-instance results ---------------------------------------------------------------
status, appeal = manager.call("POST", "/api/v1/cases/%s/proceedings" % case_id, {
    "stage": "APPEAL", "courtId": appeal_court["id"], "courtCaseNumber": "7%s" % run[-5:], "courtCaseYear": 2026,
    "filingDate": today, "parentProceedingId": first_stage})
check("an appeal is recorded against the first-instance proceeding", status, 201)
status, judgment2 = manager.call("POST", "/api/v1/proceedings/%s/judgment" % appeal["id"], {
    "judgmentDate": today, "outcome": "IN_FAVOUR", "summary": "حكم الاستئناف (%s)" % MARK, "final": True})
check("the appeal judgment is recorded (legacy request, no details)", (status, judgment2["details"]["operativeText"]), (201, None))
status, recorded2 = manager.call("PUT", "/api/v1/judgments/%s/claim-results" % judgment2["id"], {"results": [
    {"claimId": claims[1], "result": "ACCEPTED", "awardedAmount": "900"}, {"claimId": claims[2], "result": "NOT_DECIDED"}]})
check("the appeal gives its own results", [r["result"] for r in recorded2], ["ACCEPTED", "NOT_DECIDED", None])
first_again = results_of(manager, judgment1["id"])
check("the first-instance results are unchanged", [first_again[i]["result"] for i in (1, 2, 3)], ["REJECTED", "ACCEPTED", "PARTIAL"])
manager.call("PUT", "/api/v1/judgments/%s/defense-positions" % judgment2["id"], {"positions": [{"defenseId": defense["id"], "position": "ACCEPTED"}]})
status, assertion = staff.call("PUT", "/api/v1/defenses/%s/assertions/%s" % (defense["id"], appeal["id"]), {"status": "RE_ASSERTED", "onDate": today})
check("the defence is re-asserted in the appeal", (status, assertion["stage"], assertion["status"]), (200, "APPEAL", "RE_ASSERTED"))
_, matrix = staff.call("GET", "/api/v1/cases/%s/claim-results" % case_id)
check("the case shows five results across the two stages, in stage order",
      [(r["stage"], r["claimSeq"], r["result"]) for r in matrix],
      [("FIRST_INSTANCE", 1, "REJECTED"), ("FIRST_INSTANCE", 2, "ACCEPTED"), ("FIRST_INSTANCE", 3, "PARTIAL"),
       ("APPEAL", 1, "ACCEPTED"), ("APPEAL", 2, "NOT_DECIDED")])
_, defense_detail = staff.call("GET", "/api/v1/defenses/%s" % defense["id"])
check("the defence keeps the court's position in each stage",
      [(p["stage"], p["position"]) for p in defense_detail["positions"]], [("FIRST_INSTANCE", "REJECTED"), ("APPEAL", "ACCEPTED")])
status, _ = manager.call("PUT", "/api/v1/judgments/%s" % judgment1["id"], {
    "judgmentDate": today, "outcome": "PARTIAL", "summary": "حكم جزئي (%s)" % MARK, "final": False, "version": judgment1["version"]})
_, kept = manager.call("GET", "/api/v1/judgments/%s" % judgment1["id"])
check("a legacy update without details keeps the extended fields", (status, kept["details"]["operativeText"]), (200, "منطوق اختباري (%s)" % MARK))

# --- statistics count the journey's own results, and only for those who may see the case ---------------------------------
_, after = manager.call("GET", "/api/v1/reports/claim-results")
count = lambda stats, result: next(r["count"] for r in stats["byResult"] if r["result"] == result)
check("the statistics grew by the five results recorded", after["total"] - before["total"], 5)
check("... two of them accepted", count(after, "ACCEPTED") - count(before, "ACCEPTED"), 2)
_, viewer_after = viewer.call("GET", "/api/v1/reports/claim-results")
check("a user who cannot see the case counts none of it", viewer_after.get("total", 0) if isinstance(viewer_after, dict) else 0, viewer_before.get("total", 0) if isinstance(viewer_before, dict) else 0)

# --- access (D3): the viewer holds no scope and no grant -----------------------------------------------------------------
for label, path in (("claims", "/api/v1/cases/%s/claims" % case_id), ("defences", "/api/v1/cases/%s/defenses" % case_id),
                    ("facts", "/api/v1/cases/%s/facts" % case_id), ("evidence", "/api/v1/cases/%s/evidence" % case_id),
                    ("evidence gaps", "/api/v1/cases/%s/evidence/gaps" % case_id), ("claim results", "/api/v1/cases/%s/claim-results" % case_id),
                    ("document usage", "/api/v1/cases/%s/document-usage" % case_id), ("one claim", "/api/v1/claims/%s" % claims[1]),
                    ("claim revisions", "/api/v1/claims/%s/revisions" % claims[1]), ("one defence", "/api/v1/defenses/%s" % defense["id"]),
                    ("judgment results", "/api/v1/judgments/%s/claim-results" % judgment1["id"])):
    check("the viewer is told %s do not exist" % label, viewer.call("GET", path)[0] in (403, 404), True)
# The read-only role lacks the permission to write at all, which is refused before the case is looked at.
check("the viewer cannot add a claim", viewer.call("POST", "/api/v1/cases/%s/claims" % case_id, {"text": "x", "kind": "OTHER"})[0], 403)
check("the viewer cannot record results", viewer.call("PUT", "/api/v1/judgments/%s/claim-results" % judgment1["id"], {"results": [{"claimId": claims[1], "result": "OTHER"}]})[0], 403)

# --- tidy the one shared record: a managed-list value cannot be deleted, so it is deactivated --------------------------
status, _ = manager.call("PUT", "/api/v1/lookups/values/%s" % proposed["id"], {
    "labelAr": proposed["labelAr"], "labelEn": proposed["labelEn"], "active": False, "version": proposed["version"]})
check("the synthetic managed-list value is deactivated again", status, 200)
_, history = manager.call("GET", "/api/v1/lookups/values/%s/history" % proposed["id"])
check("its history keeps the creation and the deactivation", [h["change"] for h in history], ["DEACTIVATED", "CREATED"])

journey.finish(sys.argv[1] if len(sys.argv) > 1 else "wp2_journey.json")
