#!/usr/bin/env python3
"""Independently audit the 650-row source register and write conservative QA coverage."""

import csv
import json
import re
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
SOURCE = ROOT / "docs/requirements/lawdesk-comprehensive-source.txt"
REGISTER = ROOT / "docs/requirements/lawdesk-comprehensive-requirements-matrix.md"
APP_SHA = "352277571426bd4d135ea4acb0e12489aa09fc3c"

# These are narrow, exact assertions in independent UAT reruns. A passing
# developer journey never promotes every row assigned to its evidence bundle.
ACCEPT = {
    "LD-DOC-01-012": ("wp1_access", "claimed amount keeps three decimals"),
    "LD-DOC-04-004": ("wp1d_stages", "a CONSTITUTIONAL proceeding follows the first instance"),
    "LD-DOC-04-005": ("wp1d_stages", "a ENFORCEMENT proceeding follows the first instance"),
    "LD-DOC-05-002": ("wp2", "the case lists its three claims in order"),
    "LD-DOC-05-003": ("wp2", "the previous text is kept verbatim"),
    "LD-DOC-06-014": ("wp1_access", "renaming the unit later leaves the case's record unchanged"),
    "LD-DOC-08-013": ("wp3_followup", "closing it did not close the linked case"),
    "LD-DOC-13-005": ("wp5", "the old version is still downloadable"),
    "LD-DOC-23-010": ("wp9", "the case is still open: a settlement never closes it"),
}

SCENARIO_ACCEPT = {
    "LD-DOC-35-001": [("wp2", "the case lists its three claims in order"), ("wp2", "each claim gets its own result")],
    "LD-DOC-35-002": [("wp2", "the previous text is kept verbatim"), ("wp2", "the change records the user and the date")],
    "LD-DOC-35-003": [("wp2", "the specialist records a defence linked to a memo"),
                       ("wp2", "the same document is linked to a fact and to a defence"),
                       ("wp2", "the court's position is recorded on each defence")],
    "LD-DOC-35-004": [("wp7", "the specialist registers the judgment"),
                       ("wp7", "the specialist extracts a principle apart from the judgment"),
                       ("wp7", "a reader finds the judgment by words found only in the principle")],
    "LD-DOC-35-007": [("wp3_followup", "a new document leads to a new version"),
                       ("wp3_followup", "the earlier version is unchanged")],
    "LD-DOC-35-009": [("wp3_followup", "the previous hearing is adjourned, not replaced"),
                       ("wp3_followup", "an adjournment with no next date is recorded")],
    "LD-DOC-35-010": [("wp3_followup", "the link shows in the file of case A"),
                       ("wp3_followup", "and in the file of case B"),
                       ("wp3_followup", "closing it did not close the linked case")],
    "LD-DOC-35-011": [("wp6", "the personal calendar answers"),
                       ("wp6", "both are in the general calendar of the department"),
                       ("wp6", "the calendar detects the conflict")],
}

SCENARIO_PARTIAL = {
    "LD-DOC-35-005": ("wp7", "اقتراح مشابه مع مصدر ونقاط؛ لم يُتحقق من عرض أوجه الاختلاف في قضية جديدة"),
    "LD-DOC-35-006": ("wp3_followup", "طلب وردود وتأخير؛ لم تُعد جميع حالات جزئي/غير مكتمل/مكتمل في تسلسل واحد"),
    "LD-DOC-35-008": ("wp2", "روابط الواقعة والدفع والطلب ظهرت؛ لم يثبت أن الملف نفسه رُبط بالثلاثة"),
    "LD-DOC-35-012": ("wp3_followup", "التاريخ محفوظ بعد الإغلاق؛ مراجعة البنود والمواعيد المفتوحة لم تُفحص بالكامل"),
    "LD-DOC-35-013": ("wp2", "نتائج الدرجات حُفظت؛ ثبات المرجع القضائي وأرقام المحكمة المستقلة لم يُفحصا معًا"),
    "LD-DOC-35-015": ("wp1_access", "صلاحيات ونطاق ومنح محدودة فُحصت؛ حساب qa.admin معطل، والأدوار السبعة لم تُفحص كلها"),
}

ALIAS = {"wp1a": "wp1_access", "wp1c": "wp1c_parties", "wp1d": "wp1d_stages",
         "wp3": "wp3_followup", "conn": "connected"}

SECTION_PROBE = {
    0: "connected", 1: "wp1_access", 2: "wp1c_parties", 3: "wp1c_parties",
    4: "wp1d_stages", 5: "wp2", 6: "wp1_access", 7: "wp2", 8: "wp3_followup",
    9: "wp2", 10: "wp4", 11: "wp5", 12: "wp5", 13: "ocr", 14: "wp6",
    15: "wp6", 16: "wp7", 17: "wp2", 18: "wp3_followup", 19: "wp3_followup",
    20: "wp2", 21: "connected", 22: "wp10", 23: "wp9", 24: "wp9",
    25: "wp9", 26: "wp8", 27: "wp8", 28: "wp8", 29: "wp9", 30: "wp1_access",
    31: "wp1_access", 32: "wp6", 33: "wp3_followup", 34: "isolated", 35: "S1–S16",
    36: "all", 37: "docs-review",
}


def read_rows():
    source = SOURCE.read_text(encoding="utf-8").splitlines()
    rows = []
    for line in REGISTER.read_text(encoding="utf-8").splitlines():
        if not re.match(r"^\| LD-DOC-\d\d-\d\d\d \|", line):
            continue
        cells = [x.strip() for x in line.strip("|").split("|")]
        if len(cells) != 9:
            continue  # explanatory split rows in the appendix are not atomic rows
        assert len(cells) == 9, (cells[0], len(cells))
        rid, lineno = cells[0], int(cells[1])
        assert cells[3] in source[lineno - 1], (rid, lineno, "source text mismatch")
        rows.append(cells)
    ids = [r[0] for r in rows]
    assert len(rows) == 650, len(rows)
    assert len(set(ids)) == len(ids), "duplicate requirement ID"
    by_section = defaultdict(list)
    for rid in ids:
        _, _, sec, seq = rid.split("-")
        by_section[sec].append(int(seq))
    for sec, seqs in by_section.items():
        assert sorted(seqs) == list(range(1, max(seqs) + 1)), (sec, "missing atomic ordinal")
    assert set(by_section) == {f"{n:02d}" for n in range(38)}, "missing section"
    return rows, source


def journey(name):
    p = HERE / "runs" / f"{name}.json"
    summary = json.loads((HERE / "runs/run-summary.json").read_text())
    meta = next((x for x in summary if x["name"] == name), None)
    if not p.exists() or not meta or meta["state"] != "SCRIPT_PASS" or \
            meta["applicationShaBefore"] != APP_SHA or meta["applicationShaAfter"] != APP_SHA:
        return None
    return json.loads(p.read_text())


def result_for(c):
    rid, _, _, _, implemented, developer, _, cause, note = c
    if rid in SCENARIO_ACCEPT:
        required = SCENARIO_ACCEPT[rid]
        if all((data := journey(name)) and any(s["step"] == step and s.get("ok") is True for s in data["steps"])
               for name, step in required):
            paths = sorted({f"runs/{name}.json" for name, _ in required})
            return "PASS", "; ".join(paths) + ": " + "; ".join(step for _, step in required), APP_SHA, \
                "قبول السيناريو المحدد فقط؛ لا يعمم على جميع حقول الوحدة"
    if rid in SCENARIO_PARTIAL:
        name, gap = SCENARIO_PARTIAL[rid]
        if journey(name):
            return "PARTIAL", f"runs/{name}.json", APP_SHA, gap
    if rid == "LD-DOC-13-004":
        targeted = json.loads((HERE / "ocr-targeted-result.json").read_text())
        if targeted["applicationShaBefore"] == targeted["applicationShaAfter"] == APP_SHA:
            return "PARTIAL", "ocr-targeted-result.json; runs/ocr.json", APP_SHA, \
                "OCR_DONE والفهرسة تعملان، لكن كلمة الكويتي والمرجع LD-2026/045 غائبان ولا يظهر الملف بالبحث عنهما"
    if rid in ACCEPT:
        name, step = ACCEPT[rid]
        data = journey(name)
        if data and any(s["step"] == step and s.get("ok") is True for s in data["steps"]):
            return "PASS", f"runs/{name}.json: {step}", APP_SHA, "ضيق النطاق: تحقق هذا النص الذري فقط"
    if implemented == "BLOCKED":
        return "BLOCKED", "docs/requirements: " + cause, "", note
    if implemented == "PARTIAL":
        return "PARTIAL", "docs/requirements: " + cause, APP_SHA, note
    match = re.search(r"\[([A-Z0-9_]+)\]", developer)
    return "NOT_TESTED", "", "", ("اختبار مقترح: " + developer[:180].strip() if developer else "يلزم تصميم اختبار مستقل للحقل أو القاعدة")


def blocker_assessment(c):
    rid, _, _, text, implemented, _, _, cause, note = c
    if rid == "LD-DOC-34-006":
        return "ENGINEERING_NOW", "يمكن تحسين الاستعلام دون قرار snapshot؛ 0.5 ثانية حد مقترح للقائمة والبحث فقط"
    if rid == "LD-DOC-34-016":
        return "QA_NOW", "عرض اللوحي وفحص الوصولية اختباران تقنيان لا ينتظران المالك"
    if rid == "LD-DOC-16-043":
        return "ENGINEERING_NOW", "واجهة اختيار الدفع أثناء إعداد المذكرة ناقصة؛ لا قرار مالك مطلوب"
    if rid.startswith("LD-DOC-22-") and int(rid[-3:]) in range(24, 32):
        return "SPLIT_TECHNICAL_PART", "استرجاع هذا الحقل ومصدره قابل للاختبار بمعزل عن التوليد؛ التشغيل التوليدي ينتظر D8"
    if cause == "EXTERNAL_PROVIDER":
        return "OWNER_PROVIDER", "الوظيفة التوليدية التشغيلية تنتظر اعتماد D8؛ اختبارات الاسترجاع والحدود التقنية مستقلة"
    if cause == "LEGAL_INPUT":
        return "SPECIALIST_RULES", "القواعد والعطلات الحقيقية تحتاج اعتماد المختص؛ المحرك الاصطناعي قابل للاختبار"
    if cause == "DATA_NOT_PROVIDED":
        return "OWNER_DATA", "الآلية التقنية مستقلة، لكن بيانات الهيكل الحقيقي غير متاحة"
    if cause == "INFRA":
        return "ISOLATED_ENV", "أعد الاختبار في بيئة معزولة مناسبة؛ لا تغيّر UAT"
    if cause == "OWNER_DECISION":
        return "OWNER_DECISION", "القرار المذكور مطلوب؛ تحقق من الجزء التقني المستقل"
    if cause == "SPECIALIST":
        return "SPECIALIST_TEST", "فحص أمني مستقل مطلوب قبل القبول"
    if cause == "UI_NOT_BUILT":
        return "ENGINEERING_NOW", "تنفيذ واجهة ناقصة لا ينتظر قرارًا"
    if cause == "TEST_NOT_RUN":
        return "QA_NOW", "نفّذ الفحص التقني دون انتظار قرار"
    return "ENGINEERING_REVIEW", "راجع الجزء المتبقي مع اختبار مستقل"


def plan_for(c):
    rid, _, _, text, implemented, developer, _, cause, _ = c
    sec = int(rid.split("-")[2])
    baseline = SECTION_PROBE[sec]
    field = text[:105].replace("\n", " ")
    if cause == "EXTERNAL_PROVIDER":
        return f"تحقق محلي من الاسترجاع والصلاحيات والمصادر للجزء «{field}»؛ اختبار توليدي تشغيلي بعد D8 فقط"
    if cause == "LEGAL_INPUT":
        return f"اختبار محرك اصطناعي معزول للجزء «{field}»؛ المدد الفعلية بعد اعتماد D6"
    if sec == 34:
        return f"فحص بنية/أمن/أداء/استعادة مستقل في بيئة معزولة للجزء «{field}»"
    if sec in (36, 37):
        return f"مراجعة الدليل والتسليم وربط البنود التفصيلية للجزء «{field}»"
    return f"إعادة رحلة {baseline} مع تأكيد مستقل للحقل/القاعدة «{field}» عبر API، ثم واجهة/صلاحية عند اللزوم"


def main():
    rows, source = read_rows()
    out = HERE / "coverage.csv"
    counts = Counter()
    with out.open("w", newline="", encoding="utf-8") as f:
        w = csv.writer(f, lineterminator="\n")
        w.writerow(["requirement_id", "source_line", "source_text", "implemented_claim", "developer_evidence_claim",
                    "qa_test_plan", "qa_result", "qa_evidence", "tested_app_sha", "qa_note"])
        for c in rows:
            state, evidence, sha, note = result_for(c)
            counts[state] += 1
            w.writerow([x.rstrip() if isinstance(x, str) else x for x in
                        (c[0], c[1], c[3], c[4], c[5], plan_for(c), state, evidence, sha, note)])
    with (HERE / "open_rows_review.csv").open("w", newline="", encoding="utf-8") as f:
        w = csv.writer(f, lineterminator="\n")
        w.writerow(["requirement_id", "source_text", "implementation_claim", "claimed_blocker", "qa_assessment", "action"])
        for c in rows:
            if c[4] in ("PARTIAL", "BLOCKED"):
                category, action = blocker_assessment(c)
                w.writerow([c[0], c[3], c[4], c[7], category, action])
    summary = {
        "sourceNonblankLines": sum(bool(x.strip()) for x in source),
        "atomicRows": len(rows),
        "uniqueIds": len({r[0] for r in rows}),
        "sections": 38,
        "implementationClaims": dict(Counter(r[4] for r in rows)),
        "developerEvidenceClaimCount": sum(bool(r[5]) for r in rows),
        "qaCounts": {k: counts[k] for k in ("PASS", "FAIL", "BLOCKED", "NOT_TESTED", "PARTIAL")},
        "independentlyAccepted": counts["PASS"],
        "openRowsReviewed": sum(r[4] in ("PARTIAL", "BLOCKED") for r in rows),
        "applicationSha": APP_SHA,
    }
    (HERE / "coverage-summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(summary, ensure_ascii=False))


if __name__ == "__main__":
    main()
