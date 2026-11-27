#!/usr/bin/env python3
"""Validates portal/content against SCHEMA.md and the repository at the build SHA. Files only; no network.

Usage: validate_content.py [--root <repo root>] [feature-id ...]   exit 0 clean, 1 problems.
"""
import argparse
import json
import re
import sys
from pathlib import Path

STATUSES = {"IMPLEMENTED_ON_DEVELOP", "VERIFIED_ON_UAT", "IN_DEVELOPMENT", "PROPOSED", "BLOCKED", "NOT_VERIFIED"}
LAYERS = {"screen", "component", "api_call", "controller", "service", "authorization", "repository", "tables", "event", "result"}
ALT_KINDS = {"denied", "validation", "error", "exceptional"}
ARABIC = re.compile(r"[؀-ۿ]")
SECRET = re.compile(r"(?i)(password\s*[:=]\s*\S{4,}|secret\s*[:=]\s*\S{8,}|api[_-]?key\s*[:=]\s*\S{8,}|BEGIN [A-Z ]*PRIVATE KEY|AKIA[0-9A-Z]{16}|eyJ[A-Za-z0-9_-]{20,}\.)")
REQUIRED = ["id", "category", "name_ar", "summary_ar", "purpose_ar", "who_ar", "access_ar", "prerequisites_ar", "fields", "steps",
            "rules", "after_submit_ar", "notifications_ar", "approvals_ar", "errors", "result_ar", "related", "endpoints",
            "status", "status_note_ar", "status_evidence", "verification_ar"]
AR_FIELDS = ["name_ar", "summary_ar", "purpose_ar", "access_ar", "after_submit_ar", "result_ar", "status_note_ar", "verification_ar"]


def lines_ok(root, e, problems, where, need=None):
    if not isinstance(e, dict) or "path" not in e or "lines" not in e:
        problems.append(f"{where}: evidence needs path and lines: {e!r}")
        return
    p = (root / e["path"]).resolve()
    if root.resolve() not in p.parents or not p.is_file():
        problems.append(f"{where}: no such file {e['path']}")
        return
    m = re.fullmatch(r"(\d+)(?:-(\d+))?", str(e["lines"]))
    if not m:
        problems.append(f"{where}: bad line range {e['lines']!r}")
        return
    a, b = int(m.group(1)), int(m.group(2) or m.group(1))
    text = p.read_text(encoding="utf-8", errors="replace").splitlines()
    if not (1 <= a <= b <= len(text)):
        problems.append(f"{where}: lines {e['lines']} outside {e['path']} ({len(text)} lines)")
        return
    if need and need not in "\n".join(text[a - 1:b]):
        problems.append(f"{where}: lines {e['lines']} of {e['path']} do not contain {need!r}")


def evs(root, items, problems, where, minimum=0):
    items = items or []
    if len(items) < minimum:
        problems.append(f"{where}: needs at least {minimum} evidence reference(s)")
    for i, e in enumerate(items):
        lines_ok(root, e, problems, f"{where}[{i}]")


def check(root, path, cats):
    problems = []
    try:
        d = json.loads(path.read_text(encoding="utf-8"))
    except Exception as ex:  # noqa: BLE001
        return [f"{path.name}: invalid JSON: {ex}"]
    w = d.get("id", path.name)
    for k in REQUIRED:
        if k not in d:
            problems.append(f"{w}: missing {k}")
    if d.get("id") != path.stem:
        problems.append(f"{w}: id must equal the file name {path.stem}")
    if d.get("category") not in cats:
        problems.append(f"{w}: unknown category {d.get('category')!r}")
    if d.get("status") not in STATUSES:
        problems.append(f"{w}: status {d.get('status')!r} not allowed")
    for k in AR_FIELDS:
        if k in d and not ARABIC.search(str(d[k])):
            problems.append(f"{w}: {k} has no Arabic text")
    if not d.get("who_ar") or not all(ARABIC.search(x) for x in d.get("who_ar", [])):
        problems.append(f"{w}: who_ar must list Arabic role names")
    if SECRET.search(path.read_text(encoding="utf-8")):
        problems.append(f"{w}: looks like it contains a secret")
    steps = d.get("steps") or []
    if not steps:
        problems.append(f"{w}: no walkthrough steps")
    for i, s in enumerate(steps):
        sw = f"{w} step {s.get('n', i + 1)}"
        if s.get("n") != i + 1:
            problems.append(f"{sw}: steps must be numbered 1..n in order")
        for k in ("actor_ar", "action_ar", "response_ar", "next_ar"):
            if not ARABIC.search(str(s.get(k, ""))):
                problems.append(f"{sw}: {k} missing or not Arabic")
        evs(root, s.get("evidence"), problems, sw + " evidence", 1)
        for a in s.get("alt", []):
            if a.get("kind") not in ALT_KINDS:
                problems.append(f"{sw}: alt kind {a.get('kind')!r}")
            evs(root, a.get("evidence"), problems, sw + " alt", 1)
    for grp in ("required", "optional"):
        for f in d.get("fields", {}).get(grp, []):
            evs(root, f.get("evidence"), problems, f"{w} field {f.get('code')}", 1)
    for r in d.get("rules", []):
        evs(root, r.get("evidence"), problems, f"{w} rule", 1)
    for er in d.get("errors", []):
        evs(root, er.get("evidence"), problems, f"{w} error {er.get('code')}", 1)
    for ep in d.get("endpoints", []):
        frag = re.sub(r"^/api/v1", "", ep.get("path", "")).split("{")[0].rstrip("/") or "/"
        lines_ok(root, ep.get("evidence"), problems, f"{w} endpoint {ep.get('method')} {ep.get('path')}", None)
        if isinstance(ep.get("evidence"), dict) and frag and len(frag) > 1:
            lines_ok(root, ep["evidence"], problems, f"{w} endpoint {ep.get('path')}", frag.split("/")[-1] if frag != "/" else None)
    evs(root, d.get("status_evidence"), problems, f"{w} status_evidence", 1)
    if d.get("status") == "VERIFIED_ON_UAT" and "UAT" not in str(d.get("verification_ar", "")):
        problems.append(f"{w}: VERIFIED_ON_UAT needs the UAT evidence described in verification_ar")
    for c in (d.get("tech") or {}).get("chain", []):
        if c.get("layer") not in LAYERS:
            problems.append(f"{w}: tech layer {c.get('layer')!r}")
        lines_ok(root, c.get("ref"), problems, f"{w} tech {c.get('label')}")
        if not ARABIC.search(str(c.get("data_ar", ""))):
            problems.append(f"{w}: tech {c.get('label')} data_ar missing")
    return problems


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", default=str(Path(__file__).resolve().parents[2]))
    ap.add_argument("ids", nargs="*")
    a = ap.parse_args()
    root = Path(a.root)
    cdir = root / "portal/content"
    cats = {c["id"] for c in json.loads((cdir / "categories.json").read_text(encoding="utf-8"))}
    files = sorted((cdir / "features").glob("*.json"))
    if a.ids:
        files = [f for f in files if f.stem in a.ids]
    allp = []
    ids = {f.stem for f in (cdir / "features").glob("*.json")}
    for f in files:
        p = check(root, f, cats)
        try:
            for r in json.loads(f.read_text(encoding="utf-8")).get("related", []):
                if r not in ids:
                    p.append(f"{f.stem}: related feature {r!r} does not exist")
        except Exception:  # noqa: BLE001
            pass
        allp += p
    for p in allp:
        print("PROBLEM", p)
    print(f"{len(files)} feature file(s) checked, {len(allp)} problem(s)")
    return 1 if allp else 0


if __name__ == "__main__":
    sys.exit(main())
