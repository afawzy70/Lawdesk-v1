#!/usr/bin/env python3
"""Preserve the prior 650-row QA matrix and add this pinned-release window as a separate layer."""
import csv
import json
from collections import Counter
from pathlib import Path

HERE = Path(__file__).resolve().parent
SHA = "18822a83bff8e2a38b83db4370674a9f69919d81"
release_names = (
    "release-window2-start-18822a8.json", "release-window2-after-matrix1.json",
    "release-window2-after-matrix2.json", "release-window2-after-deep.json",
    "release-window2-after-bell.json", "release-window2-after-party.json", "release-window2-end.json",
)
for name in release_names:
    x = json.loads((HERE / name).read_text())
    assert x["accounts"]["manager"]["release"]["release"] == SHA, name
    assert x["accounts"]["admin"]["login"] == "401", name
for name in ("browser-uat-result-window2a.json", "browser-uat-result-window2b.json"):
    rows = json.loads((HERE / name).read_text())
    assert len(rows) == 32 and all(r["applicationShaBefore"] == r["applicationShaAfter"] == SHA
                                   and not r.get("fatal") and not r.get("networkErrors") and not r.get("consoleErrors")
                                   for r in rows), name
deep = json.loads((HERE / "browser-deep-18822a8-window2.json").read_text())
assert len(deep) == 2 and sum(len(x["checks"]) for x in deep) == 58
assert all(x["applicationShaBefore"] == x["applicationShaAfter"] == SHA and not x.get("errors")
           and all(c["ok"] for c in x["checks"]) for x in deep)
bell = json.loads((HERE / "bell-matrix-window2.json").read_text())
assert bell["summary"]["rows"] == 16 and bell["summary"]["failed"] == 0
party = json.loads((HERE / "party-edge-window2.json").read_text())
assert party["applicationShaBefore"] == party["applicationShaAfter"] == SHA
assert party["steps"]["unattached"]["viewerSeesMarkedName"] and not party["viewerHasPartyManage"]
grant = json.loads((HERE / "grant-session-window2.json").read_text())
assert grant["state"] == "PASS" and grant["applicationShaBefore"] == grant["applicationShaAfter"] == SHA
for name, checks, latest in (("restore-old-window2", 21, 781), ("restore-v845-window2", 15, 845)):
    x = json.loads((HERE / name / "drill-result.json").read_text())
    assert x["passed"] and len(x["checks"]) == checks and all(c["ok"] for c in x["checks"]), name
    assert x["fingerprint_after"]["flyway"]["latest"] == latest, name
    if name == "restore-old-window2":
        assert x["restored_boot"]["migrations_applied"] == 10 and not x["differences_after_flyway"]

evidence = {
    "LD-DOC-02-004": ("COUNSEL_SLICE_OBSERVED", "party-edge-window2.json"),
    "LD-DOC-02-007": ("PARTY_LINK_SCOPE_SLICE_OBSERVED", "party-edge-window2.json"),
    "LD-DOC-30-001": ("BLOCKED_RECONFIRMED", "release-window2-start-18822a8.json; release-window2-end.json"),
    "LD-DOC-30-007": ("VIEWER_ROLE_SLICE_OBSERVED", "grant-session-window2.json; party-edge-window2.json"),
    "LD-DOC-30-009": ("CASE_GRANT_SCOPE_SLICE_OBSERVED", "grant-session-window2.json"),
    "LD-DOC-34-009": ("PARTIAL_REVERIFIED_RESTORE", "restore-old-window2/drill-result.json; restore-v845-window2/drill-result.json"),
    "LD-DOC-34-010": ("AUTH_PARTIAL_REVERIFIED", "release-window2-start-18822a8.json; grant-session-window2.json"),
    "LD-DOC-34-011": ("AUTHZ_PARTIAL_REVERIFIED", "grant-session-window2.json"),
    "LD-DOC-34-014": ("BELL_BROWSER_SLICE_REVERIFIED", "bell-matrix-window2.json"),
    "LD-DOC-34-016": ("PARTIAL_REVERIFIED_CASE_UI", "browser-uat-result-window2a.json; browser-uat-result-window2b.json; browser-deep-18822a8-window2.json"),
    "LD-DOC-35-015": ("FAIL_OUT_OF_SCOPE_PARTY", "party-edge-window2.json; grant-session-window2.json"),
}
pr113_assistant = {4, 8, 9, *range(24, 32), *range(39, 46)}
base = list(csv.DictReader((HERE / "coverage-18822a8.csv").open()))
assert len(base) == len({x["requirement_id"] for x in base}) == 650
fieldnames = list(base[0]) + ["prior_qa_result", "window2_result", "window2_evidence",
                               "window2_app_sha", "acceptance_history", "next_sha_retest"]
out = []
for row in base:
    item = dict(row)
    rid = item["requirement_id"]
    item["prior_qa_result"] = item["qa_result"]
    item["window2_result"], item["window2_evidence"] = evidence.get(rid, ("NOT_REPLAYED", ""))
    item["window2_app_sha"] = SHA if rid in evidence else ""
    item["acceptance_history"] = "PASS_AT_18822A8_PRIOR_ROUND" if item["prior_qa_result"] == "PASS" else "NONE"
    item["next_sha_retest"] = "YES" if item["prior_qa_result"] == "PASS" or rid in evidence else "AS_COVERAGE_PLAN"
    if rid.startswith("LD-DOC-22-") and int(rid[-3:]) in pr113_assistant:
        item["window2_result"] = "PR113_DEVELOPER_RECLASSIFICATION_REVIEWED_NO_QA_PROMOTION"
        item["window2_evidence"] = "PR113-ASSISTANT-REVIEW.md"
    if rid == "LD-DOC-35-015":
        item["qa_result"] = "FAIL"
        item["qa_evidence"] = "party-edge-window2.json; grant-session-window2.json"
        item["tested_app_sha"] = SHA
        item["qa_note"] = "S15: qa.viewer بلا PARTY_MANAGE يرى اسم طرف سري بلا قضية؛ يختفي بعد ربطه بقضية خارج نطاقه. حاجة الربط لا تبرر كشف دليل الأطراف للجميع؛ QA-RT-014. المنح والسحب نجحا في جلسة قائمة."
    if rid == "LD-DOC-34-016":
        item["qa_evidence"] += "; " + evidence[rid][1]
        item["qa_note"] += "; جولة ثانية 32×2 و58 تحققًا لاكتمال البيانات على SHA نفسه؛ QA-RT-011 مغلق للمسار الأصلي"
    if rid == "LD-DOC-34-009":
        item["qa_evidence"] += "; " + evidence[rid][1]
        item["qa_note"] += "; V781 الصناعي 21/21 وdump بعد V845 ‏15/15 في جولة ثانية؛ نسخة تشغيلية قديمة فعلية غير متاحة"
    out.append(item)

with (HERE / "coverage-window2-18822a8.csv").open("w", newline="") as f:
    writer = csv.DictWriter(f, fieldnames=fieldnames)
    writer.writeheader()
    writer.writerows(out)
counts = Counter(x["qa_result"] for x in out)
summary = {
    "applicationSha": SHA, "priorCoverage": "coverage-18822a8.csv", "rows": len(out),
    "counts": {s: counts[s] for s in ("PASS", "FAIL", "BLOCKED", "NOT_TESTED", "PARTIAL")},
    "acceptedHistoricalSameSha": sum(x["acceptance_history"] != "NONE" for x in out),
    "acceptedExactStepReplayedThisWindow": 0,
    "developerPr113AssistantRowsReviewedWithoutQaPromotion": len(pr113_assistant),
    "releaseObservations": len(release_names),
}
(HERE / "coverage-window2-summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n")
print(json.dumps(summary, ensure_ascii=False))
