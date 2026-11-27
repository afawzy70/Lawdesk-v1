#!/usr/bin/env python3
"""Builds the Arabic LawDesk understanding portal as a self-contained static site (standard library only).

  build_portal.py --sha <40-hex develop sha> --out <dir> [--map <floorplan export .html>] [--audience internal|public]

Inputs  portal/content/{categories.json,features/*.json}, portal/planned/*.json, portal/assets/*, optional Floorplan export.
Output  <out>/index.html, features/*.html, planned.html, map/index.html (+ the export), search-index.json, assets, build.json.
No network access; no external fonts, scripts or images. The `public` audience omits file paths and line numbers.
"""
import argparse
import hashlib
import html
import json
import re
import shutil
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

HERE = Path(__file__).resolve().parent
PORTAL = HERE.parent
REPO = PORTAL.parent
GITHUB = "https://github.com/afawzy70/Lawdesk-v1"
STATUS_AR = {
    "IMPLEMENTED_ON_DEVELOP": "مُنفَّذ على develop",
    "VERIFIED_ON_UAT": "تم التحقق منه على UAT",
    "IN_DEVELOPMENT": "قيد التطوير",
    "PROPOSED": "مقترح / مخطط",
    "BLOCKED": "موقوف",
    "NOT_VERIFIED": "لم يُتحقق منه",
}
STATUS_HELP = {
    "IMPLEMENTED_ON_DEVELOP": "الكود موجود في فرع develop. هذا لا يعني أنه نُشر أو اختُبر على بيئة التجربة.",
    "VERIFIED_ON_UAT": "اختبره فريق الاختبار المستقل على بيئة التجربة لنفس رقم الإصدار.",
    "IN_DEVELOPMENT": "عمل غير مدمج بعد في develop (طلب دمج مفتوح).",
    "PROPOSED": "مجرد وثائق أو مقترح؛ لا يوجد برنامج يعمل.",
    "BLOCKED": "موقوف بانتظار موافقة قانونية أو قرار من المالك.",
    "NOT_VERIFIED": "لم نتمكن من التأكد من هذا السلوك من الكود.",
}
LAYER_AR = {"screen": "الشاشة", "component": "مكوّن الواجهة", "api_call": "استدعاء الخادم", "controller": "المتحكم (Controller)",
            "service": "الخدمة (Service)", "authorization": "التحقق من الصلاحية", "repository": "المستودع (Repository)",
            "tables": "جداول قاعدة البيانات", "event": "حدث / مهمة خلفية", "result": "ما يراه المستخدم"}
ALT_AR = {"denied": "رفض الصلاحية", "validation": "خطأ في البيانات", "error": "خطأ", "exceptional": "حالة استثنائية"}
E = html.escape


def ltr(s):
    return f'<code class="ltr">{E(str(s))}</code>'


def ev_html(ev, sha, public):
    if public or not ev:
        return ""
    items = ev if isinstance(ev, list) else [ev]
    out = []
    for e in items:
        a, _, b = str(e["lines"]).partition("-")
        frag = f"#L{a}" + (f"-L{b}" if b else "")
        out.append(f'<a class="ltr" href="{GITHUB}/blob/{sha}/{E(e["path"])}{frag}" rel="noopener noreferrer">{E(e["path"].split("/")[-1])}:{E(str(e["lines"]))}</a>')
    return '<div class="ev">المصدر: ' + " · ".join(out) + "</div>"


def ul(items):
    return "<ul>" + "".join(f"<li>{E(i)}</li>" for i in items) + "</ul>" if items else "<p>لا يوجد.</p>"


def page(title, body, cats, current, base, crumbs, sha, desc=""):
    side = ['<h2>الأقسام</h2><ul><li><a href="%sindex.html">الرئيسية</a></li><li><a href="%smap/index.html">خريطة المعمارية (Floorplan)</a></li><li><a href="%splanned.html">المخطط وغير المدمج (R5 / R6)</a></li><li><a href="%sglossary.html">المصطلحات</a></li><li><a href="%sabout.html">عن هذه البوابة وحدودها</a></li></ul>' % ((base,) * 5)]
    for c in cats:
        if not c["features"]:
            continue
        side.append(f'<h2>{E(c["name_ar"])}</h2><ul>')
        for f in c["features"]:
            cur = ' aria-current="page"' if f["id"] == current else ""
            side.append(f'<li><a href="{base}features/{f["id"]}.html"{cur}>{E(f["name_ar"])}</a></li>')
        side.append("</ul>")
    cr = " › ".join(f'<a href="{base}{u}">{E(t)}</a>' if u else E(t) for t, u in crumbs)
    return f"""<!doctype html>
<html lang="ar" dir="rtl"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<meta name="robots" content="noindex,nofollow,noarchive"><meta name="referrer" content="no-referrer">
<title>{E(title)} — بوابة فهم LawDesk</title><meta name="description" content="{E(desc)}">
<link rel="stylesheet" href="{base}assets/style.css"></head>
<body data-base="{base}"><a class="skip" href="#main">تخطَّ إلى المحتوى</a>
<header class="top"><button id="menu" class="menu-btn" aria-controls="side" aria-expanded="false">القائمة</button>
<a class="brand" href="{base}index.html">بوابة فهم LawDesk</a><div class="spacer"></div>
<div class="searchwrap"><input id="q" type="search" placeholder="ابحث بالعربية عن ميزة… (اضغط /)" aria-label="بحث" autocomplete="off"><div id="results" hidden></div></div>
<button id="theme" aria-label="تبديل الوضع الفاتح/الداكن">الوضع</button></header>
<div class="layout"><nav class="side" id="side" aria-label="الأقسام">{"".join(side)}</nav>
<main id="main"><div class="crumbs">{cr}</div>{body}</main></div>
<footer>رقم إصدار المصدر: {ltr(sha)} · محتوى توضيحي بأمثلة افتراضية فقط · لا يحتوي على بيانات قضايا حقيقية.</footer>
<script src="{base}assets/app.js"></script></body></html>"""


def feature_body(f, sha, public):
    st = f["status"]
    h = [f'<h1>{E(f["name_ar"])}</h1>',
         f'<p><span class="badge st-{st}">{E(STATUS_AR[st])}</span> <span class="ev">{E(STATUS_HELP[st])}</span></p>',
         f'<div class="notice"><b>الحالة:</b> {E(f["status_note_ar"])}<br>رقم إصدار المصدر: {ltr(sha)}{ev_html(f["status_evidence"], sha, public)}</div>',
         f'<div class="card"><p>{E(f["summary_ar"])}</p><h3>لماذا توجد هذه الميزة؟</h3><p>{E(f["purpose_ar"])}</p></div>',
         '<div class="meta">',
         "<div><b>من يستخدمها</b>" + "".join(f'<span class="role">{E(r)}</span>' for r in f["who_ar"]) + (
             "<br>" + " ".join(ltr(p) for p in f.get("who_perms", [])) if f.get("who_perms") else "") + "</div>",
         f'<div><b>كيف تصل إليها</b>{E(f["access_ar"])}' + (f"<br>{ltr(f['route'])}" if f.get("route") else "") + "</div>",
         "<div><b>ما يلزم قبل البدء</b>" + ul(f["prerequisites_ar"]) + "</div></div>"]
    steps = f["steps"]
    h.append('<h2 id="walkthrough">الجولة التفاعلية خطوة بخطوة</h2><div class="stepper" tabindex="0" aria-label="جولة إرشادية. استخدم أزرار الأسهم للتنقل">')
    h.append(f'<div class="bar" role="progressbar" aria-valuemin="1" aria-valuemax="{len(steps)}" aria-valuenow="1" aria-label="التقدم"><i></i></div>')
    h.append('<div class="ctrl"><button data-prev>السابق</button><button data-next>التالي</button><span class="count" aria-live="polite"></span></div>')
    for s in steps:
        h.append(f'<section class="card step" aria-label="الخطوة {s["n"]}"><h3><span class="n">{s["n"]}</span>{E(s["action_ar"])}</h3><dl>'
                 f'<dt>من يقوم بها</dt><dd>{E(s["actor_ar"])}</dd><dt>رد النظام</dt><dd>{E(s["response_ar"])}</dd>'
                 + (f'<dt>البيانات</dt><dd>{E(s["data_ar"])}</dd>' if s.get("data_ar") else "")
                 + (f'<dt>قواعد العمل</dt><dd>{ul(s["rules_ar"])}</dd>' if s.get("rules_ar") else "")
                 + f'<dt>الخطوة التالية</dt><dd>{E(s["next_ar"])}</dd></dl>')
        for a in s.get("alt", []):
            h.append(f'<div class="alt {E(a["kind"])}"><b>{E(ALT_AR[a["kind"]])}: {E(a["title_ar"])}</b><br>{E(a["detail_ar"])}{ev_html(a.get("evidence"), sha, public)}</div>')
        h.append(ev_html(s["evidence"], sha, public) + "</section>")
    h.append("</div>")
    flds = f["fields"]
    for key, title in (("required", "البيانات المطلوبة"), ("optional", "البيانات الاختيارية")):
        rows = flds.get(key, [])
        h.append(f"<h2>{title}</h2>")
        if rows:
            h.append('<div class="tw"><table><tr><th>الحقل</th><th>الرمز في النظام</th><th>القاعدة</th></tr>' + "".join(
                f'<tr><td>{E(r["name_ar"])}</td><td>{ltr(r["code"])}</td><td>{E(r["rule_ar"])}{ev_html(r.get("evidence"), sha, public)}</td></tr>' for r in rows) + "</table></div>")
        else:
            h.append("<p>لا يوجد.</p>")
    h.append("<h2>قواعد العمل والتحقق</h2>" + "".join(f'<div class="card">{E(r["text_ar"])}{ev_html(r.get("evidence"), sha, public)}</div>' for r in f["rules"]))
    h.append(f'<h2>ماذا يحدث بعد الحفظ أو الإرسال</h2><p>{E(f["after_submit_ar"])}</p>')
    h.append("<h2>الإشعارات والموافقات</h2><h3>الإشعارات</h3>" + ul(f["notifications_ar"]) + "<h3>الموافقات</h3>" + ul(f["approvals_ar"]))
    h.append("<h2>الأخطاء المحتملة ومعناها</h2>" + ('<div class="tw"><table><tr><th>الرمز</th><th>المعنى</th></tr>' + "".join(
        f'<tr><td>{ltr(e["code"])}</td><td>{E(e["meaning_ar"])}{ev_html(e.get("evidence"), sha, public)}</td></tr>' for e in f["errors"]) + "</table></div>" if f["errors"] else "<p>لا يوجد.</p>"))
    h.append(f'<h2>ما يراه المستخدم عند الانتهاء</h2><p>{E(f["result_ar"])}</p>')
    chain = (f.get("tech") or {}).get("chain", [])
    if chain:
        h.append('<h2 id="tech">المسار التقني الموثّق</h2><p class="ev">كل حلقة مثبتة من الكود. السهم يبيّن ما الذي ينتقل إلى الحلقة التالية.</p><div class="flow">')
        for i, c in enumerate(chain):
            h.append(f'<div class="node"><small>{E(LAYER_AR[c["layer"]])}</small><b class="ltr">{E(c["label"])}</b>{ev_html(c.get("ref"), sha, public)}</div>')
            if i < len(chain) - 1:
                h.append('<div class="arrow" aria-hidden="true">←</div>')
        h.append("</div><ol>" + "".join(f'<li><b class="ltr">{E(c["label"])}</b>: {E(c["data_ar"])}</li>' for c in chain) + "</ol>")
        h.append(f'<p><a class="btn" href="../map/index.html">افتح الخريطة المعمارية (Floorplan)</a></p>')
    if f["endpoints"]:
        h.append("<h2>نقاط النهاية (Endpoints)</h2><div class='tw'><table><tr><th>الطريقة</th><th>المسار</th></tr>" + "".join(
            f'<tr><td>{ltr(e["method"])}</td><td>{ltr(e["path"])}{ev_html(e["evidence"], sha, public)}</td></tr>' for e in f["endpoints"]) + "</table></div>")
    h.append(f'<h2>حالة التحقق</h2><div class="card">{E(f["verification_ar"])}</div>')
    if f["related"]:
        h.append("<h2>ميزات مرتبطة</h2><p>" + " · ".join(f'<a href="{r}.html">{E(r)}</a>' for r in f["related"]) + "</p>")
    return "".join(h)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--sha", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--map")
    ap.add_argument("--audience", choices=["internal", "public"], default="internal")
    a = ap.parse_args()
    if not re.fullmatch(r"[0-9a-f]{40}", a.sha):
        sys.exit("--sha must be a full 40-hex commit sha")
    public = a.audience == "public"
    out = Path(a.out)
    if out.exists():
        shutil.rmtree(out)
    (out / "features").mkdir(parents=True)
    (out / "map").mkdir()
    shutil.copytree(PORTAL / "assets", out / "assets")
    cats = json.loads((PORTAL / "content/categories.json").read_text(encoding="utf-8"))
    feats = [json.loads(p.read_text(encoding="utf-8")) for p in sorted((PORTAL / "content/features").glob("*.json"))]
    for c in cats:
        c["features"] = sorted([f for f in feats if f["category"] == c["id"]], key=lambda f: f["name_ar"])
    planned = [json.loads(p.read_text(encoding="utf-8")) for p in sorted((PORTAL / "planned").glob("*.json"))]
    index = []
    cname = {c["id"]: c["name_ar"] for c in cats}
    for f in feats:
        body = feature_body(f, a.sha, public)
        (out / "features" / f'{f["id"]}.html').write_text(page(f["name_ar"], body, cats, f["id"], "../",
                                                             [("الرئيسية", "index.html"), (cname[f["category"]], None), (f["name_ar"], None)], a.sha, f["summary_ar"]), encoding="utf-8")
        text = " ".join([f["summary_ar"], f["purpose_ar"]] + [s["action_ar"] + " " + s["response_ar"] for s in f["steps"]] + f["who_ar"])
        index.append({"title": f["name_ar"], "cat": cname[f["category"]], "url": f'features/{f["id"]}.html', "text": text})
    # home
    counts = {}
    for f in feats:
        counts[f["status"]] = counts.get(f["status"], 0) + 1
    home = ['<h1>بوابة فهم LawDesk</h1><p>موقع تفاعلي بالعربية يشرح كل ميزة منفَّذة في LawDesk: ما هي، ولماذا توجد، ومن يستخدمها، وكيف تُستخدم خطوة بخطوة، وكيف تمر البيانات داخل النظام.</p>',
            f'<div class="notice"><b>صدق المعلومات:</b> كل خطوة مربوطة بملف في الكود عند الإصدار {ltr(a.sha)}. وجود الكود لا يعني أنه نُشر أو اختُبر؛ انظر شارة الحالة في كل صفحة. ما لم يُدمج بعد موجود في قسم منفصل.</div>',
            '<div class="meta">' + "".join(f'<div><b>{E(STATUS_AR[k])}</b>{v} ميزة</div>' for k, v in sorted(counts.items())) + "</div>"]
    for c in cats:
        if c["id"] == "planned":
            continue
        if c["features"]:
            home.append(f'<h2>{E(c["name_ar"])}</h2><div class="grid">' + "".join(
                f'<a class="card" href="features/{f["id"]}.html"><b>{E(f["name_ar"])}</b><br><span class="badge st-{f["status"]}">{E(STATUS_AR[f["status"]])}</span><p>{E(f["summary_ar"][:140])}…</p></a>' for f in c["features"]) + "</div>")
    home.append('<h2>خريطة المعمارية</h2><p><a class="btn" href="map/index.html">افتح خريطة Floorplan التفاعلية</a> — الشاشة ← الخادم ← قاعدة البيانات.</p>')
    (out / "index.html").write_text(page("الرئيسية", "".join(home), cats, "", "", [("الرئيسية", None)], a.sha, "بوابة عربية تفاعلية لفهم LawDesk"), encoding="utf-8")
    # planned
    pl = ['<h1>المخطط له وغير المدمج (R5 / R6)</h1><div class="notice"><b>تنبيه:</b> لا شيء في هذه الصفحة يعمل في LawDesk الآن. هذه أعمال غير مدمجة في develop أو وثائق متطلبات فقط، وحالتها كما أُعلنت من فريق الاختبار المستقل.</div>']
    for p in planned:
        pl.append(f'<div class="card"><h2>{E(p["name_ar"])}</h2><p><span class="badge st-{p["status"]}">{E(STATUS_AR[p["status"]])}</span></p><p>{E(p["summary_ar"])}</p><div class="meta">'
                  f'<div><b>الطلب والفرع</b>{ltr(p["ref"])}</div><div><b>رقم الإصدار</b>{ltr(p["sha"])}</div><div><b>حالة الاختبار المستقل</b>{E(p["qa_ar"])}</div></div>'
                  f'<h3>ما ينتظر قبل أي تفعيل</h3>{ul(p["blockers_ar"])}<h3>ما لا يجوز اعتباره منفَّذاً</h3><p>{E(p["not_implemented_ar"])}</p></div>')
        index.append({"title": p["name_ar"], "cat": "المخطط له", "url": "planned.html", "text": p["summary_ar"]})
    (out / "planned.html").write_text(page("المخطط وغير المدمج", "".join(pl), cats, "", "", [("الرئيسية", "index.html"), ("المخطط", None)], a.sha), encoding="utf-8")
    # glossary + about
    gl = json.loads((PORTAL / "content/glossary.json").read_text(encoding="utf-8")) if (PORTAL / "content/glossary.json").exists() else []
    gbody = "<h1>المصطلحات</h1>" + "".join(f'<div class="card"><b>{E(g["term"])}</b>: {E(g["meaning"])}</div>' for g in gl)
    (out / "glossary.html").write_text(page("المصطلحات", gbody, cats, "", "", [("الرئيسية", "index.html"), ("المصطلحات", None)], a.sha), encoding="utf-8")
    about = (PORTAL / "content/about.html")
    (out / "about.html").write_text(page("عن هذه البوابة", about.read_text(encoding="utf-8") if about.exists() else "<h1>عن هذه البوابة</h1>", cats, "", "", [("الرئيسية", "index.html"), ("حدود البوابة", None)], a.sha), encoding="utf-8")
    # map shell
    mapfile = ""
    if a.map and Path(a.map).is_file():
        shutil.copy(a.map, out / "map" / "lawdesk-map.html")
        mapfile = "lawdesk-map.html"
    mshell = ('<h1>خريطة المعمارية (Floorplan)</h1><p>هذه خريطة تفاعلية مولَّدة من الكود الفعلي بإضافة Floorplan الرسمية. أسماء الأجزاء ووصفها بالعربية. أزرار الخريطة نفسها بالإنجليزية لأن الإضافة لا تدعم العربية؛ دليلها بالعربية أدناه.</p>'
              '<div class="card"><b>دليل الأزرار:</b> <b>Screens</b> = ابدأ من شاشة تعرفها · <b>Flows</b> = مسارات إرشادية · <b>Filters</b> = إظهار التفاصيل التقنية · <b>Search</b> = بحث · انقر مرتين على أي جزء لتفتحه. '
              'خاصية <b>Ask</b> (سؤال الخريطة) معطّلة في هذا الموقع المنشور لأنها تحتاج Claude Code على جهاز المشغّل؛ استبدلناها بمسارات مكتوبة مسبقاً في صفحات الميزات.</div>'
              + (f'<iframe class="map" src="{mapfile}" title="خريطة LawDesk التفاعلية" sandbox="allow-scripts" referrerpolicy="no-referrer"></iframe><p><a href="{mapfile}">فتح الخريطة في صفحة كاملة</a></p>' if mapfile else '<div class="notice">لم تُضمَّن الخريطة في هذا البناء.</div>'))
    (out / "map" / "index.html").write_text(page("خريطة المعمارية", mshell, cats, "", "../", [("الرئيسية", "index.html"), ("الخريطة", None)], a.sha), encoding="utf-8")
    (out / "search-index.json").write_text(json.dumps(index, ensure_ascii=False), encoding="utf-8")
    h = hashlib.sha256()
    for p in sorted(out.rglob("*")):
        if p.is_file():
            h.update(p.relative_to(out).as_posix().encode())
            h.update(p.read_bytes())
    fp = (REPO / "portal/floorplan-src/PLUGIN-VERSION.txt")
    meta = {"source_sha": a.sha, "built_at": datetime.now(timezone.utc).isoformat(timespec="seconds"), "audience": a.audience,
            "features": len(feats), "planned": len(planned), "content_sha256": h.hexdigest(),
            "floorplan_plugin": fp.read_text().strip() if fp.exists() else "unknown"}
    (out / "build.json").write_text(json.dumps(meta, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(meta))


if __name__ == "__main__":
    main()
