"""OCR journey on UAT (requirement 13-004, task DOC-OCR): synthetic page images (SYNTH-DOC, tools/journeys/ocr_fixtures, written by
the backend test OcrFixtureExport with known ground truth) are uploaded to one synthetic case. The journey proves that the
server's OCR engine reads them (text state OCR_DONE: the application's state for "OCR completed"), that the text is found by the
archive search and by the unified search with Arabic spelling variants, that a user who cannot see the case finds nothing, and
that the original file is still downloadable byte for byte.
Run after the integration owner has deployed the image that carries Tesseract: python3 tools/journeys/ocr_journey.py [evidence.json]
Creates only records marked SYNTH-DOC and never edits an existing case. The background worker reads waiting scans every minute
(five per run), so the journey polls for up to LAWDESK_OCR_WAIT seconds (default 420)."""

import io
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

from uat import BASE, MARK, Journey, Session

HERE = os.path.dirname(os.path.abspath(__file__))
FIXTURES = os.path.join(HERE, "ocr_fixtures")
WAIT = int(os.environ.get("LAWDESK_OCR_WAIT", "420"))
run = time.strftime("%y%m%d%H%M%S")
today = time.strftime("%Y-%m-%d")
journey = Journey("OCR: Arabic scans are read, searchable and access-restricted (%s)" % run)
check = journey.check
manager = Session("manager").login("manager")
staff = Session("staff").login("staff")
viewer = Session("viewer").login("viewer")


def multipart(session, path, filename, content, ctype, fields=None):
    boundary = "----synth" + uuid.uuid4().hex
    body = io.BytesIO()
    parts = [(k, None, v.encode(), None) for k, v in (fields or {}).items()]
    parts.append(("file", filename, content, ctype))
    for name, fname, data, ct in parts:
        body.write(("--%s\r\n" % boundary).encode())
        disposition = 'form-data; name="%s"' % name + ('; filename="%s"' % fname if fname else "")
        body.write(("Content-Disposition: %s\r\n" % disposition).encode())
        if ct:
            body.write(("Content-Type: %s\r\n" % ct).encode())
        body.write(b"\r\n" + data + b"\r\n")
    body.write(("--%s--\r\n" % boundary).encode())
    request = urllib.request.Request(
        BASE + path, data=body.getvalue(), method="POST",
        headers={"Content-Type": "multipart/form-data; boundary=" + boundary, "X-XSRF-TOKEN": session._xsrf(),
                 "Accept": "application/json"})
    try:
        with session.opener.open(request, timeout=90) as response:
            return response.status, json.loads(response.read() or b"null")
    except urllib.error.HTTPError as error:
        raw = error.read()
        try:
            return error.code, json.loads(raw)
        except ValueError:
            return error.code, None


def download(session, path):
    request = urllib.request.Request(BASE + path, headers={"Accept": "*/*"})
    with session.opener.open(request, timeout=60) as response:
        return response.read()


def archive_search(session, query, **extra):
    return session.call("GET", "/api/v1/documents/search?" + urllib.parse.urlencode({"q": query, **extra}))


# --- the engine as the server reports it ------------------------------------------------------------------------------------
status, release = manager.call("GET", "/api/v1/release")
journey.note("deployed release", release if status == 200 else status)
status, settings = staff.call("GET", "/api/v1/archive/settings")
check("the archive settings are readable", status, 200)
journey.note("OCR engine on this server", settings["ocr"])
check("the server has Tesseract with Arabic and English data", (settings["ocr"]["available"], settings["ocr"]["engine"]),
      (True, "tesseract"))
check("the languages are ara+eng", settings["ocr"]["languages"], "ara+eng")

# --- a synthetic case (as in the WP5 journey) --------------------------------------------------------------------------------
_, users = manager.call("GET", "/api/v1/users?size=100&q=qa.")
by_name = {u["username"]: u for u in users["items"]}
_, courts = manager.call("GET", "/api/v1/courts?size=100")
first_court = next((c for c in courts["items"] if c["level"] == "FIRST_INSTANCE" and c["active"]), None)
if first_court is None:
    _, first_court = manager.call("POST", "/api/v1/courts", {
        "code": "SYNO-" + run, "nameAr": "محكمة اختبارية (%s)" % MARK, "nameEn": "Synthetic court", "level": "FIRST_INSTANCE"})
status, created = manager.call("POST", "/api/v1/cases/intake", {
    "automatedNo": "AUTOO-%s" % run, "title": "%s-%s قضية اختبار التعرف الضوئي" % (MARK, run), "caseType": "ADMINISTRATIVE",
    "subject": "%s: دعوى اختبارية للتعرف الضوئي" % MARK, "openedOn": today,
    "firstInstance": {"courtId": first_court["id"], "courtCaseNumber": "9%s" % run[-5:], "courtCaseYear": 2026, "filingDate": today},
    "authorityPosition": "DEFENDANT",
    "parties": [{"newParty": {"kind": "PERSON", "name": "مدعٍ اختباري %s" % run, "nationalId": None}, "side": "PLAINTIFF"}],
    "primaryMemberId": by_name["qa.staff"]["id"], "alternateMemberId": by_name["qa.manager"]["id"], "financialImpact": "NO"})
check("intake registers the synthetic case", status, 201)
case_id = created["id"]
docs_path = "/api/v1/cases/%s/documents" % case_id

# --- upload the synthetic scans ----------------------------------------------------------------------------------------------
manifest = json.load(open(os.path.join(FIXTURES, "manifest.json"), encoding="utf-8"))
uploaded = {}
for entry in manifest:
    content = open(os.path.join(FIXTURES, entry["file"]), "rb").read()
    media = "image/jpeg" if entry["file"].endswith(".jpg") else "image/png"
    status, doc = multipart(staff, docs_path, entry["file"], content, media, {"title": "%s %s" % (MARK, entry["file"])})
    check("%s is archived" % entry["file"], (status, doc["mediaType"]), (201, media))
    uploaded[entry["file"]] = {"id": doc["id"], "bytes": content, "lines": entry["lines"], "dpi": entry["dpi"],
                               "skew": entry["skewDegrees"]}
    _, state = staff.call("GET", "/api/v1/documents/%s/text" % doc["id"])
    check("%s waits for recognition (the scan is stored first)" % entry["file"], state["status"] in ("PENDING", "OCR_DONE"), True)

# --- wait for the worker (it runs every minute) ------------------------------------------------------------------------------
deadline = time.time() + WAIT
states = {}
while time.time() < deadline:
    states = {name: staff.call("GET", "/api/v1/documents/%s/text" % info["id"])[1] for name, info in uploaded.items()}
    if all(s["status"] not in ("PENDING",) for s in states.values()):
        break
    time.sleep(15)
for name, state in states.items():
    check("%s: OCR completed" % name, (state["status"], state["method"], state["engine"]), ("OCR_DONE", "OCR", "tesseract"))

# --- the text: accuracy against ground truth, measured on UAT ------------------------------------------------------------------
sys.path.insert(0, HERE)


def words(text):
    table = str.maketrans("أإآٱىةؤئ٠١٢٣٤٥٦٧٨٩", "اااايهويء0123456789")
    cleaned = "".join(ch if ch.isalnum() else " " for ch in text.lower().translate(table)
                      if not ("ً" <= ch <= "ٰ" or ch == "ـ"))
    return cleaned.split()


for name, info in uploaded.items():
    _, with_text = staff.call("GET", "/api/v1/documents/%s/text?withText=true" % info["id"])
    truth = words(" ".join(info["lines"]))
    got = set(words(with_text.get("text") or ""))
    recall = sum(1 for w in truth if w in got) / len(truth)
    journey.note("%s: share of ground-truth words found in the read text (dpi %s, skew %s)" % (name, info["dpi"], info["skew"]),
                 round(recall, 3))
    check("%s: at least 85%% of the words were read" % name, recall >= 0.85, True)

# --- search: archive and unified, with Arabic spelling variants ----------------------------------------------------------------
queries = [
    ("ocr-clear-ar.png", ["مذكرة دفاع", "مذكره دفاع", "القانون المدني", "قانون مدني الكويتي", "OCRJOURNEYONE"]),
    ("ocr-scan-skew.jpg", ["صحيفة استئناف", "صحيفه استئناف", "محكمة أول درجة", "OCRJOURNEYTWO"]),
    ("ocr-mixed.png", ["عقد الإيجار", "عقد الايجار", "ABC Trading", "1,250.500", "OCRJOURNEYTHREE"]),
]
for name, variants in queries:
    doc_id = uploaded[name]["id"]
    for query in variants:
        _, hits = archive_search(staff, query, caseId=case_id)
        check("archive search '%s' finds %s" % (query, name), doc_id in [h["documentId"] for h in hits["items"]], True)
    _, unified = staff.call("GET", "/api/v1/search?" + urllib.parse.urlencode({"q": variants[-1], "types": "DOCUMENT", "caseId": case_id}))
    ids = [item["id"] for group in unified["groups"] for item in group["items"]]
    check("unified search '%s' finds %s through its text" % (variants[-1], name), doc_id in ids, True)
    matched = [item["matchedIn"] for group in unified["groups"] for item in group["items"] if item["id"] == doc_id]
    check("unified search says the match is in the file text", matched and "DOCUMENT_TEXT" in matched[0], True)

# --- access: the viewer account is not a member of the case ------------------------------------------------------------------
for name, variants in queries:
    _, hits = archive_search(viewer, variants[-1])
    check("a user outside the case does not find %s in the archive search" % name,
          uploaded[name]["id"] in [h["documentId"] for h in (hits or {}).get("items", [])], False)
    status, hidden = viewer.call("GET", "/api/v1/documents/%s/text?withText=true" % uploaded[name]["id"])
    check("nor read its text", status in (403, 404), True)

# --- the file is never lost -----------------------------------------------------------------------------------------------------
for name, info in uploaded.items():
    check("%s is still downloadable byte for byte" % name, download(staff, "/api/v1/documents/%s/content" % info["id"]) == info["bytes"], True)

journey.finish(sys.argv[1] if len(sys.argv) > 1 else "ocr_journey.json")
