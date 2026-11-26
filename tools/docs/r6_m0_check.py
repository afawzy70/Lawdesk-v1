#!/usr/bin/env python3
"""LD-DOC-R6 M0 documentation checks (documents only; reads files, changes nothing).

1. Source coverage: every paragraph of docs/evidence/DOC-R6-M0/source-extract.txt is cited by at least one register row in
   docs/design/r6/LD-DOC-R6-01 (a "¶a-b" or "¶a" reference), or is listed as structural (title/heading/example/closing line).
2. Every requirement/test/decision/gate id used in documents 02-06 is defined; the register equals the rendering of r6_register_data.py; the word 'implemented' is not used for R6 behaviour.
3. Every register row has all eight fields, every test id is defined once, gates and findings are defined.
4. Migration allocation scan: lists the highest Flyway version on every remote branch (run before creating any migration;
   the planning range V900-V949 is only a plan until this scan agrees).
Exit 0 = all consistency checks pass (the scan only prints).
"""
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DESIGN = ROOT / "docs" / "design" / "r6"
EXTRACT = ROOT / "docs" / "evidence" / "DOC-R6-M0" / "source-extract.txt"
# Structural lines: titles, section headings, the example sentence and the closing "goal" sentences carry no separate requirement
# (their content is covered by the rows that cite the neighbouring paragraphs).
STRUCTURAL = {1, 2, 3, 14, 15, 22, 30, 42, 61, 62, 64, 74, 92, 98, 107, 130, 131, 138, 141, 143, 146, 148, 166, 167, 169, 170,
              182, 187, 188, 193, 198, 200, 205}
REF = re.compile(r"¶(\d+)(?:\s*[-–]\s*(\d+))?")


def cited(text):
    seen = set()
    for a, b in REF.findall(text):
        seen.update(range(int(a), int(b or a) + 1))
    return seen


def main():
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    import r6_register_data as data
    import r6_register_build
    problems = []
    register = (DESIGN / "LD-DOC-R6-01-requirements-register.md").read_text()
    if register != r6_register_build.render():
        problems.append("01 register is not the rendering of r6_register_data.py (run r6_register_build.py)")
    lines = {}
    for raw in EXTRACT.read_text().splitlines():
        m = re.match(r"\s*(\d+)\t(.*)", raw)
        if m:
            lines[int(m.group(1))] = m.group(2)
    covered = set()
    for row in data.ROWS:
        covered |= cited(row[1])
    for n in sorted(lines):
        if n not in covered and n not in STRUCTURAL:
            problems.append("source paragraph %d not cited by any register row: %s" % (n, lines[n][:70]))
    for n in sorted(set(covered) - set(lines)):
        problems.append("register cites paragraph %d which does not exist" % n)
    ids = [r[0] for r in data.ROWS]
    if len(ids) != len(set(ids)):
        problems.append("duplicate requirement id")
    for r in data.ROWS:
        for i, name in enumerate(("id", "source", "expected", "baseline", "proposed", "acceptance", "tests", "dependency")):
            if not r[i]:
                problems.append("%s: empty field %s" % (r[0], name))
    defined_tests = {t for r in data.ROWS for t, _ in r[6]} | {t for t, _ in data.CROSS}
    all_tests = [t for r in data.ROWS for t, _ in r[6]] + [t for t, _ in data.CROSS]
    if len(all_tests) != len(set(all_tests)):
        problems.append("duplicate test id")
    docs = {p.name: p.read_text() for p in sorted(DESIGN.glob("LD-DOC-R6-0*.md"))}
    other = "".join(v for k, v in docs.items() if not k.startswith("LD-DOC-R6-01"))
    for rid in sorted(set(re.findall(r"\b(?:PJ|ST|AL|PC)-\d+\b", other + register)) - set(ids)):
        problems.append("requirement id %s used but not in the register" % rid)
    for t in sorted(set(re.findall(r"\bT-[A-Z]+\d*-\d+\b", other)) - defined_tests):
        problems.append("test id %s used in 02-06 but not defined" % t)
    for m in re.finditer(r"\b(T-[A-Z]+)-(\d+)…(\d+)", other):
        for n in range(int(m.group(2)), int(m.group(3)) + 1):
            if "%s-%d" % (m.group(1), n) not in defined_tests:
                problems.append("range %s-%s…%s includes undefined %s-%d" % (m.group(1), m.group(2), m.group(3), m.group(1), n))
    decisions = docs["LD-DOC-R6-04-decisions-tests-milestones.md"]
    defined = set(re.findall(r"^\| (D-\d+) \|", decisions, re.M))
    everything = register + other
    for d in sorted(set(re.findall(r"\bD-\d+\b", everything)) - defined):
        problems.append("decision %s referenced but not defined in 04" % d)
    for d in sorted(defined):
        if not re.search(r"\b" + d + r"\b", register + "".join(v for k, v in docs.items() if not k.startswith("LD-DOC-R6-04"))):
            problems.append("decision %s is defined but referenced nowhere outside 04" % d)
    gates = {g for g in re.findall(r"\bG-[A-Z]+\b", everything)}
    for g in sorted(gates):
        if not re.search(r"\b" + g + r"\b", docs["LD-DOC-R6-05-authoritative-design.md"]) :
            problems.append("gate %s is not defined in 05" % g)
    # Terminology: in a documentation-only milestone nothing is called implemented (the vocabulary line that explains the ban is allowed).
    ALLOWED = ("not implemented", "none implemented", "built in r6", "is not used", "not used for r6", "\"implemented as written\"")
    for name, text in docs.items():
        for line in text.splitlines():
            if re.search(r"\bimplemented\b", line, re.I) and not any(a in line.lower() for a in ALLOWED):
                problems.append("%s uses 'implemented': %s" % (name, line.strip()[:90]))
    findings = docs["LD-DOC-R6-06-qa-finding-matrix.md"]
    for n in range(1, 17):
        if "QA-R6-M0-%03d" % n not in findings:
            problems.append("finding QA-R6-M0-%03d missing from the matrix" % n)
    print("register rows: %d  tests: %d  source paragraphs: %d  cited: %d  structural: %d  decisions: %d  gates: %d"
          % (len(ids), len(defined_tests), len(lines), len(covered & set(lines)), len(STRUCTURAL), len(defined), len(gates)))
    for p in problems:
        print("PROBLEM", p)
    print("migration scan (highest version per remote branch; compare before creating any migration):")
    branches = [b.strip() for b in subprocess.run(["git", "branch", "-r"], cwd=ROOT, capture_output=True, text=True).stdout.splitlines() if "HEAD" not in b]
    top = {}
    for b in branches:
        out = subprocess.run(["git", "ls-tree", "--name-only", b, "backend/src/main/resources/db/migration/"], cwd=ROOT, capture_output=True, text=True).stdout
        versions = [int(v) for v in re.findall(r"/V(\d+)__", out)]
        if versions:
            top[b] = max(versions)
    for b, v in sorted(top.items(), key=lambda kv: -kv[1])[:8]:
        print("  V%d  %s" % (v, b))
    print("highest anywhere: V%d" % max(top.values()))
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
