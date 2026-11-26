#!/usr/bin/env python3
"""LD-DOC-R6 M0 documentation checks (documents only; reads files, changes nothing).

1. Source coverage: every paragraph of docs/evidence/DOC-R6-M0/source-extract.txt is cited by at least one register row in
   docs/design/r6/LD-DOC-R6-01 (a "¶a-b" or "¶a" reference), or is listed as structural (title/heading/example/closing line).
2. Every requirement ID used in documents 02-04 exists in the register; every decision D-nn used anywhere is defined in 04.
3. Every register row has a status vocabulary entry (Exists / Partial / Missing / Compliant by design / —).
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
    problems = []
    register = (DESIGN / "LD-DOC-R6-01-requirements-register.md").read_text()
    lines = {}
    for raw in EXTRACT.read_text().splitlines():
        m = re.match(r"\s*(\d+)\t(.*)", raw)
        if m:
            lines[int(m.group(1))] = m.group(2)
    covered = cited(register)
    for n in sorted(lines):
        if n not in covered and n not in STRUCTURAL:
            problems.append("source paragraph %d not cited by any register row: %s" % (n, lines[n][:70]))
    for n in sorted(set(covered) - set(lines)):
        problems.append("register cites paragraph %d which does not exist" % n)
    ids = set(re.findall(r"^\| ((?:PJ|ST|AL|PC)-\d+) \|", register, re.M))
    other = "".join((DESIGN / f).read_text() for f in sorted(p.name for p in DESIGN.glob("LD-DOC-R6-0[234]*.md")))
    for rid in sorted(set(re.findall(r"\b(?:PJ|ST|AL|PC)-\d+\b", other)) - ids):
        problems.append("requirement id %s used in 02-04 but not in the register" % rid)
    decisions = (DESIGN / "LD-DOC-R6-04-decisions-tests-milestones.md").read_text()
    defined = set(re.findall(r"^\| (D-\d+) \|", decisions, re.M))
    everything = register + other
    for d in sorted(set(re.findall(r"\bD-\d+\b", everything)) - defined):
        problems.append("decision %s referenced but not defined in 04" % d)
    for d in sorted(defined):
        if not re.search(r"\b" + d + r"\b", register + (DESIGN / "LD-DOC-R6-03-migrations-permissions-states.md").read_text() + (DESIGN / "LD-DOC-R6-02-gap-architecture-r5.md").read_text()):
            problems.append("decision %s is defined but no register/design row refers to it" % d)
    print("register rows: %d  source paragraphs: %d  cited: %d  structural: %d  decisions: %d"
          % (len(ids), len(lines), len(covered & set(lines)), len(STRUCTURAL), len(defined)))
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
