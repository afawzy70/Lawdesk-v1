#!/usr/bin/env python3
"""Independently audit the 650-row source register and write conservative QA coverage."""

import csv
import json
import os
import re
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
SOURCE = ROOT / "docs/requirements/lawdesk-comprehensive-source.txt"
REGISTER = ROOT / "docs/requirements/lawdesk-comprehensive-requirements-matrix.md"
APP_SHA = os.environ.get("QA_APP_SHA", "352277571426bd4d135ea4acb0e12489aa09fc3c")
RUN_DIR = Path(os.environ.get("QA_RUN_DIR", str(HERE / "runs"))).resolve()
OUTPUT_TAG = os.environ.get("QA_OUTPUT_TAG", "")
if RUN_DIR.parent != HERE or not RUN_DIR.name.startswith("runs"):
    raise SystemExit("QA_RUN_DIR must name a runs directory directly under qa/comprehensive_retest")
if OUTPUT_TAG and not re.fullmatch(r"-[a-z0-9]+", OUTPUT_TAG):
    raise SystemExit("QA_OUTPUT_TAG must be a simple -suffix")

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
    "LD-DOC-35-014": ("wp4", "قواعد اصطناعية ومراجعة وتنبيه؛ القواعد والعطلات القانونية الفعلية تنتظر D6"),
    "LD-DOC-35-015": ("wp1_access", "صلاحيات ونطاق ومنح محدودة فُحصت؛ حساب qa.admin معطل، والأدوار السبعة لم تُفحص كلها"),
    "LD-DOC-35-016": ("wp10", "الاسترجاع والمصادر مع مزود اختباري حتمي؛ التشغيل التوليدي الفعلي ينتظر D8"),
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
    p = RUN_DIR / f"{name}.json"
    summary = json.loads((RUN_DIR / "run-summary.json").read_text())
    meta = next((x for x in summary if x["name"] == name), None)
    if not p.exists() or not meta or meta["state"] != "SCRIPT_PASS" or \
            meta["applicationShaBefore"] != APP_SHA or meta["applicationShaAfter"] != APP_SHA:
        return None
    return json.loads(p.read_text())


def result_for(c):
    rid, _, _, _, implemented, developer, _, cause, note = c
    if rid == "LD-DOC-30-001":
        probe_name = {"": "release-before.json", "-aea33fa": "release-aea-before.json",
                      "-514c481": "release-514-after.json", "-03ff08e": "release-03ff-after.json",
                      "-18822a8": "release-111-after-journeys.json"}.get(OUTPUT_TAG)
        if probe_name:
            probe = json.loads((HERE / probe_name).read_text())
            if (probe["accounts"].get("admin", {}).get("login") == "401"
                    and probe["accounts"]["manager"]["release"]["release"] == APP_SHA):
                return "BLOCKED", probe_name, APP_SHA, \
                    "اختبار مدير النظام المباشر محجوب: حساب qa.admin المخصص يعيد 401؛ QA-RT-002"
    if rid == "LD-DOC-35-015" and OUTPUT_TAG == "-514c481":
        leak_path = HERE / "party-directory-scope-514c481-result.json"
        if leak_path.exists():
            leak = json.loads(leak_path.read_text())
            if leak["applicationShaBefore"] == leak["applicationShaAfter"] == APP_SHA \
                    and leak["outsiderSearchTotal"] > 0 and leak["outsiderSeesName"] \
                    and leak["outsiderCaseCount"] == 0:
                return "FAIL", leak_path.name, APP_SHA, \
                    "S15: مشاهد خارج نطاق القضية استطاع العثور على طرفها وقراءة اسمه؛ QA-RT-010"
    if rid in SCENARIO_ACCEPT:
        required = SCENARIO_ACCEPT[rid]
        if all((data := journey(name)) and any(s["step"] == step and s.get("ok") is True for s in data["steps"])
               for name, step in required):
            paths = sorted({f"{RUN_DIR.name}/{name}.json" for name, _ in required})
            return "PASS", "; ".join(paths) + ": " + "; ".join(step for _, step in required), APP_SHA, \
                "قبول السيناريو المحدد فقط؛ لا يعمم على جميع حقول الوحدة"
    if rid in SCENARIO_PARTIAL:
        name, gap = SCENARIO_PARTIAL[rid]
        if journey(name):
            return "PARTIAL", f"{RUN_DIR.name}/{name}.json", APP_SHA, gap
    if rid == "LD-DOC-13-004":
        targeted = json.loads((HERE / f"ocr-targeted-result{OUTPUT_TAG}.json").read_text())
        if targeted["applicationShaBefore"] == targeted["applicationShaAfter"] == APP_SHA:
            return "PARTIAL", f"ocr-targeted-result{OUTPUT_TAG}.json; {RUN_DIR.name}/ocr.json", APP_SHA, \
                "OCR_DONE والفهرسة تعملان، لكن كلمة الكويتي والمرجع LD-2026/045 غائبان ولا يظهر الملف بالبحث عنهما"
    if rid == "LD-DOC-34-016":
        browser_path = HERE / f"browser-uat-result{OUTPUT_TAG}.json"
        if browser_path.exists():
            browser = json.loads(browser_path.read_text())
            if len(browser) == 32 and all(x.get("applicationShaBefore") == APP_SHA
                                          and x.get("applicationShaAfter") == APP_SHA for x in browser):
                violations = sum(len(x.get("axeViolations", [])) for x in browser)
                errors500 = sum(e.get("status") == 500 for x in browser for e in x.get("networkErrors", []))
                if errors500:
                    return "FAIL", browser_path.name, APP_SHA, \
                        f"صفحة القضية أعادت {errors500} ردًا HTTP 500 في 32 فحص Chromium؛ QA-RT-011"
                if OUTPUT_TAG == "-18822a8":
                    second_path = HERE / "browser-uat-result-18822a8b.json"
                    deep_path = HERE / "browser-deep-18822a8.json"
                    second = json.loads(second_path.read_text()) if second_path.exists() else []
                    deep = json.loads(deep_path.read_text()) if deep_path.exists() else []
                    healthy = len(second) == 32 and len(deep) == 2 and all(
                        x.get("applicationShaBefore") == APP_SHA == x.get("applicationShaAfter")
                        and not x.get("networkErrors") and not x.get("consoleErrors") for x in browser + second)
                    complete = all(x.get("applicationShaBefore") == APP_SHA == x.get("applicationShaAfter")
                                   and not x.get("errors") and all(c.get("ok") for c in x.get("checks", [])) for x in deep)
                    if not healthy or not complete:
                        return "FAIL", f"{browser_path.name}; {second_path.name}; {deep_path.name}", APP_SHA, \
                            "إعادة المتصفح أو اكتمال بيانات اللوحات لم يجتز التحقق؛ راجع الأدلة المفصلة"
                    return "PARTIAL", f"{browser_path.name}; {second_path.name}; {deep_path.name}; " \
                        "browser-uat-result-firefox18822a8.json; browser-uat-result-webkit18822a8.json", APP_SHA, \
                        f"إعادة Chromium 32×2 بلا 5xx أو طلب ملغى؛ اكتمال بيانات اللوحات فُحص؛ مخالفات axe={violations} في كل تكرار QA-RT-012؛ Firefox 32 نظيفة؛ WebKit 32 بلا 5xx لكن 200 رفض CSP في Console QA-RT-013؛ اللوحي غير مفحوص"
                return "PARTIAL", browser_path.name, APP_SHA, \
                    f"32 فحص Chromium للهاتف والحاسب؛ اللوحي وFirefox/WebKit غير مفحوصة، ومخالفات axe={violations}"
    if OUTPUT_TAG == "-18822a8":
        if rid == "LD-DOC-34-006":
            p = HERE / "performance-50k-18822a8.md"
            if p.exists() and APP_SHA in p.read_text() and "cases: 50000" in p.read_text():
                return "PARTIAL", "PERFORMANCE-18822a8-QA.md; performance-50k-18822a8.raw.csv", APP_SHA, \
                    "50 ألف قضية مع p50/p95؛ فلتر نص المدير p95=545ms فوق هدف 500ms المقترح؛ بحث شامل/cache/صحة حمولة 50 ألف لم تُفحص"
        if rid == "LD-DOC-34-009":
            p = HERE / "restore-old-18822a8/drill-result.json"
            if p.exists() and (drill := json.loads(p.read_text())).get("passed") and \
                    len(drill.get("checks", [])) == 21 and all(x.get("ok") for x in drill["checks"]):
                return "PARTIAL", "RESTORE-OLD-18822a8-QA.md; restore-old-18822a8/drill-result.json", APP_SHA, \
                    "استعادة dump صناعي فعلي من V781 نجحت في قاعدة جانبية؛ نسخ التشغيل الأقدم/الحقيقية وآلية النسخ قبل النشر غير مقبولة بعد"
        if rid in ("LD-DOC-34-008", "LD-DOC-34-010", "LD-DOC-34-011"):
            p = HERE / "source-18822a8-auth-result.json"
            if p.exists() and (auth := json.loads(p.read_text())).get("applicationSourceSha") == APP_SHA \
                    and auth.get("totals", {}).get("tests") == 46 \
                    and auth["totals"].get("failures") == auth["totals"].get("errors") == 0:
                details = {
                    "LD-DOC-34-008": "حماية تحميل/نص المستند فُحصت في API وOCR صناعي؛ التشفير والاحتفاظ وكل أنواع الملفات مفتوحة",
                    "LD-DOC-34-010": "مصادقة ثلاثة حسابات UAT وتغير الدور/تعطيل الجلسة في قاعدة معزولة؛ qa.admin والأدوار السبعة مفتوحة",
                    "LD-DOC-34-011": "رفض خارج النطاق ومنح/سحب و13 جسم طلب صحيح في مصدر مطابق؛ مصفوفة UAT للأدوار السبعة مفتوحة",
                }
                evidence = "source-18822a8-auth-result.json; runs-18822a8/wp1_access.json"
                if rid == "LD-DOC-34-008":
                    evidence += "; runs-18822a8/ocr.json"
                if rid == "LD-DOC-34-011":
                    evidence += "; party-directory-scope-18822a8-result.json"
                return "PARTIAL", evidence, APP_SHA, details[rid]
        if rid in ("LD-DOC-34-014", "LD-DOC-34-015"):
            p = HERE / "browser-summary-18822a8.json"
            if p.exists() and (summary := json.loads(p.read_text())).get("applicationSha") == APP_SHA \
                    and all(v["rows"] == 32 and v["releaseChanged"] == v["fatal"] == 0
                            and v.get("unexpectedDirection") == v.get("languageMismatch") == 0
                            for v in summary.get("matrices", {}).values()) and len(summary["matrices"]) == 4:
                note = ("Chromium 32×2 وFirefox/WebKit 32؛ WebKit يسجل 200 CSP Console، والتفاعل اليدوي/اللوحي مفتوح"
                        if rid == "LD-DOC-34-014" else
                        "RTL والعربية والإنجليزية ظهرت في 128 صف متصفح؛ اكتمال ترجمة كل نموذج وحقل ولوحي لم يُفحص")
                return "PARTIAL", "browser-summary-18822a8.json; webkit-csp-18822a8.json", APP_SHA, note
    if rid in ACCEPT:
        name, step = ACCEPT[rid]
        data = journey(name)
        if data and any(s["step"] == step and s.get("ok") is True for s in data["steps"]):
            return "PASS", f"{RUN_DIR.name}/{name}.json: {step}", APP_SHA, "ضيق النطاق: تحقق هذا النص الذري فقط"
    if implemented == "BLOCKED":
        category, action = blocker_assessment(c)
        if category in ("OWNER_PROVIDER", "OWNER_DECISION", "OWNER_DATA", "SPECIALIST_RULES", "SPECIALIST_TEST"):
            return "BLOCKED", f"open_rows_review{OUTPUT_TAG}.csv; docs/design owner decisions: " + cause, "", action
        return "NOT_TESTED", f"open_rows_review{OUTPUT_TAG}.csv", "", "المطور صنفه محجوبًا، لكن الجزء التقني قابل للفحص: " + action
    if implemented == "PARTIAL":
        return "NOT_TESTED", f"open_rows_review{OUTPUT_TAG}.csv", "", "ادعاء المطور PARTIAL فقط؛ لا دليل QA مستقل لهذا الصف: " + note
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
    out = HERE / f"coverage{OUTPUT_TAG}.csv"
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
    with (HERE / f"open_rows_review{OUTPUT_TAG}.csv").open("w", newline="", encoding="utf-8") as f:
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
    (HERE / f"coverage-summary{OUTPUT_TAG}.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(summary, ensure_ascii=False))


if __name__ == "__main__":
    main()
