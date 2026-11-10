#!/usr/bin/env python3
"""Probe OCR omissions explicitly instead of dropping a failed search term."""

import json
import os
import re
import sys
import urllib.parse
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT / "tools" / "journeys"))
from uat import Session  # noqa: E402


def main():
    run_dir = Path(os.environ.get("QA_RUN_DIR", str(HERE / "runs"))).resolve()
    if run_dir.parent != HERE or not run_dir.name.startswith("runs"):
        raise SystemExit("QA_RUN_DIR must name a runs directory directly under qa/comprehensive_retest")
    prior = json.loads((run_dir / "ocr.json").read_text())
    run = re.search(r"\((\d+)\)$", prior["journey"]).group(1)
    staff = Session("staff").login("staff")
    release_before = staff.call("GET", "/api/v1/release")[1]["release"]
    status, cases = staff.call("GET", "/api/v1/cases?" + urllib.parse.urlencode({"q": "SYNTH-DOC-" + run, "size": 10}))
    assert status == 200
    case = next(c for c in cases["items"] if "SYNTH-DOC-" + run in c["title"])
    status, docs = staff.call("GET", f"/api/v1/cases/{case['id']}/documents")
    assert status == 200
    documents = docs["items"] if isinstance(docs, dict) else docs
    by_name = {d["fileName"]: d for d in documents}
    checks = []
    for filename, term in (("ocr-clear-ar.png", "الكويتي"), ("ocr-mixed.png", "LD-2026/045")):
        doc = by_name[filename]
        _, state = staff.call("GET", f"/api/v1/documents/{doc['id']}/text?withText=true")
        raw = state.get("text") or ""
        s, hits = staff.call("GET", "/api/v1/documents/search?" + urllib.parse.urlencode({"q": term, "caseId": case["id"]}))
        found = s == 200 and doc["id"] in [h["documentId"] for h in hits["items"]]
        checks.append({"file": filename, "term": term, "ocrStatus": state["status"],
                       "literalPresentInExtractedText": term.casefold() in raw.casefold(),
                       "archiveSearchReturnsFile": found, "searchHttpStatus": s})
    release_after = staff.call("GET", "/api/v1/release")[1]["release"]
    result = {"observedAtUtc": datetime.now(timezone.utc).isoformat(),
              "applicationShaBefore": release_before, "applicationShaAfter": release_after,
              "checks": checks}
    output = Path(os.environ.get("QA_OCR_OUT", str(HERE / "ocr-targeted-result.json"))).resolve()
    if output.parent != HERE:
        raise SystemExit("QA_OCR_OUT must be in qa/comprehensive_retest")
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(result, ensure_ascii=False))
    return 0 if release_before == release_after else 2


if __name__ == "__main__":
    raise SystemExit(main())
